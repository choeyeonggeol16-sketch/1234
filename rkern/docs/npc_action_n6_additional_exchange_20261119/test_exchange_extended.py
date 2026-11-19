"""Compile actual exchange handlers with typed fake transaction, plus routing and recipe baseline checks."""
import pathlib, re, subprocess, sys, tempfile, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]; prefix='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(sys.argv[1]) as z:
 old=z.read(prefix+'CS_NpcAcion2.java').decode(); oldmain=z.read(prefix+'HtmlCmdHandler.java').decode()
new=(root/prefix/'CS_NpcAcion2.java').read_text(); main=(root/prefix/'HtmlCmdHandler.java').read_text(); handler=(root/prefix/'NpcExchangeCommands.java').read_text()
oldg=old[old.index('if (npcid == 200201)'):old.index('} else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 4200018)')]
rows=re.findall(r'checkEnchantItem\((\d+), 7, 1\).*?checkEnchantItem\((\d+), 7, 1\).*?checkItem\(41246, 30000\).*?consumeItem\(41246, (\d+)\).*?storeItem\((\d+), 1\).*?htmlid = "(joegolem\d+)"',oldg,re.S)
assert rows==[('5','6','3000','602','joegolem9'),('145','148','30000','605','joegolem10'),('52','64','30000','601','joegolem11'),('125','129','30000','603','joegolem12'),('99','104','30000','604','joegolem13'),('32','42','30000','600','joegolem14')],rows
for start,end,reward in [(410097,410106,410107),(410144,410161,410143)]:
 assert all(f'consumeItem({i}, 1)' in old for i in range(start,end+1)) and f'storeItem({reward}, 1)' in old
assert all(f'storeItem({i}, 1)' in old for i in range(60036,60041))
assert 'int[] prismIds = { 210106, 210107, 210108, 210109 };' in oldmain
assert 'NpcExchangeCommands.prism(pc, objid, _random)' in main
assert 'NpcExchangeCommands.moonItem(pc, objid)' in main
assert 'NpcExchangeCommands.golem(pc, obj.getId(), s, htmlid)' in new
assert 'NpcExchangeCommands.diary(pc, obj.getId(), false)' in new and 'NpcExchangeCommands.diary(pc, obj.getId(), true)' in new
assert 'NpcExchangeCommands.rune(pc, npcid, s, htmlid)' in new
assert 'createItem(' not in '\n'.join(x for x in handler.splitlines() if not x.strip().startswith('//'))
assert 'String htmlid = null;' in main
# Compare the extracted destination table and test actual changed branch, including invalid commands.
travel=[]
for npc,method in [(1000001,'cave'),(81210,'city')]:
 def extract(s):
  a=s.index('get_npcId() == '+str(npc)+')'); b=s.index('\n\t\t\t} else if',a)
  bdy=s[s.index('int locx = 0',a):b]
  return bdy.strip()
 before,after=extract(old),extract(new)
 assert re.findall(r'(?:locx|locy|mapid|map) = \d+;',before)==re.findall(r'(?:locx|locy|mapid|map) = \d+;',after)
 travel.append('static String '+method+'(L1PcInstance pc,String s,String htmlid){'+after+'\nreturn htmlid;}')
# Second ZIP is the immediate N6 baseline; verify all recipe rows in it, not in new source.
with zipfile.ZipFile(sys.argv[2]) as z:
 prior=z.read(prefix+'CS_NpcAcion2.java').decode()
def branch(npc):
 m=re.search(r'(?m)^\t{3}\} else if \((?:npcid|\(\(L1NpcInstance\) obj\)\.getNpcTemplate\(\)\.get_npcId\(\)) == '+str(npc)+r'\) \{[^\n]*\n',prior)
 return prior[m.end():prior.index('\n\t\t\t} else if',m.end())]
assert [tuple(map(int,t)) for t in re.findall(r'consumeItem\(49026, (\d+)\).*?storeItem\((\d+), 1\)',branch(71180),re.S)] == list(zip([1000,5000,10000,100000]*2,[41093,41094,41095,41096,41098,41099,41100,41101]))
assert [tuple(map(int,t)) for t in re.findall(r'consumeItem\(3000211, (\d+)\).*?storeItem\(3000210, (\d+)\)',branch(7310174),re.S)] == [(300,1),(500,2),(1000,5)]
assert [x for x in re.findall(r's.equalsIgnoreCase\("(.)"\)',branch(70690))] == list('abbdefgh')
assert [tuple(map(int,t)) for t in re.findall(r'consumeItem\(410061, 50\);\s+pc.getInventory\(\).consumeItem\((\d+), (\d+)\);(?:\s+pc.getInventory\(\).consumeItem\(\d+, 5\);)?\s+pc.getInventory\(\).storeItem\((\d+), 1\)',branch(70690),re.S)] == [(40053,10,222307),(40052,10,22359),(40055,10,222308),(40054,10,222309),(560030,1,222307),(560033,1,22359),(560032,1,222308),(560031,1,222309)]
assert 'consumeItem(3000200, 1)' in branch(7310149) and 'consumeItem(40308, 20000)' in branch(7310149) and 'storeItem(700022, 1)' in branch(7310149)
assert 'consumeEnchantItem(20273, 7, 1)' in branch(70641) and 'storeItem(20274, 1)' in branch(70641) and 'storeItem(20273, 1)' in branch(70641)
for npc,method in [(71180,'jape'),(7310174,'eva'),(70690,'bamut'),(7310149,'memoryCrystal'),(70641,'habert')]:
 assert 'return NpcExchangeCommands.'+method+'(pc, obj.getId(), s, htmlid);' in new
for id,n in [(40308,7777),(40308,300000),(810000,1)]:
 assert f'checkItem({id}, {n})\n                            && pc.getInventory().consumeItem({id}, {n}))' in new
# typed fake (does not perform DB or exercise real production inventory implementation)
code='\n'.join(l for l in handler.splitlines() if not l.startswith(('package ','import '))).replace('final class NpcExchangeCommands','static final class NpcExchangeCommands')
java=r'''import java.util.*;import java.util.logging.*;
class ExchangeTest {
 static class L1ItemId {static final int ADENA=40308;}
 static class L1Item {final boolean stack;L1Item(boolean s){stack=s;}boolean isStackable(){return stack;}}
 static class ItemTable {static final ItemTable T=new ItemTable();static ItemTable getInstance(){return T;}L1Item getTemplate(int id){return new L1Item(id==41246||id==40308||id==60034||id==49026||id==3000211||id==410061||id==820004);}}
 static class L1ItemInstance {final int id;int en;final boolean eq;int count;L1ItemInstance(int id,int en,int count,boolean eq){this.id=id;this.en=en;this.count=count;this.eq=eq;}int getItemId(){return id;}int getEnchantLevel(){return en;}void setEnchantLevel(int e){en=e;} String getLogName(){return "i"+id+"+"+en;}boolean isEquipped(){return eq;}int getCount(){return count;}String getNumberedName(int n){return "i"+id+":"+n;} }
 static class L1PcInstance {List<L1ItemInstance> items=new ArrayList<>();int level=60, flags, rejectAt=-1, tele, x,y,map;String trace="";Runnable success,fail;int calls=0;boolean busy=false;L1PcInstance getInventory(){return this;}List<L1ItemInstance> getItems(){return items;}int getId(){return 10;}int getLevel(){return level;}int getHeading(){return 3;} void start_teleport(int x,int y,int map,int h,int e,boolean a,boolean b){tele++;this.x=x;this.y=y;this.map=map;}void add(int id,int en,int count,boolean eq){items.add(new L1ItemInstance(id,en,count,eq));}L1ItemInstance findItemId(int id){for(var i:items)if(i.id==id&&i.count>0)return i;return null;}L1ItemInstance[] findItemsId(int id){return items.stream().filter(i->i.id==id&&i.count>0).toArray(L1ItemInstance[]::new);}int total(int id){int n=0;for(var i:items)if(i.id==id)n+=i.count;return n;}boolean checkItem(int id,int n){var d=ItemTable.T.getTemplate(id);if(d.stack){var i=findItemId(id);return i!=null&&i.count>=n;}int k=0;for(var i:items)if(i.id==id&&i.count>0)k++;return k>=n;}boolean checkEnchantItem(int id,int e,int n){int k=0;for(var i:items)if(i.id==id&&i.en==e&&!i.eq&&i.count>0)k++;return k>=n;}boolean consumeItem(int id,int n){if((flags&2)!=0)return false;var i=findItemId(id);if(i==null||i.count<n)return false;i.count-=n;return true;}String snapshot(){StringBuilder b=new StringBuilder();for(var i:items)b.append(i.id).append('/').append(i.en).append('/').append(i.count).append('/').append(i.eq).append(';');return b.toString();}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int i,String s){p.trace+="h"+i+":"+s+";";}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.trace+="m"+s+";";}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int i,String s){p.trace+="s"+i+":"+s+";";}}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance pc;IdentityHashMap<L1ItemInstance,Integer> staged=new IdentityHashMap<>();List<L1ItemInstance> rewards=new ArrayList<>();Runnable failure;boolean submitted;L1InventoryTransaction(L1PcInstance p){pc=p;}static L1InventoryTransaction begin(L1PcInstance p,int k){if(p.busy||(p.flags&1)!=0)throw new IllegalStateException();p.busy=true;return new L1InventoryTransaction(p);}boolean debit(L1ItemInstance i,int n){int old=staged.getOrDefault(i,i.count);if((pc.flags&2)!=0||pc.calls++==pc.rejectAt||n<=0||i.eq||old<n)return false;staged.put(i,old-n);return true;}L1ItemInstance grant(int id,int n){if((pc.flags&4)!=0)return null;var i=new L1ItemInstance(id,0,n,false);rewards.add(i);return i;}void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){if((pc.flags&8)!=0)throw new IllegalStateException();submitted=true;pc.success=()->{staged.forEach((i,n)->i.count=n);pc.items.addAll(rewards);pc.busy=false;r.run();};pc.fail=()->{pc.busy=false;failure.run();};}public void close(){if(!submitted)pc.busy=false;}}
 HANDLER
 TRAVEL
 static int cases=0;static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void deferred(L1PcInstance p,String page,String snapshot,boolean valid,boolean committed,String success,String fail){check(p.snapshot().equals(snapshot),"premature mutation");check((p.success!=null)==valid,"submission "+page+" trace="+p.trace);if(valid){check(page==null&&p.trace.isEmpty(),"premature response");if(committed){p.success.run();check(p.trace.equals(success),"success: "+p.trace+" expected "+success);}else{p.fail.run();check(p.snapshot().equals(snapshot)&&p.trace.equals(fail),"rollback: "+p.trace);}}check(!p.busy,"gate leaked");cases++;}
 public static void main(String[] args){Logger.getLogger(NpcExchangeCommands.class.getName()).setLevel(Level.OFF);
 int[] l={5,145,52,125,99,32},r={6,148,64,129,104,42},out={602,605,601,603,604,600};
 for(int i=0;i<6;i++)for(boolean lower:new boolean[]{false,true})for(int cash:new int[]{0,2999,3000,29999,30000,60000})for(int mat=0;mat<8;mat++)for(int flags=0;flags<16;flags++)for(boolean commit:new boolean[]{false,true}){
 L1PcInstance p=new L1PcInstance();p.flags=flags;p.add(l[i],7,1,true);p.add(l[i],6,1,false);if((mat&1)!=0)p.add(l[i],7,1,false);if((mat&2)!=0)p.add(r[i],7,1,false);if((mat&4)!=0)p.add(r[i],8,1,false);p.add(41246,0,cash,false);String snap=p.snapshot();String c=String.valueOf((char)((lower?'a':'A')+i));String page=NpcExchangeCommands.golem(p,42,c,"tail");boolean valid=(mat&3)==3&&cash>=30000&&flags==0;deferred(p,page,snap,valid,commit,"h42:joegolem"+(9+i)+";","h42:joegolem15;");if(valid&&commit){check(p.total(out[i])==1&&p.total(41246)==cash-(i==0?3000:30000),"golem results");check(p.items.get(0).count==1&&p.items.get(1).count==1,"wrong weapon consumed");}else if(!valid)check(page.equals("joegolem15")&&p.snapshot().equals(snap),"golem failure");}
 for(boolean orim:new boolean[]{false,true})for(int missing=-1;missing<(orim?18:10);missing++)for(int rejected=-1;rejected<(orim?18:10);rejected++)for(int flags:new int[]{0,1,4,8})for(boolean commit:new boolean[]{false,true}){L1PcInstance p=new L1PcInstance();p.flags=flags;p.rejectAt=rejected;int first=orim?410144:410097,last=orim?410161:410106;for(int id=first;id<=last;id++)if(id-first!=missing){p.add(id,4,1,false);p.add(id,0,1,false);}String snap=p.snapshot();String page=NpcExchangeCommands.diary(p,42,orim);boolean valid=missing<0&&rejected<0&&flags==0;deferred(p,page,snap,valid,commit,"h42:j_html04;","h42:j_html06;");if(valid&&commit){check(p.total(orim?410143:410107)==1,"diary reward");for(int j=0;j<p.items.size()-1;j+=2)check(p.items.get(j).count==1&&p.items.get(j+1).count==0,"legacy lowest enchant selection");}else if(!valid)check(page.equals("j_html06")&&p.snapshot().equals(snap),"diary failure");}
 String[] msg={"힘","민첩","체력","지식","지혜"};for(boolean pouch:new boolean[]{false,true})for(int i=0;i<5;i++)for(int level:new int[]{54,55,60})for(int m=0;m<4;m++)for(int flags=0;flags<16;flags++)for(boolean commit:new boolean[]{false,true}){L1PcInstance p=new L1PcInstance();p.level=level;p.flags=flags;if((m&1)!=0)p.add(pouch?60034:60031,0,1,false);if((m&2)!=0)p.add(60032,0,1,false);String snap=p.snapshot();String page=NpcExchangeCommands.rune(p,pouch?7000055:7000054,String.valueOf((char)('A'+i)),null);boolean valid=flags==0&&(pouch?(m&1)!=0:level>=55&&m==3);deferred(p,page,snap,valid,commit,pouch?"m"+msg[i]+"의 엘릭서 룬을 획득했습니다.;":"h10:seirune6;",pouch?"h10:riddle2;":"h10:seirune5;");if(valid&&commit)check(p.total(60036+i)==1,"rune grant");if(!valid)check(p.snapshot().equals(snap)&&!p.trace.contains("seirune6"),"rune rejected");}
 for(int i=0;i<4;i++)for(int cash:new int[]{0,9999,10000,20000})for(int flags=0;flags<16;flags++)for(boolean commit:new boolean[]{false,true}){L1PcInstance p=new L1PcInstance();p.flags=flags;p.add(40308,0,cash,false);int k=i;int[] draws={0};Random rnd=new Random(){public int nextInt(int bound){check(bound==4,"rng bound");draws[0]++;return k;}};String snap=p.snapshot();String page=NpcExchangeCommands.prism(p,42,rnd);boolean valid=cash>=10000&&flags==0;deferred(p,page,snap,valid,commit,"s403:i"+(210106+i)+":1;h42:camus2;","h42:camus3;");check(draws[0]==(cash>=10000&&(flags&3)==0?1:0),"rng order");if(valid&&commit)check(p.total(210106+i)==1&&p.total(40308)==cash-10000,"prism reward");else if(!valid)check(page.equals("camus3"),"prism failure");}
 for(int level:new int[]{30,31,60})for(int cash:new int[]{0,4999,5000,10000})for(boolean owned:new boolean[]{false,true})for(int flags=0;flags<16;flags++)for(boolean commit:new boolean[]{false,true}){L1PcInstance p=new L1PcInstance();p.flags=flags;p.level=level;p.add(40308,0,cash,false);if(owned)p.add(22253,0,1,false);String snap=p.snapshot();String page=NpcExchangeCommands.moonItem(p,42);boolean valid=level>30&&cash>=5000&&!owned&&flags==0;deferred(p,page,snap,valid,commit,"h42:rabbita5;","h42:rabbita4;");if(valid&&commit)check(p.total(22253)==1&&p.total(40308)==cash-5000,"moon reward");else if(!valid)check(page.equals(level<=30?"rabbita2":owned?"rabbita3":"rabbita4"),"moon failure page");}
 for(String c:new String[]{"","a","z","AA"," buy ","0"}){L1PcInstance p=new L1PcInstance();if(!c.equals("a"))check(NpcExchangeCommands.golem(p,42,c,"tail").equals("tail"),"unknown golem command");check(NpcExchangeCommands.rune(p,7000055,c,"tail").equals("tail"),"case sensitive rune");cases++;}
 for(boolean city:new boolean[]{false,true})for(char c='a';c<='z';c++)for(boolean upper:new boolean[]{false,true})for(int cash:new int[]{0,99,100,499,500,1000})for(int flags:new int[]{0,2}){L1PcInstance p=new L1PcInstance();p.flags=flags;p.add(40308,0,cash,false);String cmd=String.valueOf(upper?Character.toUpperCase(c):c);boolean known=(city?"bcdefghijklnv":"bcdefg").indexOf(c)>=0;int fee=city?100:500;String page=city?city(p,cmd,"tail"):cave(p,cmd,"tail");boolean valid=known&&cash>=fee&&flags==0;check(p.tele==(valid?1:0),"travel admission");check(p.total(40308)==cash-(valid?fee:0),"travel fee");if(valid)check(p.x!=0&&p.y!=0,"zero coordinate teleport");if(!known)check(page.equals("tail"),"invalid command page");cases++;}

 System.out.println("Prior exchange/travel cases: "+cases);
 int priorCases=cases;
 // Kind, command index, reward id, reward count, then input id/count pairs.
 int[][] recipes={
 {0,0,41093,1,49026,1000},{0,1,41094,1,49026,5000},{0,2,41095,1,49026,10000},{0,3,41096,1,49026,100000},
 {0,4,41098,1,49026,1000},{0,5,41099,1,49026,5000},{0,6,41100,1,49026,10000},{0,7,41101,1,49026,100000},
 {1,0,3000210,1,3000211,300},{1,1,3000210,2,3000211,500},{1,2,3000210,5,3000211,1000},
 {2,0,222307,1,410061,50,40053,10,40393,5},{2,1,22359,1,410061,50,40052,10,40396,5},
 {2,3,222309,1,410061,50,40054,10,40395,5},{2,4,222307,1,410061,50,560030,1},{2,5,22359,1,410061,50,560033,1},
 {2,6,222308,1,410061,50,560032,1},{2,7,222309,1,410061,50,560031,1},
 {3,0,700022,1,3000200,1,40308,20000},{4,0,20273,1,40395,1,410061,10,820004,300},
 {4,1,20274,1,20273,1,40395,1,410061,10,820004,300,820005,1}};
 for(int[] row:recipes)for(boolean upper:new boolean[]{false,true})for(int missing=-1;missing<(row.length-4)/2;missing++)for(int flags=0;flags<16;flags++)for(boolean commit:new boolean[]{false,true})for(boolean tailPresent:new boolean[]{false,true}){
   L1PcInstance p=new L1PcInstance();p.flags=flags;int kind=row[0],i=row[1];String tail=tailPresent?"tail":null;
   for(int j=4;j<row.length;j+=2){int id=row[j],n=row[j+1]-(missing==(j-4)/2?1:0);int en=id==20273?7:0;
     if(ItemTable.T.getTemplate(id).stack){p.add(id,en,n,false);}else{for(int k=0;k<n;k++)p.add(id,en,1,false);}}
   String snap=p.snapshot();String cmd=kind==3?(upper?"REQUEST MEMORY CRYSTAL":"request memory crystal"):String.valueOf((char)((upper?'A':'a')+i));
   String page=switch(kind){case 0->NpcExchangeCommands.jape(p,42,cmd,tail);case 1->NpcExchangeCommands.eva(p,42,cmd,tail);case 2->NpcExchangeCommands.bamut(p,42,cmd,tail);case 3->NpcExchangeCommands.memoryCrystal(p,42,cmd,tail);default->NpcExchangeCommands.habert(p,42,cmd,tail);};
   boolean ok=flags==0&&missing<0;check((p.success!=null)==ok,"new submission "+kind+"/"+i+" flags="+flags+" missing="+missing);check(p.snapshot().equals(snap),"new premature mutation");
   if(ok){check(page==null&&p.trace.isEmpty(),"new premature response");if(commit){p.success.run();check(p.total(row[2])==row[3],"new reward");for(int j=4;j<row.length;j+=2)check(p.total(row[j])==0,"new debit");String expect=switch(kind){case 0->"h42:"+(i<4?"jp6":"jp8")+";";case 1->"m감사합니다. 다음에 또 오세요.;"+(tailPresent?"h42:tail;":"");case 3->"m제작이 완료 되었습니다.;h42:;";default->"h42:;";};check(p.trace.equals(expect),"new complete "+p.trace);}else{p.fail.run();check(p.snapshot().equals(snap),"new rollback");check(!p.trace.contains("감사합니다")&&!p.trace.contains("완료 되었습니다")&&!p.trace.contains("jp6")&&!p.trace.contains("jp8"),"new failure announced success");if(kind==0)check(p.trace.equals("h42:jp5;"),"rollback jp5");else check(p.trace.endsWith(tailPresent?"h42:tail;":";"),"failure tail");}}
   else{check(Objects.equals(page,kind==0?"jp5":tail),"new failure tail");check(!p.trace.contains("감사합니다")&&!p.trace.contains("완료 되었습니다"),"failure announced success");}
   check(!p.busy,"new gate leaked");cases++;
 }
 for(String cmd:new String[]{"", "c", "C", "buy", "aa", "9"}){L1PcInstance p=new L1PcInstance();check(NpcExchangeCommands.bamut(p,42,cmd,"tail").equals("tail")&&p.success==null&&p.trace.isEmpty(),"unreachable Bamut recipe activated");cases++;}
 // Multi-unit nonstackable exact selection: excess high enchant must survive; equal-enchant ordering stable.
 for(int rejected=-1;rejected<16;rejected++){L1PcInstance p=new L1PcInstance();p.rejectAt=rejected;p.add(410061,0,50,false);for(int k=0;k<12;k++)p.add(40053,12-k,1,false);for(int k=0;k<5;k++)p.add(40393,0,1,false);String snap=p.snapshot();String page=NpcExchangeCommands.bamut(p,42,"a",null);if(rejected<0){check(page==null&&p.success!=null,"nonstackable submission");p.success.run();check(p.items.get(1).count==1&&p.items.get(2).count==1&&p.total(40053)==2&&p.total(222307)==1,"lowest enchant selection");}else{check(p.success==null&&p.snapshot().equals(snap),"nonstack stage failure rollback");}cases++;}
 System.out.println("Additional recipe cases: "+(cases-priorCases));
 System.out.println("Exchange / travel typed fake cases: "+cases);
 }
}'''.replace('HANDLER',code).replace('TRAVEL','\n'.join(travel))
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'ExchangeTest.java';p.write_text(java);subprocess.run(['javac','-encoding','UTF-8','--release','21',str(p)],check=True);subprocess.run(['java','-cp',d,'ExchangeTest'],check=True)
print('Legacy recipe vectors, handler routing, travel coordinates: PASS')
