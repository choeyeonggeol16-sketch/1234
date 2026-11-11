package l1j.server.server.model;

import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.GameTimeClock;
import l1j.server.server.serverpackets.S_PinkName;

public class L1PinkName {

	private L1PinkName() {
	}

	static class PinkNameTimer implements Runnable {
		private L1PcInstance _attacker = null;
		private int _nextPinkNameSendTime;

		public PinkNameTimer(L1PcInstance attacker) {
			_attacker = attacker;
			_nextPinkNameSendTime = GameTimeClock.getInstance().getGameTime().getSeconds() + _attacker.getPinkNameTime() + 1;
		}

		@Override
		public void run() {

			if (!_attacker.isPinkName()) {
				_attacker.setPinkName(true);

				_attacker.sendPackets(new S_PinkName(_attacker.getId(), _attacker.getPinkNameTime()));

				if (!_attacker.isGmInvis()) {
					_attacker.broadcastPacket(new S_PinkName(_attacker.getId(), _attacker.getPinkNameTime()));
				}
			}

			if (_attacker.isDead()) {
				_attacker.SetPinkNameTime(0);
			} else if (_attacker.DecrementPinkNameTime() > 0) {
				int currentTime = GameTimeClock.getInstance().getGameTime().getSeconds();

				if (_nextPinkNameSendTime < currentTime) {
					_attacker.sendPackets(new S_PinkName(_attacker.getId(), _attacker.getPinkNameTime()));

					if (!_attacker.isGmInvis()) {
						_attacker.broadcastPacket(new S_PinkName(_attacker.getId(), _attacker.getPinkNameTime()));
					}

					_nextPinkNameSendTime = currentTime + _attacker.getPinkNameTime() + 1;
				}

				GeneralThreadPool.getInstance().schedule(this, 1000);
				return;
			}

			stopPinkName(_attacker);
		}

		private void stopPinkName(L1PcInstance attacker) {
			attacker.setPinkName(false);
			attacker.sendPackets(new S_PinkName(attacker.getId(), 0));
			attacker.broadcastPacket(new S_PinkName(attacker.getId(), 0));
		}
	}
	
	public static void onAction(L1PcInstance pc, L1Character cha) {
		if (pc == null || cha == null) {
			return;
		}
		if (!(cha instanceof L1PcInstance)) {
			return;
		}
		L1PcInstance attacker = (L1PcInstance) cha;
		if (pc.getId() == attacker.getId()) {
			return;
		}
		boolean isNowWar = false;
		int castleId = L1CastleLocation.getCastleIdByArea(attacker);
		if (castleId != 0) {
			isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
		}

		if (pc.getZoneType() == 0 && attacker.getZoneType() == 0 && isNowWar == false) {
			if (attacker.SetPinkNameTime(20) == 0) {
				attacker.setPinkName(true);
				attacker.sendPackets(new S_PinkName(attacker.getId(), 20));
				if (!attacker.isGmInvis()) {
					attacker.broadcastPacket(new S_PinkName(attacker.getId(), 20));
				}
				PinkNameTimer pink = new PinkNameTimer(attacker);
				GeneralThreadPool.getInstance().execute(pink);
			}
		}
	}
}
