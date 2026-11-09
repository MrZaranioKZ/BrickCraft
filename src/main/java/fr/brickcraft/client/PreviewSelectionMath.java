package fr.brickcraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minecraft-independent math for the imported LDraw preview.
 * Screen projection, hit testing and gizmo locations intentionally share one frame.
 * Hit testing uses oriented part bounding volumes, never the 20-LDU Minecraft grid.
 */
public final class PreviewSelectionMath {
    private PreviewSelectionMath() {}
    public record Point(double x, double y) {}
    public record Frame(double cx,double cy,double cz,double cosine,double sine,
                        double unit,double screenX,double screenY) {
        public Point project(double x,double y,double z) {
            double a=x-cx,b=y-cy,c=z-cz;
            return new Point(screenX+(a*cosine-c*sine)*unit,
                    screenY+((a*sine+c*cosine)*.4+b*.86)*unit);
        }
        public double depth(double x,double y,double z){
            return (x-cx)*sine+(z-cz)*cosine-(y-cy)*.55;
        }
    }
    public static Frame frame(double[] bounds,double orbit,double zoom,double panX,double panY,
                              int x1,int y1,int x2,int y2){
        if(bounds==null||bounds.length!=6)throw new IllegalArgumentException("Bornes requises");
        double cx=(bounds[0]+bounds[3])/2,cy=(bounds[1]+bounds[4])/2,cz=(bounds[2]+bounds[5])/2;
        double sx=Math.max(20,bounds[3]-bounds[0]),sy=Math.max(8,bounds[4]-bounds[1]),sz=Math.max(20,bounds[5]-bounds[2]);
        double co=Math.cos(orbit),si=Math.sin(orbit),boundW=1,boundH=1;
        for(int k=0;k<8;k++){
            double x=((k&1)==0?-sx:sx)/2,y=((k&2)==0?-sy:sy)/2,z=((k&4)==0?-sz:sz)/2;
            boundW=Math.max(boundW,Math.abs(x*co-z*si));
            boundH=Math.max(boundH,Math.abs((x*si+z*co)*.40+y*.86));
        }
        double unit=Math.max(.0001,Math.min((x2-x1-36)/(2*boundW),(y2-y1-36)/(2*boundH))*zoom);
        return new Frame(cx,cy,cz,co,si,unit,(x1+x2)*.5+panX,(y1+y2)*.5+panY);
    }
    public static Point[] corners(Frame f,double[][] corners){
        Point[] points=new Point[8];
        for(int i=0;i<8;i++)points[i]=f.project(corners[i][0],corners[i][1],corners[i][2]);
        return points;
    }
    private static final int[][] FACES={{0,4,5,1},{2,3,7,6},{0,2,6,4},
                                        {1,5,7,3},{0,1,3,2},{4,6,7,5}};
    private static boolean insideTriangle(Point p,Point a,Point b,Point c){
        if(Math.abs(cross(a,b,c))<1e-7)return false;
        double s1=cross(a,b,p),s2=cross(b,c,p),s3=cross(c,a,p);
        return (s1>=-1e-7&&s2>=-1e-7&&s3>=-1e-7)||(s1<=1e-7&&s2<=1e-7&&s3<=1e-7);
    }
    private static double cross(Point a,Point b,Point p){return (b.x-a.x)*(p.y-a.y)-(b.y-a.y)*(p.x-a.x);}
    public static boolean hitBox(Point[] points,double x,double y){
        Point p=new Point(x,y);
        for(int[] f:FACES){
            if(insideTriangle(p,points[f[0]],points[f[1]],points[f[2]]) ||
                    insideTriangle(p,points[f[0]],points[f[2]],points[f[3]]))return true;
        }
        return false;
    }
    public record Candidate(int index,double depth) {}
    public static List<Candidate> hits(Frame frame,List<double[][]> volumes,double mx,double my){
        ArrayList<Candidate> out=new ArrayList<>();
        for(int i=0;i<volumes.size();i++){
            double[][] cube=volumes.get(i);
            if(cube==null||cube.length!=8)continue;
            Point[] projected=corners(frame,cube);
            double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
            double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            for(Point p:projected){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);
                minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
            if(mx<minX-2||mx>maxX+2||my<minY-2||my>maxY+2||!hitBox(projected,mx,my))continue;
            double sx=0,sy=0,sz=0;
            for(double[] v:cube){sx+=v[0];sy+=v[1];sz+=v[2];}
            out.add(new Candidate(i,frame.depth(sx/8,sy/8,sz/8)));
        }
        out.sort(Comparator.comparingDouble(Candidate::depth).reversed());
        return out;
    }
    /** Shift+click rotates through all projected volumes under the pointer. */
    public static int choose(List<Candidate> hits,int current,boolean cycle){
        if(hits.isEmpty())return -1;
        if(cycle){for(int i=0;i<hits.size();i++)if(hits.get(i).index()==current)
            return hits.get((i+1)%hits.size()).index();}
        return hits.get(0).index();
    }
    /** Screen-space axis endpoint for a 3D manipulator in LDraw coordinates. */
    public static Point axisEnd(Frame f,double x,double y,double z,int axis,double ldu){
        return switch(axis){
            case 0->f.project(x+ldu,y,z);
            case 1->f.project(x,y+ldu,z);
            case 2->f.project(x,y,z+ldu);
            default->throw new IllegalArgumentException("Axe invalide");
        };
    }
    /** The drag projection is based on the fixed initial frame, never on a moving camera. */
    public static double axisDrag(Frame frame,double mx,double my,double startX,double startY,
                                  double x,double y,double z,int axis){
        Point from=frame.project(x,y,z),to=axisEnd(frame,x,y,z,axis,20);
        double vx=to.x-from.x,vy=to.y-from.y;
        double squared=vx*vx+vy*vy;
        if(squared<.0001)return 0;
        return ((mx-startX)*vx+(my-startY)*vy)/squared*20;
    }
    public static double snap(double amount,double step){
        if(!Double.isFinite(amount)||!Double.isFinite(step)||step<=0)throw new IllegalArgumentException("Pas invalide");
        return Math.rint(amount/step)*step;
    }
}
