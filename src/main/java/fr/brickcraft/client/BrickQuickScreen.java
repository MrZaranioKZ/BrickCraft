package fr.brickcraft.client;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.core.BrickCatalog;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPalette;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/** N palette: starter parts + thousands of official LDraw parts (loaded from local ZIP). */
public final class BrickQuickScreen extends Screen {
    private String query="";
    private TextFieldWidget search, colorSearch;
    private String colorQuery="", colorFamily="Tout";
    private static final String[] FAMILIES={"Tout","Opaque","Transparent","Métal","Spécial"};
    private void cycleFamily(){
        int i=0;for(int j=0;j<FAMILIES.length;j++)if(FAMILIES[j].equals(colorFamily))i=j;
        colorFamily=FAMILIES[(i+1)%FAMILIES.length];colorPage=0;updateColors();
    }
    private void updateColors(){colors=LDrawLibrary.searchColors(colorQuery,colorFamily);colorPage=0;}
    private int page=0,colorPage=0,colCount,rows,perPage,leftX,leftW,rightX,rightW,topY,cardW;
    private boolean ldraw=LDrawLibrary.available();
    private List<BrickKind> legacy=List.of();
    private List<LDrawLibrary.PartInfo> imported=List.of();
    private List<LDrawLibrary.Colour> colors=List.of();
    public BrickQuickScreen(){super(Text.literal(BrickI18n.tr("BrickCraft - catalogue LDraw")));}
    private int matchCount(){return ldraw?imported.size():legacy.size();}
    private void updateMatches(){
        legacy=BrickCatalog.search(query);
        imported=LDrawLibrary.search(query,500);
        page=0;
    }
    @Override protected void init(){
        leftX=12;rightW=Math.min(196,Math.max(128,width/3));rightX=width-rightW-12;
        leftW=Math.max(90,rightX-leftX-12);topY=87;
        colCount=leftW>=340?3:2;
        rows=Math.max(2,Math.min(8,(height-161)/30));perPage=colCount*rows;
        cardW=(leftW-4*(colCount-1))/colCount;
        updateColors();updateMatches();
        search=new TextFieldWidget(textRenderer,leftX,33,leftW,20,Text.literal(BrickI18n.tr("Recherche : nom, dimensions ou ID LDraw")));
        search.setMaxLength(60);search.setText(query);
        search.setChangedListener(value->{
            query=value;updateMatches();
            if(!ldraw){
                BrickCatalog.Entry element=BrickCatalog.findElement(value);
                if(element!=null){BrickSelection.kind=element.kind();BrickSelection.partId="";BrickSelection.setRgb(element.rgb());}
            }
        });
        addDrawableChild(search);
        colorSearch=new TextFieldWidget(textRenderer,rightX,33,rightW,19,Text.literal(BrickI18n.tr("Couleur : nom ou code")));
        colorSearch.setMaxLength(40);colorSearch.setText(colorQuery);
        colorSearch.setChangedListener(v->{colorQuery=v;updateColors();});
        addDrawableChild(colorSearch);
        button(rightX,57,rightW,19,BrickI18n.fmt("brickcraft.v241.colors.finish",BrickI18n.tr(colorFamily)),()->{
            cycleFamily();if(client!=null)clearAndInit();
        });
        button(leftX,57,(leftW-4)/2,19,"Classiques",()->{ldraw=false;page=0;});
        button(leftX+(leftW-4)/2+4,57,(leftW-4)/2,19,"LDraw",()->{ldraw=true;page=0;});
        int bottom=Math.min(height-46,topY+rows*30+14);
        int bw=Math.max(42,(leftW-5)/2);
        button(leftX,bottom,bw,20,"< Page",()->page=Math.max(0,page-1));
        button(leftX+bw+5,bottom,bw,20,"Page >",()->page=Math.min(Math.max(0,(matchCount()-1)/perPage),page+1));
        button(rightX,topY+138,(rightW-4)/2,19,"< Couleurs",()->colorPage=Math.max(0,colorPage-1));
        button(rightX+(rightW-4)/2+4,topY+138,(rightW-4)/2,19,"Couleurs >",()->colorPage++);
        int y=topY+170;
        button(rightX,y,rightW/3-3,19,"Y -",()->BrickSelection.rotate(-1));
        button(rightX+rightW/3,y,rightW/3-3,19,"Y +",()->BrickSelection.rotate(1));
        button(rightX+rightW*2/3,y,rightW/3-3,19,"Pas",BrickSelection::cycleStep);
        button(rightX,y+24,rightW/2-2,19,"X +",()->BrickSelection.rotatePitch(1));
        button(rightX+rightW/2+2,y+24,rightW/2-2,19,"Z +",()->BrickSelection.rotateRoll(1));
        button(rightX,y+48,rightW,19,"Reset rotation",BrickSelection::resetRotation);
        button(rightX,y+76,rightW,19,"Retour en jeu",this::close);
    }
    private void button(int x,int y,int w,int h,String label,Runnable action){
        if(y+h>=height-12 || w<18)return;
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr(label)),b->action.run()).dimensions(x,y,w,h).build());
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        ctx.fill(0,0,width,height,0xE9091420);
        ctx.fill(leftX-5,topY-62,rightX-6,height-38,0xFF152634);
        ctx.fill(rightX-5,topY-62,width-8,height-38,0xFF152634);
        ctx.drawTextWithShadow(textRenderer,BrickI18n.tr("BRICKCRAFT | Catalogue de pieces"),12,10,0xFFE6F3FF);
        String label=ldraw?BrickI18n.fmt("brickcraft.v241.quick.ldraw",LDrawLibrary.size(),imported.size())
                :BrickI18n.fmt("brickcraft.v241.quick.classic",legacy.size());
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(label,leftW),leftX,topY-7,0xFF8BCBE4);
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(BrickI18n.fmt("brickcraft.v241.quick.colors",colors.size()),rightW),rightX,topY-7,0xFF8BCBE4);
        int count=matchCount();page=Math.min(page,Math.max(0,(count-1)/perPage));
        for(int i=page*perPage;i<Math.min(count,(page+1)*perPage);i++){
            int slot=i-page*perPage;
            int x=leftX+(slot%colCount)*(cardW+4),y=topY+12+(slot/colCount)*30;
            boolean selected;
            String name;
            if(ldraw){
                LDrawLibrary.PartInfo part=imported.get(i);
                selected=BrickSelection.kind==BrickKind.LDRAW_PART&&part.id().equals(BrickSelection.partId);
                name=part.id()+" "+part.name();
            }else{
                BrickKind k=legacy.get(i);selected=k==BrickSelection.kind;
                name=k.title.replace("Brique ","B ").replace("Plaque ","P ").replace("Tuile ","T ")
                      .replace("Pente ","S ").replace('×','x');
            }
            ctx.fill(x-1,y-1,x+cardW+1,y+27,selected?0xFF70DDCE:0xFF345366);
            ctx.fill(x,y,x+cardW,y+26,0xFF344556);
            int tx=x+4;
            if(!ldraw){ctx.drawItem(new ItemStack(BrickCraft.PART_ITEMS.get(legacy.get(i))),x+3,y+5);tx=x+23;}
            ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(BrickI18n.tr(name),cardW-(tx-x)-4),tx,y+8,0xFFE9F5F8);
        }
        int cellW=Math.max(22,(rightW-9)/4);
        boolean many=!colors.isEmpty();
        int total=LDrawLibrary.colors().isEmpty()?BrickPalette.COLORS.length:colors.size();
        int maxPage=Math.max(0,(total-1)/16);colorPage=Math.min(colorPage,maxPage);
        for(int i=0;i<16 && colorPage*16+i<total;i++){
            int x=rightX+(i%4)*cellW,y=topY+13+(i/4)*26;
            LDrawLibrary.Colour c=LDrawLibrary.colors().isEmpty()?null:colors.get(colorPage*16+i);
            int color=c==null?BrickPalette.COLORS[colorPage*16+i]:c.rgb();
            boolean selected=c==null?(BrickSelection.colorCode<0 && BrickSelection.rgb==color):
                    c.code()==BrickSelection.colorCode;
            ctx.fill(x,y,x+cellW-4,y+23,selected?0xFFFFFFFF:0xFF45596C);
            // Checkerboard makes translucent colours distinguishable from their opaque RGB twins.
            ctx.fill(x+2,y+2,x+cellW-6,y+21,0xFFDDDDDD);
            ctx.fill(x+2,y+2,x+(cellW-4)/2,y+11,0xFF727272);
            ctx.fill(x+(cellW-4)/2,y+11,x+cellW-6,y+21,0xFF727272);
            ctx.fill(x+2,y+2,x+cellW-6,y+21,((c==null?255:c.alpha())<<24)|color);
        }
        ctx.drawTextWithShadow(textRenderer,BrickI18n.fmt("brickcraft.v241.quick.palette",colorPage+1,maxPage+1),rightX,topY+121,0xFFFFE5B1);
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(BrickI18n.tr(BrickSelection.colorName()),rightW),
                rightX,topY+157,0xFFFFE5B1);
        ctx.drawTextWithShadow(textRenderer,BrickI18n.tr("Y="+BrickSelection.angle+" X="+BrickSelection.pitch+" Z="+BrickSelection.roll),
                rightX,topY+185,0xFFC2E2ED);
        String selected=BrickI18n.fmt("brickcraft.v241.quick.selected",BrickI18n.tr(BrickSelection.selectedName()));
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(selected,width-24),leftX,height-31,0xFFB9DDEB);
        String help=BrickI18n.fmt("brickcraft.v241.quick.help",LDrawLibrary.size());
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(help,width-24),leftX,height-16,0xFF91B0C3);
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0){
            int count=matchCount();
            for(int i=page*perPage;i<Math.min(count,(page+1)*perPage);i++){
                int slot=i-page*perPage,xx=leftX+(slot%colCount)*(cardW+4),yy=topY+12+(slot/colCount)*30;
                if(x>=xx&&x<xx+cardW&&y>=yy&&y<yy+26){
                    if(ldraw){
                        BrickSelection.kind=BrickKind.LDRAW_PART;
                        BrickSelection.partId=imported.get(i).id();
                        if(query.trim().equals("6258572"))BrickSelection.setColor(LDrawLibrary.color(353));
                    }else{
                        BrickSelection.kind=legacy.get(i);BrickSelection.partId="";
                        BrickCatalog.Entry element=BrickCatalog.findElement(query);
                        if(element!=null&&element.kind()==BrickSelection.kind)BrickSelection.setRgb(element.rgb());
                    }
                    BrickSelection.sampled=true;
                    return true;
                }
            }
            int total=LDrawLibrary.colors().isEmpty()?BrickPalette.COLORS.length:colors.size();
            int cellW=Math.max(22,(rightW-9)/4);
            for(int i=0;i<16&&colorPage*16+i<total;i++){
                int xx=rightX+(i%4)*cellW,yy=topY+13+(i/4)*26;
                if(x>=xx&&x<xx+cellW-4&&y>=yy&&y<yy+23){
                    if(LDrawLibrary.colors().isEmpty())BrickSelection.setRgb(BrickPalette.COLORS[colorPage*16+i]);
                    else BrickSelection.setColor(colors.get(colorPage*16+i));
                    return true;
                }
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){close();return true;}
        if(search!=null&&search.isFocused())return super.keyPressed(key,scan,mods);
        if(key==GLFW.GLFW_KEY_N){close();return true;}
        if(key==GLFW.GLFW_KEY_R){BrickSelection.rotate((mods&GLFW.GLFW_MOD_SHIFT)!=0?-1:1);return true;}
        if(key==GLFW.GLFW_KEY_X){BrickSelection.rotatePitch(1);return true;}
        if(key==GLFW.GLFW_KEY_Z){BrickSelection.rotateRoll(1);return true;}
        if(key==GLFW.GLFW_KEY_H){BrickSelection.resetRotation();return true;}
        if(key==GLFW.GLFW_KEY_G){BrickSelection.cycleStep();return true;}
        if(key==GLFW.GLFW_KEY_C){BrickSelection.nextColor();return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean shouldPause(){return false;}
}
