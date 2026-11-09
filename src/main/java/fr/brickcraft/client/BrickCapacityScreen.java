package fr.brickcraft.client;

import fr.brickcraft.core.BrickCapacityConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** Per-installation settings (remote servers use their own limits). */
public final class BrickCapacityScreen extends Screen {
    private final Screen previous;
    private int partIndex,cellIndex;
    private String notice="";
    public BrickCapacityScreen(Screen previous){
        super(Text.literal(BrickI18n.tr("BrickCraft - Capacites des grands sets")));this.previous=previous;
        partIndex=find(BrickCapacityConfig.PART_STEPS,BrickCapacityConfig.partsSetting());
        cellIndex=find(BrickCapacityConfig.CELL_STEPS,BrickCapacityConfig.cellsSetting());
    }
    private static int find(int[] arr,int v){for(int i=0;i<arr.length;i++)if(arr[i]==v)return i;return Math.max(0,arr.length-3);}
    private static String name(int n){return n==0?BrickI18n.tr("SANS LIMITE CHOISIE"):String.format(java.util.Locale.ROOT,"%,d",n);}
    @Override protected void init(){
        int w=Math.min(388,width-24),x=(width-w)/2,y=Math.max(48,height/2-98);
        addDrawableChild(new SliderWidget(x,y+18,w,20,Text.empty(),partIndex/(double)(BrickCapacityConfig.PART_STEPS.length-1)){
            @Override protected void updateMessage(){partIndex=(int)Math.round(value*(BrickCapacityConfig.PART_STEPS.length-1));
                setMessage(Text.literal(BrickI18n.tr(BrickI18n.fmt("brickcraft.v241.capacity.parts",name(BrickCapacityConfig.PART_STEPS[partIndex])))));}
            @Override protected void applyValue(){partIndex=(int)Math.round(value*(BrickCapacityConfig.PART_STEPS.length-1));updateMessage();}
        });
        addDrawableChild(new SliderWidget(x,y+70,w,20,Text.empty(),cellIndex/(double)(BrickCapacityConfig.CELL_STEPS.length-1)){
            @Override protected void updateMessage(){cellIndex=(int)Math.round(value*(BrickCapacityConfig.CELL_STEPS.length-1));
                setMessage(Text.literal(BrickI18n.tr(BrickI18n.fmt("brickcraft.v241.capacity.cells",name(BrickCapacityConfig.CELL_STEPS[cellIndex])))));}
            @Override protected void applyValue(){cellIndex=(int)Math.round(value*(BrickCapacityConfig.CELL_STEPS.length-1));updateMessage();}
        });
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Enregistrer")),b->{
            try{BrickCapacityConfig.save(BrickCapacityConfig.PART_STEPS[partIndex],BrickCapacityConfig.CELL_STEPS[cellIndex]);close();}
            catch(java.io.IOException ex){notice="Erreur : "+ex.getMessage();}
        }).dimensions(x,y+117,(w-6)/2,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Annuler")),b->close()).dimensions(x+(w+6)/2,y+117,(w-6)/2,20).build());
    }
    @Override public void renderBackground(DrawContext d,int mx,int my,float dt){}
    @Override public void render(DrawContext d,int mx,int my,float dt){
        d.fill(0,0,width,height,0xFF0A1420);
        int y=Math.max(48,height/2-98);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("BRICKCRAFT / CAPACITES"),width/2,y-23,0xFF9CE7DD);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Nombre de pieces par projection"),width/2,y,0xFFE3EFFA);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Cellules de collisions precises (1 bloc = 8x8x8)"),width/2,y+52,0xFFE3EFFA);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Droite : sans plafond choisi, mais securites techniques conservees."),width/2,y+97,0xFFFFCC8A);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Limites techniques : 250 000 pieces / 400 000 cellules / 20 M triangles"),width/2,y+146,0xFFB4C9D9);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Attention : beaucoup de collisions peut ralentir le serveur et grossir la sauvegarde."),width/2,y+159,0xFFFFB7A3);
        if(!notice.isEmpty())d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr(notice),width/2,y+174,0xFFFF8D8D);
        super.render(d,mx,my,dt);
    }
    @Override public void close(){client.setScreen(previous);}
}
