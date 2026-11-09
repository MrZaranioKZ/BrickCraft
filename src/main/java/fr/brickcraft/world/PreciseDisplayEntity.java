package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.importer.PreciseWorldModel;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;

/** Single-NBT-anchor storage; keep compressed original bytes even if complete.zip is missing. */
public final class PreciseDisplayEntity extends BlockEntity {
    private PreciseWorldModel model;
    private byte[] packed=new byte[0];
    private boolean decodeAttempted=false;
    private long[] collisionCells=new long[0];
    // Version 0.12 accidentally omitted the entire 1x1x1 block under the anchor.
    // The projection anchor must provide collision for its own LEGO geometry.
    private long[] anchorMask=new long[0];
    private transient VoxelShape anchorShape;
    public boolean hasAnchorCollision(){return anchorMask.length==8;}
    public VoxelShape anchorCollisionShape(){
        if(anchorMask.length!=8)return VoxelShapes.empty();
        if(anchorShape==null)anchorShape=PreciseCollisionEntity.createShape(anchorMask);
        return anchorShape;
    }
    public void setAnchorCollisionMask(long[] data){
        if(data!=null&&data.length!=0&&data.length!=8)
            throw new IllegalArgumentException("Masque ancre invalide");
        anchorMask=data==null?new long[0]:data.clone();anchorShape=null;markDirty();
        if(world instanceof net.minecraft.server.world.ServerWorld server)
            server.getChunkManager().markForUpdate(pos);
    }

    public long[] collisionCells(){return collisionCells.clone();}
    public void setCollisionCells(long[] cells){collisionCells=cells.clone();markDirty();}
    public PreciseDisplayEntity(BlockPos pos,BlockState state){super(BrickCraft.PRECISE_ENTITY,pos,state);}
    public PreciseWorldModel model(){
        if(model==null && packed.length>0 && !decodeAttempted){
            decodeAttempted=true;
            try{model=PreciseWorldModel.readBytes(packed);}catch(IOException ignored){}
        }
        return model;
    }
    public void setModel(PreciseWorldModel value)throws IOException{
        byte[] bytes=value.writeBytes();
        this.model=value;this.packed=bytes;this.decodeAttempted=true;markDirty();
        if(world instanceof net.minecraft.server.world.ServerWorld server)server.getChunkManager().markForUpdate(pos);
    }
    @Override protected void writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registry){
        super.writeNbt(nbt,registry);
        nbt.putByteArray("BrickCraftPreciseV1",packed);
        nbt.putLongArray("BrickCraftPhysicsCells",collisionCells);
        if(anchorMask.length==8)nbt.putLongArray("BrickCraftAnchorMicroVoxels",anchorMask);
    }
    @Override public void readNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registry){
        super.readNbt(nbt,registry);
        packed=nbt.getByteArray("BrickCraftPreciseV1");model=null;decodeAttempted=false;
        collisionCells=nbt.getLongArray("BrickCraftPhysicsCells");
        long[] loadedMask=nbt.getLongArray("BrickCraftAnchorMicroVoxels");
        anchorMask=loadedMask.length==8?loadedMask:new long[0];anchorShape=null;
        // Preserve corrupted/missing-library data in NBT instead of silently deleting it.
        if(packed.length>0)model();
    }
    @Override public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket(){
        return BlockEntityUpdateS2CPacket.create(this);
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registry){
        return createNbt(registry);
    }
}
