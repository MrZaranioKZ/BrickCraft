package fr.brickcraft.client;

import fr.brickcraft.core.BrickPbrProfiles;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.minecraft.util.Identifier;

/** Shared Minecraft/Iris material texture: the _n/_s maps are loaded automatically
 * by Iris when supported by the active shaderpack, never through an Iris dependency.
 */
final class BrickPbrTexture {
    private BrickPbrTexture() {}
    static final Identifier ATLAS=Identifier.of("brickcraft","textures/entity/lego_materials.png");
    static int slot(int colorCode,int argb){
        var meta=LDrawLibrary.color(colorCode);
        return BrickPbrProfiles.slot(meta==null?"SOLID":meta.finish(),(argb>>>24)&255);
    }
    static float u(int colorCode,int argb){return BrickPbrProfiles.u(slot(colorCode,argb));}
    static float v(){return BrickPbrProfiles.v();}
}
