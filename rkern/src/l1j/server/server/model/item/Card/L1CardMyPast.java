package l1j.server.server.model.item.Card;

import l1j.server.server.shared.GameRandom;


import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 신화 합성 대상 카드 자동 선택 시스템
 */
public class L1CardMyPast {

    public static void L1PastSelct(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null || pc.isItemTransferBusy()) return;

        Account acc = pc.getAccount();
        
        // 현재 소지 중인 11종 신화/유일 카드의 수량을 리스트에 담음 (201~211번 매핑)
        int[] eligibleCards = new int[11];
        int eligibleCount = 0;
        if (acc.getInfo_class_poly() >= 2) eligibleCards[eligibleCount++] = 201;
        if (acc.getInfo_ki_poly() >= 2) eligibleCards[eligibleCount++] = 202;
        if (acc.getInfo_el_poly() >= 2) eligibleCards[eligibleCount++] = 203;
        if (acc.getInfo_wi_poly() >= 2) eligibleCards[eligibleCount++] = 204;
        if (acc.getInfo_da_poly() >= 2) eligibleCards[eligibleCount++] = 205;
        if (acc.getInfo_wa_poly() >= 2) eligibleCards[eligibleCount++] = 206;
        if (acc.getInfo_drag_poly() >= 2) eligibleCards[eligibleCount++] = 207;
        if (acc.getInfo_il_poly() >= 2) eligibleCards[eligibleCount++] = 208;
        if (acc.getInfo_dsn_poly() >= 2) eligibleCards[eligibleCount++] = 209;
        if (acc.getInfo_bapo_poly() >= 2) eligibleCards[eligibleCount++] = 210;
        if (acc.getmyth01() >= 2) eligibleCards[eligibleCount++] = 211;


        // 중복 보유한 카드가 4종류 미만이면 실행 불가
        if (eligibleCount < 4) {
            SC_SystemMessage.send(pc, "보유한 카드가 4종류 이상이어야 자동 등록이 가능합니다.");
            return;
        }

        // 리스트를 무작위로 섞음
        shufflePrefix(eligibleCards, eligibleCount, 4);

        //  기존 선택 정보 초기화 및 새로운 카드 4장 세팅
        acc.setInfo_card_a(eligibleCards[0]);
        acc.setInfo_card_b(eligibleCards[1]);
        acc.setInfo_card_c(eligibleCards[2]);
        acc.setInfo_card_d(eligibleCards[3]);

        // UI 업데이트
        L1CardC.cardlist(pc);
    }


    // Select an unbiased, uniformly ordered prefix with no List, boxing, or temporary shuffle copy.
    // Partial Fisher-Yates: all ordered pairs/quads have equal probability.
    private static void shufflePrefix(int[] cards, int length, int selected) {
        for (int i = 0; i < selected; i++) {
            int j = i + GameRandom.nextInt(length - i);
            int tmp = cards[i];
            cards[i] = cards[j];
            cards[j] = tmp;
        }
    }
}
