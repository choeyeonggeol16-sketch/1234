package l1j.server.server.model.rank.buff;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.rank.loader.RankLoadManager;
import LIN.SC_SPELL_BUFF_NOTI;
import LIN.SC_SPELL_BUFF_NOTI.eDurationShowType;
import LIN.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;

/**
 * 랭킹 버프 Lv1~11 통합 처리기
 */
public final class RankBuff {

    private static final int IDX_STR = 0, IDX_DEX = 1, IDX_INT = 2;

    private RankBuff() {}

    // ─────────────────────────────────────────────
    // 공개 API
    // ─────────────────────────────────────────────

    public static void apply(L1PcInstance pc, int level, int classId, int classGfx){
        if(level < 1 || level > 11) return;

        int skill  = skillId(level, classId, classGfx);
        int branch = branchIdx(level, classId, classGfx);

        pc.setSkillEffect(skill, -1);
        sendIconOn(pc, level, skill, tooltipId(level, branch));
        applyStat(pc, level, branchClass(level, classId, classGfx), +1);
        if(level == 11)
            giveProtectionItem(pc);
        if(level >= 5)
            SC_OwnCharStatus.send(pc);
    }

    public static void remove(L1PcInstance pc, int level, int classId, int classGfx){
        if(level < 1 || level > 11) return;

        int skill = skillId(level, classId, classGfx);

        pc.killSkillEffectTimer(skill);
        sendIconOff(pc, skill);
        applyStat(pc, level, branchClass(level, classId, classGfx), -1);
        if(level == 11)
            pc.getInventory().findAndRemoveItemId(RankLoadManager.MRK_TOPPROTECTION_ID);
        if(level >= 5)
            SC_OwnCharStatus.send(pc);
    }

    // ─────────────────────────────────────────────
    // 아이콘 전송 — 우림은 mutable 패킷이라 매번 새로 생성
    // ─────────────────────────────────────────────

    private static void sendIconOn(L1PcInstance pc, int level, int skill, int tooltip){
        LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
        int notiWire_spellId = 0;
        int notiWire_duration = 0;
        LIN.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
        int notiWire_onIconId = 0;
        int notiWire_offIconId = 0;
        int notiWire_iconPriority = 0;
        int notiWire_tooltipStrId = 0;
        int notiWire_newStrId = 0;
        int notiWire_endStrId = 0;
        boolean notiWire_isGood = false;
        int notiWire_bit = 0;


        { notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.NEW; }
        { notiWire_bit |= 2; notiWire_spellId = skill; }
        { notiWire_bit |= 4; notiWire_duration = 1; }
        { notiWire_bit |= 8; notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_UNLIMIT; }
        int icon = iconId(level);
        { notiWire_bit |= 16; notiWire_onIconId = icon; }
        { notiWire_bit |= 32; notiWire_offIconId = icon; }
        { notiWire_bit |= 64; notiWire_iconPriority = 3; }
        { notiWire_bit |= 128; notiWire_tooltipStrId = tooltip; }
        { notiWire_bit |= 256; notiWire_newStrId = 0; }
        { notiWire_bit |= 512; notiWire_endStrId = 0; }
        { notiWire_bit |= 1024; notiWire_isGood = true; }
        LIN.SC_SPELL_BUFF_NOTI.sendFields(
                pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
                notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId,
                notiWire_isGood, 0, 0, 0, notiWire_bit);
    }

    private static void sendIconOff(L1PcInstance pc, int skill){
        LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
        int notiWire_spellId = 0;
        int notiWire_offIconId = 0;
        int notiWire_endStrId = 0;
        int notiWire_bit = 0;


        { notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.END; }
        { notiWire_bit |= 2; notiWire_spellId = skill; }
        { notiWire_bit |= 32; notiWire_offIconId = 0; }
        { notiWire_bit |= 512; notiWire_endStrId = 0; }
        LIN.SC_SPELL_BUFF_NOTI.sendFields(
                pc, notiWire_notiType, notiWire_spellId, 0, null, 0, notiWire_offIconId, 0, 0, 0, notiWire_endStrId, false,
                0, 0, 0, notiWire_bit);
    }

    // ─────────────────────────────────────────────
    // 스킬ID / 분기
    // ─────────────────────────────────────────────

    private static int skillIdSimple(int level){
        switch(level){
        case 1: return L1SkillId.RANK_BUFF_1;
        case 2: return L1SkillId.RANK_BUFF_2;
        case 3: return L1SkillId.RANK_BUFF_3;
        case 4: return L1SkillId.RANK_BUFF_4;
        case 5: return L1SkillId.RANK_BUFF_5;
        case 6: return L1SkillId.RANK_BUFF_6;
        case 7: return L1SkillId.RANK_BUFF_7;
        case 8: return L1SkillId.RANK_BUFF_8;
        case 9: return L1SkillId.RANK_BUFF_9;
        default: return 0;
        }
    }

    private static int skillId(int level, int classId, int classGfx){
        switch(level){
        case 10: return rank10Skill(classId);
        case 11: return rank11Skill(classGfx);
        default: return skillIdSimple(level);
        }
    }

    private static int rank10Skill(int classId){
        switch(classId){
        case 2:         return L1SkillId.RANK_BUFF_10_DEX;
        case 3: case 6: return L1SkillId.RANK_BUFF_10_INT;
        default:        return L1SkillId.RANK_BUFF_10_STR;
        }
    }

    private static int rank11Skill(int classGfx){
        switch(classGfx){
        case 2:         return L1SkillId.RANK_BUFF_11_DEX;
        case 3: case 6: return L1SkillId.RANK_BUFF_11_INT;
        default:        return L1SkillId.RANK_BUFF_11_STR;
        }
    }

    private static int branchIdx(int level, int classId, int classGfx){
        if(level < 10) return 0;
        int c = (level == 11) ? classGfx : classId;
        switch(c){
        case 2:         return IDX_DEX;
        case 3: case 6: return IDX_INT;
        default:        return IDX_STR;
        }
    }

    private static int branchClass(int level, int classId, int classGfx){
        return (level == 11) ? classGfx : classId;
    }

    // ─────────────────────────────────────────────
    // 스탯 — MJ 원본 그대로
    // ─────────────────────────────────────────────

    private static void applyStat(L1PcInstance pc, int level, int classId, int sign){
        switch(level){
        case 1:
            break;
        case 2:
            pc.addMaxHp(50 * sign);
            break;
        case 3:
            pc.addMaxHp(100 * sign);
            break;
        case 4:
            pc.addMaxHp(200 * sign);
            break;
        case 5:
            pc.addMaxHp(200 * sign);
            pc.getAC().addAc(-1 * sign);
            break;
        case 6:
            pc.addMaxHp(200 * sign);
            pc.getAC().addAc(-2 * sign);
            pc.getResistance().addcalcPcDefense(sign);
            break;
        case 7:
            pc.addMaxHp(200 * sign);
            pc.getAC().addAc(-3 * sign);
            pc.getResistance().addcalcPcDefense(sign);
            break;
        case 8:
            pc.addMaxHp(200 * sign);
            pc.getAC().addAc(-3 * sign);
            pc.getResistance().addcalcPcDefense(2 * sign);
            break;
        case 9:
            pc.addMaxHp(200 * sign);
            pc.getAC().addAc(-3 * sign);
            pc.getResistance().addcalcPcDefense(2 * sign);
            pc.addDmgupByArmor(sign);
            pc.addBowDmgupByArmor(sign);
            break;
        case 10:
        case 11:
            switch(classId){
            case 2:         pc.getAbility().addAddedDex(sign); break;
            case 3: case 6: pc.getAbility().addAddedInt(sign); break;
            default:        pc.getAbility().addAddedStr(sign); break;
            }
            pc.addMaxHp(200 * sign);
            pc.getAC().addAc(-3 * sign);
            pc.getResistance().addcalcPcDefense(2 * sign);
            pc.addDmgupByArmor(sign);
            pc.addBowDmgupByArmor(sign);
            break;
        }
    }

    // ─────────────────────────────────────────────
    // 아이콘/툴팁 ID
    // ─────────────────────────────────────────────

    private static int iconId(int level){
        switch(level){
        case 1: case 2: case 3: case 4: case 5: return 8532;
        case 6: case 7: return 7093;
        case 8: case 9: return 7094;
        case 10: return 7095;
        case 11: return 7096;
        default: return 0;
        }
    }

    /** branch 인덱스 기반 툴팁 (Lv10: 5145~5147 / Lv11: 5148~5150) */
    private static int tooltipId(int level, int branch){
        switch(level){
        case 10:
            switch(branch){
            case IDX_DEX: return 5146;
            case IDX_INT: return 5147;
            default:      return 5145;
            }
        case 11:
            switch(branch){
            case IDX_DEX: return 5149;
            case IDX_INT: return 5150;
            default:      return 5148;
            }
        default:
            return 5135 + level;   // Lv1=5136 ... Lv9=5144
        }
    }

    private static void giveProtectionItem(L1PcInstance pc){
        L1ItemInstance item = ItemTable.getInstance().createItem(RankLoadManager.MRK_TOPPROTECTION_ID);
        if(pc.getInventory().checkAddItem(item, 1) == L1Inventory.OK
                && !pc.getInventory().checkItem(RankLoadManager.MRK_TOPPROTECTION_ID))
            pc.getInventory().storeItem(item);
    }
}