package fr.brickcraft.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/** One removable marker anchor for an entire lossless, NON-COLLIDING LDraw world projection. */
public final class PreciseDisplayBlock extends BlockWithEntity {
    public static final MapCodec<PreciseDisplayBlock> CODEC=createCodec(PreciseDisplayBlock::new);
    public PreciseDisplayBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return CODEC;}
    @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
    @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos,BlockState state){
        return new PreciseDisplayEntity(pos,state);
    }
    @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState newState,boolean moved){
        if(!state.isOf(newState.getBlock()) && world instanceof ServerWorld server &&
                world.getBlockEntity(pos) instanceof PreciseDisplayEntity entity)
            PrecisePhysicsManager.cleanup(server,entity);
        super.onStateReplaced(state,world,pos,newState,moved);
    }
    // The underlying marker can be mined normally to remove the entire projection.
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext ctx){
        return VoxelShapes.cuboid(.3,0,.3,.7,.35,.7);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext ctx){
        return world.getBlockEntity(pos) instanceof PreciseDisplayEntity be
                ?be.anchorCollisionShape():VoxelShapes.empty();
    }
}
