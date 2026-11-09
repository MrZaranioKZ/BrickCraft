package fr.brickcraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** The NEXT step is a lightweight wireframe ghost, never a permanent world block. */
public final class BrickGuideTargetOverlay {
    private BrickGuideTargetOverlay(){}
    private static final int[][] EDGES={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},
            {3,7},{4,5},{4,6},{5,7},{6,7}};
    public static void init(){
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(!BrickGuideController.active()||!BrickGuideController.targetOverlay()||
                    ctx.consumers()==null||ctx.matrixStack()==null)return;
            var mc=MinecraftClient.getInstance();if(mc.player==null)return;
            var targets=BrickGuideController.nextTargets(48);
            if(targets.isEmpty())return;
            var stack=ctx.matrixStack();Vec3d cam=ctx.camera().getPos();
            stack.push();stack.translate(-cam.x,-cam.y,-cam.z);
            var lines=ctx.consumers().getBuffer(RenderLayer.getLines());
            for(var t:targets){
                for(int[] e:EDGES)line(lines,stack,t.corners()[e[0]],t.corners()[e[1]],0xFF4CEBD6);
                double x=t.x(),y=t.y(),z=t.z(),s=.16;
                line(lines,stack,new double[]{x-s,y,z},new double[]{x+s,y,z},0xFFFFFF76);
                line(lines,stack,new double[]{x,y-s,z},new double[]{x,y+s,z},0xFFFFFF76);
                line(lines,stack,new double[]{x,y,z-s},new double[]{x,y,z+s},0xFFFFFF76);
                // Vertical indicator makes small targets discoverable from above.
                line(lines,stack,new double[]{x,y+.18,z},new double[]{x,y+.9,z},0xFF4CEBD6);
            }
            stack.pop();
        });
    }
    private static void line(VertexConsumer output,MatrixStack matrices,double[] a,double[] b,int rgba){
        var matrix=matrices.peek().getPositionMatrix();
        output.vertex(matrix,(float)a[0],(float)a[1],(float)a[2]).color(rgba).normal(0,1,0);
        output.vertex(matrix,(float)b[0],(float)b[1],(float)b[2]).color(rgba).normal(0,1,0);
    }
}
