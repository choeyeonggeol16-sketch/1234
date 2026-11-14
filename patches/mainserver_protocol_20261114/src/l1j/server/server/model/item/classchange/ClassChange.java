package l1j.server.server.model.item.classchange;

import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import java.sql.Connection;
import java.sql.PreparedStatement;

import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJClassesType.EClassesType;
import l1j.server.server.Account;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.packets.Protocol.Packet.SC_TOP_RANKER_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_DelSkill;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
public class ClassChange {

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

    
    // 클래스 이름
    private static final String[] CLASS_NAMES = { "군주", "기사", "요정", "마법사", "다크엘프", "용기사", "환술사", "전사" };

    public static void usePotion(L1PcInstance pc, L1ItemInstance item) {
        int itemId = item.getItemId();
        int targetType = -1;

        if (itemId >= 51093 && itemId <= 51100) {
            targetType = itemId - 51093;
        }

        if (targetType == -1) return;
        
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

        deleteSkillsAndResetStats(pc);

        // 클래스 변경 수행
        changeClass(pc, targetType);

        // 아이템 삭제 및 저장
        pc.getInventory().removeItem(item, 1);
        pc.save();

        // 재시작 처리 (0.5초 후)
        handleRestart(pc);
    }
    private static void deleteSkillsAndResetStats(L1PcInstance pc) {
    	clearNewSkills(pc);
    	resetStats(pc);
        
        // 장비 해제
        if (pc.getWeapon() != null) {
            pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
        }
        pc.getInventory().takeoffEquip(945); 

        for (L1ItemInstance armor : pc.getInventory().getItems()) {
            if (armor != null && armor.isEquipped()) {
                pc.getInventory().setEquipped(armor, false, false, false, false);
            }
        }
       
        // 스킬 및 스펠 삭제
        SC_DelSkill.send(pc, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255);
        deleteSkills(pc);
        deletePassives(pc);
        pc.clearSlotChange();
    }

    private static void changeClass(L1PcInstance pc, int targetType) {

        pc.setType(targetType);
        
        int newClassId = (pc.get_sex() == 0) ? MALE_CLASSES[targetType] : FEMALE_CLASSES[targetType];
        pc.setClassId(newClassId);
        pc.setCurrentSprite(newClassId);

        // 랭킹 시스템 업데이트
        SC_TOP_RANKER_NOTI noti = RankUserLoader.getInstance().get(pc.getId());
        if (noti != null) {
            noti.set_class(EClassesType.fromGfx(pc.getClassId()).toInt());
        }

        pc.sendShape(pc.getClassId());
    }

    private static void handleRestart(final L1PcInstance pc) {
        ThreadPool.getInstance().scheduleRequired(new Runnable() {
            @Override
            public void run() {
                CIOClient clnt = pc.getNetConnection();
                if (clnt == null) return;
                
                CS_CharSelect.restartProcess(pc);
                CS_CommonClick.sendCharAmountAndPacks(clnt);
            }
        }, 500L);
    }
    
    private static void clearNewSkills(L1PcInstance pc) {
        // 삭제할 아이템 ID 목록
        int[] itemIdsToConsume = {
            70733, 90088, 80888, 70706, 70708, 70710, 70714, 70721, 
            707777, 707779, 707781, 70735, 70888
        };

        for (int itemId : itemIdsToConsume) {
            if (pc.getInventory().checkItem(itemId)) {
                pc.getInventory().consumeItem(itemId);
            }
        }

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
        }
    }

    
    private static void resetStats(L1PcInstance pc) {
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

            for (L1ItemInstance item : pc.getInventory().getItems()) {
                if (item != null && item.isEquipped()) {
                    pc.getInventory().setEquipped(item, false, false, false, false);
                }
            }

            SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
            SC_OwnCharStatus2.send(pc);

            pc.setReturnStat(pc.getExp());
            
            pc.save();
            
        } catch (Exception e) {
            // 에러 메시지를 좀 더 명확하게 출력
            System.err.println("[Error] 캐릭터 스텟 초기화 중 예외 발생: " + pc.getName());
            e.printStackTrace();
        }
    }
    
    private static void executeDelete(int charId, String sql) {
        Connection con = null;
        PreparedStatement pstm = null;
        try {
            con = DatabaseFactory.getInstance().getConnection();
            pstm = con.prepareStatement(sql);
            pstm.setInt(1, charId);
            pstm.execute();
        } catch (Exception e) {
            // 어떤 쿼리 실행 중 에러가 났는지 로그 출력
            System.err.println("[DB Error] Query: " + sql);
            e.printStackTrace();
        } finally {
            SQLUtil.close(pstm);
            SQLUtil.close(con);
        }
    }

    private static void deleteSkills(L1PcInstance pc) {
        // 액티브 스킬 삭제
        executeDelete(pc.getId(), "DELETE FROM character_skills WHERE char_obj_id=?");
    }

    private static void deletePassives(L1PcInstance pc) {
        // 패시브 스킬 삭제
        executeDelete(pc.getId(), "DELETE FROM passive_user_info WHERE character_id=?");
    }
}