package fr.brickcraft.world;
import fr.brickcraft.core.BrickPiece;import net.minecraft.util.math.BlockPos;import java.util.List;
public record BrickCellEntity(BlockPos pos,List<BrickPiece> pieces){public BlockPos getPos(){return pos;}}
