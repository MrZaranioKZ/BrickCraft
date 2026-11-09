package fr.brickcraft.client;

import fr.brickcraft.client.BrickGuideController.Mode;
import fr.brickcraft.world.PreciseDisplayEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Compact overlay UI: keep the model visible in the live world behind the controls. */
public final class BrickGuideScreen extends Screen {
    private int projectIndex=0,materialPage=0;
    private boolean firstInit=true;
    private String message="";
    private int rows(){return Math.clamp((height-365)/14,1,8);}
    public BrickGuideScreen(){super(Text.literal(BrickI18n.tr("BrickCraft - Notices interactives")));}
    private void button(int x,int y,int w,int h,String name,Runnable action){
        button(x,y,w,h,name,true,action);
    }
    private void button(int x,int y,int w,int h,String name,boolean enabled,Runnable action){
        ButtonWidget widget=ButtonWidget.builder(Text.literal(BrickI18n.tr(name)),b->action.run())
                .dimensions(x,y,w,h).build();
        widget.active=enabled;
        addDrawableChild(widget);
    }
    @Override protected void init(){
        clearChildren();
        var visible=BrickGuideController.nearby(client);
        int panel=Math.min(390,width-24),x=12;
        if(firstInit){
            firstInit=false;
            if(BrickGuideController.active()){
                for(int i=0;i<visible.size();i++)
                    if(visible.get(i).getPos().equals(BrickGuideController.anchor())){
                        projectIndex=i;break;
                    }
            }
        }
        if(!visible.isEmpty()&&!BrickGuideController.active()){
            projectIndex=Math.clamp(projectIndex,0,visible.size()-1);
            BrickGuideController.attach(client,visible.get(projectIndex));
        }
        boolean canNavigate=visible.size()>1;
        boolean hasGuide=BrickGuideController.active();
        int y=48;
        button(x,y,38,20,"<",canNavigate,()->{cycle(-1);});
        button(x+42,y,Math.max(60,panel-92),20,visible.isEmpty()?BrickI18n.tr("Projet indisponible"):
             BrickI18n.fmt("brickcraft.v241.guide.project",projectIndex+1,visible.size()),canNavigate,()->cycle(1));
        button(x+panel-44,y,44,20,">",canNavigate,()->cycle(1));
        y+=31;
        int col=(panel-8)/2;
        button(x,y,col,22,"Couches",hasGuide,()->switchMode(Mode.LAYERS));
        button(x+col+8,y,col,22,"Etapes Studio",hasGuide,()->switchMode(Mode.SOURCE));
        y+=26;
        button(x,y,col,22,"Lots de 20",hasGuide,()->switchMode(Mode.LOTS));
        button(x+col+8,y,col,22,"Piece / piece",hasGuide,()->switchMode(Mode.PIECES));
        y+=38;
        button(x,y,56,22,"|<",hasGuide,()->jump(0));
        button(x+62,y,56,22,"-1",hasGuide,()->jump(BrickGuideController.step()-1));
        button(x+124,y,Math.max(50,panel-248),22,"+1",hasGuide,()->jump(BrickGuideController.step()+1));
        button(x+panel-118,y,56,22,"+10",hasGuide,()->jump(BrickGuideController.step()+10));
        button(x+panel-56,y,56,22,">|",hasGuide,()->jump(BrickGuideController.total()));
        y+=36;
        button(x,y,col,21,"Verifier pieces",hasGuide,()->{
            message=BrickGuideController.verifyRealPlacedParts(client);materialPage=0;});
        button(x+col+8,y,col,21,"Confirmer (manuel)",hasGuide,()->{
            message=BrickGuideController.confirmManually(client);materialPage=0;});
        y+=27;
        button(x,y,panel-8,19,BrickI18n.fmt("brickcraft.v241.guide.target",BrickGuideController.targetOverlay()?"ON":"OFF"),hasGuide,()->{
            BrickGuideController.toggleTargetOverlay();init();});
        y+=25;
        button(x,y,col,21,"< Materiaux",hasGuide,()->{materialPage=Math.max(0,materialPage-1);});
        button(x+col+8,y,col,21,"Materiaux >",hasGuide,()->{materialPage++;});
        int lastY=Math.max(height-38,y+146);
        button(x,lastY,col,22,"Quitter le menu",()->close());
        button(x+col+8,lastY,col,22,"Retirer la notice",hasGuide,()->{
            BrickGuideController.hide();close();
        });
    }
    private void cycle(int delta){
        var projects=BrickGuideController.nearby(client);
        if(projects.isEmpty())return;
        projectIndex=Math.floorMod(projectIndex+delta,projects.size());
        BrickGuideController.attach(client,projects.get(projectIndex));
        materialPage=0;init();
    }
    private void jump(int step){BrickGuideController.jump(step);materialPage=0;}
    private void switchMode(Mode next){
        if(next==Mode.SOURCE&&!BrickGuideController.sourceAvailable()){
            message="Place le .io original dans config/brickcraft/imports/";
            return;
        }
        BrickGuideController.setMode(next);materialPage=0;message="";
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_PAGE_UP){jump(BrickGuideController.step()-1);return true;}
        if(key==GLFW.GLFW_KEY_PAGE_DOWN){jump(BrickGuideController.step()+1);return true;}
        if(key==GLFW.GLFW_KEY_HOME){jump(0);return true;}
        if(key==GLFW.GLFW_KEY_END){jump(BrickGuideController.total());return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        int panel=Math.min(390,width-24),x=12;
        draw.fill(x-7,17,x+panel+7,height-9,0xE9112635);
        draw.fill(x-7,17,x+panel+7,20,0xFF46CBD4);
        super.render(draw,mouseX,mouseY,delta);
        draw.drawTextWithShadow(textRenderer,BrickI18n.tr("BRICKCRAFT / NOTICE"),x+3,29,0xFFBDEFF6);
        int y=259;
        if(!BrickGuideController.active()){
            draw.drawTextWithShadow(textRenderer,BrickI18n.tr("Aucun projet visible : rapproche-toi du LEGO."),x+3,y,0xFFF0BF8F);
            return;
        }
        String[] captions={
            BrickI18n.fmt("brickcraft.v241.guide.mode",BrickI18n.tr(switch(BrickGuideController.mode()){
                case SOURCE->"Etapes d'origine Studio";case LAYERS->"Couches calculees";
                case LOTS->"Lots automatiques";case PIECES->"Piece par piece";})),
            BrickI18n.fmt("brickcraft.v241.guide.progress",BrickGuideController.step(),BrickGuideController.total()),
            BrickI18n.fmt("brickcraft.v241.guide.visible",BrickGuideController.shownCount(),BrickGuideController.partCount()),
            BrickGuideController.stepTitle(),
        };
        int maxTextY=height-56;
        for(String s:captions){
            if(y+12>maxTextY)break;
            draw.drawTextWithShadow(textRenderer,cut(s,panel-8),x+3,y,0xFFD9F3FA);y+=14;
        }
        int left=x+3;
        if(y+16<maxTextY)draw.drawTextWithShadow(textRenderer,BrickI18n.tr("--- Pieces pour cette etape ---"),left,y+4,0xFFF1D599);
        y+=23;
        List<BrickGuideController.Material> materials=BrickGuideController.materials();
        int maxPage=Math.max(0,(materials.size()-1)/rows());
        materialPage=Math.clamp(materialPage,0,maxPage);
        int from=materialPage*rows();
        for(int i=from;i<Math.min(materials.size(),from+rows());i++){
            var m=materials.get(i);
            int rgb=fr.brickcraft.ldraw.LDrawLibrary.argb(m.color(),0xAAAAAA)|0xFF000000;
            if(y+12>=maxTextY)break;
            draw.fill(left,y,left+9,y+9,rgb);
            String line="x"+m.quantity()+"  "+m.part()+"  |  "+BrickGuideController.colorName(m.color());
            draw.drawTextWithShadow(textRenderer,cut(line,panel-25),left+14,y,0xFFECF5F8);y+=14;
        }
        y+=5;
        if(y+12<maxTextY)draw.drawTextWithShadow(textRenderer,BrickI18n.tr(BrickI18n.fmt("brickcraft.v241.guide.materials",materialPage+1,maxPage+1)),left,y,0xFFB1C9D4);
        y+=15;
        String state=BrickGuideController.sourceMessage();
        if(y<height-54)draw.drawTextWithShadow(textRenderer,cut(state,panel-8),left,y,0xFF85DED2);
        if(!message.isEmpty()&&y+14<height-50)
            draw.drawTextWithShadow(textRenderer,cut(message,panel-8),left,y+14,0xFFFFC184);
    }
    private String cut(String s,int available){
        s=BrickI18n.tr(s);
        if(textRenderer.getWidth(s)<=available)return s;
        while(s.length()>3 && textRenderer.getWidth(s+"...")>available)s=s.substring(0,s.length()-1);
        return s+"...";
    }
    @Override public void close(){client.setScreen(null);}
    @Override public boolean shouldPause(){return false;}
}
