import fr.brickcraft.core.BrickPalette;
import fr.brickcraft.BrickCommandPalette;
import fr.brickcraft.world.BrickCellCoordinates;

public final class BrickCreativeCommandsV020Test {
    private static void check(boolean pass,String label){if(!pass)throw new AssertionError(label);}
    public static void main(String[] args){
        check(BrickCommandPalette.rgb("rouge")==BrickPalette.COLORS[4],"French palette");
        check(BrickCommandPalette.rgb("bleu") == BrickPalette.COLORS[8],"French blue");
        check(BrickCommandPalette.rgb("red")==BrickPalette.COLORS[4],"English palette");
        check(BrickCommandPalette.rgb("gris_fonce")==BrickPalette.COLORS[3],"Accent normalization");
        check(BrickCommandPalette.rgb("0x35B0FF")==0x35B0FF,"Hexadecimal RGB");
        check(BrickCommandPalette.rgb("#00AA11")==0x00AA11,"Hash RGB");
        check(BrickCommandPalette.rgb("16711680")==0xFF0000,"Decimal RGB");
        boolean bad=false;try{BrickCommandPalette.rgb("0xZZZZZZ");}catch(IllegalArgumentException e){bad=true;}
        check(bad,"Bad colour must be rejected");
        int[][] cases={{10,325,-17,10,64,-17},{15,327,-14,10,64,-17},
                {-350,-25,122,-353,-6,120},{245600,1005,-200,245580,199,-201}};
        for(int[] a:cases){
            int[] relative=BrickCellCoordinates.relative(a[0],a[1],a[2],a[3],a[4],a[5]);
            int[] round=BrickCellCoordinates.absolute(relative[0],relative[1],relative[2],a[3],a[4],a[5]);
            check(round[0]==a[0] && round[1]==a[1] && round[2]==a[2],"Relative save/reload");
            // Pasted to a different cell: the piece must follow the new world coordinates.
            int[] moved=BrickCellCoordinates.absolute(relative[0],relative[1],relative[2],a[3]+32,a[4]-5,a[5]+12);
            check(moved[0]==a[0]+32 && moved[1]==a[1]-25 && moved[2]==a[2]+12,"Paste translation");
        }
        System.out.println("BrickCraft V0.20: 8 RGB checks + 8 coordinate tests OK");
    }
}
