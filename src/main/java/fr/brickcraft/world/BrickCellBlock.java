package fr.brickcraft.world;

import com.mojang.serialization.MapCodec;
import fr.brickcraft.core.BrickPiece;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;
import java.util.ArrayList;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/** One Minecraft block per stud and vertical slice, with precise LEGO-height shapes. */
public final class BrickCellBlock extends BlockWithEntity {
    public static final MapCodec<BrickCellBlock> CODEC = createCodec(BrickCellBlock::new);
    public BrickCellBlock(Settings settings) { super(settings); }
    @Override public MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BrickCellEntity(pos, state);
    }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world,
                                                     BlockPos pos, ShapeContext context) {
        if (!(world.getBlockEntity(pos) instanceof BrickCellEntity cell)) return VoxelShapes.fullCube();
        return cell.outlineShape();
    }

    /** Mining any fragment dismantles complete affected pieces, preventing orphan visuals. */
    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos,
                                             BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient
                && world.getBlockEntity(pos) instanceof BrickCellEntity cell) {
            for (BrickPiece part : new ArrayList<>(cell.pieces())) {
                for (BlockPos other : part.occupiedCells()) {
                    if (other.equals(pos) || !world.isChunkLoaded(other)) continue;
                    if (world.getBlockEntity(other) instanceof BrickCellEntity neighbor && neighbor.remove(part)
                            && neighbor.pieces().isEmpty()) {
                        world.setBlockState(other, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
        super.onStateReplaced(state,world,pos,newState,moved);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world,
                                                       BlockPos pos, ShapeContext context) {
        return getOutlineShape(state,world,pos,context);
    }
}
