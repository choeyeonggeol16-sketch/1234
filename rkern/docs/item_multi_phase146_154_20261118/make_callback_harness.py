from pathlib import Path
import sys
ROOT=Path(sys.argv[1]); OUT=Path(sys.argv[2]); OUT.mkdir(parents=True,exist_ok=True)
root=ROOT/'src/l1j/server/server/model/item/function' 
def callback(name,marker):
 s=(root/(name+'.java')).read_text('utf8'); idx=s.index(marker); a=s.index('{',idx); depth=1;i=a+1
 while depth:
  if s[i]=='{':depth+=1
  if s[i]=='}':depth-=1
  i+=1
 return s[a+1:i-1]
code = {
'attribute': callback('AttributeEnchantScrollEffects','CDB.RequestUseItem(pc, tx, () -> {'),
'potential': callback('PotentialScrollTransaction','CDB.RequestUseItem(pc, tx, () -> {'),
'doll': callback('DollPotentialTransactions','tx.commitAfterInventoryRelease(() -> {'),
'elixir': callback('ElixirStatTransactions','tx.commitAfterInventoryRelease(() -> {'),
'slot': callback('InventorySlotExpansionItems','tx.commitAfterInventoryRelease(() -> {'),
'lawful': callback('LawfulPotionTransactions','tx.commitAfterInventoryRelease(() -> {'),
'uncurse': callback('UncurseScrollReceipt','CDB.RequestUseItem(pc, tx, () -> {'),
}
s='''import java.util.*;
import java.util.logging.*;
public class PostCommitHarness {
    static final Logger ITEM_USE2_LOG=Logger.getLogger("test-item");
    static final Logger _log=Logger.getLogger("test-item");
    static final Logger LOG=Logger.getLogger("test-item");
    static int checks;
    static void check(boolean c,String s){checks++;if(!c)throw new AssertionError(s);}
    static void attribute(L1PcInstance pc,boolean success,L1ItemInstance weapon,String weaponName) {\n'''+code['attribute']+'''\n    }
    static void potential(L1PcInstance pc,int newSkill,String notice) {\n'''+code['potential']+'''\n    }
    static void doll(L1PcInstance pc,String message,boolean announceHero,boolean announceLegend) {\n'''+code['doll']+'''\n    }
    static void elixir(L1PcInstance pc,int type,int level,int beforeCount,int beforeStat,int nextCount) {\n'''+code['elixir']+'''\n    }
    static void slot(L1PcInstance pc,int before,int level,int next) {\n'''+code['slot']+'''\n    }
    static void lawful(L1PcInstance pc,int before,int next,int itemId,int characterId) {\n'''+code['lawful']+'''\n    }
    static void uncurse(L1PcInstance pc) {\n'''+code['uncurse']+'''\n    }
    static void reset(String fail){Notice.fail=fail;Notice.calls.clear();}
    static void checkSeen(String... names){for(String n:names)check(Notice.calls.contains(n),"missing notice "+n);}
    public static void main(String[] args) {
        for(String fail:new String[]{"attr-update","server",""}){
            reset(fail);attribute(new L1PcInstance(),true,new L1ItemInstance(),"weapon");
            checkSeen("attr-update","server");
        }
        for(String fail:new String[]{"sound","broadcast","system","attrdef",""}){
            reset(fail);L1PcInstance pc=new L1PcInstance();potential(pc,180,"skill");
            check(pc.skillApplied==1&&pc.dollCleared==1&&pc.skillSet==1,"potential state lost");
            checkSeen("sound","broadcast","system","attrdef");
        }
        for(String fail:new String[]{"system","attrdef",""}){
            reset(fail);L1PcInstance pc=new L1PcInstance();potential(pc,0,"failed");
            check(pc.skillApplied==0,"failed potential applied skill");checkSeen("system","attrdef");
        }
        for(boolean hero:new boolean[]{true,false}){
            for(String fail:new String[]{"player","green",""}){
                reset(fail);doll(new L1PcInstance(),"doll",hero,!hero);
                checkSeen("player","green");
            }
        }
        for(String fail:new String[]{"own-status","elixir-ui",""}){
            reset(fail);L1PcInstance pc=new L1PcInstance();elixir(pc,0,80,0,10,1);
            check(pc.ability.str==11&&pc.elixirs==1&&pc.saves==1,"committed elixir not saved");
            checkSeen("own-status","elixir-ui");
        }
        reset("");L1PcInstance bad=new L1PcInstance();bad.elixirs=3;
        try {elixir(bad,0,80,0,10,1);throw new AssertionError("stale elixir accepted");}
        catch(IllegalStateException expected){checks++;}
        check(bad.saves==0,"stale elixir saved");
        for(String fail:new String[]{"server",""}){
            reset(fail);L1PcInstance pc=new L1PcInstance();slot(pc,0,80,20);
            check(pc.special==20&&pc.saves==1,"special slot lost");checkSeen("server");
        }
        for(int itemId:new int[]{490028,490029}){
            for(String fail:new String[]{"server","player",""}){
                reset(fail);L1PcInstance pc=new L1PcInstance();lawful(pc,0,10,itemId,7);
                check(pc.lawful==10&&pc.saves==1&&pc.changedLawful==1,"lawful state lost");
                checkSeen(itemId==490028?"server":"player");
            }
        }
        for(String fail:new String[]{"server",""}){
            reset(fail);uncurse(new L1PcInstance());checkSeen("server");
        }
        System.out.println("PRODUCTION_POST_COMMIT_CALLBACK_CHECKS="+checks+" PASS");
    }
}
class Notice {
    static String fail="";static final HashSet<String> calls=new HashSet<>();
    static void send(String id){calls.add(id);if(id.equals(fail))throw new IllegalStateException("Injected "+id);}
}
class Config {static boolean 영웅등급멘트=true,전설등급멘트=true;}
class L1ItemInstance{}
class L1PcInstance {
    int level=80,elixirs=0,special=0,lawful=0,saves=0,skillApplied=0,skillSet=0,dollCleared=0,changedLawful=0;
    Ability ability=new Ability();int getId(){return 7;}int getLevel(){return level;}
    Ability getAbility(){return ability;}int getElixirStats(){return elixirs;}void setElixirStats(int n){elixirs=n;}
    void resetBaseAc(){}void resetBaseMr(){}void save(){saves++;}
    int get_SpecialSize(){return special;}void set_SpecialSize(int n){special=n;}
    int getLawful(){return lawful;}void setLawful(int n){lawful=n;}void onChangeLawful(){changedLawful++;}
    void sendPackets(String x){Notice.send("player");}void setSkillEffect(int n,int m){skillSet++;}
    String getName(){return "test";}
}
class Ability {
    int str=10,con=10,dex=10,intelligence=10,wis=10,cha=10;
    int getStr(){return str;}int getCon(){return con;}int getDex(){return dex;}
    int getInt(){return intelligence;}int getWis(){return wis;}int getCha(){return cha;}
    void addStr(int n){str+=n;}void addCon(int n){con+=n;}void addDex(int n){dex+=n;}
    void addInt(int n){intelligence+=n;}void addWis(int n){wis+=n;}void addCha(int n){cha+=n;}
}
class SC_PacketBox {static final int ITEM_ENCHANT_UPDATE=1;static void send(L1PcInstance p,int id,L1ItemInstance i){Notice.send("attr-update");}
 static void broadcastGreen(String s){Notice.send("green");}}
class SC_ServerMessage {static void send(L1PcInstance p,int id,Object... args){Notice.send("server");}}
class SC_SystemMessage {static void send(L1PcInstance p,String msg){Notice.send("system");}}
class SC_SkillSound {static void send(L1PcInstance p,int id,int sid){Notice.send("sound");}
 static void broadcast(L1PcInstance p,int id,int sid){Notice.send("broadcast");}}
class SC_OwnCharAttrDef {static void send(L1PcInstance p){Notice.send("attrdef");}}
class SC_OwnCharStatus2 {static void send(L1PcInstance p){Notice.send("own-status");}}
class SC_ActionUI2 {static final int Elixir=1;static void send(L1PcInstance p,int id,int n){Notice.send("elixir-ui");}}
class DollEffectItems {static void RemoveDollPotential(L1PcInstance p){p.dollCleared++;}}
class PotentialSkillEffects {static void applyPotentialSkill(L1PcInstance p,int n){p.skillApplied++;}}
'''
(OUT/'PostCommitHarness.java').write_text(s)
print('callbacks extracted',[(k,len(v)) for k,v in code.items()])
