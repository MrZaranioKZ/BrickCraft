import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseProjectIO;
import fr.brickcraft.importer.StudioIoArchive;
import fr.brickcraft.guide.BrickGuidePlan;
import fr.brickcraft.guide.BrickSourceStepReader;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

/** Pure-Java tests: works without the Minecraft/Fabric dependencies. */
public class TEST_STUDIO_IO_V016 {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void synthetic() throws Exception {
        StringBuilder source=new StringBuilder("0 FILE test_model.ldr\n");
        for(int i=0;i<12000;i++)source.append("1 16 ").append(i*20)
            .append(" 0 0 1 0 0 0 1 0 0 0 1 3004.dat\n");
        Path p=Files.createTempFile("bc_large_", ".io");
        Path saved=Files.createTempFile("bc_large_", ".bcpx");
        try {
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(p))){
                out.putNextEntry(new ZipEntry("model.ldr"));
                out.write(source.toString().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            var parsed=LDrawProjectReader.inspect(p);
            check(parsed.parts().size()==12000,"Les 12000 references doivent etre lues");
            check(parsed.problems().isEmpty(),"Problemes MPD inattendus");
            PreciseProjectIO.save(saved,parsed);
            var roundtrip=PreciseProjectIO.read(saved);
            check(roundtrip.parts().size()==12000,"Sauvegarde BCPX tronquee");
            check(roundtrip.parts().get(11999).x()==11999*20.0,"Precision des coordonnees perdue");
            BrickGuidePlan guide=BrickSourceStepReader.inspect(p,parsed.parts());
            check(guide.partCount()==12000,"Notice tronquee");
            System.out.println("OK : 12000 pieces, ZIP non chiffre, BCPX, notices");
        }finally{Files.deleteIfExists(p);Files.deleteIfExists(saved);}
    }
    private static void projectionCapacity() throws Exception {
        // Build a tiny user-provided LDraw geometry library to test the full projection
        // serializer without shipping third-party LEGO geometry or requiring Minecraft.
        Path root=Files.createTempDirectory("bc_v016_library_");
        Path dir=root.resolve("brickcraft");Files.createDirectories(dir);
        Path zip=dir.resolve("complete.zip");
        try {
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){
                out.putNextEntry(new ZipEntry("parts/3004.dat"));
                String model="0 Synthetic part\n"+
                    "3 16 0 0 0 20 0 0 0 20 0\n"+
                    "3 16 0 0 0 20 0 0 0 0 20\n"+
                    "3 16 0 0 0 0 20 0 0 0 20\n"+
                    "3 16 20 0 0 0 20 0 0 0 20\n";
                out.write(model.getBytes(StandardCharsets.UTF_8));out.closeEntry();
            }
            fr.brickcraft.ldraw.LDrawLibrary.init(root);
            check(fr.brickcraft.ldraw.LDrawLibrary.contains("3004"),"Piece test introuvable");
            var parts=new ArrayList<LDrawProjectReader.Instance>(16384);
            for(int i=0;i<16384;i++)parts.add(new LDrawProjectReader.Instance("3004",16,
                (i%16)*20.0,0,((i/16)%16)*20.0,new double[]{1,0,0,0,1,0,0,0,1}));
            var big=fr.brickcraft.importer.PreciseWorldModel.create(parts);
            byte[] packed=big.writeBytes();
            var restored=fr.brickcraft.importer.PreciseWorldModel.readBytes(packed);
            check(restored.parts().size()==16384,"Projection 16384 tronquee");
            check(restored.parts().get(16383).x()==parts.get(16383).x(),"Positions non conservees");
            // V0.17 raised the technical limit to 250,000; 16,385 is now valid.
            parts.add(parts.get(0));
            check(fr.brickcraft.importer.PreciseWorldModel.create(parts).parts().size()==16385,
                    "La capacite V0.17 doit accepter plus de 16384 pieces");
            // Test the hard cap without allocating or processing another 250k meshes.
            java.util.List<LDrawProjectReader.Instance> oversized=new java.util.AbstractList<>(){
                @Override public int size(){return fr.brickcraft.importer.PreciseWorldModel.MAX_PARTS+1;}
                @Override public LDrawProjectReader.Instance get(int i){return parts.get(0);}
            };
            try{fr.brickcraft.importer.PreciseWorldModel.create(oversized);
                throw new AssertionError("La limite technique 250000 n'est pas appliquee");
            }catch(IllegalArgumentException expected){}
            System.out.println("OK : projection 16385 pieces, GZIP NBT, plafond 250000");
        }finally{
            fr.brickcraft.ldraw.LDrawLibrary.close();
            Files.deleteIfExists(zip);Files.deleteIfExists(dir);Files.deleteIfExists(root);
        }
    }
    public static void main(String[] args) throws Exception {
        synthetic();
        projectionCapacity();
        int scanned=0;
        for(String arg:args){
            Path path=Path.of(arg);
            if(Files.isDirectory(path)){
                try(var files=Files.list(path)){
                    for(Path f:files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".io")).sorted().toList())
                        scanned+=scan(f);
                }
            }else if(Files.isRegularFile(path))scanned+=scan(path);
        }
        System.out.println("OK : "+scanned+" projets supplementaires analyses.");
    }
    private static int scan(Path file)throws Exception {
        var project=LDrawProjectReader.inspect(file);
        check(!project.parts().isEmpty(),"Projet vide : "+file);
        check(project.problems().isEmpty(),"Problemes LDraw : "+file+" : "+project.problems());
        BrickGuidePlan guide=BrickSourceStepReader.inspect(file,project.parts());
        check(guide.partCount()==project.parts().size(),"Notice incomplete : "+file);
        System.out.println(file.getFileName()+" : "+project.parts().size()+" pieces, "+
                project.submodels()+" sous-modeles, "+guide.size()+" etapes");
        return 1;
    }
}
