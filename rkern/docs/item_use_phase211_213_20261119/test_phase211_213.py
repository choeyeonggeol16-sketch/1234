from pathlib import Path
import re, subprocess, tempfile
P=Path(__file__).resolve().parent; R=P.parents[1]; D=R/'src/l1j/server/server/model/item/function'
def body(s,name):
    return re.sub(r'\bpublic final class '+name+r'\b','final class '+name,s[s.index('final class '+name):],count=1)
def get(name):return body((D/(name+'.java')).read_text(),name)
registry=body((R/'src/l1j/server/server/utils/SupplementalItemIds.java').read_text(),'SupplementalItemIds')
old=registry.replace('32005, 32011, 32029','32005, 32029').replace('SupplementalItemIds','OldSupplementalItemIds')
# Ensure the new admission is the only change, across the actual compiled registry.
regvals=set(map(int,re.findall(r'\d+',registry.split('ITEM_IDS = {')[1].split('};')[0])))|{40312}
assert 32011 in regvals
point=get('WorldDestinationItems'); point=point[point.index('    static ItemActionResult handlePointTeleport'):point.rindex('}')]
revival=get('RevivalScrollCraftingItems').replace('l1j.server.server.model.L1InventoryTransaction','L1InventoryTransaction').replace('l1j.server.server.storage.CacheD','CacheD')
admin=get('AdminItemPackages')
route=get('SupplementalItemActions')+get('WorldItemActions')+get('WorldRevivalItems')+get('SupplementalTargetId')+get('SupplementalEarringTargetRules')+get('SupplementalElixirEligibility')
# Stub every unrelated child routing method, preserving their signature from actual call sites.
routers=''
for name,args in [('SupplementalCraftRouter','CIOClient c,L1PcInstance p,L1ItemInstance i,int id,L1ItemInstance t'),('AdvancedAbilityItems','L1PcInstance p,L1ItemInstance i,int id'),('SupplementalRewardRouter','L1PcInstance p,L1ItemInstance i,int id,L1ItemInstance t'),('TravelAndRecoveryItems','CIOClient c,L1PcInstance p,L1ItemInstance i,int id')]+[(n,'CIOClient c,L1PcInstance p,L1ItemInstance i,int id,L1ItemInstance t') for n in ['WorldTravelScrollItems','WorldRewardAndCraftItems','WorldQuestAndEventItems','WorldBuffAndCureItems','WorldPetVoucherItems','WorldDollPotentialItems']]:
    routers+='class '+name+' { static void use('+args+'){E.other++;} }\n'
code='''import java.util.*; import java.util.logging.*;
class InnHelper {static final int INN_KEYID=40312;}
class E {static int other,opened,debit,grant,request,post,msg,teleport;static boolean allowDebit=true,allowGrant=true,commit=true; static int input,reward,n; static void reset(){other=opened=debit=grant=request=post=msg=teleport=input=reward=n=0;allowDebit=allowGrant=commit=true;}}
class Template {int id;Template(int id){this.id=id;}int getItemId(){return id;}}
class L1ItemInstance {Template t;int obj=7;L1ItemInstance(int id){t=new Template(id);}Template getItem(){return t;}int getItemId(){return t.id;}int getId(){return obj;}String getLogName(){return "item";}}
class Inv {L1ItemInstance live;L1ItemInstance getItem(int id){return live!=null&&live.obj==id?live:null;}}
class L1PcInstance {Inv inv=new Inv();boolean gm; boolean isGm(){return gm;}Inv getInventory(){return inv;}int getId(){return 1;}int getLevel(){return 80;}int getElixirStats(){return 0;}short getMapId(){return 4;}int getHeading(){return 7;}boolean getAutoHunt(){return false;}void start_teleport(int x,int y,short m,int h,int e,boolean b,boolean a){if(x!=12||y!=34||m!=4||h!=7||e!=169||b||a)throw new AssertionError();E.teleport++;}}
class CIOClient {L1PcInstance p;L1PcInstance getActiveChar(){return p;}}
class CacheD {static String accountOrderKey(String s,int i){return s+i;}}
class User {static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,String k){E.opened++;return new L1InventoryTransaction();}}
class L1InventoryTransaction implements AutoCloseable {Runnable failure; boolean debit(int id,int n){E.debit++;E.input=id;E.n=n;return E.allowDebit;}boolean debit(L1ItemInstance i,int n){return debit(i.getItemId(),n);}L1ItemInstance grant(int id,int n){E.grant++;E.reward=id;return E.allowGrant?new L1ItemInstance(id):null;}void onFailure(Runnable r){failure=r;}public void close(){}}
class CDB {static void RequestUseItem(L1PcInstance p,L1InventoryTransaction tx,Runnable cb){E.request++;if(E.commit){cb.run();E.post++;}else if(tx.failure!=null)tx.failure.run();}}
class SC_SystemMessage{static void send(L1PcInstance p,String s){E.msg++;}}
class SC_ServerMessage{static void send(L1PcInstance p,int n,Object...args){E.msg++;}}
class SC_SkillSound{static void send(L1PcInstance p,int i,int n){}static void broadcast(L1PcInstance p,int i,int n){}}
class SC_ChatPacket{static void sendDirect(L1PcInstance p,L1PcInstance q,String s,int i){}}
class CreatureItemActions{static final Logger ITEM_USE2_LOG=Logger.getAnonymousLogger();}
enum ItemActionResult{STOP,COMPLETE}
class ItemActionContext{L1PcInstance p;ItemActionContext(L1PcInstance p){this.p=p;}L1PcInstance player(){return p;}int spellX(){return 12;}int spellY(){return 34;}}
'''+registry+old+route+revival+admin+routers+'class WorldDestinationItems{'+point+'}\n'+'''
public class Test {
static void ck(boolean b){if(!b)throw new AssertionError();}
static L1PcInstance pc(int id,boolean gm){L1PcInstance p=new L1PcInstance();p.gm=gm;p.inv.live=new L1ItemInstance(id);return p;}
public static void main(String[] args){
int diff=0;for(int i=-1;i<=1_000_000;i++){boolean a=OldSupplementalItemIds.contains(i),b=SupplementalItemIds.contains(i);if(a!=b){ck(i==32011&&b);diff++;}}ck(diff==1);
for(int i:new int[]{'''+','.join(map(str,sorted(regvals)))+'''})ck(SupplementalItemIds.contains(i)&&(i==32011||OldSupplementalItemIds.contains(i)));System.out.println("PASS 1000002 contiguous registry probes plus all registered IDs: only 32011 added");
for(boolean debit:new boolean[]{false,true})for(boolean grant:new boolean[]{false,true})for(boolean commit:new boolean[]{false,true})for(boolean owned:new boolean[]{false,true})for(boolean active:new boolean[]{false,true}){E.reset();E.allowDebit=debit;E.allowGrant=grant;E.commit=commit;L1PcInstance p=pc(32011,false);CIOClient c=new CIOClient();c.p=active?p:null;var item=p.inv.live;if(!owned)p.inv.live=null;SupplementalItemActions.use(c,p,item,0);boolean started=owned&&active;ck(E.opened==(started?1:0));ck(E.debit==(started?1:0));if(started){ck(E.input==32011&&E.n==8);}ck(E.grant==(started&&debit?1:0));if(started&&debit)ck(E.reward==4100102);ck(E.request==(started&&debit&&grant?1:0));ck(E.post==(started&&debit&&grant&&commit?1:0));}System.out.println("PASS 32 actual supplemental/World/Revival handler outcomes (stub transaction)");
for(boolean gm:new boolean[]{false,true})for(boolean owned:new boolean[]{false,true})for(boolean correct:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true})for(boolean grant:new boolean[]{false,true})for(boolean commit:new boolean[]{false,true}){E.reset();E.allowDebit=debit;E.allowGrant=grant;E.commit=commit;L1PcInstance p=pc(correct?123123:1,gm);var item=p.inv.live;if(!owned)p.inv.live=null;AdminItemPackages.open(p,item);boolean started=gm&&owned&&correct;ck(E.opened==(started?1:0));ck(E.debit==(started?1:0));ck(E.grant==(started&&debit?(grant?13:1):0));ck(E.request==(started&&debit&&grant?1:0));ck(E.post==(started&&debit&&grant&&commit?1:0));}System.out.println("PASS 64 administrator package authorization/transaction outcomes");
for(boolean gm:new boolean[]{false,true}){E.reset();L1PcInstance p=pc(42501,gm);ck(WorldDestinationItems.handlePointTeleport(new ItemActionContext(p))==(gm?ItemActionResult.COMPLETE:ItemActionResult.STOP));ck(E.teleport==(gm?1:0));}System.out.println("PASS 2 point-teleport authorization outcomes");
}}
'''
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp); (p/'Test.java').write_text(code); subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,str(p/'Test.java')],check=True); subprocess.run(['java','-cp',tmp,'Test'],check=True)
