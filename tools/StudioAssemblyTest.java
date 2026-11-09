import fr.brickcraft.client.StudioAssembly;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;

public class StudioAssemblyTest {
    private static int assertions;
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        assertions++;
    }
    private static BrickPiece piece(BrickKind kind, int x,int y,int z) {
        return new BrickPiece(kind,0xCC3333,x,y,z,0);
    }
    public static void main(String[] args) {
        StudioAssembly m = new StudioAssembly();
        check(m.setGridSize(16),"legacy small-grid test mode");
        BrickKind brick = BrickKind.BRICK_2X4;
        check(m.size()==0 && !m.canUndo(),"start empty");
        check(m.canPlace(piece(brick,1,0,1),-1),"first valid");
        check(!m.canPlace(piece(brick,15,0,1),-1),"reject X edge");
        check(!m.canPlace(piece(brick,1,0,14),-1),"reject Z edge");
        check(!m.canPlace(piece(brick,1,-2,1),-1),"reject below board");
        check(m.add(piece(brick,1,0,1)),"add brick");
        check(m.size()==1,"one piece");
        check(m.snapTop(1,1,BrickKind.PLATE_1X2,0,0,-1)==6,"auto stack on full brick");
        check(m.snapTop(9,9,BrickKind.PLATE_1X2,0,0,-1)==0,"empty stack base");
        check(m.snapTop(1,1,BrickKind.PLATE_1X2,0,4,-1)==6,"floor and snap max");
        check(!m.canPlace(piece(brick,1,4,1),-1),"reject body collision");
        check(m.add(piece(BrickKind.PLATE_1X2,1,6,1)),"plate atop brick");
        check(m.snapTop(1,1,BrickKind.BRICK_1X1,0,0,-1)==8,"auto stack on plate");
        check(m.size()==2,"two pieces");
        check(m.replace(1,piece(BrickKind.PLATE_1X2,2,6,1)),"move piece");
        check(m.get(1).x()==2,"move location");
        check(!m.replace(1,piece(BrickKind.PLATE_1X2,1,0,1)),"bad move blocked");
        check(m.get(1).x()==2,"bad move preserved");
        check(m.snapTop(1,1,BrickKind.PLATE_1X2,0,0,1)==6,"exclude selected while moving");
        check(m.undo(),"undo move");
        check(m.get(1).x()==1,"undo restored");
        check(m.redo(),"redo move");
        check(m.get(1).x()==2,"redo restored");
        check(m.undo(),"undo again");
        check(m.add(piece(BrickKind.TILE_1X2,5,0,5)),"new edit after undo");
        check(!m.canRedo(),"redo invalidated on branch");
        check(m.remove(0),"delete");
        check(m.size()==2,"delete count");
        check(m.clear(),"clear");
        check(m.size()==0,"clear count");
        check(m.undo() && m.size()==2,"undo clear");
        check(!m.canPlace(piece(BrickKind.BRICK_2X4,0,1276,0),-1),"height cap");
        check(m.canPlace(piece(BrickKind.PLATE_1X2,0,1278,0),-1),"plate at max layer");
        check(!m.canPlace(piece(BrickKind.PLATE_1X2,0,1280,0),-1),"plate over max layer");
        check(m.pieces().size()==2,"snapshot list");
        System.out.println("STUDIO ASSEMBLY: " + assertions + " tests OK");
    }
}
