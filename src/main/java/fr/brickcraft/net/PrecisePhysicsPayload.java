package fr.brickcraft.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record PrecisePhysicsPayload(BlockPos anchor,boolean enable) implements CustomPayload {
    public static final CustomPayload.Id<PrecisePhysicsPayload> ID=
            new CustomPayload.Id<>(Identifier.of("brickcraft","precise_physics"));
    public static final PacketCodec<RegistryByteBuf,PrecisePhysicsPayload> CODEC=
            PacketCodec.of(PrecisePhysicsPayload::write,PrecisePhysicsPayload::read);
    private void write(RegistryByteBuf buf){buf.writeBlockPos(anchor);buf.writeBoolean(enable);}
    private static PrecisePhysicsPayload read(RegistryByteBuf buf){return new PrecisePhysicsPayload(buf.readBlockPos(),buf.readBoolean());}
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
