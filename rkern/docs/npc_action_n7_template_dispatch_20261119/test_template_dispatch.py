from pathlib import Path
import re, zipfile, tempfile, subprocess, os
root=Path(__file__).resolve().parents[2] if 'docs' in str(Path(__file__).resolve()) else Path('/tmp/npcnext')
p=next((root/'src').rglob('NpcTemplateCommands.java')).parent
base=Path(os.environ.get('RKERN_PREVIOUS_ZIP','/mnt/data/RKern_NPC_Action_N7_Travel_Buff_Domain_Safety_FULL_SRC_20261119.zip'))
with zipfile.ZipFile(base) as z:
    orig=z.read(next(n for n in z.namelist() if n.endswith('/CS_NpcAcion2.java'))).decode()
new=(p/'NpcTemplateCommands.java').read_text()
# Compare all dispatch conditions and exact first matching route (except removed unreachable duplicate).
pat=re.compile(r'^\s{3}(?:\} else )?if \((.*?)\) \{[^\n]*$',re.M|re.S) # use tabs-only original
pat=re.compile(r'^\t\t\t(?:\} else )?if \((.*?)\) \{([^\n]*)\n',re.M|re.S)
oldc=[' '.join(m.group(1).split()) for m in pat.finditer(orig[orig.index('if (npcid == 200201)')-3:orig.index('} catch (Exception e)')])]
newc=[' '.join(m.group(1).split()) for m in re.finditer(r'^            (?:else )?if \((.*?)\) \{\n',new,re.M|re.S)]
assert len(oldc)==55 and len(newc)==54
assert newc==oldc[:36]+oldc[37:] and oldc[17]==oldc[36]
# All existing delegated calls except newly moved methods must still appear unchanged.
oldcalls=re.findall(r'return (Npc\w+\.\w+\([^;]+\));',orig)
for c in oldcalls:
    assert c in new or any(c in (p/n).read_text() for n in ['NpcGameEventActions.java','NpcExchangeCommands.java']), c
assert not any('CS_NpcAcion2' in f.read_text() for f in (root/'src').rglob('*.java'))
assert 'NpcTemplateCommands.dispatch(pc, obj, s, htmlid)' in (p/'NpcEventHtmlCommands.java').read_text()
# Actual source methods extracted without substitutions; compile with typed stubs.
def extract(file,name):
    s=(p/file).read_text(); start=s.index('    static String '+name+'('); a=s.index('{',start); depth=1; i=a+1
    while depth:
        if s[i]=='{': depth+=1
        elif s[i]=='}': depth-=1
        i+=1
    return s[start:i]
methods='\n'.join([extract('NpcQuestTransactions.java','hamo'),extract('NpcTravelCommands.java','orimLaboratory'),extract('NpcGameEventActions.java','valakasReady'),extract('NpcGameEventActions.java','valakasRaid')])
java='''public class FinalNpcTest {
static int n; static void ck(boolean b){n++; if(!b)throw new AssertionError("case "+n);}
static class L1Quest {static final int QUEST_HAMO=7, QUEST_END=255; int step;int get_step(int id){return step;} void applyCommittedStepIfLoaded(int id,int val){step=val;}}
static class Inventory {boolean hasPouch,hasOrb,hasMoney,hasSword,consume=true,store=true;int debits,stores;boolean checkItem(int id){return checkItem(id,1);}boolean checkItem(int id,int n){return switch(id){case 820000->hasPouch;case 410096->hasOrb;case 40308->hasMoney;case 203003->hasSword;default->false;};}boolean consumeItem(int id,int count){debits++;return consume;}Object storeItem(int id,int count){stores++;return store?new Object():null;}}
static class L1PcInstance {int lv=60,tp,msg,grants,calls,ready,raid;String page;Inventory inv=new Inventory();L1Quest q=new L1Quest();int getLevel(){return lv;}Inventory getInventory(){return inv;}L1Quest getQuest(){return q;}int getHeading(){return 0;}void start_teleport(int a,int b,int c,int d,int e,boolean f,boolean g){tp++;}}
static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.msg++;}} static class SC_NPCTalkReturn{static void send(L1PcInstance p,int id,String page){p.page=page;}}
static int mode,readyCount,raidCount; static Runnable pending,failed;static class L1InventoryTransaction implements AutoCloseable{L1PcInstance p;int grant,stage,debit;L1InventoryTransaction(L1PcInstance pc){p=pc;}static L1InventoryTransaction begin(L1PcInstance p,int key){p.calls++;if(mode==5)throw new IllegalStateException();return new L1InventoryTransaction(p);}Object grant(int id,int n){if(mode==1)return null;grant++;return new Object();}void onFailure(Runnable r){failed=r;}void commitAfterInventoryRelease(Runnable r){if(mode==3){failed.run();return;}if(mode==4){pending=r;return;}p.grants+=grant;r.run();}public void close(){}}
static void stageQuestStep(L1InventoryTransaction t,L1PcInstance p,int q,int a,int b,int c){t.stage++;}
static class NpcExchangeCommands{static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int id,int n){t.debit++;return mode!=1 && !(mode==2 && t.debit==2);}}
static L1PcInstance current; static class ValakasReadyStart{static ValakasReadyStart getInstance(){return new ValakasReadyStart();}int countReadyRaid(){return readyCount;}void startReady(L1PcInstance p){p.ready++;}}
static class ValakasRoomSystem{static ValakasRoomSystem getInstance(){return new ValakasRoomSystem();}int countStartRaid(){return raidCount;}void startRaid(L1PcInstance p){p.raid++;}}
'''+methods+'''
public static void main(String[] args){
for(int lvl:new int[]{59,60,61})for(int step:new int[]{0,1,254,255})for(boolean pouch:new boolean[]{false,true})for(int m=0;m<=5;m++)for(String s:new String[]{"a","A","b",""}){
mode=m;pending=null;failed=null;L1PcInstance p=new L1PcInstance();p.lv=lvl;p.q.step=step;p.inv.hasPouch=pouch;String r=hamo(p,1,s,"tail");
if(!s.equalsIgnoreCase("a")){ck("tail".equals(r)&&p.calls==0&&p.q.step==step);continue;}
if(lvl<60){ck("hamo3".equals(r)&&p.msg==1&&p.calls==0);continue;}
if(pouch||step==255){ck("hamo1".equals(r)&&p.calls==0);continue;}
if(m==1||m==5){ck("tail".equals(r)&&p.q.step==step&&p.grants==0);continue;}
ck(r==null);if(m==3){ck(p.q.step==step&&p.page==null&&p.grants==0);}else if(m==4){ck(p.q.step==step&&p.page==null&&pending!=null);pending.run();ck(p.q.step==255&&"".equals(p.page));}else{ck(p.q.step==255&&p.grants==1&&"".equals(p.page));}}
for(boolean orb:new boolean[]{false,true})for(boolean cash:new boolean[]{false,true})for(int m=0;m<=5;m++){mode=m;pending=null;L1PcInstance p=new L1PcInstance();p.inv.hasOrb=orb;p.inv.hasMoney=cash;String r=orimLaboratory(p,1,"tail");if(!orb||!cash||m==1||m==2||m==5){ck("j_html02".equals(r)&&p.tp==0);}else if(m==3){ck(r==null&&p.tp==0&&"j_html02".equals(p.page));}else if(m==4){ck(r==null&&p.tp==0&&pending!=null);pending.run();ck(p.tp==1&&"".equals(p.page));}else ck(r==null&&p.tp==1&&"".equals(p.page));}
for(int lvl:new int[]{59,60,61})for(int room:new int[]{0,48,49,99})for(boolean consume:new boolean[]{false,true})for(String s:new String[]{"a","A","x"}){readyCount=room;L1PcInstance p=new L1PcInstance();p.lv=lvl;p.inv.consume=consume;String r=valakasReady(p,s,"tail");boolean valid=s.equalsIgnoreCase("a")&&lvl>=60&&room<49;ck(p.inv.debits==(valid?1:0)&&p.ready==(valid&&consume?1:0));ck(r.equals(!s.equalsIgnoreCase("a")?"tail":lvl<60?"eldnas3":valid&&!consume?"eldnas1":"tail"));}
for(int lvl:new int[]{59,60,61})for(int room:new int[]{0,48,49,99})for(boolean sword:new boolean[]{false,true})for(boolean store:new boolean[]{false,true})for(String s:new String[]{"enter","ENTER","x"}){raidCount=room;L1PcInstance p=new L1PcInstance();p.lv=lvl;p.inv.hasSword=sword;p.inv.store=store;String r=valakasRaid(p,s,"tail");boolean valid=s.equalsIgnoreCase("enter")&&lvl>=60&&room<49&&!sword;ck(p.inv.stores==(valid?1:0)&&p.raid==(valid&&store?1:0));ck(r.equals(s.equalsIgnoreCase("enter")&&lvl<60?"fd_death2":"tail"));}
System.out.println("ACTUAL_METHOD_ASSERTIONS="+n);}}
'''
with tempfile.TemporaryDirectory() as d:
    f=Path(d)/'FinalNpcTest.java'; f.write_text(java); subprocess.run(['javac','-encoding','UTF-8',str(f)],check=True); subprocess.run(['java','-cp',d,'FinalNpcTest'],check=True)
print('ROUTES=55 original, 54 reachable branches; existing calls preserved; legacy references=0')
