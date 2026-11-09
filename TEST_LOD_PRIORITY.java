import fr.brickcraft.client.PreciseLodPlanner;
import java.util.*;
public final class TEST_LOD_PRIORITY {
    static int checks=0;
    static void test(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    static List<Integer> ids(List<PreciseLodPlanner.Candidate> c){return c.stream().map(PreciseLodPlanner.Candidate::index).toList();}
    public static void main(String[] args){
        var p=PreciseLodPlanner.class;
        var src=List.of(new PreciseLodPlanner.Candidate(0,900),new PreciseLodPlanner.Candidate(1,16),
                new PreciseLodPlanner.Candidate(2,100),new PreciseLodPlanner.Candidate(3,4));
        test(ids(PreciseLodPlanner.nearestFirst(src,1600)).equals(List.of(3,1,2,0)),"near first");
        test(ids(PreciseLodPlanner.nearestFirst(src.reversed(),1600)).equals(List.of(3,1,2,0)),"file order irrelevant");
        test(ids(PreciseLodPlanner.nearestFirst(src,25)).equals(List.of(3,1)),"radius filtering");
        var tied=List.of(new PreciseLodPlanner.Candidate(9,25),new PreciseLodPlanner.Candidate(2,25));
        test(ids(PreciseLodPlanner.nearestFirst(tied,25)).equals(List.of(2,9)),"tie deterministic");
        var invalid=List.of(new PreciseLodPlanner.Candidate(1,Double.NaN),
                new PreciseLodPlanner.Candidate(-1,2),new PreciseLodPlanner.Candidate(3,-2),
                new PreciseLodPlanner.Candidate(5,Double.POSITIVE_INFINITY));
        test(PreciseLodPlanner.nearestFirst(invalid,100).isEmpty(),"bad entries rejected");
        // Simulated triangle budgets: high detail belongs to near parts, not first MPD entry.
        var sorted=PreciseLodPlanner.nearestFirst(src,1600);
        int[] triangleCost={8,6,8,6}; int budget=12;List<Integer> picked=new ArrayList<>();
        for(var c:sorted)if(triangleCost[c.index()]<=budget){picked.add(c.index());budget-=triangleCost[c.index()];}
        test(picked.equals(List.of(3,1)),"nearest pieces claim triangle budget");
        System.out.println("PASS : "+checks+" LOD priority assertions");
    }
}
