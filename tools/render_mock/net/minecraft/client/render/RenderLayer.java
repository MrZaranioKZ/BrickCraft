package net.minecraft.client.render;
import net.minecraft.util.Identifier;
public record RenderLayer(String name){
public static RenderLayer getEntitySolid(Identifier id){return new RenderLayer("solid");}
public static RenderLayer getEntityTranslucent(Identifier id){return new RenderLayer("translucent");}}
