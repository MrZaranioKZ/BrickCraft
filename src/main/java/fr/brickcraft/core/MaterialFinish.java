package fr.brickcraft.core;

/**
 * Deterministic per-face approximation for LDraw material families. No shaders,
 * extra buffers, random frame noise or change to the source alpha channel.
 * Dedicated GPU shader effects can replace this later.
 */
public final class MaterialFinish {
    private MaterialFinish() {}
    public static int shade(int argb,String finish,double nx,double ny,double nz,long seed){
        if(finish==null || finish.equals("SOLID"))return argb;
        double dot=Math.max(0,(-0.45*nx + 0.82*ny - 0.35*nz));
        double shine=Math.pow(dot,10);
        double broad=Math.pow(dot,2);
        int h=(int)(seed^(seed>>>32));
        h ^= h>>>16; h*=0x7feb352d;h ^= h>>>15;h*=0x846ca68b;h ^= h>>>16;
        double noise=((h&0xFFFF)/65535.0)-0.5;
        double factor=switch(finish){
            case "CHROME" -> 0.58 + 0.40*broad + 1.25*shine;
            case "METAL" -> 0.72 + 0.38*broad + 0.50*shine;
            case "MATTE_METALLIC" -> 0.76 + 0.32*broad + 0.20*shine;
            case "PEARLESCENT" -> 0.86 + 0.27*broad + 0.17*Math.max(0,(nx+nz+1)*0.5);
            case "RUBBER" -> 0.83 + 0.14*dot;
            case "FABRIC" -> 0.77 + 0.16*dot;
            case "GLITTER" -> 0.83 + 0.21*broad + (noise>0.38 ? 0.65 : noise*0.14);
            case "SPECKLE" -> 0.85 + 0.17*broad + noise*0.27;
            default -> 1.0;
        };
        factor=Math.max(0.35,Math.min(1.75,factor));
        int a=(argb>>>24)&255;
        int r=Math.min(255,(int)Math.round(((argb>>>16)&255)*factor));
        int g=Math.min(255,(int)Math.round(((argb>>>8)&255)*factor));
        int b=Math.min(255,(int)Math.round((argb&255)*factor));
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
}
