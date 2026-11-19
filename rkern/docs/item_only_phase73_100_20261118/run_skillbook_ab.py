#!/usr/bin/env python3
"""Compile production spellbook method and extracted warrior method with typed Java21 stubs, compare normal runs, exercise invalid inputs."""
from pathlib import Path
import tempfile, subprocess, re
DOC=Path(__file__).resolve().parent
ROOT=DOC.parents[1]
BASE=DOC/'baseline'
PROD=ROOT/'src/l1j/server/server/model/item/function'
FILES=['SkillBookLearning.java','WarriorSkillBookItems.java']

def method_body(src,signature):
    start=src.index(signature);a=src.index('{',start);depth=1;i=a+1
    while depth:
        if src[i]=='{':depth+=1
        elif src[i]=='}':depth-=1
        i+=1
    return src[start:i]

with tempfile.TemporaryDirectory(prefix='rkern_skill_') as temp:
    w=Path(temp);root=w/'src';root.mkdir()
    def write(name,src):
        dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(src)
    for version,place in [('Old',BASE),('New',PROD)]:
        src=(place/'SkillBookLearning.java').read_text()
        src=src.replace('final class SkillBookLearning {','final class '+version+'SkillBookLearning {')
        src=src.replace('private SkillBookLearning() {}','private '+version+'SkillBookLearning() {}')
        write('l1j/server/server/model/item/function/'+version+'SkillBookLearning.java',src)
        warrior=(place/'WarriorSkillBookItems.java').read_text()
        meth=method_body(warrior,'public static void 전사스킬(')
        wrapped='''package l1j.server.server.model.item.function;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.*;
public final class '''+version+'Warrior {\n'+meth+'\n}'
        write('l1j/server/server/model/item/function/'+version+'Warrior.java',wrapped)
    write('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
public class L1ItemInstance {
 int id;final Item item; public L1ItemInstance(int id,String name){this.id=id;item=new Item(name);}
 public int getItemId(){return id;} public Item getItem(){return item;}
 public static class Item{String name;Item(String x){name=x;}public String getName(){return name;}}
}''')
    write('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
import l1j.server.server.model.item.function.Trace;
public class L1PcInstance {
 public final Inventory inventory=new Inventory();public boolean mastery;public boolean gm;public int elfattr;
 public boolean isGm(){return gm;}public int getElfAttr(){return elfattr;}
 public boolean isSkillMastery(int skillId){Trace.logs.append("mastery=").append(skillId).append(';');return mastery;}
 public int getId(){return 419;} public Inventory getInventory(){return inventory;}
 public static class Inventory{public void removeItem(L1ItemInstance item,int count){Trace.logs.append("remove:").append(item.getItemId()).append(':').append(count).append(';');}}
}''')
    write('l1j/server/server/templates/L1Skills.java','''package l1j.server.server.templates;
public class L1Skills {
 public final int id,level;public L1Skills(int id,int level){this.id=id;this.level=level;}
 public int getId(){return id;}public int getSkillLevel(){return level;}public int getSkillId(){return id;}public String getName(){return "Skill"+id;}
 public int getAttr(){return 0;}
}''')
    write('l1j/server/server/datatables/SkillsTable.java','''package l1j.server.server.datatables;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.model.item.function.Trace;
public class SkillsTable {
 public static boolean missing=false;public static int warriorLevel=1;
 private static final SkillsTable INSTANCE=new SkillsTable();public static SkillsTable getInstance(){return INSTANCE;}
 public L1Skills getTemplate(int id){return missing?null:new L1Skills(id,1);}
 public L1Skills getTemplateByItem(int id){return missing?null:new L1Skills(id,warriorLevel);}
 public void spellMastery(int id,int sid,String name,int a,int b){Trace.logs.append("save:").append(id).append(':').append(sid).append(':').append(name).append(';');}
}''')
    packet={'SC_ServerMessage':'''public static void send(L1PcInstance p,int n){Trace.logs.append("server:").append(n).append(';');}''',
            'SC_SystemMessage':'''public static void send(L1PcInstance p,String m){Trace.logs.append("system:").append(m).append(';');}''',
            'SC_AddSkill':'''public static void send(L1PcInstance p,int[] values){Trace.logs.append("skill:").append(java.util.Arrays.toString(values)).append(';');}''',
            'SC_SkillSound':'''public static long pack(int id,int sound){return ((long)id<<32)|sound;}
 public static void sendPacked(L1PcInstance p,long s){Trace.logs.append("sound:").append(s).append(';');}
 public static void broadcastPacked(L1PcInstance p,long s){Trace.logs.append("broadcast:").append(s).append(';');}'''}
    for cl,body in packet.items():
        write('l1j/server/server/model/network/core/sc/packet/'+cl+'.java','''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.function.Trace;
public class '''+cl+'{'+body+'}')
    write('l1j/server/server/model/item/function/Trace.java','''package l1j.server.server.model.item.function;
public class Trace{public static final StringBuilder logs=new StringBuilder();}
''')
    write('l1j/server/server/model/item/function/Run.java','''package l1j.server.server.model.item.function;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.*;
public class Run {
 static String skill(boolean mod,String kind,boolean mastered){
   Trace.logs.setLength(0);SkillsTable.missing=false;
   L1PcInstance pc=new L1PcInstance();pc.mastery=mastered;
   if(mod){NewSkillBookLearning.Kind k=NewSkillBookLearning.Kind.valueOf(kind);
    NewSkillBookLearning.learn(pc,new L1ItemInstance(710,k.prefix+"Skill"+k.last+")"),k);}
   else{OldSkillBookLearning.Kind k=OldSkillBookLearning.Kind.valueOf(kind);
    OldSkillBookLearning.learn(pc,new L1ItemInstance(710,k.prefix+"Skill"+k.last+")"),k);}
   return Trace.logs.toString();
 }
 static String warrior(boolean mod,int level,boolean missing){
   Trace.logs.setLength(0);SkillsTable.missing=missing;SkillsTable.warriorLevel=level;
   L1PcInstance pc=new L1PcInstance();
   try{if(mod)NewWarrior.전사스킬(pc,new L1ItemInstance(202,"Warrior"),false);
       else OldWarrior.전사스킬(pc,new L1ItemInstance(202,"Warrior"),false);
   }catch(RuntimeException e){Trace.logs.append("EXCEPTION:").append(e.getClass().getSimpleName());}
   return Trace.logs.toString();
 }
 public static void main(String[] args){int normal=0;
  for(OldSkillBookLearning.Kind k: OldSkillBookLearning.Kind.values())for(boolean mastered:new boolean[]{false,true}){
    String old=skill(false,k.name(),mastered),cur=skill(true,k.name(),mastered);
    if(!old.equals(cur))throw new AssertionError("normal spell: "+k+" old="+old+" new="+cur);normal++;
  }
  for(int level:new int[]{1,2,28,29})for(boolean missing:new boolean[]{false,true}){
    String old=warrior(false,level,missing),cur=warrior(true,level,missing);
    if(missing){
      if(cur.contains("remove:") || cur.contains("save:"))throw new AssertionError("missing warrior book consumed: "+cur);
    }else if(!old.equals(cur))throw new AssertionError("warrior: "+level+" old="+old+" new="+cur);
    normal++;
  }
  for(int level:new int[]{-100,0,30,100}){
   String outcome=warrior(true,level,false);
   if(outcome.contains("remove:")||outcome.contains("save:")||outcome.contains("EXCEPTION:"))throw new AssertionError("bad warrior level: "+outcome);
  }
  SkillsTable.missing=true;Trace.logs.setLength(0);
  L1PcInstance pc=new L1PcInstance();
  NewSkillBookLearning.learn(pc,new L1ItemInstance(710,"마법서 (Unknown)"),NewSkillBookLearning.Kind.ELF);
  if(Trace.logs.toString().contains("remove:"))throw new AssertionError("missing skillbook consumed");
  if(NewSkillBookLearning.matches(null,"prefix","name") || NewSkillBookLearning.matches("prefixname)","prefix",null))
    throw new AssertionError("null skill name or book should be rejected without exception");
  System.out.println("PASS: "+normal+" normal typed comparisons and invalid levels/missing templates fail-closed");
 }
}''')
    java=[str(p) for p in root.rglob('*.java')]
    cp=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(w/'classes'),*java],capture_output=True,text=True)
    if cp.returncode: raise RuntimeError(cp.stderr)
    cp=subprocess.run(['java','-cp',str(w/'classes'),'l1j.server.server.model.item.function.Run'],capture_output=True,text=True,timeout=90)
    if cp.returncode:raise RuntimeError(cp.stdout+'\n'+cp.stderr)
    print(cp.stdout.strip())
