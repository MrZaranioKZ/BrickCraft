package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.net.PreciseManagePayload;
import fr.brickcraft.net.PreciseManageStatusPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** List loaded, nearby projection anchors. Remove only the exact selected anchor. */
public final class PreciseManageManager {
    private PreciseManageManager(){}
    private static void reply(ServerPlayerEntity p,byte event,String message,List<PreciseManageStatusPayload.Entry> projects){
        ServerPlayNetworking.send(p,new PreciseManageStatusPayload(event,projects,message));
    }
    public static void receive(ServerPlayerEntity player,PreciseManagePayload request){
        if(!player.isCreative()||player.isSpectator()){
            reply(player,PreciseManageStatusPayload.ERROR,"Mode Creatif requis.",List.of());return;
        }
        if(request.operation()==PreciseManagePayload.LIST){list(player);return;}
        if(request.operation()==PreciseManagePayload.REMOVE){remove(player,request.anchor());return;}
        reply(player,PreciseManageStatusPayload.ERROR,"Operation inconnue.",List.of());
    }
    private static void list(ServerPlayerEntity player){
        ServerWorld world=player.getServerWorld();BlockPos base=player.getBlockPos();
        int cX=base.getX()>>4,cZ=base.getZ()>>4;
        int sections=(PreciseManageRules.RADIUS+15)/16;
        ArrayList<PreciseManageStatusPayload.Entry> entries=new ArrayList<>();
        // isChunkLoaded is checked BEFORE getChunk; listing must never generate terrain.
        for(int x=cX-sections;x<=cX+sections;x++)for(int z=cZ-sections;z<=cZ+sections;z++){
            BlockPos probe=new BlockPos(x<<4,base.getY(),z<<4);
            if(!world.isChunkLoaded(probe))continue;
            for(var be:world.getChunk(x,z).getBlockEntities().values()){
                if(!(be instanceof PreciseDisplayEntity anchor))continue;
                BlockPos pos=anchor.getPos();
                if(!PreciseManageRules.inRange(player.getX(),player.getY(),player.getZ(),
                        pos.getX(),pos.getY(),pos.getZ()))continue;
                if(!world.getBlockState(pos).isOf(BrickCraft.PRECISE_DISPLAY))continue;
                var model=anchor.model();
                entries.add(new PreciseManageStatusPayload.Entry(pos.toImmutable(),
                        model==null?0:model.parts().size()));
            }
        }
        entries.sort(Comparator.comparingDouble(p->player.squaredDistanceTo(
                p.anchor().getX()+.5,p.anchor().getY()+.5,p.anchor().getZ()+.5)));
        if(entries.size()>PreciseManageStatusPayload.LIMIT)
            entries.subList(PreciseManageStatusPayload.LIMIT,entries.size()).clear();
        reply(player,PreciseManageStatusPayload.LIST,
                "Projections proches et chargees : "+entries.size(),entries);
    }
    private static void remove(ServerPlayerEntity player,BlockPos pos){
        ServerWorld world=player.getServerWorld();
        if(!PreciseManageRules.inRange(player.getX(),player.getY(),player.getZ(),pos.getX(),pos.getY(),pos.getZ())
                || !world.isChunkLoaded(pos) || !world.isInBuildLimit(pos)
                || !world.canPlayerModifyAt(player,pos)
                || !world.getBlockState(pos).isOf(BrickCraft.PRECISE_DISPLAY)
                || !(world.getBlockEntity(pos) instanceof PreciseDisplayEntity)){
            reply(player,PreciseManageStatusPayload.ERROR,
                    "Retrait refuse : ancre absente, trop loin ou sans permission.",List.of());return;
        }
        if(world.getBlockEntity(pos) instanceof PreciseDisplayEntity anchor && (anchor.collisionCells().length>0 || anchor.hasAnchorCollision())){
            reply(player,PreciseManageStatusPayload.ERROR,
                    "Desactive la physique avant de retirer cette projection.",List.of());return;
        }
        // The projection consists of exactly one marker block and its compressed model.
        // No LEGO cells, other blocks or .io/.bcpx files are modified.
        if(!world.setBlockState(pos,Blocks.AIR.getDefaultState(),3)){
            reply(player,PreciseManageStatusPayload.ERROR,"Impossible de retirer l'ancre.",List.of());return;
        }
        reply(player,PreciseManageStatusPayload.REMOVED,
                "Projection retiree en "+pos.toShortString()+". Fichiers intacts.",
                List.of(new PreciseManageStatusPayload.Entry(pos,0)));
    }
}
