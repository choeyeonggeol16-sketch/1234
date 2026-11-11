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
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Templates.L1Npc;
import l1j.server.configure.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.DropTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ChangeHeading;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_NpcChatPacket;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.utils.CalcExp;

public class L1GuardianInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger _log = Logger.getLogger(L1GuardianInstance.class
			.getName());

	private Random _random = new Random();
	private L1GuardianInstance _npc = this;

	/**
	 * @param template
	 */
	public L1GuardianInstance(L1Npc template) {
		super(template);
		_restCallCount = new AtomicInteger(0);
	}

	@Override
	public void searchTarget() {
		// 타겟 검색
		L1PcInstance targetPlayer = null;

		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			if (pc == null || pc.getCurrentHp() <= 0 || pc.isDead()
					|| pc.isGm() || pc.isGhost()) {
				continue;
			}
			if (!pc.isInvisble() || getNpcTemplate().is_agrocoi()) { // 인비지체크
				if (!pc.isElf()) { // 요정 이외
					targetPlayer = pc;
					wideBroadcastPacket(new S_NpcChatPacket(this, "$804", 2));
					break;
				}
			}
		}
		if (targetPlayer != null) {
			_hateList.add(targetPlayer, 0);
			_target = targetPlayer;
		}
	}

	// 링크의 설정
	@Override
	public void setLink(L1Character cha) {
		if (cha != null && _hateList.isEmpty()) { // 타겟이 없는 경우만 추가
			_hateList.add(cha, 0);
			checkTarget();
		}
	}

	@Override
	public void onNpcAI() {
		if (isAiRunning()) {
			return;
		}
		setActived(false);
		startAI();
		startChat(CHAT_TIMING_APPEARANCE); // 채팅 시작
	}

	@Override
	public void onAction(L1PcInstance player) {
		if (this == null || player == null)
			return;
		if (player.getType() == 2 && player.getCurrentWeapon() == 0
				&& player.isElf()) {
			Attack attack = new Attack(player, this);

			if (attack.calcHit()) {
				if (getNpcTemplate().get_npcId() == 70848) { // 엔트
					int chance = _random.nextInt(100) + 1;
					if (chance <= 10) {
						player.getInventory().storeItem(40506, 1);
						player.sendPackets(new S_ServerMessage(143, "$755",
								"$794")); // \f1%0이%1를 주었습니다.
					} else if (chance <= 60 && chance > 10) {
						player.getInventory().storeItem(40507, 1);
						player.sendPackets(new S_ServerMessage(143, "$755",
								"$763")); // \f1%0이%1를 주었습니다.
					} else if (chance <= 70 && chance > 60) {
						player.getInventory().storeItem(40505, 1);
						player.sendPackets(new S_ServerMessage(143, "$755",
								"$770")); // \f1%0이%1를 주었습니다.
					}
				}
				if (getNpcTemplate().get_npcId() == 70850) { // 빵
					int chance = _random.nextInt(100) + 1;
					if (chance <= 30) {
						player.getInventory().storeItem(40519, 5);
						player.sendPackets(new S_ServerMessage(143, "$753",
								"$760" + " (" + 5 + ")")); // \f1%0이%1를 주었습니다.
					}
				}
				if (getNpcTemplate().get_npcId() == 70846) { // 아라크네
					int chance = _random.nextInt(100) + 1;
					if (chance <= 30) {
						player.getInventory().storeItem(40503, 1);
						player.sendPackets(new S_ServerMessage(143, "$752",
								"$769")); // \f1%0이%1를 주었습니다.
					}
				}
				attack.calcDamage();
			}
			attack.action();
			attack.commit();
		} else if (getCurrentHp() > 0 && !isDead()) {
			Attack attack = new Attack(player, this);
			if (attack.calcHit()) {
				attack.calcDamage();
			}
			attack.action();
			attack.commit();
		}
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (player == null || this == null)
			return;
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(
				getNpcTemplate().get_npcId());
		L1Object object = L1World.getInstance().findObject(getId());
		if (object == null) {
			return;
		}
		L1NpcInstance target = (L1NpcInstance) object;

		if (talking != null) {
			int pcx = player.getX(); // PC의 X좌표
			int pcy = player.getY(); // PC의 Y좌표
			int npcx = target.getX(); // NPC의 X좌표
			int npcy = target.getY(); // NPC의 Y좌표

			if (pcx == npcx && pcy < npcy) {
				setHeading(0);
			} else if (pcx > npcx && pcy < npcy) {
				setHeading(1);
			} else if (pcx > npcx && pcy == npcy) {
				setHeading(2);
			} else if (pcx > npcx && pcy > npcy) {
				setHeading(3);
			} else if (pcx == npcx && pcy > npcy) {
				setHeading(4);
			} else if (pcx < npcx && pcy > npcy) {
				setHeading(5);
			} else if (pcx < npcx && pcy == npcy) {
				setHeading(6);
			} else if (pcx < npcx && pcy < npcy) {
				setHeading(7);
			}
			broadcastPacket(S_ChangeHeading.get(this));

			if (player.getLawful() < -1000) { // 플레이어가 카오틱
				player.sendPackets(new S_NPCTalkReturn(talking, objid, 2));
			} else {
				player.sendPackets(new S_NPCTalkReturn(talking, objid, 1));
			}
			if (_restCallCount.getAndIncrement() == 0) {
				setRest(true);
			}

			GeneralThreadPool.getInstance().schedule(new RestMonitor(),
					REST_MILLISEC);
		}
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) { // 공격으로 HP를 줄일
																	// 때는 여기를 사용
		if (this == null || attacker == null)
			return;
		if (attacker instanceof L1PcInstance && damage > 0) {
			L1PcInstance pc = (L1PcInstance) attacker;
			if (pc.getType() == 2 && // 맨손이라면 데미지 없음
					pc.getCurrentWeapon() == 0) {
			} else {
				if (getCurrentHp() > 0 && !isDead()) {
					if (damage >= 0) {
						setHate(attacker, damage);
					}
					if (damage > 0) {
						removeSkillEffect(SkillId.포그_오브_슬리핑);
					}
					onNpcAI();
					// 동료의식을 가지는 monster의 타겟으로 설정
					serchLink(pc, getNpcTemplate().get_family());
					if (damage > 0) {
						pc.setPetTarget(this);
					}

					int newHp = getCurrentHp() - damage;
					if (newHp <= 0 && !isDead()) {
						setCurrentHpDirect(0);
						setDead(true);
						_lastattacker = attacker;
						Death death = new Death();
						GeneralThreadPool.getInstance().execute(death);
					}
					if (newHp > 0) {
						setCurrentHp(newHp);
					}
				} else if (!isDead()) { // 만약을 위해
					setDead(true);
					_lastattacker = attacker;
					Death death = new Death();
					GeneralThreadPool.getInstance().execute(death);
				}
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

		if (getMaxHp() > getCurrentHp()) {
			startHpRegeneration();
		}
	}

	@Override
	public void setCurrentMp(int i) {
		int currentMp = i;
		if (currentMp >= getMaxMp()) {
			currentMp = getMaxMp();
		}
		setCurrentMpDirect(currentMp);

		if (getMaxMp() > getCurrentMp()) {
			startMpRegeneration();
		}
	}

	private L1Character _lastattacker;

	class Death implements Runnable {
		L1Character lastAttacker = _lastattacker;

		public void run() {
			setDeathProcessing(true);
			setCurrentHpDirect(0);
			setDead(true);
			setStatus(ActionCodes.ACTION_Die);
			int targetobjid = getId();
			getMap().setPassable(getLocation(), true);
			broadcastPacket(S_DoActionGFX.get(targetobjid, ActionCodes.ACTION_Die));

			L1PcInstance player = null;
			if (lastAttacker instanceof L1PcInstance) {
				player = (L1PcInstance) lastAttacker;
			} else if (lastAttacker instanceof PetInstance) {
				player = (L1PcInstance) ((PetInstance) lastAttacker)
						.getMaster();
			} else if (lastAttacker instanceof SummonInstance) {
				player = (L1PcInstance) ((SummonInstance) lastAttacker)
						.getMaster();
			}
			if (player != null) {
				ArrayList<L1Character> targetList = _hateList
						.toTargetArrayList();
				ArrayList<Integer> hateList = _hateList.toHateArrayList();
				int exp = getExp();
				CalcExp.calcExp(player, targetobjid, targetList, hateList, exp);

				ArrayList<L1Character> dropTargetList = _dropHateList
						.toTargetArrayList();
				ArrayList<Integer> dropHateList = _dropHateList
						.toHateArrayList();
				try {
					DropTable.getInstance().dropShare(_npc, dropTargetList,
							dropHateList, player);
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
				player.addKarma((int) (getKarma() * Config.RATE_KARMA));
			}
			setDeathProcessing(false);

			setKarma(0);
			setExp(0);
			allTargetClear();

			startDeleteTimer();
		}
	}

	@Override
	public void onFinalAction(L1PcInstance player, String action) {
	}

	public void doFinalAction(L1PcInstance player) {
	}

	private static final long REST_MILLISEC = 10000;

	private AtomicInteger _restCallCount;

	public class RestMonitor implements Runnable {
		@Override
		public void run() {
			if (_restCallCount.decrementAndGet() == 0) {
				setRest(false);
			}
		}
	}
}
