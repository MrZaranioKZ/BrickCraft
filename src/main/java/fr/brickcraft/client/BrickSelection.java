package fr.brickcraft.client;

import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPalette;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.ldraw.LDrawLibrary;

/** Creative palette shared by Studio and in-world hotbar builder. */
public final class BrickSelection {
    private BrickSelection() {}
    public static final int[] STEPS = {90,45,15,5,1};
    public static BrickKind kind=BrickKind.BRICK_2X2;
    public static String partId="";
    public static int rgb=BrickPalette.COLORS[4];
    public static int colorCode=-1;
    public static int turn=0;  // retained for older Studio and tests
    public static int angle=0;
    public static int pitch=0,roll=0;
    public static boolean sampled=false;
    public static int stepIndex=0;
    public static int layer=0;
    public static int step(){return STEPS[stepIndex];}
    public static void cycleStep(){stepIndex=(stepIndex+1)%STEPS.length;}
    public static void resetRotation(){angle=0;turn=0;pitch=0;roll=0;}
    public static void rotatePitch(int direction){pitch=Math.floorMod(pitch+90*direction,360);}
    public static void rotateRoll(int direction){roll=Math.floorMod(roll+90*direction,360);}
    public static void sample(BrickPiece p) {
        kind=p.kind();partId=p.partId();rgb=p.rgb();colorCode=p.colorCode();angle=p.angle();turn=p.turn();pitch=p.pitch();roll=p.roll();
        sampled=true;
    }
    public static BrickPiece oriented(BrickKind pieceKind,int x,int y,int z) {
        int a=Math.floorMod(angle,360);
        return new BrickPiece(pieceKind,rgb,x,y,z,a/90,a,pitch,roll,
            pieceKind==BrickKind.LDRAW_PART?partId:"",colorCode);
    }
    public static void rotate(int direction) {
        angle=Math.floorMod(angle+step()*direction,360);
        turn=angle/90;
    }
    public static BrickPiece piece(int x,int z) {
        return oriented(kind,x,layer*2,z);
    }
    public static void setColor(LDrawLibrary.Colour c){
        if(c!=null){rgb=c.rgb();colorCode=c.code();}
    }
    public static void setRgb(int value){rgb=value&0xFFFFFF;colorCode=-1;}
    public static void nextColor() {
        java.util.List<LDrawLibrary.Colour> available=LDrawLibrary.colors();
        if(!available.isEmpty()){
            int index=0;
            for(int i=0;i<available.size();i++)if(available.get(i).code()==colorCode){index=i+1;break;}
            setColor(available.get(index%available.size()));return;
        }
        int index=0;
        for(int i=0;i<BrickPalette.COLORS.length;i++)
            if(BrickPalette.COLORS[i]==rgb){index=i+1;break;}
        setRgb(BrickPalette.COLORS[index%BrickPalette.COLORS.length]);
    }
    public static String selectedName(){
        if(kind!=BrickKind.LDRAW_PART)return kind.title;
        LDrawLibrary.PartInfo info=LDrawLibrary.info(partId);
        return info==null?"LDraw "+partId:info.name()+" ("+partId+")";
    }
    public static String colorName() {
        if(colorCode>=0 && LDrawLibrary.color(colorCode)!=null)
            return LDrawLibrary.describe(colorCode,rgb);
        for(int i=0;i<BrickPalette.COLORS.length;i++)
            if(BrickPalette.COLORS[i]==rgb) return BrickPalette.NAMES[i];
        return String.format("#%06X",rgb);
    }
    public static void nextKind() {
        BrickKind[] all=BrickKind.values();
        kind=all[(kind==BrickKind.LDRAW_PART?0:kind.ordinal()+1)%(all.length-1)];partId="";
    }
}
