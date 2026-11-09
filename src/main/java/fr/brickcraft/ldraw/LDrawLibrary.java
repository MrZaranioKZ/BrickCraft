package fr.brickcraft.ldraw;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import fr.brickcraft.core.MaterialFinish;
import java.util.zip.*;

/**
 * Lazy, self-contained LDraw parts reader. Source: user's official complete.zip.
 * Root part .dat files are indexed at startup, meshes are expanded only on demand.
 * LDraw primitives (type 1 references, 3 triangles and 4 quadrilaterals) are supported.
 * TEXMAP decals/UV, conditional lines, BFC winding, photometric material effects are NOT.
 * No server-side download, and no third-party assets shipped with BrickCraft.
 */
public final class LDrawLibrary {
    private LDrawLibrary() {}
    public record PartInfo(String id, String name) {}
    /** Complete LDConfig colour metadata. The code, not RGB, identifies the LEGO material. */
    public record Colour(int code, String name, int rgb, int alpha, String finish,
                         int luminance, String material) {
        public String family() {
            if(alpha<255) return "Transparent";
            return switch(finish) {
                case "SOLID" -> "Opaque";
                case "CHROME", "PEARLESCENT", "METAL", "MATTE_METALLIC" -> "Métal";
                default -> "Spécial";
            };
        }
        public String displayName(){return name.replace('_',' ');}
    }
    /** Parse an official LDraw !COLOUR line, including optional finish metadata. */
    public static Colour parseColour(String line) {
        if(line==null)return null;
        String[] t=line.trim().split("\\s+");
        if(t.length<9 || !t[0].equals("0") || !t[1].equalsIgnoreCase("!COLOUR"))return null;
        try {
            int codeIndex=-1,valueIndex=-1,alphaIndex=-1,luminanceIndex=-1,materialIndex=-1;
            for(int i=2;i<t.length;i++)switch(t[i].toUpperCase(Locale.ROOT)) {
                case "CODE" -> codeIndex=i;
                case "VALUE" -> valueIndex=i;
                case "ALPHA" -> alphaIndex=i;
                case "LUMINANCE" -> luminanceIndex=i;
                case "MATERIAL" -> materialIndex=i;
            }
            if(codeIndex<3 || valueIndex<0 || codeIndex+1>=t.length || valueIndex+1>=t.length)return null;
            int code=Integer.parseInt(t[codeIndex+1]);
            int rgb=Integer.parseInt(t[valueIndex+1].replace("#","").replace("0x",""),16)&0xFFFFFF;
            int alpha=alphaIndex>=0?Integer.parseInt(t[alphaIndex+1]):255;
            int luminance=luminanceIndex>=0?Integer.parseInt(t[luminanceIndex+1]):0;
            String finish="SOLID",material="";
            for(String tag:List.of("CHROME","PEARLESCENT","RUBBER","MATTE_METALLIC","METAL"))
                if(Arrays.asList(t).contains(tag)){finish=tag;break;}
            if(materialIndex>=0 && materialIndex+1<t.length){
                material=t[materialIndex+1].toUpperCase(Locale.ROOT);
                finish=material.equals("FABRIC")?"FABRIC":material;
            }
            String name=String.join("_",Arrays.asList(t).subList(2,codeIndex));
            return new Colour(code,name,rgb,Math.clamp(alpha,0,255),finish,
                    Math.clamp(luminance,0,255),material);
        }catch(RuntimeException e){return null;}
    }
    /** XYZ vertex coordinates in Minecraft units, normalized to the minimum bounds; each 9 floats = triangle. */
    public record Mesh(float[] xyz, int[] colors, float width, float height, float depth, int triangles) {
        public boolean isEmpty(){return triangles==0;}
    }
    private static volatile ZipFile archive;
    /** Optional, user-provided BrickLink Studio geometry supplement; never bundled in the mod JAR. */
    private static volatile ZipFile studioArchive;
    private static Map<String,String> studioLookup=Map.of();
    private static Map<String,Path> customLookup=Map.of();
    private static Map<String, String> lookup=Map.of();
    private static List<PartInfo> parts=List.of();
    private static Map<String, PartInfo> byId=Map.of();
    private static Map<Integer, Colour> colours=Map.of();
    private static String message="Bibliotheque absente : place complete.zip dans config/brickcraft/";
    /** Bounds in original LDraw units (before normalization to the mesh minimum). */
    private static final Map<String,double[]> sourceBounds=new HashMap<>();
    private static final LinkedHashMap<String, Mesh> cached=new LinkedHashMap<>(96,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Mesh> e){return size()>48;}
    };
    public static synchronized void init(Path configDir) {
        close();
        Path file=configDir.resolve("brickcraft").resolve("complete.zip");
        if(!Files.isRegularFile(file)) {message="LDraw non installe : "+file.toAbsolutePath();return;}
        try {
            ZipFile zip=new ZipFile(file.toFile());
            Map<String,String> index=new HashMap<>();
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry ent=entries.nextElement();
                if(ent.isDirectory())continue;
                String name=ent.getName().replace('\\','/').toLowerCase(Locale.ROOT);
                if(name.startsWith("ldraw/"))name=name.substring(6);
                if(!name.endsWith(".dat")&&!name.endsWith(".ldr")) continue;
                if(name.contains("../") || name.startsWith("/"))continue;
                index.putIfAbsent(name,ent.getName());
            }
            Map<String,PartInfo> all=new LinkedHashMap<>();
            for(var e:index.entrySet()){
                String key=e.getKey();
                if(!key.startsWith("parts/") || key.substring(6).contains("/"))continue;
                String id=key.substring(6,key.length()-4);
                String title="";
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry(e.getValue())),StandardCharsets.UTF_8))){
                    for(int i=0;i<5;i++){
                        String line=reader.readLine();if(line==null)break;
                        String s=line.trim();
                        if(s.startsWith("0 ") && !s.startsWith("0 !") && !s.startsWith("0 BFC") && !s.startsWith("0 Name:") && !s.startsWith("0 Author:")){
                            title=s.substring(2).trim();break;
                        }
                    }
                }catch(Exception ignored){}
                all.put(id,new PartInfo(id,title.isEmpty()?id:title));
            }
            // Project-specific shape definitions extracted from the owner's .io/model2.ldr.
            // Prefer them only for their exact part IDs (e.g. bl_3004pb342), never
            // overwrite the official base part 3004.dat.
            Path studioFile=configDir.resolve("brickcraft").resolve("studio_parts.zip");
            ZipFile extra=null;
            Map<String,String> extraIndex=new HashMap<>();
            if(Files.isRegularFile(studioFile)){
                try{
                    extra=new ZipFile(studioFile.toFile());
                    Enumeration<? extends ZipEntry> customEntries=extra.entries();
                    int count=0;
                    while(customEntries.hasMoreElements()){
                        ZipEntry ent=customEntries.nextElement();
                        String name=ent.getName().replace('\\','/').toLowerCase(Locale.ROOT);
                        if(ent.isDirectory() || !name.startsWith("parts/") || !name.endsWith(".dat")
                            || name.contains("../") || ent.getSize()>4*1024*1024)continue;
                        // Keep user-supplied parts in an isolated namespace; no official overwrite.
                        String id=name.substring(6,name.length()-4);
                        if(!id.matches("bl_[a-z0-9_]+") || index.containsKey(name))continue;
                        if(++count>4096)break;
                        extraIndex.put(name,ent.getName());
                        all.putIfAbsent(id,new PartInfo(id,"Studio : "+id));
                    }
                }catch(IOException ex){
                    if(extra!=null)try{extra.close();}catch(IOException ignored){}
                    extra=null;extraIndex.clear();
                }
            }
            studioArchive=extra;studioLookup=Map.copyOf(extraIndex);
            // User-installed geometry, isolated from the official archive. No overwriting
            // official .dat files. Folder may contain flat IDs or LDraw paths parts/s/ / p/.
            Map<String,Path> custom=new LinkedHashMap<>();
            Path customDir=configDir.resolve("brickcraft").resolve("parts_custom");
            if(Files.isDirectory(customDir))try(var walk=Files.walk(customDir,5)){
                walk.filter(Files::isRegularFile).limit(10000).forEach(filePath->{
                    try{
                        if(Files.isSymbolicLink(filePath)||Files.size(filePath)>2*1024*1024)return;
                        String relative=customDir.relativize(filePath).toString().replace('\\','/').toLowerCase(Locale.ROOT);
                        if(!relative.matches("[a-z0-9_./-]+\\.(dat|ldr)"))return;
                        String key=(relative.startsWith("parts/")||relative.startsWith("p/"))?
                                relative:"parts/"+relative;
                        if(index.containsKey(key)||extraIndex.containsKey(key))return;
                        custom.putIfAbsent(key,filePath);
                        if(key.startsWith("parts/") && !key.substring(6).contains("/")){
                            String id=key.substring(6,key.lastIndexOf('.'));
                            String title=id;
                            try(BufferedReader reader=Files.newBufferedReader(filePath,StandardCharsets.UTF_8)){
                                String line=reader.readLine();if(line!=null&&line.startsWith("0 "))title=line.substring(2).trim();
                            }
                            all.putIfAbsent(id,new PartInfo(id,title));
                        }
                    }catch(IOException ignored){}
                });
            }catch(IOException ignored){}
            customLookup=Map.copyOf(custom);
            List<PartInfo> sorted=new ArrayList<>(all.values());
            sorted.sort(Comparator.comparing(PartInfo::id,String.CASE_INSENSITIVE_ORDER));
            Map<Integer,Colour> loadedColors=new LinkedHashMap<>();
            String config=index.get("ldconfig.ldr");
            if(config!=null) {
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry(config)),StandardCharsets.UTF_8))){
                    for(String line;(line=reader.readLine())!=null;){
                        Colour color=parseColour(line);
                        if(color!=null)loadedColors.put(color.code(),color);
                    }
                }
            }
            archive=zip;
            lookup=Map.copyOf(index);parts=List.copyOf(sorted);byId=Map.copyOf(all);colours=Map.copyOf(loadedColors);
            message="LDraw : "+parts.size()+" fichiers indexés ("+file.getFileName()+")"+
                    (studioLookup.isEmpty()?"":" dont "+studioLookup.size()+" formes Studio spécifiques")+
                    (customLookup.isEmpty()?"":" + "+customLookup.size()+" geometries personnalisees");
        } catch(Exception ex){close();message="Erreur LDraw : "+ex.getMessage();}
    }
    public static synchronized void close(){
        try {if(archive!=null)archive.close();}catch(IOException ignored){}
        try {if(studioArchive!=null)studioArchive.close();}catch(IOException ignored){}
        studioArchive=null;studioLookup=Map.of();customLookup=Map.of();
        archive=null;lookup=Map.of();parts=List.of();byId=Map.of();colours=Map.of();cached.clear();sourceBounds.clear();
    }
    public static String status(){return message;}
    public static int size(){return parts.size();}
    public static boolean available(){return archive!=null;}
    public static boolean contains(String id){return id!=null && byId.containsKey(id.toLowerCase(Locale.ROOT));}
    public static PartInfo info(String id){return byId.get(id.toLowerCase(Locale.ROOT));}
    public static List<Colour> colors(){return colours.values().stream().sorted(Comparator.comparingInt(Colour::code)).toList();}
    public static Colour color(int code){return colours.get(code);}
    /** Filter by name, exact/partial numeric code, and material family, without collapsing duplicate RGB values. */
    public static List<Colour> searchColors(String query,String family){
        String q=query==null?"":query.strip().toLowerCase(Locale.ROOT).replace('_',' ');
        String f=family==null?"Tout":family;
        return colors().stream().filter(c->switch(f){
            case "Opaque" -> c.alpha()==255 && c.finish().equals("SOLID");
            case "Transparent" -> c.alpha()<255;
            case "Métal" -> List.of("CHROME","PEARLESCENT","METAL","MATTE_METALLIC").contains(c.finish());
            case "Spécial" -> !c.finish().equals("SOLID") &&
                    !List.of("CHROME","PEARLESCENT","METAL","MATTE_METALLIC").contains(c.finish());
            default -> true;
        })
            .filter(c->q.isBlank() || c.displayName().toLowerCase(Locale.ROOT).contains(q)
                    || Integer.toString(c.code()).contains(q)
                    || c.finish().toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    /** Compatibility for old pieces holding only RGB. */

    /** ARGB including LDraw transparency; code -1 means legacy opaque RGB. */
    public static int argb(int colorCode,int fallbackRGB){
        Colour c=colours.get(colorCode);
        return c==null?(0xFF000000|(fallbackRGB&0xFFFFFF)):(c.alpha()<<24)|c.rgb();
    }
    public static int meshColor(int triangleCode,int mainColorCode,int mainRgb){
        if(triangleCode>=0x1000000)return 0xFF000000|(triangleCode&0xFFFFFF);
        return argb(triangleCode==16 || triangleCode==24?mainColorCode:triangleCode,
                triangleCode==16 || triangleCode==24?mainRgb:resolveColor(triangleCode,mainRgb));
    }
    /** Deterministic pseudo-specular material response; alpha stays unchanged. */
    public static int surfaceTint(int argb,int colorCode,double nx,double ny,double nz,long seed){
        Colour col=colours.get(colorCode);
        return MaterialFinish.shade(argb,col==null?"SOLID":col.finish(),nx,ny,nz,seed);
    }
    /** Approximate material response for Minecraft's fixed vertex-colour pipeline. */
    public static int tint(int argb,int colorCode,double lightFacing){
        Colour material=colours.get(colorCode);
        double factor=1.0;
        if(material!=null) {
            factor=switch(material.finish()) {
                case "CHROME" -> 0.85+0.4*lightFacing;
                case "PEARLESCENT" -> 0.93+0.22*lightFacing;
                case "METAL", "MATTE_METALLIC" -> 0.88+0.25*lightFacing;
                case "GLITTER", "SPECKLE" -> 0.9+0.19*lightFacing;
                case "FABRIC", "RUBBER" -> 0.83+0.08*lightFacing;
                default -> 1.0;
            };
            if(material.luminance()>0)factor=Math.max(factor,1.0+material.luminance()/512.0);
        }
        int a=(argb>>>24)&255;
        int r=Math.min(255,(int)(((argb>>>16)&255)*factor));
        int g=Math.min(255,(int)(((argb>>>8)&255)*factor));
        int b=Math.min(255,(int)((argb&255)*factor));
        return a<<24|r<<16|g<<8|b;
    }
    public static String describe(int colorCode,int fallbackRGB){
        Colour c=colours.get(colorCode);
        return c==null?String.format("#%06X",fallbackRGB&0xFFFFFF):
            c.displayName()+" (#"+c.code()+", "+c.family()+")";
    }
    public static int resolveColor(int ldrawCode,int main){
        if(ldrawCode==16||ldrawCode==24)return main;
        Colour c=colours.get(ldrawCode);
        return c!=null?c.rgb():main;
    }
    public static String normalize(String id){return id==null?"":id.trim().toLowerCase(Locale.ROOT).replace(".dat","");}
    public static List<PartInfo> search(String query,int max){
        String q=normalize(query).replaceAll("[ _-]+","").replace('×','x');
        // Explicit verified LEGO element -> LDraw design alias (not a general element-ID database).
        if(q.equals("6258572"))q="3004";
        List<PartInfo> result=new ArrayList<>();
        for(PartInfo p:parts){
            String name=p.name().toLowerCase(Locale.ROOT).replaceAll("[ _-]+","").replace('×','x');
            if(q.isEmpty()||p.id().contains(q)||name.contains(q)){
                result.add(p);if(result.size()>=max)break;
            }
        }
        return result;
    }
    public static synchronized double[] originalBounds(String id){
        String key=normalize(id);
        double[] value=sourceBounds.get(key);
        if(value==null){mesh(key);value=sourceBounds.get(key);}
        return value==null?null:value.clone();
    }
    public static synchronized Mesh mesh(String id){
        String key=normalize(id);
        if(!contains(key))return null;
        Mesh cachedMesh=cached.get(key);if(cachedMesh!=null)return cachedMesh;
        if(archive==null)return null;
        try {
            ArrayList<Tri> result=new ArrayList<>();
            expand("parts/"+key+".dat",16,new double[]{0,0,0},
                    new double[]{1,0,0,0,1,0,0,0,1},result,new HashSet<>(),0);
            if(result.isEmpty())return null;
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(Tri t:result)for(int j=0;j<9;j+=3)for(int d=0;d<3;d++){
                double c=t.xyz[j+d];lo[d]=Math.min(lo[d],c);hi[d]=Math.max(hi[d],c);
            }
            if(hi[0]-lo[0]>.001 && hi[1]-lo[1]>.001 && hi[2]-lo[2]>.001 &&
               hi[0]-lo[0]<=1600 && hi[1]-lo[1]<=1600 && hi[2]-lo[2]<=1600) {
                float[] xyz=new float[result.size()*9];int[] codes=new int[result.size()];
                for(int i=0;i<result.size();i++){
                    Tri tri=result.get(i);codes[i]=tri.code;
                    for(int j=0;j<9;j+=3){
                        xyz[i*9+j]=(float)((tri.xyz[j]-lo[0])/20.);
                        xyz[i*9+j+1]=(float)((hi[1]-tri.xyz[j+1])/20.); // LDraw Y down; MC Y up
                        xyz[i*9+j+2]=(float)((tri.xyz[j+2]-lo[2])/20.);
                    }
                }
                Mesh mesh=new Mesh(xyz,codes,(float)((hi[0]-lo[0])/20.),
                        (float)((hi[1]-lo[1])/20.),(float)((hi[2]-lo[2])/20.),result.size());
                sourceBounds.put(key,new double[]{lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]});
                cached.put(key,mesh);return mesh;
            }
        }catch(Exception ignored){}
        return null;
    }
    private record Tri(double[] xyz,int code){}
    private static void expand(String file,int current,double[] offset,double[] rot,
                               List<Tri> dst,Set<String> stack,int depth) throws IOException {
        if(depth>=24||dst.size()>60000)return;
        String name=normalizeFile(file);
        if(!stack.add(name))return;
        boolean studio=studioLookup.containsKey(name);
        ZipFile source=studio?studioArchive:archive;
        String mapped=studio?studioLookup.get(name):lookup.get(name);
        Path userFile=customLookup.get(name);
        if(mapped==null&&userFile==null){stack.remove(name);return;}
        if(mapped!=null&&source==null){stack.remove(name);return;}
        ZipEntry zipEntry=mapped==null?null:source.getEntry(mapped);
        if(mapped!=null&&zipEntry==null){stack.remove(name);return;}
        try(BufferedReader reader=mapped!=null?
                new BufferedReader(new InputStreamReader(source.getInputStream(zipEntry),StandardCharsets.UTF_8)):
                Files.newBufferedReader(userFile,StandardCharsets.UTF_8)){
            for(String line;(line=reader.readLine())!=null;) {
                String[] t=line.trim().split("\\s+");
                if(t.length==0)continue;
                try {
                    int type=Integer.parseInt(t[0]);
                    if(type==1&&t.length>=15) {
                        int col=parseColor(t[1],current);
                        double[] pos={Double.parseDouble(t[2]),Double.parseDouble(t[3]),Double.parseDouble(t[4])};
                        double[] local=new double[9];for(int i=0;i<9;i++)local[i]=Double.parseDouble(t[5+i]);
                        StringBuilder child=new StringBuilder(t[14]);for(int j=15;j<t.length;j++)child.append(' ').append(t[j]);
                        double[] composed=mul(rot,local);
                        double[] translated=transform(pos,offset,rot);
                        String resolved=resolveRef(child.toString());
                        if(resolved!=null)expand(resolved,col,translated,composed,dst,stack,depth+1);
                    }else if(type==3&&t.length>=11) {
                        double[] xyz=new double[9];for(int i=0;i<9;i++)xyz[i]=Double.parseDouble(t[2+i]);
                        for(int i=0;i<9;i+=3){double[] p=transform(Arrays.copyOfRange(xyz,i,i+3),offset,rot);
                            System.arraycopy(p,0,xyz,i,3);}
                        dst.add(new Tri(xyz,parseColor(t[1],current)));
                    }else if(type==4&&t.length>=14) {
                        double[] quad=new double[12];for(int i=0;i<12;i++)quad[i]=Double.parseDouble(t[2+i]);
                        double[] a=new double[12];for(int i=0;i<12;i+=3){double[] p=transform(Arrays.copyOfRange(quad,i,i+3),offset,rot);System.arraycopy(p,0,a,i,3);}
                        dst.add(new Tri(new double[]{a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8]},parseColor(t[1],current)));
                        dst.add(new Tri(new double[]{a[0],a[1],a[2],a[6],a[7],a[8],a[9],a[10],a[11]},parseColor(t[1],current)));
                    }
                }catch(NumberFormatException|ArrayIndexOutOfBoundsException ignored){}
                if(dst.size()>60000)throw new IOException("Too many triangles");
            }
        }finally{stack.remove(name);}
    }
    private static String resolveRef(String filename){
        String normalized=normalizeFile(filename);
        if(lookup.containsKey(normalized)||studioLookup.containsKey(normalized)||customLookup.containsKey(normalized))return normalized;
        for(String candidate:List.of("parts/"+normalized,"p/"+normalized,"parts/s/"+normalized))
            if(lookup.containsKey(candidate)||studioLookup.containsKey(candidate)||customLookup.containsKey(candidate))return candidate;
        return null;
    }
    private static String normalizeFile(String s){return s.replace('\\','/').toLowerCase(Locale.ROOT).replaceFirst("^ldraw/","");}
    private static int parseColor(String token,int current){
        if(token.equals("16")||token.equals("24"))return current;
        if(token.startsWith("0x2")&&token.length()==9)return Integer.parseInt(token.substring(3),16)|0x1000000;
        return Integer.parseInt(token);
    }
    private static double[] transform(double[] p,double[] translation,double[] m){
        return new double[]{translation[0]+m[0]*p[0]+m[1]*p[1]+m[2]*p[2],
                translation[1]+m[3]*p[0]+m[4]*p[1]+m[5]*p[2],
                translation[2]+m[6]*p[0]+m[7]*p[1]+m[8]*p[2]};
    }
    private static double[] mul(double[] a,double[] b){
        double[] c=new double[9];
        for(int r=0;r<3;r++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)c[r*3+col]+=a[r*3+k]*b[k*3+col];
        return c;
    }
}
