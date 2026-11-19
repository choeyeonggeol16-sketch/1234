"""Compile the actual production CardFusionChanceItems with Java 21 typed stubs.
Exercise receipt callbacks, commit-vs-rollback, independent packet failures.
This is not an actual DB, CIO, socket or game test.
"""
from pathlib import Path
import subprocess
import shutil
import textwrap
import tempfile
root=Path(__file__).resolve().parents[2]
tmp=Path(tempfile.mkdtemp(prefix='rkern_item_phase112_'))
src=tmp/'src';classes=tmp/'classes';classes.mkdir(parents=True)

def put(name,source):
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(textwrap.dedent(source).strip()+'\n',encoding='utf8')

(src/'l1j/server/server/model/item/function').mkdir(parents=True,exist_ok=True)
shutil.copy2(root/'src/l1j/server/server/model/item/function/CardFusionChanceItems.java',
             src/'l1j/server/server/model/item/function/CardFusionChanceItems.java')
put('l1j/server/server/model/Instance/L1PcInstance.java','''
package l1j.server.server.model.Instance;
public class L1PcInstance {
 public boolean 영웅합성확률증가,전설합성확률증가,신화합성확률증가;
 public int getId(){return 12345;}
}
''')
put('l1j/server/server/model/Instance/L1ItemInstance.java','''
package l1j.server.server.model.Instance;
public class L1ItemInstance { public final int id; public L1ItemInstance(int id){this.id=id;} }
''')
put('l1j/server/server/model/network/core/session/CIOClient.java','''
package l1j.server.server.model.network.core.session;
public class CIOClient {}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SystemMessage {
 public static boolean FAIL; public static int TRIED,SUCCEEDED;
 public static String LAST;
 public static void send(L1PcInstance pc,String value){TRIED++;if(FAIL)throw new IllegalStateException("system packet fault"); SUCCEEDED++;LAST=value;}
 public static void reset(){FAIL=false;TRIED=0;SUCCEEDED=0;LAST=null;}
}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_PacketBox {
 public static final int GREEN_MESSAGE=22;
 public static boolean FAIL; public static int TRIED,SUCCEEDED;
 public static String LAST;
 public static void send(L1PcInstance pc,int code,String msg){if(code!=GREEN_MESSAGE)throw new AssertionError("changed packet type");TRIED++;if(FAIL)throw new IllegalStateException("packet box fault");SUCCEEDED++;LAST=msg;}
 public static void reset(){FAIL=false;TRIED=0;SUCCEEDED=0;LAST=null;}
}
''')
put('l1j/server/server/model/item/function/ItemReceiptOperations.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemReceiptOperations {
 public static int SUBMITTED,COMMITTED,ROLLBACK;public static Runnable CALLBACK;
 public static L1PcInstance PLAYER;public static L1ItemInstance ITEM;
 public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable afterCommitted){SUBMITTED++;CALLBACK=afterCommitted;PLAYER=pc;ITEM=item;}
 public static void finish(boolean committed){if(CALLBACK==null)throw new AssertionError("no submission");Runnable cb=CALLBACK;CALLBACK=null;if(committed){COMMITTED++;cb.run();}else ROLLBACK++;}
 public static void reset(){SUBMITTED=0;COMMITTED=0;ROLLBACK=0;CALLBACK=null;PLAYER=null;ITEM=null;}
}
''')
put('l1j/server/server/model/item/function/CardChance112Test.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.*;
public class CardChance112Test {
 static int checks;
 static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static boolean bonus(L1PcInstance p,int tier){return tier==0?p.영웅합성확률증가:tier==1?p.전설합성확률증가:p.신화합성확률증가;}
 static void flag(L1PcInstance p,int tier,boolean v){if(tier==0)p.영웅합성확률증가=v;else if(tier==1)p.전설합성확률증가=v;else p.신화합성확률증가=v;}
 static String[] messages={"영웅카드 합성확률이 증가합니다!","전설카드 합성확률이 증가합니다!","신화카드 합성확률이 증가합니다!"};
 static void reset(){ItemReceiptOperations.reset();SC_SystemMessage.reset();SC_PacketBox.reset();}
 public static void main(String[] args){
  for(int repeat=0;repeat<64;repeat++)for(int tier=0;tier<3;tier++)for(int mask=0;mask<4;mask++){
    reset();L1PcInstance p=new L1PcInstance();L1ItemInstance input=new L1ItemInstance(5000+tier);
    CardFusionChanceItems.use(null,p,input,5200101+tier,null);
    ok(ItemReceiptOperations.SUBMITTED==1,"exactly one item submission");
    ok(ItemReceiptOperations.PLAYER==p && ItemReceiptOperations.ITEM==input,"original owner/input passed");
    ok(!bonus(p,tier),"buff cannot apply before durable commit");
    SC_SystemMessage.FAIL=(mask&1)!=0;SC_PacketBox.FAIL=(mask&2)!=0;
    ItemReceiptOperations.finish(true); // Must never throw for packet-only failures.
    ok(bonus(p,tier),"committed potion must activate buff despite packet failures");
    ok(ItemReceiptOperations.COMMITTED==1,"one durable commit");
    ok(SC_SystemMessage.TRIED==1 && SC_PacketBox.TRIED==1,"independent attempted UI effects");
    ok(SC_SystemMessage.SUCCEEDED==((mask&1)!=0?0:1),"system packet failure isolated");
    ok(SC_PacketBox.SUCCEEDED==((mask&2)!=0?0:1),"packet box failure isolated");
    if((mask&1)==0)ok(messages[tier].equals(SC_SystemMessage.LAST),"system text unchanged");
    if((mask&2)==0)ok(messages[tier].equals(SC_PacketBox.LAST),"packet box text unchanged");
  }
  for(int tier=0;tier<3;tier++){
    reset();L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(tier);
    CardFusionChanceItems.use(null,p,item,5200101+tier,null);
    ItemReceiptOperations.finish(false);
    ok(!bonus(p,tier),"rollback cannot activate buff");
    ok(ItemReceiptOperations.ROLLBACK==1 && ItemReceiptOperations.COMMITTED==0,"rolled back, never committed");
    reset();flag(p,tier,true);
    CardFusionChanceItems.use(null,p,item,5200101+tier,null);
    ok(ItemReceiptOperations.SUBMITTED==0,"duplicate active bonus cannot consume potion");
    ok(SC_SystemMessage.SUCCEEDED==1,"active-bonus warning preserved");
  }
  reset();CardFusionChanceItems.use(null,new L1PcInstance(),new L1ItemInstance(0),42,null);
  ok(ItemReceiptOperations.SUBMITTED==0,"other item IDs untouched");
  System.out.println("PASS 112 typed-production java21 independent fault injection checks="+checks);
 }
}
''')
files=sorted(src.rglob('*.java'))
r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*[str(p) for p in files]],capture_output=True,text=True)
if r.returncode:
 print(r.stdout,r.stderr);raise SystemExit('JAVAC FAILED')
r=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.CardChance112Test'],capture_output=True,text=True)
print(r.stdout.strip());print('expected injected logger warnings:',r.stderr.count('WARNING:'))
if r.returncode:
 print(r.stderr[-2500:]);raise SystemExit('JAVA TEST FAILED')

shutil.rmtree(tmp, ignore_errors=True)
