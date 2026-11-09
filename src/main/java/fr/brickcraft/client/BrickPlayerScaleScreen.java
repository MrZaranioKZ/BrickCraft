package fr.brickcraft.client;

import fr.brickcraft.net.PlayerScalePayload;
import fr.brickcraft.world.PlayerScaleManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.text.Text;
import java.util.Locale;

/** Accessible with U or from the world-project menu. Uses a logarithmic scale for small LEGO explorers. */
public final class BrickPlayerScaleScreen extends Screen {
    private final Screen previous;
    private double selected;
    private SliderWidget slider;
    private static final double MIN=PlayerScaleManager.MIN,MAX=PlayerScaleManager.MAX;
    public BrickPlayerScaleScreen(Screen previous){
        super(Text.literal(BrickI18n.tr("BrickCraft - Taille et hitbox du joueur")));this.previous=previous;
    }
    private static double fromSlider(double v){return MIN*Math.pow(MAX/MIN,v);}
    private static double toSlider(double value){return Math.clamp(Math.log(value/MIN)/Math.log(MAX/MIN),0,1);}
    private static String format(double d){return String.format(Locale.FRANCE,"%.2f",d);}
    @Override protected void init(){
        if(slider==null){
            double actual=client!=null&&client.player!=null?
                client.player.getAttributeValue(EntityAttributes.GENERIC_SCALE):1.0;
            selected=Math.clamp(actual,MIN,MAX);
        }
        int w=Math.min(370,width-30),x=(width-w)/2,y=Math.max(55,height/2-67);
        slider=addDrawableChild(new SliderWidget(x,y,w,20,Text.empty(),toSlider(selected)){
            @Override protected void updateMessage(){selected=fromSlider(value);setMessage(Text.literal(BrickI18n.tr("Taille x"+format(selected)+" | hitbox " + format(.6*selected)+" x "+format(1.8*selected)+" blocs")));}
            @Override protected void applyValue(){selected=fromSlider(value);}
        });
        final double[] presets={.125,.25,.5,1,2,4};
        int gap=4,presetWidth=(w-gap*(presets.length-1))/presets.length;
        for(int i=0;i<presets.length;i++){
            double scale=presets[i],labelScale=scale;
            addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("x"+format(labelScale))),b->{
                selected=scale;clearAndInit();
            }).dimensions(x+i*(presetWidth+gap),y+28,presetWidth,20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Appliquer : taille + hitbox")),b->{
            if(client!=null&&client.player!=null){
                ClientPlayNetworking.send(new PlayerScalePayload((float)selected));
                close();
            }
        }).dimensions(x,y+60,w,21).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Retour")),b->close())
                .dimensions(x,y+87,w,20).build());
    }
    @Override public void renderBackground(DrawContext d,int mx,int my,float delta){}
    @Override public void render(DrawContext d,int mx,int my,float delta){
        d.fill(0,0,width,height,0xF00C1724);
        int middle=width/2,top=Math.max(16,height/2-121);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("BRICKCRAFT / EXPLORATION LEGO"),middle,top,0xFF79DFE9);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Rejoins la taille d'une figurine ou domine les constructions"),middle,top+18,0xFFDEE9F4);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("x1 = joueur normal | x0,125 = minuscule | x4 = geant"),middle,top+33,0xFFBDCFDC);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("La hauteur de la camera, la hitbox et les collisions sont synchronisees."),
                middle,Math.min(height-26,height/2+63),0xFFB1C6D3);
        super.render(d,mx,my,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(previous);}
}
