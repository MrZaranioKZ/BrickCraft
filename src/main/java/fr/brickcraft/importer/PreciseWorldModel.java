package fr.brickcraft.importer;

import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.core.BrickCapacityConfig;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Immutable, lossless LDraw assembly for a world-space VISUAL projection (not physical LEGO). */
public final class PreciseWorldModel {
    public static final int MAX_PARTS=BrickCapacityConfig.HARD_PARTS;
    private static final int MAGIC_V1=0x42505731; // BPW1 legacy
    private static final int MAGIC=0x42505732; // BPW2 with FIXED local projection origin
    private final List<LDrawProjectReader.Instance> parts;
    private final double minX,minY,minZ,maxX,maxY,maxZ;
    private final int mappedPrints;

    public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int mappedPrints) {
        public double width(){return maxX-minX;}
        public double height(){return maxY-minY;}
        public double depth(){return maxZ-minZ;}
    }
    private PreciseWorldModel(List<LDrawProjectReader.Instance> parts,Bounds bounds){
        this.parts=List.copyOf(parts);
        this.minX=bounds.minX();this.minY=bounds.minY();this.minZ=bounds.minZ();
        this.maxX=bounds.maxX();this.maxY=bounds.maxY();this.maxZ=bounds.maxZ();
        this.mappedPrints=bounds.mappedPrints();
    }
    public List<LDrawProjectReader.Instance> parts(){return parts;}
    public int mappedPrints(){return mappedPrints;}
    public double minX(){return minX;}public double minY(){return minY;}public double minZ(){return minZ;}
    public double maxX(){return maxX;}public double maxY(){return maxY;}public double maxZ(){return maxZ;}
    public double width(){return maxX-minX;}public double height(){return maxY-minY;}public double depth(){return maxZ-minZ;}
    public static boolean validMatrix(double[] m){
        if(m==null||m.length!=9)return false;
        for(double v:m)if(!Double.isFinite(v)||Math.abs(v)>1.001)return false;
        for(int i=0;i<3;i++)for(int j=i;j<3;j++){
            double d=0;for(int k=0;k<3;k++)d+=m[i*3+k]*m[j*3+k];
            if(Math.abs(d-(i==j?1:0))>.025)return false;
        }
        return true;
    }
    public static Bounds analyze(List<LDrawProjectReader.Instance> parts){
        if(parts.isEmpty()||parts.size()>MAX_PARTS)throw new IllegalArgumentException("Projet : limite technique de "+MAX_PARTS+" pieces");
        double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        int prints=0;
        // Large projects often reuse the same few hundred designs thousands of times.
        // Prevent repeated expensive ZIP geometry expansion when LDrawLibrary's LRU rolls over.
        Map<String,double[]> boundsCache=new HashMap<>();
        for(LDrawProjectReader.Instance p:parts){
            if(p.partId()==null || p.partId().length()>96 || !p.partId().matches("[A-Za-z0-9_./-]{1,96}") ||
                    !Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()) ||
                    Math.abs(p.x())>100000 || Math.abs(p.y())>100000 || Math.abs(p.z())>100000 ||
                    !validMatrix(p.matrix()))throw new IllegalArgumentException("Position, reference ou matrice invalide : "+p.partId());
            String resolved=PreciseReferenceMapper.geometryId(p.partId());
            if(resolved==null)throw new IllegalArgumentException("Modele LDraw introuvable : "+p.partId());
            if(PreciseReferenceMapper.losesPrint(p.partId(),resolved))prints++;
            double[] b=boundsCache.computeIfAbsent(resolved,LDrawLibrary::originalBounds);
            if(b==null)throw new IllegalArgumentException("Geometrie LDraw non chargeable : "+p.partId());
            double[] m=p.matrix();
            // LDraw Y increases down. Minecraft Y increases up.
            for(int mask=0;mask<8;mask++){
                double lx=b[(mask&1)==0?0:3],ly=b[(mask&2)==0?1:4],lz=b[(mask&4)==0?2:5];
                double x=(p.x()+m[0]*lx+m[1]*ly+m[2]*lz)/20.0;
                double y=-(p.y()+m[3]*lx+m[4]*ly+m[5]*lz)/20.0;
                double z=(p.z()+m[6]*lx+m[7]*ly+m[8]*lz)/20.0;
                double[] v={x,y,z};
                for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],v[i]);hi[i]=Math.max(hi[i],v[i]);}
            }
        }
        Bounds bounds=new Bounds(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2],prints);
        if(bounds.width()>256 || bounds.depth()>256 || bounds.height()>256)
            throw new IllegalArgumentException("Projection depasse 256 blocs sur un axe");
        return bounds;
    }
    public static PreciseWorldModel create(List<LDrawProjectReader.Instance> parts){
        return new PreciseWorldModel(parts,analyze(parts));
    }
    /** Editing any extreme part MUST NOT move all the other parts of the projection. */
    public static PreciseWorldModel editKeepingOrigin(List<LDrawProjectReader.Instance> parts,
                                                        PreciseWorldModel before){
        Bounds bounds=analyze(parts);
        if(!Double.isFinite(before.minX)||!Double.isFinite(before.minY)||!Double.isFinite(before.minZ))
            throw new IllegalArgumentException("Origine de la projection non valide");
        if(Math.max(Math.max(Math.abs(bounds.maxX-before.minX),Math.abs(bounds.minX-before.minX)),
                    Math.max(Math.abs(bounds.maxZ-before.minZ),Math.abs(bounds.minZ-before.minZ)))>320 ||
                Math.max(Math.abs(bounds.maxY-before.minY),Math.abs(bounds.minY-before.minY))>320)
            throw new IllegalArgumentException("Edition hors volume maximum de la projection");
        return new PreciseWorldModel(parts,new Bounds(before.minX,before.minY,before.minZ,
                bounds.maxX,bounds.maxY,bounds.maxZ,bounds.mappedPrints));
    }
    /** Short compressed representation for chunk NBT and block-entity update packets. */
    public byte[] writeBytes()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))){
            out.writeInt(MAGIC);out.writeInt(parts.size());
            out.writeDouble(minX);out.writeDouble(minY);out.writeDouble(minZ);
            for(var p:parts){
                out.writeUTF(p.partId());out.writeInt(p.color());
                out.writeDouble(p.x());out.writeDouble(p.y());out.writeDouble(p.z());
                for(double d:p.matrix())out.writeDouble(d);
            }
        }
        byte[] result=bytes.toByteArray();
        if(result.length>12*1024*1024)throw new IOException("Projection compressee trop volumineuse");
        return result;
    }
    public static PreciseWorldModel readBytes(byte[] bytes)throws IOException{
        if(bytes.length<12 || bytes.length>12*1024*1024)throw new IOException("NBT precise trop volumineux ou vide");
        try(var in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))){
            int version=in.readInt();
            if(version!=MAGIC && version!=MAGIC_V1)throw new IOException("Signature precise invalide");
            int count=in.readInt();if(count<1||count>MAX_PARTS)throw new IOException("Nombre de pieces invalide");
            double[] origin=null;
            if(version==MAGIC){
                origin=new double[]{in.readDouble(),in.readDouble(),in.readDouble()};
                for(double value:origin)if(!Double.isFinite(value)||Math.abs(value)>5000)
                    throw new IOException("Origine BCPX invalide");
            }
            ArrayList<LDrawProjectReader.Instance> parts=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                String id=in.readUTF();int color=in.readInt();
                double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();
                double[] m=new double[9];for(int j=0;j<9;j++)m[j]=in.readDouble();
                if(id.isEmpty()||id.length()>96||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!validMatrix(m))
                    throw new IOException("Corruption du projet precise");
                parts.add(new LDrawProjectReader.Instance(id,color,x,y,z,m));
            }
            if(in.read()!=-1)throw new IOException("Donnees inattendues");
            PreciseWorldModel base=create(parts);
            if(origin==null)return base;
            if(Math.max(Math.abs(base.maxX()-origin[0]),Math.abs(base.minX()-origin[0]))>320 ||
               Math.max(Math.abs(base.maxY()-origin[1]),Math.abs(base.minY()-origin[1]))>320 ||
               Math.max(Math.abs(base.maxZ()-origin[2]),Math.abs(base.minZ()-origin[2]))>320)
                throw new IOException("Origine BCPX trop eloignee");
            return new PreciseWorldModel(parts,new Bounds(origin[0],origin[1],origin[2],
                    base.maxX(),base.maxY(),base.maxZ(),base.mappedPrints()));
        }catch(IllegalArgumentException ex){throw new IOException("Donnees invalides : "+ex.getMessage(),ex);}
    }
}
