import fr.brickcraft.importer.*;
import fr.brickcraft.ldraw.LDrawLibrary;
import java.nio.file.*;
import java.util.*;

/** Standalone Java 21 regression tests; run with complete.zip + a BCPX castle model. */
public final class TEST_PRECISION_MONDE {
    static int checks=0;
    static void check(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Arguments: dossier de config contenant brickcraft/complete.zip, puis fichier .bcpx");
        LDrawLibrary.init(Path.of(args[0]));
        check(LDrawLibrary.size()>10000,"Bibliotheque LDraw non chargee");
        var file=PreciseProjectIO.read(Path.of(args[1]));
        check(file.parts().size()==3245,"Nombre de pieces du chateau");
        var model=PreciseWorldModel.create(file.parts());
        check(model.mappedPrints()==249,"Correspondance des pieces imprimees");
        check(model.width()>25&&model.width()<27,"Largeur du chateau");
        check(model.height()>38&&model.height()<40,"Hauteur du chateau");
        check(model.depth()>57&&model.depth()<59,"Profondeur du chateau");
        check("24246".equals(PreciseReferenceMapper.geometryId("bl_24246pb057")),"Forme de base BL");
        check("3004".equals(PreciseReferenceMapper.geometryId("bl_3004pb342")),"Forme de brique BL");
        check(PreciseReferenceMapper.geometryId("bl_nonexistentpb99")==null,"Inconnue refusee");
        byte[] bytes=model.writeBytes();check(bytes.length<128*1024,"Taille de stockage");
        var decoded=PreciseWorldModel.readBytes(bytes);
        check(decoded.parts().size()==model.parts().size(),"Decompression");
        check(Arrays.equals(bytes,decoded.writeBytes()),"Reencodage compressé identique");
        for(int i=0;i<model.parts().size();i++){
            var a=model.parts().get(i);var b=decoded.parts().get(i);
            check(a.partId().equals(b.partId()),"ID "+i);
            check(a.color()==b.color(),"Couleur "+i);
            check(Double.doubleToRawLongBits(a.x())==Double.doubleToRawLongBits(b.x()),"X "+i);
            check(Double.doubleToRawLongBits(a.y())==Double.doubleToRawLongBits(b.y()),"Y "+i);
            check(Double.doubleToRawLongBits(a.z())==Double.doubleToRawLongBits(b.z()),"Z "+i);
            for(int j=0;j<9;j++)check(Double.doubleToRawLongBits(a.matrix()[j])==Double.doubleToRawLongBits(b.matrix()[j]),"Matrice "+i+" "+j);
        }
        boolean rejected=false;
        try{
            var p=file.parts().get(0);
            var invalid=new LDrawProjectReader.Instance(p.partId(),p.color(),p.x(),p.y(),p.z(),new double[]{1,0,0,0,2,0,0,0,1});
            PreciseWorldModel.create(List.of(invalid));
        }catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Matrice non orthogonale refusee");
        bytes[0]^=1;rejected=false;
        try{PreciseWorldModel.readBytes(bytes);}catch(java.io.IOException expected){rejected=true;}
        check(rejected,"NBT GZIP invalide refuse");
        System.out.printf("OK : %,d controles, %,d pieces, %,d substitutions, %.2f x %.2f x %.2f blocs%n",
                checks,model.parts().size(),model.mappedPrints(),model.width(),model.height(),model.depth());
    }
}
