"""Execute original/current boss dispatch and current Ekins receipt methods with typed stubs.
No MySQL or actual game runtime; castle branch text comparison is separate.
Usage: python test_events_experience.py BASELINE_ZIP
"""
from pathlib import Path
import re,subprocess,tempfile,zipfile,sys
root=Path(__file__).resolve().parents[2]; base='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(sys.argv[1]) as z: old=z.read(base+'CS_NpcAcion2.java').decode()
source=lambda name:(root/base/name).read_text()
new=source('CS_NpcAcion2.java'); ev=source('NpcGameEventActions.java'); exp=source('NpcExperienceCommands.java'); castle=source('NpcCastleActions.java')
assert 'return NpcGameEventActions.bossKey(pc, (L1NpcInstance) obj, s, htmlid, _random);' in new
assert 'return NpcCastleActions.catapult(pc, obj, s, htmlid);' in new
assert 'return NpcExperienceCommands.ekins(pc, obj.getId(), s, htmlid);' in new
assert 'npcid == 7)' not in new and 'npcid == 9)' not in new
# Extract exact original boss handler. The fake spawn() records events, not real NPC state.
start=old.index('L1NpcInstance npc = (L1NpcInstance) obj;',old.index('== 7000080)')); stop=old.index('// 낡은책더미',start)
oldboss=old[start:stop].replace('L1NpcInstance npc = (L1NpcInstance) obj;','').replace('_random','random')
newboss=ev[ev.index('private static final String[] BOSS_COMMANDS'):ev.index('\n\tprivate static void spawn(')]
start=old.index('private void 에킨스경험치2('); stop=old.index('private boolean createNewItem(',start)
oldexp=old[start:stop].replace('private void','private static void'); newexp=exp[exp.index('private static void 에킨스경험치2('):exp.rindex('}')]
assert oldexp.strip()==newexp.strip(), 'EXP formulas were changed'
# Original catapult branch retained except documented invalid castle guard.
start=old.index('|| npcid == 7000087) {')+len('|| npcid == 7000087) {'); stop=old.index('\n\t\t\t}\n\t\t} catch (Exception e)',start)
oldcastle=old[start:stop].replace('boolean isNowWar = false;\n\t\t\t\t\tisNowWar = CastleWarBusiness.getInstance().isNowWar(castleid);','boolean isNowWar = castleid != 0 && CastleWarBusiness.getInstance().isNowWar(castleid);')
assert oldcastle in castle
oldhelper=old[old.index('private void getShellDmg('):old.rindex('\n}')].replace('private void getShellDmg','private static void getShellDmg').replace('for (L1Object object : L1World.getInstance().getVisibleObjects(effect, 3)) {','if (effect == null) return;\n\t\tfor (L1Object object : L1World.getInstance().getVisibleObjects(effect, 3)) {')
assert oldhelper.strip() in castle
ex=exp[exp.index('static String ekins('):exp.index('private static boolean 봉인템(')]
java=r'''
import java.util.*; import java.util.logging.*;
public class EventTest {
 static final Logger LOG=Logger.getLogger("test"); static L1PcInstance current;
 static class Config {static String mobboss="101, 102, 103";}
 static class TYUtil {static int called;static int[] GetIntTokenizeIntArray(String v){called++;String temp=""; StringTokenizer values=new StringTokenizer(v," "); while(values.hasMoreElements())temp+=values.nextToken(); StringTokenizer list=new StringTokenizer(temp,","); ArrayList<Integer> a=new ArrayList<>(); while(list.hasMoreElements())a.add(Integer.valueOf(list.nextToken())); return a.stream().mapToInt(Integer::intValue).toArray();}}
 static class NpcTable {static final NpcTable I=new NpcTable();static int missing=-1;static NpcTable getInstance(){return I;}Object getTemplate(int id){return id==missing?null:this;}}
 static class L1NpcInstance {}
 static class Inv {Map<Integer,Integer> m=new TreeMap<>();boolean consumeItem(int id,int count){if(!checkItem(id,count))return false;m.put(id,m.get(id)-count);return true;}boolean checkItem(int id,int count){return m.getOrDefault(id,0)>=count;}}
 static class L1PcInstance {Inv inv=new Inv();int level, xp, effects,flags;Runnable success,fail;StringBuilder trace=new StringBuilder();Inv getInventory(){return inv;}int getMapId(){return 4;}int getId(){return 9;}int getLevel(){return level;}void addExp(int x){xp+=x;}}
 static class L1SpawnUtil {static void spawn2(int x,int y,short map,int id,int range,int ms,int movemap){current.trace.append("spawn:").append(id).append(":").append(x).append(":").append(y).append(":").append(ms).append(";");}}
 static void spawn(int x,int y,short map,int head,int id,int range){current.trace.append("random:").append(id).append(";");}
 static class SC_NpcChatPacket {static void sendNpc(L1PcInstance pc,L1NpcInstance npc,String msg,int i){pc.trace.append(msg).append(";");}}
 static class ExpTable {static int getNeedExpNextLevel(int i){return 1234567;}static double getPenaltyRate(int level){return 0.00123;}}
 static class SC_SkillSound {static void broadcast(L1PcInstance p,int id,int sound){p.effects++;}static void send(L1PcInstance p,int id,int sound){p.effects++;}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page){p.trace.append("page:").append(page);}}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Map<Integer,Integer> stage=new TreeMap<>();Runnable failure;static L1InventoryTransaction begin(L1PcInstance p,int key){if((p.flags&1)!=0)throw new IllegalStateException();return new L1InventoryTransaction(p);}L1InventoryTransaction(L1PcInstance p){this.p=p;stage.putAll(p.inv.m);}boolean debit(int i,int n){if((p.flags&2)!=0||stage.getOrDefault(i,0)<n)return false;stage.put(i,stage.get(i)-n);return true;}Object grant(int i,int n){if((p.flags&4)!=0)return null;stage.merge(i,n,Integer::sum);return this;}void onFailure(Runnable f){failure=f;}void commitAfterInventoryRelease(Runnable r){p.success=()->{p.inv.m.clear();p.inv.m.putAll(stage);r.run();};p.fail=failure;}public void close(){}}
 static class NpcExchangeCommands {static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int item,int count){return t.debit(item,count);}}
 static String oldBoss(L1PcInstance pc,L1NpcInstance npc,String s,String htmlid,Random random){OLDBOSS return htmlid;}
 NEWBOSS
 NEWEXP
 EKINS
 static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
 static L1PcInstance funded(int mask){L1PcInstance p=new L1PcInstance();for(int i=80450;i<=80479;i++)p.inv.m.put(i,((mask >>> (i%7))&1));return p;}
 public static void main(String[] args){LOG.setLevel(Level.OFF);int cases=0; L1NpcInstance npc=new L1NpcInstance();
  List<String> cmds=new ArrayList<>(List.of("AA","aa","VV","vv","","unknown","İ","ı","K")); for(char c='A';c<='Z';c++){cmds.add(""+c);cmds.add(""+Character.toLowerCase(c));}
  for(String s:cmds)for(int mask=0;mask<128;mask++)for(String tail:new String[]{null,"tail"}){
   L1PcInstance a=funded(mask), b=funded(mask);current=a;String x=oldBoss(a,npc,s,tail,new Random(mask));current=b;String y=bossKey(b,npc,s,tail,new Random(mask));ck(Objects.equals(x,y)&&a.inv.m.equals(b.inv.m)&&a.trace.toString().equals(b.trace.toString()),"boss mismatch "+s+" "+mask);cases++;
  }
  System.out.println("Boss original/current equivalence: "+cases);
  int guard=0;for(String invalid:new String[]{null,""," ","bad","101,BAD","101,102"}) {Config.mobboss=invalid;NpcTable.missing=102;L1PcInstance p=funded(127);current=p;var before=new TreeMap<>(p.inv.m);ck("bosskey10".equals(bossKey(p,npc,"AA","tail",new Random(5)))&&p.inv.m.equals(before)&&p.trace.isEmpty(),"invalid config debited");guard++;}
  Config.mobboss="bad";NpcTable.missing=-1;TYUtil.called=0;L1PcInstance p=funded(127);current=p;bossKey(p,npc,"A","tail",new Random(1));ck(TYUtil.called==0&&p.trace.toString().contains("spawn:5020")&&p.trace.toString().contains("spawn:7123"),"fixed command config independence");guard++;
  NpcTable.missing=5020;p=funded(127);current=p;ck("bosskey10".equals(bossKey(p,npc,"A","tail",new Random(1)))&&p.inv.m.get(80466)==1&&p.inv.m.get(80456)==0,"missing template guard");guard++; System.out.println("Boss invalid configuration/IDs: "+guard);
  int ec=0;for(String cmd:new String[]{"d","c","b","a","D","C","B","A","x"})for(int lv:new int[]{1,60,61,65,66,70,71,75,76,99})for(int have=0;have<4;have++)for(int flags=0;flags<8;flags++)for(boolean commit:new boolean[]{true,false})for(String tail:new String[]{null,"tail"}){
   L1PcInstance q=new L1PcInstance();q.level=lv;q.flags=flags;String s=cmd.toLowerCase(Locale.ROOT);int cat=s.equals("a")?810001:s.equals("b")?810002:3000215;int n=s.equals("d")?8:s.equals("c")?5:1;boolean large=s.equals("d")||s.equals("b");q.inv.m.put(cat,have&1);q.inv.m.put(1000004,(have>>1)&1);var before=new TreeMap<>(q.inv.m);String page=ekins(q,12,cmd,tail);boolean match=!s.equals("x");boolean ok=match&&(have&1)!=0&&(!s.equals("d")||(have&2)!=0)&&flags==0;
   ck(q.inv.m.equals(before)&&q.xp==0&&q.effects==0,"precommit side effect");ck((q.success!=null)==ok,"ekins admission");if(ok){ck(page==null,"early page");if(commit){q.success.run();L1PcInstance expected=new L1PcInstance();expected.level=lv;if(large) 에킨스경험치6(expected);else 에킨스경험치2(expected);ck(q.xp==expected.xp&&q.effects==2&&q.inv.m.getOrDefault(810010,0)==n&&q.inv.m.get(cat)==0&&(!s.equals("d")||q.inv.m.get(1000004)==0),"ekins success");ck(q.trace.toString().equals(tail==null?"":"page:"+tail),"ekins page");}else{q.fail.run();ck(q.inv.m.equals(before)&&q.xp==0&&q.effects==0&&q.trace.toString().equals("page:ekins5"),"ekins rollback");}}else ck(Objects.equals(page,match?"ekins5":tail),"reject page");ec++;
  }System.out.println("Ekins admission/commit/rollback: "+ec);
 }
}
'''.replace('OLDBOSS',oldboss).replace('NEWBOSS',newboss).replace('NEWEXP',newexp).replace('EKINS',ex)
with tempfile.TemporaryDirectory() as path:
 p=Path(path)/'EventTest.java';p.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True);subprocess.run(['java','-cp',path,'EventTest'],check=True)
print('Catapult source equivalence except 2 documented guards; Ekins EXP formula equality; removed NPC routes stay removed.')
