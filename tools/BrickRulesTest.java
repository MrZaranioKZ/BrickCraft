import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public class BrickRulesTest {
    static int count=0;
    private static void test(boolean value,String title) {
        if(!value) throw new AssertionError(title);
        count++;
    }
    public static void main(String[] args) {
        BrickPiece b=new BrickPiece(BrickKind.BRICK_2X4,0xFF1000,10,320,20,0);
        test(b.valid(),"valid 2x4 brick");
        test(b.width()==2 && b.length()==4,"footprint 2x4 blocks");
        test(b.height()==6,"brick 6 fifths = 1.2 blocks");
        test(new BrickPiece(BrickKind.PLATE_1X2,0,0,0,0,0).height()==2,"plate 2 fifths = 0.4 blocks");
        test(new BrickPiece(BrickKind.TILE_1X2,0,0,0,0,0).height()==2,"tile 0.4 blocks");
        test(new BrickPiece(BrickKind.BRICK_2X4,0,10,320,20,1).width()==4,"rotated width");
        test(new BrickPiece(BrickKind.BRICK_2X4,0,10,320,20,1).length()==2,"rotated length");
        test(b.intersects(new BrickPiece(BrickKind.PLATE_1X2,0,11,323,21,0)),"overlap");
        test(!b.intersects(new BrickPiece(BrickKind.PLATE_1X2,0,11,326,21,0)),"brick stacking at top");
        test(!b.intersects(new BrickPiece(BrickKind.BRICK_1X1,0,9,320,20,0)),"adjacent footprint");
        test(!new BrickPiece(BrickKind.BRICK_1X1,-1,0,0,0,0).valid(),"RGB 24bit only");
        test(b.occupiedCells().size()==16,"2x4 spans two block height layers");
        test(b.occupiedCells().contains(new BlockPos(10,64,20)),"occupy base");
        test(b.occupiedCells().contains(new BlockPos(11,65,23)),"occupy upper slice");
        BrickPiece plate=new BrickPiece(BrickKind.PLATE_1X2,0,10,326,20,0);
        test(!plate.intersects(b),"plate stacked on brick");
        test(plate.occupiedCells().contains(new BlockPos(10,65,20)),"plate at noninteger height");
        BrickPiece stacked=new BrickPiece(BrickKind.BRICK_2X4,0,10,b.top(),20,0);
        test(!b.intersects(stacked),"two full bricks stack");
        test(stacked.y()==326 && stacked.top()==332,"exact world height");
        BrickPiece below=new BrickPiece(BrickKind.BRICK_1X1,0,-5,-10,-5,0);
        test(below.occupiedCells().contains(new BlockPos(-5,-2,-5)),"negative Y exact floor");
        test(new BrickPiece(BrickKind.BRICK_1X1,0,1,0,1,0).occupiedCells().size()==2,"stud crosses top block");
        System.out.println("BRICKCRAFT V0.2 CORE TESTS: "+count+" OK");
    }
}
