package l1j.server.server.model.character.skill;

import java.util.Iterator;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Magic;

/** Shared definitions for the existing cast and expiration entry points. */
final class CSkillEffect {
    private static final int PAGED_LIMIT = 32768;
    private static final int PAGE_SHIFT = 5;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final CSkillEffect[][] pages = new CSkillEffect[PAGED_LIMIT >>> PAGE_SHIFT][];
    private static final Int2ObjectOpenHashMap<CSkillEffect> sparse = new Int2ObjectOpenHashMap<>(48);

    private static boolean frozen;

    static {
        SkillHandlers.registerEffects();
        SkillStopHandlers.registerEffects();
        frozen = true;
    }

    private final SkillHandlers.SkillHandler instant;
    // A legacy callback can include both an immediate action and a timed effect.
    private final SkillHandlers.SkillHandler pumpStart;
    private final SkillStopHandlers.StopHandler pumpEnd;

    private CSkillEffect(SkillHandlers.SkillHandler instant, SkillHandlers.SkillHandler pumpStart,
            SkillStopHandlers.StopHandler pumpEnd) {
        this.instant = instant;
        this.pumpStart = pumpStart;
        this.pumpEnd = pumpEnd;
    }

    static CSkillEffect get(int skillId) {
        if (skillId < 0) return null;
        if (skillId >= PAGED_LIMIT) return sparse.get(skillId);
        final CSkillEffect[] page = pages[skillId >>> PAGE_SHIFT];
        return page == null ? null : page[skillId & (PAGE_SIZE - 1)];
    }

    private static void put(int skillId, CSkillEffect effect) {
        if (skillId >= PAGED_LIMIT) {
            sparse.put(skillId, effect);
        } else {
            final int index = skillId >>> PAGE_SHIFT;
            CSkillEffect[] page = pages[index];
            if (page == null) pages[index] = page = new CSkillEffect[PAGE_SIZE];
            page[skillId & (PAGE_SIZE - 1)] = effect;
        }
    }

    // These methods are called only by the two boot registrations above.
    static void registerInstant(int skillId, SkillHandlers.SkillHandler instant) {
        if (!frozen && skillId >= 0 && instant != null) put(skillId, new CSkillEffect(instant, null, null));
    }

    static void registerPumpStart(int skillId, SkillHandlers.SkillHandler pumpStart) {
        if (!frozen && skillId >= 0 && pumpStart != null) put(skillId, new CSkillEffect(null, pumpStart, null));
    }

    static void registerLegacyCast(int skillId, SkillHandlers.SkillHandler handler) {
        registerPumpStart(skillId, handler);
    }

    static void registerPumpEnd(int skillId, SkillStopHandlers.StopHandler pumpEnd) {
        if (frozen || skillId < 0 || pumpEnd == null) return;
        final CSkillEffect start = get(skillId);
        put(skillId, start == null ? new CSkillEffect(null, null, pumpEnd)
                : new CSkillEffect(start.instant, start.pumpStart, pumpEnd));
    }

    SkillHandlers.HandlerResult activate(L1SkillUse cast, L1Character target, L1Magic magic,
            int undeadType, Iterator<?> targets) {
        if (instant != null) return instant.handle(cast, target, magic, undeadType, targets);
        if (pumpStart != null) return pumpStart.handle(cast, target, magic, undeadType, targets);
        return SkillHandlers.HandlerResult.NOT_HANDLED;
    }

    SkillStopHandlers.StopHandler pumpEnd() {
        return pumpEnd;
    }

    /** Pure L1 adjustments. The caller owns timer changes and packet order. */
    enum PhysicalDefence {
        DIAMOND_GOLEM(-10, 0),
        SHINING_AURA(-8, 0),
        EARTH_DRAGON(-3, 5);

        private final int ac;
        private final int mr;

        PhysicalDefence(int ac, int mr) {
            this.ac = ac;
            this.mr = mr;
        }

        void pump(L1Character target) {
            target.getAC().addAc(ac);
            if (mr != 0) target.getResistance().addMr(mr);
        }

        void pumpEnd(L1Character target) {
            target.getAC().addAc(-ac);
            if (mr != 0) target.getResistance().addMr(-mr);
        }
    }

    enum MaxHp {
        PRIDE(L1SkillId.PRIDE, 4.0, 8854, 5264, 5265),
        GIGANTIC(L1SkillId.GIGANTIC, 2.0, 6168, 3918, 3919);

        private final double levelDivisor;
        final int skillId;
        final int icon;
        final int startMessage;
        final int stopMessage;

        MaxHp(int skillId, double levelDivisor, int icon, int startMessage, int stopMessage) {
            this.skillId = skillId;
            this.levelDivisor = levelDivisor;
            this.icon = icon;
            this.startMessage = startMessage;
            this.stopMessage = stopMessage;
        }

        void pump(L1PcInstance target) {
            final double percent = target.getLevel() / levelDivisor;
            final int hp = (int) Math.round(target.getBaseMaxHp() * (percent * 0.01));
            target.setMagicBuffHp(hp);
            target.addMaxHp(target.getMagicBuffHp());
        }

        void pumpEnd(L1PcInstance target) {
            target.addMaxHp(-target.getMagicBuffHp());
            target.setMagicBuffHp(0);
        }
    }

    static void pumpAdvanceSpirit(L1PcInstance target) {
        target.setAdvenHp(target.getBaseMaxHp() / 5);
        target.setAdvenMp(target.getBaseMaxMp() / 5);
        target.addMaxHp(target.getAdvenHp());
        target.addMaxMp(target.getAdvenMp());
    }

    static void pumpEndAdvanceSpirit(L1PcInstance target) {
        target.addMaxHp(-target.getAdvenHp());
        target.addMaxMp(-target.getAdvenMp());
        target.setAdvenHp(0);
        target.setAdvenMp(0);
    }
}
