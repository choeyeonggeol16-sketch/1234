package l1j.server.server.model.character.skill;

import static l1j.server.server.model.character.skill.L1SkillId.*;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.util.Collection;

import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.templates.L1Skills;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_Liquor;
public class L1BuffUtil {

	private static final long ALL_BUFF_INTERVAL_MS = 2000L;

	private static final int[] BUFF_LIST_1 = {
			PHYSICAL_ENCHANT_STR, PHYSICAL_ENCHANT_DEX, BLESS_WEAPON, HASTE, //ADVANCE_SPIRIT,
			IRON_SKIN, BUFF_특별한공격축복, BUFF_특별한방어축복, BUFF_특별한성장축복, BRAVE_AURA,
			REDUCTION_ARMOR, DRESS_EVASION, AQUA_PROTECTER, NATURES_TOUCH, IMPACT,
			PATIENCE, INSIGHT, IllUSION_OGRE, IllUSION_DIAMONDGOLEM, IllUSION_LICH,
			CONCENTRATION, COMA_B, COUNTER_MAGIC, IMMUNE_TO_HARM
	};

	private static final int[] BUFF_LIST_2 = { FEATHER_BUFF_A };
	private static final int[] BUFF_LIST_3 = { God_buff };
	private static final int[] BUFF_LIST_4 = { COMA_B };
	private static final int[] EMPTY_BUFF_LIST = new int[0];

	private static final int ABORT_ALL_BUFF = Integer.MIN_VALUE;

	private L1BuffUtil() {}

	public static void haste(L1PcInstance pc, int timeMillis) {
		pc.setSkillEffect(STATUS_HASTE, timeMillis);
		final int objId = pc.getId();

		SC_SkillHaste.send(pc, objId, 1, timeMillis / 1000);
		SC_SkillHaste.broadcast(pc, objId, 1, 0);

		SC_SkillSound.send(pc, objId, 191);
		SC_SkillSound.broadcast(pc, objId, 191);

		pc.setMoveSpeed(1);
	}

	public static void barrier(L1PcInstance pc, int timeMillis) {
		pc.setSkillEffect(ABSOLUTE_BARRIER, timeMillis);
	}

	public static void cancelBarrier(L1PcInstance pc) {
		if (pc.hasSkillEffect(ABSOLUTE_BARRIER)) {
			pc.removeSkillEffect(ABSOLUTE_BARRIER);
		}
	}

	public static void brave(L1PcInstance pc, int timeMillis) {
		pc.setSkillEffect(STATUS_BRAVE, timeMillis);
		final int objId = pc.getId();

		SC_SkillBrave.send(pc, objId, 1, timeMillis / 1000);
		SC_SkillBrave.broadcast(pc, objId, 1, 0);

		SC_SkillSound.send(pc, objId, 751);
		SC_SkillSound.broadcast(pc, objId, 751);

		pc.setBraveSpeed(1);
	}

	public static void thirdSpeed(L1PcInstance pc, int timeMillis) {
		if (pc.hasSkillEffect(STATUS_DRAGON_PEARL)) {
			pc.killSkillEffectTimer(STATUS_DRAGON_PEARL);
		}

		pc.setSkillEffect(STATUS_DRAGON_PEARL, timeMillis);
		final int objId = pc.getId();

		SC_SkillSound.send(pc, objId, 8031);
		SC_SkillSound.broadcast(pc, objId, 8031);

		SC_Liquor.send(pc, objId, 8);
		SC_Liquor.broadcast(pc, objId, 8);
	}

	public static void useBuff(L1PcInstance pc, int skillId) {
		if (pc == null) return;
		L1SkillUse.cast(pc, skillId, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_GMBUFF);
	}

	public static void takeAllBuff(Collection<L1PcInstance> pcList, int buffType, int time) {
		if (pcList == null || pcList.isEmpty()) {
			return;
		}

		final int[] buffList = getBuffList(buffType);
		if (buffList.length == 0) {
			return;
		}

		final int durationSec = time * 60;

		for (L1PcInstance target : pcList) {
			if (target == null || target.isFishing()) continue;

			clearExclusiveBuff(target, buffType);

			if (target.isDead()) continue;

			if (target.noPlayerRobot) {
				sendRobotBuffSounds(target, buffList);
				continue;
			}

			for (int i = 0; i < buffList.length; i++) {
				final int originalSkillId = buffList[i];
				final int skillId = resolveSkillId(target, originalSkillId);

				if (skillId == ABORT_ALL_BUFF) {
					return;
				}

				if (skillId <= SKILLS_END) {
					final int targetId = target.getId();
					final int x = target.getX();
					final int y = target.getY();

					L1SkillUse.cast(target, skillId, targetId, x, y, null, durationSec, L1SkillUse.TYPE_LOGIN);

					final L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
					if (skill != null) {
						SC_PacketBox.send(target, SC_PacketBox.BUFFICON, skill.getCastGfx(), durationSec);
					}
				} else {
					useBuff(target, skillId);
				}
			}
		}
	}

	public static void startAllBuffThread(L1PcInstance user, String range, int buffType) {
		new AllBuffThread(user, range, buffType).begin();
	}

	public static final class AllBuffThread implements Runnable {

		private final L1PcInstance _user;
		private final String _range;
		private final int _buffType;
		private final int[] _buffList;
		private int _index;

		AllBuffThread(L1PcInstance user, String range, int buffType) {
			_user = user;
			_range = range;
			_buffType = buffType;
			_buffList = getBuffList(buffType);
		}

		@Override
		public void run() {
			try {
				if (_index >= _buffList.length) return;

				final int skillId = _buffList[_index];

				if (!applySkillToRange(skillId)) {
					return;
				}

				_index++;

				if (_index < _buffList.length) {
					ThreadPool.getInstance().scheduleRequired(this, ALL_BUFF_INTERVAL_MS);
				}

			} catch (Throwable t) {
				
			}
		}

		private boolean applySkillToRange(int skillId) {
			final L1World world = L1World.getInstance();

			if (_range == null) {
				return applySkillToCollection(world.getAllPlayers(), skillId);
			}
			if ("!".equals(_range)) {
				return applySkillToCollection(world.getRecognizePlayer(_user), skillId);
			}

			final L1PcInstance pc = world.getPlayer(_range);
			if (pc == null) {
				return false;
			}

			return applySkillToTarget(pc, skillId);
		}

		private boolean applySkillToCollection(Collection<L1PcInstance> pcList, int skillId) {
			if (pcList == null || pcList.isEmpty()) {
				return true;
			}

			for (L1PcInstance target : pcList) {
				if (!applySkillToTarget(target, skillId)) {
					return false;
				}
			}
			return true;
		}

		private boolean applySkillToTarget(L1PcInstance target, int skillId) {
			if (target == null || target.isFishing()) return true;

			clearExclusiveBuff(target, _buffType);

			if (target.isDead()) return true;

			final int eachSkillId = resolveSkillId(target, skillId);
			if (eachSkillId == ABORT_ALL_BUFF) {
				return false;
			}

			target.setBuffnoch(1);
			try {
				if (target.noPlayerRobot) {
					final L1Skills skill = SkillsTable.getInstance().getTemplate(eachSkillId);
					if (skill != null) {
						SC_SkillSound.broadcast(target, target.getId(), skill.getCastGfx());
					}
					return true;
				}

				if (eachSkillId <= SKILLS_END) {
					L1SkillUse.cast(target, eachSkillId, target.getId(), target.getX(), target.getY(), null, 0, L1SkillUse.TYPE_GMBUFF);
				} else {
					L1BuffUtil.useBuff(target, eachSkillId);
				}
				return true;

			} finally {
				target.setBuffnoch(0);
			}
		}

		private void begin() {
			if (_buffList.length == 0) return;
			ThreadPool.getInstance().execute(this);
		}
	}

	private static int[] getBuffList(int buffType) {
		switch (buffType) {
		case 1: return BUFF_LIST_1;
		case 2: return BUFF_LIST_2;
		case 3: return BUFF_LIST_3;
		case 4: return BUFF_LIST_4;
		default: return EMPTY_BUFF_LIST;
		}
	}

	/*
	 * 기존 코드의 전용 버프 제거 로직.
	 */
	private static void clearExclusiveBuff(L1PcInstance target, int buffType) {
		switch (buffType) {
		case 2:
			removeSkillEffectIfPresent(target, L1SkillId.FEATHER_BUFF_A);
			removeSkillEffectIfPresent(target, L1SkillId.FEATHER_BUFF_B);
			removeSkillEffectIfPresent(target, L1SkillId.FEATHER_BUFF_C);
			removeSkillEffectIfPresent(target, L1SkillId.FEATHER_BUFF_D);
			break;
		case 3:
			removeSkillEffectIfPresent(target, L1SkillId.God_buff);
			break;
		case 4:
			removeSkillEffectIfPresent(target, L1SkillId.COMA_B);
			break;
		default:
			break;
		}
	}

	private static void removeSkillEffectIfPresent(L1PcInstance target, int skillId) {
		if (target.hasSkillEffect(skillId)) {
			target.removeSkillEffect(skillId);
		}
	}

	private static int resolveSkillId(L1PcInstance target, int skillId) {
		if (skillId == BURNING_WEAPON && target.getCurrentWeapon() == 20) {
			return STORM_SHOT;
		}
		if (skillId == EARTH_WEAPON && target.getCurrentWeapon() == 20) {
			return AQUA_SHOT;
		}
//		if (skillId == ADVANCE_SPIRIT) {
//			if (target.isWarrior()) {
//				return GIGANTIC;
//			}
//			if(target.isKnight()) {
//				return PRIDE;	
//			}
//			if (target.hasSkillEffect(L1SkillId.어드밴스)) {
//				return ABORT_ALL_BUFF;
//			}
//		}
		return skillId;
	}

	private static void sendRobotBuffSounds(L1PcInstance target, int[] buffList) {
		final int objId = target.getId();
		for (int i = 0; i < buffList.length; i++) {
			final L1Skills skill = SkillsTable.getInstance().getTemplate(buffList[i]);
			if (skill == null) continue;

			SC_SkillSound.broadcast(target, objId, skill.getCastGfx());
		}
	}
}