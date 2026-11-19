#!/usr/bin/env python3
"""Item-only phase66 source parity checks and Java21 extracted-method execution tests.
No real DB or server dependencies; this is not a full game compile.
"""
import json, re, subprocess, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FUN = ROOT/'src/l1j/server/server/model/item/function'
REFERENCE = json.loads((Path(__file__).parent/'BASELINE_REFERENCE.json').read_text('utf-8'))

def get_method(source,name):
    m = re.search(r'(?:private |public )?static (?:void|double) '+re.escape(name)+r'\(',source)
    assert m, f'missing {name}'
    i = source.index('{',m.start()); d = 0
    for j in range(i,len(source)):
        if source[j]=='{':d+=1
        elif source[j]=='}':
            d-=1
            if d==0:return source[m.start():j+1]
    raise AssertionError('brace mismatch '+name)

def literals(name,field):
    s = (FUN/(name+'.java')).read_text('utf-8')
    m = re.search(r'private static final (?:int|double|String)\[\] '+field+r'\s*=\s*\{(.*?)\};',s,re.S)
    assert m, f'{name} static field {field}'
    items=[x.strip() for x in m.group(1).split(',') if x.strip()]
    assert items==REFERENCE['tables'][name]['values'],f'table mismatch: {name}'
    return s, m.group(1)

names={
    'BravePotion':'CONFLICTING_BRAVE_SKILLS',
    'TargetPolymorphEffects':'POLYMORPH_IDS',
    'PetAndSummonItems':'SUMMON_IDS',
    'ProtectedClanJoinItemUse':'PROTECTED_CLAN_NAMES',
    'EquipmentExperienceBonuses':'ARKA_900116_BY_ENCHANT',
}
texts={}
for name,field in names.items():
    texts[name],_ = literals(name,field)
    assert texts[name].count('private static final ' +REFERENCE['tables'][name]['type']+'[] '+field)==1

# Local arrays in all five execution branches must be gone.
for name,term in [('BravePotion','int[] skillsToCheck'),('TargetPolymorphEffects','int[] polyArray'),
                  ('PetAndSummonItems','int[] summon_id'),('ProtectedClanJoinItemUse','String[] clan_id'),
                  ('EquipmentExperienceBonuses','final double[] t')]:
    assert term not in texts[name],f'remaining per-use allocation in {name}'

# Preserve each RNG draw; moving lookup data may never change generator calls.
assert texts['TargetPolymorphEffects'].count('_random.nextInt(POLYMORPH_IDS.length)') == 1
assert texts['TargetPolymorphEffects'].count('_random.nextInt(100)') == 1
assert texts['PetAndSummonItems'].count('CommonUtil.random(3)') == 1
assert texts['ProtectedClanJoinItemUse'].count('CommonUtil.random(1)') == 1

old_b=REFERENCE['methods']['oldBrave']
new_b=get_method(texts['BravePotion'],'clearConflictingBraveEffects')
old_e=REFERENCE['methods']['oldExp']
new_e=get_method(texts['EquipmentExperienceBonuses'],'calcArkaDeltaIfAny')
# Exact production-extracted bodies are compiled below, not reimplemented equivalents.

def table(t):
    v=REFERENCE['tables'][t]['values']; typ=REFERENCE['tables'][t]['type']
    return 'new '+typ+'[] {'+', '.join(v)+'}'

java='''import java.util.*;
public class ItemOnly66Parity {
  static final class L1PcInstance {
    final StringBuilder log = new StringBuilder();
    final boolean[] skills = new boolean[22000];
    int speed = 1;
    int getId() {return 77;}
    boolean hasSkillEffect(int id) {return skills[id];}
    void killSkillEffectTimer(int id) {log.append("kill").append(id).append(';');skills[id]=false;}
    void setBraveSpeed(int v) {speed=v;log.append("speed").append(v).append(';');}
  }
  static final class SC_SkillBrave {
    static void send(L1PcInstance pc,int a,int b,int c) {pc.log.append("send").append(b).append('/').append(c).append(';');}
    static void broadcast(L1PcInstance pc,int a,int b,int c) {pc.log.append("broadcast").append(b).append('/').append(c).append(';');}
  }
  static final class L1ItemInstance {
    final int id, enchant;
    L1ItemInstance(int id,int ench){this.id=id;this.enchant=ench;}
    int getItemId(){return id;}
    int getEnchantLevel(){return enchant;}
  }
  static final class BonusMap {
    double getOrZero(int k) {
      return switch(k){case 900033,9961 -> 0.2;
        case 555001,555002,555003,666001,666002,666003 -> 0.1;
        default -> 0.0;};
    }
  }
  static final class OldBrave {
'''+old_b+'''
  }
  static final class NewBrave {
    static final int[] CONFLICTING_BRAVE_SKILLS = '''+table('BravePotion')+''';
'''+new_b+'''
  }
  static final class OldExp {
    static final BonusMap ARKA_FIXED = new BonusMap();
'''+old_e+'''
  }
  static final class NewExp {
    static final BonusMap ARKA_FIXED = new BonusMap();
    static final double[] ARKA_900116_BY_ENCHANT = '''+table('EquipmentExperienceBonuses')+''';
'''+new_e+'''
  }
  static final int[] oldPoly = '''+table('TargetPolymorphEffects')+''';
  static final int[] newPoly = '''+table('TargetPolymorphEffects')+''';
  static final int[] oldSummons = '''+table('PetAndSummonItems')+''';
  static final int[] newSummons = '''+table('PetAndSummonItems')+''';
  static final String[] oldClan = '''+table('ProtectedClanJoinItemUse')+''';
  static final String[] newClan = '''+table('ProtectedClanJoinItemUse')+''';
  static void yes(boolean v) {if(!v) throw new AssertionError();}
  public static void main(String[] args) {
    int braveCases=0, expCases=0, rollCases=0;
    final int[] ids={900033,9961,555001,555003,666002,900025,900184,900116,900130,123,0,-1};
    for (int id:ids) for (int e=-20;e<=80;e++) {
      L1ItemInstance item=new L1ItemInstance(id,e);
      long a=Double.doubleToRawLongBits(OldExp.calcArkaDeltaIfAny(item));
      long b=Double.doubleToRawLongBits(NewExp.calcArkaDeltaIfAny(item));
      yes(a==b);expCases++;
    }
    final int[] skills={1000,1016,52,101,177,178,179,155,20079};
    for(int mask=0;mask<512;mask++) for (int skip=0;skip<=skills.length;skip++) {
      L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();
      for(int i=0;i<9;i++) {a.skills[skills[i]]=(mask&(1<<i))!=0; b.skills[skills[i]]=a.skills[skills[i]];}
      int omit=skip==skills.length?-1:skills[skip];
      OldBrave.clearConflictingBraveEffects(a,omit);
      NewBrave.clearConflictingBraveEffects(b,omit);
      yes(a.log.toString().equals(b.log.toString()) && a.speed==b.speed);
      yes(Arrays.equals(a.skills,b.skills));braveCases++;
    }
    Random a=new Random(1258329),b=new Random(1258329);
    for(int i=0;i<100000;i++) {
      int pa=oldPoly[a.nextInt(oldPoly.length)],pb=newPoly[b.nextInt(newPoly.length)]; yes(pa==pb);
      // The summon code intentionally retains the original random(3) bound.
      int sa=oldSummons[a.nextInt(3)],sb=newSummons[b.nextInt(3)];yes(sa==sb);
      yes(oldClan[a.nextInt(1)].equals(newClan[b.nextInt(1)]));rollCases+=3;
    }
    yes(a.nextInt()==b.nextInt());
    System.out.println("PASS Java21 extracted-method parity brave="+braveCases+" exp="+expCases+" deterministic rolls="+rollCases);
  }
}
'''
with tempfile.TemporaryDirectory(prefix='itemonly66_') as tmp:
    p=Path(tmp)/'ItemOnly66Parity.java';p.write_text(java,'utf-8')
    cmd=['javac','--release','21','-encoding','UTF-8','-d',tmp,str(p)]
    subprocess.run(cmd,check=True)
    result=subprocess.run(['java','-cp',tmp,'ItemOnly66Parity'],check=True,capture_output=True,text=True)
    print(result.stdout.strip())
print('PASS item-only static lookup tables, unchanged RNG invocation and 5 no-per-use-array checks')
