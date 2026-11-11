package l1j.server.RaidSystem.Util;

import java.util.Random;

import l1j.server.Templates.L1Npc;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.poison.DamagePoison;
import l1j.server.server.serverpackets.S_RemoveObject;

@SuppressWarnings("serial")
public class RaidPoisonInstance extends L1NpcInstance {
	private static final int	INTERVAL	= 0x3e8;
	private static Random 		_rnd = new Random(System.nanoTime());
	private MonsterInstance 	_owner;
	public RaidPoisonInstance(L1Npc template) {
		super(template);
	}
	public void setOwner(MonsterInstance owner){
		_owner = owner;
	}
	
	public MonsterInstance getOwner(){
		return _owner;
	}
	
	@Override
	public void deleteMe() {
		_destroyed = true;
		if (getInventory() != null) {
			getInventory().clearItems();
		}
		allTargetClear();
		_master = null;
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			if (pc == null) continue;
			pc.removeKnownObject(this);
			pc.sendPackets(S_RemoveObject.get(this));
		}
		removeAllKnownObjects();
	}
	
	class poisonTickTimer implements Runnable {
		private RaidPoisonInstance _poison;
		public poisonTickTimer(RaidPoisonInstance poison){
			_poison = poison;
		}
		@Override 
		public void run(){
			if(_destroyed)
				return;
			
			try{	
				for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
					if(pc == null || getMapId() != pc.getMapId() || getX() != pc.getX() || getY() != pc.getY() || pc.isDead()  || pc.isGhost() || pc.isGm())
						continue;
					
					int dmg = _rnd.nextInt(10) + 32;
					DamagePoison.doInfection(getOwner() == null ? _poison : getOwner(), pc, 3000, dmg);
				}
				GeneralThreadPool.getInstance().schedule(this, INTERVAL);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
}
