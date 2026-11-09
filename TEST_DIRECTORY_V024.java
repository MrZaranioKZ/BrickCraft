import java.nio.file.*;
import fr.brickcraft.BrickDirectories;
public class TEST_DIRECTORY_V024 {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("brickcraft-test-");
        try{
            BrickDirectories.ensure(dir);
            if(!Files.isDirectory(BrickDirectories.imports(dir)) ||
                    !Files.isDirectory(BrickDirectories.imports(dir).resolve("reports")) ||
                    !Files.isDirectory(BrickDirectories.root(dir).resolve("parts_custom")) ||
                    !BrickDirectories.ldrawArchive(dir).equals(dir.resolve("brickcraft/complete.zip")))
                throw new IllegalStateException("Bad directory layout");
            System.out.println("OK: BrickCraft config and imports directories");
        }finally{
            Files.deleteIfExists(dir.resolve("brickcraft/imports/reports"));
            Files.deleteIfExists(dir.resolve("brickcraft/imports"));
            Files.deleteIfExists(dir.resolve("brickcraft/parts_custom"));
            Files.deleteIfExists(dir.resolve("brickcraft"));
            Files.deleteIfExists(dir);
        }
    }
}
