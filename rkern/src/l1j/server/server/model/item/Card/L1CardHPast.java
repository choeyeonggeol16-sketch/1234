package l1j.server.server.model.item.Card;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 영웅 합성 대상 카드 자동 선택 시스템
 */
public class L1CardHPast {
    
    public static void L1PastSelct(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null || pc.isItemTransferBusy()) return;

        Account acc = pc.getAccount();
        
        // Read all 36 counters in the same order; retain only eligible IDs.
        // The temporary int[37] snapshot was not needed after this filtering pass.
        int[] eligibleIndices = new int[36];
        int eligibleCount = loadEligibleIndices(acc, eligibleIndices);

        //  중복 카드가 4종류 미만이면 실행 불가
        if (eligibleCount < 4) {
            SC_SystemMessage.send(pc, "보유한 카드가 4종류 이상이어야 자동 등록이 가능합니다.");
            return;
        }

        shufflePrefix(eligibleIndices, eligibleCount, 4);

        // 앞의 4개를 선택하여 계정 데이터에 즉시 세팅
        acc.setInfo_card_a(eligibleIndices[0]);
        acc.setInfo_card_b(eligibleIndices[1]);
        acc.setInfo_card_c(eligibleIndices[2]);
        acc.setInfo_card_d(eligibleIndices[3]);

        // UI 업데이트
        L1CardA.cardlist(pc);
    }

    /**
     * 계정에서 36종의 영웅 카드 정보를 배열에 담음
     */
    private static int loadEligibleIndices(Account acc, int[] eligible) {
        int count = 0;
        if (acc.getInfo_heaven_poly() >= 2) eligible[count++] = 1;
        if (acc.gethero01() >= 2) eligible[count++] = 2;
        if (acc.gethero02() >= 2) eligible[count++] = 3;
        if (acc.gethero03() >= 2) eligible[count++] = 4;
        if (acc.gethero04() >= 2) eligible[count++] = 5;
        if (acc.gethero13() >= 2) eligible[count++] = 6;
        if (acc.gethero14() >= 2) eligible[count++] = 7;
        if (acc.gethero05() >= 2) eligible[count++] = 8;
        if (acc.gethero06() >= 2) eligible[count++] = 9;
        if (acc.gethero07() >= 2) eligible[count++] = 10;
        if (acc.getInfo_assm_poly() >= 2) eligible[count++] = 11;
        if (acc.gethero08() >= 2) eligible[count++] = 12;
        if (acc.gethero09() >= 2) eligible[count++] = 13;
        if (acc.getInfo_unik_poly() >= 2) eligible[count++] = 14;
        if (acc.gethero10() >= 2) eligible[count++] = 15;
        if (acc.getInfo_unir_poly() >= 2) eligible[count++] = 16;
        if (acc.getInfo_unim_poly() >= 2) eligible[count++] = 17;
        if (acc.gethero11() >= 2) eligible[count++] = 18;
        if (acc.getInfo_val_poly() >= 2) eligible[count++] = 19;
        if (acc.getInfo_larry_poly() >= 2) eligible[count++] = 20;
        if (acc.getInfo_gunter_poly() >= 2) eligible[count++] = 21;
        if (acc.getInfo_depor_poly() >= 2) eligible[count++] = 22;
        if (acc.getInfo_deathred_poly() >= 2) eligible[count++] = 23;
        if (acc.getInfo_ecil_poly() >= 2) eligible[count++] = 24;
        if (acc.gethero12() >= 2) eligible[count++] = 25;
        if (acc.gethero15() >= 2) eligible[count++] = 26;
        if (acc.gethero16() >= 2) eligible[count++] = 27;
        if (acc.getInfo_yozil_poly() >= 2) eligible[count++] = 28;
        if (acc.getInfo_owen_poly() >= 2) eligible[count++] = 29;
        if (acc.getInfo_helv_poly() >= 2) eligible[count++] = 30;
        if (acc.gethero17() >= 2) eligible[count++] = 31;
        if (acc.gethero18() >= 2) eligible[count++] = 32;
        if (acc.gethero19() >= 2) eligible[count++] = 33;
        if (acc.gethero20() >= 2) eligible[count++] = 34;
        if (acc.getInfo_kerep_poly() >= 2) eligible[count++] = 35;
        if (acc.getInfo_jowo_poly() >= 2) eligible[count++] = 36;
        return count;
    }

    // Select an unbiased, uniformly ordered prefix with no List, boxing, or temporary shuffle copy.
    // Partial Fisher-Yates: all ordered pairs/quads have equal probability.
    private static void shufflePrefix(int[] cards, int length, int selected) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < selected; i++) {
            int j = i + random.nextInt(length - i);
            int tmp = cards[i];
            cards[i] = cards[j];
            cards[j] = tmp;
        }
    }
}
