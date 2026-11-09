import fr.brickcraft.client.StudioLayout;

/** Executable without Minecraft, sanity-checks GUI-scale-dependent geometry. */
public class StudioLayoutTest {
    private static int checks=0;
    private static void test(boolean b,String description) {
        if(!b) throw new AssertionError(description);
        checks++;
    }
    public static void main(String[] args) {
        int[][] screens={{320,240},{416,329},{426,326},{640,360},{854,480},{1280,720},{1920,1080}};
        for(int[] size:screens) {
            StudioLayout l=StudioLayout.calculate(size[0],size[1]);
            test(l.panelX()+l.panelW()+3<l.stageX(),"sidebar/stage gap "+size[0]);
            test(l.stageWidth()>=130,"stage >= 130 logical pixels at "+size[0]);
            test(l.stageX()>=l.panelX()+l.panelW(),"stage does not cover sidebar "+size[0]);
            test(l.stageBottom()<l.footerY(),"stage does not cover toolbar "+size[1]);
            test(l.footerY()+20<size[1],"toolbar does not cover status "+size[1]);
            test(l.insideStage(l.stageX()+5,l.stageTop()+28),"hit test stage "+size[0]);
            test(!l.insideStage(l.panelX()+5,l.stageTop()+28),"no stage hit on sidebar "+size[0]);
        }
        System.out.println("STUDIO LAYOUT TESTS: "+checks+" OK");
    }
}
