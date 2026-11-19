package l1j.server.server.model.Instance;
import l1j.server.server.Account;
public class L1PcInstance {
 public Account acc;
 public int sex, job, id=17;
 public boolean dead;
 public Account getAccount(){return acc;}
 public int get_sex(){return sex;}
 public int getId(){return id;}
 public boolean isDead(){return dead;}
 public boolean isCrown(){return job==0;}
 public boolean isKnight(){return job==1;}
 public boolean isElf(){return job==2;}
 public boolean isWizard(){return job==3;}
 public boolean isDarkelf(){return job==4;}
 public boolean isWarrior(){return job==5;}
 public boolean isDragonknight(){return job==6;}
 public boolean isBlackwizard(){return job==7;}
}
