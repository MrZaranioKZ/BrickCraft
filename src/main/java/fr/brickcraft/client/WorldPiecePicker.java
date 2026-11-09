package fr.brickcraft.client;

import fr.brickcraft.importer.LDrawProjectReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Exact ray vs. LDraw part's oriented bounding box, without Minecraft block-grid snapping.
 * Independent of the renderer: picking never walks or uploads triangles.
 * Coordinates are Minecraft world blocks; part coordinates/bounds are LDraw units.
 */
public final class WorldPiecePicker {
    private WorldPiecePicker() {}
    public record Hit(int index,double distance) {}
    public static final double STUD=20.0;

    public static List<Hit> hits(List<LDrawProjectReader.Instance> parts, double minX,double minY,double minZ,
                                 double anchorX,double anchorY,double anchorZ,
                                 double ox,double oy,double oz,double dx,double dy,double dz,
                                 double reach,Function<String,double[]> bounds){
        ArrayList<Hit> out=new ArrayList<>();
        if(reach<=0||!Double.isFinite(reach)||!Double.isFinite(ox)||!Double.isFinite(oy)
                ||!Double.isFinite(oz)||!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz))return out;
        double norm=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(norm<1e-9)return out;
        dx/=norm;dy/=norm;dz/=norm;
        // Minecraft -> original LDraw: negate vertical axis, then inverse-rotate by R^T.
        double rx=(ox-anchorX+minX)*STUD,ry=-(oy-anchorY+minY)*STUD,rz=(oz-anchorZ+minZ)*STUD;
        double vx=dx*STUD,vy=-dy*STUD,vz=dz*STUD;
        for(int i=0;i<parts.size();i++){
            LDrawProjectReader.Instance p=parts.get(i);
            double[] b=bounds.apply(p.partId());if(b==null||b.length<6)continue;
            double[] m=p.matrix();
            double x=rx-p.x(),y=ry-p.y(),z=rz-p.z();
            double lx=m[0]*x+m[3]*y+m[6]*z,ly=m[1]*x+m[4]*y+m[7]*z,lz=m[2]*x+m[5]*y+m[8]*z;
            double ux=m[0]*vx+m[3]*vy+m[6]*vz,uy=m[1]*vx+m[4]*vy+m[7]*vz,uz=m[2]*vx+m[5]*vy+m[8]*vz;
            double near=0,far=reach;
            double[] starts={lx,ly,lz},dirs={ux,uy,uz};
            boolean missed=false;
            for(int k=0;k<3;k++){
                double low=b[k],high=b[k+3],s=starts[k],d=dirs[k];
                if(Math.abs(d)<1e-9){if(s<low||s>high){missed=true;break;}continue;}
                double t1=(low-s)/d,t2=(high-s)/d;
                near=Math.max(near,Math.min(t1,t2));far=Math.min(far,Math.max(t1,t2));
                if(far<near){missed=true;break;}
            }
            if(!missed)out.add(new Hit(i,near));
        }
        out.sort(Comparator.comparingDouble(Hit::distance).thenComparingInt(Hit::index));
        return out;
    }
    /** Shift+click cycles overlapping oriented volumes under the crosshair. */
    public static Hit choose(List<Hit> hits,int old,boolean cycle){
        if(hits.isEmpty())return null;
        if(cycle)for(int i=0;i<hits.size();i++)if(hits.get(i).index()==old)return hits.get((i+1)%hits.size());
        return hits.get(0);
    }
    public static double[][] corners(LDrawProjectReader.Instance p,double[] b,
                                     double minX,double minY,double minZ,double ax,double ay,double az){
        double[][] result=new double[8][3];double[] m=p.matrix();
        for(int i=0;i<8;i++){
            double x=b[(i&1)==0?0:3],y=b[(i&2)==0?1:4],z=b[(i&4)==0?2:5];
            result[i][0]=ax+(p.x()+m[0]*x+m[1]*y+m[2]*z)/STUD-minX;
            result[i][1]=ay-(p.y()+m[3]*x+m[4]*y+m[5]*z)/STUD-minY;
            result[i][2]=az+(p.z()+m[6]*x+m[7]*y+m[8]*z)/STUD-minZ;
        }
        return result;
    }
}
