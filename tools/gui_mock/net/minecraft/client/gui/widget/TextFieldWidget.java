package net.minecraft.client.gui.widget;
import net.minecraft.text.Text;
import net.minecraft.client.font.TextRenderer;
public class TextFieldWidget {
 private String value="";
 public int x,y,w,h;
 public TextFieldWidget(TextRenderer r,int x,int y,int w,int h,Text t){this.x=x;this.y=y;this.w=w;this.h=h;}
 public void setMaxLength(int len){}
 public void setText(String str){value=str;}
 public String getText(){return value;}
}
