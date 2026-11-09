package fr.brickcraft.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Geometry-first connector registry for V0.8. No false positive "sockets" on slopes,
 * axles or arbitrary LDraw meshes: only named, validated rectangular designs and the
 * native parametric bricks have connector locations.
 *
 * Positions are in MC block units, normals are in world coordinates. A LEGO stud
 * can mate with a socket when their normals face in opposite directions. This also
 * supports inverted bricks, rotated plates and sideways attachment where representable.
 *
 * IMPORTANT: the existing BrickPiece format has integer stud coordinates and 1/5-block
 * vertical coordinates. Off-grid connections are deliberately NOT approximated.
 */
public final class ConnectorSnapper {
    private ConnectorSnapper() {}
    public enum Type { STUD, SOCKET }
    public record Point(Type type, double x, double y, double z, double nx, double ny, double nz) {}
    public record Result(BrickPiece piece, boolean snapped, int matchedConnectors) {}
    private record Profile(int width,int depth,boolean topStuds) {}

    // Verified rectangular LDraw parts. Descriptions/geometry vary, so the dimensions
    // are checked against the loaded LDraw mesh before any connector is generated.
    private static final Map<String,Profile> LDRAW = Map.ofEntries(
        Map.entry("3005",new Profile(1,1,true)),Map.entry("3004",new Profile(2,1,true)),
        Map.entry("3622",new Profile(3,1,true)),Map.entry("3010",new Profile(4,1,true)),
        Map.entry("3009",new Profile(6,1,true)),Map.entry("3003",new Profile(2,2,true)),
        Map.entry("3002",new Profile(3,2,true)),Map.entry("3001",new Profile(4,2,true)),
        Map.entry("2456",new Profile(6,2,true)),Map.entry("3007",new Profile(8,2,true)),
        Map.entry("3024",new Profile(1,1,true)),Map.entry("3023",new Profile(2,1,true)),
        Map.entry("3623",new Profile(3,1,true)),Map.entry("3710",new Profile(4,1,true)),
        Map.entry("3022",new Profile(2,2,true)),Map.entry("3021",new Profile(3,2,true)),
        Map.entry("3020",new Profile(4,2,true)),
        Map.entry("3070b",new Profile(1,1,false)),Map.entry("3069b",new Profile(2,1,false)),
        Map.entry("3068b",new Profile(2,2,false)));

    private static Profile profile(BrickPiece p) {
        if(p.kind()==null || p.kind().slope)return null;
        if(p.kind()!=BrickKind.LDRAW_PART)
            return new Profile(p.kind().width,p.kind().length,p.kind().studs);
        if(p.partId()==null)return null;
        Profile known=LDRAW.get(p.partId().toLowerCase(Locale.ROOT));
        if(known==null)return null;
        // Refuse mis-identified or incompatible mesh dimensions.
        if(Math.abs(p.bodyWidth()-known.width())>0.30 ||
           Math.abs(p.bodyDepth()-known.depth())>0.30)return null;
        return known;
    }

    public static List<Point> connectors(BrickPiece p) {
        Profile shape=profile(p);
        if(shape==null)return List.of();
        int max=Math.min(shape.width()*shape.depth(),256);
        ArrayList<Point> result=new ArrayList<>(max*2);
        double[] center=p.center();double[][] axes=p.axes();
        for(int i=0,produced=0;i<shape.width() && produced<max;i++)
            for(int j=0;j<shape.depth() && produced<max;j++,produced++) {
                double u=i+.5-p.bodyWidth()/2.0, v=j+.5-p.bodyDepth()/2.0;
                // Bottom socket is supported even on a smooth tile.
                result.add(point(Type.SOCKET,center,axes,u,-p.bodyHeight()/2.0,v,-1));
                if(shape.topStuds())
                    result.add(point(Type.STUD,center,axes,u,p.bodyHeight()/2.0,v,1));
            }
        return List.copyOf(result);
    }
    private static Point point(Type type,double[] c,double[][] a,double u,double h,double v,int sign){
        return new Point(type,c[0]+u*a[0][0]+h*a[1][0]+v*a[2][0],
            c[1]+u*a[0][1]+h*a[1][1]+v*a[2][1],
            c[2]+u*a[0][2]+h*a[1][2]+v*a[2][2],
            sign*a[1][0],sign*a[1][1],sign*a[1][2]);
    }
    private static boolean compatible(Point a,Point b){
        return a.type()!=b.type() &&
            a.nx()*b.nx()+a.ny()*b.ny()+a.nz()*b.nz() < -.98;
    }
    private static double distance(Point p,double x,double y,double z){
        return Math.sqrt(sq(p.x()-x)+sq(p.y()-y)+sq(p.z()-z));
    }
    private static double sq(double v){return v*v;}

    /**
     * Try all actual connector pairs near the cursor. Coordinate quantization is exact:
     * if a connection would require a fractional X/Z anchor or sub-plate Y anchor,
     * return the original piece unchanged (free placement remains available).
     *
     * The predicate represents collision, border and permission checks. With a null
     * predicate this method only tests direct overlap with the support piece.
     */
    public static Result snap(BrickPiece incoming,BrickPiece support,
                              double clickX,double clickY,double clickZ,
                              Predicate<BrickPiece> allowed) {
        if(incoming==null || support==null || !Double.isFinite(clickX) ||
                !Double.isFinite(clickY) || !Double.isFinite(clickZ))
            return new Result(incoming,false,0);
        List<Point> supports=connectors(support),targets=connectors(incoming);
        if(supports.isEmpty()||targets.isEmpty())return new Result(incoming,false,0);
        BrickPiece bestPiece=incoming;double best=Double.POSITIVE_INFINITY;
        int matches=0;
        for(Point a:supports){
            double fromClick=distance(a,clickX,clickY,clickZ);
            if(fromClick>0.88 || Math.abs(a.y()-clickY)>0.18)continue;
            for(Point b:targets){
                if(!compatible(a,b))continue;
                double rawX=a.x()-b.x(),rawZ=a.z()-b.z(),rawY=(a.y()-b.y())*5;
                int deltaX=(int)Math.round(rawX),deltaZ=(int)Math.round(rawZ),deltaY=(int)Math.round(rawY);
                if(Math.abs(rawX-deltaX)>0.065 || Math.abs(rawZ-deltaZ)>0.065 ||
                   Math.abs(rawY-deltaY)>.24)continue;
                if(Math.abs(deltaX)>64 || Math.abs(deltaZ)>64 || Math.abs(deltaY)>640)continue;
                BrickPiece moved=incoming.withPosition(incoming.x()+deltaX,
                        incoming.y()+deltaY,incoming.z()+deltaZ);
                if(moved.intersects(support) || allowed!=null&&!allowed.test(moved))continue;
                double cost=fromClick*10 + Math.hypot(deltaX,deltaZ)*.12 + Math.abs(deltaY)*.009;
                // Prefer the connector(s) closest to click without jumping far away.
                if(cost<best){best=cost;bestPiece=moved;matches=1;}
            }
        }
        return new Result(bestPiece,matches>0,matches);
    }

    /** V0.7 compatibility: now uses mating sockets and 3D normal checks. */
    public static BrickPiece alignOnTop(BrickPiece target,BrickPiece support,double clickX,double clickZ){
        Result result=snap(target,support,clickX,support.center()[1]+support.bodyHeight()/2.,clickZ,null);
        return result.piece();
    }
}
