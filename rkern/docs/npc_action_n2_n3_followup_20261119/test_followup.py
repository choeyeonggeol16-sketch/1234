"""JDK21 extracted production-method tests using typed doubles, not live server tests.
Usage: python test_followup.py BASELINE_ZIP [root]
"""
from pathlib import Path
import re,sys,zipfile,tempfile,subprocess
root=Path(sys.argv[2]) if len(sys.argv)>2 else Path(__file__).resolve().parents[2]
p='src/l1j/server/server/model/network/core/cs/packet/'
def read(n): return (root/p/(n+'.java')).read_text()
with zipfile.ZipFile(sys.argv[1]) as z:
 old=z.read(p+'HtmlCmdHandler.java').decode(); sup=z.read(p+'CS_NpcAcion2.java').decode(); local=z.read(p+'NpcLocalCommands.java').decode()
menu=read('NpcPlayerMenuHandler'); travel=read('NpcTravelCommands'); quest=read('NpcQuestTransactions'); now=read('NpcLocalCommands')
def strip(s):return '\n'.join(l for l in s.splitlines() if not l.startswith(('package ','import ')))
oldmenu=old[old.index('\tprivate static final String[] summonstr_list'):old.index('\n\tprivate static void poly(')]; oldmenu=oldmenu.replace('private static void summonMonster','static void summonMonster');
lo=sup.index('\t\t\t} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 50024)'); hi=sup.index('\t\t\t} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 5126)',lo); block=sup[lo:hi]; ids=re.findall(r'get_npcId\(\) == (\d+)\) \{',block)
# Preserve exact original branch text, with only NPC fixture lookup substituted.
oldtravel=block.replace('} else if (','} else if (',1).replace('((L1NpcInstance) obj).getNpcTemplate().get_npcId()','npcId'); oldtravel='if (false) {'+oldtravel+'}\nreturn htmlid;'
q=quest[quest.index('    static String exchangeProtonGun'):quest.index('    /** NPC 71198')].replace('l1j.server.server.model.L1InventoryTransaction','L1InventoryTransaction')
xml=(root/'src/l1j/server/server/model/npc/action/L1NpcMakeItemAction.java').read_text(); arithmetic=re.search(r'private static boolean validExpandedAmount\(.*?\n    }',xml,re.S).group();
assert 'NpcQuestTransactions.exchangeProtonGun(pc, obj.getId(), s, htmlid)' in read('CS_NpcAcion2')
assert 'NpcTravelCommands.pcRoomTeleport(pc, npcid, s, htmlid)' in read('CS_NpcAcion2')
assert 'NpcPlayerMenuHandler.handle(pc, target, s)' in read('HtmlCmdHandler')
assert 'EMPTY_ARGS' in xml and 'weight += (long)' in xml
java=r'''import java.util.*;
class Followup {
 static class L1PcInstance { boolean magic,PC방_버프=true,available=true,debitOK=true,grantOK=true,commitThrow=false; int level=80,cha=20,id=7,material=100,reward=0; String trace=""; Map<Integer,L1NpcInstance> pets=new HashMap<>(); Runnable success,fail; int heading=3;
 int getId(){return id;} int getLevel(){return level;} int getTotalCha(){return cha;} L1PcInstance getAbility(){return this;} Map<Integer,L1NpcInstance> getPetList(){return pets;} boolean isMagicItem(){return magic;} void setMagicItem(boolean x){magic=x;trace+="flag;";} L1PcInstance getInventory(){return this;} boolean checkItem(int i,int n){trace+="check:"+i+":"+n+";";return available;} boolean consumeItem(int i,int n){trace+="debit:"+i+":"+n+";";return debitOK;} int getHeading(){return heading;} void start_teleport(int x,int y,int map,int h,int e,boolean a,boolean b){trace+="move:"+x+":"+y+":"+map+":"+h+":"+e+":"+a+":"+b+";";}}
 static class L1NpcInstance{int cost; L1NpcInstance(int c){cost=c;} int getPetcost(){return cost;}}
 static class L1Npc {int id;L1Npc(int i){id=i;}}
 static class NpcTable {static boolean missing;static NpcTable getInstance(){return new NpcTable();} L1Npc getTemplate(int id){return missing?null:new L1Npc(id);}}
 static class L1SummonInstance {L1PcInstance p; L1SummonInstance(L1Npc n,L1PcInstance pc){p=pc;pc.trace+="summon:"+n.id+";";}void setPetcost(int c){p.trace+="cost:"+c+";";}}
 static class L1PolyMorph {static void MagicBookPoly(L1PcInstance p,String s,int t){p.trace+="book:"+s+":"+t+";";}static void handleCommands(L1PcInstance p,String s){p.trace+="poly:"+s+";";}}
 static class SC_ServerMessage{static void send(L1PcInstance p,int m){p.trace+="msg:"+m+";";}}
 static class SC_CloseList{static void send(L1PcInstance p,int i){p.trace+="close:"+i+";";}}
 static class SC_SystemMessage{static void send(L1PcInstance p,String m){p.trace+="msg:"+m+";";}}
 static class SC_NPCTalkReturn{static void send(L1PcInstance p,int i,String m){p.trace+="page:"+i+":"+m+";";}}
 static class L1InventoryTransaction implements AutoCloseable{ L1PcInstance p;int d,g;Runnable failure; L1InventoryTransaction(L1PcInstance p){this.p=p;} static L1InventoryTransaction begin(L1PcInstance p,int k){if(k!=535)throw new AssertionError();return new L1InventoryTransaction(p);} boolean debit(int id,int n){if(id!=3000506)throw new AssertionError();if(!p.debitOK||p.material<n)return false;d=n;return true;} Object grant(int id,int n){if(id!=3000246)throw new AssertionError();if(!p.grantOK)return null;g=n;return this;}void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){if(p.commitThrow)throw new IllegalStateException();p.success=()->{p.material-=d;p.reward+=g;r.run();};p.fail=failure;}public void close(){} }
 OLD_MENU
 NEW_MENU
 TRAVEL
 static String oldTravel(L1PcInstance pc,int npcId,String s,String htmlid){OLD_TRAVEL}
 QUEST
 ARITH
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
 int n=0;for(String s:new String[]{"","0","7","263","519","18","274","ps_0","ps_","ps_９","-1"," 7","7\n","１２","99999999999999999999999999","none"})for(int off=0;off<=s.length();off++){check(NpcPlayerMenuHandler.isAsciiNumber(s,off)==s.substring(off).matches("[0-9]+"),"numeric "+s);n++;}
 System.out.println("ASCII number equivalence="+n);n=0;
 for(String cmd:OLD_MENU_CLASS.summonstr_list)for(int level:new int[]{1,28,32,48,52,56,60,64,68,72,80})for(int cha:new int[]{0,8,18,30,60})for(int pet:new int[]{0,8,36}){L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();a.level=b.level=level;a.cha=b.cha=cha;if(pet>0){a.pets.put(1,new L1NpcInstance(pet));b.pets.put(1,new L1NpcInstance(pet));}OLD_MENU_CLASS.summonMonster(a,cmd);NpcPlayerMenuHandler.handle(b,b,cmd);check(a.trace.equals(b.trace),cmd+" "+a.trace+" vs "+b.trace);n++;}
 System.out.println("Mapped summon equivalence="+n);n=0;
 for(String cmd:new String[]{"0","1","9999999999999999999","none","poly","7"})for(boolean magic:new boolean[]{false,true}){L1PcInstance p=new L1PcInstance(),other=new L1PcInstance();other.magic=p.magic=magic;NpcPlayerMenuHandler.handle(p,other,cmd);check(p.trace.isEmpty()&&other.trace.isEmpty()&&other.magic==magic,"cross target");n++;}
 L1PcInstance p=new L1PcInstance();NpcPlayerMenuHandler.handle(p,p,"0");check(p.trace.isEmpty(),"invalid summon");NpcTable.missing=true;NpcPlayerMenuHandler.handle(p,p,"7");check(p.trace.isEmpty(),"missing template");NpcTable.missing=false;System.out.println("Invalid/cross target checks="+(n+2));
 n=0;for(int id:IDS)for(String cmd:new String[]{"T_pcbang","t_PCBANG","buy","","T_pcbangx"})for(boolean pass:new boolean[]{false,true})for(boolean avail:new boolean[]{false,true}) {L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();a.PC방_버프=b.PC방_버프=pass;a.available=b.available=avail;check(NpcTravelCommands.isPcRoomTeleporter(id),"id");check(Objects.equals(oldTravel(a,id,cmd,"old"),NpcTravelCommands.pcRoomTeleport(b,id,cmd,"old")),"html");check(a.trace.equals(b.trace),"travel "+id+" "+a.trace+" "+b.trace);n++;}
 for(int id:IDS){L1PcInstance a=new L1PcInstance();a.debitOK=false;NpcTravelCommands.pcRoomTeleport(a,id,"T_pcbang",null);check(!a.trace.contains("move:")&&a.trace.contains("msg:"),"failed debit");n++;}System.out.println("Travel equivalence and failed debit="+n);
 n=0;for(String cmd:new String[]{"a","B","c","d",""})for(int flags=0;flags<16;flags++)for(boolean commit:new boolean[]{false,true}){L1PcInstance a=new L1PcInstance();a.available=(flags&1)!=0;a.debitOK=(flags&2)!=0;a.grantOK=(flags&4)!=0;a.commitThrow=(flags&8)!=0;String html=exchangeProtonGun(a,22,cmd,"old");int count=cmd.equalsIgnoreCase("a")?1:cmd.equalsIgnoreCase("b")?5:cmd.equalsIgnoreCase("c")?10:0;check(a.reward==0&&a.material==100&&!a.trace.contains("획득:"),"premature grant");if(count==0){check("old".equals(html)&&a.success==null,"other command");}else if(a.available&&a.debitOK&&a.grantOK&&!a.commitThrow){check(html==null&&a.success!=null,"async");if(commit){a.success.run();check(a.reward==count*3&&a.material==100-count&&a.trace.contains("page:22:;"),"commit");}else{a.fail.run();check(a.material==100&&a.reward==0&&a.trace.contains("falllovef"),"rollback");}}else{check("falllovef".equals(html)&&a.success==null,"failed prep");}n++;}System.out.println("Exchange receipt mock="+n);
 n=0;for(int unit:new int[]{Integer.MIN_VALUE,-1,0,1,1000,2147483,2147484,Integer.MAX_VALUE})for(int amount:new int[]{Integer.MIN_VALUE,-1,0,1,2,999,1000,Integer.MAX_VALUE}){check(validExpandedAmount(unit,amount)==(unit>0&&amount>0&&((long)unit*amount<=Integer.MAX_VALUE)),"overflow");n++;}System.out.println("Expanded amount bounds="+n);
 }
}
'''
java=java.replace('OLD_MENU_CLASS','LegacyMenu').replace('OLD_MENU','static class LegacyMenu {\n'+oldmenu.replace('private static final String[] summonstr_list','static final String[] summonstr_list')+'\n}').replace('NEW_MENU',strip(menu).replace('final class NpcPlayerMenuHandler','static final class NpcPlayerMenuHandler')).replace('TRAVEL',strip(travel).replace('final class NpcTravelCommands','static final class NpcTravelCommands'),1).replace('OLD_TRAVEL',oldtravel).replace('QUEST',q).replace('ARITH',arithmetic).replace('IDS','new int[]{'+','.join(ids)+'}')
# local method extracted from both versions, use previous typed harness with bool debit result
prior=(root/'docs/npc_action_n0_n1_20261119/test_npc_entry.py').read_text(); harness=prior.split("java=r'''",1)[1].split("'''.replace",1)[0];
def body(t):return t[t.index('{',t.index('static boolean dispatchLocalCommand'))+1:t.rindex('\n\t}')]
harness=harness.replace('static void legacy(L1PcInstance pc,String s) {','static boolean legacy(L1PcInstance pc,String s) {').replace(' pc.trace.append("fallthrough;");\n }',' }',1).replace('legacy(a,cmd); if(!current(b,cmd))','if(!legacy(a,cmd)) a.trace.append("fallthrough;"); if(!current(b,cmd))').replace('void consumeItem(int i,int n){trace.append("consume;");}','boolean debitOK=true; boolean consumeItem(int i,int n){trace.append("consume;");return debitOK;}').replace('LEGACY',body(local)).replace('CURRENT',body(now)).replace('NpcPlayerMenuHandler.isAsciiNumber(s, 3)','s.substring(3).matches("[0-9]+")')
harness=harness.replace('int count=0;', '''for(String cmd:new String[]{"nenormal","nelowp"}){L1PcInstance p=new L1PcInstance(true,false,false,false,99);p.debitOK=false;current(p,cmd);if(p.trace.toString().contains("normal;")||p.trace.toString().contains("low;"))throw new AssertionError("failed consume");}System.out.println("Local failed consume cases=2");int count=0;''')
with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp); (t/'Followup.java').write_text(java);(t/'NpcLocalTest.java').write_text(harness);subprocess.run(['javac','--release','21',str(t/'Followup.java'),str(t/'NpcLocalTest.java')],check=True);subprocess.run(['java','-cp',tmp,'Followup'],check=True);subprocess.run(['java','-cp',tmp,'NpcLocalTest'],check=True)
