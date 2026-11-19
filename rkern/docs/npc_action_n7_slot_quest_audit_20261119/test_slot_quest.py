from pathlib import Path
import re, subprocess, tempfile, zipfile
ROOT=Path(__file__).resolve().parents[2]; P=ROOT/'src/l1j/server/server/model/network/core/cs/packet'
s=(P/'NpcQuestTransactions.java').read_text(); new=(P/'HtmlCmdHandler.java').read_text()
with zipfile.ZipFile('/mnt/data/RKern_NPC_Action_N7_Paid_Tower_Spy_Audit_FULL_SRC_20261119.zip') as z:
 old=z.read(str((P/'HtmlCmdHandler.java').relative_to(ROOT))).decode()
pat=r'get_npcId\(\)\s*==\s*(\d+)'; assert re.findall(pat,old)==re.findall(pat,new)
def extract(n):
 m=re.search(r'(?m)^\s*static (?:void|boolean) '+n+r'\([^)]*\)\s*\{',s);assert m,n
 i=s.index('{',m.start())+1;depth=1
 while depth:depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[m.start():i].strip()
methods='\n'.join(extract(n) for n in ['unlockEquipmentSlot','grantLegacyNpcItem','jpePotionExchange'])
# Independent old-slot cost oracle and routes.
block=old[old.index('get_npcId() == 5162)'):old.index('get_npcId() == 70614)')]
rows=re.findall(r'checkItem\((3000\d+), 1\) && pc.getInventory\(\).checkItem\(40308, (\d+)\) && pc.getLevel\(\) >= (\d+)',block)
assert rows==[('3000400','5000000','76'),('3000401','15000000','81'),('3000399','1000000','59'),('3000402','15000000','83'),('3000403','1000000','70')]
java=r'''import java.util.*;
public class Check {
 static int checks;static void ck(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
 static class L1ItemId {static final int ADENA=40308;}
 static class L1Quest {static final int QUEST_SLOT76=60,QUEST_SLOT81=61,QUEST_SLOT59=62,QUEST_SLOT_SHOULD=64,QUEST_SLOT_BADGE=65,QUEST_END=255;Map<Integer,Integer> steps=new HashMap<>();int get_step(int q){return steps.getOrDefault(q,0);}void applyCommittedStepIfLoaded(int q,int s){steps.put(q,s);}}
 static class Item {int id;Item(int id){this.id=id;}String getName(){return "item"+id;}}
 static class L1ItemInstance {Item item;L1ItemInstance(int id){item=new Item(id);}Item getItem(){return item;}}
 static class Inv {Map<Integer,Integer> m=new HashMap<>();boolean reject;boolean checkItem(int id,int n){return m.getOrDefault(id,0)>=n;}L1ItemInstance storeItem(int id,int n){if(reject)return null;m.merge(id,n,Integer::sum);return new L1ItemInstance(id);}}
 static class L1PcInstance {int mode,level;Inv inv=new Inv();L1Quest quest=new L1Quest();L1InventoryTransaction pending;List<String> out=new ArrayList<>();Inv getInventory(){return inv;}L1Quest getQuest(){return quest;}int getId(){return 8;}int getLevel(){return level;}}
 static class L1NpcInstance {int getId(){return 7;}L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "NPC";}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int n){p.out.add("msg:"+n);}static void send(L1PcInstance p,int n,String a,String b){ck(n==143&&a.equals("NPC"));p.out.add("reward:"+b);}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.out.add("error");}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page){p.out.add("html:"+id+":"+page);}}
 static class SC_ReturnedStat {static final int RING_RUNE_SLOT=67,SUBTYPE_RING=1,OPEN_SLOT_SHOULD=64,OPEN_SLOT_BADGE=128;static void sendSlot(L1PcInstance p,int t,int s,int v){p.out.add("slot:"+t+":"+s+":"+v);}}
 static class SC_SkillSound {static void send(L1PcInstance p,int id,int s){p.out.add("sound:"+s);}}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Map<Integer,Integer> stage;int debits;Runnable ok,fail;int quest=-1,expected;L1InventoryTransaction(L1PcInstance p){this.p=p;stage=new HashMap<>(p.inv.m);}static L1InventoryTransaction begin(L1PcInstance p,int id){if(p.mode==1||p.pending!=null)throw new IllegalStateException();return new L1InventoryTransaction(p);}boolean debit(int id,int n){debits++;if(p.mode==2||p.mode==6&&debits==2||stage.getOrDefault(id,0)<n)return false;stage.merge(id,-n,Integer::sum);return true;}L1ItemInstance grant(int id,int n){if(p.mode==3)return null;stage.merge(id,n,Integer::sum);return new L1ItemInstance(id);}void onFailure(Runnable r){fail=r;}void commitAfterInventoryRelease(Runnable r){if(p.mode==4)throw new IllegalStateException();p.pending=this;ok=r;}void complete(){p.pending=null;if(p.mode==5||quest!=-1&&p.quest.get_step(quest)!=expected){fail.run();return;}p.inv.m=stage;ok.run();}public void close(){}}
 static class NpcExchangeCommands {static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int id,int n){return t.debit(id,n);}}
 static void stageQuestStep(L1InventoryTransaction t,L1PcInstance p,int q,int from,int to,int r){ck(to==255&&r==0);t.quest=q;t.expected=from;}
'''+methods+r'''
 static void slot(int i,String cmd,int level,int funds,boolean coupon,int step,int mode){int[] quests={60,61,62,64,65},levels={76,81,59,83,70},prices={5000000,15000000,1000000,15000000,1000000},coupons={3000400,3000401,3000399,3000402,3000403},values={1,2,16,64,128};L1PcInstance p=new L1PcInstance();p.level=level;p.mode=mode;int q=quests[i];p.quest.steps.put(q,step);p.inv.m.put(40308,funds);p.inv.m.put(coupons[i],coupon?1:0);Map<Integer,Integer> before=new HashMap<>(p.inv.m);ck(unlockEquipmentSlot(p,cmd));ck(p.inv.m.equals(before)&&p.quest.get_step(q)==step);boolean discount=coupon&&funds>=prices[i];int price=discount?prices[i]:prices[i]*2;boolean submit=step!=255&&level>=levels[i]&&funds>=price&&mode!=1&&mode!=2&&mode!=4&&!(discount&&mode==6);ck((p.pending!=null)==submit);String fail="html:8:"+(i<2?"slot6":"slot11");if(!submit){ck(p.out.equals(List.of(step==255?"msg:3255":fail)));return;}ck(p.out.isEmpty());p.pending.complete();if(mode==5){ck(p.inv.m.equals(before)&&p.quest.get_step(q)==step&&p.out.equals(List.of(fail)));return;}ck(p.inv.m.get(40308)==funds-price&&p.inv.m.get(coupons[i])==(coupon&&!discount?1:0)&&p.quest.get_step(q)==255);String packet="slot:67:"+(1)+":"+values[i];String snd="sound:"+(i<2?12003:12004);ck(p.out.equals(i==2?List.of(packet,"html:8:slot9",snd):List.of("html:8:slot9",packet,snd)));ck(unlockEquipmentSlot(p,cmd)&&p.pending==null&&p.out.getLast().equals("msg:3255"));}
 static void potion(int n,int mode){L1PcInstance p=new L1PcInstance();p.mode=mode;p.inv.m.put(41213,n);Map<Integer,Integer> before=new HashMap<>(p.inv.m);jpePotionExchange(p,new L1NpcInstance());ck(p.inv.m.equals(before));boolean submitted=n>0&&mode!=1&&mode!=2&&mode!=3&&mode!=4;ck((p.pending!=null)==submitted);if(submitted){ck(p.out.isEmpty());p.pending.complete();if(mode==5){ck(p.inv.m.equals(before)&&p.out.equals(List.of("error")));}else{ck(p.inv.m.get(41213)==n-1&&p.inv.m.get(40029)==20&&p.out.equals(List.of("reward:item40029 (20)","html:7:")));}}}
 public static void main(String[]args){String[] cmds={"A","B","C","E","F"};int[] lev={76,81,59,83,70},pr={5000000,15000000,1000000,15000000,1000000};for(int i=0;i<5;i++)for(int l: new int[]{lev[i]-1,lev[i],lev[i]+1})for(int f:new int[]{0,pr[i]-1,pr[i],pr[i]*2-1,pr[i]*2,pr[i]*2+1})for(boolean c:new boolean[]{false,true})for(int st:new int[]{0,1,255})for(int m=0;m<=6;m++)slot(i,cmds[i],l,f,c,st,m);for(int c=0;c<=65535;c++){String cmd=String.valueOf((char)c);L1PcInstance p=new L1PcInstance();boolean expected=cmd.equals("A")||cmd.equals("B")||cmd.equals("C")||cmd.equals("D")||cmd.equalsIgnoreCase("E")||cmd.equalsIgnoreCase("F");ck(unlockEquipmentSlot(p,cmd)==expected);}for(int m=0;m<=6;m++)for(int n=0;n<3;n++)potion(n,m);for(boolean absent:new boolean[]{false,true})for(boolean only:new boolean[]{false,true})for(boolean reject:new boolean[]{false,true}){L1PcInstance p=new L1PcInstance();p.inv.m.put(41209,absent?0:1);p.inv.reject=reject;grantLegacyNpcItem(p,new L1NpcInstance(),41209,1,only,"page");boolean eligible=absent||!only;ck(p.inv.m.get(41209)==(absent?0:1)+(eligible&&!reject?1:0));ck(p.out.equals(!eligible?List.of():reject?List.of("error"):List.of("reward:item41209","html:7:page")));}L1PcInstance stale=new L1PcInstance();stale.level=80;stale.inv.m.put(40308,10000000);ck(unlockEquipmentSlot(stale,"A"));stale.quest.steps.put(60,255);stale.pending.complete();ck(stale.inv.m.get(40308)==10000000&&stale.out.equals(List.of("html:8:slot6")));System.out.println("PASS assertions="+checks);}
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Check.java';p.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True);subprocess.run(['java','-cp',d,'Check'],check=True)
