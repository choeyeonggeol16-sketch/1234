from pathlib import Path
import re, subprocess, tempfile, zipfile
ROOT=Path(__file__).resolve().parents[2]; P=ROOT/'src/l1j/server/server/model/network/core/cs/packet'
def read(c):return (P/(c+'.java')).read_text()
def extract(c,n):
 s=read(c); m=re.search(r'(?m)^\s*(?:private )?static (?:void|boolean|String) '+n+r'\([^)]*\)\s*\{',s); assert m,n
 i=s.index('{',m.start())+1; depth=1
 while depth:depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[m.start():i].strip()
methods='\n'.join(extract(c,n) for c,ns in [('NpcTravelCommands',['towerGate','towerUpperFloor','paidTowerTravel','towerFailure']),('NpcBuffCommands',['featherBuff']),('NpcExchangeCommands',['rechargeCrownRelic']),('NpcQuestTransactions',['spyStart','spyReset'])] for n in ns)
fields='\n'.join(re.search(r'private static final int\[\](?:\[\])? '+n+r'\s*=.*?;',read(c),re.S).group() for c,n in [('NpcTravelCommands','TOWER_X'),('NpcTravelCommands','TOWER_Y'),('NpcBuffCommands','FEATHER_SKILLS'),('NpcBuffCommands','FEATHER_REMOVE_ORDER')])
# Ensure no NPC routing changes and original floor coordinates match all 19 branches.
with zipfile.ZipFile('/mnt/data/RKern_NPC_Action_N7_Paid_Purchases_Marbin_Receipt_FULL_SRC_20261119.zip') as z:
 old=z.read(str((P/'HtmlCmdHandler.java').relative_to(ROOT))).decode()
new=read('HtmlCmdHandler'); pat=r'get_npcId\(\)\s*==\s*(\d+)'; assert re.findall(pat,old)==re.findall(pat,new)
assert 'if (step != L1Quest.QUEST_END && pc.getLevel() > 13)' in new
assert 'pc.getInventory().consumeItem(41028, 1))' in new
assert 'if (pc.getInventory().consumeItem(40921, 1))' in new
assert 'if (pc.getInventory().consumeItem(210081, 1))' in new
assert 'if (pc.getInventory().consumeItem(41314, 1))' in new
# Compare all original tower gate constants, rather than using the new table as oracle.
old_gate=old[old.index('get_npcId() == 6000015)'):old.index('get_npcId() == 80067)')]
rows=re.findall(r'pc.start_teleport\((\d+), (\d+), (\d+), 5, 169, true, false\);\s*pc.getInventory\(\).consumeItem\(41158, (\d+)\);',old_gate)
assert len(rows)==10
for i,(x,y,mapid,cost) in enumerate(rows):
 assert int(x)==int(re.search(r'TOWER_X = \{([^}]+)',fields).group(1).split(',')[i])
 assert int(y)==int(re.search(r'TOWER_Y = \{([^}]+)',fields).group(1).split(',')[i])
 assert int(mapid)==110+i*10 and int(cost)==(i+1)*10
java=r'''import java.util.*;
public class Safety {
 static int checks; static void ck(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
 static class L1ItemId {static final int ADENA=40308;}
 static class L1SkillId {static final int FEATHER_BUFF_A=101,FEATHER_BUFF_B=102,FEATHER_BUFF_C=103,FEATHER_BUFF_D=104;}
 static class L1Quest {static final int QUEST_SHADOWS=37,QUEST_DESIRE=36,QUEST_END=255; int step;int get_step(int id){return step;}void applyCommittedStepIfLoaded(int id,int s){step=s;}}
 static class L1ItemInstance {int id,charge;L1ItemInstance(int i){id=i;} L1ItemInstance getItem(){return this;}String getName(){return "item"+id;}int getChargeCount(){return charge;}}
 static class Inv {Map<Integer,Integer> m=new HashMap<>();L1ItemInstance relic=new L1ItemInstance(20383);boolean failConsume;boolean checkItem(int id,int n){return m.getOrDefault(id,0)>=n;}boolean consumeItem(int id,int n){if(failConsume||!checkItem(id,n))return false;m.merge(id,-n,Integer::sum);return true;}L1ItemInstance findItemId(int id){return checkItem(id,1)?relic:null;}void put(int id,int n){m.put(id,n);}}
 static class L1PcInstance {Inv inv=new Inv(); L1Quest quest=new L1Quest();Set<Integer> skills=new HashSet<>();List<String> log=new ArrayList<>(); L1InventoryTransaction pending; int mode, map=116, heading=3,morph;boolean crown=true,released;Inv getInventory(){return inv;}L1Quest getQuest(){return quest;}int getMapId(){return map;}int getHeading(){return heading;}int getId(){return 8;}int getX(){return 1;}int getY(){return 2;}boolean isCrown(){return crown;}boolean hasSkillEffect(int s){return skills.contains(s);}void removeSkillEffect(int s){skills.remove(s);log.add("remove:"+s);}void start_teleport(int x,int y,int m,int h,int e,boolean a,boolean b){ck(e==169&&a&&!b);log.add("move:"+x+":"+y+":"+m+":"+h);}}
 static class L1NpcInstance {int getId(){return 7;}L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "NPC";}}
 static class CIOClient {L1PcInstance pc;CIOClient(L1PcInstance p){pc=p;}L1PcInstance getActiveChar(){return pc;}}
 static class NpcLocalCommands {static void poly(CIOClient c,int n){c.pc.morph=n;c.pc.log.add("morph:"+n);}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int n,String s){ck(n==7);p.log.add("html:"+s);}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.log.add("error");}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int n,String s){ck(n==337);p.log.add("error");}static void send(L1PcInstance p,int n,String a,String b){ck(n==143&&p.released&&a.equals("NPC"));p.log.add(b);}}
 static class SC_SkillSound {static void broadcast(L1PcInstance p,int id,int s){p.log.add("sound:"+s);}}
 static class L1SkillUse {static final int TYPE_GMBUFF=1; static void cast(L1PcInstance p,int s,int id,int x,int y,Object n,int v,int t){p.skills.add(s);p.log.add("cast:"+s);}}
 // Model of receipt publication/rollback. No actual SQL tested here.
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Map<Integer,Integer> stage; int debits,grants,step=-1,charge=-1;Runnable ok,fail;boolean submitted;L1InventoryTransaction(L1PcInstance p){this.p=p;stage=new HashMap<>(p.inv.m);}static L1InventoryTransaction begin(L1PcInstance p,int id){if(p.mode==1)throw new IllegalStateException();return new L1InventoryTransaction(p);}boolean debit(int id,int n){debits++;if(p.mode==2||p.mode==6&&debits==2||stage.getOrDefault(id,0)<n)return false;stage.merge(id,-n,Integer::sum);return true;}L1ItemInstance grant(int id,int n){grants++;if(p.mode==3||p.mode==6&&grants==2)return null;stage.merge(id,n,Integer::sum);return new L1ItemInstance(id);}void changeChargeCount(L1ItemInstance i,int n){charge=n;}void onFailure(Runnable f){fail=f;}void commitAfterInventoryRelease(Runnable r){if(p.mode==4)throw new IllegalStateException();submitted=true;ok=r;p.pending=this;}void complete(){p.pending=null;p.released=true;if(p.mode==5){fail.run();return;}p.inv.m=stage;if(charge>=0)p.inv.relic.charge=charge;ok.run();}public void close(){if(!submitted)p.released=true;}}
 static class NpcExchangeCommands {static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int id,int n){return t.debit(id,n);}static boolean debitRecipe(L1InventoryTransaction t,L1PcInstance p,int id,int n){return t.debit(id,n);}}
 static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int id,int n){return t.debit(id,n);}
 static void stageQuestStep(L1InventoryTransaction t,L1PcInstance p,int q,int from,int to,int r){ck(p.quest.step==from);t.step=to;}
'''+fields+'\n'+methods+r'''
 static void tower(int f,int mode,boolean upper,boolean missingToken,boolean missingAdena){L1PcInstance p=new L1PcInstance();p.mode=mode;int id=upper?40104+f:41158,n=upper?2:(f+1)*10;p.inv.put(id,n-(missingToken?1:0));p.inv.put(40308,missingAdena?299:300);Map<Integer,Integer> before=new HashMap<>(p.inv.m);if(upper)towerUpperFloor(p,7,900090+f);else ck(towerGate(p,7,String.valueOf((char)('a'+f))));ck(p.inv.m.equals(before));ck(p.log.stream().noneMatch(s->s.startsWith("move:")));boolean success=!missingToken&&(!upper||!missingAdena)&&mode!=1&&mode!=2&&mode!=4&&!(mode==6&&upper);ck((p.pending!=null)==success);if(p.pending==null)return;p.pending.complete();if(mode==5){ck(p.inv.m.equals(before));ck(p.log.stream().noneMatch(s->s.startsWith("move:")));return;}ck(p.inv.m.get(id)==0);ck(p.inv.m.get(40308)==(upper?0:before.get(40308)));int[] x={32754,32754,32754,32754,32635,32637,32637,32637,32732};int[] y={32747,32747,32747,32747,32790,32791,32791,32791,32857};ck(p.log.equals(List.of("move:"+(upper?x[f]:TOWER_X[f])+":"+(upper?y[f]:TOWER_Y[f])+":"+(upper?120:110+f*10)+":"+(upper?3:5))));}
 static void feather(int mask,int active,boolean reject){L1PcInstance p=new L1PcInstance();p.inv.failConsume=reject;for(int i=0;i<4;i++){if((mask&(1<<i))!=0)p.inv.put(410057+i,1);if((active&(1<<i))!=0)p.skills.add(101+i);}Set<Integer> before=new HashSet<>(p.skills);Map<Integer,Integer> pre=new HashMap<>(p.inv.m);String ans=featherBuff(p);if(mask==0||reject){ck(ans.equals("suschef4")&&p.log.isEmpty()&&p.skills.equals(before)&&p.inv.m.equals(pre));return;}int i=Integer.numberOfTrailingZeros(mask);ck(ans.equals("")&&p.skills.equals(Set.of(101+i))&&p.inv.m.get(410057+i)==0);List<String> log=new ArrayList<>();int[][] order={{1,2,3},{0,2,3},{1,0,3},{1,2,0}};for(int j:order[i])if(before.contains(101+j))log.add("remove:"+(101+j));log.add("cast:"+(101+i));log.add("sound:"+(7947+i));ck(log.equals(p.log));}
 static void spy(boolean shadow,int step,int mode,boolean reset,int mask){L1PcInstance p=new L1PcInstance();p.mode=mode;p.quest.step=step;int first=shadow?41123:41132;for(int i=0;i<7;i++)if((mask&(1<<i))!=0)p.inv.put(first+i,2);Map<Integer,Integer> before=new HashMap<>(p.inv.m);if(reset)spyReset(p,new L1NpcInstance(),shadow);else spyStart(p,new CIOClient(p),new L1NpcInstance(),shadow);ck(p.inv.m.equals(before)&&p.quest.step==step);if(step==255||(!reset&&step!=0)){ck(p.pending==null);ck(p.morph==(!reset&&step==1?(shadow?6035:6034):0));return;}if(p.pending==null){ck(mode==1||mode==4||!reset&&(mode==3||mode==6)||reset&&(mode==2&&mask!=0||mode==6&&Integer.bitCount(mask)>1));return;}ck(p.morph==0);p.pending.complete();if(mode==5){ck(p.inv.m.equals(before)&&p.quest.step==step&&p.morph==0);return;}ck(p.quest.step==(reset?0:1));for(int i=0;i<7;i++)ck(p.inv.m.getOrDefault(first+i,0)==before.getOrDefault(first+i,0)+(reset?((mask&(1<<i))!=0?-1:0):(i<3?1:0)));ck(p.morph==(reset?0:shadow?6035:6034));}
 static void relic(int mode,boolean crown,boolean has,boolean funds,int charge){L1PcInstance p=new L1PcInstance();p.mode=mode;p.crown=crown;p.inv.put(20383,has?1:0);p.inv.put(40308,funds?100000:99999);p.inv.relic.charge=charge;Map<Integer,Integer> before=new HashMap<>(p.inv.m);rechargeCrownRelic(p,7);ck(p.inv.m.equals(before)&&p.inv.relic.charge==charge);boolean pending=crown&&has&&funds&&charge!=50&&mode!=1&&mode!=2&&mode!=4;ck((p.pending!=null)==pending);if(!pending)return;p.pending.complete();if(mode==5){ck(p.inv.m.equals(before)&&p.inv.relic.charge==charge);return;}ck(p.inv.relic.charge==50&&p.inv.m.get(40308)==0&&p.log.equals(List.of("html:")));}
 public static void main(String[] args){for(int m=0;m<=6;m++)for(int f=0;f<10;f++)for(boolean t:new boolean[]{false,true})for(boolean a:new boolean[]{false,true}){tower(f,m,false,t,a);if(f<9)tower(f,m,true,t,a);}for(int c=0;c<=65535;c++){String s=String.valueOf((char)c);boolean expected=false;for(char v='a';v<='j';v++)expected|=s.equalsIgnoreCase(String.valueOf(v));L1PcInstance p=new L1PcInstance();ck(towerGate(p,7,s)==(expected||s.equals("1")));}for(int mask=0;mask<16;mask++)for(int a=0;a<16;a++)for(boolean r:new boolean[]{false,true})feather(mask,a,r);for(boolean s:new boolean[]{false,true})for(int q:new int[]{0,1,255})for(int m=0;m<=6;m++)for(boolean r:new boolean[]{false,true})for(int mask=0;mask<128;mask++)spy(s,q,m,r,mask);for(int m=0;m<=6;m++)for(boolean c:new boolean[]{false,true})for(boolean h:new boolean[]{false,true})for(boolean f:new boolean[]{false,true})for(int n:new int[]{0,49,50,51})relic(m,c,h,f,n);System.out.println("PASS assertions="+checks);}
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Safety.java';p.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True);subprocess.run(['java','-cp',d,'Safety'],check=True)
