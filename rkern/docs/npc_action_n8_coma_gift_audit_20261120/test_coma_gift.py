from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=next((root/'src').rglob('NpcGameEventActions.java')).read_text()
body=s[s.index('    static void comaCheck('):s.index('\tstatic void selectComa(')]
q=next((root/'src').rglob('NpcQuestTransactions.java')).read_text()
body+=q[q.index('    static void grantMissingNpcItem('):q.index('    /** Free support grants')]
code='''import java.util.*;
public class Check {
static class L1PcInstance {
 int[] items=new int[5],sel=new int[5];Map<Integer,Integer> gifts=new HashMap<>(); int fail,attempts,mode,skill,duration,sound,broadcast,con,dex,str,sp,hit,ac,status,spmr,notices,grants; boolean busy; Runnable finish; String page;
 L1PcInstance getInventory(){return this;} L1PcInstance getAbility(){return this;} L1PcInstance getAC(){return this;} int getId(){return 100;}
 boolean checkItem(int id,int n){return (id>=3000022&&id<=3000026?items[id-3000022]:gifts.getOrDefault(id,0))>=n;}
 int getDeathMatchPiece(){return sel[0];} int getGhostHousePiece(){return sel[1];} int getPetRacePiece(){return sel[2];} int getPetMatchPiece(){return sel[3];} int getUltimateBattlePiece(){return sel[4];}
 void setDeathMatchPiece(int n){sel[0]=n;} void setGhostHousePiece(int n){sel[1]=n;} void setPetRacePiece(int n){sel[2]=n;} void setPetMatchPiece(int n){sel[3]=n;} void setUltimateBattlePiece(int n){sel[4]=n;}
 void removeSkillEffect(int id){if(skill==id)skill=0;} boolean hasSkillEffect(int id){return skill==id;} void setSkillEffect(int id,int d){skill=id;duration=d;}
 void addSp(int n){sp+=n;}void addAddedCon(int n){con+=n;}void addAddedDex(int n){dex+=n;}void addAddedStr(int n){str+=n;}void addHitRate(int n){hit+=n;}void addAc(int n){ac+=n;}
}
static class L1SkillId {static final int COMA_A=1,COMA_B=2;}
static class L1NpcInstance {int getId(){return 77;}int getNpcId(){return 7000021;} L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "Npc";}}
static class L1ItemInstance {L1ItemInstance getItem(){return this;}String getName(){return "Gift";}}
static class SC_NPCTalkReturn {static void send(L1PcInstance p,int i,String s){ck(i==77);p.page=s;}}
static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.notices++;}}
static class SC_ServerMessage {static void send(L1PcInstance p,int i,String a,String b){ck(i==143&&a.equals("Npc")&&b.equals("Gift"));p.grants++;}}
static class SC_OwnCharStatus {static void send(L1PcInstance p){p.status++;}}
static class SC_SPMR {static void send(L1PcInstance p){p.spmr++;}}
static class SC_SkillSound {static void send(L1PcInstance p,int i,int s){p.sound=s;}static void broadcast(L1PcInstance p,int i,int s){p.broadcast=s;}}
static class L1InventoryTransaction implements AutoCloseable {
 L1PcInstance p;int[] staged;Map<Integer,Integer> gifts;Runnable failure;boolean submitted;
 static L1InventoryTransaction begin(L1PcInstance p,int resource){if(p.busy)throw new IllegalStateException();var t=new L1InventoryTransaction();t.p=p;t.staged=p.items.clone();t.gifts=new HashMap<>(p.gifts);p.busy=true;return t;}
 boolean debit(int id,int n){int i=id-3000022;if(++p.attempts==p.fail||n<=0||staged[i]<n)return false;staged[i]-=n;return true;}
 L1ItemInstance grant(int id,int n){if(++p.attempts==p.fail)return null;gifts.merge(id,n,Integer::sum);return new L1ItemInstance();}
 void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){submitted=true;p.finish=()->{if(p.mode==2)return;p.busy=false;if(p.mode==1)failure.run();else{p.items=staged;p.gifts=gifts;r.run();}};}
 public void close(){if(!submitted)p.busy=false;}
}
'''+body+'''
static int checks,cases;static void ck(boolean ok){checks++;if(!ok)throw new AssertionError("case="+cases+" check="+checks);}
static void checkEffect(L1PcInstance p,int type,boolean already){int skill=type==3?1:2;ck(p.skill==skill);ck(p.duration==(type==3?3600000:7200000));ck(p.sound==(type==3?7382:7383)&&p.broadcast==p.sound);ck(p.status==(already?0:1));ck(p.spmr==(!already&&type==5?1:0));ck(p.con==(already?0:type==3?1:3));ck(p.sp==(!already&&type==5?1:0));ck(p.str==(already?0:5)&&p.dex==p.str);ck(p.hit==(already?0:type==3?3:5));ck(p.ac==(already?0:type==3?-3:-8));ck("".equals(p.page));}
static void run(L1PcInstance p,int[] need,int type,boolean selected){cases++;int[] original=p.items.clone();int prior=p.skill;int positive=0;boolean enough=true;for(int i=0;i<5;i++){if(need[i]>0)positive++;if(need[i]>original[i])enough=false;}if(selected)giveComaBuff(p,77);else comaCheck(p,type,77);ck(Arrays.equals(original,p.items));ck(p.skill==prior&&p.sound==0);if(selected)ck(Arrays.equals(p.sel,new int[5]));boolean submit=(type==3||type==5)&&enough&&(p.fail==0||p.fail>positive);ck((p.finish!=null)==submit);if(!submit){ck(!p.busy);ck(p.page!=null);return;}ck(p.busy&&p.page==null);p.sel[0]=2;p.finish.run();ck(p.sel[0]==2);if(p.mode==0){for(int i=0;i<5;i++)ck(p.items[i]==original[i]-need[i]);checkEffect(p,type,prior==(type==3?1:2));ck(!p.busy);}else{ck(Arrays.equals(original,p.items));ck(p.skill==prior&&p.sound==0);ck(p.busy==(p.mode==2));ck(p.mode==2?p.page==null:p.page!=null);}}
public static void main(String[] a){for(int mask=0;mask<32;mask++)for(int type:new int[]{3,5})for(int mode=0;mode<3;mode++)for(int fail=0;fail<=5;fail++)for(int prior=0;prior<3;prior++){var p=new L1PcInstance();p.mode=mode;p.fail=fail;p.skill=prior;int[] need=new int[5];int rem=type;for(int i=0;i<5;i++){p.items[i]=(mask>>i)&1;if(p.items[i]>0&&rem>0){need[i]=1;rem--;}}if(rem!=0){for(int i=0;i<5;i++)if(p.items[i]==0&&rem-->0)need[i]=1;}run(p,need,type,false);}
for(int v=0;v<7776;v++){int[] need=new int[5];int n=v,sum=0;for(int i=0;i<5;i++){need[i]=n%6;n/=6;sum+=need[i];}for(int mode=0;mode<3;mode++)for(int f=0;f<3;f++){var p=new L1PcInstance();p.sel=need.clone();Arrays.fill(p.items,5);p.mode=mode;p.fail=f;run(p,need,sum,true);}}
for(int id:new int[]{500211,200000})for(boolean held:new boolean[]{false,true})for(boolean fail:new boolean[]{false,true})for(boolean announce:new boolean[]{false,true})for(int mode=0;mode<3;mode++){cases++;var p=new L1PcInstance();p.mode=mode;p.fail=fail?1:0;if(held)p.gifts.put(id,1);grantMissingNpcItem(p,new L1NpcInstance(),id,"ok","already",announce);ck(p.gifts.getOrDefault(id,0)==(held?1:0));ck(p.grants==0);if(held||fail){ck(p.finish==null&&!p.busy);ck(held?"already".equals(p.page):p.notices==1);continue;}ck(p.finish!=null&&p.busy&&p.page==null);grantMissingNpcItem(p,new L1NpcInstance(),id,"ok","already",announce);ck(p.gifts.getOrDefault(id,0)==0);p.finish.run();ck(p.gifts.getOrDefault(id,0)==(mode==0?1:0));ck(p.grants==(mode==0&&announce?1:0));ck(mode==0?"ok".equals(p.page):p.page==null);ck(p.busy==(mode==2));}
System.out.println("cases="+cases+" checks="+checks);}}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Check.java';p.write_text(code);subprocess.run(['javac','-encoding','UTF-8',str(p)],check=True);subprocess.run(['java','-cp',d,'Check'],check=True)
