package l1j.server.server.model.world.instance.raid.util;

import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.templates.L1Npc;

@SuppressWarnings("serial")
public class L1RaidPoisonInstance extends L1NpcInstance{
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
		CWorld.getInstance().removeVisibleObject(this);
		CWorld.getInstance().removeObject(this);
		try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
				SC_RemoveObject.nativeWriter(getId()),
				Integer.MIN_VALUE, Integer.MAX_VALUE)) {
			for (L1PcInstance pc : CWorld.getInstance().getRecognizePlayer(this)) {
				if (pc == null) {
					continue;
				}
				pc.removeKnownObject(this);
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, pc);
			}
		}
		removeAllKnownObjects();
	}
	
}
