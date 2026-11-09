package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
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

/** Stores only 64 bytes per collision block: 8x8x8 sub-block occupancy, synced to clients. */
public final class PreciseCollisionEntity extends BlockEntity {
    private long[] bits=new long[0];
    private transient VoxelShape cached;
    public PreciseCollisionEntity(BlockPos pos,BlockState state){super(BrickCraft.PRECISE_COLLISION_ENTITY,pos,state);}
    public void setMask(long[] mask){
        if(mask==null||mask.length!=8)throw new IllegalArgumentException("Masque de collision invalide");
        bits=mask.clone();cached=null;markDirty();
        if(world instanceof net.minecraft.server.world.ServerWorld server)
            server.getChunkManager().markForUpdate(pos);
    }
    public VoxelShape shape(){
        if(bits.length!=8)return VoxelShapes.empty(); // Unknown/legacy data must NOT become an invisible full wall.
        if(cached!=null)return cached;
        return cached=createShape(bits);
    }
    /** Shape utility shared by transparent collision blocks AND the visible projection anchor. */
    public static VoxelShape createShape(long[] bits){
        if(bits==null||bits.length!=8)return VoxelShapes.empty();
        // Merge occupied micro-voxels into maximal rectangular boxes for cheap collision tests.
        boolean[] used=new boolean[512];VoxelShape result=VoxelShapes.empty();
        for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<8;x++){
            if(!free(bits,x,y,z,used))continue;
            int xx=x+1;while(xx<8&&free(bits,xx,y,z,used))xx++;
            int zz=z+1;
            zloop:while(zz<8){for(int a=x;a<xx;a++)if(!free(bits,a,y,zz,used))break zloop;zz++;}
            int yy=y+1;
            yloop:while(yy<8){for(int b=z;b<zz;b++)for(int a=x;a<xx;a++)
                if(!free(bits,a,yy,b,used))break yloop;yy++;}
            for(int b=y;b<yy;b++)for(int c=z;c<zz;c++)for(int a=x;a<xx;a++)used[b*64+c*8+a]=true;
            result=VoxelShapes.union(result,VoxelShapes.cuboid(x/8.0,y/8.0,z/8.0,xx/8.0,yy/8.0,zz/8.0));
        }
        return result;
    }
    private static boolean free(long[] bits,int x,int y,int z,boolean[] used){
        return !used[y*64+z*8+x]&&PreciseMeshCollisionPlan.occupied(bits,x,y,z);
    }
    @Override protected void writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup){
        super.writeNbt(nbt,lookup);
        if(bits.length==8)nbt.putLongArray("BrickCraftMicroVoxels",bits);
    }
    @Override public void readNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup){
        super.readNbt(nbt,lookup);long[] data=nbt.getLongArray("BrickCraftMicroVoxels");
        bits=data.length==8?data:new long[0];cached=null;
    }
    @Override public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket(){
        return BlockEntityUpdateS2CPacket.create(this);
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup){return createNbt(lookup);}
}
