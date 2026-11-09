import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.world.PreciseMeshCollisionPlan;
import fr.brickcraft.world.ExplorationStepRules;
import java.util.*;

/** Tests the problems observed in v0.12: unreachable doors, missing anchor floor and ramps. */
public final class TEST_PHYSIQUE_V013 {
    private static final double[] ID={1,0,0,0,1,0,0,0,1};
    private static void check(boolean value,String error){if(!value)throw new AssertionError(error);}
    private static boolean at(PreciseMeshCollisionPlan.Result result,int bx,int by,int bz,int x,int y,int z){
        return PreciseMeshCollisionPlan.occupied(result.masks().get(new PreciseMeshCollisionPlan.Cell(bx,by,bz)),x,y,z);
    }
    private static PreciseMeshCollisionPlan.Result arch(TEST_PHYSIQUE_V012.Mesh mesh){
        return PreciseMeshCollisionPlan.build(
            List.of(new LDrawProjectReader.Instance("arch",1,0,0,0,ID)),-5,-6,-5,0,0,0,
            id->mesh.geometry());
    }
    public static void main(String[] args){
        TEST_PHYSIQUE_V012.main(args);
        var arched=new TEST_PHYSIQUE_V012.Mesh();
        // 3-block-high doorway: posts left/right, lintel over an empty 1x2.5 portal.
        arched.box(0,0,0,1,3,1);
        arched.box(2,0,0,3,3,1);
        arched.box(1,2.5,0,2,3,1);
        var passage=arch(arched);
        check(!at(passage,6,5,5,4,4,4),"Doorway MUST be open at lower level");
        check(!at(passage,6,6,5,4,4,4),"Doorway MUST be open at head level");
        check(at(passage,5,6,5,4,4,4),"Left door post collides");
        check(at(passage,7,6,5,4,4,4),"Right door post collides");
        check(at(passage,6,7,5,4,4,4),"Upper beam collides");
        // A face aligned exactly on a 1/8 voxel boundary must not build a phantom
        // step of collision ABOVE it (the visible "levitating feet" regression).
        var lowSlab=new TEST_PHYSIQUE_V012.Mesh();lowSlab.box(0,0,0,1,.5,1);
        var half=arch(lowSlab);
        check(at(half,5,5,5,4,3,4),"Inside slab is solid");
        check(!at(half,5,5,5,4,4,4),"No invisible 1/8 step over slab surface");
        // Mesh has an open side (incomplete LDraw shell). Do not magically solidify its interior.
        var panel=new TEST_PHYSIQUE_V012.Mesh();
        panel.quad(new double[]{0,0,0},new double[]{0,1,0},new double[]{0,1,1},new double[]{0,0,1});
        var sheet=arch(panel);
        check(!at(sheet,5,5,5,4,4,4),"Single open wall should not generate invisible filled box");
        // V0.12 threw away the *entire* anchor cell's collision mask.
        var cube=new TEST_PHYSIQUE_V012.Mesh();cube.box(0,0,0,1,1,1);
        var atAnchor=PreciseMeshCollisionPlan.build(
            List.of(new LDrawProjectReader.Instance("anchor",1,0,-20,0,ID)),0,0,0,0,0,0,id->cube.geometry());
        check(atAnchor.masks().containsKey(new PreciseMeshCollisionPlan.Cell(0,0,0)),
              "Anchor voxel cell must be present");
        check(ExplorationStepRules.stepHeight(.125)>=.125,"Miniature climbs 1/8-block terraces");
        check(ExplorationStepRules.stepHeight(1)>.6,"Normal player walks larger LEGO slope microsteps");
        check(ExplorationStepRules.stepHeight(4)<1,"Full Minecraft walls stay non-stepable");
        System.out.println("Physique V0.13 : portes, parois ouvertes, ancre, marche auto = OK");
    }
}
