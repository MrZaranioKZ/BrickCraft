package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseProjectIO;
import fr.brickcraft.importer.PreciseReferenceMapper;
import fr.brickcraft.importer.PreciseWorldModel;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.client.WorldPiecePicker;
import fr.brickcraft.net.PreciseWorldEditPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.io.IOException;
import java.util.*;

/** Persistent, server-authoritative editing of one part inside a projected LEGO assembly. */
public final class PreciseWorldEditManager {
    private PreciseWorldEditManager(){}
    private record Last(String dimension,BlockPos anchor,int index,LDrawProjectReader.Instance before,
                        LDrawProjectReader.Instance after){}
    private static final Map<UUID,Deque<Last>> undo=new HashMap<>();
    private static final Map<UUID,Integer> ticks=new HashMap<>(),requests=new HashMap<>();
    private static void error(ServerPlayerEntity p,String message){p.sendMessage(Text.literal("BrickCraft : "+message),true);}
    private static boolean same(LDrawProjectReader.Instance a,LDrawProjectReader.Instance b){
        return a!=null&&b!=null&&a.partId().equals(b.partId())&&a.color()==b.color()&&
                a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z()&&Arrays.equals(a.matrix(),b.matrix());
    }
    private static boolean nearPart(ServerPlayerEntity player,PreciseWorldModel model,BlockPos pos,int index){
        var p=model.parts().get(index);
        String id=PreciseReferenceMapper.geometryId(p.partId());
        double[] b=id==null?null:LDrawLibrary.originalBounds(id);
        if(b==null)return false;
        double[][] corners=WorldPiecePicker.corners(p,b,model.minX(),model.minY(),model.minZ(),
                pos.getX(),pos.getY(),pos.getZ());
        double loX=Double.POSITIVE_INFINITY,loY=loX,loZ=loX,hiX=-loX,hiY=-loX,hiZ=-loX;
        for(double[] v:corners){loX=Math.min(loX,v[0]);loY=Math.min(loY,v[1]);loZ=Math.min(loZ,v[2]);
            hiX=Math.max(hiX,v[0]);hiY=Math.max(hiY,v[1]);hiZ=Math.max(hiZ,v[2]);}
        Vec3d eye=player.getEyePos();
        double dx=Math.max(0,Math.max(loX-eye.x,eye.x-hiX));
        double dy=Math.max(0,Math.max(loY-eye.y,eye.y-hiY));
        double dz=Math.max(0,Math.max(loZ-eye.z,eye.z-hiZ));
        return dx*dx+dy*dy+dz*dz<=16*16;
    }
    public static void receive(ServerPlayerEntity player,PreciseWorldEditPayload q){
        if(!player.isCreative()||player.isSpectator()){player.sendMessage(Text.translatable("brickcraft.world_edit.creative_only"),true);return;}
        // Never allow a malformed client to flood serialized NBT/network updates.
        int tick=player.age;
        if(ticks.getOrDefault(player.getUuid(),-1)!=tick){ticks.put(player.getUuid(),tick);requests.put(player.getUuid(),0);}
        int n=requests.merge(player.getUuid(),1,Integer::sum);
        if(n>6)return;
        ServerWorld world=player.getServerWorld();BlockPos pos=q.anchor();
        if(!world.isChunkLoaded(pos)||!world.isInBuildLimit(pos)||
                !world.getBlockState(pos).isOf(BrickCraft.PRECISE_DISPLAY)||
                !world.canPlayerModifyAt(player,pos)||
                !(world.getBlockEntity(pos) instanceof PreciseDisplayEntity anchor)){
            error(player,"projection indisponible ou zone protegee");return;
        }
        if((anchor.collisionCells().length>0 || anchor.hasAnchorCollision())){
            error(player,"desactive la physique avant de deplacer des pieces (recalcul des collisions requis)");return;
        }
        PreciseWorldModel model=anchor.model();
        if(model==null||q.index()>=model.parts().size()){error(player,"piece non chargee");return;}
        if(!nearPart(player,model,pos,q.index())){error(player,"rapproche-toi de la piece (16 blocs maximum)");return;}
        if(q.operation()==PreciseWorldEditMath.UNDO){undo(player,world,anchor,model,q.index());return;}
        var before=model.parts().get(q.index());
        LDrawProjectReader.Instance after;
        try {
            if(q.operation()==PreciseWorldEditMath.SNAP_NOW){
                if(q.amount()!=0){error(player,"snap : parametre inattendu");return;}
                after=before;
            }else if(q.operation()==PreciseWorldEditMath.MOVE_WITH_SNAP){
                after=PreciseWorldEditMath.apply(before,PreciseWorldEditMath.MOVE,q.axis(),q.amount());
            }else{
                after=PreciseWorldEditMath.apply(before,q.operation(),q.axis(),q.amount());
            }
        }catch(IllegalArgumentException ex){error(player,ex.getMessage());return;}
        ArrayList<LDrawProjectReader.Instance> parts=new ArrayList<>(model.parts());
        parts.set(q.index(),after);
        boolean didSnap=false;
        int mate=-1;
        if(q.operation()==PreciseWorldEditMath.SNAP_NOW &&
                PreciseStudSnapper.profile(before.partId())==null){
            error(player,"reference sans connecteurs verifies : placement libre disponible");return;
        }
        if(q.operation()==PreciseWorldEditMath.SNAP_NOW ||
                q.operation()==PreciseWorldEditMath.MOVE_WITH_SNAP){
            try {
                var snapped=PreciseStudSnapper.snap(parts,q.index(),10.0);
                if(snapped.snapped()){
                    didSnap=true;mate=snapped.supportIndex();
                    after=snapped.piece();parts.set(q.index(),after);
                }else if(q.operation()==PreciseWorldEditMath.SNAP_NOW){
                    error(player,"aucun tenon compatible proche (10 LDU). Piece libre conservee");
                    return;
                }
            }catch(IllegalArgumentException ex){error(player,ex.getMessage());return;}
        }
        if(same(before,after))return;
        try{anchor.setModel(PreciseWorldModel.editKeepingOrigin(parts,model));}
        catch(IllegalArgumentException|IOException ex){error(player,"deplacement impossible : "+ex.getMessage());return;}
        if(didSnap)player.sendMessage(Text.translatable("brickcraft.world_edit.connected",q.index()+1,mate+1),true);
        Deque<Last> history=undo.computeIfAbsent(player.getUuid(),id->new ArrayDeque<>());
        history.addFirst(new Last(world.getRegistryKey().getValue().toString(),pos.toImmutable(),q.index(),before,after));
        while(history.size()>64)history.removeLast();
    }
    private static void undo(ServerPlayerEntity player,ServerWorld world,PreciseDisplayEntity be,
                             PreciseWorldModel model,int index){
        Deque<Last> history=undo.get(player.getUuid());
        if(history==null||history.isEmpty()){error(player,"aucune action a annuler");return;}
        Last last=history.peekFirst();
        if(!last.dimension().equals(world.getRegistryKey().getValue().toString())||
                !last.anchor().equals(be.getPos())||last.index()!=index||
                !same(model.parts().get(index),last.after())){
            error(player,"annulation refusee : projet modifie ou autre piece selectionnee");return;
        }
        ArrayList<LDrawProjectReader.Instance> parts=new ArrayList<>(model.parts());parts.set(index,last.before());
        try{be.setModel(PreciseWorldModel.editKeepingOrigin(parts,model));history.removeFirst();}
        catch(IOException|IllegalArgumentException ex){error(player,"annulation impossible");}
    }
}
