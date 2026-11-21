from pathlib import Path
import re, subprocess, tempfile
P=Path(__file__).resolve().parent; R=P.parents[1]; D=R/'src/l1j/server/server/model/item/function'
def cls(p,n):
 s=p.read_text(); return s[s.index('final class '+n):]
registry=cls(R/'src/l1j/server/server/utils/SupplementalItemIds.java','SupplementalItemIds')
old=registry.replace('70740, 70741, 80804','70740, 70741, 70888, 80804').replace('SupplementalItemIds','OldSupplementalItemIds')
decoder=cls(D/'ItemTargetDecoder.java','ItemTargetDecoder')
olddecoder=decoder.replace('30074, 70888 -> TARGET','30074 -> TARGET').replace('ItemTargetDecoder','OldItemTargetDecoder')
code='''import java.util.*; import java.util.concurrent.ThreadLocalRandom;
class InnHelper {static int INN_KEYID=40312;}
class CollectionLoadManager {static int CTSYSTEM_STORE_ID=7777777;}
class TransformationListItems {static boolean showNeigeSelection(L1PcInstance p){return true;}}
class CreatureItemActions {static final Random _random=new Random(2);}
class Config {static int 엘븐스트라이크기본=100,엘븐스트라이크양=0,엘븐스트라이크음=0,엘븐스트라이크배율=5,엘븐스트라이크대기=10,엘븐스트라이크몬스터=100,매직실드대기=10,매직실드지속=30;static double 리버레이션절감=.5;}
class L1SkillId {static final int 엘븐스트라이크대기=1,리버레이션=2,보조버프=3,매직실드대기=4,매직실드1=5,매직실드2=6;}
class L1Object {int id=3,map=4,x=1,y=1;int getId(){return id;} int getMapId(){return map;} int getX(){return x;}int getY(){return y;}}
class AC {int getAc(){return 0;}}
class L1Character extends L1Object {boolean dead,invis,glance=true;int zone,level=85,damage,bind;boolean isDead(){return dead;}boolean isInvisble(){return invis;}int getZoneType(){return zone;}int getLevel(){return level;}AC getAC(){return new AC();}boolean glanceCheck(int x,int y){return glance;}boolean hasSkillEffect(int i){return false;}void setSkillEffect(int i,int t){bind++;}void receiveDamage(L1PcInstance p,int d){if(d<100)throw new AssertionError();damage++;}}
class L1ItemInstance {int itemId=70888;int getItemId(){return itemId;}int getId(){return 77;}}
class Inv {L1ItemInstance item=new L1ItemInstance(); boolean check=true,consume=true;int attempts,spent; L1ItemInstance getItem(int id){return id==77?item:null;}boolean checkItem(int id,int n){return check;}boolean tryConsumeCombatReagent(int id,int n){if(id!=40319)throw new AssertionError();attempts++;if(consume)spent+=n;return consume;}}
class L1PcInstance extends L1Character {boolean teleport,ghost,gmInvis,gm,pvp,nonAction;int type=2,mp=40;Inv inv=new Inv();Set<Integer> effects=new HashSet<>();boolean get_teleport(){return teleport;}boolean isGhost(){return ghost;}boolean isGmInvis(){return gmInvis;}boolean isGm(){return gm;}boolean checkNonPvP(L1PcInstance p,L1Character ch){return pvp;}Inv getInventory(){return inv;}Object getNetConnection(){return null;}int getType(){return type;}boolean getSafetyZone(){return zone==1;}int getCurrentMp(){return mp;}void setCurrentMp(int v){mp=v;}boolean hasSkillEffect(int i){return effects.contains(i);}void setSkillEffect(int i,int t){effects.add(i);super.setSkillEffect(i,t);}void removeSkillEffect(int i){effects.remove(i);}int getSkillEffectTimeSec(int i){return 2;}void sendPackets(String s){}}
class L1NpcInstance extends L1Character {boolean hidden,template=true;Object getNpcTemplate(){return template?this:null;}}
class L1MonsterInstance extends L1NpcInstance {}
class L1PetInstance extends L1NpcInstance {L1PcInstance master;L1PcInstance getMaster(){return master;}}
class L1SummonInstance extends L1NpcInstance {L1PcInstance master;L1PcInstance getMaster(){return master;}}
class L1CompanionInstance extends L1NpcInstance {int owner=-1;int get_master_id(){return owner;}}
class L1AttackHelper {static boolean isAttackableNpcTarget(L1NpcInstance n){return !n.hidden;}}
class L1World {static final L1World W=new L1World();L1Object target;static L1World getInstance(){return W;}L1Object findObject(int id){return target;}}
class ActionCodes {static final int ACTION_Wand=1;}
class SC_AttackPacket {static void send(Object o,L1PcInstance p,int i,int j){}static void broadcast(L1PcInstance p,int i,int j){}}
class SC_PacketBox {static final int GREEN_MESSAGE=1;static void send(L1PcInstance p,int i,String s){}}
class SC_SystemMessage {static void send(L1PcInstance p,String s){}}
class SC_ServerMessage {static void send(L1PcInstance p,int n){}}
class GameUtils {static boolean isNonAction(L1PcInstance p){return p.nonAction;}}
class DurationChoiceItems {static int elven(Random r){return 5000;}}
class SC_Paralysis {static int TYPE_BIND=1;static void send(L1Character c,int i,boolean b){}}
class L1EffectSpawn {static L1EffectSpawn getInstance(){return new L1EffectSpawn();}void spawnEffect(int a,int b,int c,int d,int e){}}
class L1SkillUse {static int TYPE_SPELLSC=1;static void cast(L1PcInstance p,int a,int b,int c,int d,Object o,int n,int k){}}
class SC_SkillSound {static void send(L1Character p,int a,int b){}static void broadcast(L1Character p,int a,int b){}}
class SC_InventoryIcon {static void icoNew(L1PcInstance p,int a,int b,int c,boolean d){}}
'''+registry+old+decoder+olddecoder+cls(D/'ElvenStrikeItemEffect.java','ElvenStrikeItemEffect').replace('STATUS_FREEZE','999')+cls(D/'MagicShieldItemEffect.java','MagicShieldItemEffect')+'''
public class Test {
 static void ck(boolean b){if(!b)throw new AssertionError();}
 static L1PcInstance pc(){L1PcInstance p=new L1PcInstance();p.id=1;return p;}
 static boolean use(L1PcInstance p,L1Object target){L1World.W.target=target;return ElvenStrikeItemEffect.엘븐스트라이크(p,3,p.inv.item);}
 static void rejected(L1PcInstance p,L1Object t){ck(!use(p,t));ck(p.mp<=40&&p.effects.isEmpty()&&p.inv.spent==0);if(t instanceof L1Character c)ck(c.damage==0&&c.bind==0);}
 public static void main(String[] args){
  int dif=0;for(int i=-1;i<=1_000_000;i++){if(SupplementalItemIds.contains(i)!=OldSupplementalItemIds.contains(i)){ck(i==70888);dif++;}}ck(dif==1);
  for(int i:new int[]{3000147,32011,41303,41304,875640450})ck(SupplementalItemIds.contains(i)==OldSupplementalItemIds.contains(i));
  for(int i=0;i<=64;i++){ck(ItemTargetDecoder.modeOf(70888,i)==ItemTargetDecoder.TARGET);ck(ItemTargetDecoder.modeOf(80888,i)==OldItemTargetDecoder.modeOf(80888,i));}
  int n=0;
  for(int t=0;t<2;t++)for(int flags=0;flags<256;flags++){
   L1PcInstance p=pc();L1Character c=t==0?new L1PcInstance():new L1MonsterInstance();
   c.dead=(flags&1)!=0;c.map=(flags&2)!=0?5:4;c.invis=(flags&4)!=0;c.x=(flags&8)!=0?10:1;
   p.glance=(flags&16)==0;p.inv.check=(flags&32)==0;p.inv.consume=(flags&64)==0;p.nonAction=(flags&128)!=0;
   boolean ok=use(p,c);ck(ok==(flags==0));ck(p.inv.spent==(ok?2:0));ck(p.mp==(ok?20:40));ck(c.damage==(ok?1:0));ck(p.effects.contains(L1SkillId.엘븐스트라이크대기)==ok);n++;
  }
  L1PcInstance p=pc();rejected(p,null);rejected(pc(),new L1Object());rejected(pc(),new L1NpcInstance());p=pc();rejected(p,p);
  for(int f=0;f<5;f++){p=pc();L1PcInstance q=new L1PcInstance();if(f==0)q.teleport=true;if(f==1)q.ghost=true;if(f==2)q.gmInvis=true;if(f==3)q.zone=1;if(f==4)p.pvp=true;rejected(p,q);}
  p=pc();L1PetInstance pet=new L1PetInstance();pet.master=p;rejected(p,pet);p=pc();L1SummonInstance summon=new L1SummonInstance();summon.master=p;rejected(p,summon);p=pc();L1CompanionInstance companion=new L1CompanionInstance();companion.owner=p.id;rejected(p,companion);p=pc();L1MonsterInstance mon=new L1MonsterInstance();mon.hidden=true;rejected(p,mon);
  p=pc();p.inv.item.itemId=1;rejected(p,new L1MonsterInstance());
  System.out.println("PASS registry 1000002 probes, target decode 65 types, Elven 512 matrix + 14 invalid targets");
  for(int f=0;f<128;f++){p=pc();p.type=(f&1)!=0?0:2;p.level=(f&2)!=0?84:85;p.invis=(f&4)!=0;p.inv.check=(f&8)==0;p.inv.consume=(f&16)==0;p.nonAction=(f&32)!=0;p.mp=(f&64)!=0?19:40;int before=p.mp;boolean ok=MagicShieldItemEffect.매직실드(p);ck(ok==(f==0));ck(p.mp==before-(ok?20:0));ck(p.inv.spent==(ok?5:0));ck(p.effects.size()==(ok?3:0));}
  p=pc();p.effects.add(L1SkillId.매직실드대기);ck(!MagicShieldItemEffect.매직실드(p));ck(p.inv.spent==0&&p.mp==40&&p.effects.size()==1);
  p=pc();p.effects.add(L1SkillId.매직실드1);p.inv.consume=false;ck(!MagicShieldItemEffect.매직실드(p));ck(p.effects.contains(L1SkillId.매직실드1));
  System.out.println("PASS Magic Shield 130 outcomes including retained existing buff on debit failure");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'Test.java').write_text(code);subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,str(p/'Test.java')],check=True);subprocess.run(['java','-cp',tmp,'Test'],check=True)
# Current dispatcher invokes result-aware methods and applies completion only on success.
s=(D/'ItemActionDispatcher.java').read_text();assert re.search(r'case 70888 -> ElvenStrikeItemEffect\.엘븐스트라이크\(pc, spellTargetId, item\)\s*\? ItemActionResult.COMPLETE : ItemActionResult.STOP;',s);assert re.search(r'case 220012 -> MagicShieldItemEffect\.매직실드\(pc\)\s*\? ItemActionResult.COMPLETE : ItemActionResult.STOP;',s)
