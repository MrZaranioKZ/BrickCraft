package fr.brickcraft.client;

import fr.brickcraft.net.PreciseManagePayload;
import fr.brickcraft.net.PrecisePhysicsPayload;
import fr.brickcraft.net.PreciseManageStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;

/** Dedicated world-projection manager: it never deletes a file on disk. */
public final class PreciseWorldProjectsScreen extends Screen {
    private final Screen previous;
    private List<PreciseManageStatusPayload.Entry> entries=List.of();
    private int selected=-1,page=0;
    private String status="Recherche des projections chargees...";
    private static final int MAX_PAGE_ROWS=6;
    /** Keep the projection list above the action panel at larger GUI scales. */
    private int pageRows(){return Math.clamp((height-198)/29,1,MAX_PAGE_ROWS);}
    private boolean requested=false;
    public PreciseWorldProjectsScreen(Screen previous){
        super(Text.literal(BrickI18n.tr("BrickCraft - Projections du monde")));this.previous=previous;
    }
    private void button(int x,int y,int w,int h,String text,Runnable callback){
        button(x,y,w,h,text,true,callback);
    }
    private void button(int x,int y,int w,int h,String text,boolean enabled,Runnable callback){
        ButtonWidget widget=ButtonWidget.builder(Text.literal(BrickI18n.tr(text)),b->callback.run())
                .dimensions(x,y,w,h).build();
        widget.active=enabled;
        addDrawableChild(widget);
    }
    @Override protected void init(){
        rebuild();
        if(!requested){requested=true;refresh();}
    }
    private void rebuild(){
        clearChildren();
        int x=Math.max(10,width/2-235),w=Math.min(470,width-20);
        int listY=68,rows=pageRows();
        page=Math.clamp(page,0,Math.max(0,(entries.size()-1)/rows));
        for(int row=0;row<rows;row++){
            final int which=page*rows+row;
            if(which>=entries.size())break;
            var p=entries.get(which);
            button(x,listY+row*29,w,23,BrickI18n.fmt("brickcraft.v252.projections.item",(which==selected?"✓ ":""),which+1,p.pieces(),p.anchor().toShortString()),
                    ()->{selected=which;rebuild();});
        }
        int navY=Math.min(Math.max(listY+rows*29+6,height-140),height-116);
        boolean hasSelection=selected>=0 && selected<entries.size();
        int lastPage=Math.max(0,(entries.size()-1)/rows);
        button(x,navY,(w-8)/2,20,"< Page",page>0,()->{page=Math.max(0,page-1);rebuild();});
        button(x+(w+8)/2,navY,(w-8)/2,20,"Page >",page<lastPage,()->{
            page=Math.min(lastPage,page+1);rebuild();});
        button(x,navY+26,(w-8)/2,22,"Physique ON",hasSelection,()->askPhysics(true));
        button(x+(w+8)/2,navY+26,(w-8)/2,22,"Physique OFF",hasSelection,()->askPhysics(false));
        button(x,navY+54,w,20,"Retirer la projection selectionnee",hasSelection,this::askRemove);
        int third=(w-16)/3;
        button(x,navY+80,third,20,"Taille/hitbox (U)",
                ()->client.setScreen(new BrickPlayerScaleScreen(this)));
        button(x+third+8,navY+80,third,20,"Actualiser",this::refresh);
        button(x+2*(third+8),navY+80,third,20,"Retour import",()->client.setScreen(previous));
    }
    private void refresh(){
        if(client.player==null||client.world==null){status="Ouvre un monde pour gerer ses projections.";return;}
        status="Recherche des ancres dans les chunks charges (192 blocs)...";
        ClientPlayNetworking.send(PreciseManagePayload.list());
    }
    private void askPhysics(boolean enable){
        if(selected<0||selected>=entries.size()){status="Selectionne une projection d'abord.";return;}
        var p=entries.get(selected);
        client.setScreen(new BrickConfirmScreen(this,BrickI18n.fmt(enable?"brickcraft.v252.projections.physics_on.title":"brickcraft.v252.projections.physics_off.title"),
                BrickI18n.fmt(enable?"brickcraft.v252.projections.physics_on.detail":"brickcraft.v252.projections.physics_off.detail"),()->{status=BrickI18n.fmt("brickcraft.v252.projections.requested");
                    ClientPlayNetworking.send(new PrecisePhysicsPayload(p.anchor(),enable));}));
    }
    private void askRemove(){
        if(selected<0||selected>=entries.size()){status="Selectionne une projection dans la liste.";return;}
        var p=entries.get(selected);
        client.setScreen(new BrickConfirmScreen(this,BrickI18n.fmt("brickcraft.v252.projections.remove.title"),
                BrickI18n.fmt("brickcraft.v252.projections.remove.detail",p.pieces(),p.anchor().toShortString()),
                ()->{status=BrickI18n.fmt("brickcraft.v252.projections.removal_requested");ClientPlayNetworking.send(PreciseManagePayload.remove(p.anchor()));}));
    }
    public void receive(PreciseManageStatusPayload response){
        status=response.message();
        if(response.event()==PreciseManageStatusPayload.LIST){
            entries=response.projects();selected=-1;page=0;rebuild();
        }else if(response.event()==PreciseManageStatusPayload.REMOVED){
            entries=List.of();selected=-1;page=0;rebuild();refresh();
        }
    }
    @Override public void renderBackground(DrawContext ctx,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xFF0A1420);
        int x=Math.max(10,width/2-235),w=Math.min(470,width-20);
        ctx.fill(x-5,34,x+w+5,height-19,0xFF142535);
        ctx.fill(x-5,34,x+w+5,38,0xFF55CFD8);
        String heading=BrickI18n.tr("BRICKCRAFT / PROJECTIONS");
        ctx.drawTextWithShadow(textRenderer,heading,(width-textRenderer.getWidth(heading))/2,14,0xFFE7F8FF);
        String count=BrickI18n.fmt("brickcraft.v252.projections.count",entries.size(),page+1);
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(count,w-8),x+4,45,0xFFA5CBD9);
        if(entries.isEmpty()){
            String empty=BrickI18n.tr("Aucune projection chargee dans les 192 blocs autour de toi.");
            ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(empty,w-15),
                    x+8,115,0xFFE7C992);
        }
        // Translate before measuring. Truncating first used to break the translation key.
        String message=BrickI18n.tr(status);
        message=textRenderer.trimToWidth(message,Math.max(0,width-24));
        ctx.fill(0,height-18,width,height,0xFF102132);
        ctx.drawTextWithShadow(textRenderer,message,10,height-14,0xFFE5CE94);
        super.render(ctx,mouseX,mouseY,delta);
    }
    @Override public void close(){client.setScreen(previous);}
}
