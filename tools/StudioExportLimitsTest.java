import fr.brickcraft.client.StudioAssembly;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.core.StudioExportLimits;
import java.util.HashSet;
import java.util.Set;

public class StudioExportLimitsTest {
    private static int checks;
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);checks++;
    }
    private static BrickPiece p(BrickKind kind,int x,int y,int z){
        return new BrickPiece(kind,0xBB4422,x,y,z,0);
    }
    public static void main(String[] ignored){
        for(int grid:new int[]{16,32,64,128,256})check(StudioExportLimits.validGrid(grid),"grid " +grid);
        for(int grid:new int[]{0,12,15,63,257,1024})check(!StudioExportLimits.validGrid(grid),"reject grid "+grid);
        check(StudioExportLimits.validLocal(p(BrickKind.BRICK_2X4,250,0,250),256),"far footprint");
        check(!StudioExportLimits.validLocal(p(BrickKind.BRICK_2X4,254,0,253),256),"overflow footprint");
        check(StudioExportLimits.validLocal(p(BrickKind.PLATE_1X2,1,1278,1),256),"top layer allowed");
        check(!StudioExportLimits.validLocal(p(BrickKind.PLATE_1X2,1,1280,1),256),"top exceeds limit");
        check(!StudioExportLimits.validLocal(p(BrickKind.PLATE_1X2,-1,0,1),256),"negative x");
        check(!StudioExportLimits.validLocal(p(BrickKind.PLATE_1X2,0,-1,1),256),"negative y");
        check(StudioExportLimits.upperBoundCells(p(BrickKind.BRICK_2X4,0,0,0))>=8,"footprint upper bound");
        check(StudioExportLimits.upperBoundCells(p(BrickKind.BRICK_2X4,0,0,0))<=24,"bound realistic");
        BrickPiece far=StudioExportLimits.inWorld(p(BrickKind.BRICK_1X2,100,50,101),1000,64,-1000);
        check(far.x()==1100 && far.y()==370 && far.z()==-899,"world coordinate origin");
        check(far.width()==1 && far.length()==2,"shape unchanged");
        for(int k=1;k<=24;k++)check(StudioExportLimits.validBatch(0,k,0,100),"valid first batch "+k);
        for(int offset=0;offset<4096;offset+=24){
            int count=Math.min(24,4096-offset);
            check(StudioExportLimits.validBatch(offset,count,offset,4096),"valid complete batch "+offset);
        }
        check(!StudioExportLimits.validBatch(0,25,0,100),"oversized packet");
        check(!StudioExportLimits.validBatch(0,0,0,100),"empty packet");
        check(!StudioExportLimits.validBatch(24,24,0,100),"out of order");
        check(!StudioExportLimits.validBatch(50,24,50,60),"beyond total");
        check(!StudioExportLimits.validBatch(0,1,0,4097),"too many pieces");
        StudioAssembly assembly=new StudioAssembly();
        check(assembly.setGridSize(256),"large studio setup");
        // Disjoint placements, including far beyond the old 12x12 envelope.
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            // Use one stud with 2-stud gaps to avoid any collision; 4096 pieces total.
            BrickPiece piece=p(BrickKind.PLATE_1X1,x*3,0,z*3);
            
            check(assembly.add(piece),"large assembly x="+x+" z="+z);
        }
        check(assembly.size()==256,"256 pieces across expanded Studio");
        check(assembly.pieces().stream().anyMatch(p->p.x()>12),"positions greater than old 12");
        System.out.println("LARGE EXPORT LIMITS: "+checks+" assertions OK");
    }
}
