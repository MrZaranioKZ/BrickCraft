package fr.brickcraft.guide;

/** Pure GPU batch helper, tested independently of Fabric/GL. */
public final class BrickGuideGpuBatches {
    private BrickGuideGpuBatches(){}
    public static int bucket(int zeroBasedStep,int batchSize){
        if(zeroBasedStep<0||batchSize<1)throw new IllegalArgumentException("Indice de lot invalide");
        return zeroBasedStep/batchSize;
    }
    /** State to upload for one batch. -1 = no visible triangle. */
    public static int uploadStamp(int completeSteps,int firstStep,int lastStep){
        if(firstStep<0||lastStep<firstStep)throw new IllegalArgumentException("Etapes non triees");
        if(completeSteps<=firstStep)return -1;
        return Math.min(completeSteps,lastStep+1);
    }
}
