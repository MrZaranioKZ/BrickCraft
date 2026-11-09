import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseProjectIO;
import fr.brickcraft.importer.PreciseLDrawGeometry;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Runs offline with the user's October LDraw archive and Neuschwanstein .io project. */
public final class PreciseImportV09Test {
    private static int assertions=0;
    private static void check(boolean ok,String what){assertions++;if(!ok)throw new AssertionError(what);}
    private static void equal(LDrawProjectReader.Result a,LDrawProjectReader.Result b){
        check(a.root().equals(b.root()),"root");
        check(a.submodels()==b.submodels(),"submodels");
        check(a.parts().size()==b.parts().size(),"part count");
        for(int i=0;i<a.parts().size();i++){
            var p=a.parts().get(i);var q=b.parts().get(i);
            check(p.partId().equals(q.partId()),"part ID "+i);
            check(p.color()==q.color(),"color "+i);
            check(Double.doubleToLongBits(p.x())==Double.doubleToLongBits(q.x()),"x "+i);
            check(Double.doubleToLongBits(p.y())==Double.doubleToLongBits(q.y()),"y "+i);
            check(Double.doubleToLongBits(p.z())==Double.doubleToLongBits(q.z()),"z "+i);
            double[] aa=p.matrix(),bb=q.matrix();
            for(int j=0;j<9;j++)check(Double.doubleToLongBits(aa[j])==Double.doubleToLongBits(bb[j]),"matrix "+i+"/"+j);
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length<2)throw new IllegalArgumentException("Usage: test <config-directory> <castle.io>");
        LDrawLibrary.init(Path.of(args[0]));
        check(LDrawLibrary.available(),"complete.zip missing");
        LDrawLibrary.Mesh mesh=LDrawLibrary.mesh("3004");
        double[] bounds=LDrawLibrary.originalBounds("3004");
        check(mesh!=null&&!mesh.isEmpty()&&bounds!=null,"actual triangle data for part 3004");
        float[] xyz=mesh.xyz();
        // For a source mesh, normalized minimum Y is actually source maximum Y (LDraw down-axis).
        double[] local=PreciseLDrawGeometry.localLdu(bounds,xyz[0],xyz[1],xyz[2]);
        check(local[0]>=bounds[0]-.001 && local[0]<=bounds[3]+.001,"mesh x source bounds");
        check(local[1]>=bounds[1]-.001 && local[1]<=bounds[4]+.001,"mesh y source bounds");
        check(local[2]>=bounds[2]-.001 && local[2]<=bounds[5]+.001,"mesh z source bounds");
        String model="0 FILE model.ldr\n1 353 10.5 4.125 -5.75 0 -1 0 1 0 0 0 0 1 3004.dat\n0 NOFILE\n";
        var source=LDrawProjectReader.parse(model,"model.ldr");
        var p=source.parts().get(0);
        double[] w=PreciseLDrawGeometry.worldLdu(p,3,4,5);
        check(w[0]==6.5&&w[1]==7.125&&w[2]==-.75,"exact matrix and fractional coordinates");
        Path tmp=Files.createTempFile("brickcraft-v09-test-",".bcpx");
        try {PreciseProjectIO.save(tmp,source);equal(source,PreciseProjectIO.read(tmp));}
        finally{Files.deleteIfExists(tmp);}
        long started=System.nanoTime();
        var castle=LDrawProjectReader.inspect(Path.of(args[1]));
        check(castle.parts().size()==3245,"Castle part count");
        Path snapshot=Files.createTempFile("brickcraft-v09-castle-",".bcpx");
        try {PreciseProjectIO.save(snapshot,castle);equal(castle,PreciseProjectIO.read(snapshot));}
        finally{Files.deleteIfExists(snapshot);}
        long ms=(System.nanoTime()-started)/1_000_000;
        System.out.printf("V0.9 precision: %d assertions OK; castle 3245 pieces (exact bitwise 9-matrix+xyz), %d ms%n",assertions,ms);
    }
}
