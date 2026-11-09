package fr.brickcraft.guide;

import fr.brickcraft.importer.LDrawProjectReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Retrieves real 0 STEP boundaries, including nested MPD submodels, without changing the flat BCPX format. */
public final class BrickSourceStepReader {
    private BrickSourceStepReader(){}
    private sealed interface Token permits Part, Break {}
    private record Part(String reference) implements Token {}
    private record Break() implements Token {}
    private static String normalize(String file){return file.replace('\\','/').strip().toLowerCase(Locale.ROOT);}
    private static String sourceText(Path file)throws IOException{
        if(Files.size(file)>96L*1024*1024)throw new IOException("Source trop volumineuse");
        if(file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".io"))
            return fr.brickcraft.importer.StudioIoArchive.modelText(file);
        if(Files.size(file)>32L*1024*1024)throw new IOException("Source trop volumineuse");
        return Files.readString(file,StandardCharsets.UTF_8);
    }
    private static final class Cursor {int index=0;}
    private static void add(List<BrickGuidePlan.Step> steps,List<Integer> current){
        if(!current.isEmpty()){
            steps.add(new BrickGuidePlan.Step("Etape Studio "+(steps.size()+1),current));
            current.clear();
        }
    }
    private static void expand(String key,Map<String,List<Token>> models,Set<String> stack,
                               Cursor cursor,List<BrickGuidePlan.Step> groups,int depth)throws IOException{
        if(depth>32||!stack.add(key))throw new IOException("Sous-modele recursif ou profondeur excessive");
        ArrayList<Integer> pending=new ArrayList<>();
        for(Token token:models.getOrDefault(key,List.of())){
            if(token instanceof Break){add(groups,pending);continue;}
            String part=((Part)token).reference();
            if(models.containsKey(part)){
                add(groups,pending);expand(part,models,stack,cursor,groups,depth+1);
            }else{
                if(cursor.index>=250_000)throw new IOException("Trop de pieces");
                pending.add(cursor.index++);
            }
        }
        add(groups,pending);stack.remove(key);
    }
    public static BrickGuidePlan inspect(Path file,List<LDrawProjectReader.Instance> worldParts)throws IOException{
        String text=sourceText(file);
        // The original loader supplies the authoritative flattened part order.
        LDrawProjectReader.Result flattened=LDrawProjectReader.parse(text,file.getFileName().toString());
        if(flattened.parts().size()!=worldParts.size()||flattened.parts().isEmpty())
            throw new IOException("Source ne correspond pas a la projection (nombre de pieces)");
        int matches=0;
        for(int i=0;i<worldParts.size();i++){
            var a=flattened.parts().get(i);var b=worldParts.get(i);
            if(normalize(a.partId()).equals(normalize(b.partId())) && a.color()==b.color())matches++;
        }
        // World edits may change a few pieces, but an unrelated model must never be accepted.
        if(matches < Math.max(1,worldParts.size()-(worldParts.size()<100?0:Math.max(1,worldParts.size()/100))))
            throw new IOException("Source et projection ne correspondent pas par index et couleur");
        LinkedHashMap<String,List<Token>> models=new LinkedHashMap<>();
        String current=normalize(file.getFileName().toString());
        models.put(current,new ArrayList<>());
        boolean hasMpd=false;int lines=0;
        for(String line:text.split("\\R")){
            if(++lines>1_000_000)throw new IOException("Trop de lignes");
            String t=line.replace("\ufeff","").trim();
            if(t.regionMatches(true,0,"0 FILE ",0,7)){
                if(!hasMpd){models.clear();hasMpd=true;}
                current=normalize(t.substring(7));models.putIfAbsent(current,new ArrayList<>());continue;
            }
            if(t.equalsIgnoreCase("0 NOFILE")){current=null;continue;}
            if(current==null)continue;
            if(t.equalsIgnoreCase("0 STEP") || t.equalsIgnoreCase("0 ROTSTEP")){
                models.get(current).add(new Break());continue;
            }
            String[] fields=t.split("\\s+",15);
            if(fields.length>=15&&fields[0].equals("1"))
                models.get(current).add(new Part(normalize(fields[14])));
        }
        Cursor cursor=new Cursor();ArrayList<BrickGuidePlan.Step> result=new ArrayList<>();
        expand(models.keySet().iterator().next(),models,new HashSet<>(),cursor,result,0);
        if(cursor.index!=worldParts.size())throw new IOException("Index des etapes non concordant");
        return new BrickGuidePlan(worldParts.size(),result,"Etapes 0 STEP du fichier "+file.getFileName(),true);
    }
}
