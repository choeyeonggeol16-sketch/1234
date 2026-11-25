#!/usr/bin/env python3
"""Typed mock of actual grant/grantCopy entry points, compared with 175 methods."""
from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parents[2]
new=(root/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text('utf-8')
old=(Path(__file__).parent/'original_L1InventoryTransaction.java').read_text('utf-8')
def extract(s,sig):
    st=s.index(sig); i=s.index('{',st); nesting=1; i+=1
    while nesting:
        if s[i]=='{':nesting+=1
        elif s[i]=='}':nesting-=1
        i+=1
    return s[st:i]
newmethods='\n'.join([
 extract(new,'    public L1ItemInstance grant(L1PcInstance pc,int template,int count)'),
 extract(new,'    public L1ItemInstance grantCopy(L1PcInstance pc,L1ItemInstance image,int count)'),
 extract(new,'    private L1ItemInstance grantCopyPrepared(')
])
oldmethods='\n'.join([
 extract(old,'    public L1ItemInstance grant(L1PcInstance pc,int template,int count)'),
 extract(old,'    public L1ItemInstance grantCopy(L1PcInstance pc,L1ItemInstance image,int count)')
])

fixture=r'''import java.util.*;
public class TestGrant {
 static class IdFactory {static final IdFactory factory=new IdFactory();int index=5000;
    static IdFactory getInstance(){return factory;} int nextId(){return ++index;} }
 static class ItemTable {static final ItemTable it=new ItemTable();static ItemTable getInstance(){return it;}
    L1Item getTemplate(int id){return id<=0?null:new L1Item(id,id%2==1);}}
 static class CInventory {static final int MAX_AMOUNT=100000;}
 static class CUserInventory {static final int MAX_SIZE=30;final ArrayList<L1ItemInstance> items=new ArrayList<>(); Collection<L1ItemInstance> getItems(){return items;}}
 static class L1PcInstance {final CUserInventory inv=new CUserInventory();CUserInventory getInventory(){return inv;}}
 static class L1Item {final int id;final boolean stack;L1Item(int id,boolean stack){this.id=id;this.stack=stack;}int getBless(){return 0;}}
 static class L1ItemInstance {
  final L1Item item; int id,count,bless;boolean identified,equipped;int x;
  L1ItemInstance(L1Item a,int n){item=a;count=n;}
  int getId(){return id;}void setId(int n){id=n;}
  int getItemId(){return item.id;}int getCount(){return count;}void setCount(int n){count=n;}
  int getBless(){return bless;}void setBless(int n){bless=n;}
  boolean isStackable(){return item.stack;}boolean isEquipped(){return equipped;}void setEquipped(boolean b){equipped=b;}
  int get_bless_level(){return 0;} void setIdentified(boolean b){identified=b;}
 }
 static class Entry {L1PcInstance owner;L1ItemInstance image;Entry(L1PcInstance p,L1ItemInstance item,boolean existing){owner=p;image=item;}}
 static abstract class Base {
  final LinkedHashMap<Integer,Entry> entries=new LinkedHashMap<>();int copies;
  void open(){} void owns(L1PcInstance p){}
  Entry entry(L1PcInstance pc,L1ItemInstance item){Entry e=entries.get(item.getId());if(e!=null)return e;
    e=new Entry(pc,copy(item),true);entries.put(item.getId(),e);return e;}
  L1ItemInstance copy(L1ItemInstance item){copies++;L1ItemInstance x=new L1ItemInstance(item.item,item.count);x.id=item.id;x.bless=item.bless;x.equipped=item.equipped;x.identified=item.identified;return x;}
  void initializeNewGrant(L1ItemInstance x){x.x=42;}
  boolean mergeable(L1ItemInstance a,L1ItemInstance b){return a.isStackable()&&b.isStackable()&&a.getItemId()==b.getItemId()&&a.getBless()==b.getBless();}
 }
 static class Original extends Base {%OLD%}
 static class Optimized extends Base {%NEW%}
 static int checks;
 static void chk(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;}
 static String signature(Base c){
  ArrayList<String> rows=new ArrayList<>();
  for(var e:c.entries.values()){var im=e.image;rows.add(im.id+":"+im.getItemId()+":"+im.count+":"+im.bless+":"+im.equipped);}
  return rows.toString();
 }
 static void run(boolean external,int template,int count,boolean merge,boolean duplicateId){
  Original o=new Original(); Optimized n=new Optimized();
  L1PcInstance p=new L1PcInstance(); L1PcInstance q=new L1PcInstance();
  var tpl=ItemTable.getInstance().getTemplate(template);
  if(merge){
    var row=new L1ItemInstance(tpl,2);row.id=900;
    var row2=new L1ItemInstance(tpl,2);row2.id=900;
    o.entries.put(900,new Entry(p,row,true));n.entries.put(900,new Entry(q,row2,true));
  }
  IdFactory.factory.index=5000;
  L1ItemInstance first;
  L1ItemInstance request1=new L1ItemInstance(tpl,1);
  request1.id=duplicateId?900:0;
  if(external)first=o.grantCopy(p,request1,count);else first=o.grant(p,template,count);
  int oldNext=IdFactory.factory.index;
  IdFactory.factory.index=5000;
  L1ItemInstance second;
  L1ItemInstance request2=new L1ItemInstance(tpl,1);
  request2.id=duplicateId?900:0;
  if(external)second=n.grantCopy(q,request2,count);else second=n.grant(q,template,count);
  chk((first==null)==(second==null),"return validity");
  if(first!=null){chk(first.getItemId()==second.getItemId(),"result template");chk(first.getCount()==second.getCount(),"result quantity");}
  chk(oldNext==IdFactory.factory.index,"ID sequencing");
  chk(signature(o).equals(signature(n)),"prepared rows");
  if(external){chk(request1.count==1 && request2.count==1,"external input count modified");chk(request1.id==request2.id,"external input ID modified");}
  chk(n.copies<=o.copies,"optimized allocated more copies");
  if(count==1&&!external&&!merge)chk(n.copies==o.copies-1,"fresh single grant clone not eliminated");
  if(!tpl.stack&&count>1)chk(n.copies==o.copies-count,"per-item double-copy not removed");
 }
 public static void main(String[] arg){
  for(int t=1;t<=40;t++)for(int count=1;count<=6;count++)for(boolean external:new boolean[]{false,true})for(boolean merge:new boolean[]{false,true}){
    run(external,t,count,merge,false);
    run(external,t,count,merge,true);
  }
  System.out.println("PASS " + checks + " grant-equivalence assertions");
 }
}
'''
fixture=fixture.replace('%OLD%',oldmethods).replace('%NEW%',newmethods)
with tempfile.TemporaryDirectory(prefix='r_item_grant_') as d:
    a=Path(d)/'TestGrant.java'
    a.write_text(fixture,'utf-8')
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',d,str(a)],check=True)
    subprocess.run(['java','-Xmx256m','-cp',d,'TestGrant'],check=True)
