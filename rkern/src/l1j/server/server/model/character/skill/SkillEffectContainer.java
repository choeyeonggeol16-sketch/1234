package l1j.server.server.model.character.skill;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Character-owned skill effect lifetime state.
 *
 * Mutation is single-owner only: all put/remove/clear/tick calls are serialized
 * through the character's CIOThread owner. Therefore no synchronized/lock is
 * used on the write path.
 *
 * Reads may still happen outside the owner thread. A tiny seqlock-style
 * revision protects contains()/getRemainingTimeSec() from observing a partial
 * swap-remove or array growth without adding a monitor to the hot path.
 */
public final class SkillEffectContainer {

	private static final Logger LOG = Logger.getLogger(SkillEffectContainer.class.getName());

	private static final int INITIAL_CAPACITY = 8;

	private final L1Character owner;

	private int[] skillIds = new int[INITIAL_CAPACITY];
	private int[] remainingSeconds = new int[INITIAL_CAPACITY];
	private long[] nextTickAtMillis = new long[INITIAL_CAPACITY];
	private int[] expiredScratch;
	private SkillModifiers skillModifiers;

	private int size;
	private int timedCount;

	private int heartbeatGeneration;
	private boolean heartbeatArmed;

	/*
	 * even = stable snapshot, odd = owner mutation in progress.
	 * Single writer means no CAS is required.
	 */
	private volatile int revision;

	public SkillEffectContainer(L1Character owner) {
		this(owner, 0);
	}

	public SkillEffectContainer(L1Character owner, int previousHeartbeatGeneration) {
		this.owner = owner;
		this.heartbeatGeneration = previousHeartbeatGeneration;
	}

	/** Only allocated for characters with an applied, migrated effect. */
	static final class SkillModifiers {
		// Two words per source, starting with the numeric ID.
		long[] applied = new long[4];
		int count;
		int hp, mp, ac, mr, er, dg, meleeHit, meleeDamage, rangedDamage, sp;
		int carryLimit, armorReduction, baseMagicHit;
		int rangedHit, rangedCritical, magicCritical, weaponHitRate, magicHit, weaponDamageRate;
		int hpRegen, mpRegen, impactProbability;
		int pvpDamage, pvpReduction;
		long specialResistance;
		// Potential captures six independent values once, with two extra packed pairs.
		long potentialMagic, potentialEvasion;

		// Independent unsigned bytes: STR, DEX, INT, WIS, CON, CHA.
		long primaryStats, primaryPenalty;
		// Independent totals for water, wind, earth, and fire.
		int waterResistance, windResistance, earthResistance, fireResistance;

		void initializeSkillMod() {
			hp = mp = ac = mr = er = dg = meleeHit = meleeDamage = rangedDamage = sp = 0;
			primaryStats = primaryPenalty = 0L;
			waterResistance = windResistance = earthResistance = fireResistance = 0;
			carryLimit = armorReduction = baseMagicHit = 0;
			rangedHit = rangedCritical = magicCritical = weaponHitRate = magicHit = weaponDamageRate = 0;
			hpRegen = mpRegen = impactProbability = 0;
			pvpDamage = pvpReduction = 0;
			specialResistance = 0;
		}

		int find(int id) {
			for (int i = 0; i < count * 2; i += 2) if (applied[i] == id) return i;
			return -1;
		}

		long effectivePrimaryStats() {
			if (primaryPenalty == 0) return primaryStats;
			long combined = 0;
			for (int shift = 0; shift < 48; shift += 8) {
				final int bonus = (int) (primaryStats >>> shift) & 0xff;
				final int penalty = (int) (primaryPenalty >>> shift) & 0xff;
				combined |= ((bonus - penalty) & 0xffL) << shift;
			}
			return combined;
		}
	}

	public void applySkillModifier(int skillId, long captured) {
		applySkillModifier(skillId, captured, false);
	}

	private void applySkillModifier(int skillId, long captured, boolean changedOtherValues) {
		if (!CSkillInfo.isManaged(skillId)) throw new IllegalArgumentException("Unmanaged skill " + skillId);
		SkillModifiers state = skillModifiers;
		if (state == null) skillModifiers = state = new SkillModifiers();
		int index = state.find(skillId);
		if (index >= 0 && state.applied[index + 1] == captured && !changedOtherValues) return;
		if (index < 0) {
			index = state.count * 2;
			if (index == state.applied.length)
				state.applied = java.util.Arrays.copyOf(state.applied, index << 1);
			state.applied[index] = skillId;
			state.count++;
		}
		state.applied[index + 1] = captured;
		validateSkillMod(true);
	}

	public void applyPotentialModifier(int hp, int mp, int mr, int sp, int er, int dg) {
		SkillModifiers state = skillModifiers;
		if (state == null) skillModifiers = state = new SkillModifiers();
		final long magic = ((long) sp << 32) | Integer.toUnsignedLong(mr);
		final long evasion = ((long) dg << 32) | Integer.toUnsignedLong(er);
		final boolean changed = state.potentialMagic != magic || state.potentialEvasion != evasion;
		state.potentialMagic = magic;
		state.potentialEvasion = evasion;
		applySkillModifier(L1SkillId.포텐셜, ((long) mp << 32) | Integer.toUnsignedLong(hp), changed);
	}

	public boolean removeSkillModifier(int skillId, boolean notify) {
		SkillModifiers state = skillModifiers;
		if (state == null) return false;
		int index = state.find(skillId);
		if (index < 0) return false;
		if (skillId == L1SkillId.포텐셜) state.potentialMagic = state.potentialEvasion = 0;
		int last = --state.count * 2;
		if (state.count == 0) {
			// Release the retained captured-value array after the last source ends.
			skillModifiers = null;
		} else {
			state.applied[index] = state.applied[last];
			state.applied[index + 1] = state.applied[last + 1];
		}
		validateSkillMod(notify);
		return true;
	}

	public void validateSkillMod(boolean notify) {
		SkillModifiers state = skillModifiers;
		if (state == null) {
			owner.setPumpedSkillModifiers(0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0, notify);
			owner.getResistance().setSkillCombatModifiers(0, 0L, notify);
			if (owner instanceof L1PcInstance pc) {
				pc.setPumpedCarryArmorAndAccuracy(0, notify);
				pc.setPumpedRegenAndWeaponRates(0);
			}
			return;
		}
		state.initializeSkillMod();
		for (int i = 0; i < state.count * 2; i += 2)
			CSkillInfo.get((int) state.applied[i]).pump(owner, state.applied[i + 1], state);
		final int evasion = (state.dg << 16) | (state.er & 0xffff);
		final long weapon = ((long) state.rangedHit << 48) | ((state.rangedDamage & 0xffffL) << 32)
				| ((long) (state.meleeDamage & 0xffff) << 16) | (state.meleeHit & 0xffff);
		final long combatRates = ((long) state.weaponHitRate << 48) | ((state.magicHit & 0xffffL) << 32)
                | ((state.magicCritical & 0xffffL) << 16) | (state.rangedCritical & 0xffffL);
        final long elements = ((long) state.fireResistance << 48) | ((state.earthResistance & 0xffffL) << 32)
                | ((state.windResistance & 0xffffL) << 16) | (state.waterResistance & 0xffffL);
		owner.setPumpedSkillModifiers(state.hp, state.mp, state.ac, state.mr,
				state.effectivePrimaryStats(), elements, evasion, weapon, combatRates, state.sp, notify);
		final int pvp = (state.pvpReduction << 16) | (state.pvpDamage & 0xffff);
		owner.getResistance().setSkillCombatModifiers(pvp, state.specialResistance, notify);
		if (owner instanceof L1PcInstance pc) {
			final long carryArmorAndAccuracy = ((long) state.baseMagicHit << 32)
                    | ((state.armorReduction & 0xffffL) << 16) | (state.carryLimit & 0xffffL);
			pc.setPumpedCarryArmorAndAccuracy(carryArmorAndAccuracy, notify);
			final long regenAndWeaponRates = ((long) state.impactProbability << 48)
                    | ((state.weaponDamageRate & 0xffffL) << 32)
                    | ((state.mpRegen & 0xffffL) << 16) | (state.hpRegen & 0xffffL);
            pc.setPumpedRegenAndWeaponRates(regenAndWeaponRates);
		}
	}

	public boolean contains(int skillId) {
		return containsAny(skillId, skillId, skillId);
	}

	public boolean containsAny(int first, int second, int third) {
		for (;;) {
			final int before = revision;
			if ((before & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}

			final int n = size;
			boolean found = false;

			for (int i = 0; i < n; i++) {
				final int id = skillIds[i];
				if (id == first || id == second || id == third) {
					found = true;
					break;
				}
			}

			if (before == revision) {
				return found;
			}
		}
	}

	public boolean isEmpty() {
		for (;;) {
			final int before = revision;
			if ((before & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}

			final boolean empty = size == 0;

			if (before == revision) {
				return empty;
			}
		}
	}

	public boolean containsMatching(java.util.function.IntPredicate matchesSkillId, int exceptSkillId) {
		for (;;) {
			final int before = revision;
			if ((before & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}
			final int count = size;
			boolean found = false;
			for (int i = 0; i < count; i++) {
				final int id = skillIds[i];
				if (id != exceptSkillId && matchesSkillId.test(id)) {
					found = true;
					break;
				}
			}
			if (before == revision) return found;
		}
	}

	public int getRemainingTimeSec(int skillId) {
		for (;;) {
			final int before = revision;
			if ((before & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}

			final int n = size;
			int result = -1;

			for (int i = 0; i < n; i++) {
				if (skillIds[i] == skillId) {
					final int remaining = remainingSeconds[i];
					result = remaining < 0 ? 0 : remaining;
					break;
				}
			}

			if (before == revision) {
				return result;
			}
		}
	}

	/**
	 * CIO owner only.
	 */
	public void put(int skillId, int timeMillis) {
		beginWrite();
		try {
			final int newRemaining =
					timeMillis > 0
							? Math.max(1, timeMillis / 1000)
							: -1;

			final long nextTick =
					newRemaining >= 0
							? System.currentTimeMillis() + 1000L
							: 0L;

			final int index = indexOf(skillId);
			if (index >= 0) {
				final int oldRemaining = remainingSeconds[index];

				if (oldRemaining >= 0 && newRemaining < 0) {
					timedCount--;
				} else if (oldRemaining < 0 && newRemaining >= 0) {
					timedCount++;
				}

				remainingSeconds[index] = newRemaining;
				nextTickAtMillis[index] = nextTick;
				return;
			}

			ensureCapacity(size + 1);

			skillIds[size] = skillId;
			remainingSeconds[size] = newRemaining;
			nextTickAtMillis[size] = nextTick;
			size++;

			if (newRemaining >= 0) {
				timedCount++;
			}
		} finally {
			endWrite();
		}
	}

	/**
	 * CIO owner only.
	 *
	 * @param stop true = execute legacy stop cleanup after removing state.
	 */
	public boolean remove(int skillId, boolean stop) {
		boolean removed = false;

		beginWrite();
		try {
			final int index = indexOf(skillId);
			if (index >= 0) {
				removeAt(index);
				removed = true;

				if (timedCount == 0) {
					invalidateHeartbeat();
				}
			}
		} finally {
			endWrite();
		}

		if (removed) {
			if (stop) {
				try { L1SkillStop.stopSkill(owner, skillId); }
				finally { retireModifierIfInactive(skillId); }
			} else {
				removeSkillModifier(skillId, false);
			}
		}

		return removed;
	}

	/**
	 * CIO owner only.
	 * Legacy clearSkillEffectTimer semantics: drop lifetime state without
	 * invoking every stop handler.
	 */
	public int clear() {
		beginWrite();
		try {
			size = 0;
			timedCount = 0;
			invalidateHeartbeat();
		} finally {
			endWrite();
		}
		if (skillModifiers != null) {
			skillModifiers = null;
			validateSkillMod(false);
		}
		return heartbeatGeneration;
	}

	/**
	 * CIO owner only.
	 */
	public int tryArmHeartbeat() {
		if (heartbeatArmed || timedCount <= 0) {
			return 0;
		}

		int next = heartbeatGeneration + 1;
		if (next == 0) {
			next = 1;
		}

		heartbeatGeneration = next;
		heartbeatArmed = true;
		return next;
	}

	/**
	 * CIO owner only.
	 */
	public boolean isHeartbeatValid(int generation) {
		return generation != 0
				&& heartbeatArmed
				&& heartbeatGeneration == generation
				&& timedCount > 0;
	}

	/**
	 * CIO owner only.
	 */
	public void releaseHeartbeat(int generation) {
		if (generation != 0
				&& heartbeatArmed
				&& heartbeatGeneration == generation) {

			heartbeatArmed = false;
		}
	}

	/**
	 * CIO owner only. One shared 1-second heartbeat for every timed effect
	 * owned by this character.
	 */
	public long onHeartbeat(int generation) {
		if (!isHeartbeatValid(generation)) {
			return 0L;
		}

		int expiredCount = 0;
		long nextDue = Long.MAX_VALUE;

		beginWrite();
		try {

			final long now = System.currentTimeMillis();

			int i = 0;
			while (i < size) {
				int remaining = remainingSeconds[i];

				if (remaining < 0) {
					i++;
					continue;
				}

				long due = nextTickAtMillis[i];

				if (now >= due) {
					/*
					 * Do not lose elapsed seconds when the owner/wheel runs late.
					 * Keep the original phase instead of doing "now + 1000".
					 */
					long elapsedTicks = ((now - due) / 1000L) + 1L;

					if (skillIds[i] == L1SkillId.EXP_POTION
							&& owner.getMap().isSafetyZone(
									owner.getX(),
									owner.getY())) {

						due += elapsedTicks * 1000L;
						nextTickAtMillis[i] = due;
						if (due < nextDue) {
							nextDue = due;
						}
						i++;
						continue;
					}

					if (elapsedTicks >= remaining) {
						ensureScratchCapacity(expiredCount + 1);
					expiredScratch[expiredCount++] = skillIds[i];
						removeAt(i);
						continue;
					}

					remaining -= (int) elapsedTicks;
					remainingSeconds[i] = remaining;

					due += elapsedTicks * 1000L;
					nextTickAtMillis[i] = due;
				}

				if (due < nextDue) {
					nextDue = due;
				}

				i++;
			}

			if (timedCount <= 0) {
				heartbeatArmed = false;
				nextDue = Long.MAX_VALUE;
			}
		} finally {
			endWrite();
		}

		/*
		 * Stop handlers may re-enter skill-effect APIs. We are still on the
		 * same CIO owner, so those mutations execute directly and remain ordered.
		 */
		boolean invisibilityExpired = false;
		for (int i = 0; i < expiredCount; i++) {
			final int expiredId = expiredScratch[i];
			// An earlier stop callback may have cast this skill again. Its new
			// lifetime must not receive the expired instance's stop side effects.
			if (contains(expiredId)) continue;
			if (expiredId == L1SkillId.INVISIBILITY
					|| expiredId == L1SkillId.BLIND_HIDING) {
				invisibilityExpired = true;
			}
			try {
				L1SkillStop.stopSkill(owner, expiredId);
			} catch (Throwable failure) {
				// A failed stop is isolated from other expirations, but never hidden.
				LOG.log(Level.SEVERE, "Skill expiration cleanup failed for id=" + expiredId, failure);
			} finally {
				retireModifierIfInactive(expiredId);
			}
		}
		if (invisibilityExpired && owner instanceof L1PcInstance pc
				&& !pc.isInvisble()) {
			NpcEventFactory.onPlayerVisibilityRestored(pc);
		}

		// Stop handlers run on the owner and may replace/add/remove other timers.
		// Re-read only on expiration, keeping ordinary heartbeat ticks O(active).
		if (expiredCount != 0) {
			if (!isHeartbeatValid(generation)) return 0L;
			nextDue = Long.MAX_VALUE;
			for (int j = 0; j < size; j++) {
				if (remainingSeconds[j] >= 0 && nextTickAtMillis[j] < nextDue)
					nextDue = nextTickAtMillis[j];
			}
		}
		if (nextDue == Long.MAX_VALUE) {
			return 0L;
		}

		final long delay = nextDue - System.currentTimeMillis();

		/*
		 * Never drift the phase forward. If already due, run on the next wheel
		 * opportunity; otherwise sleep exactly until the earliest effect tick.
		 */
		if (delay <= 0L) {
			return 1L;
		}
		return delay > 1000L ? 1000L : delay;
	}

	private void retireModifierIfInactive(int id) {
		if (skillModifiers != null && skillModifiers.find(id) >= 0 && !contains(id))
			removeSkillModifier(id, false);
	}

	private void beginWrite() {
		revision++;
	}

	private void endWrite() {
		revision++;
	}

	private int indexOf(int skillId) {
		for (int i = 0; i < size; i++) {
			if (skillIds[i] == skillId) {
				return i;
			}
		}
		return -1;
	}

	private void removeAt(int index) {
		if (remainingSeconds[index] >= 0) {
			timedCount--;
		}

		final int last = --size;

		if (index != last) {
			skillIds[index] = skillIds[last];
			remainingSeconds[index] = remainingSeconds[last];
			nextTickAtMillis[index] = nextTickAtMillis[last];
		}

		skillIds[last] = 0;
		remainingSeconds[last] = 0;
		nextTickAtMillis[last] = 0L;
	}

	private void invalidateHeartbeat() {
		if (!heartbeatArmed) {
			return;
		}

		heartbeatArmed = false;

		int next = heartbeatGeneration + 1;
		if (next == 0) {
			next = 1;
		}
		heartbeatGeneration = next;
	}

	private void ensureCapacity(int required) {
		if (required <= skillIds.length) {
			return;
		}

		int newCapacity = skillIds.length << 1;
		while (newCapacity < required) {
			newCapacity <<= 1;
		}

		final int[] newIds = new int[newCapacity];
		final int[] newRemaining = new int[newCapacity];
		final long[] newNextTickAt = new long[newCapacity];

		System.arraycopy(skillIds, 0, newIds, 0, size);
		System.arraycopy(remainingSeconds, 0, newRemaining, 0, size);
		System.arraycopy(nextTickAtMillis, 0, newNextTickAt, 0, size);

		skillIds = newIds;
		remainingSeconds = newRemaining;
		nextTickAtMillis = newNextTickAt;
	}

	private void ensureScratchCapacity(int required) {
		if (expiredScratch == null) expiredScratch = new int[INITIAL_CAPACITY];
		else if (required > expiredScratch.length)
			expiredScratch = java.util.Arrays.copyOf(expiredScratch, expiredScratch.length << 1);
	}
}
