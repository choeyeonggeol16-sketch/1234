#!/usr/bin/env python3
"""Compile actual DollEffectItems and extracted effect handlers, execute 4,129 skill masks, compare to Phase7 fixture."""
from pathlib import Path
import hashlib,re,sys,subprocess,tempfile,zipfile
ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'src/l1j/server/server/model/item/function'
AUDIT=Path(__file__).parent
BASELINE=None
if '--baseline' in sys.argv:
    BASELINE=Path(sys.argv[sys.argv.index('--baseline')+1])

skill_src='\n'.join((F/n).read_text('utf8') for n in ('DollVitalDefenseEffectRemoval.java','DollAttributeEffectRemoval.java','DollCombatEffectRemoval.java','DollResistanceEffectRemoval.java'))
skills=list(dict.fromkeys(re.findall(r'L1SkillId\.(\w+)',skill_src)))
assert len(skills)==31
mod1=sorted(set(re.findall(r'pc\.(add\w+)\(',skill_src)))
mod2=sorted(set(re.findall(r'pc\.get(?:Resistance|Ability|AC)\(\)\.(add\w+)\(',skill_src)))
values=sorted(set(re.findall(r'eKind\.(\w+)',skill_src)))

# All these doubles are deterministic recorders, and the REAL production source is compiled.
def put(root,path,contents):
    p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(contents,'utf8')
def generate(tmp,orig=False):
    put(tmp,'l1j/server/server/model/character/skill/L1SkillId.java','package l1j.server.server.model.character.skill;\npublic final class L1SkillId {\n'+''.join(f' public static final int {name}={idx};\n' for idx,name in enumerate(skills))+'}\n')
    put(tmp,'l1j/server/server/utils/eKind.java','package l1j.server.server.utils; public enum eKind { '+','.join(values)+' }\n')
    put(tmp,'l1j/server/server/model/item/function/CreatureItemActions.java','package l1j.server.server.model.item.function; public final class CreatureItemActions {}\n')
    pc=['package l1j.server.server.model.Instance;','import l1j.server.server.utils.eKind;',
        'public final class L1PcInstance {','public static final StringBuilder LOG=new StringBuilder();',
        'public static void record(String x){LOG.append(x).append("|");}',
        'private int skills; public L1PcInstance(int mask){skills=mask;}',
        'public boolean hasSkillEffect(int id){record("has:"+id);return (skills&(1<<id))!=0;}',
        'public void removeSkillEffect(int id){record("remove:"+id);skills&=~(1<<id);}',
        'private int maxHp=1000,maxMp=600;',
        'public int getCurrentHp(){record("currentHp");return 777;}',
        'public int getCurrentMp(){record("currentMp");return 444;}',
        'public int getMaxHp(){record("maxHp");return maxHp;}',
        'public int getMaxMp(){record("maxMp");return maxMp;}',
        'public void addMaxHp(int x){maxHp+=x; record("addMaxHp:"+x);}',
        'public void addMaxMp(int x){maxMp+=x; record("addMaxMp:"+x);}',
        'public final Mod mod=new Mod();',
        'public Mod getAC(){record("getAC");return mod;}',
        'public Mod getResistance(){record("getResistance");return mod;}',
        'public Mod getAbility(){record("getAbility");return mod;}',
        'public String toString(){return "pc";}',
       ]
    for name in mod1:
        if name not in ('addMaxHp','addMaxMp'):pc.append(f'public void {name}(int x){{record("{name}:"+x);}}')
    pc.append('public static final class Mod {')
    for name in mod2:
        typ='(byte x)' if name.startswith('addAdded') else '(int x)'
        pc.append(f'public void {name}{typ}{{record("{name}:"+x);}}')
    for name in ('addSpecialPierce','addSpecialResistance'):
        if name in mod2:pc.append(f'public void {name}(eKind kind,int x){{record("{name}:"+kind+":"+x);}}')
    pc+=['}','}']
    put(tmp,'l1j/server/server/model/Instance/L1PcInstance.java','\n'.join(pc))
    pack='l1j/server/server/model/network/core/sc/packet/'
    for cn,args in [('SC_HpUpdate','Object pc,int a,int b'),('SC_MpUpdate','Object pc,int a,int b'),('SC_OwnCharAttrDef','Object pc'),('SC_SPMR','Object pc')]:
        put(tmp,pack+cn+'.java',f'package l1j.server.server.model.network.core.sc.packet;\nimport l1j.server.server.model.Instance.L1PcInstance; public final class {cn} '+'{ public static void send('+args+'){ L1PcInstance.record("'+cn+'"+"/"'+ ('+a+"/"+b' if 'int a' in args else '') +'); } }\n')
    put(tmp,'l1j/server/server/packets/Protocol/MainServer/Proto/SC_SPELL_BUFF_NOTI.java',
        'package l1j.server.server.packets.Protocol.MainServer.Proto;\nimport l1j.server.server.model.Instance.L1PcInstance;\n'
        'public final class SC_SPELL_BUFF_NOTI { public enum eNotiType { END } '
        'public static void sendFields(Object... args){L1PcInstance.record("notify:"+java.util.Arrays.toString(args));} }\n')
    put(tmp,'l1j/server/server/model/item/function/TestDoll.java',
        'package l1j.server.server.model.item.function;\nimport l1j.server.server.model.Instance.L1PcInstance;'
        'import java.util.Random;import java.util.HexFormat;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;'
        'public final class TestDoll {\npublic static void main(String[] args)throws Exception { '
        'MessageDigest md=MessageDigest.getInstance("SHA-256");\n'
        'int[] masks=new int[4129]; masks[0]=0;masks[1]=-1;'
        'for(int i=0;i<31;i++)masks[i+2]=(1<<i);'
        'Random rand=new Random(37100983L);for(int i=33;i<masks.length;i++)masks[i]=rand.nextInt();'
        'for(int mask:masks){ L1PcInstance.LOG.setLength(0);'
        'DollEffectItems.RemoveDollPotential(new L1PcInstance(mask));'
        'md.update((Integer.toUnsignedString(mask)+":"+L1PcInstance.LOG.toString()+"\\n").getBytes(StandardCharsets.UTF_8));'
        '}System.out.println(HexFormat.of().formatHex(md.digest())); } }\n')
    if orig:
        with zipfile.ZipFile(BASELINE) as z:
            raw=z.read('src/l1j/server/server/model/item/function/DollEffectItems.java')
        put(tmp,'l1j/server/server/model/item/function/DollEffectItems.java',raw.decode('utf8'))
        sources=list(tmp.rglob('*.java'))
    else:
        sources=list(tmp.rglob('*.java'))+[F/(cls+'.java') for cls in ['DollEffectItems','DollVitalDefenseEffectRemoval','DollAttributeEffectRemoval','DollCombatEffectRemoval','DollResistanceEffectRemoval']]
    # Java compiler will reject any incompatible API, missing method, or duplicate class.
    out=tmp/'classes';out.mkdir()
    r=subprocess.run(['javac','-encoding','UTF-8','-d',str(out),*[str(x) for x in sources]],capture_output=True,text=True)
    assert r.returncode==0,'typed doll javac error:\n'+r.stderr
    r=subprocess.run(['java','-cp',str(out),'l1j.server.server.model.item.function.TestDoll'],capture_output=True,text=True)
    assert r.returncode==0,'doll exercise failed:\n'+r.stderr
    return r.stdout.strip()

with tempfile.TemporaryDirectory(prefix='rkern_doll_typetest_') as name:
    tmp=Path(name)
    new=tmp/'new';new.mkdir()
    newsha=generate(new)
    if BASELINE:
        old=tmp/'old';old.mkdir(); oldsha=generate(old,True)
        assert newsha==oldsha, ('Doll differential divergence',oldsha,newsha)
        (AUDIT/'DOLL_ORIGINAL_4129_MASK_SHA256.txt').write_text(oldsha+'\n','utf8')
    else:
        expect=(AUDIT/'DOLL_ORIGINAL_4129_MASK_SHA256.txt').read_text('utf8').strip()
        assert newsha==expect, ('Doll expected trace changed',newsha,expect)
    print('PASS production DollEffectItems Java21 compile + 4,129 mask differential original v phase8:',newsha)
