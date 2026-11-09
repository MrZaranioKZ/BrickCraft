import fr.brickcraft.core.BrickPbrProfiles;
public final class TEST_PBR_PROFILES_V022 {
    public static void main(String[] a) {
        test("SOLID",255,0);test("CHROME",255,1);test("METAL",255,2);
        test("MATTE_METALLIC",255,3);test("PEARLESCENT",255,4);
        test("RUBBER",255,5);test("FABRIC",255,6);test("GLITTER",255,7);
        test("SPECKLE",255,8);test("SOLID",160,9);
        for(int i=0;i<BrickPbrProfiles.COUNT;i++) {
            float u=BrickPbrProfiles.u(i);
            if(!(u>=i/10f && u<(i+1)/10f)) throw new AssertionError("UV: "+i);
        }
        System.out.println("OK: 10 LabPBR material slots and UVs");
    }
    static void test(String finish,int alpha,int expected){
        int found=BrickPbrProfiles.slot(finish,alpha);
        if(found!=expected) throw new AssertionError(finish+": "+found+" != "+expected);
    }
}
