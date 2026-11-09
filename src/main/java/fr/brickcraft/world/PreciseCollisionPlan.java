package fr.brickcraft.world;

import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.core.BrickCapacityConfig;
import java.util.*;
import java.util.function.Function;

/** Pure math, LDU -> voxel collision cells. Full-block SOLID proxies are intentionally approximate. */
public final class PreciseCollisionPlan {
    private PreciseCollisionPlan(){}
    public record Cell(int x,int y,int z) {}
    public static final int MAX_CELLS=BrickCapacityConfig.HARD_CELLS;
    public static List<Cell> build(List<LDrawProjectReader.Instance> parts,double originX,double originY,double originZ,
                                   int ax,int ay,int az,Function<String,double[]> bounds){
        TreeSet<Cell> cells=new TreeSet<>(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z));
        for(var part:parts){
            double[] b=bounds.apply(part.partId());
            if(b==null||b.length!=6)throw new IllegalArgumentException("Dimensions de piece manquantes : "+part.partId());
            double[] m=part.matrix();
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(int mask=0;mask<8;mask++){
                double x=b[(mask&1)==0?0:3],y=b[(mask&2)==0?1:4],z=b[(mask&4)==0?2:5];
                double[] w={ax+(part.x()+m[0]*x+m[1]*y+m[2]*z)/20.0-originX,
                            ay-(part.y()+m[3]*x+m[4]*y+m[5]*z)/20.0-originY,
                            az+(part.z()+m[6]*x+m[7]*y+m[8]*z)/20.0-originZ};
                for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],w[i]);hi[i]=Math.max(hi[i],w[i]);}
            }
            // Avoid artificially occupying adjacent cells just because a face falls on a grid boundary.
            int x1=(int)Math.floor(lo[0]+.002),y1=(int)Math.floor(lo[1]+.002),z1=(int)Math.floor(lo[2]+.002);
            int x2=(int)Math.floor(hi[0]-.002),y2=(int)Math.floor(hi[1]-.002),z2=(int)Math.floor(hi[2]-.002);
            x2=Math.max(x1,x2);y2=Math.max(y1,y2);z2=Math.max(z1,z2);
            long size=(long)(x2-x1+1)*(y2-y1+1)*(z2-z1+1);
            if(size>4096)throw new IllegalArgumentException("Geometrie trop grande pour la physique simplifiee");
            for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++){
                // The anchor block must never be overwritten.
                if(x==ax && y==ay && z==az)continue;
                cells.add(new Cell(x,y,z));
                if(cells.size()>BrickCapacityConfig.effectiveCells())throw new IllegalArgumentException("Trop de cellules physiques (limite "+BrickCapacityConfig.effectiveCells()+")");
            }
        }
        return List.copyOf(cells);
    }
}
