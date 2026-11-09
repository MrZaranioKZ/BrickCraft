package fr.brickcraft.net;

import fr.brickcraft.importer.LDrawProjectReader;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Bounded stream of original LDU coordinates + full 3x3 matrices, never rounded to the Minecraft grid. */
public record PreciseExportPayload(byte operation, BlockPos origin,int total,int offset,
                                   List<LDrawProjectReader.Instance> parts) implements CustomPayload {
    public static final byte BEGIN=0,PARTS=1,COMMIT=2,CANCEL=3;
    public static final int BATCH_SIZE=32;
    public static final CustomPayload.Id<PreciseExportPayload> ID =
            new CustomPayload.Id<>(Identifier.of("brickcraft","precise_export"));
    public static final PacketCodec<RegistryByteBuf,PreciseExportPayload> CODEC =
            PacketCodec.of(PreciseExportPayload::write,PreciseExportPayload::read);
    public static PreciseExportPayload begin(BlockPos origin,int total){return new PreciseExportPayload(BEGIN,origin,total,0,List.of());}
    public static PreciseExportPayload batch(int offset,List<LDrawProjectReader.Instance> parts){
        return new PreciseExportPayload(PARTS,BlockPos.ORIGIN,0,offset,List.copyOf(parts));
    }
    public static PreciseExportPayload commit(){return new PreciseExportPayload(COMMIT,BlockPos.ORIGIN,0,0,List.of());}
    public static PreciseExportPayload cancel(){return new PreciseExportPayload(CANCEL,BlockPos.ORIGIN,0,0,List.of());}
    private void write(RegistryByteBuf buf){
        buf.writeByte(operation);
        if(operation==BEGIN){buf.writeBlockPos(origin);buf.writeVarInt(total);}
        if(operation==PARTS){
            buf.writeVarInt(offset);buf.writeVarInt(parts.size());
            for(var p:parts){
                buf.writeString(p.partId(),96);buf.writeVarInt(p.color());
                buf.writeDouble(p.x());buf.writeDouble(p.y());buf.writeDouble(p.z());
                for(double d:p.matrix())buf.writeDouble(d);
            }
        }
    }
    private static PreciseExportPayload read(RegistryByteBuf buf){
        byte op=buf.readByte();
        if(op==BEGIN)return begin(buf.readBlockPos(),buf.readVarInt());
        if(op==PARTS){
            int offset=buf.readVarInt(),count=buf.readVarInt();
            if(offset<0||count<1||count>BATCH_SIZE)throw new IllegalArgumentException("Lot precis invalide");
            List<LDrawProjectReader.Instance> parts=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                String name=buf.readString(96);int color=buf.readVarInt();
                double x=buf.readDouble(),y=buf.readDouble(),z=buf.readDouble();
                double[] m=new double[9];for(int j=0;j<9;j++)m[j]=buf.readDouble();
                parts.add(new LDrawProjectReader.Instance(name,color,x,y,z,m));
            }
            return batch(offset,parts);
        }
        if(op==COMMIT)return commit();
        if(op==CANCEL)return cancel();
        throw new IllegalArgumentException("Operation precise inconnue");
    }
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
