package l1j.server.server.model.world.instance.lobby.template;

import java.util.ArrayList;
import java.util.logging.Level;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerFromLfcInstance;
import l1j.server.server.model.world.instance.InstanceEnums.InstStatus;
import l1j.server.server.model.world.instance.InstanceEnums.LFCMessages;
import l1j.server.server.model.world.instance.InstanceSpace;
import l1j.server.server.model.world.instance.lobby.creator.LobbyCreator;

public class LobbyArenaTower extends LobbyObject{
	private ArrayList<L1NpcInstance> 			_boundary;
	private ArrayList<L1TowerFromLfcInstance> 	_tower;
	
	public static LobbyArenaTower createInstance(){
		return new LobbyArenaTower();
	}
	
	public LobbyArenaTower() {
		super();
		_boundary	= new ArrayList<L1NpcInstance>();
		_tower 		= new ArrayList<L1TowerFromLfcInstance>();
	}
	
	@Override
	public void init(){
		L1NpcInstance boundary;
		boundary = spawnBoundary(32799, 32832, 0);
		if(boundary!=null)
			_boundary.add(boundary);
		boundary = spawnBoundary(32805, 32832, 0);
		if(boundary!=null)
			_boundary.add(boundary);
		
		L1TowerFromLfcInstance tower = spawnTower(32801, 32821);
		if(tower != null)
			_tower.add(tower);
		tower = spawnTower(32801, 32843);
		if(tower != null)
			_tower.add(tower);
		
		super.init();
	}

	@Override
	public void run(){
		try{
			waitCount();
			for(L1NpcInstance boundary : _boundary)
				deleteNpc(boundary);
			_boundary.clear();
			_boundary=null;
			
			for(L1PcInstance pc : _red)
				LobbyCreator.setInstStatus(pc, InstStatus.INST_USERSTATUS_LFC);
			for(L1PcInstance pc : _blue)
				LobbyCreator.setInstStatus(pc, InstStatus.INST_USERSTATUS_LFC);
			
			while(_isrun){
				Thread.sleep(1000);
				if(!checkSecond() || _tower.get(0).isDead() || _tower.get(1).isDead()){
					close();
					return;
				}
			}
		}catch(Exception e){
			close();
			e.printStackTrace();
			_log.log(Level.SEVERE, getName(), e);
		}
	}
	
	@Override
	public void closeForGM(){
		for(L1TowerFromLfcInstance tower : _tower)
			deleteTower(tower);
		super.closeForGM();
	}
	
	@Override
	public void close(){
		for(L1TowerFromLfcInstance tower : _tower)
			deleteTower(tower);
		
		if(!_tower.get(0).isDead() && !_tower.get(1).isDead() && _tower.get(0).getCurrentHp() == _tower.get(1).getCurrentHp()){
			super.close();
			return;
		}
		ArrayList<L1PcInstance> winners = _red;
		ArrayList<L1PcInstance> losers	= _blue;
		
		sendGreenBothTeams(LFCMessages.INGAME_CLOSE, null);
		
		if(_tower.get(0).isDead()){
			winners = _blue;
			losers	= _red;
		}else if(_tower.get(1).isDead()){
			winners = _red;
			losers	= _blue;
		}else if(_tower.get(0).getCurrentHp() > _tower.get(1).getCurrentHp()){
			winners = _red;
			losers 	= _blue;
		}else{
			winners = _blue;
			losers 	= _red;			
		}
		
		try{
			Thread.sleep(3000);
		}catch(Exception e){
			
		}
		
		compensate(winners, losers);		
		getBack();
		_isrun = false;
		InstanceSpace.getInstance().releaseInstance(this);
	}
	
	@Override
	public String getName(){
		return "LobbyArenaTower";
	}
}
