package fr.brickcraft;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** A real, registry-backed item. The reader is opened only on the client. */
public final class BrickGuideBookItem extends Item {
    public BrickGuideBookItem(Settings settings) { super(settings); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) fr.brickcraft.client.BrickGuideBookScreen.open();
        return TypedActionResult.success(stack, world.isClient());
    }
}
