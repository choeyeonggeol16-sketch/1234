package l1j.server.server.model.Instance;
public class L1ItemInstance {
 public int count=1;public int id=1;public int chargeCount;
 public l1j.server.server.templates.L1EtcItem template=new l1j.server.server.templates.L1EtcItem();
 public java.sql.Timestamp lastUsed;
 public int getId(){return id;} public int getItemId(){return template.getItemId();}
 public l1j.server.server.templates.L1EtcItem getItem(){return template;}
 public int getChargeCount(){return chargeCount;} public void setChargeCount(int n){chargeCount=n;}
 public void setLastUsed(java.sql.Timestamp t){lastUsed=t;}
}