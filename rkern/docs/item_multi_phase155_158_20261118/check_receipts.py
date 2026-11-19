#!/usr/bin/env python3
"""Java21 typed execution of actual ItemTransactionActions/ItemReceiptOperations with injected callbacks."""
import sys,subprocess,tempfile
from pathlib import Path
src=Path(sys.argv[1]); base=src/'l1j/server/server/model/item/function'
production=[base/'ItemTransactionActions.java']
with tempfile.TemporaryDirectory(prefix='rk_receipt_') as tmp:
 r=Path(tmp)
 def add(name,body):
  p=r/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf8')
 add('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;public class L1ItemInstance {
 public int id,count=1,recordingColumns;public L1ItemInstance(int id){this.id=id;}public int getId(){return id;}public int getRecordingColumns(){return recordingColumns;}
 }''')
 add('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
 import l1j.server.server.model.L1PcInventory;
 public class L1PcInstance {public L1PcInventory inv=new L1PcInventory();public int getId(){return 123;}public L1PcInventory getInventory(){return inv;}}
 ''')
 add('l1j/server/server/model/L1PcInventory.java','''package l1j.server.server.model;
 import java.util.*;import l1j.server.server.model.Instance.*;
 public class L1PcInventory {public static final int COL_SAVE_ALL=32768;
 public Map<Integer,L1ItemInstance> items=new HashMap<>();public int saved;
 public L1ItemInstance getItem(int id){return items.get(id);}public void saveItem(L1ItemInstance x,int col){saved++;}}
 ''')
 add('l1j/server/server/storage/CacheD.java','''package l1j.server.server.storage;public class CacheD{public static int accountOrderKey(String s,int id){return id;}}''')
 add('l1j/server/server/model/L1InventoryTransaction.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.*;
 public class L1InventoryTransaction implements AutoCloseable{
 public static boolean accept=true;public static L1InventoryTransaction last;
 public boolean submitted,closed;public Runnable failure,success;public int debits,grants;
 public boolean debit(L1ItemInstance x,int count){debits++;return accept && x.count>=count;}
 public L1ItemInstance grant(int id,int count){grants++;return new L1ItemInstance(id);}
 public void onFailure(Runnable r){failure=r;}
 public void close(){closed=true;}
 }
 ''')
 add('l1j/server/server/model/network/core/cs/packet/User.java','''package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 public class User{public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,int key){
 L1InventoryTransaction tx=new L1InventoryTransaction();L1InventoryTransaction.last=tx;return tx;}}
 ''')
 add('l1j/server/server/model/network/core/cs/packet/CDB.java','''package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 public class CDB {public static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable callback){tx.submitted=true;tx.success=callback;}}
 ''')
 for cls in ('SC_SystemMessage','SC_ServerMessage'):
  add('l1j/server/server/model/network/core/sc/packet/'+cls+'.java',f'''package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public class {cls} {{public static boolean throwOnSend;public static int sends;
 public static void send(L1PcInstance pc,String msg){{sends++;if(throwOnSend)throw new RuntimeException("disconnected");}}
 public static void send(L1PcInstance pc,int msg){{sends++;if(throwOnSend)throw new RuntimeException("disconnected");}}
 }}''')
 add('l1j/server/server/model/item/function/CreatureItemActions.java','''package l1j.server.server.model.item.function;
 import java.util.logging.*;public class CreatureItemActions{public static final Logger ITEM_USE2_LOG=Logger.getLogger("tx-stub");}
 ''')
 # Reuse the complete real source body with unused imports omitted only in this typed-test copy.
 receipt=(base/'ItemReceiptOperations.java').read_text(encoding='utf8')
 receipt='\n'.join(line for line in receipt.splitlines() if not line.startswith('import '))
 imports='\n'.join(['import l1j.server.server.model.Instance.L1ItemInstance;',
 'import l1j.server.server.model.Instance.L1PcInstance;',
 'import java.util.logging.Logger;',
 'import l1j.server.server.model.network.core.cs.packet.CDB;',
 'import l1j.server.server.model.network.core.cs.packet.User;',
 'import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;',
 'import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;'])
 receipt=receipt.replace('package l1j.server.server.model.item.function;',
      'package l1j.server.server.model.item.function;\n'+imports,1)
 add('l1j/server/server/model/item/function/ItemReceiptOperations.java',receipt)
 add('l1j/server/server/model/item/function/Check.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.*;
 import l1j.server.server.model.network.core.sc.packet.*;
 public class Check {static int cases; static void ok(boolean b){cases++;if(!b)throw new AssertionError("failed "+cases);}
 public static void main(String[] args){
 L1PcInstance pc=new L1PcInstance();L1ItemInstance a=new L1ItemInstance(1),b=new L1ItemInstance(2);
 pc.inv.items.put(1,a);pc.inv.items.put(2,b);a.recordingColumns=123;
 int[] applied={0};ItemReceiptOperations.UseDurableConsumeTwoInput(pc,a,b,()->applied[0]++);
 L1InventoryTransaction tx=L1InventoryTransaction.last;
 ok(tx.debits==2);ok(pc.inv.saved==1);ok(tx.submitted);ok(applied[0]==0);ok(tx.closed);
 tx.success.run();ok(applied[0]==1);
 // Confirmed rollback: a disconnected error-packet recipient cannot throw out of callback.
 SC_SystemMessage.throwOnSend=true;
 tx.failure.run();ok(SC_SystemMessage.sends==1);
 SC_SystemMessage.throwOnSend=false;
 L1InventoryTransaction.accept=false;
 ItemReceiptOperations.UseDurableConsumeTwoInput(pc,a,b,()->applied[0]++);
 ok(!L1InventoryTransaction.last.submitted);ok(applied[0]==1);
 L1InventoryTransaction.accept=true;
 ItemReceiptOperations.UseDurableExchange(pc,a,101,()->applied[0]++);
 ok(L1InventoryTransaction.last.grants==1);ok(L1InventoryTransaction.last.submitted);
 SC_SystemMessage.throwOnSend=true; L1InventoryTransaction.last.failure.run();ok(SC_SystemMessage.sends==2);
 System.out.println("RECEIPTS_TYPED_CASES="+cases+" PASS");
 }}''')
 classes=r/'classes';classes.mkdir();files=list(r.rglob('*.java'))+production
 c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes)]+list(map(str,files)),capture_output=True,text=True)
 if c.returncode:print(c.stdout+c.stderr);sys.exit(c.returncode)
 c=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.Check'],capture_output=True,text=True)
 if c.returncode: print(c.stdout+c.stderr);sys.exit(c.returncode)
 print(c.stdout.strip())
