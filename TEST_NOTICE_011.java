import fr.brickcraft.guide.*;
import fr.brickcraft.importer.LDrawProjectReader;
import java.nio.file.*;
import java.util.*;
public class TEST_NOTICE_011 {
    static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        var identity=new double[]{1,0,0,0,1,0,0,0,1};
        List<LDrawProjectReader.Instance> parts=List.of(
            new LDrawProjectReader.Instance("3005",4,0,0,0,identity),
            new LDrawProjectReader.Instance("3005",4,20,-8,0,identity),
            new LDrawProjectReader.Instance("3004",1,20,-24,0,identity),
            new LDrawProjectReader.Instance("3005",4,40,-24,0,identity));
        var layers=BrickGuidePlan.layers(parts,8);
        check(BrickGuideGpuBatches.bucket(25,24)==1,"bucket");
        check(BrickGuideGpuBatches.uploadStamp(0,0,23)==-1,"no geometry at step zero");
        check(BrickGuideGpuBatches.uploadStamp(10,0,23)==10,"partial batch");
        check(BrickGuideGpuBatches.uploadStamp(99,0,23)==24,"completed batch stays cached");
        for(int step=1;step<4000;step++){
            int previous=BrickGuideGpuBatches.uploadStamp(step-1,0,23);
            int next=BrickGuideGpuBatches.uploadStamp(step,0,23);
            if(step>24)check(previous==next,"old batch must not be rebuilt");
        }
        check(layers.size()==3,"layers");check(layers.revealedParts(0)==0,"empty first");
        check(layers.revealedParts(3)==4,"all revealed");
        var singles=BrickGuidePlan.batches(parts,1);check(singles.size()==4,"single steps");
        for(int i=0;i<4;i++)check(singles.stepFor(i)>=0,"mapping");
        var lots=BrickGuidePlan.batches(parts,3);check(lots.size()==2,"lots");
        Path temp=Files.createTempFile("brickcraft-steps-",".ldr");
        try{
            Files.writeString(temp,"0 FILE model.ldr\n1 4 0 0 0 1 0 0 0 1 0 0 0 1 3005.dat\n"+
                "0 STEP\n1 4 20 -8 0 1 0 0 0 1 0 0 0 1 3005.dat\n"+
                "0 STEP\n1 1 20 -24 0 1 0 0 0 1 0 0 0 1 3004.dat\n"+
                "1 4 40 -24 0 1 0 0 0 1 0 0 0 1 3005.dat\n");
            var original=BrickSourceStepReader.inspect(temp,parts);
            check(original.size()==3&&original.originalSteps(),"original steps");
            check(original.at(3).partIndexes().equals(List.of(2,3)),"source order");
            var swapped=new ArrayList<>(parts);Collections.swap(swapped,0,2);
            try{BrickSourceStepReader.inspect(temp,swapped);throw new AssertionError("unrelated source accepted");}
            catch(java.io.IOException expected){}
        }finally{Files.deleteIfExists(temp);}
        if(args.length>0){
            Path castle=Path.of(args[0]);
            var project=LDrawProjectReader.inspect(castle);
            var original=BrickSourceStepReader.inspect(castle,project.parts());
            check(original.partCount()==project.parts().size(),"castle matching");
            check(original.size()>50,"castle rich step count");
            System.out.println("Chateau : "+original.partCount()+" pieces / "+original.size()+" etapes source");
        }
        System.out.println("Notices V0.11.0 : tests purs REUSSIS");
    }
}
