package fr.brickcraft.core;

/**
 * Parametric starter catalog. IMPORTANT: never reorder existing constants;
 * multiplayer packets use ordinal IDs, while worlds use stable enum names.
 */
public enum BrickKind {
    BRICK_1X1("Brique 1×1", 1, 1, 3, true),
    BRICK_1X2("Brique 1×2", 1, 2, 3, true),
    BRICK_2X2("Brique 2×2", 2, 2, 3, true),
    BRICK_2X4("Brique 2×4", 2, 4, 3, true),
    PLATE_1X2("Plaque 1×2", 1, 2, 1, true),
    TILE_1X2("Tuile 1×2", 1, 2, 1, false),

    // New V0.3 shapes (rectangular LEGO-family pieces; LDraw mesh import comes later).
    BRICK_1X3("Brique 1×3", 1, 3, 3, true),
    BRICK_1X4("Brique 1×4", 1, 4, 3, true),
    BRICK_1X6("Brique 1×6", 1, 6, 3, true),
    BRICK_2X3("Brique 2×3", 2, 3, 3, true),
    BRICK_2X6("Brique 2×6", 2, 6, 3, true),
    BRICK_2X8("Brique 2×8", 2, 8, 3, true),
    PLATE_1X1("Plaque 1×1", 1, 1, 1, true),
    PLATE_1X3("Plaque 1×3", 1, 3, 1, true),
    PLATE_1X4("Plaque 1×4", 1, 4, 1, true),
    PLATE_2X2("Plaque 2×2", 2, 2, 1, true),
    PLATE_2X3("Plaque 2×3", 2, 3, 1, true),
    PLATE_2X4("Plaque 2×4", 2, 4, 1, true),
    TILE_1X1("Tuile 1×1", 1, 1, 1, false),
    TILE_1X3("Tuile 1×3", 1, 3, 1, false),
    TILE_1X4("Tuile 1×4", 1, 4, 1, false),
    TILE_2X2("Tuile 2×2", 2, 2, 1, false),
    TILE_2X4("Tuile 2×4", 2, 4, 1, false),
    SLOPE_1X2("Pente 1×2", 1, 2, 3, false, true),
    SLOPE_2X2("Pente 2×2", 2, 2, 3, false, true),
    SLOPE_2X3("Pente 2×3", 2, 3, 3, false, true),
    LDRAW_PART("Piece LDraw", 1, 1, 3, false);

    public final String title;
    public final int width, length, plates;
    public final boolean studs, slope;

    BrickKind(String title, int width, int length, int plates, boolean studs) {
        this(title,width,length,plates,studs,false);
    }
    BrickKind(String title, int width, int length, int plates, boolean studs,boolean slope) {
        this.title = title;
        this.width = width;
        this.length = length;
        this.plates = plates;
        this.studs = studs;
        this.slope = slope;
    }

    public int width(int quarterTurn) { return (quarterTurn & 1) == 0 ? width : length; }
    public int length(int quarterTurn) { return (quarterTurn & 1) == 0 ? length : width; }
}
