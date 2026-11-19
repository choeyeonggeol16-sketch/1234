package l1j.server.server.model.character.equip;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.MJPassiveSkill.MJPassiveID;

public final class EquipRules {

    private EquipRules() {}

    // =========================
    // ARMOR RULES
    // =========================
    public static EquipResult canEquipArmor(L1PcInstance pc, L1PcInventory inv, L1ItemInstance armor) {
        if (pc == null || inv == null || armor == null || armor.getItem() == null) return EquipResult.fail(null);

        final int type = armor.getItem().getType();

        // 900111 특수
        if (armor.getItemId() == 900111 && inv.checkEquippedAtOnce(new int[]{900111, 20288})) {
            return EquipResult.fail(EquipFail.text("이미 착용중인 아이템입니다."));
        }

        // 변신 장착 제한
        if (!L1PolyMorph.isEquipableArmor(pc.getCurrentSpriteId(), type)) {
            return EquipResult.fail(null);
        }

        // 슬롯 여유
        final int ringCount = (type == 9)  ? inv.getTypeEquipped(2, 9)  : -1;
        final int earCount  = (type == 12) ? inv.getTypeEquipped(2, 12) : -1;

        if (type == 9) {
            if (ringCount >= 4) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
        } else if (type == 12) {
            if (earCount >= 2) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
        } else {
            if (inv.getTypeEquipped(2, type) >= 1) return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);
        }

        // 링(9) 슬롯 확장 퀘스트/상한
        if (type == 9) {
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT76) && ringCount >= 2) {
                return EquipResult.fail(EquipFail.system("스냅퍼에게 76레벨 슬롯 개방 후 착용 가능"));
            }
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT81) && ringCount >= 3) {
                return EquipResult.fail(EquipFail.system("스냅퍼에게 81레벨 슬롯 개방 후 착용 가능"));
            }
            if (inv.getTypeAndItemIdEquipped(2, 9, armor.getItem().getItemId()) == 2) {
                return EquipResult.fail(EquipFail.MSG_DUP_ITEM_3278);
            }
            if (inv.getTypeAndGradeEquipped(2, 9, armor.getItem().getGrade()) == 2) {
                return EquipResult.fail(EquipFail.MSG_DUP_GRADE_3279);
            }
        }

        // 귀걸이(12) 슬롯 확장/중복
        if (type == 12) {
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT59) && earCount >= 1) {
                return EquipResult.fail(EquipFail.system("스냅퍼에게 59레벨 슬롯 개방 후 착용 가능"));
            }
            if (inv.getTypeAndItemIdEquipped(2, 12, armor.getItem().getItemId()) >= 1) {
                return EquipResult.fail(EquipFail.MSG_DUP_ITEM_3278);
            }
            if (inv.getNameEquipped(2, 12, armor.getName()) >= 1) {
                return EquipResult.fail(EquipFail.MSG_DUP_ITEM_3278);
            }
        }

        // 견갑/휘장
        if (type == 29) {
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT_SHOULD)) return EquipResult.fail(EquipFail.MSG_SHOULDER_5077);
            if (inv.getTypeEquipped(2, 29) >= 1) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
        }
        if (type == 30) {
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT_BADGE)) return EquipResult.fail(EquipFail.MSG_BADGE_5078);
            if (inv.getTypeEquipped(2, 30) >= 1) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
        }

        // 방패(7) <-> 가더(13) 상호배타
        if ((type == 7  && inv.getTypeEquipped(2, 13) >= 1) ||
            (type == 13 && inv.getTypeEquipped(2, 7)  >= 1)) {
            return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);
        }

        // 쌍수 상태에서 방패/가더 금지
        if ((type == 7 || type == 13) && pc.getEquipSlot().getWeaponCount() == 2) {
            return EquipResult.fail(EquipFail.MSG_LEVEL_OVER_129);
        }

        // 방패 착용 시 양손무기 금지(UseType 13 예외 유지)
        if (type == 7 && pc.getWeapon() != null) {
            if (pc.getWeapon().getItem().isTwohandedWeapon() && armor.getItem().getUseType() != 13) {
                return EquipResult.fail(EquipFail.MSG_LEVEL_OVER_129);
            }
        }

        return EquipResult.OK;
    }

    // =========================
    // WEAPON RULES
    // =========================
    public static EquipResult canEquipWeaponBasic(L1PcInstance pc, L1PcInventory inv, L1ItemInstance weapon) {
        if (pc == null || inv == null || weapon == null || weapon.getItem() == null) return EquipResult.fail(null);

        if (!L1PolyMorph.isEquipableWeapon(pc.getCurrentSpriteId(), weapon.getItem().getType())) {
            return EquipResult.fail(null);
        }

        // 양손 + 방패 장착중 불가
        if (weapon.getItem().isTwohandedWeapon() && inv.getTypeEquipped(2, 7) >= 1) {
            return EquipResult.fail(EquipFail.MSG_SHIELD_BLOCK_128);
        }

        return EquipResult.OK;
    }

    // 슬레이어 + 한손도끼 조건일 때 “2번째 무기” 가능 여부
    public static EquipResult canEquipAsSecondWeaponSlayer(L1PcInstance pc, L1PcInventory inv, L1ItemInstance current, L1ItemInstance next) {
        if (pc == null || inv == null || current == null || next == null) return EquipResult.fail(null);

        boolean applicable =
                pc.isPassive(MJPassiveID.SLAYER.toInt())
                && next.getItem().getType1() == 11
                && current.getItem().getType1() == 11
                && next.getItem().getType() == 6
                && current.getItem().getType() == 6;

        if (!applicable) return EquipResult.fail(null); // "해당 없음" 

        if (inv.getTypeEquipped(2, 7) >= 1)  return EquipResult.fail(EquipFail.MSG_SHIELD_BLOCK_128);
        if (inv.getTypeEquipped(2, 13) >= 1) return EquipResult.fail(EquipFail.MSG_SHIELD_BLOCK_128);
        if (pc.getEquipSlot().getWeaponCount() >= 2) return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);

        return EquipResult.OK; // 두번째 무기로 OK
    }
}
