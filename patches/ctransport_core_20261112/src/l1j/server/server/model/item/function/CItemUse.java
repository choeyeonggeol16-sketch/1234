package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

public final class CItemUse {

    public enum Result {
        NOT_HANDLED,
        HANDLED,
        RETURN
    }

    static final int ITEM_FAFU_MAAN = 410032;
    static final int ITEM_LIND_MAAN = 410033;
    static final int ITEM_ANTA_MAAN = 410034;
    static final int ITEM_VALA_MAAN = 410035;
    static final int ITEM_BIRTH_MAAN = 410036;
    static final int ITEM_SHAPE_MAAN = 410037;
    static final int ITEM_LIFE_MAAN = 410038;

    static final int ITEM_RAID_1 = 779911;
    static final int ITEM_RAID_2 = 779922;
    static final int ITEM_RAID_3 = 779933;

    static final int ITEM_LIFE_MAAN_ONCE = 3000159;

    static final int ITEM_ADENA_VOUCHER_100M = 400254;
    static final int ITEM_ADENA_VOUCHER_50M = 500254;
    static final int ITEM_ADENA_VOUCHER_10M = 600254;
    static final int ITEM_ADENA = 40308;
    static final int ADENA_EXCHANGE_LIMIT = 2_000_000_000;

    static final int RAID_PORTAL_LIFETIME_MS = 600_000;

    // Exact legacy roll tables. One shared allocation instead of arrays per use.
    static final int[] ENCHANT_BOX_3000039_REWARDS = {
        20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056,
        20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20056, 20074, 20079
    };
    static final int[] ENCHANT_BOX_3000039_ENCHANTS = {
        0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 5, 5, 6
    };

    static final int[] ENCHANT_BOX_3000169_REWARDS = {
        66, 12, 134, 86, 61, 202011, 202012, 202013, 202014
    };
    static final int[] ENCHANT_BOX_3000169_ENCHANTS = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 3, 3
    };

    static final int[] ENCHANT_BOX_718_REWARDS = {
        315, 316, 317, 318, 319, 320, 1104, 7000136, 7000213
    };
    static final int[] ENCHANT_BOX_718_ENCHANTS = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3
    };

    static final int[] TRANSFORM_WEAPON_698 = { 40346, 40370, 40362, 40354 };
    static final int[] TRANSFORM_WEAPON_776 = { 3000088, 3000352, 755, 756, 757 };

    static final int[] CLASS_GIFT_COMMON = {
        600223, 600199, 600200, 600201, 600202, 600203, 600204,
        600198, 600213, 600214, 600215, 600216, 600217
    };

    public static void useCreatureItem(CIOClient client, L1PcInstance pc,
            L1ItemInstance item, int itemObjid, int targetId,
            int x, int y, String name, int skillId) {
        ItemUseCreature.UseItem(client, pc, item, itemObjid, targetId, x, y, name, skillId);
    }

    private CItemUse() {
    }

    public static boolean isHandledItem(int itemId) {
        switch (itemId) {
        case ITEM_FAFU_MAAN:
        case ITEM_LIND_MAAN:
        case ITEM_ANTA_MAAN:
        case ITEM_VALA_MAAN:
        case ITEM_BIRTH_MAAN:
        case ITEM_SHAPE_MAAN:
        case ITEM_LIFE_MAAN:
        case ITEM_RAID_1:
        case ITEM_RAID_2:
        case ITEM_RAID_3:
        case ITEM_LIFE_MAAN_ONCE:
        case ITEM_ADENA_VOUCHER_100M:
        case ITEM_ADENA_VOUCHER_50M:
        case ITEM_ADENA_VOUCHER_10M:
            return true;

        default:
            return false;
        }
    }

    public static Result use(
            L1PcInstance pc,
            L1ItemInstance item) {

        if (pc == null || item == null) {
            return Result.NOT_HANDLED;
        }

        final int itemId = item.getItemId();

        if (!isHandledItem(itemId)) {
            return Result.NOT_HANDLED;
        }

        // A voucher is a debit-and-grant transaction, not a normal reusable
        // consumable. Never write its timestamp or start item delay before COMMIT.
        if (itemId == ITEM_ADENA_VOUCHER_100M
                || itemId == ITEM_ADENA_VOUCHER_50M
                || itemId == ITEM_ADENA_VOUCHER_10M) {
            return useAdenaVoucher(pc, item);
        }

        // Preserve any pre-existing cooldown, but never create or mutate the
        // consumable's last-used timestamp ahead of the durable debit.
        if (itemId == ITEM_LIFE_MAAN_ONCE) {
            final Timestamp previous = item.getLastUsed();
            if (previous != null && System.currentTimeMillis() < previous.getTime())
                return Result.HANDLED;
            return useLifeMaanOnce(pc, item);
        }

        Timestamp lastUsed = item.getLastUsed();

        // Raid consumables have a deferred durable debit. Do not modify their
        // live cooldown timestamp until the receipt is committed.
        final boolean raid = itemId == ITEM_RAID_1 || itemId == ITEM_RAID_2
                || itemId == ITEM_RAID_3;
        if (lastUsed == null && !raid) {
            lastUsed = nowTimestamp();
            item.setLastUsed(lastUsed);
        }

        final long now = System.currentTimeMillis();

        if (lastUsed != null && now < lastUsed.getTime()) {
            return Result.HANDLED;
        }

        switch (itemId) {
        case ITEM_FAFU_MAAN:
            castAndTouch(pc, item, L1SkillId.FAFU_MAAN);
            return Result.HANDLED;

        case ITEM_LIND_MAAN:
            castAndTouch(pc, item, L1SkillId.LIND_MAAN);
            return Result.HANDLED;

        case ITEM_ANTA_MAAN:
            castAndTouch(pc, item, L1SkillId.ANTA_MAAN);
            return Result.HANDLED;

        case ITEM_VALA_MAAN:
            castAndTouch(pc, item, L1SkillId.VALA_MAAN);
            return Result.HANDLED;

        case ITEM_BIRTH_MAAN:
            castAndTouch(pc, item, L1SkillId.BIRTH_MAAN);
            return Result.HANDLED;

        case ITEM_SHAPE_MAAN:
            castAndTouch(pc, item, L1SkillId.SHAPE_MAAN);
            return Result.HANDLED;

        case ITEM_LIFE_MAAN:
            castAndTouch(pc, item, L1SkillId.LIFE_MAAN);
            return Result.HANDLED;

        case ITEM_RAID_1:
            return useRaidItem(
                    pc,
                    item,
                    53,
                    32735,
                    32842,
                    (short) 481,
                    778877,
                    32736,
                    32819,
                    (short) 481);

        case ITEM_RAID_2:
            return useRaidItem(
                    pc,
                    item,
                    15403,
                    32544,
                    32863,
                    (short) 12257,
                    778888,
                    32544,
                    32863,
                    (short) 12257);

        case ITEM_RAID_3:
            return useRaidItem(
                    pc,
                    item,
                    12862,
                    32744,
                    32808,
                    (short) 41,
                    778899,
                    32736,
                    32799,
                    (short) 41);

        default:
            return Result.NOT_HANDLED;
        }
    }

    /**
     * An L1 resolvent consumes the selected target and the selected solvent in
     * the same CacheD receipt as its crystal reward. All use conditions were
     * admitted by CCreature; this routine owns the item-specific settlement.
     */
    public static void useResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent) {
        CItemUseResolvent.useResolvent(pc, target, solvent);
    }



    /** Compatibility entry for the legacy explicit-count dissolution call. */
    public static void useResolventCount(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent, int base) {
        CItemUseResolvent.useResolventCount(pc, target, solvent, base);
    }

    /** Dragon-ball resolvent keeps its original fixed crystal reward. */
    public static void useDragonResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent) {
        CItemUseResolvent.useDragonResolvent(pc, target, solvent);
    }




    /** Convert exactly one physical voucher into its configured adena amount.
     * The two inventory mutations share a single CacheD receipt; only the
     * original CIO owner presents success and ordinary item delay after COMMIT.
     */
    private static Result useAdenaVoucher(L1PcInstance pc, L1ItemInstance item) {
        return CItemUseVoucher.useAdenaVoucher(pc, item);
    }

    /** A one-use effect must not be applied before the item debit is durable.
     * The caller's ordinary admission/cooldown checks have already succeeded.
     * Returning RETURN stops the dispatcher from starting item delay early.
     */
    private static Result useLifeMaanOnce(L1PcInstance pc, L1ItemInstance item) {
        return CItemUseMaanRaid.useLifeMaanOnce(pc, item);
    }



    private static void castAndTouch(
            L1PcInstance pc,
            L1ItemInstance item,
            int skillId) {
        CItemUseMaanRaid.castAndTouch(pc, item, skillId);
    }



    /** Raid creation is a post-commit world effect, never a preparation action.
     * CCreature has already admitted this item; the existing CacheD receipt
     * owns its debit and the CIO owner runs effects after inventory replay.
     */
    private static Result useRaidItem(
            L1PcInstance pc,
            L1ItemInstance item,
            int requiredMapId,
            int teleportX,
            int teleportY,
            short teleportMapId,
            int portalNpcId,
            int bossX,
            int bossY,
            short bossMapId) {
        return CItemUseMaanRaid.useRaidItem(pc, item, requiredMapId, teleportX, teleportY, teleportMapId, portalNpcId, bossX, bossY, bossMapId);
    }


    /**
     * Doll potential is one durable operation: consume scrolls and replace the
     * selected doll's item_level in the same CacheD receipt. All broadcasts and
     * owner-visible effects happen only after the inventory replay.
     */
    public static void useDollPotential(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance doll) {
        CItemUseDoll.useDollPotential(pc, scroll, doll);
    }



    /** Attendance cash box and 4 money boxes: debit and reward in one receipt. */
    public static void useRandomMoneyBox(L1PcInstance pc, L1ItemInstance box) {
        CItemUseRewards.useRandomMoneyBox(pc, box);
    }



    /** Consume the level EXP potion and persist its EXP reward in the same
     * CacheD receipt. The legacy below-51 branch awarded EXP without consuming
     * its item; a crash must not make the committed item debit lose the EXP.
     */
    public static void useLevelExpPotion(L1PcInstance pc, L1ItemInstance item) {
        CItemUseRewards.useLevelExpPotion(pc, item);
    }

    /** Atomic one-item consumption + character EXP update for fixed-reward EXP potions. */
    public static void useFixedExpPotion(L1PcInstance pc, L1ItemInstance item, int expReward) {
        CItemUseRewards.useFixedExpPotion(pc, item, expReward);
    }

    /** Persist the whole 14-item class reward bundle with its single input voucher. */
    public static void useClassGiftBundle(L1PcInstance pc, L1ItemInstance voucher) {
        CItemUseRewards.useClassGiftBundle(pc, voucher);
    }

    /** Rune de-powering consumes exactly the selected rune and catalyst atomically. */
    public static void useRunePowerRemover(L1PcInstance pc, L1ItemInstance remover,
            L1ItemInstance rune) {
        CItemUseCraft.useRunePowerRemover(pc, remover, rune);
    }

    /** At most one guardian redemption; durable reward and flute debit share one receipt. */
    public static void useGuardianFlute(L1PcInstance pc, L1ItemInstance flute) {
        CItemUseRewards.useGuardianFlute(pc, flute);
    }

    /** Preserve the exact success odds and failure consumption of legacy targeted purification. */
    public static void useTargetedPurification(L1PcInstance pc, L1ItemInstance reagent,
            L1ItemInstance target) {
        CItemUseCraft.useTargetedPurification(pc, reagent, target);
    }

    /** One persisted exchange for the selected weapon and its transformation scroll. */
    public static void useWeaponTransformation(L1PcInstance pc, L1ItemInstance scroll,
            L1ItemInstance weapon) {
        CItemUseCraft.useWeaponTransformation(pc, scroll, weapon);
    }

    /** A charm is consumed only if its adena award is persisted. */
    public static void useBurnedCharm(L1PcInstance pc, L1ItemInstance charm) {
        CItemUseRewards.useBurnedCharm(pc, charm);
    }

    /** Commit all 100 fragments and the crafted life eye in the same receipt. */
    public static void craftLifeEye(L1PcInstance pc, L1ItemInstance fragment) {
        CItemUseCraft.craftLifeEye(pc, fragment);
    }

    /** The selected enchant and item grant are persisted with the consumed box. */
    public static void useRandomEnchantBox(L1PcInstance pc, L1ItemInstance box) {
        CItemUseRewards.useRandomEnchantBox(pc, box);
    }

    /** Commit the leader's one-use clan buff scroll before affecting any member. */
    public static void useClanBuff(L1PcInstance pc, L1ItemInstance item) {
        CItemUseClan.useClanBuff(pc, item);
    }



    static Timestamp nowTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }



    public static void spawn(
            int x,
            int y,
            short mapId,
            int heading,
            int npcId,
            int randomRange,
            int roomnumber) {
        CItemUseMaanRaid.spawn(x, y, mapId, heading, npcId, randomRange, roomnumber);
    }

    public static void useSupplemental(CIOClient client, L1PcInstance pc,
            L1ItemInstance item, int targetId) {
        CItemUseSupplemental.use(client, pc, item, targetId);
    }


}
