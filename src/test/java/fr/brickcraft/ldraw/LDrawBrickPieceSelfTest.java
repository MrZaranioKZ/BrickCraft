package fr.brickcraft.ldraw;

import fr.brickcraft.core.*;
import fr.brickcraft.client.StudioAssembly;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/** Integration check using real BrickPiece maths + fake ZIP, no Minecraft jar required. */
public final class LDrawBrickPieceSelfTest {
    private static int tests=0;
    private static void check(boolean yes,String message){tests++;if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path folder=Files.createTempDirectory("brickcraft-piece-test");
        Path config=folder.resolve("brickcraft");Files.createDirectories(config);
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(config.resolve("complete.zip")))){
            zip.putNextEntry(new ZipEntry("parts/3004.dat"));
            // 1 x 2 x 1.2 block bounding box using quads.
            String content="0 Brick 1 x 2\n"+
                 "4 16 -10 0 -20 -10 24 -20 10 24 -20 10 0 -20\n"+
                 "4 16 -10 0 20 10 0 20 10 24 20 -10 24 20\n"+
                 "4 16 -10 0 -20 10 0 -20 10 0 20 -10 0 20\n"+
                 "4 16 -10 24 -20 -10 24 20 10 24 20 10 24 -20\n";
            zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        LDrawLibrary.init(folder);
        check(LDrawLibrary.size()==1,"one test part indexed");
        BrickPiece p=new BrickPiece(BrickKind.LDRAW_PART,0xAABBCC,3,0,4,0,0,0,0,"3004");
        check(p.valid(),"valid ID and geometry");
        check(p.width()==1 && p.length()==2 && p.height()==6,"LEGO 1x2 3 plate dimensions");
        check(p.occupiedCells().size()==4,"one by two occupies two MC layers");
        check(!p.intersects(p.withPosition(3,6,4)),"snap exact top");
        check(p.intersects(p.withPosition(3,2,4)),"intersect along Y");
        check(p.withAngle(90).width()==2 && p.withAngle(90).length()==1,"rotation swaps dimensions");
        check(p.withAngle(90).partId().equals("3004"),"part ID survives rotation");
        check(p.withColor(0x123456).partId().equals("3004"),"ID survives recoloring");
        StudioAssembly board=new StudioAssembly();
        check(board.add(p),"studio accepts imported brick");
        check(board.snapTop(p,0,-1)==6,"studio snap imported 3 plates");
        check(board.add(p.withPosition(3,6,4)),"studio stacking imported bricks");
        check(board.undo() && board.size()==1,"undo custom part");
        check(board.redo() && board.size()==2,"redo custom part");
        BrickPiece missing=new BrickPiece(BrickKind.LDRAW_PART,0,0,0,0,0,0,0,0,"doesnotexist");
        check(!missing.valid(),"unknown imported part rejected");
        LDrawLibrary.close();
        System.out.println("LDraw BrickPiece/Studio OK: "+tests+" assertions");
    }
}
