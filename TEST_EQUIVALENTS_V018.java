import fr.brickcraft.importer.*;
import java.nio.file.*;
import java.util.*;

/** Pure Java checks: matching, safe replacement and lossless BCPX roundtrip. */
public final class TEST_EQUIVALENTS_V018 {
    private static final double[] ID={1,0,0,0,1,0,0,0,1};
    private static void check(boolean p,String m){if(!p)throw new AssertionError(m);}
    private static LDrawProjectReader.Instance piece(String id,int color,double x){
        return new LDrawProjectReader.Instance(id,color,x,23.5,14.25,ID);
    }
    public static void main(String[] args)throws Exception{
        var available=List.of(
                new PartReplacementMatcher.Available("4079","Plate 1 x 1 with Clip"),
                new PartReplacementMatcher.Available("4079a","Previous variant"),
                new PartReplacementMatcher.Available("4497","Hinge Plate"),
                new PartReplacementMatcher.Available("57029ac01","Window Assembly"),
                new PartReplacementMatcher.Available("3004","Brick 1 x 2"),
                new PartReplacementMatcher.Available("4078","Different part"));
        var a=PartReplacementMatcher.suggest("4079b",available,10);
        check(!a.isEmpty()&&a.get(0).id().equals("4079"),"4079b -> 4079 should rank first");
        var b=PartReplacementMatcher.suggest("4497b",available,10);
        check(!b.isEmpty()&&b.get(0).id().equals("4497"),"4497b -> 4497 failed");
        var c=PartReplacementMatcher.suggest("57029c01",available,10);
        check(!c.isEmpty()&&c.get(0).id().equals("57029ac01"),"57029c01 -> 57029ac01 failed");
        check(PartReplacementMatcher.suggest("4079b",List.of(new PartReplacementMatcher.Available("9999","Other")),10).isEmpty(),"Unrelated IDs suggested");
        check(PartReplacementMatcher.suggest("4079b",available,1).size()==1,"Suggestions unbounded");
        check(PartReplacementMatcher.editDistance("57029c01","57029ac01",2)==1,"Levenshtein insertion");
        System.out.println("OK : variantes 4079b, 4497b, 57029c01 et filtrage prudent");

        var model=new LDrawProjectReader.Result("project.io",2,List.of(
                piece("4079b",4,1.0),piece("4079b",16,20.125),
                piece("4497b",1,42),piece("57029c01",5,55),piece("3004",2,77)),List.of("source intacte"));
        var first=PartReplacementEditor.replace(model,"4079b","4079");
        check(first.count()==2,"All occurrences must be replaced");
        check(model.parts().get(0).partId().equals("4079b"),"The original model was modified");
        check(first.project().parts().get(1).partId().equals("4079"),"Wrong target ID");
        check(first.project().parts().get(1).color()==16 && first.project().parts().get(1).x()==20.125 &&
                first.project().parts().get(1).y()==23.5 && first.project().parts().get(1).z()==14.25,
                "Part colour / position mutated");
        check(Arrays.equals(first.project().parts().get(0).matrix(),ID),"Rotation lost");
        var second=PartReplacementEditor.replace(first.project(),"4497b","4497");
        var third=PartReplacementEditor.replace(second.project(),"57029c01","57029ac01");
        check(second.count()==1&&third.count()==1,"Separate replacement failed");
        check(third.project().parts().get(4).partId().equals("3004"),"Unrelated piece changed");
        check(third.project().problems().stream().anyMatch(x->x.contains("4079b -> 4079")),"No audit history");
        check(PartReplacementEditor.replace(third.project(),"4079b","4079").count()==0,"Repeated replacement changed pieces");
        try{PartReplacementEditor.replace(model,"4079b","../../evil");throw new AssertionError("unsafe replacement accepted");}
        catch(IllegalArgumentException expected){}

        Path temp=Files.createTempFile("brickcraft_v018_equiv_", ".bcpx");
        try {
            PreciseProjectIO.save(temp,third.project());
            var reread=PreciseProjectIO.read(temp);
            check(reread.parts().size()==5,"BCPX lost pieces");
            check(reread.parts().get(3).partId().equals("57029ac01"),"BCPX lost replacement");
            check(reread.problems().stream().anyMatch(s->s.contains("4497b -> 4497")),"BCPX lost audit");
            check(reread.parts().get(1).x()==20.125,"BCPX lost precision");
        }finally{Files.deleteIfExists(temp);}
        System.out.println("OK : 4 remplacements manuels, positions, couleurs, rotations, historique et BCPX");
        for(String file:args){
            Path path=Path.of(file);
            if(!Files.isRegularFile(path))continue;
            var real=LDrawProjectReader.inspect(path);
            int originalSize=real.parts().size();
            for(String[] pair:new String[][]{{"4079b","4079"},{"4497b","4497"},{"57029c01","57029ac01"}}){
                int before=(int)real.parts().stream().filter(p->p.partId().equalsIgnoreCase(pair[0])).count();
                var changed=PartReplacementEditor.replace(real,pair[0],pair[1]);
                check(changed.count()==before,"Incorrect replacement count for "+pair[0]);
                real=changed.project();
                check(real.parts().size()==originalSize,"Real .io changed its piece count");
                System.out.println(path.getFileName()+" : "+pair[0]+" -> "+pair[1]+" : "+changed.count()+" pieces");
            }
        }
    }
}
