package fr.brickcraft.client;

/**
 * Pure pixel-space layout for the six-page guide reader.
 * All coordinates are in Minecraft scaled GUI pixels, not framebuffer pixels.
 */
public final class BrickGuideLayout {
    private BrickGuideLayout() {}

    public record Frame(int x, int y, int width, int height,
                        int titleY, int iconsY, int bodyTop, int bodyBottom,
                        int navigationY, int navigationWidth, int navigationGap,
                        boolean showIcons) {
        public int navX(int index) {
            return x + 10 + index * (navigationWidth + navigationGap);
        }
        public int bodyHeight() { return Math.max(0, bodyBottom - bodyTop); }
    }

    public static Frame calculate(int screenWidth, int screenHeight) {
        int w=Math.max(80,Math.min(360,screenWidth-24));
        int h=Math.max(70,Math.min(224,screenHeight-20));
        // On real Minecraft screens with a very small scaled resolution,
        // stay inside the viewport instead of pushing navigation off-screen.
        w=Math.min(w,Math.max(1,screenWidth-4));
        h=Math.min(h,Math.max(1,screenHeight-4));
        int x=(screenWidth-w)/2;
        int y=(screenHeight-h)/2;
        int navY=y+h-27;
        int gap=5;
        int navW=Math.max(12,(w-20-2*gap)/3);
        boolean showIcons=h>=145;
        int contentY=y+(h>=188?100:showIcons?88:56);
        int bottom=Math.max(contentY+1,navY-8);
        return new Frame(x,y,w,h,y+39,y+61,contentY,bottom,
                navY,navW,gap,showIcons);
    }
}
