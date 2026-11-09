import fr.brickcraft.client.PreciseMeshLods;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.*;
import java.util.*;
public final class TEST_LOD_FORMES {
  static int checks=0;
  static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception{
    float[] xyz={0,0,0, 1,0,0, 1,1,0, 0,0,0, 1,1,0, 0,1,0, 0,0,0, 1,0,0, 1,1,0};
    var synthetic=new LDrawLibrary.Mesh(xyz,new int[]{16,16,16},1,1,1,3);
    var levels=PreciseMeshLods.generate(synthetic);
    check(levels.tiny().triangles()<=3,"coarse triangle reduction");
    check(levels.tiny().triangles()==2,"duplicate same-color triangle removed");
    check(levels.full().triangles()==3,"keep exact source");
    var colorDiff=new LDrawLibrary.Mesh(xyz,new int[]{16,16,4},1,1,1,3);
    check(PreciseMeshLods.simplify(colorDiff,4).triangles()==3,"different face colors retained");
    Path config=Path.of("run","config").toAbsolutePath();
    Path complete=Path.of("run","config","brickcraft","complete.zip").toAbsolutePath();
    if(!Files.isRegularFile(complete)){
       System.out.println("SKIP archive : copie complete.zip dans run/config/brickcraft pour le test reel.");
       System.out.printf("PASS : %d assertions synthétiques%n",checks);return;
    }
    LDrawLibrary.init(config);
    long full=0,tiny=0,medium=0;
    for(String id:List.of("3004","3005","3001","3023","24246","3941")){
      var mesh=LDrawLibrary.mesh(id);
      if(mesh==null){System.out.println("missing: "+id);continue;}
      var lod=PreciseMeshLods.generate(mesh);
      check(lod.tiny().triangles()>0,"nonempty "+id);
      check(lod.tiny().triangles()<=mesh.triangles(),"no increase "+id);
      full+=mesh.triangles();tiny+=lod.tiny().triangles();medium+=lod.medium().triangles();
      System.out.printf("%s : %d -> %d -> %d -> %d triangles%n",id,mesh.triangles(),lod.medium().triangles(),lod.low().triangles(),lod.tiny().triangles());
    }
    System.out.printf("PASS : %d assertions; six sample parts triangles %d -> %d (medium), %d (tiny)%n",checks,full,medium,tiny);
  }
}
