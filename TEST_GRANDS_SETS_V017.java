import fr.brickcraft.core.BrickCapacityConfig;
import fr.brickcraft.importer.*;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.world.PreciseMeshCollisionPlan;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class TEST_GRANDS_SETS_V017 {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final double[] ID={1,0,0,0,1,0,0,0,1};
    static void test()throws Exception{
        Path root=Files.createTempDirectory("brickcraft_017_");
        Path base=root.resolve("brickcraft"),custom=base.resolve("parts_custom");
        Files.createDirectories(custom);
        try {
            BrickCapacityConfig.init(root);
            BrickCapacityConfig.save(0,0);
            BrickCapacityConfig.init(root);
            check(BrickCapacityConfig.effectiveParts()==250_000,"No-limit pieces");
            check(BrickCapacityConfig.effectiveCells()==400_000,"No-limit collisions");
            check(BrickCapacityConfig.effectiveTriangles()==20_000_000,"No-limit triangles");
            String shape="0 Test - brique personnalisee\n"+
                    "3 16 0 0 0 20 0 0 0 20 0\n"+
                    "3 16 0 0 0 20 0 0 0 0 20\n"+
                    "3 16 0 0 0 0 20 0 0 0 20\n"+
                    "3 16 20 0 0 0 20 0 0 0 20\n";
            Path zip=base.resolve("complete.zip");
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){
                out.putNextEntry(new ZipEntry("parts/3004.dat"));out.write(shape.getBytes(StandardCharsets.UTF_8));out.closeEntry();
            }
            // User-defined geometry absent from official library.
            Files.writeString(custom.resolve("missing42.dat"),shape,StandardCharsets.UTF_8);
            LDrawLibrary.init(root);
            check(LDrawLibrary.contains("missing42"),"Custom .dat not indexed");
            check(LDrawLibrary.info("missing42").name().contains("personnalisee"),"Custom name missing");
            check(LDrawLibrary.mesh("missing42")!=null,"Custom .dat not expanded into mesh");
            List<LDrawProjectReader.Instance> parts=new ArrayList<>();
            for(int i=0;i<32768;i++)parts.add(new LDrawProjectReader.Instance("missing42",16,
                    (i%32)*20.,0,((i/32)%32)*20.,ID));
            var model=PreciseWorldModel.create(parts);
            byte[] packed=model.writeBytes();
            var restored=PreciseWorldModel.readBytes(packed);
            check(restored.parts().size()==32768,"32768-parts model truncated");
            System.out.println("OK 32768-part model and compressed save: "+packed.length+" bytes");
            var reportInput=new LDrawProjectReader.Result("sample.ldr",0,
                    List.of(new LDrawProjectReader.Instance("missing42",16,0,0,0,ID),
                            new LDrawProjectReader.Instance("no98765",16,20,0,0,ID),
                            new LDrawProjectReader.Instance("no98765",16,40,0,0,ID)),List.of());
            var summary=StudioProjectAnalysis.analyze(reportInput);
            check(summary.missingInstances()==2,"Missing reference count wrong");
            Path report=MissingPartsReport.write(summary,base.resolve("imports/reports"),"sample.io");
            String html=Files.readString(report);check(html.contains("no98765"),"HTML missing id");
            check(html.contains("Chercher en ligne"),"HTML missing search link");
            check(Files.exists(report.resolveSibling("sample_pieces_manquantes.csv")),"CSV missing");
            System.out.println("OK custom part and named HTML/CSV missing-parts reports");
            Path embedded=base.resolve("embedded.io");
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(embedded))){
                out.putNextEntry(new ZipEntry("CustomParts/missing75.dat"));
                out.write(("0 FILE missing75.dat\n0 Brick - Special Test Shape\n"+shape).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("CustomParts/s/s/test77.dat"));
                out.write(shape.getBytes(StandardCharsets.UTF_8));out.closeEntry();
            }
            var embeddedNames=StudioCustomPartsInstaller.names(embedded);
            check(embeddedNames.get("missing75").contains("Special Test Shape"),"Embedded .io name missing");
            var installed=StudioCustomPartsInstaller.install(embedded,custom);
            check(installed.added()==2,"Embedded .dat imports missing");
            check(Files.isRegularFile(custom.resolve("parts/missing75.dat")),"Embedded part file not installed");
            check(Files.isRegularFile(custom.resolve("parts/s/test77.dat")),"Embedded subpart not installed");
            System.out.println("OK .io embedded .dat importer and names");
            float[] xyz={0,0,0,1,0,0,0,1,0, 0,0,0,1,0,0,0,0,1, 0,0,0,0,1,0,0,0,1, 1,0,0,0,1,0,0,0,1};
            var small=List.of(new LDrawProjectReader.Instance("x",16,0,0,0,ID),
                    new LDrawProjectReader.Instance("x",16,40,0,0,ID));
            var incremental=new PreciseMeshCollisionPlan.Incremental(small,0,0,0,0,0,0,
                    id->new PreciseMeshCollisionPlan.Geometry(xyz,new double[]{0,0,0,20,20,20}),1000,1000);
            incremental.advance(1);check(!incremental.done(),"Physics advance processed too many");
            incremental.advance(1);check(incremental.done(),"Physics advance incomplete");
            check(incremental.finish().pieces()==2,"Physics builder incorrect");
            System.out.println("OK incremental physics preparation");
        }finally{
            LDrawLibrary.close();
            try(var paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(Exception ignored){}});}
        }
    }
    public static void main(String[] args)throws Exception{
        test();
        for(String arg:args){
            Path file=Path.of(arg);
            if(!Files.isRegularFile(file))continue;
            var result=LDrawProjectReader.inspect(file);
            check(!result.parts().isEmpty(),"Empty project "+file);
            System.out.println(file.getFileName()+" : "+result.parts().size()+" pieces / "+result.submodels()+" submodels");
        }
    }
}
