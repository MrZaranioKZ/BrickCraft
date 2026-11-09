package fr.brickcraft.core; public enum BrickKind {
 BRICK_1X1(false,true,1,1,3), LDRAW_PART(false,true,1,1,3);
 public final boolean slope,studs;public final int width,length,plates;
 BrickKind(boolean slope,boolean studs,int w,int d,int h){this.slope=slope;this.studs=studs;width=w;length=d;plates=h;}
}