package fr.brickcraft;

import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.core.BrickCapacityConfig;
import fr.brickcraft.core.ConnectorSnapper;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.fabricmc.loader.api.FabricLoader;
import fr.brickcraft.world.BrickPartItem;
import java.util.EnumMap;
import java.util.Map;
import fr.brickcraft.net.BrickActionPayload;
import fr.brickcraft.net.BrickEditPayload;
import fr.brickcraft.net.StudioExportPayload;
import fr.brickcraft.net.StudioExportStatusPayload;
import fr.brickcraft.world.StudioExportManager;
import fr.brickcraft.world.PreciseDisplayBlock;
import fr.brickcraft.world.PreciseDisplayEntity;
import fr.brickcraft.world.PreciseCollisionBlock;
import fr.brickcraft.world.PreciseCollisionEntity;
import fr.brickcraft.net.PlayerScalePayload;
import fr.brickcraft.world.PlayerScaleManager;
import fr.brickcraft.world.PrecisePhysicsManager;
import fr.brickcraft.net.PrecisePhysicsPayload;
import fr.brickcraft.world.PreciseExportManager;
import fr.brickcraft.net.PreciseExportPayload;
import fr.brickcraft.net.PreciseManagePayload;
import fr.brickcraft.net.PreciseManageStatusPayload;
import fr.brickcraft.net.PreciseWorldEditPayload;
import fr.brickcraft.world.PreciseWorldEditManager;
import fr.brickcraft.world.PreciseManageManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.Deque;
import fr.brickcraft.world.BrickCellBlock;
import fr.brickcraft.world.BrickCellEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import java.util.List;

public final class BrickCraft implements ModInitializer {
    public static final BrickCellBlock CELL = Registry.register(Registries.BLOCK,
            Identifier.of("brickcraft", "cell"),
            new BrickCellBlock(AbstractBlock.Settings.create().nonOpaque().dynamicBounds().strength(.3f)));
    public static final PreciseDisplayBlock PRECISE_DISPLAY=Registry.register(Registries.BLOCK,
            Identifier.of("brickcraft","precise_display"),
            new PreciseDisplayBlock(AbstractBlock.Settings.create().nonOpaque().dynamicBounds().strength(1.0f)));
    public static final PreciseCollisionBlock PRECISE_COLLISION=Registry.register(Registries.BLOCK,
            Identifier.of("brickcraft","precise_collision"),
            new PreciseCollisionBlock(AbstractBlock.Settings.create().nonOpaque().dynamicBounds().strength(-1.0f).dropsNothing()));
    public static final BlockEntityType<PreciseCollisionEntity> PRECISE_COLLISION_ENTITY=Registry.register(
            Registries.BLOCK_ENTITY_TYPE,Identifier.of("brickcraft","precise_collision"),
            FabricBlockEntityTypeBuilder.create(PreciseCollisionEntity::new,PRECISE_COLLISION).build());
    public static final BlockEntityType<PreciseDisplayEntity> PRECISE_ENTITY=Registry.register(
            Registries.BLOCK_ENTITY_TYPE,Identifier.of("brickcraft","precise_display"),
            FabricBlockEntityTypeBuilder.create(PreciseDisplayEntity::new,PRECISE_DISPLAY).build());
    public static final Item TOOL = Registry.register(Registries.ITEM,
            Identifier.of("brickcraft", "builder_wand"), new Item(new Item.Settings().maxCount(1)));
    public static final Item GUIDE_BOOK = Registry.register(Registries.ITEM,
            Identifier.of("brickcraft", "guide_book"), new BrickGuideBookItem(new Item.Settings().maxCount(1)));
    public static final Item LDRAW_ITEM = Registry.register(Registries.ITEM,
            Identifier.of("brickcraft","ldraw_part"),new Item(new Item.Settings().maxCount(64)));
    public static final Map<BrickKind, BrickPartItem> PART_ITEMS=new EnumMap<>(BrickKind.class);
    static {
        for (BrickKind kind : BrickKind.values()) {
            if(kind==BrickKind.LDRAW_PART)continue;
            BrickPartItem item = Registry.register(Registries.ITEM,
                    Identifier.of("brickcraft",kind.name().toLowerCase(java.util.Locale.ROOT)),
                    new BrickPartItem(kind,new Item.Settings().maxCount(64)));
            PART_ITEMS.put(kind,item);
        }
    }
    public static final BlockEntityType<BrickCellEntity> CELL_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Identifier.of("brickcraft", "cell"),
            FabricBlockEntityTypeBuilder.create(BrickCellEntity::new, CELL).build());

    private record Edit(String worldId,BrickPiece piece,boolean placement){}
    private static final class History {
        final Deque<Edit> undo=new ArrayDeque<>(), redo=new ArrayDeque<>();
    }
    private static final Map<UUID,History> HISTORIES=new HashMap<>();
    private static String worldId(ServerWorld world){return world.getRegistryKey().getValue().toString();}
    private static void remember(ServerWorld world,ServerPlayerEntity player,BrickPiece piece,boolean placement){
        History history=HISTORIES.computeIfAbsent(player.getUuid(),id->new History());
        history.undo.addFirst(new Edit(worldId(world),piece,placement));
        while(history.undo.size()>64)history.undo.removeLast();
        history.redo.clear();
    }
    private static void history(ServerPlayerEntity player,boolean redo){
        History h=HISTORIES.get(player.getUuid());
        if(h==null)return;
        Deque<Edit> from=redo?h.redo:h.undo, to=redo?h.undo:h.redo;
        if(from.isEmpty()){player.sendMessage(Text.translatable("message.brickcraft.undo.empty"),true);return;}
        Edit e=from.peekFirst();ServerWorld world=player.getServerWorld();
        if(!e.worldId().equals(worldId(world))){
            player.sendMessage(Text.translatable("message.brickcraft.undo.dimension"),true);
            return;
        }
        // Undo a placement by removal, or restore a deletion; redo does the opposite.
        boolean place=redo?e.placement():!e.placement();
        boolean success=place?place(world,player,e.piece()):remove(world,player,e.piece());
        if(success){from.removeFirst();to.addFirst(e);}
        else player.sendMessage(Text.translatable("message.brickcraft.undo.fail"),true);
    }

    @Override public void onInitialize() {
        try { BrickDirectories.ensure(FabricLoader.getInstance().getConfigDir()); }
        catch(java.io.IOException ex) {
            System.err.println("[BrickCraft] Unable to create config/import directories: " + ex.getMessage());
        }
        BrickCapacityConfig.init(FabricLoader.getInstance().getConfigDir());
        LDrawLibrary.init(FabricLoader.getInstance().getConfigDir());
        BrickCreativeCatalog.register();
        BrickCraftCommands.register();
        PayloadTypeRegistry.playC2S().register(BrickActionPayload.ID, BrickActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BrickEditPayload.ID, BrickEditPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StudioExportPayload.ID, StudioExportPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PreciseExportPayload.ID, PreciseExportPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PreciseManagePayload.ID, PreciseManagePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PreciseWorldEditPayload.ID, PreciseWorldEditPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PrecisePhysicsPayload.ID, PrecisePhysicsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlayerScalePayload.ID,PlayerScalePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StudioExportStatusPayload.ID, StudioExportStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PreciseManageStatusPayload.ID, PreciseManageStatusPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StudioExportPayload.ID,
                (packet,context)->StudioExportManager.receive(context.player(),packet));
        ServerTickEvents.END_SERVER_TICK.register(StudioExportManager::tick);
        ServerPlayNetworking.registerGlobalReceiver(PreciseExportPayload.ID,
                (packet,context)->PreciseExportManager.receive(context.player(),packet));
        ServerTickEvents.END_SERVER_TICK.register(PreciseExportManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(PrecisePhysicsManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(PlayerScaleManager::tick);
        ServerPlayerEvents.AFTER_RESPAWN.register(PlayerScaleManager::respawn);
        ServerPlayNetworking.registerGlobalReceiver(PrecisePhysicsPayload.ID,
                (packet,context)->PrecisePhysicsManager.receive(context.player(),packet));
        ServerPlayNetworking.registerGlobalReceiver(PlayerScalePayload.ID,
                (packet,context)->PlayerScaleManager.receive(context.player(),packet));
        ServerPlayNetworking.registerGlobalReceiver(PreciseManagePayload.ID,
                (packet,context)->PreciseManageManager.receive(context.player(),packet));
        ServerPlayNetworking.registerGlobalReceiver(PreciseWorldEditPayload.ID,
                (packet,context)->PreciseWorldEditManager.receive(context.player(),packet));
        ServerPlayNetworking.registerGlobalReceiver(BrickEditPayload.ID,(packet,context)->{
            ServerPlayerEntity player=context.player();
            if(!player.isSpectator())history(player,packet.redo());
        });
        // Intercept left click before Minecraft destroys the entire CELL containing stacked plates.
        AttackBlockCallback.EVENT.register((player,world,hand,pos,side)->{
            if(hand!=Hand.MAIN_HAND || player.isSpectator() || !world.getBlockState(pos).isOf(CELL))
                return net.minecraft.util.ActionResult.PASS;
            if(world instanceof ServerWorld server && player instanceof ServerPlayerEntity sp &&
                    server.getBlockEntity(pos) instanceof BrickCellEntity cell) {
                BlockHitResult actual=sp.raycast(6.0,1.0f,false) instanceof BlockHitResult hit?hit:null;
                if(server.canPlayerModifyAt(sp,pos)) {
                    BrickPiece chosen=(actual!=null && actual.getBlockPos().equals(pos))
                            ?cell.pickAt(actual.getPos().y,actual.getSide()==Direction.UP)
                            :cell.topmost(); // fallback if another mod changes the server-side raycast
                    if(chosen!=null && remove(server,sp,chosen)) remember(server,sp,chosen,false);
                }
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(BrickActionPayload.ID, (packet, context) -> {
            ServerPlayerEntity player = context.player();
            if (player.isSpectator() ||
                    !(player.getMainHandStack().isOf(TOOL) ||
                      player.getMainHandStack().getItem() instanceof BrickPartItem ||
                      player.getMainHandStack().isOf(LDRAW_ITEM))) return;
            // A customised /brickcraft give item cannot place an unrelated part;
            // the normal generic catalogue item retains the old free-selection behaviour.
            String bound=BrickPartStacks.boundLdrawId(player.getMainHandStack());
            if(!packet.remove() && player.getMainHandStack().isOf(LDRAW_ITEM) && bound==null)return;
            if(!packet.remove() && bound!=null && (packet.piece()==null ||
                    packet.piece().kind()!=BrickKind.LDRAW_PART ||
                    !bound.equalsIgnoreCase(packet.piece().partId())))return;
            handle(player.getServerWorld(),player,packet);
        });
    }

    private static void handle(ServerWorld world, ServerPlayerEntity player, BrickActionPayload packet) {
        BlockPos hit = packet.hit();
        if (!world.isInBuildLimit(hit) || !world.isChunkLoaded(hit)
                || player.squaredDistanceTo(Vec3d.ofCenter(hit)) > 100.0) return;
        if (!world.canPlayerModifyAt(player, hit)) return;
        BrickPiece intent = packet.piece();
        if (intent == null || !intent.valid() ||
                !Float.isFinite(packet.localX()) ||
                !Float.isFinite(packet.localY()) || !Float.isFinite(packet.localZ())) return;

        if (packet.remove()) {
            if (world.getBlockEntity(hit) instanceof BrickCellEntity cell) {
                BrickPiece selected = cell.pickAt(hit.getY()+packet.localY(), packet.side()==Direction.UP);
                if (selected != null && remove(world,player,selected)) remember(world,player,selected,false);
            }
            return;
        }

        // The old single-action Studio export is intentionally disabled. Large plans must
        // use StudioExportPayload for full preflight and chunk-safe batched placement.
        if(packet.studio()) {
            player.sendMessage(Text.translatable("message.brickcraft.export.old_protocol"),true);
            return;
        }
        final BrickPiece candidate;
        {
            if (packet.localX() < -.01f || packet.localX() > 1.01f ||
                    packet.localY() < -.01f || packet.localY() > 1.01f ||
                    packet.localZ() < -.01f || packet.localZ() > 1.01f) return;
            boolean lego = world.getBlockState(hit).isOf(CELL);
            if (!lego && world.getBlockState(hit).isAir()) return;
            Direction side = packet.side();
            int px = hit.getX() + side.getOffsetX();
            int pz = hit.getZ() + side.getOffsetZ();
            int baseUnits = (hit.getY()+side.getOffsetY())*5;
            if (lego && world.getBlockEntity(hit) instanceof BrickCellEntity cell) {
                BrickPiece selected = cell.pickAt(hit.getY()+packet.localY(),side==Direction.UP);
                if (selected != null) {
                    if (side == Direction.UP) {
                        px = hit.getX(); pz = hit.getZ(); baseUnits = selected.top();
                    } else if (side.getAxis().isHorizontal()) {
                        baseUnits = selected.y();
                    }
                }
            }
            BrickPiece provisional = new BrickPiece(intent.kind(),intent.rgb(),px,baseUnits,pz,
                    intent.turn(),intent.angle(),intent.pitch(),intent.roll(),intent.partId(),intent.colorCode());
            if(lego && side==Direction.UP && world.getBlockEntity(hit) instanceof BrickCellEntity cell){
                BrickPiece support=cell.pickAt(hit.getY()+packet.localY(),true);
                // Only attach to a connector of the actually selected LEGO piece.
                // Clicks on top/bottom use the *real* LEGO face height rather than the
                // surrounding Minecraft block's 1x1x1 collision surface.
                double clickY=support==null?hit.getY()+packet.localY():
                    side==Direction.UP?support.top()/5.0:
                    side==Direction.DOWN?support.y()/5.0:hit.getY()+packet.localY();
                candidate=support==null?provisional:ConnectorSnapper.snap(provisional,support,
                        hit.getX()+packet.localX(),clickY,hit.getZ()+packet.localZ(),null).piece();
            }else candidate=provisional;
        }
        if(place(world,player,candidate)) remember(world,player,candidate,true);
        else player.sendMessage(Text.translatable("message.brickcraft.place.blocked"),true);
    }

    /** Server-side command placement; ignores player's reach but respects chunk and region safety. */
    public static boolean placeCommand(ServerWorld world,ServerPlayerEntity player,BrickPiece piece) {
        if(!placeChecked(world,player,piece,false))return false;
        if(player!=null)remember(world,player,piece,true);
        return true;
    }

    /** Validate the complete footprint BEFORE touching any world block. */
    private static boolean place(ServerWorld world, ServerPlayerEntity player, BrickPiece piece) {
        return placeChecked(world,player,piece,true);
    }
    private static boolean placeChecked(ServerWorld world, ServerPlayerEntity player,
                                        BrickPiece piece,boolean checkReach) {
        if (!piece.valid()) return false;
        if(piece.kind()==BrickKind.LDRAW_PART && LDrawLibrary.mesh(piece.partId())==null)return false;
        List<BlockPos> locations = piece.occupiedCells();
        if (locations.size()>256) return false;
        for (BlockPos pos : locations) {
            if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) return false;
            if(player!=null && (!world.canPlayerModifyAt(player,pos)
                    || (checkReach && player.squaredDistanceTo(Vec3d.ofCenter(pos))>256))) return false;
            if (world.getBlockState(pos).isAir()) continue;
            if (!(world.getBlockEntity(pos) instanceof BrickCellEntity cell) || !cell.canAdd(piece)) return false;
        }
        for (BlockPos pos : locations) {
            if (world.getBlockState(pos).isAir()) world.setBlockState(pos,CELL.getDefaultState(),3);
            if (world.getBlockEntity(pos) instanceof BrickCellEntity cell) cell.add(piece);
        }
        return true;
    }

    /** A LEGO piece is always removed from every fragment cell it occupies. */
    private static boolean remove(ServerWorld world, ServerPlayerEntity player, BrickPiece piece) {
        List<BlockPos> locations=piece.occupiedCells();
        for (BlockPos pos:locations) {
            if (!world.isChunkLoaded(pos) || !world.canPlayerModifyAt(player,pos)
                    || !(world.getBlockEntity(pos) instanceof BrickCellEntity cell)
                    || !cell.pieces().contains(piece)) return false;
        }
        for (BlockPos pos:locations) {
            if (world.getBlockEntity(pos) instanceof BrickCellEntity cell && cell.remove(piece)
                    && cell.pieces().isEmpty()) world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);
        }
        return true;
    }
}
