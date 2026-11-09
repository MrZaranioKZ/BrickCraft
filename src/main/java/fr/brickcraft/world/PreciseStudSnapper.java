package fr.brickcraft.world;

import fr.brickcraft.importer.LDrawProjectReader;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conservative, server-side LDraw stud/socket snapping for KNOWN rectangular parts.
 * All positions are in LDraw units (LDU), Y points DOWN. A stud is 20 LDU wide;
 * a standard brick is 24 LDU high, a plate is 8 LDU high.
 *
 * Unsupported parts, non-matching orientations and solutions farther than maxLdu
 * remain FREE. This intentionally does not invent connections for Technic/hinges.
 * It does not require LDraw meshes, and never changes source orientation matrices.
 */
public final class PreciseStudSnapper {
    private PreciseStudSnapper() {}
    public record Profile(int xStuds, int zStuds, int heightLdu, boolean topStuds) {
        public Profile {
            if (xStuds<1||zStuds<1||heightLdu<1) throw new IllegalArgumentException("Profil invalide");
        }
    }
    public record Result(LDrawProjectReader.Instance piece, boolean snapped, int supportIndex,
                         double correctionLdu) {}
    /** Transformed stud/socket centers in SOURCE LDraw coordinates, for a cheap gizmo overlay.
     * Not intended as a general Technic or hinge connector catalogue. */
    public record ConnectorPoint(boolean top,double x,double y,double z) {}
    public static java.util.List<ConnectorPoint> connectorPoints(LDrawProjectReader.Instance piece,int limit) {
        Profile prof=profile(piece==null?null:piece.partId());
        if(prof==null||limit<=0||piece.matrix()==null||piece.matrix().length!=9) return java.util.List.of();
        java.util.ArrayList<ConnectorPoint> out=new java.util.ArrayList<>();
        for(int z=0;z<prof.zStuds()&&out.size()<limit;z++)for(int x=0;x<prof.xStuds()&&out.size()<limit;x++){
            if(prof.topStuds()){
                Connector c=connector(piece,prof,x,z,Gender.STUD);
                out.add(new ConnectorPoint(true,c.x(),c.y(),c.z()));
            }
            if(out.size()<limit){
                Connector c=connector(piece,prof,x,z,Gender.SOCKET);
                out.add(new ConnectorPoint(false,c.x(),c.y(),c.z()));
            }
        }
        return java.util.List.copyOf(out);
    }
    private enum Gender { STUD, SOCKET }
    private record Connector(Gender gender,double x,double y,double z,double nx,double ny,double nz) {}
    private record Body(double[] center,double[][] axes,double[] half) {}
    private static final Map<String,Profile> PROFILES=Map.ofEntries(
        // Bricks (24 LDU): pivot at center of the top footprint; underside at y=0.
        Map.entry("3005",new Profile(1,1,24,true)),
        Map.entry("3004",new Profile(1,2,24,true)),
        Map.entry("3622",new Profile(1,3,24,true)),
        Map.entry("3010",new Profile(1,4,24,true)),
        Map.entry("3009",new Profile(1,6,24,true)),
        Map.entry("3003",new Profile(2,2,24,true)),
        Map.entry("3002",new Profile(2,3,24,true)),
        Map.entry("3001",new Profile(2,4,24,true)),
        Map.entry("2456",new Profile(2,6,24,true)),
        Map.entry("3007",new Profile(2,8,24,true)),
        // Plates (8 LDU).
        Map.entry("3024",new Profile(1,1,8,true)),
        Map.entry("3023",new Profile(1,2,8,true)),
        Map.entry("3023b",new Profile(1,2,8,true)),
        Map.entry("3623",new Profile(1,3,8,true)),
        Map.entry("3710",new Profile(1,4,8,true)),
        Map.entry("3022",new Profile(2,2,8,true)),
        Map.entry("3021",new Profile(2,3,8,true)),
        Map.entry("3020",new Profile(2,4,8,true)),
        Map.entry("3034",new Profile(2,8,8,true)),
        // Smooth tiles: underside only, no studs on top.
        Map.entry("3070b",new Profile(1,1,8,false)),
        Map.entry("3069b",new Profile(1,2,8,false)),
        Map.entry("3068b",new Profile(2,2,8,false))
    );
    public static Profile profile(String id) {
        if(id==null)return null;
        String name=id.toLowerCase(Locale.ROOT).replace('\\','/');
        if(name.startsWith("parts/"))name=name.substring(6);
        if(name.endsWith(".dat"))name=name.substring(0,name.length()-4);
        // Do not interpret unrelated library paths (subparts, primitives) as bricks.
        if(name.contains("/"))return null;
        return PROFILES.get(name);
    }
    private static double dot(double[] a,double[] b) {
        return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
    }
    private static double[] vector(double[] m,double x,double y,double z) {
        return new double[]{m[0]*x+m[1]*y+m[2]*z,
                            m[3]*x+m[4]*y+m[5]*z,
                            m[6]*x+m[7]*y+m[8]*z};
    }
    private static Connector connector(LDrawProjectReader.Instance p,Profile profile,
                                       int sx,int sz,Gender gender){
        double dx=(2*sx-profile.xStuds()+1)*10.0;
        double dz=(2*sz-profile.zStuds()+1)*10.0;
        double dy=gender==Gender.STUD?-profile.heightLdu():0;
        double[] r=vector(p.matrix(),dx,dy,dz);
        double[] normal=vector(p.matrix(),0,gender==Gender.STUD?-1:1,0);
        return new Connector(gender,p.x()+r[0],p.y()+r[1],p.z()+r[2],
                normal[0],normal[1],normal[2]);
    }
    private static Body body(LDrawProjectReader.Instance p,Profile profile){
        double[] m=p.matrix();
        double[] mid=vector(m,0,-profile.heightLdu()/2.0,0);
        double[][] axes={vector(m,1,0,0),vector(m,0,1,0),vector(m,0,0,1)};
        return new Body(new double[]{p.x()+mid[0],p.y()+mid[1],p.z()+mid[2]},axes,
                new double[]{profile.xStuds()*10.0,profile.heightLdu()/2.0,profile.zStuds()*10.0});
    }
    /** SAT separating axis test on 3+3 axes and their 9 cross products. */
    private static boolean bodiesOverlap(Body a,Body b){
        double[] t={b.center()[0]-a.center()[0],b.center()[1]-a.center()[1],b.center()[2]-a.center()[2]};
        double[][] candidates=new double[15][];
        System.arraycopy(a.axes(),0,candidates,0,3);
        System.arraycopy(b.axes(),0,candidates,3,3);
        int idx=6;
        for(double[] va:a.axes())for(double[] vb:b.axes())
            candidates[idx++]=new double[]{va[1]*vb[2]-va[2]*vb[1],va[2]*vb[0]-va[0]*vb[2],va[0]*vb[1]-va[1]*vb[0]};
        for(double[] axis:candidates){
            double n2=dot(axis,axis);if(n2<1e-10)continue;
            double sum=0;
            for(int j=0;j<3;j++){
                sum+=Math.abs(dot(axis,a.axes()[j]))*a.half()[j];
                sum+=Math.abs(dot(axis,b.axes()[j]))*b.half()[j];
            }
            // Bodies that only TOUCH (or the stud/under-body overlap) are valid.
            if(Math.abs(dot(axis,t))>=sum-0.45*Math.sqrt(n2))return false;
        }
        return true;
    }
    private static boolean collides(List<LDrawProjectReader.Instance> project,int movingIndex,
                                    LDrawProjectReader.Instance moved,Profile movingProfile){
        Body movedBody=body(moved,movingProfile);
        for(int k=0;k<project.size();k++){
            if(k==movingIndex)continue;
            var p=project.get(k);Profile other=profile(p.partId());
            if(other==null)continue; // No fake collision bounds for irregular shapes.
            if(Math.abs(moved.x()-p.x())>240||Math.abs(moved.y()-p.y())>160||
                    Math.abs(moved.z()-p.z())>240)continue;
            if(bodiesOverlap(movedBody,body(p,other)))return true;
        }
        return false;
    }
    private static boolean validMatrix(double[] m){
        if(m==null||m.length!=9)return false;
        for(double v:m)if(!Double.isFinite(v)||Math.abs(v)>1.0005)return false;
        for(int i=0;i<3;i++)for(int j=i;j<3;j++){
            double value=0;for(int k=0;k<3;k++)value+=m[3*i+k]*m[3*j+k];
            if(Math.abs(value-(i==j?1:0))>0.015)return false;
        }
        return true;
    }
    public static Result snap(List<LDrawProjectReader.Instance> project,int movingIndex,double maxLdu){
        if(project==null||movingIndex<0||movingIndex>=project.size()||
                !Double.isFinite(maxLdu)||maxLdu<=0||maxLdu>20)throw new IllegalArgumentException("Snap invalide");
        var selected=project.get(movingIndex);Profile moving=profile(selected.partId());
        if(moving==null||!validMatrix(selected.matrix()))return new Result(selected,false,-1,0);
        double best=maxLdu*maxLdu+1e-9;
        LDrawProjectReader.Instance bestPiece=selected;
        int bestSupport=-1;
        int checkedCollisionCandidates=0;
        // Only seek mates on known rectangular parts nearby. No distant teleportation.
        for(int i=0;i<project.size();i++){
            if(i==movingIndex)continue;
            var support=project.get(i);Profile fixed=profile(support.partId());
            if(fixed==null||!validMatrix(support.matrix()))continue;
            if(Math.abs(selected.x()-support.x())>260||Math.abs(selected.y()-support.y())>100||
                    Math.abs(selected.z()-support.z())>260)continue;
            for(int s=0;s<fixed.xStuds()*fixed.zStuds();s++){
                int sx=s%fixed.xStuds(),sz=s/fixed.xStuds();
                // Either a bottom socket on the moving brick approaches a fixed stud,
                // or an inverted moving stud approaches a fixed bottom socket.
                for(int polarity=0;polarity<2;polarity++){
                    Gender fixedGender=polarity==0?Gender.STUD:Gender.SOCKET;
                    if(fixedGender==Gender.STUD&&!fixed.topStuds())continue;
                    Connector a=connector(support,fixed,sx,sz,fixedGender);
                    Gender movingGender=fixedGender==Gender.STUD?Gender.SOCKET:Gender.STUD;
                    if(movingGender==Gender.STUD&&!moving.topStuds())continue;
                    for(int t=0;t<moving.xStuds()*moving.zStuds();t++){
                        Connector b=connector(selected,moving,t%moving.xStuds(),t/moving.xStuds(),movingGender);
                        if(a.nx()*b.nx()+a.ny()*b.ny()+a.nz()*b.nz()>-0.985)continue;
                        double dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z();
                        double score=dx*dx+dy*dy+dz*dz;
                        if(score>best+1e-8 || score<1e-8)continue;
                        // For equal candidates prefer first stable index; no oscillations.
                        if(score>=best-1e-8)continue;
                        // Limit costly full-assembly OBB collision checks per request.
                        if(++checkedCollisionCandidates>96)
                            return new Result(bestPiece,bestSupport>=0,bestSupport,
                                bestSupport<0?0:Math.sqrt(Math.max(0,best)));
                        var candidate=new LDrawProjectReader.Instance(selected.partId(),selected.color(),
                                selected.x()+dx,selected.y()+dy,selected.z()+dz,selected.matrix());
                        if(collides(project,movingIndex,candidate,moving))continue;
                        best=score;bestPiece=candidate;bestSupport=i;
                    }
                }
            }
        }
        return new Result(bestPiece,bestSupport>=0,bestSupport,
                bestSupport<0?0:Math.sqrt(Math.max(0,best)));
    }
}
