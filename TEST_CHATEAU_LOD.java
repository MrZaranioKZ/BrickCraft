import fr.brickcraft.client.PreciseMeshLods;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.importer.*;
import java.nio.file.*;
import java.util.*;
public final class TEST_CHATEAU_LOD {
  public static void main(String[] args)throws Exception{
    Path config=Path.of("run","config").toAbsolutePath();
    Path complete=config.resolve("brickcraft").resolve("complete.zip");
    if(!Files.isRegularFile(complete))throw new IllegalStateException("Place complete.zip dans run/config/brickcraft");
    LDrawLibrary.init(config);
    String name=args.length==0?"EXEMPLES/Neuschwanstein_21063.bcpx":args[0];
    var model=PreciseProjectIO.read(Path.of(name));
    Map<String,Integer> count=new HashMap<>();int missing=0;
    for(var p:model.parts()){
      String id=PreciseReferenceMapper.geometryId(p.partId());if(id!=null)count.merge(id,1,Integer::sum);else missing++;
    }
    long triangleRaw=0,triangleTiny=0,footprint=0;int failures=0;
    for(var e:count.entrySet()){
      String id=e.getKey();var m=LDrawLibrary.mesh(id);
      if(m==null||m.isEmpty()){failures++;continue;}
      var lod=PreciseMeshLods.generate(m);
      if(lod.tiny().triangles()>m.triangles()||lod.tiny().triangles()<=0)throw new AssertionError(id);
      triangleRaw+=(long)m.triangles()*e.getValue();triangleTiny+=(long)lod.tiny().triangles()*e.getValue();footprint+=lod.cachedTriangles();
    }
    System.out.printf("CHATEAU : %d pieces, %d forme(s), references introuvables %d, maillages sans geomet %d%n",model.parts().size(),count.size(),missing,failures);
    System.out.printf("Tri par instance total raw=%,d tiny=%,d, reduction %.1f%%; cache unique=%,d%n",triangleRaw,triangleTiny,100.*(triangleRaw-triangleTiny)/triangleRaw,footprint);
  }
}
