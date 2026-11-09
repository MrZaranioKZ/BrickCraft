package fr.brickcraft.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coarse, bounded spatial proximity cache for static LEGO self-shadowing.
 * Approximation only: this is NOT a replacement for per-triangle shadow casting.
 * Data is computed once per projection revision, never in the render loop.
 */
public final class BrickModelOcclusion {
    private static final double CELL=2.0;
    private static final int MAX_CELLS_PER_PART=96;
    private static final int MAX_TOTAL_REFERENCES=250_000;
    private record Box(int part,double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        boolean contains(double x,double y,double z){
            // A small margin avoids darkening faces that only share a common boundary.
            return x>minX+.025 && x<maxX-.025 && y>minY+.025 && y<maxY-.025 && z>minZ+.025 && z<maxZ-.025;
        }
    }
    private final Map<Long,List<Box>> cells=new HashMap<>();
    private int references;
    private static int grid(double v){return (int)Math.floor(v/CELL);}
    private static long key(int x,int y,int z){
        return ((long)(x&0x1fffff)<<42)|((long)(y&0x1fffff)<<21)|(z&0x1fffff);
    }

    public void add(int part,double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        if(!(maxX>minX && maxY>minY && maxZ>minZ))return;
        int a=grid(minX),b=grid(minY),c=grid(minZ);
        int d=grid(maxX),e=grid(maxY),f=grid(maxZ);
        long count=(long)(d-a+1)*(e-b+1)*(f-c+1);
        if(count>MAX_CELLS_PER_PART || references+count>MAX_TOTAL_REFERENCES)return;
        Box box=new Box(part,minX,minY,minZ,maxX,maxY,maxZ);
        for(int x=a;x<=d;x++)for(int y=b;y<=e;y++)for(int z=c;z<=f;z++)
            cells.computeIfAbsent(key(x,y,z),unused->new ArrayList<>(2)).add(box);
        references+=(int)count;
    }

    private boolean covered(double x,double y,double z,int except){
        List<Box> candidates=cells.get(key(grid(x),grid(y),grid(z)));
        if(candidates==null)return false;
        for(Box box:candidates)if(box.part!=except && box.contains(x,y,z))return true;
        return false;
    }

    /** 1 = unobstructed, values below 1 = close to other pieces along surface normal. */
    public float exposure(double x,double y,double z,double nx,double ny,double nz,int except){
        double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(len<1e-9)return 1f;
        nx/=len;ny/=len;nz/=len;
        if(covered(x+nx*.27,y+ny*.27,z+nz*.27,except))return .57f;
        if(covered(x+nx*.70,y+ny*.70,z+nz*.70,except))return .78f;
        return 1f;
    }

    public int indexedReferences(){return references;}
}
