import fr.brickcraft.client.BrickStudioScreen;
import fr.brickcraft.client.ProjectImportScreen;
import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.StudioProjectAnalysis;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.minecraft.client.gui.DrawContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

/** GUI smoke test with substitutes for Minecraft classes. Not a real GPU benchmark. */
public final class PrecisePreviewGuiV09Test {
    public static void main(String[] args)throws Exception {
        LDrawLibrary.init(Path.of(args[0]));
        ProjectImportScreen screen=new ProjectImportScreen(new BrickStudioScreen());
        var project=LDrawProjectReader.parse("0 FILE v.ldr\n"+
             "1 4 0 0 0 1 0 0 0 1 0 0 0 1 3004.dat\n"+
             "1 2 40 0 0 0 -1 0 1 0 0 0 0 1 3004.dat\n","v.ldr");
        Field summary=ProjectImportScreen.class.getDeclaredField("summary");
        summary.setAccessible(true);summary.set(screen,StudioProjectAnalysis.analyze(project));
        Method build=ProjectImportScreen.class.getDeclaredMethod("buildPreview");
        build.setAccessible(true);build.invoke(screen);
        screen.render(new DrawContext(), 0, 0, 0);
        Field count=ProjectImportScreen.class.getDeclaredField("realMeshesRendered");
        count.setAccessible(true);
        int rendered=(int)count.get(screen);
        if(rendered<=0)throw new AssertionError("No exact mesh rendered in the default close-up");
        System.out.println("V0.9 GUI simulated: "+rendered+" actual 3004 triangle meshes rendered");
    }
}
