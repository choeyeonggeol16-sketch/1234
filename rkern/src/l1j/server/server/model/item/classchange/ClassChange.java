package l1j.server.server.model.item.classchange;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.stream.IntStream;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.classinfo.EClassesType;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_DelSkill;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_TOP_RANKER_NOTI;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.storage.mysql.CSkillAcquireStorage;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;
public final class ClassChange {
    private static final Logger LOG = Logger.getLogger(ClassChange.class.getName());
    private static final int[] RETIRED_SKILL_ITEMS = {
        70733, 90088, 80888, 70706, 70708, 70710, 70714, 70721,
        707777, 707779, 707781, 70735, 70888
    };

    private ClassChange() {}


    // 클래스 ID 정의 (남성, 여성)
    private static final int[] MALE_CLASSES = { 0, 61, 138, 734, 2786, 6658, 6671, 12490 };
    private static final int[] FEMALE_CLASSES = { 1, 48, 37, 1186, 2796, 6661, 6650, 12494 };
    
    // 아이콘 종료 패킷 송신 + 스킬 효과 삭제 대상
    private static final int[] skillsWithIcons = {
            L1SkillId.램페이지, L1SkillId.래스트, L1SkillId.맥시멈, L1SkillId.게일샷,
            L1SkillId.버서크, L1SkillId.할파스, L1SkillId.포스스턴, L1SkillId.레이징포스,
            L1SkillId.기간틱, L1SkillId.엠파이어오버로드, L1SkillId.샤이닝아머, L1SkillId.마제스티,
            L1SkillId.네메시스, L1SkillId.가드피어, L1SkillId.가드피어호러, L1SkillId.버닝,
            L1SkillId.이베이젼, L1SkillId.솔리드캐리지, L1SkillId.프라이드, L1SkillId.일루션리치,
            L1SkillId.일루션골렘, L1SkillId.비욘드
        };
    
    // 스킬 효과만 삭제 대상 (아이콘 처리 없음)
    private static final int[] skillsOnly = {
            L1SkillId.어드밴스, L1SkillId.에볼루션, L1SkillId.아머데스티니, 
            L1SkillId.카배마스터, L1SkillId.프라임, L1SkillId.포텐셜, 
            L1SkillId.세인트, L1SkillId.리덕션베테랑
        };

    
    private static final int[] RETIRED_EFFECTS = IntStream.concat(Arrays.stream(skillsWithIcons),
            IntStream.concat(Arrays.stream(skillsOnly), IntStream.of(L1SkillId.에테리얼, L1SkillId.SHAPE_CHANGE, L1SkillId.POLY_RING_MASTER))).toArray();

    // 클래스 이름
    private static final String[] CLASS_NAMES = { "군주", "기사", "요정", "마법사", "다크엘프", "용기사", "환술사", "전사" };

    public static void usePotion(L1PcInstance pc, L1ItemInstance item) {
        int targetType = item.getItemId() - 51093;
        if (targetType < 0 || targetType >= CLASS_NAMES.length) return;
        
        if (!pc.getMap().isSafetyZone(pc.getLocation())) {
            SC_SystemMessage.send(pc, "이 아이템은 마을에서 사용 할 수 있습니다.");
            return;
        }

        if (pc.getClanid() != 0) {
            SC_SystemMessage.send(pc, "혈맹을 먼저 탈퇴하여 주시기 바랍니다.");
            return;
        }

        if (pc.getType() == targetType) {
            SC_SystemMessage.send(pc, "당신은 이미 " + CLASS_NAMES[targetType] + " 클래스 입니다.");
            return;
        }
        
        if (pc.getLevel() < 51) {
            SC_SystemMessage.send(pc, "클래스 변경은 51레벨부터 가능합니다.");
            return;
        }

        int oldType = pc.getType();
        int newGfx = (pc.get_sex() == 0 ? MALE_CLASSES : FEMALE_CLASSES)[targetType];
        int returnStat = pc.getExp();
        try (L1InventoryTransaction tx = L1InventoryTransaction.beginCharacterStat(pc)) {
            if (!tx.debit(item, 1)) return;
            for (int itemId : RETIRED_SKILL_ITEMS) {
                L1ItemInstance retired = pc.getInventory().GetFirstItemByClassID(itemId);
                if (retired != null && retired.getCount() > 0 && !tx.debit(retired, retired.getCount()))
                    throw new IllegalStateException("Cannot retire class skill item " + itemId);
            }
            tx.requireTables("characters", "character_skills", "passive_user_info", "character_buff", "character_items");
            tx.sql(con -> {
                CSkillAcquireStorage.changeClass(con, pc.getId(), oldType, targetType, newGfx, returnStat);
                CSkillAcquireStorage.deletePermanentEffects(con, pc.getId(), RETIRED_EFFECTS);
                MySqlCharactersItemStorage.clearEquippedForClassChange(con, pc.getId());
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "클래스 변경을 저장하지 못했습니다."));
            tx.commitAfterInventoryRelease(() -> {
                pc.lockUserState();
                try {
                    pc.setType(targetType);
                    pc.setClassId(newGfx);
                    pc.setReturnStat(returnStat);
                    pc.DeleteAllAcquireSkills();
                    pc.clearAcquiredPassives();
                    pc.publishPermanentStatRevision();
                } finally {
                    pc.unlockUserState();
                }
            }, () -> {
                try {
                    pc.CancelTransform();
                    deleteSkillsAndResetStats(pc, oldType);
                    finishClassChange(pc, newGfx);
                    pc.save();
                } finally {
                    handleRestart(pc);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Could not prepare class change for " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "클래스 변경을 처리하지 못했습니다.");
        }
    }

    private static void deleteSkillsAndResetStats(L1PcInstance pc, int previousClass) {
        clearNewSkills(pc);
        resetStats(pc, previousClass);

        // 스킬 및 스펠 삭제
        SC_DelSkill.send(pc, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255);
        pc.clearSlotChange();
    }

    private static void finishClassChange(L1PcInstance pc, int newGfx) {
        pc.setCurrentSprite(newGfx);

        // 랭킹 시스템 업데이트
        SC_TOP_RANKER_NOTI noti = RankUserLoader.getInstance().get(pc.getId());
        if (noti != null) {
            noti.set_class(EClassesType.fromGfx(pc.getClassId()).toInt());
        }

        pc.sendShape(pc.getClassId());
    }

    private static void handleRestart(final L1PcInstance pc) {
        CS_CharSelect.requestRestartAfter(pc, 500L);
    }
    
    private static void clearNewSkills(L1PcInstance pc) {
        for (int skillId : skillsWithIcons) {
            if (pc.hasSkillEffect(skillId)) {
                SC_InventoryIcon.icoEnd(pc, skillId);
                pc.removeSkillEffect(skillId);
            }
        }

        for (int skillId : skillsOnly) {
            if (pc.hasSkillEffect(skillId)) {
                pc.removeSkillEffect(skillId);
            }
        }

        if (pc.hasSkillEffect(L1SkillId.에테리얼)) {
            SC_InventoryIcon.icoEnd(pc, L1SkillId.에테리얼);
            pc.removeSkillEffect(L1SkillId.에테리얼);
        }
    }

    
    private static void resetStats(L1PcInstance pc, int previousClass) {
        try {
        	L1SkillUse.cast(
        			pc,
        			L1SkillId.CANCELLATION,
        			pc.getId(),
        			pc.getX(),
        			pc.getY(),
        			null,
        			0,
        			L1SkillUse.TYPE_LOGIN);

            pc.getEquipment().removeForClassChange(previousClass);

            SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
            SC_OwnCharStatus2.send(pc);

        } catch (Exception e) {
            // 에러 메시지를 좀 더 명확하게 출력
            System.err.println("[Error] 캐릭터 스텟 초기화 중 예외 발생: " + pc.getName());
            e.printStackTrace();
        }
    }
}
