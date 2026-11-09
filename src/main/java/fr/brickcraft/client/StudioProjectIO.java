package fr.brickcraft.client;

import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Text-only local .bcp format; no network/deserialization of Java objects. */
public final class StudioProjectIO {
    public record Project(int grid, List<BrickPiece> pieces){}
    private static final String HEADER="BRICKCRAFT-PROJECT-2";
    private StudioProjectIO(){}

    public static void save(Path file,StudioAssembly assembly) throws IOException {
        if(file.getParent()!=null)Files.createDirectories(file.getParent());
        List<String> out=new ArrayList<>();
        out.add(HEADER);out.add("GRID " + assembly.gridSize());
        for(BrickPiece p:assembly.pieces()){
            out.add(String.join(" ","P",p.kind().name(),Integer.toString(p.rgb()),
                Integer.toString(p.x()),Integer.toString(p.y()),Integer.toString(p.z()),
                Integer.toString(p.turn()),Integer.toString(p.angle()),
                Integer.toString(p.pitch()),Integer.toString(p.roll()),
                p.partId()==null || p.partId().isEmpty()?"-":p.partId(),Integer.toString(p.colorCode())));
        }
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        Files.write(temp,out,StandardCharsets.UTF_8);
        try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(java.nio.file.AtomicMoveNotSupportedException e){
            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    public static Project read(Path file) throws IOException {
        if(!Files.isRegularFile(file))throw new IOException("Projet introuvable : "+file);
        if(Files.size(file)>1024*1024)throw new IOException("Projet trop volumineux");
        List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
        if(lines.size()<2 || (!lines.get(0).equals(HEADER) && !lines.get(0).equals("BRICKCRAFT-PROJECT-1")))
            throw new IOException("Format projet non reconnu");
        String[] first=lines.get(1).split(" ");
        if(first.length!=2 || !first[0].equals("GRID"))throw new IOException("Grille invalide");
        int grid;
        try{grid=Integer.parseInt(first[1]);}
        catch(NumberFormatException e){throw new IOException("Taille de grille invalide",e);}
        if(grid!=16 && grid!=32 && grid!=64 && grid!=128 && grid!=256)throw new IOException("Grille hors limites");
        if(lines.size()-2>StudioAssembly.MAX_PIECES)throw new IOException("Trop de pieces");
        List<BrickPiece> pieces=new ArrayList<>();
        for(int i=2;i<lines.size();i++) {
            String[] f=lines.get(i).split(" ");
            if((f.length!=11 && f.length!=12) || !f[0].equals("P"))throw new IOException("Ligne "+(i+1)+" invalide");
            try{
                BrickKind kind=BrickKind.valueOf(f[1]);
                String id=f[10].equals("-")?"":f[10];
                BrickPiece p=new BrickPiece(kind,Integer.parseInt(f[2]),Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]),Integer.parseInt(f[5]),Integer.parseInt(f[6]),
                    Integer.parseInt(f[7]),Integer.parseInt(f[8]),Integer.parseInt(f[9]),id,
                    f.length>=12?Integer.parseInt(f[11]):-1);
                pieces.add(p);
            }catch(IllegalArgumentException e){throw new IOException("Piece invalide a la ligne "+(i+1),e);}
        }
        return new Project(grid,List.copyOf(pieces));
    }
}
