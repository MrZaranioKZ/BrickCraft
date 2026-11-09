import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.world.PreciseMeshCollisionPlan;
import java.util.*;

/** Runs without Fabric/Gradle. Checks closed solids, open LEGO cavities and sloped geometry. */
public class TEST_PHYSIQUE_V012 {
    static final double[] ID={1,0,0,0,1,0,0,0,1};
    static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static final class Mesh {
        final ArrayList<Float> xyz=new ArrayList<>();
        void tri(double[] a,double[] b,double[] c){for(double[] v:new double[][]{a,b,c})for(double value:v)xyz.add((float)value);}
        void quad(double[] a,double[] b,double[] c,double[] d){tri(a,b,c);tri(a,c,d);}
        void box(double x1,double y1,double z1,double x2,double y2,double z2){
            double[] a={x1,y1,z1},b={x2,y1,z1},c={x2,y2,z1},d={x1,y2,z1};quad(a,b,c,d);
            a=new double[]{x1,y1,z2};b=new double[]{x2,y1,z2};c=new double[]{x2,y2,z2};d=new double[]{x1,y2,z2};quad(a,d,c,b);
            quad(new double[]{x1,y1,z1},new double[]{x1,y1,z2},new double[]{x2,y1,z2},new double[]{x2,y1,z1});
            quad(new double[]{x1,y2,z1},new double[]{x2,y2,z1},new double[]{x2,y2,z2},new double[]{x1,y2,z2});
            quad(new double[]{x1,y1,z1},new double[]{x1,y2,z1},new double[]{x1,y2,z2},new double[]{x1,y1,z2});
            quad(new double[]{x2,y1,z1},new double[]{x2,y1,z2},new double[]{x2,y2,z2},new double[]{x2,y2,z1});
        }
        PreciseMeshCollisionPlan.Geometry geometry(){
            float[] vertices=new float[xyz.size()];for(int i=0;i<vertices.length;i++)vertices[i]=xyz.get(i);
            return new PreciseMeshCollisionPlan.Geometry(vertices,new double[]{0,0,0,20,20,20});
        }
    }
    static PreciseMeshCollisionPlan.Result build(String name,Mesh m,double[] matrix){
        return PreciseMeshCollisionPlan.build(List.of(new LDrawProjectReader.Instance(name,4,0,0,0,matrix)),
                -5,-6,-5,0,0,0,id->m.geometry());
    }
    static boolean at(PreciseMeshCollisionPlan.Result r,int x,int y,int z,int sx,int sy,int sz){
        long[] bits=r.masks().get(new PreciseMeshCollisionPlan.Cell(x,y,z));
        return PreciseMeshCollisionPlan.occupied(bits,sx,sy,sz);
    }
    public static void main(String[] args){
        Mesh cube=new Mesh();cube.box(0,0,0,1,1,1);
        var solid=build("cube",cube,ID);
        check(solid.masks().size()==1,"1 cube occupies 1 cell: "+solid.masks().size());
        check(at(solid,5,5,5,4,4,4),"cube interior filled");
        check(PreciseMeshCollisionPlan.occupiedCount(solid.masks().values().iterator().next())==512,"cube filled entirely");
        // Four hollow walls (tube open at top and bottom) = hole remains traversable.
        Mesh tube=new Mesh();
        tube.box(0,0,0,1,1,.125);tube.box(0,0,.875,1,1,1);
        tube.box(0,0,.125,.125,1,.875);tube.box(.875,0,.125,1,1,.875);
        var hole=build("hollow",tube,ID);
        check(at(hole,5,5,5,0,4,4),"tube wall solid");
        check(!at(hole,5,5,5,4,4,4),"hollow center stays air");
        // A wedge: lower left is occupied, upper right outside is air.
        Mesh slope=new Mesh();
        double[] a={0,0,0},b={1,0,0},c={1,0,1},d={0,0,1},e={1,1,0},f={1,1,1};
        slope.quad(a,d,c,b);slope.quad(b,c,f,e);slope.quad(a,b,e,a); // degenerate face intentionally tolerated
        slope.tri(a,e,d);slope.tri(d,e,f);slope.tri(d,f,c);
        var incline=build("wedge",slope,ID);
        check(at(incline,5,5,5,7,2,4),"under slope solid");
        check(!at(incline,5,5,5,0,7,4),"above slope air");
        // Translation/rotation should create a narrow rotated footprint, not an entire AABB.
        double c45=Math.sqrt(.5);
        double[] rotated={c45,0,-c45,0,1,0,c45,0,c45};
        var turned=build("rotated",cube,rotated);
        int active=0;
        for(var mask:turned.masks().values())active+=PreciseMeshCollisionPlan.occupiedCount(mask);
        check(active>0,"rotation preserves collisions");
        check(turned.masks().size()>=2,"rotation spans multiple cells");
        System.out.println("Physique V0.12 : cube plein, cavite ouverte, pente, rotation = OK");
    }
}
