package fr.brickcraft.client;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.core.BrickKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Six concise pages with native BrickCraft item illustrations.
 * V0.23.1 fixes the former dark-on-light text shadows and scales the whole
 * reader to the actual Minecraft GUI coordinate system. Long translations
 * remain readable through clipped, scrollable body content.
 */
public final class BrickGuideBookScreen extends Screen {
    private static final int PAGE_COUNT=6;
    private static final int PAPER=0xFFF3F0E7;
    private static final int INK=0xFF1B3444;
    private static final int MUTED=0xFF456174;
    private static final int OUTLINE=0xFF0E2536;
    private static final int ACCENT=0xFF4DB2C9;
    private static final int LINE_HEIGHT=13;

    private int page;
    private int scrollLine;
    private int maxScroll;
    private BrickGuideLayout.Frame frame;

    public BrickGuideBookScreen(){super(Text.translatable("guide.brickcraft.title"));}

    public static void open(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc!=null && mc.currentScreen==null)mc.setScreen(new BrickGuideBookScreen());
    }

    private Text txt(String key){return Text.translatable("guide.brickcraft."+key);}

    @Override protected void init(){
        frame=BrickGuideLayout.calculate(width,height);
        int w=frame.navigationWidth(),y=frame.navigationY();
        addDrawableChild(ButtonWidget.builder(txt("previous"),b->change(-1))
                .dimensions(frame.navX(0),y,w,20).build()).active=page>0;
        addDrawableChild(ButtonWidget.builder(txt("close"),b->close())
                .dimensions(frame.navX(1),y,w,20).build());
        addDrawableChild(ButtonWidget.builder(txt("next"),b->change(1))
                .dimensions(frame.navX(2),y,w,20).build()).active=page<PAGE_COUNT-1;
    }

    private void change(int offset){
        int next=Math.clamp(page+offset,0,PAGE_COUNT-1);
        if(next==page)return;
        page=next;
        scrollLine=0;
        clearAndInit();
    }

    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}

    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        if(frame==null)frame=BrickGuideLayout.calculate(width,height);
        BrickGuideLayout.Frame f=frame;
        int x=f.x(),y=f.y(),w=f.width(),h=f.height();

        ctx.fill(0,0,width,height,0xC8101B2A);
        ctx.fill(x,y,x+w,y+h,OUTLINE);
        ctx.fill(x+2,y+2,x+w-2,y+h-2,PAPER);
        ctx.fill(x+8,y+8,x+w-8,y+31,OUTLINE);
        ctx.fill(x+8,y+30,x+w-8,y+32,ACCENT);

        // Draw unshadowed text on the light paper; Minecraft's dark text shadow
        // was visually doubling the glyphs at GUI scale 3+.
        plain(ctx,txt("title"),x+15,y+15,w-30,0xFFF6FAFD);
        plain(ctx,txt("page"+(page+1)+".title"),x+15,f.titleY(),w-88,INK);
        plain(ctx,Text.literal((page+1)+" / "+PAGE_COUNT),x+w-49,f.titleY(),40,MUTED);

        if(f.showIcons()){
            int iy=f.iconsY();
            switch(page){
                case 0 -> {icon(ctx,BrickCraft.GUIDE_BOOK,x+18,iy);icon(ctx,BrickCraft.TOOL,x+51,iy);icon(ctx,BrickCraft.LDRAW_ITEM,x+84,iy);}
                case 1 -> {icon(ctx,BrickCraft.TOOL,x+18,iy);arrow(ctx,x+47,iy+10);icon(ctx,BrickCraft.PART_ITEMS.get(BrickKind.BRICK_2X4),x+71,iy);}
                case 2 -> {icon(ctx,BrickCraft.LDRAW_ITEM,x+18,iy);arrow(ctx,x+47,iy+10);icon(ctx,BrickCraft.TOOL,x+71,iy);}
                case 3 -> {icon(ctx,BrickCraft.LDRAW_ITEM,x+18,iy);arrow(ctx,x+47,iy+10);icon(ctx,BrickCraft.PART_ITEMS.get(BrickKind.BRICK_2X4),x+71,iy);}
                case 4 -> {icon(ctx,BrickCraft.TOOL,x+18,iy);icon(ctx,BrickCraft.LDRAW_ITEM,x+51,iy);}
                case 5 -> {icon(ctx,BrickCraft.PART_ITEMS.get(BrickKind.SLOPE_1X2),x+18,iy);icon(ctx,BrickCraft.GUIDE_BOOK,x+51,iy);}
                default -> {}
            }
        }

        // Keep body text inside a dedicated content area, with no shadow and
        // no overlap with navigation. Wheel scrolling supports long locales.
        int bodyLeft=x+16,bodyRight=x+w-16;
        int bodyTop=f.bodyTop(),bodyBottom=f.bodyBottom();
        List<OrderedText> lines=textRenderer.wrapLines(txt("page"+(page+1)+".body"),Math.max(32,bodyRight-bodyLeft-10));
        int visibleLines=Math.max(1,Math.max(0,bodyBottom-bodyTop)/LINE_HEIGHT);
        maxScroll=Math.max(0,lines.size()-visibleLines);
        scrollLine=Math.clamp(scrollLine,0,maxScroll);
        ctx.fill(bodyLeft-5,bodyTop-5,bodyRight+5,bodyBottom+5,0xFFF9F7F1);
        ctx.enableScissor(bodyLeft,bodyTop,bodyRight,bodyBottom);
        for(int i=0;i<visibleLines&&scrollLine+i<lines.size();i++){
            // False = no text shadow; necessary on light backgrounds at high UI scale.
            ctx.drawText(textRenderer,lines.get(scrollLine+i),bodyLeft,
                    bodyTop+i*LINE_HEIGHT,INK,false);
        }
        ctx.disableScissor();
        if(maxScroll>0){
            // Compact visible scroll indicator, without obscuring content.
            int trackTop=bodyTop+2,trackBottom=bodyBottom-2;
            if(trackBottom>trackTop){
                int track=trackBottom-trackTop;
                int thumb=Math.max(8,track*visibleLines/Math.max(1,lines.size()));
                int offset=(track-thumb)*scrollLine/maxScroll;
                ctx.fill(bodyRight+1,trackTop,bodyRight+3,trackBottom,0xFFCED8DA);
                ctx.fill(bodyRight,trackTop+offset,bodyRight+4,trackTop+offset+thumb,ACCENT);
            }
        }
        ctx.fill(x+9,f.navigationY()-7,x+w-9,f.navigationY()-6,0xFFCED7DA);
        super.render(ctx,mx,my,delta);
    }

    private void plain(DrawContext ctx,Text value,int x,int y,int maxWidth,int color){
        if(maxWidth<=0)return;
        String content=value.getString();
        if(textRenderer.getWidth(content)>maxWidth)
            content=textRenderer.trimToWidth(content,Math.max(0,maxWidth-textRenderer.getWidth("...")))+"...";
        ctx.drawText(textRenderer,content,x,y,color,false);
    }

    private void icon(DrawContext ctx,Item item,int x,int y){
        if(item==null)return;
        ctx.fill(x-2,y-2,x+20,y+20,0xFFCFDDE3);
        ctx.fill(x-1,y-1,x+19,y+19,0xFFEAF2F4);
        ctx.drawItem(new ItemStack(item),x+1,y+1);
    }

    private void arrow(DrawContext ctx,int x,int y){
        ctx.fill(x,y,x+10,y+2,0xFF528CA2);
        ctx.fill(x+7,y-3,x+10,y+5,0xFF528CA2);
    }

    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(frame!=null && maxScroll>0 && mx>=frame.x() && mx<=frame.x()+frame.width()
                && my>=frame.bodyTop() && my<=frame.bodyBottom()){
            int direction=vertical>0?-1:vertical<0?1:0;
            if(direction!=0)scrollLine=Math.clamp(scrollLine+direction,0,maxScroll);
            return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }

    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_LEFT){change(-1);return true;}
        if(key==GLFW.GLFW_KEY_RIGHT){change(1);return true;}
        if(key==GLFW.GLFW_KEY_PAGE_UP || key==GLFW.GLFW_KEY_UP){
            scrollLine=Math.max(0,scrollLine-1);return true;
        }
        if(key==GLFW.GLFW_KEY_PAGE_DOWN || key==GLFW.GLFW_KEY_DOWN){
            scrollLine=Math.min(maxScroll,scrollLine+1);return true;
        }
        return super.keyPressed(key,scan,mods);
    }

    @Override public void close(){client.setScreen(null);}
    @Override public boolean shouldPause(){return false;}
}
