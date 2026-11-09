package net.fabricmc.loader.api;
import java.nio.file.Path;
public final class FabricLoader {
    public static FabricLoader getInstance(){return new FabricLoader();}
    public Path getConfigDir(){return Path.of(System.getProperty("java.io.tmpdir"),"brickcraft-render-mock");}
}
