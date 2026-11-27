"""Run both private-store decoders and the real controller transition with controlled admission.
The fixture controls the existing FIFO, inventory availability, wire output and persistence.
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
model=root/"src/l1j/server/server/model"
actor=(model/"Instance/L1PcInstance.java").read_text(encoding="utf-8")
actor_shop=actor[actor.index("    public PlayerPrivateStore.State privateStoreState()"):
                 actor.index("    /** Memory half of a receipt-committed private-shop operation.")].replace("@Override ", "")
store=(model/"shop/PlayerPrivateStore.java").read_text(encoding="utf-8")
store=store[store.index("public final class PlayerPrivateStore"):].replace(
    "public final class PlayerPrivateStore", "static final class PlayerPrivateStore", 1)
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
transfer=extract(tx,"static boolean canTransfer(") + "\n" + extract(tx,"private static boolean matchesListing(") + "\n" + extract(tx,"private static DynamicShopItem findListing(")
action=(model/"character/action/CPrivateStoreAction.java").read_text(encoding="utf-8")
action=action[action.index("final class CPrivateStoreAction"):].replace(
    "final class CPrivateStoreAction","static final class CPrivateStoreAction",1)
controller=(model/"character/action/CCreatureController.java").read_text(encoding="utf-8")
action_types=controller[controller.index("    public static final byte ACTION_NONE"):
                        controller.index("    public static final int FLAG_AUTO_ATTACK")]
kind_start=controller.index("    private static final int EXEC_PRIVATE_STORE")
action_kind=controller[kind_start:controller.index("\n",kind_start)]
action_entries="\n".join(extract(controller, name) for name in (
    "public boolean StartPrivateStore(", "public boolean ClosePrivateStore(",
    "private boolean submitPrivateStore(", "void OnPrivateStoreOpened("))
action_dispatch=controller[controller.index("        case EXEC_PRIVATE_STORE: {"):
                           controller.index("        case EXEC_TIMER:", controller.index("        case EXEC_PRIVATE_STORE: {"))]
movement_policy=controller[controller.index("        final L1PcInstance pc = actor;",
                                          controller.index("    private void Submit(")):
                            controller.index("        // Validate wand usage only inside the serialized action executor.")]
classic=(src/"CS_PersonalShop.java").read_text(encoding="utf-8")
classic=classic[classic.index("public final class CS_PersonalShop"):].replace(
    "public final class CS_PersonalShop","static final class CS_PersonalShop",1)
action_ui=(src/"CS_ActionUi.java").read_text(encoding="utf-8")
ui_payload=extract(action_ui,"public static final class ShopPayload")
ui_adapter=action_ui[action_ui.index("            case NEWSHOP:"):
                     action_ui.index("            case EVENT_TELEPORT:",action_ui.index("            case NEWSHOP:"))]
packet_map=(model/"network/packet/UserPacketCallbackMap.java").read_text(encoding="utf-8")
ui_decode=packet_map[packet_map.index("        case ACTION_NEWSHOP: {"):
                     packet_map.index("        case ACTION_BLESSED_TELEPORT: {")]
ui_readers="\n".join(extract(packet_map, name) for name in (
    "private static long readActionVarInt(", "private static String readActionFixedEucKr("))
java=r"""
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.*;

public class PrivateStoreTest {
    static class User {
""" + body + r"""
    }
""" + classic + action + r"""
    static class CCreatureController {
""" + action_types + action_entries + action_kind + r"""
        private final L1PcInstance actor;
        private int actorEpoch=2;
        private byte currentType, pendingType;
        private CPrivateStoreAction privateStoreAction;
        final ArrayDeque<Runnable> queue=new ArrayDeque<>();
        boolean defer,reject;
        int moves,attackAttempts,skillAttempts,pickups,actionEnd,resumeTarget;
        CCreatureController(L1PcInstance actor){this.actor=actor;}
        byte currentActionType(){return currentType;}
        void ClearAutoResume(){resumeTarget=0;}
        void ClearPending(){pendingType=ACTION_NONE;}
        void EndCurrent(boolean finished,boolean pendingAllowed){
            check(!finished&&!pendingAllowed,"shop quiesces without running pending combat");
            currentType=ACTION_NONE;actionEnd++;
        }
        boolean executeOrPublish(int kind,int type,int a0,int a1,int a2,int a3,long x,long y,int flags,
                Object o0,Object o1,int stamp,int epoch){
            if(reject)return false;
            Runnable run=()->{
                if(stamp!=actor.getSessionStamp()||epoch!=actorEpoch)return;
                switch(kind){
""" + action_dispatch + r"""
                default:throw new AssertionError("unexpected controller action");
                }
            };
            if(defer)queue.add(run);else run.run();
            return true;
        }
        void move(byte type,int flags){
""" + movement_policy + r"""
            if(type==ACTION_MOVE)moves++;
            if(type==ACTION_ATTACK)attackAttempts++;
            if(type==ACTION_SKILL)skillAttempts++;
            if(type==ACTION_GET_ITEM)pickups++;
        }
    }
    static class CGeneralAction {static final int TYPE_WAND=1;}
    static class GameUtils {static boolean isNonAction(L1PcInstance p){return p.heldByControl;}}
    static class PlayerShopTransactions {
""" + reach + "\n" + transfer + r"""
        static void execute(L1PcInstance a,L1PcInstance b,boolean p,int n,long x,byte[] z){}
    }
    static final class NoTradable {
        static int blocked;
        static boolean blocksPlayerTransfer(boolean gm,int template){return !gm && template==blocked;}
    }
    static final class CInventory {static final int MAX_AMOUNT=2000000000;}
    static final class CTrade {static boolean HasPendingTradeBlocker(L1PcInstance pc){return pc.pending;}}
    static final class L1ItemId {static final int ADENA=40308;}
    static final class L1SkillId {static final int SHAPE_CHANGE=1,POLY_RING_MASTER=2;}
    static final class ActionCodes {static final int ACTION_Shop=1,ACTION_Idle=2;}
    static final class Account {int Shop_open_count;}
    static class CUserSocket {
        L1PcInstance pc; Account account=new Account(); boolean ready=true;
        L1PcInstance getActiveChar(){return pc;}
        Account getAccount(){return account;}
        boolean ownsReadyGameSession(){return ready&&pc!=null;}
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
""" + store + r"""
    static class L1PcInstance {
        final CUserSocket socket=new CUserSocket(); final Inventory inv=new Inventory(this);
        final CCreatureController controller=new CCreatureController(this);
        final Map<Integer,Object> dolls=new HashMap<>();
        final int id; int map=800,x=1,y=1,sprite=10,상점변신,stamp=1,notices;
        int shopActions, shopBroadcasts, shapes, idleActions, undoPoly, curePoison, backsteps;
        String account; boolean gm; boolean isGm(){return gm;}
        boolean dead,ghost,teleport,logout,pending,invis,fishing,nonAction,heldByControl,failPresentation;
        Object trade; Companion companion;
        private static final AtomicReferenceFieldUpdater<L1PcInstance, PlayerPrivateStore> PRIVATE_STORE =
                AtomicReferenceFieldUpdater.newUpdater(L1PcInstance.class, PlayerPrivateStore.class, "privateStore");
        private volatile PlayerPrivateStore privateStore;
        L1PcInstance(int id){this.id=id;account="a"+id;socket.pc=this;}
        int getId(){return id;} Object mapRef=CWorld.world; Object getMap(){return mapRef;} int getMapId(){return map;} int getX(){return x;} int getY(){return y;}
        boolean isDead(){return dead;} boolean isGhost(){return ghost;} boolean get_teleport(){return teleport;}
        boolean is_non_action(){return nonAction;}
        CCreatureController getCreatureController(){return controller;}
        boolean isLogoutStarted(){return logout;} Object GetTrade(){return trade;}
        boolean isInvisble(){return invis;} boolean isFishing(){return fishing;}
        void setFishing(boolean b){fishing=b;} void setFishingTime(int t){} void setFishingReady(boolean b){}
        int getCurrentSpriteId(){return sprite;} int getClassId(){return 10;}
        int getSkillEffectTimeSec(int i){return 10;}
        boolean hasSkillEffect(int i){return false;} void killSkillEffectTimer(int i){} void removeSkillEffect(int i){}
        String getAccountName(){return account;}
        int getSessionStamp(){return stamp;}
        boolean isItemTransferSession(CUserSocket s,int st){return !logout&&s==socket&&s.pc==this&&st==stamp;}
        Inventory getInventory(){return inv;} Companion get_companion(){return companion;}
        Map<Integer,Object> getDollList(){return dolls;} CUserSocket getNetConnection(){return socket;}
""" + actor_shop + r"""
        void setCurrentSprite(int i){sprite=i;} void curePoison(){curePoison++;}
    }
    static class CWorld {
        static final CWorld world=new CWorld(); final List<L1PcInstance> players=new ArrayList<>();
        static CWorld getInstance(){return world;} boolean hasOtherPrivateShop(L1PcInstance pc) {
            return players.stream().anyMatch(other -> other.getId() != pc.getId() && other.isPrivateShop()
                    && pc.getAccountName().equalsIgnoreCase(other.getAccountName()));
        }
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
        static void enqueuePlayerListingRemoval(int id){
            L1PcInstance pc = CWorld.world.players.stream().filter(player -> player.id == id).findFirst().orElseThrow();
            if(pc.inv.depth!=1)throw new AssertionError("close outside gate");
            if(!pc.isPrivateShop())throw new AssertionError("removal enqueued for a closed shop");
            if(reject)throw new RejectedExecutionException();deletes++;
        }
    }
    static class CS_ActionUi {
        static final int NEWSHOP=0x0331;
        static final int[] EMPTY_SHOP_ENTRIES=new int[0];
""" + ui_payload + r"""
        static void shop(CUserSocket client,int type,int p1,Object o0){
            if(client==null||client.pc==null||client.pc.isGhost())return;
            switch(type){
""" + ui_adapter + r"""
            }
        }
    }
    record Disassemble(byte[] bytes){byte readByte(int at){return bytes[at];}}
    static class ActionUiReader {
        static final int ACTION_NEWSHOP=0x0331;
        static final byte[] EMPTY_BYTES=new byte[0];
        static final ThreadLocal<byte[]> FIXED_STRING_SCRATCH=ThreadLocal.withInitial(()->new byte[256]);
        static final java.nio.charset.Charset EUC_KR=java.nio.charset.Charset.forName("EUC-KR");
""" + ui_readers + r"""
        static boolean decode(CUserSocket client,byte[] payload){
            Disassemble buffer=new Disassemble(payload);
            int pos=3,p1=0,type=ACTION_NEWSHOP;
            final int limit=payload.length;
            Object o0=null;
            switch(type){
""" + ui_decode + r"""
            }
            CS_ActionUi.shop(client,type,p1,o0);
            return true;
        }
    }
    static class L1PolyMorph {
        static void undoPoly(L1PcInstance pc){pc.sprite=10;pc.undoPoly++;}
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
        static void send(L1PcInstance p,int i,int a,byte[] b){
            check(p.isPrivateShop() && p.getShopChat()==b,"shop action follows published chat");
            if(p.failPresentation)throw new IllegalStateException("test presentation failed");
            p.shopActions++;
        }
        static void broadcast(L1PcInstance p,int i,int a,byte[] b){p.shopBroadcasts++;}
    }
    static class SC_DoActionGFX {
        static void send(CUserSocket p,int i,int a){p.pc.idleActions++;} static void broadcast(L1PcInstance p,int a){}
    }
    static class SC_ChangeShape {
        static void send(L1PcInstance p,int i,int a,int b){p.shapes++;} static void broadcast(L1PcInstance p,int i,int a,int b){}
    }
    static class SC_CharVisualUpdate {
        static long pack(L1PcInstance p){return 0;}
        static void sendPacked(L1PcInstance p,long l){}static void broadcastPacked(L1PcInstance p,long l){}
    }
    static class SC_WorldPutObject {static void broadcastPc(L1PcInstance p){}}
    static class SC_PacketBox {
        static final int 상점개설횟수=1,유저빽스탭=2;
        static void send(L1PcInstance p,int a,int b){}
        static void send(L1PcInstance p,int a,L1PcInstance target){if(a==유저빽스탭&&p==target)p.backsteps++;}
    }
    static int checks;
    static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static byte[] classic(int id,int price,int count) {
        return java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short)1).putInt(id).putInt(price).putInt(count).putShort((short)0).array();
    }
    static void open(L1PcInstance pc,int id,int p,int c){CS_PersonalShop.handle(pc.socket,0,classic(id,p,c));}
    static void close(L1PcInstance pc){CS_PersonalShop.handle(pc.socket,1,null);}
    static void closeUi(L1PcInstance pc){ActionUiReader.decode(pc.socket,new byte[]{0,0,0,1,0,0,1});}
    static void ui(L1PcInstance pc,int... entries){
        var payload=new java.io.ByteArrayOutputStream();
        payload.writeBytes(new byte[3]);
        payload.write(entries.length/4+1);
        payload.writeBytes(new byte[3]);
        for(int i=0;i<entries.length;i+=4){
            var entry=new java.io.ByteArrayOutputStream();
            for(int field=1;field<=3;field++){
                entry.write(field<<3);
                int value=entries[i+field];
                while((value&~0x7F)!=0){entry.write((value&0x7F)|0x80);value>>>=7;}
                entry.write(value);
            }
            payload.write(entries[i]);
            payload.write(entry.size());
            payload.writeBytes(entry.toByteArray());
        }
        payload.writeBytes(new byte[]{0x22,1,'a',0x2a,10});
        payload.writeBytes("tradezone2".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        ActionUiReader.decode(pc.socket,payload.toByteArray());
    }
    static void noOpen(L1PcInstance pc,String why){check(!pc.isPrivateShop()&&pc.inv.depth==0,why);}
    static void checkController() {
        var pc=new L1PcInstance(3);
        CWorld.world.players.add(pc);
        var item=new L1ItemInstance(11,100);
        pc.inv.items.put(11,item);
        var controller=pc.controller;
        controller.reject=true;
        open(pc,11,2,1);
        noOpen(pc,"full controller FIFO does not publish");
        check(pc.notices==1&&pc.shopActions==0&&controller.privateStoreAction==null,
                "submit rejection sends retry without visuals or action holder");
        controller.reject=false;
        pc.socket.ready=false;
        ui(pc,0x12,11,6,1);
        noOpen(pc,"unready session cannot start");
        pc.socket.ready=true;
        pc.nonAction=true;
        ui(pc,0x12,11,6,1);
        noOpen(pc,"native non-action state refuses start");
        pc.nonAction=false;
        pc.heldByControl=true;
        open(pc,11,2,1);
        noOpen(pc,"active control state refuses start");
        pc.heldByControl=false;
        controller.currentType=CCreatureController.ACTION_SKILL;
        ui(pc,0x12,11,3,1);
        noOpen(pc,"active cast refuses start");
        check(controller.currentType==CCreatureController.ACTION_SKILL&&controller.actionEnd==0&&pc.shopActions==0,
                "refusal preserves active cast and presentation");

        controller.currentType=CCreatureController.ACTION_MOVE;
        controller.pendingType=CCreatureController.ACTION_ATTACK;
        controller.resumeTarget=99;
        controller.defer=true;
        open(pc,11,4,1);
        check(controller.queue.size()==1&&!pc.isPrivateShop()&&pc.inv.depth==0,
                "decoded packet waits in the action queue without holding inventory");
        item.equipped=true;
        controller.queue.remove().run();
        noOpen(pc,"queued request revalidates equipped flag");
        check(controller.currentType==CCreatureController.ACTION_MOVE&&controller.resumeTarget==99
                &&controller.pendingType==CCreatureController.ACTION_ATTACK&&pc.shopActions==0,
                "rejection at execution preserves existing movement and pending state");
        item.equipped=false;

        open(pc,11,4,1);
        pc.socket.ready=false;
        controller.queue.remove().run();
        noOpen(pc,"descheduled session cannot publish at execution");
        pc.socket.ready=true;
        open(pc,11,4,1);
        pc.stamp++;
        controller.queue.remove().run();
        noOpen(pc,"queued action cannot follow a replaced session epoch");
        open(pc,11,4,1);
        ui(pc,0x12,11,9,2);
        check(controller.queue.size()==2&&pc.shopActions==0,"distinct requests retained in FIFO");
        controller.queue.remove().run();
        var reusable=controller.privateStoreAction;
        var classic=pc.privateStoreState();
        check(reusable!=null&&classic.open()&&classic.sellings().get(0).price==4&&classic.sellings().get(0).count==1
                &&controller.currentType==CCreatureController.ACTION_NONE&&controller.pendingType==CCreatureController.ACTION_NONE
                &&controller.resumeTarget==0&&controller.actionEnd==1,"successful open ends current and pending movement");
        controller.queue.remove().run();
        var modern=pc.privateStoreState();
        check(modern.generation()==classic.generation()+1&&modern.sellings()!=classic.sellings()
                &&modern.sellings().get(0).price==9&&modern.sellings().get(0).count==2
                &&classic.sellings().get(0).price==4&&pc.shopActions==2&&pc.sprite==11483&&controller.privateStoreAction==reusable,
                "second wire request gets only its own callback and new order state");
        controller.move(CCreatureController.ACTION_MOVE,0);
        controller.move(CCreatureController.ACTION_ATTACK,0);
        controller.move(CCreatureController.ACTION_SKILL,0);
        controller.move(CCreatureController.ACTION_GET_ITEM,0);
        check(controller.moves==0&&controller.attackAttempts==0&&controller.skillAttempts==0&&controller.pickups==0
                &&pc.backsteps==1,"open store blocks movement, attacks, skills and pickups and resynchronizes walking");
        closeUi(pc);
        check(controller.queue.size()==1&&pc.isPrivateShop(),"queued close keeps state until execution");
        controller.queue.remove().run();
        controller.move(CCreatureController.ACTION_MOVE,0);
        check(!pc.isPrivateShop()&&controller.moves==1,"movement resumes after actual close");

        controller.currentType=CCreatureController.ACTION_MOVE;
        pc.failPresentation=true;
        ui(pc,0x12,11,9,2);
        boolean failed=false;
        try{controller.queue.remove().run();}catch(IllegalStateException expected){failed=true;}
        pc.failPresentation=false;
        check(failed&&pc.isPrivateShop()&&pc.inv.depth==0&&controller.currentType==CCreatureController.ACTION_NONE,
                "published shop still ends movement and releases its gate on presentation failure");
        close(pc);
        controller.queue.remove().run();
        noOpen(pc,"controller fixture finished with shop closed");
        check(controller.queue.isEmpty()&&!pc.inv.readWithoutGate,"no queued requests and inventory reads were gated");
    }

    static void checkUiLimits() {
        var pc=new L1PcInstance(4);
        CWorld.world.players.add(pc);
        var controller=pc.controller;
        controller.defer=true;
        int maximum=PlayerPrivateStore.MAX_ITEMS_PER_SIDE*2;
        int[] entries=new int[maximum*4];
        for(int i=0;i<maximum;i++){
            int id=20+i;
            pc.inv.items.put(id,new L1ItemInstance(id,100));
            entries[i*4]=i<maximum/2?0x12:0x1a;
            entries[i*4+1]=id;
            entries[i*4+2]=3;
            entries[i*4+3]=1;
        }
        ui(pc,entries);
        check(controller.queue.size()==1,"legal full 7/7 action-UI listing reaches the controller");
        controller.queue.remove().run();
        check(pc.getSellings().size()==maximum/2&&pc.getPurchasings().size()==maximum/2,
                "legal protocol limit keeps all lists");
        close(pc);
        controller.queue.remove().run();
        int[] excess=Arrays.copyOf(entries,entries.length+4);
        System.arraycopy(entries,0,excess,entries.length,4);
        ui(pc,excess);
        check(controller.queue.isEmpty()&&!pc.isPrivateShop(),"over-limit UI packet rejected before action allocation");
    }

    public static void main(String[] args) {
        L1PcInstance pc=new L1PcInstance(1),customer=new L1PcInstance(2);
        CWorld.world.players.add(pc);CWorld.world.players.add(customer);
        L1ItemInstance item=new L1ItemInstance(11,100);
        pc.inv.items.put(11,item);pc.inv.items.put(12,new L1ItemInstance(12,40308));
        NoTradable.blocked=100;
        open(pc,11,1,1);noOpen(pc,"blocked classic sell");
        ui(pc,0x12,11,5,1);noOpen(pc,"blocked action sell");
        ui(pc,0x1a,11,5,1);noOpen(pc,"blocked action buy");
        pc.gm=true;open(pc,11,1,1);check(pc.isPrivateShop(),"GM operator-list exception");close(pc);
        pc.gm=false;NoTradable.blocked=0;
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
        NoTradable.blocked=100;
        User.OnPrivateStorePacket(pc.socket,0,buy);noOpen(pc,"blocked classic buy");
        pc.gm=true;template.tradable=false;
        User.OnPrivateStorePacket(pc.socket,0,buy);noOpen(pc,"GM still subject to template rule");
        pc.gm=false;template.tradable=true;NoTradable.blocked=0;
        pc.companion=new Companion();pc.companion.id=11;open(pc,11,1,1);noOpen(pc,"companion");pc.companion=null;
        int actions=pc.shopActions, shapes=pc.shapes;
        DynamicShopStorage.reject=true;open(pc,11,1,1);noOpen(pc,"storage reject");DynamicShopStorage.reject=false;
        check(pc.shopActions==actions&&pc.shapes==shapes&&pc.sprite==10,"storage refusal has no presentation effects");
        open(pc,11,5,2);
        check(pc.isPrivateShop()&&pc.getSellings().size()==1&&pc.getSellings().get(0).count==2
                &&!pc.inv.readWithoutGate&&pc.inv.depth==0,"classic");
        check(pc.shopActions==actions+1&&pc.shopBroadcasts==pc.shopActions&&pc.shapes==shapes+1,
                "classic open presents once after admission");
        check(PlayerShopTransactions.canReach(customer,pc),"reachable");
        check(!PlayerShopTransactions.canReach(null,pc)&&!PlayerShopTransactions.canReach(customer,null),"null parties");
        customer.trade=new Object();check(!PlayerShopTransactions.canReach(customer,pc),"customer trading");customer.trade=null;
        pc.dead=true;check(!PlayerShopTransactions.canReach(customer,pc),"dead merchant");
        pc.map=1;close(pc);check(!pc.isPrivateShop()&&pc.getSellings()==null&&pc.inv.depth==0,"close outside market/dead");
        pc.dead=false;pc.map=800;
        pc.inv.adena=false;ui(pc,0x12,11,5,1);noOpen(pc,"UI funds");pc.inv.adena=true;
        ui(pc,0x12,11,5,1,0x1a,11,6,1);noOpen(pc,"duplicate across sides");
        ui(pc,0x12,11,5,1);check(pc.isPrivateShop()&&pc.sprite==11483&&pc.inv.depth==0,"UI open");
        int openedVersion = pc.getPrivateShopVersion();
        var published = pc.getSellings();
        int opensBefore = DynamicShopStorage.writes, visualsBefore = pc.shopActions, morphsBefore = pc.undoPoly;
        DynamicShopStorage.reject=true;
        ui(pc,0x12,11,6,1);
        check(pc.isPrivateShop() && pc.getSellings() == published && pc.getPrivateShopVersion() == openedVersion,
                "rejected re-open retains old publication");
        check(DynamicShopStorage.writes==opensBefore&&pc.shopActions==visualsBefore&&pc.undoPoly==morphsBefore,
                "rejected replacement sends no new action and preserves existing morph");
        DynamicShopStorage.reject=false;
        ui(pc,0x12,11,7,2);
        check(pc.getPrivateShopVersion()==openedVersion+1&&pc.getSellings()!=published
                &&published.get(0).price==5&&published.get(0).count==1
                &&pc.getSellings().get(0).price==7&&pc.getSellings().get(0).count==2
                && DynamicShopStorage.writes==opensBefore+1&&pc.shopActions==visualsBefore+1,
                "valid replacement publishes a fresh generation and presents once");
        openedVersion=pc.getPrivateShopVersion();
        published=pc.getSellings();
        pc.setPrivateReady(true);closeUi(pc);
        check(pc.isPrivateShop(),"offline-ready UI close guard");pc.setPrivateReady(false);
        int idlesBefore=pc.idleActions;
        DynamicShopStorage.reject=true;close(pc);
        check(pc.isPrivateShop() && pc.getSellings() == published && pc.getPrivateShopVersion() == openedVersion
                && pc.inv.depth == 0,
                "failed close keeps list and generation");
        check(pc.idleActions==idlesBefore&&pc.sprite==11483,"rejected close keeps shop presentation");
        DynamicShopStorage.reject=false;close(pc);noOpen(pc,"close");
        check(pc.idleActions==idlesBefore+1&&pc.sprite==10,"accepted close restores presentation once");
        final int stoppedVersion = pc.getPrivateShopVersion(), stoppedDeletes = DynamicShopStorage.deletes;
        close(pc);
        check(User.RemovePrivateStoreList(pc) && pc.getPrivateShopVersion() == stoppedVersion
                && DynamicShopStorage.deletes == stoppedDeletes,
                "repeated close succeeds without enqueue or generation change");
        ui(pc,0x1a,11,5,25);
        check(pc.isPrivateShop()&&pc.getPurchasings().get(0).count==25,"UI buy demand above example amount");
        DynamicShopItem d=pc.getPurchasings().get(0);
        check(PlayerShopTransactions.canTransfer(customer,item,d,1,1),"valid transfer");
        check(!PlayerShopTransactions.canTransfer(customer,item,d,0,1),"zero transfer");
        check(!PlayerShopTransactions.canTransfer(customer,item,d,-1,1),"negative transfer");
        check(!PlayerShopTransactions.canTransfer(customer,item,d,21,1),"insufficient actual item count");
        check(!PlayerShopTransactions.canTransfer(customer,item,null,1,1),"null listing");
        NoTradable.blocked=100;
        check(!PlayerShopTransactions.canTransfer(customer,item,d,1,1),"settlement denies newly restricted seller");
        customer.gm=true;
        check(PlayerShopTransactions.canTransfer(customer,item,d,1,1),"GM seller may transfer operator-restricted item");
        check(PlayerShopTransactions.findListing(pc,pc.getPurchasings(),d.objId)==null,"newly restricted buy listing");
        pc.gm=true;
        check(PlayerShopTransactions.findListing(pc,pc.getPurchasings(),d.objId)==d,"GM buy listing operator exception");
        customer.gm=false;pc.gm=false;NoTradable.blocked=0;
        item.expiry=new java.sql.Timestamp(1);
        check(!PlayerShopTransactions.canTransfer(customer,item,d,1,1),"expired item cannot be sold");
        item.expiry=new java.sql.Timestamp(2);
        check(PlayerShopTransactions.canTransfer(customer,item,d,1,1),"unexpired item");
        item.expiry=null;
        item.enchant=1;check(!PlayerShopTransactions.canTransfer(customer,item,d,1,1),"changed item");item.enchant=0;
        pc.getPurchasings().clear();
        final int beforeEmptyDeletion = DynamicShopStorage.deletes;
        close(pc);
        check(!pc.isPrivateShop() && DynamicShopStorage.deletes == beforeEmptyDeletion + 1,
                "a real shop with no stock left still retires its persisted listing");
        final int beforePresentationOpen = DynamicShopStorage.writes;
        pc.openDisplayPrivateStore(new byte[0]);
        final int beforePresentationClose = pc.getPrivateShopVersion();
        close(pc);
        check(!pc.isPrivateShop() && !pc.isDisplayOnlyPrivateStore()
                && pc.getPrivateShopVersion() == beforePresentationClose + 1
                && DynamicShopStorage.writes == beforePresentationOpen && DynamicShopStorage.deletes == beforeEmptyDeletion + 1,
                "presentation-only shop closes without admitting SQL");
        check(!pc.inv.readWithoutGate&&pc.inv.depth==0,"all listing reads gated");
        checkController();
        checkUiLimits();
        System.out.println("PASS private store admission and lifecycle ("+checks+" checks)");
    }
}
"""
with tempfile.TemporaryDirectory() as td:
    f=Path(td)/"PrivateStoreTest.java"
    f.write_text(java,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8",str(f)],check=True)
    subprocess.run(["java","-cp",td,"PrivateStoreTest"],check=True)
