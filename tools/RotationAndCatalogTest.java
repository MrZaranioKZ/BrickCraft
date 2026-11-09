import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.client.BrickSelection;
import fr.brickcraft.client.StudioAssembly;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Offline validation: rotation footprints, intersections, stacking, legacy saves. */
public final class RotationAndCatalogTest {
    private static int n=0;
    private static void ok(boolean condition,String title) {
        if(!condition)throw new AssertionError(title);
        n++;
    }
    public static void main(String[] args) {
        ok(BrickKind.values().length==27,"26 classiques + LDraw");
        ok(BrickKind.SLOPE_2X2.slope,"slope piece exists");
        ok(!BrickKind.BRICK_2X2.slope,"rectangular pieces stay rectangular");
        // The first six ordinals are kept for cross-version compatibility.
        ok(BrickKind.values()[3]==BrickKind.BRICK_2X4,"stable original enum");
        BrickPiece legacy=new BrickPiece(BrickKind.BRICK_2X4,0xAB1122,1,0,1,1);
        ok(legacy.angle()==90 && legacy.width()==4 && legacy.length()==2,"legacy 90");
        for(int angle : new int[]{0,90,180,270}) {
            BrickPiece b=BrickPiece.atAngle(BrickKind.BRICK_2X4,0,5,0,8,angle);
            ok(b.valid(),"valid cardinal angle "+angle);
            ok(b.width()==(angle%180==0?2:4),"width cardinal "+angle);
            ok(b.length()==(angle%180==0?4:2),"depth cardinal "+angle);
            ok(b.occupiedCells().size()==16,"occupied two layers "+angle);
        }
        for (int angle=0;angle<360;angle++) {
            BrickPiece a=BrickPiece.atAngle(BrickKind.BRICK_2X4,0xEEEEEE,2,0,3,angle);
            ok(a.valid(),"angle valid "+angle);
            ok(a.angle()==angle,"angle persisted "+angle);
            ok(a.width()>0 && a.length()>0,"bounds positive "+angle);
            ok(!a.occupiedCells().isEmpty(),"occupied cells "+angle);
            ok(a.occupiedCells().contains(a.renderAnchor()),"mesh anchor "+angle);
            ok(!a.intersects(BrickPiece.atAngle(BrickKind.BRICK_2X4,0,2,a.top(),3,angle)),
                    "stack top "+angle);
        }
        for(int angle:new int[]{1,5,15,22,30,45,60,75,89,91,135,180,225,269,315,359}) {
            BrickPiece a=BrickPiece.atAngle(BrickKind.BRICK_2X4,0,4,0,4,angle);
            ok(a.intersects(a),"self collision "+angle);
            BrickPiece b=BrickPiece.atAngle(BrickKind.BRICK_1X1,0,100,0,100,angle);
            ok(!a.intersects(b),"far apart "+angle);
            ok(!a.horizontalOverlaps(b),"far horizontal "+angle);
        }
        BrickPiece diagonal=BrickPiece.atAngle(BrickKind.BRICK_2X4,0,0,0,0,45);
        ok(diagonal.width()==5 && diagonal.length()==5,"45-degree AABB 5x5");
        ok(diagonal.occupiedCells().size()<diagonal.width()*diagonal.length()*2,
                "rotated shape not full AABB");
        ok(!diagonal.intersects(BrickPiece.atAngle(BrickKind.BRICK_1X1,0,4,0,4,0)),
                "corner outside polygon is free");
        StudioAssembly board=new StudioAssembly();
        ok(board.add(diagonal),"place angled in studio");
        ok(board.snapTop(1,1,BrickKind.BRICK_1X1,45,0,-1)==6,"snap to angled top");
        ok(!board.canPlace(diagonal,-1),"cannot self overlap");
        ok(board.canPlace(diagonal.withAngle(45).withColor(0xFFCC00),0),"replace excluding self");
        BrickSelection.angle=0;
        BrickSelection.stepIndex=0;
        BrickSelection.rotate(1);
        BrickSelection.rotate(1);
        BrickSelection.rotate(1);
        ok(BrickSelection.angle==270,"90+90+90 rotates 270");
        BrickSelection.rotate(1);
        ok(BrickSelection.angle==0,"full revolution");
        BrickSelection.cycleStep();
        BrickSelection.rotate(1);
        ok(BrickSelection.angle==45,"45 step");
        BrickSelection.cycleStep();
        BrickSelection.rotate(-1);
        ok(BrickSelection.angle==30,"negative 15");
        BrickSelection.stepIndex=4;
        BrickSelection.rotate(-1);
        ok(BrickSelection.angle==29,"one degree granularity");
        System.out.println("ROTATION & CATALOG: "+n+" assertions OK");
    }
}
