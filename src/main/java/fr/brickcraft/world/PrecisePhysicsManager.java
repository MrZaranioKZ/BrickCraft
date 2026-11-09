package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.core.BrickCapacityConfig;
import fr.brickcraft.importer.PreciseReferenceMapper;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.net.PrecisePhysicsPayload;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** LDraw surface + multi-axis parity rasterization, quantized to 8^3 micro-voxels per cell.
 * Only AIR is filled; all cell ownership is recorded on the anchor BEFORE block placement.
 * A voxel mask is persisted on EACH proxy BlockEntity and synced to clients. */
public final class PrecisePhysicsManager {
    private PrecisePhysicsManager(){}
    private record Job(ServerWorld world,BlockPos anchor,ServerPlayerEntity player,long[] cells,
                       Map<Long,long[]> masks,long[] anchorMask,boolean enable,int next){}
    private static final Map<String,Job> JOBS=new LinkedHashMap<>();
    private record Preparation(ServerWorld world,BlockPos anchor,UUID player,
                               PreciseMeshCollisionPlan.Incremental calculator){}
    private static final Map<String,Preparation> PREPARATIONS=new LinkedHashMap<>();
    private static String key(ServerWorld world,BlockPos pos){return world.getRegistryKey().getValue()+":"+pos.asLong();}
    private static void say(ServerPlayerEntity p,String key,Object...args){if(p!=null)p.sendMessage(Text.translatable(key,args),false);}
    public static void receive(ServerPlayerEntity player,PrecisePhysicsPayload data){
        ServerWorld world=player.getServerWorld();BlockPos anchor=data.anchor();
        if(!player.isCreative()||player.isSpectator()||player.squaredDistanceTo(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5)>192*192||
           !world.isChunkLoaded(anchor)||!world.canPlayerModifyAt(player,anchor)||
           !(world.getBlockEntity(anchor) instanceof PreciseDisplayEntity be)||be.model()==null){
            say(player,"brickcraft.v252.physics.denied");return;
        }
        String taskKey=key(world,anchor);
        if(!data.enable() && PREPARATIONS.remove(taskKey)!=null){
            say(player,"brickcraft.v252.physics.cancel_prep");return;
        }
        Job previousJob=JOBS.get(taskKey);
        if(!data.enable() && previousJob!=null && previousJob.enable()){
            long[] owned=be.collisionCells();
            JOBS.put(taskKey,new Job(world,anchor,player,owned,Map.of(),new long[0],false,0));
            say(player,"brickcraft.v252.physics.abort",owned.length);return;
        }
        if(JOBS.containsKey(taskKey)||PREPARATIONS.containsKey(taskKey)){
            say(player,"brickcraft.v252.physics.busy");return;
        }
        if(data.enable()){
            try{
                var model=be.model();
                Map<String,PreciseMeshCollisionPlan.Geometry> geometryCache=new HashMap<>();
                var calculator=new PreciseMeshCollisionPlan.Incremental(
                        model.parts(),model.minX(),model.minY(),model.minZ(),
                        anchor.getX(),anchor.getY(),anchor.getZ(),id->geometryCache.computeIfAbsent(id,partId->{
                            String ref=PreciseReferenceMapper.geometryId(partId);
                            if(ref==null)return null;
                            double[] bounds=LDrawLibrary.originalBounds(ref);
                            LDrawLibrary.Mesh mesh=LDrawLibrary.mesh(ref);
                            return bounds==null||mesh==null?null:new PreciseMeshCollisionPlan.Geometry(mesh.xyz(),bounds);
                        }),BrickCapacityConfig.effectiveCells(),BrickCapacityConfig.effectiveTriangles());
                PREPARATIONS.put(key(world,anchor),new Preparation(world,anchor,player.getUuid(),calculator));
                say(player,"brickcraft.v252.physics.preparing",model.parts().size(),BrickCapacityConfig.label(BrickCapacityConfig.cellsSetting()));
            }catch(Exception e){say(player,"brickcraft.v252.physics.error",e.getMessage());}
        }else{
            long[] existing=be.collisionCells();
            if(existing.length==0 && !be.hasAnchorCollision()){say(player,"brickcraft.v252.physics.no_collisions");return;}
            JOBS.put(key(world,anchor),new Job(world,anchor,player,existing,Map.of(),new long[0],false,0));
            say(player,"brickcraft.v252.physics.removing",existing.length);
        }
    }
    /** Only called after all micro-voxels are prepared, with the same preflight as V0.13. */
    private static void placePrepared(ServerPlayerEntity player,ServerWorld world,BlockPos anchor,
                                      PreciseDisplayEntity be,PreciseMeshCollisionPlan.Result result){
        long[] anchorMask=result.masks().getOrDefault(
                new PreciseMeshCollisionPlan.Cell(anchor.getX(),anchor.getY(),anchor.getZ()),new long[0]);
        Map<Long,long[]> prepared=new LinkedHashMap<>();
        for(var entry:result.masks().entrySet()){
            var c=entry.getKey();
            if(c.x()==anchor.getX()&&c.y()==anchor.getY()&&c.z()==anchor.getZ())continue;
            prepared.put(new BlockPos(c.x(),c.y(),c.z()).asLong(),entry.getValue());
        }
        long[] previous=be.collisionCells();
        if(previous.length>0){
            Map<Long,long[]> owned=new LinkedHashMap<>();
            for(long pos:previous)if(prepared.containsKey(pos))owned.put(pos,prepared.get(pos));
            if(owned.size()!=previous.length)throw new IllegalArgumentException("Anciennes collisions incompatibles : Physique OFF puis ON requis");
            for(long encoded:previous){
                BlockPos pos=BlockPos.fromLong(encoded);
                if(!world.isChunkLoaded(pos)||!world.canPlayerModifyAt(player,pos))
                    throw new IllegalArgumentException("Zone non chargee/protegee : "+pos.toShortString());
                if(!world.getBlockState(pos).isAir()&&!world.getBlockState(pos).isOf(BrickCraft.PRECISE_COLLISION))
                    throw new IllegalArgumentException("Terrain modifie : "+pos.toShortString());
            }
            JOBS.put(key(world,anchor),new Job(world,anchor,player,previous,owned,anchorMask,true,0));
            say(player,"brickcraft.v252.physics.updating",previous.length);return;
        }
        Map<Long,long[]> free=new LinkedHashMap<>();int terrain=0;
        for(var entry:prepared.entrySet()){
            BlockPos pos=BlockPos.fromLong(entry.getKey());
            if(!world.isInBuildLimit(pos)||!world.isChunkLoaded(pos)||!world.canPlayerModifyAt(player,pos))
                throw new IllegalArgumentException("Zone non chargee ou protegee : "+pos.toShortString());
            if(world.getBlockState(pos).isAir())free.put(entry.getKey(),entry.getValue());
            else if(world.getBlockState(pos).isOf(BrickCraft.PRECISE_COLLISION))
                throw new IllegalArgumentException("Collision avec une autre projection");
            else terrain++;
        }
        long[] keys=free.keySet().stream().mapToLong(Long::longValue).toArray();
        if(keys.length==0){
            be.setAnchorCollisionMask(anchorMask);
            say(player,"brickcraft.v252.physics.only_anchor");return;
        }
        be.setCollisionCells(keys); // ownership persisted before mutations
        JOBS.put(key(world,anchor),new Job(world,anchor,player,keys,free,anchorMask,true,0));
        say(player,"brickcraft.v252.physics.prepared",keys.length,result.triangles(),terrain);
    }
    private static void tickPreparation(MinecraftServer server){
        // Bounded number of pieces per tick. A high-detail individual part can still cost
        // a tick; never start multiple large jobs simultaneously by default.
        int budget=6;
        var iter=PREPARATIONS.entrySet().iterator();
        while(iter.hasNext()&&budget>0){
            var item=iter.next();var prep=item.getValue();
            ServerPlayerEntity player=server.getPlayerManager().getPlayer(prep.player());
            if(player==null||player.getServerWorld()!=prep.world()||!player.isCreative()||
               player.squaredDistanceTo(prep.anchor().getX()+.5,prep.anchor().getY()+.5,prep.anchor().getZ()+.5)>192*192||
               !prep.world().canPlayerModifyAt(player,prep.anchor())||
               !(prep.world().getBlockEntity(prep.anchor()) instanceof PreciseDisplayEntity be)){
                iter.remove();continue;
            }
            try{
                int n=Math.min(budget,6);
                prep.calculator().advance(n);budget-=n;
                int done=prep.calculator().completed();
                // Send a periodic, localized action-bar update, not dozens of chat messages.
                if(done%500<n)player.sendMessage(Text.translatable("brickcraft.v252.physics.progress",done,prep.calculator().total()),true);
                if(prep.calculator().done()){
                    iter.remove();
                    placePrepared(player,prep.world(),prep.anchor(),be,prep.calculator().finish());
                }
            }catch(Exception ex){iter.remove();say(player,"brickcraft.v252.physics.error",ex.getMessage());}
        }
    }
    public static void tick(MinecraftServer server){
        tickPreparation(server);
        var iter=JOBS.entrySet().iterator();int budget=128;
        while(iter.hasNext()&&budget>0){
            var entry=iter.next();Job job=entry.getValue();
            if(job.world().getBlockEntity(job.anchor()) instanceof PreciseDisplayEntity be){
                int i=job.next();
                while(i<job.cells().length && budget>0){
                    long encoded=job.cells()[i];BlockPos pos=BlockPos.fromLong(encoded);
                    if(!job.world().isChunkLoaded(pos))break; // Never silently skip an unloaded cell.
                    {
                        if(job.enable()){
                            if(job.world().getBlockState(pos).isAir())
                                job.world().setBlockState(pos,BrickCraft.PRECISE_COLLISION.getDefaultState(),3);
                            if(job.world().getBlockState(pos).isOf(BrickCraft.PRECISE_COLLISION)&&
                               job.world().getBlockEntity(pos) instanceof PreciseCollisionEntity cell){
                                long[] mask=job.masks().get(encoded);
                                if(mask!=null)cell.setMask(mask);
                            }
                        }else if(job.world().getBlockState(pos).isOf(BrickCraft.PRECISE_COLLISION))
                            job.world().setBlockState(pos,Blocks.AIR.getDefaultState(),3);
                    }
                    i++;budget--;
                    if(i%10000==0)job.player().sendMessage(Text.translatable("brickcraft.v252.physics.placing",i,job.cells().length),true);
                }
                if(i>=job.cells().length){
                    if(!job.enable())be.setCollisionCells(new long[0]);
                    be.setAnchorCollisionMask(job.enable()?job.anchorMask():new long[0]);
                    say(job.player(),job.enable()?"brickcraft.v252.physics.complete_on":"brickcraft.v252.physics.complete_off",job.cells().length);
                    iter.remove();
                }else entry.setValue(new Job(job.world(),job.anchor(),job.player(),job.cells(),job.masks(),job.anchorMask(),job.enable(),i));
            }else iter.remove(); // anchor's onStateReplaced already invoked cleanup
        }
    }
    /** Triggered by anchor destruction; only touches loaded chunks and our own collision blocks. */
    public static void cleanup(ServerWorld world,PreciseDisplayEntity be){
        JOBS.remove(key(world,be.getPos()));
        PREPARATIONS.remove(key(world,be.getPos()));
        for(long key:be.collisionCells()){
            BlockPos pos=BlockPos.fromLong(key);
            if(world.isChunkLoaded(pos)&&world.getBlockState(pos).isOf(BrickCraft.PRECISE_COLLISION))
                world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);
        }
        be.setCollisionCells(new long[0]);
        be.setAnchorCollisionMask(new long[0]);
    }
}
