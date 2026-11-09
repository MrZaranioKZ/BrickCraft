package fr.brickcraft.client;

/** Precision-safe reference equations for VBO local coordinates.
 *  Each cached triangle is relative to the projection's BlockEntity anchor.
 *  The world position is anchor + vertex; the camera-relative position is
 *  world - camera. Both operations should agree with the legacy renderer.
 */
public final class GpuLocalSpace {
    private GpuLocalSpace(){}
    public static double local(double ldrawAxis,double minAxis){
        return ldrawAxis/20.0-minAxis;
    }
    public static double relativeToCamera(double vertexLocal,double anchor,double camera){
        return vertexLocal + (anchor-camera);
    }
    public static double[] worldToCamera(double[] local,double[] anchor,double[] camera){
        if(local.length!=3||anchor.length!=3||camera.length!=3)throw new IllegalArgumentException("3D only");
        return new double[]{relativeToCamera(local[0],anchor[0],camera[0]),
                relativeToCamera(local[1],anchor[1],camera[1]),
                relativeToCamera(local[2],anchor[2],camera[2])};
    }
}
