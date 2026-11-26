"""Run the actual transfer-finally and tile-retirement methods with isolated dependencies."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
model = root / "src/l1j/server/server/model/inventory"
def extract(file, start, end):
    source = (model / file).read_text(encoding="utf-8")
    a = source.index(start)
    return source[a:source.index(end, a)]
transfer = extract("CInventory.java", "    private L1ItemInstance tradeItemLocked(", "    private L1ItemInstance transferWithPermits(")
retirement = extract("L1GroundInventory.java", "    void finishGroundTransfer(", "    /** Ordinary stackable direct pickups")
harness = r'''
import java.util.logging.*;
public class TileAudit {
    static final class L1ItemInstance { }
    static final class CacheD { static boolean applying; static boolean isWarehouseApplying(){return applying;} }
    static class CInventory {
        boolean reject, fail, add;
        L1ItemInstance transferWithPermits(L1ItemInstance item,int count,CInventory inventory){
            if(fail) throw new IllegalStateException("transfer");
            if(reject) return null;
            if(add && inventory instanceof L1GroundInventory g)g._items.empty=false;
            return item;
        }
'''
middle = r'''
    }
    static class CUserInventory extends CInventory {
        boolean busy; int taken, released;
        boolean tryBeginEquipmentChange(){if(busy)return false;taken++;return true;}
        void endEquipmentChange(){released++;}
    }
    static class Items {boolean empty=true;boolean isEmpty(){return empty;}}
    static class L1World {
        static final L1World WORLD=new L1World();
        static L1World getInstance(){return WORLD;}
        L1GroundInventory current;int removed;boolean fail;
        L1GroundInventory findGroundInventory(int x,int y,short map){return current;}
        void removeVisibleObject(L1GroundInventory g){if(fail)throw new IllegalStateException("cleanup");if(current==g){current=null;removed++;}}
    }
    static class L1GroundInventory extends CInventory {
        final Items _items=new Items();
        static final Logger _log=Logger.getLogger("tile");
        static final boolean DEBUG_GROUND_ITEM_TIMER=false;
        int getX(){return 1;}int getY(){return 1;}short getMapId(){return 1;}int getId(){return -1;}
        static void debug(String a,L1ItemInstance b,String c){}
'''
checks = r'''
    }
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        L1GroundInventory._log.setLevel(Level.OFF);
        int cases=0;
        for(boolean applying:new boolean[]{false,true})for(boolean busy:new boolean[]{false,true})for(boolean reject:new boolean[]{false,true})for(boolean fail:new boolean[]{false,true})for(boolean populated:new boolean[]{false,true})for(boolean stale:new boolean[]{false,true})for(boolean cleanupFail:new boolean[]{false,true}){
            CacheD.applying=applying;CUserInventory src=new CUserInventory();src.busy=busy;src.reject=reject;src.fail=fail;src.add=true;
            L1GroundInventory dst=new L1GroundInventory();dst._items.empty=!populated;
            L1World w=L1World.WORLD;L1GroundInventory replacement=new L1GroundInventory();w.current=stale?replacement:dst;w.removed=0;w.fail=cleanupFail;
            boolean entered=applying||!busy;boolean success=entered&&!fail&&!reject;
            L1ItemInstance result=null;boolean threw=false;
            synchronized(dst){synchronized(src){try{result=((CInventory)src).tradeItemLocked(new L1ItemInstance(),1,dst);}catch(IllegalStateException e){check(e.getMessage().equals("transfer"));threw=true;}}}
            check(threw==(entered&&fail));check((result!=null)==success);
            check(src.taken==src.released);check(src.taken==(!applying&&!busy?1:0));
            boolean retired=!populated&&!success&&!stale&&!cleanupFail;
            check(w.removed==(retired?1:0));check(w.current==(stale?replacement:retired?null:dst));
            cases++;
        }
        System.out.println("PASS "+cases+" isolated Java 21 tile-retirement / gate / failure conditions");
    }
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "TileAudit.java"
    java.write_text(harness + transfer + middle + retirement + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", work, "TileAudit"], check=True, timeout=30)
