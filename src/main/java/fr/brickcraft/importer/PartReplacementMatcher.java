package fr.brickcraft.importer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Offline, conservative suggestions for missing LDraw part references.
 * A close reference is only a HINT: variants may differ in geometry, print,
 * thickness or connectivity. This class never replaces parts automatically.
 */
public final class PartReplacementMatcher {
    private PartReplacementMatcher() {}

    public record Available(String id, String name) {}
    public record Candidate(String id, String name, int distance, int score, String reason) {}

    public static String normalize(String raw) {
        if(raw == null) return "";
        String id=raw.trim().replace('\\','/').toLowerCase(Locale.ROOT);
        if(id.endsWith(".dat") || id.endsWith(".ldr")) id=id.substring(0,id.length()-4);
        return id;
    }
    private static String numericRoot(String id) {
        int i=0;
        while(i<id.length() && id.charAt(i)>='0' && id.charAt(i)<='9') i++;
        return i>=3?id.substring(0,i):"";
    }
    private static int commonPrefix(String a,String b) {
        int i=0;
        while(i<a.length() && i<b.length() && a.charAt(i)==b.charAt(i))i++;
        return i;
    }
    /** Bounded Damerau-free Levenshtein (insert/delete/substitute), early cutoff. */
    public static int editDistance(String a,String b,int limit) {
        if(Math.abs(a.length()-b.length())>limit)return limit+1;
        if(a.equals(b))return 0;
        int[] old=new int[b.length()+1],next=new int[b.length()+1];
        for(int j=0;j<old.length;j++)old[j]=j;
        for(int i=1;i<=a.length();i++){
            next[0]=i;int best=next[0];
            for(int j=1;j<=b.length();j++){
                int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;
                next[j]=Math.min(Math.min(next[j-1]+1,old[j]+1),old[j-1]+cost);
                best=Math.min(best,next[j]);
            }
            if(best>limit)return limit+1;
            int[] swap=old;old=next;next=swap;
        }
        return old[b.length()];
    }
    /** Results are ranked, distinct, and limited; the caller must check mesh availability. */
    public static List<Candidate> suggest(String missing,Collection<Available> available,int maximum) {
        String source=normalize(missing);
        if(source.isEmpty()||maximum<=0)return List.of();
        String root=numericRoot(source);
        ArrayList<Candidate> results=new ArrayList<>();
        for(Available item:available){
            String target=normalize(item.id());
            if(target.isEmpty() || target.equals(source) || target.contains("/") ||
                    !target.matches("[a-z0-9_-]{2,60}")) continue;
            String targetRoot=numericRoot(target);
            boolean sameRoot=!root.isEmpty() && root.equals(targetRoot);
            int prefix=commonPrefix(source,target);
            int cutoff=sameRoot?2:1;
            if(!sameRoot && prefix<4)continue;
            int distance=editDistance(source,target,cutoff);
            if(distance>cutoff)continue;
            // Never suggest unrelated numerical designs from a single digit typo.
            if(!root.isEmpty() && !sameRoot)continue;
            int score=200-distance*50+Math.min(10,prefix)*2;
            if(sameRoot)score+=45;
            if(!root.isEmpty() && target.equals(root))score+=80; // 4079b -> 4079
            if(!root.isEmpty() && source.equals(targetRoot))score+=25;
            if(source.startsWith(target))score+=18;
            String reason=target.equals(root) && !root.isEmpty() ? "Reference de base" :
                    sameRoot?"Meme numero de base":"Reference tres proche";
            results.add(new Candidate(target,item.name(),distance,score,reason));
        }
        results.sort(Comparator.comparingInt(Candidate::score).reversed()
                .thenComparingInt(Candidate::distance).thenComparing(Candidate::id));
        if(results.size()>maximum)return List.copyOf(results.subList(0,maximum));
        return List.copyOf(results);
    }
}
