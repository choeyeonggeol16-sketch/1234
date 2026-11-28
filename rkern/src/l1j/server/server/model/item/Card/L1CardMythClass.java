package l1j.server.server.model.item.Card;

import l1j.server.server.model.character.transform.TransformType;
import l1j.server.server.model.character.transform.CTransform;
import l1j.server.server.Account;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 신화 클래스 전용 변신 핸들러 
 */
public class L1CardMythClass {

    private static final int POLY_SCROLL_ID = 875640379; // 변신 주문서 ID
    private static final int POLY_DURATION = 14400;      // 변신 시간 (4시간)

    public static void polyclass(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        int targetSprite = -1;

        // 클래스별 신화 카드 보유 여부 및 변신 번호 결정
        if (pc.isCrown() && acc.getInfo_class_poly() != 0) {
            targetSprite = (pc.get_sex() == 0) ? 21082 : 21086;
        } 
        else if (pc.isKnight() && acc.getInfo_ki_poly() != 0) {
            targetSprite = (pc.get_sex() == 0) ? 19082 : 18942;
        } 
        else if (pc.isElf() && acc.getInfo_el_poly() != 0) {
            if (pc.get_sex() == 0) {
                targetSprite = (pc.getCurrentSpriteId() == 21070) ? 21053 : 21070;
            } else {
                targetSprite = (pc.getCurrentSpriteId() == 21066) ? 21049 : 21066;
            }
        } 
        else if (pc.isWizard() && acc.getInfo_wi_poly() != 0) {
            targetSprite = (pc.get_sex() == 0) ? 21074 : 21078;
        } 
        else if (pc.isDarkelf() && acc.getInfo_da_poly() != 0) {
            targetSprite = (pc.get_sex() == 0) ? 21090 : 21094;
        } 
        else if (pc.isWarrior() && acc.getInfo_wa_poly() != 0) {
            targetSprite = 19086; // 전사는 남녀 공통
        } 
        else if (pc.isDragonknight() && acc.getInfo_drag_poly() != 0) {
            targetSprite = 19090; // 용기사 남녀 공통
        } 
        else if (pc.isBlackwizard() && acc.getInfo_il_poly() != 0) {
            targetSprite = (pc.get_sex() == 0) ? 21132 : 21136;
        }

        //  보유 카드가 없거나 해당 클래스가 아니면 종료
        if (targetSprite == -1) return;

        // 공통 제약 조건 체크 
        if (!pc.getInventory().HaveItemByClassId(POLY_SCROLL_ID, 1)) {
            SC_SystemMessage.send(pc, "변신주문서가 부족합니다.");
            return;
        }
        if (pc.isInvisble()) {
            SC_SystemMessage.send(pc, "지금은 사용할 수 없는 상태입니다.");
            return;
        }

        CTransform.TransformWithCost(pc, targetSprite, POLY_DURATION, TransformType.ITEM_MAGIC, true,
                POLY_SCROLL_ID, 1, () -> {
                    long sound = SC_SkillSound.pack(pc.getId(), 6082);
                    SC_SkillSound.sendPacked(pc, sound);
                    SC_SkillSound.broadcastPacked(pc, sound);
                });
    }
}
