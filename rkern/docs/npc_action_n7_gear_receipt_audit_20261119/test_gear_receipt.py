from pathlib import Path
import tempfile, subprocess, re, zipfile
root=Path(__file__).resolve().parents[2]
s=next((root/'src').rglob('NpcExchangeCommands.java')).read_text()
def extract(signature):
 a=s.index(signature); b=s.index('{',a); depth=1;i=b+1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
methods='\n'.join(extract(x) for x in ['static String fireGolem(', 'private static String fireArmorFailure(', 'static String 비자야(', 'static void PiersItem(', 'static void PiersCheckItem(', 'private static void clearPiersSelection(', 'private static boolean validPiersSelection(', 'static String piers(', 'private static boolean debitEnchant(', 'static boolean debitLegacy(', 'private static void sendTail('])
methods+='\n'+re.search(r'private static final int\[\] ANTON_ARMOR = .*?;',s).group()
base=Path('/mnt/data/RKern_NPC_Action_N7_Veteran_Anton_Receipt_Audit_FULL_SRC_20261119.zip')
if base.exists():
 with zipfile.ZipFile(base) as z: old=z.read(next(n for n in z.namelist() if n.endswith('/NpcExchangeCommands.java'))).decode()
 arm=old[old.index('static String fireGolem('):old.index('static boolean createNewItem(')]
 assert re.findall(r'oldArmor = (\d+);',arm)[1:]==['20095','20094','20092','20093']
 assert re.findall(r'itemId = (\d+);',arm)[1:]==['222300','222301','222302','222303']
 assert 'consumeItem(41246, 100000)' in arm and 'enchant - 7' in arm
 for c in 'ABCD': assert f's.equals("{c}")' in old[old.index('static String 비자야('):old.index('static void PiersItem(')]
java='''import java.util.*;import java.util.logging.*;
public class Check {
 static int checks, cases; static void ck(boolean b){if(!b)throw new AssertionError("assertion "+checks+" case "+cases);checks++;}
 static final Logger LOG=Logger.getAnonymousLogger();
 record L1Item(int id){boolean isStackable(){return id==40308||id==41246;}}
 static class ItemTable {static boolean invalid, stack;static ItemTable getInstance(){return new ItemTable();}L1Item getTemplate(int id){return invalid&&id>=222300?null:stack&&id>=222300?new L1Item(40308):new L1Item(id);}}
 static class L1Object {int getId(){return 8;}}
 static class L1NpcInstance extends L1Object {L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "npc";}}
 static int seq=100;
 static class L1ItemInstance {int oid=++seq,id,c,e;boolean equipped; L1ItemInstance(int i,int n,int en,boolean q){id=i;c=n;e=en;equipped=q;}int getId(){return oid;}int getItemId(){return id;}int getCount(){return c;}int getEnchantLevel(){return e;}boolean isEquipped(){return equipped;}void setEnchantLevel(int x){e=x;}String getLogName(){return id+":"+e;}String getName(){return "item"+id;}}
 static class Inv {List<L1ItemInstance> items=new ArrayList<>();List<L1ItemInstance> getItems(){return items;}L1ItemInstance getItem(int oid){return items.stream().filter(i->i.oid==oid).findFirst().orElse(null);}L1ItemInstance findItemId(int id){return items.stream().filter(i->i.id==id).findFirst().orElse(null);}boolean checkItem(int id,int c){return items.stream().anyMatch(i->i.id==id&&i.c>=c);}}
 static class L1PcInstance {Inv inv=new Inv();int fail,submitted,delegated;int[] PiersItemId=new int[19],PiersEnchant=new int[19];List<L1ItemInstance> consumed=new ArrayList<>(),granted=new ArrayList<>();List<String> pages=new ArrayList<>(),msgs=new ArrayList<>();Runnable complete,rollback;Inv getInventory(){return inv;} }
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance pc;int step;List<L1ItemInstance> debits=new ArrayList<>(),rewards=new ArrayList<>();Runnable failure;
 static L1InventoryTransaction begin(L1PcInstance p,int key){if(p.fail==1||p.complete!=null)throw new IllegalStateException();L1InventoryTransaction t=new L1InventoryTransaction();t.pc=p;t.step=1;return t;}
 boolean debit(L1ItemInstance i,int c){if(++step==pc.fail||i.equipped||c<=0||c>i.c)return false;debits.add(i);return true;}
 L1ItemInstance grant(int id,int c){if(++step==pc.fail)return null;L1ItemInstance i=new L1ItemInstance(id,c,0,false);rewards.add(i);return i;}
 void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){if(pc.fail==8)throw new IllegalStateException();pc.submitted++;pc.complete=()->{pc.consumed.addAll(debits);pc.granted.addAll(rewards);pc.complete=null;r.run();};pc.rollback=()->{pc.complete=null;failure.run();};}public void close(){} }
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page){ck(id==8);p.pages.add(page);}static void send(L1PcInstance p,int id,String page,String[] list){ck(list.length==10);send(p,id,page);}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int code,String npc,String name){ck(code==143&&npc.equals("npc"));p.msgs.add(name);}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String m){p.msgs.add(m);}}
 static String fireGolemWeapon(L1PcInstance p,int o,String cmd,String tail){p.delegated++;return tail;}
'''+methods+'''
 static L1ItemInstance add(L1PcInstance p,int id,int count,int e,boolean q){L1ItemInstance i=new L1ItemInstance(id,count,e,q);p.inv.items.add(i);return i;}
 static void page(L1PcInstance p,String r){if(r!=null)p.pages.add(r);}
 static void finish(L1PcInstance p,int mode){if(p.complete!=null&&mode==1)p.complete.run();else if(p.complete!=null&&mode==2)p.rollback.run();}
 static void armor(int idx,int mask,int fail,int mode,boolean bad,boolean stack){cases++;L1PcInstance p=new L1PcInstance();p.fail=fail;ItemTable.invalid=bad;ItemTable.stack=stack;
 int group=idx/4,e=idx%4,old=new int[]{20095,20094,20092,20093}[group];add(p,20110,1,e+7,true);L1ItemInstance a=add(p,20110,1,e+7,(mask&1)==0);L1ItemInstance b=add(p,41246,(mask&2)==0?99999:100000,0,false);L1ItemInstance c=add(p,old,1,0,(mask&4)==0);
 boolean ready=mask==7&&!bad&&!stack&&!Set.of(1,2,3,4,5,8).contains(fail);
 page(p,fireGolem(p,new L1NpcInstance(),String.valueOf((char)('A'+idx)),"tail"));ck((p.complete!=null)==ready);ck(p.consumed.isEmpty()&&p.granted.isEmpty());if(ready)ck(p.pages.isEmpty()&&p.msgs.isEmpty());finish(p,mode);
 boolean success=ready&&mode==1;ck(p.granted.size()==(success?1:0));ck(p.consumed.equals(success?List.of(a,b,c):List.of()));ck(p.pages.equals(success?List.of(""):ready&&mode==0?List.of():List.of("tail")));if(success)ck(p.granted.get(0).id==222300+group&&p.granted.get(0).e==e);ItemTable.invalid=false;ItemTable.stack=false;}
 static void vijaya(int idx,int mask,int fail,int mode){cases++;L1PcInstance p=new L1PcInstance();p.fail=fail;int e=8+idx%2,price=idx%2==0?5000000:10000000;add(p,503+idx/2,1,e,true);L1ItemInstance a=add(p,503+idx/2,1,e,(mask&1)==0);L1ItemInstance b=add(p,40308,price-((mask&2)==0?1:0),0,false);boolean ready=mask==3&&!Set.of(1,2,3,4,8).contains(fail);page(p,비자야(p,new L1NpcInstance(),String.valueOf((char)('A'+idx))));ck((p.complete!=null)==ready);ck(p.granted.isEmpty()&&p.consumed.isEmpty());finish(p,mode);boolean success=ready&&mode==1;ck(p.pages.equals(success?List.of("vjaya05"):ready&&mode==0?List.of():List.of("vjaya04")));ck(p.consumed.equals(success?List.of(a,b):List.of()));ck(p.granted.size()==(success?1:0));if(success)ck(p.granted.get(0).id==30119+idx%2&&p.granted.get(0).e==0);}
 static void piersCase(int ix,int stale,int fail,int mode){cases++;L1PcInstance p=new L1PcInstance();p.fail=fail;int e=8+ix/2,price=e==8?5000000:10000000;add(p,13,1,e,true);L1ItemInstance first=add(p,13,1,e,false),selected=add(p,13,1,e,false),cash=add(p,40308,price,0,false);PiersCheckItem(p,8,e,price,30018+ix,e-1);ck(p.pages.equals(List.of("piers00")));ck(p.PiersItemId[0]==first.oid&&p.PiersItemId[1]==selected.oid&&p.PiersItemId[2]==0);p.pages.clear();if(stale==1)p.inv.items.remove(selected);if(stale==2)selected.equipped=true;if(stale==3)selected.e++;if(stale==4)cash.c--;boolean ready=stale==0&&!Set.of(1,2,3,4,8).contains(fail);
 PiersItem(p,8,"npc",p.PiersItemId[1],p.PiersEnchant[1],p.PiersItemId[18]);ck((p.complete!=null)==ready);ck(p.granted.isEmpty()&&p.consumed.isEmpty());if(ready){for(int v:p.PiersItemId)ck(v==0);for(int v:p.PiersEnchant)ck(v==0);ck(p.pages.isEmpty()); // a newer menu must not be erased by delayed callback
 p.PiersItemId[0]=999;}
 finish(p,mode);boolean success=ready&&mode==1;ck(p.pages.equals(success?List.of(""):ready&&mode==0?List.of():List.of("piers04")));ck(p.consumed.equals(success?List.of(selected,cash):List.of()));ck(p.granted.size()==(success?1:0));if(success)ck(p.granted.get(0).id==30018+ix&&p.granted.get(0).e==e-1);if(ready)ck(p.PiersItemId[0]==999);}
 public static void main(String[] args){LOG.setLevel(Level.OFF);for(int i=0;i<16;i++)for(int mask=0;mask<8;mask++)for(int f:new int[]{0,1,2,3,4,5,8})for(int m=0;m<3;m++)armor(i,mask,f,m,false,false);for(int i=0;i<16;i++){armor(i,7,0,1,true,false);armor(i,7,0,1,false,true);}for(int i=0;i<4;i++)for(int mask=0;mask<4;mask++)for(int f:new int[]{0,1,2,3,4,8})for(int m=0;m<3;m++)vijaya(i,mask,f,m);for(int i=0;i<4;i++)for(int st=0;st<5;st++)for(int f:new int[]{0,1,2,3,4,8})for(int m=0;m<3;m++)piersCase(i,st,f,m);for(int c=0;c<65536;c++){String cmd=String.valueOf((char)c);L1PcInstance p=new L1PcInstance();if(!(c>='A'&&c<='P'))ck(fireGolem(p,new L1NpcInstance(),cmd,"tail").equals("tail")&&p.delegated==1);if(!(c>='A'&&c<='D'))ck(비자야(p,new L1NpcInstance(),cmd).equals("vjaya04")&&p.submitted==0);}System.out.println("PASS cases="+cases+" assertions="+checks);}
}'''
with tempfile.TemporaryDirectory() as d:
 f=Path(d)/'Check.java';f.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(f)],check=True);subprocess.run(['java','-cp',d,'Check'],check=True)
