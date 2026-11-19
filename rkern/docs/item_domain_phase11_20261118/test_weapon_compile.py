#!/usr/bin/env python3
"""Type compile actual Phase11 weapon domain with a controlled Java21 item model."""
from pathlib import Path
import tempfile,subprocess,re
R=Path(__file__).resolve().parents[2]
F=R/'src/l1j/server/server/model/item/function'
P='l1j/server/server/model/item/function/'
I='l1j/server/server/model/Instance/'
N='l1j/server/server/model/network/core/sc/packet/'
S='l1j/server/server/model/network/core/session/'
files={}
for name in ('WeaponEnchantScrollActions','WeaponEnchantTargetRules','WeaponEnchantLevelLimits','WeaponEnchantScrollResolution','WeaponEnchantChanceCalculation','ItemActionResult'):
    files[P+name+'.java']=(F/(name+'.java')).read_text('utf-8')
text='\n'.join(files.values())
fields=sorted(set(re.findall(r'Config\.([\w\u0080-\uffff]+)',text)) - {'ENCHANT_MAX_FAIL','rareweapon','weapon'})
files['l1j/server/Config.java']='package l1j.server; public final class Config { public static boolean ENCHANT_MAX_FAIL=false; public static int[] rareweapon=new int[20],weapon=new int[20]; '+ ' '.join(f'public static int {n}=12;' for n in fields)+' }'
files[P+'ItemActionContext.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
final class ItemActionContext {
 L1PcInstance p;L1ItemInstance item,target;CIOClient client=new CIOClient();int id;
 L1PcInstance player(){return p;} L1ItemInstance item(){return item;}
 L1ItemInstance targetItem(){return target;} CIOClient client(){return client;} int itemId(){return id;}
}'''
files[I+'L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public final class L1ItemInstance {
 public int id=99,type2=1,safe=6,ench=0,attr=0,bless=0,material=0; public boolean equipped;
 public boolean isEquipped(){return equipped;} public L1ItemInstance getItem(){return this;}
 public int getType2(){return type2;} public int get_safeenchant(){return safe;}
 public int getItemId(){return id;} public int getBless(){return bless;}
 public int getEnchantLevel(){return ench;} public int getAttrEnchantLevel(){return attr;}
 public int getMaterial(){return material;}
}'''
methods=' '.join(f'public boolean getIsChangeItem{n}(){{return changing;}}' for n in range(1,21))
files[I+'L1PcInstance.java']='''package l1j.server.server.model.Instance;
public final class L1PcInstance {
 public boolean changing,gm,_EnchantWeaponSuccess;public static StringBuilder log=new StringBuilder();
 public static void add(String s){log.append(s).append('|');}
 public boolean isGm(){return gm;} public Inventory getInventory(){return new Inventory();}
 public static final class Inventory {public void removeItem(L1ItemInstance i,int n){add("consume:"+n);}}
'''+methods+' }'
files[S+'CIOClient.java']='package l1j.server.server.model.network.core.session;public final class CIOClient {}'
files['l1j/server/server/datatables/WeaponEnchantInformationTable.java']='''package l1j.server.server.datatables;
public final class WeaponEnchantInformationTable {
 public static WeaponEnchantInformationTable getInstance(){return new WeaponEnchantInformationTable();}
 public int getChance(int weapon,int level){return 35;}
}'''
files['l1j/server/server/model/item/L1ItemId.java']='''package l1j.server.server.model.item;
public final class L1ItemId {
 public static final int SCROLL_OF_ENCHANT_QUEST_WEAPON=17, SCROLL_OF_ENCHANT_FANTASY_WEAPON=18, IVORYTOWER_WEAPON_SCROLL=19,C_SCROLL_OF_ENCHANT_WEAPON=20,ENCHANT_WEAPONA=21;
}'''
files[N+'SC_ChatPacket.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_ChatPacket { public static void sendDirect(L1PcInstance pc,L1PcInstance t,String s){L1PcInstance.add("chat");}}'''
files[N+'SC_ServerMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_ServerMessage {public static void send(L1PcInstance p,int id){L1PcInstance.add("server:"+id);}}'''
files[N+'SC_SystemMessage.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SystemMessage {public static void send(L1PcInstance p,String msg){L1PcInstance.add("system");}}'''
files['l1j/server/server/utils/CommonUtil.java']='package l1j.server.server.utils; public final class CommonUtil {public static int random(int n){return 0;}}'
files[P+'CreatureItemActions.java']='''package l1j.server.server.model.item.function;
import java.util.Random;
public final class CreatureItemActions { static final Random _random=new Random(7); }'''
files[P+'EnchantmentItems.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
public final class EnchantmentItems {
 public static void AttrEnchant(L1PcInstance p,L1ItemInstance scroll,L1ItemInstance target){L1PcInstance.add("attribute");}
 public static void FailureEnchant(L1PcInstance p,L1ItemInstance target,CIOClient client){L1PcInstance.add("failure");}
 public static void SuccessEnchant(L1PcInstance p,L1ItemInstance target,CIOClient client,int level){L1PcInstance.add("success:"+level);}
}'''
files[P+'TransformationItems.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class TransformationItems {public static int RandomELevel(L1ItemInstance target,int id){return 1;}}'''
files[P+'AttributeEnchantScrolls.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
public final class AttributeEnchantScrolls {static void apply(L1PcInstance p,L1ItemInstance item,L1ItemInstance target){L1PcInstance.add("attribute2");}}'''
files[P+'Phase11RealWeaponPipeline.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class Phase11RealWeaponPipeline {
 public static void main(String[]args) {
   ItemActionContext ctx=new ItemActionContext();ctx.p=new L1PcInstance();ctx.item=new L1ItemInstance();ctx.id=40087;
   if(WeaponEnchantScrollActions.use(ctx)!=ItemActionResult.STOP||!L1PcInstance.log.toString().equals("server:4302|"))throw new AssertionError("null target gate "+L1PcInstance.log);
   L1PcInstance.log.setLength(0);ctx.target=new L1ItemInstance();ctx.target.equipped=true;
   if(WeaponEnchantScrollActions.use(ctx)!=ItemActionResult.STOP||!L1PcInstance.log.toString().equals("server:4302|"))throw new AssertionError("equipped gate");
   L1PcInstance.log.setLength(0);ctx.target.equipped=false;ctx.target.type2=0;
   if(WeaponEnchantScrollActions.use(ctx)!=ItemActionResult.STOP||!L1PcInstance.log.toString().equals("server:79|"))throw new AssertionError("type gate");
   L1PcInstance.log.setLength(0);ctx.target.type2=1;ctx.target.safe=6;ctx.target.ench=0;
   if(WeaponEnchantScrollActions.use(ctx)!=ItemActionResult.COMPLETE || !L1PcInstance.log.toString().equals("consume:1|success:1|")) throw new AssertionError("normal enchant "+L1PcInstance.log);
   System.out.println("PASS 5 production weapon classes typed Java 21 compilation and 4 boundary scenarios");
 }
}'''
with tempfile.TemporaryDirectory(prefix='rkern_phase11_weapon_') as tmp:
    t=Path(tmp)
    for name,src in files.items():
        dest=t/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(src,'utf-8')
    run=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,*[str(p) for p in t.rglob('*.java')]],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
    run=subprocess.run(['java','-cp',tmp,'l1j.server.server.model.item.function.Phase11RealWeaponPipeline'],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
    print(run.stdout.strip())
