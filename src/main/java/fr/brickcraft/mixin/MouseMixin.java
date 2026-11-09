package fr.brickcraft.mixin;

import fr.brickcraft.client.PreciseWorldEditor;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In world-edit mode the wheel moves/rotates LEGO instead of changing the hotbar. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow private double x;
    @Shadow private double y;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;
    /** Only consume an actual gizmo click; regular left clicks still select LEGO parts. */
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void brickcraft$worldGizmoClick(long window,int button,int action,int mods,CallbackInfo ci){
        if(PreciseWorldEditor.mouseButton(button,action))ci.cancel();
    }
    @Inject(method="onCursorPos",at=@At("HEAD"),cancellable=true)
    private void brickcraft$worldGizmoDrag(long window,double x,double y,CallbackInfo ci){
        if(PreciseWorldEditor.mouseMove(x,y)){
            // Keep Minecraft's cached pointer position in sync; otherwise mouse look
            // would jump when releasing the handle after a long drag.
            this.x=x;this.y=y;this.cursorDeltaX=0;this.cursorDeltaY=0;
            ci.cancel();
        }
    }
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void brickcraft$legoWheel(long window,double horizontal,double vertical,CallbackInfo ci){
        if(PreciseWorldEditor.scroll(vertical))ci.cancel();
    }
}
