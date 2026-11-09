package fr.brickcraft.client;

import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.world.BrickCellEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.fabricmc.loader.api.FabricLoader;

/** Renders each one-stud world cell independently (no oversized BER culling). */
public final class BrickCellRenderer implements BlockEntityRenderer<BrickCellEntity> {
    private static final Identifier WHITE = BrickPbrTexture.ATLAS;
    // Block entity renderer distance is INDEPENDENT of Minecraft's chunk render distance.
    // The default vanilla radius is too short for BrickPalette's panoramic viewpoints.
    private final int renderDistance;
    public BrickCellRenderer(BlockEntityRendererFactory.Context ignored) {
        this.renderDistance = BrickRenderDistanceConfig.load(
                FabricLoader.getInstance().getConfigDir());
    }

    @Override public int getRenderDistance() {return renderDistance;}


    @Override public void render(BrickCellEntity cell,float tickDelta,MatrixStack matrices,
                                 VertexConsumerProvider providers,int light,int overlay) {
        BlockPos origin = cell.getPos();
        // VertexConsumerProvider.Immediate can end a BufferBuilder when a different
        // RenderLayer is requested. Keeping BOTH consumers alive (solid + translucent)
        // and writing to the former after requesting the latter crashes with
        // IllegalStateException: Not building!. Render in two consecutive passes,
        // obtaining the layer's consumer only immediately before drawing its faces.
        // LDraw may contain translucent sub-parts, so render all its faces in the
        // translucent pass; ordinary bricks use their actual LDraw colour alpha.
        var pieces = cell.pieces();
        for (int pass = 0; pass < 2; pass++) {
            boolean translucentPass = pass == 1;
            VertexConsumer out = null;
            for (BrickPiece p : pieces) {
                boolean ldraw = p.kind() == BrickKind.LDRAW_PART;
                boolean translucent = ldraw ||
                        ((LDrawLibrary.argb(p.colorCode(), p.rgb()) >>> 24) < 255);
                if (translucent != translucentPass) continue;
                // Don't open a buffer for an LDraw piece belonging to another cell.
                if (ldraw && !origin.equals(p.renderAnchor())) continue;
                if (out == null) out = providers.getBuffer(translucentPass
                        ? RenderLayer.getEntityTranslucent(WHITE)
                        : RenderLayer.getEntitySolid(WHITE));
                if (ldraw) renderLDraw(out, matrices, p, origin, light);
                else if (!p.tilted() && p.angle() % 90 == 0 && !p.kind().slope)
                    renderFragment(out, matrices, p, origin, light);
                else if (origin.equals(p.renderAnchor()))
                    renderWholeAngle(out, matrices, p, origin, light);
            }
        }
    }

    @Override public boolean rendersOutsideBoundingBox(BrickCellEntity entity) { return true; }

    /** Lazy real LDraw geometry, colored per face. Approximate box used only for placement. */
    private static void renderLDraw(VertexConsumer out,MatrixStack matrices,
                                    BrickPiece p,BlockPos cell,int light){
        LDrawLibrary.Mesh mesh=LDrawLibrary.mesh(p.partId());
        if(mesh==null)return;
        matrices.push();
        double[] center=p.center();
        matrices.translate(center[0]-cell.getX(),center[1]-cell.getY(),center[2]-cell.getZ());
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(p.angle()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.pitch()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.roll()));
        matrices.translate(-mesh.width()/2.,-mesh.height()/2.,-mesh.depth()/2.);
        float[] coords=mesh.xyz(); int[] codes=mesh.colors();
        for(int i=0;i<mesh.triangles();i++){
            int c=codes[i];
            int argb=LDrawLibrary.meshColor(c,p.colorCode(),p.rgb());
            int finishCode=(c==16 || c==24)?p.colorCode():c;
            int off=i*9;
            triangle(out,matrices,argb,finishCode,light,coords,off,false);
            triangle(out,matrices,argb,finishCode,light,coords,off,true);
        }
        matrices.pop();
    }
    private static void triangle(VertexConsumer out,MatrixStack mat,int argb,int finishCode,int light,
                                 float[] xyz,int off,boolean reverse){
        int a=reverse?6:0,b=3,c=reverse?0:6;
        float ux=xyz[off+b]-xyz[off+a],uy=xyz[off+b+1]-xyz[off+a+1],uz=xyz[off+b+2]-xyz[off+a+2];
        float vx=xyz[off+c]-xyz[off+a],vy=xyz[off+c+1]-xyz[off+a+1],vz=xyz[off+c+2]-xyz[off+a+2];
        float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(len<0.000001f)return;
        nx/=len;ny/=len;nz/=len;
        int color=LDrawLibrary.surfaceTint(argb,finishCode,nx,ny,nz,off);
        // Minecraft entity buffers expect QUADS. Duplicate vertex 3 into vertex 4.
        int[] order={a,b,c,c};
        for(int j=0;j<4;j++){
            int k=off+order[j];
            out.vertex(mat.peek(),xyz[k],xyz[k+1],xyz[k+2])
                .color(color).texture(BrickPbrTexture.u(finishCode,argb),BrickPbrTexture.v())
                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(mat.peek(),nx,ny,nz);
        }
    }

    /** Render an arbitrary-angle piece ONCE, on its home cell, not 4-64 times. */
    private static void renderWholeAngle(VertexConsumer out,MatrixStack matrices,
                                         BrickPiece p,BlockPos cell,int light) {
        matrices.push();
        double[] center=p.center();
        matrices.translate(center[0]-cell.getX(),center[1]-cell.getY(),center[2]-cell.getZ());
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(p.angle()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.pitch()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.roll()));
        matrices.translate(-p.kind().width/2.,-p.kind().plates*.2,-p.kind().length/2.);
        float w=p.kind().width,d=p.kind().length,h=p.height()/5f,eps=.009f;
        int color=LDrawLibrary.argb(p.colorCode(),p.rgb());
        float a=eps,b=w-eps,c=eps,e=d-eps;
        float far=p.kind().slope ? h*.14f : h;
        face(out,matrices,color,p.colorCode(),light,0,1,0,a,h,c,a,far,e,b,far,e,b,h,c);
        face(out,matrices,color,p.colorCode(),light,0,-1,0,a,0,e,a,0,c,b,0,c,b,0,e);
        face(out,matrices,color,p.colorCode(),light,-1,0,0,a,0,e,a,far,e,a,h,c,a,0,c);
        face(out,matrices,color,p.colorCode(),light,1,0,0,b,0,c,b,h,c,b,far,e,b,0,e);
        face(out,matrices,color,p.colorCode(),light,0,0,-1,a,0,c,a,h,c,b,h,c,b,0,c);
        face(out,matrices,color,p.colorCode(),light,0,0,1,b,0,e,b,far,e,a,far,e,a,0,e);
        if(p.kind().studs)
            for(int ix=0;ix<p.kind().width;ix++) for(int iz=0;iz<p.kind().length;iz++)
                cylinder(out,matrices,ix+.5f,iz+.5f,.3f,h,h+.2f,color,p.colorCode(),light,true);
        matrices.pop();
    }

    private static void renderFragment(VertexConsumer out, MatrixStack mat, BrickPiece p,
                                       BlockPos cell,int light) {
        float yBottom=p.y()/5f-cell.getY();
        float yTop=p.top()/5f-cell.getY();
        float lo=Math.max(0,yBottom), hi=Math.min(1,yTop);
        int rgb=LDrawLibrary.argb(p.colorCode(),p.rgb());
        if (hi>lo) {
            float inset=.009f;
            float x0 = cell.getX() == p.x() ? inset : 0;
            float x1 = cell.getX() == p.x()+p.width()-1 ? 1-inset : 1;
            float z0 = cell.getZ() == p.z() ? inset : 0;
            float z1 = cell.getZ() == p.z()+p.length()-1 ? 1-inset : 1;
            if (yTop<=1) face(out,mat,rgb,p.colorCode(),light,0,1,0,x0,hi,z0,x0,hi,z1,x1,hi,z1,x1,hi,z0);
            if (yBottom>=0) face(out,mat,rgb,p.colorCode(),light,0,-1,0,x0,lo,z1,x0,lo,z0,x1,lo,z0,x1,lo,z1);
            if (cell.getX()==p.x()+p.width()-1) face(out,mat,rgb,p.colorCode(),light,1,0,0,x1,lo,z0,x1,hi,z0,x1,hi,z1,x1,lo,z1);
            if (cell.getX()==p.x()) face(out,mat,rgb,p.colorCode(),light,-1,0,0,x0,lo,z1,x0,hi,z1,x0,hi,z0,x0,lo,z0);
            if (cell.getZ()==p.z()+p.length()-1) face(out,mat,rgb,p.colorCode(),light,0,0,1,x1,lo,z1,x1,hi,z1,x0,hi,z1,x0,lo,z1);
            if (cell.getZ()==p.z()) face(out,mat,rgb,p.colorCode(),light,0,0,-1,x0,lo,z0,x0,hi,z0,x1,hi,z0,x1,lo,z0);
        }
        if (p.kind().studs) {
            float studStart=yTop, studEnd=yTop+.2f;
            float s0=Math.max(0,studStart), s1=Math.min(1,studEnd);
            if (s1>s0) cylinder(out,mat,.5f,.5f,.3f,s0,s1,rgb,p.colorCode(),light,studEnd<=1.0f);
        }
    }

    private static void cylinder(VertexConsumer out,MatrixStack mat,float cx,float cz,float radius,
                                 float lo,float hi,int color,int colorCode,int light,boolean topVisible) {
        int segments=12;
        for(int i=0;i<segments;i++) {
            double a=Math.PI*2*i/segments,b=Math.PI*2*(i+1)/segments;
            float x0=cx+radius*(float)Math.cos(a), z0=cz+radius*(float)Math.sin(a);
            float x1=cx+radius*(float)Math.cos(b), z1=cz+radius*(float)Math.sin(b);
            float nx=(float)Math.cos((a+b)/2), nz=(float)Math.sin((a+b)/2);
            face(out,mat,color,colorCode,light,nx,0,nz, x0,lo,z0,x0,hi,z0,x1,hi,z1,x1,lo,z1);
            if(topVisible) {
                face(out,mat,color,colorCode,light,0,1,0,cx,hi,cz,x0,hi,z0,x1,hi,z1,cx,hi,cz);
            }
        }
    }

    private static void face(VertexConsumer out,MatrixStack mat,int rgb,int colorCode,int light,
                             float nx,float ny,float nz,float... v) {
        // No extra RenderLayer requests: safe with the consecutive buffer passes in V0.6.2.
        long seed=Float.floatToIntBits(v[0])*31L+Float.floatToIntBits(v[1])*17L+
                Float.floatToIntBits(v[2])*13L+Float.floatToIntBits(nx)*7L;
        rgb=LDrawLibrary.surfaceTint(rgb,colorCode,nx,ny,nz,seed);
        for (int i=0;i<4;i++) {
            float u=(i==2||i==3)?1f:0f, t=(i==1||i==2)?1f:0f;
            out.vertex(mat.peek(),v[3*i],v[3*i+1],v[3*i+2])
                    .color(rgb).texture(BrickPbrTexture.u(colorCode,rgb),BrickPbrTexture.v())
                    .overlay(OverlayTexture.DEFAULT_UV).light(light)
                    .normal(mat.peek(),nx,ny,nz);
        }
    }
}
