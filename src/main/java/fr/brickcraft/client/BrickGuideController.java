package fr.brickcraft.client;

import fr.brickcraft.guide.BrickGuidePlan;
import fr.brickcraft.importer.PreciseReferenceMapper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import fr.brickcraft.world.BrickCellEntity;
import fr.brickcraft.guide.BrickSourceStepReader;
import fr.brickcraft.importer.PreciseWorldModel;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.world.PreciseDisplayEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Client-only instructions: NEVER removes Minecraft blocks or alters the world/NBT. */
public final class BrickGuideController {
    private BrickGuideController(){}
    public enum Mode { SOURCE, LAYERS, LOTS, PIECES }
    public record Filter(int revision,int[] stepOfPart,int completedSteps,int uploadBatchSize) {}
    public record Material(String part,int color,int quantity) {}
    private static Object boundWorld;
    private static BlockPos anchor;
    private static PreciseWorldModel boundModel;
    private static BrickGuidePlan plan,sourcePlan;
    private static Mode mode=Mode.LAYERS;
    private static int completed=0,revision=0;
    private static boolean active=false;
    private static boolean targetOverlay=true;
    public record Target(int index,String part,int color,double[][] corners,double x,double y,double z) {}
    public static boolean targetOverlay(){return targetOverlay;}
    public static boolean toggleTargetOverlay(){targetOverlay=!targetOverlay;return targetOverlay;}
    /** Next step, up to 48 highlighted oriented volumes; index mapping stays lossless. */
    public static List<Target> nextTargets(int limit){
        if(!active()||completed>=plan.size()||boundModel==null||anchor==null)return List.of();
        ArrayList<Target> list=new ArrayList<>();
        for(int idx:plan.at(completed+1).partIndexes()){
            var part=boundModel.parts().get(idx);
            String ref=PreciseReferenceMapper.geometryId(part.partId());
            double[] b=ref==null?null:LDrawLibrary.originalBounds(ref);
            if(b==null)continue;
            double[][] corners=WorldPiecePicker.corners(part,b,boundModel.minX(),boundModel.minY(),boundModel.minZ(),
                    anchor.getX(),anchor.getY(),anchor.getZ());
            double x=0,y=0,z=0;
            for(double[] v:corners){x+=v[0]/8;y+=v[1]/8;z+=v[2]/8;}
            list.add(new Target(idx,part.partId(),part.color(),corners,x,y,z));
            if(list.size()>=limit)break;
        }
        return List.copyOf(list);
    }
    /** Verify separately PLACED BrickCraft bricks. The visual projection itself is NOT proof of a pose. */
    public static String verifyRealPlacedParts(MinecraftClient client){
        if(!active()||completed>=plan.size()||client.world==null)return "Aucune etape en cours";
        var targets=nextTargets(64);
        if(targets.isEmpty())return "Dimensions manquantes pour cette etape";
        if(plan.at(completed+1).partIndexes().size()>64)return "Etape trop grande pour la verification (max 64)";
        for(Target target:targets){
            boolean match=false;
            // The target centre is used only to find candidate Minecraft cells;
            // both the form and the LDraw colour must match, not just a proxy block.
            BlockPos centre=BlockPos.ofFloored(target.x(),target.y(),target.z());
            for(int dx=-1;dx<=1&&!match;dx++)for(int dy=-1;dy<=1&&!match;dy++)
                for(int dz=-1;dz<=1&&!match;dz++){
                    BlockPos cell=centre.add(dx,dy,dz);
                    if(client.world.getBlockEntity(cell) instanceof BrickCellEntity brick)
                        for(var piece:brick.pieces())if(piece.partId().equalsIgnoreCase(target.part())&&
                                piece.colorCode()==target.color()){
                            match=true;break;
                        }
                }
            if(!match)return "Piece #"+(target.index()+1)+" non verifiee : "+target.part()+
                    " (l'objet projete ne compte pas comme une piece posee)";
        }
        completed++;return "Etape confirmee : reference/couleur trouvees a proximite (rotation non verifiee)";
    }
    /** Explicit USER confirmation. Not an assertion that a matching Minecraft block was detected. */
    public static String confirmManually(MinecraftClient client){
        if(!active()||completed>=plan.size())return "Aucune etape restante";
        if(client.player==null)return "Ouvre le monde Minecraft";
        Vec3d eye=client.player.getEyePos();
        boolean nearby=false;
        for(Target t:nextTargets(128)){
            double dx=t.x()-eye.x,dy=t.y()-eye.y,dz=t.z()-eye.z;
            if(dx*dx+dy*dy+dz*dz<=12*12){nearby=true;break;}
        }
        if(!nearby)return "Rapproche-toi de la zone cible (12 blocs)";
        completed++;return "Etape marquee terminee MANUELLEMENT (pose non verifiee)";
    }
    private static int[] mapping;
    private static String sourceMessage="Aucune source Studio associee";

    public static void reset(){boundWorld=null;anchor=null;boundModel=null;plan=null;sourcePlan=null;mapping=null;
        completed=0;mode=Mode.LAYERS;active=false;sourceMessage="";revision++;}
    public static void tick(MinecraftClient client){
        if(active&&(client.world==null||client.world!=boundWorld))reset();
    }
    public static boolean active(){return active&&plan!=null;}
    public static BlockPos anchor(){return anchor;}
    public static int step(){return completed;}
    public static int total(){return plan==null?0:plan.size();}
    public static int partCount(){return boundModel==null?0:boundModel.parts().size();}
    public static int shownCount(){return plan==null?0:plan.revealedParts(completed);}
    public static Mode mode(){return mode;}
    public static String sourceMessage(){return sourceMessage;}
    public static String description(){return plan==null?"Aucune notice active":plan.description();}
    public static String stepTitle(){return plan==null||completed==0?"Aucune piece affichee":plan.at(completed).title();}
    public static boolean sourceAvailable(){return sourcePlan!=null;}

    /** Renderer calls this for every visible projection; unrelated projects retain their old VBOs. */
    public static Filter filter(PreciseDisplayEntity be,PreciseWorldModel model){
        if(!active||be.getWorld()!=boundWorld||!be.getPos().equals(anchor)||mapping==null ||
                model.parts().size()!=mapping.length)return null;
        if(boundModel!=model)boundModel=model; // world edit preserves the part-index order
        return new Filter(revision,mapping,completed,switch(mode){
            case LAYERS->2;case LOTS->4;case SOURCE,PIECES->24;});
    }
    public static List<PreciseDisplayEntity> nearby(MinecraftClient client){
        if(client.world==null||client.player==null)return List.of();
        ArrayList<PreciseDisplayEntity> found=new ArrayList<>(PreciseDisplayRenderer.activeProjections(client.world));
        found.sort(Comparator.comparingDouble(e->client.player.getPos().squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(e.getPos()))));
        return found;
    }
    public static boolean attach(MinecraftClient client,PreciseDisplayEntity entity){
        if(client.world==null||entity==null||entity.model()==null)return false;
        PreciseWorldModel m=entity.model();
        if(active&&boundWorld==client.world&&anchor.equals(entity.getPos())&&boundModel==m)return true;
        boundWorld=client.world;anchor=entity.getPos().toImmutable();boundModel=m;
        mode=Mode.LAYERS;sourcePlan=null;completed=0;active=true;
        loadSource();rebuild();return true;
    }
    public static void loadSource(){
        sourcePlan=null;sourceMessage="Source .io non associee : notices calculees disponibles";
        if(boundModel==null)return;
        Path folder=FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("imports");
        if(!Files.isDirectory(folder))return;
        try(var files=Files.list(folder)){
            List<Path> candidates=files.filter(Files::isRegularFile).filter(p->{
                String s=p.getFileName().toString().toLowerCase(Locale.ROOT);
                return s.endsWith(".io")||s.endsWith(".ldr")||s.endsWith(".mpd");
            }).sorted(Comparator.comparing(p->p.getFileName().toString())).limit(64).toList();
            for(Path candidate:candidates){
                try{
                    BrickGuidePlan resolved=BrickSourceStepReader.inspect(candidate,boundModel.parts());
                    if(resolved.originalSteps()){
                        sourcePlan=resolved;
                        sourceMessage="Etapes Studio retrouvees : "+candidate.getFileName();
                        return;
                    }
                }catch(Exception ignored){/* An unrelated or invalid import is not an error. */}
            }
        }catch(Exception ignored){sourceMessage="Dossier imports non accessible";}
    }
    private static void rebuild(){
        if(boundModel==null)return;
        plan=switch(mode){
            case SOURCE -> sourcePlan!=null?sourcePlan:BrickGuidePlan.layers(boundModel.parts(),8);
            case LAYERS -> BrickGuidePlan.layers(boundModel.parts(),8);
            case LOTS -> BrickGuidePlan.batches(boundModel.parts(),20);
            case PIECES -> BrickGuidePlan.batches(boundModel.parts(),1);
        };
        mapping=plan.mapping();completed=Math.clamp(completed,0,plan.size());revision++;
    }
    public static void setMode(Mode next){
        if(!active)return;
        if(next==Mode.SOURCE&&sourcePlan==null)return;
        if(mode==next)return;
        mode=next;completed=0;rebuild();
    }
    public static void advance(int delta){
        if(!active)return;
        completed=Math.clamp(completed+delta,0,plan.size());
    }
    public static void jump(int next){
        if(!active)return;
        completed=Math.clamp(next,0,plan.size());
    }
    /** Disabling the guide returns ALL original pieces without touching their saved state. */
    public static void hide(){active=false;plan=null;mapping=null;sourcePlan=null;completed=0;revision++;}
    public static List<Material> materials(){
        if(!active||completed==0)return List.of();
        Map<String,Integer> count=new LinkedHashMap<>();
        for(int i:plan.at(completed).partIndexes()){
            var p=boundModel.parts().get(i);
            count.merge(p.partId()+"@"+p.color(),1,Integer::sum);
        }
        ArrayList<Material> result=new ArrayList<>();
        for(var entry:count.entrySet()){
            String key=entry.getKey();int pos=key.lastIndexOf('@');
            result.add(new Material(key.substring(0,pos),Integer.parseInt(key.substring(pos+1)),entry.getValue()));
        }
        result.sort(Comparator.comparingInt(Material::quantity).reversed().thenComparing(Material::part));
        return List.copyOf(result);
    }
    public static String colorName(int code){
        var col=LDrawLibrary.color(code);
        return col==null?"LDraw "+code:col.displayName();
    }
    public static void hud(MinecraftClient client,DrawContext draw){
        if(!active||client.player==null||client.options.hudHidden)return;
        String title="BRICKCRAFT | NOTICE "+(mode==Mode.SOURCE?"STUDIO":mode==Mode.LAYERS?"COUCHES":
                mode==Mode.LOTS?"LOTS":"PIECES")+" | "+completed+" / "+plan.size();
        String detail=shownCount()+" / "+partCount()+" pieces | J: menu | PgUp/PgDn: etape";
        int width=Math.max(client.textRenderer.getWidth(title),client.textRenderer.getWidth(detail))+16;
        int x=Math.max(5,(draw.getScaledWindowWidth()-width)/2),y=6;
        draw.fill(x,y,x+width,y+32,0xDA102638);
        draw.fill(x,y,x+width,y+2,0xFF4DD3DC);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr(title),x+8,y+6,0xFF8BE9E6);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr(detail),x+8,y+18,0xFFF1E0A5);
    }
}
