import fr.brickcraft.client.BrickI18n;
import net.minecraft.client.resource.language.I18n;

public final class BrickI18nFormatV0251Test {
    private static void same(String expected,String actual){
        if(!expected.equals(actual))throw new AssertionError("expected <"+expected+"> got <"+actual+">");
    }
    public static void main(String...args){
        if(!I18n.translate("studio.height").startsWith("Format error:"))
           throw new AssertionError("Test stub must reproduce the old eager-format bug");
        same("SHAPES 1–6 / 26",BrickI18n.fmt("studio.parts",1,6,26));
        same("Height: AUTO",BrickI18n.fmt("studio.height","AUTO"));
        same("Opacity: 75%",BrickI18n.fmt("percent",75));
        same("Snap guides: enabled",BrickI18n.fmt("no_parameters"));
        same("unknown",BrickI18n.fmt("unknown",1));
        System.out.println("OK: formatage des traductions sans 'Format error'");
    }
}
