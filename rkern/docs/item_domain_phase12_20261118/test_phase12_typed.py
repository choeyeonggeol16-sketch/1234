from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
FUN=ROOT/'src/l1j/server/server/model/item/function'
P='l1j/server/server/model/item/function/'
I='l1j/server/server/model/Instance/'
SC='l1j/server/server/model/network/core/sc/packet/'
files={}
for cls in ('GeneralSpellbookConsumable','ElfSpellbookConsumable','GeneralSpellbookLearning','EnchantmentSuccessEffects','EnchantmentFailureEffects'):
    files[P+cls+'.java']=(FUN/(cls+'.java')).read_text('utf-8')
files[P+'SpellBookItems.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class SpellBookItems {
 public static void SpellBook(L1PcInstance p,L1ItemInstance item,boolean isLawful){GeneralSpellbookLearning.SpellBook(p,item,isLawful);}
 public static void SpellBook2(L1PcInstance p,L1ItemInstance item){p.add("spellbook2");}
}'''
files['l1j/server/Config.java']='''package l1j.server;public final class Config {public static boolean MASTERENCHANTMESS=true;public static int LOGGING_WEAPON_ENCHANT=1,LOGGING_ARMOR_ENCHANT=1;}'''
files[I+'L1ItemInstance.java']='''package l1j.server.server.model.Instance;
public final class L1ItemInstance {
 public int id=123,type2=1,type=1,safe=6,ench=0,count=1;public boolean ided=true,equipped;
 public String name="마법서 (Skill1)";
 public L1ItemInstance getItem(){return this;}public int getItemId(){return id;}
 public String getName(){return name;}public int getType2(){return type2;}
 public int getType(){return type;}public int get_safeenchant(){return safe;}
 public boolean isIdentified(){return ided;}public int getEnchantLevel(){return ench;}
 public void setEnchantLevel(int v){ench=v;}public boolean isEquipped(){return equipped;}
 public int getCount(){return count;}
}'''
files['l1j/server/server/model/L1PcInventory.java']='''package l1j.server.server.model;
import l1j.server.server.model.Instance.*;
public class L1PcInventory {
 public static final int COL_ENCHANTLVL=5;private L1PcInstance pc;
 public L1PcInventory(L1PcInstance pc){this.pc=pc;}
 public void removeItem(L1ItemInstance i,int n){pc.add("remove:"+n);}
 public void setEquipped(L1ItemInstance i,boolean v){pc.add("equipped:"+v);}
 public void updateItem(L1ItemInstance i,int col){pc.add("update:"+col);}
 public void saveItem(L1ItemInstance i,int col){pc.add("saveItem:"+col);}
}'''
files[I+'L1PcInstance.java']='''package l1j.server.server.model.Instance;
import l1j.server.server.model.L1PcInventory;
public final class L1PcInstance {
 public StringBuilder log=new StringBuilder();public int lvl=80,hp=100,id=42,attr=1,role=0;public boolean gm=false,skill=false;
 public L1PcInventory inv=new L1PcInventory(this);public AC ac=new AC();public Resist resist=new Resist();
 public void add(String t){log.append(t).append('|');}
 public int getLevel(){return lvl;}public int getId(){return id;}public int getElfAttr(){return attr;}
 public boolean isGm(){return gm;}public boolean isKnight(){return role==1;}
 public boolean isWarrior(){return role==2;}public boolean isCrown(){return role==3;}
 public boolean isDarkelf(){return role==4;}public boolean isElf(){return role==5;}
 public boolean isWizard(){return role==6;}public boolean isSkillMastery(int n){return skill;}
 public L1PcInventory getInventory(){return inv;}public void saveInventory(){add("saveInventory");}
 public AC getAC(){return ac;}public Resist getResistance(){return resist;}
 public int getCurrentHp(){return hp;}public void setCurrentHp(int n){hp=n;add("hp:"+n);}
 public void death(Object o,boolean b){add("death");}
 public final class AC{public void addAc(int v){add("ac:"+v);}}
 public final class Resist{public void addMr(int v){add("mr:"+v);}}
}'''
files['l1j/server/server/model/network/core/session/CIOClient.java']='''package l1j.server.server.model.network.core.session;
import l1j.server.server.model.Instance.L1PcInstance;
public final class CIOClient {private L1PcInstance pc;public CIOClient(L1PcInstance p){pc=p;}public L1PcInstance getActiveChar(){return pc;}}'''
for cls in ['SC_ServerMessage','SC_SystemMessage','SC_AddSkill','SC_SkillSound','SC_OwnCharStatus','SC_PacketBox','SC_SPMR']:
    body={
      'SC_ServerMessage':'public static void send(L1PcInstance pc,int v,String...args){pc.add("server:"+v);}',
      'SC_SystemMessage':'public static void send(L1PcInstance pc,String s){pc.add("system:"+s);}public static void broadcast(String s){}',
      'SC_AddSkill':'public static void send(L1PcInstance pc,int... ids){pc.add("skill:"+ids.length);}',
      'SC_SkillSound':'public static long pack(int id,int snd){return snd;}public static void sendPacked(L1PcInstance pc,long v){pc.add("sound:"+v);}public static void broadcastPacked(L1PcInstance pc,long v){pc.add("broadcast:"+v);}',
      'SC_OwnCharStatus':'public static void send(L1PcInstance pc){pc.add("status");}',
      'SC_PacketBox':'public static void broadcastGreen(String s){}',
      'SC_SPMR':'public static void send(L1PcInstance pc){pc.add("spmr");}',
    }[cls]
    files[SC+cls+'.java']='package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class '+cls+'{'+body+'}'
files['l1j/server/server/templates/L1Skills.java']='''package l1j.server.server.templates;
public final class L1Skills {private int id;public L1Skills(int id){this.id=id;}public String getName(){return "Skill"+id;}public int getSkillLevel(){return (id-1)/8+1;}public int getId(){return id;}public int getSkillId(){return id;}}'''
files['l1j/server/server/datatables/SkillsTable.java']='''package l1j.server.server.datatables;
import l1j.server.server.templates.L1Skills;
public final class SkillsTable {public static SkillsTable getInstance(){return new SkillsTable();}
 public L1Skills getTemplate(int id){return new L1Skills(id);}
 public void spellMastery(int objid,int id,String s,int a,int b){}
}'''
files['l1j/server/server/monitor/LoggerInstance.java']='''package l1j.server.server.monitor;
import l1j.server.server.model.Instance.*;
public final class LoggerInstance{public static LoggerInstance getInstance(){return new LoggerInstance();}public void addEnchant(L1PcInstance p,L1ItemInstance i,boolean success){p.add("enchantLog:"+success);}}'''
files[P+'Phase12CompileProbe.java']='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.session.CIOClient;
public final class Phase12CompileProbe {
 public static void main(String[]a){
   L1PcInstance pc=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();CIOClient cl=new CIOClient(pc);
   pc.role=6;GeneralSpellbookConsumable.useSpellBook(pc,i,45000);
   pc.role=5;ElfSpellbookConsumable.useElfSpellBook(pc,i,40233);
   EnchantmentSuccessEffects.SuccessEnchant(pc,i,cl,1);
   EnchantmentFailureEffects.FailureEnchant(pc,i,cl);
   if(!pc.log.toString().contains("update:5")||!pc.log.toString().contains("remove:1")) throw new AssertionError(pc.log);
   System.out.println("PASS phase12 5 actual production classes Java 21 typed stub compilation and entrypoint probe");
 }
}'''
with tempfile.TemporaryDirectory(prefix='phase12_typed_') as tm:
    t=Path(tm)
    for n,src in files.items():
        p=t/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(src,'utf-8')
    cmd=['javac','--release','21','-encoding','UTF-8','-d',str(t),*[str(p) for p in t.rglob('*.java')]]
    proc=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    assert proc.returncode==0,proc.stdout
    proc=subprocess.run(['java','-cp',str(t),'l1j.server.server.model.item.function.Phase12CompileProbe'],capture_output=True,text=True)
    assert proc.returncode==0,proc.stdout+proc.stderr
    print(proc.stdout.strip())
