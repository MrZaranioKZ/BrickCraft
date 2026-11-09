package fr.brickcraft.client;

/**
 * Math used by the world GPU pipeline. Keeps the familiar six-sided vanilla
 * directional lighting while allowing continuous shading of rotated LDraw faces.
 * This class deliberately has no Minecraft dependencies for fast regression tests.
 */
public final class BrickLightingMath {
    private BrickLightingMath(){}

    public static float directional(float nx,float ny,float nz,
                                    float up,float down,float north,float south,float east,float west){
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length<1e-8f)return 1f;
        nx/=length;ny/=length;nz/=length;
        // Squared components interpolate smoothly across LEGO slopes instead of
        // turning their faces into staircase-looking light bands.
        float x=nx*nx,y=ny*ny,z=nz*nz;
        float xf=nx>=0?east:west;
        float yf=ny>=0?up:down;
        float zf=nz>=0?south:north;
        return Math.max(0.25f,Math.min(1f,x*xf+y*yf+z*zf));
    }

    public static int shade(int argb,float brightness){
        brightness=Math.max(0,Math.min(1,brightness));
        int r=Math.max(0,Math.min(255,Math.round(((argb>>>16)&255)*brightness)));
        int g=Math.max(0,Math.min(255,Math.round(((argb>>>8)&255)*brightness)));
        int b=Math.max(0,Math.min(255,Math.round((argb&255)*brightness)));
        return (argb&0xff000000)|(r<<16)|(g<<8)|b;
    }

    /** Approximate self-shadow of adjacent LEGO volumes. Leaves a nonzero skylight floor. */
    public static int shadowSky(int sky,float exposure){
        return Math.max(0,Math.min(15,sky-Math.round((1-Math.max(0,Math.min(1,exposure)))*7f)));
    }
}
