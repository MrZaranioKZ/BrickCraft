package fr.brickcraft.client;

import java.util.*;

/** Pure nearest-first priority selection for BrickCraft's precise projection renderer. */
public final class PreciseLodPlanner {
    private PreciseLodPlanner(){}
    public record Candidate(int index,double distanceSq) {}

    /** Includes every candidate within the visible detail radius, nearest first.
     * Index is only a deterministic tie-breaker; source order must not control priority.
     */
    public static List<Candidate> nearestFirst(Collection<Candidate> candidates,double radiusSq){
        ArrayList<Candidate> result=new ArrayList<>();
        if(!Double.isFinite(radiusSq)||radiusSq<0)return result;
        for(Candidate c:candidates){
            if(c.index()>=0&&Double.isFinite(c.distanceSq())&&c.distanceSq()>=0&&c.distanceSq()<=radiusSq)
                result.add(c);
        }
        result.sort(Comparator.comparingDouble(Candidate::distanceSq).thenComparingInt(Candidate::index));
        return result;
    }
}
