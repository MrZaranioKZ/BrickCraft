package fr.brickcraft;

import fr.brickcraft.ldraw.LDrawLibrary;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/** Stable LDraw item identity independent of the client-only colour/part palette. */
public final class BrickPartStacks {
    private static final String KEY="BrickCraftPartId";
    private BrickPartStacks() {}

    public static ItemStack ldraw(String id,int amount) {
        if(id==null || !id.matches("[a-zA-Z0-9_-]{1,32}") || !LDrawLibrary.contains(id))
            throw new IllegalArgumentException("Reference LDraw inconnue : "+id);
        ItemStack stack=new ItemStack(BrickCraft.LDRAW_ITEM,amount);
        NbtCompound tag=new NbtCompound();tag.putString(KEY,id);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA,stack,tag);
        var info=LDrawLibrary.info(id);
        stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(info==null?"LDraw "+id:info.name()+" ["+id+"]"));
        return stack;
    }

    /** Null means a generic LDraw palette item (N key), not a broken item. */
    public static String boundLdrawId(ItemStack stack) {
        if(!stack.isOf(BrickCraft.LDRAW_ITEM))return null;
        NbtComponent data=stack.get(DataComponentTypes.CUSTOM_DATA);
        if(data==null || !data.contains(KEY))return null;
        String id=data.copyNbt().getString(KEY);
        return id.matches("[a-zA-Z0-9_-]{1,32}") && LDrawLibrary.contains(id)?id:null;
    }
}
