package fr.brickcraft.client;
import java.util.List;
import fr.brickcraft.core.BrickKind;import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.world.BrickCellEntity;import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.VertexConsumerProvider;import net.minecraft.client.util.math.MatrixStack;
public class RenderLifecycleTest {
public static void main(String[] a){
BlockPos pos=new BlockPos(0,0,0);
var cell=new BrickCellEntity(pos,List.of(
new BrickPiece(BrickKind.BRICK_1X1,0xAA0000,-1,pos),
new BrickPiece(BrickKind.BRICK_1X1,0xDD00DD,100,pos),
new BrickPiece(BrickKind.BRICK_1X1,0x0000DD,-1,pos),
new BrickPiece(BrickKind.LDRAW_PART,0x55AAFF,-1,pos)));
var providers=new VertexConsumerProvider();
new BrickCellRenderer(null).render(cell,0,new MatrixStack(),providers,0,0);
if(providers.flushed()<80)throw new AssertionError("Faces not emitted");
System.out.println("RENDER PASS OK: "+providers.flushed()+" vertices "+providers.swaps+" layer switches");
}
}