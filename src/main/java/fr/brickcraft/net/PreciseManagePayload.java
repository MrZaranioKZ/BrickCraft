package fr.brickcraft.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Explicit, server-authorized projection listing and removal requests. */
public record PreciseManagePayload(byte operation,BlockPos anchor) implements CustomPayload {
    public static final byte LIST=0,REMOVE=1;
    public static final CustomPayload.Id<PreciseManagePayload> ID=
            new CustomPayload.Id<>(Identifier.of("brickcraft","precise_manage"));
    public static final PacketCodec<RegistryByteBuf,PreciseManagePayload> CODEC=
            PacketCodec.of(PreciseManagePayload::write,PreciseManagePayload::read);
    public static PreciseManagePayload list(){return new PreciseManagePayload(LIST,BlockPos.ORIGIN);}
    public static PreciseManagePayload remove(BlockPos pos){return new PreciseManagePayload(REMOVE,pos);}
    private void write(RegistryByteBuf buf){buf.writeByte(operation);buf.writeBlockPos(anchor);}
    private static PreciseManagePayload read(RegistryByteBuf buf){
        byte op=buf.readByte();BlockPos p=buf.readBlockPos();
        if(op!=LIST&&op!=REMOVE)throw new IllegalArgumentException("Operation inconnue");
        return new PreciseManagePayload(op,p);
    }
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
