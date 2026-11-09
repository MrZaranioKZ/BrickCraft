import fr.brickcraft.client.PreciseGpuPlan;
import java.util.*;
/** Run without Minecraft/Gradle: checks spatial batching and conservative behind-camera culling. */
public final class GpuBatchPlanTest {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        check(PreciseGpuPlan.cell(0,0,0,12).equals(new PreciseGpuPlan.Cell(0,0,0)),"origin");
        check(PreciseGpuPlan.cell(11.999,0,0,12).x()==0,"positive border");
        check(PreciseGpuPlan.cell(12,0,0,12).x()==1,"next section");
        check(PreciseGpuPlan.cell(-.001,0,0,12).x()==-1,"negative coordinate");
        check(PreciseGpuPlan.cell(-12,0,0,12).x()==-1,"negative border");
        List<PreciseGpuPlan.Point> points=List.of(
            new PreciseGpuPlan.Point(2,1,3),new PreciseGpuPlan.Point(4,2,9),
            new PreciseGpuPlan.Point(25,2,0),new PreciseGpuPlan.Point(-4,0,-4));
        var groups=PreciseGpuPlan.group(points,12);
        check(groups.size()==3,"number of sections");
        int total=groups.values().stream().mapToInt(List::size).sum();
        check(total==points.size(),"no missing entries");
        check(groups.get(new PreciseGpuPlan.Cell(0,0,0)).equals(List.of(0,1)),"correct grouping");
        check(PreciseGpuPlan.behindCamera(0,0,0,0,0,10,0,0,-1,1),"clearly behind");
        check(!PreciseGpuPlan.behindCamera(0,0,0,0,0,-10,0,0,-1,1),"in front");
        check(!PreciseGpuPlan.behindCamera(0,0,0,0,0,0,0,0,-1,3),"overlapping eye");
        System.out.println("GPU batch planner PASS : "+checks+" assertions");
    }
}
