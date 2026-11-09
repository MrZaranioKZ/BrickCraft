package fr.brickcraft.world;

import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.core.BrickCapacityConfig;
import java.util.*;
import java.util.function.Function;

/**
 * Server-side, deterministic collision voxelizer of original (not LOD) LDraw triangles.
 * Triangle surface rasterization and multi-axis parity keep doorways open even with imperfect LDraw meshes.
 * Sub-block voxels are 1/8 of a Minecraft block; input matrices remain lossless.
 * This class has no Minecraft runtime dependency and can be tested with plain javac.
 */
public final class PreciseMeshCollisionPlan {
    private PreciseMeshCollisionPlan(){}
    public static final int RESOLUTION=8;
    public static final int MAX_CELLS=BrickCapacityConfig.HARD_CELLS, MAX_TRIANGLES=BrickCapacityConfig.HARD_TRIANGLES;
    private static final int MAX_PART_VOXELS=2_500_000;
    public record Geometry(float[] xyz,double[] bounds){
        public Geometry {
            if(xyz==null||xyz.length%9!=0||bounds==null||bounds.length!=6)
                throw new IllegalArgumentException("Maillage LDraw incomplet");
        }
    }
    public record Cell(int x,int y,int z){}
    public record Result(Map<Cell,long[]> masks,int pieces,int triangles){
        public Result {masks=Collections.unmodifiableMap(new LinkedHashMap<>(masks));}
    }
    /** Bits: index = (localY * 64 + localZ * 8 + localX), with local coordinates 0..7. */
    public static boolean occupied(long[] bits,int x,int y,int z){
        int bit=y*64+z*8+x;
        return bits!=null&&bits.length==8&&(bits[bit>>>6]&(1L<<(bit&63)))!=0;
    }
    public static int occupiedCount(long[] bits){int n=0;for(long b:bits)n+=Long.bitCount(b);return n;}

    public static Result build(List<LDrawProjectReader.Instance> parts,double originX,double originY,double originZ,
                               int anchorX,int anchorY,int anchorZ,Function<String,Geometry> geometry){
        Incremental build=new Incremental(parts,originX,originY,originZ,anchorX,anchorY,anchorZ,geometry,MAX_CELLS,MAX_TRIANGLES);
        build.advance(parts.size());
        return build.finish();
    }
    /** Incremental preprocessing: never voxelize the whole castle on a networking callback. */
    public static final class Incremental {
        private final List<LDrawProjectReader.Instance> parts;
        private final double ox,oy,oz;
        private final int ax,ay,az,cellLimit,triangleLimit;
        private final Function<String,Geometry> geometry;
        private final Map<Cell,long[]> all=new LinkedHashMap<>();
        private int completed=0,triangles=0;
        public Incremental(List<LDrawProjectReader.Instance> parts,double ox,double oy,double oz,
                           int ax,int ay,int az,Function<String,Geometry> geometry,int cellLimit,int triangleLimit){
            if(cellLimit<1||cellLimit>MAX_CELLS||triangleLimit<1||triangleLimit>MAX_TRIANGLES)
                throw new IllegalArgumentException("Budget physique invalide");
            this.parts=List.copyOf(parts);this.ox=ox;this.oy=oy;this.oz=oz;
            this.ax=ax;this.ay=ay;this.az=az;this.geometry=geometry;
            this.cellLimit=cellLimit;this.triangleLimit=triangleLimit;
        }
        public int completed(){return completed;}
        public int total(){return parts.size();}
        public boolean done(){return completed>=parts.size();}
        public void advance(int budget){
            for(int n=0;n<budget&&!done();n++){
                var part=parts.get(completed);
                Geometry g=geometry.apply(part.partId());
                if(g==null||g.xyz().length==0)throw new IllegalArgumentException("Maillage LDraw absent : "+part.partId());
                long next=(long)triangles+g.xyz().length/9;
                if(next>triangleLimit)throw new IllegalArgumentException("Triangles physiques : budget "+triangleLimit+" depasse (piece "+(completed+1)+")");
                addPart(part,g,ox,oy,oz,ax,ay,az,all);
                if(all.size()>cellLimit)throw new IllegalArgumentException("Cellules physiques : budget "+cellLimit+" depasse (piece "+(completed+1)+")");
                triangles=(int)next;completed++;
            }
        }
        public Result finish(){
            if(!done())throw new IllegalStateException("Construction physique inachevee");
            all.entrySet().removeIf(e->occupiedCount(e.getValue())==0);
            return new Result(all,completed,triangles);
        }
    }
    private static void addPart(LDrawProjectReader.Instance part,Geometry g,
                                double ox,double oy,double oz,int ax,int ay,int az,Map<Cell,long[]> destination){
        float[] mesh=g.xyz();double[] b=g.bounds(),m=part.matrix();
        int count=mesh.length/9;
        // World-space coordinates: the same transform as PreciseGpuRenderer (LDraw Y is down).
        double[] t=new double[mesh.length];
        double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
        double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(int k=0;k<mesh.length;k+=3){
            double x=b[0]+mesh[k]*20,y=b[4]-mesh[k+1]*20,z=b[2]+mesh[k+2]*20;
            double wx=ax+(part.x()+m[0]*x+m[1]*y+m[2]*z)/20-ox;
            double wy=ay-(part.y()+m[3]*x+m[4]*y+m[5]*z)/20-oy;
            double wz=az+(part.z()+m[6]*x+m[7]*y+m[8]*z)/20-oz;
            t[k]=wx;t[k+1]=wy;t[k+2]=wz;
            lo[0]=Math.min(lo[0],wx);lo[1]=Math.min(lo[1],wy);lo[2]=Math.min(lo[2],wz);
            hi[0]=Math.max(hi[0],wx);hi[1]=Math.max(hi[1],wy);hi[2]=Math.max(hi[2],wz);
        }
        // Extra padding for ray/parity bounds, never persisted into the world.
        int sx=(int)Math.floor(lo[0]*8)-1,sy=(int)Math.floor(lo[1]*8)-1,sz=(int)Math.floor(lo[2]*8)-1;
        int ex=(int)Math.ceil(hi[0]*8)+1,ey=(int)Math.ceil(hi[1]*8)+1,ez=(int)Math.ceil(hi[2]*8)+1;
        int nx=ex-sx,ny=ey-sy,nz=ez-sz;
        long total=(long)nx*ny*nz;
        if(nx<=0||ny<=0||nz<=0||total>MAX_PART_VOXELS)
            throw new IllegalArgumentException("Piece trop grande pour la physique fine : "+part.partId());
        // Rasterize all triangle surfaces, including sloped faces, rings, curved studs and hollow tubes.
        BitSet solid=new BitSet((int)total);
        for(int i=0;i<count;i++){
            int k=i*9;
            double[] a={t[k],t[k+1],t[k+2]},c={t[k+3],t[k+4],t[k+5]},d={t[k+6],t[k+7],t[k+8]};
            int x0=Math.max(0,(int)Math.floor(Math.min(a[0],Math.min(c[0],d[0]))*8)-sx-1);
            int y0=Math.max(0,(int)Math.floor(Math.min(a[1],Math.min(c[1],d[1]))*8)-sy-1);
            int z0=Math.max(0,(int)Math.floor(Math.min(a[2],Math.min(c[2],d[2]))*8)-sz-1);
            int x1=Math.min(nx-1,(int)Math.ceil(Math.max(a[0],Math.max(c[0],d[0]))*8)-sx);
            int y1=Math.min(ny-1,(int)Math.ceil(Math.max(a[1],Math.max(c[1],d[1]))*8)-sy);
            int z1=Math.min(nz-1,(int)Math.ceil(Math.max(a[2],Math.max(c[2],d[2]))*8)-sz);
            for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){
                if(intersects(a,c,d,(sx+x+.5)/8.0,(sy+y+.5)/8.0,(sz+z+.5)/8.0))
                    solid.set(index(x,y,z,nx,nz));
            }
        }
        // Version 0.12 used a flood-fill from the OUTSIDE for the entire part. One small
        // topological crack / voxel bridge could then label a whole doorway or room SOLID.
        // Instead, fill ONLY samples classified as inside along at least two independent
        // axis-aligned rays. Open (non-manifold) LDraw shells remain walkable.
        byte[] insideVotes=new byte[(int)total];
        fillInteriorByRayParity(t,sx,sy,sz,nx,ny,nz,insideVotes);
        // Keep the anchor cell too: its collision is stored on the anchor BlockEntity.
        int minX=(int)Math.floor(lo[0]*8),minY=(int)Math.floor(lo[1]*8),minZ=(int)Math.floor(lo[2]*8);
        int maxX=(int)Math.ceil(hi[0]*8),maxY=(int)Math.ceil(hi[1]*8),maxZ=(int)Math.ceil(hi[2]*8);
        for(int gy=minY;gy<maxY;gy++)for(int gz=minZ;gz<maxZ;gz++)for(int gx=minX;gx<maxX;gx++){
            int idx=index(gx-sx,gy-sy,gz-sz,nx,nz);
            // Conservative surface rasterization in v0.12 put a FULL micro-cell ABOVE
            // every horizontal face exactly on a 1/8 boundary: players visibly floated.
            // A voxel with 0 inside votes but touching a reliably-inside voxel is the
            // OUTSIDE fringe, not a solid step. Keep isolated open mesh faces walkable.
            if(insideVotes[idx]<2 && (!solid.get(idx) ||
                (insideVotes[idx]==0 && touchesInside(idx,gx-sx,gy-sy,gz-sz,nx,ny,nz,insideVotes))))continue;
            int bx=Math.floorDiv(gx,8),by=Math.floorDiv(gy,8),bz=Math.floorDiv(gz,8);
            Cell cell=new Cell(bx,by,bz);
            long[] bits=destination.computeIfAbsent(cell,k->new long[8]);
            int bit=Math.floorMod(gy,8)*64+Math.floorMod(gz,8)*8+Math.floorMod(gx,8);
            bits[bit>>>6]|=1L<<(bit&63);
        }
    }
    private static boolean touchesInside(int p,int x,int y,int z,int nx,int ny,int nz,byte[] votes){
        return x>0&&votes[p-1]>=2 || x+1<nx&&votes[p+1]>=2 ||
               z>0&&votes[p-nx]>=2 || z+1<nz&&votes[p+nx]>=2 ||
               y>0&&votes[p-nx*nz]>=2 || y+1<ny&&votes[p+nx*nz]>=2;
    }
    private static int index(int x,int y,int z,int nx,int nz){return (y*nz+z)*nx+x;}
    /** Intersection coordinates along each ray, stored in primitive arrays to avoid boxing. */
    private static final class Hits {
        private double[] values=new double[4];
        private int count;
        void add(double value){
            if(count==values.length)values=Arrays.copyOf(values,count*2);
            values[count++]=value;
        }
        void sort(){Arrays.sort(values,0,count);}
    }
    /**
     * Counts parity intervals on three perpendicular ray grids. A closed solid receives
     * three inside votes, a missing surface at most one or two, and a doorway zero.
     * Dedup intersections on shared triangle edges: one quad is two triangles, not two walls.
     */
    private static void fillInteriorByRayParity(double[] triangles,int sx,int sy,int sz,
                                                 int nx,int ny,int nz,byte[] votes){
        final int[] starts={sx,sy,sz},sizes={nx,ny,nz};
        for(int axis=0;axis<3;axis++){
            int u=(axis+1)%3,v=(axis+2)%3;
            HashMap<Integer,Hits> lines=new HashMap<>();
            for(int k=0;k<triangles.length;k+=9){
                double aU=triangles[k+u],aV=triangles[k+v],aAxis=triangles[k+axis];
                double bU=triangles[k+3+u],bV=triangles[k+3+v],bAxis=triangles[k+3+axis];
                double cU=triangles[k+6+u],cV=triangles[k+6+v],cAxis=triangles[k+6+axis];
                double den=(bV-cV)*(aU-cU)+(cU-bU)*(aV-cV);
                if(Math.abs(den)<1e-10)continue; // surface parallel to this ray
                int u0=Math.max(0,(int)Math.floor(Math.min(aU,Math.min(bU,cU))*RESOLUTION-starts[u]-1));
                int v0=Math.max(0,(int)Math.floor(Math.min(aV,Math.min(bV,cV))*RESOLUTION-starts[v]-1));
                int u1=Math.min(sizes[u]-1,(int)Math.ceil(Math.max(aU,Math.max(bU,cU))*RESOLUTION-starts[u]));
                int v1=Math.min(sizes[v]-1,(int)Math.ceil(Math.max(aV,Math.max(bV,cV))*RESOLUTION-starts[v]));
                for(int j=v0;j<=v1;j++){
                    double pv=(starts[v]+j+.5)/RESOLUTION;
                    for(int i=u0;i<=u1;i++){
                        double pu=(starts[u]+i+.5)/RESOLUTION;
                        double wa=((bV-cV)*(pu-cU)+(cU-bU)*(pv-cV))/den;
                        double wb=((cV-aV)*(pu-cU)+(aU-cU)*(pv-cV))/den;
                        double wc=1-wa-wb;
                        if(wa< -1e-9||wb< -1e-9||wc< -1e-9)continue;
                        lines.computeIfAbsent(j*sizes[u]+i,unused->new Hits())
                                .add(wa*aAxis+wb*bAxis+wc*cAxis);
                    }
                }
            }
            for(var entry:lines.entrySet()){
                Hits hits=entry.getValue();hits.sort();
                // Touching coplanar/adjacent LDraw triangles describe one crossing.
                int unique=0;
                for(int k=0;k<hits.count;k++){
                    double h=hits.values[k];
                    if(unique==0||h-hits.values[unique-1]>1e-5)hits.values[unique++]=h;
                }
                // Unpaired surfaces (open meshes) never generate unbounded solid space.
                int a=entry.getKey()%sizes[u],b=entry.getKey()/sizes[u];
                for(int pair=0;pair+1<unique;pair+=2){
                    double low=hits.values[pair],high=hits.values[pair+1];
                    if(high-low<=1e-7)continue;
                    int w0=Math.max(0,(int)Math.ceil(low*RESOLUTION-starts[axis]-.5+1e-6));
                    int w1=Math.min(sizes[axis]-1,(int)Math.floor(high*RESOLUTION-starts[axis]-.5-1e-6));
                    for(int w=w0;w<=w1;w++){
                        int x=axis==0?w:(u==0?a:(v==0?b:0));
                        int y=axis==1?w:(u==1?a:(v==1?b:0));
                        int z=axis==2?w:(u==2?a:(v==2?b:0));
                        votes[index(x,y,z,nx,nz)]++;
                    }
                }
            }
        }
    }
    private static boolean intersects(double[] a,double[] b,double[] c,double px,double py,double pz){
        final double h=1.0/16.0+1e-7;
        double[][] v={{a[0]-px,a[1]-py,a[2]-pz},{b[0]-px,b[1]-py,b[2]-pz},
                      {c[0]-px,c[1]-py,c[2]-pz}};
        for(int axis=0;axis<3;axis++){
            double min=Math.min(v[0][axis],Math.min(v[1][axis],v[2][axis]));
            double max=Math.max(v[0][axis],Math.max(v[1][axis],v[2][axis]));
            if(min>h||max< -h)return false;
        }
        for(int i=0;i<3;i++){
            double[] p=v[i],q=v[(i+1)%3];
            double ex=q[0]-p[0],ey=q[1]-p[1],ez=q[2]-p[2];
            // Separating axes edge x X, edge x Y and edge x Z.
            if(separate(v,0,ez,-ey,h)||separate(v,-ez,0,ex,h)||separate(v,ey,-ex,0,h))return false;
        }
        double ux=v[1][0]-v[0][0],uy=v[1][1]-v[0][1],uz=v[1][2]-v[0][2];
        double vx=v[2][0]-v[0][0],vy=v[2][1]-v[0][1],vz=v[2][2]-v[0][2];
        return !separate(v,uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx,h);
    }
    private static boolean separate(double[][] v,double x,double y,double z,double h){
        double r=h*(Math.abs(x)+Math.abs(y)+Math.abs(z));
        double a=v[0][0]*x+v[0][1]*y+v[0][2]*z;
        double b=v[1][0]*x+v[1][1]*y+v[1][2]*z;
        double c=v[2][0]*x+v[2][1]*y+v[2][2]*z;
        return Math.min(a,Math.min(b,c))>r||Math.max(a,Math.max(b,c))< -r;
    }
}
