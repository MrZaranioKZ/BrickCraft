import fr.brickcraft.client.BrickGuideLayout;

/** Pure Java checks for Minecraft scaled GUI dimensions, no Fabric required. */
public final class TEST_GUIDE_LAYOUT_V0231 {
    public static void main(String[] args) {
        int checked=0;
        for (int width : new int[]{224,256,320,426,554,854,1920}) {
            for (int height : new int[]{150,180,216,240,294,480,1080}) {
                var f=BrickGuideLayout.calculate(width,height);
                check(f.x()>=0 && f.y()>=0,"start outside window");
                check(f.x()+f.width()<=width && f.y()+f.height()<=height,"panel outside window");
                check(f.navX(0)>=f.x() && f.navX(2)+f.navigationWidth()<=f.x()+f.width(),"buttons overflow");
                check(f.navigationY()>=f.y() && f.navigationY()+20<=f.y()+f.height(),"buttons outside panel");
                check(f.bodyTop()<f.bodyBottom() && f.bodyBottom()<f.navigationY(),"text overlaps buttons");
                check(f.iconsY()<f.bodyTop() || !f.showIcons(),"icons overlap body");
                checked++;
            }
        }
        System.out.println("PASS: " + checked + " guide layouts across window sizes and GUI scales");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
