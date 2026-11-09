package fr.brickcraft.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** Adjustable LDraw mesh-detail budget; rebuild GPU buffers once when confirmed. */
public final class BrickGpuQualityScreen extends Screen {
    private final Screen previous;
    private int chosen;
    public BrickGpuQualityScreen(Screen previous){
        super(Text.literal(BrickI18n.tr("BrickCraft - Qualite geometrie")));
        this.previous=previous;chosen=PreciseDisplayRenderer.gpuQualityPercent();
    }
    @Override protected void init(){
        int w=Math.min(350,width-30),x=(width-w)/2,y=Math.max(55,height/2-75);
        addDrawableChild(new SliderWidget(x,y,w,20,Text.empty(),chosen/100.0){
            @Override protected void updateMessage(){
                chosen=(int)Math.round(value*100);
                setMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.quality.triangles",chosen)));
            }
            @Override protected void applyValue(){chosen=(int)Math.round(value*100);}
        });
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Appliquer et reconstruire les lots GPU")),b->{
            try{
                BrickGpuQualityConfig.save(FabricLoader.getInstance().getConfigDir(),chosen);
                PreciseDisplayRenderer.changeGpuQuality(chosen);
                close();
            }catch(java.io.IOException ex){
                if(client!=null&&client.player!=null)client.player.sendMessage(
                        Text.literal(BrickI18n.tr("BrickCraft : sauvegarde qualite impossible : "+ex.getMessage())),false);
            }
        }).dimensions(x,y+32,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Recalculer l'eclairage des LEGO")),b->{
            PreciseDisplayRenderer.refreshWorldLighting();
            if(client!=null && client.player!=null)client.player.sendMessage(
                    Text.literal(BrickI18n.tr("BrickCraft : eclairage actualise progressivement.")),true);
            close();
        }).dimensions(x,y+58,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Capacites / grands sets")),b->client.setScreen(new BrickCapacityScreen(this)))
                .dimensions(x,y+85,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Annuler")),b->close())
                .dimensions(x,y+111,w,20).build());
    }
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext d,int mx,int my,float delta){
        d.fill(0,0,width,height,0xF00B111A);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("BRICKCRAFT / DETAIL 3D"),width/2,
                Math.max(15,height/2-115),0xFF8CE5E6);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("0% = maillage tres simplifie | 100% = triangles LDraw originaux"),
                width/2,Math.max(35,height/2-95),0xFFE7CF9E);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Les tenons retrouvent leurs courbes a 100% (cout GPU plus eleve)."),
                width/2,Math.max(48,height/2-80),0xFFBDD4E5);
        super.render(d,mx,my,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(previous);}
}
