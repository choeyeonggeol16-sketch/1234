"""Compile actual restore/clear methods and packed table with isolated world/packet stubs."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
model = root / "src/l1j/server/server/model"
source = (model / "CUserInventory.java").read_text(encoding="utf-8")
a = source.index("    public synchronized void loadItems(List")
b = source.index("\t@Override\n\tpublic synchronized void insertItem", a)
methods = source[a:b]
table = (model / "CItemCountTable.java").read_text(encoding="utf-8")
table = table[table.index("final class CItemCountTable"):].replace("CInventory.MAX_AMOUNT", "CacheAudit.MAX_AMOUNT")
harness = r'''
import java.util.*;
import java.io.Serializable;
public class CacheAudit {
 static final int MAX_AMOUNT=2_000_000_000;
 static class L1PcInstance {boolean poly;int getId(){return 1;}void setPolyRingMaster(boolean b){poly=b;}}
 static class L1ItemInstance {int id,itemId,count; L1PcInstance _cha; L1ItemInstance(int id,int itemId,int count){this.id=id;this.itemId=itemId;this.count=count;}int getId(){return id;}int getItemId(){return itemId;}int getCount(){return count;}}
 static class Items extends ArrayList<L1ItemInstance>{void Push(L1ItemInstance i){if(contains(i))throw new IllegalStateException();add(i);}void Pop(L1ItemInstance i){remove(i);}}
 static class L1World{static final L1World W=new L1World();Map<Integer,L1ItemInstance> rows=new HashMap<>();int failId;static L1World getInstance(){return W;}void storeObject(L1ItemInstance i){rows.put(i.id,i);if(i.id==failId)throw new IllegalStateException("store");}void removeObject(L1ItemInstance i){rows.remove(i.id,i);}}
 static class SC_Ability{static boolean fail;static void send(L1PcInstance p,int n,boolean b){if(fail)throw new IllegalStateException("notice");}}
 static class CInventory{final Items _items=new Items();void clearItems(){for(var i:_items)L1World.W.removeObject(i);_items.clear();}L1ItemInstance GetByServerID(int id){for(var i:_items)if(i.id==id)return i;return null;}}
 static class CUserInventory extends CInventory {
 final L1PcInstance _owner=new L1PcInstance();final CItemCountTable _itemCountMap=new CItemCountTable(),_equippedCountMap=new CItemCountTable();int[] displayedAccessories;int displayedPrimaryWeapon,displayedSecondaryWeapon;
 static void receiptNoticeFailed(String n,RuntimeException e){}
'''
checks = r'''
 }
 static void check(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
 var rows=CItemCountTable.class.getDeclaredField("rows");rows.setAccessible(true);
 int tests=0;for(boolean prior:new boolean[]{false,true})for(boolean duplicate:new boolean[]{false,true})for(boolean failure:new boolean[]{false,true})for(boolean packet:new boolean[]{false,true})for(int qty:new int[]{0,1,MAX_AMOUNT})for(int type:new int[]{123,900075}){
  L1World.W.rows.clear();L1World.W.failId=0;SC_Ability.fail=packet;CUserInventory inv=new CUserInventory();if(prior)inv.loadItems(List.of(new L1ItemInstance(1,type,1)));int id=duplicate&&prior?1:2;var row=new L1ItemInstance(id,type,qty);var old=new L1PcInstance();row._cha=old;L1World.W.failId=failure?id:0;boolean ok=qty>0&&!(prior&&qty==MAX_AMOUNT)&&!(duplicate&&prior)&&!failure;boolean got=true;try{inv.loadItems(List.of(row));}catch(IllegalStateException e){got=false;}check(got==ok);check(inv._items.size()==(prior?1:0)+(ok?1:0));check(inv._itemCountMap.getOrDefault(type,0)==(prior?1:0)+(ok?qty:0));check(row._cha==(ok?inv._owner:old));check(L1World.W.rows.size()==inv._items.size());inv._equippedCountMap.put(type,1);inv.displayedAccessories=new int[9];inv.displayedPrimaryWeapon=4;inv.displayedSecondaryWeapon=5;inv.clearItems();check(inv._items.isEmpty()&&rows.get(inv._itemCountMap)==null&&rows.get(inv._equippedCountMap)==null&&inv.displayedAccessories==null&&inv.displayedPrimaryWeapon==0&&inv.displayedSecondaryWeapon==0&&L1World.W.rows.isEmpty());tests++;
 } System.out.println("PASS "+tests+" isolated restore / rollback / clear cases");}
}
'''
with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "CacheAudit.java"
    java.write_text(harness + methods + checks + "\n" + table, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", work, "CacheAudit"], check=True, timeout=30)
