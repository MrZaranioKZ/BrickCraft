package fr.brickcraft.net;

import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Both UIs send placement intents. The server calculates and validates occupied cells. */
public record BrickActionPayload(BlockPos hit, Direction side, float localX, float localY,
                                 float localZ, boolean remove, boolean studio, BrickPiece piece)
        implements CustomPayload {
    public static final CustomPayload.Id<BrickActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of("brickcraft", "action"));
    public static final PacketCodec<RegistryByteBuf,BrickActionPayload> CODEC =
            PacketCodec.of(BrickActionPayload::write, BrickActionPayload::read);

    private void write(RegistryByteBuf buf) {
        buf.writeBlockPos(hit);
        buf.writeByte(side.getId());
        buf.writeFloat(localX); buf.writeFloat(localY); buf.writeFloat(localZ);
        buf.writeBoolean(remove); buf.writeBoolean(studio);
        buf.writeVarInt(piece.kind().ordinal()); buf.writeInt(piece.rgb());
        buf.writeInt(piece.x()); buf.writeInt(piece.y()); buf.writeInt(piece.z());
        buf.writeByte(piece.turn());
        buf.writeShort(piece.angle());
        buf.writeByte(piece.pitch()/90); buf.writeByte(piece.roll()/90);
        buf.writeString(piece.partId()==null?"":piece.partId(),40);
        buf.writeInt(piece.colorCode());
    }

    private static BrickActionPayload read(RegistryByteBuf buf) {
        BlockPos hit = buf.readBlockPos();
        Direction side = Direction.byId(buf.readByte());
        float x = buf.readFloat(), y = buf.readFloat(), z = buf.readFloat();
        boolean remove = buf.readBoolean(), studio = buf.readBoolean();
        int index = buf.readVarInt(), rgb = buf.readInt();
        int px = buf.readInt(), py = buf.readInt(), pz = buf.readInt(), turn = buf.readByte();
        int angle = buf.readUnsignedShort();
        int pitch=buf.readUnsignedByte()*90,roll=buf.readUnsignedByte()*90;
        String partId=buf.readString(40);
        int colorCode=buf.readInt();
        BrickKind[] kinds = BrickKind.values();
        BrickKind kind = kinds[Math.floorMod(index, kinds.length)];
        return new BrickActionPayload(hit, side, x,y,z, remove,studio,
                new BrickPiece(kind,rgb,px,py,pz,turn,angle,pitch,roll,partId,colorCode));
    }
    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
