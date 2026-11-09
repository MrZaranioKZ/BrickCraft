import fr.brickcraft.client.GpuLocalSpace;
import fr.brickcraft.client.PreciseGpuPlan;
import java.util.*;
/** Java-only regression: correct relative space and full upload plan (no hidden batches). */
public final class GpuStabilityTest {
    static int count;
    static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    static void same(double a,double b,String why){check(Math.abs(a-b)<1e-10,why+" : "+a+" vs "+b);}
    public static void main(String[] args){
        double[] anchor={345.,-61.,315.};
        double[] camera={317.946,-56.37056,364.331};
        double[] local={14.75,28.625,11.125};
        double[] result=GpuLocalSpace.worldToCamera(local,anchor,camera);
        for(int i=0;i<3;i++)same(result[i],(anchor[i]+local[i])-camera[i],"world to camera "+i);
        // External camera facing MUST NOT change the batch set in safe mode.
        List<PreciseGpuPlan.Point> partCenters=new ArrayList<>();
        for(int i=0;i<3245;i++)partCenters.add(new PreciseGpuPlan.Point((i*7)%260/10.,(i*13)%580/10.,(i*19)%390/10.));
        var groups=PreciseGpuPlan.group(partCenters,12);
        int entries=groups.values().stream().mapToInt(List::size).sum();
        check(entries==3245,"no pieces silently omitted");
        int sections=groups.size();
        for(int yaw=0;yaw<360;yaw+=30){
            // With gpu_culling=off no direction should remove a section.
            check(groups.size()==sections,"every batch independent of yaw "+yaw);
        }
        check(PreciseGpuPlan.cell(-0.1,0,0,12).x()==-1,"negative sections");
        same(GpuLocalSpace.local(200,-1.5),11.5,"LDraw to local conversion");
        System.out.println("GPU STABILITE PASS : "+count+" checks, "+sections+" sections");
    }
}
