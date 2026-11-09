import fr.brickcraft.client.WorldGizmoMath;
import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.world.PreciseStudSnapper;
import java.util.*;

public final class TEST_GIZMO_0101 {
    static int count;
    static void check(boolean b,String m){count++;if(!b)throw new AssertionError(m);}
    static void near(double x,double y,String m){check(Math.abs(x-y)<1e-6,m+" got="+x+", expected="+y);}
    static final double[] I={1,0,0,0,1,0,0,0,1};
    static void picks(){
        // Looking from -Z, facing +Z: X and Y handle tips must be selectable, not the origin.
        var x=WorldGizmoMath.pick(new double[]{.8,0,-5},new double[]{0,0,1},new double[]{0,0,0},12,.16);
        check(x!=null&&x.axis()==0,"X handle hit");near(x.distance(),5,"X depth");
        var y=WorldGizmoMath.pick(new double[]{0,.8,-5},new double[]{0,0,1},new double[]{0,0,0},12,.16);
        check(y!=null&&y.axis()==1,"Y handle hit");
        check(WorldGizmoMath.pick(new double[]{0,0,-5},new double[]{0,0,1},new double[]{0,0,0},12,.16)==null,
                "center does not steal selection click");
        check(WorldGizmoMath.pick(new double[]{3,3,-5},new double[]{0,0,1},new double[]{0,0,0},12,.16)==null,
                "distant crosshair does not steal click");
        check(WorldGizmoMath.pick(new double[]{.8,0,-30},new double[]{0,0,1},new double[]{0,0,0},12,.16)==null,
                "out of reach does not select");
        var z=WorldGizmoMath.pick(new double[]{0,-5,.8},new double[]{0,1,0},new double[]{0,0,0},12,.16);
        check(z!=null&&z.axis()==2,"Z handle from vertical angle");
        check(WorldGizmoMath.pick(new double[]{0,0,0},new double[]{0,0,0},new double[]{0,0,0},12,.16)==null,
                "zero direction refused");
    }
    static void drag(){
        near(WorldGizmoMath.dragLdu(100,0,new double[]{0,0,1},0,5),27.5,"horizontal X drag");
        near(WorldGizmoMath.dragLdu(0,-100,new double[]{0,0,1},1,5),27.5,"upward Y drag");
        near(WorldGizmoMath.dragLdu(0,0,new double[]{0,0,1},1,5),0,"no movement");
        near(WorldGizmoMath.dragLdu(100,0,new double[]{1,0,0},0,5),0,"depth axis cannot drag");
        near(WorldGizmoMath.step(13.7,5,80),15,"coarse snap");
        near(WorldGizmoMath.step(-13.7,1,80),-14,"fine snap");
        near(WorldGizmoMath.step(900,1,80),80,"limit packet delta");
        near(WorldGizmoMath.step(-900,5,180),-180,"limit rotation");
    }
    static void connectors(){
        var p=new LDrawProjectReader.Instance("3005",4,0,0,0,I);
        var pts=PreciseStudSnapper.connectorPoints(p,10);
        check(pts.size()==2,"two connectors for brick 1x1");
        check(pts.get(0).top(),"stud top");near(pts.get(0).y(),-24,"top height");
        check(!pts.get(1).top(),"socket bottom");near(pts.get(1).y(),0,"bottom height");
        var plate=new LDrawProjectReader.Instance("3020",4,0,0,0,I);
        check(PreciseStudSnapper.connectorPoints(plate,100).size()==16,"plate 2x4 has 16 points");
        check(PreciseStudSnapper.connectorPoints(plate,3).size()==3,"limited points");
        var unsupported=new LDrawProjectReader.Instance("NON_EXISTENT_PART",4,0,0,0,I);
        check(PreciseStudSnapper.connectorPoints(unsupported,100).isEmpty(),"no invented points");
    }
    public static void main(String[] args){
        picks();drag();connectors();
        Random rng=new Random(1001);
        for(int i=0;i<2000;i++){
            double x=rng.nextDouble()*3-1.5,y=rng.nextDouble()*3-1.5;
            var picked=WorldGizmoMath.pick(new double[]{x,y,-6},new double[]{0,0,1},
                            new double[]{0,0,0},12,.10);
            if(picked!=null){check(picked.axis()<3&&picked.distance()<=12,"random handles valid");}
            double value=WorldGizmoMath.step(rng.nextDouble()*300-150,5,80);
            check(value>=-80&&value<=80&&value%5==0,"quantized delta in bounds");
        }
        System.out.println("OK BrickCraft 0.10.1: "+count+" assertions (ray hit, drag, connectors)");
    }
}
