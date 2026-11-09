package fr.brickcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseReferenceMapper;
import fr.brickcraft.importer.PreciseWorldModel;
import fr.brickcraft.guide.BrickGuideGpuBatches;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.world.PreciseDisplayEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.9.5.1 corrected GPU path. Static, camera-independent LEGO triangles are uploaded
 * once as POSITION_COLOR TRIANGLES. Normal frames only bind and draw existing buffers.
 * IMPORTANT: all calls (including clear) must take place on the client render thread.
 * The legacy 0.9.4 renderer remains available through gpu_render_mode=legacy.
 */
final class PreciseGpuRenderer {
    private record Key(int x,int y,int z){}
    private static final int VERTEX_BYTES=36; // POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL (Yarn 1.21.1)
    private final int cellSize,uploadsPerFrame,limitBytes;
    private int qualityPercent;
    private final boolean contactShadows;
    private Object currentWorld;
    private final LinkedHashMap<BlockPos,Scene> scenes=new LinkedHashMap<>(8,.75f,true);
    private final Map<String,LDrawLibrary.Mesh> meshCache=new HashMap<>();
    private int memoryBytes;
    private long uploadedTriangles,renderedTriangles,drawCalls,lastDrawNanos,lastUploadNanos;
    private int pendingSections;
    private int memoryBlockedSections;
    private boolean visibility=true;
    // In V0.9.5, experimental camera-direction culling could hide valid sections.
    // Disabled by default until a tested frustum/AABB implementation replaces it.
    private boolean cullingEnabled;
    // The BER's MatrixStack is not necessarily the complete view matrix for direct VBOs.
    // Explicit camera view is the safe default; prior implementation is diagnostic only.
    private boolean explicitCameraView;
    private int totalSections,visibleSections,culledSections,readySections,unloadedSections;


    /**
     * quality: 0=tiny, 1=low, 2=medium, 3=full (all are original LEGO meshes).
     */
    PreciseGpuRenderer(Properties settings){
        cellSize=integer(settings,"gpu_section_blocks",12,4,32);
        uploadsPerFrame=integer(settings,"gpu_uploads_per_frame",2,1,12);
        limitBytes=integer(settings,"gpu_max_memory_mb",512,32,1536)*1024*1024;
        contactShadows=!settings.getProperty("gpu_contact_shadows","on").equalsIgnoreCase("off");
        String q=settings.getProperty("gpu_mesh_quality","low").trim().toLowerCase(Locale.ROOT);
        int initial=q.equals("tiny")?10:q.equals("medium")?60:q.equals("full")?100:25;
        qualityPercent=integer(settings,"gpu_mesh_detail_percent",initial,0,100);
        cullingEnabled=settings.getProperty("gpu_culling","off").trim().equalsIgnoreCase("legacy");
        explicitCameraView=!settings.getProperty("gpu_view_matrix","explicit")
                .trim().equalsIgnoreCase("entity");
    }
    private static int integer(Properties p,String key,int fallback,int lo,int hi){
        try{return Math.clamp(Integer.parseInt(p.getProperty(key,"")),lo,hi);}
        catch(NumberFormatException ignored){return fallback;}
    }
    private static final class Section {
        final List<Integer> indices=new ArrayList<>();
        // Guided display groups 24 consecutive steps into one uploadable VBO batch.
        // Only the current batch is partially re-uploaded after a navigation change.
        int firstStep=Integer.MAX_VALUE,lastStep=-1,uploadedStep=-1;
        int bucket;

        double loX=Double.POSITIVE_INFINITY,loY=Double.POSITIVE_INFINITY,loZ=Double.POSITIVE_INFINITY;
        double hiX=Double.NEGATIVE_INFINITY,hiY=Double.NEGATIVE_INFINITY,hiZ=Double.NEGATIVE_INFINITY;
        VertexBuffer opaque,transparent;
        int trianglesOpaque,trianglesTransparent,bytes;
        boolean complete;
        boolean memoryDeferred;
        void include(double x,double y,double z,double radius){
            loX=Math.min(loX,x-radius);loY=Math.min(loY,y-radius);loZ=Math.min(loZ,z-radius);
            hiX=Math.max(hiX,x+radius);hiY=Math.max(hiY,y+radius);hiZ=Math.max(hiZ,z+radius);
        }
        double cx(){return (loX+hiX)/2;}
        double cy(){return (loY+hiY)/2;}
        double cz(){return (loZ+hiZ)/2;}
        double radius(){return Math.sqrt(Math.pow(hiX-loX,2)+Math.pow(hiY-loY,2)+Math.pow(hiZ-loZ,2))*.5;}
        double distanceSquared(double x,double y,double z){
            double dx=Math.max(0,Math.max(loX-x,x-hiX));
            double dy=Math.max(0,Math.max(loY-y,y-hiY));
            double dz=Math.max(0,Math.max(loZ-z,z-hiZ));
            return dx*dx+dy*dy+dz*dz;
        }
        void close(){
            if(opaque!=null){opaque.close();opaque=null;}
            if(transparent!=null){transparent.close();transparent=null;}
        }
    }
    private static final class Scene {
        final PreciseWorldModel model;
        final List<Section> sections;
        final BlockPos anchor;
        final int guideRevision;
        final BrickModelOcclusion occlusion;
        Scene(PreciseWorldModel model,BlockPos anchor,List<Section> sections,int guideRevision,
              BrickModelOcclusion occlusion){
            this.model=model;this.anchor=anchor;this.sections=sections;
            this.guideRevision=guideRevision;this.occlusion=occlusion;
        }
        void close(){for(Section section:sections)section.close();}
    }

    private Scene getScene(PreciseDisplayEntity be,PreciseWorldModel model,Object world,
                           BrickGuideController.Filter guide){
        if(currentWorld!=world){close();currentWorld=world;}
        BlockPos pos=be.getPos();
        Scene current=scenes.get(pos);
        int revision=guide==null?0:guide.revision();
        if(current!=null && current.model==model && current.guideRevision==revision)return current;
        List<Section> next=planSections(model,pos,guide);
        // Incremental edit: reuse unchanged VBO sections instead of re-uploading
        // the entire 3,245-brick castle for every mouse wheel notch.
        if(current!=null && current.guideRevision==revision &&
                current.model.parts().size()==model.parts().size() &&
                current.model.minX()==model.minX()&&current.model.minY()==model.minY()&&
                current.model.minZ()==model.minZ()){
            boolean[] modified=new boolean[model.parts().size()];
            for(int i=0;i<modified.length;i++)
                modified[i]=!samePart(current.model.parts().get(i),model.parts().get(i));
            Map<List<Integer>,Section> existing=new HashMap<>();
            for(Section s:current.sections)existing.put(List.copyOf(s.indices),s);
            Set<Section> reused=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int i=0;i<next.size();i++){
                Section planned=next.get(i),older=existing.get(planned.indices);
                if(older==null)continue;
                boolean changed=false;for(int idx:planned.indices)if(modified[idx]){changed=true;break;}
                if(!changed){next.set(i,older);reused.add(older);}
            }
            for(Section old:current.sections)if(!reused.contains(old)){
                memoryBytes=Math.max(0,memoryBytes-old.bytes);old.close();
            }
            scenes.remove(pos);
        }else if(current!=null){
            memoryBytes=Math.max(0,memoryBytes-bytes(current));current.close();scenes.remove(pos);
        }
        Scene scene=new Scene(model,pos,next,revision,contactShadows?buildOcclusion(model):new BrickModelOcclusion());
        scenes.put(pos,scene);
        // Avoid unbounded GPU use when multiple projections exist in one world.
        while(scenes.size()>8){
            var it=scenes.entrySet().iterator();var oldest=it.next();
            memoryBytes-=bytes(oldest.getValue());oldest.getValue().close();it.remove();
        }
        return scene;
    }
    private static boolean samePart(LDrawProjectReader.Instance a,LDrawProjectReader.Instance b){
        return a.partId().equals(b.partId())&&a.color()==b.color() &&
                a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z() && Arrays.equals(a.matrix(),b.matrix());
    }
    private static int bytes(Scene scene){
        int n=0;for(Section section:scene.sections)n+=section.bytes;return n;
    }
    private record GuideCell(PreciseGpuPlan.Cell cell,int bucket) {}
    private List<Section> planSections(PreciseWorldModel model,BlockPos anchor,
                                       BrickGuideController.Filter guide){
        Map<GuideCell,Section> grouped=new LinkedHashMap<>();
        List<LDrawProjectReader.Instance> parts=model.parts();
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i);
            // Centre of the actual LDraw part (not just the stud location).
            String id=PreciseReferenceMapper.geometryId(p.partId());
            double[] b=id==null?null:LDrawLibrary.originalBounds(id);
            if(b==null)continue;
            double[] m=p.matrix();
            double x=(b[0]+b[3])*.5,y=(b[1]+b[4])*.5,z=(b[2]+b[5])*.5;
            double wx=(p.x()+m[0]*x+m[1]*y+m[2]*z)/20-model.minX();
            double wy=-(p.y()+m[3]*x+m[4]*y+m[5]*z)/20-model.minY();
            double wz=(p.z()+m[6]*x+m[7]*y+m[8]*z)/20-model.minZ();
            double dx=b[3]-b[0],dy=b[4]-b[1],dz=b[5]-b[2];
            double r=Math.sqrt(dx*dx+dy*dy+dz*dz)/40;
            int step=guide==null?0:guide.stepOfPart()[i];
            int bucket=guide==null?0:BrickGuideGpuBatches.bucket(step,guide.uploadBatchSize());
            GuideCell key=new GuideCell(PreciseGpuPlan.cell(wx,wy,wz,cellSize),bucket);
            Section section=grouped.computeIfAbsent(key,k->{Section newSection=new Section();
                newSection.bucket=bucket;return newSection;});
            section.indices.add(i);section.include(wx,wy,wz,r);
            section.firstStep=Math.min(section.firstStep,step);
            section.lastStep=Math.max(section.lastStep,step);
        }
        return new ArrayList<>(grouped.values());
    }
    /** Index axis-aligned bounds in local world blocks, once per model revision.
     * The index samples nearby pieces for contact shading; it is deliberately
     * bounded and never attempts an O(numberOfPieces^2) neighbour search.
     */
    private BrickModelOcclusion buildOcclusion(PreciseWorldModel model){
        BrickModelOcclusion shadows=new BrickModelOcclusion();
        var parts=model.parts();
        for(int part=0;part<parts.size();part++){
            var p=parts.get(part);
            String id=PreciseReferenceMapper.geometryId(p.partId());
            double[] bounds=id==null?null:LDrawLibrary.originalBounds(id);
            if(bounds==null)continue;
            double[] matrix=p.matrix();
            double ax=Double.POSITIVE_INFINITY,ay=Double.POSITIVE_INFINITY,az=Double.POSITIVE_INFINITY;
            double bx=Double.NEGATIVE_INFINITY,by=Double.NEGATIVE_INFINITY,bz=Double.NEGATIVE_INFINITY;
            for(int corner=0;corner<8;corner++){
                double x=bounds[(corner&1)==0?0:3];
                double y=bounds[(corner&2)==0?1:4];
                double z=bounds[(corner&4)==0?2:5];
                double wx=(p.x()+matrix[0]*x+matrix[1]*y+matrix[2]*z)/20-model.minX();
                double wy=-(p.y()+matrix[3]*x+matrix[4]*y+matrix[5]*z)/20-model.minY();
                double wz=(p.z()+matrix[6]*x+matrix[7]*y+matrix[8]*z)/20-model.minZ();
                ax=Math.min(ax,wx);ay=Math.min(ay,wy);az=Math.min(az,wz);
                bx=Math.max(bx,wx);by=Math.max(by,wy);bz=Math.max(bz,wz);
            }
            shadows.add(part,ax,ay,az,bx,by,bz);
        }
        return shadows;
    }

    private LDrawLibrary.Mesh mesh(String id){
        if(meshCache.containsKey(id))return meshCache.get(id);
        LDrawLibrary.Mesh full=LDrawLibrary.mesh(id);
        LDrawLibrary.Mesh selected=null;
        if(full!=null && !full.isEmpty()){
            if(qualityPercent>=100)selected=full;
            else{
                // Smooth 0..100% slider: a stronger mesh grid preserves round studs.
                // 100% is the FULL original LDraw mesh, without decimation.
                int subdivisions=qualityPercent<15?4:qualityPercent<30?8:
                    qualityPercent<45?12:qualityPercent<60?16:
                    qualityPercent<75?24:qualityPercent<90?32:64;
                selected=PreciseMeshLods.simplify(full,subdivisions);
            }
        }
        meshCache.put(id,selected);
        return selected;
    }
    private record Counters(int opaque,int transparent){}
    private static final class Builder {
        final BufferAllocator alloc;
        final BufferBuilder out;
        int tris;
        Builder(int estimatedBytes){
            alloc=new BufferAllocator(Math.max(4096,estimatedBytes));
            out=new BufferBuilder(alloc,VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }
        void v(float x,float y,float z,int color,float u,int light,float nx,float ny,float nz){
            out.vertex(x,y,z).color(color).texture(u,BrickPbrTexture.v())
                    .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light)
                    .normal(nx,ny,nz);
        }
        VertexBuffer upload(){
            if(tris==0){alloc.close();return null;}
            try(BuiltBuffer built=out.end()){
                VertexBuffer gpu=new VertexBuffer(VertexBuffer.Usage.STATIC);
                try{
                    gpu.bind();gpu.upload(built);return gpu;
                }catch(RuntimeException exc){gpu.close();throw exc;}
                finally{VertexBuffer.unbind();}
            }finally{alloc.close();}
        }
    }
    /** Load one whole spatial section and transfer it to the GPU exactly once. */
    private void uploadSection(Section section,Scene scene,
                               BrickGuideController.Filter guide,ClientWorld world){
        long start=System.nanoTime();
        PreciseWorldModel model=scene.model;
        BlockPos anchor=scene.anchor;
        BrickModelOcclusion occlusion=scene.occlusion;
        // Minecraft's exact directional constants (including Nether/End rules).
        float up=world.getBrightness(Direction.UP,true);
        float down=world.getBrightness(Direction.DOWN,true);
        float north=world.getBrightness(Direction.NORTH,true);
        float south=world.getBrightness(Direction.SOUTH,true);
        float east=world.getBrightness(Direction.EAST,true);
        float west=world.getBrightness(Direction.WEST,true);
        // 1 light engine query per unique block cell in this section; no per-frame queries.
        Map<Long,Integer> rawLight=new HashMap<>();
        // Estimate; BufferAllocator grows if real meshes exceed estimate.
        Builder opaque=new Builder(section.indices.size()*1200);
        Builder glass=new Builder(section.indices.size()*128);
        for(int i:section.indices){
            if(guide!=null && guide.stepOfPart()[i]>=guide.completedSteps())continue;
            var p=model.parts().get(i);String id=PreciseReferenceMapper.geometryId(p.partId());
            if(id==null)continue;
            double[] bounds=LDrawLibrary.originalBounds(id);
            LDrawLibrary.Mesh mesh=mesh(id);
            if(mesh==null || bounds==null)continue;
            float[] xyz=mesh.xyz();int[] colors=mesh.colors();double[] mat=p.matrix();
            double baseX=model.minX(),baseY=model.minY(),baseZ=model.minZ();
            int fallback=LDrawLibrary.resolveColor(p.color(),0xD0D0D0);
            float[] transformed=new float[9];
            for(int t=0;t<mesh.triangles();t++){
                int rgb=LDrawLibrary.meshColor(colors[t],p.color(),fallback);
                int finishCode=(colors[t]==16 || colors[t]==24)?p.color():colors[t];
                float materialU=BrickPbrTexture.u(finishCode,rgb);
                Builder dst=((rgb>>>24)&255)<250?glass:opaque;
                int o=t*9;
                for(int v=0;v<3;v++){
                    int j=o+v*3;
                    double x=bounds[0]+xyz[j]*20;
                    double y=bounds[4]-xyz[j+1]*20;
                    double z=bounds[2]+xyz[j+2]*20;
                    transformed[v*3]=(float)((p.x()+mat[0]*x+mat[1]*y+mat[2]*z)/20-baseX);
                    transformed[v*3+1]=(float)(-(p.y()+mat[3]*x+mat[4]*y+mat[5]*z)/20-baseY);
                    transformed[v*3+2]=(float)((p.z()+mat[6]*x+mat[7]*y+mat[8]*z)/20-baseZ);
                }
                // Smooth LDraw slope lighting, using the *same* directional multipliers
                // that vanilla Minecraft applies to block faces (instead of fake flat light).
                float nx=(transformed[4]-transformed[1])*(transformed[8]-transformed[2])-(transformed[5]-transformed[2])*(transformed[7]-transformed[1]);
                float ny=(transformed[5]-transformed[2])*(transformed[6]-transformed[0])-(transformed[3]-transformed[0])*(transformed[8]-transformed[2]);
                float nz=(transformed[3]-transformed[0])*(transformed[7]-transformed[1])-(transformed[4]-transformed[1])*(transformed[6]-transformed[0]);
                float mag=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(mag<1e-8f)continue;
                nx/=mag;ny/=mag;nz/=mag;
                float directional=BrickLightingMath.directional(nx,ny,nz,up,down,north,south,east,west);
                double centerX=(transformed[0]+transformed[3]+transformed[6])/3.0;
                double centerY=(transformed[1]+transformed[4]+transformed[7])/3.0;
                double centerZ=(transformed[2]+transformed[5]+transformed[8])/3.0;
                float exposure=occlusion.exposure(centerX,centerY,centerZ,nx,ny,nz,i);
                // Faces touching neighbouring LEGO pieces are slightly darker.
                int color=BrickLightingMath.shade(rgb,directional*(.78f+.22f*exposure));
                // Sample the world *outside* the triangle, not from its solid interior.
                int sx=(int)Math.floor(anchor.getX()+centerX+nx*.09);
                int sy=(int)Math.floor(anchor.getY()+centerY+ny*.09);
                int sz=(int)Math.floor(anchor.getZ()+centerZ+nz*.09);
                long sampleKey=BlockPos.asLong(sx,sy,sz);
                Integer cachedLight=rawLight.get(sampleKey);
                if(cachedLight==null){
                    BlockPos sample=new BlockPos(sx,sy,sz);
                    int block=world.getLightLevel(LightType.BLOCK,sample);
                    int sky=world.getLightLevel(LightType.SKY,sample);
                    cachedLight=(sky<<4)|block;
                    rawLight.put(sampleKey,cachedLight);
                }
                int raw=cachedLight;
                int block=raw&15,sky=(raw>>>4)&15;
                int uv=LightmapTextureManager.pack(block,BrickLightingMath.shadowSky(sky,exposure));
                // Lightmap stays responsive to day/night, weather, gamma and night vision
                // without constantly rebuilding the GPU geometry.
                for(int v=0;v<3;v++){
                    dst.v(transformed[v*3],transformed[v*3+1],transformed[v*3+2],color,materialU,uv,nx,ny,nz);
                }
                dst.tris++;
            }
        }
        // Guard against building an unlimited number of GPU buffers.
        int targetBytes=(opaque.tris+glass.tris)*3*VERTEX_BYTES;
        if(memoryBytes+targetBytes>limitBytes){
            // Cancel the native builders safely; leave the section pending for reporting.
            // upload() cleans temporary allocations, and buffers are immediately closed.
            VertexBuffer a=opaque.upload(),b=glass.upload();
            if(a!=null)a.close();if(b!=null)b.close();
            section.complete=true;section.memoryDeferred=true;
            memoryBlockedSections++;
            return;
        }
        VertexBuffer a=null,b=null;
        try{
            a=opaque.upload();b=glass.upload();
            section.opaque=a;section.transparent=b;
            section.trianglesOpaque=opaque.tris;section.trianglesTransparent=glass.tris;
            section.bytes=targetBytes;memoryBytes+=targetBytes;
            uploadedTriangles+=opaque.tris+glass.tris;
            section.complete=true;
        }catch(RuntimeException exc){
            if(a!=null)a.close();if(b!=null)b.close();throw exc;
        }finally{lastUploadNanos=System.nanoTime()-start;}
    }
    /** Direct VBO rendering with explicit world-to-camera transform.
     *  All VBO vertices are in model-local space relative to the BE anchor:
     *  worldPoint = anchor + localVertex. The camera matrix must first subtract the
     *  camera position from the anchor and then apply inverse camera rotation.
     *  Do not apply the BER MatrixStack *and* this translation (double transform).
     */
    private Matrix4f viewMatrix(PreciseDisplayEntity entity,MatrixStack matrices,
                                net.minecraft.client.render.Camera camera){
        if(!explicitCameraView){
            // Old V0.9.5 path, available only to compare screenshots (F10).
            return new Matrix4f(matrices.peek().getPositionMatrix());
        }
        Vec3d cam=camera.getPos(); BlockPos anchor=entity.getPos();
        // The quaternion describes camera-to-world; its conjugate is the view rotation.
        Quaternionf inverse=new Quaternionf(camera.getRotation()).conjugate();
        return new Matrix4f().rotate(inverse).translate(
                (float)((double)anchor.getX()-cam.x),
                (float)((double)anchor.getY()-cam.y),
                (float)((double)anchor.getZ()-cam.z));
    }
    void render(PreciseDisplayEntity entity,PreciseWorldModel model,MatrixStack matrices){
        // If a huge model exhausted the VBO budget, retry with lower detail rather
        // than silently leaving entire LEGO sections missing forever. One retry at
        // the start of the next render avoids mutating scene lists during an upload.
        if(memoryBlockedSections>0 && qualityPercent>10){
            qualityPercent=Math.max(10,qualityPercent*2/3);
            close();
        }
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world==null)return;
        BrickGuideController.Filter guide=BrickGuideController.filter(entity,model);
        Scene scene=getScene(entity,model,client.world,guide);
        if(scene.sections.isEmpty() && !model.parts().isEmpty())
            throw new IllegalStateException("Aucun lot GPU planifie : activer le renderer legacy");
        var camera=client.gameRenderer.getCamera();
        Vec3d cam=camera.getPos();
        double x=cam.x-entity.getPos().getX(),y=cam.y-entity.getPos().getY(),z=cam.z-entity.getPos().getZ();
        List<Section> visible=new ArrayList<>(scene.sections.size());
        totalSections=scene.sections.size();culledSections=0;
        if(cullingEnabled){
            Vector3f forward=new Vector3f(0,0,-1).rotate(camera.getRotation());
            for(Section s:scene.sections){
                if(PreciseGpuPlan.behindCamera(x,y,z,s.cx(),s.cy(),s.cz(),forward.x,forward.y,forward.z,s.radius())){
                    culledSections++;continue;
                }
                visible.add(s);
            }
        }else{
            // The safe mode neither hides NOR skips preparation of any section.
            visible.addAll(scene.sections);
        }
        // Avoid building/drawing future pieces; no block is removed from the save.
        if(guide!=null)visible.removeIf(s->s.firstStep>=guide.completedSteps());
        visible.sort(Comparator.comparingDouble(s->s.distanceSquared(x,y,z)));
        visibleSections=visible.size();
        int remainingUploads=uploadsPerFrame;
        pendingSections=0;
        for(Section s:visible){
            // A batch is complete if its last step precedes the current reveal boundary.
            // If only part of this batch is visible, upload exactly those triangles.
            int required=guide==null?Integer.MAX_VALUE:
                    BrickGuideGpuBatches.uploadStamp(guide.completedSteps(),s.firstStep,s.lastStep);
            if(s.complete && s.uploadedStep!=required){
                memoryBytes=Math.max(0,memoryBytes-s.bytes);
                s.close();s.bytes=0;s.complete=false;s.memoryDeferred=false;
                s.trianglesOpaque=s.trianglesTransparent=0;
            }
            if(s.complete)continue;
            pendingSections++;
            if(remainingUploads>0){
                uploadSection(s,scene,guide,client.world);s.uploadedStep=required;remainingUploads--;
            }
        }
        // Keep counter semantics explicit: complete but memory-deferred is NOT ready.
        readySections=0;unloadedSections=0;
        for(Section s:scene.sections){
            if(s.memoryDeferred || !s.complete)unloadedSections++;
            else readySections++;
        }
        if(!visibility)return;
        long start=System.nanoTime();renderedTriangles=0;drawCalls=0;
        Matrix4f view=viewMatrix(entity,matrices,camera);
        Matrix4f projection=RenderSystem.getProjectionMatrix();
        // This is the real vanilla 16x16 block/sky lightmap, not a static RGB guess.
        client.gameRenderer.getLightmapTextureManager().enable();
        client.gameRenderer.getOverlayTexture().setupOverlayColor();
        RenderSystem.setShaderTexture(0,BrickPbrTexture.ATLAS);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull(); // LDraw often has mixed-face winding.
        try{
            RenderSystem.disableBlend();RenderSystem.depthMask(true);
            for(Section s:visible)if(s.opaque!=null){
                s.opaque.bind();s.opaque.draw(view,projection,GameRenderer.getRenderTypeEntitySolidProgram());
                renderedTriangles+=s.trianglesOpaque;drawCalls++;
            }
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
            // Translucent batches are sorted far to near at section granularity.
            for(int i=visible.size()-1;i>=0;i--){Section s=visible.get(i);
                if(s.transparent!=null){
                    s.transparent.bind();s.transparent.draw(view,projection,GameRenderer.getRenderTypeEntityTranslucentProgram());
                    renderedTriangles+=s.trianglesTransparent;drawCalls++;
                }
            }
        }finally{
            VertexBuffer.unbind();RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.enableCull();
            client.gameRenderer.getOverlayTexture().teardownOverlayColor();
            lastDrawNanos=System.nanoTime()-start;
        }
    }
    String stats(){
        return "GPU DETAIL:"+qualityPercent+"% | CAMERA:" + (explicitCameraView?"EXPLICITE":"ANCIENNE")
                + " | LUMIERE:MINECRAFT | CONTACT:"+(contactShadows?"ON":"OFF")
                + " | CULL:"+(cullingEnabled?"ON":"OFF")
                + " | visibles:"+visibleSections+"/"+totalSections
                + " | prets:"+readySections+" manquants:"+unloadedSections+" rejetes:"+culledSections
                + "\n"+drawCalls+" draw | "+renderedTriangles+" tris | "
                +String.format(Locale.ROOT,"%.2f",lastDrawNanos/1e6)+" ms CPU"
                + " | VRAM estim.:"+(memoryBytes/1048576)+" MiB"
                +(memoryBlockedSections>0?" | MEMOIRE BLOQUE "+memoryBlockedSections:"");
    }
    int qualityPercent(){return qualityPercent;}
    void setQualityPercent(int next){
        next=Math.clamp(next,0,100);
        if(qualityPercent==next)return;
        qualityPercent=next;
        close(); // GL context: invoked from the Minecraft client render thread only.
    }
    boolean toggleViewMatrix(){explicitCameraView=!explicitCameraView;return explicitCameraView;}
    boolean toggleCulling(){cullingEnabled=!cullingEnabled;return cullingEnabled;}
    boolean explicitCameraView(){return explicitCameraView;}
    boolean cullingEnabled(){return cullingEnabled;}
    void refreshLighting(){close();}
    void setVisible(boolean v){visibility=v;}
    /** Release the exact projection's VBOs as soon as its anchor is removed. */
    void release(BlockPos pos){
        Scene s=scenes.remove(pos);
        if(s!=null){memoryBytes=Math.max(0,memoryBytes-bytes(s));s.close();}
    }
    void close(){
        for(Scene s:scenes.values())s.close();
        scenes.clear();meshCache.clear();memoryBytes=0;memoryBlockedSections=0;currentWorld=null;
    }
}
