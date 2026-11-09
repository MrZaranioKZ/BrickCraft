package fr.brickcraft;

import java.util.Locale;
import java.text.Normalizer;
import fr.brickcraft.core.BrickPalette;

/** Colour inputs for /brickcraft: named French/English colours, RGB hex or decimal. */
public final class BrickCommandPalette {
    private BrickCommandPalette() {}
    public static int rgb(String value) {
        if(value==null)throw new IllegalArgumentException("Couleur vide");
        String raw=value.trim();
        String key=Normalizer.normalize(raw.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").replace(' ','_');
        String[] en={"white","black","light_gray","dark_gray","red","orange","yellow",
                "green","blue","cyan","purple","pink","tan","brown","lime","navy","coral"};
        for(int i=0;i<BrickPalette.NAMES.length;i++) {
            String fr=Normalizer.normalize(BrickPalette.NAMES[i].toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
                    .replaceAll("\\p{M}","").replace(' ','_');
            if(key.equals(fr)||key.equals(en[i]))return BrickPalette.COLORS[i];
        }
        if(key.equals("grey")||key.equals("gray"))return 0xA3A8AE;
        if(raw.startsWith("#"))raw=raw.substring(1);
        else if(raw.startsWith("0x")||raw.startsWith("0X"))raw=raw.substring(2);
        else if(raw.matches("[0-9]{1,8}")){
            // Bare numeric values are decimal; prefix # or 0x for hexadecimal RGB.
            long decimal=Long.parseLong(raw);
            if(decimal>=0 && decimal<=0xFFFFFF)return (int)decimal;
            throw new IllegalArgumentException("RGB hors limites");
        }
        if(!raw.matches("(?i)[0-9a-f]{6}"))throw new IllegalArgumentException("Couleur : nom, #RRGGBB ou 0xRRGGBB");
        return Integer.parseInt(raw,16);
    }
}
