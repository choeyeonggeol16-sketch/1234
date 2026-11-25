#!/usr/bin/env python3
from pathlib import Path
import tempfile, subprocess
ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'src/l1j/server/server/model/item/function'
D=Path(__file__).resolve().parent
BASE=D/'baseline'
P='l1j/server/server/model/item/function/'
I='l1j/server/server/model/Instance/'

def compile_test(files):
 with tempfile.TemporaryDirectory(prefix='rkern19_starter_') as d:
  p=Path(d)
  for key,value in files.items():
   out=p/key;out.parent.mkdir(parents=True,exist_ok=True);out.write_text(value,'utf-8')
  cmd=['javac','--release','21','-encoding','UTF-8','-d',d]+[str(x) for x in p.rglob('*.java')]
  x=subprocess.run(cmd,capture_output=True,text=True)
  if x.returncode: raise RuntimeError('java compilation:\n'+x.stdout+x.stderr)
  x=subprocess.run(['java','-cp',d,'l1j.server.server.model.item.function.TestStarter19'],capture_output=True,text=True)
  if x.returncode:raise RuntimeError('java test:\n'+x.stdout+x.stderr)
  print(x.stdout.strip())

files={P+'RecruitStarterKit.java':(F/'RecruitStarterKit.java').read_text(),P+'RecruitStarterItemGrant.java':(F/'RecruitStarterItemGrant.java').read_text()}
files[P+'RecruitStarterKitBaseline.java']=(BASE/'RecruitStarterKit.java.baseline').read_text('utf8').replace('class RecruitStarterKit {','class RecruitStarterKitBaseline {')
for name in ('Warrior','Knight','Dragonknight','Crown','Wizard','Blackwizard','Elf','Darkelf'):
 n='RecruitStarter'+name+'Kit.java'
 files[P+n]=(BASE/(n+'.baseline')).read_text('utf8')
files[I+'L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public class L1ItemInstance {
 int id,count,enchant,attr,bless;boolean identified;
 public L1ItemInstance(int v){id=v;}
 public void setCount(int v){count=v;}public void setEnchantLevel(int v){enchant=v;}
 public void setAttrEnchantLevel(int v){attr=v;}public void setBless(int v){bless=v;}
 public void setIdentified(boolean b){identified=b;}
 public String getLogName(){return "i"+id+":"+count+":"+enchant+":"+attr+":"+identified;}
}'''
files['l1j/server/server/model/CInventory.java']='package l1j.server.server.model;public class CInventory{public static final int OK=0;}'
files['l1j/server/server/model/CUserInventory.java']='package l1j.server.server.model;public class CUserInventory{public static final int COL_BLESS=1;}'
files['l1j/server/server/datatables/ItemTable.java']='''package l1j.server.server.datatables;
import l1j.server.server.model.Instance.L1ItemInstance;
public class ItemTable{public static ItemTable getInstance(){return new ItemTable();}public L1ItemInstance createItem(int id){return new L1ItemInstance(id);}}'''
files[I+'L1PcInstance.java']='''package l1j.server.server.model.Instance;
import l1j.server.server.model.CInventory;
public class L1PcInstance {
 public final StringBuilder events=new StringBuilder();
 public void log(String s){events.append(s).append(';');}
 public int clazz,capacity,consumed; public boolean hasItem=true;
 public Inventory inv = new Inventory();
 public boolean isWarrior(){return (clazz&1)!=0;} public boolean isKnight(){return (clazz&2)!=0;}
 public boolean isDragonknight(){return (clazz&4)!=0;}public boolean isCrown(){return (clazz&8)!=0;}
 public boolean isWizard(){return (clazz&16)!=0;}public boolean isBlackwizard(){return (clazz&32)!=0;}
 public boolean isElf(){return (clazz&64)!=0;}public boolean isDarkelf(){return (clazz&128)!=0;}
 public Inventory getInventory(){return inv;}
 public class Inventory{
  public boolean checkItem(int id,int n){log("CHECK:"+id+":"+n);return hasItem;}
  public void consumeItem(int id,int n){consumed+=n;log("CONSUME:"+id+":"+n);}
  public int checkAddItem(L1ItemInstance item,int n){log("ADD_CHECK:"+item.getLogName());return capacity==0?CInventory.OK:1;}
  public void storeItem(L1ItemInstance item){log("STORE:"+item.getLogName());}
  public void updateItem(L1ItemInstance item,int col){log("UPDATE:"+item.getLogName()+":"+col);}
  public void saveItem(L1ItemInstance item,int col){log("SAVE:"+item.getLogName()+":"+col);}
 }
}'''
files['l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_ServerMessage {public static void send(L1PcInstance p,int n){p.log("MSG:"+n);}public static void send(L1PcInstance p,int n,String msg){p.log("MSG:"+n+":"+msg);}}'''
files[P+'TestStarter19.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class TestStarter19 {
 public static void main(String[] args){int n=0;
  for(int clazz=0;clazz<256;clazz++)for(int cap=0;cap<2;cap++)for(int has=0;has<2;has++){
   L1PcInstance oldPc=new L1PcInstance(),newPc=new L1PcInstance();
   oldPc.clazz=newPc.clazz=clazz;oldPc.capacity=newPc.capacity=cap;
   oldPc.hasItem=newPc.hasItem=has==1;
   L1ItemInstance item=new L1ItemInstance(3000366);
   RecruitStarterKitBaseline.clickItem(oldPc,3000366,item);
   RecruitStarterKit.clickItem(newPc,3000366,item);
   if(!oldPc.events.toString().equals(newPc.events.toString())||oldPc.consumed!=newPc.consumed)
     throw new AssertionError("class="+clazz+" cap="+cap+" has="+has+" old="+oldPc.events+" now="+newPc.events);
   n++;
  }
  System.out.println("PASS recruit starter 8 loadouts Java21 typed differential: "+n+" combinations, exact grant order/consumption/failure");
 }
}'''
compile_test(files)
