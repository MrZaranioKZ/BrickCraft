import fr.brickcraft.client.*;
import fr.brickcraft.importer.*;
import net.minecraft.client.gui.widget.*;
import java.lang.reflect.Method;
import java.util.*;

/** Verify GUI widgets do not overlap or escape the panel for common Minecraft GUI scales. */
public class Interaction098GuiLayoutTest{
    record Rect(int x,int y,int w,int h){}
    static Rect rect(Object v){
        if(v instanceof ButtonWidget b)return new Rect(b.x,b.y,b.w,b.h);
        if(v instanceof TextFieldWidget f)return new Rect(f.x,f.y,f.w,f.h);
        throw new AssertionError("Unexpected widget "+v);
    }
    static boolean overlaps(Rect a,Rect b){return a.x<b.x+b.w && a.x+a.w>b.x && a.y<b.y+b.h && a.y+a.h>b.y;}
    public static void main(String[] args)throws Exception{
        Method init=PreciseFreeScreen.class.getDeclaredMethod("init");init.setAccessible(true);
        int checks=0;
        var source=new LDrawProjectReader.Result("test.ldr",0,List.of(),List.of());
        for(int w:new int[]{650,821,1024,1400})for(int h:new int[]{340,360,400,461,600,820}){
            var screen=new PreciseFreeScreen(new ProjectImportScreen(),source,"mock.ldr");
            screen.width=w;screen.height=h;init.invoke(screen);
            var widgets=screen.children.stream().map(Interaction098GuiLayoutTest::rect).toList();
            for(int i=0;i<widgets.size();i++){
                var a=widgets.get(i);
                if(a.x<0||a.y<0||a.x+a.w>w||a.y+a.h>h-25||a.w<=0||a.h<=0)
                    throw new AssertionError("Widget outside screen "+w+"x"+h+" "+a);
                checks++;
                for(int j=i+1;j<widgets.size();j++)if(overlaps(a,widgets.get(j)))
                    throw new AssertionError("Overlap "+w+"x"+h+" "+a+" / "+widgets.get(j));
            }
        }
        System.out.println("GUI layout: "+checks+" widgets in 24 screen sizes; no overlaps");
    }
}
