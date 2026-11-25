#!/usr/bin/env python3
"""Typed Java 21 regression against production item admission/prep/completion classes."""
from pathlib import Path
import tempfile, subprocess
R=Path(__file__).resolve().parents[3]
PK=R/'src/l1j/server/server/model/item/function'
F='l1j/server/server/model/item/function/'
stubs={
'l1j/server/server/model/network/core/session/CIOClient': 'package l1j.server.server.model.network.core.session; public class CIOClient {}',
'l1j/server/server/model/network/core/cs/packet/CS_ItemUse':'package l1j.server.server.model.network.core.cs.packet; public final class CS_ItemUse {}',
'l1j/server/server/utils/SupplementalItemIds':'package l1j.server.server.utils; public final class SupplementalItemIds {public static boolean contains(int id){return false;}}',
'l1j/server/server/templates/L1EtcItem':'''package l1j.server.server.templates;
public class L1EtcItem {
 public final int id,type,type2,delay;
 public L1EtcItem(int id,int type,int type2,int delay){this.id=id;this.type=type;this.type2=type2;this.delay=delay;}
 public int getItemId(){return id;} public int getUseType(){return 0;} public int getType(){return type;} public int getType2(){return type2;} public int get_delayEffect(){return delay;}
}''',
'l1j/server/server/model/Instance/L1ItemInstance':'''package l1j.server.server.model.Instance;
import java.sql.Timestamp;import l1j.server.server.templates.L1EtcItem;
public class L1ItemInstance {
 public final L1EtcItem item; public int charge=0;public Timestamp used; public int chargesWritten;public L1ItemInstance(L1EtcItem i){item=i;}
 public L1EtcItem getItem(){return item;} public String getLogName(){return "item";}
 public int getChargeCount(){return charge;} public void setChargeCount(int n){charge=n;chargesWritten++;}
 public void setLastUsed(Timestamp n){used=n;}
}''',
'l1j/server/server/model/CUserInventory':'''package l1j.server.server.model;
import l1j.server.server.model.Instance.L1ItemInstance;
public class CUserInventory {
 public static final int COL_CHARGE_COUNT=4,COL_DELAY_EFFECT=8;
 public int arrowId,stingId,finds,scans,updates,saves,removes,arrowGets,stingGets;
 public L1ItemInstance arrow,sting,target;
 public int getArrowItemId(){return arrowId;} public void setArrow(int id){arrowId=id;}
 public int getStingItemId(){return stingId;} public void setSting(int id){stingId=id;}
 public L1ItemInstance getArrow(){arrowGets++;return arrow;} public L1ItemInstance getSting(){stingGets++;return sting;}
 public L1ItemInstance findItemId(int id){finds++;return arrow!=null && arrow.getItem().getItemId()==id?arrow:sting!=null && sting.getItem().getItemId()==id?sting:null;}
 public L1ItemInstance getItem(int id){scans++;return id==777?target:null;}
 public void removeItem(L1ItemInstance i,int n){removes++;}
 public void updateItem(L1ItemInstance i,int col){updates++;}
 public void saveItem(L1ItemInstance i,int col){saves++;}
}''',
'l1j/server/server/model/Instance/L1PcInstance':'''package l1j.server.server.model.Instance;
import l1j.server.server.model.CUserInventory;
public class L1PcInstance {public final CUserInventory inv=new CUserInventory(); public L1ItemInstance weapon; public CUserInventory getInventory(){return inv;} public L1ItemInstance getWeapon(){return weapon;}}''',
'l1j/server/server/model/L1ItemDelay':'''package l1j.server.server.model;
import l1j.server.server.model.Instance.*;
public class L1ItemDelay {public static int calls;public static void onItemUse(L1PcInstance p,L1ItemInstance i){calls++;}}''',
'l1j/server/server/model/item/L1TreasureBox':'''package l1j.server.server.model.item;
import l1j.server.server.model.Instance.*;
public class L1TreasureBox {public static int opens;public static L1TreasureBox get(int id){return id==1600?new L1TreasureBox():null;}public void openAtomic(L1PcInstance p,L1ItemInstance i){opens++;}}''',
F+'StarterSupportHandler':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
final class StarterSupportHandler {static int calls;static final StarterSupportHandler SINGLE=new StarterSupportHandler();static StarterSupportHandler getInstance(){return SINGLE;}void handle(L1PcInstance p,int id,L1ItemInstance i){calls++;}}''',
F+'ItemTargetDecoder':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
final class ItemTargetDecoder {static final int NONE=0,NAME=1,ITEM=2,BLANK=3,TARGET=4,BOOK=5,BOOKMARK=6,FISHING=7,TARGET_NAME=8,POINT=9,STORED=10,COOKING=11,DEFAULT=12;static int modeOf(int i,int t){return DEFAULT;}static boolean showSelection(L1PcInstance pc,int i){return true;}}''',
F+'ItemActionAdmission':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
final class ItemActionAdmission {static boolean deny;static boolean canUse(L1PcInstance p,L1ItemInstance i){return true;}static boolean canUseEtc(L1PcInstance p,L1ItemInstance i){return !deny;}}''',
F+'SupplementalItemActions':'''package l1j.server.server.model.item.function; import l1j.server.server.model.Instance.*; import l1j.server.server.model.network.core.session.*; final class SupplementalItemActions {static void use(CIOClient c,L1PcInstance p,L1ItemInstance i,int target){}}''',
F+'ItemActionDispatcher':'''package l1j.server.server.model.item.function;
final class ItemActionDispatcher {static int calls;static ItemActionResult use(ItemActionContext c){calls++;return ItemActionResult.STOP;}}''',
'l1j/server/server/model/network/core/sc/packet/SC_ItemName':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;public class SC_ItemName {public static int calls;public static void send(L1PcInstance p,L1ItemInstance i){if(i==null)throw new AssertionError("null item name");calls++;}}''',
'l1j/server/server/model/network/core/sc/packet/SC_ServerMessage':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;public class SC_ServerMessage {public static void send(L1PcInstance p,int n,String s){}}''',
'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;public class SC_SystemMessage {public static void send(L1PcInstance p,String s){}}''',
}
test='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.*;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.model.item.L1TreasureBox;
public class ItemHotPathTest {
 static void ck(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static L1ItemInstance item(int id,int type,int delay){return new L1ItemInstance(new L1EtcItem(id,type,0,delay));}
 static void use(L1PcInstance pc,L1ItemInstance it){ItemActionService.use(null,pc,it,123,555,0,0,"name",777);}
 public static void main(String[] argv){
   // Item rejected by class/level admission: no target search, no dispatch.
   L1PcInstance pc=new L1PcInstance();L1ItemInstance denied=item(350,1,0);
   ItemActionAdmission.deny=true;use(pc,denied);
   ck(pc.inv.scans==0,"denied item must not resolve target");ck(ItemActionDispatcher.calls==0,"denied dispatched");
   ItemActionAdmission.deny=false;
   // Starter/box fast paths execute without ItemActionContext/linear inventory lookup.
   use(pc,item(3000572,1,0));ck(StarterSupportHandler.calls==1,"starter not used");
   use(pc,item(1600,16,0));ck(L1TreasureBox.opens==1,"box not opened");
   ck(pc.inv.scans==0,"fast path resolved unrelated target");
   // Normal dispatcher continues to receive resolved target and uses same action completion gate.
   use(pc,item(450,1,0));ck(pc.inv.scans==1 && ItemActionDispatcher.calls==1,"normal dispatch lost target");
   // Sting selection with no prior selection must not modify arrow ID.
   L1PcInstance stingPc=new L1PcInstance();stingPc.inv.arrowId=500;
   ItemActionPreparation.stopsDispatch(stingPc,item(501,15,0),501);
   ck(stingPc.inv.stingId==501 && stingPc.inv.arrowId==500,"sting incorrectly selected as arrow");
   ck(stingPc.inv.finds==0,"sting scanned twice");
   // Existing selected sting: preserve previous-name packet without a second findItemId.
   stingPc.inv.sting=item(502,15,0);
   int oldNameCalls=l1j.server.server.model.network.core.sc.packet.SC_ItemName.calls;
   ItemActionPreparation.stopsDispatch(stingPc,item(503,15,0),503);
   ck(stingPc.inv.stingId==503 && stingPc.inv.finds==0,"sting old selection double scan");
   ck(l1j.server.server.model.network.core.sc.packet.SC_ItemName.calls==oldNameCalls+2,"sting name packet sequence");
   // Arrow switching with a bow reuses getArrow without a findItemId scan.
   L1PcInstance ar=new L1PcInstance();ar.weapon=item(999,4,0);ar.inv.arrow=item(55,0,0);
   ItemActionPreparation.stopsDispatch(ar,item(56,0,0),56);
   ck(ar.inv.arrowId==56 && ar.inv.finds==0 && ar.inv.arrowGets==1,"bow arrow selection");
   // Last charge removes item; remaining charges persist a new cooldown timestamp.
   L1PcInstance c=new L1PcInstance();L1ItemInstance one=item(410008,1,30);one.charge=1;
   ItemActionCompletion.finish(new ItemActionContext(null,c,one,1,0,0,0,"",0));
   ck(c.inv.removes==1 && c.inv.updates==0 && one.used==null && one.charge==0,"last charge semantics");
   L1ItemInstance two=item(410008,1,30);two.charge=2;
   ItemActionCompletion.finish(new ItemActionContext(null,c,two,1,0,0,0,"",0));
   ck(c.inv.removes==1 && c.inv.updates==1 && c.inv.saves==1 && two.used!=null && two.charge==1,"remaining charge semantics");
   ck(L1ItemDelay.calls==2,"item delay after completion");
   System.out.println("PASS actual item service/preparation/context/completion: admission, starter, box, sting, arrow, charges");
 }
}'''
with tempfile.TemporaryDirectory(prefix='itemfollow_') as d:
 d=Path(d);files=[]
 for name,src in stubs.items():
  p=d/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(src,'utf8');files.append(str(p))
 for name in ('ItemActionService','ItemActionPreparation','ItemActionContext','ItemActionCompletion','ItemActionResult'):
  files.append(str(PK/(name+'.java')))
 p=d/(F+'ItemHotPathTest.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(test,'utf8');files.append(str(p))
 out=d/'cls';out.mkdir()
 q=subprocess.run(['javac','-encoding','UTF-8','-d',str(out),*files],capture_output=True,text=True)
 if q.returncode:raise RuntimeError('javac failure: '+q.stderr[:5000])
 q=subprocess.run(['java','-ea','-cp',str(out),'l1j.server.server.model.item.function.ItemHotPathTest'],capture_output=True,text=True)
 if q.returncode:raise RuntimeError('runtime failure: '+q.stderr[:5000])
 print(q.stdout.strip())
