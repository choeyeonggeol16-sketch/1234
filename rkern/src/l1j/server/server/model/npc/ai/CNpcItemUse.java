package l1j.server.server.model.npc.ai;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.model.item.L1ItemId;

import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
/**
 * NPC(Pet/Summon/Support 등)의 포션 자동 사용 정책.
 */
public final class CNpcItemUse {

    private CNpcItemUse() {}

    /** 포션 사용 가능 여부: 침묵(스킬 71) 상태 차단 */
    private static boolean IsSilenced(L1NpcInstance npc) {
        return npc.hasSkillEffect(L1SkillId.DECAY_POTION);
    }

    /** 회복 포션 사용 (반드시 소비 성공 후 호출) */
    private static void HealPotion(L1NpcInstance npc, int healHp, int effectId) {
        int heal = healHp;
        if (npc.hasSkillEffect(L1SkillId.POLLUTE_WATER)) {
            heal /= 2;
        }
        SC_SkillSound.broadcast(npc, npc.getId(), effectId);
        npc.applyNpcHpDelta(heal);
    }

    /** 가속 포션 사용 (반드시 소비 성공 후 호출) */
    public static void HastePotion(L1NpcInstance npc, int time) {
        SC_SkillHaste.broadcast(npc, npc.getId(), 1, time);
        SC_SkillSound.broadcast(npc, npc.getId(), 191);
        npc.setMoveSpeed(1);
        npc.setSkillEffect(L1SkillId.STATUS_HASTE, time * 1000);
    }

    /**
     * NPC가 확률적으로 포션을 사용한다.
     */
    public static boolean UseItem(L1NpcInstance npc, int type, int chance) {
        if (npc == null || IsSilenced(npc)) {
            return false;
        }
        if (chance <= 0) {
            return false;
        }
        if (chance < 100 && ThreadLocalRandom.current().nextInt(100) >= chance) {
            return false;
        }
        if (type == USEITEM_HEAL) {
            return UseHealPotion(npc);
        }
        if (type == USEITEM_HASTE) {
            return UseHastePotion(npc);
        }
        return false;
    }

    /** 우선순위: GHE > EHE > HE — 보유분부터 순서대로 소비 */
    private static boolean UseHealPotion(L1NpcInstance npc) {
        if (npc.getInventory().DeleteItemInInventory(L1ItemId.POTION_OF_GREATER_HEALING, 1)) {
            HealPotion(npc, 75, 197);
            return true;
        }
        if (npc.getInventory().DeleteItemInInventory(L1ItemId.POTION_OF_EXTRA_HEALING, 1)) {
            HealPotion(npc, 45, 194);
            return true;
        }
        if (npc.getInventory().DeleteItemInInventory(L1ItemId.POTION_OF_HEALING, 1)) {
            HealPotion(npc, 15, 189);
            return true;
        }
        return false;
    }

    /** 우선순위: 고급가속 > 가속 > 약한가속 > 가속물약 */
    private static boolean UseHastePotion(L1NpcInstance npc) {
        // 이미 가속 상태면 중복 사용 안 함 
        if (npc.hasSkillEffect(1001)) {
            return false;
        }
        if (npc.getInventory().DeleteItemInInventory(L1ItemId.B_POTION_OF_GREATER_HASTE_SELF, 1)
                || npc.getInventory().DeleteItemInInventory(L1ItemId.POTION_OF_GREATER_HASTE_SELF1, 1)) {
            HastePotion(npc, 2100);
            return true;
        }
        if (npc.getInventory().DeleteItemInInventory(L1ItemId.POTION_OF_GREATER_HASTE_SELF, 1)) {
            HastePotion(npc, 1800);
            return true;
        }
        if (npc.getInventory().DeleteItemInInventory(L1ItemId.B_POTION_OF_HASTE_SELF, 1)) {
            HastePotion(npc, 350);
            return true;
        }
        if (npc.getInventory().DeleteItemInInventory(L1ItemId.POTION_OF_HASTE_SELF, 1)
                || npc.getInventory().DeleteItemInInventory(7006, 1)) {
            HastePotion(npc, 300);
            return true;
        }
        return false;
    }
    public static final int USEITEM_HEAL = 0;
    public static final int USEITEM_HASTE = 1;


    /** Dispatch after the existing NPC item-acquisition callback. */
    public static void OnGetItem(final L1NpcInstance npc, final int itemId) {
        if (itemId == L1ItemId.POTION_OF_GREATER_HEALING
                || itemId == L1ItemId.POTION_OF_EXTRA_HEALING
                || itemId == L1ItemId.POTION_OF_HEALING
                || itemId == 4100021) {
            if (npc.getCurrentHp() != npc.getMaxHp()) {
                npc.useItem(USEITEM_HEAL, 100);
            }
        } else if (IsHastePotion(itemId)) {
            npc.useItem(USEITEM_HASTE, 100);
        }
    }
    public static boolean IsHastePotion(final int itemId) {
        return itemId == L1ItemId.B_POTION_OF_GREATER_HASTE_SELF
                || itemId == L1ItemId.POTION_OF_GREATER_HASTE_SELF
                || itemId == L1ItemId.POTION_OF_GREATER_HASTE_SELF1
                || itemId == L1ItemId.B_POTION_OF_HASTE_SELF
                || itemId == L1ItemId.POTION_OF_HASTE_SELF
                || itemId == 7006;
    }
}
