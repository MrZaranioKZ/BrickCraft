package fr.brickcraft.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline starter part search. Element IDs include color, unlike shape/design IDs. */
public final class BrickCatalog {
    private BrickCatalog() {}
    public record Entry(BrickKind kind,int rgb,String reference) {}

    public static Entry findElement(String query){
        return switch(query.trim()) {
            // BrickLink: design 3004 + Coral (LDraw 353). Verified element ID.
            case "6258572" -> new Entry(BrickKind.BRICK_1X2,0xFF6D77,"6258572");
            default -> null;
        };
    }
    public static String designNumber(BrickKind kind){
        return switch(kind){
            case BRICK_1X1 -> "3005";
            case BRICK_1X2 -> "3004";
            case BRICK_1X3 -> "3622";
            case BRICK_1X4 -> "3010";
            case BRICK_2X2 -> "3003";
            case BRICK_2X3 -> "3002";
            case BRICK_2X4 -> "3001";
            case BRICK_2X6 -> "2456";
            case PLATE_1X1 -> "3024";
            case PLATE_1X2 -> "3023";
            case PLATE_2X2 -> "3022";
            case PLATE_2X4 -> "3020";
            case TILE_1X2 -> "3069";
            default -> "";
        };
    }
    public static List<BrickKind> search(String query){
        String q=normalize(query);
        List<BrickKind> result=new ArrayList<>();
        Entry element=findElement(query);
        if(element!=null){result.add(element.kind());return result;}
        for(BrickKind kind:BrickKind.values()) {
            if(kind==BrickKind.LDRAW_PART)continue;
            String title=normalize(kind.title),shortName=normalize(kind.name());
            String dimensions=kind.width+"x"+kind.length;
            if(q.isEmpty()||title.contains(q)||shortName.contains(q)||dimensions.contains(q)||
                    (!designNumber(kind).isEmpty()&&designNumber(kind).contains(q)))result.add(kind);
        }
        return result;
    }
    private static String normalize(String value){
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replace('×','x').replaceAll("[ _-]+", "").trim();
    }
}
