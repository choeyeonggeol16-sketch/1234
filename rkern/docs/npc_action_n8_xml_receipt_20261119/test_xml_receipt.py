"""Compile actual XML actions/parser/factory against a controllable receipt stub.
Tests action sequencing, exact debit, failure and unknown outcomes; not MySQL.
"""
from pathlib import Path
import tempfile, shutil, subprocess, re
root=Path(__file__).resolve().parents[2]; src=root/'src'; base='l1j/server/server/'
with tempfile.TemporaryDirectory() as tmp:
 d=Path(tmp); java=d/'src'; out=d/'out';out.mkdir()
 def write(path,body):
  f=java/(base+path); f.parent.mkdir(parents=True,exist_ok=True); f.write_text(body)
 files=['model/npc/action/'+x+'.java' for x in ['L1NpcAction','L1NpcListedAction','L1NpcMakeItemAction','L1NpcXmlAction','L1NpcXmlParser','L1NpcActionFactory','L1NpcShowHtmlAction','L1NpcSetQuestAction']]+['model/npc/L1NpcHtml.java','model/L1ObjectAmount.java','utils/IterableElementList.java','utils/IterableNodeList.java','utils/IntRange.java','utils/FileUtil.java','datatables/NpcActionTable.java']
 for path in files: write(path,(src/(base+path)).read_text())
 write('model/L1World.java','package l1j.server.server.model;public class L1World {public static L1World getInstance(){return new L1World();}public long smartIdOf(L1Object object){return object.getId();}}')
 write('model/network/core/session/CIOClient.java','package l1j.server.server.model.network.core.session;import l1j.server.server.model.Instance.L1PcInstance;public class CIOClient {public int version=1;public int npcDialogueVersion(){return version;}public boolean canReplyToNpcDialogue(L1PcInstance pc,long id,int old){return old==version;}}')
 write('model/L1Object.java','package l1j.server.server.model; public class L1Object { public int getId(){return 91;} }')
 write('model/CInventory.java','package l1j.server.server.model; public class CInventory { public static final int MAX_AMOUNT=2000000000; }')
 constants=re.findall(r'public static final int QUEST_[A-Z0-9_]+ = \d+;', (src/(base+'model/L1Quest.java')).read_text())
 write('model/L1Quest.java','package l1j.server.server.model; import java.util.*; public class L1Quest { '+''.join(constants)+' Map<Integer,Integer> steps=new HashMap<>(); public int get_step(int id){return steps.getOrDefault(id,0);} public void set_step(int id,int step){steps.put(id,step);} }')
 write('templates/L1Item.java','''package l1j.server.server.templates; public class L1Item { public final int id; public L1Item(int i){id=i;} public boolean isStackable(){return id!=3;} public int getWeight(){return 1;} public int getBless(){return 1;} public String getName(){return "item"+id;} }''')
 write('datatables/ItemTable.java','''package l1j.server.server.datatables; import l1j.server.server.templates.*; public class ItemTable { public static ItemTable current=new ItemTable(); public boolean missing; public static ItemTable getInstance(){return current;} public L1Item getTemplate(int id){return id>0 && !missing?new L1Item(id):null;} }''')
 write('IdFactory.java','package l1j.server.server; public class IdFactory {static final IdFactory I=new IdFactory(); int n=100;public static IdFactory getInstance(){return I;} public int nextId(){return ++n;} }')
 write('model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance; import l1j.server.server.templates.*; public class L1ItemInstance {public L1Item def;public int count,enchant,id,bless;public boolean equipped,identified;public L1ItemInstance(L1Item d,int c){def=d;count=c;} public int getItemId(){return def.id;}public int getCount(){return count;}public int getEnchantLevel(){return enchant;}public boolean isEquipped(){return equipped;}public void setId(int i){id=i;}public void setBless(int b){bless=b;}public void setEnchantLevel(int e){enchant=e;}public void setIdentified(boolean b){identified=b;}}''')
 write('model/CUserInventory.java','''package l1j.server.server.model; import java.util.*;import l1j.server.server.model.Instance.*;public class CUserInventory {public final List<L1ItemInstance> items=new ArrayList<>();public int slots,weight;public List<L1ItemInstance> getItems(){return items;}public boolean checkItem(int id){return items.stream().anyMatch(x->x.getItemId()==id&&x.count>0);} public int getSize(){return items.size()+slots;}public int getWeight(){return weight;} }''')
 write('model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;import java.util.*;import l1j.server.server.model.*;public class L1PcInstance extends L1Object {public l1j.server.server.model.network.core.session.CIOClient client=new l1j.server.server.model.network.core.session.CIOClient();public l1j.server.server.model.network.core.session.CIOClient getNetConnection(){return client;}public CUserInventory inv=new CUserInventory();public L1Quest quest=new L1Quest();public List<String> trace=new ArrayList<>();public int failCall,operation,submits,maxWeight=10000;public boolean pending;public Runnable committed,failed; public CUserInventory getInventory(){return inv;}public int getMaxWeight(){return maxWeight;}public int getLevel(){return 80;}public int getType(){return 1;}public L1Quest getQuest(){return quest;} public void resolve(boolean ok){if(!pending)throw new AssertionError("not pending");Runnable r=ok?committed:failed;pending=false;committed=failed=null;r.run();}}''')
 write('model/Instance/L1NpcInstance.java','''package l1j.server.server.model.Instance;import l1j.server.server.model.*;public class L1NpcInstance extends L1Object {public L1NpcInstance getNpcTemplate(){return this;}public int get_npcId(){return 123;}public String get_name(){return "npc";} }''')
 write('model/L1InventoryTransaction.java','''package l1j.server.server.model;import java.util.*;import l1j.server.server.model.Instance.*;public class L1InventoryTransaction implements AutoCloseable {final L1PcInstance pc;Map<L1ItemInstance,Integer> debits=new IdentityHashMap<>();List<L1ItemInstance> grants=new ArrayList<>();Runnable fail;boolean submitted;L1InventoryTransaction(L1PcInstance p){pc=p;}public static L1InventoryTransaction begin(L1PcInstance p,int key){if(p.pending||++p.operation==p.failCall)throw new IllegalStateException();p.pending=true;return new L1InventoryTransaction(p);} public boolean debit(L1ItemInstance i,int n){int sum=debits.getOrDefault(i,0)+n;if(++pc.operation==pc.failCall||i.equipped||!pc.inv.items.contains(i)||sum>i.count)return false;debits.put(i,sum);return true;}public L1ItemInstance grant(L1ItemInstance i){if(++pc.operation==pc.failCall)return null;if(i.id<=0||i.count<=0)throw new AssertionError();grants.add(i);return i;}public void onFailure(Runnable r){fail=r;}public void commitAfterInventoryRelease(Runnable r){if(++pc.operation==pc.failCall)throw new IllegalStateException();submitted=true;pc.submits++;pc.failed=fail;pc.committed=()->{for(var e:debits.entrySet())e.getKey().count-=e.getValue();pc.inv.items.removeIf(i->i.count<=0);for(var i:grants){if(i.def.isStackable()){var same=pc.inv.items.stream().filter(x->x.def.id==i.def.id&&x.enchant==i.enchant).findFirst();if(same.isPresent()){same.get().count+=i.count;continue;}}pc.inv.items.add(i);}r.run();};}public void close(){if(!submitted)pc.pending=false;}}''')
 write('model/network/core/sc/packet/SC_NPCTalkReturn.java','''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.*;import l1j.server.server.model.npc.*;public class SC_NPCTalkReturn {public static void send(L1PcInstance p,int id,L1NpcHtml h){p.trace.add("html:"+h.getName());}}''')
 write('model/network/core/sc/packet/SC_ServerMessage.java','''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.*;public class SC_ServerMessage {public static void send(L1PcInstance p,int n,String... a){p.trace.add("msg:"+n+":"+String.join("/",a));}}''')
 write('model/network/core/sc/packet/SC_SystemMessage.java','''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.*;public class SC_SystemMessage {public static void send(L1PcInstance p,String s){p.trace.add(s);}}''')
 write('model/network/core/sc/packet/SC_HowManyMake.java','''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.*;public class SC_HowManyMake {public static void send(L1PcInstance p,int id,int n,String s){p.trace.add("prompt:"+n);}}''')
 write('model/npc/action/L1NpcTeleportAction.java','''package l1j.server.server.model.npc.action;import org.w3c.dom.Element;import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;import l1j.server.server.model.npc.*;public class L1NpcTeleportAction extends L1NpcXmlAction {public L1NpcTeleportAction(Element e){super(e);}public L1NpcHtml execute(String s,L1PcInstance p,L1Object o,byte[] a){p.trace.add("teleport");return null;}}''')
 (java/'Check.java').write_text('''import java.util.*;import javax.xml.parsers.*;import org.xml.sax.InputSource;import java.io.StringReader;import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;import l1j.server.server.model.npc.action.*;import l1j.server.server.templates.*;import l1j.server.server.datatables.*;
 public class Check {static int checks,cases; static final L1NpcInstance npc=new L1NpcInstance();static void ck(boolean b){checks++;if(!b)throw new AssertionError("case "+cases+" check "+checks);}static L1NpcAction parse(String s)throws Exception{return L1NpcActionFactory.newAction(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(s))).getDocumentElement());}static L1ItemInstance add(L1PcInstance p,int id,int count,int en,boolean eq){var i=new L1ItemInstance(new L1Item(id),count);i.enchant=en;i.equipped=eq;p.inv.items.add(i);return i;}static int qty(L1PcInstance p,int id,int en,boolean eq){return p.inv.items.stream().filter(i->i.getItemId()==id&&i.enchant==en&&i.equipped==eq).mapToInt(i->i.count).sum();}static long htmls(L1PcInstance p){return p.trace.stream().filter(s->s.startsWith("html:")).count();}static void run(L1NpcAction a,L1PcInstance p,int amount){a.dispatch("go",p,npc,new byte[0],amount);}static String make(String attrs,String body,String branches){return "<MakeItem "+attrs+">"+body+branches+"</MakeItem>";}static String m(int id,int n,int e){return "<Material ItemId='"+id+"' Amount='"+n+"' Enchant='"+e+"'/>";}static String i(int id,int n,int e){return "<Item ItemId='"+id+"' Amount='"+n+"' Enchant='"+e+"'/>";}static String h(String s){return "<ShowHtml HtmlId='"+s+"'/>";}static String q(int step){return "<SetQuest Id='level15' Step='"+step+"'/>";}static String branch(String b,String content){return "<"+b+">"+content+"</"+b+">";}
 static void lookupChecks() throws Exception {
   cases++;
   var player = new L1PcInstance();
   var blocked = parse("<ShowHtml Name='other' NpcId='123' LevelMin='90' HtmlId='x'/>");
   ck(!blocked.acceptsRequest("selected", player, npc) && player.trace.isEmpty());
   ck(!blocked.acceptsRequest("other", player, npc) && player.trace.getLast().contains("90"));
   player.trace.clear();
   var overMax = parse("<ShowHtml Name='selected' LevelMax='70' HtmlId='x'/>");
   ck(!overMax.acceptsRequest("selected", player, npc) && player.trace.getLast().contains("70"));
   player.trace.clear();
   ck(parse("<ShowHtml NpcId='456, 123' Class='pK'/>").acceptsRequest("selected", player, npc));
   ck(!parse("<ShowHtml Class='W'/>").acceptsRequest("selected", player, npc) && player.trace.isEmpty());
   Locale original = Locale.getDefault();
   try {
     Locale.setDefault(Locale.forLanguageTag("tr-TR"));
     ck(L1NpcXmlParser.parseQuestId("FIRSTQUEST") == L1Quest.QUEST_FIRSTQUEST);
   } finally {
     Locale.setDefault(original);
   }

   var root = java.nio.file.Path.of("data", "xml", "NpcActions");
   java.nio.file.Files.createDirectories(root.resolve("users"));
   java.nio.file.Files.writeString(root.resolve("users/override.xml"),
       "<NpcActionList><ShowHtml Name='selected' HtmlId='override'/></NpcActionList>");
   java.nio.file.Files.writeString(root.resolve("default.XML"),
       "<NpcActionList><ShowHtml Name='selected' HtmlId='default'/></NpcActionList>");
   java.nio.file.Files.writeString(root.resolve("talk.xml"),
       "<NpcTalkActionList><ShowHtml NpcId='123' HtmlId='talk'/></NpcTalkActionList>");
   NpcActionTable.load();
   var actions = NpcActionTable.getInstance();
   ck(actions.get("selected", player, npc).execute("selected", player, npc, null).getName().equals("override"));
   ck(actions.get(player, npc).execute("", player, npc, null).getName().equals("talk"));
   ck(actions.get("missing", player, npc) == null);
 }
 public static void main(String[] args)throws Exception{
 lookupChecks();
 String body=m(1,2,0)+m(1,1,0)+m(2,1,7)+i(4,2,2)+i(4,1,0)+i(3,2,6);String br=branch("Succeed",q(1)+h("success"))+branch("Fail",q(2)+h("fail"));var a=parse("<Action>"+h("before")+make("AmountInputable='false'",body,br)+"<ShowHtml QuestId='level15' QuestStep='1' HtmlId='after'/>"+"</Action>");
 for(int n=0;n<=6;n++)for(int en:new int[]{0,7})for(int fail=0;fail<=10;fail++)for(int resolution=0;resolution<3;resolution++){
 cases++;var p=new L1PcInstance();p.failCall=fail;var z=add(p,1,n,0,false);add(p,1,20,9,false);add(p,1,20,0,true);add(p,2,1,en,false);add(p,4,10,0,false);run(a,p,0);
 if(p.pending){ck(n>=3&&en==7);ck(htmls(p)==0);ck(p.getQuest().get_step(1)==0);ck(qty(p,1,0,false)==n);ck(qty(p,4,2,false)==0);if(resolution==2){ck(p.pending);continue;}p.resolve(resolution==0);if(resolution==0){ck(qty(p,1,0,false)==n-3);ck(qty(p,2,7,false)==0);ck(qty(p,4,2,false)==2);ck(qty(p,4,0,false)==11);ck(qty(p,3,6,false)==2);ck(p.getQuest().get_step(1)==1);ck(p.trace.getLast().equals("html:after"));}else{ck(qty(p,1,0,false)==n);ck(p.getQuest().get_step(1)==2);ck(p.trace.getLast().equals("html:fail"));}}
 else{ck(p.submits==0);ck(qty(p,1,0,false)==n);ck(qty(p,4,2,false)==0);ck(p.getQuest().get_step(1)==2);ck(p.trace.getLast().equals("html:fail"));}ck(qty(p,1,9,false)==20);ck(qty(p,1,0,true)==20);ck(htmls(p)==1);ck(!p.pending);
 }
 // Nested Succeed receipt resumes outer siblings after both commits; descendant Fail is not parent Fail.
 var nested=parse("<Action>"+make("AmountInputable='false'",m(1,1,0)+i(2,1,0),branch("Succeed",make("AmountInputable='false'",m(2,1,0)+i(4,1,0),branch("Succeed",q(1)+h("nested"))+branch("Fail",q(2)+h("nestedFail")))))+h("tail")+"</Action>");
 for(int mode=0;mode<4;mode++){cases++;var p=new L1PcInstance();if(mode!=3)add(p,1,1,0,false);run(nested,p,0);if(mode==3){ck(p.getQuest().get_step(1)==0);ck(p.trace.getLast().equals("html:tail"));continue;}ck(p.pending&&htmls(p)==0);p.resolve(mode!=2);if(mode==2){ck(!p.pending);ck(p.getQuest().get_step(1)==0);ck(qty(p,1,0,false)==1);}else{ck(p.pending&&htmls(p)==0);ck(qty(p,2,0,false)==1);p.resolve(mode==0);ck(p.getQuest().get_step(1)==(mode==0?1:2));ck(qty(p,4,0,false)==(mode==0?1:0));}ck(htmls(p)==1&&p.trace.getLast().equals("html:tail"));}
 // Prompt, direct amount, cap, exact duplicates across stacks, and immutable notice on template reload.
 for(int amount:new int[]{0,1,2,999,1000,2000000000})for(boolean allow:new boolean[]{false,true}){cases++;var p=new L1PcInstance();add(p,1,1000,0,false);add(p,1,2000,0,false);var b=parse(make("AmountInputable='"+allow+"'",m(1,2,0)+m(1,1,0)+i(4,1,0),branch("Succeed",h("success"))+branch("Fail",h("fail"))));run(b,p,amount);if(amount==0&&allow){ck(p.trace.equals(List.of("prompt:999")));ck(!p.pending);}else if(amount>999||(!allow&&amount>1)){ck(p.trace.getLast().equals("html:fail"));ck(!p.pending);}else{ck(p.pending);ItemTable.current.missing=true;p.resolve(true);ItemTable.current.missing=false;ck(qty(p,1,0,false)==3000-3*Math.max(1,amount));ck(qty(p,4,0,false)==Math.max(1,amount));ck(p.trace.getLast().equals("html:success"));}}
 for(int mode=0;mode<2;mode++){cases++;var p=new L1PcInstance();add(p,1,1,0,false);if(mode==0)p.inv.slots=180;else p.maxWeight=0;p.inv.weight=1;var b=parse(make("AmountInputable='false'",m(1,1,0)+i(3,1,0),br));run(b,p,0);ck(!p.pending&&qty(p,1,0,false)==1&&p.getQuest().get_step(1)==2);}
 for (int invalid : new int[]{0, -1, 2000000000}) {
   cases++;
   var p = new L1PcInstance();
   add(p, 1, 50, 0, false);
   var craft = parse(make("", m(1, 2, 0) + m(1, invalid, 0) + i(4, 1, 0), branch("Fail", h("fail"))));
   run(craft, p, 0);
   ck(!p.pending && qty(p, 1, 0, false) == 50 && p.trace.getLast().equals("html:fail"));
 }
 System.out.println("PASS scenarios="+cases+" assertions="+checks);}
 }
 ''')
 subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(out),*map(str,java.rglob('*.java'))],check=True)
 subprocess.run(['java','-cp',str(out),'Check'],cwd=d,check=True)
