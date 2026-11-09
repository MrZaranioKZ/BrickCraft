import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.StudioProjectAnalysis;
import fr.brickcraft.importer.BrickCraftProjectConverter;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ImportProjectSelfTest {
    private static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        if(args.length<1)throw new IllegalArgumentException("Pass Minecraft config directory as argument");
        LDrawLibrary.init(Path.of(args[0]));
        ok(LDrawLibrary.available(),"complete.zip missing: "+LDrawLibrary.status());
        String model="0 FILE test.ldr\n1 4 0 0 0 1 0 0 0 1 0 0 0 1 3004.dat\n"+
            "1 14 40 0 0 1 0 0 0 1 0 0 0 1 3004.dat\n0 NOFILE\n";
        Path io=Files.createTempFile("brickcraft-selftest", ".io");
        try {
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(io))){
                zip.putNextEntry(new ZipEntry("model.ldr"));
                zip.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
                zip.write(model.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            var project=LDrawProjectReader.inspect(io);
            ok(project.parts().size()==2,"BOM/IO parsing failed");
            ok(project.root().equals("test.ldr"),"Incorrect MPD root: "+project.root());
            var summary=StudioProjectAnalysis.analyze(project);
            ok(summary.missingInstances()==0,"Missing 3004.dat library entry");
            var converted=BrickCraftProjectConverter.convert(project);
            ok(converted.success(),"Simple conversion failed: "+converted.errors());
            ok(converted.pieces().size()==2,"Missing pieces after conversion");
            var half=LDrawProjectReader.parse("0 FILE m.ldr\n"+
                "1 4 10 0 0 1 0 0 0 1 0 0 0 1 3004.dat\n"+
                "1 4 40 0 0 1 0 0 0 1 0 0 0 1 3004.dat\n","m.ldr");
            ok(!BrickCraftProjectConverter.convert(half).success(),"Half stud must not be rounded");
            System.out.println("BRICKCRAFT : 6 tests d'import .io/.mpd, BOM et grille OK");
        } finally {Files.deleteIfExists(io);}
    }
}
