from pathlib import Path
import re,tempfile,subprocess
ROOT=Path(__file__).resolve().parents[2];F=ROOT/'src/l1j/server/server/model/item/function'
D=ROOT/'docs/item_domain_phase14_20261118'
originals={'MovementPotionEffects':(D/'MovementPotionEffects.java.baseline').read_text('utf-8')}
def typed_suite(name,classes,stubs,driver):
    with tempfile.TemporaryDirectory(prefix='rkern_phase14_'+name+'_') as td:
        t=Path(td);allfiles={**classes,**stubs,'Test14.java':driver}
        for p,c in allfiles.items():
            q=t/p;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(c,'utf-8')
        cmd=['javac','--release','21','-encoding','UTF-8','-d',str(t)]+[str(t/p) for p in allfiles]
        run=subprocess.run(cmd,capture_output=True,text=True)
        assert run.returncode==0, name+' typed Java21 compile failed: '+run.stderr[:10000]
        run=subprocess.run(['java','-cp',str(t),('l1j.server.server.model.item.function.Test14' if driver.startswith('package l1j.server.server.model.item.function;') else 'Test14')],capture_output=True,text=True)
        assert run.returncode==0,name+' runtime failed: '+run.stdout+'\n'+run.stderr[:10000]
        print(run.stdout.strip())

P='l1j/server/server/model/item/function/'
I='l1j/server/server/model/Instance/'
N='l1j/server/server/model/network/core/sc/packet/'
# ------------ movement potion original vs exact new production classes ------------
move_classes={P+'MovementPotionEffects.java':(F/'MovementPotionEffects.java').read_text()}
move_classes[P+'MovementPotionEffectsBaseline.java']=originals['MovementPotionEffects'].replace('MovementPotionEffects','MovementPotionEffectsBaseline')
# Legacy file contained dozens of unrelated wildcard imports. Trim imports in the
# temporary test copy only; retain its entire original production method bodies.
used_imports = list(dict.fromkeys(i for k,c in move_classes.items() if not k.endswith("MovementPotionEffectsBaseline.java") for i in re.findall(r'(?m)^import [^;]+;',c)))
base_move = move_classes[P+'MovementPotionEffectsBaseline.java']
move_classes[P+'MovementPotionEffectsBaseline.java'] = ('package l1j.server.server.model.item.function;\n'+'\n'.join(used_imports)+'\n'+base_move[base_move.index('/** MovementPotionEffectsBaseline'):])
staticnames=re.findall(r'(?m)^import static l1j\.server\.server\.model\.character\.skill\.L1SkillId\.(\w+);',originals['MovementPotionEffects'])
move_skill='package l1j.server.server.model.character.skill; public class L1SkillId {\n'+'\n'.join(' public static final int '+nm+' = '+str(i+200)+';' for i,nm in enumerate(staticnames))+'\n}'
move_stubs={
 'l1j/server/server/model/character/skill/L1SkillId.java':move_skill,
 'l1j/server/server/model/item/L1ItemId.java':'''package l1j.server.server.model.item;
 public final class L1ItemId { public static final int POTION_OF_HASTE_SELF=4001,B_POTION_OF_HASTE_SELF=4002,POTION_OF_EMOTION_BRAVERY=4003,B_POTION_OF_EMOTION_BRAVERY=4004; }''',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
 import java.util.*; import l1j.server.server.model.character.skill.L1SkillId;
 public class L1PcInstance {
 public final java.util.ArrayList<String> log=new java.util.ArrayList<>();
 public final java.util.HashMap<Integer,Integer> status = new java.util.HashMap<>();
 public int hasteEquipped,moveSpeed,braveSpeed;public boolean drink=true;
 public boolean hasSkillEffect(int k){return status.containsKey(k);}
 public int getId(){return 234;}
 public int getHasteItemEquipped(){return hasteEquipped;}
 public void setDrink(boolean b){drink=b;log.add("drink="+b);}
 public void killSkillEffectTimer(int k){log.add("kill="+k);status.remove(k);}
 public int getSkillEffectTimeSec(int k){return status.getOrDefault(k,0);}
 public void setMoveSpeed(int v){moveSpeed=v;log.add("move="+v);}
 public void setSkillEffect(int k,int ms){status.put(k,ms / 1000);log.add("effect="+k+"/"+ms);}
 public void setBraveSpeed(int v){braveSpeed=v;log.add("brave="+v);}
 public int getBraveSpeed(){return braveSpeed;}
 public void remove_elf_second_brave(){log.add("elfclear");}
 }''',
 P+'CreatureItemActions.java':'''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.L1PcInstance; public class CreatureItemActions {public static void cancelAbsoluteBarrier(L1PcInstance p){p.log.add("barrier");}}'''
}
for packet in ('SC_SkillSound','SC_ServerMessage','SC_SkillHaste','SC_SkillBrave'):
    move_stubs[N+packet+'.java']=f'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class {packet} {{public static void send(L1PcInstance p,Object... a){{p.log.add("{packet}.send"+java.util.Arrays.toString(a));}}
public static void broadcast(L1PcInstance p,Object... a){{p.log.add("{packet}.broadcast"+java.util.Arrays.toString(a));}} }}'''
move_test='''import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.function.*;
public class Test14 {
  static void run(int kind,boolean old,L1PcInstance p,int id){
    if(old){
      if(kind==0)MovementPotionEffectsBaseline.useGreenPotion(p,id);
      else if(kind==1)MovementPotionEffectsBaseline.useBravePotion(p,id);
      else MovementPotionEffectsBaseline.useFruit(p,id);
    }else{
      if(kind==0)MovementPotionEffects.useGreenPotion(p,id);
      else if(kind==1)MovementPotionEffects.useBravePotion(p,id);
      else MovementPotionEffects.useFruit(p,id);
    }
  }
  static void check(int kind,int id,int mask,int seed){
   L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();
   int[] keys={71,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224,225,226,227,228,229,230,231,232,233,234,235};
   for(int i=0;i<keys.length;i++) if((mask&(1<<(i%24)))!=0){ a.status.put(keys[i],seed); b.status.put(keys[i],seed); }
   a.hasteEquipped=b.hasteEquipped=(seed==7200?1:0);
   run(kind,true,a,id);run(kind,false,b,id);
   if(!a.log.equals(b.log)||!a.status.equals(b.status)||a.moveSpeed!=b.moveSpeed||a.braveSpeed!=b.braveSpeed||a.drink!=b.drink)throw new AssertionError("potion "+kind+" id="+id+" mask="+mask+" seed="+seed+" old="+a.log+" new="+b.log);
  }
  public static void main(String[] argv){
   int[] ids={0,4001,4002,4003,4004,7006,40018,41342,140018,41338,30158,40039,40040,30067,40030,41261,41262,41268,41269,41271,41272,41273,30073,41415,712,40068,30076,140068,210110,40031,30075,40733,210036,30077,713,50000};
   java.util.Random r=new java.util.Random(0x14);
   int cases=0;
   for(int kind=0;kind<3;kind++)for(int id:ids)for(int i=0;i<160;i++){
      int mask=i<32 ? (1<<i) : r.nextInt(); int seed=(i%5==0)?7200:((i%5==1)?2:300);
      check(kind,id,mask,seed);cases++;
   }
   System.out.println("PASS movement potion Java21 production vs original: "+cases+" scenarios, exact effects/packets/skill mutation");
  }
}'''
# Baseline has package-private class; direct external test cannot call it. Move test into production package.
move_test=move_test.replace('public class Test14','public class Test14')
# change baseline and new MovementPotionEffects to public only in test build, since actual class remains package private
for k in (P+'MovementPotionEffects.java',P+'MovementPotionEffectsBaseline.java'):
    move_classes[k]=move_classes[k].replace('final class MovementPotionEffects','public final class MovementPotionEffects')
typed_suite('movement',move_classes,move_stubs,move_test)

