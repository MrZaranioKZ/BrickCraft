package fr.brickcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** Persistent 2D GPU mesh built only when the editor camera/model changes.
 * Vertex positions are projected GUI pixels, preserving exact legacy shading/painter ordering. */
final class StudioPreviewGpu implements AutoCloseable {
    record Triangle(float ax,float ay,float bx,float by,float cx,float cy,int rgba){}
    private final List<Triangle> collected=new ArrayList<>();
    private VertexBuffer buffer;
    private int triangleCount;
    void begin(){collected.clear();}
    void face(int[] x,int[] y,int color){
        if(x.length<3)return;
        collected.add(new Triangle(x[0],y[0],x[1],y[1],x[2],y[2],color));
        if(x.length==4)collected.add(new Triangle(x[0],y[0],x[2],y[2],x[3],y[3],color));
    }
    void rectangle(int x1,int y1,int x2,int y2,int color){
        if(x2<=x1||y2<=y1)return;
        face(new int[]{x1,x2,x2,x1},new int[]{y1,y1,y2,y2},color);
    }
    int triangles(){return triangleCount;}
    void upload(){
        release();triangleCount=collected.size();
        if(collected.isEmpty())return;
        try(BufferAllocator allocator=new BufferAllocator(Math.max(4096,collected.size()*3*16))){
            BufferBuilder builder=new BufferBuilder(allocator,VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR);
            for(Triangle t:collected){
                builder.vertex(t.ax(),t.ay(),0).color(t.rgba());
                builder.vertex(t.bx(),t.by(),0).color(t.rgba());
                builder.vertex(t.cx(),t.cy(),0).color(t.rgba());
            }
            try(BuiltBuffer built=builder.end()){
                VertexBuffer gpu=new VertexBuffer(VertexBuffer.Usage.STATIC);
                try{gpu.bind();gpu.upload(built);buffer=gpu;}
                catch(RuntimeException e){gpu.close();throw e;}
                finally{VertexBuffer.unbind();}
            }
        }finally{collected.clear();}
    }
    void draw(DrawContext ctx,int x1,int y1,int x2,int y2){
        if(buffer==null)return;
        ctx.draw(); // Flush queued GUI geometry before a direct VertexBuffer draw.
        ctx.enableScissor(x1,y1,x2,y2);
        RenderSystem.disableDepthTest();RenderSystem.disableCull();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        try{
            // The world projection is NOT guaranteed to be the GUI orthographic matrix.
            // The VBO already contains projected GUI pixel coordinates, so use an
            // explicit top-left-origin projection in scaled GUI units (not framebuffer px).
            float guiW=ctx.getScaledWindowWidth(),guiH=ctx.getScaledWindowHeight();
            Matrix4f model=new Matrix4f().identity();
            Matrix4f projection=new Matrix4f().setOrtho(0f,guiW,guiH,0f,-1000f,1000f);
            buffer.bind();buffer.draw(model,projection,GameRenderer.getPositionColorProgram());
        }finally{
            VertexBuffer.unbind();RenderSystem.disableBlend();RenderSystem.enableDepthTest();RenderSystem.enableCull();
            ctx.disableScissor();
        }
    }
    void release(){if(buffer!=null){buffer.close();buffer=null;}}
    @Override public void close(){release();collected.clear();}
}
