package l1j.server.server.model.Instance;
import l1j.server.server.model.L1Character;import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.core.session.CIOClient;
public class L1PcInstance extends L1Character {
 public CIOClient client;public int team;public boolean gm,shop,world=true;public int known;
 public L1PcInstance(int id,int map,CIOClient client){super(id,map);this.client=client;}
 public CIOClient getNetConnection(){return client;}public boolean isPrivateShop(){return shop;}
 public boolean is_world(){return world;}public boolean isGm(){return gm;}
 public int get_current_combat_team_id(){return team;} public void addKnownObject(L1Object obj){known++;}
}