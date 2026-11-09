package fr.brickcraft.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persist the triangle-detail slider without overwriting unrelated GPU settings. */
public final class BrickGpuQualityConfig {
    private BrickGpuQualityConfig(){}
    private static Path file(Path configDir){return configDir.resolve("brickcraft").resolve("precise-render.properties");}
    public static int load(Path dir){
        Properties p=new Properties();Path file=file(dir);
        try{if(Files.isRegularFile(file))try(var in=Files.newInputStream(file)){p.load(in);}}
        catch(IOException ignored){}
        int fallback=switch(p.getProperty("gpu_mesh_quality","low").trim().toLowerCase(java.util.Locale.ROOT)){
            case "tiny"->10;case "medium"->60;case "full"->100;default->25;
        };
        try{return Math.clamp(Integer.parseInt(p.getProperty("gpu_mesh_detail_percent","")),0,100);}
        catch(NumberFormatException ignored){return fallback;}
    }
    /** Opt-in for experimental GPU toggles F7/F10. Hidden from ordinary controls. */
    public static boolean experimentalDiagnostics(Path dir){
        Path path=file(dir);
        if(!Files.isRegularFile(path))return false;
        Properties settings=new Properties();
        try(var in=Files.newInputStream(path)){
            settings.load(in);
            return Boolean.parseBoolean(settings.getProperty("experimental_gpu_diagnostics","false"));
        }catch(IOException ignored){return false;}
    }
    public static void save(Path dir,int percent)throws IOException{
        if(percent<0||percent>100)throw new IllegalArgumentException("Detail 0..100");
        Path file=file(dir);Properties p=new Properties();Files.createDirectories(file.getParent());
        if(Files.isRegularFile(file))try(var in=Files.newInputStream(file)){p.load(in);}
        p.setProperty("gpu_mesh_detail_percent",Integer.toString(percent));
        try(var out=Files.newOutputStream(file)){p.store(out,"BrickCraft GPU - geometric mesh detail percent; 100 = full original LDraw triangles");}
    }
}
