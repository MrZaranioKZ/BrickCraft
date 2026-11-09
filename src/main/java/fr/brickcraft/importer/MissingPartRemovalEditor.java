package fr.brickcraft.importer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes confirmed missing references ONLY from an in-memory precise project.
 * The original .io/.ldr/.mpd file is never opened or changed by this editor.
 */
public final class MissingPartRemovalEditor {
    private MissingPartRemovalEditor() {}

    public record Change(LDrawProjectReader.Result project, int count, int references) {}

    /** Remove every occurrence of one reference (case and .dat suffix insensitive). */
    public static Change removeReference(LDrawProjectReader.Result project, String source) {
        String id=PartReplacementMatcher.normalize(source);
        if(id.isBlank())return new Change(project,0,0);
        return removeReferences(project,Set.of(id));
    }

    /** Remove every occurrence matching any of the explicitly provided missing IDs. */
    public static Change removeReferences(LDrawProjectReader.Result project, Set<String> sources) {
        if(sources==null || sources.isEmpty())return new Change(project,0,0);
        Set<String> ids=new HashSet<>();
        for(String source:sources){
            String normalized=PartReplacementMatcher.normalize(source);
            if(!normalized.isBlank())ids.add(normalized);
        }
        if(ids.isEmpty())return new Change(project,0,0);

        List<LDrawProjectReader.Instance> kept=new ArrayList<>(project.parts().size());
        Set<String> actuallyRemoved=new HashSet<>();
        int removed=0;
        for(var piece:project.parts()){
            String id=PartReplacementMatcher.normalize(piece.partId());
            if(ids.contains(id)){
                removed++;
                actuallyRemoved.add(id);
            }else kept.add(piece);
        }
        if(removed==0)return new Change(project,0,0);
        List<String> history=new ArrayList<>(project.problems());
        if(history.size()<7_900){
            // Keep this compact: enormous missing-part lists must not flood BCPX history.
            history.add("Suppression confirmee des pieces manquantes : "+removed+
                    " occurrences / "+actuallyRemoved.size()+" references");
        }
        return new Change(new LDrawProjectReader.Result(project.root(),project.submodels(),kept,history),
                removed,actuallyRemoved.size());
    }
}
