package net.fabricmc.loader.api;
import java.nio.file.Path;
public class FabricLoader{public static FabricLoader getInstance(){return new FabricLoader();}public Path getConfigDir(){return Path.of("/tmp");}}
