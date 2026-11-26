package l1j.server.server.shared.builder;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.world.instance.shared.GameL1TowerInstance;

public class TowerBuilder {
	private int _npcid;
	private int _x;
	private int _y;
	private short _map_id;
	public TowerBuilder set_npcid(int npcid){
		_npcid = npcid;
		return this;
	}
	
	public TowerBuilder set_x(int x){
		_x = x;
		return this;
	}
	
	public TowerBuilder set_y(int y){
		_y = y;
		return this;
	}
	
	public TowerBuilder set_map_id(short map_id){
		_map_id = map_id;
		return this;
	}
	
	public GameL1TowerInstance build(){
        GameL1TowerInstance tower = new GameL1TowerInstance(CObjectDB.LoadNPC().getTemplate(_npcid));
		tower.setId(IdFactory.getInstance().nextId());
		tower.setMap(_map_id);
		tower.getLocation().forward(0);
		tower.setX(_x);
		tower.setY(_y);
		tower.setHomeX(_x);
		tower.setHomeY(_y);
		tower.setHeading(0);
		CWorld.getInstance().storeAndEnterWorld(tower);
		return tower;
	}
}
