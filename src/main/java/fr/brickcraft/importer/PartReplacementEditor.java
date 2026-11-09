package fr.brickcraft.importer;

import java.util.ArrayList;
import java.util.List;

/** Replaces only one exact part ID, preserving all coordinates, matrices and colours. */
public final class PartReplacementEditor {
    private PartReplacementEditor() {}
    public record Change(LDrawProjectReader.Result project,int count) {}
    public static Change replace(LDrawProjectReader.Result project,String source,String target) {
        String from=PartReplacementMatcher.normalize(source);
        String to=PartReplacementMatcher.normalize(target);
        if(from.isBlank() || to.isBlank() || from.equals(to) || to.contains("/") ||
                !to.matches("[a-z0-9_-]{2,60}"))
            throw new IllegalArgumentException("References de remplacement invalides");
        List<LDrawProjectReader.Instance> changed=new ArrayList<>(project.parts().size());
        int count=0;
        for(LDrawProjectReader.Instance p:project.parts()){
            if(PartReplacementMatcher.normalize(p.partId()).equals(from)){
                changed.add(new LDrawProjectReader.Instance(to,p.color(),p.x(),p.y(),p.z(),p.matrix()));
                count++;
            }else changed.add(p);
        }
        List<String> history=new ArrayList<>(project.problems());
        if(count>0 && history.size()<7_900)history.add("Substitution confirmee : "+from+" -> "+to+" ("+count+" pieces)");
        return new Change(new LDrawProjectReader.Result(project.root(),project.submodels(),changed,history),count);
    }
}
