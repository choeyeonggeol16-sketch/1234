#!/usr/bin/env python3
"""Compare actual 188 and candidate production debit methods in typed Java21 fixtures.
No MySQL used. Intentionally permits the new path to omit no-op failed Entry allocations.
"""
from pathlib import Path
import subprocess,tempfile
old=(Path(__file__).resolve().parent/'baseline_188_L1InventoryTransaction.java').read_text()
new=(Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text()
def block(s,prefix):
 st=s.index(prefix);br=s.index('{',st);lvl=1;p=br+1
 while lvl:
  if s[p]=='{':lvl+=1
  elif s[p]=='}':lvl-=1
  p+=1
 return s[st:p]
shared='\n'.join(block(new,s) for s in [
'    private Entry entryAt(int index)',
'    private Entry findEntry(int rowId)',
'    private void addEntry(Entry e)',
'    private Entry entry(L1PcInstance pc, L1ItemInstance item)',
'    private static boolean requiresEntityDeletion(int template)',
])
old_methods='\n'.join(block(old,s) for s in ['    public boolean debit(L1PcInstance pc,L1ItemInstance item,int count)', '    public boolean debit(L1PcInstance pc,int templateId,int count)'])
new_methods='\n'.join(block(new,s) for s in ['    public boolean debit(L1PcInstance pc,L1ItemInstance item,int count)', '    public boolean debit(L1PcInstance pc,int templateId,int count)'])
java='''import java.util.*;
public final class DebitCheck {
 static int assertions, snapshots, copies;
 static void check(boolean v,String message){assertions++;if(!v)throw new AssertionError(message);}
 static final class TIntObjectHashMap<E> extends HashMap<Integer,E>{TIntObjectHashMap(int size){super(size);} E get(int i){return super.get(i);} E put(int i,E e){return super.put(i,e);}}
 static final class L1ItemInstance {
  int id,itemId,count;boolean equipped;
  L1ItemInstance(int id,int itemId,int count,boolean equipped){this.id=id;this.itemId=itemId;this.count=count;this.equipped=equipped;}
  int getId(){return id;}int getItemId(){return itemId;}int getCount(){return count;}void setCount(int n){count=n;}
  boolean isEquipped(){return equipped;}
 }
 static final class CUserInventory {
  final ArrayList<L1ItemInstance> all=new ArrayList<>(); int getItemCalls,iterCalls;
  L1ItemInstance getItem(int id){getItemCalls++;for(var i:all)if(i.id==id)return i;return null;}
  Collection<L1ItemInstance> getItems(){iterCalls++;return all;}
 }
 static final class L1PcInstance {
  final int id;CUserInventory inv=new CUserInventory();
  L1PcInstance(int i){id=i;}
  CUserInventory getInventory(){return inv;}
  int getId(){return id;}
 }
 static final class ItemSnapshot {static ItemSnapshot capture(L1ItemInstance a,int owner){snapshots++;return new ItemSnapshot();}}
 static L1ItemInstance copy(L1ItemInstance i){copies++;return new L1ItemInstance(i.id,i.itemId,i.count,i.equipped);}
 static final class Entry {
  final L1PcInstance originalOwner;final L1ItemInstance original;final ItemSnapshot before;final int rowId;
  L1PcInstance owner;L1ItemInstance image;
  Entry(L1PcInstance p,L1ItemInstance i,boolean existing){
    originalOwner=existing?p:null;original=existing?i:null;
    before=existing?ItemSnapshot.capture(i,p.getId()):null;rowId=i.id;owner=p;
    image=existing?DebitCheck.copy(i):i;
  }
 }
 abstract static class Base {
  private static final int INDEX_THRESHOLD=9;
  Object entries;int entryCount;TIntObjectHashMap<Entry> entryIndex;boolean closed,submitted;
  final L1PcInstance primary;
  Base(L1PcInstance p){primary=p;}
  void open(){if(closed||submitted)throw new IllegalStateException("Inventory transaction is closed");}
  void owns(L1PcInstance p){if(p!=primary)throw new IllegalArgumentException("Unreserved inventory owner");}
  %SHARED%
  abstract boolean debit(L1PcInstance pc,L1ItemInstance i,int count);
  abstract boolean debit(L1PcInstance pc,int template,int count);
  String state(){StringJoiner j=new StringJoiner("|");for(int i=0;i<entryCount;i++){
    Entry e=entryAt(i);j.add(e.rowId+":"+e.image.count+":"+e.owner.id+":"+(e.original==null?0:e.original.id));
    }return j.toString();}
  int preparedQty(int id,L1PcInstance p){Entry e=findEntry(id);return e==null?p.inv.getItem(id).count:e.image.count;}
 }
 static final class Legacy extends Base {
  Legacy(L1PcInstance p){super(p);} %OLD%
 }
 static final class Optimized extends Base {
  Optimized(L1PcInstance p){super(p);} %NEW%
 }
 static final class Scenario {
  final L1PcInstance p=new L1PcInstance(3);final Base t;
  Scenario(boolean faster,int n,int stacks,long seed){
    t=faster?new Optimized(p):new Legacy(p);
    Random rand=new Random(seed);
    for(int i=0;i<n;i++){
      int tpl=100+rand.nextInt(stacks),cnt=rand.nextInt(9)+1;
      boolean equipped=rand.nextInt(7)==0;
      p.inv.all.add(new L1ItemInstance(2000+i,tpl,cnt,equipped));
    }
  }
  String committedQty(){StringJoiner j=new StringJoiner("|");for(var i:p.inv.all)j.add(i.id+":"+t.preparedQty(i.id,p));return j.toString();}
 }
 static void pairedCase(int n,int stacks,long seed){
  Scenario legacy=new Scenario(false,n,stacks,seed),optimized=new Scenario(true,n,stacks,seed);
  Random r=new Random(seed+391);
  for(int op=0;op<18;op++){
    int ix=r.nextInt(n);
    L1ItemInstance a=legacy.p.inv.all.get(ix),b=optimized.p.inv.all.get(ix);
    int q=r.nextInt(18)+1; boolean viaTemplate=r.nextBoolean();
    Object ra,rb;
    try {ra=viaTemplate?legacy.t.debit(legacy.p,a.itemId,q):legacy.t.debit(legacy.p,a,q);}catch(Exception ex){ra=ex.getClass().getName();}
    try {rb=viaTemplate?optimized.t.debit(optimized.p,b.itemId,q):optimized.t.debit(optimized.p,b,q);}catch(Exception ex){rb=ex.getClass().getName();}
    check(Objects.equals(ra,rb),"return mismatch "+n+" "+seed+" "+op+" "+ra+" "+rb);
    check(legacy.committedQty().equals(optimized.committedQty()),"prepared quantity mismatch "+n+" "+seed+" "+op);
    // A failed direct debit may legitimately stage a no-op in 188, but must not stage it now.
    if(Objects.equals(ra,Boolean.TRUE)){
      // 188 retains unchanged rows prepared by earlier insufficient debits;
      // the optimized transaction legitimately omits them.
      for(int ki=0;ki<optimized.t.entryCount;ki++){
        Entry en=optimized.t.entryAt(ki);int originalIdx=-1;
        for(int oldi=0;oldi<legacy.t.entryCount;oldi++){
          if(legacy.t.entryAt(oldi).rowId==en.rowId){originalIdx=oldi;break;}
        }
        check(originalIdx>=0,"missing prepared row in baseline");
        check(legacy.t.entryAt(originalIdx).image.count==en.image.count,"committed entry quantity mismatch");
      }
    }
  }
 }
 static void failedFresh(){
  Scenario old=new Scenario(false,1,1,99),now=new Scenario(true,1,1,99);
  L1ItemInstance a=old.p.inv.all.get(0),b=now.p.inv.all.get(0);
  a.equipped=false;b.equipped=false;
  snapshots=0;copies=0;
  check(!old.t.debit(old.p,a,99),"old reject");int oldSnap=snapshots,oldCopy=copies;
  snapshots=0;copies=0;
  check(!now.t.debit(now.p,b,99),"new reject");
  check(oldSnap==1&&oldCopy==1,"baseline eager snapshot expected");
  check(snapshots==0&&copies==0,"new rejected request allocated snapshot or copy");
  check(now.t.entryCount==0,"failed request must not stage a no-op row");
  check(old.t.entryCount==1,"baseline stages a no-op row");
 }
 static void stale(){
   Scenario old=new Scenario(false,1,1,211),now=new Scenario(true,1,1,211);
   var a=new L1ItemInstance(old.p.inv.all.get(0).id,100,1,false);
   var b=new L1ItemInstance(now.p.inv.all.get(0).id,100,1,false);
   Object ra,rb;
   try {old.t.debit(old.p,a,10);ra="OK";}catch(Exception ex){ra=ex.getClass().getName();}
   try {now.t.debit(now.p,b,10);rb="OK";}catch(Exception ex){rb=ex.getClass().getName();}
   check(ra.equals(rb)&&ra.equals(IllegalStateException.class.getName()),"stale object not detected");
 }
 static void singleStackPass(){
  Scenario old=new Scenario(false,1,1,1),now=new Scenario(true,1,1,1);
  old.p.inv.all.get(0).equipped=false;now.p.inv.all.get(0).equipped=false;
  int q=old.p.inv.all.get(0).count;
  check(old.t.debit(old.p,old.p.inv.all.get(0).itemId,q),"old success");
  check(now.t.debit(now.p,now.p.inv.all.get(0).itemId,q),"new success");
  check(old.p.inv.iterCalls==2&&now.p.inv.iterCalls==1,"single-stack expected one scan, old two");
 }
 public static void main(String[] args){
  failedFresh();stale();singleStackPass();
  for(int n:new int[]{1,2,3,5,8,9,16,32,80,128})
   for(int stacks:new int[]{1,2,3,10})for(int seed=1;seed<=45;seed++)pairedCase(n,stacks,17L*seed+n*555L+stacks);
  System.out.println("PASS "+assertions+" typed debit conditions");
 }
}
'''
java=java.replace('%SHARED%',shared.replace('private ','')).replace('%OLD%',old_methods).replace('%NEW%',new_methods)
# Base abstract methods cannot be overridden with package-private? yes.
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'DebitCheck.java';p.write_text(java)
 a=subprocess.run(['javac','--release','21',str(p)],capture_output=True,text=True)
 if a.returncode:print(a.stderr);raise SystemExit(a.returncode)
 b=subprocess.run(['java','-cp',d,'DebitCheck'],capture_output=True,text=True,timeout=80)
 print(b.stdout,b.stderr);raise SystemExit(b.returncode)
