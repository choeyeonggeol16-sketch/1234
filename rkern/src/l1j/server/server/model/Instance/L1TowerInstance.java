/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.Instance;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1WarSpawn;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.world.siege.CCastle;
import l1j.server.server.model.world.siege.CCastleSiegeManager;
import l1j.server.server.templates.L1Npc;

public class L1TowerInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
	private static final int ADEN_SUB_TOWER_FIRST = 81190;
	private static final int ADEN_SUB_TOWER_LAST = 81193;

	public L1TowerInstance(L1Npc template) {
		super(template);
	}

	protected L1Character _lastattacker;
	private int _castle_id;
	protected int _crackStatus;

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null)
			return;

		

		if (SC_WorldPutObject.get(
				perceivedFrom.getNetConnection(),
				this)) {
			perceivedFrom.addKnownObject(this);
		}
	}

	@Override
	public void onAction(L1PcInstance player) {
		if (getCurrentHp() > 0 && !isDead()) {

			CAttackAction.attackPcNpc(player, this, true);
		}
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) {

		if (getNpcId() == 65007) { // 클라우디아 수호탑

			if (getCurrentHp() > 0 && !isDead()) {

				int newHp = applyNpcHpDelta(-(long) damage);

				if (newHp == 0 && !isDead()) {
                    if (Die()) {
                        try {
                            setStatus(36);
                            _lastattacker = attacker;
                            _crackStatus = 0;
                        } finally {
                            DeathFinished(false);
                        }
                    }
				}

				if (newHp > 0) {



					// 몹스폰
					if ((getMaxHp() * 1 / 5) > getCurrentHp()) {

						if (_crackStatus != 4) {

							SC_DoActionGFX.broadcast(
									this,
									35);

							setStatus(35);
							_crackStatus = 4;
						}

					} else if ((getMaxHp() * 2 / 5) > getCurrentHp()) {

						if (_crackStatus != 3) {

							SC_DoActionGFX.broadcast(
									this,
									34);

							setStatus(34);
							_crackStatus = 3;
						}

					} else if ((getMaxHp() * 3 / 5) > getCurrentHp()) {

						if (_crackStatus != 2) {

							SC_DoActionGFX.broadcast(
									this,
									33);

							setStatus(33);
							_crackStatus = 2;
						}

					} else if ((getMaxHp() * 4 / 5) > getCurrentHp()) {

						if (_crackStatus != 1) {

							SC_DoActionGFX.broadcast(
									this,
									32);

							setStatus(32);
							_crackStatus = 1;
						}
					}
				}

            }
		}

		if (_castle_id == 0) {

			if (isSubTower()) {
				_castle_id = L1CastleLocation.ADEN_CASTLE_ID;

			} else {
				_castle_id = L1CastleLocation.getCastleId(
						getX(),
						getY(),
						getMapId());
			}
		}

		CCastle war = _castle_id > 0 ? CCastleSiegeManager.getInstance().get(_castle_id) : null;
		if (war != null && war.IsUnderSiege()) {

			L1PcInstance pc = null;

			if (attacker instanceof L1PcInstance) {

				pc = (L1PcInstance) attacker;

			} else if (attacker instanceof L1CompanionInstance) {

				pc = ((L1CompanionInstance) attacker).get_master();

			} else if (attacker instanceof L1SummonInstance summon
					&& summon.getMaster() instanceof L1PcInstance player) {
				pc = player;
			}

			if (pc == null) {
				return;
			}

			if (!pc.isGm()) {
				L1Clan offense = pc.getClan();
				if (offense == null || offense.getCurrentWar() != war
						|| war.getOffenseClan(offense.getClanId()) != offense) {
					return;
				}
			}

			if (_castle_id == L1CastleLocation.ADEN_CASTLE_ID
					&& !isSubTower() && !hasDestroyedAdenSubTowers()) {
				return;
			}

			if (getCurrentHp() > 0 && !isDead()) {

				int newHp = applyNpcHpDelta(-(long) damage);

				if (newHp == 0 && !isDead()) {

					onDeath(attacker);
				}

				if (newHp > 0) {



					if ((getMaxHp() * 1 / 4) > getCurrentHp()) {

						if (_crackStatus != 3) {

							SC_DoActionGFX.broadcast(
									this,
									ActionCodes.ACTION_TowerCrack3);

							setStatus(
									ActionCodes.ACTION_TowerCrack3);

							setStatus(
									ActionCodes.ACTION_TowerCrack3);

							_crackStatus = 3;
						}

					} else if ((getMaxHp() * 2 / 4) > getCurrentHp()) {

						if (_crackStatus != 2) {

							SC_DoActionGFX.broadcast(
									this,
									ActionCodes.ACTION_TowerCrack2);

							setStatus(
									ActionCodes.ACTION_TowerCrack2);

							setStatus(
									ActionCodes.ACTION_TowerCrack2);

							_crackStatus = 2;
						}

					} else if ((getMaxHp() * 3 / 4) > getCurrentHp()) {

						if (_crackStatus != 1) {

							SC_DoActionGFX.broadcast(
									this,
									ActionCodes.ACTION_TowerCrack1);

							setStatus(
									ActionCodes.ACTION_TowerCrack1);

							setStatus(
									ActionCodes.ACTION_TowerCrack1);

							_crackStatus = 1;
						}
					}
				}

            }
		}
	}

	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
	}

	@Override
	protected void onDeath(L1Character lastAttacker) {
        if (!Die()) return;
        try {
            setStatus(ActionCodes.ACTION_TowerDie);
            _lastattacker = lastAttacker;
            _crackStatus = 0;
            SC_DoActionGFX.broadcast(this, ActionCodes.ACTION_TowerDie);
            if (!isSubTower()) {
                L1WarSpawn warspawn = new L1WarSpawn();
                warspawn.SpawnCrown(_castle_id);
            }
        } finally {
            DeathFinished(false);
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

		CWorld.getInstance()
				.removeVisibleObject(this);

		CWorld.getInstance()
				.removeObject(this);

		try {
		try (var sharedRemove = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
		        SC_RemoveObject.nativeWriter(getId()), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
		for (L1PcInstance pc
				: CWorld.getInstance()
						.getRecognizePlayer(this)) {

			pc.removeKnownObject(this);
			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(sharedRemove, pc);
		}
		}

		} finally {
			removeAllKnownObjects();
		}
	}

	private static boolean hasDestroyedAdenSubTowers() {
		CWorld world = CWorld.getInstance();
		int destroyed = 0;
		for (int templateId = ADEN_SUB_TOWER_FIRST; templateId <= ADEN_SUB_TOWER_LAST; templateId++) {
			for (L1NpcInstance npc : world.getNpcsByTemplateId(templateId)) {
				if (npc instanceof L1TowerInstance tower && tower.isSubTower() && tower.isDead()
						&& ++destroyed == 3) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isSubTower() {
		int templateId = getNpcTemplate().get_npcId();
		return templateId >= ADEN_SUB_TOWER_FIRST && templateId <= ADEN_SUB_TOWER_LAST;
	}
}
