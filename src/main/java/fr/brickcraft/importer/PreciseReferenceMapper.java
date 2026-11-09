package fr.brickcraft.importer;

import fr.brickcraft.ldraw.LDrawLibrary;
import java.util.Locale;

/** Shape-only aliases for decorated BrickLink Studio names. The original ID stays in BCPX/NBT. */
public final class PreciseReferenceMapper {
    private PreciseReferenceMapper() {}
    public static String geometryId(String sourceId) {
        if(sourceId==null)return null;
        String id=LDrawLibrary.normalize(sourceId).toLowerCase(Locale.ROOT);
        if(id.endsWith(".dat"))id=id.substring(0,id.length()-4);
        if(LDrawLibrary.contains(id))return id;
        if(id.startsWith("bl_")){
            String base=id.substring(3);
            if(LDrawLibrary.contains(base))return base;
            int printed=base.indexOf("pb");
            if(printed>0){
                String design=base.substring(0,printed);
                if(LDrawLibrary.contains(design))return design;
            }
        }
        return null;
    }
    public static boolean losesPrint(String sourceId,String geometryId){
        // A Studio-specific polygon mesh can preserve the shape, but not the
        // PE_TEX_INFO printed artwork until the GPU renderer supports UVs.
        String original=LDrawLibrary.normalize(sourceId);
        return original.matches("bl_[a-z0-9_]*pb[0-9a-z_]*") ||
                geometryId!=null && !geometryId.equals(original);
    }
}
