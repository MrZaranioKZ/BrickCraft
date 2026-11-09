package fr.brickcraft.client;

import fr.brickcraft.importer.LDrawProjectReader;
import fr.brickcraft.importer.PreciseReferenceMapper;
import fr.brickcraft.importer.PreciseWorldModel;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.world.PreciseDisplayEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Renders an anchored BCPX assembly. All transformations remain full LDraw matrices. */
public final class PreciseDisplayRenderer implements BlockEntityRenderer<PreciseDisplayEntity> {
    private static final Identifier WHITE=BrickPbrTexture.ATLAS;
    // Keep an LRU cache: the selection budget is PER FRAME, not a permanent decision
    // about which parts can ever be displayed in high detail.
    private final LinkedHashMap<String,PreciseMeshLods.Levels> cachedMeshes=new LinkedHashMap<>(96,.75f,true);
    private final Set<String> noMesh=new HashSet<>();
    private final Map<String,double[]> boundsCache=new HashMap<>();
    private final boolean shapeOnlyLod;
    private final PreciseGpuRenderer gpu;
    private final boolean gpuMode;
    private boolean gpuFailed;
    private static boolean forceLegacy=false;
    public static boolean toggleLegacyMode(){forceLegacy=!forceLegacy;return forceLegacy;}
    public static String toggleGpuCameraMatrix(){
        if(current==null || current.gpu==null)return "Moteur GPU indisponible";
        return current.gpu.toggleViewMatrix()?"Camera explicite (corrigee)":"Matrice ancienne (diagnostic)";
    }
    public static String toggleGpuCulling(){
        if(current==null || current.gpu==null)return "Moteur GPU indisponible";
        return current.gpu.toggleCulling()?"Filtrage ancien ACTIVE (test seulement)":"Filtrage DESACTIVE (recommande)";
    }

    private static PreciseDisplayRenderer current;
    private static boolean projectionVisible=true;
    private static boolean statsVisible=false;
    public static void toggleVisibility(){projectionVisible=!projectionVisible;}
    public static boolean projectionVisible(){return projectionVisible;}
    public static void toggleStatistics(){statsVisible=!statsVisible;}
    public static String statistics(){
        if(!statsVisible)return null;
        if(current==null)return "BrickCraft : renderer pas encore charge";
        return current.gpuMode&&!current.gpuFailed&&!forceLegacy?current.gpu.stats():
                "BrickCraft : renderer LEGACY (F6 GPU), projection intacte";
    }
    public static int gpuQualityPercent(){
        return current!=null&&current.gpu!=null?current.gpu.qualityPercent():
                BrickGpuQualityConfig.load(FabricLoader.getInstance().getConfigDir());
    }
    public static void changeGpuQuality(int next){
        if(current!=null&&current.gpu!=null)current.gpu.setQualityPercent(next);
    }
    /** Re-samples changed torches/blocks without removing or modifying projections. */
    public static void refreshWorldLighting(){
        if(current!=null && current.gpu!=null)current.gpu.refreshLighting();
    }

    // Remember only projections seen by the renderer in this client world.
    // A virtual LDraw brick is not a Minecraft block, so vanilla block raycast cannot select it.
    private static Object activeWorld;
    private static final LinkedHashMap<net.minecraft.util.math.BlockPos,PreciseDisplayEntity> activeProjections=new LinkedHashMap<>();
    public static java.util.Collection<PreciseDisplayEntity> activeProjections(Object world){
        if(world!=activeWorld){activeWorld=world;activeProjections.clear();}
        java.util.ArrayList<PreciseDisplayEntity> result=new java.util.ArrayList<>();
        for(var entry:activeProjections.entrySet()){
            var be=entry.getValue();
            if(be.getWorld()==world && be.model()!=null &&
                    world instanceof net.minecraft.client.world.ClientWorld clientWorld &&
                    clientWorld.getBlockEntity(entry.getKey())==be)result.add(be);
        }
        return result;
    }
    public static void releaseProjection(net.minecraft.util.math.BlockPos pos){
        if(BrickGuideController.active()&&pos.equals(BrickGuideController.anchor()))
            BrickGuideController.hide();
        activeProjections.remove(pos);
        if(current!=null&&current.gpu!=null)current.gpu.release(pos);
    }
    public static void clearGpuOnDisconnect(){
        activeProjections.clear();activeWorld=null;
        if(current!=null && current.gpu!=null)current.gpu.close();
    }

    // Prepared world-space triangle data, keyed by source part/LOD identity.
    // Bounded LRU: accelerates static projections without changing saved BCPX files.
    private final LinkedHashMap<Integer,Prepared> prepared=new LinkedHashMap<>(512,.75f,true);
    private PreciseWorldModel preparedModel;
    private int preparedTriangleCount;
    private final int preparedTriangleLimit;
    private final int preparationsPerFrame;
    private record Prepared(LDrawLibrary.Mesh source,float[] positions,float[] normals,int[] colors,int[] materialCodes){
        int triangles(){return colors.length;}
    }

    private int totalCachedTriangles=0;
    private final int renderDistance;
    private final int maxRealTriangles;
    private final double meshRadius;
    private final int newMeshesPerFrame;
    private final int maxMeshTriangles;
    private final int cacheTriangleLimit;
    public PreciseDisplayRenderer(BlockEntityRendererFactory.Context unused){
        java.nio.file.Path config=FabricLoader.getInstance().getConfigDir();
        renderDistance=BrickRenderDistanceConfig.load(config);
        java.util.Properties properties=new java.util.Properties();
        java.nio.file.Path file=config.resolve("brickcraft").resolve("precise-render.properties");
        try{
            java.nio.file.Files.createDirectories(file.getParent());
            if(java.nio.file.Files.isRegularFile(file))try(var input=java.nio.file.Files.newInputStream(file)){
                properties.load(input);
            }
            else{
                properties.setProperty("max_real_triangles","28000");
                properties.setProperty("mesh_radius_blocks","42");
                properties.setProperty("lod_profile","balanced"); // balanced, performance, photo, custom
                properties.setProperty("shape_lod_mode","all"); // all, hybrid
                properties.setProperty("gpu_render_mode","gpu"); // gpu or legacy (V0.9.4)
                properties.setProperty("gpu_mesh_quality","low"); // tiny,low,medium,full
                properties.setProperty("gpu_section_blocks","12");
                properties.setProperty("gpu_uploads_per_frame","2");
                properties.setProperty("gpu_max_memory_mb","512");
                properties.setProperty("gpu_contact_shadows","on"); // optional approximate neighbouring-piece AO
                properties.setProperty("gpu_culling","off"); // off recommended; legacy experimental
                properties.setProperty("gpu_view_matrix","explicit"); // explicit recommended; entity diagnostic
                try(var out=java.nio.file.Files.newOutputStream(file)){
                    properties.store(out,"BrickCraft 0.9.5.1: GPU stabilized; culling off + explicit camera matrix");
                }
            }
        }catch(java.io.IOException ignored){}
        // One-time upgrade of the old 192 MiB DEFAULT on existing installations.
        // User-customised budgets other than 192 are never changed. A later manual
        // edit is preserved because the migration marker prevents repetition.
        if(!properties.containsKey("gpu_memory_migrated_v017")){
            if("192".equals(properties.getProperty("gpu_max_memory_mb","192").trim()))
                properties.setProperty("gpu_max_memory_mb","512");
            properties.setProperty("gpu_memory_migrated_v017","true");
            try(var output=java.nio.file.Files.newOutputStream(file)){
                properties.store(output,"BrickCraft 0.17 - GPU budget migration");
            }catch(java.io.IOException ignored){}
        }
        // Existing manual numeric settings are retained in balanced/custom modes.
        // Photo mode deliberately spends more time and memory on large constructions.
        String profile=properties.getProperty("lod_profile","balanced").strip().toLowerCase(Locale.ROOT);
        int budget=clampInt(properties.getProperty("max_real_triangles"),28000,4000,250000);
        int radius=clampInt(properties.getProperty("mesh_radius_blocks"),42,8,160);
        int meshes=4, perMesh=6000, cache=1750000;
        // all: never replace a loaded LDraw part by a cuboid for budget reasons.
        // hybrid: faster fallback outside the chosen detail radius.
        shapeOnlyLod=!properties.getProperty("shape_lod_mode","all")
                .strip().equalsIgnoreCase("hybrid");
        if(profile.equals("performance")){
            budget=16000;radius=32;meshes=3;perMesh=3000;cache=900000;
        } else if(profile.equals("photo")){
            budget=160000;radius=112;meshes=8;perMesh=12000;cache=3000000;
        } else if(profile.equals("custom")){
            meshes=clampInt(properties.getProperty("new_meshes_per_frame"),3,1,12);
            perMesh=clampInt(properties.getProperty("max_triangles_per_mesh"),6000,500,20000);
            cache=clampInt(properties.getProperty("cache_triangle_limit"),1750000,20000,6000000);
        }
        preparedTriangleLimit=clampInt(properties.getProperty("prepared_triangle_cache_limit"),
                380000,0,1500000);
        preparationsPerFrame=clampInt(properties.getProperty("prepare_parts_per_frame"),
                18,0,256);
        gpuMode=!properties.getProperty("gpu_render_mode","gpu").equalsIgnoreCase("legacy");
        gpu=gpuMode?new PreciseGpuRenderer(properties):null;
        current=this;
        maxRealTriangles=budget;meshRadius=radius;
        newMeshesPerFrame=meshes;maxMeshTriangles=perMesh;cacheTriangleLimit=cache;
    }
    private static int clampInt(String input,int fallback,int min,int max){
        try{return Math.max(min,Math.min(max,Integer.parseInt(input)));}
        catch(NumberFormatException ignored){return fallback;}
    }
    @Override public int getRenderDistance(){return renderDistance;}
    @Override public boolean rendersOutsideBoundingBox(PreciseDisplayEntity be){return true;}

    private double[] getBounds(String id){
        double[] b=boundsCache.get(id);if(b!=null)return b;
        b=LDrawLibrary.originalBounds(id);
        if(b!=null)boundsCache.put(id,b);
        return b;
    }
    private PreciseMeshLods.Levels getMesh(String id,int[] slots){
        PreciseMeshLods.Levels already=cachedMeshes.get(id);
        if(already!=null)return already;
        if(noMesh.contains(id)||slots[0]<=0)return null;
        slots[0]--;
        LDrawLibrary.Mesh mesh=LDrawLibrary.mesh(id);
        if(mesh==null||mesh.isEmpty()){noMesh.add(id);return null;}
        PreciseMeshLods.Levels all=PreciseMeshLods.generate(mesh);
        // Keep only reduced geometry when full detail exceeds the per-part allowance.
        if(mesh.triangles()>maxMeshTriangles){
            LDrawLibrary.Mesh reduced=all.medium().triangles()<=maxMeshTriangles?
                    all.medium():all.low().triangles()<=maxMeshTriangles?all.low():all.tiny();
            all=new PreciseMeshLods.Levels(reduced,all.medium(),all.low(),all.tiny());
        }
        int footprint=all.cachedTriangles();
        if(footprint>cacheTriangleLimit){
            // Rare enormous meshes may still use the low/tiny surface, never a permanent blacklist.
            LDrawLibrary.Mesh tiny=all.tiny();
            if(tiny.triangles()>cacheTriangleLimit)return null;
            all=new PreciseMeshLods.Levels(tiny,tiny,tiny,tiny);
            footprint=all.cachedTriangles();
        }
        while(totalCachedTriangles+footprint>cacheTriangleLimit && !cachedMeshes.isEmpty()){
            var iterator=cachedMeshes.entrySet().iterator();
            var oldest=iterator.next();
            totalCachedTriangles-=oldest.getValue().cachedTriangles();iterator.remove();
        }
        cachedMeshes.put(id,all);totalCachedTriangles+=footprint;
        return all;
    }

    /** Distance to the oriented part's enclosing world AABB, not its LDraw origin.
     * A large wall can start far away while a surface is directly in front of the camera.
     */
    private static double distanceSqToPart(LDrawProjectReader.Instance p,double[] b,
                PreciseWorldModel model,Vec3d camera,double ox,double oy,double oz){
        double[] m=p.matrix();
        double cx=(b[0]+b[3])*.5,cy=(b[1]+b[4])*.5,cz=(b[2]+b[5])*.5;
        double hx=(b[3]-b[0])*.025,hy=(b[4]-b[1])*.025,hz=(b[5]-b[2])*.025;
        double wx=ox+(p.x()+m[0]*cx+m[1]*cy+m[2]*cz)/20-model.minX();
        double wy=oy-(p.y()+m[3]*cx+m[4]*cy+m[5]*cz)/20-model.minY();
        double wz=oz+(p.z()+m[6]*cx+m[7]*cy+m[8]*cz)/20-model.minZ();
        double ex=Math.abs(m[0])*hx+Math.abs(m[1])*hy+Math.abs(m[2])*hz;
        double ey=Math.abs(m[3])*hx+Math.abs(m[4])*hy+Math.abs(m[5])*hz;
        double ez=Math.abs(m[6])*hx+Math.abs(m[7])*hy+Math.abs(m[8])*hz;
        double dx=Math.max(0,Math.abs(camera.x-wx)-ex);
        double dy=Math.max(0,Math.abs(camera.y-wy)-ey);
        double dz=Math.max(0,Math.abs(camera.z-wz)-ez);
        return dx*dx+dy*dy+dz*dz;
    }
    @Override public void render(PreciseDisplayEntity be,float tickDelta,MatrixStack matrices,
                                 VertexConsumerProvider provider,int light,int overlay){
        PreciseWorldModel model=be.model();if(model==null || !projectionVisible)return;
        Object world=be.getWorld();
        if(activeWorld!=world){activeWorld=world;activeProjections.clear();}
        activeProjections.put(be.getPos().toImmutable(),be);
        if(gpuMode&&!gpuFailed&&!forceLegacy){
            try{gpu.render(be,model,matrices);return;}
            catch(RuntimeException failure){
                gpuFailed=true;
                // Safety net: never lose the projection when the experimental GPU path fails.
                System.err.println("[BrickCraft 0.9.5.1] GPU renderer disabled after failure: "+failure);
                failure.printStackTrace();
                gpu.close();
            }
        }
        if(preparedModel!=model){preparedModel=model;prepared.clear();preparedTriangleCount=0;}
        // One consumer only, to avoid the old "Not building!" buffer lifecycle crash.
        VertexConsumer out=provider.getBuffer(RenderLayer.getEntityTranslucent(WHITE));
        Vec3d camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double ox=be.getPos().getX(),oy=be.getPos().getY(),oz=be.getPos().getZ();
        List<LDrawProjectReader.Instance> parts=model.parts();
        List<PreciseLodPlanner.Candidate> candidates=new ArrayList<>(parts.size());
        String[] ids=new String[parts.size()];double[][] bounds=new double[parts.size()][];

        // Distances are computed against actual oriented bounds, not source-file order.
        BrickGuideController.Filter guide=BrickGuideController.filter(be,model);
        for(int i=0;i<parts.size();i++){
            if(guide!=null && guide.stepOfPart()[i]>=guide.completedSteps())continue;
            var p=parts.get(i);String id=PreciseReferenceMapper.geometryId(p.partId());
            ids[i]=id;if(id==null)continue;
            double[] b=getBounds(id);bounds[i]=b;if(b==null)continue;
            double d=distanceSqToPart(p,b,model,camera,ox,oy,oz);
            candidates.add(new PreciseLodPlanner.Candidate(i,d));
        }
        // No hard 42-block cutoff for the *shape*. Only the DETAIL level uses that radius.
        // This avoids the frustrating near-cube / far-real-LEGO mixture.
        int[] slots={newMeshesPerFrame};
        PreciseMeshLods.Levels[] assets=new PreciseMeshLods.Levels[parts.size()];
        LDrawLibrary.Mesh[] selection=new LDrawLibrary.Mesh[parts.size()];
        for(var c:PreciseLodPlanner.nearestFirst(candidates,Double.MAX_VALUE)){
            int i=c.index();
            PreciseMeshLods.Levels levels=getMesh(ids[i],slots);
            assets[i]=levels;
            if(levels!=null && (shapeOnlyLod || c.distanceSq()<=meshRadius*meshRadius*2.25))
                selection[i]=levels.tiny();
        }
        // Improve nearby pieces in two controlled passes, prioritising the camera.
        // Base-level shapes are intentionally kept even when the extra-detail budget
        // runs out; use shape_lod_mode=hybrid if that is too costly for the hardware.
        int remaining=maxRealTriangles;
        var close=PreciseLodPlanner.nearestFirst(candidates,meshRadius*meshRadius);
        for(int pass=0;pass<2;pass++)for(var c:close){
            int i=c.index();PreciseMeshLods.Levels levels=assets[i];
            if(levels==null || selection[i]==null)continue;
            LDrawLibrary.Mesh target=pass==0?levels.low():
                    c.distanceSq()<=meshRadius*meshRadius*0.36?levels.full():levels.medium();
            int extra=target.triangles()-selection[i].triangles();
            if(extra>0 && extra<=remaining){selection[i]=target;remaining-=extra;}
        }
        int preparedThisFrame=0;
        for(int i=0;i<parts.size();i++){
            if(guide!=null && guide.stepOfPart()[i]>=guide.completedSteps())continue;
            var p=parts.get(i);String id=ids[i];if(id==null)continue;
            LDrawLibrary.Mesh mesh=selection[i];
            if(mesh!=null && bounds[i]!=null){
                Prepared cached=preparedTriangleLimit==0?null:prepared.get(i);
                if(cached!=null && cached.source()!=mesh){
                    preparedTriangleCount-=cached.triangles();prepared.remove(i);cached=null;
                }
                if(cached==null && preparedThisFrame<preparationsPerFrame
                        && mesh.triangles()<=preparedTriangleLimit){
                    cached=prepare(p,mesh,bounds[i],model);
                    if(cached!=null){
                        while(preparedTriangleCount+cached.triangles()>preparedTriangleLimit
                                && !prepared.isEmpty()){
                            var it=prepared.entrySet().iterator();var eldest=it.next();
                            preparedTriangleCount-=eldest.getValue().triangles();it.remove();
                        }
                        prepared.put(i,cached);preparedTriangleCount+=cached.triangles();
                        preparedThisFrame++;
                    }
                }
                if(cached!=null)drawPrepared(out,matrices,cached,light);
                else drawMesh(out,matrices,p,mesh,bounds[i],model,light);
            }else drawBox(out,matrices,p,id,model,light);
        }
    }
    /** Prepare static world-space triangle coordinates, colors and normals once per LOD.
     * The vertex consumer is still used each frame; GPU-side batching/VBOs are a later step.
     */
    private static Prepared prepare(LDrawProjectReader.Instance p,LDrawLibrary.Mesh mesh,
                                    double[] bounds,PreciseWorldModel model){
        int count=mesh.triangles();
        if(count<=0)return null;
        float[] xyz=mesh.xyz();int[] faceColors=mesh.colors();double[] matrix=p.matrix();
        float[] pos=new float[count*9],norm=new float[count*3];int[] colors=new int[count],materials=new int[count];
        float[] v=new float[3];
        for(int i=0;i<count;i++){
            int off=i*9;
            for(int j=0;j<3;j++){
                int k=off+j*3;
                transformInto(v,p,model,matrix,bounds[0]+xyz[k]*20,
                        bounds[4]-xyz[k+1]*20,bounds[2]+xyz[k+2]*20);
                pos[k]=v[0];pos[k+1]=v[1];pos[k+2]=v[2];
            }
            float ux=pos[off+3]-pos[off],uy=pos[off+4]-pos[off+1],uz=pos[off+5]-pos[off+2];
            float vx=pos[off+6]-pos[off],vy=pos[off+7]-pos[off+1],vz=pos[off+8]-pos[off+2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            norm[i*3]=len<1e-7f?0:nx/len;
            norm[i*3+1]=len<1e-7f?0:ny/len;
            norm[i*3+2]=len<1e-7f?0:nz/len;
            colors[i]=LDrawLibrary.meshColor(faceColors[i],p.color(),
                    LDrawLibrary.resolveColor(p.color(),0xD0D0D0));
            materials[i]=(faceColors[i]==16 || faceColors[i]==24)?p.color():faceColors[i];
        }
        return new Prepared(mesh,pos,norm,colors,materials);
    }
    private static void drawPrepared(VertexConsumer out,MatrixStack matrices,Prepared cache,int light){
        float[] xyz=cache.positions(),norm=cache.normals();int[] colors=cache.colors();int[] materials=cache.materialCodes();
        var entry=matrices.peek();
        for(int i=0;i<colors.length;i++){
            int ix=i*9,ni=i*3;
            if(norm[ni]==0 && norm[ni+1]==0 && norm[ni+2]==0)continue;
            // Entity render layers use QUADS. Fourth corner repeats the last vertex
            // to encode one triangle; do not change this to three without changing layer.
            for(int j=0;j<4;j++){
                int k=ix+Math.min(j,2)*3;
                out.vertex(entry,xyz[k],xyz[k+1],xyz[k+2]).color(colors[i])
                    .texture(BrickPbrTexture.u(materials[i],colors[i]),BrickPbrTexture.v()).overlay(OverlayTexture.DEFAULT_UV).light(light)
                    .normal(entry,norm[ni],norm[ni+1],norm[ni+2]);
            }
        }
    }
    /** One LDraw vertex, converted from original units through the source 3x3 matrix. */
    private static float[] transform(LDrawProjectReader.Instance p,PreciseWorldModel model,
                                     double[] m,double lx,double ly,double lz){
        float[] vertex=new float[3];
        transformInto(vertex,p,model,m,lx,ly,lz);
        return vertex;
    }
    private static void transformInto(float[] dst,LDrawProjectReader.Instance p,PreciseWorldModel model,
                                      double[] m,double lx,double ly,double lz){
        dst[0]=(float)((p.x()+m[0]*lx+m[1]*ly+m[2]*lz)/20-model.minX());
        dst[1]=(float)(-(p.y()+m[3]*lx+m[4]*ly+m[5]*lz)/20-model.minY());
        dst[2]=(float)((p.z()+m[6]*lx+m[7]*ly+m[8]*lz)/20-model.minZ());
    }
    private static int drawMesh(VertexConsumer out,MatrixStack mat,LDrawProjectReader.Instance p,
                                LDrawLibrary.Mesh mesh,double[] bounds,PreciseWorldModel model,int light){
        float[] xyz=mesh.xyz();int[] faceColors=mesh.colors();double[] matrix=p.matrix();
        // Fallback while a cached triangle buffer is being prepared; avoids large one-frame stalls.
        // Reuse temporary positions, avoiding hundreds of thousands of tiny heap allocations
        // on a multi-thousand-part castle every frame.
        float[] a=new float[3],b=new float[3],c=new float[3];
        for(int i=0;i<mesh.triangles();i++){
            for(int j=0;j<3;j++){
                int index=i*9+j*3;
                double x=bounds[0]+xyz[index]*20,y=bounds[4]-xyz[index+1]*20,
                       z=bounds[2]+xyz[index+2]*20;
                transformInto(j==0?a:j==1?b:c,p,model,matrix,x,y,z);
            }
            int color=LDrawLibrary.meshColor(faceColors[i],p.color(),
                    LDrawLibrary.resolveColor(p.color(),0xD0D0D0));
            // Entity translucent is non-culling, so duplicate reverse-winding triangles
            // were wasted vertices and could trigger depth artifacts.
            emitTriangle(out,mat,a,b,c,color,(faceColors[i]==16 || faceColors[i]==24)?p.color():faceColors[i],light);
        }
        return mesh.triangles();
    }
    private void drawBox(VertexConsumer out,MatrixStack mat,LDrawProjectReader.Instance p,
                                String id,PreciseWorldModel model,int light){
        double[] b=getBounds(id);
        if(b==null)return;
        double[] matrix=p.matrix();float[][] corners=new float[8][];
        for(int i=0;i<8;i++){
            double x=b[(i&1)==0?0:3],y=b[(i&2)==0?1:4],z=b[(i&4)==0?2:5];
            corners[i]=transform(p,model,matrix,x,y,z);
        }
        int color=LDrawLibrary.argb(p.color(),LDrawLibrary.resolveColor(p.color(),0xCCCCCC));
        int[][] sides={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        for(int[] face:sides){
            emitTriangle(out,mat,corners[face[0]],corners[face[1]],corners[face[2]],color,p.color(),light);
            emitTriangle(out,mat,corners[face[0]],corners[face[2]],corners[face[3]],color,p.color(),light);
        }
    }
    private static void emitTriangle(VertexConsumer out,MatrixStack mat,float[] a,float[] b,float[] c,
                                     int argb,int colorCode,int light){
        float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
        float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
        float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(len<1e-7f)return;
        nx/=len;ny/=len;nz/=len;
        // QUADS pipeline requires four corners even for a triangle.
        for(int i=0;i<4;i++){
            float[] v=i==0?a:i==1?b:c;
            out.vertex(mat.peek(),v[0],v[1],v[2])
                .color(argb).texture(BrickPbrTexture.u(colorCode,argb),BrickPbrTexture.v()).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(mat.peek(),nx,ny,nz);
        }
    }
}
