#!/usr/bin/env python3
from pathlib import Path
import tempfile, subprocess, re, shutil
ROOT=Path(__file__).resolve().parents[2]; FUN=ROOT/'src/l1j/server/server/model/item/function'; D=ROOT/'docs/item_domain_phase13_20261118'
def run(files,main):
    with tempfile.TemporaryDirectory(prefix='rkern13_') as temp:
        tmp=Path(temp)
        for n,txt in files.items():
            p=tmp/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(txt,'utf-8')
        args=['javac','--release','21','-encoding','UTF-8','-d',temp,*map(str,tmp.rglob('*.java'))]
        p=subprocess.run(args,capture_output=True,text=True)
        if p.returncode:
            print(p.stdout+p.stderr);raise RuntimeError('compilation failed')
        p=subprocess.run(['java','-cp',temp,main],capture_output=True,text=True)
        if p.returncode:
            print(p.stdout+p.stderr);raise RuntimeError('run failed')
        print(p.stdout.strip())

pfx='l1j/server/server/model/item/function/'
def prod(*names):return {pfx+n+'.java':(FUN/(n+'.java')).read_text('utf-8') for n in names}
def legacy(name):
    src=(D/(name+'.java.baseline')).read_text('utf-8')
    src=src.replace('public final class '+name,'final class Legacy'+name,1).replace('private '+name+'()','private Legacy'+name+'()',1)
    return src

# Ice buffs: compile actual extracted classes and full original; compare durable-consume callbacks
# and every observable skill/message/status/cancellation event in order.
files=prod('IceBuffItem')
files[pfx+'LegacyIceBuffItem.java']=legacy('IceBuffItem')
orig=(D/'IceBuffItem.java.baseline').read_text('utf-8')
skills=sorted(set(re.findall(r'L1SkillId\.([\w\u0080-\uffff]+)',orig)))
files['l1j/server/server/model/character/skill/L1SkillId.java']='''package l1j.server.server.model.character.skill;
public final class L1SkillId {'''+''.join('public static final int '+s+' = '+str(i+500)+';\n' for i,s in enumerate(skills))+'''}'''
files['l1j/server/server/model/character/skill/L1SkillUse.java']='''package l1j.server.server.model.character.skill;
import l1j.server.server.model.Instance.L1PcInstance;
public final class L1SkillUse {public static final int TYPE_GMBUFF=1;
public static void cast(L1PcInstance p,int sk,int id,int x,int y,Object obj,int sec,int mode){p.log("CAST:"+sk+":"+mode);}}
'''
files['l1j/server/server/model/Instance/L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public class L1ItemInstance {public int id,type1;public L1ItemInstance(int id,int type1){this.id=id;this.type1=type1;}
public int getItemId(){return id;}public L1ItemInstance getItem(){return this;}public int getType1(){return type1;}}
'''
files['l1j/server/server/model/Instance/L1PcInstance.java']='''package l1j.server.server.model.Instance;
import java.util.*;
public final class L1PcInstance {
 public final StringBuilder events=new StringBuilder();public Set<Integer> effects=new HashSet<>();
 public boolean elf,wizard;public int attr,weaponType=-1,buffnoch,maxmp=100;
 public void log(String s){events.append(s).append(';');}
 public L1ItemInstance getWeapon(){return weaponType<0?null:new L1ItemInstance(999,weaponType);}
 public void sendPackets(String s){log("MSG:"+s);}public int getId(){return 123;}public int getX(){return 77;}public int getY(){return 88;}
 public void setBuffnoch(int n){buffnoch=n;log("BuffNo:"+n);}public boolean isElf(){return elf;} public int getElfAttr(){return attr;}
 public boolean isWizard(){return wizard;}public Ability getAbility(){return new Ability(this);}public Resistance getResistance(){return new Resistance(this);}
 public static final class Ability {final L1PcInstance p;Ability(L1PcInstance p){this.p=p;}
 public void addSp(int n){p.log("SP:"+n);}public void addAddedStr(int n){p.log("STR:"+n);}public void addAddedDex(byte n){p.log("DEX:"+n);}public void addAddedInt(int n){p.log("INT:"+n);}}
 public static final class Resistance {final L1PcInstance p;Resistance(L1PcInstance p){this.p=p;}public void addcalcPcDefense(int n){p.log("DEF:"+n);}}
 public void addMaxMp(int n){maxmp+=n;log("MaxMP:"+n);}public void addBaseMagicHitUp(int n){log("MHIT:"+n);}public void addDmgRate(int n){log("DmgRate:"+n);}
 public void addHitup(int n){log("Hit:"+n);}public void addDmgup(int n){log("Dmg:"+n);}public void addBowDmgup(int n){log("BowDmg:"+n);}public void addBowHitup(int n){log("BowHit:"+n);}
 public void curePoison(){log("Cure");}public void setSkillEffect(int s,int t){effects.add(s);log("SET:"+s+":"+t);}
 public boolean hasSkillEffect(int s){log("HAS:"+s);return effects.contains(s);}public void removeSkillEffect(int s){effects.remove(s);log("REM:"+s);}
 public int getTotalER(){return 5;}public int getCurrentMp(){return 16;}public int getMaxMp(){return maxmp;}
}'''
files[pfx+'ItemReceiptOperations.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class ItemReceiptOperations {public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable callback){pc.log("RECEIPT:"+item.getItemId());callback.run();pc.log("RECEIPT_END");}}
'''
sys='l1j/server/server/model/network/core/sc/packet/'
files[sys+'SC_SPMR.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SPMR {public static void send(L1PcInstance p){p.log("SC_SPMR");}}
'''
files[sys+'SC_MpUpdate.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_MpUpdate {public static void send(L1PcInstance p,int cur,int max){p.log("MPUP:"+cur+":"+max);}}
'''
files[sys+'SC_SkillSound.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SkillSound {public static long pack(int a,int b){return (((long)a)<<32)|b;}
public static void sendPacked(L1PcInstance p,long v){p.log("SOUNDSEND:"+v);}public static void broadcastPacked(L1PcInstance p,long v){p.log("SOUNDBROAD:"+v);}}
'''
files[sys+'SC_OwnCharStatus.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_OwnCharStatus {public static void send(L1PcInstance p){p.log("OWNCHAR");}}
'''
files[sys+'SC_PacketBox.java']='''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_PacketBox {public static final int ER_UpDate=33;public static void send(L1PcInstance p,int box,int value){p.log("BOX:"+box+":"+value);}}
'''
files[pfx+'Phase13IceDifferential.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.character.skill.L1SkillId;
public final class Phase13IceDifferential {
 public static void main(String[]args){int n=0;
 int[] ids={60208,60209,60210,4100040,10000,0};
 for(int id:ids)for(int weapon:new int[]{-1,1,20,5})for(int cls=0;cls<5;cls++)for(int eff=0;eff<8;eff++){
 L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();
 a.elf=b.elf=cls==1||cls==2;a.wizard=b.wizard=cls==3;
 a.attr=b.attr=cls==2?2:1;a.weaponType=b.weaponType=weapon;
 for(int x=0;x<12;x++)if(((x+eff)&3)==0){a.effects.add(500+x);b.effects.add(500+x);}
 L1ItemInstance item=new L1ItemInstance(id,0);
 boolean ar=LegacyIceBuffItem.use(a,item),br=IceBuffItem.use(b,item);
 if(ar!=br||!a.events.toString().equals(b.events.toString())||!a.effects.equals(b.effects)||a.buffnoch!=b.buffnoch||a.maxmp!=b.maxmp)
 throw new AssertionError("id="+id+" weapon="+weapon+" cls="+cls+" eff="+eff+" before="+a.events+" now="+b.events);
 n++;}
 if(LegacyIceBuffItem.use(null,null)!=IceBuffItem.use(null,null))throw new AssertionError("null");
 System.out.println("PASS ice merged production class typed and "+n+" original-vs-new item use cases");
 }
}'''
run(files,'l1j.server.server.model.item.function.Phase13IceDifferential')

