#!/usr/bin/env python3
"""Compare actual production transaction entry/debit/grant/transfer methods (178 vs candidate).
Fixtures replace external game dependencies, not the methods under test. Java 21.
"""
from pathlib import Path
import tempfile, subprocess, re
root=Path(__file__).resolve().parent
old=(Path(__file__).resolve().parents[2]/'docs/item_memory_phase179_181_20261118/baseline_178_L1InventoryTransaction.java').read_text('utf-8')
new=(Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text('utf-8')
def block(s, sig):
    a=s.index(sig)
    b=s.index('{', a)
    level=1;i=b+1
    while level:
        if s[i]=='{': level+=1
        elif s[i]=='}': level-=1
        i+=1
    return s[a:i]
def cls_block(s, sig):
    return block(s,sig)
common_entry=cls_block(new,'    private static final class Entry {')
new_store='\n'.join([
    '    private static final int INDEX_THRESHOLD = 9;',
    '    private Object entries;',
    '    private int entryCount;',
    '    private TIntObjectHashMap<Entry> entryIndex;',
    block(new,'    private Entry entryAt(int index)'),
    block(new,'    private Entry findEntry(int rowId)'),
    block(new,'    private void addEntry(Entry e)'),
])
old_store='    private final LinkedHashMap<Integer,Entry> entries = new LinkedHashMap<>();'
# Typed wrappers emulate same participants and live item identity checks.
methods=[
 '    private Entry entry(L1PcInstance pc, L1ItemInstance item)',
 '    public boolean debit(L1PcInstance pc,L1ItemInstance item,int count)',
 '    public boolean debit(L1PcInstance pc,int templateId,int count)',
 '    public L1ItemInstance grant(L1ItemInstance image)',
 '    public L1ItemInstance grantCopy(L1PcInstance pc,L1ItemInstance image,int count)',
 '    private L1ItemInstance grantCopyPrepared(',
 '    public L1ItemInstance transfer(L1PcInstance from,L1ItemInstance item,int count,L1PcInstance to)',
]
new_methods='\n'.join(block(new,s) for s in methods)
old_methods='\n'.join(block(old,s) for s in methods)
# Both implement the same shared support methods, preserving unchanged production code.
support='\n'.join([
 block(new,'    private static boolean requiresEntityDeletion(int template)'),
 block(new,'    private static boolean mergeable('),
]).replace('private static boolean', 'static boolean')
# TestGrant external method needs entry image to remain detached.
java=r'''import java.util.*;
import gnu.trove.map.hash.TIntObjectHashMap;

public final class TestEntryMemory {
  static int copies,asserts;
  static L1ItemInstance copy(L1ItemInstance a) {
    copies++;
    L1ItemInstance b = new L1ItemInstance(a.item,a.count);
    b.id=a.id;b.enchant=a.enchant;b.bless=a.bless;b.equipped=a.equipped;
    b.charge=a.charge;b.remaining=a.remaining;b.attr=a.attr;b.special=a.special;
    b.blessLevel=a.blessLevel;b.blessLevel2=a.blessLevel2;b.level=a.level;
    b.pack=a.pack;b.expiry=a.expiry;b.last=a.last;b.hotel=a.hotel;return b;
  }
  static class CInventory {static final int MAX_AMOUNT=1000000;}
  static class CUserInventory {
    static final int MAX_SIZE=128;
    final ArrayList<L1ItemInstance> list=new ArrayList<>();
    Collection<L1ItemInstance> getItems(){return list;}
    L1ItemInstance getItem(int id){for(var i:list)if(i.id==id)return i;return null;}
  }
  static class L1PcInstance {
    final int id; final CUserInventory inventory = new CUserInventory();
    L1PcInstance(int id){this.id=id;}
    CUserInventory getInventory(){return inventory;}
    int getId(){return id;}
  }
  static class L1Item {
    final int id; final boolean stack;
    L1Item(int id,boolean stack){this.id=id;this.stack=stack;}
  }
  static class L1ItemInstance {
    final L1Item item; int id,count,enchant,bless,charge,remaining,attr,special,blessLevel,blessLevel2,level;
    boolean equipped,pack,identified;String expiry,last,hotel;
    L1ItemInstance(L1Item item,int count){this.item=item;this.count=count;}
    int getId(){return id;}void setId(int a){id=a;}
    int getItemId(){return item.id;}int getCount(){return count;}void setCount(int a){count=a;}
    L1Item getItem(){return item;}boolean isStackable(){return item.stack;}
    boolean isEquipped(){return equipped;}void setEquipped(boolean b){equipped=b;}
    int getEnchantLevel(){return enchant;}int getBless(){return bless;}void setBless(int n){bless=n;}
    int getAttrEnchantLevel(){return attr;}int getSpecialEnchant(){return special;}
    int getChargeCount(){return charge;}int getRemainingTime(){return remaining;}
    int get_bless_level(){return blessLevel;}int get_bless_level2(){return blessLevel2;}
    int get_item_level(){return level;}boolean isPackage(){return pack;}
    String getEndTime(){return expiry;}String getLastUsed(){return last;}String getHotel_Town(){return hotel;}
  }
  static class ItemSnapshot { static ItemSnapshot capture(L1ItemInstance a,int owner){return new ItemSnapshot();} }
  static class Warehouse {
    static L1ItemInstance transferCopy(L1ItemInstance i,int id,int count,int charge){
        L1ItemInstance b=copy(i);b.setId(id);b.setCount(count);return b;
    }
  }
  static class L1World {static final L1World w=new L1World();static L1World getInstance(){return w;}Object findObject(int id){return null;}}
  static class IdFactory {static final IdFactory f=new IdFactory();int last=10000;
    static IdFactory getInstance(){return f;}int nextId(){return ++last;}}
  static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);asserts++;}
  %ENTRY%
  static abstract class Base {
    final L1PcInstance primary;
    Base(L1PcInstance p){primary=p;}
    void open(){}
    void owns(L1PcInstance p){if(p==null)throw new AssertionError("owner");}
    static void initializeNewGrant(L1ItemInstance item){}
    static L1ItemInstance copy(L1ItemInstance i){return TestEntryMemory.copy(i);}
    %SUPPORT%
    abstract Entry lookup(int row);
    abstract int size();
    abstract Entry at(int ix);
    String signature(){StringJoiner sj=new StringJoiner("|");for(int k=0;k<size();k++){
       Entry e=at(k);
       sj.add(e.rowId+":"+e.image.id+":"+e.image.item.id+":"+e.image.count+":"+e.owner.id+":"+(e.original==null?0:e.original.id));
    }return sj.toString();}
  }
  static final class Legacy extends Base {
    Legacy(L1PcInstance p){super(p);}
    %OLD_STORE%
    @Override Entry lookup(int row){return entries.get(row);}
    @Override int size(){return entries.size();}
    @Override Entry at(int i){int j=0;for(Entry e:entries.values())if(j++==i)return e;throw new AssertionError();}
    %OLD_METHODS%
  }
  static final class Optimized extends Base {
    Optimized(L1PcInstance p){super(p);}
    %NEW_STORE%
    @Override Entry lookup(int row){return findEntry(row);}
    @Override int size(){return entryCount;}
    @Override Entry at(int i){return entryAt(i);}
    %NEW_METHODS%
  }
  static class Scenario {
    final Base tx;
    final L1PcInstance[] pcs;
    Scenario(boolean optimized,int users,int items,boolean stack){
      pcs=new L1PcInstance[users];for(int i=0;i<users;i++)pcs[i]=new L1PcInstance(i+1);
      tx=optimized?new Optimized(pcs[0]):new Legacy(pcs[0]);
      for(int i=0;i<items;i++){
        var pc=pcs[i%users];
        L1ItemInstance item=new L1ItemInstance(new L1Item(2000+i,stack),3+(i%4));
        item.id=100+i;pc.inventory.list.add(item);
      }
    }
    L1ItemInstance find(int id){for(var p:pcs){var i=p.inventory.getItem(id);if(i!=null)return i;}return null;}
  }
  static void compare(int userCount,int itemCount,long seed) {
    Scenario old=new Scenario(false,userCount,itemCount,true);
    Scenario now=new Scenario(true,userCount,itemCount,true);
    Random r=new Random(seed);
    for(int ix=0;ix<itemCount;ix++){
      int index=r.nextInt(itemCount);
      int obj=100+index;
      var a=old.find(obj); var b=now.find(obj);
      var pa=old.pcs[index%userCount];var pb=now.pcs[index%userCount];
      // Duplicate requests and template-item debits exercise indexed and non-indexed lookups.
      boolean first= ((Legacy)old.tx).debit(pa,a,1);
      boolean second=((Optimized)now.tx).debit(pb,b,1);
      check(first==second,"debit success: "+itemCount+" "+ix);
      check(old.tx.signature().equals(now.tx.signature()),"insertion/signature "+itemCount+" "+ix);
      check(now.tx.lookup(obj)!=null,"lookup "+obj);
      if (now.tx instanceof Optimized opt && opt.entryCount==1)
        check(opt.entries instanceof Entry,"singleton allocated array");
      if(now.tx instanceof Optimized n){
        if(n.entryCount<9)check(n.entryIndex==null,"large index allocated early");
        else check(n.entryIndex!=null,"large index not allocated");
      }
    }
    // Exercise template-ID debits after previous prepared debits (including zero-count entries).
    for(int step=0;step<Math.min(8,itemCount);step++){
      int itemIndex=r.nextInt(itemCount);
      int templateId=2000+itemIndex;
      var pa=old.pcs[itemIndex%userCount];var pb=now.pcs[itemIndex%userCount];
      boolean first=((Legacy)old.tx).debit(pa,templateId,1);
      boolean second=((Optimized)now.tx).debit(pb,templateId,1);
      check(first==second,"template debit success");
      check(old.tx.signature().equals(now.tx.signature()),"template debit rows");
    }
    // Detached grant input, both merge-to-existing and new-row paths, on every party size.
    for(int g=0;g<4;g++){
      int templateId=g%2==0?2000+r.nextInt(itemCount):4000+g;
      int ownerIndex=r.nextInt(userCount);
      var pa=old.pcs[ownerIndex];var pb=now.pcs[ownerIndex];
      var oldImage=new L1ItemInstance(new L1Item(templateId,true),1);
      var newImage=new L1ItemInstance(new L1Item(templateId,true),1);
      IdFactory.getInstance().last=30000+g*500;
      var oldGrant=((Legacy)old.tx).grantCopy(pa,oldImage,2);int idAfterOld=IdFactory.getInstance().last;
      IdFactory.getInstance().last=30000+g*500;
      var newGrant=((Optimized)now.tx).grantCopy(pb,newImage,2);
      check(idAfterOld==IdFactory.getInstance().last,"grant ID sequence after debits");
      check((oldGrant==null)==(newGrant==null),"grant validity after debits");
      check(oldGrant.count==newGrant.count && oldGrant.id==newGrant.id,"grant data after debits");
      check(old.tx.signature().equals(now.tx.signature()),"grant insertion order after debits");
      check(oldImage.count==1 && newImage.count==1,"external grant aliasing");
    }
    // Compare identical row-lock ordering including multi-owner transactions.
    ArrayList<Integer> sorted=new ArrayList<>();
    for(int i=0;i<old.tx.size();i++) sorted.add(old.tx.at(i).image.id);
    Collections.sort(sorted);
    Entry[] actual=new Entry[now.tx.size()];for(int i=0;i<actual.length;i++)actual[i]=now.tx.at(i);
    Arrays.sort(actual,(a,b)->Integer.compare(a.image.id,b.image.id));
    for(int i=0;i<actual.length;i++)check(actual[i].image.id==sorted.get(i),"sort order");
    check(now.tx.size()==old.tx.size(),"entry count");
    if (now.tx instanceof Optimized opt) {
      check(opt.entryCount!=1 || opt.entries instanceof Entry,"singleton array allocation");
      if (opt.entryCount>1) check(opt.entries instanceof Entry[],"stale singleton reference");
    }
    for(int i=0;i<itemCount;i++) check(now.tx.lookup(100+i)==old.tx.lookup(100+i) /* different objects */?false:
      (now.tx.lookup(100+i)!=null)==(old.tx.lookup(100+i)!=null),"key membership");
  }
  static void grants(int count,boolean stack){
    Scenario a=new Scenario(false,1,0,stack),b=new Scenario(true,1,0,stack);
    L1Item tpl=new L1Item(9010,stack);
    L1ItemInstance one=new L1ItemInstance(tpl,count),two=new L1ItemInstance(tpl,count);
    one.id=one.count*17+1;two.id=one.id;
    IdFactory.getInstance().last=10000;
    copies=0;L1ItemInstance outA=((Legacy)a.tx).grant(one);int oldCopies=copies,oldId=IdFactory.getInstance().last;
    IdFactory.getInstance().last=10000;
    copies=0;L1ItemInstance outB=((Optimized)b.tx).grant(two);int newCopies=copies;
    check(oldId==IdFactory.getInstance().last,"grant ID sequencing");
    check(outA.count==outB.count&&outA.id==outB.id,"grant result");
    check(a.tx.signature().equals(b.tx.signature()),"grant rows "+count+","+stack);
    check(newCopies<=oldCopies,"more grant copies");
    check(oldCopies-newCopies==(stack||count==1?1:0),"grant copy count="+count+" stack="+stack+" old="+oldCopies+" new="+newCopies);
    check(one.count==count&&two.count==count,"external grant mutated");
  }
  static void transfers(int count,boolean stack){
    Scenario a=new Scenario(false,2,0,stack),b=new Scenario(true,2,0,stack);
    var tpl=new L1Item(9002,stack);
    var v1=new L1ItemInstance(tpl,5);v1.id=198;
    var v2=new L1ItemInstance(tpl,5);v2.id=198;
    a.pcs[0].inventory.list.add(v1);b.pcs[0].inventory.list.add(v2);
    IdFactory.getInstance().last=10000;
    copies=0;var outA=((Legacy)a.tx).transfer(a.pcs[0],v1,count,a.pcs[1]);int oldCopies=copies,oldId=IdFactory.getInstance().last;
    IdFactory.getInstance().last=10000;
    copies=0;var outB=((Optimized)b.tx).transfer(b.pcs[0],v2,count,b.pcs[1]);int newCopies=copies;
    check(oldId==IdFactory.getInstance().last,"transfer ID sequencing");
    check(outA.count==outB.count&&outA.id==outB.id,"transfer result");
    check(a.tx.signature().equals(b.tx.signature()),"transfer rows "+count+","+stack);
    if(count<5)check(oldCopies-newCopies==1,"partial transfer copy remains");
    else check(oldCopies==newCopies,"full transfer copy mismatch");
  }
  static void runAll(){
    // Test edge cases around primitive-index promotion: 0..8..9..hundreds.
    for(int users=1;users<=6;users++)for(int count:new int[]{1,2,3,4,7,8,9,10,12,16,32,80,144}){
      for(int pass=0;pass<4;pass++)compare(users,count,0x11aaaaL+pass*997+users*571L+count*37L);
    }
    for(boolean stack:new boolean[]{true,false})for(int count=1;count<=9;count++)grants(count,stack);
    for(boolean stack:new boolean[]{true,false})for(int count=1;count<=5;count++)if(stack||count==1)transfers(count,stack);
    System.out.println("PASS "+asserts+" entry/lookup/grant/transfer/order conditions");
  }
  public static void main(String[] a){runAll();}
}
'''
java=(java.replace('%ENTRY%',common_entry)
 .replace('%SUPPORT%',support)
 .replace('%OLD_STORE%',old_store)
 .replace('%NEW_STORE%',new_store)
 .replace('%OLD_METHODS%',old_methods)
 .replace('%NEW_METHODS%',new_methods))
# 'new' has extra static method grantCopyPrepared using primary, includes helper; old also.
java=java.replace('now.tx.lookup(100+i)==old.tx.lookup(100+i) /* different objects */?false:\n      (now.tx.lookup(100+i)!=null)==(old.tx.lookup(100+i)!=null)', '(now.tx.lookup(100+i)!=null)==(old.tx.lookup(100+i)!=null)')
trove=r'''package gnu.trove.map.hash;
import java.util.HashMap;
public final class TIntObjectHashMap<T> {
  private final HashMap<Integer,T> map;
  public TIntObjectHashMap(int cap){map=new HashMap<>(cap);}
  public T get(int key){return map.get(key);}
  public T put(int key,T item){return map.put(key,item);}
}'''
with tempfile.TemporaryDirectory(prefix='r_item_entry_') as d:
    d=Path(d)
    (d/'TestEntryMemory.java').write_text(java,encoding='utf-8')
    (d/'gnu/trove/map/hash').mkdir(parents=True)
    (d/'gnu/trove/map/hash/TIntObjectHashMap.java').write_text(trove,encoding='utf-8')
    r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(d),str(d/'TestEntryMemory.java'),str(d/'gnu/trove/map/hash/TIntObjectHashMap.java')],capture_output=True,text=True)
    if r.returncode:
        print(r.stderr)
        (root/'bad_harness.java').write_text(java,encoding='utf-8')
        raise SystemExit(r.returncode)
    r=subprocess.run(['java','-ea','-Xmx512m','-cp',str(d),'TestEntryMemory'],capture_output=True,text=True)
    print(r.stdout,r.stderr)
    if r.returncode:
        (root/'bad_harness.java').write_text(java,encoding='utf-8')
        raise SystemExit(r.returncode)
