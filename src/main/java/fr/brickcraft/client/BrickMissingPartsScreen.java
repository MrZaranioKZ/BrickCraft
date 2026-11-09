package fr.brickcraft.client;

import fr.brickcraft.importer.MissingPartsReport;
import fr.brickcraft.importer.StudioCustomPartsInstaller;
import fr.brickcraft.importer.StudioProjectAnalysis;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** In-game missing part list. Browser search only opens an explicit, verified site. */
public final class BrickMissingPartsScreen extends Screen {
    private final ProjectImportScreen previous;
    private StudioProjectAnalysis.Summary summary;
    private final String source;
    private int page=0;
    private List<MissingPartsReport.Missing> entries;
    private String status="Telecharge les .dat depuis une source fiable puis recharge la bibliotheque.";
    public BrickMissingPartsScreen(ProjectImportScreen previous,StudioProjectAnalysis.Summary summary,String source){
        super(Text.literal(BrickI18n.tr("BrickCraft - Pieces manquantes")));this.previous=previous;this.summary=summary;this.source=source;
        entries=MissingPartsReport.list(summary,StudioCustomPartsInstaller.names(sourcePath()));
        if(entries.isEmpty())status="";
    }
    private Path sourcePath(){return FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("imports").resolve(source);}
    private Path customDir(){return FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("parts_custom");}
    private Path reportDir(){return FabricLoader.getInstance().getConfigDir().resolve("brickcraft").resolve("imports").resolve("reports");}
    private void browse(String url){
        try{if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IllegalStateException("Navigateur absent");
            Desktop.getDesktop().browse(URI.create(url));}
        catch(Exception ex){status=BrickI18n.fmt("brickcraft.v252.missing.browser",ex.getMessage());}
    }
    private void report(){
        try{Path html=MissingPartsReport.write(summary,reportDir(),source,StudioCustomPartsInstaller.names(sourcePath()));
            if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))Desktop.getDesktop().browse(html.toUri());
            status=BrickI18n.fmt("brickcraft.v251.missing.report_saved");}
        catch(Exception ex){status=BrickI18n.fmt("brickcraft.v252.missing.report_fail",ex.getMessage());}
    }
    private void folder(){
        try{Files.createDirectories(customDir());if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.OPEN))Desktop.getDesktop().open(customDir().toFile());
            else status=BrickI18n.fmt("brickcraft.v252.missing.folder",customDir().toAbsolutePath());}
        catch(Exception ex){status=BrickI18n.fmt("brickcraft.v252.missing.folder",customDir().toAbsolutePath());}
    }
    private void importEmbedded(){
        try{
            var installed=StudioCustomPartsInstaller.install(sourcePath(),customDir());
            LDrawLibrary.init(FabricLoader.getInstance().getConfigDir());
            status=BrickI18n.fmt("brickcraft.v252.missing.embedded",installed.added(),installed.skipped());
        }catch(Exception ex){status=BrickI18n.fmt("brickcraft.v252.missing.install_fail",ex.getMessage());}
    }
    private void reload(){
        LDrawLibrary.init(FabricLoader.getInstance().getConfigDir());entries=MissingPartsReport.list(summary,StudioCustomPartsInstaller.names(sourcePath()));
        // Re-analyse on return to import screen for accurate resolved/missing counts.
        status=BrickI18n.fmt("brickcraft.v251.missing.reloaded",LDrawLibrary.size());
    }
    /** Refresh the list after confirmed, in-memory project modifications. */
    int replaceMissing(String from,String target){
        int changed=previous.replaceMissingPart(from,target);
        if(changed>0){
            summary=previous.currentMissingSummary();
            entries=MissingPartsReport.list(summary,StudioCustomPartsInstaller.names(sourcePath()));
            page=0;
            status=BrickI18n.fmt("brickcraft.v252.missing.replaced",changed,from,target);
            clearAndInit();
        }
        return changed;
    }
    private void replacements(MissingPartsReport.Missing item){
        client.setScreen(new BrickPartReplacementScreen(this,item.id(),item.name(),item.count()));
    }
    private void refreshAfterDeletion(int removed, String label){
        if(removed<=0){status="Aucune piece supprimee : rapport a actualiser.";return;}
        summary=previous.currentMissingSummary();
        entries=MissingPartsReport.list(summary,StudioCustomPartsInstaller.names(sourcePath()));
        page=0;
        status=BrickI18n.fmt("brickcraft.v252.missing.removed",removed,label);
        clearAndInit();
    }
    private void confirmRemove(MissingPartsReport.Missing item){
        client.setScreen(new BrickConfirmScreen(this,BrickI18n.fmt("brickcraft.v252.missing.remove_title",item.id()),
                BrickI18n.fmt("brickcraft.v252.missing.remove_detail",item.count()),
                ()->refreshAfterDeletion(previous.removeMissingPart(item.id()),item.id())));
    }
    private void confirmRemoveAll(){
        if(entries.isEmpty()){status="Aucune piece manquante a supprimer.";return;}
        int count=summary.missingInstances();
        int references=entries.size();
        client.setScreen(new BrickConfirmScreen(this,BrickI18n.fmt("brickcraft.v252.missing.remove_all_title"),
                BrickI18n.fmt("brickcraft.v252.missing.remove_all_detail",count,references),
                ()->refreshAfterDeletion(previous.removeAllMissingParts(),BrickI18n.fmt("brickcraft.v252.missing.all_missing"))));
    }
    private int rows(){return Math.max(1,Math.min(8,(height-202)/26));}
    private void add(int x,int y,int w,int h,String label,Runnable run){
        addDrawableChild(ButtonWidget.builder(Text.literal(BrickI18n.tr(label)),b->run.run()).dimensions(x,y,w,h).build());
    }
    @Override protected void init(){
        int x=Math.max(8,width/2-310),w=Math.min(620,width-16);
        int rows=rows();
        page=Math.min(page,Math.max(0,(entries.size()-1)/rows));
        for(int k=0;k<rows;k++){
            int i=page*rows+k;if(i>=entries.size())break;
            var item=entries.get(i);int y=58+k*26;
            int buttonStart=x+w-238;
            add(buttonStart,y,89,21,"Equivalents",()->replacements(item));
            add(buttonStart+92,y,79,21,"Chercher",()->browse(item.queryUrl()));
            add(buttonStart+174,y,64,21,"Suppr.",()->confirmRemove(item));
        }
        int y=height-155;
        if(!entries.isEmpty()){
            add(x,y,115,21,"< Page",()->{if(page>0)page--;clearAndInit();});
            add(x+120,y,115,21,"Page >",()->{if((page+1)*rows<entries.size())page++;clearAndInit();});
            add(x+240,y,w-240,21,"Supprimer tout",this::confirmRemoveAll);
        }
        if(!entries.isEmpty()){
            add(x,y+26,w/2-3,21,"Importer .dat inclus dans .io",this::importEmbedded);
            add(x+w/2+3,y+26,w/2-3,21,"Dossier .dat",this::folder);
            add(x,y+52,w/2-3,21,"Rapport HTML + CSV",this::report);
        }
        add(x+w/2+3,y+52,w/2-3,21,"Recharger LDraw",this::reload);
        add(x,y+78,w,21,"Retour import",()->close());
    }
    @Override public void renderBackground(DrawContext d,int mx,int my,float dt){}
    @Override public void render(DrawContext d,int mx,int my,float dt){
        d.fill(0,0,width,height,0xFF0A1420);
        int x=Math.max(8,width/2-310),w=Math.min(620,width-16);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("BRICKCRAFT / PIECES MANQUANTES"),width/2,11,0xFF9DE5E5);
        d.drawCenteredTextWithShadow(textRenderer,BrickI18n.fmt("brickcraft.v251.missing.count",summary.missingInstances(),entries.size()),width/2,32,0xFFE6C897);
        int rows=rows();
        for(int k=0;k<rows;k++){
            int i=page*rows+k;if(i>=entries.size())break;
            var item=entries.get(i);int yy=62+k*26;
            String description=item.name();
            if(description.startsWith("Description indisponible"))description=BrickI18n.fmt("brickcraft.v252.missing.description");
            String s=BrickI18n.tr(item.id()+"  x"+item.count()+"   "+description);
            if(textRenderer.getWidth(s)>w-246)s=textRenderer.trimToWidth(s,Math.max(8,w-258))+"…";
            d.drawTextWithShadow(textRenderer,s,x+3,yy,0xFFD4E9EF);
        }
        if(entries.isEmpty())d.drawCenteredTextWithShadow(textRenderer,BrickI18n.tr("Aucune reference manquante : projet pret a etre sauvegarde."),width/2,78,0xFF8DE5C6);
        d.fill(0,height-21,width,height,0xFF132B37);
        String line=BrickI18n.tr(status);
        if(textRenderer.getWidth(line)>width-18)
            line=textRenderer.trimToWidth(line,Math.max(10,width-18-textRenderer.getWidth("...")))+"...";
        d.drawTextWithShadow(textRenderer,line,8,height-15,0xFFFFD19D);
        super.render(d,mx,my,dt);
    }
    @Override public void close(){client.setScreen(previous);}
}
