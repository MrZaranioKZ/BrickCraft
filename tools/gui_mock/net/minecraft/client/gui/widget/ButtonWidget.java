package net.minecraft.client.gui.widget;
import net.minecraft.text.Text;
import java.util.function.Consumer;
public class ButtonWidget {
 public int x,y,w,h;
 public static Builder builder(Text msg,Consumer<ButtonWidget> action){return new Builder();}
 public void setMessage(Text msg){}
 public static class Builder{
    private int x,y,w,h;
    public Builder dimensions(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
    public ButtonWidget build(){var b=new ButtonWidget();b.x=x;b.y=y;b.w=w;b.h=h;return b;}
 }
}
