import fr.brickcraft.importer.*;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.client.PreciseGpuPlan;
import java.nio.file.*;
import java.util.*;
/** Coverage of all real .bcpx pieces, independent of camera orientation. */
public class GpuCastleCoverageTest {
 static int checks;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  Path config=Path.of(args[0]);Path file=Path.of(args[1]);
  LDrawLibrary.init(config);
  var result=PreciseProjectIO.read(file);
  PreciseWorldModel model=PreciseWorldModel.create(result.parts());
  ok(result.parts().size()==3245,"whole castle loaded");
  ArrayList<PreciseGpuPlan.Point> points=new ArrayList<>();
  for(var part:model.parts()){
   var id=PreciseReferenceMapper.geometryId(part.partId());ok(id!=null,"reference: "+part.partId());
   double[] b=LDrawLibrary.originalBounds(id);ok(b!=null,"bounds: "+id);
   double[] m=part.matrix();
   double cx=(b[0]+b[3])*.5,cy=(b[1]+b[4])*.5,cz=(b[2]+b[5])*.5;
   double x=(part.x()+m[0]*cx+m[1]*cy+m[2]*cz)/20-model.minX();
   double y=-(part.y()+m[3]*cx+m[4]*cy+m[5]*cz)/20-model.minY();
   double z=(part.z()+m[6]*cx+m[7]*cy+m[8]*cz)/20-model.minZ();
   points.add(new PreciseGpuPlan.Point(x,y,z));
  }
  var sections=PreciseGpuPlan.group(points,12);
  ok(sections.values().stream().mapToInt(List::size).sum()==3245,"no missing parts from plan");
  ok(!sections.isEmpty(),"sections are nonempty");
  System.out.println("CASTLE GPU coverage PASS : "+checks+" checks, "+sections.size()+" sections, "+points.size()+" parts");
 }
}
