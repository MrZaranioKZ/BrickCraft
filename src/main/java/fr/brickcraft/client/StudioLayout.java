package fr.brickcraft.client;

/**
 * Screen geometry in Minecraft's *scaled GUI pixels*, never physical pixels.
 * Only one side panel is visible: the stage retains room even at GUI scale 3/4.
 * Has no Minecraft dependencies so layout behavior can be regression-tested.
 */
public record StudioLayout(
    int width, int height,
    int panelX, int panelW,
    int stageX, int stageRight,
    int stageTop, int stageBottom,
    int footerY
) {
    public static StudioLayout calculate(int width, int height) {
        return calculate(width,height,false);
    }
    public static StudioLayout calculate(int width, int height, boolean fullStage) {
        if(fullStage){
            return new StudioLayout(width,height,6,0,6,width-6,52,Math.max(82,height-47),height-42);
        }
        // A slightly wider catalogue makes part IDs and colors legible, while
        // preserving at least 165 scaled pixels for the workspace.
        int side = Math.max(116, Math.min(220, width * 34 / 100));
        if (width - side - 18 < 165) side = Math.max(100, width - 183);
        int stageX = 6 + side + 7;
        return new StudioLayout(width, height, 6, side,
                stageX, width - 6, 52, Math.max(82, height - 47), height - 42);
    }

    public int stageWidth() { return Math.max(1, stageRight - stageX); }
    public int stageHeight() { return Math.max(1, stageBottom - stageTop); }
    public boolean insideStage(double x, double y) {
        return x >= stageX && x < stageRight && y >= stageTop + 24 && y < stageBottom;
    }
}
