package net.minecraft.client.render;
public class VertexConsumerProvider{
private RenderLayer last=null;private VertexConsumer current=null;public int swaps=0,total=0;
public VertexConsumer getBuffer(RenderLayer layer){
if(!layer.equals(last)){
 if(current!=null){current.active=false;total+=current.vertices;}
 current=new VertexConsumer();last=layer;swaps++;
}
return current;
}
public int flushed(){return total+(current==null?0:current.vertices);}
}
