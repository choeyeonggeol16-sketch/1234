package l1j.server.server.model;
import java.util.*;import l1j.server.server.model.Instance.L1PcInstance;
public final class L1World {
 static final L1World SINGLETON=new L1World();public static L1World getInstance(){return SINGLETON;}
 public List<L1PcInstance> visible=new ArrayList<>(),all=new ArrayList<>();public int borrows,releases;
 public List<L1PcInstance> borrowVisiblePlayerFast(L1Object anchor){borrows++;return visible;}
 public void releaseVisiblePlayerFast(List<L1PcInstance> x){releases++;}
 public List<L1PcInstance> getVisiblePlayer(L1Object anchor){return visible;}
 public List<L1PcInstance> getVisiblePlayer(L1Object anchor,int radius){return visible;}
 public List<L1PcInstance> getVisiblePlayers(int mapId){return visible.stream().filter(p->p.map==mapId).toList();}
 public List<L1PcInstance> getAllPlayers(){return all;}
}