package fr.brickcraft.world;

import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseFreeAssembly;

/** Server-side incremental transformations; never replace LDraw matrices with Euler angles. */
public final class PreciseWorldEditMath {
    private PreciseWorldEditMath(){}
    public static final byte MOVE=0,ROTATE=1,RESET=2,UNDO=3, SNAP_NOW=4, MOVE_WITH_SNAP=5;
    public static LDrawProjectReader.Instance apply(LDrawProjectReader.Instance source,
                                                      int operation,int axis,double amount){
        if(source==null||axis<0||axis>2||!Double.isFinite(amount))throw new IllegalArgumentException("Parametres incorrects");
        if(operation==MOVE){
            if(amount==0||Math.abs(amount)>80)throw new IllegalArgumentException("Deplacement limite a 80 LDU");
            return new LDrawProjectReader.Instance(source.partId(),source.color(),
                    source.x()+(axis==0?amount:0),source.y()+(axis==1?amount:0),
                    source.z()+(axis==2?amount:0),source.matrix());
        }
        if(operation==ROTATE){
            if(amount==0||Math.abs(amount)>180)throw new IllegalArgumentException("Rotation limitee a 180 degres");
            double[] a=PreciseFreeAssembly.rotation(PreciseFreeAssembly.Axis.values()[axis],amount);
            return new LDrawProjectReader.Instance(source.partId(),source.color(),source.x(),source.y(),source.z(),
                    PreciseFreeAssembly.multiply(a,source.matrix()));
        }
        if(operation==RESET)return new LDrawProjectReader.Instance(source.partId(),source.color(),
                source.x(),source.y(),source.z(),PreciseFreeAssembly.identity());
        throw new IllegalArgumentException("Operation inconnue");
    }
}
