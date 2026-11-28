package l1j.server.server.model.character.skill;

import java.util.Random;

import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.core.MagicEffectApplier;
import l1j.server.server.model.character.skill.core.SkillConsumeService;
import l1j.server.server.model.character.skill.core.SkillDelayPolicy;
import l1j.server.server.model.character.skill.core.SkillExecutor;
import l1j.server.server.model.character.skill.core.SkillGfxFacade;
import l1j.server.server.model.character.skill.core.SkillUseInitializer;
import l1j.server.server.model.character.skill.core.TargetRules;
import l1j.server.server.model.character.skill.helper.SkillTargetFilter;
import l1j.server.server.model.character.skill.helper.SkillTargetListBuilder;
import l1j.server.server.model.character.skill.helper.SkillTargetingHelper;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.character.skill.helper.SummonHelper;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.templates.L1Skills;

public class L1SkillUse {

	public static final int TYPE_NORMAL = 0;
	public static final int TYPE_LOGIN = 1;
	public static final int TYPE_SPELLSC = 2;
	public static final int TYPE_NPCBUFF = 3;
	public static final int TYPE_GMBUFF = 4;

	private static final int PC_PC = 1;
	private static final int PC_NPC = 2;
	private static final int NPC_PC = 3;
	private static final int NPC_NPC = 4;

	private L1Skills _skill;

	private int _skillId;
	private int _shockStunDuration;
	private int _getBuffIconDuration;
	private int _targetID;
	private int _mpConsume;
	private int _hpConsume;
	private int _targetX;
	private int _targetY;
	private int _earthBindDuration;
	private int _skillTime;
	private int _type;
	private int _itemobjid;
	private int _leverage = 10;
	private int _calcType;

	private boolean _isPK;
	private boolean _checkedUseSkill;
	private boolean _isCounterMagic = true;
	private boolean _isGlanceCheckFail;
	private boolean _isCriticalDamage;

	private L1Character _user;
	private L1Character _target;

	private L1PcInstance _player;
	private L1NpcInstance _npc;
	private L1NpcInstance _targetNpc;

	private static final int TARGET_INIT_CAPACITY = 8;
	private static final int TARGET_MAX_CAPACITY = 64;

	private L1Character[] _targets = new L1Character[TARGET_INIT_CAPACITY];
	private int _targetCount;
	private long _targetNoCalcMask;

	private short _bookmark_mapid;
	private int _bookmark_x;
	private int _bookmark_y;

	// =====================================================
	// Handler override values
	// =====================================================
	private int _hdDamage = Integer.MIN_VALUE;
	private int _hdDrainMana = Integer.MIN_VALUE;
	private int _hdHeal = Integer.MIN_VALUE;

	// =====================================================
	// Fail reason
	// =====================================================
	public enum FailReason {
		NONE,
		INVALID_TARGET
	}

	private FailReason _failReason = FailReason.NONE;

	public L1SkillUse() {}

	// =====================================================
	// Thread-local reuse pool
	// =====================================================
	private static final int REUSE_POOL_SLOTS = 8;

	private static final ThreadLocal<ReusePool> REUSE_POOL =
			ThreadLocal.withInitial(ReusePool::new);

	private static final class ReusePool {
		private final L1SkillUse[] slots = new L1SkillUse[REUSE_POOL_SLOTS];
		private int depth;

		L1SkillUse acquire() {
			final int index = depth++;

			// 정상적인 중첩 시전은 슬롯 재사용.
			if (index < slots.length) {
				L1SkillUse su = slots[index];
				if (su == null) {
					su = new L1SkillUse();
					slots[index] = su;
				}
				return su;
			}

			// 비정상적으로 깊은 중첩은 안전하게 임시 객체로 처리한다.
			return new L1SkillUse();
		}

		void release(L1SkillUse su) {
			try {
				if (su != null) {
					su.resetForReuse();
				}
			} finally {
				if (depth > 0) {
					depth--;
				}
			}
		}
	}

	// =====================================================
	// Basic
	// =====================================================
	public void setLeverage(int i) { _leverage = i; }
	public int getLeverage() { return _leverage; }

	private boolean isCheckedUseSkill() { return _checkedUseSkill; }
	public void setCheckedUseSkill(boolean flg) { _checkedUseSkill = flg; }

	// =====================================================
	// checkUseSkill
	// =====================================================
	public boolean checkUseSkill(
	        L1PcInstance player,
	        int skillid,
	        int target_id,
	        int x,
	        int y,
	        String message,
	        int time,
	        int type,
	        L1Character attacker) {

	    final boolean result =
	            SkillUseInitializer.INSTANCE.init(
	                    this,
	                    player,
	                    skillid,
	                    target_id,
	                    x,
	                    y,
	                    message,
	                    time,
	                    type,
	                    attacker);


	    return result;
	}

	// =====================================================
	// Check state
	// =====================================================
	public void resetStateForCheck() {
		clearTargets();

		_targetNpc = null;
		_target = null;
		_npc = null;
		_player = null;
		_user = null;

		_isPK = false;
		_isGlanceCheckFail = false;
		_isCriticalDamage = false;
		_isCounterMagic = true;

		_mpConsume = 0;
		_hpConsume = 0;

		_shockStunDuration = 0;
		_getBuffIconDuration = 0;
		_earthBindDuration = 0;

		_bookmark_mapid = 0;
		_bookmark_x = 0;
		_bookmark_y = 0;

		_itemobjid = 0;

		_failReason = FailReason.NONE;

		hdResetCommitValues();
	}

	/**
	 * ThreadLocal pool 반환용 완전 초기화.
	 * _targets 배열 자체는 유지해서 확장된 capacity를 다음 시전에 재사용한다.
	 */
	private void resetForReuse() {
		resetStateForCheck();

		_skill = null;
		_skillId = 0;
		_targetID = 0;
		_targetX = 0;
		_targetY = 0;
		_skillTime = 0;
		_type = TYPE_NORMAL;
		_leverage = 10;
		_calcType = 0;
		_checkedUseSkill = false;
	}

	public boolean initSkill(int skillid, int x, int y, int time, int type) {
		_skill = CSkillDB.getInstance().getTemplate(skillid);
		_skillId = skillid;
		_targetX = x;
		_targetY = y;
		_skillTime = time;
		_type = type;
		return _skill != null;
	}

	public boolean setupUser(L1PcInstance player, L1Character attacker) {
		if (attacker == null) {
			_player = player;
			_user = player;
			return true;
		}

		if (!(attacker instanceof L1NpcInstance)) {
			return false;
		}

		_player = player;
		_npc = (L1NpcInstance) attacker;
		_user = _npc;

		return true;
	}

	public void setupTargetBasics(String skillTarget, int targetId) {
		if ("none".equals(skillTarget)) {
			_targetID = _user.getId();
			_targetX = _user.getX();
			_targetY = _user.getY();
		} else {
			_targetID = targetId;
		}
	}

	public boolean isGroundSkill(int skillId) {
		return skillId == L1SkillId.FIRE_WALL || skillId == L1SkillId.LIFE_STREAM;
	}

	public void determineCalcTypeAndTargetNpc(String skillTarget) {
		if (_user instanceof L1PcInstance) {
			if (_target instanceof L1PcInstance) {
				_calcType = PC_PC;
			} else {
				_calcType = PC_NPC;
				_targetNpc = (L1NpcInstance) _target;
			}
			return;
		}

		if (_user instanceof L1NpcInstance) {
			if (_target instanceof L1PcInstance) {
				_calcType = NPC_PC;
			} else if ("none".equals(skillTarget)) {
				_calcType = NPC_PC;
			} else {
				_calcType = NPC_NPC;
				_targetNpc = (L1NpcInstance) _target;
			}
		}
	}

	public void applyBookmarksAndItemTarget(int targetId, int x, int y) {
		switch (_skillId) {
		case L1SkillId.TELEPORT:
		case L1SkillId.MASS_TELEPORT:
		case L1SkillId.TRUE_TARGET:
			_bookmark_mapid = (short) targetId;
			_bookmark_x = x;
			_bookmark_y = y;
			break;
		case L1SkillId.SUMMON_MONSTER:
			_bookmark_x = x;
			break;
		case L1SkillId.BRING_STONE:
		case L1SkillId.BLESSED_ARMOR:
		case L1SkillId.ENCHANT_WEAPON:
		case L1SkillId.SHADOW_FANG:
			_itemobjid = targetId;
			break;
		default:
			break;
		}
	}

	// =====================================================
	// Commands
	// =====================================================

	/** 단발 시전용. 호출부에서 new L1SkillUse()를 만들 필요가 없다. */
	public static void cast(
			L1PcInstance player,
			int skillId,
			int targetId,
			int x,
			int y,
			String message,
			int timeSecs,
			int type) {

		cast(player, skillId, targetId, x, y, message, timeSecs, type, null);
	}

	/** 단발 시전용 - NPC attacker 지정 버전. */
	public static void cast(
			L1PcInstance player,
			int skillId,
			int targetId,
			int x,
			int y,
			String message,
			int timeSecs,
			int type,
			L1Character attacker) {

		final ReusePool pool = REUSE_POOL.get();
		final L1SkillUse su = pool.acquire();

		try {
			su.handleCommands(
					player,
					skillId,
					targetId,
					x,
					y,
					message,
					timeSecs,
					type,
					attacker);
		} finally {
			pool.release(su);
		}
	}

	/**
	 * NPC가 스킬을 시전할 때 사용하는 ThreadLocal 재사용 진입점.
	 *
	 * 기존 L1MobSkillUse의 순서:
	 * setLeverage -> checkUseSkill -> handleCommands
	 * 를 같은 L1SkillUse 인스턴스에서 그대로 보존한다.
	 *
	 * @return checkUseSkill을 통과해 시전 단계에 들어갔으면 true
	 */
	public static boolean castNpc(
			L1Character attacker,
			int skillId,
			int targetId,
			int x,
			int y,
			String message,
			int timeSecs,
			int type,
			int leverage) {

		if (attacker == null || skillId <= 0) {
			return false;
		}

		final ReusePool pool = REUSE_POOL.get();
		final L1SkillUse su = pool.acquire();

		try {
			if (leverage > 0) {
				su.setLeverage(leverage);
			}

			if (!su.checkUseSkill(
					null,
					skillId,
					targetId,
					x,
					y,
					message,
					timeSecs,
					type,
					attacker)) {
				return false;
			}

			// NPC-specific synchronous path: handleCommands() swallows
			// Throwable and returns void, so reporting true after calling it
			// incorrectly marks a failed cast as UseSkillFinished(well=true).
			// Execute the same checked path, but preserve the actual outcome.
			try {
				if (!su.ensureChecked(null, skillId, targetId, x, y,
						message, timeSecs, type, attacker)) {
					return false;
				}
				// executeNormal() silently returns when LOS/target rules
				// disallow the cast. That is not a successful NPC action.
				if (type == TYPE_NORMAL && !su.canRunNormal()) {
					return false;
				}
				su.executeByType(type, skillId);
				return true;
			} catch (Throwable t) {
				t.printStackTrace();
				return false;
			} finally {
				su.resetChecked();
			}

		} finally {
			pool.release(su);
		}
	}

	/**
	 * 같은 대상/좌표에 여러 스킬을 순차 시전한다.
	 * L1SkillUse 하나를 전체 루프 동안 재사용한다.
	 */
	public static void castAll(
			L1PcInstance player,
			int[] skillIds,
			int targetId,
			int x,
			int y,
			String message,
			int timeSecs,
			int type) {

		castAll(player, skillIds, targetId, x, y, message, timeSecs, type, null);
	}

	/** 같은 대상/좌표 다중 시전 - NPC attacker 지정 버전. */
	public static void castAll(
			L1PcInstance player,
			int[] skillIds,
			int targetId,
			int x,
			int y,
			String message,
			int timeSecs,
			int type,
			L1Character attacker) {

		if (skillIds == null || skillIds.length == 0) {
			return;
		}

		final ReusePool pool = REUSE_POOL.get();
		final L1SkillUse su = pool.acquire();

		try {
			for (int i = 0; i < skillIds.length; i++) {
				su.handleCommands(
						player,
						skillIds[i],
						targetId,
						x,
						y,
						message,
						timeSecs,
						type,
						attacker);
			}
		} finally {
			pool.release(su);
		}
	}

	/**
	 * 자기 자신에게 여러 스킬을 거는 버프 묶음용.
	 * 기존 루프처럼 매 스킬마다 현재 x/y를 다시 읽는다.
	 */
	public static void castAllSelf(
			L1PcInstance player,
			int[] skillIds,
			int timeSecs,
			int type) {

		if (player == null || skillIds == null || skillIds.length == 0) {
			return;
		}

		final ReusePool pool = REUSE_POOL.get();
		final L1SkillUse su = pool.acquire();
		final int targetId = player.getId();

		try {
			for (int i = 0; i < skillIds.length; i++) {
				su.handleCommands(
						player,
						skillIds[i],
						targetId,
						player.getX(),
						player.getY(),
						null,
						timeSecs,
						type);
			}
		} finally {
			pool.release(su);
		}
	}

	public void handleCommands(L1PcInstance player, int skillId, int targetId, int x, int y, String message, int timeSecs, int type) {
		handleCommands(player, skillId, targetId, x, y, message, timeSecs, type, null);
	}

	public void handleCommands(L1PcInstance player, int skillId, int targetId, int x, int y, String message, int timeSecs, int type, L1Character attacker) {
		try {
			if (!ensureChecked(player, skillId, targetId, x, y, message, timeSecs, type, attacker)) {
				return;
			}
			executeByType(type, skillId);
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			resetChecked();
		}
	}

	private boolean ensureChecked(L1PcInstance player, int skillId, int targetId, int x, int y, String message, int timeSecs, int type, L1Character attacker) {
		if (isCheckedUseSkill()) {
			return true;
		}

		if (!checkUseSkill(player, skillId, targetId, x, y, message, timeSecs, type, attacker)) {
			failSkill();
			return false;
		}

		return true;
	}

	private void executeByType(int type, int skillId) {
		switch (type) {
		case TYPE_NORMAL:
			executeNormal(skillId);
			break;
		case TYPE_LOGIN:
			runSkill();
			break;
		case TYPE_SPELLSC:
			runSkill();
			sendGrfx(true);
			setDelay();
			break;
		case TYPE_GMBUFF:
			runSkill();
			sendGrfx(false);
			break;
		case TYPE_NPCBUFF:
			runSkill();
			sendGrfx(true);
			break;
		default:
			break;
		}
	}

	private void executeNormal(int skillId) {
		if (!canRunNormal()) {
			return;
		}

		final boolean preGfx = isSpecialOrderSkill(skillId);

		if (preGfx) {
			sendGrfx(true);
		}

		runSkill();
		useConsume();
		if (!preGfx) {
			sendGrfx(true);
		}
		sendFailMessageHandle();
		setDelay();
	}

	private boolean canRunNormal() {
		if (_skill == null) {
			return false;
		}
		if (!_isGlanceCheckFail) {
			return true;
		}
		if (_skill.getArea() > 0) {
			return true;
		}
		return "none".equals(_skill.getTarget());
	}

	private void resetChecked() {
		_isGlanceCheckFail = false;
		_checkedUseSkill = false;
	}

	private void failSkill() {
		_checkedUseSkill = false;
		if (_player == null) {
			return;
		}

		switch (_skillId) {
		case L1SkillId.TELEPORT:
		case L1SkillId.MASS_TELEPORT:
		case L1SkillId.TELEPORT_TO_MATHER:
			_player.tpEndFinish();
			break;
		default:
			break;
		}
	}

	// =====================================================
	// Messages
	// =====================================================
	private void sendFailMessage() {
		if (!(_user instanceof L1PcInstance)) {
			return;
		}

		if (_player == null || _skill == null) {
			return;
		}

		final FailReason reason = consumeFailReason();
		final int msgId;

		switch (reason) {
		case INVALID_TARGET:
			msgId = 3327;
			break;
		case NONE:
		default:
			msgId = _skill.getSysmsgIdFail();
			break;
		}

		sendServerMessage(_player, msgId);
	}

	private static void sendServerMessage(L1PcInstance pc, int msgId) {
		if (pc == null || msgId <= 0) {
			return;
		}
		SC_ServerMessage.send(pc, msgId);
	}

	private void sendHappenMessage(L1PcInstance pc) {
		if (_skill == null) {
			return;
		}
		sendServerMessage(pc, _skill.getSysmsgIdHappen());
	}

	private void sendFailMessageHandle() {
		if (_skill == null) {
			return;
		}
		if (_skill.getType() == L1Skills.TYPE_ATTACK) {
			return;
		}
		if ("none".equals(_skill.getTarget())) {
			return;
		}
		if (_targetCount != 0) {
			return;
		}
		sendFailMessage();
	}

	public void fail(FailReason reason) {
		_failReason = reason != null ? reason : FailReason.NONE;
	}

	private FailReason consumeFailReason() {
		final FailReason reason = _failReason;
		_failReason = FailReason.NONE;
		return reason;
	}

	// =====================================================
	// Target storage
	// =====================================================
	private void clearTargets() {
		for (int i = 0; i < _targetCount; i++) {
			_targets[i] = null;
		}
		_targetCount = 0;
		_targetNoCalcMask = 0L;
	}

	private void ensureTargetCapacity(int required) {
		if (required <= _targets.length) {
			return;
		}

		int newCapacity = _targets.length << 1;
		if (newCapacity < required) {
			newCapacity = required;
		}
		if (newCapacity > TARGET_MAX_CAPACITY) {
			newCapacity = TARGET_MAX_CAPACITY;
		}

		final L1Character[] next = new L1Character[newCapacity];
		System.arraycopy(_targets, 0, next, 0, _targetCount);
		_targets = next;
	}

	private void addTargetStatus(L1Character cha, boolean calcOk) {
		if (cha == null || _targetCount >= TARGET_MAX_CAPACITY) {
			return;
		}

		ensureTargetCapacity(_targetCount + 1);

		final int index = _targetCount;
		_targets[index] = cha;

		if (!calcOk) {
			_targetNoCalcMask |= (1L << index);
		}

		_targetCount = index + 1;
	}

	private boolean containsTargetId(int id) {
		for (int i = 0; i < _targetCount; i++) {
			final L1Character target = _targets[i];
			if (target != null && target.getId() == id) {
				return true;
			}
		}
		return false;
	}

	public int exTargetCount() {
		return _targetCount;
	}

	public boolean exTargetsEmpty() {
		return _targetCount == 0;
	}

	public L1Character exTargetAt(int index) {
		if (index < 0 || index >= _targetCount) {
			return null;
		}
		return _targets[index];
	}

	public boolean exTargetCalcAt(int index) {
		if (index < 0 || index >= _targetCount) {
			return false;
		}
		return (_targetNoCalcMask & (1L << index)) == 0L;
	}

	public void exMarkTargetRemoved(int index) {
		if (index < 0 || index >= _targetCount) {
			return;
		}
		_targets[index] = null;
	}

	public void exCompactTargets() {
		final int oldCount = _targetCount;
		if (oldCount == 0) {
			_targetNoCalcMask = 0L;
			return;
		}

		int write = 0;
		long newNoCalcMask = 0L;

		for (int read = 0; read < oldCount; read++) {
			final L1Character target = _targets[read];
			if (target == null) {
				continue;
			}
			final boolean calcOk = (_targetNoCalcMask & (1L << read)) == 0L;

			if (write != read) {
				_targets[write] = target;
			}
			if (!calcOk) {
				newNoCalcMask |= (1L << write);
			}
			write++;
		}

		for (int i = write; i < oldCount; i++) {
			_targets[i] = null;
		}

		_targetCount = write;
		_targetNoCalcMask = newNoCalcMask;
	}

	// =====================================================
	// Consume API
	// =====================================================
	public int getMpConsume() { return _mpConsume; }
	public int getHpConsume() { return _hpConsume; }
	public void setMpConsume(int value) { _mpConsume = value; }
	public void setHpConsume(int value) { _hpConsume = value; }

	// =====================================================
	// Handler/Core API
	// =====================================================
	public int hdSkillId() { return _skillId; }
	public L1PcInstance hdPlayer() { return _player; }
	public L1Character hdUser() { return _user; }
	public L1Character hdTarget() { return _target; }
	public int hdTargetId() { return _targetID; }
	public int hdCalcType() { return _calcType; }
	public int hdCalcTypePcPc() { return PC_PC; }
	public int hdCalcTypeNpcPc() { return NPC_PC; }
	public int hdSkillCastGfx() { return _skill != null ? _skill.getCastGfx() : 0; }
	public Random hdRandom() { return GameRandom.current(); }
	public void hdSetShockStunDuration(int ms) { _shockStunDuration = ms; }
	public void hdSetEarthBindDuration(int ms) { _earthBindDuration = ms; }
	public int hdSkillsBegin() { return L1SkillId.SKILLS_BEGIN; }
	public int hdSkillsEnd() { return L1SkillId.SKILLS_END; }
	public int hdStatusBegin() { return L1SkillId.STATUS_BEGIN; }
	public int hdStatusEnd() { return L1SkillId.STATUS_END; }
	public int hdCookingBegin() { return L1SkillId.COOKING_BEGIN; }
	public int hdCookingEnd() { return L1SkillId.COOKING_END; }
	public int hdStatusChatProhibited() { return L1SkillId.STATUS_CHAT_PROHIBITED; }
	public int hdStatusCurseBarlog() { return L1SkillId.STATUS_CURSE_BARLOG; }
	public int hdStatusCurseYahee() { return L1SkillId.STATUS_CURSE_YAHEE; }
	public int hdStatusFruit() { return L1SkillId.STATUS_FRUIT; }
	public int hdStatusFreeze() { return L1SkillId.STATUS_FREEZE; }
	public int hdGetBuffIconDuration() { return _getBuffIconDuration; }
	public boolean hdIsNotCancelable(int skillId) { return SkillUseHelper.isNotCancelable(skillId); }
	public void hdDetection(L1PcInstance pc, boolean on) { SkillUseHelper.detection(pc, on); }
	public int hdBookmarkX() { return _bookmark_x; }
	public int hdBookmarkY() { return _bookmark_y; }
	public short hdBookmarkMapId() { return _bookmark_mapid; }
	public int hdItemObjId() { return _itemobjid; }
	public void hdSummonMonster(L1PcInstance pc, int order) { SummonHelper.summonByOrder(pc, order); }
	public int hdSkillUseType() { return _type; }
	public int hdStatusFloatingEye() { return L1SkillId.STATUS_FLOATING_EYE; }

	// =====================================================
	// Handler commit override
	// =====================================================
	public void hdResetCommitValues() {
		_hdDamage = Integer.MIN_VALUE;
		_hdDrainMana = Integer.MIN_VALUE;
		_hdHeal = Integer.MIN_VALUE;
	}

	public void hdClearOverrides() { hdResetCommitValues(); }
	public void hdSetDamage(int value) { _hdDamage = value; }
	public int hdGetDamage() { return _hdDamage; }
	public void hdSetDrainMana(int value) { _hdDrainMana = value; }
	public int hdGetDrainMana() { return _hdDrainMana; }
	public void hdSetHeal(int value) { _hdHeal = value; }
	public int hdGetHeal() { return _hdHeal; }

	// =====================================================
	// Core aliases
	// =====================================================
	public int exGetMpConsume() { return _mpConsume; }
	public int exGetHpConsume() { return _hpConsume; }
	public void exSetMpConsume(int value) { _mpConsume = value; }
	public void exSetHpConsume(int value) { _hpConsume = value; }
	public L1Skills exSkill() { return _skill; }
	public int exTargetX() { return _targetX; }
	public int exTargetY() { return _targetY; }
	public int exSkillTimeSec() { return _skillTime; }
	public int exShockStunDurationMs() { return _shockStunDuration; }
	public int exEarthBindDurationMs() { return _earthBindDuration; }
	public void exSetEarthBindDurationMs(int ms) { _earthBindDuration = ms; }

	public int exBuffIconDurationSec() {
		if (_skillTime != 0) {
			return _skillTime;
		}
		return _skill != null ? _skill.getBuffDuration() : 0;
	}
	
	public L1Character[] exTargetBackingArray() {
	    return _targets;
	}

	public void exSetCriticalDamage(boolean value) { _isCriticalDamage = value; }
	public boolean exIsCriticalDamage() { return _isCriticalDamage; }
	public void exSetBuffIconDurationSec(int sec) { _getBuffIconDuration = sec; }
	public boolean exIsCounterMagicEnabled() { return _isCounterMagic; }
	public void exSetCounterMagicEnabled(boolean value) { _isCounterMagic = value; }
	public boolean exIsTargetCalc(L1Character cha) { return TargetRules.INSTANCE.isTargetCalc(this, cha); }
	public void exAddMagicList(L1Character cha, boolean repetition) { MagicEffectApplier.INSTANCE.applySkillEffect(this, cha, repetition); }
	public void exSendHappenMessage(L1PcInstance pc) { sendHappenMessage(pc); }
	public void exSetResolvedTarget(L1Character target) { _target = target; }
	public L1NpcInstance exTargetNpc() { return _targetNpc; }
	public void exSetPK(boolean value) { _isPK = value; }

	public void exSetCalcTypeForGround() {
		_calcType = _user instanceof L1PcInstance ? PC_NPC : NPC_PC;
	}

	// =====================================================
	// TargetListBuilder API
	// =====================================================
	public int tlType() { return _type; }
	public L1Skills tlSkill() { return _skill; }
	public int tlSkillId() { return _skillId; }
	public L1Character tlUser() { return _user; }
	public L1Character tlTarget() { return _target; }
	public boolean tlIsTarget(L1Character cha) { return SkillTargetFilter.INSTANCE.isTarget(this, cha); }
	public boolean tlIsAttackSkill() {
	    return SkillTargetingHelper.isAttackSkill(this);
	}
	public void tlAddTarget(L1Character cha, boolean calcOk) { addTargetStatus(cha, calcOk); }
	public boolean tlAlreadyInList(L1Character cha) { return cha != null && containsTargetId(cha.getId()); }

	// =====================================================
	// TargetFilter API
	// =====================================================
	public L1Skills tfSkill() { return _skill; }
	public int tfSkillId() { return _skillId; }
	public int tfCalcType() { return _calcType; }
	public int tfCalcTypePcPc() { return PC_PC; }
	public int tfCalcTypePcNpc() { return PC_NPC; }
	public int tfCalcTypeNpcPc() { return NPC_PC; }
	public int tfCalcTypeNpcNpc() { return NPC_NPC; }
	public L1Character tfUser() { return _user; }
	public L1PcInstance tfPlayer() { return _player; }
	public L1NpcInstance tfNpc() { return _npc; }
	public L1Character tfTarget() { return _target; }
	public boolean tfIsPK() { return _isPK; }
	public void tfSetGlanceCheckFail(boolean value) { _isGlanceCheckFail = value; }
	public boolean tfIsAttackSkill() {
	    return SkillTargetingHelper.isAttackSkill(this);
	}
	public boolean tfBaseTargetByTargetTo(L1Character cha) {
	    return SkillTargetingHelper.baseTargetByTargetTo(this, cha);
	}
	public boolean tfIsControllable(L1Object obj) { return SkillTargetingHelper.isControllable(obj); }
	public boolean tfIsPcOrControllable(L1Object obj) { return SkillTargetingHelper.isPcOrControllable(obj); }
	public int tfGetMasterIdOrZero(L1Object obj) { return SkillTargetingHelper.getMasterIdOrZero(obj); }

	// =====================================================
	// Skill execution
	// =====================================================
	private void runSkill() { SkillExecutor.INSTANCE.execute(this); }
	private void useConsume() { SkillConsumeService.INSTANCE.commitConsume(this); }
	public void makeTargetList() { SkillTargetListBuilder.INSTANCE.build(this); }
	private void sendGrfx(boolean isSkillAction) { SkillGfxFacade.INSTANCE.send(this, isSkillAction); }
	private void setDelay() { SkillDelayPolicy.INSTANCE.applyAfterCast(this); }
	private boolean isSpecialOrderSkill(int skillId) { return SkillUseHelper.isSpecialOrderSkill(skillId); }
	public static boolean isNotCancelable(int skillNum) { return SkillUseHelper.isNotCancelable(skillNum); }
}