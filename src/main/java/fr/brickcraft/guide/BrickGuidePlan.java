package fr.brickcraft.guide;

import fr.brickcraft.importer.LDrawProjectReader;
import java.util.*;

/** Immutable indexed guide; zero pieces are hidden when visibleCount=0. Pure Java, no client dependency. */
public final class BrickGuidePlan {
    public record Step(String title,List<Integer> partIndexes) {
        public Step {partIndexes=List.copyOf(partIndexes);}
    }
    private final List<Step> steps;
    private final int[] stepByPart;
    private final int[] prefixCount;
    private final String description;
    private final boolean originalSteps;

    public BrickGuidePlan(int partCount,List<Step> groups,String description,boolean originalSteps){
        if(partCount<1||partCount>250_000 || groups.isEmpty()||groups.size()>200_000)
            throw new IllegalArgumentException("Notice vide ou trop grande");
        this.steps=List.copyOf(groups);this.description=description;this.originalSteps=originalSteps;
        stepByPart=new int[partCount];Arrays.fill(stepByPart,-1);
        prefixCount=new int[groups.size()+1];
        for(int step=0;step<groups.size();step++){
            Step group=groups.get(step);
            if(group.partIndexes().isEmpty())throw new IllegalArgumentException("Etape vide");
            for(int index:group.partIndexes()){
                if(index<0||index>=partCount || stepByPart[index]>=0)
                    throw new IllegalArgumentException("Index de notice absent ou repete : "+index);
                stepByPart[index]=step;
            }
            prefixCount[step+1]=prefixCount[step]+group.partIndexes().size();
        }
        for(int idx:stepByPart)if(idx<0)throw new IllegalArgumentException("Piece manquante dans la notice");
    }
    public List<Step> steps(){return steps;}
    public int size(){return steps.size();}
    public int partCount(){return stepByPart.length;}
    public int[] mapping(){return stepByPart.clone();}
    public int stepFor(int index){return stepByPart[index];}
    public int revealedParts(int completedSteps){return prefixCount[Math.clamp(completedSteps,0,size())];}
    public boolean originalSteps(){return originalSteps;}
    public String description(){return description;}
    public Step at(int oneBased){return steps.get(Math.clamp(oneBased,1,size())-1);}

    /** LDraw Y points down. Approximate layers are sorted from lowest to highest. */
    public static BrickGuidePlan layers(List<LDrawProjectReader.Instance> parts,double plateLdu){
        if(!Double.isFinite(plateLdu)||plateLdu<=0||plateLdu>1000)throw new IllegalArgumentException("Pas invalide");
        TreeMap<Long,List<Integer>> buckets=new TreeMap<>(Comparator.reverseOrder());
        for(int i=0;i<parts.size();i++){
            double y=parts.get(i).y();if(!Double.isFinite(y))throw new IllegalArgumentException("Y invalide");
            buckets.computeIfAbsent(Math.round(y/plateLdu),k->new ArrayList<>()).add(i);
        }
        ArrayList<Step> groups=new ArrayList<>();
        for(var e:buckets.entrySet())groups.add(new Step("Couche "+(groups.size()+1)+" (Y ~ "+
                Math.round(e.getKey()*plateLdu)+" LDU)",e.getValue()));
        return new BrickGuidePlan(parts.size(),groups,"Couches calculees par origine (approximation)",false);
    }
    /** Piece-by-piece or several pieces at once, bottom-to-top, preserving source order inside each layer. */
    public static BrickGuidePlan batches(List<LDrawProjectReader.Instance> parts,int batchSize){
        if(batchSize<1||batchSize>4096)throw new IllegalArgumentException("Lot invalide");
        BrickGuidePlan layers=layers(parts,8);
        ArrayList<Integer> ordered=new ArrayList<>();
        for(Step s:layers.steps())ordered.addAll(s.partIndexes());
        ArrayList<Step> groups=new ArrayList<>();
        for(int i=0;i<ordered.size();i+=batchSize)
            groups.add(new Step((batchSize==1?"Piece ":"Lot ")+(groups.size()+1),
                    ordered.subList(i,Math.min(ordered.size(),i+batchSize))));
        return new BrickGuidePlan(parts.size(),groups,
                batchSize==1?"Piece par piece (ordre calcule)":"Lots de "+batchSize+" pieces (ordre calcule)",false);
    }
}
