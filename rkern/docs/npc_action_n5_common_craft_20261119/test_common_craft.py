"""Production recipe handler with typed fakes, plus literal recipe mapping audit."""
import re, sys, subprocess, tempfile, zipfile
from pathlib import Path
root=Path(sys.argv[2]) if len(sys.argv)>2 else Path(__file__).resolve().parents[2]
p='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(sys.argv[1]) as z: old=z.read(p+'HtmlCmdHandler.java').decode()
new=(root/p/'HtmlCmdHandler.java').read_text(); handler=(root/p/'NpcCraftCommands.java').read_text()
fields={k:tuple(map(int,v.split(','))) for k,v in re.findall(r'private static final int\[\] (RECIPE_VALUES_\d+) = \{([^}]+)\};',new)}
pattern=r'\b(materials|counts|createitem|createcount)\s*=\s*\(?new int\[\]\s*\{\s*([\d,\s]+)\s*\}\)?;'
oldrows=[(k,tuple(map(int,v.split(',')))) for k,v in re.findall(pattern,old)]
# One unreachable return branch (duplicate checkItem(40312)) is removed.
# Recalculate rather than relying on literal row length for dynamic alternatives.
a=old.index('\t\t\t\t\t} else if (pc.getInventory().checkItem(40312)) {',old.index('} else if (s.equalsIgnoreCase("return")) {')); b=old.index('\n\t\t\t\t\t} else {',a+1)
old=old[:a]+old[b:]; oldrows=[(k,tuple(map(int,v.split(',')))) for k,v in re.findall(pattern,old)]
newrows=[(k,fields[v]) for k,v in re.findall(r'\b(materials|counts|createitem|createcount)\s*=\s*(RECIPE_VALUES_\d+);',new)]
assert newrows==oldrows,(len(oldrows),len(newrows))
assert new.count('Math.multiplyExact(i1,')==10
assert 'Math.multiplyExact(ct, 60)' in new
assert 'NpcCraftCommands.execute(pc, obj, objid, materials, counts,' in new
assert 'tx.commitAfterInventoryRelease' in handler and 'consumeItem(' not in handler and 'storeItem(' not in handler
print('literal recipe vectors:',len(newrows),'static arrays:',len(fields))
code='\n'.join(l for l in handler.splitlines() if not l.startswith(('package ','import '))).replace('final class NpcCraftCommands','static final class NpcCraftCommands')
java=r'''import java.util.*;import java.util.logging.*;
class CraftTest {
 static class CInventory {static final int MAX_AMOUNT=2000000000;}
 static class L1Item {final int id,w; final boolean stack;L1Item(int i,boolean s,int w){id=i;stack=s;this.w=w;}boolean isStackable(){return stack;}int getWeight(){return w;}String getName(){return "i"+id;}}
 static class ItemTable {static final ItemTable t=new ItemTable();static ItemTable getInstance(){return t;}L1Item getTemplate(int id){return id==99?null:new L1Item(id,id!=20,id==30?Integer.MAX_VALUE:1000);}}
 static class L1Object {}
 static class L1NpcInstance extends L1Object {int getNpcId(){return 5;} L1NpcInstance getNpcTemplate(){return this;} String get_name(){return "npc";}}
 static class L1PcInstance { Map<Integer,Integer> items=new HashMap<>();int size=5,weight=0,maxWeight=10000;boolean rejectDebit,rejectGrant,rejectCommit,rejectBegin;String trace="";Runnable success,fail;L1PcInstance getInventory(){return this;}boolean checkItemNotEquipped(int i,int n){return items.getOrDefault(i,0)>=n;}boolean checkItem(int i){return checkItemNotEquipped(i,1);}int getSize(){return size;}int getWeight(){return weight;}int getMaxWeight(){return maxWeight;}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int n,String... args){p.trace+="m"+n+Arrays.toString(args)+";";}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int i,String s,String[] a){p.trace+="h"+s+";";}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.trace+="error;";}}
 static class L1InventoryTransaction implements AutoCloseable {L1PcInstance p;Map<Integer,Integer> stage;Runnable failure;L1InventoryTransaction(L1PcInstance p){this.p=p;stage=new HashMap<>(p.items);}static L1InventoryTransaction begin(L1PcInstance p,int key){if(p.rejectBegin)throw new IllegalStateException();return new L1InventoryTransaction(p);}boolean debit(int i,int n){int old=stage.getOrDefault(i,0);if(p.rejectDebit||old<n)return false;stage.put(i,old-n);return true;}Object grant(int i,int n){if(p.rejectGrant)return null;long count=(long)stage.getOrDefault(i,0)+n;if(count>CInventory.MAX_AMOUNT)return null;stage.put(i,(int)count);return this;}void onFailure(Runnable r){failure=r;}void commitAfterInventoryRelease(Runnable r){if(p.rejectCommit)throw new IllegalStateException();p.success=()->{p.items=stage;r.run();};p.fail=failure;}public void close(){}}
 HANDLER
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){Logger.getLogger(NpcCraftCommands.class.getName()).setLevel(Level.OFF);int n=0;
 for(int count:new int[]{1,2,5,10,100})for(int avail:new int[]{0,1,5,10,100,200})for(int flags=0;flags<16;flags++)for(boolean commit:new boolean[]{false,true})for(boolean dup:new boolean[]{false,true})for(boolean page:new boolean[]{false,true}){
 L1PcInstance p=new L1PcInstance();p.items.put(1,avail);p.rejectBegin=(flags&1)!=0;p.rejectDebit=(flags&2)!=0;p.rejectGrant=(flags&4)!=0;p.rejectCommit=(flags&8)!=0;
 int[] mats=dup?new int[]{1,1}:new int[]{1};int[] costs=dup?new int[]{count,count}:new int[]{count};String fail=page?"fail":null;
 NpcCraftCommands.execute(p,new L1NpcInstance(),4,mats,costs,new int[]{10},new int[]{count},"ok",fail,"tail",null);
 check(p.items.get(1)==avail&&!p.items.containsKey(10),"premature mutation");check(!p.trace.contains("hok;")&&!p.trace.contains("htail;"),"premature success");
 boolean valid=flags==0&&avail>=count*(dup?2:1);check((p.success!=null)==valid,"admission");
 if(valid){if(commit){p.success.run();check(p.items.get(1)==avail-count*(dup?2:1)&&p.items.get(10)==count,"commit");String name="i10"+(count>1?" ("+count+")":"");check(p.trace.equals("m143[npc, "+name+"];hok;htail;"),"normal order "+p.trace);}else{p.fail.run();check(p.items.get(1)==avail&&!p.items.containsKey(10)&&!p.trace.contains("hok;"),"rollback");check(p.trace.equals(page?"hfail;":"error;"),"rollback response");}}else if(avail>=count){check(p.trace.equals(page?"hfail;":"error;"),"prepare failure response");}n++;
 }
 for(int reward:new int[]{10,20,30,99})for(int count:new int[]{-1,0,1,180,181,2000000000,Integer.MAX_VALUE})for(int size:new int[]{0,179,180}){L1PcInstance p=new L1PcInstance();p.items.put(1,1);p.size=size;NpcCraftCommands.execute(p,new L1NpcInstance(),4,new int[]{1},new int[]{1},new int[]{reward},new int[]{count},"ok","fail","tail",null);if(count<=0||count>CInventory.MAX_AMOUNT||reward==99||size==180||(reward==20&&size+(long)count>180)||(reward==30)||count>10000)check(p.success==null,"bad capacity");check(p.items.get(1)==1&&!p.trace.contains("hok;"),"capacity mutation");n++;}
 for(int f=0;f<5;f++){L1PcInstance p=new L1PcInstance();p.items.put(1,1);NpcCraftCommands.execute(p,new L1Object(),4,f==0?null:f==1?new int[]{}:new int[]{1},f==2?null:f==3?new int[]{1,2}:new int[]{f==4?-1:1},new int[]{10},new int[]{1},"ok","fail","tail",null);check(p.success==null&&p.trace.equals("hfail;"),"malformed recipe");n++;}
 System.out.println("Craft handler cases: "+n);
 }
}'''.replace('HANDLER',code)
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'CraftTest.java').write_text(java);subprocess.run(['javac','-encoding','UTF-8','--release','21',str(p/'CraftTest.java')],check=True);subprocess.run(['java','-cp',d,'CraftTest'],check=True)
