package l1j.server.server.model.character.scheduler;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import l1j.server.Config;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.utils.CalcStat;

/**
 * Per-PC creature heartbeat regen owner.
 *
 * This object owns only creature-local state and game logic.
 */
public final class CRegenController {

    private static final long HEARTBEAT_MS = 666L;

    // Existing L1 cadence converted to the closest 666ms heartbeat count.
    private static final int HP_IDLE_TICKS = ticksForSeconds(3);   // 5  = 3.330s
    private static final int HP_MOVE_TICKS = ticksForSeconds(6);   // 9  = 5.994s
    private static final int HP_ATK_TICKS  = ticksForSeconds(12);  // 18 = 11.988s

    private static final int MP_IDLE_TICKS = ticksForSeconds(16);  // 24 = 15.984s
    private static final int MP_MOVE_TICKS = ticksForSeconds(32);  // 48 = 31.968s
    private static final int MP_ATK_TICKS  = ticksForSeconds(64);  // 96 = 63.936s

    private static final int DOLL_HP_TICKS     = ticksForSeconds(32);
    private static final int DOLL_MP_TICKS     = ticksForSeconds(64);
    private static final int DOLL_ACTION_TICKS = ticksForSeconds(30);
    private static final int VALA_MP_TICKS     = ticksForSeconds(16);
    private static final int DRAGON_FX_TICKS   = ticksForSeconds(180);

    private static final int VALA_MP_ADD = 4;
    private static final int LIND_ARMOR_FX_GFX = 763;
    private static final int PAPU_ARMOR_FX_GFX = 2245;

    private final L1PcInstance actor;

    // These two can be touched by packet/action/damage paths outside the heartbeat callback.
    private volatile long lastMoveMs;
    private volatile long lastAttackMs;

    // Doll change can arrive outside the owner heartbeat. Epoch collapse is fine: one reset is enough.
    private volatile int dollEpoch;
    private int seenDollEpoch;

    // Owner-thread heartbeat state. No atomics/collections/nodes are required.
    private volatile int lastLocKey;
    private int hpTicks;
    private int mpTicks;
    private int dollHpTicks;
    private int dollMpTicks;
    private int dollActionTicks;
    private int valaMpTicks;
    private int dragonFxTicks;

    public CRegenController(L1PcInstance actor) {
        if (actor == null) {
            throw new NullPointerException("actor");
        }
        this.actor = actor;
        Reset();
    }

    public void Reset() {
        lastMoveMs = 0L;
        lastAttackMs = 0L;
        lastLocKey = safeLocKey(actor);

        hpTicks = HP_IDLE_TICKS;
        mpTicks = MP_IDLE_TICKS;
        dollHpTicks = DOLL_HP_TICKS;
        dollMpTicks = DOLL_MP_TICKS;
        dollActionTicks = DOLL_ACTION_TICKS;
        valaMpTicks = VALA_MP_TICKS;
        dragonFxTicks = DRAGON_FX_TICKS;

        seenDollEpoch = dollEpoch;
    }

    public void OnMove(long ignoredNowMs) {
        lastMoveMs = monotonicMs();
        lastLocKey = safeLocKey(actor);
    }

    public void OnAttack(long ignoredNowMs) {
        lastAttackMs = monotonicMs();
    }

    public void OnDollChanged() {
        dollEpoch++;
    }

    /**
     * Called only by the actor's CIOThread creature heartbeat.
     */
    public void TimerExpired() {
        final L1PcInstance pc = actor;
        if (isPcCk(pc)) {
            return;
        }

        final int epoch = dollEpoch;
        if (seenDollEpoch != epoch) {
            seenDollEpoch = epoch;
            dollHpTicks = DOLL_HP_TICKS;
            dollMpTicks = DOLL_MP_TICKS;
            dollActionTicks = DOLL_ACTION_TICKS;
        }

        final long nowMs = monotonicMs();

        if (--hpTicks <= 0) {
            regenHp(pc);
            clanbuff(pc);
            hpTicks = calcHpIntervalTicks(pc, nowMs);
        }

        if (--mpTicks <= 0) {
            regenMp(pc);
            mpTicks = calcMpIntervalTicks(pc, nowMs);
        }

        if (--dollHpTicks <= 0) {
            if (pc.getDollHpRegenTick() > 0) {
                applyDollHpTick(pc);
            }
            dollHpTicks = DOLL_HP_TICKS;
        }

        if (--dollMpTicks <= 0) {
            if (pc.getDollMpRegenTick() > 0) {
                applyDollMpTick(pc);
            }
            dollMpTicks = DOLL_MP_TICKS;
        }

        if (--valaMpTicks <= 0) {
            applyValakasMpTick(pc);
            valaMpTicks = VALA_MP_TICKS;
        }

        if (--dollActionTicks <= 0) {
            if (pc.getDollList() != null && !pc.getDollList().isEmpty()) {
                applyDollActionTick(pc);
            }
            dollActionTicks = DOLL_ACTION_TICKS;
        }

        if (--dragonFxTicks <= 0) {
            playDragonArmorFxIfNeeded(pc);
            dragonFxTicks = DRAGON_FX_TICKS;
        }
    }

    /** Compatibility hook for old direct callers while the old scheduler is removed. */
    public void RegenHpNow() {
        regenHp(actor);
    }

    /** Compatibility hook for old direct callers while the old scheduler is removed. */
    public void RegenMpNow() {
        regenMp(actor);
    }

    private int calcHpIntervalTicks(L1PcInstance pc, long nowMs) {
        final long attack = lastAttackMs;
        if (attack > 0L && nowMs - attack <= 4_000L) {
            return HP_ATK_TICKS;
        }

        final long move = lastMoveMs;
        if (move > 0L && nowMs - move <= 2_000L) {
            return HP_MOVE_TICKS;
        }

        final int locKey = safeLocKey(pc);
        if (locKey != lastLocKey) {
            lastLocKey = locKey;
            return HP_MOVE_TICKS;
        }

        return HP_IDLE_TICKS;
    }

    private int calcMpIntervalTicks(L1PcInstance pc, long nowMs) {
        final long attack = lastAttackMs;
        if (attack > 0L && nowMs - attack <= 4_000L) {
            return MP_ATK_TICKS;
        }

        final long move = lastMoveMs;
        if (move > 0L && nowMs - move <= 2_000L) {
            return MP_MOVE_TICKS;
        }

        final int locKey = safeLocKey(pc);
        if (locKey != lastLocKey) {
            lastLocKey = locKey;
            return MP_MOVE_TICKS;
        }

        return MP_IDLE_TICKS;
    }

    private static void regenMp(L1PcInstance pc) {
        if (isPcCk(pc)) return;
        if (pc.getCurrentMp() >= pc.getMaxMp()) return;
        if (pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) return;

        int baseMpr = CalcStat.naturalMpRegen(pc.getLevel(),
                pc.getAbility().getTotalWis(), pc.getAbility().getWis());

        if (pc.hasSkillEffect(L1SkillId.STATUS_BLUE_POTION2)) baseMpr += 1;
        else if (pc.hasSkillEffect(L1SkillId.STATUS_BLUE_POTION)) baseMpr += 2;

        if (pc.hasSkillEffect(L1SkillId.비욘드)) {
            baseMpr += Config.메디테이션엠피1 + (pc.getMaxMp() * (Config.메디테이션엠피 * 0.01));
        }
        if (pc.hasSkillEffect(L1SkillId.MEDITATION))
            baseMpr += Config.메디테이션엠피1;
        if (pc.hasSkillEffect(L1SkillId.CONCENTRATION)) baseMpr += 4;

        if (L1HouseLocation.isInHouse(pc.getX(), pc.getY(), pc.getMapId())) baseMpr += 3;

        if (pc.hasSkillEffect(L1SkillId.COOKING_1_2_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_2_S)) baseMpr += 3;
        if (pc.hasSkillEffect(L1SkillId.COOKING_1_20_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_20_S)) baseMpr += 2;
        if (pc.hasSkillEffect(L1SkillId.COOKING_1_12_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_12_S)) baseMpr += 2;

        if (pc.hasSkillEffect(L1SkillId.STATUS_CASHSCROLL2)) baseMpr += 4;

        if (isInn(pc)) {
            baseMpr += 5;
        }

        if (((pc.getMapId() == 4 && pc.getX() >= 33339 && pc.getX() <= 33543 && pc.getY() >= 32651 && pc.getY() <= 32926)
                || pc.getMapId() == 800)) {
            baseMpr += 20;
        }

        if (L1HouseLocation.isRegenLoc(pc, pc.getX(), pc.getY(), pc.getMapId())) baseMpr += 3;

        int itemMpr = pc.getMpr();

        if (pc.get_food() < 40) return;

        int mpr = CalcStat.naturalRegenAfterWeight(baseMpr + itemMpr,
                isOverWeight(pc), pc.getAbility().getWis());
        int newMp = pc.getCurrentMp() + mpr;
        if (newMp > pc.getMaxMp()) newMp = pc.getMaxMp();
        pc.setCurrentMp(newMp);
    }

    private static void regenHp(L1PcInstance pc) {
        if (isPcCk(pc)) return;
        if (pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) return;

        if (pc.getCurrentHp() >= pc.getMaxHp() && !isUnderwater(pc)) return;

        int maxBonus = 1;
        if (pc.getLevel() > 11 && pc.getAbility().getTotalCon() >= 14) {
            maxBonus = pc.getAbility().getTotalCon() - 12;
            if (pc.getAbility().getTotalCon() > 25) maxBonus = 14;
        }

        int basebonus = CalcStat.피회복틱(pc.getAbility().getCon());
        int equipHpr = pc.getHpr();

        int bonus = ThreadLocalRandom.current().nextInt(maxBonus) + 1;

        if (pc.hasSkillEffect(L1SkillId.NATURES_TOUCH)) bonus += 15;
        if (L1HouseLocation.isInHouse(pc.getX(), pc.getY(), pc.getMapId())) bonus += 5;

        if (pc.hasSkillEffect(L1SkillId.COOKING_1_12_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_12_S)) bonus += 2;
        if (pc.hasSkillEffect(L1SkillId.COOKING_1_19_N) || pc.hasSkillEffect(L1SkillId.COOKING_1_19_S)) bonus += 2;

        if (pc.hasSkillEffect(L1SkillId.STATUS_CASHSCROLL)) bonus += 4;
        if (isInn(pc)) {
            bonus += 5;
        }
        if (L1HouseLocation.isRegenLoc(pc, pc.getX(), pc.getY(), pc.getMapId())) bonus += 5;

        boolean inLifeStream = false;
        if (isPlayerInLifeStream(pc)) {
            inLifeStream = true;
            bonus += 3;
        }

        if (pc.get_food() < 40 || pc.hasSkillEffect(L1SkillId.BERSERKERS)) {
            bonus = 0;
            basebonus = 0;
            equipHpr = 0;
        }

        int normalHpr = bonus + equipHpr + basebonus;
        int recovered = CalcStat.naturalRegenAfterWeight(normalHpr,
                isOverWeight(pc), pc.getAbility().getCon());
        int newHp = pc.getCurrentHp() + recovered;
        if (newHp < 1) newHp = 1;

        if (isUnderwater(pc)) {
            newHp -= 20;
            if (newHp < 1) {
                if (pc.isGm()) newHp = 1;
                else pc.death(null, true);
            }
        }

        if (isLv50Quest(pc) && !inLifeStream) {
            newHp -= 10;
            if (newHp < 1) {
                if (pc.isGm()) newHp = 1;
                else pc.death(null, true);
            }
        }

        if (pc.getMapId() == 410 && !inLifeStream) {
            newHp -= 10;
            if (newHp < 1) {
                if (pc.isGm()) newHp = 1;
                else pc.death(null, true);
            }
        }

        if (!pc.isDead()) {
            pc.setCurrentHp(Math.min(newHp, pc.getMaxHp()));
        }
    }

    private static void applyDollActionTick(L1PcInstance pc) {
        if (pc == null || pc.getDollList() == null || pc.getDollList().isEmpty()) {
            return;
        }

        L1DollInstance doll = pc.getDollList().values().iterator().next();
        if (doll == null) return;

        final int actionId =
                66 + ThreadLocalRandom.current().nextInt(2);

        SC_DoActionGFX.send(
                pc.getNetConnection(),
                doll.getId(),
                actionId);

        SC_DoActionGFX.broadcast(
                pc,
                actionId);
    }

    private static void applyValakasMpTick(L1PcInstance pc) {
        if (pc == null || !pc.getInventory().isEquippedAny(22208, 22209, 22210, 22211)) return;
        if (pc.getCurrentMp() >= pc.getMaxMp()) return;

        int newMp = Math.min(pc.getMaxMp(), pc.getCurrentMp() + VALA_MP_ADD);
        pc.setCurrentMp(newMp);
    }

    private static void playDragonArmorFxIfNeeded(L1PcInstance pc) {
        if (pc == null) return;

        if (pc.getInventory().isEquippedAny(22204, 22205, 22206, 22207)) {
            sendMeSkillSound(pc, pc.getId(), LIND_ARMOR_FX_GFX);
        } else if (pc.getInventory().isEquippedAny(22200, 22201, 22202, 22203)) {
            sendMeSkillSound(pc, pc.getId(), PAPU_ARMOR_FX_GFX);
        }
    }

    private static void applyDollHpTick(L1PcInstance pc) {
        int add = pc.getDollHpRegenTick();
        if (add <= 0) return;

        pc.setCurrentHp(Math.min(
                pc.getMaxHp(),
                Math.max(1, pc.getCurrentHp() + add)));

        sendSkillSound(pc, pc.getId(), 1608);
    }

    private static void applyDollMpTick(L1PcInstance pc) {
        int add = pc.getDollMpRegenTick();
        if (add <= 0) return;

        pc.setCurrentMp(Math.min(
                pc.getMaxMp(),
                pc.getCurrentMp() + add));

        sendSkillSound(pc, pc.getId(), 6321);
    }

    private static void sendMeSkillSound(L1PcInstance pc, int objId, int gfx) {
        SC_SkillSound.send(pc, objId, gfx);
    }

    private static void sendSkillSound(L1PcInstance pc, int objId, int gfx) {
        final long s = SC_SkillSound.pack(objId, gfx);
        SC_SkillSound.sendPacked(pc, s);
        SC_SkillSound.broadcastPacked(pc, s);
    }

    private static boolean isPcCk(L1PcInstance pc) {
        return pc == null
                || pc.isDead()
                || pc.getNetConnection() == null
                || pc.getCurrentHp() == 0
                || pc.noPlayerCK
                || pc.noPlayerck2;
    }

    private static boolean isUnderwater(L1PcInstance pc) {
        if (pc.hasSkillEffect(L1SkillId.STATUS_UNDERWATER_BREATH)) return false;
        if (pc.getInventory().isEquippedDirect(22207)) return false;
        return pc.getMap().isUnderwater();
    }

    private static boolean isOverWeight(L1PcInstance pc) {
        if (pc.hasSkillEffect(L1SkillId.EXOTIC_VITALIZE)
                || pc.hasSkillEffect(L1SkillId.ADDITIONAL_FIRE)
                || pc.hasSkillEffect(L1SkillId.SCALES_WATER_DRAGON)) {
            return false;
        }

        if (pc.is_top_ranker()) return false;
        if (isInn(pc)) return false;

        return 50 <= pc.getInventory().getWeight100();
    }

    private static boolean isLv50Quest(L1PcInstance pc) {
        int mapId = pc.getMapId();
        return mapId == 2000 || mapId == 2001;
    }

    private static void clanbuff(L1PcInstance pc) {
        try {
            if (isPcCk(pc)) return;

            L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan == null) return;

            if (pc.getClanid() != 0
                    && clan.getOnlineClanMember().length >= Config.CLAN_COUNT
                    && !pc.isClanBuff()) {

                pc.setSkillEffect(L1SkillId.CLANBUFF_YES, 0);
                SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 450, true);
                pc.setClanBuff(true);

            } else if (pc.getClanid() != 0
                    && clan.getOnlineClanMember().length < Config.CLAN_COUNT
                    && pc.isClanBuff()) {

                pc.killSkillEffectTimer(L1SkillId.CLANBUFF_YES);
                SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 450, false);
                pc.setClanBuff(false);
            }
        } catch (Throwable ignore) {
        }
    }

    private static boolean isPlayerInLifeStream(L1PcInstance pc) {
        try {
            for (L1Object object : pc.getKnownObjects()) {
                if (!(object instanceof L1EffectInstance)) continue;

                L1EffectInstance effect = (L1EffectInstance) object;
                if (effect.getNpcId() == 81169
                        && effect.getLocation().getTileLineDistance(pc.getLocation()) < 4) {

                    return true;
                }
            }
        } catch (Throwable ignore) {
        }

        return false;
    }

    private static boolean isInn(L1PcInstance pc) {
        int mapId = pc.getMapId();
        return mapId == 16384 || mapId == 16896 || mapId == 17408 || mapId == 17492 || mapId == 17820
                || mapId == 17920 || mapId == 18432 || mapId == 18944 || mapId == 19456 || mapId == 19968
                || mapId == 20480 || mapId == 20992 || mapId == 21504 || mapId == 22016 || mapId == 22528
                || mapId == 23040 || mapId == 23552 || mapId == 24064 || mapId == 24576 || mapId == 25088;
    }

    private static int safeLocKey(L1PcInstance pc) {
        int map = pc.getMapId() & 0xFFFF;
        int x = pc.getX() & 0x0FFF;
        int y = pc.getY() & 0x0FFF;
        return (map << 24) ^ (x << 12) ^ y;
    }

    private static int ticksForSeconds(int seconds) {
        return Math.max(1, (int) (((seconds * 1000L) + (HEARTBEAT_MS >>> 1)) / HEARTBEAT_MS));
    }

    private static long monotonicMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
