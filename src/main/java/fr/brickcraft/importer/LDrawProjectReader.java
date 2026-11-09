package fr.brickcraft.importer;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LDR/MPD assembly reader (diagnostics and future BrickLink Studio importer groundwork).
 * Preserves submodel relationships, arbitrary LDU coordinates and full 3x3 rotations;
 * DOES NOT round them to BrickPiece's integer-block grid or write Minecraft worlds.
 * BrickLink .io archives are supported by inspecting their model.ldr entry.
 */
public final class LDrawProjectReader {
    private LDrawProjectReader() {}
    private static final int MAX_LINES=1_000_000, MAX_DEPTH=32, MAX_INSTANCES=250_000;
    public record Instance(String partId,int color,double x,double y,double z,double[] matrix) {
        public Instance {matrix=matrix.clone();}
        @Override public double[] matrix(){return matrix.clone();}
    }
    public record Result(String root,int submodels,List<Instance> parts,List<String> problems){
        public Result {parts=List.copyOf(parts);problems=List.copyOf(problems);}
    }
    private record Ref(String file,int color,double x,double y,double z,double[] matrix){}
    private static String normalize(String name){
        return name.replace('\\','/').trim().toLowerCase(Locale.ROOT);
    }

    public static Result inspect(Path file) throws IOException {
        String lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
        if(lower.endsWith(".io")) {
            // Encrypted and plain BrickLink Studio archives share this reader.
            return parse(StudioIoArchive.modelText(file),file.getFileName().toString());
        }
        if(!lower.endsWith(".ldr")&&!lower.endsWith(".mpd"))
            throw new IOException("Fichier .io, .ldr ou .mpd attendu");
        if(Files.size(file)>32*1024*1024)throw new IOException("Fichier trop volumineux (32 Mo maximum)");
        return parse(Files.readString(file,StandardCharsets.UTF_8),file.getFileName().toString());
    }
    public static Result parse(String text,String filename) throws IOException {
        if(text.length()>32*1024*1024)throw new IOException("Source LDraw trop volumineuse");
        Map<String,List<Ref>> models=new LinkedHashMap<>();
        List<String> issues=new ArrayList<>();
        String current=normalize(filename);
        boolean hasMpd=false,headerSeen=false;
        models.put(current,new ArrayList<>());
        int row=0;
        try(BufferedReader reader=new BufferedReader(new StringReader(text))){
        for(String line;(line=reader.readLine())!=null;){
            if(++row>MAX_LINES)throw new IOException("Limite de lignes depassee");
            // Studio writes a UTF-8 BOM at the beginning of model.ldr in some versions.
            String trimmed=line.replace("\uFEFF", "").trim();
            if(trimmed.regionMatches(true,0,"0 FILE ",0,7)){
                if(!hasMpd){models.clear();hasMpd=true;}
                current=normalize(trimmed.substring(7));
                if(current.isEmpty()) {issues.add("Ligne "+row+" : nom de sous-modele vide");continue;}
                if(models.containsKey(current))issues.add("Sous-modele duplique : "+current);
                models.putIfAbsent(current,new ArrayList<>());
                headerSeen=true;continue;
            }
            if(trimmed.equalsIgnoreCase("0 NOFILE")) {current=null;continue;}
            if(current==null || hasMpd && !headerSeen)continue;
            String[] values=trimmed.split("\\s+",15);
            if(values.length==0 || !values[0].equals("1"))continue;
            if(values.length<15){issues.add("Ligne "+row+" : reference LDraw incomplete");continue;}
            try{
                int color=Integer.parseInt(values[1]);
                double x=Double.parseDouble(values[2]),y=Double.parseDouble(values[3]),z=Double.parseDouble(values[4]);
                double[] m=new double[9];
                boolean finite=Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);
                for(int i=0;i<9;i++){m[i]=Double.parseDouble(values[i+5]);finite &= Double.isFinite(m[i]);}
                if(!finite){issues.add("Ligne "+row+" : transformation non finie");continue;}
                models.get(current).add(new Ref(normalize(values[14]),color,x,y,z,m));
            }catch(NumberFormatException ex){issues.add("Ligne "+row+" : reference illisible");}
        }
        }
        String root=models.keySet().iterator().next();
        List<Instance> result=new ArrayList<>();
        expand(root,16,0,0,0,identity(),models,new HashSet<>(),result,issues,0);
        return new Result(root,models.size(),result,issues);
    }
    private static void expand(String key,int inherited,double px,double py,double pz,double[] rot,
                               Map<String,List<Ref>> models,Set<String> stack,List<Instance> out,
                               List<String> issues,int depth) throws IOException {
        if(depth>MAX_DEPTH)throw new IOException("Imbrication MPD excessive");
        if(!stack.add(key)){issues.add("Cycle MPD : "+key);return;}
        for(Ref r:models.getOrDefault(key,List.of())){
            int color=r.color()==16?inherited:r.color();
            double[] t=transform(r.x(),r.y(),r.z(),rot);
            double x=px+t[0],y=py+t[1],z=pz+t[2];
            double[] m=mul(rot,r.matrix());
            String reference=r.file();
            if(models.containsKey(reference)){
                expand(reference,color,x,y,z,m,models,stack,out,issues,depth+1);
            }else{
                if(out.size()>=MAX_INSTANCES)throw new IOException("Plus de 250 000 pieces");
                // Library design references should be .dat; external .ldr references are
                // recorded, but must later be reported as unresolved, not guessed.
                String id=reference.endsWith(".dat")?reference.substring(0,reference.length()-4):reference;
                out.add(new Instance(id,color,x,y,z,m));
            }
        }
        stack.remove(key);
    }
    private static double[] identity(){return new double[]{1,0,0,0,1,0,0,0,1};}
    private static double[] transform(double x,double y,double z,double[] m){
        return new double[]{m[0]*x+m[1]*y+m[2]*z,m[3]*x+m[4]*y+m[5]*z,m[6]*x+m[7]*y+m[8]*z};
    }
    private static double[] mul(double[] a,double[] b){
        double[] out=new double[9];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)
            for(int k=0;k<3;k++)out[row*3+col]+=a[row*3+k]*b[k*3+col];
        return out;
    }
}
