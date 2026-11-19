from pathlib import Path
import re, subprocess, tempfile, zipfile
ROOT=Path(__file__).resolve().parents[2]; P=ROOT/'src/l1j/server/server/model/network/core/cs/packet'
h=(P/'HtmlCmdHandler.java').read_text(); q=(P/'NpcQuestTransactions.java').read_text(); e=(P/'NpcGameEventActions.java').read_text(); m=(ROOT/'src/l1j/server/server/model/L1PetMatch.java').read_text()
with zipfile.ZipFile('/mnt/data/RKern_NPC_Action_N7_Slot_Quest_Audit_FULL_SRC_20261119.zip') as z: old=z.read(str((P/'HtmlCmdHandler.java').relative_to(ROOT))).decode()
assert re.findall(r'get_npcId\(\)\s*==\s*(\d+)',old)==re.findall(r'get_npcId\(\)\s*==\s*(\d+)',h)
def method(s,n):
 a=re.search(r'(?m)^\s*(?:private |public |static |synchronized )+(?:String|void|boolean|L1PetInstance) '+n+r'\([^)]*\)\s*\{',s);assert a,n
 i=s.index('{',a.start())+1; d=1
 while d:d+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a.start():i].strip()
recipe=h[h.index('final boolean seals ='):h.index('\n\t\t\t\t}\n\t\t\t} else if',h.index('final boolean seals ='))]
const='\n'.join(re.findall(r'private static final int\[\] (?:RUMTIS_\w+|RECIPE_VALUES_4|RECIPE_VALUES_67) = \{[^}]+\};',h))
# Build a typed harness from the actual modified Java methods and recipe selection branch.
java=r'''import java.util.*;
public class Check {
 static int checks; static void ck(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
 static class L1ItemInstance {int id,n;L1ItemInstance(int id,int n){this.id=id;this.n=n;}int getCount(){return n;}L1ItemInstance getItem(){return this;}String getName(){return "item"+id;}}
 static class Inv {Map<Integer,Integer> m=new HashMap<>();L1ItemInstance getItem(int id){return m.containsKey(id)?new L1ItemInstance(id,m.get(id)):null;}boolean checkItem(int id,int n){return m.getOrDefault(id,0)>=n;}}
 static class L1NpcInstance {int getId(){return 7;}int getNpcId(){return 80067;}L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "NPC";}}
 static class L1PcInstance {Inv inv=new Inv();Map<Integer,Integer> pets=new HashMap<>();List<String> out=new ArrayList<>();int mode,teleports,created;L1InventoryTransaction pending;boolean petRecord,npcTemplate,room;Inv getInventory(){return inv;}Map<Integer,Integer> getPetList(){return pets;}int getId(){return 8;}void start_teleport(int x,int y,int m,int h,int a,boolean b,boolean c){ck(x==32799&&y==32868&&m==5125);teleports++;}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int n){p.out.add("msg:"+n);}static void send(L1PcInstance p,int n,String a,String b){ck(n==143&&a.equals("NPC"));p.out.add("reward:"+b);}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.out.add("error");}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page){ck(id==7);p.out.add("html:"+page);}}
 static class L1Pet {int get_npcid(){return 1;}} static class L1Npc {} static L1PcInstance active;
 static class PetTable {static PetTable getInstance(){return new PetTable();}L1Pet getTemplate(int i){return active.petRecord?new L1Pet():null;}}
 static class NpcTable {static NpcTable getInstance(){return new NpcTable();}L1Npc getTemplate(int i){return active.npcTemplate?new L1Npc():null;}}
 static class L1PetInstance {L1PetInstance(L1Npc t,L1PcInstance p,L1Pet pet){ck(t!=null);p.created++;}void setPetcost(int i){ck(i==6);}int getId(){return 19;}int getX(){return 1;}int getY(){return 2;}}
 static class L1SkillId {static final int CANCELLATION=1;}static class L1SkillUse {static final int TYPE_LOGIN=1;static void cast(L1PcInstance p,int a,int id,int x,int y,Object o,int v,int l){ck(id==19);}}
 static class L1PetMatchReadyTimer {L1PetMatchReadyTimer(int i,L1PcInstance p,L1PetInstance pet){ck(i==0&&pet!=null);}void begin(){}}
 static class L1PetMatch {static L1PetMatch getInstance(){return new L1PetMatch();}int decidePetMatchNo(){return active.room?0:-1;}static final short[] PET_MATCH_MAPID={5125};
'''+method(m,'enterPetMatch')+method(m,'withdrawPet')+r'''}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Map<Integer,Integer> stage;Runnable ok,fail;int debits;L1InventoryTransaction(L1PcInstance p){this.p=p;stage=new HashMap<>(p.inv.m);}static L1InventoryTransaction begin(L1PcInstance p,int id){if(p.mode==1||p.pending!=null)throw new IllegalStateException();return new L1InventoryTransaction(p);}boolean debit(int id,int n){debits++;if(p.mode==2||p.mode==6&&debits==2||stage.getOrDefault(id,0)<n)return false;stage.merge(id,-n,Integer::sum);return true;}L1ItemInstance grant(int id,int n){if(p.mode==3)return null;stage.merge(id,n,Integer::sum);return new L1ItemInstance(id,n);}void onFailure(Runnable r){fail=r;}void commitAfterInventoryRelease(Runnable r){if(p.mode==4)throw new IllegalStateException();p.pending=this;ok=r;}void complete(){p.pending=null;if(p.mode==5){fail.run();return;}p.inv.m=stage;ok.run();}public void close(){}}
 static class NpcExchangeCommands {static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int id,int n){return t.debit(id,n);}}
'''+method(q,'spyGift')+method(q,'spyDiscard')+method(e,'enterPetMatch')+const+r'''
 static void recipe(L1PcInstance pc,boolean sealsExpected,boolean badgesExpected,boolean common){int[] materials=null,counts=null,createitem=null,createcount=null;String htmlid=null;
'''+recipe+r'''
 boolean valid=common&&(sealsExpected||badgesExpected);ck((materials!=null)==valid);if(valid){ck(htmlid.equals("rumtis60"));ck(materials.length==10&&counts.length==10);ck(createitem[0]==40926&&createcount[0]==1);ck(materials[7]==40645&&counts[7]==5);for(int i=0;i<10;i++)ck(pc.inv.checkItem(materials[i],counts[i]));ck(materials[0]==(sealsExpected?40959:40642));}else ck(Objects.equals(htmlid,common?"rumtis18":null));}
 static void pet(String input,boolean pets,int count,boolean row,boolean templ,boolean room){L1PcInstance p=new L1PcInstance();active=p;p.room=room;p.petRecord=row;p.npcTemplate=templ;if(pets)p.pets.put(1,1);if(count>=0)p.inv.m.put(10,count);ck(enterPetMatch(p,input).equals(""));boolean parse;int v=0;try{v=Integer.parseInt(input);parse=true;}catch(NumberFormatException f){parse=false;}boolean good=parse&&!pets&&v==10&&count>0&&row&&templ&&room;ck((p.teleports==1)==good);ck((p.created==1)==good);ck(p.out.equals(good?List.of():List.of(parse&&pets?"msg:1187":"msg:1182")));}
 static void spy(boolean shadow,int a,int b,int mode,boolean gift){L1PcInstance p=new L1PcInstance();p.mode=mode;int id=shadow?41121:41130;p.inv.m.put(id,a);p.inv.m.put(id+1,b);Map<Integer,Integer> before=new HashMap<>(p.inv.m);if(gift)spyGift(p,new L1NpcInstance(),shadow);else spyDiscard(p,new L1NpcInstance(),shadow);ck(p.inv.m.equals(before));boolean noop=gift?a>0:a==0&&b==0;boolean submit=!noop&&mode!=1&&mode!=4&&(gift?mode!=3:mode!=2&&!(mode==6&&a>0&&b>0));ck((p.pending!=null)==submit);String page="html:"+(gift?"":shadow?"minitos09":"minicod09");if(noop){ck(p.out.equals(List.of(page)));return;}if(!submit){ck(p.out.equals(List.of("error")));return;}ck(p.out.isEmpty());p.pending.complete();if(mode==5){ck(p.inv.m.equals(before)&&p.out.equals(List.of("error")));return;}ck(p.inv.m.get(id)==(gift?1:Math.max(0,a-1))&&p.inv.m.get(id+1)==(gift?b:Math.max(0,b-1)));ck(p.out.equals(gift?List.of("reward:item"+id,page):List.of(page)));if(gift){spyGift(p,new L1NpcInstance(),shadow);ck(p.pending==null&&p.inv.m.get(id)==1);}}
 public static void main(String[] args){int[] ids={40959,40960,40961,40962,40642,40635,40638,40667,40046,40618,40643,40645,40651,40676};for(int bits=0;bits<(1<<14);bits++){L1PcInstance p=new L1PcInstance();for(int i=0;i<14;i++){int cost=i<4||i==8?1:i<8?10:5;p.inv.m.put(ids[i],(bits&(1<<i))==0?cost-1:cost);}recipe(p,(bits&15)==15,(bits&240)==240,(bits&16128)==16128);}for(String input:new String[]{null,"","10","+10","-10","0","2147483648","1a","11"," 10"})for(boolean pets:new boolean[]{false,true})for(int count=-1;count<=1;count++)for(boolean row:new boolean[]{false,true})for(boolean t:new boolean[]{false,true})for(boolean room:new boolean[]{false,true})pet(input,pets,count,row,t,room);for(boolean shadow:new boolean[]{false,true})for(int a=0;a<3;a++)for(int b=0;b<3;b++)for(int mode=0;mode<=6;mode++)for(boolean gift:new boolean[]{false,true})spy(shadow,a,b,mode,gift);System.out.println("PASS assertions="+checks);}
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Check.java';p.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True);subprocess.run(['java','-cp',d,'Check'],check=True)
# house safety / unreachable duplicate room route check
assert h.count('if (house == null) return;') >= 4
assert h.count('loc != null && loc.length >= 3 && loc[0] > 0 && loc[1] > 0')==2
assert h.count('s.equalsIgnoreCase("room")')==old.count('s.equalsIgnoreCase("room")')-2
print('PASS house guards and route preservation (static)')
