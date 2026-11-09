package fr.brickcraft.core;
import net.minecraft.util.math.BlockPos;
public record BrickPiece(BrickKind kind,int rgb,int colorCode,BlockPos anchor){
 public BlockPos renderAnchor(){return anchor;} public boolean tilted(){return false;} public int angle(){return 0;}
 public int pitch(){return 0;}public int roll(){return 0;}public double[] center(){return new double[]{anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5};}
 public int y(){return anchor.getY()*5;} public int top(){return y()+5;} public int x(){return anchor.getX();}public int z(){return anchor.getZ();}
 public int width(){return 1;}public int length(){return 1;}public int height(){return 5;} public String partId(){return "3004";}
}