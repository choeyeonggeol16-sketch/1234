package l1j.server.server.model.item.Card;

import l1j.server.server.Account;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 전설 샤르나 변신 카드 핸들러 
 */
public class L1CardSyarna {

    private static final int POLY_SCROLL_ID = 875640379; // 변신 주문서 ID
    private static final int POLY_DURATION = 14400;      // 변신 시간 (4시간)

    public static void polyclass(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();

        // 샤르나 카드 보유 여부 체크 (legend09 = 샤르나)
        if (acc.getlegend09() == 0) return;

        // 변신 불가 클래스 체크 (전사, 용기사, 환술사)
        if (pc.isWarrior() || pc.isDragonknight() || pc.isBlackwizard()) {
            SC_ChatPacket.sendDirect(pc, pc, "\\aG샤르나 변신은 용기사, 전사, 환술사는 불가합니다.");
            return;
        }
        // 클래스 및 성별에 따른 변신 Sprite ID 결정
        int maleSprite = -1;
        int femaleSprite = -1;

        if (pc.isCrown()) {
            maleSprite = 15254; femaleSprite = 15258;
        } else if (pc.isKnight()) {
            maleSprite = 15272; femaleSprite = 15276;
        } else if (pc.isElf()) {
            maleSprite = 15280; femaleSprite = 15284;
        } else if (pc.isWizard()) {
            maleSprite = 15296; femaleSprite = 15300;
        } else if (pc.isDarkelf()) {
            maleSprite = 15288; femaleSprite = 15292;
        }

        // 해당 사항 없는 경우 종료
        if (maleSprite == -1) return;

        // 공통 제약 조건 검사 (주문서, 은신)
        if (!pc.getInventory().HaveItemByClassId(POLY_SCROLL_ID, 1)) {
            SC_SystemMessage.send(pc, "변신주문서가 부족합니다.");
            return;
        }
        if (pc.isInvisble()) {
            SC_SystemMessage.send(pc, "지금은 사용할 수 없는 상태입니다.");
            return;
        }

        // 실행 (아이템 소모 및 변신)
        pc.getInventory().DeleteItemInInventory(POLY_SCROLL_ID, 1);
        int targetSprite = (pc.get_sex() == 0) ? maleSprite : femaleSprite;
        
        executeSyarnaPoly(pc, targetSprite);
    }

    /**
     * 변신 실행 및 공통 후처리
     */
    private static void executeSyarnaPoly(L1PcInstance pc, int spriteId) {
        // 변신 수행
        L1PolyMorph.doPoly(pc, spriteId, POLY_DURATION, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
        
        // 버그 체크 및 사운드 출력
       // L1NeigeBugCheck.shapechange(pc);
        
        long ss = SC_SkillSound.pack(pc.getId(), 6082);
        SC_SkillSound.sendPacked(pc, ss);
        SC_SkillSound.broadcastPacked(pc, ss);
    }
}