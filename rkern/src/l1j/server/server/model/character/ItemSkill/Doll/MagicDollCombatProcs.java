package l1j.server.server.model.character.ItemSkill.Doll;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;

/** src22 doll on-hit procs, adapted to current doll lifecycle and CIO packets. */
public final class MagicDollCombatProcs {
    private static final Logger LOG = Logger.getLogger(MagicDollCombatProcs.class.getName());
    private static final int NONE = 0;
    private static final int DEATH = 1;
    private static final int BLACK = 2;
    private static final int WOLF = 3;
    private static final int DESEL = 4;
    private static final int HAL = 5;
    private static final int AU = 6;
    private static final int BEHE = 7;
    private static final int RULER1 = 8;
    private static final int RULER1_DAILY = 9;
    private static final int RULER2 = 10;
    private static final int RULER2_DAILY = 11;
    private static final int RULER3 = 12;
    private static final int RULER3_DAILY = 13;
    private static final int RULER4 = 14;
    private static final int RULER4_DAILY = 15;

    private MagicDollCombatProcs() {}

    public static boolean isProcDoll(int dollType) { return procForType(dollType) != NONE; }

    public static int procForType(int type) {
        return switch (type) {
            case L1DollInstance.DOLLTYPE_DEATHNIGHT, L1DollInstance.DOLLTYPE_축데스 -> DEATH;
            case L1DollInstance.DOLLTYPE_흑장로, L1DollInstance.DOLLTYPE_축흑장로 -> BLACK;
            case L1DollInstance.DOLLTYPE_WAREWOLF, L1DollInstance.DOLLTYPE_CRUSTACEA -> WOLF;
            case L1DollInstance.desel -> DESEL;
            case L1DollInstance.hal -> HAL;
            case L1DollInstance.au -> AU;
            case L1DollInstance.behe -> BEHE;
            case L1DollInstance.ruler1 -> RULER1;
            case L1DollInstance.ruler5 -> RULER1_DAILY;
            case L1DollInstance.ruler2 -> RULER2;
            case L1DollInstance.ruler6 -> RULER2_DAILY;
            case L1DollInstance.ruler3 -> RULER3;
            case L1DollInstance.ruler7 -> RULER3_DAILY;
            case L1DollInstance.ruler4 -> RULER4;
            case L1DollInstance.ruler8 -> RULER4_DAILY;
            default -> NONE;
        };
    }

    /** One proc roll per confirmed hit. MeleeOnly applies only to the werewolf proc. */
    public static int applyOnHit(L1PcInstance attacker, L1Character target, int damage, boolean meleePhysicalHit) {
        if (attacker == null || target == null || attacker == target || damage <= 0) return damage;
        final MagicDollCombatConfig.Settings rates = MagicDollCombatConfig.get();
        if (rates.enabled() == 0) return damage;

        // Managed by L1PcInstance.addDoll/removeDoll, no scan or temporary collection per hit.
        final L1DollInstance doll = attacker.getCombatProcDoll();
        if (doll == null || doll.isDeleted() || doll.getMaster() != attacker) return damage;

        final int proc = procForType(doll.getDollType());
        final int chance, lo, hi, fx;
        final MagicDollCombatConfig.ExtraDollProc extra;
        switch (proc) {
            case DEATH:
                chance = rates.deathChance(); lo = rates.deathMin(); hi = rates.deathMax(); fx = rates.deathFx();
                extra = null;
                break;
            case BLACK:
                chance = rates.blackChance(); lo = rates.blackMin(); hi = rates.blackMax(); fx = rates.blackFx();
                extra = null;
                break;
            case WOLF:
                if (!meleePhysicalHit) return damage;
                chance = rates.wolfChance(); lo = hi = rates.wolfDamage(); fx = rates.wolfFx();
                extra = null;
                break;
            default:
                extra = switch (proc) {
                    case DESEL -> rates.desel();
                    case HAL -> rates.hal();
                    case AU -> rates.au();
                    case BEHE -> rates.behe();
                    case RULER1 -> rates.ruler1();
                    case RULER1_DAILY -> rates.ruler1Daily();
                    case RULER2 -> rates.ruler2();
                    case RULER2_DAILY -> rates.ruler2Daily();
                    case RULER3 -> rates.ruler3();
                    case RULER3_DAILY -> rates.ruler3Daily();
                    case RULER4 -> rates.ruler4();
                    case RULER4_DAILY -> rates.ruler4Daily();
                    default -> null;
                };
                if (extra == null) return damage;
                chance = extra.chance(); lo = extra.minDamage(); hi = extra.maxDamage(); fx = extra.effect();
                break;
        }
        if (chance <= 0 || ThreadLocalRandom.current().nextInt(100) >= chance) return damage;
        int bonus = lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
        if (bonus <= 0) return damage;

        // Erase and MR critical belong only to the original Death Knight / Black Elder procs.
        if ((proc == DEATH || proc == BLACK) && target.hasSkillEffect(L1SkillId.ERASE_MAGIC)) {
            target.removeSkillEffect(L1SkillId.ERASE_MAGIC);
            if (target instanceof L1PcInstance pc) {
                SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 55, false);
            }
        }
        if ((proc == DEATH || proc == BLACK) && target.getResistance().getEffectedMrBySkill() <= rates.criticalMrMax()
                && ThreadLocalRandom.current().nextInt(100) < rates.criticalChance()) {
            bonus = (int) (((long) bonus * rates.criticalMultiplierPercent() + 50L) / 100L);
        }
        if (fx > 0) {
            try {
                SC_UseAttackSkill.sendAndBroadcast(attacker.getNetConnection(), doll, attacker,
                        target.getId(), fx, target.getX(), target.getY(), ActionCodes.ACTION_SkillAttack, true);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Magic doll proc visual effect failed: doll=" + doll.getId(), ex);
            }
        }
        return damage > Integer.MAX_VALUE - bonus ? Integer.MAX_VALUE : damage + bonus;
    }
}
