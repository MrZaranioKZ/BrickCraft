package fr.brickcraft.importer;

import fr.brickcraft.ldraw.LDrawLibrary;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Non-grid LDraw editor. All coordinates remain exact double-precision LDU;
 * existing arbitrary 3x3 matrices are NEVER reconstituted from Euler angles.
 * Rotation buttons compose an incremental rotation with the source matrix.
 * No changes to the stable GPU renderer or to existing .bcpx files.
 */
public final class PreciseFreeAssembly {
    public enum Axis {X,Y,Z}
    private static final int HISTORY=64, MAX=PreciseWorldModel.MAX_PARTS;
    private final String root;
    private final int submodels;
    private final List<String> problems;
    private final ArrayList<LDrawProjectReader.Instance> parts=new ArrayList<>();
    private final Deque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private int selected=-1;
    private LDrawProjectReader.Instance gestureStart=null;
    private boolean gestureChanged=false;
    private record State(List<LDrawProjectReader.Instance> parts,int selected) {}

    public PreciseFreeAssembly(LDrawProjectReader.Result original){
        root=original.root();submodels=original.submodels();problems=original.problems();
        if(original.parts().size()>MAX)throw new IllegalArgumentException("Editeur limite a "+MAX+" pieces");
        parts.addAll(original.parts());
        if(!parts.isEmpty())selected=0;
    }
    public int size(){return parts.size();}
    public int selectedIndex(){return selected;}
    public boolean canUndo(){return !undo.isEmpty();}
    public boolean canRedo(){return !redo.isEmpty();}
    public List<LDrawProjectReader.Instance> parts(){return List.copyOf(parts);}
    public LDrawProjectReader.Instance selected(){return selected<0||selected>=parts.size()?null:parts.get(selected);}
    public LDrawProjectReader.Result result(){return new LDrawProjectReader.Result(root,submodels,parts,problems);}
    public boolean select(int index){if(index<0||index>=parts.size())return false;finishGesture();selected=index;return true;}
    private void checkpoint(){
        finishGesture();
        undo.push(new State(List.copyOf(parts),selected));
        while(undo.size()>(parts.size()>16384?8:HISTORY))undo.removeLast();
        redo.clear();
    }
    private void restore(State s){parts.clear();parts.addAll(s.parts());selected=s.selected();}
    public boolean undo(){finishGesture();if(undo.isEmpty())return false;redo.push(new State(List.copyOf(parts),selected));restore(undo.pop());return true;}
    public boolean redo(){finishGesture();if(redo.isEmpty())return false;undo.push(new State(List.copyOf(parts),selected));restore(redo.pop());return true;}
    public static boolean valid(LDrawProjectReader.Instance part){
        if(part==null||part.partId()==null||!part.partId().matches("[A-Za-z0-9_./-]{1,96}") ||
                part.color()<0||part.color()>0x1FFFFFF || !PreciseWorldModel.validMatrix(part.matrix()))return false;
        return Double.isFinite(part.x())&&Double.isFinite(part.y())&&Double.isFinite(part.z())&&
                Math.max(Math.max(Math.abs(part.x()),Math.abs(part.y())),Math.abs(part.z()))<=100000;
    }
    public static void validateShape(LDrawProjectReader.Instance part){
        if(!valid(part))throw new IllegalArgumentException("Coordonnees, couleur ou matrice incorrectes");
        String id=PreciseReferenceMapper.geometryId(part.partId());
        if(id==null||LDrawLibrary.originalBounds(id)==null)
            throw new IllegalArgumentException("Reference LDraw introuvable : "+part.partId());
    }
    public boolean add(LDrawProjectReader.Instance part){
        validateShape(part);
        if(parts.size()>=MAX)throw new IllegalArgumentException("Maximum "+MAX+" pieces");
        checkpoint();parts.add(part);selected=parts.size()-1;return true;
    }
    public boolean replace(LDrawProjectReader.Instance part){
        validateShape(part);
        if(selected()==null)return false;
        checkpoint();parts.set(selected,part);return true;
    }
    public boolean duplicate(){
        if(selected()==null)return false;
        var p=selected();
        return add(new LDrawProjectReader.Instance(p.partId(),p.color(),p.x()+20,p.y(),p.z(),p.matrix()));
    }
    public boolean delete(){
        if(selected()==null)return false;
        checkpoint();parts.remove(selected);selected=parts.isEmpty()?-1:Math.min(selected,parts.size()-1);return true;
    }
    public boolean move(Axis axis,double ldu){
        var p=selected();if(p==null||!Double.isFinite(ldu))return false;
        double x=p.x()+(axis==Axis.X?ldu:0),y=p.y()+(axis==Axis.Y?ldu:0),z=p.z()+(axis==Axis.Z?ldu:0);
        return replace(new LDrawProjectReader.Instance(p.partId(),p.color(),x,y,z,p.matrix()));
    }
    public boolean rotate(Axis axis,double angle){
        var p=selected();if(p==null||!Double.isFinite(angle))return false;
        return replace(new LDrawProjectReader.Instance(p.partId(),p.color(),p.x(),p.y(),p.z(),
                multiply(rotation(axis,angle),p.matrix())));
    }
    public boolean resetRotation(){
        var p=selected();if(p==null)return false;
        return replace(new LDrawProjectReader.Instance(p.partId(),p.color(),p.x(),p.y(),p.z(),identity()));
    }
    /** One undo checkpoint per complete mouse drag, regardless of mouse event count. */
    public boolean beginGesture(){
        finishGesture();
        gestureStart=selected();gestureChanged=false;
        return gestureStart!=null;
    }
    public boolean updateGesture(Axis axis,double amount,boolean rotation){
        if(gestureStart==null||selected<0||!Double.isFinite(amount))return false;
        if(amount==0 && !gestureChanged)return false;
        var start=gestureStart;
        double nx=start.x(),ny=start.y(),nz=start.z();
        double[] matrix=start.matrix();
        if(rotation)matrix=multiply(rotation(axis,amount),matrix);
        else switch(axis){case X->nx+=amount;case Y->ny+=amount;case Z->nz+=amount;}
        var changed=new LDrawProjectReader.Instance(start.partId(),start.color(),nx,ny,nz,matrix);
        if(!valid(changed))return false;
        if(!gestureChanged){checkpointGesture();gestureChanged=true;}
        parts.set(selected,changed);return true;
    }
    private void checkpointGesture(){
        undo.push(new State(List.copyOf(parts),selected));
        while(undo.size()>(parts.size()>16384?8:HISTORY))undo.removeLast();
        redo.clear();
    }
    public void finishGesture(){gestureStart=null;gestureChanged=false;}
    public boolean gestureActive(){return gestureStart!=null;}
    public static double[] identity(){return new double[]{1,0,0,0,1,0,0,0,1};}
    public static double[] rotation(Axis axis,double angle){
        if(!Double.isFinite(angle)||Math.abs(angle)>36000)throw new IllegalArgumentException("Angle invalide");
        double t=Math.toRadians(angle),c=Math.cos(t),s=Math.sin(t);
        return switch(axis){
            case X -> new double[]{1,0,0,0,c,-s,0,s,c};
            case Y -> new double[]{c,0,s,0,1,0,-s,0,c};
            case Z -> new double[]{c,-s,0,s,c,0,0,0,1};
        };
    }
    /** Left-multiply for rotations relative to the construction's LDraw axes. */
    public static double[] multiply(double[] a,double[] b){
        double[] out=new double[9];
        for(int r=0;r<3;r++)for(int c=0;c<3;c++)
            for(int k=0;k<3;k++)out[r*3+c]+=a[r*3+k]*b[k*3+c];
        return out;
    }
}
