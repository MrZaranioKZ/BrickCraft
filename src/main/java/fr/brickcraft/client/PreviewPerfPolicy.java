package fr.brickcraft.client;

/** Work limit for the GUI-only emergency CPU preview; normal GPU views never skip parts. */
public final class PreviewPerfPolicy {
    private static final int MAX_CPU_VOLUMES=700;
    private PreviewPerfPolicy(){}
    public static int cpuStride(int partCount){
        return partCount<=MAX_CPU_VOLUMES?1:(partCount+MAX_CPU_VOLUMES-1)/MAX_CPU_VOLUMES;
    }
    public static int sampledCount(int partCount){
        if(partCount<=0)return 0;
        return (partCount+cpuStride(partCount)-1)/cpuStride(partCount);
    }
}
