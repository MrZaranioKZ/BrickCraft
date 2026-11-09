package fr.brickcraft.client;

import fr.brickcraft.core.BrickKind;
import fr.brickcraft.core.BrickPiece;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Undoable construction model, independent from the GUI and Minecraft world.
 * A workspace is a bounded editor canvas, NOT a single block entity.
 */
public final class StudioAssembly {
    public static final int DEFAULT_GRID = 64;
    public static final int MAX_GRID = 256;
    public static final int MAX_TOP = 1280; // 256 Minecraft blocks = 1280 fifth-block units
    public static final int MAX_PIECES = 4096;
    private static final int HISTORY = 64;
    private int gridSize = DEFAULT_GRID;

    private final List<BrickPiece> pieces = new ArrayList<>();
    private final Deque<List<BrickPiece>> undo = new ArrayDeque<>();
    private final Deque<List<BrickPiece>> redo = new ArrayDeque<>();

    public int gridSize(){return gridSize;}
    public int maxTop(){return MAX_TOP;}
    public List<BrickPiece> pieces(){return List.copyOf(pieces);}
    public int size(){return pieces.size();}
    public boolean isEmpty(){return pieces.isEmpty();}
    public BrickPiece get(int index){return index >= 0 && index < pieces.size() ? pieces.get(index):null;}
    public boolean canUndo(){return !undo.isEmpty();}
    public boolean canRedo(){return !redo.isEmpty();}

    public boolean setGridSize(int size){
        if(size!=16 && size!=32 && size!=64 && size!=128 && size!=256) return false;
        for(BrickPiece p:pieces) if(p.x()<0 || p.z()<0 || p.x()+p.width()>size || p.z()+p.length()>size) return false;
        gridSize=size;
        return true;
    }
    public int nextGridSize(){return switch(gridSize){
        case 16 -> 32;case 32 -> 64;case 64 -> 128;case 128 -> 256;default -> 16;
    };}

    public int snapTop(int x,int z,BrickKind kind,int angle,int floor,int exclude) {
        return snapTop(x,z,kind,angle,0,0,floor,exclude);
    }
    public int snapTop(int x,int z,BrickKind kind,int angle,int pitch,int roll,int floor,int exclude){
        BrickPiece candidate=new BrickPiece(kind,0,x,0,z,Math.floorMod(angle,360)/90,
                Math.floorMod(angle,360),pitch,roll);
        return snapTop(candidate,floor,exclude);
    }
    public int snapTop(BrickPiece source,int floor,int exclude){
        int top=Math.max(0,floor);
        BrickPiece candidate=source.withPosition(source.x(),0,source.z());
        for(int i=0;i<pieces.size();i++){
            if(i==exclude)continue;
            BrickPiece other=pieces.get(i);
            if(candidate.horizontalOverlaps(other))top=Math.max(top,other.top());
        }
        return top;
    }

    public boolean canPlace(BrickPiece candidate,int exclude){
        if(candidate==null || !candidate.valid() || candidate.x()<0 || candidate.z()<0 ||
                candidate.y()<0 || candidate.x()+candidate.width()>gridSize ||
                candidate.z()+candidate.length()>gridSize || candidate.top()>MAX_TOP)return false;
        for(int i=0;i<pieces.size();i++) {
            if(i!=exclude){
                BrickPiece other=pieces.get(i);
                // Cheap bounding rejection before SAT for large sets.
                if(candidate.x()<other.x()+other.width() && candidate.x()+candidate.width()>other.x() &&
                        candidate.z()<other.z()+other.length() && candidate.z()+candidate.length()>other.z() &&
                        candidate.y()<other.top() && candidate.top()>other.y() && candidate.intersects(other))return false;
            }
        }
        return true;
    }
    public boolean add(BrickPiece p){
        if(pieces.size()>=MAX_PIECES || !canPlace(p,-1))return false;
        checkpoint();pieces.add(p);return true;
    }
    public boolean replace(int i,BrickPiece p){
        if(get(i)==null || !canPlace(p,i))return false;
        if(pieces.get(i).equals(p))return true;
        checkpoint();pieces.set(i,p);return true;
    }
    public boolean remove(int i){
        if(get(i)==null)return false;
        checkpoint();pieces.remove(i);return true;
    }
    public boolean clear(){
        if(pieces.isEmpty())return false;
        checkpoint();pieces.clear();return true;
    }
    /** Transactional project load. Do not alter the project if any part is invalid. */
    public boolean loadProject(int newGrid, List<BrickPiece> imported){
        if(imported==null || imported.size()>MAX_PIECES)return false;
        StudioAssembly test=new StudioAssembly();
        if(!test.setGridSize(newGrid))return false;
        for(BrickPiece p:imported){
            if(!test.canPlace(p,-1))return false;
            // A staging assembly has no undo history. Avoid 4096 list snapshots.
            test.pieces.add(p);
        }
        // Loading a different project starts a fresh history with no stale grid states.
        undo.clear();redo.clear();
        gridSize=newGrid;
        pieces.clear();pieces.addAll(imported);
        return true;
    }
    public boolean undo(){
        if(undo.isEmpty())return false;
        redo.push(List.copyOf(pieces));restore(undo.pop());return true;
    }
    public boolean redo(){
        if(redo.isEmpty())return false;
        undo.push(List.copyOf(pieces));restore(redo.pop());return true;
    }
    private void checkpoint(){
        undo.push(List.copyOf(pieces));
        while(undo.size()>HISTORY)undo.removeLast();
        redo.clear();
    }
    private void restore(List<BrickPiece> saved){pieces.clear();pieces.addAll(saved);}
}
