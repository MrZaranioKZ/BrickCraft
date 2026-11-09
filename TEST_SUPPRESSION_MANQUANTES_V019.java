import fr.brickcraft.importer.*;
import java.nio.file.*;
import java.util.*;

/** Independent JDK 21 tests: selective/bulk deletion, non-destructive behaviour and BCPX. */
public final class TEST_SUPPRESSION_MANQUANTES_V019 {
    private static final double[] ID={1,0,0,0,1,0,0,0,1};
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static LDrawProjectReader.Instance piece(String id,int color,double x){
        return new LDrawProjectReader.Instance(id,color,x,23.5,14.25,ID);
    }
    private static void sample()throws Exception{
        var project=new LDrawProjectReader.Result("test.io",2,List.of(
            piece("4079b",4,1.125),piece("4079B.dat",16,20.125),
            piece("4497b",1,42),piece("57029c01",5,55),piece("3004",2,77)),
            List.of("source conservee"));
        var removed=MissingPartRemovalEditor.removeReference(project,"4079b");
        check(removed.count()==2&&removed.references()==1,"Two case/suffix variants must go");
        check(removed.project().parts().size()==3,"One-reference deletion removed extra bricks");
        check(project.parts().size()==5,"Original project mutated");
        check(removed.project().parts().get(2).partId().equals("3004"),"Available brick was removed");
        check(Arrays.equals(removed.project().parts().get(0).matrix(),ID),"Matrix changed");
        check(removed.project().parts().get(0).color()==1 && removed.project().parts().get(0).x()==42,
                "Remaining brick properties changed");
        check(removed.project().problems().stream().anyMatch(x->x.contains("Suppression confirmee")),
                "No deletion audit trail");
        var absent=MissingPartRemovalEditor.removeReference(removed.project(),"not_there");
        check(absent.count()==0&&absent.project()==removed.project(),"No-op should return same source");
        var bulk=MissingPartRemovalEditor.removeReferences(removed.project(),
                Set.of("4497b","57029c01"));
        check(bulk.count()==2&&bulk.references()==2,"Bulk deletion failed");
        check(bulk.project().parts().size()==1&&bulk.project().parts().get(0).partId().equals("3004"),
                "Bulk removed a valid brick");
        Path file=Files.createTempFile("brickcraft_v019_deleted_", ".bcpx");
        try{
            PreciseProjectIO.save(file,bulk.project());
            var restored=PreciseProjectIO.read(file);
            check(restored.parts().size()==1 && restored.parts().get(0).x()==77,"BCPX deleted project corrupt");
            check(restored.problems().stream().anyMatch(x->x.contains("Suppression confirmee")),
                    "BCPX audit trail lost");
            var cleared=MissingPartRemovalEditor.removeReferences(restored,Set.of("3004"));
            check(cleared.project().parts().isEmpty(),"Last brick not deleted");
            PreciseProjectIO.save(file,cleared.project());
            check(PreciseProjectIO.read(file).parts().isEmpty(),"Empty BCPX failed");
        }finally{Files.deleteIfExists(file);}
        check(MissingPartRemovalEditor.removeReferences(project,Set.of()).count()==0,"Empty reference set");
        System.out.println("OK: delete reference, delete all, empty model, unchanged originals, geometry and BCPX");
    }
    private static void realFiles(String[] args)throws Exception{
        for(String name:args){
            Path file=Path.of(name);
            if(!Files.isRegularFile(file))continue;
            var model=LDrawProjectReader.inspect(file);
            Set<String> ids=Set.of("4079b","4497b","57029c01");
            int n=(int)model.parts().stream().filter(p->ids.contains(PartReplacementMatcher.normalize(p.partId()))).count();
            var cleaned=MissingPartRemovalEditor.removeReferences(model,ids);
            check(cleaned.count()==n,"Real file deletion count mismatch: "+file.getFileName());
            check(cleaned.project().parts().size()==model.parts().size()-n,"Real file lost other bricks");
            check(model.parts().size()==LDrawProjectReader.inspect(file).parts().size(),"Original file touched");
            System.out.println(file.getFileName()+" : "+model.parts().size()+" bricks, deleted "+n);
        }
    }
    public static void main(String[] args)throws Exception {sample();realFiles(args);}
}
