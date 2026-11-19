#!/usr/bin/env python3
from pathlib import Path
import subprocess,shutil,hashlib,tempfile
D=Path(__file__).resolve().parent
OLD=D/'baseline'
NEW=D.parents[1]/'src/l1j/server/server/model/item/function'
TEST=Path(tempfile.mkdtemp(prefix='rkern_phase18_poly_'))
if TEST.exists():shutil.rmtree(TEST)
PKG='l1j.server.server.model.item.function'
pre=PKG.replace('.','/')

def source(root, rel, content):
 p=root/'src'/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,'utf8')
def shared(root):
 source(root,'l1j/server/server/model/Instance/L1PcInstance.java', '''package l1j.server.server.model.Instance;
public final class L1PcInstance {
  public final int flags,sex;
  public int poly;
  public L1PcInstance(int flags,int sex) {this.flags=flags;this.sex=sex;}
  public int get_sex(){return sex;}
  public boolean isCrown(){return (flags&1)!=0;}
  public boolean isKnight(){return (flags&2)!=0;}
  public boolean isElf(){return (flags&4)!=0;}
  public boolean isWizard(){return (flags&8)!=0;}
  public boolean isDarkelf(){return (flags&16)!=0;}
  public boolean isDragonknight(){return (flags&32)!=0;}
  public boolean isBlackwizard(){return (flags&64)!=0;}
  public boolean isWarrior(){return (flags&128)!=0;}
  public Inventory getInventory(){return new Inventory(sex);}
  public static final class Inventory {
   private final int sex; Inventory(int s){sex=s;}
   public boolean checkItem(int id,int count){return sex==1;}
  }
}
''')
 source(root,'l1j/server/server/model/L1PolyMorph.java', '''package l1j.server.server.model;
import l1j.server.server.model.Instance.L1PcInstance;
public final class L1PolyMorph {
 public static final int MORPH_BY_ITEMMAGIC=12;
 public static void doPoly(L1PcInstance pc,int polyId,int duration,int how,boolean flag){pc.poly=polyId;}
}
''')
 source(root,'l1j/server/server/model/L1InventoryTransaction.java', '''package l1j.server.server.model; public final class L1InventoryTransaction {}''')
 source(root,pre+'/PackageRewardStaging.java', '''package l1j.server.server.model.item.function;
import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
final class PackageRewardStaging {
 static void stageReward(L1InventoryTransaction tx,ArrayList<String> messages,int id,int count,int a,int b,int c,boolean d){messages.add(id+":"+count+":"+a+":"+b+":"+c+":"+d);}
 static void stageSealedReward(L1InventoryTransaction tx,ArrayList<String> messages,int id,int count,int a,int b,int c,boolean d){messages.add("seal:"+id+":"+count);}
}
''')
 source(root,pre+'/CompareMain.java','''package l1j.server.server.model.item.function;
import java.util.ArrayList;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
public final class CompareMain {
 public static void main(String[] args) {
  int[] polys={210097,210098,210099,210100,210101,210102,210103,210116,210117,42};
  int[] rewards={714,715,716,717,721,722,12};
  L1InventoryTransaction tx=new L1InventoryTransaction();
  int n=0;
  for(int itemId:polys) for(int flags=0;flags<256;flags++) for(int sex=0;sex<2;sex++) {
    L1PcInstance pc=new L1PcInstance(flags,sex);
    LevelPolymorphScrollEffects.useLevelPolyScroll(pc,itemId);
    System.out.println("P:"+itemId+":"+flags+":"+sex+":"+pc.poly); n++;
  }
  for(int itemId:rewards) for(int flags=0;flags<256;flags++) for(int has=0;has<2;has++) {
    L1PcInstance pc=new L1PcInstance(flags,has);
    ArrayList<String> messages=new ArrayList<>();
    LevelMilestonePackages.stage(pc,itemId,tx,messages);
    System.out.println("R:"+itemId+":"+flags+":"+has+":"+messages); n++;
  }
  System.err.println("CASES="+n);
 }
}
''')

def execute(root,actual):
 shared(root)
 names=['LevelPolymorphScrollEffects','LevelMilestonePackages']
 if not actual:names += [f'Level{i}PolymorphForm' for i in (30,40,52,55,60,65,70,75,80)]+[f'Level{i}AchievementReward' for i in (82,84,86,88,89,90)]
 for n in names:source(root,pre+'/'+n+'.java',(NEW if actual else OLD).joinpath(n+'.java').read_text('utf8'))
 files=list((root/'src').rglob('*.java'));classes=root/'classes';classes.mkdir()
 subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(classes),*map(str,files)],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 out=subprocess.run(['java','-cp',str(classes),PKG+'.CompareMain'],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 print('TYPED',root.name,'bytes',len(out.stdout),out.stderr.decode('utf-8').strip())
 return out.stdout
old=execute(TEST/'baseline',False)
new=execute(TEST/'phase18',True)
assert old==new,'java typed behavioral mismatch'
print('PASS POLYMORPH/ACHIEVEMENT TYPED JAVA21 EQUALITY 8704 SCENARIOS SHA256',hashlib.sha256(new).hexdigest())
