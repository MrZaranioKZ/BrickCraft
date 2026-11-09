package fr.brickcraft.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Read once when the client registers its block-entity renderer. */
public final class BrickRenderDistanceConfig {
    public static final int DEFAULT_DISTANCE = 256;
    public static final int MIN_DISTANCE = 64;
    public static final int MAX_DISTANCE = 512;
    private static final String PROPERTY = "block_entity_render_distance";

    private BrickRenderDistanceConfig() {}

    public static int sanitize(String input) {
        if (input == null) return DEFAULT_DISTANCE;
        try {
            int value = Integer.parseInt(input.trim());
            if (value < MIN_DISTANCE) return MIN_DISTANCE;
            return Math.min(value, MAX_DISTANCE);
        } catch (NumberFormatException ignored) {
            return DEFAULT_DISTANCE;
        }
    }

    public static int load(Path configRoot) {
        Path file = configRoot.resolve("brickcraft").resolve("render.properties");
        Properties values = new Properties();
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    values.load(in);
                }
            } else {
                values.setProperty(PROPERTY, Integer.toString(DEFAULT_DISTANCE));
                try (OutputStream out = Files.newOutputStream(file)) {
                    values.store(out, "BrickCraft rendering range (blocks): 64..512. Restart Minecraft after editing. High values cost FPS.");
                }
            }
        } catch (IOException ignored) {
            return DEFAULT_DISTANCE;
        }
        return sanitize(values.getProperty(PROPERTY));
    }
}
