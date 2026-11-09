import fr.brickcraft.core.*;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.Path;

public final class InspectorMaterialSnapTest {
    private static int assertions;
    private static void ok(boolean good,String message){assertions++;if(!good)throw new AssertionError(message);}
    private static BrickPiece piece(BrickKind k,int x,int y,int z,int angle){
        return new BrickPiece(k,0xC82D31,x,y,z,Math.floorMod(angle,360)/90,Math.floorMod(angle,360),0,0);
    }
    public static void main(String[] args) {
        int base=0x8066AA22;
        for(String finish:new String[]{"SOLID","CHROME","METAL","PEARLESCENT","MATTE_METALLIC",
                "RUBBER","FABRIC","GLITTER","SPECKLE"}){
            int c=MaterialFinish.shade(base,finish,0,1,0,42);
            ok((c>>>24)==0x80,"alpha changed: "+finish);
            ok(c==MaterialFinish.shade(base,finish,0,1,0,42),"unstable: "+finish);
            ok((c&0xFFFFFF)!=0,"blackout "+finish);
        }
        ok(MaterialFinish.shade(base,"SOLID",0,1,0,42)==base,"solid must be identical");
        ok(MaterialFinish.shade(base,"CHROME",0,1,0,42)!=MaterialFinish.shade(base,"CHROME",0,-1,0,42),"chrome highlights");
        BrickPiece bottom=piece(BrickKind.BRICK_2X4,10,0,20,0);
        BrickPiece upper=piece(BrickKind.BRICK_1X2,10,0,20,0);
        BrickPiece snap=ConnectorSnapper.alignOnTop(upper,bottom,10.5,20.5);
        ok(snap.y()==bottom.top(),"snapped height");
        ok(snap.x()==10 && snap.z()==20,"snapped position");
        ok(!snap.intersects(bottom),"snapped overlaps body");
        BrickPiece tile=piece(BrickKind.TILE_1X2,10,0,20,0);
        ok(ConnectorSnapper.alignOnTop(upper,tile,10.5,20.5).equals(upper),"tile has no top studs");
        BrickPiece rotated=piece(BrickKind.BRICK_2X4,10,0,20,90);
        BrickPiece snap90=ConnectorSnapper.alignOnTop(upper,rotated,11.5,21.5);
        ok(snap90.y()==rotated.top(),"right-angle target snaps");
        BrickPiece diagonal=piece(BrickKind.BRICK_2X4,10,0,20,45);
        ok(ConnectorSnapper.alignOnTop(upper,diagonal,10.5,20.5).equals(upper),"arbitrary angle must not fake studs");
        if(args.length>0){
            LDrawLibrary.init(Path.of(args[0]));
            ok(LDrawLibrary.available(),LDrawLibrary.status());
            ok(LDrawLibrary.colors().size()>=300,"missing October colors");
            LDrawLibrary.Colour coral=LDrawLibrary.color(353);
            ok(coral!=null && coral.displayName().toLowerCase().contains("coral"),"coral palette");
            ok(LDrawLibrary.colors().stream().anyMatch(c->c.finish().equals("CHROME")),"chrome family");
            ok(LDrawLibrary.colors().stream().anyMatch(c->c.alpha()<255),"transparent family");
            ok(LDrawLibrary.colors().stream().anyMatch(c->c.finish().equals("PEARLESCENT")),"pearl family");
            int transparent=LDrawLibrary.argb(LDrawLibrary.colors().stream().filter(c->c.alpha()<255).findFirst().orElseThrow().code(),0);
            ok((transparent>>>24)<255,"LDraw alpha");
            LDrawLibrary.close();
        }
        System.out.println("OK "+assertions+" checks | shader alpha/materials + stud snapping + official palette");
    }
}
