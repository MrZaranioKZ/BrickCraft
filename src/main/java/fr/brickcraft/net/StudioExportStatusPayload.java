package fr.brickcraft.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client export progress; never interpreted as an authoritative world change. */
public record StudioExportStatusPayload(boolean finished,boolean success,String message) implements CustomPayload {
    public static final CustomPayload.Id<StudioExportStatusPayload> ID =
            new CustomPayload.Id<>(Identifier.of("brickcraft","studio_export_status"));
    public static final PacketCodec<RegistryByteBuf,StudioExportStatusPayload> CODEC =
            PacketCodec.of(StudioExportStatusPayload::write,StudioExportStatusPayload::read);
    private void write(RegistryByteBuf buf){
        buf.writeBoolean(finished);buf.writeBoolean(success);buf.writeString(message,240);
    }
    private static StudioExportStatusPayload read(RegistryByteBuf buf){
        return new StudioExportStatusPayload(buf.readBoolean(),buf.readBoolean(),buf.readString(240));
    }
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
