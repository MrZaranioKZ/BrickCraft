package net.minecraft.client.render;
import net.minecraft.client.util.math.MatrixStack;
public class VertexConsumer{
public boolean active=true;public int vertices=0;
public VertexConsumer vertex(MatrixStack.Entry e,float x,float y,float z){if(!active)throw new IllegalStateException("Not building!");vertices++;return this;}
public VertexConsumer color(int c){return this;}public VertexConsumer texture(float a,float b){return this;}
public VertexConsumer overlay(int n){return this;}public VertexConsumer light(int n){return this;}
public VertexConsumer normal(MatrixStack.Entry e,float x,float y,float z){return this;}
}
