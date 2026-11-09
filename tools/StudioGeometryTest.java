import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.importer.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public final class StudioGeometryTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Usage: StudioGeometryTest <config> <bcpx>");
        Path config=Path.of(args[0]);
        LDrawLibrary.init(config);
        System.out.println(LDrawLibrary.status());
        var project=PreciseProjectIO.read(Path.of(args[1]));
        String[] special={"bl_24246pb057","bl_3004pb342","bl_3004pb341","bl_24246pb058","bl_79741","bl_3941pb56","bl_3941pb58","bl_3941pb57","bl_49656pb09"};
        Map<String,Integer> found=new LinkedHashMap<>();
        for(String id:special) {
            String resolved=PreciseReferenceMapper.geometryId(id);
            if(!id.equals(resolved))throw new AssertionError(id+" resolved to "+resolved);
            var mesh=LDrawLibrary.mesh(id);
            if(mesh==null||mesh.isEmpty()||LDrawLibrary.originalBounds(id)==null)
                throw new AssertionError("Missing mesh: "+id);
            found.put(id,mesh.triangles());
        }
        var stats=PreciseWorldModel.analyze(project.parts());
        // 239 printed pieces remain untextured, while all 249 missing shapes
        // now resolve to their matching Studio polygon meshes.
        if(stats.mappedPrints()!=239)throw new AssertionError("Unexpected untextured prints: "+stats.mappedPrints());
        if(project.parts().size()!=3245)throw new AssertionError("Part count changed");
        int extraordinary=0;int exactCustom=0;int checked=0;
        for(var p:project.parts()){
            if(p.partId().startsWith("bl_"))exactCustom++;
            double[] m=p.matrix();
            if(PreciseWorldModel.validMatrix(m))checked++;
            else throw new AssertionError("Not a rotation matrix: "+p.partId());
            if(Math.abs(m[0])+Math.abs(m[1])+Math.abs(m[2])!=1.0)extraordinary++;
        }
        // Roundtrip the world snapshot: exact matrices and positions in saved NBT.
        PreciseWorldModel world=PreciseWorldModel.create(project.parts());
        PreciseWorldModel reread=PreciseWorldModel.readBytes(world.writeBytes());
        for(int i=0;i<world.parts().size();i++){
            var a=world.parts().get(i);var b=reread.parts().get(i);
            if(!a.partId().equals(b.partId()) || a.color()!=b.color() ||
               Double.compare(a.x(),b.x())!=0 || Double.compare(a.y(),b.y())!=0 ||
               Double.compare(a.z(),b.z())!=0 || !Arrays.equals(a.matrix(),b.matrix()))
                throw new AssertionError("Roundtrip changed part "+i);
        }
        System.out.println("TEST PASS. Shapes:"+found+" | parts="+project.parts().size()+
            " | previously_missing="+exactCustom+" | rotations_valid="+checked+" | special_orientations="+extraordinary+
            " | world_bbox="+stats.width()+","+stats.height()+","+stats.depth());
        LDrawLibrary.close();
    }
}
