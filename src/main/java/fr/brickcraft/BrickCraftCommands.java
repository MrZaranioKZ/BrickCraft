package fr.brickcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.ldraw.LDrawLibrary;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.Locale;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Server-authoritative LEGO placement, usable from chat, /execute, functions and command blocks. */
public final class BrickCraftCommands {
    private BrickCraftCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->{
            var root=literal("brickcraft")
                    .executes(c->help(c.getSource()));
            root.then(literal("help").executes(c->help(c.getSource())));
            root.then(literal("guide").executes(c->giveGuide(c.getSource())));
            root.then(literal("list").executes(c->{
                StringBuilder names=new StringBuilder();
                for(BrickKind kind:BrickKind.values())if(kind!=BrickKind.LDRAW_PART){
                    if(names.length()>0)names.append(", ");
                    names.append(kind.name().toLowerCase(Locale.ROOT));
                }
                c.getSource().sendFeedback(()->Text.translatable("command.brickcraft.list",names.toString()),false);
                return 1;
            }));
            root.then(literal("place").requires(source->source.hasPermissionLevel(2)).then(argument("kind",StringArgumentType.word())
                    .suggests((c,b)->{
                        for(BrickKind k:BrickKind.values())if(k!=BrickKind.LDRAW_PART)
                            b.suggest(k.name().toLowerCase(Locale.ROOT));
                        return b.buildFuture();
                    })
                    .then(positionBranch(false))));
            root.then(literal("ldraw").requires(source->source.hasPermissionLevel(2)).then(argument("part",StringArgumentType.word())
                    .suggests((c,b)->{
                        for(var p:LDrawLibrary.search(b.getRemaining(),36))b.suggest(p.id());
                        return b.buildFuture();
                    })
                    .then(positionBranch(true))));
            root.then(literal("give").requires(source->source.hasPermissionLevel(2)).then(argument("part",StringArgumentType.word())
                    .suggests((c,b)->{
                        for(var p:LDrawLibrary.search(b.getRemaining(),36))b.suggest(p.id());
                        return b.buildFuture();
                    })
                    .executes(c->give(c,1))
                    .then(argument("count",IntegerArgumentType.integer(1,64))
                            .executes(c->give(c,IntegerArgumentType.getInteger(c,"count"))))));
            dispatcher.register(root);
        });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, net.minecraft.command.argument.PosArgument> positionBranch(boolean ldraw) {
        return argument("pos",BlockPosArgumentType.blockPos())
                .executes(c->place(c,ldraw,false,false))
                .then(argument("color",StringArgumentType.word())
                        .executes(c->place(c,ldraw,true,false))
                        .then(argument("angle",IntegerArgumentType.integer(-359,359))
                                .executes(c->place(c,ldraw,true,true))));
    }

    private static int giveGuide(ServerCommandSource source){
        ServerPlayerEntity player=source.getPlayer();
        if(player==null){source.sendError(Text.translatable("command.brickcraft.guide.player_only"));return 0;}
        player.giveItemStack(new ItemStack(BrickCraft.GUIDE_BOOK));
        source.sendFeedback(()->Text.translatable("command.brickcraft.guide.given"),false);
        return 1;
    }

    private static int help(ServerCommandSource source){
        source.sendFeedback(()->Text.translatable("command.brickcraft.help.place"),false);
        source.sendFeedback(()->Text.translatable("command.brickcraft.help.ldraw"),false);
        source.sendFeedback(()->Text.translatable("command.brickcraft.help.give"),false);
        source.sendFeedback(()->Text.translatable("command.brickcraft.help.colors"),false);
        source.sendFeedback(()->Text.translatable("command.brickcraft.guide.hint"),false);
        return 1;
    }

    private static int place(CommandContext<ServerCommandSource> c,boolean ldraw,boolean withColor,boolean withAngle){
        ServerCommandSource source=c.getSource();
        String ref=StringArgumentType.getString(c,ldraw?"part":"kind");
        BrickKind kind;
        String id="";
        if(ldraw){
            id=ref.toLowerCase(Locale.ROOT);
            if(!id.matches("[a-z0-9_-]{1,32}") || !LDrawLibrary.contains(id) || LDrawLibrary.mesh(id)==null){
                source.sendError(Text.translatable("command.brickcraft.error.ldraw_absent",ref));return 0;
            }
            kind=BrickKind.LDRAW_PART;
        }else{
            try{kind=BrickKind.valueOf(ref.toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException ex){source.sendError(Text.translatable("command.brickcraft.error.unknown_shape"));return 0;}
            if(kind==BrickKind.LDRAW_PART){source.sendError(Text.translatable("command.brickcraft.error.use_ldraw"));return 0;}
        }
        int color=0xF4F4F4;
        try{
            if(withColor)color=BrickCommandPalette.rgb(StringArgumentType.getString(c,"color"));
        }catch(IllegalArgumentException ex){source.sendError(Text.literal(ex.getMessage()));return 0;}
        int yaw=withAngle?Math.floorMod(IntegerArgumentType.getInteger(c,"angle"),360):0;
        BlockPos target=BlockPosArgumentType.getBlockPos(c,"pos");
        if(!source.getWorld().isInBuildLimit(target)||!source.getWorld().isChunkLoaded(target)){
            source.sendError(Text.translatable("command.brickcraft.error.chunk",target.toShortString()));return 0;
        }
        BrickPiece piece=new BrickPiece(kind,color,target.getX(),target.getY()*5,target.getZ(),yaw/90,yaw,0,0,id,-1);
        if(!BrickCraft.placeCommand(source.getWorld(),source.getPlayer(),piece)){
            source.sendError(Text.translatable("command.brickcraft.error.placement"));
            return 0;
        }
        String name=ldraw?id:kind.name().toLowerCase(Locale.ROOT);
        source.sendFeedback(()->Text.translatable("command.brickcraft.place.done",name,target.toShortString()),false);
        return 1;
    }

    private static int give(CommandContext<ServerCommandSource> c,int count){
        ServerCommandSource source=c.getSource();
        String id=StringArgumentType.getString(c,"part").toLowerCase(Locale.ROOT);
        if(!id.matches("[a-z0-9_-]{1,32}") || !LDrawLibrary.contains(id) || LDrawLibrary.mesh(id)==null){
            source.sendError(Text.translatable("command.brickcraft.error.missing_ldraw",id));return 0;
        }
        ServerPlayerEntity player=source.getPlayer();
        if(player==null){source.sendError(Text.translatable("command.brickcraft.error.player_only"));return 0;}
        ItemStack item=BrickPartStacks.ldraw(id,count);
        player.giveItemStack(item);
        source.sendFeedback(()->Text.translatable("command.brickcraft.give.done",count,id),false);
        return count;
    }
}
