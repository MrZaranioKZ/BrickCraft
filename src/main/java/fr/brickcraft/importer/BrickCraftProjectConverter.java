package fr.brickcraft.importer;

import java.util.ArrayList;
import java.util.List;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.client.StudioAssembly;

/** Lossless placement converter. Never silently rounds 1/2-stud offsets or arbitrary rotations. */
public final class BrickCraftProjectConverter {
    private BrickCraftProjectConverter(){}
    public record Conversion(int grid,List<BrickPiece> pieces,List<String> errors){
        public Conversion {pieces=List.copyOf(pieces);errors=List.copyOf(errors);}
        public boolean success(){return errors.isEmpty();}
    }
    private record Candidate(String id,int code,int rgb,double cx,double cy,double cz,int angle,int pitch,int roll,
                             int width,int height,int length){}
    private static boolean close(double a,double b){return Math.abs(a-b)<0.0005;}
    /** Match the block model's R_y(-yaw) R_x(pitch) R_z(roll) basis. */
    private static int[] orientation(double[] matrix) {
        // Convert LDraw right-handed Y-down rotation to the Minecraft Y-up convention.
        double[] source={matrix[0],-matrix[1],matrix[2],-matrix[3],matrix[4],-matrix[5],matrix[6],-matrix[7],matrix[8]};
        for(int yaw=0;yaw<360;yaw+=90)for(int pitch=0;pitch<360;pitch+=90)for(int roll=0;roll<360;roll+=90){
            BrickPiece probe=new BrickPiece(BrickKind.BRICK_1X1,0,0,0,0,yaw/90,yaw,pitch,roll);
            double[][] a=probe.axes();
            boolean match=true;
            for(int r=0;r<3;r++)for(int c=0;c<3;c++){
                if(!close(a[c][r],source[3*r+c]))match=false;
            }
            if(match)return new int[]{yaw,pitch,roll};
        }
        return null;
    }
    private static int bestGrid(double span){
        for(int size:new int[]{16,32,64,128,256})if(span<=size+0.0001)return size;
        return -1;
    }
    public static Conversion convert(LDrawProjectReader.Result project){
        List<String> issues=new ArrayList<>();
        if(project.parts().size()>StudioAssembly.MAX_PIECES)
            return new Conversion(0,List.of(),List.of("Plus de "+StudioAssembly.MAX_PIECES+" pieces en Studio grille (utilise la projection precise)"));
        List<Candidate> candidateList=new ArrayList<>();
        for(var part:project.parts()){
            String id=LDrawLibrary.normalize(part.partId());
            if(!LDrawLibrary.contains(id)){
                issues.add("Reference manquante : "+id);continue;
            }
            double[] bounds=LDrawLibrary.originalBounds(id);
            if(bounds==null){issues.add("Maillage non chargeable : "+id);continue;}
            int[] o=orientation(part.matrix());
            if(o==null){issues.add("Rotation non prise en charge : "+id);continue;}
            LDrawLibrary.Colour color=LDrawLibrary.color(part.color());
            if(color==null){issues.add("Couleur LDraw inconnue : "+part.color());continue;}
            double[] localCenter={((bounds[0]+bounds[3])/2)/20.,-((bounds[1]+bounds[4])/2)/20.,((bounds[2]+bounds[5])/2)/20.};
            double[][] axis=new BrickPiece(BrickKind.BRICK_1X1,0,0,0,0,o[0]/90,o[0],o[1],o[2]).axes();
            double worldX=part.x()/20.,worldY=-part.y()/20.,worldZ=part.z()/20.;
            for(int j=0;j<3;j++){
                worldX+=localCenter[j]*axis[j][0];
                worldY+=localCenter[j]*axis[j][1];
                worldZ+=localCenter[j]*axis[j][2];
            }
            BrickPiece dims=new BrickPiece(BrickKind.LDRAW_PART,color.rgb(),0,0,0,o[0]/90,o[0],o[1],o[2],id,color.code());
            candidateList.add(new Candidate(id,color.code(),color.rgb(),worldX,worldY,worldZ,o[0],o[1],o[2],dims.width(),dims.height(),dims.length()));
            if(issues.size()>40)break;
        }
        if(!issues.isEmpty())return new Conversion(0,List.of(),issues);
        if(candidateList.isEmpty())return new Conversion(0,List.of(),List.of("Projet vide"));
        // Shift the min envelope to local 0; exact integer cells are mandatory.
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
        for(Candidate c:candidateList){
            minX=Math.min(minX,c.cx-c.width/2.0);
            minY=Math.min(minY,c.cy-c.height/10.0);
            minZ=Math.min(minZ,c.cz-c.length/2.0);
        }
        List<BrickPiece> pieces=new ArrayList<>(candidateList.size());
        double maxSpan=0;
        for(Candidate c:candidateList){
            double x=c.cx-c.width/2.0-minX;
            double y=(c.cy-c.height/10.0-minY)*5.0;
            double z=c.cz-c.length/2.0-minZ;
            if(!close(x,Math.rint(x))||!close(y,Math.rint(y))||!close(z,Math.rint(z))){
                if(issues.size()<12)issues.add("Coordonnees fractionnaires ("+c.id+") : X="+round(x)+" Y="+round(y/5.)+" Z="+round(z));
                continue;
            }
            int ix=(int)Math.rint(x),iy=(int)Math.rint(y),iz=(int)Math.rint(z);
            if(iy<0 || iy+c.height>StudioAssembly.MAX_TOP){issues.add("Hauteur hors limites ("+c.id+")");continue;}
            maxSpan=Math.max(maxSpan,Math.max(ix+c.width,iz+c.length));
            pieces.add(new BrickPiece(BrickKind.LDRAW_PART,c.rgb,ix,iy,iz,c.angle/90,c.angle,c.pitch,c.roll,c.id,c.code));
        }
        if(!issues.isEmpty())return new Conversion(0,List.of(),issues);
        int grid=bestGrid(maxSpan);
        if(grid==-1)return new Conversion(0,List.of(),List.of("Modele depassant 256 tenons de cote"));
        StudioAssembly staging=new StudioAssembly();staging.setGridSize(grid);
        if(!staging.loadProject(grid,pieces))return new Conversion(0,List.of(),List.of(
                "Certaines enveloppes des pieces se chevauchent, ou des limites du Studio sont atteintes. " +
                "Import annule sans modification : les geometries complexes necessitent des collisions precises."));
        return new Conversion(grid,pieces,List.of());
    }
    private static String round(double x){return String.format(java.util.Locale.ROOT,"%.3f",x);}
}
