package fr.brickcraft.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-authoritative world undo/redo, per player and world. */
public record BrickEditPayload(boolean redo) implements CustomPayload {
    public static final Id<BrickEditPayload> ID=new Id<>(Identifier.of("brickcraft","edit_history"));
    public static final PacketCodec<RegistryByteBuf,BrickEditPayload> CODEC=
        PacketCodec.of((payload,buf)->buf.writeBoolean(payload.redo()),buf->new BrickEditPayload(buf.readBoolean()));
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
