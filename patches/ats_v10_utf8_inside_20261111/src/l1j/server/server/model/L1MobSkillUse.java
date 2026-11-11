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
package l1j.server.server.model;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.ActionCodes.EActionCodes;
import l1j.server.Templates.L1MobSkill;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Skills;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.MobSkillTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_WorldObject;

public class L1MobSkillUse {
	private static Logger _log = Logger.getLogger(L1MobSkillUse.class.getName());

	private L1MobSkill _mobSkillTemplate = null;

	private L1NpcInstance _attacker = null;

	private L1Character _target = null;

	private Random _rnd = new Random();

	private long _sleepTime = 0;

	private int _skillUseCount[];
	
	public void dispose(){
		_mobSkillTemplate 	= null;
		_attacker 			= null;
		_target				= null;
	}

	public L1MobSkillUse(L1NpcInstance npc) {
		_sleepTime = 0;

		_mobSkillTemplate = MobSkillTable.getInstance().getTemplate(npc.getNpcTemplate().get_npcId());
		if (_mobSkillTemplate == null) {
			return;
		}
		_attacker = npc;
		_skillUseCount = new int[getMobSkillTemplate().getSkillSize()];
	}

	private int getSkillUseCount(int idx) {
		return _skillUseCount[idx];
	}

	private void skillUseCountUp(int idx) {
		_skillUseCount[idx]++;
	}

	public void resetAllSkillUseCount() {
		if (getMobSkillTemplate() == null) {
			return;
		}

		for (int i = 0; i < getMobSkillTemplate().getSkillSize(); i++) {
			_skillUseCount[i] = 0;
		}
	}

	public long getSleepTime() {
		return _sleepTime;
	}

	public void setSleepTime(long i) {
		_sleepTime = i;
	}

	public L1MobSkill getMobSkillTemplate() {
		return _mobSkillTemplate;
	}

	/*
	 * 스킬 공격 스킬 공격 가능하면 true를 돌려준다. 공격할 수 없으면, false를 돌려주어, 통상 공격을 실시한다.
	 */
	public boolean skillUse(L1Character tg) {
		if (_mobSkillTemplate == null) {
			return false;
		}
		_target = tg;

		int type;
		type = getMobSkillTemplate().getType(0);

		if (type == L1MobSkill.TYPE_NONE) {
			return false;
		}

		int i = 0;
		for (i = 0; i < getMobSkillTemplate().getSkillSize()
				&& getMobSkillTemplate().getType(i) != L1MobSkill.TYPE_NONE; i++) {

			// changeTarget가 설정되어 있는 경우, 타겟의 교체
			int changeType = getMobSkillTemplate().getChangeTarget(i);
			if (changeType > 0) {
				_target = changeTarget(changeType, i);
			} else {
				// 설정되지 않은 경우는 본래의 타겟으로 한다
				_target = tg;
			}

			if (isSkillUseble(i) == false) {
				continue;
			}

			type = getMobSkillTemplate().getType(i);
			if (type == L1MobSkill.TYPE_PHYSICAL_ATTACK) {
				// 물리 공격
				if (physicalAttack(i) == true) {
					skillUseCountUp(i);
					return true;
				}
			} else if (type == L1MobSkill.TYPE_MAGIC_ATTACK) {
				// 마법 공격
				if (magicAttack(i) == true) {
					skillUseCountUp(i);
					return true;
				}
			} else if (type == L1MobSkill.TYPE_SUMMON) {
				// 서먼 
				if (summon(i) == true) {
					skillUseCountUp(i);
					return true;
				}
			} else if (type == L1MobSkill.TYPE_POLY) {
				// 강제 변신시킨다
				if (poly(i) == true) {
					skillUseCountUp(i);
					return true;
				}
			}
		}

		return false;
	}

	private boolean summon(int idx) {
		int summonId = getMobSkillTemplate().getSummon(idx);
		int min = getMobSkillTemplate().getSummonMin(idx);
		int max = getMobSkillTemplate().getSummonMax(idx);
		int count = 0;

		if (summonId == 0) {
			return false;
		}

		count = _rnd.nextInt(max) + min;
		mobspawn(summonId, count);

		// 매직 스퀘어의 표시
		_attacker.broadcastPacket(S_SkillSound.get(_attacker.getId(), 761));

		// 마법을 사용하는 동작의 effect
		_attacker.broadcastPacket(S_DoActionGFX.get(_attacker.getId(), ActionCodes.ACTION_SkillBuff));

		_sleepTime = _attacker.getCurrentSpriteInterval(EActionCodes.spell_nodir);
		return true;
	}

	/*
	 * 15 셀 이내에서 쏘아 맞히고 선이 통과하는 PC를 지정한 monster에게 강제 변신시킨다. 대PC 밖에 사용할 수 없다.
	 */
	private boolean poly(int idx) {
		int polyId = getMobSkillTemplate().getPolyId(idx);
		boolean usePoly = false;

		if (polyId == 0) {
			return false;
		}

		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(_attacker)) {
			if (pc.isDead()) { // 사망하고 있다
				continue;
			}
			if (pc.isGhost()) {
				continue;
			}
			if (pc.isGmInvis()) {
				continue;
			}
			if (_attacker.glanceCheck(pc.getX(), pc.getY()) == false) {
				continue; // 쏘아 맞히고 선이 통하지 않다
			}

			int npcId = _attacker.getNpcTemplate().get_npcId();
			switch (npcId) {
			case 81082: // 야히의 경우
				pc.getInventory().takeoffEquip(945); // 소의 polyId로 장비를 전부 제외한다.
				break;
			default:
				break;
			}
			L1PolyMorph.doPoly(pc, polyId, 1800,
					L1PolyMorph.MORPH_BY_LOGIN);

			usePoly = true;
		}
		if (usePoly) {
			// 변신시켰을 경우, 오렌지의 기둥을 표시한다.
			_attacker.broadcastPacket(S_SkillSound.get(_attacker.getId(), 230));
			// 마법을 사용하는 동작의 effect
			_attacker.broadcastPacket(S_DoActionGFX.get(_attacker.getId(), ActionCodes.ACTION_SkillBuff));

			_sleepTime = _attacker.getCurrentSpriteInterval(EActionCodes.spell_nodir);
		}

		return usePoly;
	}

	private boolean magicAttack(int idx) {
		SkillUse skillUse = new SkillUse();
		int skillid = getMobSkillTemplate().getSkillId(idx);
		boolean canUseSkill = false;

		if (skillid > 0) {
			canUseSkill = skillUse.checkUseSkill(null, skillid, _target.getId(), _target.getX(), _target.getY(), null,0, SkillUse.TYPE_NORMAL, _attacker);
		}
		if (canUseSkill == true) {
			if (getMobSkillTemplate().getLeverage(idx) > 0) {
				skillUse.setLeverage(getMobSkillTemplate().getLeverage(idx));
			}
			skillUse.handleCommands(null, skillid, _target.getId(), _target.getX(), _target.getY(), null, 0,SkillUse.TYPE_NORMAL, _attacker);
			// 사용 스킬에 의한 sleepTime의 설정
			L1Skills skill = SkillsTable.getInstance().getTemplate(skillid);
			if (skill.getTarget().equals("attack") && skillid != 18) { // 유방향 마법
				_sleepTime = _attacker.getCurrentSpriteInterval(EActionCodes.spell_dir);
			} else { // 무방향 마법
				_sleepTime = _attacker.getCurrentSpriteInterval(EActionCodes.spell_nodir);
			}
			return true;
		}
		return false;
	}

	/*
	 * 물리 공격
	 */
	private boolean physicalAttack(int idx) {
		Map<Integer, Integer> targetList = new ConcurrentHashMap<Integer, Integer>();
		int areaWidth = getMobSkillTemplate().getAreaWidth(idx);
		int areaHeight = getMobSkillTemplate().getAreaHeight(idx);
		int range = getMobSkillTemplate().getRange(idx);
		int actId = getMobSkillTemplate().getActid(idx);
		int gfxId = getMobSkillTemplate().getGfxid(idx);

		// 레인지외
		if (_attacker.getLocation().getTileLineDistance(_target.getLocation()) > range) {
			return false;
		}

		// 장애물이 있는 경우 공격 불가능
		if (!_attacker.glanceCheck(_target.getX(), _target.getY())) {
			return false;
		}

		_attacker.setHeading(_attacker.targetDirection(_target.getX(), _target.getY())); // 방향세트

		if (areaHeight > 0) {
			// 범위 공격
			ArrayList<L1Object> objs = L1World.getInstance().getVisibleBoxObjects(_attacker, _attacker.getHeading(),
					areaWidth, areaHeight);

			for (L1Object obj : objs) {
				if (!(obj instanceof L1Character)) { // 타겟이 캐릭터 이외의 경우 아무것도 하지 않는다.
					continue;
				}

				L1Character cha = (L1Character) obj;
				if (cha.isDead()) { // 죽어있는 캐릭터는 대상외
					continue;
				}

				// 고우스트 상태는 대상외
				if (cha instanceof L1PcInstance) {
					if (((L1PcInstance) cha).isGhost()) {
						continue;
					}
				}

				// 장애물이 있는 경우는 대상외
				if (!_attacker.glanceCheck(cha.getX(), cha.getY())) {
					continue;
				}

				if (_target instanceof L1PcInstance || _target instanceof SummonInstance
						|| _target instanceof PetInstance) {
					// 대PC
					if (obj instanceof L1PcInstance && !((L1PcInstance) obj).isGhost()
							&& !((L1PcInstance) obj).isGmInvis() || obj instanceof SummonInstance
							|| obj instanceof PetInstance) {
						targetList.put(obj.getId(), 0);
					}
				} else {
					// 대NPC
					if (obj instanceof MonsterInstance) {
						targetList.put(obj.getId(), 0);
					}
				}
			}
		} else {
			// 단체 공격
			targetList.put(_target.getId(), 0); // 타겟만 추가
		}

		if (targetList.size() == 0) {
			return false;
		}

		Iterator<Integer> ite = targetList.keySet().iterator();
		EActionCodes actionCode = EActionCodes.attack;
		while (ite.hasNext()) {
			int targetId = ite.next();
			actionCode = EActionCodes.attack;
			Attack attack = new Attack(_attacker, (L1Character) L1World.getInstance().findObject(targetId));
			if (attack.calcHit()) {
				if (getMobSkillTemplate().getLeverage(idx) > 0) {
					attack.setLeverage(getMobSkillTemplate().getLeverage(idx));
				}
				attack.calcDamage();
			}
			if (actId > 0) {
				attack.setActId(actId);
			}
			// 공격 모션은 실제의 타겟으로 대해서만 실시한다
			if (targetId == _target.getId()) {
				if (gfxId > 0) {
					_attacker.broadcastPacket(S_SkillSound.get(_attacker.getId(), gfxId));
				}
				attack.action();
			}
			attack.commit();
		}

		_sleepTime = _attacker.getCurrentSpriteInterval(actionCode);
		return true;
	}

	/*
	 * 트리거의 조건만 체크
	 */
	private boolean isSkillUseble(int skillIdx) {
		boolean useble = false;

		if (getMobSkillTemplate().getTriggerRandom(skillIdx) > 0) {
			int chance = _rnd.nextInt(100) + 1;
			if (chance < getMobSkillTemplate().getTriggerRandom(skillIdx)) {
				useble = true;
			} else {
				return false;
			}
		}

		if (getMobSkillTemplate().getTriggerHp(skillIdx) > 0) {
			int hpRatio = (_attacker.getCurrentHp() * 100) / _attacker.getMaxHp();
			if (hpRatio <= getMobSkillTemplate().getTriggerHp(skillIdx)) {
				useble = true;
			} else {
				return false;
			}
		}

		if (getMobSkillTemplate().getTriggerCompanionHp(skillIdx) > 0) {
			L1NpcInstance companionNpc = searchMinCompanionHp();
			if (companionNpc == null) {
				return false;
			}

			int hpRatio = (companionNpc.getCurrentHp() * 100) / companionNpc.getMaxHp();
			if (hpRatio <= getMobSkillTemplate().getTriggerCompanionHp(skillIdx)) {
				useble = true;
				_target = companionNpc; // 타겟의 교체
			} else {
				return false;
			}
		}

		if (getMobSkillTemplate().getTriggerRange(skillIdx) != 0) {
			int distance = _attacker.getLocation().getTileLineDistance(_target.getLocation());

			if (getMobSkillTemplate().isTriggerDistance(skillIdx, distance)) {
				useble = true;
			} else {
				return false;
			}
		}

		if (getMobSkillTemplate().getTriggerCount(skillIdx) > 0) {
			if (getSkillUseCount(skillIdx) < getMobSkillTemplate().getTriggerCount(skillIdx)) {
				useble = true;
			} else {
				return false;
			}
		}
		return useble;
	}

	private L1NpcInstance searchMinCompanionHp() {
		L1NpcInstance npc;
		L1NpcInstance minHpNpc = null;
		int hpRatio = 100;
		int companionHpRatio;
		int family = _attacker.getNpcTemplate().get_family();

		for (L1Object object : L1World.getInstance().getVisibleObjects(_attacker)) {
			if (object instanceof L1NpcInstance) {
				npc = (L1NpcInstance) object;
				if (npc.getNpcTemplate().get_family() == family) {
					companionHpRatio = (npc.getCurrentHp() * 100) / npc.getMaxHp();
					if (companionHpRatio < hpRatio) {
						hpRatio = companionHpRatio;
						minHpNpc = npc;
					}
				}
			}
		}
		return minHpNpc;
	}

	private void mobspawn(int summonId, int count) {
		int i;

		for (i = 0; i < count; i++) {
			mobspawn(summonId);
		}
	}

	private void mobspawn(int summonId) {
		try {
			L1Npc spawnmonster = NpcTable.getInstance().getTemplate(summonId);
			if (spawnmonster != null) {
				L1NpcInstance mob = null;
				try {
					String implementationName = spawnmonster.getImpl();
					Constructor<?> _constructor = Class
							.forName((new StringBuilder()).append("l1j.server.server.model.Instance.")
									.append(implementationName).append("Instance").toString())
							.getConstructors()[0];
					mob = (L1NpcInstance) _constructor.newInstance(new Object[] { spawnmonster });
					mob.setId(IdFactory.getInstance().nextId());
					L1Location loc = _attacker.getLocation().randomLocation(8, false);
					int heading = _rnd.nextInt(8);
					mob.setX(loc.getX());
					mob.setY(loc.getY());
					mob.setHomeX(loc.getX());
					mob.setHomeY(loc.getY());
					short mapid = _attacker.getMapId();
					mob.setMap(mapid);
					mob.setHeading(heading);
					L1World.getInstance().storeObject(mob);
					L1World.getInstance().addVisibleObject(mob);
					L1Object object = L1World.getInstance().findObject(mob.getId());
					MonsterInstance newnpc = (MonsterInstance) object;
					newnpc.set_storeDroped(true); // 소환된 monster는 드롭 없음
					if (summonId == 45061 // 카즈드스파르트이
							|| summonId == 45161 // 스파르트이
							|| summonId == 45181 // 스파르트이
							|| summonId == 45455) { // 데드 리스 펄 토이
						newnpc.broadcastPacket(S_DoActionGFX.get(newnpc.getId(), ActionCodes.ACTION_Hide));
						newnpc.setStatus(13);
						newnpc.broadcastPacket(S_WorldObject.npc(newnpc));
						newnpc.broadcastPacket(S_DoActionGFX.get(newnpc.getId(), ActionCodes.ACTION_Appear));
						newnpc.setStatus(0);
						newnpc.broadcastPacket(S_WorldObject.npc(newnpc));
					}
					newnpc.onNpcAI();
					newnpc.getLight().turnOnOffLight();
					newnpc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	// 현재 ChangeTarget로 유효한 값은 2,3만
	private L1Character changeTarget(int type, int idx) {
		L1Character target;

		switch (type) {
		case L1MobSkill.CHANGE_TARGET_ME:
			target = _attacker;
			break;
		case L1MobSkill.CHANGE_TARGET_RANDOM:
			// 타겟 후보의 선정
			List<L1Character> targetList = new ArrayList<L1Character>();
			for (L1Object obj : L1World.getInstance().getVisibleObjects(_attacker)) {
				if (obj instanceof L1PcInstance || obj instanceof PetInstance || obj instanceof SummonInstance) {
					L1Character cha = (L1Character) obj;

					int distance = _attacker.getLocation().getTileLineDistance(cha.getLocation());

					// 발동 범위외의 캐릭터는 대상외
					if (!getMobSkillTemplate().isTriggerDistance(idx, distance)) {
						continue;
					}

					// 장애물이 있는 경우는 대상외
					if (!_attacker.glanceCheck(cha.getX(), cha.getY())) {
						continue;
					}

					if (!_attacker.getHateList().containsKey(cha)) { // 헤이트가 없는 경우 대상외
						continue;
					}

					if (cha.isDead()) { // 죽어있는 캐릭터는 대상외
						continue;
					}

					// 고우스트 상태는 대상외
					if (cha instanceof L1PcInstance) {
						if (((L1PcInstance) cha).isGhost()) {
							continue;
						}
					}
					targetList.add((L1Character) obj);
				}
			}

			if (targetList.size() == 0) {
				target = _target;
			} else {
				int randomSize = targetList.size() * 100;
				int targetIndex = _rnd.nextInt(randomSize) / 100;
				target = targetList.get(targetIndex);
			}
			break;

		default:
			target = _target;
			break;
		}
		return target;
	}
}
