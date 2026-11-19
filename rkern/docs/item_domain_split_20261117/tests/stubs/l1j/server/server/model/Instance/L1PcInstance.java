package l1j.server.server.model.Instance;
public class L1PcInstance {
 public int charClass; public L1ItemInstance input;public boolean valid=true;
 public static final StringBuilder events = new StringBuilder();
 public boolean skillActive=true; public int x=11,y=22;
 public boolean hasSkillEffect(int skill){events.append("HAS:").append(skill).append('|'); return skillActive;}
 public void removeSkillEffect(int skill){events.append("REMOVE:").append(skill).append('|');}
 public void setSkillEffect(int skill,int ms){events.append("SET:").append(skill).append(':').append(ms).append('|');}
 public int getX(){return x;}public int getY(){return y;}
 public Ability getAbility(){return new Ability();}
 public static final class Ability{public void addAddedWis(byte b){events.append("WIS:").append(b).append('|');}}

 public L1PcInstance(int c,int itemId){charClass=c;input=new L1ItemInstance(new l1j.server.server.templates.L1Item(itemId),1);input.setId(7);}
 public int getId(){return 1234;}
 public L1PcInstance getInventory(){return this;}
 public L1ItemInstance getItem(int id){return valid&&id==7?input:null;}
 public boolean checkItem(int id,int count){return valid&&id==input.getItemId() && count==1;}
 public boolean isWarrior(){return charClass==0;} public boolean isKnight(){return charClass==1;}
 public boolean isDragonknight(){return charClass==2;} public boolean isCrown(){return charClass==3;}
 public boolean isWizard(){return charClass==4;} public boolean isBlackwizard(){return charClass==5;}
 public boolean isElf(){return charClass==6;} public boolean isDarkelf(){return charClass==7;}
}
