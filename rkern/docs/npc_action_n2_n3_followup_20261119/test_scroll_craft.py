"""JDK21 exact legacy vs current scroll commands with typed inventory/receipt doubles.
Usage: python test_scroll_craft.py BASELINE_ZIP [root]"""
from pathlib import Path
import zipfile,sys,tempfile,subprocess
root=Path(sys.argv[2]) if len(sys.argv)>2 else Path(__file__).resolve().parents[2]
p='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(sys.argv[1]) as z: old=z.read(p+'HtmlCmdHandler.java').decode()
old=old[old.index('\tprivate static String[] 빈줌제작('):].rstrip()[:-1]
old+='''static void handle(L1PcInstance pc,int objid,String cmd){String[] values=빈줌제작(cmd,pc); String html=젠도르(cmd,pc,objid); if(html!=null) SC_NPCTalkReturn.send(pc,objid,html,values);}'''
new=(root/p/'NpcSpellScrollCommands.java').read_text();new='\n'.join(l for l in new.splitlines() if not l.startswith(('package ','import '))).replace('final class NpcSpellScrollCommands','static final class NpcSpellScrollCommands')
java=r'''import java.util.*;
class ScrollTest {
 static class L1ItemInstance {int type;L1ItemInstance(int t){type=t;}L1ItemInstance getItem(){return this;}int getType1(){return type;}}
 static class L1PcInstance {int _x;int castle;L1ItemInstance weapon;Map<Integer,Integer> inv=new TreeMap<>(); List<String> trace=new ArrayList<>();boolean consumeOK=true,grantOK=true,submitOK=true;Runnable success,failure;int failDebit=-1, attempted;
 L1PcInstance(){for(int id:new int[]{40090,40091,40092,40093,40094,40308,40318})inv.put(id,1000000);}L1PcInstance getInventory(){return this;}int getId(){return 11;}int getX(){return 3;}int getY(){return 4;}int getClanid(){return castle;}L1ItemInstance getWeapon(){return weapon;}boolean checkItem(int id,int amount){return amount>0&&inv.getOrDefault(id,0)>=amount;}boolean consumeItem(int id,int amount){if(!consumeOK||!checkItem(id,amount))return false;inv.put(id,inv.get(id)-amount);return true;}void storeItem(int id,int amount){inv.merge(id,amount,Integer::sum);} }
 static class L1Clan {int getCastleId(){return 1;}}
 static class L1World {static L1World getInstance(){return new L1World();}L1Clan getClan(int id){return id==0?null:new L1Clan();}}
 static class L1SkillId {static int 성혈버프=999;}
 static class L1SkillUse {static int TYPE_GMBUFF=7;static void cast(L1PcInstance p,int s,int id,int x,int y,Object o,int n,int t){p.trace.add("skill:"+s);}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.trace.add("sys:"+s);}}
 static class SC_ChatPacket {static void sendDirect(L1PcInstance p,L1PcInstance other,String s){p.trace.add("chat:"+s);}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String html,String[] data){p.trace.add("html:"+html+":"+Arrays.toString(data));}}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Map<Integer,Integer> stage;Runnable failure;L1InventoryTransaction(L1PcInstance pc){p=pc;stage=new TreeMap<>(p.inv);}static L1InventoryTransaction begin(L1PcInstance p,int key){if(key!=900088)throw new AssertionError();return new L1InventoryTransaction(p);}boolean debit(int id,int count){if(++p.attempted==p.failDebit||count<=0||stage.getOrDefault(id,0)<count)return false;stage.merge(id,-count,Integer::sum);return true;}Object grant(int id,int n){if(!p.grantOK)return null;stage.merge(id,n,Integer::sum);return this;}void onFailure(Runnable cb){failure=cb;}void commitAfterInventoryRelease(Runnable cb){if(!p.submitOK)throw new IllegalStateException();p.success=()->{p.inv.clear();p.inv.putAll(stage);cb.run();};p.failure=failure;}public void close(){}}
 static class Legacy { OLD }
 NEW
 static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
 static void equal(L1PcInstance a,L1PcInstance b,String name){check(a._x==b._x&&a.inv.equals(b.inv)&&a.trace.equals(b.trace),name+" "+a.inv+" "+b.inv+" "+a.trace+" "+b.trace);}
 public static void main(String[] args){int n=0;for(String size:new String[]{"1","2","3","4","5"})for(String cmd:new String[]{"A","b","C","d","E","F","g","H","i","J","K","l","M","N","O","p","Q","R","s","T","U","V","W","X","y","ſ","ı","İ","K","unknown",""})for(int miss:new int[]{0,40090,40091,40092,40093,40094,40308,40318}){L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();if(miss!=0){a.inv.put(miss,0);b.inv.put(miss,0);}Legacy.handle(a,31,size);NpcSpellScrollCommands.handle(b,31,size);equal(a,b,"selection");Legacy.handle(a,31,cmd);Map<Integer,Integer> before=new TreeMap<>(b.inv);int traces=b.trace.size();NpcSpellScrollCommands.handle(b,31,cmd);if(b.success!=null){check(b.inv.equals(before)&&b.trace.size()==traces,"before commit");b.success.run();}equal(a,b,size+cmd+miss);n++;}System.out.println("Scroll legacy/current success and shortage cases="+n);
 n=0;for(String size:new String[]{"1","2","3","4","5"})for(char c='A';c<='Y';c++)for(int fail:new int[]{0,1,2,3,4,5}){L1PcInstance b=new L1PcInstance();NpcSpellScrollCommands.handle(b,7,size);Map<Integer,Integer> before=new TreeMap<>(b.inv);if(fail==1||fail==2)b.failDebit=fail;else if(fail==3)b.grantOK=false;else if(fail==4)b.submitOK=false;else if(fail==5&&(c=='S'||c=='T'))b.failDebit=3;NpcSpellScrollCommands.handle(b,7,""+c);check(b._x==0&&b.inv.equals(before),"precommit");if(b.success!=null)b.failure.run();check(b.inv.equals(before)&&b.trace.getLast().contains("bs_m2"),"failure "+c+fail);n++;}System.out.println("Scroll staged failure/replay mock cases="+n);
 n=0;for(String cmd:new String[]{"a","A","z","Z","other"})for(int type:new int[]{-1,0,20})for(int castle:new int[]{0,1})for(boolean have:new boolean[]{false,true}){L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();if(type>=0){a.weapon=new L1ItemInstance(type);b.weapon=new L1ItemInstance(type);}a.castle=b.castle=castle;if(!have){a.inv.put(40308,0);b.inv.put(40308,0);}Legacy.handle(a,1,cmd);NpcSpellScrollCommands.handle(b,1,cmd);equal(a,b,"buff");n++;}System.out.println("Buff and empty selection equivalence="+n);
 L1PcInstance b=new L1PcInstance();b.weapon=new L1ItemInstance(20);b.consumeOK=false;NpcSpellScrollCommands.handle(b,1,"a");check(b.trace.size()==1&&b.trace.getFirst().startsWith("sys:"),"buff debit failure");for(int amount:new int[]{-1,2,999,Integer.MAX_VALUE}){b=new L1PcInstance();b._x=amount;NpcSpellScrollCommands.handle(b,1,"A");check(b._x==0&&b.success==null&&b.trace.getLast().contains("bs_m2"),"invalid amount");}System.out.println("Additional invalid amount/buff failed debit=5");}
}
'''.replace(' OLD ',old).replace('\n NEW\n','\n'+new+'\n')
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp)/'ScrollTest.java';t.write_text(java);subprocess.run(['javac','--release','21',str(t)],check=True);subprocess.run(['java','-cp',tmp,'ScrollTest'],check=True)
