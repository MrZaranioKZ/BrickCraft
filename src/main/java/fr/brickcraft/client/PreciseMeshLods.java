package fr.brickcraft.client;

import fr.brickcraft.ldraw.LDrawLibrary;
import java.util.*;

/**
 * Cache-friendly vertex-cluster LODs for LDraw mesh geometry. Does not replace parts
 * with boxes: each level is assembled from the part's own triangles.
 *
 * This is a deliberately conservative coarse reducer, not a watertight quadric
 * simplifier: thin details may be distorted, so full fidelity remains available.
 */
public final class PreciseMeshLods {
    private PreciseMeshLods() {}
    public record Levels(LDrawLibrary.Mesh full, LDrawLibrary.Mesh medium,
                         LDrawLibrary.Mesh low, LDrawLibrary.Mesh tiny) {
        public int cachedTriangles() {
            java.util.Set<LDrawLibrary.Mesh> seen=java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<>());
            int total=0;
            for(var mesh:java.util.List.of(full,medium,low,tiny))if(seen.add(mesh))total+=mesh.triangles();
            return total;
        }
    }
    public static Levels generate(LDrawLibrary.Mesh source){
        Objects.requireNonNull(source);
        if(source.isEmpty())return new Levels(source,source,source,source);
        // 16 cells retain most studs, 8 simplify curves and 4 retain the broad shape.
        LDrawLibrary.Mesh medium=simplify(source,16);
        LDrawLibrary.Mesh low=simplify(source,8);
        LDrawLibrary.Mesh tiny=simplify(source,4);
        return new Levels(source,preferSmaller(source,medium),
                preferSmaller(source,low),preferSmaller(source,tiny));
    }
    private static LDrawLibrary.Mesh preferSmaller(LDrawLibrary.Mesh source,LDrawLibrary.Mesh candidate){
        return candidate.triangles()>0 && candidate.triangles()<=source.triangles()?candidate:source;
    }
    private record TriangleKey(long a,long b,long c,int colour){}

    /** Quantize each axis independently to preserve thinner plates/rods. */
    public static LDrawLibrary.Mesh simplify(LDrawLibrary.Mesh source,int subdivisions){
        if(subdivisions<2 || subdivisions>128)throw new IllegalArgumentException("subdivisions 2..128");
        if(source.isEmpty())return source;
        float[] input=source.xyz();int[] sourceColors=source.colors();
        int count=Math.min(source.triangles(),Math.min(input.length/9,sourceColors.length));
        if(count==0)return source;
        float[][] outVertices=new float[count*3][];
        int[] outColors=new int[count];int accepted=0;
        Set<TriangleKey> seen=new HashSet<>(Math.min(count*2,160_000));
        double[] sizes={source.width(),source.height(),source.depth()};
        for(int i=0;i<count;i++){
            long[] keys=new long[3];float[][] verts=new float[3][3];
            for(int j=0;j<3;j++){
                int[] coords=new int[3];
                for(int a=0;a<3;a++){
                    float v=input[i*9+j*3+a];
                    if(!Float.isFinite(v))return source;
                    double size=sizes[a];
                    coords[a]=size<=0?0:(int)Math.round(Math.max(0,Math.min(subdivisions,v/size*subdivisions)));
                    verts[j][a]=(float)(size<=0?v:size*coords[a]/subdivisions);
                }
                keys[j]=((long)coords[0]<<16)|((long)coords[1]<<8)|coords[2];
            }
            if(keys[0]==keys[1] || keys[1]==keys[2] || keys[0]==keys[2])continue;
            double ux=verts[1][0]-verts[0][0],uy=verts[1][1]-verts[0][1],uz=verts[1][2]-verts[0][2];
            double vx=verts[2][0]-verts[0][0],vy=verts[2][1]-verts[0][1],vz=verts[2][2]-verts[0][2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            if(nx*nx+ny*ny+nz*nz<1e-13)continue;
            long[] sorted=keys.clone();Arrays.sort(sorted);
            TriangleKey key=new TriangleKey(sorted[0],sorted[1],sorted[2],sourceColors[i]);
            if(!seen.add(key))continue;
            outVertices[accepted*3]=verts[0];outVertices[accepted*3+1]=verts[1];
            outVertices[accepted*3+2]=verts[2];outColors[accepted]=sourceColors[i];accepted++;
        }
        if(accepted==0)return source;
        float[] xyz=new float[accepted*9];
        for(int i=0;i<accepted*3;i++)System.arraycopy(outVertices[i],0,xyz,i*3,3);
        return new LDrawLibrary.Mesh(xyz,Arrays.copyOf(outColors,accepted),
                source.width(),source.height(),source.depth(),accepted);
    }
}
