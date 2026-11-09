import fr.brickcraft.core.BrickCatalog;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPalette;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.client.BrickSelection;
import fr.brickcraft.client.StudioAssembly;

public final class Upgrade031Test {
    private static int asserts=0;
    private static void check(boolean condition,String message){asserts++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        check(BrickCatalog.search("1x4").contains(BrickKind.BRICK_1X4),"find 1x4");
        check(BrickCatalog.search("3010").contains(BrickKind.BRICK_1X4),"find design id 3010");
        BrickCatalog.Entry element=BrickCatalog.findElement("6258572");
        check(element!=null && element.kind()==BrickKind.BRICK_1X2 && element.rgb()==0xFF6D77,
                "LEGO element 6258572 coral brick 1x2");
        check(BrickCatalog.search("6258572").size()==1,"element selects unique kind");
        check(BrickPalette.NAMES.length==BrickPalette.COLORS.length,"color palette lengths");
        BrickSelection.angle=0;BrickSelection.pitch=0;BrickSelection.roll=0;
        BrickSelection.rotate(1);check(BrickSelection.angle==90,"first yaw");
        BrickSelection.rotate(1);check(BrickSelection.angle==180,"second yaw");
        BrickSelection.rotate(1);check(BrickSelection.angle==270,"third yaw");
        BrickSelection.rotate(1);check(BrickSelection.angle==0,"fourth yaw");
        BrickSelection.rotatePitch(1);check(BrickSelection.pitch==90,"pitch 90");
        BrickSelection.rotateRoll(1);check(BrickSelection.roll==90,"roll 90");
        BrickSelection.resetRotation();check(BrickSelection.pitch==0&&BrickSelection.roll==0&&
                BrickSelection.angle==0,"reset 3 axes");

        BrickPiece flat=BrickPiece.atAngle(BrickKind.BRICK_1X2,0xFF6D77,0,0,0,0);
        BrickPiece alongZ=flat.withPitch(90);
        BrickPiece alongX=flat.withRoll(90);
        check(flat.width()==1 && flat.length()==2 && flat.height()==6,"flat dimensions");
        check(alongZ.height()==10,"standing pitch 1x2 height two blocks");
        check(alongX.height()==5,"standing roll height one block");
        check(alongZ.valid()&&alongX.valid(),"tilted valid");
        check(!flat.intersects(flat.withPosition(0,flat.height(),0)),"stacked plates don't collide");
        check(flat.intersects(flat.withPosition(0,0,0)),"same location must collide");

        for(int pitch=0;pitch<360;pitch+=90)for(int roll=0;roll<360;roll+=90)
            for(int yaw=0;yaw<360;yaw+=15){
                BrickPiece p=flat.withPitch(pitch).withRoll(roll).withAngle(yaw);
                check(p.valid(),"angle valid: "+yaw+"/"+pitch+"/"+roll);
                check(p.width()>0&&p.length()>0&&p.height()>0,"envelope positive");
                check(!p.occupiedCells().isEmpty(),"occupied nonempty");
                check(p.occupiedCells().contains(p.renderAnchor()),"renderer anchor in occupied cell");
                check(p.intersects(p),"self collision");
                check(!p.intersects(p.withPosition(p.x()+p.width()+1,p.y(),p.z())),"disjoint x");
            }
        StudioAssembly assembly=new StudioAssembly();
        BrickPiece low=BrickPiece.atAngle(BrickKind.PLATE_1X2,0xFF0000,0,0,0,0);
        BrickPiece upper=low.withPosition(0,low.top(),0);
        check(assembly.add(low),"plate 1");
        check(assembly.add(upper),"plate 2");
        check(assembly.remove(1),"remove only upper plate");
        check(assembly.size()==1&&assembly.get(0).equals(low),"lower plate preserved");
        check(assembly.undo()&&assembly.size()==2,"studio undo restores upper");
        check(assembly.redo()&&assembly.size()==1,"redo removes upper");
        System.out.println("BRICKCRAFT 0.3.1: "+asserts+" targeted assertions OK");
    }
}
