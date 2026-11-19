package l1j.server.server.model.Instance;
import java.util.*;
public class L1PcInstance {
 public boolean 물품멘트=false;public final List<String> log=new ArrayList<>();public final Inventory inventory=new Inventory();
 public Map<Integer,L1DollInstance> getDollList(){return Collections.emptyMap();}
 public int getId(){return 9;} public Inventory getInventory(){return inventory;}
 public final class Inventory{ public int ticketCount=5; public int countItems(int id){return id==474747?ticketCount:0;} }
}