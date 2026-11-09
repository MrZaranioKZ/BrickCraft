package fr.brickcraft.guide;

import fr.brickcraft.importer.LDrawProjectReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preparation for BrickCraft construction guides (not authoritative LEGO instructions).
 * The current BCPX v1 stores flat instances, without original 0 STEP / submodel hierarchy.
 * "Layer" therefore groups approximate part ORIGINS at a plate-height resolution.
 * "Source order" preserves the order of flattened instances, NOT the assembly sequence.
 */
public final class BrickGuidePlanner {
    private BrickGuidePlanner() {}
    public record Group(int number,String label,List<Integer> indexes) {}
    public record Preview(List<Group> layers,List<Group> sourceSteps,int partCount) {}

    public static Preview preview(LDrawProjectReader.Result project,double plateLdu,int maxPiecesPerStep){
        if(!Double.isFinite(plateLdu)||plateLdu<=0||plateLdu>1000)
            throw new IllegalArgumentException("Hauteur de couche invalide");
        if(maxPiecesPerStep<1||maxPiecesPerStep>4096)
            throw new IllegalArgumentException("Taille des etapes invalide");
        Map<Long,List<Integer>> buckets=new LinkedHashMap<>();
        for(int i=0;i<project.parts().size();i++){
            double y=project.parts().get(i).y();
            if(!Double.isFinite(y))throw new IllegalArgumentException("Origine Y non finie");
            long layer=Math.round(y/plateLdu);
            buckets.computeIfAbsent(layer,k->new ArrayList<>()).add(i);
        }
        ArrayList<Group> layers=new ArrayList<>();
        // LDraw Y increases DOWNWARD; descending yields a rough bottom-to-top preview.
        List<Long> sorted=buckets.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        for(long bucket:sorted)
            layers.add(new Group(layers.size()+1,"Plate Y ~ "+bucket+" (LDU "+(bucket*plateLdu)+")",
                    List.copyOf(buckets.get(bucket))));
        ArrayList<Group> source=new ArrayList<>();
        for(int start=0;start<project.parts().size();start+=maxPiecesPerStep){
            ArrayList<Integer> partIndexes=new ArrayList<>();
            for(int i=start;i<Math.min(project.parts().size(),start+maxPiecesPerStep);i++)partIndexes.add(i);
            source.add(new Group(source.size()+1,"Ordre source - lot "+(source.size()+1),List.copyOf(partIndexes)));
        }
        return new Preview(List.copyOf(layers),List.copyOf(source),project.parts().size());
    }
}
