package l1j.server.server.model.Instance;


import l1j.server.Templates.L1Npc;
import l1j.server.WarSystem.CastleWar;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;

public class CrownInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public CrownInstance(L1Npc template) {
		super(template);
	}

	@Override
	public synchronized void onAction(L1PcInstance player) {
		if(_destroyed)
			return;
		try{
			L1Clan clan = player.getClan();
			if(clan == null) {
				return;
			}		
			if(player.getClanid() == 0 || !player.isCrown()) {
				return;
			}
			clan = L1World.getInstance().getClan(player.getClanid());
			if(clan == null || clan.getLeaderId() != player.getId()) {
				return;
			}
			else if(clan.getCastleId() != 0){ 
				player.sendPackets(new S_ServerMessage(474));	// 이미 성을 보유
				return;
			}
			
			if(!checkRange(player)) {
				return;
			}
			// 크라운의 좌표로부터 castle_id를 취득
			CastleWar war = (CastleWar)clan.getCurrentWar();
			int castle_id = L1CastleLocation.getCastleId(getX(), getY(), getMapId());
			if(war == null || (war.getDefenseClan().getCastleId() != 0 && war.getDefenseClan().getCastleId() != castle_id)) {
				player.sendPackets(new S_SystemMessage("선포되어 있지 않습니다."));
				return;
			}
			
			war.updateDefense(clan);
			deleteMe();
		}catch(Exception e){
			e.printStackTrace();
		}
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
			pc.removeKnownObject(this);
			pc.sendPackets(S_RemoveObject.get(this));
		}
		removeAllKnownObjects();
	}

	private boolean checkRange(L1PcInstance pc) {
		return (getX() - 1 <= pc.getX() && pc.getX() <= getX() + 1 && getY() - 1 <= pc.getY()
				&& pc.getY() <= getY() + 1);
	}
}
