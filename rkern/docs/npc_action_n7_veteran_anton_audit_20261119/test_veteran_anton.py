from pathlib import Path
import tempfile, subprocess, zipfile, re
root=Path(__file__).resolve().parents[2]
s=next((root/'src').rglob('NpcExchangeCommands.java')).read_text()
def extract(signature):
 a=s.index(signature); b=s.index('{',a); depth=1;i=b+1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
methods='\n'.join(extract(x) for x in ['static String veteran(', 'private static boolean veteranClassAllowed(', 'static String anton(', 'private static String antonFailure(', 'static boolean debitLegacy(', 'private static boolean debitEnchant(', 'private static boolean has(', 'private static void sendTail('])
methods+='\n'+re.search(r'private static final int\[\] ANTON_ARMOR = .*?;',s).group()
# Original class masks and output IDs are recovered from the unchanged previous archive.
baseline=Path('/mnt/data/RKern_NPC_Action_N7_Support_XML_Quantity_Audit_FULL_SRC_20261119.zip')
if baseline.exists():
 with zipfile.ZipFile(baseline) as z:
  original=z.read(next(n for n in z.namelist() if n.endswith('/HtmlCmdHandler.java'))).decode()
 old=original[original.index('get_npcId() == 5111)'):original.index('get_npcId() == 5112)')]
 masks=[]; names=['Crown','Knight','Elf','Wizard','Darkelf','Dragonknight','Blackwizard']
 for i,c in enumerate('abcdefghijklmnop'):
  a=old.index('s.equals("'+c+'")'); b=old.index('} else if (s.equals(',a)
  block=old[a:b]; m=re.search(r'createNewItem\(pc, npcName, (\d+), 1, 0\)',block); assert int(m.group(1))==(1126+i if i<8 else 22328+i-8)
  mask=sum(1<<j for j,n in enumerate(names) if 'pc.is'+n+'()' in block);masks.append(mask or 127)
else:
 masks=[31,39,35,84,72,16,32,64,2,117,72,34,127,127,35,84]
java='''import java.util.*;import java.util.logging.*;
public class Check {
 static int n; static void ck(boolean b){if(!b)throw new AssertionError("assertion "+n);n++;}
 static final Logger LOG=Logger.getAnonymousLogger();
 record L1Item(int id){boolean isStackable(){return id==30065||id==41246||id==49015||id==60029||id==30068||id==30069;}}
 static class ItemTable {static ItemTable getInstance(){return new ItemTable();} L1Item getTemplate(int id){return id>0?new L1Item(id):null;}}
 static class L1ItemInstance {int id,c,e;boolean equipped;L1ItemInstance(int id,int c,int e,boolean q){this.id=id;this.c=c;this.e=e;equipped=q;}int getItemId(){return id;}int getCount(){return c;}int getEnchantLevel(){return e;}boolean isEquipped(){return equipped;}void setEnchantLevel(int x){e=x;}String getNumberedName(int x){return id+":"+x+":"+e;}}
 static class Inv {List<L1ItemInstance> items=new ArrayList<>();List<L1ItemInstance> getItems(){return items;}L1ItemInstance findItemId(int id){return items.stream().filter(i->i.id==id).findFirst().orElse(null);}boolean checkItem(int id,int c){return items.stream().anyMatch(i->i.id==id&&i.c>=c);}}
 static class L1PcInstance {Inv inv=new Inv();int type,fail,submitted;List<L1ItemInstance> consumed=new ArrayList<>(),granted=new ArrayList<>();List<String> pages=new ArrayList<>(),msgs=new ArrayList<>();Inv getInventory(){return inv;}int getId(){return 9;}boolean isCrown(){return type==0;}boolean isKnight(){return type==1;}boolean isElf(){return type==2;}boolean isWizard(){return type==3;}boolean isDarkelf(){return type==4;}boolean isDragonknight(){return type==5;}boolean isBlackwizard(){return type==6;}}
 static class L1NpcInstance {int getId(){return 8;}L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "npc";}}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance pc;int step;List<L1ItemInstance> debits=new ArrayList<>(),rewards=new ArrayList<>();Runnable failure;static L1InventoryTransaction begin(L1PcInstance p,int key){if(p.fail==1)throw new IllegalStateException();L1InventoryTransaction t=new L1InventoryTransaction();t.pc=p;t.step=1;return t;}boolean debit(L1ItemInstance i,int c){if(++step==pc.fail||i.equipped||c<=0||c>i.c)return false;debits.add(new L1ItemInstance(i.id,c,i.e,false));return true;}L1ItemInstance grant(int id,int c){if(++step==pc.fail)return null;L1ItemInstance i=new L1ItemInstance(id,c,0,false);rewards.add(i);return i;}void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){if(pc.fail==8)throw new IllegalStateException();pc.submitted++;if(pc.fail==9){failure.run();return;}if(pc.fail==10)return;pc.consumed.addAll(debits);pc.granted.addAll(rewards);r.run();}public void close(){}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page){p.pages.add(id+":"+page);}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int code,String npc,String name){ck(code==143&&npc.equals("npc"));p.msgs.add(name);}}
 static final int[] MASKS={'''+','.join(map(str,masks))+'''};
'''+methods+'''
 static L1ItemInstance item(int id,int c,int e,boolean q){return new L1ItemInstance(id,c,e,q);}
 static void page(L1PcInstance p,String returned){if(returned!=null)SC_NPCTalkReturn.send(p,8,returned);}
 static void veteranCase(int idx,int type,boolean token,int mode,int fail){L1PcInstance p=new L1PcInstance();p.type=type;p.fail=fail;if(token)p.inv.items.add(item(30065,1,0,false));boolean equip=idx<16;int first=idx<19?1126:22328;if(!equip){if(mode>=1)p.inv.items.add(item(first+2,1,7,false));if(mode>=2)p.inv.items.add(item(first,1,6,true));if(mode>=3){p.inv.items.add(item(first+1,1,9,false));p.inv.items.add(item(first+1,1,2,false));}}String command=String.valueOf((char)(equip?'a'+idx:'1'+idx-16));String early=equip?!token?"veteranE1":(MASKS[idx]&(1<<type))==0?"veteranE6":null:mode==0?"veteranE4":null;String failure=equip?"veteranE1":"veteranE4";String success=equip?"veteranE2":"veteranE3";boolean commit=early==null&&!Set.of(1,2,3,8,9,10).contains(fail);boolean pending=early==null&&fail==10;page(p,veteran(p,new L1NpcInstance(),command,null));ck(p.pages.equals(pending?List.of():List.of("8:"+(early!=null?early:commit?success:failure))));ck(p.granted.size()==(commit?1:0));ck(p.consumed.size()==(commit?1:0));ck(p.msgs.size()==(commit?1:0));if(commit){int reward=equip?(idx<8?1126+idx:22328+idx-8):new int[]{60029,30068,30069}[(idx-16)%3];int count=!equip&&(idx-16)%3==0?50:1;ck(p.granted.get(0).id==reward&&p.granted.get(0).c==count&&p.granted.get(0).e==0);ck(p.consumed.get(0).id==(equip?30065:mode>=3?first+1:first+2));ck(p.consumed.get(0).e==(equip?0:mode>=3?2:7));ck(p.msgs.get(0).equals(reward+":"+count+":0"));}}
 static void antonCase(int idx,int mask,int fail,String tail){L1PcInstance p=new L1PcInstance();p.fail=fail;boolean enchanted=idx<16;int group=enchanted?idx/4:idx-16;int e=enchanted?idx%4:0;int old=new int[]{20095,20094,20092,20093}[group];if((mask&1)!=0)p.inv.items.add(item(old,1,0,false));else p.inv.items.add(item(old,1,0,true));if((mask&2)!=0)p.inv.items.add(item(enchanted?41246:49015,enchanted?100000:1,0,false));if((mask&4)!=0)p.inv.items.add(item(20110,1,e+7,false));else p.inv.items.add(item(20110,1,e+7,true));p.inv.items.add(item(20110,1,e+8,false));boolean ready=(mask&3)==3&&(!enchanted||(mask&4)!=0);boolean commit=ready&&!Set.of(1,2,3,8,9,10).contains(fail)&&(!enchanted||fail!=4)&&fail!=(enchanted?5:4);boolean pending=ready&&fail==10;String command=String.valueOf((char)(enchanted?'A'+idx:'1'+idx-16));page(p,anton(p,new L1NpcInstance(),command,tail));List<String> expected=pending?List.of():commit?List.of("8:"):tail==null?List.of("9:anton9"):List.of("9:anton9","8:"+tail);ck(p.pages.equals(expected));ck(p.granted.size()==(commit?1:0));ck(p.consumed.size()==(commit?enchanted?3:2:0));ck(p.msgs.size()==(commit?1:0));if(commit){ck(p.granted.get(0).id==222300+group&&p.granted.get(0).e==e);ck(p.consumed.get(0).id==(enchanted?20110:49015));ck(p.consumed.get(p.consumed.size()-1).id==old);if(enchanted){ck(p.consumed.get(0).e==e+7);ck(p.consumed.get(1).id==41246&&p.consumed.get(1).c==100000);}}}
 public static void main(String[] args){LOG.setLevel(Level.OFF);for(int idx=0;idx<22;idx++)for(int type=0;type<7;type++)for(boolean t:new boolean[]{false,true})for(int m=0;m<4;m++)for(int f=0;f<=10;f++)veteranCase(idx,type,t,m,f);for(int idx=0;idx<20;idx++)for(int m=0;m<8;m++)for(int f=0;f<=10;f++)for(String tail:new String[]{null,"old"})antonCase(idx,m,f,tail);for(int c=0;c<65536;c++){String x=String.valueOf((char)c);L1PcInstance p=new L1PcInstance();if(!(c>='a'&&c<='p')&&!(c>='1'&&c<='6'))ck(veteran(p,new L1NpcInstance(),x,"old").equals("old")&&p.submitted==0);if(!(c>='A'&&c<='P')&&!(c>='1'&&c<='4'))ck(anton(p,new L1NpcInstance(),x,"old").equals("old")&&p.pages.isEmpty());}System.out.println("PASS assertions="+n);}
}'''
with tempfile.TemporaryDirectory() as d:
 f=Path(d)/'Check.java';f.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(f)],check=True);subprocess.run(['java','-cp',d,'Check'],check=True)
