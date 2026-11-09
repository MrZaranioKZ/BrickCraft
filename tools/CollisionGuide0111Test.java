import fr.brickcraft.world.PreciseCollisionPlan;
import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseProjectIO;
import fr.brickcraft.importer.PreciseReferenceMapper;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.*;
import java.util.*;

public class CollisionGuide0111Test {
  private static int n;
  private static void check(boolean ok,String reason){n++;if(!ok)throw new AssertionError(reason);}
  private static final double[] IDENT={1,0,0,0,1,0,0,0,1};
  public static void main(String[] a)throws Exception{
    var one=new LDrawProjectReader.Instance("3004",4,0,0,0,IDENT.clone());
    // Baseline: one 1x2 part creates only a small group of occupied voxels.
    var b=PreciseCollisionPlan.build(List.of(one),0,0,0,100,50,200,id->new double[]{-10,-24,-50,10,0,50});
    check(!b.isEmpty(),"empty collisions");check(b.size()<30,"too many voxels");
    check(b.stream().noneMatch(c->c.x()==100&&c.y()==50&&c.z()==200),"anchor changed");
    var twice=PreciseCollisionPlan.build(List.of(one,one),0,0,0,100,50,200,id->new double[]{-10,-24,-50,10,0,50});
    check(twice.equals(b),"duplicates must be merged");
    var shift=new LDrawProjectReader.Instance("3004",4,40,0,0,IDENT.clone());
    var moved=PreciseCollisionPlan.build(List.of(shift),0,0,0,100,50,200,id->new double[]{-10,-24,-50,10,0,50});
    check(!moved.equals(b),"translation ignored");
    var rot=new LDrawProjectReader.Instance("3004",4,0,0,0,new double[]{0,0,1,0,1,0,-1,0,0});
    var turned=PreciseCollisionPlan.build(List.of(rot),0,0,0,100,50,200,id->new double[]{-10,-24,-50,10,0,50});
    check(!turned.equals(b),"rotation ignored");
    try{PreciseCollisionPlan.build(List.of(one),0,0,0,0,0,0,id->null);throw new AssertionError("missing reference");}
    catch(IllegalArgumentException expected){n++;}
    if(a.length>1){
        Path archive=Path.of(a[0]),project=Path.of(a[1]);
        LDrawLibrary.init(archive.getParent());
        var resultModel=PreciseProjectIO.read(project);
        var model=fr.brickcraft.importer.PreciseWorldModel.create(resultModel.parts());
        var result=PreciseCollisionPlan.build(model.parts(),model.minX(),model.minY(),model.minZ(),0,64,0,id->{
            String ref=PreciseReferenceMapper.geometryId(id);
            return ref==null?null:LDrawLibrary.originalBounds(ref);
        });
        check(result.size()<=16000,"castle too many voxels");
        System.out.println("Neuschwanstein : "+model.parts().size()+" pieces, "+result.size()+" cellules physiques approx.");
    }
    System.out.println("OK - "+n+" controle(s) du plan physique");
  }
}
