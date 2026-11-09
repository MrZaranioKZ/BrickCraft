package fr.brickcraft.client;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import fr.brickcraft.importer.LDrawProjectReader;
import java.nio.file.Path;
public class ProjectImportScreen extends Screen{
 public ProjectImportScreen(){super(Text.literal("mock"));}
 void applyFreeEdits(LDrawProjectReader.Result result){}
 void refreshFreeFiles(Path destination){}
 void renderFreeViewport(DrawContext d,int a,int b,int c,int e,int idx,boolean rotate){}
 int editorHitGizmo(double mx,double my,int idx,int a,int b,int c,int d){return -1;}
 PreviewSelectionMath.Frame editorDragFrame(int a,int b,int c,int d){return null;}
 int editorPickPart(double mx,double my,int selected,boolean cycle,int a,int b,int c,int d){return -1;}
 boolean editorPreviewMouseClick(double mx,double my,int btn,int a,int b,int c,int d){return false;}
 boolean editorPreviewMouseDrag(int b,double dx,double dy){return false;}
 boolean editorPreviewMouseRelease(int b){return false;}
 boolean editorPreviewWheel(double mx,double my,double v,int a,int b,int c,int d){return false;}
}
