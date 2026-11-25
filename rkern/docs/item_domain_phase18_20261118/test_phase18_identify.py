#!/usr/bin/env python3
from pathlib import Path
import shutil,subprocess,hashlib,tempfile
D=Path(__file__).resolve().parent
OLD=D/'baseline/IdentificationAndScrollItems.java'
NEW=D.parents[1]/'src/l1j/server/server/model/item/function/IdentificationAndScrollItems.java'
ROOT=Path(tempfile.mkdtemp(prefix='rkern_phase18_identify_'))

def make(root,source):
 p=root/'src/l1j/server/server/model/item/function/IdentifyTest.java';p.parent.mkdir(parents=True,exist_ok=True)
 data=source.read_text('utf8');start=data.index('    static ItemActionResult handleIdentifyItem(');end=data.index('    static ItemActionResult handleWriteBlankScroll(',start)
 method=data[start:end].rstrip()
 body='''package l1j.server.server.model.item.function;
public final class IdentifyTest {
 static final class CUserInventory { static final int COL_IS_ID=12; }
 enum ItemActionResult { COMPLETE, STOP }
 static final class Item {final int type,safe,mpr,hpr;Item(int t,int s,int m,int h){type=t;safe=s;mpr=m;hpr=h;}int get_addmpr(){return mpr;}int get_addhpr(){return hpr;}int get_safeenchant(){return safe;}int getType2(){return type;}}
 static final class L1ItemInstance {
  final Item data;boolean identified;L1ItemInstance(Item d,boolean id){data=d;identified=id;}Item getItem(){return data;}boolean isIdentified(){return identified;}void setIdentified(boolean v){identified=v;}
 }
 static final class L1PcInstance {
  final StringBuilder out=new StringBuilder();final Inventory inventory=new Inventory(this);Inventory getInventory(){return inventory;}
 }
 static final class Inventory {final L1PcInstance pc;Inventory(L1PcInstance pc){this.pc=pc;}void updateItem(L1ItemInstance i,int f){pc.out.append("UPDATE;");}void removeItem(L1ItemInstance i,int count){pc.out.append("REMOVE:").append(count).append(';');}}
 static final class ItemActionContext {final L1PcInstance pc;final L1ItemInstance input,target;ItemActionContext(L1PcInstance pc,L1ItemInstance i,L1ItemInstance t){this.pc=pc;input=i;target=t;}L1PcInstance player(){return pc;}L1ItemInstance item(){return input;}L1ItemInstance targetItem(){return target;}}
 static final class SC_IdentifyDesc {static void send(L1PcInstance pc,L1ItemInstance item){pc.out.append("DESC;");}}
 static final class SC_SystemMessage {static void send(L1PcInstance pc,String msg){pc.out.append("MSG:").append(msg).append(';');}}
'''+method+'''
 public static void main(String[] args){int n=0;
  for(int typ=0;typ<4;typ++) for(int ench:new int[]{-1,0,1,7}) for(int hpr:new int[]{-2,0,10}) for(int mpr:new int[]{-2,0,10})for(int identified=0;identified<2;identified++) {
    L1PcInstance pc=new L1PcInstance();L1ItemInstance input=new L1ItemInstance(new Item(0,0,0,0),true);
    L1ItemInstance target=new L1ItemInstance(new Item(typ,ench,mpr,hpr),identified!=0);
    ItemActionResult result=handleIdentifyItem(new ItemActionContext(pc,input,target));
    System.out.println(typ+":"+ench+":"+hpr+":"+mpr+":"+identified+":"+result+":"+target.identified+":"+pc.out);n++;
  }
  System.err.println("CASES="+n);
 }
}
'''
 p.write_text(body,'utf8')
 classes=root/'classes';classes.mkdir(parents=True,exist_ok=True)
 proc=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),str(p)],capture_output=True,text=True)
 assert proc.returncode==0,proc.stderr
 proc=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.IdentifyTest'],capture_output=True)
 assert proc.returncode==0,proc.stderr
 print(root.name,proc.stderr.decode().strip())
 return proc.stdout
old=make(ROOT/'identify_old',OLD)
new=make(ROOT/'identify_new',NEW)
assert old==new,'identification scroll action changed'
print('PASS IDENTIFY TYPED JAVA21 EQUALITY 288 SCENARIOS SHA256',hashlib.sha256(new).hexdigest())
