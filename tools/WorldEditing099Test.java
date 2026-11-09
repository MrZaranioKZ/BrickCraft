import fr.brickcraft.client.BrickGpuQualityConfig;
import fr.brickcraft.client.PreciseMeshLods;
import fr.brickcraft.client.WorldPiecePicker;
import fr.brickcraft.importer.*;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.world.PreciseWorldEditMath;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.zip.GZIPOutputStream;

/** Pure Java regression: ray picker, incremental matrices, NBT-origin, quality profiles. */
public class WorldEditing099Test {
    static int tests=0;
    static void yes(boolean b,String message){tests++;if(!b)throw new AssertionError(message);}
    static void close(double a,double b,String message){yes(Math.abs(a-b)<1e-8,message+" "+a+" vs "+b);}
    static final double[] ID=PreciseFreeAssembly.identity();
    static LDrawProjectReader.Instance part(String name,double x,double y,double z,double[] m){
        return new LDrawProjectReader.Instance(name,4,x,y,z,m);
    }
    static final double[] BOX={0,0,0,20,20,20};
    static void ray(){
        var one=part("3005",0,0,0,ID);
        var hits=WorldPiecePicker.hits(List.of(one),0,0,0,0,0,0,
                -5,-.5,.5,1,0,0,12,id->BOX);
        yes(hits.size()==1,"ray hits oriented volume");close(hits.get(0).distance(),5,"ray entry distance");
        yes(WorldPiecePicker.hits(List.of(one),0,0,0,0,0,0,
                -5,3,.5,1,0,0,12,id->BOX).isEmpty(),"ray miss vertical");
        yes(WorldPiecePicker.hits(List.of(one),0,0,0,0,0,0,
                -5,-.5,.5,1,0,0,4,id->BOX).isEmpty(),"reach blocks far hit");
        var two=List.of(one,part("3005",0,0,0,ID));
        var overlap=WorldPiecePicker.hits(two,0,0,0,0,0,0,-5,-.5,.5,1,0,0,12,id->BOX);
        yes(overlap.size()==2,"overlapping picks");
        yes(WorldPiecePicker.choose(overlap,0,true).index()==1,"cycle overlapping");
        yes(WorldPiecePicker.choose(overlap,1,true).index()==0,"cycle wraps");
        for(double angle:new double[]{0,15,30,45,90,135,180,270}){
            var p=part("3005",0,0,0,PreciseFreeAssembly.rotation(PreciseFreeAssembly.Axis.Y,angle));
            double[][] corners=WorldPiecePicker.corners(p,BOX,0,0,0,0,0,0);
            double cx=0,cy=0,cz=0;for(var c:corners){cx+=c[0];cy+=c[1];cz+=c[2];}cx/=8;cy/=8;cz/=8;
            var ray=WorldPiecePicker.hits(List.of(p),0,0,0,0,0,0,cx-3,cy,cz,1,0,0,12,id->BOX);
            yes(ray.size()==1,"picking rotated "+angle);
        }
    }
    static void editAndPersist()throws Exception{
        var rotated=PreciseFreeAssembly.rotation(PreciseFreeAssembly.Axis.Y,37.25);
        var a=part("3005",0,0,0,ID);var b=part("3005",40,0,0,rotated);
        var model=PreciseWorldModel.create(List.of(a,b));
        double originalMinX=model.minX(),originalMinY=model.minY(),originalMinZ=model.minZ();
        var changed=PreciseWorldEditMath.apply(a,PreciseWorldEditMath.MOVE,0,-20);
        var after=PreciseWorldModel.editKeepingOrigin(List.of(changed,b),model);
        close(after.minX(),originalMinX,"fixed X origin");
        close(after.minY(),originalMinY,"fixed Y origin");
        close(after.minZ(),originalMinZ,"fixed Z origin");
        yes(changed.x()==-20,"move transformed part");
        double[][] a1=WorldPiecePicker.corners(b,LDrawLibrary.originalBounds("3005"),model.minX(),model.minY(),model.minZ(),0,0,0);
        double[][] a2=WorldPiecePicker.corners(b,LDrawLibrary.originalBounds("3005"),after.minX(),after.minY(),after.minZ(),0,0,0);
        for(int i=0;i<8;i++)for(int j=0;j<3;j++)close(a1[i][j],a2[i][j],"other pieces do not shift");
        var turned=PreciseWorldEditMath.apply(b,PreciseWorldEditMath.ROTATE,2,17.5);
        yes(PreciseWorldModel.validMatrix(turned.matrix()),"rotation remains orthogonal");
        var reset=PreciseWorldEditMath.apply(turned,PreciseWorldEditMath.RESET,0,0);
        yes(Arrays.equals(reset.matrix(),ID),"orientation reset");
        byte[] packed=after.writeBytes();
        var roundtrip=PreciseWorldModel.readBytes(packed);
        yes(roundtrip.parts().size()==2,"roundtrip part count");
        close(roundtrip.minX(),originalMinX,"roundtrip fixed origin");
        close(roundtrip.parts().get(0).x(),-20,"roundtrip edited location");
        // BPW1 snapshots produced by 0.9.1-0.9.8 must still open.
        var legacyBuffer=new ByteArrayOutputStream();
        try(var out=new java.io.DataOutputStream(new GZIPOutputStream(legacyBuffer))){
            out.writeInt(0x42505731);out.writeInt(2);
            for(var p:List.of(a,b)){
                out.writeUTF(p.partId());out.writeInt(p.color());
                out.writeDouble(p.x());out.writeDouble(p.y());out.writeDouble(p.z());
                for(double d:p.matrix())out.writeDouble(d);
            }
        }
        var legacy=PreciseWorldModel.readBytes(legacyBuffer.toByteArray());
        yes(legacy.parts().size()==2,"legacy BPW1 still readable");
    }
    static void meshAndSettings()throws Exception{
        var full=LDrawLibrary.mesh("3005");
        yes(full!=null&&full.triangles()>0,"LDraw mesh present");
        for(int subdivisions:new int[]{4,8,12,16,24,32,64}){
            var sim=PreciseMeshLods.simplify(full,subdivisions);
            yes(sim.triangles()>0&&sim.triangles()<=full.triangles(),"LOD mesh triangle limit "+subdivisions);
        }
        Path dir=Files.createTempDirectory("brickcraft099quality");
        try{
            var file=dir.resolve("brickcraft").resolve("precise-render.properties");
            Files.createDirectories(file.getParent());Files.writeString(file,"gpu_mesh_quality=low\ngpu_max_memory_mb=192\n");
            yes(BrickGpuQualityConfig.load(dir)==25,"legacy quality preset mapped");
            BrickGpuQualityConfig.save(dir,100);yes(BrickGpuQualityConfig.load(dir)==100,"100% quality saved");
            yes(Files.readString(file).contains("gpu_max_memory_mb=192"),"unrelated settings preserved");
        }finally{try(var files=Files.walk(dir)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
    }
    public static void main(String[] args)throws Exception{
        LDrawLibrary.init(Path.of(args[0]));ray();editAndPersist();meshAndSettings();
        System.out.println("BrickCraft 0.9.9 : "+tests+" tests monde/qualite OK");
    }
}
