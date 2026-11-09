package fr.brickcraft.mixin;

import fr.brickcraft.client.BrickCraftClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import fr.brickcraft.client.PreciseWorldEditor;

/** Intercept vanilla middle-click ONLY on BrickCraft cells; leave normal blocks unchanged. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void brickcraft$selectVirtualPart(CallbackInfoReturnable<Boolean> ci){
        if(PreciseWorldEditor.attack((MinecraftClient)(Object)this))ci.setReturnValue(false);
    }
    @Inject(method="doItemPick",at=@At("HEAD"),cancellable=true)
    private void brickcraft$pickBrick(CallbackInfo ci){
        if(BrickCraftClient.pickBrick((MinecraftClient)(Object)this))ci.cancel();
    }
}
