import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.StudioProjectAnalysis;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone inspector: .io, .mpd, .ldr without starting Minecraft. */
public final class InspectBricklinkProject {
    public static void main(String[] args)throws Exception {
        if(args.length<1){
            System.err.println("Usage : INSPECTER_BRICKLINK_WINDOWS.bat chemin_vers_projet.io [config_minecraft]");
            System.exit(2);
        }
        if(args.length>1)LDrawLibrary.init(Path.of(args[1]));
        var project=LDrawProjectReader.inspect(Path.of(args[0]));
        var info=StudioProjectAnalysis.analyze(project);
        System.out.println(StudioProjectAnalysis.report(info,Path.of(args[0]).getFileName().toString()));
        Path report=Path.of(args[0]).toAbsolutePath().resolveSibling(Path.of(args[0]).getFileName()+".brickcraft-diagnostic.txt");
        Files.writeString(report,StudioProjectAnalysis.report(info,Path.of(args[0]).getFileName().toString()));
        System.out.println("Rapport : "+report);
    }
}
