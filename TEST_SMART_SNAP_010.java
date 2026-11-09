import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.world.PreciseStudSnapper;
import java.util.*;

public final class TEST_SMART_SNAP_010 {
    private static final double[] I={1,0,0,0,1,0,0,0,1};
    private static final double[] ROT_Y_90={0,0,1,0,1,0,-1,0,0};
    private static final double[] ROT_X_90={1,0,0,0,0,-1,0,1,0};
    private static int checks;
    private static LDrawProjectReader.Instance p(String id,double x,double y,double z,double[] m){
        return new LDrawProjectReader.Instance(id,4,x,y,z,m);
    }
    private static void ok(boolean condition,String explanation){
        checks++;if(!condition)throw new AssertionError(explanation);
    }
    private static void near(double actual,double expected,String explanation){
        ok(Math.abs(actual-expected)<1e-6,explanation+" got "+actual+" expected "+expected);
    }
    private static void testBasic(){
        var a=p("3005",0,0,0,I);var b=p("3005",3,-22,-4,I);
        var r=PreciseStudSnapper.snap(List.of(a,b),1,10);
        ok(r.snapped(),"stud 1x1");ok(r.supportIndex()==0,"support index");
        near(r.piece().x(),0,"stud X");near(r.piece().y(),-24,"stud Y");near(r.piece().z(),0,"stud Z");
        ok(Arrays.equals(r.piece().matrix(),I),"matrix intact");
        var no=PreciseStudSnapper.snap(List.of(a,p("3005",30,-22,-4,I)),1,10);
        ok(!no.snapped(),"far away never teleports");
    }
    private static void testPlate(){
        var a=p("3023",0,0,0,I);var b=p("3024",2,-9,12,I);
        var r=PreciseStudSnapper.snap(List.of(a,b),1,10);
        ok(r.snapped(),"plate attaches");near(r.piece().x(),0,"plate x");
        near(r.piece().y(),-8,"plate height");near(r.piece().z(),10,"plate z");
        var tile=p("3069b",0,-8,10,I);
        ok(PreciseStudSnapper.profile("parts/3069b.dat")!=null,"prefix and dat extension");
        var tileSnap=PreciseStudSnapper.snap(List.of(a,tile),1,10);
        ok(!tileSnap.snapped(),"already aligned must be a no-op");
    }
    private static void testRotation(){
        var a=p("3005",0,0,0,ROT_Y_90);var b=p("3005",-3,-25,2,ROT_Y_90);
        var r=PreciseStudSnapper.snap(List.of(a,b),1,10);
        ok(r.snapped(),"90deg matrix snap");near(r.piece().x(),0,"y90 x");
        near(r.piece().y(),-24,"y90 y");near(r.piece().z(),0,"y90 z");
        ok(Arrays.equals(r.piece().matrix(),ROT_Y_90),"rotation kept");
        a=p("3005",0,0,0,ROT_X_90);b=p("3005",4,1,-21,ROT_X_90);
        r=PreciseStudSnapper.snap(List.of(a,b),1,10);
        ok(r.snapped(),"sideways matrix can attach");near(r.piece().x(),0,"side x");
        near(r.piece().y(),0,"side y");near(r.piece().z(),-24,"side z");
    }
    private static void testRefusal(){
        var a=p("3005",0,0,0,I);var unknown=p("Technic_999",2,-24,0,I);
        ok(!PreciseStudSnapper.snap(List.of(a,unknown),1,10).snapped(),"unknown cannot invent sockets");
        var incompatible=p("3005",0,-24,0,ROT_X_90);
        ok(!PreciseStudSnapper.snap(List.of(a,incompatible),1,10).snapped(),"bad normals refused");
        var blocker=p("3005",0,-24,0,I);var moving=p("3005",2,-22,0,I);
        ok(!PreciseStudSnapper.snap(List.of(a,blocker,moving),2,10).snapped(),"occupied position refused");
        ok(PreciseStudSnapper.profile("s/3005.dat")==null,"primitive not brick");
        ok(PreciseStudSnapper.profile("123456789")==null,"unknown part");
        for(double d:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,20.5}) {
            try{PreciseStudSnapper.snap(List.of(a),0,d);throw new AssertionError("should reject "+d);}
            catch(IllegalArgumentException expected){checks++;}
        }
    }
    private static void testRandomOffsets(){
        var random=new Random(9121);
        for(int trial=0;trial<250;trial++){
            double sx=(random.nextInt(500)-250)*20,sy=(random.nextInt(100)-50)*8;
            double sz=(random.nextInt(500)-250)*20;
            var support=p("3005",sx,sy,sz,I);
            double dx=random.nextDouble()*12-6,dy=random.nextDouble()*12-6,dz=random.nextDouble()*12-6;
            var piece=p("3005",sx+dx,sy-24+dy,sz+dz,I);
            var r=PreciseStudSnapper.snap(List.of(support,piece),1,10);
            ok(r.snapped(),"random near offset trial="+trial);
            near(r.piece().x(),sx,"offset X "+trial);
            near(r.piece().y(),sy-24,"offset Y "+trial);
            near(r.piece().z(),sz,"offset Z "+trial);
        }
    }
    public static void main(String[] args){
        testBasic();testPlate();testRotation();testRefusal();testRandomOffsets();
        System.out.println("OK BrickCraft 0.10.0: "+checks+" checks (geometry, sideways, safe refusals, random offsets)");
    }
}
