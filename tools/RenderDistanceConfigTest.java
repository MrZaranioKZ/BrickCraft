import fr.brickcraft.client.BrickRenderDistanceConfig;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RenderDistanceConfigTest {
    private static void expect(int got, int want) {
        if(got != want) throw new AssertionError("got="+got+" want="+want);
    }
    public static void main(String[] args) throws Exception {
        expect(BrickRenderDistanceConfig.sanitize(null),256);
        expect(BrickRenderDistanceConfig.sanitize(" 128 "),128);
        expect(BrickRenderDistanceConfig.sanitize("not_a_number"),256);
        expect(BrickRenderDistanceConfig.sanitize("24"),64);
        expect(BrickRenderDistanceConfig.sanitize("2048"),512);
        Path dir=Files.createTempDirectory("brickcraft-range-test");
        expect(BrickRenderDistanceConfig.load(dir),256);
        Path prop=dir.resolve("brickcraft/render.properties");
        if(!Files.readString(prop).contains("block_entity_render_distance=256"))
            throw new AssertionError("config not written");
        Files.writeString(prop,"block_entity_render_distance=384\n");
        expect(BrickRenderDistanceConfig.load(dir),384);
        System.out.println("BrickCraft render distance: 7 assertions OK");
    }
}
