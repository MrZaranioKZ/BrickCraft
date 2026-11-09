package fr.brickcraft.ldraw;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Standalone tests; can run with javac/java without Gradle, Minecraft or Internet. */
public final class LDrawLibrarySelfTest {
    private static int checks;
    private static void ok(boolean value,String desc){checks++;if(!value)throw new AssertionError(desc);}
    private static void eq(double got,double expected,String desc){ok(Math.abs(got-expected)<0.0001,desc+": "+got+" instead of "+expected);}
    public static void main(String[] args)throws Exception {
        Path tmp=Files.createTempDirectory("brickcraft-ldraw-test");
        Path dir=tmp.resolve("config").resolve("brickcraft");Files.createDirectories(dir);
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(dir.resolve("complete.zip")))){
            add(zip,"ldraw/LDConfig.ldr","0 !COLOUR Coral CODE 353 VALUE #FF6D77 EDGE #333333\\n".replace("\\n","\n"));
            add(zip,"ldraw/parts/3004.dat", "0 Brick 1 x 2\n"+
                    "1 16 0 0 0 1 0 0 0 1 0 0 0 1 s\\sub.dat\n"+
                    "3 16 -10 0 -20  10 0 -20  10 0 20\n");
            add(zip,"ldraw/parts/s/sub.dat", "0 ~Subpart\n"+
                    "4 353 -10 0 -20 -10 0 20 -10 8 20 -10 8 -20\n"+
                    "4 16 10 0 -20 10 0 20 10 8 20 10 8 -20\n"+
                    "4 16 -10 8 -20 -10 8 20 10 8 20 10 8 -20\n");
            add(zip,"ldraw/parts/1x2test.dat", "0 Test part rotated\n"+
                    "1 16 0 0 0 0 0 -1 0 1 0 1 0 0 3004.dat\n");
        }
        LDrawLibrary.init(tmp.resolve("config"));
        ok(LDrawLibrary.available(),"zip active");
        ok(LDrawLibrary.size()==2,"root part count, no subparts");
        ok(LDrawLibrary.contains("3004"),"id");
        ok(LDrawLibrary.search("1x2",20).size()>=1,"find by description");
        ok(LDrawLibrary.search("3004",20).size()==1,"find by ID");
        ok(LDrawLibrary.resolveColor(353,0xAA00AA)==0xFF6D77,"LDConfig RGB");
        LDrawLibrary.Mesh a=LDrawLibrary.mesh("3004");
        ok(a!=null,"real mesh");
        ok(a.triangles()==7,"quad to triangle conversion + root triangle");
        eq(a.width(),1.0,"width 1 stud");
        eq(a.height(),.4,"height 1 plate");
        eq(a.depth(),2.0,"depth 2 studs");
        ok(a.colors()[0]==353,"subpart fixed color");
        ok(a==LDrawLibrary.mesh("3004"),"cache reused");
        LDrawLibrary.Mesh b=LDrawLibrary.mesh("1x2test");
        ok(b!=null,"rotated recursive reference");
        eq(b.width(),2.0,"matrix rotate bounds x");
        eq(b.depth(),1.0,"matrix rotate bounds z");
        LDrawLibrary.close();
        ok(!LDrawLibrary.available(),"close");
        System.out.println("LDraw OK: "+checks+" assertions");
    }
    private static void add(ZipOutputStream z,String path,String data)throws Exception{
        z.putNextEntry(new ZipEntry(path));z.write(data.getBytes(StandardCharsets.UTF_8));z.closeEntry();
    }
}
