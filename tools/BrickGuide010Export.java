import fr.brickcraft.guide.BrickGuidePlanner;
import fr.brickcraft.importer.PreciseProjectIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline planning snapshot; does NOT claim to recreate BrickLink instructions. */
public final class BrickGuide010Export {
    private static String esc(String in){
        return in.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");
    }
    private static void groups(StringBuilder out,List<BrickGuidePlanner.Group> groups){
        out.append("[\n");
        for(int g=0;g<groups.size();g++){
            var group=groups.get(g);
            if(g>0)out.append(",\n");
            out.append("    {\"number\":").append(group.number())
               .append(",\"label\":\"").append(esc(group.label())).append("\",\"indices\":[");
            var indexes=group.indexes();
            for(int i=0;i<indexes.size();i++){
                if(i>0)out.append(',');out.append(indexes.get(i));
            }
            out.append("]}");
        }
        out.append("\n  ]");
    }
    public static void main(String[] args)throws Exception{
        if(args.length<1||args.length>2){System.err.println("Utilisation: java BrickGuide010Export fichier.bcpx [sortie.json]");System.exit(2);}
        Path input=Path.of(args[0]);
        Path output=args.length==2?Path.of(args[1]):Path.of(args[0].replaceAll("(?i)\\.bcpx$","" )+"_guide_010.json");
        var project=PreciseProjectIO.read(input);
        var plan=BrickGuidePlanner.preview(project,8.0,20);
        StringBuilder out=new StringBuilder(project.parts().size()*17+4000);
        out.append("{\n  \"format\":\"brickcraft-guide-preview-v1\",\n")
           .append("  \"notice_officielle\":false,\n")
           .append("  \"disclaimer\":\"Couches par origine Y, lots dans l'ordre des instances aplaties; pas des vraies etapes de montage\",\n")
           .append("  \"piece_count\":").append(plan.partCount()).append(",\n")
           .append("  \"layer_height_ldu\":8,\n  \"max_pieces_per_source_batch\":20,\n")
           .append("  \"layers\":");
        groups(out,plan.layers());out.append(",\n  \"source_batches\":");
        groups(out,plan.sourceSteps());out.append("\n}\n");
        Path parent=output.toAbsolutePath().getParent();
        if(parent!=null)Files.createDirectories(parent);
        Files.writeString(output,out,StandardCharsets.UTF_8);
        System.out.println("Guide non officiel exporte : "+output+" | " + plan.partCount()+" pieces, "+
          plan.layers().size()+" couches et "+plan.sourceSteps().size()+" lots source.");
    }
}
