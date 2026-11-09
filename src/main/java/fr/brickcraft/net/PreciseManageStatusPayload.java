package fr.brickcraft.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Current loaded projections only, from the server's authoritative block entities. */
public record PreciseManageStatusPayload(byte event,List<Entry> projects,String message) implements CustomPayload {
    public static final byte LIST=0,REMOVED=1,ERROR=2;
    public static final int LIMIT=32;
    public record Entry(BlockPos anchor,int pieces){}
    public static final CustomPayload.Id<PreciseManageStatusPayload> ID=
            new CustomPayload.Id<>(Identifier.of("brickcraft","precise_manage_status"));
    public static final PacketCodec<RegistryByteBuf,PreciseManageStatusPayload> CODEC=
            PacketCodec.of(PreciseManageStatusPayload::write,PreciseManageStatusPayload::read);
    public PreciseManageStatusPayload{projects=List.copyOf(projects);}
    private void write(RegistryByteBuf buf){
        buf.writeByte(event);buf.writeString(message,240);buf.writeVarInt(projects.size());
        for(Entry p:projects){buf.writeBlockPos(p.anchor());buf.writeVarInt(p.pieces());}
    }
    private static PreciseManageStatusPayload read(RegistryByteBuf buf){
        byte event=buf.readByte();String message=buf.readString(240);int n=buf.readVarInt();
        if(n<0||n>LIMIT)throw new IllegalArgumentException("Liste de projections trop longue");
        List<Entry> projects=new ArrayList<>(n);
        for(int i=0;i<n;i++)projects.add(new Entry(buf.readBlockPos(),buf.readVarInt()));
        return new PreciseManageStatusPayload(event,projects,message);
    }
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
