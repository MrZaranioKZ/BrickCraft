package fr.brickcraft.client;

/**
 * Pure math for BrickCraft in-world move handles (no Minecraft or OpenGL classes).
 * A handle is the OUTER part of an axis, not the center of the LEGO piece.
 * Ray coordinates are world blocks; a shift is expressed in LDraw units (20 = 1 block).
 */
public final class WorldGizmoMath {
    private WorldGizmoMath() {}
    public record Hit(int axis,double distance,double separation) {}

    public static Hit pick(double[] origin,double[] direction,double[] center,
                           double reach,double radius) {
        if(origin==null||origin.length!=3||direction==null||direction.length!=3||
                center==null||center.length!=3||!Double.isFinite(reach)||reach<=0||
                !Double.isFinite(radius)||radius<=0) return null;
        for(double v:origin)if(!Double.isFinite(v))return null;
        for(double v:direction)if(!Double.isFinite(v))return null;
        for(double v:center)if(!Double.isFinite(v))return null;
        double n=Math.sqrt(dot(direction,direction));if(n<1e-8)return null;
        double[] d={direction[0]/n,direction[1]/n,direction[2]/n};
        Hit best=null;
        for(int a=0;a<3;a++){
            // An axis facing the camera is not draggable in screen space: don't steal selection.
            if(Math.abs(d[a])>0.965)continue;
            double[] begin=center.clone();begin[a]+=0.38;
            double[] end=center.clone();end[a]+=1.2;
            double[] u={end[0]-begin[0],end[1]-begin[1],end[2]-begin[2]};
            double[] w={origin[0]-begin[0],origin[1]-begin[1],origin[2]-begin[2]};
            // Closest ray-to-segment approach with both limits enforced.
            double uu=dot(u,u),du=dot(d,u),dw=dot(d,w),uw=dot(u,w);
            double denom=uu-du*du;
            double s=denom>1e-9?(du*uw-uu*dw)/denom:0;
            double t=denom>1e-9?(uw-du*dw)/denom:Math.max(0,Math.min(1,uw/uu));
            s=Math.max(0,Math.min(reach,s));
            t=Math.max(0,Math.min(1,(du*s+uw)/uu));
            s=Math.max(0,Math.min(reach,du*t-dw));
            // Recompute the segment point once the ray distance has been clamped.
            t=Math.max(0,Math.min(1,(du*s+uw)/uu));
            double dx=w[0]+d[0]*s-u[0]*t;
            double dy=w[1]+d[1]*s-u[1]*t;
            double dz=w[2]+d[2]*s-u[2]*t;
            double sep=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(sep<=radius&&(best==null||s<best.distance()-1e-5||
                    (Math.abs(s-best.distance())<1e-5&&sep<best.separation())))
                best=new Hit(a,s,sep);
        }
        return best;
    }
    /** Pixel motion projected onto the visible direction of an XYZ world handle. */
    public static double dragLdu(double dx,double dy,double[] facing,int axis,double distanceBlocks) {
        if(facing==null||facing.length!=3||axis<0||axis>2||
                !Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(distanceBlocks))return 0;
        double n=Math.sqrt(dot(facing,facing));if(n<1e-7)return 0;
        double fx=facing[0]/n,fy=facing[1]/n,fz=facing[2]/n;
        double rightLen=Math.hypot(fz,fx);
        if(rightLen<1e-6)return 0;
        double[] right={fz/rightLen,0,-fx/rightLen};
        double[] up={fy*right[2],fz*right[0]-fx*right[2],-fy*right[0]};
        double sx=right[axis],sy=-up[axis]; // screen Y increases downwards
        double screenLength=Math.hypot(sx,sy);
        if(screenLength<0.18)return 0; // Axis is almost pointing directly at the player.
        double unit=Math.min(1.6,Math.max(0.12,Math.abs(distanceBlocks)*0.055));
        return (dx*sx+dy*sy)/screenLength*unit;
    }
    public static double step(double raw,double resolution,double cap){
        if(!Double.isFinite(raw)||!Double.isFinite(resolution)||resolution<=0||
                !Double.isFinite(cap)||cap<=0)return 0;
        return Math.max(-cap,Math.min(cap,Math.rint(raw/resolution)*resolution));
    }
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
}
