import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.core.*;
import fr.brickcraft.client.*;
import java.nio.file.*;
import java.util.*;

/** Execute with config directory path holding brickcraft/complete.zip. */
public class PaletteV06Test {
    private static int checks;
    private static void ok(boolean value,String context){checks++;if(!value)throw new AssertionError(context);}
    public static void main(String[] args)throws Exception {
        Path config=Path.of(args[0]);
        LDrawLibrary.init(config);
        ok(LDrawLibrary.available(),LDrawLibrary.status());
        ok(LDrawLibrary.colors().size()==322,"October LDConfig colour count");
        ok(LDrawLibrary.size()>=24000,"Official LDraw parts indexed");
        ok(LDrawLibrary.color(353).rgb()==0xFF6D77,"coral RGB");
        ok(LDrawLibrary.color(353).finish().equals("SOLID"),"coral ABS");
        ok(LDrawLibrary.color(383).finish().equals("CHROME"),"chrome metadata");
        ok(LDrawLibrary.colors().stream().anyMatch(c->c.alpha()<255),"translucent metadata");
        ok(LDrawLibrary.searchColors("353","Tout").stream().anyMatch(c->c.code()==353),"lookup number");
        ok(LDrawLibrary.searchColors("coral","Tout").stream().anyMatch(c->c.code()==353),"lookup name");
        ok(LDrawLibrary.searchColors("chrome","Métal").size()>0,"lookup finish");
        ok(LDrawLibrary.searchColors("glitter","Spécial").size()>0,"lookup material");
        ok(LDrawLibrary.searchColors("transparent","Transparent").isEmpty() ||
           LDrawLibrary.searchColors("transparent","Transparent").stream().allMatch(c->c.alpha()<255),"filter transparent");
        var trans=LDrawLibrary.colors().stream().filter(c->c.alpha()<255).findFirst().orElseThrow();
        ok((LDrawLibrary.argb(trans.code(),0)>>24&255)==trans.alpha(),"alpha preservation");
        ok((LDrawLibrary.tint(LDrawLibrary.argb(trans.code(),0),trans.code(),0.7)>>>24)==trans.alpha(),"tint alpha");
        ok(LDrawLibrary.mesh("3004")!=null,"LDraw 3004 mesh");
        BrickPiece p=new BrickPiece(BrickKind.BRICK_1X2,0xFF6D77,0,0,0,0).withLDrawColor(353);
        ok(p.colorCode()==353,"colorcode piece");
        ok(p.withAngle(90).withPitch(90).withRoll(90).withPosition(2,2,2).colorCode()==353,"rotation preservation");
        BrickSelection.setColor(LDrawLibrary.color(383));
        ok(BrickSelection.colorCode==383,"selection stores material code");
        BrickSelection.sample(p);
        ok(BrickSelection.colorCode==353,"pipette retains colour code");
        StudioAssembly assembly=new StudioAssembly();
        ok(assembly.add(p),"assembly accepts material");
        Path tmp=Files.createTempFile("brickcraft-v06-", ".bcp");
        try{
            StudioProjectIO.save(tmp,assembly);
            var saved=StudioProjectIO.read(tmp);
            ok(saved.pieces().size()==1,"project restored size");
            ok(saved.pieces().get(0).colorCode()==353,"BCP keeps material code");
            List<String> lines=Files.readAllLines(tmp);
            lines.set(0,"BRICKCRAFT-PROJECT-1");
            lines.set(2,lines.get(2).substring(0,lines.get(2).lastIndexOf(' ')));
            Files.write(tmp,lines);
            ok(StudioProjectIO.read(tmp).pieces().get(0).colorCode()==-1,"v1 project migration");
        }finally{Files.deleteIfExists(tmp);LDrawLibrary.close();}
        System.out.println("PALETTE V0.6 : "+checks+" assertions OK");
    }
}
