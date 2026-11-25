"""Exercise the actual ground timer method with deterministic tile-lock races."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "src/l1j/server/server/model/Instance/L1ItemInstance.java").read_text(encoding="utf-8")
a = source.index("    public void Delete() {", source.index("public void TimerExpired("))
b = source.index("\tprivate int nextGroundDeleteTimerGeneration()", a)
methods = source[a:b]
harness = r'''
import java.util.*;
import java.util.concurrent.*;
public class GroundAudit {
    static class Config { static String ALT_ITEM_DELETION_TYPE="std"; static int ALT_ITEM_DELETION_RANGE=10; }
    static class L1HouseLocation { static boolean house; static boolean isInHouse(int x,int y,short m){return house;} }
    static class L1GroundInventory { GroundAudit item; int deleted; GroundAudit GetByServerID(int id){return item;} void DeleteItemAmount(GroundAudit i){if(item==i){deleted++;item=null;}} }
    static class L1World {
        static volatile L1World current; L1GroundInventory ground; GroundAudit object; boolean viewer; volatile CountDownLatch lookup;
        static L1World getInstance(){return current;}
        L1GroundInventory findGroundInventory(int x,int y,short m){var latch=lookup;if(latch!=null)latch.countDown();return ground;}
        GroundAudit findObject(int id){return object;}
        List<Integer> getVisiblePlayer(GroundAudit i,int radius){return viewer?List.of(1):List.of();}
    }
    static final int TIMER_GROUND_DELETE=1;
    volatile int _groundDeleteTimerGeneration=5; int x=10,y=20,id=100,template=1; short map=1; boolean pending,unsettled; int scheduled;
    int getX(){return x;} int getY(){return y;} short getMapId(){return map;} int getId(){return id;} int getItemId(){return template;}
    boolean isGroundPickupPending(){return pending;} boolean isGroundDropUnsettled(){return unsettled;}
    void AddTimer(int t,long d){_groundDeleteTimerGeneration++;scheduled++;} void CancelTimer(int t){_groundDeleteTimerGeneration++;} long groundDeleteDelayMs(){return 60000;}
'''
checks = r'''
    static int tests;
    static void check(boolean b){if(!b)throw new AssertionError("case "+tests);}
    static GroundAudit setup(){var i=new GroundAudit();var w=new L1World();var g=new L1GroundInventory();w.ground=g;w.object=i;g.item=i;L1World.current=w;L1HouseLocation.house=false;Config.ALT_ITEM_DELETION_TYPE="std";return i;}
    public static void main(String[] args)throws Exception{
        for(int mode=0;mode<8;mode++){
            var i=setup();var w=L1World.current;var g=w.ground;w.lookup=new CountDownLatch(1);
            if(mode==6){Config.ALT_ITEM_DELETION_TYPE="auto";w.viewer=true;}
            Thread t=new Thread(()->i.deleteGroundItem(5));
            synchronized(g){t.start();check(w.lookup.await(2,TimeUnit.SECONDS));switch(mode){case 0->i.CancelTimer(1);case 1->i.AddTimer(1,60000);case 2->i.x++;case 3->g.item=new GroundAudit();case 4->w.ground=new L1GroundInventory();case 5->L1World.current=new L1World();case 6->i.AddTimer(1,60000);case 7->w.object=new GroundAudit();}}
            t.join(2000);check(!t.isAlive());check(g.deleted==0);check(i.scheduled==(mode==1||mode==6?1:0));tests++;
        }
        for(int mode=0;mode<8;mode++){
            var i=setup();var w=L1World.current;var g=w.ground;
            switch(mode){case 1->i.pending=true;case 2->i.unsettled=true;case 3->{Config.ALT_ITEM_DELETION_TYPE="auto";w.viewer=true;}case 4->{Config.ALT_ITEM_DELETION_TYPE="auto";L1HouseLocation.house=true;}case 5->i.template=40515;case 6->i._groundDeleteTimerGeneration=6;case 7->i.pending=true;}
            if(mode==7)i.Delete();else i.deleteGroundItem(5);
            check(g.deleted==(mode==0?1:0));check(i.scheduled==(mode==1||mode==2||mode==3||mode==7?1:0));tests++;
        }
        System.out.println("PASS "+tests+" ground timer cases");
    }
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "GroundAudit.java"
    java.write_text(harness + methods + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-cp", work, "GroundAudit"], check=True, timeout=30)
