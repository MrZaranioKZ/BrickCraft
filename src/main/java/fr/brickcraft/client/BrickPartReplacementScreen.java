package fr.brickcraft.client;

import fr.brickcraft.importer.PartReplacementMatcher;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Manual, confirmed replacement of ONE missing reference in the loaded LDraw project. */
public final class BrickPartReplacementScreen extends Screen {
    private final BrickMissingPartsScreen previous;
    private final String sourceId;
    private final String sourceName;
    private final int occurrences;
    private final List<PartReplacementMatcher.Candidate> suggestions;
    private TextFieldWidget targetField;
    private int page;
    private String chosen="";
    private String status=BrickI18n.fmt("brickcraft.v252.replace.initial");

    public BrickPartReplacementScreen(BrickMissingPartsScreen previous,String sourceId,String sourceName,int occurrences){
        super(Text.literal(BrickI18n.tr("BrickCraft - Remplacer une piece")));
        this.previous=previous;
        this.sourceId=PartReplacementMatcher.normalize(sourceId);
        this.sourceName=sourceName;
        this.occurrences=occurrences;
        // Installed library only; never propose a reference downloaded on speculation.
        var all=LDrawLibrary.search("",150_000).stream()
                .map(p->new PartReplacementMatcher.Available(p.id(),p.name())).toList();
        this.suggestions=PartReplacementMatcher.suggest(this.sourceId,all,24).stream()
                .filter(p->LDrawLibrary.originalBounds(p.id())!=null).toList();
    }
    private int rows(){return Math.max(1,Math.min(7,(height-184)/26));}
    private void choose(String id){
        chosen=id;
        if(targetField!=null)targetField.setText(id);
        status=BrickI18n.fmt("brickcraft.v252.replace.target",id);
    }
    private void apply(){
        String target=PartReplacementMatcher.normalize(targetField==null?chosen:targetField.getText());
        if(target.equals(sourceId)){status=BrickI18n.fmt("brickcraft.v252.replace.same");return;}
        if(!target.matches("[a-z0-9_-]{2,60}") || !LDrawLibrary.contains(target)){
            status=BrickI18n.fmt("brickcraft.v252.replace.not_installed",target);return;
        }
        if(LDrawLibrary.originalBounds(target)==null){
            status=BrickI18n.fmt("brickcraft.v252.replace.no_geometry",target);return;
        }
        // Confirmation deliberately mandatory: close IDs are NOT evidence of equivalent geometry.
        client.setScreen(new BrickConfirmScreen(this,BrickI18n.fmt("brickcraft.v252.replace.title",sourceId,target),
                BrickI18n.fmt("brickcraft.v252.replace.detail",occurrences),()->{
                    int count=previous.replaceMissing(sourceId,target);
                    if(count>0){client.setScreen(previous);}
                    else status=BrickI18n.fmt("brickcraft.v252.replace.done_none");
                }));
    }
    private void add(int x,int y,int w,int h,String label,Runnable action){
        if(w<24||y<0||y+h>height-23)return;
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr(label)),btn->action.run()).dimensions(x,y,w,h).build());
    }
    @Override protected void init(){
        int w=Math.min(610,width-20),x=(width-w)/2;
        int availableRows=rows();
        int maxPage=Math.max(0,(suggestions.size()-1)/availableRows);
        page=Math.min(page,maxPage);
        targetField=new TextFieldWidget(textRenderer,x+92,49,Math.max(60,w-224),20,Text.literal(BrickI18n.tr("Reference cible (.dat)")));
        targetField.setMaxLength(60);
        targetField.setText(chosen);
        targetField.setChangedListener(v->chosen=v);
        addDrawableChild(targetField);
        add(x+w-122,49,122,20,"Remplacer",this::apply);
        for(int k=0;k<availableRows;k++){
            int i=page*availableRows+k;if(i>=suggestions.size())break;
            var item=suggestions.get(i);
            int y=87+k*26;
            add(x+w-95,y,95,20,"Choisir",()->choose(item.id()));
        }
        int y=height-87;
        add(x,y,90,20,"< Page",()->{if(page>0){page--;clearAndInit();}});
        add(x+94,y,90,20,"Page >",()->{if((page+1)*rows()<suggestions.size()){page++;clearAndInit();}});
        add(x,y+28,w,22,BrickI18n.fmt("brickcraft.v252.replace.back"),this::close);
    }
    private String cut(String s,int max){
        if(max<8)return "";
        while(textRenderer.getWidth(s)>max&&s.length()>5)s=s.substring(0,s.length()-4)+"...";
        return s;
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float dt){}
    @Override public void render(DrawContext ctx,int mx,int my,float dt){
        ctx.fill(0,0,width,height,0xFF0A1420);
        int w=Math.min(610,width-20),x=(width-w)/2;
        ctx.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("BRICKCRAFT / EQUIVALENTS LDRAW"),width/2,10,0xFF9DE5E5);
        String visibleName=sourceName.startsWith("Description indisponible")?BrickI18n.fmt("brickcraft.v252.missing.description"):sourceName;
        ctx.drawTextWithShadow(textRenderer,cut(sourceId+"  x"+occurrences+" | "+visibleName,w-6),x,29,0xFFFFD19D);
        ctx.drawTextWithShadow(textRenderer,BrickI18n.tr("Cible :"),x,55,0xFFD5E7F2);
        for(int k=0;k<rows();k++){
            int i=page*rows()+k;if(i>=suggestions.size())break;
            var item=suggestions.get(i);int yy=90+k*26;
            ctx.fill(x,yy-5,x+w,yy+20,i%2==0?0xFF162737:0xFF101E2C);
            ctx.drawTextWithShadow(textRenderer,cut(BrickI18n.tr(item.id()+" | "+item.name()+" ("+item.reason()+")"),w-106),x+5,yy,0xFFD4E9EF);
        }
        if(suggestions.isEmpty())ctx.drawTextWithShadow(textRenderer,BrickI18n.fmt("brickcraft.v252.replace.not_found"),x,98,0xFFE3BD88);
        ctx.drawTextWithShadow(textRenderer,cut(BrickI18n.fmt("brickcraft.v252.replace.suggest",suggestions.size()),w),x,72,0xFF93B1C7);
        ctx.drawTextWithShadow(textRenderer,cut(BrickI18n.tr(status),width-18),10,height-15,0xFFFFD19D);
        super.render(ctx,mx,my,dt);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ENTER){apply();return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public void close(){client.setScreen(previous);}
}
