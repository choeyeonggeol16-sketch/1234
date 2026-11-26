package l1j.server.server.model.character.ItemSkill.Doll;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.item.doll.DollRegistry.CombatProc;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;

/** src22 doll on-hit procs, adapted to current doll lifecycle and CIO packets. */
public final class MagicDollCombatProcs {
    private static final Logger LOG = Logger.getLogger(MagicDollCombatProcs.class.getName());
    private MagicDollCombatProcs() {}

    public static boolean isProcDoll(int dollType) {
        return DollRegistry.combatProc(dollType) != CombatProc.NONE;
    }

    public static int applyOnHit(L1PcInstance attacker, L1Character target, int damage, boolean meleePhysicalHit) {
        if (attacker == null || target == null || attacker == target || damage <= 0) return damage;
        final MagicDollCombatConfig.Settings rates = MagicDollCombatConfig.get();
        if (rates.enabled() == 0) return damage;

        // Managed by L1PcInstance.addDoll/removeDoll, no scan or temporary collection per hit.
        final L1DollInstance doll = attacker.getCombatProcDoll();
        if (doll == null || doll.isDeleted() || doll.getMaster() != attacker) return damage;

        final CombatProc proc = DollRegistry.combatProc(doll.getDollType());
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
        if ((proc == CombatProc.DEATH || proc == CombatProc.BLACK) && target.hasSkillEffect(L1SkillId.ERASE_MAGIC)) {
            target.removeSkillEffect(L1SkillId.ERASE_MAGIC);
            if (target instanceof L1PcInstance pc) {
                SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 55, false);
            }
        }
        if ((proc == CombatProc.DEATH || proc == CombatProc.BLACK) && target.GetMDefendBySkill() <= rates.criticalMrMax()
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
