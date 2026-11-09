package net.minecraft.client.gui.screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
public class Screen {
 public int width=1200,height=700;
 public final java.util.List<Object> children = new java.util.ArrayList<>();
 protected final TextRenderer textRenderer=new TextRenderer();
 protected final MinecraftClient client=new MinecraftClient();
 public Screen(Text title){}
 protected void init(){}
 protected <T> T addDrawableChild(T widget){children.add(widget);return widget;}
 protected Object getFocused(){return null;}
 public static boolean hasShiftDown(){return false;}
 public void renderBackground(DrawContext c,int mx,int my,float delta){}
 public void render(DrawContext c,int mx,int my,float delta){}
 public boolean mouseClicked(double x,double y,int b){return false;}
 public boolean mouseDragged(double x,double y,int b,double dx,double dy){return false;}
 public boolean mouseReleased(double x,double y,int b){return false;}
 public boolean mouseScrolled(double x,double y,double h,double v){return false;}
 public boolean keyPressed(int key,int sc,int mod){return false;}
}
