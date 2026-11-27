"""Execute production explicit offline-store command against lifecycle doubles."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[2]/"src/l1j/server/server"
s=(root/"model/network/core/cs/packet/CS_CharSelect.java").read_text(encoding="utf-8")
body=s[s.index("    public static void RequestOfflinePrivateStore("):s.rfind("}")]
assert 'case "무인상점" -> CS_CharSelect.RequestOfflinePrivateStore(pc);' in (root/"UserCommands.java").read_text(encoding="utf-8")
assert '"무인상점".equals(tcmd)' in (root/"model/network/core/cs/packet/CS_Craft.java").read_text(encoding="utf-8")
assert '".무인상점".equals(chatText.trim())' in (root/"model/network/core/cs/packet/CS_Chat.java").read_text(encoding="utf-8")
body=body.replace("l1j.server.server.model.inventory.CTrade","CTrade").replace("l1j.server.server.storage.CacheD","CacheD")
fixture=r"""
import java.util.*;
import java.util.logging.*;
public final class OfflineStoreCommandTest {
    static final Logger LOGGER=Logger.getLogger("test");
    static int checks;
    static void check(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
    enum CConnectionState {IN_GAME, AUTHENTICATED}
    static class Inventory {
        boolean busy,held;
        boolean tryBeginEquipmentChange(){if(busy||held)return false;held=true;return true;}
        void endEquipmentChange(){check(held,"release exact gate");held=false;}
    }
    static class CUserSocket {
        L1PcInstance pc;
        boolean ready=true,connected=true;
        CConnectionState state=CConnectionState.IN_GAME;
        void clearGameSessionReady(){ready=false;}
        void setActiveChar(L1PcInstance p){pc=p;}
        void setConnectionState(CConnectionState s){state=s;}
        void close(){connected=false;}
    }
    static class L1PcInstance {
        int id,map=800,online=1,notices,save,saveItems,cleanup,attendance;
        boolean shop=true,dead,ghost,teleport,offline,ready,trade,failSave,failAttendance;
        String account="a";
        final Inventory inv=new Inventory();
        CUserSocket client=new CUserSocket();
        L1PcInstance(int id){this.id=id;client.pc=this;}
        int getId(){return id;}
        int getMapId(){return map;}
        int getClanid(){return 0;}
        String getAccountName(){return account;}
        boolean isPrivateShop(){return shop;}
        boolean isDead(){return dead;}
        boolean isGhost(){return ghost;}
        boolean get_teleport(){return teleport;}
        boolean is무인상점(){return offline;}
        Object GetTrade(){return trade?this:null;}
        CUserSocket getNetConnection(){return client;}
        int getSessionStamp(){return 1;}
        boolean isItemTransferSession(CUserSocket c,int stamp){return c==client&&c.connected&&c.pc==this;}
        Inventory getInventory(){return inv;}
        void sendPackets(String s){notices++;}
        int getOnlineStatus(){return online;}
        void setOnlineStatus(int s){online=s;}
        void savePrivateStoreAttendance(){if(failAttendance)throw new IllegalStateException();attendance++;}
        void save(){if(failSave)throw new IllegalStateException();check(inv.held,"save under gate");save++;}
        void saveInventory(){check(inv.held,"items save under gate");saveItems++;}
        void set무인상점(boolean b){offline=b;}
        void setPrivateReady(boolean b){ready=b;}
        void releasePrivateStoreSessionResources(){check(client!=null&&inv.held&&offline,"cleanup before detach under gate");cleanup++;}
        void setNetConnection(CUserSocket c){client=c;}
    }
    static class CTrade {static boolean HasPendingTradeBlocker(L1PcInstance p){return false;}}
    static class CacheD {static final CacheD I=new CacheD();boolean pending;static CacheD getInstance(){return I;}boolean hasPendingOwnerApply(int i){return pending;}}
    static class L1Clan {void updateClanMemberOnline(L1PcInstance p){}}
    static class CWorld {
        static final CWorld I=new CWorld();final List<L1PcInstance> players=new ArrayList<>();
        static CWorld getInstance(){return I;}boolean hasOtherPrivateShop(L1PcInstance pc) {
            return players.stream().anyMatch(other -> other.getId() != pc.getId() && other.isPrivateShop()
                    && pc.getAccountName().equalsIgnoreCase(other.getAccountName()));
        }
        L1Clan getClan(int id){return null;}
    }
    static class SC_Unknown2 {static int sent;static void send(CUserSocket c,int v){check(c.pc==null&&c.state==CConnectionState.AUTHENTICATED&&!c.ready,"selection publication order");sent++;}}
""" + body + r"""
    public static void main(String[] a){
        L1PcInstance p=new L1PcInstance(1);
        CWorld.I.players.add(p);
        CUserSocket c=p.client;
        p.shop=false;RequestOfflinePrivateStore(p);check(!p.offline&&p.notices==1,"no store");
        p.shop=true;p.map=4;RequestOfflinePrivateStore(p);check(!p.offline,"wrong map");
        p.map=800;p.inv.busy=true;RequestOfflinePrivateStore(p);check(!p.offline,"busy receipt");
        p.inv.busy=false;CacheD.I.pending=true;RequestOfflinePrivateStore(p);check(!p.offline&&!p.inv.held,"pending owner replay");
        CacheD.I.pending=false;
        L1PcInstance other=new L1PcInstance(2);CWorld.I.players.add(other);
        RequestOfflinePrivateStore(p);check(!p.offline,"one shop per account");
        CWorld.I.players.remove(other);
        p.failAttendance=true;RequestOfflinePrivateStore(p);check(!p.offline&&p.client==c&&!p.inv.held,"attendance reject");
        p.failAttendance=false;p.failSave=true;RequestOfflinePrivateStore(p);check(!p.offline&&p.online==1&&p.client==c,"save reject");
        p.failSave=false;RequestOfflinePrivateStore(p);
        check(p.offline&&p.shop&&p.online==0&&p.client==null&&p.cleanup==1&&p.saveItems==1&&!p.inv.held,"merchant retained, session released");
        check(c.pc==null&&c.state==CConnectionState.AUTHENTICATED&&SC_Unknown2.sent==1,"character selection");
        RequestOfflinePrivateStore(p);check(p.cleanup==1&&SC_Unknown2.sent==1,"duplicate command no-op");
        System.out.println("PASS offline-store command lifecycle ("+checks+" checks)");
    }
}
"""
with tempfile.TemporaryDirectory() as td:
    p=Path(td)/"OfflineStoreCommandTest.java"
    p.write_text(fixture,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(p)],check=True)
    subprocess.run(["java","-cp",td,"OfflineStoreCommandTest"],check=True)
