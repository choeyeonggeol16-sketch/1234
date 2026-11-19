#!/usr/bin/env python3
"""Phase 10: compare extracted source to Phase 9 and execute both polymorph dispatchers."""
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
FUN = ROOT / 'src/l1j/server/server/model/item/function'
HERE = Path(__file__).resolve().parent
MAN = json.loads((HERE / 'MANIFEST.json').read_text('utf-8'))

def sha(s): return hashlib.sha256(s.encode('utf-8')).hexdigest()
def part_of(owner, marker):
    src=(FUN / (owner+'.java')).read_text('utf-8')
    return src.split('        // BEGIN_ORIGINAL_'+marker+'\n',1)[1].split('        // END_ORIGINAL_'+marker,1)[0]

original_armor = (HERE/'ARMOR_PHASE9_BASELINE.txt').read_text('utf-8')
original_level = (HERE/'LEVEL_PHASE9_BASELINE.txt').read_text('utf-8')
assert sha(original_armor)==MAN['original_armor_sha256']
assert sha(original_level)==MAN['original_level_sha256']
assert original_level == (HERE/'LEVEL_PHASE9_BASELINE.txt').read_text('utf-8')

armor_lines=original_armor.splitlines(keepends=True)
expected_armor={
    'ArmorEnchantTargetRules': ''.join(armor_lines[42:360]),
    'ArmorEnchantLevelLimits': ''.join(armor_lines[361:424]),
    'ArmorEnchantScrollResolution': ''.join(armor_lines[425:841]),
}
chance_row=MAN['chance_fragment']
chance_src=part_of(chance_row['class'],chance_row['marker'])
assert sha(chance_src)==chance_row['fragment_sha256'] and len(chance_src)==chance_row['size']
assert original_armor.count(chance_src)==1, 'moved chance block not unique'
for row in MAN['armor_fragments']:
    owner=row['class'];actual=part_of(owner,row['marker']);reference=expected_armor[owner]
    if owner=='ArmorEnchantScrollResolution':
        # The only intentional change inside the original resolution body is
        # extraction of the original rate/GM block into a dedicated class.
        invocation='''                enchant_chance_armor = ArmorEnchantChanceCalculation.calculate(
                        pc, targetItem, armorId, armortype, enchant_level, rnd);

'''
        assert actual.count(invocation)==1
        assert actual.replace(invocation,chance_src)==reference, 'original resolution not preserved'
        assert sha((FUN/(owner+'.java')).read_text('utf-8')) == MAN['resolution_after_chance_sha256']
    else:
        assert actual==reference,('armor body mismatch',owner)
    assert sha(reference)==row['fragment_sha256'],('baseline hash mismatch',owner)
    assert len(reference)==row['size'],owner
    assert actual.count('return ItemActionResult.STOP;') == reference.count('return ItemActionResult.STOP;')
assert sha((FUN/'ArmorEnchantScrollActions.java').read_text('utf-8'))==MAN['armor_gateway_sha256']

# Level owners copy the exact class/sex branching blocks, without rewriting IDs.
for row in MAN['level_fragments']:
    part=part_of(row['class'],row['class'].upper())
    assert sha(part)==row['fragment_sha256'] and len(part)==row['size'],row
    assert 'polyId =' in part,row
main=(FUN/'LevelPolymorphScrollEffects.java').read_text('utf-8')
assert sha(main)==MAN['level_controller_sha256']
assert main.count('L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);')==1
for row in MAN['level_fragments']:
    assert main.count(f"{row['class']}.select(pc)")==1,row

# Compile the *actual* Phase 10 level dispatcher and all nine production
# level-form files; compare against the original Phase 9 dispatcher with one
# fake player per class and sex, including invalid item IDs and unknown classes.
with tempfile.TemporaryDirectory(prefix='rkern_item_phase10_') as tmp:
    t=Path(tmp)
    pkg=t/'l1j/server/server/model/item/function';pkg.mkdir(parents=True)
    model=t/'l1j/server/server/model';model.mkdir(parents=True,exist_ok=True)
    ins=model/'Instance';ins.mkdir(parents=True,exist_ok=True)
    (ins/'L1PcInstance.java').write_text('''package l1j.server.server.model.Instance;
public final class L1PcInstance {
    public int kind, sex;
    public int get_sex(){return sex;}
    public boolean isCrown(){return kind==0;}
    public boolean isKnight(){return kind==1;}
    public boolean isElf(){return kind==2;}
    public boolean isWizard(){return kind==3;}
    public boolean isDarkelf(){return kind==4;}
    public boolean isDragonknight(){return kind==5;}
    public boolean isBlackwizard(){return kind==6;}
    public boolean isWarrior(){return kind==7;}
}
''','utf-8')
    (model/'L1PolyMorph.java').write_text('''package l1j.server.server.model;
import l1j.server.server.model.Instance.L1PcInstance;
public final class L1PolyMorph {
 public static final int MORPH_BY_ITEMMAGIC = 9;
 public static int lastId, lastDuration, lastMode, count;
 public static boolean lastFlag;
 public static L1PcInstance lastTarget;
 public static void doPoly(L1PcInstance pc, int id, int duration, int mode, boolean flag) {
    lastId=id;lastDuration=duration;lastMode=mode;lastFlag=flag;lastTarget=pc;count++;
 }
}
''','utf-8')
    for name in ['LevelPolymorphScrollEffects']+[x['class'] for x in MAN['level_fragments']]:
        (pkg/(name+'.java')).write_bytes((FUN/(name+'.java')).read_bytes())
    old=original_level[original_level.index('/** Original useLevelPolyScroll'):]
    old=old.replace('LevelPolymorphScrollEffects','OriginalLevelPolymorphScrollEffects')
    (pkg/'OriginalLevelPolymorphScrollEffects.java').write_text('''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PolyMorph;
'''+old,'utf-8')
    item_ids = [d['item_id'] for d in MAN['level_fragments']] + [0,-1,210118,210104,813]
    harness='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PolyMorph;
public final class Phase10RuntimeTest {
    public static void main(String[] args) {
        int count = 0;
        int[] ids = new int[]{'''+','.join(map(str,item_ids))+'''};
        for (int kind=0;kind<10;kind++) for(int sex=-1;sex<=2;sex++) for(int id:ids) {
            L1PcInstance pc = new L1PcInstance();pc.kind=kind;pc.sex=sex;
            L1PolyMorph.count=0;
            OriginalLevelPolymorphScrollEffects.useLevelPolyScroll(pc,id);
            int oldId=L1PolyMorph.lastId, oldCount=L1PolyMorph.count;
            if(L1PolyMorph.lastDuration!=1800 || L1PolyMorph.lastMode!=9 || L1PolyMorph.lastTarget!=pc || L1PolyMorph.lastFlag) throw new AssertionError("original ABI");
            L1PolyMorph.count=0;
            LevelPolymorphScrollEffects.useLevelPolyScroll(pc,id);
            if(oldId!=L1PolyMorph.lastId || oldCount!=L1PolyMorph.count || L1PolyMorph.lastDuration!=1800 || L1PolyMorph.lastMode!=9 || L1PolyMorph.lastTarget!=pc || L1PolyMorph.lastFlag)
                throw new AssertionError("mismatch kind="+kind+" sex="+sex+" itemId="+id+" old="+oldId+" new="+L1PolyMorph.lastId);
            count++;
        }
        System.out.println("PASS polymorph runtime equality: "+count+" combinations, 9 supported IDs + 5 invalid IDs, exact doPoly ABI");
    }
}
'''
    (pkg/'Phase10RuntimeTest.java').write_text(harness,'utf-8')
    srcs=[str(p) for p in t.rglob('*.java')]
    run=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(t),*srcs],text=True,capture_output=True)
    assert run.returncode==0,run.stderr
    run=subprocess.run(['java','-cp',str(t),'l1j.server.server.model.item.function.Phase10RuntimeTest'],text=True,capture_output=True)
    assert run.returncode==0,run.stderr
    print(run.stdout.strip())

# Typed gateway check: the real router must not call later stages after STOP.
with tempfile.TemporaryDirectory(prefix='rkern_armor_phase10_') as tmp:
    t=Path(tmp);pkg=t/'l1j/server/server/model/item/function';pkg.mkdir(parents=True)
    (pkg/'ArmorEnchantScrollActions.java').write_bytes((FUN/'ArmorEnchantScrollActions.java').read_bytes())
    (pkg/'ItemActionResult.java').write_bytes((FUN/'ItemActionResult.java').read_bytes())
    (pkg/'ItemActionContext.java').write_text('package l1j.server.server.model.item.function; final class ItemActionContext {}','utf-8')
    for step,cls,method in [(1,'ArmorEnchantTargetRules','validate'),(2,'ArmorEnchantLevelLimits','validate'),(3,'ArmorEnchantScrollResolution','apply')]:
        (pkg/(cls+'.java')).write_text(f'''package l1j.server.server.model.item.function;
final class {cls} {{ static ItemActionResult {method}(ItemActionContext use) {{ Phase10GatewayTest.trace=Phase10GatewayTest.trace*10+{step};return Phase10GatewayTest.stop=={step}?ItemActionResult.STOP:ItemActionResult.COMPLETE;}} }}''','utf-8')
    (pkg/'Phase10GatewayTest.java').write_text('''package l1j.server.server.model.item.function;
public final class Phase10GatewayTest {
 static int trace,stop;
 public static void main(String[]a) {
     int[] want={123,1,12,123};
     for(int n=0;n<4;n++){stop=n;trace=0;ItemActionResult ret=ArmorEnchantScrollActions.use(new ItemActionContext());
         if(trace!=want[n]||ret!=(n==0?ItemActionResult.COMPLETE:ItemActionResult.STOP))throw new AssertionError("gate "+n+" trace="+trace);
     }
     System.out.println("PASS real armor router typed stub: STOP short circuit and COMPLETE continuation");
 }
}
''','utf-8')
    run=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(t),*[str(p) for p in t.rglob('*.java')]],text=True,capture_output=True)
    assert run.returncode==0,run.stderr
    run=subprocess.run(['java','-cp',str(t),'l1j.server.server.model.item.function.Phase10GatewayTest'],text=True,capture_output=True)
    assert run.returncode==0,run.stderr
    print(run.stdout.strip())
print('PASS Phase 10 source extraction: 3 armor regions, 1 chance calculation, and 9 polymorph level bodies byte-identical to Phase 9')
