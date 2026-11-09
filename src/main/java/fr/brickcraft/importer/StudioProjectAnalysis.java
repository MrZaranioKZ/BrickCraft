package fr.brickcraft.importer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.core.BrickCapacityConfig;

/** Lossless IO/LDR/MPD inspection, independent of the Minecraft world grid. */
public final class StudioProjectAnalysis {
    private StudioProjectAnalysis(){}
    public record Summary(LDrawProjectReader.Result project,int pieces,int designs,int colors,
                          int missingInstances,Map<String,Integer> missingDesigns,
                          int fractionalX,int fractionalY,int fractionalZ,int angled,
                          double minX,double minY,double minZ,double maxX,double maxY,double maxZ,
                          List<String> warnings){
        public Summary{ missingDesigns=Map.copyOf(missingDesigns); warnings=List.copyOf(warnings); }
        public boolean fitsOriginalGrid(){return fractionalX==0 && fractionalY==0 && fractionalZ==0 && angled==0 && missingInstances==0;}
        public String quickStatus(){
            return pieces+" pieces / "+designs+" formes / "+missingInstances+" references non resolues";
        }
    }
    private static boolean integer(double value){return Math.abs(value-Math.rint(value))<0.0005;}
    private static boolean orthogonal(double[] m){
        for(double x:m)if(Math.min(Math.abs(x),Math.abs(Math.abs(x)-1.0))>0.0005)return false;
        // reject non-rigid or non-orthogonal transformations
        for(int r=0;r<3;r++){
            double length=0;for(int c=0;c<3;c++)length+=m[3*r+c]*m[3*r+c];
            if(Math.abs(length-1)>0.002)return false;
        }
        return true;
    }
    public static Summary analyze(LDrawProjectReader.Result project){
        Map<String,Integer> missing=new HashMap<>();Map<String,Boolean> hasGeometry=new HashMap<>();Set<String> designs=new HashSet<>();Set<Integer> colours=new HashSet<>();
        int fx=0,fy=0,fz=0,angled=0,missingN=0;
        double lx=Double.POSITIVE_INFINITY,ly=Double.POSITIVE_INFINITY,lz=Double.POSITIVE_INFINITY;
        double hx=Double.NEGATIVE_INFINITY,hy=Double.NEGATIVE_INFINITY,hz=Double.NEGATIVE_INFINITY;
        for(var instance:project.parts()){
            String id=instance.partId().toLowerCase(Locale.ROOT);
            designs.add(id);colours.add(instance.color());
            lx=Math.min(lx,instance.x());ly=Math.min(ly,instance.y());lz=Math.min(lz,instance.z());
            hx=Math.max(hx,instance.x());hy=Math.max(hy,instance.y());hz=Math.max(hz,instance.z());
            if(!integer(instance.x()/20.))fx++;
            if(!integer(instance.y()/4.))fy++;
            if(!integer(instance.z()/20.))fz++;
            if(!orthogonal(instance.matrix()))angled++;
            if(!hasGeometry.computeIfAbsent(id,key->{
                String resolved=PreciseReferenceMapper.geometryId(key);
                return resolved!=null&&LDrawLibrary.originalBounds(resolved)!=null;
            })){
                missingN++;missing.merge(id,1,Integer::sum);
            }
        }
        if(project.parts().isEmpty())lx=ly=lz=hx=hy=hz=0;
        List<String> notes=new ArrayList<>(project.problems());
        if(missingN>0)notes.add("References absentes de complete.zip : "+missingN+" occurrences");
        if(fx>0||fz>0)notes.add("Positions intermediaires : "+fx+" en X et "+fz+" en Z (origine des pieces)");
        if(fy>0)notes.add("Hauteurs intermediaires : "+fy+" pieces (pas de 4 LDU)");
        if(angled>0)notes.add("Rotations libres / matrices non orthogonales : "+angled+" pieces");
        if(project.parts().size()>4096)notes.add("Studio sur grille strict : 4096 pieces max ; projection LDraw precise : "+BrickCapacityConfig.label(BrickCapacityConfig.partsSetting())+" choisie");
        if(project.parts().size()>BrickCapacityConfig.effectiveParts())
            notes.add("Projet trop grand pour une projection unique : decouper en sous-modeles ("+project.parts().size()+" pieces)");
        return new Summary(project,project.parts().size(),designs.size(),colours.size(),missingN,missing,
                fx,fy,fz,angled,lx,ly,lz,hx,hy,hz,notes);
    }
    public static String report(Summary s,String file){
        StringBuilder out=new StringBuilder();
        out.append("BRICKCRAFT V0.19.0 - DIAGNOSTIC D'IMPORT\n");
        out.append("Fichier: ").append(file).append("\n");
        out.append("Racine: ").append(s.project().root()).append("\n");
        out.append("Sous-modeles: ").append(s.project().submodels()).append("\n");
        out.append("Pieces: ").append(s.pieces()).append("\n");
        out.append("Designs: ").append(s.designs()).append("\n");
        out.append("Couleurs LDraw: ").append(s.colors()).append("\n");
        out.append(String.format(Locale.ROOT,"Encombrement des origines (LDU): X %.2f..%.2f, Y %.2f..%.2f, Z %.2f..%.2f%n",s.minX(),s.maxX(),s.minY(),s.maxY(),s.minZ(),s.maxZ()));
        out.append("References manquantes (occurrences): ").append(s.missingInstances()).append("\n");
        out.append("Positions sur grille incompatibles: X=").append(s.fractionalX())
                .append(" Y=").append(s.fractionalY()).append(" Z=").append(s.fractionalZ()).append("\n");
        out.append("Orientations non orthogonales: ").append(s.angled()).append("\n");
        out.append("Modele source: transformations conservees dans le diagnostic, sans arrondi.\n");
        out.append("Le moteur BrickPiece exige encore des positions et angles representables; les modeles complexes ne doivent pas etre arrondis silencieusement.\n");
        out.append("\nREFERENCES ABSENTES\n");
        s.missingDesigns().entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String,Integer> x)->x.getValue()).reversed())
                .forEach(e->out.append(e.getKey()).append(" x").append(e.getValue()).append('\n'));
        out.append("\nREMARQUES\n");for(String note:s.warnings())out.append("- ").append(note).append('\n');
        return out.toString();
    }
}
