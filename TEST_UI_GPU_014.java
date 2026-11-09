import fr.brickcraft.client.PreviewPerfPolicy;
import fr.brickcraft.client.StudioLayout;

/** Minecraft-free regression checks: no external library required. */
public final class TEST_UI_GPU_014 {
    private static void ok(boolean test,String label){if(!test)throw new AssertionError(label);}
    public static void main(String[] args){
        for(int count: new int[]{0,1,16,700,701,3245,4096,30000}){
            int stride=PreviewPerfPolicy.cpuStride(count);
            ok(stride>=1,"invalid stride");
            ok(PreviewPerfPolicy.sampledCount(count)<=700,"cpu budget exceeded: "+count);
            if(count<=700)ok(stride==1,"small preview skipped pieces");
        }
        for(int w:new int[]{300,384,420,640,832,1920})for(int h:new int[]{220,270,360,480,1080}){
            StudioLayout l=StudioLayout.calculate(w,h);
            ok(l.panelX()>=0&&l.panelX()+l.panelW()<l.stageX(),"overlapping panels "+w);
            ok(l.stageWidth()>0&&l.stageRight()<=w,"invalid viewport width");
            ok(l.stageBottom()<=l.footerY(),"viewport overlaps footer "+h);
            StudioLayout full=StudioLayout.calculate(w,h,true);
            ok(full.stageWidth()>l.stageWidth(),"full stage does not expand");
        }
        System.out.println("OK : UI responsive et budget CPU V0.14 (120+ cas)");
    }
}
