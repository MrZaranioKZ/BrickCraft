package fr.brickcraft.world;

/** Portable cell NBT: relative X/Z in studs; relative Y in fifths of a block. */
public final class BrickCellCoordinates {
    private BrickCellCoordinates() {}
    public static int[] relative(int pieceX,int pieceY,int pieceZ,int cellX,int cellY,int cellZ) {
        return new int[]{pieceX-cellX,pieceY-5*cellY,pieceZ-cellZ};
    }
    public static int[] absolute(int localX,int localY,int localZ,int cellX,int cellY,int cellZ) {
        return new int[]{localX+cellX,localY+5*cellY,localZ+cellZ};
    }
}
