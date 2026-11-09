package fr.brickcraft.client;

import fr.brickcraft.importer.PreciseReferenceMapper;
import fr.brickcraft.importer.PreciseWorldModel;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.net.PreciseWorldEditPayload;
import fr.brickcraft.world.PreciseDisplayEntity;
import fr.brickcraft.world.PreciseWorldEditMath;
import fr.brickcraft.world.PreciseStudSnapper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/**
 * Direct-in-world manipulation. F4 toggles editor. The mouse ray selects ORIGINAL
 * oriented LEGO volumes rather than Minecraft blocks. Axis + wheel commits a small,
 * server-authorized LDU / matrix delta, persisted in the projection block entity.
 */
public final class PreciseWorldEditor {
    private PreciseWorldEditor(){}
    private static boolean enabled,rotation,snapEnabled,showConnectors;
    private static boolean dragging,dragRotation,haveCursor;
    private static int dragAxis;
    private static double dragRaw,dragAmount,lastX,lastY,dragDistance;
    private static double[] dragFacing;
    private static int axis=0,index=-1;
    private static BlockPos anchor;
    private static String status="";
    public static boolean enabled(){return enabled;}
    public static void tick(MinecraftClient client){
        if(dragging&&(client.player==null||client.world==null||client.currentScreen!=null))clearDrag();
    }
    private static void clearDrag(){dragging=false;haveCursor=false;dragRaw=dragAmount=0;dragFacing=null;}
    public static void reset(){clearDrag();enabled=false;index=-1;anchor=null;rotation=false;snapEnabled=false;showConnectors=false;status="";}
    public static boolean toggle(MinecraftClient client){
        clearDrag();enabled=!enabled;index=-1;anchor=null;rotation=false;axis=0;
        status=enabled?BrickI18n.tr("Clic gauche : choisir une piece LEGO. F4 : quitter."):"";
        if(client.player!=null)client.player.sendMessage(Text.literal(BrickI18n.tr(enabled?"BrickCraft : edition en monde ACTIVE":"BrickCraft : edition en monde DESACTIVEE")),true);
        return enabled;
    }
    private static PreciseDisplayEntity selected(MinecraftClient client){
        if(anchor==null||index<0||client.world==null)return null;
        if(!(client.world.getBlockEntity(anchor) instanceof PreciseDisplayEntity be))return null;
        return be.model()!=null&&index<be.model().parts().size()?be:null;
    }
    private static double[] bounds(String part){
        String mapped=PreciseReferenceMapper.geometryId(part);
        return mapped==null?null:LDrawLibrary.originalBounds(mapped);
    }
    private record Selection(BlockPos anchor,WorldPiecePicker.Hit hit){}
    public static boolean attack(MinecraftClient client){
        if(dragging)return true;
        if(!enabled||client.currentScreen!=null||client.player==null||client.world==null)return false;
        Vec3d from=client.player.getEyePos(),dir=client.player.getRotationVec(1.0f);
        ArrayList<Selection> hits=new ArrayList<>();
        for(PreciseDisplayEntity be:PreciseDisplayRenderer.activeProjections(client.world)){
            var model=be.model();if(model==null)continue;
            BlockPos a=be.getPos();
            for(var hit:WorldPiecePicker.hits(model.parts(),model.minX(),model.minY(),model.minZ(),
                    a.getX(),a.getY(),a.getZ(),from.x,from.y,from.z,dir.x,dir.y,dir.z,12.0,
                    PreciseWorldEditor::bounds))hits.add(new Selection(a,hit));
        }
        hits.sort(Comparator.comparingDouble(s->s.hit().distance()));
        if(hits.isEmpty()){
            index=-1;anchor=null;status="Aucune piece sous le viseur (portee : 12 blocs).";
        }else{
            int found=0;
            if(client.player.isSneaking()&&anchor!=null){
                for(int i=0;i<hits.size();i++)if(anchor.equals(hits.get(i).anchor()) && index==hits.get(i).hit().index()){
                    found=(i+1)%hits.size();break;
                }
            }
            Selection chosen=hits.get(found);
            index=chosen.hit().index();anchor=chosen.anchor();
            PreciseDisplayEntity be=selected(client);
            status=BrickI18n.fmt("brickcraft.v241.world.part",index+1,be==null?"?":be.model().parts().get(index).partId());
        }
        return true; // Cancel vanilla punching / breaking while editing a virtual assembly.
    }
    /** N toggles intelligent mating only while F4 world editing is enabled. */
    public static boolean toggleSnap(){
        if(!enabled)return false;
        if(dragging)return true;
        snapEnabled=!snapEnabled;
        status=snapEnabled?"Aimant ON : les deplacements essaient un tenon compatible.":
                           "Aimant OFF : placement totalement libre.";
        return true;
    }
    /** B requests a server-authoritative snap of the currently selected brick. */
    public static boolean snapNow(){
        if(!enabled)return false;
        if(dragging)return true;
        send(PreciseWorldEditMath.SNAP_NOW,0);
        return true;
    }
    public static boolean modeKey(boolean rotate){
        if(!enabled)return false;if(dragging)return true;rotation=rotate;
        status=rotate?"Rotation : choisis X/Y/Z, puis molette.":"Deplacement : choisis X/Y/Z, puis molette.";
        return true;
    }
    public static boolean chooseAxis(int next){
        if(!enabled)return false;if(dragging)return true;axis=next;
        status=BrickI18n.fmt("brickcraft.v241.world.axis","XYZ".charAt(axis),BrickI18n.tr(rotation?"rotation":"deplacement"));return true;
    }
    public static boolean resetRotation(){
        if(!enabled)return false;if(dragging)return true;
        send(PreciseWorldEditMath.RESET,0);return true;
    }
    public static boolean undo(){if(!enabled)return false;if(dragging)return true;send(PreciseWorldEditMath.UNDO,0);return true;}
    private static void send(byte operation,double amount){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||client.currentScreen!=null)return;
        PreciseDisplayEntity be=selected(client);
        if(be==null){status="Selectionne d'abord une piece (clic gauche).";return;}
        if(!client.player.isCreative()){status="Mode Creatif requis pour modifier le monde.";return;}
        ClientPlayNetworking.send(new PreciseWorldEditPayload(anchor,index,operation,(byte)axis,amount));
        status=BrickI18n.fmt("brickcraft.v241.world.operation",index+1,
                BrickI18n.tr(operation==PreciseWorldEditMath.SNAP_NOW?"recherche d'attache...":
                operation==PreciseWorldEditMath.MOVE_WITH_SNAP?"deplacement + aimant":
                operation==PreciseWorldEditMath.MOVE?"deplacement":"rotation"),
                "XYZ".charAt(axis),operation==PreciseWorldEditMath.RESET?BrickI18n.tr("reset"):amount);
    }
    public static boolean scroll(double vertical){
        if(!enabled)return false;
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.currentScreen!=null||client.player==null)return false;
        if(vertical==0||dragging)return true;
        boolean fine=client.player.isSneaking();
        double step=rotation?(fine?1:15):(fine?1:20);
        send(rotation?PreciseWorldEditMath.ROTATE:
                (snapEnabled?PreciseWorldEditMath.MOVE_WITH_SNAP:PreciseWorldEditMath.MOVE),
                Math.signum(vertical)*step);
        return true; // Prevent hotbar scrolling while the world editor is active.
    }
    /** K: connector markers are OFF by default. Only the selected known part is inspected. */
    public static boolean toggleConnectors(){
        if(!enabled)return false;
        showConnectors=!showConnectors;
        status=BrickI18n.fmt("brickcraft.v241.world.connectors",BrickI18n.tr(showConnectors?"VISIBLES":"MASQUES"));
        return true;
    }
    private static double[] originWorld(PreciseDisplayEntity be,
              fr.brickcraft.importer.LDrawProjectReader.Instance p){
        PreciseWorldModel model=be.model();BlockPos a=be.getPos();
        return new double[]{a.getX()+p.x()/20-model.minX(),
                a.getY()-p.y()/20-model.minY(),a.getZ()+p.z()/20-model.minZ()};
    }
    /** Handle hit-testing is independent of the 3,245 meshes of an imported castle.
     * Return true ONLY when the handle is actually hit, leaving ordinary block selection intact.
     */
    public static boolean mouseButton(int button,int action){
        if(!enabled||button!=0)return false;
        MinecraftClient client=MinecraftClient.getInstance();
        if(action==0&&dragging){
            double finalAmount=dragAmount;
            boolean rotate=dragRotation;
            int targetAxis=dragAxis;
            clearDrag();
            axis=targetAxis;rotation=rotate;
            if(finalAmount!=0)send(rotate?PreciseWorldEditMath.ROTATE:
                    (snapEnabled?PreciseWorldEditMath.MOVE_WITH_SNAP:PreciseWorldEditMath.MOVE),finalAmount);
            else status="Glissement annule (aucune modification).";
            return true;
        }
        if(action!=1||dragging||client.currentScreen!=null||client.player==null||
                client.world==null||!client.player.isCreative()||client.player.isSneaking())return false;
        PreciseDisplayEntity be=selected(client);if(be==null)return false;
        var p=be.model().parts().get(index);
        Vec3d eye=client.player.getEyePos(),facing=client.player.getRotationVec(1.0f);
        double[] center=originWorld(be,p);
        var hit=WorldGizmoMath.pick(new double[]{eye.x,eye.y,eye.z},
                 new double[]{facing.x,facing.y,facing.z},center,12.0,0.16);
        if(hit==null)return false;
        dragAxis=hit.axis();axis=dragAxis;dragRotation=rotation;
        dragDistance=Math.sqrt(Math.pow(center[0]-eye.x,2)+Math.pow(center[1]-eye.y,2)+Math.pow(center[2]-eye.z,2));
        dragFacing=new double[]{facing.x,facing.y,facing.z};
        dragRaw=dragAmount=0;haveCursor=false;dragging=true;
        status="Poignee "+"XYZ".charAt(axis)+" : maintiens et glisse, relache pour enregistrer.";
        return true;
    }
    /** Called before vanilla mouse-look, preventing camera rotation while dragging. */
    public static boolean mouseMove(double x,double y){
        if(!dragging)return false;
        if(!haveCursor){lastX=x;lastY=y;haveCursor=true;return true;}
        double dx=x-lastX,dy=y-lastY;lastX=x;lastY=y;
        if(Math.abs(dx)+Math.abs(dy)>500)return true; // Unexpected capture / focus jump.
        MinecraftClient client=MinecraftClient.getInstance();
        boolean fine=client.player!=null&&client.player.isSneaking();
        dragRaw+=dragRotation?(dx-dy)*0.65:
                WorldGizmoMath.dragLdu(dx,dy,dragFacing,dragAxis,dragDistance);
        dragAmount=WorldGizmoMath.step(dragRaw,dragRotation?(fine?1:5):(fine?1:5),dragRotation?180:80);
        return true;
    }
    private static fr.brickcraft.importer.LDrawProjectReader.Instance dragPreview(
            fr.brickcraft.importer.LDrawProjectReader.Instance p){
        if(!dragging||dragAmount==0)return p;
        try{return PreciseWorldEditMath.apply(p,dragRotation?PreciseWorldEditMath.ROTATE:
                PreciseWorldEditMath.MOVE,dragAxis,dragAmount);}
        catch(IllegalArgumentException ex){return p;}
    }
    public static void renderHud(DrawContext d,MinecraftClient client){
        if(!enabled||client.player==null||client.currentScreen!=null)return;
        int height=d.getScaledWindowHeight(),width=d.getScaledWindowWidth();
        int py=Math.max(8,height-70),px=8;
        String one=BrickI18n.fmt("brickcraft.v241.hud.title",
                BrickI18n.tr(rotation?"ROTATION":"DEPLACEMENT"),"XYZ".charAt(axis),
                snapEnabled?"ON":"OFF");
        String two=BrickI18n.tr("Clic : selection | Poignees : glisser | G/R : mode | X/Y/Z : axe");
        String three=dragging?BrickI18n.fmt("brickcraft.v241.hud.drag", "XYZ".charAt(dragAxis),
                dragAmount,dragRotation?"°":"LDU"):
                BrickI18n.tr("Molette : avancer | K : tenons | N : aimant | B : connecter");
        String selected=anchor==null?status:"#"+(index+1)+" @ "+anchor.toShortString()+" | "+status;
        String[] lines={one,two,three,BrickI18n.tr(selected)};
        int maxWidth=Math.max(80,width-22),textWidth=0;
        for(int i=0;i<lines.length;i++){
            lines[i]=client.textRenderer.trimToWidth(lines[i],maxWidth);
            textWidth=Math.max(textWidth,client.textRenderer.getWidth(lines[i]));
        }
        int w=Math.min(maxWidth,textWidth+10);
        d.fill(px-4,py-4,px+w,py+49,0xDD0A2534);
        for(int i=0;i<lines.length;i++)
            d.drawTextWithShadow(client.textRenderer,lines[i],px,py+i*12,
                    i==0?0xFF5CE3DF:i==2?0xFFEDC98E:0xFFDBF4FA);
    }
    /** Selection outline in world coordinates, including rotations. No 3D mesh allocations. */
    public static void initOverlay(){
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            if(!enabled)return;
            MinecraftClient client=MinecraftClient.getInstance();
            PreciseDisplayEntity be=selected(client);
            if(be==null||context.consumers()==null||context.matrixStack()==null)return;
            PreciseWorldModel model=be.model();
            var initial=model.parts().get(index);
            var p=dragPreview(initial);double[] b=bounds(p.partId());if(b==null)return;
            BlockPos a=be.getPos();
            double[][] corners=WorldPiecePicker.corners(p,b,model.minX(),model.minY(),model.minZ(),
                    a.getX(),a.getY(),a.getZ());
            MatrixStack matrices=context.matrixStack();Vec3d cam=context.camera().getPos();
            matrices.push();matrices.translate(-cam.x,-cam.y,-cam.z);
            VertexConsumer lines=context.consumers().getBuffer(RenderLayer.getLines());
            for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)
                line(lines,matrices,corners[i],corners[i|bit],0xFFF7D67B);
            // LDraw X/Z positive world axes, LDraw Y positive points down in Minecraft.
            double[] center=originWorld(be,p);
            double ox=center[0],oy=center[1],oz=center[2];
            line(lines,matrices,center,new double[]{ox+1.2,oy,oz},0xFFFF6666);
            line(lines,matrices,center,new double[]{ox,oy+1.2,oz},0xFF77FFAA);
            line(lines,matrices,center,new double[]{ox,oy,oz+1.2},0xFF66BFFF);
            if(showConnectors){
                // Minimal GPU cost: at most 96 crosshair markers, no solid meshes or allocations per part.
                for(var pt:PreciseStudSnapper.connectorPoints(p,96)){
                    double x=a.getX()+pt.x()/20-model.minX();
                    double y=a.getY()-pt.y()/20-model.minY();
                    double z=a.getZ()+pt.z()/20-model.minZ();
                    int color=pt.top()?0xFF63F4A9:0xFF74B6FF;
                    double size=0.09;
                    line(lines,matrices,new double[]{x-size,y,z},new double[]{x+size,y,z},color);
                    line(lines,matrices,new double[]{x,y-size,z},new double[]{x,y+size,z},color);
                    line(lines,matrices,new double[]{x,y,z-size},new double[]{x,y,z+size},color);
                }
            }
            matrices.pop();
        });
    }
    private static void line(VertexConsumer lines,MatrixStack stack,double[] a,double[] b,int c){
        var m=stack.peek().getPositionMatrix();
        lines.vertex(m,(float)a[0],(float)a[1],(float)a[2]).color(c).normal(0,1,0);
        lines.vertex(m,(float)b[0],(float)b[1],(float)b[2]).color(c).normal(0,1,0);
    }
}
