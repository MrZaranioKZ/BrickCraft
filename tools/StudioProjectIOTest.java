import fr.brickcraft.client.StudioAssembly;
import fr.brickcraft.client.StudioProjectIO;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class StudioProjectIOTest {
    static int count;
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);count++;}
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("brickcraft-workspace-test");
        try{
            StudioAssembly a=new StudioAssembly();
            check(a.setGridSize(256),"256 workspace");
            BrickPiece normal=new BrickPiece(BrickKind.BRICK_2X4,0x44AA77,210,0,220,0).withAngle(135);
            BrickPiece tilted=new BrickPiece(BrickKind.PLATE_1X2,0x0099CC,1,36,2,0).withPitch(90);
            check(a.add(normal),"large offset");
            check(a.add(tilted),"3d-tilted part");
            Path file=tmp.resolve("build.bcp");
            StudioProjectIO.save(file,a);
            check(Files.isRegularFile(file),"saved file");
            StudioProjectIO.Project saved=StudioProjectIO.read(file);
            check(saved.grid()==256,"stored workspace size");
            check(saved.pieces().equals(a.pieces()),"stored exact orientation and color");
            StudioAssembly b=new StudioAssembly();
            check(b.loadProject(saved.grid(),saved.pieces()),"load into another model");
            check(b.pieces().equals(a.pieces()),"roundtrip list");
            Files.writeString(file,"BRICKCRAFT-PROJECT-1\nGRID 999\n",StandardCharsets.UTF_8);
            boolean failed=false;try{StudioProjectIO.read(file);}catch(java.io.IOException e){failed=true;}
            check(failed,"invalid grid rejected");
        }finally{
            try(var stream=Files.walk(tmp)){
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
            }
        }
        System.out.println("PROJECT PERSISTENCE: "+count+" tests OK");
    }
}
