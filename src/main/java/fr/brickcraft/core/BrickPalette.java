package fr.brickcraft.core;

/** Approximate common ABS colors. V0.1 allows ANY 24-bit RGB color. */
public final class BrickPalette {
    private BrickPalette() {}
    public static final String[] NAMES = {
        "Blanc", "Noir", "Gris clair", "Gris foncé", "Rouge", "Orange", "Jaune", "Vert",
        "Bleu", "Azur", "Violet", "Rose", "Beige", "Marron", "Vert lime", "Bleu nuit", "Corail"
    };
    public static final int[] COLORS = {
        0xF4F4F4, 0x222222, 0xA3A8AE, 0x595F65, 0xC42830, 0xE58932, 0xF4C43B, 0x2D8D59,
        0x2169B4, 0x54BBD3, 0x8C53A8, 0xDE88B8, 0xDBBF93, 0x6A4028, 0xA5C53B, 0x193B6B, 0xFF6D77
    };
}
