package fr.brickcraft.core;

import java.util.Locale;

/** Material slots shared by the two world renderers and the LabPBR texture triplet.
 * These indexes are texture-cell slots, NOT Minecraft blocks or LDraw part IDs.
 * Every LEGO RGB tint reuses the same material, keeping textures small.
 */
public final class BrickPbrProfiles {
    private BrickPbrProfiles() {}
    public static final int COUNT = 10;
    public static final int PLASTIC = 0;
    public static final int CHROME = 1;
    public static final int METAL = 2;
    public static final int MATTE_METAL = 3;
    public static final int PEARL = 4;
    public static final int RUBBER = 5;
    public static final int FABRIC = 6;
    public static final int GLITTER = 7;
    public static final int SPECKLE = 8;
    public static final int TRANSPARENT = 9;

    public static int slot(String finish,int alpha){
        if(alpha < 250) return TRANSPARENT;
        if(finish == null) return PLASTIC;
        return switch(finish.toUpperCase(Locale.ROOT)) {
            case "CHROME" -> CHROME;
            case "METAL" -> METAL;
            case "MATTE_METALLIC" -> MATTE_METAL;
            case "PEARLESCENT" -> PEARL;
            case "RUBBER" -> RUBBER;
            case "FABRIC" -> FABRIC;
            case "GLITTER" -> GLITTER;
            case "SPECKLE" -> SPECKLE;
            default -> PLASTIC;
        };
    }
    /** Center of the 16x16 atlas cell. Constant UV prevents seams and interpolation bleed. */
    public static float u(int slot){
        if(slot < 0 || slot >= COUNT)throw new IllegalArgumentException("material " + slot);
        return (slot + .5f)/COUNT;
    }
    public static float v(){return .5f;}
}
