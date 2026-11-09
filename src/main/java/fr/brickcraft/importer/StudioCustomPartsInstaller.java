package fr.brickcraft.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Optional import of existing CustomParts/*.dat embedded in an UNENCRYPTED .io archive.
 * Files are *data only*, isolated under parts_custom and never overwrite installed parts. */
public final class StudioCustomPartsInstaller {
    private StudioCustomPartsInstaller(){}
    public record Result(int added,int skipped){}
    private static final int MAX_FILES=200, MAX_SIZE=2_000_000;
    /** Returns relative paths such as parts/61280.dat, parts/s/... or p/rect.dat. */
    public static String partPath(String zipName){
        if(zipName==null||!zipName.startsWith("CustomParts/")||!zipName.endsWith(".dat"))return null;
        String name=zipName.substring("CustomParts/".length()).replace('\\','/');
        if(name.startsWith("s/s/"))name="s/"+name.substring(4);
        if(name.startsWith("parts/"))name=name.substring(6);
        if(name.startsWith("p/"))return name.matches("p/[A-Za-z0-9_.-]+\\.dat")?name:null;
        if(name.startsWith("s/"))return name.matches("s/[A-Za-z0-9_.-]+\\.dat")?"parts/"+name:null;
        return name.matches("[A-Za-z0-9_.-]+\\.dat")?"parts/"+name:null;
    }
    public static Result install(Path io,Path customRoot)throws IOException {
        if(!io.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".io"))
            throw new IOException("Fichier .io requis");
        Files.createDirectories(customRoot);
        int added=0,skipped=0,count=0;
        try(ZipFile zip=new ZipFile(io.toFile())){
            var entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();if(entry.isDirectory())continue;
                String relative=partPath(entry.getName());if(relative==null)continue;
                if(++count>MAX_FILES)throw new IOException("Plus de 200 formes jointes");
                Path target=customRoot.toAbsolutePath().normalize().resolve(relative).normalize();
                if(!target.startsWith(customRoot.toAbsolutePath().normalize()))
                    throw new IOException("Chemin de piece non autorise");
                if(Files.exists(target)){skipped++;continue;}
                if(entry.getSize()>MAX_SIZE)throw new IOException("Piece jointe trop volumineuse : "+relative);
                try(InputStream in=zip.getInputStream(entry)){
                    byte[] bytes=in.readNBytes(MAX_SIZE+1);
                    if(bytes.length>MAX_SIZE)throw new IOException("Piece jointe trop volumineuse : "+relative);
                    // Require LDraw text; do not accept an arbitrary binary payload.
                    String first=new String(bytes,0,Math.min(160,bytes.length),StandardCharsets.UTF_8).stripLeading();
                    if(!first.startsWith("0 ")&&!first.startsWith("1 ")&&!first.startsWith("3 ")&&!first.startsWith("4 ")){
                        skipped++;continue;
                    }
                    Files.createDirectories(target.getParent());
                    Files.write(target,bytes);added++;
                }
            }
        }catch(java.util.zip.ZipException ex){throw new IOException("Pieces jointes illisibles : archive .io chiffree ou invalide",ex);}
        return new Result(added,skipped);
    }
    public static Map<String,String> names(Path io){
        Map<String,String> names=new HashMap<>();
        if(io==null || !io.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".io"))return Map.of();
        try(ZipFile zip=new ZipFile(io.toFile())){
            var entries=zip.entries();int count=0;
            while(entries.hasMoreElements() && count<MAX_FILES){
                ZipEntry entry=entries.nextElement();String path=partPath(entry.getName());
                if(path==null||entry.getSize()>MAX_SIZE)continue;count++;
                if(path.startsWith("parts/s/")||path.startsWith("p/"))continue;
                try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(zip.getInputStream(entry),StandardCharsets.UTF_8))){
                    String first=reader.readLine(),second=reader.readLine();
                    if(second!=null && second.startsWith("0 ") && !second.startsWith("0 Name:"))
                        names.put(path.substring(6,path.length()-4).toLowerCase(java.util.Locale.ROOT),second.substring(2).trim());
                    else if(first!=null && first.startsWith("0 ")&&!first.startsWith("0 FILE"))
                        names.put(path.substring(6,path.length()-4).toLowerCase(java.util.Locale.ROOT),first.substring(2).trim());
                }
            }
        }catch(Exception ignored){}
        return Map.copyOf(names);
    }
}
