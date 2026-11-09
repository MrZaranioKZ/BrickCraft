package fr.brickcraft.client;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickCatalog;
import net.minecraft.client.gui.widget.TextFieldWidget;
import fr.brickcraft.core.BrickPalette;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.core.ConnectorSnapper;
import fr.brickcraft.net.BrickActionPayload;
import fr.brickcraft.net.StudioExportPayload;
import fr.brickcraft.net.StudioExportStatusPayload;
import fr.brickcraft.core.StudioExportLimits;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Path;
import java.io.IOException;

/**
 * V0.3: angle-aware isometric editor; the world NBT format supports V0.2.x saves.
 * Client and server MUST both use 0.3 because networking has a new angle field.
 */
public final class BrickStudioScreen extends Screen {
    private static final StudioAssembly ASSEMBLY = new StudioAssembly();
    private enum Tab { PIECES, COULEURS, POSITION }
    private enum Tool { POSER, DEPLACER, EFFACER }

    private final BlockPos target;
    private Tab tab = Tab.PIECES;
    private Tool tool = Tool.POSER;
    private StudioLayout layout;
    private final List<ButtonWidget> footerActions=new ArrayList<>();
    private int cursorX = 0, cursorZ = 0;
    private int selected = -1;
    private boolean autoSnap = true;
    private boolean confirmClear = false;
    private int shapePage=0;
    private int colorPage=0;
    private String colorQuery="",colorFamily="Tout";
    private static final String[] COLOR_FAMILIES={"Tout","Opaque","Transparent","Métal","Spécial"};
    private java.util.List<LDrawLibrary.Colour> studioColors(){
        return LDrawLibrary.searchColors(colorQuery,colorFamily);
    }
    private void cycleColorFamily(){
        int at=0;for(int i=0;i<COLOR_FAMILIES.length;i++)if(COLOR_FAMILIES[i].equals(colorFamily))at=i;
        colorFamily=COLOR_FAMILIES[(at+1)%COLOR_FAMILIES.length];colorPage=0;clearAndInit();
    }
    private boolean ldrawTab=false;
    private String searchQuery="";
    private TextFieldWidget searchField;
    private boolean settingSearch=false;
    private BrickPiece clipboard=null;
    private float orbit = .72f;
    private float zoom = 1f;
    // Independent workspace coordinates and a movable camera; no 12x12 fixed canvas.
    private float cameraX = StudioAssembly.DEFAULT_GRID/2f;
    private float cameraZ = StudioAssembly.DEFAULT_GRID/2f;
    private float cameraY = 0f;
    private float panX = 0f, panY = 0f;
    private boolean panning = false;
    private boolean connectorGuides = false;
    private static final float MIN_ZOOM = .02f;
    private static final float MAX_ZOOM = 8f;
    private int grid() {return ASSEMBLY.gridSize();}
    private Path projectPath(){
        return FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("studio_project.bcp");
    }

    private boolean exportPending = false;
    private boolean orbitDragging = false;
    private boolean fullscreenStage = false;
    private static boolean autosaveChecked = false;
    private String status = "Clic gauche : poser | Molette : zoom | Droit-glisser : tourner la vue";

    public BrickStudioScreen(BlockPos target) {
        super(Text.literal(BrickI18n.tr("BrickCraft Studio")));
        this.target = target;
        if(!autosaveChecked && ASSEMBLY.isEmpty()){
            autosaveChecked=true;
            try{
                Path autosave=FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("studio_autosave.bcp");
                if(java.nio.file.Files.isRegularFile(autosave)){
                    StudioProjectIO.Project project=StudioProjectIO.read(autosave);
                    if(ASSEMBLY.loadProject(project.grid(),project.pieces()))
                        status=BrickI18n.fmt("brickcraft.v252.studio.restored",ASSEMBLY.size());
                }
            }catch(IOException e){status="Sauvegarde auto non chargee : "+e.getMessage();}
        }
        cameraX=ASSEMBLY.gridSize()/2f;cameraZ=ASSEMBLY.gridSize()/2f;
    }

    @Override
    protected void init() {
        layout = StudioLayout.calculate(width, height, fullscreenStage);
        footerActions.clear();
        searchField = null; // Remove stale focused search widgets when entering full-view mode.
        final int left = layout.panelX() + 3;
        final int panel = layout.panelW() - 6;
        final int gap = 2;
        final int tabW = (panel - 2 * gap) / 3;

        // Frequent file actions belong in the header, not buried inside rotations.
        if(width>=430){
            button(width-260,5,78,16,"Importer",this::openProjectImporter);
            button(width-177,5,78,16,"Sauver",this::saveProject);
        }
        if(width>=300){
            button(width-95,5,88,16,fullscreenStage?"Panneau":"Vue max",()->{
                fullscreenStage=!fullscreenStage;clearAndInit();
            });
        }
        if(!fullscreenStage){
        button(left, 28, tabW, 19, "Briques", () -> changeTab(Tab.PIECES));
        button(left + tabW + gap, 28, tabW, 19, "Couleurs", () -> changeTab(Tab.COULEURS));
        button(left + 2 * (tabW + gap), 28, panel - 2 * (tabW + gap), 19,
                "Reglages", () -> changeTab(Tab.POSITION));

        searchField=null;
        if (tab == Tab.PIECES) {
            button(left+2,52,panel-4,16,ldrawTab?"Catalogue LDraw - changer":"Formes classiques - changer",()->{ldrawTab=!ldrawTab;searchQuery="";shapePage=0;clearAndInit();});
            searchField=new TextFieldWidget(textRenderer,left+2,73,panel-4,17,Text.literal(BrickI18n.tr("Rechercher une piece")));
            searchField.setMaxLength(48);
            settingSearch=true;searchField.setText(searchQuery);settingSearch=false;
            searchField.setChangedListener(value->{
                if(settingSearch||value.equals(searchQuery))return;
                searchQuery=value;shapePage=0;
                BrickCatalog.Entry element=BrickCatalog.findElement(value);
                if(element!=null&&!ldrawTab){BrickSelection.kind=element.kind();BrickSelection.partId="";BrickSelection.setRgb(element.rgb());}
                clearAndInit();
                if(searchField!=null){setFocused(searchField);searchField.setFocused(true);}
            });
            addDrawableChild(searchField);

            final List<BrickKind> kinds=ldrawTab?List.of():BrickCatalog.search(searchQuery);
            final List<LDrawLibrary.PartInfo> libraryParts=ldrawTab?LDrawLibrary.search(searchQuery,500):List.of();
            final int count=ldrawTab?libraryParts.size():kinds.size();
            final int w=(panel-8)/2;
            for(int i=shapePage*6;i<Math.min(count,(shapePage+1)*6);i++){
                int idx=i-shapePage*6,row=idx/2,col=idx%2;
                int px=left+2+col*(w+4),py=100+row*27;
                if(ldrawTab){
                    LDrawLibrary.PartInfo part=libraryParts.get(i);
                    button(px,py,w,23,part.id(),()->{
                        BrickSelection.kind=BrickKind.LDRAW_PART;BrickSelection.partId=part.id();
                        if(searchQuery.trim().equals("6258572"))BrickSelection.setColor(LDrawLibrary.color(353));
                        selected=-1;tool=Tool.POSER;status="LDraw : "+part.name()+" ("+part.id()+")";
                    });
                }else{
                    BrickKind kind=kinds.get(i);
                    button(px,py,w,23,shortName(kind),()->{
                        BrickSelection.kind=kind;BrickSelection.partId="";selected=-1;tool=Tool.POSER;
                        BrickCatalog.Entry element=BrickCatalog.findElement(searchQuery);
                        if(element!=null&&element.kind()==kind)BrickSelection.setRgb(element.rgb());
                        status=BrickI18n.fmt("brickcraft.v252.studio.selected_piece",kind.title);
                    });
                }
            }
            if(ldrawTab && LDrawLibrary.size()==0) {
                button(left+2,184,panel-4,19,"Recharger LDraw",()->{
                    LDrawLibrary.init(FabricLoader.getInstance().getConfigDir());
                    searchQuery="";shapePage=0;
                    status=LDrawLibrary.status();
                    if(client!=null && client.player!=null)
                        client.player.sendMessage(Text.literal(BrickI18n.tr("BrickCraft : "+status)),false);
                    clearAndInit();
                });
            } else {
                button(left+2,184,w,19,"< Page",()->{shapePage=Math.max(0,shapePage-1);clearAndInit();});
                button(left+w+6,184,w,19,"Page >",()->{
                    shapePage=Math.min(Math.max(0,(count-1)/6),shapePage+1);clearAndInit();
                });
            }
        } else if (tab == Tab.COULEURS) {
            button(left+2,52,panel-4,17,BrickI18n.fmt("brickcraft.v241.colors.finish",BrickI18n.tr(colorFamily)),this::cycleColorFamily);
            searchField=new TextFieldWidget(textRenderer,left+2,74,panel-4,18,
                    Text.literal(BrickI18n.tr("Couleur : nom ou code LDraw")));
            searchField.setMaxLength(48);
            settingSearch=true;searchField.setText(colorQuery);settingSearch=false;
            searchField.setChangedListener(value->{
                if(settingSearch||value.equals(colorQuery))return;
                colorQuery=value;colorPage=0;clearAndInit();
                if(searchField!=null){setFocused(searchField);searchField.setFocused(true);}
            });
            addDrawableChild(searchField);
            final int bw=(panel-8)/2;
            button(left+2,207,bw,18,"< Page",()->{colorPage=Math.max(0,colorPage-1);});
            button(left+bw+6,207,bw,18,"Page >",()->{
                int total=studioColors().isEmpty()?BrickPalette.COLORS.length:studioColors().size();
                colorPage=Math.min(Math.max(0,(total-1)/12),colorPage+1);
            });
        } else if (tab == Tab.POSITION) {
            final int bw=(panel-8)/2;
            final int step=Math.min(25,Math.max(18,(layout.footerY()-82)/9));
            for(int i=0;i<10;i++){
                int py=64+i*step;
                switch(i){
                    case 0 -> {button(left+2,py,bw,19,"Y -",()->rotate(-1));
                               button(left+bw+6,py,bw,19,"Y +",()->rotate(1));}
                    case 1 -> {button(left+2,py,bw,19,"X -",()->tilt(true,-1));
                               button(left+bw+6,py,bw,19,"X +",()->tilt(true,1));}
                    case 2 -> {button(left+2,py,bw,19,"Z -",()->tilt(false,-1));
                               button(left+bw+6,py,bw,19,"Z +",()->tilt(false,1));}
                    case 3 -> button(left+2,py,panel-4,19,"Reset 0°",this::resetRotation);
                    case 4 -> button(left+2,py,panel-4,19,"Pas Y : "+BrickSelection.step()+" deg",()->{
                        BrickSelection.cycleStep();clearAndInit();});
                    case 5 -> {button(left+2,py,bw,19,"H -",()->BrickSelection.layer=Math.max(0,BrickSelection.layer-1));
                               button(left+bw+6,py,bw,19,"H +",()->BrickSelection.layer=Math.min(StudioAssembly.MAX_TOP/2,BrickSelection.layer+1));}
                    case 6 -> button(left+2,py,panel-4,19,BrickI18n.fmt("brickcraft.v241.studio.height",autoSnap?"AUTO":BrickI18n.tr("MANUEL")),()->{
                        autoSnap=!autoSnap;clearAndInit();});
                    case 7 -> {button(left+2,py,bw,19,"Grille "+grid()+"x"+grid(),this::cycleGrid);
                               button(left+bw+6,py,bw,19,"Cadrer",this::frameAll);}
                    case 8 -> {
                if(width>=430){
                    button(left+2,py,bw,19,"Charger",this::loadProject);
                    button(left+bw+6,py,bw,19,"Accroches",()->{
                        connectorGuides=!connectorGuides;
                        status=BrickI18n.fmt(connectorGuides?"brickcraft.v251.studio.connectors.on":"brickcraft.v251.studio.connectors.off");
                    });
                }else{
                    button(left+2,py,bw,19,"Sauver",this::saveProject);
                    button(left+bw+6,py,bw,19,"Importer",this::openProjectImporter);
                }
            }
                    case 9 -> button(left+2,py,panel-4,19,confirmClear?"Confirmer VIDER":"Vider atelier",this::confirmClear);
                    default -> {}
                }
            }
        }

        }
        // Tool chooser remains accessible in the full-width viewport.
        final int toolGap = 2;
        final int toolsWidth = layout.stageWidth() - toolGap*2;
        final int toolW = toolsWidth / 3;
        button(layout.stageX(), 28, toolW, 19, "Poser", () -> chooseTool(Tool.POSER));
        button(layout.stageX()+toolW+toolGap, 28, toolW, 19, "Bouger", () -> chooseTool(Tool.DEPLACER));
        button(layout.stageX()+toolW*2+toolGap*2, 28,
                Math.max(12, layout.stageWidth()-toolW*2-toolGap*2), 19,
                "Effacer", () -> chooseTool(Tool.EFFACER));

        final boolean compact = width < 560;
        // Rotation is already available with R and in Settings (Y+/Y-).
        // Avoid a second, redundant Rotate action in the permanent footer.
        final String[] names = compact
                ? new String[]{"Annuler", "Refaire", "Copie", exportPending?"Stop":"Monde", "Quitter"}
                : new String[]{"Annuler", "Retablir", "Dupliquer", exportPending?"Stop export":"Construire", "Quitter"};
        final Runnable[] actions = {
                this::undo, this::redo, this::duplicate,
                this::applyToWorld, this::close
        };
        final int spacing = 3, first = 6;
        final int available = width - 2*first - (names.length-1)*spacing;
        final int base = available / names.length;
        int bx = first;
        for (int i=0;i<names.length;i++) {
            int bw = i==names.length-1 ? width-first-bx : base;
            ButtonWidget action=button(bx,layout.footerY(),bw,20,names[i],actions[i]);
            if(action!=null)footerActions.add(action);
            bx += bw+spacing;
        }
    }

    private void chooseTool(Tool next) {
        tool = next;
        selected = -1;
        status = switch (next) {
            case POSER -> "Poser : clique dans la grille pour ajouter. H = hauteur.";
            case DEPLACER -> "Bouger : clique une piece puis sa nouvelle position.";
            case EFFACER -> "Effacer : clique directement la piece a retirer.";
        };
    }

    private void changeTab(Tab next) {
        if(tab==next) return;
        tab = next;
        clearAndInit();
    }

    private static String shortName(BrickKind kind) {
        return kind.title.replace("Brique ", "B ")
                .replace("Plaque ", "P ").replace("Tuile ", "T ")
                .replace("Pente ","S ").replace('×', 'x');
    }

    private ButtonWidget button(int x, int y, int w, int h, String title, Runnable callback) {
        // Buttons must never reach the screen footer/status area or offscreen.
        if (w < 12 || h < 12 || x >= width || y < 0 || y+h > height-19) return null;
        if (y < layout.footerY() && y+h > layout.footerY()-3) return null;
        ButtonWidget widget=ButtonWidget.builder(Text.literal(BrickI18n.tr(title)), b -> callback.run())
                .dimensions(x,y,w,h).build();
        addDrawableChild(widget);
        return widget;
    }

    /** Candidate at the current cursor, snapped to the highest overlapping LEGO body. */
    private BrickPiece candidate(int ignoreIndex, BrickPiece source) {
        BrickKind kind=source==null?BrickSelection.kind:source.kind();
        int rgb=source==null?BrickSelection.rgb:source.rgb();
        int colorCode=source==null?BrickSelection.colorCode:source.colorCode();
        int angle=source==null?BrickSelection.angle:source.angle();
        int pitch=source==null?BrickSelection.pitch:source.pitch();
        int roll=source==null?BrickSelection.roll:source.roll();
        int height=BrickSelection.layer*2;
        if(autoSnap) {
            if(kind==BrickKind.LDRAW_PART){
                String id=source==null?BrickSelection.partId:source.partId();
                BrickPiece probe=new BrickPiece(kind,rgb,cursorX,0,cursorZ,angle/90,angle,pitch,roll,id,colorCode);
                height=ASSEMBLY.snapTop(probe,height,ignoreIndex);
            }else height=ASSEMBLY.snapTop(cursorX,cursorZ,kind,angle,pitch,roll,height,ignoreIndex);
        }
        int a=Math.floorMod(angle,360);
        BrickPiece proposal=new BrickPiece(kind,rgb,cursorX,height,cursorZ,a/90,a,pitch,roll,
            kind==BrickKind.LDRAW_PART?(source==null?BrickSelection.partId:source.partId()):"",colorCode);
        if(autoSnap && height>0) {
            // Use a connector only where a real supporting brick sits exactly at this level.
            for(int i=ASSEMBLY.size()-1;i>=0;i--) {
                if(i==ignoreIndex)continue;
                BrickPiece support=ASSEMBLY.get(i);
                if(support!=null && support.top()==height && proposal.horizontalOverlaps(support)) {
                    ConnectorSnapper.Result snap=ConnectorSnapper.snap(proposal,support,
                            cursorX+0.5,support.top()/5.0,cursorZ+0.5,
                            next->ASSEMBLY.canPlace(next,ignoreIndex));
                    if(snap.snapped())return snap.piece();
                }
            }
        }
        return proposal;
    }

    private void add() {
        BrickPiece p = candidate(-1,null);
        if (ASSEMBLY.add(p)) {
            status = "Ajoute : " + p.kind().title + " a X=" + cursorX + " Z=" + cursorZ
                    + " H=" + p.y()/2 + " ("+ASSEMBLY.size()+" pieces)";
        } else {
            status = "Placement impossible : hors plateau, limite ou collision";
        }
    }

    private void undo() {
        if (ASSEMBLY.undo()) {selected=-1;status=BrickI18n.tr("Modification annulee");}
        else status=BrickI18n.tr("Rien a annuler");
    }
    private void redo() {
        if (ASSEMBLY.redo()) {selected=-1;status=BrickI18n.tr("Modification retablie");}
        else status=BrickI18n.tr("Rien a retablir");
    }
    private void duplicate() {
        BrickPiece p = ASSEMBLY.get(selected);
        if (p==null) {
            status="Selectionne d'abord une piece avec l'outil Bouger";
            chooseTool(Tool.DEPLACER);
            return;
        }
        BrickSelection.kind=p.kind(); BrickSelection.partId=p.partId(); BrickSelection.rgb=p.rgb();BrickSelection.colorCode=p.colorCode();
        BrickSelection.angle=p.angle(); BrickSelection.turn=p.turn();
        BrickSelection.pitch=p.pitch();BrickSelection.roll=p.roll();
        cursorX=Math.min(grid()-1,p.x()+p.width());
        cursorZ=p.z(); BrickSelection.layer=Math.max(0,p.y()/2);
        selected=-1;tool=Tool.POSER;
        status="Copie chargee : clique pour poser une autre piece";
    }
    private void rotate(){rotate(1);}
    private void rotate(int direction) {
        if(tool==Tool.DEPLACER && selected>=0) {
            BrickPiece p=ASSEMBLY.get(selected);
            if(p!=null) {
                BrickPiece changed=p.withAngle(p.angle()+direction*BrickSelection.step());
                status=ASSEMBLY.replace(selected,changed)
                        ? "Piece tournee : "+changed.angle()+" deg"
                        : "Rotation bloquee (collision / bord)";
                return;
            }
        }
        BrickSelection.rotate(direction);
        status=BrickI18n.fmt("brickcraft.v252.studio.rotated",BrickSelection.angle,BrickSelection.step());
    }

    private void tilt(boolean xAxis,int direction){
        if(tool==Tool.DEPLACER && selected>=0){
            BrickPiece p=ASSEMBLY.get(selected);
            if(p!=null){
                BrickPiece next=xAxis?p.withPitch(p.pitch()+90*direction):p.withRoll(p.roll()+90*direction);
                status=ASSEMBLY.replace(selected,next)?"Orientation : X="+next.pitch()+" Z="+next.roll()
                        :"Rotation impossible (collision / limites)";
                return;
            }
        }
        if(xAxis)BrickSelection.rotatePitch(direction);else BrickSelection.rotateRoll(direction);
        status="Orientation : X="+BrickSelection.pitch+" Z="+BrickSelection.roll;
    }
    private void resetRotation(){
        if(tool==Tool.DEPLACER && selected>=0){
            BrickPiece p=ASSEMBLY.get(selected);
            if(p!=null){
                BrickPiece next=p.withAngle(0).withPitch(0).withRoll(0);
                status=ASSEMBLY.replace(selected,next)?"Piece remise a 0°":"Reset impossible : collision";
                return;
            }
        }
        BrickSelection.resetRotation();status=BrickI18n.fmt("brickcraft.v251.studio.rotation_reset");
    }
    private void copySelection(){
        BrickPiece p=ASSEMBLY.get(selected);
        if(p==null){status="Selectionne d'abord une piece avec Bouger";return;}
        clipboard=p;
        status="Copie : "+p.kind().title+" | Ctrl+V pour coller";
    }
    private void pasteSelection(){
        if(clipboard==null){status="Aucune piece copiee";return;}
        BrickPiece p=candidate(-1,clipboard);
        if(ASSEMBLY.add(p))status="Piece collee (Ctrl+Z pour annuler)";
        else status="Impossible de coller ici : collision / bord";
    }

    private void confirmClear() {
        if(ASSEMBLY.size()==0) {status="Atelier deja vide";return;}
        if(!confirmClear) {
            confirmClear=true;
            status="Attention : clique a nouveau sur Vider pour confirmer";
            clearAndInit();return;
        }
        ASSEMBLY.clear(); selected=-1;confirmClear=false;
        status="Atelier vide (Ctrl+Z pour annuler)";
        clearAndInit();
    }

    /** Request a safe server-side export instead of hundreds of manual 12x12 placements. */
    private void applyToWorld() {
        if(exportPending){
            ClientPlayNetworking.send(StudioExportPayload.cancel());
            status="Annulation demandee. Le serveur retire les fragments poses.";
            return;
        }
        if(client==null || client.player==null || !client.player.getMainHandStack().isOf(BrickCraft.TOOL)) {
            status="Tiens l'outil BrickCraft dans la main";return;
        }
        if(!client.player.getAbilities().creativeMode){
            status="Export de grand projet disponible en Creatif uniquement";return;
        }
        if(target==null){status="Vise le sol avant d'ouvrir le Studio";return;}
        List<BrickPiece> pieces=ASSEMBLY.pieces();
        if(pieces.isEmpty()){status="Ajoute une piece dans l'atelier";return;}
        // Validate without editing the world; the server independently checks every cell.
        for(BrickPiece p:pieces){
            if(!StudioExportLimits.validLocal(p,grid()) ||
                    StudioExportLimits.upperBoundCells(p)>StudioExportLimits.MAX_CELLS_PER_PIECE){
                status="Une piece est trop grande ou en dehors du plateau";return;
            }
        }
        exportPending=true;
        clearAndInit();
        ClientPlayNetworking.send(StudioExportPayload.begin(target,grid(),pieces.size()));
        for(int offset=0;offset<pieces.size();offset+=StudioExportPayload.BATCH_SIZE){
            ClientPlayNetworking.send(StudioExportPayload.batch(offset,
                    pieces.subList(offset,Math.min(pieces.size(),offset+StudioExportPayload.BATCH_SIZE))));
        }
        ClientPlayNetworking.send(StudioExportPayload.commit());
        status=BrickI18n.fmt("brickcraft.v252.studio.send",pieces.size());
    }

    /** Called by the authenticated S2C progress packet on the client thread. */
    public static void receiveExportStatus(StudioExportStatusPayload message){
        if(net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof BrickStudioScreen studio){
            studio.status=message.message();
            if(message.finished()){
                studio.exportPending=false;
                studio.clearAndInit();
            }
        }
    }

    /** Own the whole background: no second blurred UI layer over labels. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Intentionally empty. render() draws a completely opaque background.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // The screen's scaled dimensions are fixed until Minecraft calls init() on resize.
        int sx=layout.stageX(), ex=layout.stageRight();
        int top=layout.stageTop(), bottom=layout.stageBottom();
        ctx.fill(0,0,width,height,0xFF0B111A);
        if(!fullscreenStage)ctx.fill(4,23,layout.panelX()+layout.panelW()+2,bottom+3,0xFF162330);
        ctx.fill(sx,top,ex,bottom,0xFF0B1B27);
        ctx.fill(sx,top,ex,top+1,0xFF345267);
        ctx.fill(0,layout.footerY()-3,width,height,0xFF192A39);
        text(ctx,"BRICKCRAFT  /  STUDIO",9,8,0xFFE4F4FF,
                Math.max(12,width-(width>=430?272:112)));
        if(!fullscreenStage){
            int tabX=layout.panelX()+3,tabWidth=(layout.panelW()-10)/3;
            ctx.fill(tabX+tab.ordinal()*(tabWidth+2),48,
                    tabX+tab.ordinal()*(tabWidth+2)+tabWidth,50,0xFF55D3D3);
        }
        int active=(tool==Tool.POSER?0:tool==Tool.DEPLACER?1:2);
        int tw=(layout.stageWidth()-4)/3;
        int highlightX=layout.stageX()+active*(tw+2);
        ctx.fill(highlightX,48,highlightX+tw,50,0xFF55D3D3);

        // Scissor sidebar content so no label can run behind the action row.
        if(!fullscreenStage){
            ctx.enableScissor(layout.panelX(),53,layout.panelX()+layout.panelW()+2,layout.footerY()-4);
            switch(tab) {
                case PIECES -> renderPiecesPanel(ctx);
                case COULEURS -> renderColorsPanel(ctx);
                case POSITION -> renderPositionPanel(ctx);
            }
            ctx.disableScissor();
        }

        ctx.enableScissor(sx+2,top+2,ex-2,bottom-2);
        String toolLabel=switch(tool){
            case POSER -> BrickI18n.tr("Poser");
            case DEPLACER -> BrickI18n.tr("Bouger");
            case EFFACER -> BrickI18n.tr("Effacer");
        };
        text(ctx,BrickI18n.fmt("brickcraft.v251.studio.stats",grid(),grid(),ASSEMBLY.size(),
                StudioAssembly.MAX_PIECES,toolLabel),sx+7,top+6,0xFFB7D8ED,ex-sx-12);
        text(ctx,"Gauche : outil | Droit : orbite | Molette : zoom | F : cadrer",sx+7,top+18,0xFF789AAB,ex-sx-12);
        ctx.enableScissor(sx+2,top+32,ex-2,bottom-3);
        renderViewport(ctx,mouseX,mouseY);
        ctx.disableScissor();
        text(ctx,"X="+cursorX+" Z="+cursorZ+" | "+Math.round(zoom*100)+"% | "+(autoSnap?"AUTO":"H="+BrickSelection.layer),
                sx+7,bottom-14,0xFFE4D29A,ex-sx-12);
        ctx.disableScissor();

        text(ctx,status,9,height-16,0xFFE4D29A,width-16);
        // Avoid stale disabled buttons after an edit; rebuild is not needed for every placement.
        for(int i=0;i<footerActions.size();i++){
            footerActions.get(i).active=switch(i){
                case 0 -> ASSEMBLY.canUndo();
                case 1 -> ASSEMBLY.canRedo();
                case 2 -> selected>=0 && ASSEMBLY.get(selected)!=null;
                case 3 -> exportPending || !ASSEMBLY.isEmpty();
                default -> true;
            };
        }
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void renderPiecesPanel(DrawContext ctx) {
        List<BrickKind> kinds=ldrawTab?List.of():BrickCatalog.search(searchQuery);
        List<LDrawLibrary.PartInfo> parts=ldrawTab?LDrawLibrary.search(searchQuery,500):List.of();
        int count=ldrawTab?parts.size():kinds.size();
        // Never paint the counter over the catalogue switch button (y=52..68).
        text(ctx,BrickI18n.fmt(ldrawTab?"brickcraft.v241.studio.parts.ldraw":"brickcraft.v241.studio.parts.classic",
                count==0?0:shapePage*6+1,Math.min(count,(shapePage+1)*6),count),
                layout.panelX()+6,207,0xFF9DCCDD,layout.panelW()-12);
        if(ldrawTab && count==0) {
            int x=layout.panelX()+6;
            int width=layout.panelW()-12;
            if(!LDrawLibrary.available() || LDrawLibrary.size()==0) {
                text(ctx,"Bibliotheque LDraw absente",x,110,0xFFFFC18B,width);
                text(ctx,"complete.zip dans",x,128,0xFFBBD8E8,width);
                text(ctx,"config/brickcraft/",x,144,0xFFBBD8E8,width);
                text(ctx,"Puis : Recharger LDraw",x,162,0xFF8EDBE1,width);
            } else {
                text(ctx,"Aucune piece trouvee",x,110,0xFFFFC18B,width);
                text(ctx,"Efface la recherche",x,128,0xFFBBD8E8,width);
            }
        }
        int index=ldrawTab?parts.indexOf(LDrawLibrary.info(BrickSelection.partId)):
                kinds.indexOf(BrickSelection.kind);
        index-=shapePage*6;
        int panel=layout.panelW()-6,buttonW=(panel-8)/2;
        if(index>=0 && index<6) {
            int row=index/2,col=index%2;
            int px=layout.panelX()+5+col*(buttonW+4),py=100+row*27;
            int c=0xFF55D3D3;
            ctx.fill(px-2,py-2,px+buttonW+2,py,c);
            ctx.fill(px-2,py+23,px+buttonW+2,py+25,c);
            ctx.fill(px-2,py,px,py+23,c);
            ctx.fill(px+buttonW,py,px+buttonW+2,py+23,c);
        }
        text(ctx,"Selection",layout.panelX()+6,224,0xFF85A7BA,layout.panelW()-12);
        text(ctx,BrickSelection.selectedName(),layout.panelX()+6,240,0xFFFFFFFF,layout.panelW()-12);
        text(ctx,"Angle : "+BrickSelection.angle+" deg / "+BrickSelection.step()+" deg",
                layout.panelX()+6,256,0xFF9BBECC,layout.panelW()-12);
        text(ctx,"X="+BrickSelection.pitch+" Z="+BrickSelection.roll+" | H="+BrickSelection.layer,
                layout.panelX()+6,272,0xFF9BBECC,layout.panelW()-12);
        text(ctx,"Couleur : "+colorName(),layout.panelX()+6,288,0xFFF4D9A1,layout.panelW()-12);
        ctx.fill(layout.panelX()+8,308,layout.panelX()+38,320,0xFF000000|BrickSelection.rgb);
    }

    private String colorName() {return BrickSelection.colorName();}

    private int swatchSize() {
        return Math.min(29,Math.max(19,(layout.panelW()-34)/4));
    }

    private void renderColorsPanel(DrawContext ctx) {
        java.util.List<LDrawLibrary.Colour> palette=studioColors();
        boolean has= !LDrawLibrary.colors().isEmpty();
        int total=has?palette.size():BrickPalette.COLORS.length;
        int pages=Math.max(1,(total+11)/12);
        colorPage=Math.min(colorPage,pages-1);
        text(ctx,has?"LDRAW : "+total+" couleurs":"Palette provisoire (installe LDraw)",
                layout.panelX()+6,96,0xFF9DCCDD,layout.panelW()-12);
        int sw=swatchSize(),gap=3,originX=layout.panelX()+9,originY=111;
        for(int i=0;i<12 && colorPage*12+i<total;i++){
            int index=colorPage*12+i;
            LDrawLibrary.Colour c=has?palette.get(index):null;
            int rgb=has?c.rgb():BrickPalette.COLORS[index];
            int alpha=has?c.alpha():255;
            int x=originX+(i%4)*(sw+gap),y=originY+(i/4)*(sw+gap);
            boolean selected=has?c.code()==BrickSelection.colorCode:BrickSelection.colorCode<0&&rgb==BrickSelection.rgb;
            ctx.fill(x-2,y-2,x+sw+2,y+sw+2,selected?0xFFFFFFFF:0xFF43596C);
            ctx.fill(x,y,x+sw,y+sw,0xFFD2D5D8);
            ctx.fill(x,y,x+sw/2,y+sw/2,0xFF66727D);
            ctx.fill(x+sw/2,y+sw/2,x+sw,y+sw,0xFF66727D);
            ctx.fill(x,y,x+sw,y+sw,(alpha<<24)|rgb);
        }
        text(ctx,BrickI18n.fmt("brickcraft.v241.page",colorPage+1,pages),layout.panelX()+6,229,0xFF9DCCDD,layout.panelW()-12);
        text(ctx,BrickSelection.colorName(),layout.panelX()+6,245,0xFFF7F7F7,layout.panelW()-12);
        LDrawLibrary.Colour chosen=LDrawLibrary.color(BrickSelection.colorCode);
        if(chosen!=null){
            text(ctx,"Code "+chosen.code()+" / "+chosen.finish(),layout.panelX()+6,261,0xFF9DBDCD,layout.panelW()-12);
            text(ctx,"Alpha "+chosen.alpha()+" / 255",layout.panelX()+6,278,0xFF9DBDCD,layout.panelW()-12);
        }else text(ctx,String.format("#%06X",BrickSelection.rgb),layout.panelX()+6,264,0xFF9DBDCD,layout.panelW()-12);
        text(ctx,"Clic : selectionner / Bouger : recolorer",layout.panelX()+6,302,0xFF7A9CAC,layout.panelW()-12);
    }

    private void renderPositionPanel(DrawContext ctx) {
        text(ctx,"REGLAGES",layout.panelX()+6,53,0xFF9DCCDD,layout.panelW()-12);
        if(layout.footerY()>=286) {
            text(ctx,"Y="+BrickSelection.angle+" X="+BrickSelection.pitch+" Z="+BrickSelection.roll,
                    layout.panelX()+6,278,0xFFE4D29A,layout.panelW()-12);
        }
    }

    private void text(DrawContext ctx,String str,int x,int y,int rgb,int maxWidth) {
        str=BrickI18n.tr(str);
        if(maxWidth<10)return;
        // Text length is measured, so labels cannot paint over the viewport or footer.
        if(textRenderer.getWidth(str)>maxWidth) {
            String suffix="...";
            int end=str.length();
            while(end>0 && textRenderer.getWidth(str.substring(0,end)+suffix)>maxWidth)end--;
            str=str.substring(0,end)+suffix;
        }
        ctx.drawTextWithShadow(textRenderer,str,x,y,rgb);
    }

    private int centerX() {return (layout.stageX()+layout.stageRight())/2;}
    private int centerY() {return (layout.stageTop()+32+layout.stageBottom())/2+9;}
    private float baseZoom() {
        float byWidth=(layout.stageWidth()-16)/18f;
        float byHeight=(layout.stageHeight()-44)/12f;
        return Math.max(4f,Math.min(byWidth,byHeight))*zoom;
    }
    private record P(int x,int y) {}
    private P project(float x,float y,float z) {
        float units=baseZoom();
        double u=(x-cameraX)*Math.cos(orbit)-(z-cameraZ)*Math.sin(orbit);
        double d=(x-cameraX)*Math.sin(orbit)+(z-cameraZ)*Math.cos(orbit);
        return new P((int)(centerX()+panX+u*units),
                (int)(centerY()+panY+d*units*.45f-(y-cameraY)*units*.9f));
    }
    private double depth(float x,float z){return x*Math.sin(orbit)+z*Math.cos(orbit);}

    private boolean inView(BrickPiece piece) {
        double[][] vertices=piece.vertices();
        int xMin=Integer.MAX_VALUE,xMax=Integer.MIN_VALUE,yMin=Integer.MAX_VALUE,yMax=Integer.MIN_VALUE;
        for(double[] v:vertices){
            P a=project((float)v[0],(float)v[1],(float)v[2]);
            xMin=Math.min(xMin,a.x);xMax=Math.max(xMax,a.x);
            yMin=Math.min(yMin,a.y);yMax=Math.max(yMax,a.y);
        }
        return xMax>=layout.stageX() && xMin<=layout.stageRight() &&
                yMax>=layout.stageTop()+28 && yMin<=layout.stageBottom();
    }

    // Liang-Barsky screen-space clipping: large canvases must never rasterize
    // tens of thousands of offscreen pixels for each grid line.
    private void gridLine(DrawContext ctx,P a,P b,int color) {
        double[] t={0,1};
        double x0=a.x,y0=a.y,dx=b.x-a.x,dy=b.y-a.y;
        double[] p={-dx,dx,-dy,dy};
        double[] q={x0-layout.stageX(),layout.stageRight()-x0,
                y0-(layout.stageTop()+32),layout.stageBottom()-y0};
        for(int i=0;i<4;i++){
            if(Math.abs(p[i])<1e-10){if(q[i]<0)return;continue;}
            double r=q[i]/p[i];
            if(p[i]<0)t[0]=Math.max(t[0],r);else t[1]=Math.min(t[1],r);
            if(t[0]>t[1])return;
        }
        P aa=new P((int)Math.round(x0+dx*t[0]),(int)Math.round(y0+dy*t[0]));
        P bb=new P((int)Math.round(x0+dx*t[1]),(int)Math.round(y0+dy*t[1]));
        line(ctx,aa,bb,color);
    }

    /** Optional connector markers, rendered only for one hovered/selected part. */
    private void drawConnectors(DrawContext ctx, BrickPiece piece) {
        int count=0;
        for(ConnectorSnapper.Point c:ConnectorSnapper.connectors(piece)) {
            if(++count>256)break;
            P pt=project((float)c.x(),(float)c.y(),(float)c.z());
            if(pt.x()<layout.stageX()+2||pt.x()>layout.stageRight()-2 ||
                    pt.y()<layout.stageTop()+32||pt.y()>layout.stageBottom()-2)continue;
            int col=c.type()==ConnectorSnapper.Type.STUD?0xFF62F5D0:0xFFFFC16C;
            ctx.fill(pt.x()-2,pt.y()-2,pt.x()+3,pt.y()+3,0xBB071721);
            ctx.fill(pt.x()-1,pt.y()-1,pt.x()+2,pt.y()+2,col);
        }
    }

    private void renderViewport(DrawContext ctx, int mx, int my) {
        int grid=grid();
        for(int k=0;k<=grid;k++) {
            int col=k%16==0?0xFF447892:k%4==0?0xFF34596C:0xFF244459;
            gridLine(ctx,project(k,0,0),project(k,0,grid),col);
            gridLine(ctx,project(0,0,k),project(grid,0,k),col);
        }
        List<BrickPiece> ordered = new ArrayList<>(ASSEMBLY.pieces());
        ordered.sort(Comparator.comparingDouble(p -> depth(p.x()+p.width()/2f,
                p.z()+p.length()/2f)+p.y()*.04));
        for (BrickPiece p:ordered) if(inView(p))drawBrick(ctx,p,false);
        if(connectorGuides && layout.insideStage(mx,my)) {
            Cursor hover=resolveCursor(mx,my);
            BrickPiece target=ASSEMBLY.get(selected>=0?selected:hover.index);
            if(target!=null)drawConnectors(ctx,target);
        }
        if (selected>=0 && tool==Tool.DEPLACER) {
            BrickPiece chosen=ASSEMBLY.get(selected);
            if (chosen!=null && inView(chosen))drawOutline(ctx,chosen,0xFFFFD36F);
        }
        if(layout.insideStage(mx,my)) {
            Cursor pointer=resolveCursor(mx,my);
            cursorX=pointer.x;cursorZ=pointer.z;
            if(tool==Tool.POSER){
                BrickPiece ghost=candidate(-1,null);
                boolean allowed=ASSEMBLY.canPlace(ghost,-1);
                if(inView(ghost))drawBrick(ctx,allowed?ghost:ghost.withColor(0xE74F55),true);
            } else if(tool==Tool.EFFACER || (tool==Tool.DEPLACER && selected<0)) {
                BrickPiece p=ASSEMBLY.get(pointer.index);
                if(p!=null && inView(p))drawOutline(ctx,p,tool==Tool.EFFACER?0xFFFF5D5D:0xFF70DFFF);
            } else if(tool==Tool.DEPLACER && selected>=0){
                BrickPiece source=ASSEMBLY.get(selected);
                if(source!=null){
                    BrickPiece ghost=candidate(selected,source);
                    boolean allowed=ASSEMBLY.canPlace(ghost,selected);
                    if(inView(ghost))drawBrick(ctx,allowed?ghost:ghost.withColor(0xE74F55),true);
                }
            }
        }
    }

    /** Focus the complete construction while respecting the current viewport's aspect ratio. */
    private void frameAll(){
        double minX=0,minZ=0,maxX=grid(),maxZ=grid(),minY=0,maxY=0;
        if(!ASSEMBLY.isEmpty()){
            minX=Double.POSITIVE_INFINITY;minZ=Double.POSITIVE_INFINITY;
            maxX=Double.NEGATIVE_INFINITY;maxZ=Double.NEGATIVE_INFINITY;
            minY=Double.POSITIVE_INFINITY;maxY=Double.NEGATIVE_INFINITY;
            for(BrickPiece piece:ASSEMBLY.pieces()){
                for(double[] v:piece.vertices()){
                    minX=Math.min(minX,v[0]);maxX=Math.max(maxX,v[0]);
                    minY=Math.min(minY,v[1]);maxY=Math.max(maxY,v[1]);
                    minZ=Math.min(minZ,v[2]);maxZ=Math.max(maxZ,v[2]);
                }
            }
        }
        cameraX=(float)((minX+maxX)/2);cameraZ=(float)((minZ+maxZ)/2);
        cameraY=(float)((minY+maxY)/2);
        panX=0;panY=0;zoom=1f;
        // Measure the projected bounds at unit zoom and fit with a useful margin.
        int minPx=Integer.MAX_VALUE,maxPx=Integer.MIN_VALUE,minPy=Integer.MAX_VALUE,maxPy=Integer.MIN_VALUE;
        double[] xx={minX,maxX}, yy={minY,maxY}, zz={minZ,maxZ};
        for(double x:xx)for(double y:yy)for(double z:zz){
            P a=project((float)x,(float)y,(float)z);
            minPx=Math.min(minPx,a.x);maxPx=Math.max(maxPx,a.x);
            minPy=Math.min(minPy,a.y);maxPy=Math.max(maxPy,a.y);
        }
        double sx=(layout.stageWidth()-42.)/Math.max(1,maxPx-minPx);
        double sy=(layout.stageHeight()-88.)/Math.max(1,maxPy-minPy);
        zoom=(float)Math.clamp(Math.min(sx,sy),MIN_ZOOM,MAX_ZOOM);
        panX=-((maxPx+minPx)/2f-centerX())*zoom;
        panY=-((maxPy+minPy)/2f-centerY())*zoom;
        status=ASSEMBLY.isEmpty()?BrickI18n.fmt("brickcraft.v251.studio.camera.grid"):BrickI18n.fmt("brickcraft.v251.studio.camera.parts",ASSEMBLY.size());
    }
    private void cycleGrid(){
        int size=ASSEMBLY.nextGridSize();
        if(!ASSEMBLY.setGridSize(size)){
            status="Impossible de reduire le plateau : des pieces seraient hors limites";
            return;
        }
        status=BrickI18n.fmt("brickcraft.v252.studio.grid_changed",size,size);
        clearAndInit();
    }
    private void saveProject(){
        try{StudioProjectIO.save(projectPath(),ASSEMBLY);
            status=BrickI18n.fmt("brickcraft.v252.studio.saved",ASSEMBLY.size());
        }catch(IOException e){status="Echec sauvegarde : "+e.getMessage();}
    }
    private void openProjectImporter(){
        if(client!=null)client.setScreen(new ProjectImportScreen(this));
    }
    /** Replace the workshop only after a fully validated, lossless conversion. */
    boolean acceptImportedProject(int size,List<BrickPiece> pieces){
        if(!ASSEMBLY.loadProject(size,pieces))return false;
        selected=-1;tool=Tool.POSER;tab=Tab.PIECES;
        frameAll();status=BrickI18n.fmt("brickcraft.v252.studio.imported",pieces.size(),size);
        clearAndInit();return true;
    }
    private void loadProject(){
        try{
            StudioProjectIO.Project project=StudioProjectIO.read(projectPath());
            if(!ASSEMBLY.loadProject(project.grid(),project.pieces())){
                status="Chargement refuse : piece manquante, collision ou limites";return;
            }
            selected=-1;cursorX=project.grid()/2;cursorZ=project.grid()/2;
            frameAll();clearAndInit();
            status=BrickI18n.fmt("brickcraft.v252.studio.loaded",ASSEMBLY.size(),grid(),grid());
        }catch(IOException e){status="Echec chargement : "+e.getMessage();}
    }

    private P[] corners(BrickPiece piece,float y) {
        double[][] world=piece.corners();
        P[] p=new P[4];
        for(int i=0;i<4;i++)p[i]=project((float)world[i][0],y,(float)world[i][1]);
        return p;
    }

    private void drawOutline(DrawContext ctx,BrickPiece piece,int color) {
        P[] p=topCorners(piece);
        for(int i=0;i<4;i++)line(ctx,p[i],p[(i+1)%4],color);
    }

    private record Cursor(int x,int z,int index) {}

    private Cursor resolveCursor(double mx,double my) {
        // Hit-test the visible top faces first; clicking onto the top of a brick
        // should not select a cell behind it on the ground plane.
        int best=-1;
        double depth=-Double.MAX_VALUE;
        int pickY=0;
        List<BrickPiece> pieces=ASSEMBLY.pieces();
        for (int i=0;i<pieces.size();i++) {
            BrickPiece p=pieces.get(i);
            float top=p.top()/5f;
            P[] cs=topCorners(p);
            if (pointInQuad(mx,my,cs[0],cs[1],cs[2],cs[3])) {
                double order=depth(p.x()+p.width()/2f,p.z()+p.length()/2f)+p.y()*.04;
                if (order>=depth) {depth=order;best=i;pickY=p.top();}
            }
        }
        // Inverse the current isometric projection on the picked top plane.
        float u=baseZoom();
        double xx=(mx-centerX()-panX)/u;
        double dd=(my-centerY()-panY+((pickY/5f)-cameraY)*u*.9f)/(u*.45f);
        int x=(int)Math.floor(xx*Math.cos(orbit)+dd*Math.sin(orbit)+cameraX);
        int z=(int)Math.floor(-xx*Math.sin(orbit)+dd*Math.cos(orbit)+cameraZ);
        x=Math.clamp(x,0,grid()-1);
        z=Math.clamp(z,0,grid()-1);
        if(best>=0) {
            BrickPiece p=pieces.get(best);
            x=Math.clamp(x,p.x(),p.x()+p.width()-1);
            z=Math.clamp(z,p.z(),p.z()+p.length()-1);
        } else {
            // Fallback selection by footprint when a side is clicked.
            for (int i=pieces.size()-1;i>=0;i--) {
                BrickPiece p=pieces.get(i);
                if(x>=p.x() && x<p.x()+p.width() && z>=p.z() && z<p.z()+p.length() &&
                        BrickPiece.atAngle(BrickKind.PLATE_1X1,0,x,0,z,0).horizontalOverlaps(p)) {
                    best=i;break;
                }
            }
        }
        return new Cursor(x,z,best);
    }

    private static boolean pointInQuad(double px,double py,P a,P b,P c,P d) {
        return triangle(px,py,a,b,c) || triangle(px,py,a,c,d);
    }
    private static boolean triangle(double px,double py,P a,P b,P c) {
        double d1=(px-b.x)*(a.y-b.y)-(a.x-b.x)*(py-b.y);
        double d2=(px-c.x)*(b.y-c.y)-(b.x-c.x)*(py-c.y);
        double d3=(px-a.x)*(c.y-a.y)-(c.x-a.x)*(py-a.y);
        return !((d1<0 || d2<0 || d3<0) && (d1>0 || d2>0 || d3>0));
    }

    private P[] topCorners(BrickPiece p) {
        float high=p.top()/5f;
        P[] corners=corners(p,high);
        if(p.kind().slope) {
            double[][] xy=p.corners();
            for(int i=2;i<4;i++)corners[i]=project((float)xy[i][0],p.y()/5f+p.height()/5f*.14f,(float)xy[i][1]);
        }
        return corners;
    }

    private void drawTiltedBrick(DrawContext ctx,BrickPiece p,boolean ghost){
        double[][] coords=p.vertices();
        P[] v=new P[8];
        for(int i=0;i<8;i++)v[i]=project((float)coords[i][0],(float)coords[i][1],(float)coords[i][2]);
        int[][] faces={{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}};
        double[] shades={.65,.83,.70,1.15,.80,.78};
        for(int i=0;i<faces.length;i++){
            int[] f=faces[i];face(ctx,new P[]{v[f[0]],v[f[1]],v[f[2]],v[f[3]]},
                    shade(p.rgb(),shades[i],previewAlpha(p,ghost)));
        }
    }
    private void drawBrick(DrawContext ctx,BrickPiece p,boolean ghost) {
        if(p.kind()==BrickKind.LDRAW_PART) {
            if(drawLDrawMesh(ctx,p,ghost)) return;
        }
        if(p.tilted()){drawTiltedBrick(ctx,p,ghost);return;}
        float low=p.y()/5f,high=p.top()/5f;
        P[] bottom=corners(p,low),top=topCorners(p);
        int alpha=previewAlpha(p,ghost),rgb=p.rgb();
        final double[] shades={.68,.75,.84,.64};
        for(int i=0;i<4;i++) {
            int j=(i+1)%4;
            face(ctx,new P[]{bottom[i],bottom[j],top[j],top[i]},
                    LDrawLibrary.surfaceTint(shade(rgb,shades[i],alpha),p.colorCode(),
                            i==0?-1:i==2?1:0,0,i==1?1:i==3?-1:0,i));
        }
        face(ctx,top,LDrawLibrary.surfaceTint(shade(rgb,1.12,alpha),p.colorCode(),0,1,0,200));
        if(p.kind().studs) {
            for(int i=0;i<p.kind().width;i++) for(int j=0;j<p.kind().length;j++) {
                double[] xy=p.stud(i,j);
                P stud=project((float)xy[0],high+.09f,(float)xy[1]);
                int radius=Math.max(2,(int)(baseZoom()*.11f));
                ctx.fill(stud.x-radius,stud.y-radius,stud.x+radius,stud.y+radius,
                        shade(rgb,1.32,alpha));
            }
        }
    }

    private record MeshTri(P a,P b,P c,double depth,int color) {}

    private boolean drawLDrawMesh(DrawContext ctx, BrickPiece p, boolean ghost){
        LDrawLibrary.Mesh mesh=LDrawLibrary.mesh(p.partId());
        if(mesh==null || mesh.isEmpty()) return false;
        float[] xyz=mesh.xyz();
        int[] colors=mesh.colors();
        double[][] axes=p.axes();
        double[] center=p.center();
        double halfW=mesh.width()/2.0, halfH=mesh.height()/2.0, halfD=mesh.depth()/2.0;
        int alpha=previewAlpha(p,ghost);
        double viewX=Math.cos(orbit), viewZ=Math.sin(orbit);
        java.util.ArrayList<MeshTri> tris=new java.util.ArrayList<>(mesh.triangles());
        for(int i=0;i<mesh.triangles();i++){
            int base=i*9;
            double[][] world=new double[3][3];
            for(int v=0;v<3;v++){
                double lx=xyz[base+v*3]-halfW;
                double ly=xyz[base+v*3+1]-halfH;
                double lz=xyz[base+v*3+2]-halfD;
                world[v][0]=center[0]+lx*axes[0][0]+ly*axes[1][0]+lz*axes[2][0];
                world[v][1]=center[1]+lx*axes[0][1]+ly*axes[1][1]+lz*axes[2][1];
                world[v][2]=center[2]+lx*axes[0][2]+ly*axes[1][2]+lz*axes[2][2];
            }
            double ux=world[1][0]-world[0][0], uy=world[1][1]-world[0][1], uz=world[1][2]-world[0][2];
            double vx=world[2][0]-world[0][0], vy=world[2][1]-world[0][1], vz=world[2][2]-world[0][2];
            double nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
            double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(len<1e-9) continue;
            nx/=len; ny/=len; nz/=len;
            double brightness=0.58 + Math.max(0.0, ny)*0.34 + Math.max(0.0, -(nx*viewX+nz*viewZ))*0.18;
            int argb=LDrawLibrary.meshColor(colors[i],p.colorCode(),p.rgb());
            int rgb=argb&0xFFFFFF;
            int baseAlpha=argb>>>24;
            int code=(colors[i]==16||colors[i]==24)?p.colorCode():colors[i];
            int color=LDrawLibrary.surfaceTint(shade(rgb,brightness,ghost?Math.min(110,baseAlpha):baseAlpha),
                    code,nx,ny,nz,i);
            P a=project((float)world[0][0],(float)world[0][1],(float)world[0][2]);
            P b=project((float)world[1][0],(float)world[1][1],(float)world[1][2]);
            P c=project((float)world[2][0],(float)world[2][1],(float)world[2][2]);
            double depth=(world[0][0]+world[1][0]+world[2][0])*Math.sin(orbit)
                    +(world[0][2]+world[1][2]+world[2][2])*Math.cos(orbit)
                    +(world[0][1]+world[1][1]+world[2][1])*0.15;
            tris.add(new MeshTri(a,b,c,depth,color));
        }
        tris.sort(java.util.Comparator.comparingDouble(MeshTri::depth));
        for(MeshTri t:tris){
            face(ctx,new P[]{t.a(),t.b(),t.c()},t.color());
        }
        return true;
    }
    private static int previewAlpha(BrickPiece piece,boolean ghost){
        int alpha=LDrawLibrary.argb(piece.colorCode(),piece.rgb())>>>24;
        return ghost?Math.min(alpha,110):alpha;
    }
    private static int shade(int rgb,double factor,int alpha) {
        int r=Math.min(255,(int)(((rgb>>16)&255)*factor));
        int g=Math.min(255,(int)(((rgb>>8)&255)*factor));
        int b=Math.min(255,(int)((rgb&255)*factor));
        return (alpha<<24)|(r<<16)|(g<<8)|b;
    }
    private static void line(DrawContext ctx,P a,P b,int color) {
        int dx=Math.abs(b.x-a.x),dy=Math.abs(b.y-a.y);
        int sx=a.x<b.x?1:-1,sy=a.y<b.y?1:-1,err=dx-dy,x=a.x,y=a.y;
        while(true) {
            ctx.fill(x,y,x+1,y+1,color);
            if(x==b.x && y==b.y)break;
            int e2=2*err;
            if(e2>-dy) {err-=dy;x+=sx;}
            if(e2<dx) {err+=dx;y+=sy;}
        }
    }
    private static void face(DrawContext ctx,P[] pts,int color) {
        int ymin=Integer.MAX_VALUE,ymax=Integer.MIN_VALUE;
        for(P p:pts) {ymin=Math.min(ymin,p.y);ymax=Math.max(ymax,p.y);}
        ymin=Math.max(0,ymin);ymax=Math.min(ctx.getScaledWindowHeight()-1,ymax);
        for(int y=ymin;y<=ymax;y++) {
            float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
            for(int i=0;i<pts.length;i++) {
                P a=pts[i],b=pts[(i+1)%pts.length];
                if(a.y==b.y || y<Math.min(a.y,b.y) || y>=Math.max(a.y,b.y))continue;
                float xx=a.x+(float)(y-a.y)*(b.x-a.x)/(b.y-a.y);
                min=Math.min(min,xx);max=Math.max(max,xx);
            }
            if(max>=min)ctx.fill((int)Math.floor(min),y,(int)Math.ceil(max)+1,y+1,color);
        }
    }

    @Override public boolean mouseClicked(double mx,double my,int button) {
        if (!fullscreenStage && button==0 && tab==Tab.COULEURS &&
                mx>=layout.panelX() && mx<layout.panelX()+layout.panelW()) {
            java.util.List<LDrawLibrary.Colour> colors=studioColors();
            boolean has=!LDrawLibrary.colors().isEmpty();
            int sw=swatchSize(),gap=3,originX=layout.panelX()+9,originY=111;
            for(int i=0;i<12 && colorPage*12+i<(has?colors.size():BrickPalette.COLORS.length);i++){
                int x=originX+(i%4)*(sw+gap),y=originY+(i/4)*(sw+gap);
                if(mx>=x && mx<x+sw && my>=y && my<y+sw){
                    if(has)BrickSelection.setColor(colors.get(colorPage*12+i));
                    else BrickSelection.setRgb(BrickPalette.COLORS[colorPage*12+i]);
                    BrickPiece piece=ASSEMBLY.get(selected);
                    if(tool==Tool.DEPLACER && piece!=null){
                        BrickPiece changed=has?piece.withLDrawColor(BrickSelection.colorCode):piece.withColor(BrickSelection.rgb);
                        ASSEMBLY.replace(selected,changed);
                        status=BrickI18n.fmt("brickcraft.v252.studio.color_repainted",BrickSelection.colorName());
                    }else status=BrickI18n.fmt("brickcraft.v252.studio.color_chosen",BrickSelection.colorName());
                    return true;
                }
            }
        }
        if(layout.insideStage(mx,my)) {
            if(button==2){panning=true;return true;}
            if(button==1){
                if((GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_LEFT_SHIFT)==GLFW.GLFW_PRESS)||
                   (GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_RIGHT_SHIFT)==GLFW.GLFW_PRESS))panning=true;
                else orbitDragging=true;
                return true;
            }
            if(button==0) {
                Cursor pointer=resolveCursor(mx,my);
                cursorX=pointer.x;cursorZ=pointer.z;
                switch(tool) {
                    case POSER -> add();
                    case EFFACER -> {
                        if (pointer.index>=0 && ASSEMBLY.remove(pointer.index)) {
                            selected=-1;status="Piece effacee (Ctrl+Z pour annuler)";
                        } else status="Clique une piece pour l'effacer";
                    }
                    case DEPLACER -> {
                        if(selected<0) {
                            selected=pointer.index;
                            status=selected>=0 ? "Piece selectionnee : clique sa destination"
                                    : "Clique une piece pour la selectionner";
                        } else {
                            BrickPiece old=ASSEMBLY.get(selected);
                            if(old!=null && pointer.index==selected) {
                                selected=-1;status="Selection annulee";
                            } else if(old!=null) {
                                BrickPiece dest=candidate(selected,old);
                                if (ASSEMBLY.replace(selected,dest)) {
                                    selected=-1;status="Piece deplacee (Ctrl+Z pour annuler)";
                                } else status="Deplacement impossible : collision ou bord";
                            } else selected=-1;
                        }
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        if(button==1 && orbitDragging) {orbit+=(float)dx*.013f;return true;}
        if((button==2 || button==1) && panning){
            panX+=(float)dx;panY+=(float)dy;return true;
        }
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button) {
        if(button==1 && orbitDragging){orbitDragging=false;return true;}
        if((button==2 || button==1) && panning){panning=false;return true;}
        return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(layout.insideStage(mx,my)) {
            zoom=Math.clamp(zoom*(float)Math.pow(1.15,vertical),MIN_ZOOM,MAX_ZOOM);
            return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        boolean ctrl=(mods & GLFW.GLFW_MOD_CONTROL)!=0;
        if(key==GLFW.GLFW_KEY_TAB){fullscreenStage=!fullscreenStage;clearAndInit();return true;}
        if(ctrl && key==GLFW.GLFW_KEY_I){openProjectImporter();return true;}
        if(searchField!=null && searchField.isFocused()){
            if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER){
                searchField.setFocused(false);setFocused(null);return true;
            }
            return super.keyPressed(key,scan,mods);
        }
        if(ctrl && key==GLFW.GLFW_KEY_S){saveProject();return true;}
        if(ctrl && key==GLFW.GLFW_KEY_O){loadProject();return true;}
        if(key==GLFW.GLFW_KEY_J){
            connectorGuides=!connectorGuides;
            status=BrickI18n.fmt(connectorGuides?"brickcraft.v252.studio.connectors_show":"brickcraft.v252.studio.connectors_hide");
            return true;
        }
        if(key==GLFW.GLFW_KEY_F){frameAll();return true;}
        if(key==GLFW.GLFW_KEY_HOME){
            cameraX=grid()/2f;cameraZ=grid()/2f;cameraY=0;
            panX=panY=0;zoom=1f;status="Camera recentree";return true;
        }
        if(ctrl && key==GLFW.GLFW_KEY_B){cycleGrid();return true;}
        if(ctrl && key==GLFW.GLFW_KEY_Z) {
            if((mods&GLFW.GLFW_MOD_SHIFT)!=0)redo();else undo();return true;
        }
        if(ctrl && key==GLFW.GLFW_KEY_Y) {redo();return true;}
        if(ctrl && key==GLFW.GLFW_KEY_C) {copySelection();return true;}
        if(ctrl && key==GLFW.GLFW_KEY_X) {
            copySelection();if(clipboard!=null && selected>=0 && ASSEMBLY.remove(selected))selected=-1;
            return true;
        }
        if(ctrl && key==GLFW.GLFW_KEY_V) {pasteSelection();return true;}
        if(ctrl && key==GLFW.GLFW_KEY_D) {duplicate();return true;}
        if(key==GLFW.GLFW_KEY_Q) {orbit-=.2f;return true;}
        if(key==GLFW.GLFW_KEY_E) {orbit+=.2f;return true;}
        if(key==GLFW.GLFW_KEY_R) {rotate((mods & GLFW.GLFW_MOD_SHIFT)!=0?-1:1);return true;}
        if(key==GLFW.GLFW_KEY_X){tilt(true,1);return true;}
        if(key==GLFW.GLFW_KEY_Z){tilt(false,1);return true;}
        if(key==GLFW.GLFW_KEY_H){resetRotation();return true;}
        if(key==GLFW.GLFW_KEY_P) {chooseTool(Tool.POSER);return true;}
        if(key==GLFW.GLFW_KEY_M) {chooseTool(Tool.DEPLACER);return true;}
        if(key==GLFW.GLFW_KEY_G) {chooseTool(Tool.EFFACER);return true;}
        if(key==GLFW.GLFW_KEY_DELETE) {
            if (selected>=0 && ASSEMBLY.remove(selected)) {
                selected=-1;status="Piece effacee";
            } else undo();
            return true;
        }
        if(key>=GLFW.GLFW_KEY_1 && key<=GLFW.GLFW_KEY_6) {
            int index=shapePage*6+key-GLFW.GLFW_KEY_1;
            if(ldrawTab){
                List<LDrawLibrary.PartInfo> matches=LDrawLibrary.search(searchQuery,500);
                if(index>=matches.size())return true;
                BrickSelection.kind=BrickKind.LDRAW_PART;
                BrickSelection.partId=matches.get(index).id();
                if(searchQuery.trim().equals("6258572"))BrickSelection.setColor(LDrawLibrary.color(353));
            }else{
                List<BrickKind> matches=BrickCatalog.search(searchQuery);
                if(index>=matches.size())return true;
                BrickSelection.kind=matches.get(index);BrickSelection.partId="";
            }
            selected=-1;tool=Tool.POSER;tab=Tab.PIECES;clearAndInit();
            return true;
        }
        if(key==GLFW.GLFW_KEY_PAGE_UP) {
            BrickSelection.layer=Math.min(StudioAssembly.MAX_TOP/2,BrickSelection.layer+1);return true;
        }
        if(key==GLFW.GLFW_KEY_PAGE_DOWN) {
            BrickSelection.layer=Math.max(0,BrickSelection.layer-1);return true;
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public void close(){
        try{
            StudioProjectIO.save(FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("studio_autosave.bcp"),ASSEMBLY);
        }catch(IOException ignored){}
        super.close();
    }
    @Override public boolean shouldPause() {return false;}
}
