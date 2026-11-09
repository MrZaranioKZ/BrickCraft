package fr.brickcraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic, safe workspace locations; never downloads or extracts LDraw for users. */
public final class BrickDirectories {
    private BrickDirectories() {}
    public static Path root(Path configDir){return configDir.resolve("brickcraft");}
    public static Path imports(Path configDir){return root(configDir).resolve("imports");}
    public static Path ldrawArchive(Path configDir){return root(configDir).resolve("complete.zip");}
    public static void ensure(Path configDir) throws IOException {
        Path home=root(configDir);
        Files.createDirectories(home);
        Files.createDirectories(imports(configDir));
        Files.createDirectories(imports(configDir).resolve("reports"));
        Files.createDirectories(home.resolve("parts_custom"));
    }
}
