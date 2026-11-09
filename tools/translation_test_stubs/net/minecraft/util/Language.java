package net.minecraft.util;
import java.util.Map;
public final class Language {
    private final Map<String,String> templates=Map.of(
      "studio.parts", "SHAPES %s–%s / %s",
      "studio.height", "Height: %s",
      "percent", "Opacity: %s%%",
      "no_parameters", "Snap guides: enabled"
    );
    private static final Language INSTANCE=new Language();
    public static Language getInstance(){return INSTANCE;}
    public String get(String key){return templates.getOrDefault(key,key);}
}
