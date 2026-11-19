from pathlib import Path
import os,re,zipfile,tempfile,subprocess
ROOT=Path(__file__).resolve().parents[2]
P=ROOT/'src/l1j/server/server/model/network/core/cs/packet'
BASE=Path(os.environ.get('RKERN_PREVIOUS_ZIP','/mnt/data/RKern_NPC_Action_N7_Elemental_Quest_Raid_Safety_FULL_SRC_20261119.zip'))
def extract(src,name):
    m=re.search(r'(?m)^\s*(?:(?:private|public) )?static (?:void|boolean|int|String) '+re.escape(name)+r'\([^)]*\)\s*\{',src)
    assert m,name
    i=src.index('{',m.start())+1; depth=1
    while depth:
        if src[i]=='{':depth+=1
        elif src[i]=='}':depth-=1
        i+=1
    return src[m.start():i].strip()
def method(cls,n): return extract((P/(cls+'.java')).read_text(),n)
with zipfile.ZipFile(BASE) as z: old=z.read(str((P/'HtmlCmdHandler.java').relative_to(ROOT))).decode()
a=old.index('} else if (s.equalsIgnoreCase("pssellps") || s.equalsIgnoreCase("sell")) {'); b=old.index('} else if (s.equalsIgnoreCase("history")) {',a); c=old.index('} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 200007)',b)
orig_sell='static void originalSell(L1PcInstance pc,L1Object obj,int objid){String htmlid=null;'+old[old.index('{',a)+1:b]+'if(htmlid!=null)SC_NPCTalkReturn.send(pc,objid,htmlid,null);}'
orig_warehouse='static void originalWarehouse(L1PcInstance pc,int objid,String s){String htmlid=null;'+old[b+7:c]+'} if(htmlid!=null)SC_NPCTalkReturn.send(pc,objid,htmlid,null);}'
methods='\n'.join(method(c,n) for c,n in [('NpcMarketCommands','sell'),('NpcMarketCommands','isWarehouseCommand'),('NpcMarketCommands','warehouse'),('NpcCastleActions','hireMercenary'),('NpcBuffCommands','paidEquipmentBuff'),('NpcTravelCommands','hyosue'),('NpcExchangeCommands','rechargeWeddingRing'),('NpcPetCommands','withdrawPets')])
java='''import java.util.*;
public class Check {
 static int checks; static void ck(boolean b){if(!b)throw new AssertionError("assertion "+checks);checks++;}
 static class L1Object {}
 static class L1NpcInstance extends L1Object {int id;L1NpcInstance(int i){id=i;}L1NpcInstance getNpcTemplate(){return this;}int get_npcId(){return id;}int getNpcId(){return id;}}
 static class L1ItemInstance {int id,charge;L1ItemInstance(int i){id=i;}int getId(){return id;}int getItemId(){return id;}L1ItemInstance getItem(){return this;}int getMaxChargeCount(){return 50;}}
 static class Inv {int size,calls,money;boolean funds=true,debit=true;List<L1ItemInstance> items=new ArrayList<>();L1ItemInstance armor;int getSize(){return size;}boolean checkItem(int id,int n){return funds;}boolean consumeItem(int id,int n){calls++;if(debit){money+=n;return true;}return false;}List<L1ItemInstance> getItems(){return items;}L1ItemInstance getItemEquipped(int a,int b){return armor;}L1ItemInstance findItemId(int id){for(var x:items)if(x.id==id)return x;return null;}}
 static class L1PcInstance {List<String> log=new ArrayList<>();Inv inv=new Inv();int rank,level=5,clan,special,partner;boolean elf,ret,pet;L1ItemInstance weapon;L1InventoryTransaction pending;Inv getInventory(){return inv;}int getClanRank(){return rank;}int getClanid(){return clan;}int getLevel(){return level;}int get_SpecialSize(){return special;}boolean isElf(){return elf;}int getPartnerId(){return partner;}int getId(){return 1;}Map<Integer,Integer> getPetList(){return pet?Map.of(1,1):Map.of();}L1ItemInstance getWeapon(){return weapon;}void sendPackets(String s){log.add("packet:"+s);}void start_teleport(int x,int y,int m,int h,int e,boolean a,boolean b){log.add("teleport:"+x+":"+y+":"+m+":"+h+":"+e+":"+a+":"+b);}}
 static class L1Clan {static final int 군주=1,수호=2,부군주=3,CLAN_RANK_LEAGUE_GUARDIAN=4,수련=5;}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.log.add("sys:"+s);}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int id){p.log.add("server:"+id);}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String s,String[] d){p.log.add("html:"+id+":"+s+":"+Arrays.toString(d));}}
 static class SC_PacketBox {static final int GREEN_MESSAGE=1;static void send(L1PcInstance p,int t,String s){p.log.add("box:"+s);}}
 static class SC_ClanWareHouseHistory {static void sendAsync(L1PcInstance p){p.log.add("history");}}
 static class SC_WAREHOUSE_ITEM_LIST_NOTI {static void send_user_warehouse_items(L1PcInstance p,int id){p.log.add("warehouse:"+id);}static void send_user_pledge_warehouse_items(L1PcInstance p,int id){p.log.add("pledge:"+id);}}
 static class SC_RetrieveList {static boolean sendElf(L1PcInstance p,int id){p.log.add("elf:"+id);return p.ret;}static boolean sendPackage(L1PcInstance p,int id){p.log.add("package:"+id);return p.ret;}}
 static class NpcCastleActions {static String sellHouse(L1PcInstance p,int id,int n){p.log.add("house:"+n);return p.ret?"housepage":null;}}
 static class MJDogFightSettings {static final int CASTER_NPC_ID=123;}
 static class MJDogFightHistory {static final MJDogFightHistory I=new MJDogFightHistory();static MJDogFightHistory getInstance(){return I;}void on_show_sells_view(L1PcInstance p,L1NpcInstance n){p.log.add("dog");}}
 static class L1AssessedItem {}
 static class L1Shop {List<L1AssessedItem> assessItems(Inv inv){return empty?List.of():List.of(new L1AssessedItem());}}
 static boolean shopExists,empty,war,templateExists;
 static class ShopTable {static final ShopTable I=new ShopTable();static ShopTable getInstance(){return I;}L1Shop get(int n){return shopExists?new L1Shop():null;}}
 static class SC_NoSell {static void send(L1PcInstance p,L1NpcInstance n){p.log.add("noSell");}}
 static class SC_ShopBuyList {static void send(int id,L1PcInstance p){p.log.add("sell:"+id);}}
 static class L1CastleLocation {static int getCastleIdByArea(L1NpcInstance n){return n.id;}}
 static class MJCastleWarBusiness {static final MJCastleWarBusiness I=new MJCastleWarBusiness();static MJCastleWarBusiness getInstance(){return I;}boolean isNowWar(int i){return war;}}
 static class L1Npc {int id;L1Npc(int n){id=n;}}
 static class NpcTable {static final NpcTable I=new NpcTable();static NpcTable getInstance(){return I;}L1Npc getTemplate(int id){return templateExists?new L1Npc(id):null;}}
 static class L1SummonInstance {L1SummonInstance(L1Npc n,L1PcInstance p){p.log.add("summon:"+n.id);}void setPetcost(int n){ck(n==6);}}
 static class SC_CloseList {static void send(L1PcInstance p,int id){p.log.add("close");}}
 static class SC_ChatPacket {static void sendDirect(L1PcInstance p,L1PcInstance q,String s,int t){p.log.add("chat:"+s);}}
 static class L1SkillId {static final int ENCHANT_WEAPON=1,BLESSED_ARMOR=2;}
 static class L1SkillUse {static final int TYPE_SPELLSC=1;static void cast(L1PcInstance p,int skill,int id,int x,int y,Object o,int n,int t){ck(p.inv.money==100);p.log.add("buff:"+skill+":"+id);}}
 static class L1ItemId {static final int ADENA=40308;}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Runnable failure,done;L1ItemInstance ring;int charge,amount;boolean submitted;L1InventoryTransaction(L1PcInstance p){this.p=p;}static L1InventoryTransaction begin(L1PcInstance p,int key){ck(key==70702);return new L1InventoryTransaction(p);}boolean debit(int id,int n){ck(id==40308&&n==1000);p.inv.calls++;if(!p.inv.debit)return false;amount=n;return true;}void changeChargeCount(L1ItemInstance i,int c){ring=i;charge=c;}void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){done=r;submitted=true;p.pending=this;}void finish(boolean ok){if(ok){p.inv.money+=amount;if(ring!=null)ring.charge=charge;done.run();}else failure.run();}public void close(){}}
 static class SC_PetList {static void send(L1PcInstance p,int id){p.log.add("pets:"+id);}}
'''+orig_sell+'\n'+orig_warehouse+'\n'+methods+'''
 public static void main(String[] args){int n=checks;for(int id=50000;id<=71000;id++)for(boolean present:new boolean[]{false,true})for(boolean e:new boolean[]{false,true})for(boolean ret:new boolean[]{false,true}){shopExists=present;empty=e;L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();a.ret=b.ret=ret;L1NpcInstance npc=new L1NpcInstance(id);originalSell(a,npc,99);sell(b,npc,99);ck(a.log.equals(b.log));}L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();originalSell(a,new L1NpcInstance(123),99);sell(b,new L1NpcInstance(123),99);ck(a.log.equals(b.log));System.out.println("sell equivalence="+(checks-n));n=checks;
 for(String cmd:new String[]{"history","HISTORY","retrieve","retrieve-elven","RETRIEVE-ELVEN","retrieve-aib","retrieve-char","retrieve-pledge","RETRIEVE-PLEDGE"})for(int size:new int[]{149,150,151,179,180,181})for(int level:new int[]{4,5,6})for(int clan:new int[]{0,1})for(int rank:new int[]{1,5})for(int special:new int[]{0,1})for(boolean elf:new boolean[]{false,true})for(boolean ret:new boolean[]{false,true}){a=new L1PcInstance();b=new L1PcInstance();for(var p:List.of(a,b)){p.inv.size=size;p.level=level;p.clan=clan;p.rank=rank;p.special=special;p.elf=elf;p.ret=ret;}ck(isWarehouseCommand(cmd));originalWarehouse(a,99,cmd);warehouse(b,99,cmd);ck(a.log.equals(b.log));}ck(!isWarehouseCommand("mayer3"));System.out.println("warehouse equivalence="+(checks-n));n=checks;
 for(boolean kent:new boolean[]{false,true})for(int rank=0;rank<=6;rank++)for(boolean pet:new boolean[]{false,true})for(int id:new int[]{0,1})for(boolean w:new boolean[]{false,true})for(boolean t:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){a=new L1PcInstance();a.rank=rank;a.pet=pet;a.inv.debit=debit;war=w;templateExists=t;boolean admit=rank>=1&&rank<=4&&!pet&&!(id>0&&w)&&t;boolean ok=admit&&debit;ck(hireMercenary(a,new L1NpcInstance(id),kent)==ok);ck(a.inv.calls==(admit?1:0));ck(a.inv.money==(ok?300000:0));ck(a.log.contains("summon:"+(kent?7320031:7320000))==ok);}System.out.println("mercenary safety="+(checks-n));n=checks;
 for(boolean w:new boolean[]{false,true})for(boolean equip:new boolean[]{false,true})for(boolean inInv:new boolean[]{false,true})for(boolean funds:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){a=new L1PcInstance();var i=new L1ItemInstance(9);if(w)a.weapon=equip?i:null;else a.inv.armor=equip?i:null;if(inInv)a.inv.items.add(i);a.inv.funds=funds;a.inv.debit=debit;paidEquipmentBuff(a,w);boolean eligible=equip&&(!w||inInv)&&funds,ok=eligible&&debit;ck(a.inv.calls==(eligible?1:0));ck(a.inv.money==(ok?100:0));ck(a.log.contains("buff:"+(w?1:2)+":9")==ok);}System.out.println("equipment buff safety="+(checks-n));n=checks;
 for(String s:new String[]{"1","2",""})for(boolean funds:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){a=new L1PcInstance();a.inv.funds=funds;a.inv.debit=debit;boolean ok=s.equals("1")&&funds&&debit;ck(Objects.equals(hyosue(a,s,"initial"),s.equals("1")?(ok?"":"hyosue1"):"initial"));ck(a.inv.money==(ok?10000:0));ck(a.log.isEmpty()!=ok);}System.out.println("travel safety="+(checks-n));n=checks;
 for(int partner:new int[]{0,1})for(int mask=0;mask<16;mask++)for(boolean funds:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true})for(boolean commit:new boolean[]{false,true}){a=new L1PcInstance();a.partner=partner;a.inv.funds=funds;a.inv.debit=debit;int selected=0;for(int i=0;i<4;i++)if((mask&(1<<i))!=0){a.inv.items.add(new L1ItemInstance(40903+i));selected=40903+i;}rechargeWeddingRing(a);boolean staged=funds&&debit;ck((a.pending!=null)==staged);ck(a.inv.money==0);for(var ring:a.inv.items)ck(ring.charge==0);if(staged)a.pending.finish(commit);boolean ok=staged&&commit;ck(a.inv.money==(ok?1000:0));for(var ring:a.inv.items)ck(ring.charge==(ok&&partner!=0&&ring.id==selected?50:0));ck(a.log.contains("sys:기부해주셔서 감사합니다.")==ok);ck(a.log.contains("sys:결혼 반지가 충전되었습니다.")==(ok&&partner!=0&&selected!=0));}System.out.println("ring receipt safety="+(checks-n));n=checks;
 for(int mask=0;mask<8;mask++){a=new L1PcInstance();for(int i=0;i<3;i++)if((mask&(1<<i))!=0)a.inv.items.add(new L1ItemInstance(i==0?40314:i==1?40316:123));withdrawPets(a,99);ck(a.log.equals(List.of((mask&3)!=0?"pets:99":"sys:보유중인 펫이 없습니다.")));}System.out.println("pet existence="+(checks-n));System.out.println("TOTAL_CHECKS="+checks);
 }
}'''
with tempfile.TemporaryDirectory() as td:
    f=Path(td)/'Check.java'; f.write_text(java); subprocess.run(['javac','-encoding','UTF-8','--release','21',str(f)],check=True); subprocess.run(['java','-cp',td,'Check'],check=True)
