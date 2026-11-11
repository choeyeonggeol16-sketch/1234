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

import java.util.ArrayList;
import java.util.Random; //## [A150] 파이어월 데미지 적용
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Skills;
import l1j.server.server.ActionCodes; //## [A150] 파이어월 데미지 적용
import l1j.server.server.datatables.SkillsTable; //## [A150] 파이어월 데미지 적용
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.serverpackets.S_DoActionGFX; //## [A150] 파이어월 데미지 적용
import l1j.server.server.serverpackets.S_RemoveObject;
import static l1j.server.server.model.skill.SkillId.*; //#

public class EffectInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public EffectInstance(L1Npc template) {
		super(template);

	}

	@Override
	public void onAction(L1PcInstance pc) {
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

	public void doFire() {
		Fire fire = new Fire(this);
		GeneralThreadPool.getInstance().execute(fire);
	}

	private class Fire implements Runnable {
		private EffectInstance _instance;
		private L1PcInstance pc = L1PcInstance.getFirewallUser(); //## [A150] 파이어월 데미지 적용

		public Fire(EffectInstance instance) {
			_instance = instance;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				try {
//					ArrayList<L1Object> objs = L1World.getInstance()
//							.getVisibleObjects(_instance, 0);
					// 여기서 데미지 처리를 넣는다
//## [A150] 파이어월 데미지 적용
					L1Skills firewall = SkillsTable.getInstance().getTemplate(58);
					Random random = new Random();
					int value = firewall.getDamageValue();
					int rnd = random.nextInt(100) + 1;
					int dmg = 0;
					int dmg1 = 0;
					int dmg2 = 0;

					dmg1 = pc.getSp() + value - 9;
					if (dmg1 > 40) {
						dmg1 = 40;
					}

					ArrayList<L1Object> objs = L1World.getInstance()
					.getVisibleObjects(_instance, 0);
					for (L1Object obj : objs) {
						if (obj instanceof MonsterInstance) {
							MonsterInstance target = (MonsterInstance) obj;

							int targetLvl = target.getLevel();
							if (targetLvl < 10) {
								targetLvl = 10;
							}
							
							dmg2 = dmg1 - (targetLvl / 10);
							if (dmg2 < 4) {
								dmg2 = 4;
							}
							
							dmg = dmg2 / 2;
							if (rnd > 50) {
								dmg /= 2; 
							}
							
							if (target.hasSkillEffect(어스_바인드) // 어스 바인드
									|| target.hasSkillEffect(아이스_랜스) // 아이스 랜스
									|| target.hasSkillEffect(프리징_블리자드)) { // 프리징 블리자드 
								target.receiveDamage(pc, 0); // 데미지 없음
							} else {
								target.receiveDamage(pc, dmg);
								target.broadcastPacket(S_DoActionGFX.get(target.getId(),ActionCodes.ACTION_Damage));
							}
						}
					}
					Thread.sleep(2000);
				} catch (InterruptedException ignore) {
					// ignore
				}
			}
			deleteMe();
		}
	}
}
