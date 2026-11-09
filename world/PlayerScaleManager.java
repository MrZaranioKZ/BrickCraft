package fr.brickcraft.world;

import fr.brickcraft.net.PlayerScalePayload;
import fr.brickcraft.BrickCraft;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Locale;

/** Uses vanilla GENERIC_SCALE: model, camera eye height AND server/client collision box stay in sync. */
public final class PlayerScaleManager {
    private PlayerScaleManager(){}
    public static final double MIN=0.125,MAX=4.0;
    private static final Identifier MODIFIER_ID=Identifier.of("brickcraft","player_exploration_scale");
    private static final Identifier STEP_MODIFIER=Identifier.of("brickcraft","lego_ramp_step_height");
    /** Only applies to players next to BrickCraft physical blocks. No global climbing buff. */
    public static void tick(MinecraftServer server){
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){
            var step=player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
            if(step==null)continue;
            boolean close=!player.isSpectator()&&nearLegoPhysics(player);
            var previous=step.getModifier(STEP_MODIFIER);
            if(!close){if(previous!=null)step.removeModifier(STEP_MODIFIER);continue;}
            double wanted=ExplorationStepRules.stepHeight(
                    player.getAttributeValue(EntityAttributes.GENERIC_SCALE));
            // Avoid attribute change packets every tick. Respect other mods' adjustments.
            if(previous!=null&&Math.abs(step.getValue()-wanted)<.01)continue;
            if(previous!=null)step.removeModifier(STEP_MODIFIER);
            double baseline=step.getValue();
            if(Math.abs(baseline-wanted)>.01)
                step.addTemporaryModifier(new EntityAttributeModifier(STEP_MODIFIER,wanted-baseline,
                        EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
    private static boolean nearLegoPhysics(ServerPlayerEntity player){
        ServerWorld world=player.getServerWorld();
        BlockPos center=player.getBlockPos();
        int radius=(int)Math.min(3,Math.ceil(.3*player.getAttributeValue(EntityAttributes.GENERIC_SCALE))+1);
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for(int y=-1;y<=1;y++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            pos.set(center.getX()+x,center.getY()+y,center.getZ()+z);
            if(!world.isChunkLoaded(pos))continue;
            if(world.getBlockState(pos).isOf(BrickCraft.PRECISE_COLLISION))return true;
            if(world.getBlockState(pos).isOf(BrickCraft.PRECISE_DISPLAY)&&
                    world.getBlockEntity(pos) instanceof PreciseDisplayEntity anchor&&anchor.hasAnchorCollision())return true;
        }
        return false;
    }

    public static boolean allowed(double value){return Double.isFinite(value)&&value>=MIN&&value<=MAX;}
    public static void receive(ServerPlayerEntity player,PlayerScalePayload packet){
        double requested=packet.scale();
        if(!allowed(requested)||player.isSpectator()||
           !(player.isCreative() || player.getServer().isSingleplayer())){
            player.sendMessage(Text.translatable("message.brickcraft.scale.denied"),false);return;
        }
        var attribute=player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if(attribute==null)return;
        double oldEffectiveScale=attribute.getValue();
        EntityAttributeModifier previous=attribute.getModifier(MODIFIER_ID);
        attribute.removeModifier(MODIFIER_ID);
        if(Math.abs(requested-1.0)>0.00001)
            attribute.addPersistentModifier(new EntityAttributeModifier(MODIFIER_ID,requested-1.0,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        player.calculateDimensions();
        // Avoid growing inside walls. A failed change restores the former attribute and bounding box.
        if(attribute.getValue()>oldEffectiveScale+1e-5 &&
           !player.getWorld().isSpaceEmpty(player,player.getBoundingBox())){
            attribute.removeModifier(MODIFIER_ID);
            if(previous!=null)attribute.addPersistentModifier(previous);
            player.calculateDimensions();
            player.sendMessage(Text.translatable("message.brickcraft.scale.blocked"),false);
            return;
        }
        player.sendMessage(Text.translatable("message.brickcraft.scale.done",String.format(Locale.ROOT,"%.3f",requested)),true);
    }
    /** Retain the setting across respawns, if vanilla recreates the attribute container. */
    public static void respawn(ServerPlayerEntity oldPlayer,ServerPlayerEntity newPlayer,boolean alive){
        var oldAttr=oldPlayer.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        var nextAttr=newPlayer.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if(oldAttr==null||nextAttr==null)return;
        EntityAttributeModifier old=oldAttr.getModifier(MODIFIER_ID);
        if(old!=null){nextAttr.overwritePersistentModifier(old);newPlayer.calculateDimensions();}
    }
}
