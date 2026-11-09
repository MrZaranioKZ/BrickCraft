package fr.brickcraft.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import java.util.List;

/** Accessible confirmation. Never clip warning details after localization. */
public final class BrickConfirmScreen extends Screen {
    private final Screen previous;
    private final String detail;
    private final Runnable confirm;
    public BrickConfirmScreen(Screen previous,String title,String detail,Runnable confirm){
        super(Text.literal(BrickI18n.tr(title)));
        this.previous=previous;this.detail=detail;this.confirm=confirm;
    }
    private int panelWidth(){return Math.min(500,Math.max(160,width-22));}
    private int textWidth(){return Math.max(100,panelWidth()-28);}
    private List<OrderedText> titleLines(){
        return textRenderer.wrapLines(Text.literal(BrickI18n.tr(title.getString())),textWidth());
    }
    private List<OrderedText> descriptionLines(){
        return textRenderer.wrapLines(Text.literal(BrickI18n.tr(detail)),textWidth());
    }
    private int dialogHeight(){
        int lines=Math.max(1,titleLines().size())+Math.max(1,descriptionLines().size());
        return Math.min(height-8,55+lines*12);
    }
    private int dialogTop(){return Math.max(4,(height-dialogHeight())/2);}
    @Override protected void init(){
        int w=panelWidth(),left=(width-w)/2,buttonW=Math.max(65,(w-36)/2),y=dialogTop()+dialogHeight()-32;
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.fmt("brickcraft.v252.confirm.ok")),
                b->{client.setScreen(previous);confirm.run();})
                .dimensions(left+12,y,buttonW,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.fmt("brickcraft.v252.confirm.cancel")),
                b->client.setScreen(previous))
                .dimensions(left+w-buttonW-12,y,buttonW,22).build());
    }
    @Override public void renderBackground(DrawContext ctx,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xFF0B1420);
        int w=panelWidth(),left=(width-w)/2,top=dialogTop(),bottom=top+dialogHeight();
        ctx.fill(left,top,left+w,bottom,0xFF182A3B);
        ctx.fill(left,top,left+w,top+4,0xFFE3AB66);
        int y=top+12;
        for(OrderedText line:titleLines()){
            if(y>=bottom-50)break;
            ctx.drawCenteredTextWithShadow(textRenderer,line,width/2,y,0xFFF5E9D8);y+=12;
        }
        y+=8;
        for(OrderedText line:descriptionLines()){
            if(y>=bottom-34)break;
            ctx.drawCenteredTextWithShadow(textRenderer,line,width/2,y,0xFFD9E3EF);y+=12;
        }
        super.render(ctx,mouseX,mouseY,delta);
    }
    @Override public void close(){client.setScreen(previous);}
}
