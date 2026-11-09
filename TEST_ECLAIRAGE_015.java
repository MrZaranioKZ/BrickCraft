import fr.brickcraft.client.BrickLightingMath;
import fr.brickcraft.client.BrickModelOcclusion;

/** Fast regression tests for the Minecraft-1.21.1-independent lighting math. */
public final class TEST_ECLAIRAGE_015 {
    private static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    private static boolean near(float a,float b){return Math.abs(a-b)<1e-5f;}
    public static void main(String[] args){
        float up=1f,down=.50f,north=.80f,south=.80f,east=.60f,west=.60f;
        check(near(BrickLightingMath.directional(0,1,0,up,down,north,south,east,west),up),"top");
        check(near(BrickLightingMath.directional(0,-1,0,up,down,north,south,east,west),down),"bottom");
        check(near(BrickLightingMath.directional(1,0,0,up,down,north,south,east,west),east),"east");
        check(near(BrickLightingMath.directional(-1,0,0,up,down,north,south,east,west),west),"west");
        check(near(BrickLightingMath.directional(0,0,1,up,down,north,south,east,west),south),"south");
        check(near(BrickLightingMath.directional(0,0,-1,up,down,north,south,east,west),north),"north");
        float slope=BrickLightingMath.directional(1,1,0,up,down,north,south,east,west);
        check(slope>east&&slope<up,"smooth slopes");
        check(BrickLightingMath.shade(0x80FFCCAA,.5f)==0x80806655,"RGBA and half brightness");
        check(BrickLightingMath.shadowSky(15,1)==15,"no cast shadow outside");
        check(BrickLightingMath.shadowSky(15,.5f)<15,"sky shadow inside");
        check(BrickLightingMath.shadowSky(0,.2f)==0,"floor");
        BrickModelOcclusion grid=new BrickModelOcclusion();
        grid.add(0,0,0,0,1,1,1);  // wall at x=0..1
        grid.add(1,1.2,0,0,2.2,1,1); // second wall at x=1.2..2.2
        check(grid.indexedReferences()>0,"spatial index allocated");
        check(grid.exposure(.75,.5,.5,1,0,0,0)<1,"nearby LEGO shades surface");
        check(grid.exposure(.5,.5,.5,-1,0,0,0)==1,"open surface remains bright");
        check(grid.exposure(1.4,.5,.5,-1,0,0,1)<1,"light shadow opposite direction");
        grid.add(2,0,0,0,5000,5000,5000); // skip huge parts rather than overflow
        check(grid.indexedReferences()<1000,"spatial index remains bounded");
        System.out.println("OK : 6 faces vanilla, pentes, RGBA, ciel, occlusion, index borne");
    }
}
