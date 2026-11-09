package fr.brickcraft.core;

import net.minecraft.util.math.BlockPos;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.util.ArrayList;
import java.util.List;

/**
 * 1 LEGO stud = 1 Minecraft block X/Z, vertical units = 1/5 block.
 * All three Euler axes are saved: angle (yaw, any degree), pitch and roll (quarter turns).
 * x/y/z locate the minimum cell of the rotated envelope; the solid is centered inside it.
 */
public record BrickPiece(BrickKind kind,int rgb,int x,int y,int z,int turn,int angle,int pitch,int roll,String partId,int colorCode) {
    private static final double EPS=1e-7;
    /** Old code and world saves without LDraw code remain opaque RGB. */
    public BrickPiece(BrickKind kind,int rgb,int x,int y,int z,int turn,int angle,int pitch,int roll,String partId){
        this(kind,rgb,x,y,z,turn,angle,pitch,roll,partId,-1);
    }
    public BrickPiece(BrickKind kind,int rgb,int x,int y,int z,int turn,int angle,int pitch,int roll) {
        this(kind,rgb,x,y,z,turn,angle,pitch,roll,"");
    }
    public String name(){
        if(kind!=BrickKind.LDRAW_PART)return kind.title;
        LDrawLibrary.PartInfo i=LDrawLibrary.info(partId);
        return i==null?"LDraw "+partId:i.name()+" ("+partId+")";
    }
    private double[] dimensions(){
        if(kind==BrickKind.LDRAW_PART){
            LDrawLibrary.Mesh m=LDrawLibrary.mesh(partId);
            if(m!=null)return new double[]{m.width(),m.height(),m.depth()};
        }
        return new double[]{kind.width,kind.plates*.4,kind.length};
    }
    public double bodyWidth(){return dimensions()[0];}
    public double bodyHeight(){return dimensions()[1];}
    public double bodyDepth(){return dimensions()[2];}


    public BrickPiece(BrickKind kind,int rgb,int x,int y,int z,int turn) {
        this(kind,rgb,x,y,z,turn,Math.floorMod(turn,4)*90,0,0);
    }
    public BrickPiece(BrickKind kind,int rgb,int x,int y,int z,int turn,int angle) {
        this(kind,rgb,x,y,z,turn,angle,0,0);
    }
    public static BrickPiece atAngle(BrickKind kind,int rgb,int x,int y,int z,int angle) {
        int a=Math.floorMod(angle,360);
        return new BrickPiece(kind,rgb,x,y,z,a/90,a,0,0);
    }
    public BrickPiece withAngle(int degrees) {
        int a=Math.floorMod(degrees,360);
        return new BrickPiece(kind,rgb,x,y,z,a/90,a,pitch,roll,partId,colorCode);
    }
    public BrickPiece withPitch(int degrees) {
        return new BrickPiece(kind,rgb,x,y,z,turn,angle,Math.floorMod(degrees,360),roll,partId,colorCode);
    }
    public BrickPiece withRoll(int degrees) {
        return new BrickPiece(kind,rgb,x,y,z,turn,angle,pitch,Math.floorMod(degrees,360),partId,colorCode);
    }
    public BrickPiece withColor(int color) {return new BrickPiece(kind,color,x,y,z,turn,angle,pitch,roll,partId,-1);}
    public BrickPiece withLDrawColor(int code) {
        LDrawLibrary.Colour c=LDrawLibrary.color(code);
        if(c==null)return this;
        return new BrickPiece(kind,c.rgb(),x,y,z,turn,angle,pitch,roll,partId,c.code());
    }
    public BrickPiece withPosition(int xx,int yy,int zz) {
        return new BrickPiece(kind,rgb,xx,yy,zz,turn,angle,pitch,roll,partId,colorCode);
    }
    public boolean tilted() {return pitch!=0 || roll!=0;}

    /** Orthogonal local axes in world XYZ (R_y(-yaw) * R_x(pitch) * R_z(roll)). */
    public double[][] axes() {
        double[][] basis={{1,0,0},{0,1,0},{0,0,1}};
        double cy=Math.cos(Math.toRadians(angle)),sy=Math.sin(Math.toRadians(angle));
        double cp=Math.cos(Math.toRadians(pitch)),sp=Math.sin(Math.toRadians(pitch));
        double cr=Math.cos(Math.toRadians(roll)),sr=Math.sin(Math.toRadians(roll));
        for(double[] v:basis) {
            double xx=v[0]*cr-v[1]*sr, yy=v[0]*sr+v[1]*cr, zz=v[2];
            double y2=yy*cp-zz*sp, z2=yy*sp+zz*cp;
            v[0]=xx*cy-z2*sy;
            v[1]=y2;
            v[2]=xx*sy+z2*cy;
        }
        return basis;
    }
    private double[] exactSpans() {
        double[][] a=axes();
        double[] dim=dimensions();
        return new double[]{
            Math.abs(a[0][0])*dim[0]+Math.abs(a[1][0])*dim[1]+Math.abs(a[2][0])*dim[2],
            Math.abs(a[0][1])*dim[0]+Math.abs(a[1][1])*dim[1]+Math.abs(a[2][1])*dim[2],
            Math.abs(a[0][2])*dim[0]+Math.abs(a[1][2])*dim[1]+Math.abs(a[2][2])*dim[2]
        };
    }
    public int width(){return Math.max(1,(int)Math.ceil(exactSpans()[0]-1e-5));}
    public int length(){return Math.max(1,(int)Math.ceil(exactSpans()[2]-1e-5));}
    public int height(){return Math.max(1,(int)Math.ceil(exactSpans()[1]*5-1e-5));}
    public int top(){return y+height();}
    public boolean valid(){
        return kind!=null && turn>=0 && turn<4 && angle>=0 && angle<360 &&
            pitch>=0 && pitch<360 && pitch%90==0 && roll>=0 && roll<360 && roll%90==0 &&
            x>=-29_999_980 && x<=29_999_980 && z>=-29_999_980 && z<=29_999_980 &&
            y>=-2000 && y<=10000 && (rgb&0xFF000000)==0 && colorCode>=-1 && colorCode<=65535 &&
            (kind!=BrickKind.LDRAW_PART || (partId!=null && partId.matches("[a-zA-Z0-9_-]{1,32}") &&
            LDrawLibrary.contains(partId)));
    }

    public double[] center() {
        return new double[]{x+width()/2.,y/5.0+height()/10.0,z+length()/2.};
    }
    /** The 8 exact corners of the oriented body, in world block units. */
    public double[][] vertices(){
        double[] c=center();double[][] a=axes();
        double[] e={bodyWidth()/2.,bodyHeight()/2.,bodyDepth()/2.};
        double[][] result=new double[8][3];
        for(int i=0;i<8;i++){
            for(int d=0;d<3;d++) result[i][d]=c[d]+
                ((i&1)==0?-1:1)*e[0]*a[0][d]+
                ((i&2)==0?-1:1)*e[1]*a[1][d]+
                ((i&4)==0?-1:1)*e[2]*a[2][d];
        }
        return result;
    }
    /** Horizontal quadrilateral for the old Studio and yaw-only SAT. */
    public double[][] corners(){
        if(tilted()) return new double[][]{{x,z},{x+width(),z},{x+width(),z+length()},{x,z+length()}};
        double w=bodyWidth(),l=bodyDepth(),c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));
        double midX=x+width()/2.,midZ=z+length()/2.;
        double[][] offsets={{-w/2,-l/2},{w/2,-l/2},{w/2,l/2},{-w/2,l/2}};
        double[][] result=new double[4][2];
        for(int i=0;i<4;i++) {
            double dx=offsets[i][0], dz=offsets[i][1];
            result[i][0]=midX+dx*c-dz*s; result[i][1]=midZ+dx*s+dz*c;
        }
        return result;
    }
    public double[] stud(int ix,int iz){
        double[][] a=axes();double[] c=center();
        double u=ix+.5-bodyWidth()/2.,v=bodyHeight()/2.,w=iz+.5-bodyDepth()/2.;
        return new double[]{c[0]+u*a[0][0]+v*a[1][0]+w*a[2][0],
                c[2]+u*a[0][2]+v*a[1][2]+w*a[2][2]};
    }
    private static boolean positiveOverlap(double[][] a,double[][] b){
        for(int shape=0;shape<2;shape++){
            double[][] p=shape==0?a:b;
            for(int i=0;i<p.length;i++){
                double dx=p[(i+1)%p.length][0]-p[i][0],dz=p[(i+1)%p.length][1]-p[i][1];
                double ax=-dz,az=dx;
                double amin=Double.POSITIVE_INFINITY,amax=Double.NEGATIVE_INFINITY;
                double bmin=Double.POSITIVE_INFINITY,bmax=Double.NEGATIVE_INFINITY;
                for(double[] v:a){double proj=v[0]*ax+v[1]*az;amin=Math.min(amin,proj);amax=Math.max(amax,proj);}
                for(double[] v:b){double proj=v[0]*ax+v[1]*az;bmin=Math.min(bmin,proj);bmax=Math.max(bmax,proj);}
                if(amax<=bmin+EPS || bmax<=amin+EPS)return false;
            }
        }
        return true;
    }
    public boolean horizontalOverlaps(BrickPiece other){
        if(x>=other.x+other.width()||x+width()<=other.x||z>=other.z+other.length()||z+length()<=other.z)return false;
        return positiveOverlap(corners(),other.corners());
    }
    /** 3D oriented box SAT; unlike the previous 2D test, works for pitched/rolled bricks. */
    public boolean intersects(BrickPiece other){
        if(y>=other.top()||top()<=other.y || !horizontalOverlaps(other))return false;
        double[][] axesA=axes(),axesB=other.axes();
        double[] ea={bodyWidth()/2.,bodyHeight()/2.,bodyDepth()/2.};
        double[] eb={other.bodyWidth()/2.,other.bodyHeight()/2.,other.bodyDepth()/2.};
        double[] ca=center(),cb=other.center();
        double[] delta={cb[0]-ca[0],cb[1]-ca[1],cb[2]-ca[2]};
        for(int k=0;k<15;k++){
            double[] n;
            if(k<3)n=axesA[k];
            else if(k<6)n=axesB[k-3];
            else {
                double[] a=axesA[(k-6)/3],b=axesB[(k-6)%3];
                n=new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
                if(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]<EPS)continue;
            }
            double dist=Math.abs(dot(delta,n));
            double ra=0,rb=0;
            for(int j=0;j<3;j++){ra+=ea[j]*Math.abs(dot(axesA[j],n));rb+=eb[j]*Math.abs(dot(axesB[j],n));}
            if(dist>=ra+rb-EPS)return false;
        }
        return true;
    }
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private boolean intersectsSquare(int sx,int sz,double[][] polygon){
        double[][] square={{sx,sz},{sx+1,sz},{sx+1,sz+1},{sx,sz+1}};
        return positiveOverlap(polygon,square);
    }
    public List<BlockPos> occupiedCells(){
        List<BlockPos> result=new ArrayList<>();
        int yStart=Math.floorDiv(y,5);
        int yEnd=Math.floorDiv(top()+(kind.studs?1:0)-1,5);
        boolean exact=(!tilted() && angle%90==0);
        double[][] poly=exact?null:corners();
        for(int yy=yStart;yy<=yEnd;yy++)for(int xx=x;xx<x+width();xx++)for(int zz=z;zz<z+length();zz++)
            if(exact||intersectsSquare(xx,zz,poly))result.add(new BlockPos(xx,yy,zz));
        return result;
    }
    public BlockPos renderAnchor(){
        if(!tilted() && angle%90==0)return new BlockPos(x,Math.floorDiv(y,5),z);
        double[][] poly=corners();
        for(int xx=x;xx<x+width();xx++)for(int zz=z;zz<z+length();zz++)
            if(intersectsSquare(xx,zz,poly))return new BlockPos(xx,Math.floorDiv(y,5),zz);
        throw new IllegalStateException("Missing render anchor");
    }
}
