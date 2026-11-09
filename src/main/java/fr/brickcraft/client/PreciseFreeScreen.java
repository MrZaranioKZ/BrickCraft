package fr.brickcraft.client;

import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseFreeAssembly;
import fr.brickcraft.importer.PreciseProjectIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;

/** V0.10.0: responsive editor, 2D projected picking, live 3-axis gizmos and lossless .bcpx. */
public final class PreciseFreeScreen extends Screen {
    private static final double[] MOVE_STEPS={20,8,4,1,.25};
    private static final double[] ANGLE_STEPS={90,45,15,5,1,.25};
    private final ProjectImportScreen preview;
    private final PreciseFreeAssembly assembly;
    private final String filename;
    private TextFieldWidget partId,color,x,y,z,index,moveInput,angleInput;
    private ButtonWidget moveStepButton,angleStepButton,modeButton;
    private int moveStep=0,angleStep=0;
    private String status="Clic gauche dans l'apercu : selectionner | Maj+clic : choix superpose.";
    private boolean dirty=false,rotateMode=false,draggingGizmo=false;
    private PreciseFreeAssembly.Axis dragAxis;
    private PreviewSelectionMath.Frame dragFrame;
    private double dragMouseX,dragMouseY,dragOriginX,dragOriginY,dragOriginZ,lastDragValue=Double.NaN;
    private int panel,viewportLeft,viewportTop,viewportRight,viewportBottom;

    public PreciseFreeScreen(ProjectImportScreen preview,LDrawProjectReader.Result source,String filename){
        super(Text.literal(BrickI18n.tr("BrickCraft - Placement libre")));
        this.preview=preview;
        this.assembly=new PreciseFreeAssembly(source);
        this.filename=filename;
    }
    /** Compress vertical placement on small GUI heights without scaling widget hitboxes. */
    private int uiY(int baseline){
        if(baseline<=32)return baseline;
        // The last edit row MUST finish at least 12px before the sticky Save row.
        // The former height-based factor allowed Add/Duplicate/Delete to overlap
        // Save at several Minecraft GUI scales.
        int lastEditBaseline=height>=439?315:289;
        double available=height-102.0-32.0;
        double factor=Math.max(.45,Math.min(1.0,available/(lastEditBaseline-32.0)));
        return 32+(int)Math.round((baseline-32)*factor);
    }
    private void button(int x,int y,int w,int h,String text,Runnable callback){
        if(w<15)return;
        if(height<400)h=Math.min(h,16);
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr(text)),b->callback.run()).dimensions(x,y<330?uiY(y):y,w,h).build());
    }
    private void footerButton(int xx,int yy,int width,String label,Runnable action){
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr(label)),b->action.run())
                .dimensions(xx,yy,width,20).build());
    }
    private TextFieldWidget field(int x,int y,int w,String placeholder,int length){
        TextFieldWidget f=new TextFieldWidget(textRenderer,x,y<330?uiY(y):y,Math.max(18,w),18,Text.literal(BrickI18n.tr(placeholder)));
        f.setMaxLength(length);addDrawableChild(f);return f;
    }
    @Override protected void init(){
        panel=Math.max(270,Math.min(Math.min(405,width-195),width*38/100));
        panel=Math.min(panel,Math.max(230,width-140));
        viewportLeft=panel+12;viewportTop=36;viewportRight=width-9;viewportBottom=height-29;
        int left=12, usable=panel-24;
        int q=(usable-12)/4,third=(usable-10)/3;
        button(left,37,q,19,"< Piece",()->pick(-1));
        button(left+q+4,37,q,19,"Piece >",()->pick(1));
        index=field(left+2*(q+4),37,q,"No",8);
        button(left+3*(q+4),37,q,19,"Aller",this::jump);
        partId=field(left,94,usable-112,"Reference LDraw",96);
        color=field(left+usable-106,94,106,"Couleur",8);
        x=field(left,138,third,"LDU X",24);
        y=field(left+third+5,138,third,"LDU Y",24);
        z=field(left+(third+5)*2,138,third,"LDU Z",24);
        int half=(usable-8)/2;
        moveStepButton=addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Pas pos.")),b->{
            moveStep=(moveStep+1)%MOVE_STEPS.length;updateStepTexts();
        }).dimensions(left,uiY(179),Math.max(58,half/2),height<400?16:19).build());
        moveInput=field(left+half/2+4,179,half-half/2-4,"LDU",20);
        angleStepButton=addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Pas rot.")),b->{
            angleStep=(angleStep+1)%ANGLE_STEPS.length;updateStepTexts();
        }).dimensions(left+half+8,uiY(179),Math.max(58,half/2),height<400?16:19).build());
        angleInput=field(left+half+8+half/2+4,179,half-half/2-4,"Degres",20);
        updateStepTexts();
        for(int c=0;c<3;c++){
            PreciseFreeAssembly.Axis axis=PreciseFreeAssembly.Axis.values()[c];
            int px=left+c*(third+5);
            button(px,207,third/2-2,19,axis+" -",()->stepMove(axis,-1));
            button(px+third/2+2,207,third/2-2,19,axis+" +",()->stepMove(axis,1));
            button(px,235,third/2-2,19,"R"+axis+" -",()->stepRotate(axis,-1));
            button(px+third/2+2,235,third/2-2,19,"R"+axis+" +",()->stepRotate(axis,1));
        }
        button(left,264,usable/2-3,19,"Appliquer",this::applyFields);
        button(left+usable/2+3,264,usable/2-3,19,"Rotation 0",()->change(assembly.resetRotation(),BrickI18n.fmt("brickcraft.v251.free.action.reset")));
        button(left,289,third,19,"Ajouter",this::add);
        button(left+third+5,289,third,19,"Dupliquer",()->change(assembly.duplicate(),BrickI18n.fmt("brickcraft.v251.free.action.duplicated")));
        button(left+(third+5)*2,289,third,19,"Supprimer",()->change(assembly.delete(),BrickI18n.fmt("brickcraft.v251.free.action.deleted")));
        if(height>=439){
            button(left,315,third,19,"Annuler",()->change(assembly.undo(),BrickI18n.fmt("brickcraft.v251.free.action.undo")));
            button(left+third+5,315,third,19,"Retablir",()->change(assembly.redo(),BrickI18n.fmt("brickcraft.v251.free.action.redo")));
            modeButton=addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Mode: bouger")),b->toggleMode())
                    .dimensions(left+(third+5)*2,uiY(315),third,19).build());
        }
        // Sticky footer stays clear of the last editable row even on small GUI scales.
        footerButton(left,height-72,usable,"Enregistrer un nouveau .bcpx",this::save);
        footerButton(left,height-45,usable,"Retour a l'import",()->client.setScreen(preview));
        refreshFields();
    }
    private void toggleMode(){rotateMode=!rotateMode;
        if(modeButton!=null)modeButton.setMessage(Text.literal(BrickI18n.tr(rotateMode?"Mode: tourner":"Mode: bouger")));
        status=rotateMode?"Poignees : rotation autour de l'axe choisi (R).":"Poignees : deplacement sur l'axe choisi (G).";
    }
    private void updateStepTexts(){moveInput.setText(fmt(MOVE_STEPS[moveStep]));angleInput.setText(fmt(ANGLE_STEPS[angleStep]));}
    private static String fmt(double v){return v==(long)v?Long.toString((long)v):Double.toString(v);}
    private static double number(TextFieldWidget field){return Double.parseDouble(field.getText().strip().replace(',','.'));}
    private static double stepValue(TextFieldWidget field,double max){
        double value=number(field);
        if(!Double.isFinite(value)||value<=0||value>max)throw new IllegalArgumentException("Pas entre 0 et "+max);
        return value;
    }
    private void refreshFields(){
        var p=assembly.selected();
        index.setText(assembly.selectedIndex()<0?"-":Integer.toString(assembly.selectedIndex()+1));
        if(p==null){partId.setText("3005");color.setText("4");x.setText("0");y.setText("0");z.setText("0");}
        else{partId.setText(p.partId());color.setText(Integer.toString(p.color()));
            x.setText(fmt(p.x()));y.setText(fmt(p.y()));z.setText(fmt(p.z()));}
    }
    private void pick(int dir){
        if(assembly.size()==0){status="Aucune piece. Utilise Ajouter.";return;}
        assembly.select(Math.floorMod(assembly.selectedIndex()+dir,assembly.size()));refreshFields();
    }
    private void jump(){
        try{if(!assembly.select(Integer.parseInt(index.getText())-1))throw new IllegalArgumentException();refreshFields();}
        catch(Exception ex){status="Numero invalide : 1 a "+assembly.size();}
    }
    private LDrawProjectReader.Instance fromFields(double[] mat){
        return new LDrawProjectReader.Instance(partId.getText().strip().replace(".dat",""),
                Integer.parseInt(color.getText().strip()),number(x),number(y),number(z),mat);
    }
    private void applyFields(){
        try{var p=assembly.selected();
            if(p==null){status=BrickI18n.fmt("brickcraft.v251.free.select_first");return;}
            change(assembly.replace(fromFields(p.matrix())),BrickI18n.fmt("brickcraft.v251.free.action.applied"));
        }catch(Exception ex){status="Champs invalides : "+ex.getMessage();}
    }
    private void add(){
        try{change(assembly.add(fromFields(PreciseFreeAssembly.identity())),BrickI18n.fmt("brickcraft.v251.free.action.added"));}
        catch(Exception ex){status="Ajout refuse : "+ex.getMessage();}
    }
    private void stepMove(PreciseFreeAssembly.Axis axis,int direction){
        try{double amount=direction*stepValue(moveInput,1000);
            change(assembly.move(axis,amount),"Deplacement "+axis+" : "+fmt(amount)+" LDU");}
        catch(Exception ex){status="Deplacement refuse : "+ex.getMessage();}
    }
    private void stepRotate(PreciseFreeAssembly.Axis axis,int direction){
        try{double amount=direction*stepValue(angleInput,360);
            change(assembly.rotate(axis,amount),"Rotation "+axis+" : "+fmt(amount)+" deg");}
        catch(Exception ex){status="Rotation refusee : "+ex.getMessage();}
    }
    private void change(boolean success,String message){
        if(!success){status=BrickI18n.fmt("brickcraft.v251.free.no_change");return;}
        dirty=true;refreshFields();preview.applyFreeEdits(assembly.result());
        status=BrickI18n.fmt("brickcraft.v251.free.change",BrickI18n.tr(message),assembly.size());
    }
    private void save(){
        if(assembly.size()==0){status="Ajoute au moins une piece avant de sauver";return;}
        try{
            Path folder=FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("imports");
            Files.createDirectories(folder);
            String stem=filename.replaceAll("(?i)\\.(io|ldr|mpd|bcpx)$","").replaceAll("[^a-zA-Z0-9_-]","_");
            if(stem.isEmpty())stem="atelier";
            Path dest=folder.resolve(stem+"_free.bcpx");
            int suffix=2;
            while(Files.exists(dest)){dest=folder.resolve(stem+"_free_"+(suffix++)+".bcpx");}
            PreciseProjectIO.save(dest,assembly.result());dirty=false;
            status=BrickI18n.fmt("brickcraft.v251.free.saved",dest.getFileName());
            preview.refreshFreeFiles(dest);
        }catch(Exception ex){status="Erreur de sauvegarde : "+ex.getMessage();}
    }
    private void limitedText(DrawContext draw,String message,int xx,int yy,int max,int color){
        if(max<=5)return;
        message=BrickI18n.tr(message);
        if(textRenderer.getWidth(message)>max)message=textRenderer.trimToWidth(message,Math.max(0,max-textRenderer.getWidth("...")))+"...";
        draw.drawTextWithShadow(textRenderer,message,xx,yy,color);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        ctx.fill(0,0,width,height,0xFF0B111A);
        preview.renderFreeViewport(ctx,viewportLeft,viewportTop,viewportRight,viewportBottom,
                assembly.selectedIndex(),rotateMode);
        ctx.fill(4,30,panel+3,height-26,0xFF172636);
        ctx.fill(4,30,panel+3,34,0xFF55CFD8);
        limitedText(ctx,"BRICKCRAFT / PLACEMENT LIBRE",12,9,width-22,0xFFE7F8FF);
        limitedText(ctx,"Piece "+(assembly.selectedIndex()+1)+" / "+assembly.size()+(dirty?"  *":""),
                12,uiY(62),panel-23,0xFF9DE4E3);
        limitedText(ctx,"Reference / code couleur",12,uiY(78),panel-23,0xFFC0CEDA);
        limitedText(ctx,"Origine LDraw : X / Y / Z",12,uiY(117),panel-23,0xFFC0CEDA);
        if(height>=420)limitedText(ctx,"1 stud = 20 LDU | 1 plate = 8 LDU",12,uiY(162),panel-23,0xFFE7C98F);
        // Help lives in the bottom status band, not between action buttons.

        limitedText(ctx,"Clic gauche : selection | Maj : suivant | Clic droit : orbite",viewportLeft+8,viewportTop+7,
                viewportRight-viewportLeft-16,0xFFAAC9D7);
        limitedText(ctx,"Clic molette : panoramique | Molette : zoom | G : bouger | R : tourner",
                viewportLeft+8,viewportTop+20,viewportRight-viewportLeft-16,0xFF7CA5B9);
        ctx.fill(0,height-25,width,height,0xFF122334);
        limitedText(ctx,status,10,height-18,width-20,0xFFFFDA91);
        super.render(ctx,mx,my,delta);
    }
    private boolean overViewport(double mx,double my){
        return mx>=viewportLeft&&mx<=viewportRight&&my>=viewportTop&&my<=viewportBottom;
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&overViewport(mx,my)){
            int axis=preview.editorHitGizmo(mx,my,assembly.selectedIndex(),viewportLeft,viewportTop,viewportRight,viewportBottom);
            if(axis>=0 && assembly.selected()!=null){
                try{stepValue(rotateMode?angleInput:moveInput,rotateMode?360:1000);}
                catch(Exception ex){status="Pas de deplacement/rotation invalide";return true;}
                dragAxis=PreciseFreeAssembly.Axis.values()[axis];
                dragFrame=preview.editorDragFrame(viewportLeft,viewportTop,viewportRight,viewportBottom);
                var p=assembly.selected();
                dragOriginX=p.x();dragOriginY=p.y();dragOriginZ=p.z();
                dragMouseX=mx;dragMouseY=my;lastDragValue=Double.NaN;
                draggingGizmo=assembly.beginGesture();
                status="Glisse la poignee "+dragAxis+" ("+(rotateMode?"rotation":"deplacement")+")";
                return true;
            }
            int picked=preview.editorPickPart(mx,my,assembly.selectedIndex(),hasShiftDown(),
                    viewportLeft,viewportTop,viewportRight,viewportBottom);
            if(picked>=0&&assembly.select(picked)){refreshFields();
                status="Piece "+(picked+1)+" / "+assembly.size()+" : "+assembly.selected().partId();}
            else status="Aucune piece sous le curseur";
            return true;
        }
        if(preview.editorPreviewMouseClick(mx,my,button,viewportLeft,viewportTop,viewportRight,viewportBottom))return true;
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(button==0&&draggingGizmo&&assembly.selected()!=null){
            try{
                double step=stepValue(rotateMode?angleInput:moveInput,rotateMode?360:1000);
                double raw=rotateMode?(mx-dragMouseX)*1.35:
                        PreviewSelectionMath.axisDrag(dragFrame,mx,my,dragMouseX,dragMouseY,
                                dragOriginX,dragOriginY,dragOriginZ,dragAxis.ordinal());
                double delta=PreviewSelectionMath.snap(raw,step);
                if(Double.compare(delta,lastDragValue)==0)return true;
                if(assembly.updateGesture(dragAxis,delta,rotateMode)){
                    lastDragValue=delta;dirty=true;preview.applyFreeEdits(assembly.result());
                    status=(rotateMode?"Rotation ":"Deplacement ")+dragAxis+" : "+fmt(delta)+
                            (rotateMode?" deg":" LDU")+" (Ctrl+Z pour annuler)";
                }
            }catch(Exception ex){status="Manipulation interrompue : "+ex.getMessage();}
            return true;
        }
        if(preview.editorPreviewMouseDrag(button,dx,dy))return true;
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(button==0&&draggingGizmo){
            draggingGizmo=false;assembly.finishGesture();refreshFields();return true;
        }
        if(preview.editorPreviewMouseRelease(button))return true;
        return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(preview.editorPreviewWheel(mx,my,vertical,viewportLeft,viewportTop,viewportRight,viewportBottom))return true;
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scancode,int modifiers){
        if((modifiers & GLFW.GLFW_MOD_CONTROL)!=0){
            if(key==GLFW.GLFW_KEY_Z){change(assembly.undo(),"Annulation");return true;}
            if(key==GLFW.GLFW_KEY_Y){change(assembly.redo(),"Retablissement");return true;}
            if(key==GLFW.GLFW_KEY_S){save();return true;}
            if(key==GLFW.GLFW_KEY_D){change(assembly.duplicate(),"Piece dupliquee");return true;}
        }
        if(!(getFocused() instanceof TextFieldWidget)){
            if(key==GLFW.GLFW_KEY_G&&!rotateMode){return true;}
            if(key==GLFW.GLFW_KEY_G&&rotateMode){toggleMode();return true;}
            if(key==GLFW.GLFW_KEY_R&&!rotateMode){toggleMode();return true;}
            if(key==GLFW.GLFW_KEY_R&&rotateMode){return true;}
        }
        if(key==GLFW.GLFW_KEY_O && !(getFocused() instanceof TextFieldWidget)){
            preview.toggleGpuPreview();status="Apercu GPU/CPU bascule (touche O)";return true;
        }
        if(key==GLFW.GLFW_KEY_ESCAPE){assembly.finishGesture();client.setScreen(preview);return true;}
        return super.keyPressed(key,scancode,modifiers);
    }
}
