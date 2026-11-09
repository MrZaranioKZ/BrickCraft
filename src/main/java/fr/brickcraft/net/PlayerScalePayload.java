package fr.brickcraft.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** One validated, finite scale multiplier. The server owns actual hitbox and eye height. */
public record PlayerScalePayload(float scale) implements CustomPayload {
    public static final Id<PlayerScalePayload> ID=new Id<>(Identifier.of("brickcraft","player_scale"));
    public static final PacketCodec<RegistryByteBuf,PlayerScalePayload> CODEC=
            PacketCodec.of(PlayerScalePayload::write,PlayerScalePayload::read);
    private void write(RegistryByteBuf buf){buf.writeFloat(scale);}
    private static PlayerScalePayload read(RegistryByteBuf buf){return new PlayerScalePayload(buf.readFloat());}
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
