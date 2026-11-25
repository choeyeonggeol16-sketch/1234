from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parents[2]
def extract(file, signature):
 s=next((root/'src').rglob(file)).read_text(); a=s.index(signature); b=s.index('{',a); depth=1;i=b+1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
methods='\n'.join(extract('NpcQuestTransactions.java', 'static void '+x+'(') for x in ['veteranSupport','discardKeplisha'])
methods+='\n'+extract('L1NpcMakeItemAction.java','private static boolean validExpandedAmount(')+'\n'+extract('L1NpcMakeItemAction.java','private int countNumOfMaterials(')
java='''import java.util.*;
public class Check {
 static int n; static void ck(boolean b){if(!b)throw new AssertionError("assertion "+n);n++;}
 static class CInventory {static final int MAX_AMOUNT=2000000000;}
 record L1ObjectAmount<T>(T getObject,int getAmount,int getEnchant){}
 static class ItemTable {static ItemTable getInstance(){return new ItemTable();} Object getTemplate(int id){return id>0?this:null;}}
 static class L1ItemInstance {int id,count,enchant;boolean equipped;L1ItemInstance(int i,int c,int e,boolean q){id=i;count=c;enchant=e;equipped=q;} int getItemId(){return id;}int getCount(){return count;}int getEnchantLevel(){return enchant;}boolean isEquipped(){return equipped;}String getLogName(){return "item"+id;}}
 static class CUserInventory {List<L1ItemInstance> items=new ArrayList<>();List<L1ItemInstance> getItems(){return items;}boolean checkItem(int id,int c){return items.stream().anyMatch(x->x.id==id&&x.count>=c);}}
 static class L1PcInstance {CUserInventory inv=new CUserInventory();int level,fail,grants,submitted,success,errors;String page;List<Integer> credited=new ArrayList<>(),debited=new ArrayList<>();int getLevel(){return level;}CUserInventory getInventory(){return inv;}}
 static class L1NpcInstance {int getId(){return 6;}L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "npc";}}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance pc;List<Integer> grants=new ArrayList<>(),debits=new ArrayList<>();Runnable failure;int calls;static L1InventoryTransaction begin(L1PcInstance p,int id){if(p.fail==1)throw new IllegalStateException();L1InventoryTransaction t=new L1InventoryTransaction();t.pc=p;return t;}L1ItemInstance grant(int id,int c){if(++calls+1==pc.fail)return null;grants.add(id);return new L1ItemInstance(id,c,0,false);}void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){if(pc.fail==5)throw new IllegalStateException();pc.submitted++;if(pc.fail==6)failure.run();else if(pc.fail!=7){pc.credited.addAll(grants);pc.debited.addAll(debits);r.run();}}public void close(){}}
 static class NpcExchangeCommands {static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int id,int count){if(++t.calls+1==p.fail)return false;t.debits.add(id);return true;}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.errors++;}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int id,String npc,String name){ck(id==143&&npc.equals("npc"));p.success++;}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page){ck(id==6);p.page=page;}}
 List<L1ObjectAmount<Integer>> _materials=new ArrayList<>(),_items=new ArrayList<>();
'''+methods+'''
 static void support(boolean bundle,int level,boolean marker,boolean candle,int fail){L1PcInstance p=new L1PcInstance();p.level=level;p.fail=fail;if(marker)p.inv.items.add(new L1ItemInstance(30070,1,0,false));if(candle)p.inv.items.add(new L1ItemInstance(200000,1,0,false));veteranSupport(p,new L1NpcInstance(),bundle);String early=bundle&&level<75?"lind_sm9":bundle&&marker?"lind_sm4":!bundle&&candle?"lind_sm5":null;boolean commit=early==null&&(fail==0||!bundle&&(fail==3||fail==4));boolean pending=early==null&&fail==7;ck(Objects.equals(p.page,early!=null?early:commit?bundle?"lind_sm2":"lind_sm6":null));ck(p.credited.equals(commit?bundle?List.of(30071,30070,200000):List.of(200000):List.of()));ck(p.success==(commit?bundle?3:1:0));ck(p.errors==(early==null&&!commit&&!pending?1:0));}
 static void discard(int mask,int fail,String tail){L1PcInstance p=new L1PcInstance();p.fail=fail;List<Integer> ids=new ArrayList<>();for(int i=0;i<3;i++)if((mask&(1<<i))!=0){ids.add(41312+i);p.inv.items.add(new L1ItemInstance(41312+i,1,0,false));}discardKeplisha(p,new L1NpcInstance(),tail);boolean commit=mask!=0&&(fail==0||fail>=2&&fail<=4&&fail>ids.size()+1);ck(p.debited.equals(commit?ids:List.of()));ck(Objects.equals(p.page,mask==0?tail:commit?"":null));ck(p.errors==(mask!=0&&!commit&&fail!=7?1:0));}
 public static void main(String[] args){for(boolean b:new boolean[]{false,true})for(int l:new int[]{1,74,75,99})for(boolean m:new boolean[]{false,true})for(boolean c:new boolean[]{false,true})for(int f=0;f<8;f++)support(b,l,m,c,f);for(int m=0;m<8;m++)for(int f=0;f<8;f++)for(String tail:new String[]{null,"old"})discard(m,f,tail);
 for(int a:new int[]{-1,0,1,2,1000,1000000000,2000000000,Integer.MAX_VALUE})for(int b:new int[]{-1,0,1,2,999,1000,2000000000})ck(validExpandedAmount(a,b)==(a>0&&b>0&&(long)a*b<=2000000000L));
 for(int a:new int[]{0,1,5,1000000000,2000000000})for(int b:new int[]{0,1,3,1000000000,2000000000})for(int c:new int[]{0,1,3,100,2000,2000000000})for(int d:new int[]{0,1,3,1000,2000000000})for(boolean equipped:new boolean[]{false,true})for(int enchant:new int[]{0,1}){Check x=new Check();x._materials.add(new L1ObjectAmount<>(1,a,0));x._materials.add(new L1ObjectAmount<>(1,b,0));x._items.add(new L1ObjectAmount<>(2,1,0));CUserInventory inv=new CUserInventory();inv.items.add(new L1ItemInstance(1,c,0,false));inv.items.add(new L1ItemInstance(1,d,enchant,equipped));long req=(long)a+b;long avail=(long)c+(!equipped&&enchant==0?d:0);int expected=a<=0||b<=0||req>2000000000L?0:(int)Math.min(999,Math.min(avail,2000000000L)/req);ck(x.countNumOfMaterials(inv)==expected);}
 System.out.println("PASS assertions="+n);}
}'''
with tempfile.TemporaryDirectory() as d:
 f=Path(d)/'Check.java';f.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(f)],check=True);subprocess.run(['java','-cp',d,'Check'],check=True)
