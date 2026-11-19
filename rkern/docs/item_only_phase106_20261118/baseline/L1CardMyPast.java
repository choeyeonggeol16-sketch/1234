package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 신화 합성 대상 카드 자동 선택 시스템
 */
public class L1CardMyPast {

    // IDs are fixed (201~211). Reuse immutable entries instead of allocating
    // one CardData per eligible card on every auto-selection.
    private static final CardData[] CARD_IDS = new CardData[11];
    static {
        for (int i = 0; i < CARD_IDS.length; i++) CARD_IDS[i] = new CardData(201 + i);
    }

    public static void L1PastSelct(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        
        // 현재 소지 중인 11종 신화/유일 카드의 수량을 리스트에 담음 (201~211번 매핑)
        List<CardData> eligibleCards = new ArrayList<>(11);
        
        checkAndAdd(eligibleCards, 201, acc.getInfo_class_poly());
        checkAndAdd(eligibleCards, 202, acc.getInfo_ki_poly());
        checkAndAdd(eligibleCards, 203, acc.getInfo_el_poly());
        checkAndAdd(eligibleCards, 204, acc.getInfo_wi_poly());
        checkAndAdd(eligibleCards, 205, acc.getInfo_da_poly());
        checkAndAdd(eligibleCards, 206, acc.getInfo_wa_poly());
        checkAndAdd(eligibleCards, 207, acc.getInfo_drag_poly());
        checkAndAdd(eligibleCards, 208, acc.getInfo_il_poly());
        checkAndAdd(eligibleCards, 209, acc.getInfo_dsn_poly());
        checkAndAdd(eligibleCards, 210, acc.getInfo_bapo_poly());
        checkAndAdd(eligibleCards, 211, acc.getmyth01());

        // 중복 보유한 카드가 4종류 미만이면 실행 불가
        if (eligibleCards.size() < 4) {
            SC_SystemMessage.send(pc, "보유한 카드가 4종류 이상이어야 자동 등록이 가능합니다.");
            return;
        }

        // 리스트를 무작위로 섞음
        Collections.shuffle(eligibleCards);

        //  기존 선택 정보 초기화 및 새로운 카드 4장 세팅
        acc.setInfo_card_a(eligibleCards.get(0).id);
        acc.setInfo_card_b(eligibleCards.get(1).id);
        acc.setInfo_card_c(eligibleCards.get(2).id);
        acc.setInfo_card_d(eligibleCards.get(3).id);

        // UI 업데이트
        L1CardC.cardlist(pc);
    }

    /**
     * 카드가 2장 이상(합성 가능)인 경우에만 리스트에 추가
     */
    private static void checkAndAdd(List<CardData> list, int id, int count) {
        if (count >= 2) {
            list.add(CARD_IDS[id - 201]);
        }
    }

    private static class CardData {
        final int id;
        CardData(int id) { this.id = id; }
    }
}