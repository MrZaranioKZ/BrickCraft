package fr.brickcraft.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Small delta packets. Server owns and persists every modified LEGO transform. */
public record PreciseWorldEditPayload(BlockPos anchor,int index,byte operation,byte axis,double amount)
        implements CustomPayload {
    public static final CustomPayload.Id<PreciseWorldEditPayload> ID=
            new CustomPayload.Id<>(Identifier.of("brickcraft","world_edit"));
    public static final PacketCodec<RegistryByteBuf,PreciseWorldEditPayload> CODEC=
            PacketCodec.of(PreciseWorldEditPayload::write,PreciseWorldEditPayload::read);
    private void write(RegistryByteBuf b){
        b.writeBlockPos(anchor);b.writeVarInt(index);b.writeByte(operation);b.writeByte(axis);b.writeDouble(amount);
    }
    private static PreciseWorldEditPayload read(RegistryByteBuf b){
        BlockPos anchor=b.readBlockPos();int index=b.readVarInt();byte op=b.readByte(),axis=b.readByte();double amount=b.readDouble();
        if(index<0||index>=fr.brickcraft.importer.PreciseWorldModel.MAX_PARTS||op<0||op>5||axis<0||axis>2||!Double.isFinite(amount))
            throw new IllegalArgumentException("Paquet WorldEdit invalide");
        return new PreciseWorldEditPayload(anchor,index,op,axis,amount);
    }
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
