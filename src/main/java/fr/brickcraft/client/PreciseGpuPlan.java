package fr.brickcraft.client;

import java.util.*;

/** Pure-Java spatial batching plan, used to test grouping without starting Minecraft. */
public final class PreciseGpuPlan {
    private PreciseGpuPlan(){}
    public record Cell(int x, int y, int z) {}
    public record Point(double x,double y,double z) {}

    public static Cell cell(double x,double y,double z,int size){
        if(size<1)throw new IllegalArgumentException("size must be positive");
        return new Cell((int)Math.floor(x/size),(int)Math.floor(y/size),(int)Math.floor(z/size));
    }
    public static Map<Cell,List<Integer>> group(List<Point> points,int size){
        Map<Cell,List<Integer>> result=new LinkedHashMap<>();
        for(int i=0;i<points.size();i++){
            Point p=points.get(i);
            result.computeIfAbsent(cell(p.x(),p.y(),p.z(),size),unused->new ArrayList<>()).add(i);
        }
        return result;
    }
    public static boolean behindCamera(double cx,double cy,double cz,double px,double py,double pz,
                                       double fx,double fy,double fz,double enclosingRadius){
        double dx=px-cx,dy=py-cy,dz=pz-cz;
        // Conservative: reject only batches entirely behind the viewer.
        return dx*fx+dy*fy+dz*fz < -Math.max(0,enclosingRadius)-1;
    }
}
