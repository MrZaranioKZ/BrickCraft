import fr.brickcraft.client.StudioAssembly;
import fr.brickcraft.client.StudioLayout;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import java.util.List;

public class StudioLargeWorkspaceTest {
    static int count;
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);count++;}
    static BrickPiece p(BrickKind k,int x,int y,int z){return new BrickPiece(k,0xAA4499,x,y,z,0);}
    public static void main(String[] args){
        StudioAssembly a=new StudioAssembly();
        check(a.gridSize()==64,"default workspace 64");
        check(a.add(p(BrickKind.BRICK_2X4,60,0,60)),"piece near grid edge");
        check(!a.add(p(BrickKind.BRICK_2X4,62,0,62)),"reject out-of-grid piece");
        check(a.setGridSize(128),"grow 128");
        check(a.add(p(BrickKind.BRICK_1X1,100,0,100)),"100x100 is usable");
        check(!a.setGridSize(32),"can't shrink and lose pieces");
        check(a.setGridSize(256),"grow 256");
        check(a.add(p(BrickKind.BRICK_2X4,250,0,252)),"near 256 edge");
        check(!a.add(p(BrickKind.BRICK_2X4,253,0,254)),"reject too wide");
        check(a.add(p(BrickKind.PLATE_1X2,0,1278,0)),"plate at top (256 blocks)");
        check(!a.add(p(BrickKind.PLATE_1X2,0,1280,0)),"reject above max height");
        check(a.snapTop(0,0,BrickKind.PLATE_1X2,0,0,-1)==1280,"snap to current top");
        check(a.add(p(BrickKind.BRICK_1X1,100,6,100)),"stacking at distant region");
        check(a.remove(a.size()-1),"delete distant piece");
        check(a.undo(),"undo distant deletion");
        check(a.redo(),"redo distant deletion");
        check(a.nextGridSize()==16,"cycle after 256");
        StudioAssembly b=new StudioAssembly();
        check(!b.loadProject(16,List.of(p(BrickKind.BRICK_1X1,200,0,0))),"invalid project not accepted");
        check(b.gridSize()==64 && b.isEmpty(),"invalid project preserves old state");
        check(b.loadProject(128,List.of(p(BrickKind.BRICK_1X2,110,0,110))),"load big project");
        check(b.gridSize()==128 && b.size()==1,"loaded exact workspace");
        for(int[] res:new int[][]{{320,240},{640,360},{960,540},{1280,720}}){
            StudioLayout l=StudioLayout.calculate(res[0],res[1],true);
            check(l.stageWidth()==res[0]-12,"fullscreen stage width");
            check(l.panelW()==0,"fullscreen hides sidebar");
            check(l.insideStage(l.stageX()+12,l.stageTop()+25),"fullscreen hit test");
        }
        System.out.println("LARGE WORKSPACE: "+count+" tests OK");
    }
}
