package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR; // 패키지 경로는 본인 서버에 맞게 수정

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;

import l1j.server.server.utils.CommonUtil;

public class StatReset {

    private static final Logger LOG = Logger.getLogger(StatReset.class.getName());

    private static final int CANDLE_ITEM_ID = 200000;
    private static final int RESET_MAP_ID = 5166;

    public static void useCandle(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != CANDLE_ITEM_ID
                || pc.getInventory().getItem(item.getId()) != item) return;
        //  사전 체크 (레벨 51 미만)
        if (pc.getLevel() < 51) {
            SC_SystemMessage.send(pc, "스텟초기화는 51레벨 이상만 가능합니다.");
            return;
        }

        //  안전지역 체크
        if (!pc.getMap().isSafetyZone(pc.getLocation())) {
            SC_SystemMessage.send(pc, "안전한 지역에서만 사용할 수 있습니다.");
            return;
        }

        //  레벨다운 캐릭 체크
        if (pc.getLevel() != pc.getHighLevel()) {
            SC_SystemMessage.send(pc, "레벨이 다운된 캐릭입니다. 레벨업 후 이용하세요.");
            return;
        }

        // Persist the exact candle object before moving or resetting the character.
        // Stat changes issue a character save, so execute after CacheD has
        // released the inventory receipt's owner-apply barrier.
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(item, 1)) return;
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "촛불 사용을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            tx.commitAfterInventoryRelease(() -> {
                int locx = 32723 + CommonUtil.random(10);
                int locy = 32851 + CommonUtil.random(10);
                pc.start_teleport(locx, locy, RESET_MAP_ID, pc.getHeading(), 169, false, false);
                resetStats(pc);
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Stat reset candle prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "촛불 사용을 처리하지 못했습니다.");
        }
    }

    /**
     * 스텟 초기화
     */
    public static void resetStats(L1PcInstance pc) {
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

            if (pc.getWeapon() != null) {
				pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
			}

			for (L1ItemInstance armor : pc.getInventory().getItems()) {
                if (armor != null && armor.isEquipped()) {
                    pc.getInventory().setEquipped(armor, false, false, false, false);
                }
            }
            // 스텟 리턴 포인트 설정
    		pc.setReturnStat(pc.getExp());
            pc.save();
            // Optional UI updates run only after committed gameplay state and
            // the durable character save request. One failed packet must not
            // block the remaining client updates.
            try { SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc)); }
            catch (RuntimeException noticeFailure) {
                LOG.log(Level.WARNING, "Committed stat reset visual notice failed", noticeFailure);
            }
            try { SC_SPMR.send(pc); }
            catch (RuntimeException noticeFailure) {
                LOG.log(Level.WARNING, "Committed stat reset MR notice failed", noticeFailure);
            }
            try { SC_OwnCharAttrDef.send(pc); }
            catch (RuntimeException noticeFailure) {
                LOG.log(Level.WARNING, "Committed stat reset attributes notice failed", noticeFailure);
            }
            try { SC_OwnCharStatus2.send(pc); }
            catch (RuntimeException noticeFailure) {
                LOG.log(Level.WARNING, "Committed stat reset status notice failed", noticeFailure);
            }
            try { SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.START); }
            catch (RuntimeException noticeFailure) {
                LOG.log(Level.WARNING, "Committed stat reset interface notice failed", noticeFailure);
            }
            
        } catch (Exception e) {
            System.err.println("[Error] 스텟 초기화 중 오류 발생: " + pc.getName());
            e.printStackTrace();
        }
    }
}