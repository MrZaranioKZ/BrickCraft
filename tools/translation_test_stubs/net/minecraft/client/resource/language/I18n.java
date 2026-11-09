package net.minecraft.client.resource.language;
public final class I18n {
    private I18n() {}
    public static String translate(String key) {
        String template=net.minecraft.util.Language.getInstance().get(key);
        try { return String.format(template); }
        catch (java.util.IllegalFormatException ex) { return "Format error: " + template; }
    }
}
