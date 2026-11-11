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

import l1j.server.Templates.L1Npc;
import l1j.server.WarSystem.CastleWar;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1WarSpawn;
import l1j.server.server.model.L1World;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_WorldObject;

public class L1TowerInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	public L1TowerInstance(L1Npc template) {
		super(template);
	}

	private L1Character _lastattacker;
	private int _castle_id;
	private int _crackStatus;

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null)
			return;
		perceivedFrom.addKnownObject(this);
		perceivedFrom.sendPackets(S_WorldObject.npc(this));
		if (isDead()) {
			perceivedFrom.sendPackets(S_DoActionGFX.get(getId(),ActionCodes.ACTION_TowerDie));
		} else {
			if ((getMaxHp() * 1 / 4) > getCurrentHp()) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_TowerCrack3));
			} else if ((getMaxHp() * 2 / 4) > getCurrentHp()) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_TowerCrack2));
			} else if ((getMaxHp() * 3 / 4) > getCurrentHp()) {
				perceivedFrom.sendPackets(S_DoActionGFX.get(getId(), ActionCodes.ACTION_TowerCrack1));
			}
		}
	}

	@Override
	public void onAction(L1PcInstance player) {
		if (getCurrentHp() > 0 && !isDead()) {
			Attack attack = new Attack(player, this);
			if (attack.calcHit()) {
				attack.calcDamage();
			}
			attack.action();
			attack.commit();
		}
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) { // 공격으로 HP를 줄일 때는 여기를 사용
		if (_castle_id == 0) { // 초기설정으로 좋지만 좋은 장소가 없다
			_castle_id = L1CastleLocation.getCastleId(getX(), getY(),
					getMapId());
		}
		if (_castle_id > 0 && CastleWarBusiness.getInstance().isNowWar(_castle_id)) { // 전쟁 시간내
			L1PcInstance pc = null;
			if (attacker instanceof L1PcInstance) {
				pc = (L1PcInstance) attacker;
			} else if (attacker instanceof PetInstance) {
				pc = (L1PcInstance) ((PetInstance) attacker).getMaster();
			} else if (attacker instanceof SummonInstance) {
				pc = (L1PcInstance) ((SummonInstance) attacker).getMaster();
			}
			if (pc == null) {
				return;
			}
			
			CastleWar war = CastleWarBusiness.getInstance().get(_castle_id);
			L1Clan offense = pc.getClan();		
			if(!pc.isGm()){
				if(offense == null || war.getOffenseClan(offense.getClanId()) == null)
					return;
			}

			if (getCurrentHp() > 0 && !isDead()) {
				int newHp = getCurrentHp() - damage;
				if (newHp <= 0 && !isDead()) {
					setCurrentHpDirect(0);
					setDead(true);
					_lastattacker = attacker;
					_crackStatus = 0;
					Death death = new Death();
					GeneralThreadPool.getInstance().execute(death);
					// Death(attacker);
				}
				if (newHp > 0) {
					setCurrentHp(newHp);
					if ((getMaxHp() * 1 / 4) > getCurrentHp()) {
						if (_crackStatus != 3) {
							broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_TowerCrack3));
							_crackStatus = 3;
						}
					} else if ((getMaxHp() * 2 / 4) > getCurrentHp()) {
						if (_crackStatus != 2) {
							broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_TowerCrack2));
							_crackStatus = 2;
						}
					} else if ((getMaxHp() * 3 / 4) > getCurrentHp()) {
						if (_crackStatus != 1) {
							broadcastPacket(S_DoActionGFX.get(getId(), ActionCodes.ACTION_TowerCrack1));
							_crackStatus = 1;
						}
					}
				}
			} else if (!isDead()) { // 만약을 위해
				setDead(true);
				_lastattacker = attacker;
				Death death = new Death();
				GeneralThreadPool.getInstance().execute(death);
				// Death(attacker);
			}
		}
	}

	@Override
	public void setCurrentHp(int i) {
		int currentHp = i;
		if (currentHp >= getMaxHp()) {
			currentHp = getMaxHp();
		}
		setCurrentHpDirect(currentHp);
	}

	class Death implements Runnable {
		L1Character lastAttacker = _lastattacker;
		L1Object object = L1World.getInstance().findObject(getId());
		L1TowerInstance npc = (L1TowerInstance) object;

		@Override
		public void run() {
			setCurrentHpDirect(0);
			setDead(true);
			int targetobjid = npc.getId();

			npc.getMap().setPassable(npc.getLocation(), true);

			npc.broadcastPacket(S_DoActionGFX.get(targetobjid, ActionCodes.ACTION_TowerDie));

			// 크라운을 spawn 한다
			L1WarSpawn warspawn = new L1WarSpawn();
			warspawn.SpawnCrown(_castle_id);
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

}
