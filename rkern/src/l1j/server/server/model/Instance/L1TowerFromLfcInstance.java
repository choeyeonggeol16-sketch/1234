package l1j.server.server.model.Instance;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.templates.L1Npc;

/** 2016.11.26 MJ 앱센터 LFC **/
public class L1TowerFromLfcInstance extends L1TowerInstance {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public L1TowerFromLfcInstance(L1Npc template) {
		super(template);
	}
	
	public void init() {
	//	getMap().setPassable(getX(), getY(), false);
	}
	
	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		if (!(attacker instanceof L1PcInstance) || attacker == null)
			return;
		
		L1PcInstance pc = (L1PcInstance) attacker;

		if (getCurrentHp() > 0 && !isDead()) {
			int newHp = applyNpcHpDelta(-(long) damage);

			if (newHp == 0 && !isDead()) {
				_crackStatus = 0;
				setDeath(attacker);

			} else if (newHp > 0) {


				if ((getMaxHp() * 1 / 4) > getCurrentHp()) {
					if (_crackStatus != 3) {
						SC_DoActionGFX.broadcast(
								this,
								ActionCodes.ACTION_TowerCrack3);

						setStatus(ActionCodes.ACTION_TowerCrack3);
						_crackStatus = 3;
					}

				} else if ((getMaxHp() * 2 / 4) > getCurrentHp()) {
					if (_crackStatus != 2) {
						SC_DoActionGFX.broadcast(
								this,
								ActionCodes.ACTION_TowerCrack2);

						setStatus(ActionCodes.ACTION_TowerCrack2);
						_crackStatus = 2;
					}

				} else if ((getMaxHp() * 3 / 4) > getCurrentHp()) {
					if (_crackStatus != 1) {
						SC_DoActionGFX.broadcast(
								this,
								ActionCodes.ACTION_TowerCrack1);

						setStatus(ActionCodes.ACTION_TowerCrack1);
						_crackStatus = 1;
					}
				}
			}

        }
	}
	
	private void setDeath(L1Character attacker) {
		setDead(true);
		setStatus(ActionCodes.ACTION_TowerDie);

	//	getMap().setPassable(getX(), getY(), true);

		_lastattacker = attacker;
		sendDead();
	}
	
	public void sendDead() {
		SC_DoActionGFX.broadcast(
				this,
				ActionCodes.ACTION_TowerDie);
	}
}