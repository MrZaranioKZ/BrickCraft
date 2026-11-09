package fr.brickcraft.importer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Versioned lossless snapshot of LDraw's source transforms (LDU doubles and full 3x3 matrices).
 * Separate from the old .bcp grid and Minecraft-world/NBT formats on purpose.
 * Byte stream is compressed and bounded for untrusted project files.
 */
public final class PreciseProjectIO {
    private PreciseProjectIO() {}
    private static final int MAGIC=0x42435058; // BCPX
    private static final int VERSION=1;
    private static final int MAX_PARTS=250_000, MAX_PROBLEMS=8_000;

    public static void save(Path destination, LDrawProjectReader.Result source) throws IOException {
        if(source.parts().size()>MAX_PARTS || source.problems().size()>MAX_PROBLEMS)
            throw new IOException("Projet trop volumineux pour BCPX");
        Path parent=destination.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp=Files.createTempFile(parent,".brickcraft-", ".tmp");
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
                out.writeInt(MAGIC);out.writeInt(VERSION);
                out.writeUTF(source.root());out.writeInt(source.submodels());
                out.writeInt(source.parts().size());
                for(var p:source.parts()) {
                    if(p.partId().length()>256)throw new IOException("Reference trop longue");
                    out.writeUTF(p.partId());out.writeInt(p.color());
                    writeFinite(out,p.x());writeFinite(out,p.y());writeFinite(out,p.z());
                    for(double d:p.matrix())writeFinite(out,d);
                }
                out.writeInt(source.problems().size());
                for(String s:source.problems())out.writeUTF(s.length()>2000?s.substring(0,2000):s);
            }
            try{Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){
                Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{Files.deleteIfExists(temp);}
    }
    private static void writeFinite(DataOutputStream out,double value)throws IOException{
        if(!Double.isFinite(value))throw new IOException("Coordonnee/matrice non finie");
        out.writeDouble(value);
    }
    private static double readFinite(DataInputStream in)throws IOException{
        double value=in.readDouble();if(!Double.isFinite(value))throw new IOException("Coordonnee non finie");return value;
    }
    public static LDrawProjectReader.Result read(Path source)throws IOException {
        if(Files.size(source)>128L*1024*1024)throw new IOException("BCPX trop volumineux");
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(source))))) {
            if(in.readInt()!=MAGIC)throw new IOException("Signature BCPX incorrecte");
            if(in.readInt()!=VERSION)throw new IOException("Version BCPX non prise en charge");
            String root=in.readUTF();int submodels=in.readInt();
            if(submodels<0||submodels>MAX_PARTS)throw new IOException("Nombre de sous-modeles invalide");
            int count=in.readInt();
            if(count<0||count>MAX_PARTS)throw new IOException("Nombre de pieces BCPX invalide");
            List<LDrawProjectReader.Instance> pieces=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                String id=in.readUTF();if(id.isEmpty()||id.length()>256)throw new IOException("Reference BCPX invalide");
                int color=in.readInt();double x=readFinite(in),y=readFinite(in),z=readFinite(in);
                double[] m=new double[9];for(int j=0;j<9;j++)m[j]=readFinite(in);
                pieces.add(new LDrawProjectReader.Instance(id,color,x,y,z,m));
            }
            int n=in.readInt();if(n<0||n>MAX_PROBLEMS)throw new IOException("Messages BCPX invalides");
            List<String> problems=new ArrayList<>(n);
            for(int i=0;i<n;i++)problems.add(in.readUTF());
            if(in.read()!=-1)throw new IOException("Donnees supplementaires dans BCPX");
            return new LDrawProjectReader.Result(root,submodels,pieces,problems);
        }
    }
}
