import fr.brickcraft.guide.BrickGuidePlanner;
import fr.brickcraft.importer.*;
import java.nio.file.Path;
import java.util.*;
public class BuildGuide098Test {
    public static void main(String[] args)throws Exception{
        var castle=PreciseProjectIO.read(Path.of(args[0]));
        var plan=BrickGuidePlanner.preview(castle,8,20);
        if(castle.parts().size()!=3245 || plan.partCount()!=3245)throw new AssertionError("Source count");
        boolean[] seen=new boolean[3245];int total=0;
        for(var layer:plan.layers())for(int i:layer.indexes()){
            if(i<0||i>=3245||seen[i])throw new AssertionError("Duplicate layer " + i);
            seen[i]=true;total++;
        }
        if(total!=3245)throw new AssertionError("Missing layer pieces");
        Arrays.fill(seen,false);total=0;
        for(var step:plan.sourceSteps())for(int i:step.indexes()){
            if(i<0||i>=3245||seen[i])throw new AssertionError("Duplicate step " + i);
            seen[i]=true;total++;
        }
        if(total!=3245 || plan.sourceSteps().size()!=163)throw new AssertionError("Step coverage");
        System.out.println("Prototype de guide: "+plan.layers().size()+" couches approximatives, "+
            plan.sourceSteps().size()+" lots source, 3245 references conservees (PAS des instructions officielles)");
    }
}
