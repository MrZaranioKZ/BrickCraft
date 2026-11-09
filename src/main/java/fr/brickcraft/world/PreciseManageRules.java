package fr.brickcraft.world;

/** Additional rules shared with independent tests; all real checks remain server-side. */
public final class PreciseManageRules {
    public static final int RADIUS=192;
    private PreciseManageRules(){}
    public static boolean inRange(double playerX,double playerY,double playerZ,int x,int y,int z){
        if(!Double.isFinite(playerX)||!Double.isFinite(playerY)||!Double.isFinite(playerZ))return false;
        double dx=playerX-(x+.5),dy=playerY-(y+.5),dz=playerZ-(z+.5);
        return dx*dx+dy*dy+dz*dz<=RADIUS*RADIUS;
    }
}
