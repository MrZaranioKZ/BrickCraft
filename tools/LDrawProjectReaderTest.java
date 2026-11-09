import fr.brickcraft.importer.LDrawProjectReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LDrawProjectReaderTest {
    static int n;
    static void ok(boolean b,String name){n++;if(!b)throw new AssertionError(name);}
    public static void main(String[] args)throws Exception{
        Path one=Files.createTempFile("brickcraft-single",".ldr");
        Files.writeString(one,"0 Standalone\n1 4 10 20 30 1 0 0 0 1 0 0 0 1 3004.dat\n",StandardCharsets.UTF_8);
        var simple=LDrawProjectReader.inspect(one);
        ok(simple.parts().size()==1,"single ldr one part");
        ok(simple.parts().get(0).partId().equals("3004"),"id");
        ok(simple.parts().get(0).color()==4,"red color");
        ok(simple.parts().get(0).x()==10,"X LDU preserved");
        Path mpd=Files.createTempFile("brickcraft-multi",".mpd");
        Files.writeString(mpd,"""
            0 FILE main.ldr
            1 14 20 0 10 0 -1 0 1 0 0 0 0 1 sub.ldr
            0 FILE sub.ldr
            1 16 10 5 0 1 0 0 0 1 0 0 0 1 3004.dat
            1 2 0 0 0 1 0 0 0 1 0 0 0 1 3005.dat
            """,StandardCharsets.UTF_8);
        var project=LDrawProjectReader.inspect(mpd);
        ok(project.parts().size()==2,"MPD flattened");
        ok(project.submodels()==2,"submodels");
        var a=project.parts().get(0);
        ok(a.partId().equals("3004"),"first design");
        ok(a.color()==14,"inherited color");
        ok(a.x()==15 && a.y()==10 && a.z()==10,"transformed child coords");
        ok(a.matrix()[1]==-1,"composed orientation retained");
        ok(project.parts().get(1).color()==2,"explicit child color retained");
        Path cyclic=Files.createTempFile("brickcraft-cycle",".mpd");
        Files.writeString(cyclic,"0 FILE main.ldr\n1 16 0 0 0 1 0 0 0 1 0 0 0 1 main.ldr\n");
        var cycle=LDrawProjectReader.inspect(cyclic);
        ok(!cycle.problems().isEmpty(),"cycle diagnostic");
        Files.deleteIfExists(one);Files.deleteIfExists(mpd);Files.deleteIfExists(cyclic);
        System.out.println("OK "+n+" project parser checks : LDR + MPD + recursion + color + matrix");
    }
}
