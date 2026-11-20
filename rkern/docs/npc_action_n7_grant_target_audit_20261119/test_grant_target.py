from pathlib import Path
import re, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
p=root/'src/l1j/server/server/model/network/core/cs/packet'
s=(p/'NpcExchangeCommands.java').read_text(); a=s.index('static boolean createNewItem('); b=s.index('{',a)+1; i=b; depth=1
while depth:
 depth+=(s[i]=='{')-(s[i]=='}'); i+=1
method=s[a:i]
java='''import java.util.*;
public class Check {
 static int n;static void ck(boolean b){if(!b)throw new AssertionError("assertion "+n);n++;}
 static class L1ItemInstance {int id,count,enchant;L1ItemInstance(int id){this.id=id;}int getId(){return id;}void setCount(int n){count=n;}void setEnchantLevel(int e){enchant=e;}String getLogName(){return count+":"+enchant;}}
 static class L1Inventory {static final int MAX_AMOUNT=2000000000,OK=0;int mode,check,stores;Map<Integer,L1ItemInstance> m=new HashMap<>();int checkAddItem(L1ItemInstance i,int n){if(check==2)throw new IllegalStateException();return check;} L1ItemInstance getItem(int id){return m.get(id);} L1ItemInstance storeItem(L1ItemInstance i){stores++;switch(mode){case 0:m.put(i.id,i);return i;case 1:return m.get(99);case 2:return null;case 3:throw new IllegalStateException();case 4:m.put(i.id,i);throw new IllegalStateException();default:throw new AssertionError();}}}
 static class L1World {static L1World w;Map<Integer,L1ItemInstance> m=new HashMap<>();L1Inventory ground=new L1Inventory();int removes;static L1World getInstance(){return w;}L1Inventory getInventory(int x,int y,short map){return ground;}Object findObject(int id){return m.get(id);}void removeObject(L1ItemInstance i){ck(m.remove(i.id,i));removes++;}}
 static class ItemTable {static ItemTable t;boolean missing;int creates;static ItemTable getInstance(){return t;}L1ItemInstance createItem(int id){creates++;if(missing)return null;L1ItemInstance i=new L1ItemInstance(1);L1World.w.m.put(i.id,i);return i;}}
 static class L1PcInstance {L1Inventory inv=new L1Inventory();int messages;boolean throwMessage;String log;L1Inventory getInventory(){return inv;}int getX(){return 1;}int getY(){return 2;}short getMapId(){return 3;}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int code,String name,String log){ck(code==143&&name.equals("npc"));p.messages++;p.log=log;if(p.throwMessage)throw new IllegalStateException();}}
'''+method+'''
 static void test(int count,int enchant,boolean missing,int check,int mode,boolean msgThrow){
  L1World.w=new L1World();ItemTable.t=new ItemTable();ItemTable.t.missing=missing;L1PcInstance p=new L1PcInstance();p.inv.check=check;p.throwMessage=msgThrow;
  L1Inventory dest=check==0?p.inv:L1World.w.ground;dest.mode=mode;L1ItemInstance old=new L1ItemInstance(99);dest.m.put(99,old);L1World.w.m.put(99,old);
  boolean valid=count>0&&count<=L1Inventory.MAX_AMOUNT;boolean found=valid&&!missing;boolean called=found&&check!=2;boolean success=called&&mode<2;boolean thrown=found&&(check==2||mode>=3||success&&msgThrow);boolean retained=called&&(mode==0||mode==4);
  boolean r=false,ex=false;try{r=createNewItem(p,"npc",100,count,enchant);}catch(IllegalStateException e){ex=true;}
  ck(r==(success&&!msgThrow));ck(ex==thrown);ck(ItemTable.t.creates==(valid?1:0));ck(dest.stores==(called?1:0));ck(p.messages==(success?1:0));ck(L1World.w.m.get(99)==old);ck(L1World.w.m.containsKey(1)==retained);ck(L1World.w.removes==(found&&!retained?1:0));if(success)ck(p.log.equals(count+":"+enchant));
 }
 public static void main(String[] args){for(int count:new int[]{Integer.MIN_VALUE,-1,0,1,120,2000000000,2000000001})for(int e:new int[]{-1,0,7,10})for(boolean missing:new boolean[]{false,true})for(int check=0;check<3;check++)for(int mode=0;mode<5;mode++)for(boolean throwMessage:new boolean[]{false,true})test(count,e,missing,check,mode,throwMessage);System.out.println("PASS assertions="+n);}
}'''
with tempfile.TemporaryDirectory() as d:
 f=Path(d)/'Check.java';f.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(f)],check=True);subprocess.run(['java','-cp',d,'Check'],check=True)
h=(p/'HtmlCmdHandler.java').read_text();assert h.index('if (dispatchRegisteredAction(pc, obj, s, actionData)) return;')<h.index('if (!(obj instanceof L1NpcInstance npc)) return;',h.index('if (dispatchRegisteredAction(pc, obj, s, actionData)) return;'))<h.index('NpcCastleActions.sendTaxScreens(pc, obj, s);')
commands='\n'.join(f.read_text() for f in p.glob('Npc*HtmlCommands.java'))
assert re.search(r'getClan\(pc.getClanid\(\)\);\s*if \(clan == null\) return;\s*int houseId',commands)
assert 'clan != null && clan.getCastleId() != 0' in commands
print('PASS action target, clan and tax guard order (static)')
