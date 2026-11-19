from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=next((root/'src').rglob('NpcQuestTransactions.java')).read_text()
body=s[s.index('    private static final int[][] MOONBOW_COSTS'):s.index('    static String 마르바(')]
code='''import java.util.*;
public class TestMoonbow {
static class L1Quest {static final int QUEST_MOONBOW=39;int step;int get_step(int q){return step;}void applyCommittedStepIfLoaded(int q,int n){step=n;}}
static class L1PcInstance {L1Quest q=new L1Quest();Map<Integer,Integer> items=new HashMap<>();String page;int notices,fail,attempts,mode;boolean busy;Runnable finish;L1Quest getQuest(){return q;}}
static class L1NpcInstance {int getId(){return 77;}}
static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page){if(id!=77)throw new AssertionError();p.page=page;}}
static class SC_SystemMessage {static void send(L1PcInstance p,String msg){p.notices++;}}
static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Map<Integer,Integer> staged;Runnable failure;int next;boolean submitted;
static L1InventoryTransaction begin(L1PcInstance p,int q){if(p.busy)throw new IllegalStateException();p.busy=true;var tx=new L1InventoryTransaction();tx.p=p;tx.staged=new HashMap<>(p.items);return tx;}
boolean debit(int id,int count){if(++p.attempts==p.fail||staged.getOrDefault(id,0)<count)return false;staged.merge(id,-count,Integer::sum);return true;}
Object grant(int id,int count){if(++p.attempts==p.fail)return null;staged.merge(id,count,Integer::sum);return new Object();}
void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){submitted=true;p.finish=()->{if(p.mode==2)return;p.busy=false;if(p.mode==1)failure.run();else{p.items=staged;r.run();}};}
public void close(){if(!submitted)p.busy=false;}}
static void stageQuestStep(L1InventoryTransaction tx,L1PcInstance pc,int q,int expected,int next,int reset){if(pc.q.step!=expected||reset!=0||q!=39)throw new AssertionError();tx.next=next;}
'''+body+'''
static int checks;static void ck(boolean x){checks++;if(!x)throw new AssertionError("check "+checks);}
static final String[] CMD={"A","B","A","B","C","D","C","E"};
static final boolean[] Z={false,false,true,true,true,true,false,false};
static final int[][] COST={{40068,1},{},{41348,1},{40048,10,40049,10,40050,10,40051,10},{40514,10,41353,1},{41349,1},{41346,1,41351,1,40651,30,40643,30,40645,30,40618,30,40676,30},{41350,1,41347,1,40491,30,40495,40,100,1,40509,12,40052,1,40053,1,40054,1,40055,1}};
static final int[][] REWARD={{},{41346,41348},{},{41353},{41354},{41351},{41350,41347},{205}};
static L1PcInstance pc(int i){var p=new L1PcInstance();p.q.step=i;for(int j=0;j<COST[i].length;j+=2)p.items.put(COST[i][j],COST[i][j+1]);return p;}
public static void main(String[] args){var npc=new L1NpcInstance();int cases=0;
for(int i=0;i<8;i++){
for(int step=0;step<8;step++)for(boolean z:new boolean[]{false,true})for(String cmd:new String[]{"A","B","C","D","E","a","b","","AA","Z"}){var p=pc(i);p.q.step=step;var before=new HashMap<>(p.items);moonbow(p,npc,cmd,z);int expected=-1;for(int j=0;j<8;j++)if(step==j&&Z[j]==z&&CMD[j].equals(cmd))expected=j;if(expected<0){ck(p.finish==null);ck(!p.busy);ck(p.items.equals(before));ck(p.q.step==step);}cases++;}
for(int fail=0;fail<=COST[i].length/2+REWARD[i].length;fail++)for(int mode=0;mode<3;mode++){var p=pc(i);p.fail=fail;p.mode=mode;var before=new HashMap<>(p.items);moonbow(p,npc,CMD[i],Z[i]);ck(p.q.step==i);ck(p.items.equals(before));if(fail>0){ck(p.finish==null);ck(!p.busy);ck(Objects.equals(p.page,MOONBOW_FAILURE[i]));}else{ck(p.finish!=null);ck(p.page==null);ck(p.busy);p.finish.run();if(mode==0){ck(p.q.step==((i+1)&7));ck(!p.busy);ck(Objects.equals(p.page,MOONBOW_SUCCESS[i]));for(int j=0;j<COST[i].length;j+=2)ck(p.items.get(COST[i][j])==0);for(int id:REWARD[i])ck(p.items.get(id)==1);p.finish=null;moonbow(p,npc,CMD[i],Z[i]);ck(p.finish==null);}else{ck(p.q.step==i);ck(p.items.equals(before));ck(p.busy==(mode==2));ck(mode==2?p.page==null:Objects.equals(p.page,MOONBOW_FAILURE[i]));}}cases++;}}
System.out.println("Moonbow scenarios="+cases+" checks="+checks);}}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'TestMoonbow.java';p.write_text(code);subprocess.run(['javac','-encoding','UTF-8',str(p)],check=True);subprocess.run(['java','-cp',d,'TestMoonbow'],check=True)
