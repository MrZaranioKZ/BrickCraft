package fr.brickcraft.core;

/** Pure limits and coordinates, shared by client, server and standalone tests. */
public final class StudioExportLimits {
    public static final int MAX_PIECES=4096;
    public static final int MAX_GRID=256;
    public static final int MAX_HEIGHT_UNITS=1280;
    public static final int MAX_CELLS_PER_PIECE=8192;
    public static final int MAX_TOTAL_CELLS=150000;
    public static final int MAX_DISTINCT_CELLS=150000;
    private StudioExportLimits(){}

    public static boolean validBatch(int offset,int count,int alreadyReceived,int total){
        return offset==alreadyReceived && count>=1 && count<=24 &&
                total>=1 && total<=MAX_PIECES && alreadyReceived+count<=total;
    }
    public static boolean validGrid(int grid){
        return grid==16 || grid==32 || grid==64 || grid==128 || grid==256;
    }
    public static boolean validLocal(BrickPiece p,int grid){
        return p!=null && p.valid() && validGrid(grid) && p.x()>=0 && p.z()>=0 &&
            p.y()>=0 && p.x()+p.width()<=grid && p.z()+p.length()<=grid &&
            p.top()<=MAX_HEIGHT_UNITS;
    }
    /** Estimate BEFORE enumerating an expensive occupiedCells() array. */
    public static long upperBoundCells(BrickPiece p){
        long vertical=Math.floorDiv(p.top()+(p.kind().studs?1:0)-1,5)-Math.floorDiv(p.y(),5)+1L;
        return (long)p.width()*p.length()*vertical;
    }
    public static BrickPiece inWorld(BrickPiece local,int x,int blockY,int z){
        long wy=(long)blockY*5+local.y();
        long wx=(long)x+local.x(),wz=(long)z+local.z();
        if(wy<Integer.MIN_VALUE || wy>Integer.MAX_VALUE || wx<Integer.MIN_VALUE ||
                wx>Integer.MAX_VALUE || wz<Integer.MIN_VALUE || wz>Integer.MAX_VALUE)
            throw new IllegalArgumentException("World origin overflow");
        return local.withPosition((int)wx,(int)wy,(int)wz);
    }
}
