package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** A 'fragment' cell: stores references to all complete world-space pieces crossing it. */
public final class BrickCellEntity extends BlockEntity {
    public static final int MAX_PIECES = 48;
    private final ArrayList<BrickPiece> pieces = new ArrayList<>();
    private transient VoxelShape cachedShape;
    // If the LDraw zip is temporarily missing, retain unknown pieces in NBT intact.
    private final NbtList unresolvedPieces = new NbtList();

    public BrickCellEntity(BlockPos pos, BlockState state) { super(BrickCraft.CELL_ENTITY, pos, state); }
    public List<BrickPiece> pieces() { return List.copyOf(pieces); }

    /** Cached shape of the classic stud-grid cells: round studs instead of square cubes. */
    public VoxelShape outlineShape() {
        if(cachedShape!=null)return cachedShape;
        VoxelShape result=VoxelShapes.empty();
        for(BrickPiece part:pieces){
            double bottom=Math.max(0.0,part.y()/5.0-pos.getY());
            double top=Math.min(1.0,part.top()/5.0-pos.getY());
            if(top>bottom)result=VoxelShapes.union(result,VoxelShapes.cuboid(0,bottom,0,1,top,1));
            if(part.kind().studs){
                double low=Math.max(0.0,part.top()/5.0-pos.getY());
                double high=Math.min(1.0,(part.top()+1)/5.0-pos.getY());
                if(high<=low)continue;
                // Eight horizontal bands approximate the real round 0.6-stud diameter.
                for(int i=0;i<8;i++){
                    double x0=.2+i*.6/8.0,x1=.2+(i+1)*.6/8.0;
                    double mid=(x0+x1)/2.0;
                    double half=Math.sqrt(Math.max(0,.3*.3-(mid-.5)*(mid-.5)));
                    result=VoxelShapes.union(result,VoxelShapes.cuboid(x0,low,.5-half,x1,high,.5+half));
                }
            }
        }
        return cachedShape=result.isEmpty()?VoxelShapes.fullCube():result;
    }

    public boolean canAdd(BrickPiece candidate) {
        if (pieces.size() >= MAX_PIECES || !unresolvedPieces.isEmpty()) return false;
        for (BrickPiece old : pieces) if (old.intersects(candidate)) return false;
        return true;
    }

    public boolean add(BrickPiece piece) {
        if (!piece.occupiedCells().contains(pos) || !canAdd(piece)) return false;
        pieces.add(piece); sync(); return true;
    }

    /** A fast insertion only for the server's prevalidated, cell-by-cell large export. */
    public boolean addFromExport(BrickPiece piece) {
        if(!canAdd(piece))return false;
        pieces.add(piece);sync();return true;
    }

    public boolean remove(BrickPiece piece) {
        if (!pieces.remove(piece)) return false;
        sync(); return true;
    }

    /** For the top of the visible LEGO under the cursor; allows exact LEGO layer stacking. */
    public BrickPiece topmost() {
        BrickPiece chosen = null;
        for (BrickPiece p : pieces) {
            if (chosen == null || p.top() > chosen.top()) chosen = p;
        }
        return chosen;
    }

    /** Distinguishes multiple plates in one cell by the precise clicked height. */
    public BrickPiece pickAt(double worldY, boolean topFace) {
        BrickPiece chosen=null;
        double best=Double.POSITIVE_INFINITY;
        for(BrickPiece p:pieces){
            double low=p.y()/5.0,high=p.top()/5.0;
            // A click at the joint belongs to the upper brick (its bottom), not to both.
            double distance;
            if(topFace)distance=Math.abs(worldY-high);
            else if(worldY>=low-.005 && worldY<=high+.005)distance=0;
            else distance=Math.min(Math.abs(worldY-low),Math.abs(worldY-high));
            if(distance<best-1e-6 || (Math.abs(distance-best)<1e-6 &&
                    chosen!=null && (topFace?p.top()>chosen.top():p.y()>chosen.y()))) {
                best=distance;chosen=p;
            }
        }
        return chosen;
    }

    private void sync() {
        cachedShape=null;
        markDirty();
        if (world instanceof ServerWorld serverWorld) serverWorld.getChunkManager().markForUpdate(pos);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        NbtList items = new NbtList();
        for (BrickPiece p : pieces) {
            NbtCompound v = new NbtCompound();
            v.putString("Kind", p.kind().name());
            int[] offsets=BrickCellCoordinates.relative(p.x(),p.y(),p.z(),pos.getX(),pos.getY(),pos.getZ());
            // Relative coordinates are portable when WorldEdit/Axiom copies the cell NBT.
            v.putBoolean("RelativeV20",true);
            v.putInt("RGB",p.rgb());v.putInt("X",offsets[0]);v.putInt("Y",offsets[1]);
            v.putInt("Z",offsets[2]);v.putInt("Turn",p.turn());v.putInt("Angle",p.angle());
            v.putInt("Pitch",p.pitch()); v.putInt("Roll",p.roll());v.putInt("LDrawColour",p.colorCode());
            if(p.kind()==BrickKind.LDRAW_PART)v.putString("LDrawId",p.partId());
            items.add(v);
        }
        for(int i=0;i<unresolvedPieces.size();i++)items.add(unresolvedPieces.getCompound(i).copy());
        nbt.put("PiecesV2", items);
    }

    @Override public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        pieces.clear();unresolvedPieces.clear();cachedShape=null;
        NbtList items = nbt.getList("PiecesV2", NbtElement.COMPOUND_TYPE);
        // Previous V0.1 format intentionally not migrated: its geometry uses a different scale.
        for (int i=0; i<items.size() && pieces.size()<MAX_PIECES; i++) {
            NbtCompound v = items.getCompound(i);
            try {
                // Legacy V0.1-V0.19 saves used world coordinates; the new format is
                // cell-relative, so copy/paste automatically relocates complete assemblies.
                int[] coords=v.getBoolean("RelativeV20")?
                    BrickCellCoordinates.absolute(v.getInt("X"),v.getInt("Y"),v.getInt("Z"),
                            pos.getX(),pos.getY(),pos.getZ()):
                    new int[]{v.getInt("X"),v.getInt("Y"),v.getInt("Z")};
                BrickPiece p = new BrickPiece(BrickKind.valueOf(v.getString("Kind")),
                    v.getInt("RGB") & 0xFFFFFF, coords[0],coords[1],coords[2], v.getInt("Turn"),
                    v.contains("Angle", NbtElement.NUMBER_TYPE) ? v.getInt("Angle") : v.getInt("Turn")*90,
                    v.getInt("Pitch"),v.getInt("Roll"),v.getString("LDrawId"),
                    v.contains("LDrawColour", NbtElement.NUMBER_TYPE)?v.getInt("LDrawColour"):-1);
                if(p.kind()==BrickKind.LDRAW_PART && !p.valid()) {
                    unresolvedPieces.add(portableEntry(v)); // Preserve worlds when users forget complete.zip.
                } else if (p.valid() && p.occupiedCells().contains(pos) && canAdd(p))pieces.add(p);
                else if(!unresolvedPieces.isEmpty())unresolvedPieces.add(portableEntry(v));
            } catch (IllegalArgumentException ignored) {
                if("LDRAW_PART".equals(v.getString("Kind")))unresolvedPieces.add(portableEntry(v));
            }
        }
    }


    /** Convert unresolved legacy NBT too: missing libraries must not break schematic relocation. */
    private NbtCompound portableEntry(NbtCompound original) {
        NbtCompound converted=original.copy();
        if(!converted.getBoolean("RelativeV20")) {
            int[] rel=BrickCellCoordinates.relative(converted.getInt("X"),converted.getInt("Y"),
                    converted.getInt("Z"),pos.getX(),pos.getY(),pos.getZ());
            converted.putInt("X",rel[0]);converted.putInt("Y",rel[1]);converted.putInt("Z",rel[2]);
            converted.putBoolean("RelativeV20",true);
        }
        return converted;
    }

    @Override public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}
