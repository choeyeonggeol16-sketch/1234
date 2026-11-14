package l1j.server.server.model.character.login.skill;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ElfIcon;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;


import l1j.server.server.utils.eKind;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.Packet.SC_RestExpInfo;
import LIN.SC_SPELL_BUFF_NOTI;
import LIN.SC_SPELL_BUFF_NOTI.eDurationShowType;
import LIN.SC_SPELL_BUFF_NOTI.eNotiType;

import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_Liquor;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
/**
 * 스킬 로그인 복구 핸들러 등록부
 */
public final class BuffRestoreRegistryInit {

    private static boolean _loaded = false;

    public static synchronized void init() {
        if (_loaded) return;
        _loaded = true;
        registerAll();
     //   System.out.println(String.format("[BuffRegistry] 로그인 복구 핸들러 등록 완료: %d개 (원본 121개)", BuffRestoreRegistry.size()));
    }

    private static void registerAll() {
        // ================= 1. USER_WANTED 계열 =================
        BuffRestoreRegistry.register(L1SkillId.USER_WANTED1,
                (pc, sid, polyId, rt, icon) -> pc.doWanted1(true));
        BuffRestoreRegistry.register(L1SkillId.USER_WANTED,
                (pc, sid, polyId, rt, icon) -> pc.doWanted(true));

        // ================= 2. God_buff =================
        BuffRestoreRegistry.register(L1SkillId.God_buff, (pc, sid, polyId, rt, icon) -> {
            pc.getAC().addAc(-2);
            pc.getResistance().addSpecialResistance(eKind.SPIRIT, 10);
            pc.addMaxHp(20);
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
            pc.addMaxMp(13);
            SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
            SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 4914, rt);
        });

        // ================= 3. SHAPE_CHANGE =================
        BuffRestoreRegistry.register(L1SkillId.SHAPE_CHANGE,
                (pc, sid, polyId, rt, icon) ->
            L1PolyMorph.doPoly(pc, polyId, rt, L1PolyMorph.MORPH_BY_ITEMMAGIC, false));

        // ================= 4. STATUS_BRAVE / ELFBRAVE / HASTE =================
        BuffRestoreRegistry.register(L1SkillId.STATUS_BRAVE, (pc, sid, polyId, rt, icon) -> {
            SC_SkillBrave.send(pc, pc.getId(), 1, rt);
            SC_SkillBrave.broadcast(pc, pc.getId(), 1, 0);
            pc.setBraveSpeed(1);
        });
        BuffRestoreRegistry.register(L1SkillId.STATUS_ELFBRAVE, (pc, sid, polyId, rt, icon) -> {
            SC_SkillBrave.send(pc, pc.getId(), 3, rt);
            SC_SkillBrave.broadcast(pc, pc.getId(), 3, 0);
            pc.setBraveSpeed(1);
        });
        BuffRestoreRegistry.register(L1SkillId.STATUS_HASTE, (pc, sid, polyId, rt, icon) -> {
            SC_SkillHaste.send(pc, pc.getId(), 1, rt);
            SC_SkillHaste.broadcast(pc, pc.getId(), 1, 0);
            pc.setMoveSpeed(1);
        });

        // ================= 5. STATUS_BLUE_POTION(공유) / CHAT_PROHIBITED =================
        BuffRestoreRegistry.register(L1SkillId.STATUS_BLUE_POTION,
                (pc, sid, polyId, rt, icon) -> SC_SkillIconGFX.send(pc, 34, rt, true));
        BuffRestoreRegistry.register(L1SkillId.STATUS_BLUE_POTION2,
                (pc, sid, polyId, rt, icon) -> SC_SkillIconGFX.send(pc, 34, rt, true));
        BuffRestoreRegistry.register(L1SkillId.STATUS_CHAT_PROHIBITED,
                (pc, sid, polyId, rt, icon) -> SC_SkillIconGFX.send(pc, 36, rt));

        // ================= 6. COUNTER_MIRROR =================
        BuffRestoreRegistry.register(L1SkillId.COUNTER_MIRROR,
                (pc, sid, polyId, rt, icon) -> pc.addWeightReduction(300));

        // ================= 7. DECREASE_WEIGHT / REDUCE_WEIGHT (공유 로직) =================
        BuffRestoreRegistry.register(L1SkillId.DECREASE_WEIGHT, (pc, sid, polyId, rt, icon) -> {
            icon[0] = rt / 16;
            pc.addWeightReduction(180);
        });
        BuffRestoreRegistry.register(L1SkillId.REDUCE_WEIGHT, (pc, sid, polyId, rt, icon) -> {
            icon[0] = rt / 16;
            pc.addWeightReduction(180);
        });

        // ================= 8. 단순 아이콘 — icon[N] = rt/4 =================
        registerIconDiv(L1SkillId.DECAY_POTION,     1, 4);
        registerIconDiv(L1SkillId.SILENCE,          2, 4);
        registerIconDiv(L1SkillId.VENOM_RESIST,     3, 4);
        registerIconDiv(L1SkillId.NATURES_TOUCH,    8, 4);
        registerIconDiv(L1SkillId.WIND_SHACKLE,     9, 4);
        registerIconDiv(L1SkillId.ERASE_MAGIC,     10, 4);
        registerIconDiv(L1SkillId.ADDITIONAL_FIRE, 11, 4);
        registerIconDiv(L1SkillId.MORTAL_BODY,     23, 4);
        registerIconDiv(L1SkillId.FEAR,            25, 4);
        registerIconDiv(L1SkillId.PATIENCE,        26, 4);
        registerIconDiv(L1SkillId.DRAGON_SKIN,     28, 16);
        registerIconDiv(L1SkillId.CONCENTRATION,   20, 16);
        registerIconDiv(L1SkillId.ELEMENTAL_FIRE,  13, 4);
        registerIconDiv(L1SkillId.STRIKER_GALE,    14, 4);
        registerIconDiv(L1SkillId.SOUL_OF_FLAME,   15, 4);
        registerIconDiv(L1SkillId.POLLUTE_WATER,   16, 4);

        // ================= 9. WEAKNESS / DISEASE / BERSERKERS / GUARD_BREAK =================
        BuffRestoreRegistry.register(L1SkillId.WEAKNESS, (pc, sid, polyId, rt, icon) -> {
            icon[4] = rt / 4;
            pc.addDmgup(-5);
            pc.addHitup(-1);
        });
        BuffRestoreRegistry.register(L1SkillId.DISEASE, (pc, sid, polyId, rt, icon) -> {
            icon[5] = rt / 4;
            pc.addHitup(-6);
            pc.getAC().addAc(12);
        });
        BuffRestoreRegistry.register(L1SkillId.BERSERKERS, (pc, sid, polyId, rt, icon) -> {
            icon[7] = rt / 4;
            pc.getAC().addAc(10);
            pc.addDmgup(2);
            pc.addHitup(8);
        });
        BuffRestoreRegistry.register(L1SkillId.GUARD_BREAK, (pc, sid, polyId, rt, icon) -> {
            icon[27] = rt / 4;
            pc.getAC().addAc(15);
        });

        // ================= 10. 혈맹버프 8종 + 회복 =================
        BuffRestoreRegistry.register(L1SkillId.혈맹버프AC, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프AC(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프AC, 5538, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프마방, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프마방(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프마방, 5539, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프PVP추가, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프PVP추가(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프PVP추가, 5540, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프PVP감소, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프PVP감소(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프PVP감소, 5541, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프HP증가, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프HP증가(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프HP증가, 5542, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프올내성, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프올내성(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프올내성, 5543, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프올적중, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프올적중(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프올적중, 5544, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프올스텟, (pc, sid, polyId, rt, icon) -> {
            pc.혈맹버프올스텟(true);
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프올스텟, 5545, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.혈맹버프회복,
                (pc, sid, polyId, rt, icon) ->
            SC_InventoryIcon.icoNew(pc, L1SkillId.혈맹버프회복, 5546, rt, true));

        // ================= 11. 빈 case 5개 =================
        BuffRestoreRegistry.register(L1SkillId.버서크대기,    NO_OP);
        BuffRestoreRegistry.register(L1SkillId.아머데스티니,   NO_OP);
        BuffRestoreRegistry.register(L1SkillId.리덕션베테랑,   NO_OP);
        BuffRestoreRegistry.register(L1SkillId.카배마스터,    NO_OP);
        BuffRestoreRegistry.register(L1SkillId.세인트,       NO_OP);

        // ================= 12. pc.setXxx(true) 단일 호출 계열 =================
        registerSetTrue(L1SkillId.램페이지,        pc -> pc.램페이지(true));
        registerSetTrue(L1SkillId.에테리얼,        pc -> pc.에테리얼(true));
        registerSetTrue(L1SkillId.게일샷,         pc -> pc.게일샷(true));
        registerSetTrue(L1SkillId.프라이드,        pc -> pc.프라이드(true));
        registerSetTrue(L1SkillId.어드밴스,        pc -> pc.어드밴스(true));
        registerSetTrue(L1SkillId.솔리드캐리지,     pc -> pc.솔리드캐리지(true));
        registerSetTrue(L1SkillId.버닝,          pc -> pc.버닝(true));
        registerSetTrue(L1SkillId.이베이젼,        pc -> pc.이베이젼(true));
        registerSetTrue(L1SkillId.일루션리치,      pc -> pc.일루션리치(true));
        registerSetTrue(L1SkillId.엠파이어오버로드,   pc -> pc.엠파이어오버로드(true));
        registerSetTrue(L1SkillId.샤이닝아머,      pc -> pc.샤이닝아머(true));
        registerSetTrue(L1SkillId.마제스티,        pc -> pc.마제스티(true));
        registerSetTrue(L1SkillId.기간틱,         pc -> pc.기간틱(true));
        registerSetTrue(L1SkillId.일루션골렘,      pc -> pc.일루션골렘(true));
        registerSetTrue(L1SkillId.포스스턴,        pc -> pc.포스스턴(true));
        registerSetTrue(L1SkillId.레이징포스,      pc -> pc.레이징포스(true));
        registerSetTrue(L1SkillId.래스트,         pc -> pc.래스트(true));
        registerSetTrue(L1SkillId.네메시스,        pc -> pc.네메시스(true));
        registerSetTrue(L1SkillId.hp300,          pc -> pc.hp300(true));
        registerSetTrue(L1SkillId.mp300,          pc -> pc.mp300(true));
        registerSetTrue(L1SkillId.대미지감소5,      pc -> pc.대미지감소5(true));
        registerSetTrue(L1SkillId.ac5,           pc -> pc.ac5(true));
        registerSetTrue(L1SkillId.mr15,          pc -> pc.mr15(true));
        registerSetTrue(L1SkillId.경험치10,        pc -> pc.경험치10(true));
        registerSetTrue(L1SkillId.모든속성저항20,    pc -> pc.모든속성저항20(true));
        registerSetTrue(L1SkillId.힘3,           pc -> pc.힘3(true));
        registerSetTrue(L1SkillId.덱3,           pc -> pc.덱3(true));
        registerSetTrue(L1SkillId.콘3,           pc -> pc.콘3(true));
        registerSetTrue(L1SkillId.위즈3,         pc -> pc.위즈3(true));
        registerSetTrue(L1SkillId.인트3,         pc -> pc.인트3(true));
        registerSetTrue(L1SkillId.pvp대미지7,      pc -> pc.pvp대미지7(true));
        registerSetTrue(L1SkillId.pvp감소7,        pc -> pc.pvp감소7(true));
        registerSetTrue(L1SkillId.근거리대미지5,     pc -> pc.근거리대미지5(true));
        registerSetTrue(L1SkillId.원거리대미지5,     pc -> pc.원거리대미지5(true));
        registerSetTrue(L1SkillId.sp5,           pc -> pc.sp5(true));
        registerSetTrue(L1SkillId.마법적중5,       pc -> pc.마법적중5(true));
        registerSetTrue(L1SkillId.기술내성8,       pc -> pc.기술내성8(true));
        registerSetTrue(L1SkillId.정령내성8,       pc -> pc.정령내성8(true));
        registerSetTrue(L1SkillId.용언내성3,       pc -> pc.용언내성3(true));
        registerSetTrue(L1SkillId.공포내성3,       pc -> pc.공포내성3(true));
        registerSetTrue(L1SkillId.모든내성3,       pc -> pc.모든내성3(true));
        registerSetTrue(L1SkillId.정령적중5,       pc -> pc.정령적중5(true));
        registerSetTrue(L1SkillId.용언적중5,       pc -> pc.용언적중5(true));
        registerSetTrue(L1SkillId.공포적중5,       pc -> pc.공포적중5(true));
        registerSetTrue(L1SkillId.기술적중5,       pc -> pc.기술적중5(true));
        registerSetTrue(L1SkillId.모든적중3,       pc -> pc.모든적중3(true));
        registerSetTrue(L1SkillId.가드피어,        pc -> pc.가드피어(true));
        registerSetTrue(L1SkillId.비욘드,         pc -> pc.비욘드(true));
        registerSetTrue(L1SkillId.가드피어호러,      pc -> pc.가드피어호러(true));

        // 라우풀고정
        BuffRestoreRegistry.register(L1SkillId.라우풀고정,
                (pc, sid, polyId, rt, icon) -> pc.setLawfulStop(1));

        // ================= 13. 경험치보너스20 (이중 setSkillEffect 특수) =================
        BuffRestoreRegistry.register(L1SkillId.경험치보너스20, (pc, sid, polyId, rt, icon) -> {
            if (rt > 0) {
                pc.setSkillEffect(L1SkillId.경험치보너스20, rt * 1000);
            }
            pc.경험치보너스20(true);
        });

        // ================= 14. 할파스의신의 (장비 착용 조건) =================
        BuffRestoreRegistry.register(L1SkillId.할파스의신의, (pc, sid, polyId, rt, icon) -> {
            if (pc.getInventory().isEquippedDirect(123111)
                    || pc.getInventory().isEquippedDirect(123112)
                    || pc.getInventory().isEquippedDirect(123113)) {
                SC_InventoryIcon.icoEnd(pc, L1SkillId.할파스의신의1);
                SC_InventoryIcon.icoNew(pc, L1SkillId.할파스의신의, 3942, rt, true);
            }
        });

        // ================= 15. COMA_A / COMA_B =================
        BuffRestoreRegistry.register(L1SkillId.COMA_A, (pc, sid, polyId, rt, icon) -> {
            icon[30] = (rt + 16) / 32;
            icon[31] = 40;
            pc.getAbility().addAddedCon(1);
            pc.getAbility().addAddedDex(5);
            pc.getAbility().addAddedStr(5);
            pc.addHitRate(3);
            pc.getAC().addAc(-3);
        });
        BuffRestoreRegistry.register(L1SkillId.COMA_B, (pc, sid, polyId, rt, icon) -> {
            icon[30] = (rt + 16) / 32;
            icon[31] = 41;
            pc.getAbility().addSp(1);
            pc.getAbility().addAddedCon(3);
            pc.getAbility().addAddedDex(5);
            pc.getAbility().addAddedStr(5);
            pc.addHitRate(5);
            pc.getAC().addAc(-8);
        });

        // ================= 16. EXP_POTION / EXP_BUFF =================
        BuffRestoreRegistry.register(L1SkillId.EXP_POTION, (pc, sid, polyId, rt, icon) -> {
            if (pc.getSafetyZone()) {
                SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.EXP_POTION + 1, 4973, true);
            } else {
                SC_InventoryIcon.icoNew(pc, L1SkillId.EXP_POTION, 4973, rt, true);
            }
        });
        BuffRestoreRegistry.register(L1SkillId.EXP_BUFF, (pc, sid, polyId, rt, icon) -> {
            long hasad = pc.getAccount().getBlessOfAin();
            if (hasad < 10000) {
                SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.EXP_BUFF + 1, 5087, true);
            } else {
                SC_InventoryIcon.icoNew(pc, L1SkillId.EXP_BUFF, 5087, rt, true);
            }
        });

        // ================= 17. STATUS_CASHSCROLL 1~6 =================
        BuffRestoreRegistry.register(L1SkillId.STATUS_CASHSCROLL, (pc, sid, polyId, rt, icon) -> {
            icon[18] = cashTime(rt);
            icon[19] = 61;
            icon[38] = (rt <= 1020) ? 0 : 1;
            pc.addMaxHp(50);
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
            if (pc.isInParty()) {
                pc.getParty().refreshPartyMemberStatus(pc);
            }
            SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        });
        BuffRestoreRegistry.register(L1SkillId.STATUS_CASHSCROLL2, (pc, sid, polyId, rt, icon) -> {
            icon[18] = cashTime(rt);
            icon[19] = 62;
            icon[38] = (rt <= 1020) ? 0 : 1;
            pc.addMaxMp(40);
            SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        });
        BuffRestoreRegistry.register(L1SkillId.STATUS_CASHSCROLL3, (pc, sid, polyId, rt, icon) -> {
            icon[18] = cashTime(rt);
            icon[19] = 63;
            icon[38] = (rt <= 1020) ? 0 : 1;
            pc.addDmgup(3);
            pc.addHitup(3);
            pc.getAbility().addSp(3);
        });
        BuffRestoreRegistry.register(L1SkillId.STATUS_CASHSCROLL4, (pc, sid, polyId, rt, icon) -> {
            icon[18] = cashTime(rt);
            icon[19] = 63;
            icon[38] = (rt <= 1020) ? 0 : 1;
            pc.getAbility().addSp(3);
            pc.addBaseMagicHitUp(5);
            pc.getResistance().addcalcPcDefense(3);
        });
        BuffRestoreRegistry.register(L1SkillId.STATUS_CASHSCROLL5, (pc, sid, polyId, rt, icon) -> {
            icon[18] = cashTime(rt);
            icon[19] = 63;
            icon[38] = (rt <= 1020) ? 0 : 1;
            pc.addBowDmgup(3);
            pc.addBowHitup(5);
            pc.getResistance().addcalcPcDefense(3);
        });
        BuffRestoreRegistry.register(L1SkillId.STATUS_CASHSCROLL6, (pc, sid, polyId, rt, icon) -> {
            icon[18] = cashTime(rt);
            icon[19] = 63;
            icon[38] = (rt <= 1020) ? 0 : 1;
            pc.addDmgRate(3);
            pc.addHitup(5);
            pc.getResistance().addcalcPcDefense(3);
        });

        // ================= 18. INSIGHT / PANIC / HORROR_OF_DEATH =================
        BuffRestoreRegistry.register(L1SkillId.INSIGHT, (pc, sid, polyId, rt, icon) -> {
            icon[21] = rt / 16;
            pc.getAbility().addAddedStr((byte) 1);
            pc.getAbility().addAddedDex((byte) 1);
            pc.getAbility().addAddedCon((byte) 1);
            pc.getAbility().addAddedInt((byte) 1);
            pc.getAbility().addAddedWis((byte) 1);
            pc.resetBaseMr();
        });
        BuffRestoreRegistry.register(L1SkillId.PANIC, (pc, sid, polyId, rt, icon) -> {
            icon[22] = rt / 16;
            pc.getAbility().addAddedStr((byte) -1);
            pc.getAbility().addAddedDex((byte) -1);
            pc.getAbility().addAddedCon((byte) -1);
            pc.getAbility().addAddedInt((byte) -1);
            pc.getAbility().addAddedWis((byte) -1);
            pc.getAbility().addAddedCha((byte) -1);
            pc.resetBaseMr();
        });
        BuffRestoreRegistry.register(L1SkillId.HORROR_OF_DEATH, (pc, sid, polyId, rt, icon) -> {
            icon[24] = rt / 4;
            pc.getAbility().addAddedStr((byte) -10);
            pc.getAbility().addAddedInt((byte) -10);
        });

        // ================= 19. STATUS_FRUIT (다크호스 분기) =================
        BuffRestoreRegistry.register(L1SkillId.STATUS_FRUIT, (pc, sid, polyId, rt, icon) -> {
            icon[29] = rt / 4;
            if (pc.isPassive(MJPassiveID.DARK_HORSE.toInt())) {
                SC_SkillBrave.send(pc, pc.getId(), 3, rt);
                SC_SkillBrave.broadcast(pc, pc.getId(), 3, 0);
                pc.setBraveSpeed(3);
            } else {
                SC_SkillBrave.send(pc, pc.getId(), 4, rt);
                SC_SkillBrave.broadcast(pc, pc.getId(), 4, 0);
                pc.setBraveSpeed(4);
            }
        });

        // ================= 20. RESIST 계열 =================
        BuffRestoreRegistry.register(L1SkillId.RESIST_MAGIC, (pc, sid, polyId, rt, icon) -> {
            pc.getResistance().addMr(10);
            SC_ElfIcon.send(pc, rt / 16, 0, 0, 0);
        });
        BuffRestoreRegistry.register(L1SkillId.RESIST_ELEMENTAL, (pc, sid, polyId, rt, icon) -> {
            pc.getResistance().addAllNaturalResistance(10);
            SC_ElfIcon.send(pc, 0, 0, rt / 16, 0);
        });
        BuffRestoreRegistry.register(L1SkillId.ELEMENTAL_PROTECTION, (pc, sid, polyId, rt, icon) -> {
            int attr = pc.getElfAttr();
            if (attr == 1)      pc.getResistance().addEarth(50);
            else if (attr == 2) pc.getResistance().addFire(50);
            else if (attr == 4) pc.getResistance().addWater(50);
            else if (attr == 8) pc.getResistance().addWind(50);
            pc.getResistance().addSpecialPierce(eKind.SPIRIT, 3);
            SC_ElfIcon.send(pc, 0, 0, 0, rt / 16);
        });

        // ================= 21. MAAN 7종 =================
        BuffRestoreRegistry.register(L1SkillId.ANTA_MAAN, (pc, sid, polyId, rt, icon) -> {
            icon[34] = rt / 30;
            icon[35] = 46;
            pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
        });
        BuffRestoreRegistry.register(L1SkillId.FAFU_MAAN, (pc, sid, polyId, rt, icon) -> {
            icon[34] = rt / 30;
            icon[35] = 47;
            pc.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
        });
        BuffRestoreRegistry.register(L1SkillId.LIND_MAAN, (pc, sid, polyId, rt, icon) -> {
            icon[34] = rt / 30;
            icon[35] = 48;
            pc.getAbility().addSp(1);
            pc.getResistance().addSpecialResistance(eKind.FEAR, 5);
        });
        BuffRestoreRegistry.register(L1SkillId.VALA_MAAN, (pc, sid, polyId, rt, icon) -> {
            icon[34] = rt / 30;
            icon[35] = 49;
            pc.addDmgup(2);
            pc.getResistance().addSpecialResistance(eKind.ABILITY, 5);
        });
        BuffRestoreRegistry.register(L1SkillId.BIRTH_MAAN, (pc, sid, polyId, rt, icon) -> {
            icon[34] = rt / 30;
            icon[35] = 50;
            pc.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
            pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
        });
        BuffRestoreRegistry.register(L1SkillId.SHAPE_MAAN, (pc, sid, polyId, rt, icon) -> {
            icon[34] = rt / 30;
            icon[35] = 51;
            pc.getAbility().addSp(1);
            pc.getResistance().addSpecialResistance(eKind.SPIRIT, 5);
            pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5);
            pc.getResistance().addSpecialResistance(eKind.FEAR, 5);
        });
        BuffRestoreRegistry.register(L1SkillId.LIFE_MAAN, (pc, sid, polyId, rt, icon) -> {
            icon[34] = rt / 30;
            icon[35] = 52;
            pc.addDmgup(2);
            pc.getAbility().addSp(1);
            pc.getResistance().addSpecialResistance(eKind.ALL, 5);
            pc.getResistance().addSpecialPierce(eKind.ALL, 3);
        });

        // ================= 22. FEATHER_BUFF A~D =================
        BuffRestoreRegistry.register(L1SkillId.FEATHER_BUFF_A, (pc, sid, polyId, rt, icon) -> {
            icon[36] = rt / 16;
            icon[37] = 70;
            pc.addHpr(3);
            pc.addMpr(3);
            pc.addDmgup(2);
            pc.addHitup(2);
            pc.addMaxHp(50);
            pc.addMaxMp(30);
            pc.getAbility().addSp(2);
        });
        BuffRestoreRegistry.register(L1SkillId.FEATHER_BUFF_B, (pc, sid, polyId, rt, icon) -> {
            icon[36] = rt / 16;
            icon[37] = 71;
            pc.addHitup(2);
            pc.getAbility().addSp(1);
            pc.addMaxHp(50);
            pc.addMaxMp(30);
        });
        BuffRestoreRegistry.register(L1SkillId.FEATHER_BUFF_C, (pc, sid, polyId, rt, icon) -> {
            icon[36] = rt / 16;
            icon[37] = 72;
            pc.addMaxHp(50);
            pc.addMaxMp(30);
            pc.getAC().addAc(-2);
        });
        BuffRestoreRegistry.register(L1SkillId.FEATHER_BUFF_D, (pc, sid, polyId, rt, icon) -> {
            icon[36] = rt / 16;
            icon[37] = 73;
            pc.getAC().addAc(-1);
        });

        // ================= 23. DRAGON_BLOOD 3종 =================
        BuffRestoreRegistry.register(L1SkillId.ANTA_BUFF, (pc, sid, polyId, rt, icon) -> {
            pc.getAC().addAc(-2);
            pc.getResistance().addWater(50);
            SC_OwnCharStatus.send(pc);
            SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 82, rt / 60);
        });
        BuffRestoreRegistry.register(L1SkillId.FAFU_BUFF, (pc, sid, polyId, rt, icon) -> {
            pc.addHpr(3);
            pc.addMpr(1);
            pc.getResistance().addWind(50);
            SC_OwnCharStatus.send(pc);
            SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 85, rt / 60);
        });
        BuffRestoreRegistry.register(L1SkillId.RIND_BUFF, (pc, sid, polyId, rt, icon) -> {
            pc.addHitup(3);
            pc.addBowHitup(3);
            pc.getResistance().addFire(50);
            SC_OwnCharStatus.send(pc);
            SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 88, rt / 60);
        });

        // ================= 24. STATUS_DRAGON_PEARL =================
        BuffRestoreRegistry.register(L1SkillId.STATUS_DRAGON_PEARL, (pc, sid, polyId, rt, icon) -> {
            SC_Liquor.send(pc, pc.getId(), 8);
            SC_Liquor.broadcast(pc, pc.getId(), 8);
            SC_PacketBox.send(pc, SC_PacketBox.DRAGON_PEARL, 8, rt / 4);
            pc.setPearl(1);
        });

        // ================= 25. 레벨업보너스 / DRAGON_TOPAZ / PUPLE =================
        BuffRestoreRegistry.register(L1SkillId.레벨업보너스,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, rt, true, true));
        BuffRestoreRegistry.register(L1SkillId.DRAGON_TOPAZ, (pc, sid, polyId, rt, icon) -> {
            SC_PacketBox.send(pc, rt, 2, true, true);
            SC_RestExpInfo.send(pc);
        });
        BuffRestoreRegistry.register(L1SkillId.DRAGON_PUPLE,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, rt, 1, true, true));

        // ================= 26. EMERALD_NO / YES / 정상의가호 =================
        BuffRestoreRegistry.register(L1SkillId.EMERALD_NO,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, SC_PacketBox.EMERALD_ICON, 0x01, rt));
        BuffRestoreRegistry.register(L1SkillId.EMERALD_YES,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, SC_PacketBox.EMERALD_ICON, 0x02, rt));
        BuffRestoreRegistry.register(L1SkillId.정상의가호,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 12536, rt));

        // ================= 27. POLY_RING_MASTER =================
        BuffRestoreRegistry.register(L1SkillId.POLY_RING_MASTER,
                (pc, sid, polyId, rt, icon) ->
            L1PolyMorph.doPoly(pc, polyId, rt, L1PolyMorph.MORPH_BY_LOGIN, true));

        // ================= 28. 축복 계열 4종 =================
        BuffRestoreRegistry.register(L1SkillId.SIDE_OF_ME_BLESSING,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 14646, rt));
        BuffRestoreRegistry.register(L1SkillId.RE_START_BLESSING,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 14647, rt));
        BuffRestoreRegistry.register(L1SkillId.NEW_START_BLESSING,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 14648, rt));
        BuffRestoreRegistry.register(L1SkillId.LIFE_BLESSING,
                (pc, sid, polyId, rt, icon) -> SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 14649, rt));

        // ================= 29. FREEZEENG_ARMOR =================
        BuffRestoreRegistry.register(L1SkillId.FREEZEENG_ARMOR, (pc, sid, polyId, rt, icon) -> {
            pc.addEffectedER(5);
            SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
            sendScBuff(pc, L1SkillId.FREEZEENG_ARMOR, rt, 9490, 5889);
        });

        // ================= 30. miso 1~3 =================
        BuffRestoreRegistry.register(L1SkillId.miso1,
                (pc, sid, polyId, rt, icon) ->
            SC_InventoryIcon.icoNew(pc, L1SkillId.miso1, 4995, rt, true));
        BuffRestoreRegistry.register(L1SkillId.miso2, (pc, sid, polyId, rt, icon) -> {
            pc.addMaxHp(100);
            pc.getResistance().addMr(10);
            pc.addHpr(2);
            SC_SPMR.send(pc);
            SC_OwnCharAttrDef.send(pc);
            SC_InventoryIcon.icoNew(pc, L1SkillId.miso2, 4996, rt, true);
        });
        BuffRestoreRegistry.register(L1SkillId.miso3, (pc, sid, polyId, rt, icon) -> {
            pc.addDmgup(3);
            pc.addBowDmgup(3);
            pc.addMaxMp(50);
            pc.getAbility().addSp(3);
            pc.addMpr(2);
            SC_SPMR.send(pc);
            SC_OwnCharAttrDef.send(pc);
            SC_InventoryIcon.icoNew(pc, L1SkillId.miso3, 4997, rt, true);
        });

        // ================= 31. ENCHANT_ACURUCY / CLEAR_MIND / MAJESTY / SHINING_ARMOR =================
        BuffRestoreRegistry.register(L1SkillId.ENCHANT_ACURUCY, (pc, sid, polyId, rt, icon) -> {
            pc.addHitup(5);
            sendScBuff(pc, L1SkillId.ENCHANT_ACURUCY, rt, 9487, 5888);
        });
        BuffRestoreRegistry.register(L1SkillId.CLEAR_MIND, (pc, sid, polyId, rt, icon) -> {
            pc.getAbility().addAddedStr((byte) 1);
            pc.getAbility().addAddedDex((byte) 1);
            pc.getAbility().addAddedInt((byte) 1);
            sendScBuff(pc, L1SkillId.CLEAR_MIND, rt, 745, 861, 5279);
        });
        BuffRestoreRegistry.register(L1SkillId.MAJESTY,
                (pc, sid, polyId, rt, icon) -> sendScBuff(pc, L1SkillId.MAJESTY, rt, 9518, 5893));
        BuffRestoreRegistry.register(L1SkillId.SHINING_ARMOR, (pc, sid, polyId, rt, icon) -> {
            pc.addEffectedER(10);
            SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
            sendScBuff(pc, L1SkillId.SHINING_ARMOR, rt, 9483, 5892);
        });
        
    }

    // ==================================================================
    // 헬퍼
    // ==================================================================

    private static final BuffRestoreHandler NO_OP = (pc, sid, polyId, rt, icon) -> {};

    private interface SetTrueInvoker { void invoke(L1PcInstance pc); }
    private static void registerSetTrue(int skillId, SetTrueInvoker invoker) {
        BuffRestoreRegistry.register(skillId, (pc, sid, polyId, rt, icon) -> invoker.invoke(pc));
    }
    /** icon[N] = rt/div 단순 아이콘 등록 */
    private static void registerIconDiv(int skillId, int slot, int div) {
        BuffRestoreRegistry.register(skillId,
                (pc, sid, polyId, rt, icon) -> icon[slot] = rt / div);
    }

    /** S_UnityIcon 캐시시간 계산: (rt/4)-255, 0 이하면 +255 */
    private static int cashTime(int rt) {
        int t = (rt / 4) - 255;
        if (t <= 0) t += 255;
        return t;
    }

    /** SC_SPELL_BUFF_NOTI 공통 전송 */
    private static void sendScBuff(L1PcInstance pc, int spellId, int rt, int iconId, int tooltipId) {
        sendScBuff(pc, spellId, rt, iconId, tooltipId, tooltipId);
    }
    private static void sendScBuff(L1PcInstance pc, int spellId, int rt, int iconId, int tooltipId, int offIconId) {
        LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
        int notiWire_spellId = 0;
        int notiWire_duration = 0;
        LIN.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
        int notiWire_onIconId = 0;
        int notiWire_offIconId = 0;
        int notiWire_tooltipStrId = 0;
        int notiWire_newStrId = 0;
        boolean notiWire_isGood = false;
        int notiWire_bit = 0;


        { notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.RESTAT; }
        { notiWire_bit |= 2; notiWire_spellId = spellId; }
        { notiWire_bit |= 4; notiWire_duration = rt; }
        { notiWire_bit |= 8; notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }
        { notiWire_bit |= 16; notiWire_onIconId = iconId; }
        { notiWire_bit |= 32; notiWire_offIconId = offIconId; }
        { notiWire_bit |= 128; notiWire_tooltipStrId = tooltipId; }
        { notiWire_bit |= 256; notiWire_newStrId = tooltipId; }
        { notiWire_bit |= 1024; notiWire_isGood = true; }
        LIN.SC_SPELL_BUFF_NOTI.sendFields(
                pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
                notiWire_offIconId, 0, notiWire_tooltipStrId, notiWire_newStrId, 0, notiWire_isGood, 0, 0, 0, notiWire_bit);
    }
}