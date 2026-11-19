#!/usr/bin/env python3
"""Typed Java21 replay: reward table fidelity, all-or-nothing failure, post-COMMIT packets."""
from pathlib import Path
import re, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'src/l1j/server/server/model/item/function'
OLD=Path(__file__).resolve().parent/'baseline'
old=(OLD/'RecruitStarterKit.java').read_text('utf8')
new=(SRC/'RecruitStarterKit.java').read_text('utf8')
classes=['Warrior','Knight','Dragonknight','Crown','Wizard','Blackwizard','Elf','Darkelf']
expected=[]
oldarg=[]
newarg=[]
for name in classes:
    a=old.split('private static void grant'+name+'(L1PcInstance pc)',1)[1].split('}',1)[0]
    b=new.split('private static boolean grant'+name+'(',1)[1].split('}',1)[0]
    o=[tuple(map(int,m.groups())) for m in re.finditer(r'(?m)^\s*봉인템\(pc,\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*true\);',a)]
    n=[tuple(map(int,m.groups())) for m in re.finditer(r'(?m)^\s*if \(!봉인템\(pc, tx, announcements,\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*true\)\) return false;',b)]
    assert o==n and o,name
    expected.append(o)
print('PASS exact original eight reward sequences, count=%d'%sum(map(len,expected)))

java_expected=',\n'.join('        {'+','.join('{'+','.join(str(v) for v in tup)+'}' for tup in group)+'}' for group in expected)
JAVA='''package l1j.server.server.model.item.function;
import java.util.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.network.core.cs.packet.*;
public final class TestRecruitAtomic31 {
  static final int[][][] EXPECTED={
@EXPECTED@
  };
  static int tests=0;
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static List<int[]> expectedFor(int mask){
    List<int[]> a=new ArrayList<>();
    for(int bit=0;bit<8;bit++)if((mask&(1<<bit))!=0)
      for(int[] row:EXPECTED[bit])a.add(row);
    return a;
  }
  static String log(int[] tuple){return tuple[0]+":"+tuple[1]+":"+tuple[2]+":"+tuple[3]+":"+tuple[4];}
  static void reset(){L1InventoryTransaction.failGrantAt=-1;L1InventoryTransaction.failCommit=false;ItemTable.missing=0;}
  static L1PcInstance pc(int mask){L1PcInstance p=new L1PcInstance();p.clazz=mask;return p;}
  static void compare(L1PcInstance p, List<int[]> expected){
    check(p.inv.rewards.size()==expected.size(),"grant count:"+p.inv.rewards.size()+" != "+expected.size());
    for(int i=0;i<expected.size();i++){
      L1ItemInstance got=p.inv.rewards.get(i);
      int[] r=expected.get(i);
      check(got.itemId==r[0]&&got.count==r[1]&&got.enchant==r[2]&&got.bless==r[3]&&got.attr==r[4]&&got.identified,"reward index="+i);
      check(p.messages.get(i).equals("403:"+log(r)),"message order index="+i+" got="+p.messages.get(i));
    }
  }
  public static void main(String[] args){
    // Exhaust all character-class flag combinations; even abnormal overlap retains old order.
    for(int mask=0;mask<256;mask++){
      reset(); L1PcInstance p=pc(mask);L1ItemInstance src=p.inv.source;
      RecruitStarterKit.clickItem(p,3000366,src);
      List<int[]> ex=expectedFor(mask);
      if(ex.isEmpty()){
        check(p.inv.count==1 && p.inv.rewards.isEmpty(),"unknown class consumed");
        check(CDB.commitCount==0 || User.last==null || !User.last.committed,"unknown class commit");
      } else {
        check(p.inv.count==1&&p.inv.rewards.isEmpty()&&p.messages.isEmpty(),"visible before COMMIT");
        check(User.last!=null&&User.last.committed,"receipt not submitted");
        User.last.finishCommitted();
        check(p.inv.count==0,"successful source debit");
        check(p.messages.size()==ex.size(),"success messages after commit");
        compare(p,ex);
      }
      tests++;
    }
    for(int mask=1;mask<=128;mask*=2){
      int size=expectedFor(mask).size();
      for(int fail=1;fail<=size;fail++){
        reset();L1InventoryTransaction.failGrantAt=fail;
        L1PcInstance p=pc(mask);RecruitStarterKit.clickItem(p,3000366,p.inv.source);
        check(p.inv.count==1&&p.inv.rewards.isEmpty(),"partial debit/grant at "+mask+"/"+fail);
        check(User.last!=null&&!User.last.committed,"commit despite grant failure");
        check(p.messages.size()==1&&p.messages.get(0).equals("82"),"failure notice");
        tests++;
      }
    }
    // Unknown template, DB rollback, admission error, bad source, wrong item ID.
    for(int mask=1;mask<=128;mask*=2){
      reset();ItemTable.missing=EXPECTED[Integer.numberOfTrailingZeros(mask)][0][0];
      L1PcInstance p=pc(mask);RecruitStarterKit.clickItem(p,3000366,p.inv.source);
      check(p.inv.count==1&&p.inv.rewards.isEmpty()&&!User.last.committed,"missing template");tests++;
      reset();p=pc(mask);RecruitStarterKit.clickItem(p,3000366,p.inv.source);
      check(p.messages.isEmpty(),"pre-failure message");
      User.last.finishFailed();
      check(p.inv.count==1&&p.inv.rewards.isEmpty(),"rollback lost source");
      check(p.messages.size()==1&&p.messages.get(0).startsWith("SYS:"),"onFailure");tests++;
    }
    // Projected-capacity rejection at submit: prepared items must never become live.
    for(int mask=1;mask<=128;mask*=2){
      reset();L1InventoryTransaction.failCommit=true;
      L1PcInstance p=pc(mask);RecruitStarterKit.clickItem(p,3000366,p.inv.source);
      check(p.inv.count==1 && p.inv.rewards.isEmpty(),"capacity rejection changed live items");
      check(p.messages.size()==1 && p.messages.get(0).startsWith("SYS:"),"capacity rejection notice");tests++;
    }
    reset();L1PcInstance p=pc(1);RecruitStarterKit.clickItem(p,3000366,new L1ItemInstance(new l1j.server.server.templates.L1Item(3000366),1));
    check(p.inv.count==1&&p.inv.rewards.isEmpty(),"stale source was consumed");tests++;
    reset();p=pc(1);RecruitStarterKit.clickItem(p,3000367,p.inv.source);
    check(p.inv.count==1&&p.inv.rewards.isEmpty(),"wrong item dispatch");tests++;
    System.out.println("PASS Java21 production typed replay "+tests+" cases incl all 8 grant lists, all intermediate failures, delayed COMMIT, rollback, stale input");
  }
}'''.replace('@EXPECTED@',java_expected)
STUBS={
'l1j/server/server/templates/L1Item.java':'''package l1j.server.server.templates;public final class L1Item {public final int id;public L1Item(int i){id=i;}}''',
'l1j/server/server/IdFactory.java':'''package l1j.server.server;public final class IdFactory{static final IdFactory SINGLETON=new IdFactory();static int n=500;public static IdFactory getInstance(){return SINGLETON;}public int nextId(){return ++n;}}''',
'l1j/server/server/datatables/ItemTable.java':'''package l1j.server.server.datatables;import l1j.server.server.templates.L1Item;public final class ItemTable{public static int missing;private static final ItemTable SINGLETON=new ItemTable();public static ItemTable getInstance(){return SINGLETON;}public L1Item getTemplate(int id){return id==missing?null:new L1Item(id);}}''',
'l1j/server/server/model/Instance/L1ItemInstance.java':'''package l1j.server.server.model.Instance;import l1j.server.server.templates.L1Item;
public final class L1ItemInstance {
 public final int itemId;public int id,count,enchant,attr,bless;public boolean identified;
 public L1ItemInstance(L1Item def,int num){itemId=def.id;count=num;}
 public void setId(int v){id=v;}public int getId(){return id;}public int getItemId(){return itemId;}
 public void setEnchantLevel(int v){enchant=v;}public void setAttrEnchantLevel(int v){attr=v;}
 public void setIdentified(boolean v){identified=v;}public void setBless(int v){bless=v;}
 public String getLogName(){return itemId+":"+count+":"+enchant+":"+bless+":"+attr;}
}''',
'l1j/server/server/model/Instance/L1PcInstance.java':'''package l1j.server.server.model.Instance;import java.util.*;
public final class L1PcInstance{
 public final List<String> messages=new ArrayList<>();public int clazz;
 public final Inventory inv=new Inventory();public Inventory getInventory(){return inv;}public int getId(){return 13;}
 public boolean isWarrior(){return (clazz&1)!=0;}public boolean isKnight(){return (clazz&2)!=0;}
 public boolean isDragonknight(){return (clazz&4)!=0;}public boolean isCrown(){return (clazz&8)!=0;}
 public boolean isWizard(){return (clazz&16)!=0;}public boolean isBlackwizard(){return (clazz&32)!=0;}
 public boolean isElf(){return (clazz&64)!=0;}public boolean isDarkelf(){return (clazz&128)!=0;}
 public final class Inventory{public int count=1;public L1ItemInstance source=new L1ItemInstance(new l1j.server.server.templates.L1Item(3000366),1);
  public final List<L1ItemInstance> rewards=new ArrayList<>();public Inventory(){source.setId(301);}
  public L1ItemInstance getItem(int id){return id==301&&count>0?source:null;}
 }
}''',
'l1j/server/server/model/L1InventoryTransaction.java':'''package l1j.server.server.model;import java.util.*;
import l1j.server.server.model.Instance.*;
public final class L1InventoryTransaction implements AutoCloseable{
 public static int failGrantAt=-1;public static boolean failCommit;public final L1PcInstance pc;public final List<L1ItemInstance> prepared=new ArrayList<>();
 public int tries=0;public boolean committed,closed;public Runnable callback,onFailure;public boolean debited;
 public L1InventoryTransaction(L1PcInstance pc){this.pc=pc;}
 public boolean debit(L1ItemInstance src,int n){if(src!=pc.inv.source||pc.inv.count<n)return false;debited=true;return true;}
 public L1ItemInstance grant(L1ItemInstance item){if(++tries==failGrantAt)return null;prepared.add(item);return item;}
 public void onFailure(Runnable fail){onFailure=fail;}
 public void commitAfterInventoryRelease(Runnable result){if(!debited||prepared.isEmpty())throw new AssertionError("invalid commit");if(failCommit)throw new IllegalStateException("Projected inventory capacity exceeded");committed=true;callback=result;}
 public void finishCommitted(){if(!committed)throw new AssertionError("not submitted");pc.inv.count--;pc.inv.rewards.addAll(prepared);if(callback!=null)callback.run();}
 public void finishFailed(){if(!committed)throw new AssertionError("not submitted");if(onFailure!=null)onFailure.run();}
 @Override public void close(){closed=true;}
}''',
'l1j/server/server/model/network/core/cs/packet/User.java':'''package l1j.server.server.model.network.core.cs.packet;import l1j.server.server.model.*;import l1j.server.server.model.Instance.L1PcInstance;
public final class User{public static L1InventoryTransaction last;public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,int key){last=new L1InventoryTransaction(p);return last;}}''',
'l1j/server/server/model/network/core/cs/packet/CDB.java':'''package l1j.server.server.model.network.core.cs.packet;import l1j.server.server.model.*;import l1j.server.server.model.Instance.L1PcInstance;
public final class CDB{public static int commitCount;public static void RequestUseItem(L1PcInstance p,L1InventoryTransaction t,Runnable after){commitCount++;t.commitAfterInventoryRelease(after);}}''',
'l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_ServerMessage{public static void send(L1PcInstance p,int n){p.messages.add(""+n);}public static void send(L1PcInstance p,int n,String msg){p.messages.add(n+":"+msg);}}''',
'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_SystemMessage{public static void send(L1PcInstance p,String msg){p.messages.add("SYS:"+msg);}}''',
'l1j/server/server/storage/CacheD.java':'''package l1j.server.server.storage;public final class CacheD{public static int accountOrderKey(String s,int n){return n;}}''',
'l1j/server/server/model/item/function/TestRecruitAtomic31.java':JAVA,
'l1j/server/server/model/item/function/RecruitStarterKit.java':(SRC/'RecruitStarterKit.java').read_text('utf8'),
'l1j/server/server/model/item/function/RecruitStarterItemGrant.java':(SRC/'RecruitStarterItemGrant.java').read_text('utf8'),
}
with tempfile.TemporaryDirectory(prefix='rkern_recruit31_') as t:
    d=Path(t)
    for name,src in STUBS.items():
        p=d/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(src,'utf8')
    out=d/'classes';out.mkdir()
    cmd=['javac','--release','21','-encoding','UTF-8','-d',str(out)]+[str(p) for p in d.rglob('*.java')]
    x=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    if x.returncode:raise RuntimeError('javac type compilation failed:\n'+x.stderr)
    x=subprocess.run(['java','-cp',str(out),'l1j.server.server.model.item.function.TestRecruitAtomic31'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    if x.returncode:raise RuntimeError('Java replay failed:\n'+x.stdout+'\n'+x.stderr)
    print(x.stdout.strip())
