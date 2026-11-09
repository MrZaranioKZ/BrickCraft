package fr.brickcraft.world;

/** Dependency-free motion policy for micro-voxel LEGO ramps (no global step-height buff). */
public final class ExplorationStepRules {
    private ExplorationStepRules(){}
    /** Never step an entire Minecraft block. A small player can ascend 1/8-block microsteps. */
    public static double stepHeight(double playerScale){
        if(!Double.isFinite(playerScale)||playerScale<=0)return .6;
        return Math.max(.25,Math.min(.90,playerScale*.78));
    }
}
