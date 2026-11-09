package fr.brickcraft.world;

import fr.brickcraft.core.BrickKind;
import net.minecraft.item.Item;

/** Each kind is a creative-inventory item usable with ordinary Minecraft hotbar. */
public final class BrickPartItem extends Item {
    private final BrickKind kind;
    public BrickPartItem(BrickKind kind, Settings settings) {super(settings); this.kind=kind;}
    public BrickKind kind() {return kind;}
}
