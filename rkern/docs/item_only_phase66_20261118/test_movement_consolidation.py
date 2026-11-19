#!/usr/bin/env python3
"""Compare original 65 movement potion production methods against phase66 consolidation."""
import json,re,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'src/l1j/server/server/model/item/function'
old=json.loads((Path(__file__).parent/'BASELINE_MOVEMENT_METHODS.json').read_text('utf-8'))
new=(F/'MovementPotionConsumables.java').read_text('utf-8')

def get_method(name):
    m=re.search(r'(?m)(?:private )?static boolean '+re.escape(name)+r'\(',new)
    assert m, name
    a=new.index('{',m.start());dep=0
    for i in range(a,len(new)):
        if new[i]=='{':dep+=1
        elif new[i]=='}':
            dep-=1
            if dep==0:return new[m.start():i+1]
    raise AssertionError('brace '+name)
for method,baseline in [('useGreen','GreenMovementPotionUse'),('useBravery','BraveryMovementPotionUse'),('useFruitPotion','FruitMovementPotionUse')]:
    # Compare exact bodies, not only result: all potion side effects remain in order.
    nm=get_method(method)
    om=old[baseline]
    assert nm[nm.index('{'):] == om[om.index('{'):], f'body changed: {method}'
assert not any((F/(name+'.java')).exists() for name in ('GreenMovementPotionUse','BraveryMovementPotionUse','FruitMovementPotionUse'))
for typ in ('GreenMovementPotionUse','BraveryMovementPotionUse','FruitMovementPotionUse'):
    assert (typ + ".use(") not in new

P='l1j/server/server/model/item/function/'
I='l1j/server/server/model/Instance/'
N='l1j/server/server/model/network/core/sc/packet/'

def old_nested(name,method):return 'static final class '+name+' { '+method+' }'
old_router=old['MovementPotionConsumables'].replace('GreenMovementPotionUse.use(', 'OldGreen.use(').replace('BraveryMovementPotionUse.use(', 'OldBrave.use(').replace('FruitMovementPotionUse.use(', 'OldFruit.use(')
legacy='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import static l1j.server.server.model.item.function.PotionEffects.useGreenPotion;
import static l1j.server.server.model.item.function.PotionEffects.useBravePotion;
import static l1j.server.server.model.item.function.PotionEffects.useFruit;
public final class TestItem66Movement {
'''+old_nested('OldRouter',old_router)+old_nested('OldGreen',old['GreenMovementPotionUse'])+old_nested('OldBrave',old['BraveryMovementPotionUse'])+old_nested('OldFruit',old['FruitMovementPotionUse'])+'''
static void check(boolean condition){if(!condition)throw new AssertionError();}
static int checks=0;
static void compare(int id,int classMask,int failAt){
    L1PcInstance a=new L1PcInstance(classMask,failAt), b=new L1PcInstance(classMask,failAt);
    L1ItemInstance obj=new L1ItemInstance(id);
    boolean oldResult=false,newResult=false;
    String oldException="",newException="";
    try {oldResult=OldRouter.use(a,obj,id);}catch(RuntimeException ex){oldException=ex.getMessage();}
    try {newResult=MovementPotionConsumables.use(b,obj,id);}catch(RuntimeException ex){newException=ex.getMessage();}
    if(oldResult!=newResult||!oldException.equals(newException)||!a.log.toString().equals(b.log.toString()))
        throw new AssertionError("id="+id+" mask="+classMask+" fail="+failAt+" old="+a.log+" new="+b.log+" exception="+oldException+" vs "+newException);
    checks++;
}
public static void main(String... args){
  int[] ids={40030,40013,7006,40018,40039,40040,41338,41261,41262,41268,41269,41271,41272,41273,41342,30067,140013,140018,30158,875640293,
    40014,140014,41415,30073,40068,140068,210110,30076,712,40031,30075,40733,713,210036,30077,0,1,999999};
  for(int id:ids)for(int classes=0;classes<32;classes++)for(int fail=0;fail<3;fail++)compare(id,classes,fail);
  System.out.println("PASS Java21 original 4 classes vs consolidated movement potion: "+checks+" scenarios, exact effects/consumption/exceptions");
}
}
'''
files={
  P+'MovementPotionConsumables.java':new,
  P+'TestItem66Movement.java':legacy,
  I+'L1ItemInstance.java':'''package l1j.server.server.model.Instance;public class L1ItemInstance {public final int id;public L1ItemInstance(int id){this.id=id;}}''',
  I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
public class L1PcInstance {
  public final StringBuilder log=new StringBuilder();public final int classes,failAt;
  public L1PcInstance(int classes,int failAt){this.classes=classes;this.failAt=failAt;}
  public boolean isKnight(){return (classes&1)!=0;}public boolean isWarrior(){return (classes&2)!=0;}
  public boolean isCrown(){return (classes&4)!=0;}public boolean isElf(){return (classes&8)!=0;}
  public boolean isBlackwizard(){return (classes&16)!=0;}
  public Inventory getInventory(){return inv;}public final Inventory inv=new Inventory();
  public class Inventory {public void removeItem(L1ItemInstance item,int count){log.append("remove:").append(item.id).append(':').append(count).append(';');if(failAt==2)throw new IllegalStateException("inventory failure");}}
}''',
  P+'PotionEffects.java':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
public final class PotionEffects {
  static void event(L1PcInstance p,String name,int id){p.log.append(name).append(':').append(id).append(';');if(p.failAt==1)throw new IllegalStateException("effect failure");}
  public static void useGreenPotion(L1PcInstance p,int id){event(p,"green",id);}
  public static void useBravePotion(L1PcInstance p,int id){event(p,"brave",id);}
  public static void useFruit(L1PcInstance p,int id){event(p,"fruit",id);}
}''',
  N+'SC_ServerMessage.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_ServerMessage {public static void send(L1PcInstance p,int id){p.log.append("msg:").append(id).append(';');}}'''
}
with tempfile.TemporaryDirectory(prefix='item66_movement_') as td:
    t=Path(td)
    for name,code in files.items():
        q=t/name;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(code,'utf-8')
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',td]+[str(t/k) for k in files],check=True)
    run=subprocess.run(['java','-cp',td,'l1j.server.server.model.item.function.TestItem66Movement'],check=True,capture_output=True,text=True)
    print(run.stdout.strip())
print('PASS movement helper method bodies byte-identical after consolidation')
