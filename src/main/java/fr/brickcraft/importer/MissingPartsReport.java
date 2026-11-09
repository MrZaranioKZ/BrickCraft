package fr.brickcraft.importer;

import fr.brickcraft.ldraw.LDrawLibrary;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local offline inventory + clickable trusted search pages (no remote code execution).
 * We never fabricate a part name when Studio doesn't provide one. */
public final class MissingPartsReport {
    private MissingPartsReport(){}
    public record Missing(String id,String name,int count,String queryUrl){ }
    public static List<Missing> list(StudioProjectAnalysis.Summary summary){return list(summary,Map.of());}
    public static List<Missing> list(StudioProjectAnalysis.Summary summary,Map<String,String> names){
        List<Missing> result=new ArrayList<>();
        for(var entry:summary.missingDesigns().entrySet()){
            String id=entry.getKey();var info=LDrawLibrary.info(id);
            String name=info!=null?info.name():names.getOrDefault(id,"Description indisponible (recherche par reference)");
            String term=URLEncoder.encode(id+" LDraw LEGO piece",StandardCharsets.UTF_8);
            result.add(new Missing(id,name,entry.getValue(),"https://www.google.com/search?q=site%3Alibrary.ldraw.org+"+term));
        }
        result.sort(Comparator.comparingInt(Missing::count).reversed().thenComparing(Missing::id));
        return List.copyOf(result);
    }
    public static Path write(StudioProjectAnalysis.Summary summary,Path reportDir,String fileName)throws IOException {
        return write(summary,reportDir,fileName,Map.of());
    }
    public static Path write(StudioProjectAnalysis.Summary summary,Path reportDir,String fileName,Map<String,String> knownNames)throws IOException {
        Files.createDirectories(reportDir);
        String stem=fileName.replaceAll("[^a-zA-Z0-9._-]","_").replaceAll("(?i)\\.(io|ldr|mpd|bcpx)$","");
        if(stem.isBlank())stem="projet";
        Path target=reportDir.resolve(stem+"_pieces_manquantes.html");
        List<Missing> list=list(summary,knownNames);
        StringBuilder html=new StringBuilder("<!doctype html><html lang=\"fr\"><meta charset=\"utf-8\"><title>BrickCraft - Pieces manquantes</title>");
        html.append("<style>body{font-family:system-ui,sans-serif;background:#101922;color:#ddecf6;max-width:960px;margin:32px auto;padding:0 18px}a{color:#7ad9ef}table{width:100%;border-collapse:collapse}td,th{padding:10px;border-bottom:1px solid #344554;text-align:left}code{color:#8de5c6}small{color:#a8becf}</style>");
        html.append("<h1>Pieces manquantes - BrickCraft</h1><p>Projet : <b>").append(escape(fileName)).append("</b></p>");
        html.append("<p>").append(summary.missingInstances()).append(" occurrences / ").append(list.size()).append(" references distinctes.</p>");
        html.append("<p>Les noms absents du modele ne sont pas inventes. Recherche via le code LDraw / BrickLink. ");
        html.append("Telecharge uniquement des fichiers .dat fiables, place-les dans <code>config/brickcraft/parts_custom/</code>, puis utilise 'Recharger LDraw' dans BrickCraft.</p>");
        html.append("<p>Sources : <a href=\"https://library.ldraw.org/\">LDraw officiel</a> | <a href=\"https://library.ldraw.org/tracker\">Parts Tracker non officiel</a></p>");
        html.append("<table><tr><th>Reference</th><th>Description connue</th><th>Occurrences</th><th>Recherche</th></tr>");
        for(Missing m:list)html.append("<tr><td><code>").append(escape(m.id())).append("</code></td><td>").append(escape(m.name())).append("</td><td>").append(m.count()).append("</td><td><a target=\"_blank\" rel=\"noopener noreferrer\" href=\"").append(escape(m.queryUrl())).append("\">Chercher en ligne</a></td></tr>");
        html.append("</table><p><small>Ce rapport utilise la bibliotheque installee sur ton PC au moment de l'analyse. Aucun telechargement automatique.</small></p></html>");
        Files.writeString(target,html.toString(),StandardCharsets.UTF_8);
        Path csv=reportDir.resolve(stem+"_pieces_manquantes.csv");
        StringBuilder tsv=new StringBuilder("reference;description;quantite;recherche\n");
        for(Missing m:list)tsv.append(csvCell(m.id())).append(';').append(csvCell(m.name())).append(';').append(m.count()).append(';').append(csvCell(m.queryUrl())).append('\n');
        Files.writeString(csv,tsv.toString(),StandardCharsets.UTF_8);
        return target;
    }
    private static String csvCell(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
