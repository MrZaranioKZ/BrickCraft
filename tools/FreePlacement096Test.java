import fr.brickcraft.importer.*;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.*;
import java.util.*;

public final class FreePlacement096Test {
    static long checks=0;
    static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static void close(double x,double y,String msg){ok(Math.abs(x-y)<1e-9,msg+" : "+x+" vs "+y);}
    public static void main(String[] args)throws Exception {
        if(args.length>0)LDrawLibrary.init(Path.of(args[0]));
        double[] id=PreciseFreeAssembly.identity();
        for(var axis:PreciseFreeAssembly.Axis.values()){
            double[] four= id.clone();
            for(int k=0;k<4;k++)four=PreciseFreeAssembly.multiply(PreciseFreeAssembly.rotation(axis,90),four);
            for(int j=0;j<9;j++)close(four[j],id[j],"four quarter-turns "+axis);
        }
        var mat=PreciseFreeAssembly.multiply(PreciseFreeAssembly.rotation(PreciseFreeAssembly.Axis.Y,37.25),
                  PreciseFreeAssembly.rotation(PreciseFreeAssembly.Axis.X,19.5));
        ok(PreciseWorldModel.validMatrix(mat),"arbitrary matrix valid");
        var initial=new LDrawProjectReader.Instance("3005",4,12.375,9.125,-0.25,mat);
        PreciseFreeAssembly editor=new PreciseFreeAssembly(new LDrawProjectReader.Result("test.ldr",0,List.of(initial),List.of()));
        ok(editor.size()==1 && editor.selected()!=null,"initialized");
        close(editor.selected().x(),12.375,"fractional LDU");
        editor.move(PreciseFreeAssembly.Axis.Z,.25);
        close(editor.selected().z(),0,"sub-LDU move");
        ok(Arrays.equals(initial.matrix(),editor.selected().matrix()),"move preserves arbitrary matrix exactly");
        editor.rotate(PreciseFreeAssembly.Axis.Z,.25);
        ok(PreciseWorldModel.validMatrix(editor.selected().matrix()),"fine rotation valid");
        editor.undo();ok(Arrays.equals(initial.matrix(),editor.selected().matrix()),"undo exact rotation");
        editor.redo();ok(!Arrays.equals(initial.matrix(),editor.selected().matrix()),"redo rotation");
        editor.resetRotation();ok(Arrays.equals(id,editor.selected().matrix()),"reset identity");
        if(args.length>0){
            editor.duplicate();ok(editor.size()==2,"duplicate");
            editor.delete();ok(editor.size()==1,"delete");
            editor.undo();ok(editor.size()==2,"undo delete");
            Path temp=Files.createTempFile("brickcraft096-", ".bcpx");
            try{
                PreciseProjectIO.save(temp,editor.result());
                var load=PreciseProjectIO.read(temp);
                ok(load.parts().size()==2,"BCPX roundtrip count");
                for(int i=0;i<load.parts().size();i++){
                    var a=editor.result().parts().get(i);var b=load.parts().get(i);
                    ok(a.partId().equals(b.partId()) && a.color()==b.color(),"BCPX part identity");
                    close(a.x(),b.x(),"BCPX x");close(a.y(),b.y(),"BCPX y");close(a.z(),b.z(),"BCPX z");
                    for(int k=0;k<9;k++)ok(Double.doubleToLongBits(a.matrix()[k])==Double.doubleToLongBits(b.matrix()[k]),"BCPX matrix bits");
                }
            }finally{Files.deleteIfExists(temp);}
        }
        if(args.length>1){
            var castle=PreciseProjectIO.read(Path.of(args[1]));
            ok(castle.parts().size()==3245,"castle 3245 instances preserved");
            PreciseFreeAssembly big=new PreciseFreeAssembly(castle);
            int idx=1234;
            ok(big.select(idx),"select arbitrary part");
            double[] matrixBefore=big.selected().matrix();
            double bx=big.selected().x(),by=big.selected().y(),bz=big.selected().z();
            // Explicit edit, then undo all without losing a single bit of the source.
            big.rotate(PreciseFreeAssembly.Axis.Y,13.37);
            big.move(PreciseFreeAssembly.Axis.X,.25);
            big.undo();big.undo();
            close(big.selected().x(),bx,"castle undo x");
            close(big.selected().y(),by,"castle undo y");
            close(big.selected().z(),bz,"castle undo z");
            for(int k=0;k<9;k++)ok(Double.doubleToLongBits(big.selected().matrix()[k])==Double.doubleToLongBits(matrixBefore[k]),"undo matrix bitwise");
            Path snapshot=Files.createTempFile("brickcraft096-castle-", ".bcpx");
            try{
                PreciseProjectIO.save(snapshot,big.result());
                var roundtrip=PreciseProjectIO.read(snapshot);
                ok(roundtrip.parts().size()==3245,"castle roundtrip size");
                for(int i=0;i<3245;i++){
                    var a=castle.parts().get(i);var b=roundtrip.parts().get(i);
                    ok(a.partId().equals(b.partId()) && a.color()==b.color(),"castle part name/color");
                    ok(Double.doubleToLongBits(a.x())==Double.doubleToLongBits(b.x()),"castle x");
                    ok(Double.doubleToLongBits(a.y())==Double.doubleToLongBits(b.y()),"castle y");
                    ok(Double.doubleToLongBits(a.z())==Double.doubleToLongBits(b.z()),"castle z");
                    double[] am=a.matrix(),bm=b.matrix();
                    for(int k=0;k<9;k++)ok(Double.doubleToLongBits(am[k])==Double.doubleToLongBits(bm[k]),"castle matrix");
                }
            }finally{Files.deleteIfExists(snapshot);}
        }
        System.out.println("V0.9.6 precise free placement: "+checks+" checks OK");
    }
}
