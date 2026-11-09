package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.core.BrickCapacityConfig;
import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseWorldModel;
import fr.brickcraft.net.PreciseExportPayload;
import fr.brickcraft.net.StudioExportStatusPayload;
import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Safe single-anchor visual projection. Does not mutate thousands of world blocks. */
public final class PreciseExportManager {
    private static final int TIMEOUT=12000, VALIDATE_PER_TICK=192, MAX_JOBS=2;
    private static final Map<UUID,Job> JOBS=new HashMap<>();
    private enum Phase{RECEIVING,VALIDATING}
    private static final class Job{
        final UUID player;final ServerWorld world;final BlockPos anchor;final int count;
        final List<LDrawProjectReader.Instance> parts=new ArrayList<>();
        final Set<String> validatedDesigns=new HashSet<>();
        int ticks=0,validated=0,prints=0;
        Phase phase=Phase.RECEIVING;
        Job(ServerPlayerEntity p,BlockPos a,int c){player=p.getUuid();world=p.getServerWorld();anchor=a.toImmutable();count=c;}
    }
    private PreciseExportManager(){}
    public static void receive(ServerPlayerEntity player,PreciseExportPayload data){
        if(data.operation()==PreciseExportPayload.BEGIN){begin(player,data);return;}
        Job job=JOBS.get(player.getUuid());
        if(job==null){if(data.operation()==PreciseExportPayload.COMMIT)reply(player,true,false,"Projection non demarree");return;}
        if(data.operation()==PreciseExportPayload.CANCEL){stop(job,false,"Projection annulee, aucun bloc modifie");return;}
        if(job.phase!=Phase.RECEIVING)return;
        if(player.getServerWorld()!=job.world){stop(job,false,"Dimension changee");return;}
        if(data.operation()==PreciseExportPayload.PARTS){
            if(data.offset()!=job.parts.size()||data.parts().size()<1 ||
                    data.parts().size()>PreciseExportPayload.BATCH_SIZE ||
                    job.parts.size()+data.parts().size()>job.count){stop(job,false,"Lots de projection invalides");return;}
            for(var p:data.parts()){
                if(p.partId()==null||p.partId().length()>96||!p.partId().matches("[A-Za-z0-9_./-]{1,96}")||
                        !Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())||
                        Math.abs(p.x())>100000||Math.abs(p.y())>100000||Math.abs(p.z())>100000 ||
                        !PreciseWorldModel.validMatrix(p.matrix())){
                    stop(job,false,"Piece ou transformation precise invalide");return;
                }
            }
            job.parts.addAll(data.parts());job.ticks=0;
        }else if(data.operation()==PreciseExportPayload.COMMIT){
            if(job.parts.size()!=job.count){stop(job,false,"Transfert incomplet : "+job.parts.size()+"/"+job.count);return;}
            job.phase=Phase.VALIDATING;job.ticks=0;
            reply(player,false,true,"Validation des "+job.count+" pieces avant insertion...");
        }
    }
    private static void begin(ServerPlayerEntity player,PreciseExportPayload p){
        BlockPos anchor=p.origin();ServerWorld world=player.getServerWorld();
        if(JOBS.containsKey(player.getUuid())||JOBS.size()>=MAX_JOBS ||
                !player.isCreative() || p.total()<1 || p.total()>BrickCapacityConfig.effectiveParts() ||
                !world.isInBuildLimit(anchor) ||
                player.squaredDistanceTo(Vec3d.ofCenter(anchor))>256 ||
                !world.canPlayerModifyAt(player,anchor)||!world.getBlockState(anchor).isAir()){
            reply(player,true,false,"Projection refusee : capacite ("+BrickCapacityConfig.label(BrickCapacityConfig.partsSetting())+") / permissions / air libre requis");return;
        }
        JOBS.put(player.getUuid(),new Job(player,anchor,p.total()));
        reply(player,false,true,"Reception projection LDraw : "+p.total()+" pieces...");
    }
    public static void tick(MinecraftServer server){
        for(Job job:new ArrayList<>(JOBS.values())){
            if(JOBS.get(job.player)!=job)continue;
            ServerPlayerEntity player=server.getPlayerManager().getPlayer(job.player);
            if(player==null || player.getServerWorld()!=job.world || !player.isCreative()){
                stop(job,false,"Projection interrompue");continue;
            }
            if(++job.ticks>TIMEOUT){stop(job,false,"Projection expiree");continue;}
            if(job.phase!=Phase.VALIDATING)continue;
            try{
                // Validate a bounded amount each tick to avoid freezing the integrated server.
                int end=Math.min(job.parts.size(),job.validated+VALIDATE_PER_TICK);
                for(int i=job.validated;i<end;i++){
                    var p=job.parts.get(i);
                    if(!job.validatedDesigns.add(p.partId()))continue;
                    String id=fr.brickcraft.importer.PreciseReferenceMapper.geometryId(p.partId());
                    if(id==null || fr.brickcraft.ldraw.LDrawLibrary.originalBounds(id)==null){
                        stop(job,false,"Reference introuvable : "+p.partId()+" (#"+i+")");break;
                    }
                }
                if(JOBS.get(job.player)!=job)continue;
                job.validated=end;
                if(job.validated<job.count)continue;
                PreciseWorldModel model=PreciseWorldModel.create(job.parts);
                if(job.anchor.getY()+model.height()+1>=job.world.getTopY() ||
                        !job.world.getBlockState(job.anchor).isAir() ||
                        !job.world.canPlayerModifyAt(player,job.anchor)){
                    stop(job,false,"Origine occupee ou construction au-dessus du monde");continue;
                }
                // Atomic world modification: only ONE anchor block, no partial castle.
                if(!job.world.setBlockState(job.anchor,BrickCraft.PRECISE_DISPLAY.getDefaultState(),3)){
                    stop(job,false,"Creation de l'ancre impossible");continue;
                }
                if(job.world.getBlockEntity(job.anchor) instanceof PreciseDisplayEntity entity){
                    try{entity.setModel(model);}
                    catch(IOException e){job.world.setBlockState(job.anchor,Blocks.AIR.getDefaultState(),3);
                        stop(job,false,"Enregistrement impossible : "+e.getMessage());continue;}
                    stop(job,true,"Projection placee : "+model.parts().size()+" pieces LDraw exactes, "+
                            model.mappedPrints()+" impressions sans texture. Ancre : "+job.anchor.toShortString()+
                            ". Projection visuelle (sans collisions).");
                }else{
                    job.world.setBlockState(job.anchor,Blocks.AIR.getDefaultState(),3);
                    stop(job,false,"Block Entity de projection manquante");
                }
            }catch(Exception ex){stop(job,false,"Erreur : "+ex.getClass().getSimpleName()+" : "+ex.getMessage());}
        }
    }
    private static void stop(Job job,boolean success,String message){
        JOBS.remove(job.player,job);
        ServerPlayerEntity player=job.world.getServer().getPlayerManager().getPlayer(job.player);
        if(player!=null)reply(player,true,success,message);
    }
    private static void reply(ServerPlayerEntity player,boolean done,boolean success,String message){
        ServerPlayNetworking.send(player,new StudioExportStatusPayload(done,success,message));
        if(done)player.sendMessage(Text.literal("BrickCraft : "+message),false);
    }
}
