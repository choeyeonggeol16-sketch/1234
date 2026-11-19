package l1j.server.MJTemplate.Builder;

import l1j.server.MJTemplate.L1Instance.MJL1TowerInstance;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1World;

public class MJTowerBuilder {
	private int _npcid;
	private int _x;
	private int _y;
	private short _map_id;
	public MJTowerBuilder set_npcid(int npcid){
		_npcid = npcid;
		return this;
	}
	
	public MJTowerBuilder set_x(int x){
		_x = x;
		return this;
	}
	
	public MJTowerBuilder set_y(int y){
		_y = y;
		return this;
	}
	
	public MJTowerBuilder set_map_id(short map_id){
		_map_id = map_id;
		return this;
	}
	
	public MJL1TowerInstance build(){
		MJL1TowerInstance tower = new MJL1TowerInstance(NpcTable.getInstance().getTemplate(_npcid));
		tower.setId(IdFactory.getInstance().nextId());
		tower.setMap(_map_id);
		tower.getLocation().forward(0);
		tower.setX(_x);
		tower.setY(_y);
		tower.setHomeX(_x);
		tower.setHomeY(_y);
		tower.setHeading(0);
		L1World.getInstance().storeAndEnterWorld(tower);
		return tower;
	}
}
