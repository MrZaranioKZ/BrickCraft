package fr.brickcraft.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Separate user preferences from the technical boundaries necessary for safe NBT/network decoding.
 *  0 means no user-selected cap, NOT infinite RAM, ticks or Minecraft world space. */
public final class BrickCapacityConfig {
    private BrickCapacityConfig(){}
    public static final int NO_LIMIT=0;
    public static final int HARD_PARTS=250_000;
    public static final int HARD_CELLS=400_000;
    public static final int HARD_TRIANGLES=20_000_000;
    public static final int[] PART_STEPS={4096,8192,16384,32768,65536,100000,160000,250000,NO_LIMIT};
    public static final int[] CELL_STEPS={8000,16000,32000,64000,128000,256000,400000,NO_LIMIT};
    private static volatile int userParts=16384,userCells=16000;
    private static Path configFile;
    public static synchronized void init(Path configDir){
        configFile=configDir.resolve("brickcraft").resolve("capacity.properties");
        Properties values=new Properties();
        if(Files.isRegularFile(configFile))try(InputStream in=Files.newInputStream(configFile)){
            values.load(in);
        }catch(IOException ignored){}
        userParts=sanitize(values.getProperty("projection_pieces"),16384,HARD_PARTS);
        userCells=sanitize(values.getProperty("physique_cellules"),16000,HARD_CELLS);
    }
    private static int sanitize(String v,int fallback,int upper){
        try{int n=Integer.parseInt(v);return n==0?0:Math.clamp(n,1,upper);}
        catch(Exception ignored){return fallback;}
    }
    public static int partsSetting(){return userParts;}
    public static int cellsSetting(){return userCells;}
    public static int effectiveParts(){return userParts==0?HARD_PARTS:Math.min(userParts,HARD_PARTS);}
    public static int effectiveCells(){return userCells==0?HARD_CELLS:Math.min(userCells,HARD_CELLS);}
    public static int effectiveTriangles(){
        if(userCells==0)return HARD_TRIANGLES;
        return (int)Math.min(HARD_TRIANGLES,Math.max(2_000_000L, (long)effectiveCells()*125L));
    }
    public static String label(int setting){return setting==0?"Sans plafond choisi":String.format(java.util.Locale.FRANCE,"%,d",setting);}
    public static synchronized void save(int parts,int cells)throws IOException{
        if(!valid(parts,HARD_PARTS)||!valid(cells,HARD_CELLS))throw new IOException("Valeur de capacite invalide");
        if(configFile==null)throw new IOException("Configuration non initialisee");
        Files.createDirectories(configFile.getParent());
        Properties props=new Properties();props.setProperty("projection_pieces",Integer.toString(parts));
        props.setProperty("physique_cellules",Integer.toString(cells));
        try(OutputStream out=Files.newOutputStream(configFile)){props.store(out,"BrickCraft - user-selected capacity limits");}
        userParts=parts;userCells=cells;
    }
    private static boolean valid(int n,int upper){return n==0||n>=1&&n<=upper;}
}
