package fr.brickcraft.importer;

/** Transforms a normalized BrickCraft LDraw mesh back to exact local LDU and onto a full MPD matrix. */
public final class PreciseLDrawGeometry {
    private PreciseLDrawGeometry() {}
    /** The mesh stores (x-minX)/20, (maxY-y)/20, (z-minZ)/20. */
    public static double[] localLdu(double[] bounds,float x,float y,float z) {
        if(bounds==null||bounds.length!=6)throw new IllegalArgumentException("LDraw original bounds required");
        return new double[]{bounds[0]+x*20.0,bounds[4]-y*20.0,bounds[2]+z*20.0};
    }
    /** No rounding or Euler-angle decomposition occurs, including for half-studs and angled joints. */
    public static double[] worldLdu(LDrawProjectReader.Instance part,double[] originalBounds,float x,float y,float z){
        double[] v=localLdu(originalBounds,x,y,z),m=part.matrix();
        return new double[]{part.x()+m[0]*v[0]+m[1]*v[1]+m[2]*v[2],
                            part.y()+m[3]*v[0]+m[4]*v[1]+m[5]*v[2],
                            part.z()+m[6]*v[0]+m[7]*v[1]+m[8]*v[2]};
    }
    public static double[] worldLdu(LDrawProjectReader.Instance part,double x,double y,double z){
        double[] m=part.matrix();
        return new double[]{part.x()+m[0]*x+m[1]*y+m[2]*z,
                            part.y()+m[3]*x+m[4]*y+m[5]*z,
                            part.z()+m[6]*x+m[7]*y+m[8]*z};
    }
}
