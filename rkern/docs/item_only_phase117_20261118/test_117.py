from pathlib import Path
import subprocess, shutil, textwrap, re, os
root=Path(os.environ.get('SOURCE_ROOT', Path(__file__).resolve().parents[2]))
src=root/'src/l1j/server/server/model/item/cooking'
test=root/'java117'
if test.exists():shutil.rmtree(test)

def put(rel,s):
    p=test/'src'/rel
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(textwrap.dedent(s).strip()+'\n')
put('l1j/server/server/model/item/cooking/CookingUseService.java',(src/'CookingUseService.java').read_text())
put('l1j/server/server/model/Instance/L1ItemInstance.java','''
package l1j.server.server.model.Instance;
public class L1ItemInstance {
 public final Template item; public int count=1; public L1ItemInstance(int id){item=new Template(id);} public Template getItem(){return item;}
 public String getNumberedName(int n){return "item"+item.id;} public static class Template {
 public final int id;public Template(int id){this.id=id;}public int getItemId(){return id;}}
}
''')
put('l1j/server/server/model/Instance/L1PcInstance.java','''
package l1j.server.server.model.Instance;
public class L1PcInstance {
 public int food=225,skill=0,duration,removed,countSet, id=17;
 public boolean effectFail; public int get_food(){return food;}
 public int getId(){return id;}public int getX(){return 333;}public int getY(){return 555;}
 public void removeSkillEffect(int skill){removed++;}
 public void setSkillEffect(int skill,int dur){countSet++;if(effectFail)throw new IllegalStateException("effect");this.skill=skill;duration=dur;}
}
''')
put('l1j/server/server/model/item/function/ItemTransactionActions.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemTransactionActions {
 public static Runnable pending;public static L1ItemInstance item; public static int attempted,commit,rollback;
 public static void consume(L1PcInstance pc,L1ItemInstance x,Runnable effect){pending=effect;item=x;attempted++;}
 public static void settle(boolean success){if(!success){rollback++;pending=null;return;}commit++;item.count--;Runnable r=pending;pending=null;r.run();}
}
''')
put('l1j/server/server/model/item/cooking/CookingRegistry.java','''
package l1j.server.server.model.item.cooking;
import l1j.server.server.model.Instance.*;
public class CookingRegistry {
 public enum Group {COOKING,DESSERT,NONE}
 public static class Def {public final int id,skillId,duration=90,icon=6;public final Group group;public final boolean food;
 public Def(int id,int skill,Group group,boolean food){this.id=id;skillId=skill;this.group=group;this.food=food;}
 public boolean isRequireFood225(){return food;}public Group getGroup(){return group;} public int getSkillId(){return skillId;}public int getDurationSec(){return duration;}public int getIconType(){return icon;}}
 public static int prev,started,ended;public static boolean deny;
 public static Def byItemId(int id){return id==10?new Def(id,101,Group.COOKING,false):id==11?new Def(id,111,Group.COOKING,true):null;}
 public static Def bySkillId(int id){return id==101?byItemId(10):null;}
 public static int currentSkill(L1PcInstance p,Group g){return prev;}
 public static boolean ensureStarted(L1PcInstance p,int id){started++;if(deny)return false;prev=id;return true;}
 public static boolean end(L1PcInstance p,int id){ended++;prev=0;return true;}
}
''')
for n,code in [('SC_EffectLocation','send(L1PcInstance p,int x,int y,int gfx)'),('SC_PacketBox','send(L1PcInstance p,int type,int icon,int duration)'),('SC_ServerMessage','send(L1PcInstance p,int code,String msg)')]:
    put(f'l1j/server/server/model/network/core/sc/packet/{n}.java',f'''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class {n} {{public static int sent;public static boolean fail;public static void {code} {{sent++;if(fail)throw new IllegalStateException("packet");}}}}
''')
put('l1j/server/server/model/item/cooking/Test117.java','''
package l1j.server.server.model.item.cooking;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.ItemTransactionActions;
import l1j.server.server.model.network.core.sc.packet.*;
public class Test117 {
 static int assertions;
 static void ok(boolean v,String msg){assertions++;if(!v)throw new AssertionError(msg);}
 static void reset(){CookingRegistry.prev=0;CookingRegistry.started=0;CookingRegistry.ended=0;CookingRegistry.deny=false;
 ItemTransactionActions.pending=null;SC_EffectLocation.sent=SC_PacketBox.sent=SC_ServerMessage.sent=0;
 SC_EffectLocation.fail=SC_PacketBox.fail=SC_ServerMessage.fail=false;}
 public static void main(String[] args){
  for(int mask=0;mask<8;mask++)for(int i=0;i<60;i++){
   reset();L1PcInstance pc=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(10);
   SC_EffectLocation.fail=(mask&1)!=0;SC_PacketBox.fail=(mask&2)!=0;SC_ServerMessage.fail=(mask&4)!=0;
   CookingUseService.useCookingItem(pc,item);
   ok(item.count==1 && pc.skill==0 && CookingRegistry.started==0,"effect before receipt");
   if(i%3==0){ItemTransactionActions.settle(false);ok(item.count==1 && pc.skill==0 && CookingRegistry.started==0,"rollback");}
   else {ItemTransactionActions.settle(true);ok(item.count==0 && pc.skill==101 && pc.duration==90000,"commit");
    ok(SC_EffectLocation.sent==1 && SC_PacketBox.sent==1 && SC_ServerMessage.sent==1,"all optional sends attempted");}
  }
  reset();L1PcInstance pc=new L1PcInstance();pc.food=200;L1ItemInstance item=new L1ItemInstance(11);
  CookingUseService.useCookingItem(pc,item);ok(ItemTransactionActions.pending==null,"food restriction should reject");
  reset();pc=new L1PcInstance();item=new L1ItemInstance(10);CookingRegistry.prev=91;
  CookingUseService.useCookingItem(pc,item);ok(CookingRegistry.ended==0,"replacement before commit");
  ItemTransactionActions.settle(true);ok(CookingRegistry.ended==1 && pc.removed==1 && pc.skill==101,"group replace after commit");
  reset();pc=new L1PcInstance();item=new L1ItemInstance(10);pc.effectFail=true;
  CookingUseService.useCookingItem(pc,item);try{ItemTransactionActions.settle(true);throw new AssertionError("missing gameplay failure");}catch(IllegalStateException expected){}
  ok(item.count==0 && CookingRegistry.ended==1,"failed gameplay must not be hidden");
  System.out.println("Phase117 source-compiled CookingUseService assertions="+assertions);
 }
}
''')
# type-compile the actual production source with mocked cross-module dependencies.
files=list((test/'src').rglob('*.java'))
(test/'bin').mkdir(parents=True,exist_ok=True)
p=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(test/'bin')]+[str(f) for f in files],capture_output=True,text=True)
if p.returncode: raise RuntimeError(p.stdout+'\n'+p.stderr)
p=subprocess.run(['java','-cp',str(test/'bin'),'l1j.server.server.model.item.cooking.Test117'],capture_output=True,text=True)
if p.returncode: raise RuntimeError(p.stdout+'\n'+p.stderr)
print(p.stdout.strip())
# Compile & exercise the production CookingRegistry.sendUpdates implementation
# extracted verbatim into a tiny type-correct fixture with each status packet mocked.
reg=(src/'CookingRegistry.java').read_text()
start=reg.index('    private static final java.util.logging.Logger COOKING_LOG =')
end=reg.index('\n}',start)
method=reg[start:end]
sub=root/'update117'; sub.mkdir(exist_ok=True)
body='''
import java.util.*;
public class Update117 {
 static final int UPD_HP=1,UPD_MP=2,UPD_SPMR=4,UPD_ATTR=8,UPD_STATUS=16,UPD_STATUS2=32;
 static int attempted;static int failMask;
 static void notice(int bit){attempted|=bit;if((failMask&bit)!=0)throw new IllegalStateException("packet");}
 public static class Pc {public int getCurrentHp(){return 1;}public int getMaxHp(){return 2;}public int getCurrentMp(){return 3;}public int getMaxMp(){return 4;}public boolean isInParty(){return false;}}
 public static class SC_HpUpdate {static void send(Pc p,int a,int b){notice(1);}}
 public static class SC_MpUpdate {static void send(Pc p,int a,int b){notice(2);}}
 public static class SC_SPMR {static void send(Pc p){notice(4);}}
 public static class SC_OwnCharAttrDef {static void send(Pc p){notice(8);}}
 public static class SC_OwnCharStatus {static void send(Pc p){notice(16);}}
 public static class SC_OwnCharStatus2 {static void send(Pc p){notice(32);}}
 public static class CookingRegistry {
 METHOD
 }
 public static void main(String[] args){int checks=0;for(int m=0;m<64;m++){failMask=m;attempted=0;CookingRegistry.sendUpdates(new Pc(),63);if(attempted!=63)throw new AssertionError("blocked later packet "+m);checks++;}System.out.println("Phase117 extracted production CookingRegistry.sendUpdates conditions="+checks);}
}
'''
# Replace real PC type in extracted source with fixture PC, preserving method body.
method=method.replace('L1PcInstance pc','Pc pc')
# real source has party refresh, supply as fixture;
body=body.replace('public boolean isInParty(){return false;}','public boolean isInParty(){return false;}public Party getParty(){return new Party();}')
body=body.replace(' public static class SC_HpUpdate', ' public static class Party {public void refreshPartyMemberStatus(Pc pc){}}\n public static class SC_HpUpdate')
(sub/'Update117.java').write_text(body.replace('METHOD',method))
p=subprocess.run(['javac','-encoding','UTF-8','--release','21',str(sub/'Update117.java')],capture_output=True,text=True)
if p.returncode: raise RuntimeError(p.stderr)
p=subprocess.run(['java','-cp',str(sub),'Update117'],capture_output=True,text=True)
if p.returncode: raise RuntimeError(p.stdout+'\n'+p.stderr)
print(p.stdout.strip())
