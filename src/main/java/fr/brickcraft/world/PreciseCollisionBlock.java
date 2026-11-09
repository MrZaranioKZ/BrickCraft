package fr.brickcraft.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/** Invisible, per-cell sub-voxel collision preserving details in the original LDraw mesh. */
public final class PreciseCollisionBlock extends BlockWithEntity {
    public static final MapCodec<PreciseCollisionBlock> CODEC=createCodec(PreciseCollisionBlock::new);
    public PreciseCollisionBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return CODEC;}
    @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos,BlockState state){
        return new PreciseCollisionEntity(pos,state);
    }
    @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext ctx){
        return VoxelShapes.empty(); // Never interfere with the visible projection's raycast.
    }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext ctx){
        return world.getBlockEntity(pos) instanceof PreciseCollisionEntity be?be.shape():VoxelShapes.fullCube();
    }
}
