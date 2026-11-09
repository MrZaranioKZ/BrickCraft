import fr.brickcraft.client.PreviewSelectionMath;
import fr.brickcraft.importer.*;
import java.nio.file.*;
import java.util.*;

/** Regression tests for click picking, overlap cycling, continuous gestures and BCPX integrity. */
public class Interaction098Test {
    private static long tests;
    private static void check(boolean condition,String text){tests++;if(!condition)throw new AssertionError(text);}
    private static void eq(double a,double b,String text){check(Math.abs(a-b)<1e-8,text+" "+a+" != "+b);}
    private static double[][] cube(double x,double y,double z){
        var out=new double[8][3];
        for(int i=0;i<8;i++){out[i]=new double[]{x+((i&1)==0?-10:10),y+((i&2)==0?-10:10),z+((i&4)==0?-10:10)};}
        return out;
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0)fr.brickcraft.ldraw.LDrawLibrary.init(Path.of(args[0]));
        var bounds=new double[]{-150,-150,-150,150,150,150};
        var frame=PreviewSelectionMath.frame(bounds,0,1,0,0,0,0,800,600);
        var a=cube(0,0,0);
        var behind=cube(0,-20,43); // overlapping projected faces, nearer in painter depth
        var list=List.of(a,behind);
        var at=frame.project(0,0,0);
        var hits=PreviewSelectionMath.hits(frame,list,at.x(),at.y());
        check(hits.size()==2,"two superposed parts are hittable");
        check(PreviewSelectionMath.choose(hits,0,false)==1,"pick nearest part");
        check(PreviewSelectionMath.choose(hits,1,true)==0,"Shift+click cycles overlap");
        check(PreviewSelectionMath.choose(hits,0,true)==1,"Shift+click wraps");
        check(PreviewSelectionMath.hits(frame,list,-10000,-10000).isEmpty(),"empty screen misses");
        var origin=frame.project(20,30,40);
        for(int axis=0;axis<3;axis++){
            var p=PreviewSelectionMath.axisEnd(frame,20,30,40,axis,20);
            double amount=PreviewSelectionMath.axisDrag(frame,p.x(),p.y(),origin.x(),origin.y(),20,30,40,axis);
            eq(amount,20,"axis drag projection "+axis);
        }
        eq(PreviewSelectionMath.snap(17.3,4),16,"snap 4 LDU");
        eq(PreviewSelectionMath.snap(-17.3,4),-16,"negative snap");
        eq(PreviewSelectionMath.snap(47,15),45,"rotation snap");
        var input=new LDrawProjectReader.Instance("3005",4,0,0,0,PreciseFreeAssembly.identity());
        var original=new LDrawProjectReader.Result("gesture.ldr",0,List.of(input),List.of());
        var editor=new PreciseFreeAssembly(original);
        check(editor.beginGesture(),"start gesture");
        for(int i=1;i<=80;i++)check(editor.updateGesture(PreciseFreeAssembly.Axis.X,i*.25,false),"update gesture "+i);
        editor.finishGesture();eq(editor.selected().x(),20,"final drag");
        check(editor.undo(),"one undo for eighty mouse events");eq(editor.selected().x(),0,"undo original");
        check(!editor.undo(),"no extra checkpoints");check(editor.redo(),"redo drag");eq(editor.selected().x(),20,"redo destination");
        check(editor.beginGesture(),"start rotation gesture");
        for(int i=1;i<=37;i++)check(editor.updateGesture(PreciseFreeAssembly.Axis.Z,i*.25,true),"rotation update "+i);
        editor.finishGesture();check(editor.undo(),"rotation undo");
        check(Arrays.equals(editor.selected().matrix(),input.matrix()),"rotation undoes bitwise");
        Path temp=Files.createTempFile("interaction098", ".bcpx");
        try{
            PreciseProjectIO.save(temp,editor.result());
            var reload=PreciseProjectIO.read(temp);
            check(reload.parts().size()==1,"save count");
            eq(reload.parts().get(0).x(),20,"saved fractional transform");
        }finally{Files.deleteIfExists(temp);}
        if(args.length>1){
            var castle=PreciseProjectIO.read(Path.of(args[1]));
            check(castle.parts().size()==3245,"real castle count");
            var edit=new PreciseFreeAssembly(castle);
            for(int i=0;i<castle.parts().size();i+=16){
                check(edit.select(i),"castle select "+i);
                var part=edit.selected();
                var originalMatrix=part.matrix();
                check(edit.beginGesture(),"castle begin "+i);
                check(edit.updateGesture(PreciseFreeAssembly.Axis.Y,12.5,true),"castle rotate "+i);
                edit.finishGesture();check(edit.undo(),"castle undo "+i);
                for(int j=0;j<9;j++)check(Double.doubleToLongBits(edit.selected().matrix()[j])==
                    Double.doubleToLongBits(originalMatrix[j]),"matrix unchanged "+i+" "+j);
            }
        }
        System.out.println("BrickCraft 0.9.8 interaction: "+tests+" checks OK");
    }
}
