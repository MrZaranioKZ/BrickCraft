package fr.brickcraft.net;

import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Ordered and size-bounded C2S transfer of one Studio blueprint. */
public record StudioExportPayload(byte operation, BlockPos origin, int grid, int total,
                                  int offset, List<BrickPiece> pieces) implements CustomPayload {
    public static final byte BEGIN=0, PARTS=1, COMMIT=2, CANCEL=3;
    public static final int BATCH_SIZE=24;
    public static final CustomPayload.Id<StudioExportPayload> ID =
            new CustomPayload.Id<>(Identifier.of("brickcraft","studio_export"));
    public static final PacketCodec<RegistryByteBuf,StudioExportPayload> CODEC =
            PacketCodec.of(StudioExportPayload::write,StudioExportPayload::read);

    public static StudioExportPayload begin(BlockPos origin,int grid,int total){
        return new StudioExportPayload(BEGIN,origin,grid,total,0,List.of());
    }
    public static StudioExportPayload batch(int offset,List<BrickPiece> pieces){
        return new StudioExportPayload(PARTS,new BlockPos(0,0,0),0,0,offset,List.copyOf(pieces));
    }
    public static StudioExportPayload commit(){
        return new StudioExportPayload(COMMIT,new BlockPos(0,0,0),0,0,0,List.of());
    }
    public static StudioExportPayload cancel(){
        return new StudioExportPayload(CANCEL,new BlockPos(0,0,0),0,0,0,List.of());
    }
    private void write(RegistryByteBuf buf){
        buf.writeByte(operation);
        if(operation==BEGIN){
            buf.writeBlockPos(origin);buf.writeVarInt(grid);buf.writeVarInt(total);
        }else if(operation==PARTS){
            buf.writeVarInt(offset);buf.writeVarInt(pieces.size());
            for(BrickPiece p:pieces){
                buf.writeVarInt(p.kind().ordinal());buf.writeInt(p.rgb());
                buf.writeVarInt(p.x());buf.writeVarInt(p.y());buf.writeVarInt(p.z());
                buf.writeByte(p.turn());buf.writeShort(p.angle());
                buf.writeByte(p.pitch()/90);buf.writeByte(p.roll()/90);
                buf.writeString(p.partId()==null?"":p.partId(),40);buf.writeInt(p.colorCode());
            }
        }
    }
    private static StudioExportPayload read(RegistryByteBuf buf){
        byte op=buf.readByte();
        if(op==BEGIN)return begin(buf.readBlockPos(),buf.readVarInt(),buf.readVarInt());
        if(op==PARTS){
            int offset=buf.readVarInt(), count=buf.readVarInt();
            if(offset<0 || count<1 || count>BATCH_SIZE)throw new IllegalArgumentException("Studio batch too large");
            List<BrickPiece> pieces=new ArrayList<>(count);
            BrickKind[] kinds=BrickKind.values();
            for(int i=0;i<count;i++){
                int kind=buf.readVarInt(),rgb=buf.readInt();
                int x=buf.readVarInt(),y=buf.readVarInt(),z=buf.readVarInt();
                int turn=buf.readByte(),angle=buf.readUnsignedShort();
                int pitch=buf.readUnsignedByte()*90,roll=buf.readUnsignedByte()*90;
                String id=buf.readString(40);int code=buf.readInt();
                if(kind<0 || kind>=kinds.length)throw new IllegalArgumentException("Invalid piece kind");
                pieces.add(new BrickPiece(kinds[kind],rgb,x,y,z,turn,angle,pitch,roll,id,code));
            }
            return batch(offset,pieces);
        }
        if(op==COMMIT)return commit();
        if(op==CANCEL)return cancel();
        throw new IllegalArgumentException("Unknown Studio export operation");
    }
    @Override public CustomPayload.Id<? extends CustomPayload> getId(){return ID;}
}
