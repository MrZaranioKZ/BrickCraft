package fr.brickcraft.client;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.BrickPartStacks;
import fr.brickcraft.world.BrickPartItem;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.net.BrickActionPayload;
import fr.brickcraft.net.BrickEditPayload;
import fr.brickcraft.net.StudioExportStatusPayload;
import fr.brickcraft.net.PreciseManageStatusPayload;
import fr.brickcraft.world.BrickCellEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class BrickCraftClient implements ClientModInitializer {
    private static KeyBinding studioKey,nextKey,turnKey,colorKey,quickKey,stepKey,pitchKey,rollKey,resetKey,inspectKey,gpuHideKey,gpuInfoKey,gpuLegacyKey,gpuCullingKey,gpuViewKey,worldModeKey,qualityKey,yAxisKey,connectorsKey,guideKey,guidePreviousKey,guideNextKey,playerScaleKey;
    private static boolean inspectVisible=false;
    private static boolean prevCopy=false,prevPaste=false,prevUndo=false,prevRedo=false;
    private static Item previousHand=null;
    private static int previousSlot=-1;
    private static BrickPiece clipboard=null;

    private static BrickKind activeKind(MinecraftClient client){
        if(BrickSelection.sampled)return BrickSelection.kind;
        if(client.player!=null && client.player.getMainHandStack().getItem() instanceof BrickPartItem item)
            return item.kind();
        return BrickSelection.kind;
    }

    /** Called before vanilla Pick Block, so Minecraft won't replace our sampled color and part. */
    public static boolean pickBrick(MinecraftClient client){
        if(client.player==null || client.world==null ||
                !(client.crosshairTarget instanceof BlockHitResult hit) ||
                !(client.world.getBlockEntity(hit.getBlockPos()) instanceof BrickCellEntity cell))return false;
        BrickPiece piece=cell.pickAt(hit.getPos().y,hit.getSide()==net.minecraft.util.math.Direction.UP);
        if(piece==null)return false;
        BrickSelection.sample(piece); clipboard=piece;
        if(client.interactionManager!=null && client.interactionManager.hasCreativeInventory()){
            // Give the matching item in the active hotbar slot. Keep the sampled RGB in selection.
            // Missing-library parts may still be present in old saves. Never crash Pick Block.
            ItemStack stack=piece.kind()==BrickKind.LDRAW_PART
                    ?(LDrawLibrary.contains(piece.partId())
                        ?BrickPartStacks.ldraw(piece.partId(),1)
                        :new ItemStack(BrickCraft.TOOL))
                    :new ItemStack(BrickCraft.PART_ITEMS.get(piece.kind()));
            int slot=client.player.getInventory().selectedSlot;
            client.player.getInventory().setStack(slot,stack);
            client.interactionManager.clickCreativeStack(stack,36+slot);
            previousHand=stack.getItem();previousSlot=slot;
        }
        client.player.sendMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.notice.pick",BrickI18n.tr(piece.name()),
                BrickI18n.tr(BrickSelection.colorName()),piece.angle())),true);
        return true;
    }

    private static void pasteAtCrosshair(MinecraftClient client){
        if(client.player==null || client.world==null || clipboard==null ||
                !(client.crosshairTarget instanceof BlockHitResult hit))return;
        Item held=client.player.getMainHandStack().getItem();
        if(held!=BrickCraft.TOOL && !(held instanceof BrickPartItem) && held!=BrickCraft.LDRAW_ITEM)return;
        BrickSelection.sample(clipboard);
        ClientPlayNetworking.send(new BrickActionPayload(hit.getBlockPos(),hit.getSide(),
                (float)(hit.getPos().x-hit.getBlockPos().getX()),
                (float)(hit.getPos().y-hit.getBlockPos().getY()),
                (float)(hit.getPos().z-hit.getBlockPos().getZ()),false,false,
                BrickSelection.oriented(clipboard.kind(),0,0,0)));
    }

    private static boolean keyDown(MinecraftClient client,int key){
        return InputUtil.isKeyPressed(client.getWindow().getHandle(),key);
    }


    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StudioExportStatusPayload.ID,
                (packet,context)->context.client().execute(()->BrickStudioScreen.receiveExportStatus(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PreciseManageStatusPayload.ID,
                (packet,context)->context.client().execute(()->{
                    if(packet.event()==PreciseManageStatusPayload.REMOVED && !packet.projects().isEmpty())
                        PreciseDisplayRenderer.releaseProjection(packet.projects().get(0).anchor());
                    if(context.client().currentScreen instanceof PreciseWorldProjectsScreen screen)
                        screen.receive(packet);
                }));

        BlockEntityRendererRegistry.register(BrickCraft.CELL_ENTITY, BrickCellRenderer::new);
        BlockEntityRendererRegistry.register(BrickCraft.PRECISE_ENTITY, PreciseDisplayRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler,client)->{
                    PreciseDisplayRenderer.clearGpuOnDisconnect();PreciseWorldEditor.reset();BrickGuideController.reset();
                });
        PreciseWorldEditor.initOverlay();
        worldModeKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.world_edit",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F4,"category.brickcraft"));
        guideKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.guide",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_J,"category.brickcraft"));
        guidePreviousKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.guide_previous",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_PAGE_UP,"category.brickcraft"));
        guideNextKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.guide_next",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_PAGE_DOWN,"category.brickcraft"));
        connectorsKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.world_connectors",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_K,"category.brickcraft"));
        playerScaleKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.player_scale",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_U,"category.brickcraft"));
        qualityKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.mesh_quality",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_P,"category.brickcraft"));
        yAxisKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.world_y_axis",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_Y,"category.brickcraft"));
        gpuHideKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.gpu_hide",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.brickcraft"));
        gpuInfoKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.gpu_info",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F9,"category.brickcraft"));
        gpuLegacyKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.brickcraft.gpu_legacy",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F6,"category.brickcraft"));
        // Experimental diagnostics are opt-in and should not clutter the normal Controls menu.
        // Set experimental_gpu_diagnostics=true in config/brickcraft/precise-render.properties.
        if(BrickGpuQualityConfig.experimentalDiagnostics(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir())){
            gpuCullingKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.brickcraft.gpu_culling",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F7,"category.brickcraft"));
            gpuViewKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.brickcraft.gpu_view",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F10,"category.brickcraft"));
        }
        studioKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.studio",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_B,"category.brickcraft"));
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.next",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_V,"category.brickcraft"));
        turnKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.turn",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_R,"category.brickcraft"));

        quickKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.quick",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_N,"category.brickcraft"));
        stepKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.step",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"category.brickcraft"));
        pitchKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.pitch",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_X,"category.brickcraft"));
        rollKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.roll",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_Z,"category.brickcraft"));
        resetKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.reset",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_H,"category.brickcraft"));
        colorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.color",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_C,"category.brickcraft"));

        inspectKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.brickcraft.inspect",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_I,"category.brickcraft"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PreciseWorldEditor.tick(client);
            BrickGuideController.tick(client);
            if(client.player!=null){
                Item held=client.player.getMainHandStack().getItem();
                int slot=client.player.getInventory().selectedSlot;
                if(previousHand!=null && (previousHand!=held || (previousSlot>=0&&previousSlot!=slot)))
                    BrickSelection.sampled=false;
                previousHand=held;previousSlot=slot;
                // A /brickcraft give LDraw item carries its reference in custom data.
                // Selecting it updates the existing world builder without opening a menu.
                String bound=BrickPartStacks.boundLdrawId(client.player.getMainHandStack());
                if(bound!=null && (!bound.equalsIgnoreCase(BrickSelection.partId) ||
                        BrickSelection.kind!=BrickKind.LDRAW_PART)) {
                    BrickSelection.kind=BrickKind.LDRAW_PART;
                    BrickSelection.partId=bound;
                    BrickSelection.sampled=true;
                }
            }
            boolean ctrl=keyDown(client,GLFW.GLFW_KEY_LEFT_CONTROL)||keyDown(client,GLFW.GLFW_KEY_RIGHT_CONTROL);
            boolean copy=ctrl&&keyDown(client,GLFW.GLFW_KEY_C);
            boolean paste=ctrl&&keyDown(client,GLFW.GLFW_KEY_V);
            boolean shift=keyDown(client,GLFW.GLFW_KEY_LEFT_SHIFT)||keyDown(client,GLFW.GLFW_KEY_RIGHT_SHIFT);
            boolean undo=ctrl&&keyDown(client,GLFW.GLFW_KEY_Z)&&!shift;
            boolean redo=ctrl&&(keyDown(client,GLFW.GLFW_KEY_Y)||
                   (keyDown(client,GLFW.GLFW_KEY_Z)&&shift));
            if(client.currentScreen==null && client.player!=null){
                if(copy&&!prevCopy)pickBrick(client);
                if(paste&&!prevPaste)pasteAtCrosshair(client);
                if(undo&&!prevUndo && !PreciseWorldEditor.undo())ClientPlayNetworking.send(new BrickEditPayload(false));
                if(redo&&!prevRedo && !PreciseWorldEditor.enabled())ClientPlayNetworking.send(new BrickEditPayload(true));
            }
            prevCopy=copy;prevPaste=paste;prevUndo=undo;prevRedo=redo;
            while(guideKey.wasPressed())if(client.currentScreen==null && client.player!=null)
                client.setScreen(new BrickGuideScreen());
            while(guidePreviousKey.wasPressed())if(client.currentScreen==null)
                BrickGuideController.advance(-1);
            while(guideNextKey.wasPressed())if(client.currentScreen==null)
                BrickGuideController.advance(1);
            while(worldModeKey.wasPressed())if(client.currentScreen==null&&client.player!=null)
                PreciseWorldEditor.toggle(client);
            while(playerScaleKey.wasPressed())if(client.currentScreen==null&&client.player!=null)
                client.setScreen(new BrickPlayerScaleScreen(null));
            while(qualityKey.wasPressed())if(client.currentScreen==null&&client.player!=null)
                client.setScreen(new BrickGpuQualityScreen(null));
            while(connectorsKey.wasPressed())if(client.currentScreen==null)
                PreciseWorldEditor.toggleConnectors();
            while(yAxisKey.wasPressed())if(client.currentScreen==null && !ctrl)
                PreciseWorldEditor.chooseAxis(1);
            while(gpuHideKey.wasPressed())if(client.currentScreen==null){
                PreciseDisplayRenderer.toggleVisibility();
                if(client.player!=null)client.player.sendMessage(Text.literal(BrickI18n.tr(PreciseDisplayRenderer.projectionVisible()?"BrickCraft : projection VISIBLE":
                        "BrickCraft : projection MASQUEE (test FPS)")),true);
            }
            while(gpuInfoKey.wasPressed())if(client.currentScreen==null)PreciseDisplayRenderer.toggleStatistics();
            while(gpuLegacyKey.wasPressed())if(client.currentScreen==null){
                boolean legacy=PreciseDisplayRenderer.toggleLegacyMode();
                if(client.player!=null)client.player.sendMessage(Text.literal(BrickI18n.tr(legacy?"BrickCraft : LEGACY, lent mais reference":"BrickCraft : GPU, lots persistants")),true);
            }
            while(gpuCullingKey!=null && gpuCullingKey.wasPressed())if(client.currentScreen==null && client.player!=null)
                client.player.sendMessage(Text.literal(BrickI18n.tr("BrickCraft : "+PreciseDisplayRenderer.toggleGpuCulling())),true);
            while(gpuViewKey!=null && gpuViewKey.wasPressed())if(client.currentScreen==null && client.player!=null)
                client.player.sendMessage(Text.literal(BrickI18n.tr("BrickCraft : "+PreciseDisplayRenderer.toggleGpuCameraMatrix())),true);
            while(inspectKey.wasPressed()) if(client.currentScreen==null && client.player!=null){
                inspectVisible=!inspectVisible;
                client.player.sendMessage(Text.literal(BrickI18n.tr(inspectVisible?"BrickCraft : inspecteur active (I pour masquer)":
                    "BrickCraft : inspecteur masque")),true);
            }
            while (studioKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null){
                    if(PreciseWorldEditor.snapNow())continue;
                    client.setScreen(new BrickStudioScreen(resolveTarget(client)));
                }
            }
            while (quickKey.wasPressed()) if (client.currentScreen==null && client.player!=null){
                if(PreciseWorldEditor.toggleSnap())continue;
                client.setScreen(new BrickQuickScreen());
            }
            while (stepKey.wasPressed()) if (client.currentScreen==null) {
                if(PreciseWorldEditor.modeKey(false))continue;
                BrickSelection.cycleStep();
                if(client.player!=null) client.player.sendMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.notice.rotation_step",BrickSelection.step())),true);
            }
            while (pitchKey.wasPressed()) if(client.currentScreen==null){
                if(PreciseWorldEditor.chooseAxis(0))continue;
                BrickSelection.rotatePitch(1);
                if(client.player!=null)client.player.sendMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.notice.axis_x",BrickSelection.pitch)),true);
            }
            while (rollKey.wasPressed()) if(client.currentScreen==null && !ctrl){
                if(PreciseWorldEditor.chooseAxis(2))continue;
                BrickSelection.rotateRoll(1);
                if(client.player!=null)client.player.sendMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.notice.axis_z",BrickSelection.roll)),true);
            }
            while (resetKey.wasPressed()) if(client.currentScreen==null){
                if(PreciseWorldEditor.resetRotation())continue;
                BrickSelection.resetRotation();
                if(client.player!=null)client.player.sendMessage(Text.literal(BrickI18n.tr("BrickCraft : rotation reinitialisee")),true);
            }
            while (nextKey.wasPressed()) if (client.currentScreen==null && !ctrl) {
                BrickSelection.nextKind();
                if (client.player!=null) client.player.sendMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.notice.selection",BrickI18n.tr(BrickSelection.selectedName()))),true);
            }
            while (colorKey.wasPressed()) if (client.currentScreen==null && !ctrl) {
                BrickSelection.nextColor();
                if (client.player!=null) client.player.sendMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.notice.color",BrickI18n.tr(BrickSelection.colorName()))),true);
            }
            while (turnKey.wasPressed()) if (client.currentScreen==null) {
                if(PreciseWorldEditor.modeKey(true))continue;
                BrickSelection.rotate(client.player!=null && client.player.isSneaking() ? -1 : 1);
                if (client.player!=null) client.player.sendMessage(Text.literal(BrickI18n.fmt("brickcraft.v241.notice.rotation",BrickSelection.angle,BrickSelection.step())),true);
            }
        });
        HudRenderCallback.EVENT.register((draw,tick) -> {
            MinecraftClient client=MinecraftClient.getInstance();
            if(client.player==null || client.currentScreen!=null || client.options.hudHidden) return;
            boolean active=client.player.getMainHandStack().isOf(BrickCraft.TOOL)
                    || client.player.getMainHandStack().getItem() instanceof BrickPartItem
                    || client.player.getMainHandStack().isOf(BrickCraft.LDRAW_ITEM);
            if(inspectVisible && !PreciseWorldEditor.enabled()) renderInspector(client,draw);
            BrickGuideController.hud(client,draw);
            PreciseWorldEditor.renderHud(draw,client);
            String gpuStats=PreciseDisplayRenderer.statistics();
            if(gpuStats!=null){
                int px=5,py=5;
                String[] lines=gpuStats.split("\n");
                int width=0;
                for(int i=0;i<lines.length;i++){
                    lines[i]=client.textRenderer.trimToWidth(BrickI18n.tr(lines[i]),Math.max(20,draw.getScaledWindowWidth()-20));
                    width=Math.max(width,client.textRenderer.getWidth(lines[i]));
                }
                draw.fill(px-2,py-2,Math.min(draw.getScaledWindowWidth()-5,
                        px+width+8),py+lines.length*11+3,0xCB081C2D);
                for(int line=0;line<lines.length;line++)
                    draw.drawTextWithShadow(client.textRenderer,lines[line],px,py+line*11,0xFF7ED9E0);
            }
            if(!active || PreciseWorldEditor.enabled())return;
            BrickKind kind=activeKind(client);
            int x=6,y=draw.getScaledWindowHeight()-76;
            int w=Math.max(170,client.textRenderer.getWidth(BrickSelection.selectedName())+22);
            draw.fill(x-3,y-3,x+w+3,y+48,0xB5102230);
            draw.fill(x,y+3,x+8,y+15,0xFF000000|BrickSelection.rgb);
            draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr(BrickSelection.selectedName()),x+13,y+4,0xFFEAF6FF);
            draw.drawTextWithShadow(client.textRenderer,BrickI18n.fmt("brickcraft.v241.hud.color",BrickI18n.tr(BrickSelection.colorName()),
                            BrickSelection.angle,BrickSelection.pitch,BrickSelection.roll),
                    x+3,y+20,0xFFF2DDA1);
            draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr("N: catalogue | I: inspecteur | G: pas | H: reset"),x+3,y+34,0xFF99C0D1);
        });
        BrickGuideTargetOverlay.init();
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if(world.isClient && PreciseWorldEditor.enabled())return ActionResult.SUCCESS;
            if (hand!=Hand.MAIN_HAND || !(player.getStackInHand(hand).isOf(BrickCraft.TOOL) ||
                    player.getStackInHand(hand).getItem() instanceof BrickPartItem ||
                    player.getStackInHand(hand).isOf(BrickCraft.LDRAW_ITEM)))
                return ActionResult.PASS;
            if (world.isClient) {
                BrickKind kind=activeKind(MinecraftClient.getInstance());
                String bound=BrickPartStacks.boundLdrawId(player.getStackInHand(hand));
                // The wand follows the palette. A /brickcraft give item uses its bound ID.
                if(player.getStackInHand(hand).isOf(BrickCraft.LDRAW_ITEM) && bound==null)
                    return ActionResult.PASS;
                if(bound!=null){BrickSelection.kind=BrickKind.LDRAW_PART;BrickSelection.partId=bound;
                    BrickSelection.sampled=false;kind=BrickKind.LDRAW_PART;}
                BrickPiece selection=BrickSelection.oriented(kind,0,0,0);
                ClientPlayNetworking.send(new BrickActionPayload(
                    hit.getBlockPos(),hit.getSide(),
                    (float)(hit.getPos().x-hit.getBlockPos().getX()),
                    (float)(hit.getPos().y-hit.getBlockPos().getY()),
                    (float)(hit.getPos().z-hit.getBlockPos().getZ()),
                    player.isSneaking(),false,selection));
            }
            return ActionResult.SUCCESS;
        });
    }

    /** Inspector is completely absent until the player presses I. */
    private static void renderInspector(MinecraftClient client,net.minecraft.client.gui.DrawContext draw){
        int right=draw.getScaledWindowWidth()-8;
        int top=9;
        int panelWidth=Math.min(245,Math.max(155,draw.getScaledWindowWidth()/3));
        int left=right-panelWidth;
        BrickPiece part=null;
        if(client.crosshairTarget instanceof BlockHitResult hit && client.world!=null &&
                client.world.getBlockEntity(hit.getBlockPos()) instanceof BrickCellEntity cell){
            part=cell.pickAt(hit.getPos().y,hit.getSide()==net.minecraft.util.math.Direction.UP);
        }
        int rows=part==null?3:8;
        draw.fill(left-5,top-5,right+4,top+rows*13+13,0xDA0C1825);
        draw.fill(left-5,top-5,right+4,top-3,0xFF58D1D7);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr("BRICKCRAFT  /  INSPECTEUR"),left,top,0xFFBDF7FF);
        if(part==null){
            draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr("Vise une piece BrickCraft"),left,top+16,0xFFE3E9F2);
            draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr("I : masquer"),left,top+29,0xFF93B5C9);
            return;
        }
        int i=1;
        draw.drawTextWithShadow(client.textRenderer,trim(client,BrickI18n.tr(part.name()),panelWidth),left,top+13*i++,0xFFFFFFFF);
        LDrawLibrary.Colour colour=LDrawLibrary.color(part.colorCode());
        String name=colour==null?"Couleur libre (RGB)":colour.displayName();
        draw.drawTextWithShadow(client.textRenderer,trim(client,BrickI18n.tr(name),panelWidth),left,top+13*i++,0xFFF6D49C);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.fmt("brickcraft.v241.inspector.ldraw",colour==null?BrickI18n.tr("aucun"):colour.code(),
                String.format("%06X",part.rgb())),left,top+13*i++,0xFFCBDCE6);
        String finish=colour==null?"OPAQUE":colour.finish();
        int alpha=colour==null?255:colour.alpha();
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.fmt("brickcraft.v241.inspector.finish",BrickI18n.tr(finish)),left,top+13*i++,0xFFCBDCE6);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.fmt("brickcraft.v241.inspector.opacity",Math.round(alpha/255f*100)),left,top+13*i++,0xFFCBDCE6);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr("Y="+part.angle()+" X="+part.pitch()+" Z="+part.roll()),left,top+13*i++,0xFFCBDCE6);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.fmt("brickcraft.v241.inspector.size",part.width(),part.length(),
                String.format(java.util.Locale.ROOT,"%.1f",part.height()/5f)),left,top+13*i++,0xFFCBDCE6);
        draw.drawTextWithShadow(client.textRenderer,BrickI18n.tr("I : masquer"),left,top+13*i,0xFF93B5C9);
    }
    private static String trim(MinecraftClient client,String value,int limit){
        if(client.textRenderer.getWidth(value)<=limit)return value;
        while(value.length()>1 && client.textRenderer.getWidth(value+"...")>limit)
            value=value.substring(0,value.length()-1);
        return value+"...";
    }

    public static BlockPos resolveTarget(MinecraftClient client) {
        if (client.world == null || !(client.crosshairTarget instanceof BlockHitResult hit)) return null;
        // Studio origin is placed NEXT TO a targeted normal block, vanilla-style.
        // A new model can also start from the top of an existing LEGO cell.
        if (client.world.getBlockState(hit.getBlockPos()).isOf(BrickCraft.CELL))
            return hit.getSide().getAxis().isVertical()
                    ? hit.getBlockPos().up() : hit.getBlockPos().offset(hit.getSide());
        return hit.getBlockPos().offset(hit.getSide());
    }
}
