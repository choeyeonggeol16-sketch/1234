"""Run real private-store User methods with isolated inventory/socket/storage doubles.
Tests both wire paths, admission, reentrant gate and close. Not a JDBC test.
"""
from pathlib import Path
import tempfile
import subprocess

root=Path(__file__).resolve().parents[2]
src=root/"src/l1j/server/server/model/network/core/cs/packet"
u=(src/"User.java").read_text(encoding="utf-8")
start=u.index("    private static final Logger PRIVATE_STORE_LOG")
body=u[start:u.rindex("}")]
tx=(src/"PlayerShopTransactions.java").read_text(encoding="utf-8")
def extract(s,marker):
    start=s.index(marker)
    i=s.index("{",start)+1
    depth=1
    while depth:
        if s[i]=="{": depth+=1
        elif s[i]=="}": depth-=1
        i+=1
    return s[start:i]
reach=extract(tx,"static boolean canReach(")
transfer=extract(tx,"private static boolean canTransfer(")
java=r"""
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.*;

public class PrivateStoreTest {
    static class User {
""" + body + r"""
    }
    static class PlayerShopTransactions {
""" + reach + "\n" + transfer + r"""
        static void execute(L1PcInstance a,L1PcInstance b,boolean p,int n,long x,byte[] z){}
    }
    static final class CInventory {static final int MAX_AMOUNT=2000000000;}
    static final class CTrade {static boolean HasPendingTradeBlocker(L1PcInstance pc){return pc.pending;}}
    static final class L1ItemId {static final int ADENA=40308;}
    static final class L1SkillId {static final int SHAPE_CHANGE=1,POLY_RING_MASTER=2;}
    static final class ActionCodes {static final int ACTION_Shop=1,ACTION_Idle=2;}
    static final class Account {int Shop_open_count;}
    static class CUserSocket {
        L1PcInstance pc; Account account=new Account();
        L1PcInstance getActiveChar(){return pc;}
        Account getAccount(){return account;}
    }
    static class L1ItemInstance {
        final int id,template; int count=20,bless=0,enchant=0,attr=0;
        boolean equipped,stackable=true,identified=true;
        long delay;
        java.sql.Timestamp expiry;
        java.sql.Timestamp getEndTime(){return expiry;}
        Template item=new Template();
        L1ItemInstance(int i,int t){id=i;template=t;}
        int getId(){return id;} int getItemId(){return template;} int getCount(){return count;}
        int getBless(){return bless;} boolean isEquipped(){return equipped;}
        boolean isStackable(){return stackable;} boolean isIdentified(){return identified;}
        int getEnchantLevel(){return enchant;} int getAttrEnchantLevel(){return attr;}
        long getItemdelay3(){return delay;} Template getItem(){return item;}
    }
    static class Template {boolean tradable=true; boolean isTradable(){return tradable;} String getName(){return "item";}}
    static class Inventory {
        final L1PcInstance owner; final Map<Integer,L1ItemInstance> items=new HashMap<>();
        int depth; boolean busy,rune,adena=true; boolean readWithoutGate;
        Inventory(L1PcInstance owner){this.owner=owner;}
        boolean tryBeginEquipmentChange(){if(busy)return false;depth++;return true;}
        void endEquipmentChange(){if(--depth<0)throw new AssertionError("gate");}
        L1ItemInstance GetByServerID(int id){if(depth==0)readWithoutGate=true;return items.get(id);}
        boolean isEquippedAny(int id){return rune;}
        boolean HaveItemByClassId(int id,int n){return adena;}
    }
    static class L1DollInstance {int id; int getItemObjId(){return id;}}
    static class Companion {int id; int get_control_object_id(){return id;}}
    static class CompanionInstanceCache {static int protectedId;static boolean is_companion_oblivion(int id){return id!=protectedId;}}
    static class L1PcInstance {
        final CUserSocket socket=new CUserSocket(); final Inventory inv=new Inventory(this);
        final Map<Integer,Object> dolls=new HashMap<>();
        final int id; int map=800,x=1,y=1,sprite=10,상점변신,stamp=1,notices;
        String account; boolean dead,ghost,teleport,logout,pending,invis,fishing,shop,ready;
        Object trade; Companion companion; byte[] chat;
        ArrayList<DynamicShopItem> sells,buys;
        L1PcInstance(int id){this.id=id;account="a"+id;socket.pc=this;}
        int getId(){return id;} int getMapId(){return map;} int getX(){return x;} int getY(){return y;}
        boolean isDead(){return dead;} boolean isGhost(){return ghost;} boolean get_teleport(){return teleport;}
        boolean isLogoutStarted(){return logout;} Object GetTrade(){return trade;}
        boolean isInvisble(){return invis;} boolean isFishing(){return fishing;}
        void setFishing(boolean b){fishing=b;} void setFishingTime(int t){} void setFishingReady(boolean b){}
        boolean isPrivateShop(){return shop;} boolean isPrivateReady(){return ready;}
        int getCurrentSpriteId(){return sprite;} int getClassId(){return 10;}
        int getSkillEffectTimeSec(int i){return 10;}
        boolean hasSkillEffect(int i){return false;} void killSkillEffectTimer(int i){} void removeSkillEffect(int i){}
        String getAccountName(){return account;}
        int getSessionStamp(){return stamp;}
        boolean isItemTransferSession(CUserSocket s,int st){return !logout&&s==socket&&s.pc==this&&st==stamp;}
        Inventory getInventory(){return inv;} Companion get_companion(){return companion;}
        Map<Integer,Object> getDollList(){return dolls;} CUserSocket getNetConnection(){return socket;}
        void setShopChat(byte[] b){chat=b;} void setSellings(ArrayList<DynamicShopItem> l){sells=l;}
        void setPurchasings(ArrayList<DynamicShopItem> l){buys=l;} void setPrivateShop(boolean b){shop=b;}
        void setCurrentSprite(int i){sprite=i;} void curePoison(){}
    }
    static class CWorld {
        static final CWorld world=new CWorld(); final List<L1PcInstance> players=new ArrayList<>();
        static CWorld getInstance(){return world;} List<L1PcInstance> getAllPlayers3(){return players;}
    }
    static class DynamicShopItem {
        int objId,itemId,count,price,iden,enchant,attr; boolean isPurchase;
        static DynamicShopItem create(L1ItemInstance i,int c,int p,boolean b){
            DynamicShopItem d=new DynamicShopItem();d.objId=i.id;d.itemId=i.template;
            d.count=c;d.price=p;d.iden=i.identified?i.bless:-1;d.enchant=i.enchant;d.attr=i.attr;d.isPurchase=b;return d;
        }
    }
    static class DynamicShopStorage {
        static boolean reject;static int writes,deletes;
        static void replacePlayerListings(L1PcInstance pc,ArrayList<DynamicShopItem> s,ArrayList<DynamicShopItem> b){
            if(pc.inv.depth!=1)throw new AssertionError("publication outside gate");
            if(reject)throw new RejectedExecutionException();writes++;
        }
        static void deleteProcess(L1PcInstance pc){
            if(pc.inv.depth!=1)throw new AssertionError("close outside gate");
            if(reject)throw new RejectedExecutionException();deletes++;pc.sells=null;pc.buys=null;
        }
    }
    static class CS_ActionUi {
        static class ShopPayload {
            int[] entries;int count;byte[] chat;String polyNum;
            ShopPayload(int[] a,int n){entries=a;count=n;chat=new byte[0];polyNum="tradezone2";}
        }
    }
    static class L1PolyMorph {
        static void undoPoly(L1PcInstance pc){pc.sprite=10;}
        static void undoPolyPrivateShop(L1PcInstance pc){pc.sprite=10;}
    }
    static class FishingTimeController {
        static FishingTimeController getInstance(){return new FishingTimeController();}
        void removeMember(L1PcInstance pc){}
    }
    static class SC_ServerMessage {static void send(L1PcInstance pc,int id,String... s){pc.notices++;}}
    static class SC_SystemMessage {static void send(L1PcInstance pc,String s){pc.notices++;}}
    static class SC_ChatPacket {static void sendDirect(L1PcInstance a,L1PcInstance b,String s){a.notices++;}}
    static class SC_Disconnect {static void send(L1PcInstance pc){pc.notices++;}}
    static class SC_DoActionShop {
        static void send(L1PcInstance p,int i,int a,byte[] b){}
        static void broadcast(L1PcInstance p,int i,int a,byte[] b){}
    }
    static class SC_DoActionGFX {
        static void send(CUserSocket p,int i,int a){} static void broadcast(L1PcInstance p,int a){}
    }
    static class SC_ChangeShape {
        static void send(L1PcInstance p,int i,int a,int b){} static void broadcast(L1PcInstance p,int i,int a,int b){}
    }
    static class SC_CharVisualUpdate {
        static long pack(L1PcInstance p){return 0;}
        static void sendPacked(L1PcInstance p,long l){}static void broadcastPacked(L1PcInstance p,long l){}
    }
    static class SC_WorldPutObject {static void broadcastPc(L1PcInstance p){}}
    static class SC_PacketBox {static final int 상점개설횟수=1;static void send(L1PcInstance p,int a,int b){}}
    static int checks;
    static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static byte[] classic(int id,int price,int count) {
        return java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short)1).putInt(id).putInt(price).putInt(count).putShort((short)0).array();
    }
    static void open(L1PcInstance pc,int id,int p,int c){User.OnPrivateStorePacket(pc.socket,0,classic(id,p,c));}
    static void close(L1PcInstance pc){User.OnPrivateStorePacket(pc.socket,1,(byte[])null);}
    static void ui(L1PcInstance pc,int... entries){
        User.OnPrivateStorePacket(pc.socket,0,new CS_ActionUi.ShopPayload(entries,entries.length/4));
    }
    static void noOpen(L1PcInstance pc,String why){check(!pc.shop&&pc.inv.depth==0,why);}
    public static void main(String[] args) {
        L1PcInstance pc=new L1PcInstance(1),customer=new L1PcInstance(2);
        CWorld.world.players.add(pc);CWorld.world.players.add(customer);
        L1ItemInstance item=new L1ItemInstance(11,100);
        pc.inv.items.put(11,item);pc.inv.items.put(12,new L1ItemInstance(12,40308));
        pc.trade=new Object();open(pc,11,1,1);noOpen(pc,"trade");pc.trade=null;
        pc.pending=true;open(pc,11,1,1);noOpen(pc,"pending");pc.pending=false;
        pc.dead=true;open(pc,11,1,1);noOpen(pc,"dead");pc.dead=false;
        pc.inv.busy=true;open(pc,11,1,1);noOpen(pc,"busy");pc.inv.busy=false;
        open(pc,11,2000000000,2);noOpen(pc,"overflow");
        open(pc,12,1,1);noOpen(pc,"currency listing");
        open(pc,11,1,21);noOpen(pc,"overcount");
        Template template=item.item;item.item=null;
        open(pc,11,1,1);noOpen(pc,"classic missing template");
        byte[] buy=java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short)0).putShort((short)1).putInt(11).putInt(5).putInt(1).array();
        User.OnPrivateStorePacket(pc.socket,0,buy);noOpen(pc,"classic buy missing template");
        ui(pc,0x12,11,5,1);noOpen(pc,"UI missing template");item.item=template;
        pc.companion=new Companion();pc.companion.id=11;open(pc,11,1,1);noOpen(pc,"companion");pc.companion=null;
        DynamicShopStorage.reject=true;open(pc,11,1,1);noOpen(pc,"storage reject");DynamicShopStorage.reject=false;
        open(pc,11,5,2);
        check(pc.shop&&pc.sells.size()==1&&pc.sells.get(0).count==2&&!pc.inv.readWithoutGate&&pc.inv.depth==0,"classic");
        check(PlayerShopTransactions.canReach(customer,pc),"reachable");
        check(!PlayerShopTransactions.canReach(null,pc)&&!PlayerShopTransactions.canReach(customer,null),"null parties");
        customer.trade=new Object();check(!PlayerShopTransactions.canReach(customer,pc),"customer trading");customer.trade=null;
        pc.dead=true;check(!PlayerShopTransactions.canReach(customer,pc),"dead merchant");
        pc.map=1;close(pc);check(!pc.shop&&pc.sells==null&&pc.inv.depth==0,"close outside market/dead");
        pc.dead=false;pc.map=800;
        pc.inv.adena=false;ui(pc,0x12,11,5,1);noOpen(pc,"UI funds");pc.inv.adena=true;
        ui(pc,0x12,11,5,1,0x1a,11,6,1);noOpen(pc,"duplicate across sides");
        ui(pc,0x12,11,5,1);check(pc.shop&&pc.sprite==11483&&pc.inv.depth==0,"UI open");
        pc.ready=true;User.OnPrivateStorePacket(pc.socket,1,(CS_ActionUi.ShopPayload)null);
        check(pc.shop,"offline-ready UI close guard");pc.ready=false;
        DynamicShopStorage.reject=true;close(pc);check(pc.shop&&pc.inv.depth==0,"failed close keeps state");
        DynamicShopStorage.reject=false;close(pc);noOpen(pc,"close");
        ui(pc,0x1a,11,5,25);
        check(pc.shop&&pc.buys.get(0).count==25,"UI buy demand above example amount");
        DynamicShopItem d=pc.buys.get(0);check(PlayerShopTransactions.canTransfer(customer,item,d,1,1),"valid transfer");
        check(!PlayerShopTransactions.canTransfer(customer,item,d,0,1),"zero transfer");
        check(!PlayerShopTransactions.canTransfer(customer,item,d,-1,1),"negative transfer");
        check(!PlayerShopTransactions.canTransfer(customer,item,d,21,1),"insufficient actual item count");
        check(!PlayerShopTransactions.canTransfer(customer,item,null,1,1),"null listing");
        item.expiry=new java.sql.Timestamp(1);
        check(!PlayerShopTransactions.canTransfer(customer,item,d,1,1),"expired item cannot be sold");
        item.expiry=new java.sql.Timestamp(2);
        check(PlayerShopTransactions.canTransfer(customer,item,d,1,1),"unexpired item");
        item.expiry=null;
        item.enchant=1;check(!PlayerShopTransactions.canTransfer(customer,item,d,1,1),"changed item");item.enchant=0;
        close(pc);
        check(!pc.inv.readWithoutGate&&pc.inv.depth==0,"all listing reads gated");
        System.out.println("PASS private store admission and lifecycle ("+checks+" checks)");
    }
}
"""
with tempfile.TemporaryDirectory() as td:
    f=Path(td)/"PrivateStoreTest.java"
    f.write_text(java,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(f)],check=True)
    subprocess.run(["java","-cp",td,"PrivateStoreTest"],check=True)
