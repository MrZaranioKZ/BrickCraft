import fr.brickcraft.core.*;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.Path;

public final class ConnectorEngineV08Test {
    static int count=0;
    static void check(boolean b,String msg){count++;if(!b)throw new AssertionError(msg);}
    static BrickPiece piece(BrickKind kind,int x,int y,int z,int a){
        return new BrickPiece(kind,0xCC3344,x,y,z,a/90,a,0,0);
    }
    static void basic(){
        BrickPiece base=piece(BrickKind.BRICK_2X4,10,0,15,0);
        BrickPiece incoming=piece(BrickKind.BRICK_1X2,10,0,15,0);
        check(ConnectorSnapper.connectors(base).size()==16,"base studs + underside sockets");
        check(ConnectorSnapper.connectors(incoming).size()==4,"upper 2 studs + cavities");
        check(ConnectorSnapper.connectors(piece(BrickKind.TILE_1X2,1,0,1,0)).size()==2,"tile only cavities");
        for(int z=15;z<=18;z++){
            BrickPiece result=ConnectorSnapper.snap(incoming,base,10.5,1.2,z+.5,null).piece();
            check(result.y()==6,"supported height, z="+z);
            check(!result.intersects(base),"touching isn't collision");
        }
        var s=ConnectorSnapper.snap(incoming,base,10.5,1.2,15.5,p->false);
        check(!s.snapped(),"predicate must reject");
        BrickPiece tile=piece(BrickKind.TILE_1X2,10,0,15,0);
        check(!ConnectorSnapper.snap(incoming,tile,10.5,.4,15.5,null).snapped(),"smooth tile no top studs");
        check(!ConnectorSnapper.snap(incoming,base,60,1.2,60,null).snapped(),"distant click");
        var rotated=piece(BrickKind.BRICK_2X4,10,0,15,90);
        var points=ConnectorSnapper.connectors(rotated);
        check(points.size()==16,"rotated connectors");
        ConnectorSnapper.Point top=points.stream().filter(v->v.type()==ConnectorSnapper.Type.STUD).findFirst().orElseThrow();
        var snap90=ConnectorSnapper.snap(incoming,rotated,top.x(),top.y(),top.z(),null);
        check(snap90.snapped(),"90-degree connector snap");
        check(snap90.piece().y()==rotated.top(),"rotated result y");
        check(!snap90.piece().intersects(rotated),"rotated result clearance");
        BrickPiece slope=piece(BrickKind.SLOPE_2X2,10,0,15,0);
        check(ConnectorSnapper.connectors(slope).isEmpty(),"no invented slope connectors");
        var ldr=piece(BrickKind.LDRAW_PART,10,0,15,0);
        check(ConnectorSnapper.connectors(ldr).isEmpty(),"unknown LDraw must not be guessed");
        var inverted=base.withPitch(180);
        var studs=ConnectorSnapper.connectors(inverted).stream().filter(v->v.type()==ConnectorSnapper.Type.STUD).toList();
        check(studs.size()==8,"inverted studs preserved");
        check(studs.get(0).ny() < -.98,"inverted studs face downward");
        BrickPiece upside=incoming.withPitch(180);
        ConnectorSnapper.Point joint=studs.get(0);
        var reverse=ConnectorSnapper.snap(upside,inverted,joint.x(),joint.y(),joint.z(),null);
        check(reverse.snapped(),"inverted connection");
        check(!reverse.piece().intersects(inverted),"inverted touching no overlap");
        BrickPiece tilted=base.withAngle(45);
        check(ConnectorSnapper.connectors(tilted).size()==16,"rotated connectors retained; precise matching required");
    }
    public static void main(String[] args){
        basic();
        if(args.length>0){
            LDrawLibrary.init(Path.of(args[0]));
            check(LDrawLibrary.available(),"expected real LDraw library");
            for(String id:new String[]{"3004","3001","3023"}){
                BrickPiece p=new BrickPiece(BrickKind.LDRAW_PART,0xE04020,3,0,4,0,0,0,0,id,-1);
                var points=ConnectorSnapper.connectors(p);
                check(!points.isEmpty(),"LDraw connector "+id);
            }
            BrickPiece unknown=new BrickPiece(BrickKind.LDRAW_PART,0xE04020,3,0,4,0,0,0,0,"4070",-1);
            check(ConnectorSnapper.connectors(unknown).isEmpty(),"unknown metadata omitted");
            LDrawLibrary.close();
        }
        System.out.println("OK "+count+" V0.8 tests : real connectors, rotations, exclusions, precise snap");
    }
}
