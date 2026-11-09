package fr.brickcraft;

import fr.brickcraft.core.BrickKind;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Dedicated, searchable creative-inventory category for the existing physical pieces. */
public final class BrickCreativeCatalog {
    private BrickCreativeCatalog() {}

    public static void register() {
        ItemGroup group=FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.brickcraft.main"))
                .icon(()->new ItemStack(BrickCraft.TOOL))
                .entries((context, entries)->{
                    entries.add(BrickCraft.GUIDE_BOOK);
                    entries.add(BrickCraft.TOOL);
                    // Generic LDraw selector duplicated the builder wand.
                    // Specific LDraw items are obtained using /brickcraft give <part>.
                    for(String prefix:new String[]{"BRICK_","PLATE_","TILE_","SLOPE_"}) {
                        for(BrickKind kind:BrickKind.values()) {
                            if(kind.name().startsWith(prefix))entries.add(BrickCraft.PART_ITEMS.get(kind));
                        }
                    }
                }).build();
        Registry.register(Registries.ITEM_GROUP,Identifier.of("brickcraft","main"),group);
    }
}
