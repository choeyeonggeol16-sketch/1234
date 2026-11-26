package l1j.server.server.model.inventory;

import java.util.List;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.templates.L1Item;

public final class EquipRules {

    private EquipRules() {}


    /** Shared class eligibility gate for item use and preset equipment. */
    public static boolean canUseByClass(L1PcInstance pc, L1Item item) {
        if (pc == null || item == null) return false;
        if (pc.isGm()) return true;
        return (pc.isCrown() && item.isUseRoyal())
                || (pc.isKnight() && item.isUseKnight())
                || (pc.isElf() && item.isUseElf())
                || (pc.isWizard() && item.isUseMage())
                || (pc.isDarkelf() && item.isUseDarkelf())
                || (pc.isDragonknight() && item.isUseDragonKnight())
                || (pc.isBlackwizard() && item.isUseBlackwizard())
                || (pc.isWarrior() && item.isUse전사());
    }

    // =========================
    // ARMOR RULES
    // =========================
    public static EquipResult canEquipArmor(L1PcInstance pc, CUserInventory inv, L1ItemInstance armor) {
        if (pc == null || inv == null || armor == null || armor.getItem() == null)
            return EquipResult.fail(null);
        final CUserEquipment slot = pc.getEquipment();
        return checkArmor(pc, inv, armor, slot.getArmors(), null, 0,
                slot.getWeaponCount(), slot.getWeapon());
    }

    /** Preflight against the full future equipment set, not the current set.
     * Called before a preset unequips a single piece of gear.
     */
    public static boolean canEquipPreset(L1PcInstance pc, CUserInventory inv,
            L1ItemInstance[] planned, int count) {
        if (pc == null || inv == null || planned == null || count < 0 || count > planned.length)
            return false;
        int weapons = 0;
        L1ItemInstance primary = null, secondary = null;
        boolean shield = false, guarder = false;
        final int sprite = pc.getCurrentSpriteId();
        for (int i = 0; i < count; i++) {
            final L1ItemInstance item = planned[i];
            if (item == null || item.getItem() == null) return false;
            if (!canUseByClass(pc, item.getItem())) return false;
            final int level = pc.getLevel();
            final int minLevel = item.getItem().getMinLevel();
            final int maxLevel = item.getItem().getMaxLevel();
            if ((minLevel > 0 && level < minLevel)
                    || (maxLevel > 0 && level > maxLevel)) return false;
            final int type = item.getItem().getType2();
            if (type == 1) {
                if (!L1PolyMorph.isEquipableWeapon(sprite, item.getItem().getType())
                        || ++weapons > 2) return false;
                if (primary == null) primary = item;
                else secondary = item;
            } else if (type == 2) {
                if (!L1PolyMorph.isEquipableArmor(sprite, item.getItem().getType())) return false;
                if (item.getItem().getType() == 7) shield = true;
                if (item.getItem().getType() == 13) guarder = true;
            } else {
                return false;
            }
        }
        if (weapons == 2 && (shield || guarder
                || !pc.getEquipment().canUseSecondWeaponBySlayer(primary, secondary)))
            return false;
        if (primary != null && primary.getItem().isTwohandedWeapon() && shield)
            return false;
        for (int i = 0; i < count; i++) {
            if (planned[i].getItem().getType2() != 2) continue;
            if (!checkArmor(pc, inv, planned[i], null, planned, count,
                    weapons, primary).ok) return false;
        }
        return true;
    }

    private static EquipResult checkArmor(L1PcInstance pc, CUserInventory inv,
            L1ItemInstance armor, List<L1ItemInstance> worn,
            L1ItemInstance[] planned, int plannedCount,
            int weaponCount, L1ItemInstance primaryWeapon) {
        final int type = armor.getItem().getType();
        final EquipmentTraits traits = EquipmentTraits.forItem(armor.getItemId());
        if (traits.has(EquipmentTraits.Flag.EXCLUSIVE_ABILITY)) {
            final int ability = traits.ability();
            if (planned == null) {
                if (EquipmentTraits.isAbilityEquipped(inv, ability))
                    return EquipResult.fail(EquipFail.text("이미 착용중인 아이템입니다."));
            } else {
                for (int i = 0; i < plannedCount; i++) {
                    final L1ItemInstance other = planned[i];
                    if (other != armor && EquipmentTraits.forItem(other.getItemId()).ability() == ability)
                        return EquipResult.fail(EquipFail.text("이미 착용중인 아이템입니다."));
                }
            }
        }
        if (!L1PolyMorph.isEquipableArmor(pc.getCurrentSpriteId(), type))
            return EquipResult.fail(null);

        final String earringName = type == 12 ? armor.getName().replace("축복받은 ", "") : null;
        int sameType = 0, sameId = 0, sameGrade = 0;
        boolean sameEarringName = false, shield = false, guarder = false;
        final int limit = planned != null ? plannedCount : worn.size();
        for (int i = 0; i < limit; i++) {
            final L1ItemInstance other = planned != null ? planned[i] : worn.get(i);
            if (other == null || other == armor || other.getItem() == null
                    || (planned == null && !other.isEquipped())) continue;
            if (other.getItem().getType2() != 2) continue;
            final int otherType = other.getItem().getType();
            if (otherType == 7) shield = true;
            if (otherType == 13) guarder = true;
            if (otherType != type) continue;
            sameType++;
            if (other.getItemId() == armor.getItemId()) sameId++;
            if (type == 9 && other.getItem().getGrade() == armor.getItem().getGrade()) sameGrade++;
            if (earringName != null && earringName.equals(other.getName().replace("축복받은 ", "")))
                sameEarringName = true;
        }

        if (type == 9) {
            if (sameType >= 4) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
            if (!pc.getQuest().isEnd(CQuestSystem.QUEST_SLOT76) && sameType >= 2)
                return EquipResult.fail(EquipFail.system("스냅퍼에게 76레벨 슬롯 개방 후 착용 가능"));
            if (!pc.getQuest().isEnd(CQuestSystem.QUEST_SLOT81) && sameType >= 3)
                return EquipResult.fail(EquipFail.system("스냅퍼에게 81레벨 슬롯 개방 후 착용 가능"));
            if (sameId >= 2) return EquipResult.fail(EquipFail.MSG_DUP_ITEM_3278);
            if (sameGrade >= 2) return EquipResult.fail(EquipFail.MSG_DUP_GRADE_3279);
        } else if (type == 12) {
            if (sameType >= 2) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
            if (!pc.getQuest().isEnd(CQuestSystem.QUEST_SLOT59) && sameType >= 1)
                return EquipResult.fail(EquipFail.system("스냅퍼에게 59레벨 슬롯 개방 후 착용 가능"));
            if (sameId != 0 || sameEarringName)
                return EquipResult.fail(EquipFail.MSG_DUP_ITEM_3278);
        } else if (sameType != 0) {
            return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);
        }
        if (type == 29 && !pc.getQuest().isEnd(CQuestSystem.QUEST_SLOT_SHOULD))
            return EquipResult.fail(EquipFail.MSG_SHOULDER_5077);
        if (type == 30 && !pc.getQuest().isEnd(CQuestSystem.QUEST_SLOT_BADGE))
            return EquipResult.fail(EquipFail.MSG_BADGE_5078);
        if ((type == 7 && guarder) || (type == 13 && shield))
            return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);
        if ((type == 7 || type == 13) && weaponCount == 2)
            return EquipResult.fail(EquipFail.MSG_LEVEL_OVER_129);
        if (type == 7 && primaryWeapon != null && primaryWeapon.getItem().isTwohandedWeapon()
                && armor.getItem().getUseType() != 13)
            return EquipResult.fail(EquipFail.MSG_LEVEL_OVER_129);
        return EquipResult.OK;
    }

    // =========================
    // WEAPON RULES
    // =========================
    public static EquipResult canEquipWeaponBasic(L1PcInstance pc, CUserInventory inv, L1ItemInstance weapon) {
        if (pc == null || inv == null || weapon == null || weapon.getItem() == null) return EquipResult.fail(null);

        if (!L1PolyMorph.isEquipableWeapon(pc.getCurrentSpriteId(), weapon.getItem().getType())) {
            return EquipResult.fail(null);
        }

        // 양손 + 방패 장착중 불가
        if (weapon.getItem().isTwohandedWeapon() && pc.getEquipment().hasAppliedArmor(7)) {
            return EquipResult.fail(EquipFail.MSG_SHIELD_BLOCK_128);
        }

        return EquipResult.OK;
    }

    // 슬레이어 + 한손도끼 조건일 때 “2번째 무기” 가능 여부
    public static EquipResult canEquipAsSecondWeaponSlayer(L1PcInstance pc, CUserInventory inv, L1ItemInstance current, L1ItemInstance next) {
        if (pc == null || inv == null || current == null || next == null) return EquipResult.fail(null);

        final CUserEquipment slot = pc.getEquipment();
        if (!slot.canUseSecondWeaponBySlayer(current, next)) return EquipResult.fail(null);
        if (slot.hasAppliedArmor(7) || slot.hasAppliedArmor(13)) return EquipResult.fail(EquipFail.MSG_SHIELD_BLOCK_128);
        if (slot.getWeaponCount() >= 2) return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);

        return EquipResult.OK; // 두번째 무기로 OK
    }
}
