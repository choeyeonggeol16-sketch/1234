package l1j.server.server.model.Instance;
import java.util.HashMap;
public class L1PcInstance {
 public final HashMap<Integer,Integer> buffs=new HashMap<>();
 public boolean decay, elf, wizard, knight, crown, warrior, darkwizard, blind, floatEye;
 public int speed, brave, sp, mpr, id=42, drink, revealed, level=50, mapId=3;
 public String lastNotice;
 public int getLevel(){return level;}
 public int getMapId(){return mapId;}
 public void sendPackets(String s){lastNotice=s;}
 private final l1j.server.server.model.CUserInventory inventory=new l1j.server.server.model.CUserInventory();
 public l1j.server.server.model.CUserInventory getInventory(){return inventory;}
 public int delayCount;
 public boolean hasSkillEffect(int id) {return decay && (id==71 || id==l1j.server.server.model.character.skill.L1SkillId.DECAY_POTION) || buffs.containsKey(id);}
 public int getSkillEffectTimeSec(int id) {return buffs.getOrDefault(id,0)/1000;}
 public void setSkillEffect(int id,int time){buffs.put(id,time);}
 public void killSkillEffectTimer(int id){buffs.remove(id);}
 public void remove_elf_second_brave() {}
 public int getId(){return id;}
 public int getHasteItemEquipped(){return 0;}
 public void setDrink(boolean flag){drink=flag?1:0;}
 public void setMoveSpeed(int v){speed=v;}
 public void setBraveSpeed(int v){brave=v;}
 public int getBraveSpeed(){return brave;}
 public boolean isWizard(){return wizard;}
 public boolean isBlackwizard(){return darkwizard;}
 public boolean isElf(){return elf;}
 public boolean isKnight(){return knight;}
 public boolean isCrown(){return crown;}
 public boolean isWarrior(){return warrior;}
 public boolean isFloatingEye(){return floatEye;}
 public boolean isBlind(){return blind;}
 public void showFloatingEyeToInvis(){revealed++;}
 public Ability getAbility(){return new Ability(this);}
 public void addMpr(int n){mpr+=n;}
 public static class Ability {L1PcInstance pc; Ability(L1PcInstance p){pc=p;} public void addSp(int n){pc.sp+=n;}}
}
