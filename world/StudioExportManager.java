package fr.brickcraft.world;

import fr.brickcraft.BrickCraft;
import fr.brickcraft.core.BrickPiece;
import fr.brickcraft.core.StudioExportLimits;
import fr.brickcraft.ldraw.LDrawLibrary;
import fr.brickcraft.core.BrickKind;
import fr.brickcraft.net.StudioExportPayload;
import fr.brickcraft.net.StudioExportStatusPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Server-authoritative large Studio exports. Receives a bounded blueprint, preflights its
 * entire footprint without editing the world, then commits/rolls back incrementally.
 * Work runs only on the server thread, with a fixed cell budget per tick.
 */
public final class StudioExportManager {
    private static final int MAX_ACTIVE_JOBS=2;
    private static final int CELLS_PER_TICK=320;
    private static final int RECEIVE_TIMEOUT_TICKS=600;
    private static final Map<UUID,Job> JOBS=new HashMap<>();

    private enum Phase { RECEIVING, VALIDATING, APPLYING, ROLLING_BACK }
    private record PlacedCell(BlockPos pos,BrickPiece piece){}
    private static final class Job {
        final UUID playerId;
        final ServerWorld world;
        final BlockPos origin;
        final int grid,total;
        final List<BrickPiece> received=new ArrayList<>();
        final List<BrickPiece> worldPieces=new ArrayList<>();
        final List<List<BlockPos>> footprints=new ArrayList<>();
        final Map<BlockPos,List<BrickPiece>> planned=new HashMap<>();
        final Deque<PlacedCell> placed=new ArrayDeque<>();
        Phase phase=Phase.RECEIVING;
        int receiveTicks=0,cellCount=0,pieceIndex=0,cellIndex=0,lastProgress=-1,rollbackFailures=0;
        String failure="";
        Job(ServerPlayerEntity player,BlockPos origin,int grid,int total){
            this.playerId=player.getUuid();this.world=player.getServerWorld();
            this.origin=origin.toImmutable();this.grid=grid;this.total=total;
        }
    }
    private StudioExportManager(){}

    public static void receive(ServerPlayerEntity player,StudioExportPayload packet){
        if(packet.operation()==StudioExportPayload.BEGIN){
            begin(player,packet);return;
        }
        Job job=JOBS.get(player.getUuid());
        if(job==null){
            // A refused BEGIN is followed by queued batch packets. Do not spam the player.
            if(packet.operation()==StudioExportPayload.COMMIT)
                reply(player,true,false,"Aucun export actif (debut refuse ou expire)");
            return;
        }
        if(packet.operation()==StudioExportPayload.CANCEL){
            if(job.phase==Phase.APPLYING || job.phase==Phase.ROLLING_BACK){
                job.failure="Export annule";job.phase=Phase.ROLLING_BACK;
                reply(player,false,false,"Annulation : retrait des pieces deja posees...");
            }else stop(job,false,"Export annule (monde inchange)");
            return;
        }
        // Ignore stale/duplicate network packets once validation has begun. Never cancel a
        // running build because a repeated COMMIT arrived from a laggy client.
        if(job.phase!=Phase.RECEIVING)return;
        if(player.getServerWorld()!=job.world){
            reject(job,"Dimension modifiee avant le transfert");return;
        }
        if(packet.operation()==StudioExportPayload.PARTS){
            if(!StudioExportLimits.validBatch(packet.offset(),packet.pieces().size(),
                    job.received.size(),job.total)){
                reject(job,"Lot invalide ou envoi incomplet");return;
            }
            for(BrickPiece p:packet.pieces()){
                if(!StudioExportLimits.validLocal(p,job.grid)){
                    reject(job,"Piece hors plateau ou invalide");return;
                }
            }
            job.received.addAll(packet.pieces());job.receiveTicks=0;
        }else if(packet.operation()==StudioExportPayload.COMMIT){
            if(job.received.size()!=job.total){
                reject(job,"Projet incomplet : "+job.received.size()+"/"+job.total);return;
            }
            job.phase=Phase.VALIDATING;
            reply(player,false,true,"Plan recu : "+job.total+" pieces. Verification du terrain...");
        }else reject(job,"Operation inconnue");
    }
    private static void begin(ServerPlayerEntity player,StudioExportPayload packet){
        if(JOBS.containsKey(player.getUuid())){
            reply(player,false,false,"Un export est deja en cours. Termine-le avant de recommencer.");return;
        }
        if(JOBS.size()>=MAX_ACTIVE_JOBS){
            reply(player,true,false,"Le serveur traite deja trop d'exports simultanes");return;
        }
        ServerWorld world=player.getServerWorld();BlockPos pos=packet.origin();
        if(!player.isCreative() || !StudioExportLimits.validGrid(packet.grid()) ||
                packet.total()<1 || packet.total()>StudioExportLimits.MAX_PIECES ||
                !world.isInBuildLimit(pos) ||
                player.squaredDistanceTo(Vec3d.ofCenter(pos))>144.0 ||
                !world.canPlayerModifyAt(player,pos)){
            reply(player,true,false,"Export refuse : mode Creatif, origine proche et droits requis");return;
        }
        JOBS.put(player.getUuid(),new Job(player,pos,packet.grid(),packet.total()));
        reply(player,false,true,"Reception du projet : "+packet.total()+" pieces...");
    }

    public static void tick(MinecraftServer server){
        for(Job job:new ArrayList<>(JOBS.values())){
            if(JOBS.get(job.playerId)!=job)continue;
            ServerPlayerEntity player=server.getPlayerManager().getPlayer(job.playerId);
            if(job.phase==Phase.RECEIVING){
                if(++job.receiveTicks>RECEIVE_TIMEOUT_TICKS || player==null)
                    stop(job,false,"Export expire avant reception complete");
                continue;
            }
            if(job.phase!=Phase.ROLLING_BACK && (player==null || player.getServerWorld()!=job.world || !player.isCreative())){
                if(job.phase==Phase.APPLYING){
                    job.failure="Joueur deconnecte ou dimension / mode modifie";
                    job.phase=Phase.ROLLING_BACK;
                }else stop(job,false,"Export interrompu avant construction");
                continue;
            }
            try{
                switch(job.phase){
                    case VALIDATING -> validate(job,player);
                    case APPLYING -> apply(job,player);
                    case ROLLING_BACK -> rollback(job);
                    default -> {}
                }
            }catch(RuntimeException e){
                // Never retain a partly built project after an unexpected runtime failure.
                if(job.phase==Phase.ROLLING_BACK){
                    stop(job,false,"ERREUR : rollback interrompu, verifie la zone : "+e.getClass().getSimpleName());
                }else if(job.phase==Phase.APPLYING){
                    job.failure="Erreur serveur pendant export : "+e.getClass().getSimpleName();
                    job.phase=Phase.ROLLING_BACK;
                }else stop(job,false,"Erreur de validation : "+e.getClass().getSimpleName());
            }
        }
    }

    private static void validate(Job job,ServerPlayerEntity player){
        int budget=CELLS_PER_TICK;
        while(budget>0 && job.pieceIndex<job.total){
            if(job.cellIndex==0){
                BrickPiece local=job.received.get(job.pieceIndex);
                if(!StudioExportLimits.validLocal(local,job.grid) ||
                        StudioExportLimits.upperBoundCells(local)>StudioExportLimits.MAX_CELLS_PER_PIECE){
                    reject(job,"Piece trop grande / hors plateau (index "+job.pieceIndex+")");return;
                }
                BrickPiece global;
                try{global=StudioExportLimits.inWorld(local,job.origin.getX(),job.origin.getY(),job.origin.getZ());}
                catch(IllegalArgumentException e){reject(job,"Origine hors limites");return;}
                if(!global.valid() || (global.kind()==BrickKind.LDRAW_PART &&
                        LDrawLibrary.mesh(global.partId())==null)){
                    reject(job,"Piece LDraw manquante sur le serveur : "+local.name());return;
                }
                List<BlockPos> cells=global.occupiedCells();
                if(cells.isEmpty() || cells.size()>StudioExportLimits.MAX_CELLS_PER_PIECE ||
                        job.cellCount+cells.size()>StudioExportLimits.MAX_TOTAL_CELLS){
                    reject(job,"Construction trop volumineuse (limite de cellules)");return;
                }
                job.cellCount+=cells.size();
                job.worldPieces.add(global);job.footprints.add(cells);
            }
            BrickPiece piece=job.worldPieces.get(job.pieceIndex);
            List<BlockPos> footprint=job.footprints.get(job.pieceIndex);
            while(budget>0 && job.cellIndex<footprint.size()){
                BlockPos pos=footprint.get(job.cellIndex++);budget--;
                if(!job.world.isInBuildLimit(pos) || !job.world.canPlayerModifyAt(player,pos)){
                    reject(job,"Hauteur ou zone protegee a "+pos.toShortString());return;
                }
                // Minecraft loads the target chunk here if necessary. Throttled by cell budget.
                BlockState state=job.world.getBlockState(pos);
                List<BrickPiece> reserved=job.planned.get(pos);
                if(!state.isAir()){
                    if(!state.isOf(BrickCraft.CELL) ||
                            !(job.world.getBlockEntity(pos) instanceof BrickCellEntity cell) ||
                            !cell.canAdd(piece) ||
                            cell.pieces().size()+(reserved==null?0:reserved.size())>=BrickCellEntity.MAX_PIECES){
                        reject(job,"Terrain occupe / LEGO en collision a "+pos.toShortString());return;
                    }
                }else if(reserved!=null && reserved.size()>=BrickCellEntity.MAX_PIECES){
                    reject(job,"Trop de pieces dans une cellule a "+pos.toShortString());return;
                }
                if(reserved!=null){
                    for(BrickPiece other:reserved)if(other.intersects(piece)){
                        reject(job,"Pieces du projet en collision a "+pos.toShortString());return;
                    }
                }else if(job.planned.size()>=StudioExportLimits.MAX_DISTINCT_CELLS){
                    reject(job,"Trop de cellules distinctes");return;
                }
                job.planned.computeIfAbsent(pos,k->new ArrayList<>()).add(piece);
            }
            if(job.cellIndex>=footprint.size()){
                job.pieceIndex++;job.cellIndex=0;
            }
        }
        if(job.pieceIndex==job.total){
            job.phase=Phase.APPLYING;job.pieceIndex=0;job.cellIndex=0;
            job.lastProgress=-1;
            job.planned.clear(); // Release preflight collision map before building.
            progress(job,player,0,"Construction");
        }else progress(job,player,job.pieceIndex*100/job.total,"Verification");
    }

    private static void apply(Job job,ServerPlayerEntity player){
        int budget=CELLS_PER_TICK;
        while(budget>0 && job.pieceIndex<job.total){
            BrickPiece piece=job.worldPieces.get(job.pieceIndex);
            List<BlockPos> footprint=job.footprints.get(job.pieceIndex);
            while(budget>0 && job.cellIndex<footprint.size()){
                BlockPos pos=footprint.get(job.cellIndex);budget--;
                if(!job.world.isInBuildLimit(pos) || !job.world.canPlayerModifyAt(player,pos)){
                    failDuringBuild(job,"Zone devenue inaccessible : "+pos.toShortString());return;
                }
                BlockState state=job.world.getBlockState(pos);
                if(state.isAir()){
                    if(!job.world.setBlockState(pos,BrickCraft.CELL.getDefaultState(),3)){
                        failDuringBuild(job,"Creation de cellule impossible");return;
                    }
                }else if(!state.isOf(BrickCraft.CELL)){
                    failDuringBuild(job,"Terrain modifie pendant l'export");return;
                }
                if(!(job.world.getBlockEntity(pos) instanceof BrickCellEntity cell) ||
                        !cell.addFromExport(piece)){
                    // If we just made an empty container, clean it up before rollback.
                    if(job.world.getBlockEntity(pos) instanceof BrickCellEntity empty && empty.pieces().isEmpty())
                        job.world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);
                    failDuringBuild(job,"Collision apparue pendant l'export");return;
                }
                job.placed.push(new PlacedCell(pos,piece));job.cellIndex++;
            }
            if(job.cellIndex>=footprint.size()){
                job.pieceIndex++;job.cellIndex=0;
            }
        }
        if(job.pieceIndex==job.total){
            stop(job,true,"Export termine : "+job.total+" pieces et "+job.cellCount+" fragments places");
        }else progress(job,player,job.pieceIndex*100/job.total,"Construction");
    }

    private static void failDuringBuild(Job job,String reason){
        job.failure=reason;job.phase=Phase.ROLLING_BACK;
        ServerPlayerEntity player=job.world.getServer().getPlayerManager().getPlayer(job.playerId);
        if(player!=null)reply(player,false,false,"Export interrompu : retour en arriere...");
    }
    private static void rollback(Job job){
        int budget=CELLS_PER_TICK;
        while(budget-->0 && !job.placed.isEmpty()){
            PlacedCell applied=job.placed.pop();BlockPos pos=applied.pos();
            if(job.world.getBlockEntity(pos) instanceof BrickCellEntity cell){
                if(cell.remove(applied.piece())){
                    if(cell.pieces().isEmpty())job.world.setBlockState(pos,Blocks.AIR.getDefaultState(),3);
                }else job.rollbackFailures++;
            }else job.rollbackFailures++;
        }
        if(job.placed.isEmpty()){
            if(job.rollbackFailures==0)stop(job,false,job.failure+" — retour en arriere effectue");
            else stop(job,false,job.failure+" — rollback INCOMPLET ("+job.rollbackFailures+
                    " cellules). Restaure une sauvegarde si necessaire.");
        }
    }
    private static void progress(Job job,ServerPlayerEntity player,int percent,String stage){
        int quarter=percent/25;
        if(quarter>job.lastProgress){
            job.lastProgress=quarter;
            reply(player,false,true,stage+" : "+percent+"% ("+job.pieceIndex+"/"+job.total+")");
        }
    }
    private static void reject(Job job,String reason){
        stop(job,false,"Export refuse : "+reason+". Monde inchange.");
    }
    private static void stop(Job job,boolean success,String reason){
        JOBS.remove(job.playerId,job);
        ServerPlayerEntity player=job.world.getServer().getPlayerManager().getPlayer(job.playerId);
        if(player!=null)reply(player,true,success,reason);
    }
    private static void reply(ServerPlayerEntity player,boolean finished,boolean success,String message){
        ServerPlayNetworking.send(player,new StudioExportStatusPayload(finished,success,message));
        if(finished)player.sendMessage(Text.literal("BrickCraft : "+message),false);
    }
}
