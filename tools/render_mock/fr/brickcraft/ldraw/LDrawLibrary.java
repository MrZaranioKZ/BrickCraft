package fr.brickcraft.ldraw; public class LDrawLibrary{
 public static int argb(int code,int rgb){return (code == 100 ? 0x90000000 : 0xFF000000) | rgb;}
 public static int meshColor(int code,int main,int rgb){return argb(main,rgb);}public static int tint(int argb,int code,double b){return argb;}
 public static Mesh mesh(String id){return new Mesh(new float[]{0,0,0, 1,0,0, 1,0,1},new int[]{16},1f,.4f,2f,1);}
 public record Mesh(float[] xyz,int[] colors,float width,float height,float depth,int triangles){}
 public static int surfaceTint(int argb,int code,double nx,double ny,double nz,long seed){return argb;}
}