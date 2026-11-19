#!/usr/bin/env python3
"""Compile the extracted production capacity functions against typed Java stubs
and compare against the unmodified 175 baseline across valid inventories.
"""
from pathlib import Path
import re, subprocess, tempfile

SRC=Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java'
OLD=Path(__file__).resolve().parent/'baseline_178_L1InventoryTransaction.java'

def method(s, sig):
    start=s.index(sig)
    body=s.index('{',start)
    dep=1
    pos=body+1
    while dep:
        if s[pos]=='{': dep+=1
        if s[pos]=='}': dep-=1
        pos+=1
    return s[start:pos]

current=SRC.read_text('utf-8'); old=OLD.read_text('utf-8')
new_validate=method(current,'    private void validateCapacity()').replace('entries[j]', 'entryArray[j]')
old_validate=method(old,'    private void validateCapacity()').replace('private void validateCapacity()', 'private void validateCapacityOld()').replace('capacityCannotIncrease()', 'capacityCannotIncreaseOld()')
fast=method(current, '    private boolean capacityCannotIncrease()').replace('entries[index]', 'entryArray[index]').replace('capacityCannotIncrease()', 'capacityCannotIncreaseIndex()')
old_fast=method(old, '    private boolean capacityCannotIncrease()').replace('capacityCannotIncrease()', 'capacityCannotIncreaseOld()')
amount=method(current,'    private static void addProjectedAmount(')
weight=method(current,'    private static long weight(')

code=r'''import java.util.*;
import gnu.trove.map.hash.TIntLongHashMap;

public final class TestCapacity {
    static class L1Inventory { static final int MAX_AMOUNT = 160; }
    static class L1PcInventory { static final int MAX_SIZE = 40; ArrayList<L1ItemInstance> items = new ArrayList<>(); int visits;
       Collection<L1ItemInstance> getItems(){ visits++; return items; }
       L1ItemInstance getItem(int id) { for(var i:items)if(i.id==id)return i;return null; }
    }
    static class Config { static final double RATE_WEIGHT_LIMIT = 1; }
    static class Def { final int weight; Def(int w){weight=w;} int getWeight(){return weight;} }
    static class L1ItemInstance {
       final int id; int itemId,count; Def definition;
       L1ItemInstance(int id,int itemId,int count,int weight){this.id=id;this.itemId=itemId;this.count=count;definition=new Def(weight);}
       int getId(){return id;} int getCount(){return count;} int getItemId(){return itemId;}
       Def getItem(){return definition;}
       L1ItemInstance dup(){return new L1ItemInstance(id,itemId,count,definition.weight);}
    }
    static class L1PcInstance { final int id; final L1PcInventory inv = new L1PcInventory(); int maxWeight;
       L1PcInstance(int id,int maxWeight){this.id=id;this.maxWeight=maxWeight;}
       L1PcInventory getInventory(){return inv;} int getMaxWeight(){return maxWeight;}
    }
    static class Owners { final L1PcInstance[] pcs; Owners(L1PcInstance... a){pcs=a;}
        int size(){return pcs.length;} L1PcInstance get(int i){return pcs[i];} }
    static class ItemSnapshot { }
    static class Entry {
        L1PcInstance owner, originalOwner; L1ItemInstance original,image; ItemSnapshot before;
        Entry(L1PcInstance owner,L1ItemInstance original,L1ItemInstance image,boolean existing){
           this.owner=owner;this.originalOwner=existing?owner:null;this.original=original;this.image=image;
           this.before=existing?new ItemSnapshot():null;
        }
    }
    final Owners owners;
    final LinkedHashMap<Integer,Entry> entries=new LinkedHashMap<>();
    Entry[] entryArray; int entryCount;
    Entry findEntry(int id){return entries.get(id);}
    boolean capacityCannotIncrease(){entryArray=entries.values().toArray(new Entry[0]);entryCount=entryArray.length;return capacityCannotIncreaseIndex();}
    TestCapacity(Owners o){owners=o;}

    %METHODS%

    static int tests;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why); tests++;}
    static boolean oldAccept(TestCapacity c){try{c.validateCapacityOld();return true;}catch(IllegalStateException x){return false;}}
    static boolean newAccept(TestCapacity c){try{c.entryArray=c.entries.values().toArray(new Entry[0]);c.entryCount=c.entryArray.length;c.validateCapacity();return true;}catch(IllegalStateException x){return false;}}
    static boolean validBase(TestCapacity c){
      HashMap<Integer,Long> totals=new HashMap<>();
      for(var pc:c.owners.pcs){
        long w=0; for(var it:pc.inv.items){ totals.merge(it.itemId,(long)it.count,Long::sum); w+=weight(it); }
        if(pc.inv.items.size()>L1PcInventory.MAX_SIZE || w*100>=240L*pc.maxWeight)return false;
      }
      for (var v:totals.values()) if (v>L1Inventory.MAX_AMOUNT) return false;
      return true;
    }
    static void tests(){
      Random rand=new Random(20261118);
      for(int trial=0;trial<30000;trial++){
        int participants=1+rand.nextInt(3);
        L1PcInstance[] pcs=new L1PcInstance[participants];
        TestCapacity t=new TestCapacity(new Owners(pcs));
        int objectId=100;
        for(int i=0;i<participants;i++){
          pcs[i]=new L1PcInstance(i, 20+rand.nextInt(180));
          for(int j=0,n=rand.nextInt(15);j<n;j++){
            int template=1+rand.nextInt(10);
            pcs[i].inv.items.add(new L1ItemInstance(objectId++,template,1+rand.nextInt(12),rand.nextInt(1200)));
          }
        }
        t=new TestCapacity(new Owners(pcs));
        if(!validBase(t)) {trial--;continue;}
        // Touch zero or several live rows; optionally transfer, grant or increase.
        for(int k=0,n=rand.nextInt(6);k<n;k++) {
           var pc=pcs[rand.nextInt(participants)];
           if(!pc.inv.items.isEmpty() && rand.nextInt(5)!=0){
             var old=pc.inv.items.get(rand.nextInt(pc.inv.items.size()));
             if(t.entries.containsKey(old.id))continue;
             var newer=old.dup();
             int op=rand.nextInt(7);
             if(op<=2)newer.count=rand.nextInt(old.count+1); // decrease only
             if(op==3)newer.count=old.count+rand.nextInt(15);
             if(op==4)newer.itemId=11+rand.nextInt(4);
             if(op==5)newer.definition=new Def(newer.definition.weight+1+rand.nextInt(1000));
             var entry=new Entry(pc,old,newer,true);
             if(op==6 && participants>1)entry.owner=pcs[(pc.id+1)%participants];
             t.entries.put(old.id,entry);
           }else{
             var newer=new L1ItemInstance(objectId++,1+rand.nextInt(10),1+rand.nextInt(5),rand.nextInt(1000));
             t.entries.put(newer.id,new Entry(pc,null,newer,false));
           }
        }
        boolean a=oldAccept(t),b=newAccept(t);
        check(a==b,"Mismatch valid inventory: "+trial+" old="+a+" new="+b);
        // A pure debit requires no owner inventory traversal or per-template map.
        if(t.capacityCannotIncrease()) {
           int before=gnu.trove.map.hash.TIntLongHashMap.creations;
           for(var pc:pcs)pc.inv.visits=0;
           boolean ret=newAccept(t);
           check(ret,"Decrement-only transaction must preserve valid base");
           check(gnu.trove.map.hash.TIntLongHashMap.creations==before,"Fast path allocated a map");
           for(var pc:pcs)check(pc.inv.visits==0,"Fast path scanned inventory");
        }
      }
      // Transfers/new grants/added quantity/weight must take the full slow path.
      var pc=new L1PcInstance(1,100000);
      var other=new L1PcInstance(2,100000);
      var old=new L1ItemInstance(400,10,3,1000);
      pc.inv.items.add(old);
      var c=new TestCapacity(new Owners(pc,other));
      c.entries.put(400,new Entry(pc,old,old.dup(),true));
      check(c.capacityCannotIncrease(),"equal image is capacity neutral");
      c.entries.get(400).image.count=4;
      check(!c.capacityCannotIncrease(),"quantity increase must be projected");
      c.entries.get(400).image.count=3;
      c.entries.get(400).owner=other;
      check(!c.capacityCannotIncrease(),"transfer must be projected");
      c.entries.get(400).owner=pc;
      c.entries.get(400).image.definition=new Def(1001);
      check(!c.capacityCannotIncrease(),"weight increase must be projected");
      c.entries.get(400).image=old.dup();
      c.entries.get(400).image.itemId=11;
      check(!c.capacityCannotIncrease(),"template change must be projected");
      c.entries.clear();
      c.entries.put(500,new Entry(pc,null,new L1ItemInstance(500,10,1,1000),false));
      check(!c.capacityCannotIncrease(),"grant must be projected");
      // One-array replacement must match list sorting order, for any row ID set.
      for(int n=0;n<50;n++)for(int rep=0;rep<30;rep++){
         ArrayList<Integer> ids=new ArrayList<>();
         for(int i=0;i<n;i++)ids.add(i*3+1);
         Collections.shuffle(ids,rand);
         var map=new LinkedHashMap<Integer,Entry>();
         for(int id:ids){var image=new L1ItemInstance(id,1,1,100);map.put(id,new Entry(pc,null,image,false));}
         Entry[] sorted=map.isEmpty()?new Entry[0]:map.values().toArray(new Entry[map.size()]);
         if(sorted.length>1)Arrays.sort(sorted,(a,b)->Integer.compare(a.image.getId(),b.image.getId()));
         for(int i=0;i<n;i++)check(sorted[i].image.id==i*3+1,"row lock sort changed");
      }
      System.out.println("PASS " + tests + " invariant and order assertions");
    }
    public static void main(String[] a){ tests(); }
}
'''
methods='\n'.join([new_validate,old_validate,fast,old_fast,amount,weight])
code=code.replace('%METHODS%',methods)
trove=r'''package gnu.trove.map.hash;
import java.util.HashMap;
public final class TIntLongHashMap{
  static public int creations;
  private final HashMap<Integer,Long> values=new HashMap<>();
  public TIntLongHashMap(){ creations++; }
  public long adjustOrPutValue(int k,long adjust,long initial){
    long val=values.containsKey(k)?values.get(k)+adjust:initial;values.put(k,val);return val;
  }
}
'''
with tempfile.TemporaryDirectory(prefix='r_item_capacity_') as d:
    folder=Path(d)
    (folder/'TestCapacity.java').write_text(code,'utf-8')
    (folder/'gnu/trove/map/hash').mkdir(parents=True)
    (folder/'gnu/trove/map/hash/TIntLongHashMap.java').write_text(trove,'utf-8')
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',d,str(folder/'TestCapacity.java'),str(folder/'gnu/trove/map/hash/TIntLongHashMap.java')],check=True)
    subprocess.run(['java','-Xmx256m','-cp',d,'TestCapacity'],check=True)
