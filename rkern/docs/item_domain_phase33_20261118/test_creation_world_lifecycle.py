from pathlib import Path
import subprocess, shutil, re
w=Path(__file__).resolve().parents[2]; test=Path(__file__).resolve().parent
sourcebase=test/'baseline'
source=w/'src/l1j/server/server/model/item/function'
root=test/'typed';shutil.rmtree(root,ignore_errors=True)
root.mkdir()
pkg='l1j.server.server.model.item.function'

# Extract exact legacy methods (not a reimplementation).
def method(src,signature):
    pos=src.index(signature)
    start=src.rfind('\n',0,pos)+1
    op=src.index('{',pos)
    depth=1; i=op+1
    while depth:
        if src[i]=='{': depth+=1
        elif src[i]=='}': depth-=1
        i+=1
    return src[start:i]

def write(rel,s):
    p=root/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,'utf-8')

base= (sourcebase/'ItemCreationItems.java').read_text()
methods=[method(base,s) for s in ('public static boolean createNewItem(', 'static boolean createGMItem(', 'static boolean createNewItem2(')]
write('l1j/server/server/model/item/function/ItemCreationBaseline.java',f'''package {pkg};
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
final class ItemCreationBaseline {{\n{chr(10).join(methods)}\n}}''')
base=(sourcebase/'SealedEquipmentRewards.java').read_text()
method_txt=method(base,'static boolean UseSealedItem(')
write('l1j/server/server/model/item/function/SealedEquipmentBaseline.java',f'''package {pkg};
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
final class SealedEquipmentBaseline {{\n{method_txt}\n}}''')
# minimal typed fakes for compile and assertions
write('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
public final class L1ItemInstance {
    private final int id; private final int template;
    private int count=1, enchant, bless, attr; private boolean identified=true;
    public L1ItemInstance(int id,int template) { this.id=id;this.template=template; }
    public int getId() {return id;} public int getItemId() {return template;}
    public int getCount() {return count;} public void setCount(int v){count=v;}
    public void setEnchantLevel(int v){enchant=v;} public int getEnchantLevel(){return enchant;}
    public void setAttrEnchantLevel(int v){attr=v;} public int getAttrEnchantLevel(){return attr;}
    public void setIdentified(boolean v){identified=v;} public boolean isIdentified(){return identified;}
    public void setBless(int v){bless=v;} public int getBless(){return bless;}
    public String getLogName(){return "#"+template+" x"+count+" e"+enchant+" b"+bless;}
}''')
write('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
import l1j.server.server.model.L1PcInventory;
public class L1PcInstance {
 private final L1PcInventory inventory=new L1PcInventory();
 public L1PcInventory getInventory(){return inventory;}
}''')
write('l1j/server/server/model/L1Inventory.java','''package l1j.server.server.model;
public class L1Inventory {public static final int OK=0;}''')
write('l1j/server/server/model/L1PcInventory.java','''package l1j.server.server.model;
import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.Instance.L1ItemInstance;
public class L1PcInventory {
 public static final int COL_BLESS=1;
 public int reject=0;public boolean failCheck=false,failStore=false,returnNull=false,stack=false;
 public int saveCalls,updateCalls; public int checkCalls,storeCalls;
 public final Map<Integer,L1ItemInstance> entries=new HashMap<>();
 public int checkAddItem(L1ItemInstance item,int count){checkCalls++;if(failCheck)throw new IllegalStateException("check fail");return reject;}
 public L1ItemInstance storeItem(L1ItemInstance item){storeCalls++;if(failStore)throw new IllegalStateException("store fail");if(returnNull)return null;
   if(stack){for(L1ItemInstance x:entries.values())if(x.getItemId()==item.getItemId()){x.setCount(x.getCount()+item.getCount());return x;}}
   entries.put(item.getId(),item);return item;}
 public L1ItemInstance getItem(int obj){return entries.get(obj);}
 public void updateItem(L1ItemInstance item,int mask){updateCalls++;}
 public void saveItem(L1ItemInstance item,int mask){saveCalls++;}
}''')
write('l1j/server/server/model/L1World.java','''package l1j.server.server.model;
import java.util.HashMap;import java.util.Map;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class L1World {
 private static final L1World INSTANCE=new L1World();
 private final Map<Integer,L1ItemInstance> entries=new HashMap<>();
 public static L1World getInstance(){return INSTANCE;}
 public void storeObject(L1ItemInstance item){entries.put(item.getId(),item);}
 public void removeObject(L1ItemInstance item){if(entries.get(item.getId())==item)entries.remove(item.getId());}
 public int size(){return entries.size();}
 public L1ItemInstance find(int id){return entries.get(id);}
 public void clear(){entries.clear();}
}''')
write('l1j/server/server/datatables/ItemTable.java','''package l1j.server.server.datatables;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1World;
public class ItemTable {
 private static final ItemTable INSTANCE=new ItemTable();private int next=100;
 public static ItemTable getInstance(){return INSTANCE;}
 public L1ItemInstance createItem(int template){if(template<1)return null;
  L1ItemInstance v=new L1ItemInstance(next++,template);L1World.getInstance().storeObject(v);return v;}
}''')
write('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''package l1j.server.server.model.network.core.sc.packet;
import java.util.ArrayList;import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ServerMessage {
 public static final List<String> events=new ArrayList<>();
 public static void send(L1PcInstance pc,int code){events.add("M"+code);}
 public static void send(L1PcInstance pc,int code,String message){events.add("M"+code+":"+message);}
}''')
write('l1j/server/server/model/item/function/TestPhase33.java','''package l1j.server.server.model.item.function;
import java.util.List;import java.util.ArrayList;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
public class TestPhase33 {
 static long checks;
 static void check(boolean pass,String detail){checks++;if(!pass)throw new AssertionError(detail);}
 interface Call {boolean invoke(L1PcInstance pc);}
 static void runCase(String mode,int template, int quantity, int enchant,int expectedEnchant, boolean identified,
    Call baseline,Call improved) {
  String[] variants={"fresh","merged","reject","throwCheck","throwStore"};
  for(String v:variants){
   List<String> outputs=new ArrayList<>();
   for(int old=0;old<2;old++){
    L1World.getInstance().clear();SC_ServerMessage.events.clear();
    L1PcInstance pc=new L1PcInstance();
    var inv=pc.getInventory();
    if(v.equals("merged")){
     inv.stack=true;L1ItemInstance existing=new L1ItemInstance(3,template);
     existing.setCount(7);inv.entries.put(3,existing);L1World.getInstance().storeObject(existing);
    }
    if(v.equals("reject"))inv.reject=1;
    if(v.equals("throwCheck"))inv.failCheck=true;
    if(v.equals("throwStore"))inv.failStore=true;
    boolean result=false;String error="";
    try {result=(old==0?baseline:improved).invoke(pc);}catch(Exception x){error=x.getMessage();}
    check(inv.checkCalls==1,mode+":"+v+" check calls");
    int expectedWorld=(v.equals("fresh")?1:v.equals("merged")?1:0);
    if(old==1)check(L1World.getInstance().size()==expectedWorld,mode+":"+v+" orphan world object");
    else if(!v.equals("fresh"))check(L1World.getInstance().size()>expectedWorld,mode+":"+v+" legacy precondition");
    if(v.equals("fresh")){
      check(inv.entries.size()==1,mode+" expected insert");
      var item=inv.entries.values().iterator().next();
      check(item.getEnchantLevel()==expectedEnchant,mode+" enchant");
      check(item.isIdentified()==identified,mode+" identified");
      check(item.getCount()==quantity,mode+" count");
      if(mode.equals("sealed")){check(item.getBless()==7,"sealed bless");check(inv.saveCalls==1 && inv.updateCalls==1,"sealed bless persisted");}
    }
    if(v.equals("merged")){check(inv.entries.size()==1,"merged item count");check(inv.entries.get(3).getCount()==7+quantity,"merged count");}
    outputs.add(result+"|"+error+"|"+SC_ServerMessage.events+"|"+inv.entries.size()+"|"+inv.storeCalls+"|"+inv.updateCalls+"|"+inv.saveCalls);
   }
   check(outputs.get(0).equals(outputs.get(1)),mode+":"+v+" behavior changed: "+outputs);
  }
 }
 public static void main(String[] args){
  for(int template:new int[]{5,100,10005})for(int qty:new int[]{1,2,50}){
    runCase("normal",template,qty,0,0,true,
       pc->ItemCreationBaseline.createNewItem(pc,template,qty),
       pc->ItemCreationItems.createNewItem(pc,template,qty));
    runCase("GM",template,qty,0,0,true,
       pc->ItemCreationBaseline.createGMItem(pc,template,qty),
       pc->ItemCreationItems.createGMItem(pc,template,qty));
    for(int enchant:new int[]{-1,0,5,9})
      runCase("enchant",template,qty,enchant,enchant,true,
       pc->ItemCreationBaseline.createNewItem2(pc,template,qty,enchant),
       pc->ItemCreationItems.createNewItem2(pc,template,qty,enchant));
    runCase("sealed",template,qty,4,4,true,
       pc->SealedEquipmentBaseline.UseSealedItem(pc,template,qty,4,7,2,false),
       pc->SealedEquipmentRewards.UseSealedItem(pc,template,qty,4,7,2,false));
  }
  L1PcInstance pc=new L1PcInstance();
  check(!ItemCreationItems.createNewItem(pc,1,0),"zero amount must not send successful grant");
  check(!ItemCreationItems.createNewItem(pc,-1,1),"invalid template must fail");
  System.out.println("Phase33 typed production + legacy execution comparison passed: "+checks+" checks");
 }
}''')
sourcefiles=[source/'ItemCreationItems.java',source/'SealedEquipmentRewards.java']
javafiles=[str(p) for p in root.rglob('*.java')]+[str(p) for p in sourcefiles]
output=test/'typed_classes';shutil.rmtree(output,ignore_errors=True);output.mkdir()
c=subprocess.run(['javac','-encoding','UTF-8','-source','21','-target','21','-d',str(output)]+javafiles,capture_output=True,text=True)
print('javac',c.returncode,c.stderr[:8000]);
if c.returncode:raise SystemExit(1)
c=subprocess.run(['java','-cp',str(output),pkg+'.TestPhase33'],capture_output=True,text=True)
print('java',c.returncode,c.stdout,c.stderr[:8000]);
if c.returncode:raise SystemExit(1)
