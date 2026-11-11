package l1j.server.server.model;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import l1j.server.Config;
import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJ3SEx.SpriteInformation;
import l1j.server.MJ3SEx.Loader.SpriteInformationLoader;
import l1j.server.MJDShopSystem.MJDShopItem;
import l1j.server.MJDShopSystem.MJDShopStorage;
import l1j.server.MJKDASystem.MJKDA;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.SkillEffectContainer;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.poison.L1Poison;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

import l1j.server.server.types.Point;
import l1j.server.server.utils.CalcStat;
import l1j.server.server.utils.IntRange;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_Lawful;
import l1j.server.server.model.network.core.sc.packet.SC_PinkName;
import l1j.server.server.model.network.core.sc.packet.SC_Poison;
public class L1Character extends L1Object {
	private static final long serialVersionUID = 1L;
	// 캐릭터 기본
	private String _name;
	private String _title;
	private int _level;
	private int _exp;
	private volatile int _currentHp;
    private static final AtomicIntegerFieldUpdater<L1Character> CURRENT_HP =
            AtomicIntegerFieldUpdater.newUpdater(L1Character.class, "_currentHp");
	private int _currentMp;
	private int _maxHp = 0;
	private int _trueMaxHp = 0;
	private int _maxMp = 0;
	private int _trueMaxMp = 0;
	private int _lawful;
	private int _karma;
	private int _MagicHit;
	// 상태
	private int _heading; // ● 방향 0. 좌상 1. 상 2. 우상 3. 오른쪽 4. 우하 5. 하 6. 좌하 7. 좌
	private int _moveSpeed; // ● 스피드 0. 통상 1. 헤이 파업 2. 슬로우
	private int _braveSpeed; // ● 치우침 이브 상태 0. 통상 1. 치우침 이브
	private L1Poison _poison = null;
	private boolean _paralyzed;
	private boolean _sleeped;
	private L1Paralysis _paralysis;
	private volatile boolean _isDead;

	protected Light light = null; // 캐릭터 주위 빛
	protected Ability ability = null; // 능력치
	protected Resistance resistance = null; // 저항 (마방, 불, 물, 바람, 땅, 스턴, 동빙, 슬립, 석화)
	protected AC ac = null; // AC 방어
	
	private int _addAttrKind;
	private int _status;
	private int _Desperadolv = 0;
	// 대미지
	private int _dmgup = 0;
	private int _trueDmgup = 0;
	private int _bowDmgup = 0;
	private int _trueBowDmgup = 0;
	private int _hitup = 0;
	private int _trueHitup = 0;
	private int _bowHitup = 0;
	private int _trueBowHitup = 0;

	private int _sp = 0; // sp

	private int _missile_critical_rate = 0;
	private int _melee_critical_rate = 0;
	private int _magic_critical_rate = 0;
	private int _summonre = 0;

	private volatile SkillEffectContainer _skillEffects;
	
	private Set<L1Object> _knownObjects;
	private Set<L1PcInstance> _knownPlayer;

	public boolean isPassive(int passiveId) {
		return false;
	}

	public int getMagicHit() {
		return this._MagicHit;
	}
	
	public void addMagicHit(int i) {
		this._MagicHit += i;
	}

	public void dispose() {
		clearSkillEffectTimer();
		if (_knownObjects != null) {
			_knownObjects.clear();
			_knownObjects = null;
		}
		if (_knownPlayer != null) {
			_knownPlayer.clear();
			_knownPlayer = null;
		}

		if (resistance != null) {
			resistance.dispose();
			resistance = null;
		}

		if (light != null) {
			light.dispose();
			light = null;
		}

		disposeShopInfo();
	}

	public L1Character() {
		_level = 1;
		ability = new Ability(this);
		resistance = new Resistance(this);
		ac = new AC(this);
		light = new Light(this);
	}

	public double getCurrentHpPercent() {
		return (100D / (double) getMaxHp()) * (double) getCurrentHp();
	}

	public double getCurrentMpPercent() {
		return (100D / (double) getMaxMp()) * (double) getCurrentMp();
	}

	public int getLongLocation() {
		int pt = (getX() << 16) & 0xffff0000;
		pt |= (getY() & 0x0000ffff);
		return pt;
	}

	public int getLongLocationReverse() {
		int pt = (getY() << 16) & 0xffff0000;
		pt |= (getX() & 0x0000ffff);
		return pt;
	}

	private int _effectedDG = 0;
	private int _characterDG = 0;
	private int _effectedER = 0;
	private int _characterER = 0;

	public void addDg(int i) {
		_effectedDG += i;
	}
	
	public boolean setCharacterDG(int i) {
		int old = _characterDG;
		_characterDG = i;
		return old != i;
	}

	public int getDg() {
		return _effectedDG + _characterDG;
	}

	public boolean setCharacterER(int i) {
		int old = _characterER;
		_characterER = i;
		return old != i;
	}

	public int getStatER() {
		int er = 0;
		er = (getAbility().getTotalDex() - 8) / 2;
		return er;
	}

	public int getDefaultER() {
		int er = 0;
		int BaseEr = CalcStat.원거리회피(getAbility().getTotalDex());
		er += BaseEr;
		return er;
	}

	public int getEffectedER() {
		return _effectedER;
	}

	public void addEffectedER(int i) {
		_effectedER += i;
	}

	public int getTotalER() {
		int er = 0;
		er += getDefaultER();
		er += getEffectedER();
		er += _characterER;
		if (er < 0) {
			er = 0;
		} else {
			if (hasSkillEffect(L1SkillId.STRIKER_GALE)) {
				er = er / 3;
			}
		}
		return er;
	}

	public void resurrect(int hp) {
	    if (!isDead()) {
	        return;
	    }

	    if (hp <= 0) {
	        hp = 1;
	    }

	    setCurrentHp(hp);
	    setDead(false);
	    setStatus(0);
	    L1PolyMorph.undoPoly(this);

	    for (L1PcInstance pc :
	            L1World.getInstance().getRecognizePlayer(this)) {

	        if (pc == null) {
	            continue;
	        }
	        pc.outOfSight(this);
	        if (pc.canSeeObject(this)) {
	            pc.inSight(this);
	        }
	    }
	}

	public int getCurrentHp() {
		return _currentHp;
	}

	public void setCurrentHp(int i) {
		if (i >= getMaxHp()) {
			i = getMaxHp();
		}
		if (i < 0)
			i = 0;

        CURRENT_HP.set(this, i);
    }

    protected final int changeHpAtomic(long delta) {
        for (;;) {
            final int oldHp = CURRENT_HP.get(this);
            if (oldHp <= 0) return -1;
            final long value = (long) oldHp + delta;
            final int maxHp = getMaxHp();
            final int next = value <= 0 ? 0 : value >= maxHp ? maxHp : (int) value;
            if (CURRENT_HP.compareAndSet(this, oldHp, next)) return next;
        }
    }

    // Existing primitive HP atomic state, with the previous value retained in
    // the high word for callers that must keep health-change side effects.
    // No boxing or allocation. A negative result means HP was already zero.
    protected final long changeHpAtomicPair(long delta) {
        for (;;) {
            final int oldHp = CURRENT_HP.get(this);
            if (oldHp <= 0) return -1L;
            final long value = (long) oldHp + delta;
            final int maxHp = getMaxHp();
            final int next = value <= 0 ? 0 : value >= maxHp ? maxHp : (int) value;
            if (CURRENT_HP.compareAndSet(this, oldHp, next))
                return ((long) oldHp << 32) | (next & 0xFFFFFFFFL);
        }
    }

	public int getCurrentMp() {
		return _currentMp;
	}

	public void setCurrentMp(int i) {
		if (i >= getMaxMp()) {
			i = getMaxMp();
		}
		if (i < 0)
			i = 0;
		
		_currentMp = i;
	}

	public boolean isSleeped() {
		return _sleeped;
	}

	public void setSleeped(boolean sleeped) {
		_sleeped = sleeped;
	}

	public boolean isParalyzed() {
		return _paralyzed;
	}

	public void setParalyzed(boolean paralyzed) {
		_paralyzed = paralyzed;
	}

	private boolean _thunderGrab;

	public boolean isThunderGrab() {
		return _thunderGrab;
	}

	public void setThunderGrab(boolean thunderGrab) {
		_thunderGrab = thunderGrab;
	}

	public L1Paralysis getParalysis() {
		return _paralysis;
	}

	public void setParalaysis(L1Paralysis p) {
		_paralysis = p;
	}

	public void cureParalaysis() {
		if (_paralysis != null) {
			_paralysis.cure();
		}
	}

	public void broadcastPacket(ServerPacket[] pcs) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			for (int i = 0; i < pcs.length; i++)
				pc.sendPackets(pcs[i], false);
		}
	}

	public void broadcastPacket(ServerPacket[] pcs, boolean isClear) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			for (int i = 0; i < pcs.length; i++)
				pc.sendPackets(pcs[i], false);
		}

	}

	public void broadcastPacket(ServerPacket packet) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(packet);
		}
	}
	
	public void broadcastPacket(byte[] data) {
	    for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
	        l1j.server.server.packets.Protocol.Proto.sendRaw(pc, data);
	    }
	}

	public void broadcastPacketM(ServerPacket packet) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			if(pc.getLinMeff() != 2){
				pc.sendPackets(packet);
			}
		}
	}

	public void broadcastPacket(ServerPacket packet, int clanid) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(packet, false, clanid);
		}
	}

	public void broadcastPacket(ServerPacket packet, boolean isClear) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(packet, false);
		}
	}

	public void broadcastPacket(ServerPacket packet, L1Character target) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
			pc.sendPackets(packet, false);
		}
	}
	
	/**
	 * 시야 검색 순회 나중에 브로드캐스트 다 바꿀것 전체 순회가 아니라 시야 순회라 부하 급감.
	 * 
	 */
    public void broadcastPacketFast(ServerPacket packet) {
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(this)) {
            pc.sendPackets(packet);
        }
    }

	public void wideBroadcastPacket(ServerPacket packet) {
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this, 50)) {
			pc.sendPackets(packet, false);
		}
	}

	public int[] getFrontLoc() {
		int[] loc = new int[2];
		int x = getX();
		int y = getY();
		int heading = getHeading();
		switch (heading) {
		case 0: { y--; } break;
		case 1: { x++; y--; } break;
		case 2: { x++; } break;
		case 3: { x++; y++; } break;
		case 4: { y++; } break;
		case 5: { x--; y++; } break;
		case 6: { x--; } break;
		case 7: { x--; y--; } break;
		default: break;
		}
		loc[0] = x;
		loc[1] = y;
		return loc;
	}

	public int targetDirection(int tx, int ty) {
		float dis_x = Math.abs(getX() - tx);
		float dis_y = Math.abs(getY() - ty);
		float dis = Math.max(dis_x, dis_y);

		if (dis == 0)
			return getHeading();

		int avg_x = (int) Math.floor((dis_x / dis) + 0.59f);
		int avg_y = (int) Math.floor((dis_y / dis) + 0.59f);

		int dir_x = 0;
		int dir_y = 0;

		if (getX() < tx) dir_x = 1;
		if (getX() > tx) dir_x = -1;

		if (getY() < ty) dir_y = 1;
		if (getY() > ty) dir_y = -1;

		if (avg_x == 0) dir_x = 0;
		if (avg_y == 0) dir_y = 0;

		if (dir_x == 1 && dir_y == -1) return 1;
		if (dir_x == 1 && dir_y == 0) return 2;
		if (dir_x == 1 && dir_y == 1) return 3;
		if (dir_x == 0 && dir_y == 1) return 4;
		if (dir_x == -1 && dir_y == 1) return 5;
		if (dir_x == -1 && dir_y == 0) return 6;
		if (dir_x == -1 && dir_y == -1) return 7;
		if (dir_x == 0 && dir_y == -1) return 0;

		return getHeading();
	}

	public boolean glanceCheck(int targetX, int targetY) {
		if (this.getX() == targetX && this.getY() == targetY) {
			return true;
		}
		L1Map map = getMap();
		if (map == null) return false;
		return map.isRayTraceable(this.getX(), this.getY(), targetX, targetY);
	}

	public boolean isAttackPosition(int x, int y, int range) {
		if (range >= 7) {
			if (getLocation().getTileDistance(new Point(x, y)) > range)
				return false;
		} else {
			if (getLocation().getTileLineDistance(new Point(x, y)) > range)
				return false;
		}
		return glanceCheck(x, y);
	}

	public boolean isAttackPosition(L1Character target, int range) {
		if (range >= 7) {
			if (getLocation().getTileDistance(target.getLocation()) > range)
				return false;
		} else {
			if (getLocation().getTileLineDistance(target.getLocation()) > range)
				return false;
		}
		return glanceCheck(target.getX(), target.getY()) && target.glanceCheck(getX(), getY());
	}

	public L1Inventory getInventory() {
		return null;
	}

	private SkillEffectContainer skillEffects(boolean create) {
		SkillEffectContainer effects = _skillEffects;

		if (effects == null && create) {
			/*
			 * create=true is reached only from the character CIO owner path
			 * (or the no-owner bootstrap/teardown fallback).
			 */
			effects = new SkillEffectContainer(this);
			_skillEffects = effects;
		}

		return effects;
	}

	public void setSkillEffect(int skillId, int timeMillis) {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillSet(this, skillId, timeMillis);
	}

	public void removeSkillEffect(int skillId) {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillRemove(this, skillId, true);
	}

	public void killSkillEffectTimer(int skillId) {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillRemove(this, skillId, false);
	}

	public void clearSkillEffectTimer() {
		CIOThreadCallback.getInstance()
				.submitCharacterSkillClear(this);
	}

	public boolean hasSkillEffect(int skillId) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects != null && effects.contains(skillId);
	}

	public int getSkillEffectTimeSec(int skillId) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects == null ? -1 : effects.getRemainingTimeSec(skillId);
	}

	/**
	 * CIO owner-only mutation entry points.
	 */
	public void skillEffectSetFromOwner(int skillId, int timeMillis) {
		final SkillEffectContainer effects = skillEffects(true);

		if (effects.contains(skillId)) {
			final int remainingTimeMillis =
					effects.getRemainingTimeSec(skillId) * 1000;

			if (remainingTimeMillis >= 0
					&& (remainingTimeMillis < timeMillis || timeMillis == 0)) {

				if (skillId == L1SkillId.DESPERADO) {
					effects.remove(skillId, true);
				} else {
					effects.remove(skillId, false);
				}

				effects.put(skillId, timeMillis);

				if (timeMillis > 0) {
					CIOThreadCallback.getInstance()
							.ensureCharacterSkillHeartbeatFromOwner(this);
				}
			}
			return;
		}

		effects.put(skillId, timeMillis);

		if (timeMillis > 0) {
			CIOThreadCallback.getInstance()
					.ensureCharacterSkillHeartbeatFromOwner(this);
		}
	}

	public void skillEffectRemoveFromOwner(int skillId, boolean stop) {
		final SkillEffectContainer effects = skillEffects(false);
		if (effects != null && effects.remove(skillId, stop)
				&& (skillId == L1SkillId.INVISIBILITY
						|| skillId == L1SkillId.BLIND_HIDING)
				&& this instanceof L1PcInstance pc && !pc.isInvisble()) {
			NpcEventFactory.onPlayerVisibilityRestored(pc);
		}
	}

	public void skillEffectClearFromOwner() {
		final SkillEffectContainer effects = skillEffects(false);
		if (effects != null) {
			effects.clear();
		}
	}

	public int skillEffectHeartbeatTryArm() {
		final SkillEffectContainer effects = skillEffects(false);
		return effects == null ? 0 : effects.tryArmHeartbeat();
	}

	public boolean skillEffectHeartbeatValid(int generation) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects != null && effects.isHeartbeatValid(generation);
	}

	public long skillEffectHeartbeatTick(int generation) {
		final SkillEffectContainer effects = skillEffects(false);
		return effects == null ? 0L : effects.onHeartbeat(generation);
	}

	public void skillEffectHeartbeatRelease(int generation) {
		final SkillEffectContainer effects = skillEffects(false);
		if (effects != null) {
			effects.releaseHeartbeat(generation);
		}
	}


	private volatile long _nextSkillTimeNs;

	public boolean isSkillDelay() {
	    final long until = _nextSkillTimeNs;
	    return until != 0L && System.nanoTime() - until < 0L;
	}

	public void setNextSkillDelay(int delayMs) {
	    if (delayMs <= 0) {
	        return;
	    }
	    _nextSkillTimeNs = System.nanoTime() + (long) delayMs * 1_000_000L;
	}
	
	public void setPoison(L1Poison poison) {
		_poison = poison;
	}

	public void curePoison() {
		if (_poison == null) {
			return;
		}
		_poison.cure();
	}

	public L1Poison getPoison() {
		return _poison;
	}

	public void setPoisonEffect(int effectId) {
		SC_Poison.broadcastFast(this, getId(), effectId);
	}

	public int getZoneType() {
		if (getMap().isSafetyZone(getLocation())) {
			if (getMapId() == 5153) {
				return -1;
			} else {
				return 1;
			}
		} else if (getMap().isCombatZone(getLocation())) {
			return -1;
		} else {
			return 0;
		}
	}

	public int getExp() {
		return _exp;
	}

	public void setExp(int exp) {
		_exp = exp;
	}

	public boolean knownsObject(L1Object obj) {
	    return _knownObjects != null && _knownObjects.contains(obj);
	}

	public Set<L1Object> getKnownObjects() {
	    return _knownObjects == null 
	        ? Collections.emptySet()
	        : _knownObjects;
	}

	public Set<L1PcInstance> getKnownPlayers() {
		   return _knownPlayer == null 
			        ? Collections.emptySet()
			        : _knownPlayer;
	}
	
	public void addKnownObject(L1Object obj) {
		if (_knownObjects == null) {
			_knownObjects = ConcurrentHashMap.newKeySet();
		}
		if (_knownObjects.add(obj)) {
			if (obj instanceof L1PcInstance) {
				if (_knownPlayer == null) {
					_knownPlayer = ConcurrentHashMap.newKeySet();
				}
				_knownPlayer.add((L1PcInstance) obj);
			}
		}
	}

	public void removeKnownObject(L1Object obj) {
		if (_knownObjects != null) {
			_knownObjects.remove(obj);
		}
		if (obj instanceof L1PcInstance && _knownPlayer != null) {
			_knownPlayer.remove((L1PcInstance) obj);
		}
	}

	public void removeAllKnownObjects() {
		if (_knownObjects != null) {
			_knownObjects.clear();
		}
		if (_knownPlayer != null) {
			_knownPlayer.clear();
		}
	}

	public String getName() {
		return _name;
	}

	public void setName(String s) {
		_name = s;
	}

	public int getLevel() {
		return _level;
	}

	public synchronized void setLevel(long level) {
		_level = (int) level;
	}

	public int getMaxHp() {
		if (getAbility() == null)
			return _maxHp;

		int maxhp = _maxHp;
        if (this instanceof L1PcInstance)
            maxhp += CalcStat.pureHpMpBonus(getAbility().getCon());
		return maxhp;
	}

	public void addMaxHp(int i) {
		setMaxHp(_trueMaxHp + i);
	}

	public void setMaxHp(int hp) {
		_trueMaxHp = hp;
		_maxHp = (int) IntRange.ensure(_trueMaxHp, 1, 10000000);
        for (;;) {
            final int oldHp = CURRENT_HP.get(this);
            final int next = Math.min(oldHp, getMaxHp());
            if (next == oldHp || CURRENT_HP.compareAndSet(this, oldHp, next)) break;
        }
	}

	public int getMaxMp() {
		if (getAbility() == null)
			return _maxMp;

		int maxmp = _maxMp;
        int wis = this instanceof L1PcInstance ? getAbility().getWis() : getAbility().getTotalWis();
        maxmp += CalcStat.pureHpMpBonus(wis);
		return maxmp;
	}

	public void setMaxMp(int mp) {
		_trueMaxMp = mp;
		_maxMp = (int) IntRange.ensure(_trueMaxMp, 0, 10000000);
		_currentMp = Math.min(_currentMp, getMaxMp());
	}

	public void addMaxMp(int i) {
		setMaxMp(_trueMaxMp + i);
	}

	public void healHp(int pt) {
		setCurrentHp(getCurrentHp() + pt);
	}

	public int getAddAttrKind() {
		return _addAttrKind;
	}

	public void setAddAttrKind(int i) {
		_addAttrKind = i;
	}

	public int getDmgup() {
		return _dmgup;
	}

	public void addDmgup(int i) {
		_trueDmgup += i;
		if (_trueDmgup >= 127) {
			_dmgup = 127;
		} else if (_trueDmgup <= -128) {
			_dmgup = -128;
		} else {
			_dmgup = _trueDmgup;
		}
	}

	public int getBowDmgup() {
		return _bowDmgup;
	}

	public void addBowDmgup(int i) {
		_trueBowDmgup += i;
		if (_trueBowDmgup >= 127) {
			_bowDmgup = 127;
		} else if (_trueBowDmgup <= -128) {
			_bowDmgup = -128;
		} else {
			_bowDmgup = _trueBowDmgup;
		}
	}

	public int getHitup() {
		return _hitup;
	}

	public void addHitup(int i) {
		_trueHitup += i;
		if (_trueHitup >= 127) {
			_hitup = 127;
		} else if (_trueHitup <= -128) {
			_hitup = -128;
		} else {
			_hitup = _trueHitup;
		}
	}

	public int get_Summonre() {
		return _summonre;
	}

	public void set_Summonre(int sum) {
		_summonre = sum;
	}

	public int getBowHitup() {
		return _bowHitup;
	}

	public void addBowHitup(int i) {
		_trueBowHitup += i;
		if (_trueBowHitup >= 127) {
			_bowHitup = 127;
		} else if (_trueBowHitup <= -128) {
			_bowHitup = -128;
		} else {
			_bowHitup = _trueBowHitup;
		}
	}

	public void addSp(int i) {
		_sp += i;
	}

	public int getSp() {
		return getTrueSp() + _sp;
	}

	public int getTrueSp() {
		return getMagicLevel() + getMagicBonus();
	}

	public int getMagicLevel() {
		return getLevel() / 4;
	}

	public int getMagicBonus() {
		int i = getAbility().getTotalInt();
		if (i <= 5) return -2;
		else if (i <= 8) return -1;
		else if (i <= 11) return 0;
		else if (i <= 14) return 1;
		else if (i <= 17) return 2;
		else return i - 15;
	}

	private long _doppeltime = 0;

	public long getDoppelTime() {
		return _doppeltime;
	}

	public void setDoppelTime(long l) {
		_doppeltime = l;
	}

	public boolean isDead() {
		return _isDead;
	}

	public void setDead(boolean flag) {
		_isDead = flag;
	}

	public int getStatus() {
		return _status;
	}

	private int _monCount;

	public int get_monCount() {
		return _monCount;
	}

	public void set_monCount(int i) {
		_monCount = i;
	}

	private int _waponexp;

	public int get_waponexp() {
		return _waponexp;
	}

	public void set_waponexp(int i) {
		_waponexp = i;
	}

	public void setStatus(int i) {
		_status = i;
	}

	public String getTitle() {
		return _title;
	}

	public void setTitle(String s) {
		_title = s;
	}

	public int getLawful() {
		return _lawful;
	}

	public void setLawful(int i) {
		_lawful = i;
	}

	public synchronized void addLawful(int i) {
		if (Config.STANDBY_SERVER)
			return;

		_lawful += i;
		if (_lawful > 32767) {
			_lawful = 32767;
		} else if (_lawful < -32768) {
			_lawful = -32768;
		}
	}

	public int getHeading() {
		return _heading;
	}

	public void setHeading(int i) {
		_heading = i;
	}

	public int getMoveSpeed() {
		return _moveSpeed;
	}

	public void setMoveSpeed(int i) {
		_moveSpeed = i;
	}

	public int getBraveSpeed() {
		return _braveSpeed;
	}

	public void setBraveSpeed(int i) {
		_braveSpeed = i;
	}

	public boolean isInvisble() {
		return (hasSkillEffect(L1SkillId.INVISIBILITY) || hasSkillEffect(L1SkillId.BLIND_HIDING));
	}

	public int getKarma() {
		return _karma;
	}

	public void setKarma(int karma) {
		_karma = karma;
	}

	private MJKDA _kda;

	public MJKDA getKDA() {
		return _kda;
	}

	public void setKDA(MJKDA kda) {
		_kda = kda;
	}

	private long _skilldelay2;

	public long getSkilldelay2() {
		return _skilldelay2;
	}

	public void setSkilldelay2(long skilldelay2) {
		_skilldelay2 = skilldelay2;
	}

	private int _buffnoch;

	public int getBuffnoch() {
		return _buffnoch;
	}

	public void setBuffnoch(int buffnoch) {
		_buffnoch = buffnoch;
	}

	public Light getLight() {
		return light;
	}

	public Ability getAbility() {
		return ability;
	}

	public Resistance getResistance() {
		return resistance;
	}

	public AC getAC() {
		return ac;
	}

	public int getDesperadolv() {
		return _Desperadolv;
	}

	public void setDesperadolv(int Desperadolv) {
		_Desperadolv = Desperadolv;
	}

	private int _mr = 0;
	private int _trueMr = 0;

	public int getMr() {
		if (hasSkillEffect(153) == true) {
			return _mr / 2;
		} else {
			return _mr;
		}
	}

	public int getTrueMr() {
		return _trueMr;
	}

	public void addMr(int i) {
		_trueMr += i;
		if (_trueMr <= 0) {
			_mr = 0;
		} else {
			_mr = _trueMr;
		}
	}

	private int 락구간상승;

	public int get락구간상승() {
		return 락구간상승;
	}

	public void set락구간상승(int i) {
		락구간상승 = i;
	}

	public void sendPetCtrlMenu(L1NpcInstance npc, boolean type) {
		if (npc instanceof L1PetInstance) {
			L1PetInstance pet = (L1PetInstance) npc;
			L1Character cha = pet.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(pc, npc, type);
			}
		} else if (npc instanceof L1SummonInstance) {
			L1SummonInstance summon = (L1SummonInstance) npc;
			L1Character cha = summon.getMaster();
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				l1j.server.server.model.network.core.sc.packet.SC_PetCtrlMenu.send(pc, npc, type);
			}
		}
	}

	private ArrayList<MJDShopItem> _sellings;
	private ArrayList<MJDShopItem> _purchasings;

	public void disposeShopInfo() {
		disposeSellings();
		disposePurchasings();
	}

	private MJDShopItem findDShopItem(ArrayList<MJDShopItem> list, int objid) {
		if (list == null)
			return null;

		int size = list.size();
		MJDShopItem item = null;
		for (int i = 0; i < size; i++) {
			item = list.get(i);
			if (item.objId == objid)
				return item;
		}
		return null;
	}

	public MJDShopItem findSellings(int objid) {
		return findDShopItem(_sellings, objid);
	}

	public void updateSellings(int objid, int count) {
		MJDShopItem item = findSellings(objid);
		if (item == null)
			return;

		if (item.count <= count) {
			_sellings.remove(item);
			MJDShopStorage.deleteProcess(this, item.objId);
		} else {
			item.count -= count;
			MJDShopStorage.updateProcess(this, item);
		}
	}

	public ArrayList<MJDShopItem> getSellings() {
		return _sellings;
	}

	public void setSellings(ArrayList<MJDShopItem> list) {
		_sellings = list;
	}

	public void addSellings(MJDShopItem item) {
		if (_sellings == null)
			_sellings = new ArrayList<MJDShopItem>(7);
		_sellings.add(item);
	}

	public void disposeSellings() {
		if (_sellings != null) {
			_sellings.clear();
			_sellings = null;
		}
	}

	public MJDShopItem findPurchasings(int objid) {
		return findDShopItem(_purchasings, objid);
	}

	public void updatePurchasings(int objid, int count) {
		MJDShopItem item = findPurchasings(objid);
		if (item == null)
			return;

		if (item.count <= count) {
			_purchasings.remove(item);
			MJDShopStorage.deleteProcess(this, item.objId);
		} else {
			item.count -= count;
			MJDShopStorage.updateProcess(this, item);
		}
	}

    /** Memory half of a receipt-committed private-shop operation. SQL listing
     * quantities were checked and changed on the same connection as payment.
     */
    public void applyCommittedShopQuantity(boolean purchase, MJDShopItem expected,
            int expectedCount, int amount) {
        final ArrayList<MJDShopItem> list = purchase ? _purchasings : _sellings;
        if (list == null || expected == null || amount <= 0 || amount > expectedCount)
            throw new IllegalStateException("Invalid committed private shop quantity");
        synchronized (list) {
            if (!list.contains(expected) || expected.count != expectedCount)
                throw new IllegalStateException("Private shop preimage changed after commit");
            if (amount == expectedCount) list.remove(expected);
            else expected.count = expectedCount - amount;
        }
    }

	public ArrayList<MJDShopItem> getPurchasings() {
		return _purchasings;
	}

	public void setPurchasings(ArrayList<MJDShopItem> list) {
		_purchasings = list;
	}

	public void addPurchasings(MJDShopItem item) {
		if (_purchasings == null)
			_purchasings = new ArrayList<MJDShopItem>(7);
		    _purchasings.add(item);
	}

	public void disposePurchasings() {
		if (_purchasings != null) {
			_purchasings.clear();
			_purchasings = null;
		}
	}


	public void sendPackets(ServerPacket pck, boolean clear) {
	}

	public void receiveDamage(L1Character attacker, int damage) {
	}

	public int Desperadolevel = 0;

	private static int _instanceType = -1;

	@Override
	public int getL1Type() {
		return _instanceType == -1 ? _instanceType = super.getL1Type() | MJL1Type.L1TYPE_CHARACTER : _instanceType;
	}

	public void sendPackets(ServerPacket sbp) {

	}

	protected SpriteInformation _currentSpriteInfo;

	public int getCurrentSpriteId() {
		return _currentSpriteInfo == null ? 1120 : _currentSpriteInfo.getSpriteId();
	}

	public SpriteInformation getCurrentSprite() {
		return _currentSpriteInfo;
	}

	public void setCurrentSprite(int spriteId) {
		if (!equalsCurrentSprite(spriteId))
			_currentSpriteInfo = SpriteInformationLoader.getInstance().get(spriteId);
	}

	public boolean equalsCurrentSprite(int compareSpriteId) {
		return getCurrentSpriteId() == compareSpriteId;
	}

	public long getCurrentSpriteInterval(EActionCodes actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	}

	public long getCurrentSpriteInterval(int actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	}

	public void sendShape(int poly) {
		SC_ChangeShape.send(this, getId(), poly, 0);
		SC_ChangeShape.broadcastFast(this, getId(), poly, 0);
	}

	public void set_missile_critical_rate(int rate) {
		_missile_critical_rate = rate;
	}

	public int add_missile_critical_rate(int rate) {
		return _missile_critical_rate += rate;
	}

	public int get_missile_critical_rate() {
		return _missile_critical_rate;
	}

	public void set_melee_critical_rate(int rate) {
		_melee_critical_rate = rate;
	}

	public int get_melee_critical_rate() {
		return _melee_critical_rate;
	}

	public int get_final_burn_critical_rate() {
		if(!isPassive(MJPassiveID.FINAL_BURN.toInt()) || getCurrentHpPercent() > 70)
			return 0;
		int level = getLevel();
		int rate = 5;
		if(level == 50) 
			rate = 6;
		
		if(level > 50)
			rate += ((level - 50) / 2);
		if(rate > 10){
			rate = 10;
		}
		return rate;
	}
	
	public int add_melee_critical_rate(int rate) {
		return _melee_critical_rate += rate;
	}

	public void set_magic_critical_rate(int rate) {
		_magic_critical_rate = rate;
	}

	public int get_magic_critical_rate() {
		return _magic_critical_rate;
	}

	public int add_magic_critical_rate(int rate) {
		return _magic_critical_rate += rate;
	}

	public void send_effect(int effect_id) {
		if (effect_id > 0) {
			long sound = SC_SkillSound.pack(getId(), effect_id);
			SC_SkillSound.sendPacked(this, sound);
			SC_SkillSound.broadcastFastPacked(this, sound);
		}
	}

	public void send_action(int action_id) {
		if (action_id > 0) {
			if (this instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) this;
				SC_DoActionGFX.send(
						pc.getNetConnection(),
						pc.getId(),
						action_id);
			}
			SC_DoActionGFX.broadcast(this, action_id);
		}
	}

	public void send_pink_name(int remain_seconds){
		SC_PinkName.send(this, getId(), remain_seconds);
		SC_PinkName.broadcastFast(this, getId(), remain_seconds);
	}
	
	public void send_lawful(){
		final int lawful = getLawful();
		SC_Lawful.send(this, getId(), lawful);
		SC_Lawful.broadcastFast(this, getId(), lawful);
	}

	private int _focus_wave_level;
	private int _last_focusing_target;

	public void set_focus_wave_level(int focus_wave_level) {
		_focus_wave_level = focus_wave_level;
	}

	public int get_focus_wave_level() {
		return _focus_wave_level;
	}

	public int inc_focus_wave_level() {
		return ++_focus_wave_level;
	}

	public int dec_focus_wave_level() {
		return --_focus_wave_level;
	}

	public void set_last_focusing_target(int last_focusing_target) {
		_last_focusing_target = last_focusing_target;
	}

	public int get_last_focusing_target() {
		return _last_focusing_target;
	}

	private static final int[] _elf_skill_braves = new int[] { 
		L1SkillId.DANCING_BLADES, L1SkillId.SAND_STORM, L1SkillId.HURRICANE, L1SkillId.FOCUS_WAVE, 
	};

	public void remove_elf_second_brave() {
		for (int skillId : _elf_skill_braves) {
			if (hasSkillEffect(skillId)) {
				removeSkillEffect(skillId);
				SC_SkillBrave.send(this, getId(), 0, 0);
				SC_SkillBrave.broadcastFast(this, getId(), 0, 0);
				setBraveSpeed(0);
			}
		}
	}

	private boolean _isbonusAdena;

	public boolean isBonusAdenaItem() {
		return _isbonusAdena;
	}

	public void setBounusAdenaItem(boolean flag) {
		_isbonusAdena = flag;
	}
	
	public boolean isHaste() {
		return (hasSkillEffect(L1SkillId.STATUS_HASTE) || hasSkillEffect(L1SkillId.HASTE)
				|| hasSkillEffect(L1SkillId.GREATER_HASTE) || getMoveSpeed() == 1);
	}

	
	private volatile long _tomahawkState;
	private static final AtomicLongFieldUpdater<L1Character> U_TOMAHAWK_STATE = AtomicLongFieldUpdater.newUpdater(
	                L1Character.class,
	                "_tomahawkState");

	/**
	 * 새 토마호크 시작 / 재시전.
	 * 항상 이전 active token과 다른 새 token을 발급한다.
	 */
	public long beginTomahawkState() {
	    while (true) {
	        final long old = U_TOMAHAWK_STATE.get(this);

	        final long next = ((old & 1L) != 0L) ? old + 2L : old + 1L;

	        if (U_TOMAHAWK_STATE.compareAndSet(this, old, next)) {
	            return next;
	        }
	    }
	}

	/**
	 * 이 node가 아직 현재 토마호크인지 확인.
	 */
	public boolean isTomahawkState(long token) {
	    return token != 0L
	            && U_TOMAHAWK_STATE.get(this) == token;
	}


	/**
	 * 정상 종료.
	 * 새 토마호크가 이미 들어왔다면 CAS 실패하므로
	 * 절대로 새 토마호크 상태를 지우지 않는다.
	 */
	public boolean finishTomahawkState(long token) {
	    if ((token & 1L) == 0L) {
	        return false;
	    }
	    return U_TOMAHAWK_STATE.compareAndSet(
	            this,
	            token,
	            token + 1L);
	}

	/**
	 * 외부 강제 취소.
	 */
	public boolean cancelTomahawkState() {
	    while (true) {
	        final long current =
	                U_TOMAHAWK_STATE.get(this);
	        if ((current & 1L) == 0L) {
	            return false;
	        }
	        if (U_TOMAHAWK_STATE.compareAndSet(this, current, current + 1L)) {
	            return true;
	        }
	    }
	}


	/**
	 * 현재 토마호크 활성 여부.
	 */
	public boolean hasTomahawkState() {
	    return (U_TOMAHAWK_STATE.get(this) & 1L) != 0L;
	}
}
