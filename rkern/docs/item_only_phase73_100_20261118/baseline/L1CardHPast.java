package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 영웅 합성 대상 카드 자동 선택 시스템
 */
public class L1CardHPast {
    
    public static void L1PastSelct(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        
        // 현재 소지 중인 36종 영웅 카드의 수량을 배열로 로드
        int[] cardCounts = new int[37]; 
        loadCardCounts(acc, cardCounts);

        // 수량이 2장 이상인 카드(중복 소지)의 번호만 추출
        List<Integer> eligibleIndices = new ArrayList<>();
        for (int i = 1; i <= 36; i++) {
            if (cardCounts[i] >= 2) {
                eligibleIndices.add(i);
            }
        }

        //  중복 카드가 4종류 미만이면 실행 불가
        if (eligibleIndices.size() < 4) {
            SC_SystemMessage.send(pc, "보유한 카드가 4종류 이상이어야 자동 등록이 가능합니다.");
            return;
        }

        Collections.shuffle(eligibleIndices);

        // 앞의 4개를 선택하여 계정 데이터에 즉시 세팅
        acc.setInfo_card_a(eligibleIndices.get(0));
        acc.setInfo_card_b(eligibleIndices.get(1));
        acc.setInfo_card_c(eligibleIndices.get(2));
        acc.setInfo_card_d(eligibleIndices.get(3));

        // UI 업데이트
        L1CardA.cardlist(pc);
    }

    /**
     * 계정에서 36종의 영웅 카드 정보를 배열에 담음
     */
    private static void loadCardCounts(Account acc, int[] counts) {
        counts[1]  = acc.getInfo_heaven_poly();
        counts[2]  = acc.gethero01();
        counts[3]  = acc.gethero02();
        counts[4]  = acc.gethero03();
        counts[5]  = acc.gethero04();
        counts[6]  = acc.gethero13();
        counts[7]  = acc.gethero14();
        counts[8]  = acc.gethero05();
        counts[9]  = acc.gethero06();
        counts[10] = acc.gethero07();
        counts[11] = acc.getInfo_assm_poly();
        counts[12] = acc.gethero08();
        counts[13] = acc.gethero09();
        counts[14] = acc.getInfo_unik_poly();
        counts[15] = acc.gethero10();
        counts[16] = acc.getInfo_unir_poly();
        counts[17] = acc.getInfo_unim_poly();
        counts[18] = acc.gethero11();
        counts[19] = acc.getInfo_val_poly();
        counts[20] = acc.getInfo_larry_poly();
        counts[21] = acc.getInfo_gunter_poly();
        counts[22] = acc.getInfo_depor_poly();
        counts[23] = acc.getInfo_deathred_poly();
        counts[24] = acc.getInfo_ecil_poly();
        counts[25] = acc.gethero12();
        counts[26] = acc.gethero15();
        counts[27] = acc.gethero16();
        counts[28] = acc.getInfo_yozil_poly();
        counts[29] = acc.getInfo_owen_poly();
        counts[30] = acc.getInfo_helv_poly();
        counts[31] = acc.gethero17();
        counts[32] = acc.gethero18();
        counts[33] = acc.gethero19();
        counts[34] = acc.gethero20();
        counts[35] = acc.getInfo_kerep_poly();
        counts[36] = acc.getInfo_jowo_poly();
    }
}