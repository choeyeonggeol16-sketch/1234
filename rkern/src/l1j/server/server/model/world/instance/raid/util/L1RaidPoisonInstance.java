package l1j.server.server.model.world.instance.raid.util;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.poison.L1DamagePoison;
import l1j.server.server.templates.L1Npc;

@SuppressWarnings("serial")
public class L1RaidPoisonInstance extends L1NpcInstance{
	private static final int	INTERVAL	= 0x3e8;
	private L1MonsterInstance 	_owner;
	public L1RaidPoisonInstance(L1Npc template){
		super(template);
	}
	
	public void setOwner(L1MonsterInstance owner){
		_owner = owner;
	}
	
	public L1MonsterInstance getOwner(){
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
		try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
				SC_RemoveObject.nativeWriter(getId()),
				Integer.MIN_VALUE, Integer.MAX_VALUE)) {
			for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
				if (pc == null) {
					continue;
				}
				pc.removeKnownObject(this);
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, pc);
			}
		}
		removeAllKnownObjects();
	}
	
	class poisonTickTimer implements Runnable{
		private L1RaidPoisonInstance _poison;
		public poisonTickTimer(L1RaidPoisonInstance poison){
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
					
					int dmg = ThreadLocalRandom.current().nextInt(150) + 50;
					L1DamagePoison.doInfection(getOwner() == null ? _poison : getOwner(), pc, 3000, dmg, false,30000);
				}
				ThreadPool.getInstance().scheduleRequired(this, INTERVAL);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
}
