#!/usr/bin/env python3
"""Typed Java 21 comparisons of actual transfer/change/enchant production methods."""
from pathlib import Path
import zipfile, tempfile, subprocess
old=Path(__file__).with_name('baseline_191_L1InventoryTransaction.java').read_text()
new=(Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text()
def block(text,search):
    st=text.index(search); start=text.index('{',st); depth=1; end=start+1
    while depth:
        if text[end]=='{':depth+=1
        if text[end]=='}':depth-=1
        end+=1
    return text[st:end]
common='\n'.join(block(new,key) for key in (
'    private Entry entryAt(int index)',
'    private Entry findEntry(int rowId)',
'    private void addEntry(Entry e)',
'    private Entry entry(L1PcInstance pc, L1ItemInstance item)',
'    private L1ItemInstance grantCopyPrepared(L1PcInstance pc,L1ItemInstance image,int count,boolean fresh)',
))
oldmeth='\n'.join(block(old,key) for key in (
'    public L1ItemInstance transfer(L1PcInstance from,L1ItemInstance item,int count,L1PcInstance to)',
'    public void change(L1PcInstance pc,L1ItemInstance item,Consumer<L1ItemInstance> mutation)',
'    public void changeEnchantLevel(L1ItemInstance item, int expectedLevel, int nextLevel)',
))
newmeth='\n'.join(block(new,key) for key in (
'    public L1ItemInstance transfer(L1PcInstance from,L1ItemInstance item,int count,L1PcInstance to)',
'    public void change(L1PcInstance pc,L1ItemInstance item,Consumer<L1ItemInstance> mutation)',
'    public void changeEnchantLevel(L1ItemInstance item, int expectedLevel, int nextLevel)',
))
java='''import java.util.*;
import java.util.function.Consumer;
public final class PreflightCheck {
 static long assertions;
 static int snapshots,copies;
 static void ck(boolean truth,String msg){assertions++;if(!truth) throw new AssertionError(msg);}
 static final class TIntObjectHashMap<E> extends HashMap<Integer,E> {TIntObjectHashMap(int size){super(size);}E get(int id){return super.get(id);} E put(int i,E o){return super.put(i,o);}}
 static final class L1Item {final int type2; L1Item(int t){type2=t;}int getType2(){return type2;}}
 static final class L1ItemInstance {
  int id,tpl,count,enchant;boolean equipped,stackable;L1Item item;
  L1ItemInstance(int id,int tpl,int count,int enchant,boolean equipped,boolean stackable,int type2){this.id=id;this.tpl=tpl;this.count=count;this.enchant=enchant;this.equipped=equipped;this.stackable=stackable;this.item=new L1Item(type2);}
  int getId(){return id;}void setId(int v){id=v;}int getItemId(){return tpl;}
  int getCount(){return count;}void setCount(int n){count=n;}
  int getEnchantLevel(){return enchant;}void setEnchantLevel(int n){enchant=n;}
  boolean isEquipped(){return equipped;}void setEquipped(boolean e){equipped=e;}
  boolean isStackable(){return stackable;}L1Item getItem(){return item;}
  int get_bless_level(){return 0;} void setBless(int i){}
 }
 static final class L1Inventory {static final int MAX_AMOUNT=2000000000;}
 static final class L1PcInventory {static final int MAX_SIZE=200;final ArrayList<L1ItemInstance> rows=new ArrayList<>(); L1ItemInstance getItem(int id){for(var item:rows)if(item.id==id)return item;return null;} Collection<L1ItemInstance> getItems(){return rows;}}
 static final class L1PcInstance {final int id;final L1PcInventory inventory=new L1PcInventory();L1PcInstance(int id){this.id=id;}int getId(){return id;} L1PcInventory getInventory(){return inventory;}}
 static final class ItemSnapshot {static ItemSnapshot capture(L1ItemInstance item,int owner){snapshots++;return new ItemSnapshot();}}
 static L1ItemInstance copy(L1ItemInstance item){copies++;return new L1ItemInstance(item.id,item.tpl,item.count,item.enchant,item.equipped,item.stackable,item.item.type2);}
 static boolean mergeable(L1ItemInstance a,L1ItemInstance b){return a.stackable&&b.stackable&&a.tpl==b.tpl&&a.enchant==b.enchant;}
 static final class IdFactory {static final IdFactory INSTANCE=new IdFactory(); int counter=90000;static IdFactory getInstance(){return INSTANCE;}int nextId(){return counter++;}}
 static final class Entry {
  final L1PcInstance originalOwner;final L1ItemInstance original;final ItemSnapshot before;final int rowId;
  L1PcInstance owner;L1ItemInstance image;
  Entry(L1PcInstance pc,L1ItemInstance item,boolean existing){originalOwner=existing?pc:null;original=existing?item:null;before=existing?ItemSnapshot.capture(item,pc.id):null;rowId=item.id;owner=pc;image=existing?PreflightCheck.copy(item):item;}
 }
 abstract static class Base {
  private static final int INDEX_THRESHOLD=9; Object entries;int entryCount;
  TIntObjectHashMap<Entry> entryIndex;boolean closed,submitted;final L1PcInstance primary,second;
  Base(L1PcInstance a,L1PcInstance b){primary=a;second=b;}
  void open(){if(closed||submitted)throw new IllegalStateException("Inventory transaction is closed");}
  void owns(L1PcInstance p){if(p!=primary&&p!=second)throw new IllegalArgumentException("Unreserved inventory owner");}
  %COMMON%
  abstract L1ItemInstance transfer(L1PcInstance a,L1ItemInstance i,int count,L1PcInstance b);
  abstract void change(L1PcInstance pc,L1ItemInstance item,Consumer<L1ItemInstance> mutation);
  abstract void changeEnchantLevel(L1ItemInstance item,int expected,int next);
  String state(){ArrayList<String> j=new ArrayList<>();for(int k=0;k<entryCount;k++){
   Entry e=entryAt(k);if(e.original!=null&&e.owner==e.originalOwner&&e.image.count==e.original.count&&e.image.enchant==e.original.enchant)continue;j.add(e.rowId+":"+e.image.count+":"+e.owner.id+":"+e.image.enchant+":"+(e.original==null?0:e.original.id));
  }Collections.sort(j);return String.join("|",j);}
 }
 static final class Legacy extends Base {Legacy(L1PcInstance a,L1PcInstance b){super(a,b);} %OLD%}
 static final class Candidate extends Base {Candidate(L1PcInstance a,L1PcInstance b){super(a,b);} %NEW%}
 static final class Scene {
  final L1PcInstance a=new L1PcInstance(1),b=new L1PcInstance(2);
  final Base tx; final L1ItemInstance[] from;final L1ItemInstance[] to;
  Scene(boolean optimized,int n,long seed){
    tx=optimized?new Candidate(a,b):new Legacy(a,b);
    Random r=new Random(seed); from=new L1ItemInstance[n];to=new L1ItemInstance[n/2];
    for(int i=0;i<n;i++){
      var item=new L1ItemInstance(i+100,200+r.nextInt(3),r.nextInt(9)+1,r.nextInt(5),r.nextInt(11)==0,r.nextBoolean(),r.nextInt(3));
      from[i]=item;a.inventory.rows.add(item);
    }
    for(int i=0;i<n/2;i++){
      var item=new L1ItemInstance(i+1000,200+r.nextInt(3),r.nextInt(9)+1,r.nextInt(5),false,r.nextBoolean(),r.nextInt(3));
      to[i]=item;b.inventory.rows.add(item);
    }
  }
 }
 interface Attempt {Object run() throws Exception;}
 static Object outcome(Attempt a){try{Object o=a.run();return o==null?"OK":o instanceof L1ItemInstance?"ITEM":o;}catch(Exception e){return e.getClass().getName()+":"+e.getMessage();}}
 static void compareTransfer(int n,long seed){
   var old=new Scene(false,n,seed);var now=new Scene(true,n,seed);var r=new Random(seed*31+7);
   int totalOldCopies=0,totalOldSnapshots=0,totalNewCopies=0,totalNewSnapshots=0;
   for(int i=0;i<14;i++){
      int idx=r.nextInt(n),amount=1+r.nextInt(24);
      var left=old.from[idx];var right=now.from[idx];
      int originalCopies=copies,originalSnap=snapshots;
      IdFactory.INSTANCE.counter=90000;Object a=outcome(()->old.tx.transfer(old.a,left,amount,old.b));
      int deltaOldCopies=copies-originalCopies,deltaOldSnapshots=snapshots-originalSnap;
      originalCopies=copies;originalSnap=snapshots;
      IdFactory.INSTANCE.counter=90000;Object b=outcome(()->now.tx.transfer(now.a,right,amount,now.b));
      int deltaNewCopies=copies-originalCopies,deltaNewSnapshots=snapshots-originalSnap;
      ck(Objects.equals(a,b),"transfer outcome "+n+" "+seed+" "+i+" "+a+" "+b);
      ck(old.tx.state().equals(now.tx.state()),"transfer staged state "+n+" "+seed+" "+i+" "+old.tx.state()+" / "+now.tx.state());
      totalOldCopies+=deltaOldCopies;totalOldSnapshots+=deltaOldSnapshots;
      totalNewCopies+=deltaNewCopies;totalNewSnapshots+=deltaNewSnapshots;
   }
   ck(totalNewCopies<=totalOldCopies&&totalNewSnapshots<=totalOldSnapshots,"cumulative transfer allocation regression");
 }
 static void failedTransfer(){
  var old=new Scene(false,1,15);var now=new Scene(true,1,15);
  old.from[0].equipped=false; now.from[0].equipped=false;
  snapshots=copies=0;
  ck(outcome(()->old.tx.transfer(old.a,old.from[0],99,old.b)).toString().contains("Insufficient"),"baseline invalid transfer");
  int oldSnap=snapshots,oldCopy=copies;
  snapshots=copies=0;
  ck(outcome(()->now.tx.transfer(now.a,now.from[0],99,now.b)).toString().contains("Insufficient"),"new invalid transfer");
  ck(oldSnap==1&&oldCopy==1&&snapshots==0&&copies==0,"insufficient transfer must allocate zero snapshots/copies");
 }
 static void mutationCases(){
  for(int seed=0;seed<10000;seed++){
    var old=new Scene(false,1,seed+51);var now=new Scene(true,1,seed+51);
    var a=old.from[0];var b=now.from[0];
    int mode=seed%8;
    if(mode==0){a.equipped=b.equipped=true;}
    if(mode==1){a.enchant=b.enchant=5;}
    if(mode==2){a.item=b.item=new L1Item(0);}
    if(mode==3){a.enchant=b.enchant=2;}
    if(mode==4){a.equipped=b.equipped=false;}
    if(mode==5){a.count=b.count=1;}
    if(mode==6){a.equipped=b.equipped=true;a.enchant=b.enchant=0;}
    if(mode==7){a.equipped=b.equipped=false;a.enchant=b.enchant=0;}
    if((seed&1)==0){
      snapshots=copies=0;
      Object x=outcome(()->{old.tx.change(old.a,a,it->it.setEnchantLevel(it.getEnchantLevel()+1));return null;});
      int sa=snapshots,ca=copies;
      snapshots=copies=0;
      Object y=outcome(()->{now.tx.change(now.a,b,it->it.setEnchantLevel(it.getEnchantLevel()+1));return null;});
      ck(x.equals(y),"generic change result");ck(old.tx.state().equals(now.tx.state()),"generic change state");
      ck(snapshots<=sa && copies<=ca,"generic change allocation regression");
      if(a.equipped) ck(snapshots==0&&copies==0,"equipped change must not snapshot");
    }else{
      int expected=(mode==1||mode==3)?0:a.enchant;
      int next=expected+(mode==4?2:1);
      snapshots=copies=0;
      Object x=outcome(()->{old.tx.changeEnchantLevel(a,expected,next);return null;});
      int sa=snapshots,ca=copies;
      snapshots=copies=0;
      Object y=outcome(()->{now.tx.changeEnchantLevel(b,expected,next);return null;});
      ck(x.equals(y),"enchant change result "+seed+" old="+x+" new="+y);
      ck(old.tx.state().equals(now.tx.state()),"enchant state");
      ck(snapshots<=sa&&copies<=ca,"enchant allocation regression");
      if((a.enchant!=expected||a.item.getType2()==0||next!=expected+1)) ck(snapshots==0&&copies==0,"invalid enchant must not stage");
    }
  }
 }
 public static void main(String[] args){
  failedTransfer();
  for(int n:new int[]{1,2,3,5,8,9,16,32})for(int i=0;i<180;i++)compareTransfer(n,10L*i+n*1003L);
  mutationCases();
  System.out.println("PASS "+assertions+" transfer/generic-change/enchant typed conditions");
 }
}
'''
java=java.replace('%COMMON%',common.replace('private ','')).replace('%OLD%',oldmeth).replace('%NEW%',newmeth)
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/'PreflightCheck.java';p.write_text(java)
    c=subprocess.run(['javac','--release','21',str(p)],capture_output=True,text=True)
    if c.returncode: print(c.stderr);raise SystemExit(c.returncode)
    r=subprocess.run(['java','-cp',d,'PreflightCheck'],capture_output=True,text=True,timeout=90)
    print(r.stdout,r.stderr)
    raise SystemExit(r.returncode)
