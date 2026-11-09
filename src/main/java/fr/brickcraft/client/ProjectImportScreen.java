package fr.brickcraft.client;

import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.StudioProjectAnalysis;
import fr.brickcraft.importer.MissingPartsReport;
import fr.brickcraft.importer.PartReplacementEditor;
import fr.brickcraft.importer.MissingPartRemovalEditor;
import fr.brickcraft.importer.StudioCustomPartsInstaller;
import fr.brickcraft.importer.BrickCraftProjectConverter;
import fr.brickcraft.importer.PreciseProjectIO;
import fr.brickcraft.importer.PreciseLDrawGeometry;
import fr.brickcraft.importer.PreciseWorldModel;
import fr.brickcraft.core.BrickCapacityConfig;
import fr.brickcraft.net.PreciseExportPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Direct .io/.mpd/.ldr loader, with lossless diagnostics and a source-coordinate preview. */
public final class ProjectImportScreen extends Screen {
    private final BrickStudioScreen parent;
    private final Path folder=FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("imports");
    private List<Path> sources=List.of();
    private int index=0;
    private LDrawProjectReader.Result current;
    private StudioProjectAnalysis.Summary summary;
    private boolean dirtyPreview=false;
    private Path loadedFile=null;
    private String status="Depose un fichier .io, .mpd ou .ldr dans le dossier imports.";
    private double orbit=.72, zoom=1.0, panX=0, panY=0;
    private boolean orbiting=false,panning=false;
    private int lastX,lastY;
    /** 0=points, 1=oriented bounding boxes, 2=actual LDraw triangles with adaptive LOD. */
    private int previewMode=2;
    /** Only one compact task panel at a time. Tools are not mixed with file navigation. */
    private enum Panel { PROJET, VISUEL, ACTIONS }
    private Panel activePanel=Panel.PROJET;
    private int sideWidth(){
        return Math.min(Math.max(150,width-166),Math.max(150,Math.min(300,width*34/100)));
    }
    private int panelInformationTop(){
        return switch(activePanel){case PROJET->205;case VISUEL->151;case ACTIONS->210;};
    }
    // A bounded per-scene cache avoids decoding a huge .dat on every rendered frame.
    private final Map<String,LDrawLibrary.Mesh> meshes=new HashMap<>();
    private final Set<String> noMesh=new HashSet<>();
    private int cachedTriangles=0;
    private static final int MAX_CACHED_TRIANGLES=1_500_000;
    private static final int MAX_PART_TRIANGLES=10_000;
    private static final int MAX_RENDER_TRIANGLES=150_000;
    private final StudioPreviewGpu gpuPreview=new StudioPreviewGpu();
    /** The Free Editor embeds this view: its bottom area is not a debug HUD. */
    private boolean embeddedPreview=false;
    private String gpuSnapshot="";
    private boolean gpuPreviewEnabled=true;
    private static StudioPreviewGpu collectingGpu;
    private void invalidateGpu(){gpuSnapshot="";gpuPreview.release();}
    /** Warm the mesh lookup in small increments, then upload one 2D VBO. */
    private boolean warmGpuMeshes(){
        int[] budget={6};
        for(PreviewPart p:previewParts){
            String id=p.instance().partId();
            if(!meshes.containsKey(id)&&!noMesh.contains(id)){
                if(budget[0]<=0)return false;
                previewMesh(id,budget);
            }
        }
        return true;
    }
    void toggleGpuPreview(){gpuPreviewEnabled=!gpuPreviewEnabled;invalidateGpu();clearAndInit();
        status="Apercu Studio : "+(gpuPreviewEnabled?"GPU (VBO)":"CPU (secours)");}
    private void renderGpuPreview(DrawContext ctx,int a,int b,int c,int d,boolean detailed){
        String key=System.identityHashCode(previewParts)+"/"+a+"/"+b+"/"+c+"/"+d+
                "/"+orbit+"/"+zoom+"/"+panX+"/"+panY+"/"+cachedTriangles+"/"+detailed;
        if(detailed && !warmGpuMeshes()){
            renderDots(ctx,a,b,c,d);
            drawText(ctx,BrickI18n.fmt("brickcraft.v252.import.gpu_preparing",meshes.size()+noMesh.size()),a+5,b+5,c-a-10,0xFFFFD989);
            return;
        }
        key=key+"/ready";
        if(!key.equals(gpuSnapshot)){
            gpuPreview.begin();collectingGpu=gpuPreview;
            try{renderVolumes(ctx,a,b,c,d,detailed);}
            finally{collectingGpu=null;}
            try{gpuPreview.upload();gpuSnapshot=key;}
            catch(RuntimeException ex){
                gpuPreviewEnabled=false;gpuPreview.close();
                status=BrickI18n.fmt("brickcraft.v252.import.gpu_fallback","upload",ex.getClass().getSimpleName());
                renderVolumes(ctx,a,b,c,d,detailed);
                return;
            }
        }
        try{gpuPreview.draw(ctx,a,b,c,d);}
        catch(RuntimeException ex){gpuPreviewEnabled=false;gpuPreview.close();
            renderVolumes(ctx,a,b,c,d,detailed);
            status=BrickI18n.fmt("brickcraft.v252.import.gpu_fallback","render",ex.getClass().getSimpleName());return;}
        if(!embeddedPreview)
            drawText(ctx,BrickI18n.fmt("brickcraft.v251.import.gpu_stats",gpuPreview.triangles()),
                    a+5,d-14,c-a-10,0xFF9BD2C4);
    }
    private int trianglesRendered=0;
    private int realMeshesRendered=0;
    private List<PreviewPart> previewParts=List.of();
    private double[] previewBounds=null; // min X/Y/Z, max X/Y/Z in original LDU
    private record PreviewPart(double[][] corners, int color, double depthX, double depthY, double depthZ,
                               LDrawProjectReader.Instance instance){}
    private record Dot(int x,int y,double depth,int color){}
    private record Pixel(int x,int y){}
    private record Face(Pixel[] verts,double depth,int color){}
    public ProjectImportScreen(BrickStudioScreen parent){
        super(Text.literal(BrickI18n.tr("BrickCraft - Importer un projet BrickLink")));
        this.parent=parent;
        reloadFiles();
    }
    private void reloadFiles(){
        // Refreshing the folder must never silently select a different .io after files are added.
        Path previouslySelected=loadedFile!=null?loadedFile:selectedFile();
        try{
            Files.createDirectories(folder);
            try(var stream=Files.list(folder)){
                sources=stream.filter(Files::isRegularFile).filter(p->{
                    String n=p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".io")||n.endsWith(".mpd")||n.endsWith(".ldr")||n.endsWith(".bcpx");
                }).sorted(Comparator.comparing(p->p.getFileName().toString(),String.CASE_INSENSITIVE_ORDER)).toList();
            }
            int preserved=previouslySelected==null?-1:sources.indexOf(previouslySelected);
            index=preserved>=0?preserved:Math.min(Math.max(0,index),Math.max(0,sources.size()-1));
        }catch(IOException e){status="Lecture du dossier impossible : "+e.getMessage();sources=List.of();}
    }
    /** Opens the real Minecraft config directory in the OS file manager. */
    private void openImportFolder(){
        try{
            Files.createDirectories(folder);
            if(!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)){
                status=BrickI18n.fmt("brickcraft.v251.import.import_folder_info");
                return;
            }
            Desktop.getDesktop().open(folder.toFile());
            status=BrickI18n.fmt("brickcraft.v252.import.folder_opened");
        }catch(IOException | SecurityException | UnsupportedOperationException e){
            status="Impossible d'ouvrir le dossier : "+folder.toAbsolutePath()+" ("+e.getClass().getSimpleName()+")";
        }
    }
    private void openConfigFolder(){
        Path config=FabricLoader.getInstance().getConfigDir().resolve("brickcraft");
        try{
            fr.brickcraft.BrickDirectories.ensure(FabricLoader.getInstance().getConfigDir());
            if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(config.toFile());
                status=BrickI18n.fmt("brickcraft.v252.import.config_opened");
            }else status=BrickI18n.fmt("brickcraft.v251.import.folder_info");
        }catch(Exception ex){status=BrickI18n.fmt("brickcraft.v251.import.folder_error");}
    }
    private Path selectedFile(){return sources.isEmpty()?null:sources.get(index);}
    private void clearLoadedProject(){
        current=null;summary=null;dirtyPreview=false;loadedFile=null;
        previewParts=List.of();previewBounds=null;invalidateGpu();
        meshes.clear();noMesh.clear();cachedTriangles=0;
        panX=panY=0;zoom=1;orbit=.72;
    }
    private void backToStudio(){
        Runnable action=()->{gpuPreview.close();client.setScreen(parent);};
        if(dirtyPreview)client.setScreen(new BrickConfirmScreen(this,"Quitter sans enregistrer ?",
                "Les changements non enregistres ne seront plus accessibles.",action));
        else action.run();
    }
    private void requestUnload(){
        if(current==null){status="Aucun projet charge : choisis un fichier puis Analyser.";return;}
        Runnable action=()->{clearLoadedProject();clearAndInit();
            status="Projet decharge. Fichier et projections du monde conserves.";};
        if(dirtyPreview)client.setScreen(new BrickConfirmScreen(this,
                "Decharger les modifications non sauvegardees ?",
                "Les modifications non enregistrees seront perdues, mais aucun fichier ne sera efface.",action));
        else action.run();
    }
    private void select(int offset){
        if(sources.isEmpty())return;
        Runnable action=()->{
            index=Math.floorMod(index+offset,sources.size());
            clearLoadedProject();status="Selection : "+selectedFile().getFileName()+" (non charge)";
        };
        if(dirtyPreview)client.setScreen(new BrickConfirmScreen(this,"Changer de projet ?",
                "Les modifications non sauvegardees de l'editeur seront perdues.",action));
        else action.run();
    }
    private void requestAnalyze(){
        if(dirtyPreview)client.setScreen(new BrickConfirmScreen(this,"Recharger le fichier ?",
                "Les changements non sauvegardes seront remplaces par le fichier sur disque.",this::analyze));
        else analyze();
    }
    private void analyze(){
        clearLoadedProject();
        Path source=selectedFile();
        if(source==null){status="Aucun fichier. Copie ton .io dans config/brickcraft/imports/";return;}
        try{
            current=source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bcpx")?
                    PreciseProjectIO.read(source):LDrawProjectReader.inspect(source);
            loadedFile=source;
            summary=StudioProjectAnalysis.analyze(current);
            meshes.clear();noMesh.clear();cachedTriangles=0;
            buildPreview();
            status=BrickI18n.fmt("brickcraft.v251.import.loaded",summary.pieces(),summary.designs(),summary.missingInstances());
            panX=panY=0;zoom=1;
            try{
                saveReport();
                MissingPartsReport.write(summary,folder.resolve("reports"),source.getFileName().toString(),StudioCustomPartsInstaller.names(source));
            }catch(IOException reportError){status+=" | Rapport non enregistre : "+reportError.getMessage();}
        }catch(Exception e){current=null;summary=null;previewParts=List.of();previewBounds=null;
            status="Erreur d'analyse : "+e.getMessage();}
    }
    private void openMissingParts(){
        if(summary==null){status="Charge d'abord un projet.";return;}
        String file=loadedFile==null?"projet":loadedFile.getFileName().toString();
        client.setScreen(new BrickMissingPartsScreen(this,summary,file));
    }
    /** Apply an explicit, confirmed replacement to the loaded project, never to the original .io file. */
    StudioProjectAnalysis.Summary currentMissingSummary(){return summary;}
    int replaceMissingPart(String from,String target){
        if(current==null)return 0;
        String id=fr.brickcraft.importer.PartReplacementMatcher.normalize(target);
        if(!LDrawLibrary.contains(id) || LDrawLibrary.originalBounds(id)==null)return 0;
        var change=PartReplacementEditor.replace(current,from,id);
        if(change.count()==0)return 0;
        current=change.project();
        summary=StudioProjectAnalysis.analyze(current);
        // Geometry and GPU caches must not retain the missing ID after replacement.
        invalidateGpu();meshes.clear();noMesh.clear();cachedTriangles=0;
        buildPreview();dirtyPreview=true;
        status="Remplace "+from+" par "+id+" : "+change.count()+" pieces. Sauve la copie .bcpx (Actions).";
        try{
            saveReport();
            MissingPartsReport.write(summary,folder.resolve("reports"),
                    loadedFile==null?"projet":loadedFile.getFileName().toString(),
                    loadedFile==null?java.util.Map.of():StudioCustomPartsInstaller.names(loadedFile));
        }catch(IOException ignored){}
        return change.count();
    }
    /** Remove only references that the latest analysis identifies as missing. */
    int removeMissingPart(String source){
        if(current==null || summary==null)return 0;
        String id=fr.brickcraft.importer.PartReplacementMatcher.normalize(source);
        boolean currentlyMissing=summary.missingDesigns().keySet().stream().anyMatch(
                key->fr.brickcraft.importer.PartReplacementMatcher.normalize(key).equals(id));
        if(!currentlyMissing)return 0;
        return applyMissingRemoval(java.util.Set.of(id));
    }
    /** One-click clean-up of all currently unresolved references, after confirmation. */
    int removeAllMissingParts(){
        if(current==null || summary==null)return 0;
        return applyMissingRemoval(summary.missingDesigns().keySet());
    }
    private int applyMissingRemoval(java.util.Set<String> ids){
        var change=MissingPartRemovalEditor.removeReferences(current,ids);
        if(change.count()==0)return 0;
        current=change.project();
        summary=StudioProjectAnalysis.analyze(current);
        // The preview/GPU cache contains obsolete instances after deletion.
        invalidateGpu();meshes.clear();noMesh.clear();cachedTriangles=0;
        buildPreview();dirtyPreview=true;
        status="Supprime : "+change.count()+" pieces / "+change.references()+
                " references. Sauvegarde ta copie .bcpx dans Actions.";
        try{
            saveReport();
            MissingPartsReport.write(summary,folder.resolve("reports"),
                    loadedFile==null?"projet":loadedFile.getFileName().toString(),
                    loadedFile==null?java.util.Map.of():StudioCustomPartsInstaller.names(loadedFile));
        }catch(IOException ignored){}
        return change.count();
    }
    private void savePrecise(){
        if(current==null||loadedFile==null){status="Analyse d'abord un projet";return;}
        try {
            String original=loadedFile.getFileName().toString();
            String stem=original.replaceAll("(?i)\\.(bcpx|io|ldr|mpd)$","")
                    .replaceAll("[^a-zA-Z0-9._-]","_");
            if(stem.isBlank())stem="projet";
            Path output=folder.resolve(stem+"_corrige.bcpx");
            int seq=2;
            while(Files.exists(output))output=folder.resolve(stem+"_corrige_"+(seq++)+".bcpx");
            PreciseProjectIO.save(output,current);
            status="Sauvegarde precise (sans arrondi) : "+output.getFileName();
            dirtyPreview=false;reloadFiles();
        }catch(IOException e){status="BCPX : "+e.getMessage();}
    }
    private void cyclePreview(){setPreviewMode((previewMode+1)%3);}
    private void setPreviewMode(int mode){
        previewMode=Math.floorMod(mode,3);invalidateGpu();clearAndInit();
        status=BrickI18n.fmt("brickcraft.v241.import.preview",BrickI18n.tr(modeName()),gpuPreviewEnabled&&previewMode!=0?"GPU":"CPU");
    }
    private void resetView(){orbit=.72;zoom=1;panX=panY=0;invalidateGpu();status="Camera recentree";}
    private void selectPanel(Panel panel){activePanel=panel;clearAndInit();}
    private String modeName(){return switch(previewMode){case 0->"points";case 1->"volumes";default->"maillages";};}
    private void saveReport()throws IOException {
        Path source=loadedFile!=null?loadedFile:selectedFile();
        if(summary==null || source==null)return;
        String name=source.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]","_");
        Path report=folder.resolve("reports").resolve(name+".diagnostic.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report,StudioProjectAnalysis.report(summary,source.getFileName().toString()));
    }
    private void convert(){
        if(current==null){status="Analyse d'abord le projet";return;}
        if(current.parts().size()>StudioAssembly.MAX_PIECES){
            status="Studio grille limite a "+StudioAssembly.MAX_PIECES+" pieces. Utilise 'Projeter dans le monde'.";return;
        }
        if(summary.missingInstances()>0 || summary.angled()>0){
            status="Import sans perte impossible : references manquantes ou rotations libres (voir rapport)";return;
        }
        try{
            BrickCraftProjectConverter.Conversion c=BrickCraftProjectConverter.convert(current);
            if(!c.success()){
                status="Conversion refusee : "+c.errors().get(0);
                return;
            }
            if(parent.acceptImportedProject(c.grid(),c.pieces())){
                client.setScreen(parent);
            }else status="Le Studio refuse ce projet (limites ou collisions)";
        }catch(Exception e){status="Conversion echouee : "+e.getMessage();}
    }
    /** Visual-only, server-validated BCPX projection. The classic solid-brick world is untouched. */
    private void projectWorld(){
        if(current==null){status="Analyse un projet avant de le projeter";return;}
        if(current.parts().isEmpty()){
            status="Projet vide : aucune piece a projeter apres le nettoyage.";return;
        }
        if(current.parts().size()>BrickCapacityConfig.effectiveParts()){status="Trop de pieces ("+current.parts().size()+") pour le plafond choisi. Ouvre Capacites.";return;}
        if(client.player==null || client.world==null || client.interactionManager==null ||
                !client.interactionManager.hasCreativeInventory()){
            status="Projection disponible uniquement en mode Creatif";return;
        }
        HitResult target=client.player.raycast(12.0,1.0f,false);
        if(!(target instanceof BlockHitResult hit) || target.getType()!=HitResult.Type.BLOCK){
            status="Vise un bloc au sol avant d'ouvrir le Studio";return;
        }
        BlockPos anchor=hit.getBlockPos().offset(hit.getSide());
        if(!client.world.getBlockState(anchor).isAir()){
            status="Espace d'ancrage occupe : vise une surface avec de l'air au-dessus";return;
        }
        try{
            var bounds=PreciseWorldModel.analyze(current.parts());
            if(anchor.getY()+bounds.height()+1>=client.world.getTopY()){
                status="Chateau trop haut pour cet endroit";return;
            }
            ClientPlayNetworking.send(PreciseExportPayload.begin(anchor,current.parts().size()));
            for(int offset=0;offset<current.parts().size();offset+=PreciseExportPayload.BATCH_SIZE){
                int end=Math.min(current.parts().size(),offset+PreciseExportPayload.BATCH_SIZE);
                ClientPlayNetworking.send(PreciseExportPayload.batch(offset,current.parts().subList(offset,end)));
            }
            ClientPlayNetworking.send(PreciseExportPayload.commit());
            status="Projection envoyee : "+current.parts().size()+" pieces, "+bounds.mappedPrints()+
                    " imprimees sans texture. Retourne en jeu pour voir le resultat.";
        }catch(Exception e){status="Projection refusee : "+e.getMessage();}
    }
    private ButtonWidget addButton(int x,int y,int w,int h,String label,Runnable click){
        return addButton(x,y,w,h,label,true,click);
    }
    private ButtonWidget addButton(int x,int y,int w,int h,String label,boolean enabled,Runnable click){
        if(w<16 || y<0 || y+h>height-56)return null;
        ButtonWidget btn=ButtonWidget.builder(Text.literal(BrickI18n.tr(label)),b->click.run()).dimensions(x,y,w,h).build();
        btn.active=enabled;addDrawableChild(btn);return btn;
    }
    /** V0.9.6: the advanced editor retains original doubles/matrices and reuses this preview. */
    private void openFreeEditor(){
        LDrawProjectReader.Result source=current!=null?current:
                new LDrawProjectReader.Result("brickcraft_free.ldr",0,List.of(),List.of());
        String file=loadedFile!=null?loadedFile.getFileName().toString():
                selectedFile()==null?"atelier.ldr":selectedFile().getFileName().toString();
        if(source.parts().size()>BrickCapacityConfig.effectiveParts()){
            status="Editeur libre limite a "+BrickCapacityConfig.label(BrickCapacityConfig.partsSetting())+" pieces; utilise un sous-modele.";return;
        }
        client.setScreen(new PreciseFreeScreen(this,source,file));
    }
    void applyFreeEdits(LDrawProjectReader.Result result){
        current=result;
        // Keep LDraw preview meshes cached when only one transform changes.
        if(current.parts().isEmpty()){summary=null;previewParts=List.of();previewBounds=null;invalidateGpu();return;}
        summary=StudioProjectAnalysis.analyze(current);
        buildPreview();dirtyPreview=true;
        status=BrickI18n.fmt("brickcraft.v251.import.free_edited",current.parts().size());
    }
    void refreshFreeFiles(Path destination){
        reloadFiles();
        int found=sources.indexOf(destination);
        if(found>=0)index=found;
        loadedFile=destination;
        dirtyPreview=false;
    }
    @Override protected void init(){
        int side=sideWidth(),x=9,w=side-18,gap=3;
        int t=(w-2*gap)/3;
        addButton(x,32,t,19,"Projet",()->selectPanel(Panel.PROJET));
        addButton(x+t+gap,32,t,19,"Visuel",()->selectPanel(Panel.VISUEL));
        addButton(x+2*(t+gap),32,w-2*(t+gap),19,"Actions",()->selectPanel(Panel.ACTIONS));
        switch(activePanel){
            case PROJET -> {
                int half=(w-gap)/2;
                addButton(x,66,half,20,"< Fichier",!sources.isEmpty(),()->select(-1));
                addButton(x+half+gap,66,w-half-gap,20,"Fichier >",!sources.isEmpty(),()->select(1));
                addButton(x,93,w,20,"Analyser / charger",selectedFile()!=null,this::requestAnalyze);
                addButton(x,120,(w-3)/2,20,"Dossier imports",this::openImportFolder);
                addButton(x+(w-3)/2+3,120,w-(w-3)/2-3,20,"Dossier LDraw",this::openConfigFolder);
                addButton(x,146,w,20,"Actualiser la liste",()->{
                    reloadFiles();status="Liste des fichiers actualisee";clearAndInit();});
                addButton(x,173,w,20,"Pieces manquantes / chercher",current!=null,this::openMissingParts);
            }
            case VISUEL -> {
                int modeW=(w-2*gap)/3;
                addButton(x,66,modeW,20,"Points",current!=null,()->setPreviewMode(0));
                addButton(x+modeW+gap,66,modeW,20,"Volumes",current!=null,()->setPreviewMode(1));
                addButton(x+2*(modeW+gap),66,w-2*(modeW+gap),20,"Maillage",current!=null,()->setPreviewMode(2));
                addButton(x,93,w,20,BrickI18n.fmt("brickcraft.v241.import.engine",gpuPreviewEnabled?BrickI18n.tr("GPU (rapide)"):BrickI18n.tr("CPU (secours)")),
                        this::toggleGpuPreview);
                addButton(x,120,w,20,"Recentrer la camera",this::resetView);
            }
            case ACTIONS -> {
                int half=(w-gap)/2;
                addButton(x,65,w,20,"Ouvrir l'editeur libre",this::openFreeEditor);
                // Gridded Studio cannot preserve missing references or arbitrary rotations.
                boolean gridCompatible=current!=null && summary!=null
                        && current.parts().size()<=StudioAssembly.MAX_PIECES
                        && summary.missingInstances()==0 && summary.angled()==0;
                addButton(x,92,half,20,"Studio grille",gridCompatible,this::convert);
                addButton(x+half+gap,92,w-half-gap,20,"Sauver .bcpx",current!=null,this::savePrecise);
                addButton(x,119,w,20,"Projeter dans le monde",current!=null,this::projectWorld);
                addButton(x,146,half,20,"Projections",()->client.setScreen(new PreciseWorldProjectsScreen(this)));
                addButton(x+half+gap,146,w-half-gap,20,"Decharger",current!=null,this::requestUnload);
                addButton(x,173,w,20,"Capacites pieces / physique",()->client.setScreen(new BrickCapacityScreen(this)));
            }
        }
        // Fixed navigation and status bands: no action widget may enter them.
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr("Retour au Studio")),b->backToStudio())
                .dimensions(x,height-50,w,20).build());
    }
    @Override public void renderBackground(DrawContext context,int mx,int my,float delta){}
    private void drawText(DrawContext ctx,String s,int x,int y,int max,int color){
        s=BrickI18n.tr(s);
        if(max<10)return;
        if(textRenderer.getWidth(s)>max){
            String suffix="...";
            int n=s.length();
            while(n>0 && textRenderer.getWidth(s.substring(0,n)+suffix)>max)n--;
            s=s.substring(0,n)+suffix;
        }
        ctx.drawTextWithShadow(textRenderer,s,x,y,color);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float dt){
        ctx.fill(0,0,width,height,0xFF0B111A);
        int side=sideWidth(),left=side+6,top=34,right=width-7,bottom=height-30;
        ctx.fill(4,28,side,bottom,0xFF162330);
        ctx.fill(left,top,right,bottom,0xFF0A1C29);
        drawText(ctx,"BRICKCRAFT  /  IMPORT & APERCU",9,9,width-16,0xFFE2F4FF);
        // Explicit active tab indicator, rather than three identical unlabeled action stacks.
        int tab=(side-24)/3,idx=activePanel.ordinal();
        ctx.fill(9+idx*(tab+3),52,9+idx*(tab+3)+tab,54,0xFF55D3D3);
        if(activePanel==Panel.VISUEL){
            int available=side-18,small=(available-6)/3;
            ctx.fill(9+previewMode*(small+3),87,9+previewMode*(small+3)+small,89,0xFF55D3D3);
        }
        int infoTop=panelInformationTop(),statsEnd=height-56;
        if(infoTop<statsEnd){
            ctx.enableScissor(8,infoTop,side-7,statsEnd);
            int y=infoTop;
            drawText(ctx,activePanel==Panel.PROJET?"PROJET SELECTIONNE":
                    activePanel==Panel.VISUEL?"QUALITE D'APERCU":"GESTION DES PROJETS",11,y,side-24,0xFF71DCD8);
            y+=17;
            if(activePanel==Panel.VISUEL){
                String[] lines={BrickI18n.fmt("brickcraft.v241.import.mode",BrickI18n.tr(modeName())),
                        BrickI18n.fmt("brickcraft.v241.import.renderer",previewMode==0?BrickI18n.tr("points (rapide)"):gpuPreviewEnabled?"GPU":BrickI18n.tr("CPU (secours)")),
                        "V : changer de vue", "O : basculer GPU / CPU", "Molette : zoom", "Clic droit : orbite"};
                for(String line:lines){if(y+11>statsEnd)break;drawText(ctx,line,11,y,side-24,0xFFA7C6D8);y+=15;}
            }else{
                String[] lines=activePanel==Panel.PROJET?
                        new String[]{BrickI18n.fmt("brickcraft.v241.import.files",sources.size()),
                        selectedFile()==null?"Aucun .io / .mpd / .ldr":selectedFile().getFileName().toString(),
                        current==null?"Aucun projet charge":BrickI18n.fmt("brickcraft.v241.import.parts",summary.pieces()),
                        current==null?"config/brickcraft/imports/":BrickI18n.fmt("brickcraft.v241.import.missing",summary.missingInstances()),
                        current==null?"Depose ton projet ici":BrickI18n.fmt("brickcraft.v241.import.rotations",summary.angled()),
                        current==null?"":"Details dans imports/reports/"}:
                        new String[]{"Editeur libre : conserve les", "positions et rotations exactes.",
                        "Studio strict : grille seulement.","BCPX : enregistrement sans perte.",
                        "Projections : gerer le monde."};
                for(String line:lines){if(y+11>statsEnd)break;drawText(ctx,line,11,y,side-24,0xFFA7C6D8);y+=15;}
            }
            ctx.disableScissor();
        }
        drawText(ctx,"Clic droit : orbite | Molette : zoom | Milieu : deplacer",left+7,top+8,right-left-14,0xFFB0D9E8);
        drawText(ctx,switch(previewMode){
            case 0 -> "Origines des pieces : apercu tres rapide";
            case 1 -> "Volumes des pieces : apercu simplifie";
            default -> "Maillages LDraw : formes reelles / LOD";
        },left+7,top+22,right-left-14,0xFF9BB9C8);
        if(summary!=null){
            if(previewMode==0)renderDots(ctx,left+5,top+38,right-5,bottom-5);
            else if(gpuPreviewEnabled)renderGpuPreview(ctx,left+5,top+38,right-5,bottom-5,previewMode==2);
            else renderVolumes(ctx,left+5,top+38,right-5,bottom-5,previewMode==2);
        }else drawText(ctx,"Choisis un fichier dans Projet puis Analyser",left+16,top+77,right-left-26,0xFF92B2C7);
        ctx.fill(0,height-25,width,height,0xFF142638);
        drawText(ctx,status,10,height-18,width-20,0xFFEED29A);
        super.render(ctx,mx,my,dt);
    }
    /** Only the 3D preview is painted by the editing screen: no imported UI widgets. */
    void renderFreeViewport(DrawContext ctx,int left,int top,int right,int bottom,int selection,boolean rotateMode){
        ctx.fill(left,top,right,bottom,0xFF0A1C29);
        embeddedPreview=true;
        try{
            if(summary==null){
                drawText(ctx,"Atelier vide : ajoute une piece",left+15,top+30,right-left-30,0xFF90B7C9);
            }else{
                if(previewMode==0)renderDots(ctx,left+4,top+4,right-4,bottom-4);
                else if(gpuPreviewEnabled)renderGpuPreview(ctx,left+4,top+4,right-4,bottom-4,previewMode==2);
                else renderVolumes(ctx,left+4,top+4,right-4,bottom-4,previewMode==2);
                drawFreeSelected(ctx,left+4,top+4,right-4,bottom-4,selection,rotateMode);
            }
        }finally{embeddedPreview=false;}
    }
    private PreviewSelectionMath.Frame editorFrame(int left,int top,int right,int bottom){
        return previewBounds==null?null:PreviewSelectionMath.frame(previewBounds,orbit,zoom,panX,panY,
                left+4,top+4,right-4,bottom-4);
    }
    PreviewSelectionMath.Frame editorDragFrame(int left,int top,int right,int bottom){
        return editorFrame(left,top,right,bottom);
    }
    int editorPickPart(double mx,double my,int selected,boolean cycling,int left,int top,int right,int bottom){
        if(mx<left+4||mx>right-4||my<top+4||my>bottom-4||previewParts.isEmpty())return -1;
        var f=editorFrame(left,top,right,bottom);
        if(f==null)return -1;
        ArrayList<double[][]> volumes=new ArrayList<>(previewParts.size());
        for(var part:previewParts)volumes.add(part.corners());
        return PreviewSelectionMath.choose(PreviewSelectionMath.hits(f,volumes,mx,my),selected,cycling);
    }
    int editorHitGizmo(double mx,double my,int selection,int left,int top,int right,int bottom){
        if(selection<0||selection>=previewParts.size())return -1;
        var f=editorFrame(left,top,right,bottom);
        if(f==null)return -1;
        var p=previewParts.get(selection).instance();
        int chosen=-1;double best=100; // 10px radius on axis tips
        for(int axis=0;axis<3;axis++){
            var point=PreviewSelectionMath.axisEnd(f,p.x(),p.y(),p.z(),axis,gizmoLength(f));
            double dx=point.x()-mx,dy=point.y()-my,d=dx*dx+dy*dy;
            if(d<=best){best=d;chosen=axis;}
        }
        return chosen;
    }
    private static final int[][] BOX_EDGES={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},
                                             {2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
    private static final int[] GIZMO_COLORS={0xFFFF6565,0xFF75E49F,0xFF75ABFF};
    private static double gizmoLength(PreviewSelectionMath.Frame f){
        // A 40-LDU gizmo becomes invisible on large imported projects.
        // Keep handles ~35 GUI pixels apart, independent of the construction scale.
        return Math.max(20,Math.min(5000,35/Math.max(.0001,f.unit())));
    }
    private void drawFreeSelected(DrawContext ctx,int x1,int y1,int x2,int y2,int selected,boolean rotate){
        if(selected<0||selected>=previewParts.size())return;
        var f=PreviewSelectionMath.frame(previewBounds,orbit,zoom,panX,panY,x1,y1,x2,y2);
        var box=previewParts.get(selected);
        var corners=PreviewSelectionMath.corners(f,box.corners());
        ctx.enableScissor(x1,y1,x2,y2);
        for(int[] edge:BOX_EDGES){
            var a=corners[edge[0]];var b=corners[edge[1]];
            drawEditorLine(ctx,a.x(),a.y(),b.x(),b.y(),0xFFFFD46C);
        }
        var p=box.instance();var origin=f.project(p.x(),p.y(),p.z());
        for(int axis=0;axis<3;axis++){
            var tip=PreviewSelectionMath.axisEnd(f,p.x(),p.y(),p.z(),axis,gizmoLength(f));
            drawEditorLine(ctx,origin.x(),origin.y(),tip.x(),tip.y(),GIZMO_COLORS[axis]);
            int tx=(int)Math.round(tip.x()),ty=(int)Math.round(tip.y());
            ctx.fill(tx-4,ty-4,tx+5,ty+5,GIZMO_COLORS[axis]);
            ctx.drawTextWithShadow(textRenderer,BrickI18n.tr("XYZ".substring(axis,axis+1)),tx+5,ty-4,0xFFFFFFFF);
        }
        ctx.disableScissor();
        // Keep the active-piece label BELOW the two instruction lines of the viewport.
        drawText(ctx,(rotate?"ROTATION":"DEPLACEMENT")+" | #"+(selected+1)+"  "+p.partId(),
                x1+8,y1+38,x2-x1-16,0xFFFFDF8B);
    }
    private static void drawEditorLine(DrawContext ctx,double ax,double ay,double bx,double by,int color){
        // GUI-independent Bresenham line; only 12 bounding-box edges + 3 handles.
        int x=(int)Math.round(ax),y=(int)Math.round(ay),x2=(int)Math.round(bx),y2=(int)Math.round(by);
        int dx=Math.abs(x2-x),dy=Math.abs(y2-y),sx=x<x2?1:-1,sy=y<y2?1:-1,err=dx-dy;
        for(int steps=0;steps<4000;steps++){
            ctx.fill(x,y,x+2,y+2,color);
            if(x==x2&&y==y2)break;
            int e=err*2;
            if(e>-dy){err-=dy;x+=sx;}
            if(e<dx){err+=dx;y+=sy;}
        }
    }
    boolean editorPreviewMouseClick(double x,double y,int button,int left,int top,int right,int bottom){
        if(x<left||x>right||y<top||y>bottom)return false;
        if(button==1){orbiting=true;lastX=(int)x;lastY=(int)y;return true;}
        if(button==2){panning=true;lastX=(int)x;lastY=(int)y;return true;}
        return false;
    }
    boolean editorPreviewMouseDrag(int button,double dx,double dy){
        if(button==1&&orbiting){orbit+=dx*.01;return true;}
        if(button==2&&panning){panX+=dx;panY+=dy;return true;}
        return false;
    }
    boolean editorPreviewMouseRelease(int button){
        if(button==1&&orbiting){orbiting=false;return true;}
        if(button==2&&panning){panning=false;return true;}
        return false;
    }
    boolean editorPreviewWheel(double x,double y,double vertical,int left,int top,int right,int bottom){
        if(x<left||x>right||y<top||y>bottom)return false;
        zoom=Math.max(.1,Math.min(15,zoom*Math.pow(1.15,vertical)));return true;
    }
    /**
     * Source coordinates are preserved: each part box is transformed by its full
     * 3x3 LDraw matrix. No grid rounding, replacement design or world export here.
     * The boxes are only a fast diagnostic preview, not actual part triangles.
     */
    private void buildPreview(){
        if(summary==null || summary.project().parts().isEmpty()){
            previewParts=List.of();previewBounds=null;return;
        }
        var source=summary.project().parts();
        java.util.Map<String,double[]> boundsCache=new java.util.HashMap<>();
        ArrayList<PreviewPart> result=new ArrayList<>(source.size());
        double[] all={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                      Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(var p:source){
            // The actual LDraw bounds refer to its local origin, not the normalized Minecraft mesh.
            double[] local=boundsCache.computeIfAbsent(p.partId(),id->{
                String actual=fr.brickcraft.importer.PreciseReferenceMapper.geometryId(id);
                double[] b=actual==null?null:LDrawLibrary.originalBounds(actual);
                return b!=null?b:new double[]{-10,-12,-10,10,12,10};
            });
            double[] m=p.matrix();
            double[][] corners=new double[8][3];
            for(int k=0;k<8;k++){
                double x=((k&1)==0)?local[0]:local[3];
                double y=((k&2)==0)?local[1]:local[4];
                double z=((k&4)==0)?local[2]:local[5];
                double[] point={p.x()+m[0]*x+m[1]*y+m[2]*z,
                                p.y()+m[3]*x+m[4]*y+m[5]*z,
                                p.z()+m[6]*x+m[7]*y+m[8]*z};
                corners[k]=point;
                for(int axis=0;axis<3;axis++){
                    all[axis]=Math.min(all[axis],point[axis]);
                    all[axis+3]=Math.max(all[axis+3],point[axis]);
                }
            }
            var color=LDrawLibrary.color(p.color());
            int argb=color==null?0xFFCED4DE:0xFF000000|color.rgb();
            result.add(new PreviewPart(corners,argb,p.x(),p.y(),p.z(),p));
        }
        previewParts=List.copyOf(result);
        previewBounds=all;
    }

    private void renderVolumes(DrawContext ctx,int x1,int y1,int x2,int y2,boolean detailed){
        if(previewParts.isEmpty() || previewBounds==null || x2<=x1 || y2<=y1)return;
        double[] b=previewBounds;
        double cx=(b[0]+b[3])/2,cy=(b[1]+b[4])/2,cz=(b[2]+b[5])/2;
        double sx=Math.max(20,b[3]-b[0]),sy=Math.max(8,b[4]-b[1]),sz=Math.max(20,b[5]-b[2]);
        double co=Math.cos(orbit),si=Math.sin(orbit);
        double boundW=1,boundH=1;
        for(int i=0;i<8;i++){
            double x=((i&1)==0?-sx:sx)/2;
            double y=((i&2)==0?-sy:sy)/2;
            double z=((i&4)==0?-sz:sz)/2;
            boundW=Math.max(boundW,Math.abs(x*co-z*si));
            boundH=Math.max(boundH,Math.abs((x*si+z*co)*.40+y*.86));
        }
        double unit=Math.max(0.0001,Math.min((x2-x1-36)/(2*boundW),(y2-y1-36)/(2*boundH))*zoom);
        double centerX=(x1+x2)*.5+panX,centerY=(y1+y2)*.5+panY;
        // Painters' order for opaque gabarits. The low LOD is deliberately cheap.
        ArrayList<PreviewPart> ordered=new ArrayList<>(previewParts);
        ordered.sort(Comparator.comparingDouble(p->(p.depthX()-cx)*si+(p.depthZ()-cz)*co-(p.depthY()-cy)*.55));
        ctx.enableScissor(x1,y1,x2,y2);
        int rendered=0;
        trianglesRendered=0;realMeshesRendered=0;
        int[] loadsLeft={2};
        // CPU emergency renderer: bounded workload for big imports. GPU still draws ALL pieces.
        int cpuStride=collectingGpu==null?PreviewPerfPolicy.cpuStride(previewParts.size()):1;
        int sourceIndex=0;
        for(PreviewPart part:ordered){
            if(sourceIndex++%cpuStride!=0)continue;
            Pixel[] pts=new Pixel[8];
            int minx=Integer.MAX_VALUE,miny=Integer.MAX_VALUE,maxx=Integer.MIN_VALUE,maxy=Integer.MIN_VALUE;
            for(int k=0;k<8;k++){
                double[] v=part.corners()[k];
                double x=v[0]-cx,y=v[1]-cy,z=v[2]-cz;
                int px=(int)Math.round(centerX+(x*co-z*si)*unit);
                int py=(int)Math.round(centerY+((x*si+z*co)*.4+y*.86)*unit);
                pts[k]=new Pixel(px,py);
                minx=Math.min(minx,px);maxx=Math.max(maxx,px);
                miny=Math.min(miny,py);maxy=Math.max(maxy,py);
            }
            if(maxx<x1 || minx>x2 || maxy<y1 || miny>y2)continue;
            // Small distant parts are 2px dots; larger parts show 3D gabarits.
            if((maxx-minx)*(maxy-miny)<9){
                int px=(minx+maxx)/2,py=(miny+maxy)/2;
                if(collectingGpu!=null)collectingGpu.rectangle(px-1,py-1,px+2,py+2,part.color());
                else ctx.fill(px-1,py-1,px+2,py+2,part.color());
            }else if(detailed && (maxx-minx)*(maxy-miny)>42 &&
                    drawMesh(ctx,part,cx,cy,cz,co,si,unit,centerX,centerY,x1,y1,x2,y2,loadsLeft)){
                realMeshesRendered++;
            }else{
                // Sort all six faces for this part, including arbitrary LDraw rotation.
                int[][] faces={{0,4,5,1},{2,3,7,6},{0,2,6,4},
                               {1,5,7,3},{0,1,3,2},{4,6,7,5}};
                int[] shades={78,112,84,96,90,73};
                ArrayList<Face> orderedFaces=new ArrayList<>(6);
                for(int f=0;f<faces.length;f++){
                    Pixel[] v=new Pixel[4];double depth=0;
                    for(int k=0;k<4;k++){
                        int ix=faces[f][k];v[k]=pts[ix];
                        depth+=(part.corners()[ix][0]-cx)*si+(part.corners()[ix][2]-cz)*co-(part.corners()[ix][1]-cy)*.55;
                    }
                    orderedFaces.add(new Face(v,depth,shade(part.color(),shades[f])));
                }
                orderedFaces.sort(Comparator.comparingDouble(Face::depth));
                for(Face face:orderedFaces)fillFace(ctx,face.verts(),face.color(),x1,y1,x2,y2);
            }
            rendered++;
        }
        ctx.disableScissor();
        if(collectingGpu==null){
            String note=cpuStride>1?" | CPU allege (1/"+cpuStride+")":"";
            drawText(ctx,detailed?"Detail : "+realMeshesRendered+" formes / "+rendered+
                    " pieces, "+trianglesRendered+" triangles"+note:
                    "Apercu : "+rendered+" volumes"+note,x1+5,y2-14,x2-x1-10,0xFF8DAABF);
        }
    }
    /** A bounded, lazy mesh cache: expensive .dat files are never expanded for every instance/frame. */
    private LDrawLibrary.Mesh previewMesh(String id,int[] loadsLeft){
        if(meshes.containsKey(id))return meshes.get(id);
        if(noMesh.contains(id)||loadsLeft[0]<=0)return null;
        loadsLeft[0]--;
        LDrawLibrary.Mesh mesh=LDrawLibrary.mesh(id);
        if(mesh==null || mesh.isEmpty() || mesh.triangles()>MAX_PART_TRIANGLES ||
                cachedTriangles+mesh.triangles()>MAX_CACHED_TRIANGLES){
            noMesh.add(id);return null;
        }
        cachedTriangles+=mesh.triangles();meshes.put(id,mesh);
        return mesh;
    }
    private boolean drawMesh(DrawContext ctx,PreviewPart part,double cx,double cy,double cz,
                             double co,double si,double unit,double centerX,double centerY,
                             int x1,int y1,int x2,int y2,int[] loadsLeft){
        var src=part.instance();
        LDrawLibrary.Mesh mesh=previewMesh(src.partId(),loadsLeft);
        if(mesh==null || trianglesRendered+mesh.triangles()>MAX_RENDER_TRIANGLES)return false;
        double[] bounds=LDrawLibrary.originalBounds(src.partId());
        if(bounds==null)return false;
        float[] xyz=mesh.xyz();int[] faceColors=mesh.colors();
        double[] m=src.matrix(); // Fetch the matrix once; the rasterizer runs many triangles/frame.
        ArrayList<Face> faces=new ArrayList<>(mesh.triangles());
        for(int i=0;i<mesh.triangles();i++){
            Pixel[] vertices=new Pixel[3];double[] wx=new double[3],wy=new double[3],wz=new double[3];
            for(int j=0;j<3;j++){
                int k=i*9+j*3;
                double lx=bounds[0]+xyz[k]*20.0;
                double ly=bounds[4]-xyz[k+1]*20.0;
                double lz=bounds[2]+xyz[k+2]*20.0;
                wx[j]=src.x()+m[0]*lx+m[1]*ly+m[2]*lz;
                wy[j]=src.y()+m[3]*lx+m[4]*ly+m[5]*lz;
                wz[j]=src.z()+m[6]*lx+m[7]*ly+m[8]*lz;
                double x=wx[j]-cx,y=wy[j]-cy,z=wz[j]-cz;
                vertices[j]=new Pixel((int)Math.round(centerX+(x*co-z*si)*unit),
                        (int)Math.round(centerY+((x*si+z*co)*.40+y*.86)*unit));
            }
            double ux=wx[1]-wx[0],uy=wy[1]-wy[0],uz=wz[1]-wz[0];
            double vx=wx[2]-wx[0],vy=wy[2]-wy[0],vz=wz[2]-wz[0];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<0.0000001)continue;
            // Double-sided triangles: no assumption about BFC winding or mirrored MPD transforms.
            double light=.74+.15*Math.abs(ny)/length+.12*Math.abs(nx*si+nz*co)/length;
            int argb=LDrawLibrary.meshColor(faceColors[i],src.color(),part.color()&0xFFFFFF);
            int tint=shade(argb,(int)Math.round(light*100));
            double depth=((wx[0]+wx[1]+wx[2])/3-cx)*si+
                         ((wz[0]+wz[1]+wz[2])/3-cz)*co-
                         ((wy[0]+wy[1]+wy[2])/3-cy)*.55;
            faces.add(new Face(vertices,depth,tint));
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
        for(Face f:faces)fillFace(ctx,f.verts(),f.color(),x1,y1,x2,y2);
        trianglesRendered+=mesh.triangles();
        return true;
    }
    private static int shade(int color,int factor){
        int r=Math.min(255,((color>>>16)&255)*factor/100),g=Math.min(255,((color>>>8)&255)*factor/100);
        int b=Math.min(255,(color&255)*factor/100);
        return (color&0xFF000000)|(r<<16)|(g<<8)|b;
    }
    private static void fillFace(DrawContext ctx,Pixel[] v,int color,int x1,int y1,int x2,int y2){
        if(collectingGpu!=null){
            int[] px=new int[v.length],py=new int[v.length];
            for(int i=0;i<v.length;i++){px[i]=v[i].x();py[i]=v[i].y();}
            collectingGpu.face(px,py,color);return;
        }
        int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;
        for(Pixel p:v){lo=Math.min(lo,p.y());hi=Math.max(hi,p.y());}
        lo=Math.max(lo,y1);hi=Math.min(hi,y2-1);
        for(int y=lo;y<=hi;y++){
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<v.length;i++){
                Pixel a=v[i],b=v[(i+1)%v.length];
                if(a.y()==b.y()||y<Math.min(a.y(),b.y())||y>=Math.max(a.y(),b.y()))continue;
                double xx=a.x()+(double)(y-a.y())*(b.x()-a.x())/(b.y()-a.y());
                min=Math.min(min,xx);max=Math.max(max,xx);
            }
            if(max>=min){
                int lx=Math.max(x1,(int)Math.floor(min));int hx=Math.min(x2,(int)Math.ceil(max)+1);
                if(hx>lx)ctx.fill(lx,y,hx,y+1,color);
            }
        }
    }

    private void renderDots(DrawContext ctx,int x1,int y1,int x2,int y2){
        if(summary==null||x2<=x1||y2<=y1)return;
        int count=summary.project().parts().size();
        if(count==0)return;
        double spanX=Math.max(20,summary.maxX()-summary.minX());
        double spanZ=Math.max(20,summary.maxZ()-summary.minZ());
        double spanY=Math.max(8,summary.maxY()-summary.minY());
        double c=Math.cos(orbit),s=Math.sin(orbit);
        double[][] sample={{0,0,0},{spanX,0,0},{0,0,spanZ},{spanX,0,spanZ},
                           {0,spanY,0},{spanX,spanY,0},{0,spanY,spanZ},{spanX,spanY,spanZ}};
        double boundX=1,boundY=1;
        for(double[] v:sample){
            double px=(v[0]-spanX/2)*c-(v[2]-spanZ/2)*s;
            double py=((v[0]-spanX/2)*s+(v[2]-spanZ/2)*c)*.40-(v[1]-spanY/2)*.86;
            boundX=Math.max(boundX,Math.abs(px));boundY=Math.max(boundY,Math.abs(py));
        }
        double unit=Math.min((x2-x1-32)/2.0/boundX,(y2-y1-32)/2.0/boundY)*zoom;
        double centerX=(x1+x2)/2.+panX,centerY=(y1+y2)/2.+panY;
        ArrayList<Dot> dots=new ArrayList<>(count);
        for(var p:summary.project().parts()){
            double xx=p.x()-summary.minX()-spanX/2;
            double zz=p.z()-summary.minZ()-spanZ/2;
            double yy=summary.maxY()-p.y()-spanY/2;
            double screenX=centerX+(xx*c-zz*s)*unit;
            double depth=xx*s+zz*c;
            double screenY=centerY+(depth*.40-yy*.86)*unit;
            if(screenX<x1+1||screenX>x2-1||screenY<y1+1||screenY>y2-1)continue;
            var color=LDrawLibrary.color(p.color());
            dots.add(new Dot((int)screenX,(int)screenY,depth,color==null?0xFFCED4DE:0xFF000000|color.rgb()));
        }
        dots.sort(Comparator.comparingDouble(Dot::depth));
        ctx.enableScissor(x1,y1,x2,y2);
        for(Dot d:dots){
            int size=Math.max(2,Math.min(5,(int)(unit*8)));
            ctx.fill(d.x-size/2,d.y-size/2,d.x+size/2+1,d.y+size/2+1,d.color);
        }
        ctx.disableScissor();
        drawText(ctx,BrickI18n.fmt("brickcraft.v252.import.fast_points",count),x1+5,y2-14,x2-x1-10,0xFF8DAABF);
    }
    @Override public boolean mouseClicked(double x,double y,int btn){
        if(x>=sideWidth()+6 && y>36 && y<height-34){
            if(btn==1){orbiting=true;lastX=(int)x;lastY=(int)y;return true;}
            if(btn==2){panning=true;lastX=(int)x;lastY=(int)y;return true;}
        }
        return super.mouseClicked(x,y,btn);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(orbiting && button==1){orbit+=dx*.01;return true;}
        if(panning && button==2){panX+=dx;panY+=dy;return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int btn){
        if(btn==1)orbiting=false;
        if(btn==2)panning=false;
        return super.mouseReleased(x,y,btn);
    }
    @Override public boolean mouseScrolled(double x,double y,double horiz,double vert){
        if(x>sideWidth()){zoom=Math.max(.1,Math.min(15,zoom*Math.pow(1.15,vert)));return true;}
        return super.mouseScrolled(x,y,horiz,vert);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){backToStudio();return true;}
        if(key==GLFW.GLFW_KEY_R){reloadFiles();return true;}
        if(key==GLFW.GLFW_KEY_ENTER){requestAnalyze();return true;}
        if(key==GLFW.GLFW_KEY_V){cyclePreview();return true;}
        if(key==GLFW.GLFW_KEY_O){toggleGpuPreview();return true;}
        if((mods & GLFW.GLFW_MOD_CONTROL)!=0 && key==GLFW.GLFW_KEY_S){savePrecise();return true;}
        return super.keyPressed(key,scan,mods);
    }
}
