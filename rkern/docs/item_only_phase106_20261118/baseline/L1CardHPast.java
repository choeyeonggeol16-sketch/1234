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
        
        // Read all 36 counters in the same order; retain only eligible IDs.
        // The temporary int[37] snapshot was not needed after this filtering pass.
        List<Integer> eligibleIndices = new ArrayList<>(36);
        loadEligibleIndices(acc, eligibleIndices);

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
    private static void loadEligibleIndices(Account acc, List<Integer> eligible) {
        if (acc.getInfo_heaven_poly() >= 2) eligible.add(1);
        if (acc.gethero01() >= 2) eligible.add(2);
        if (acc.gethero02() >= 2) eligible.add(3);
        if (acc.gethero03() >= 2) eligible.add(4);
        if (acc.gethero04() >= 2) eligible.add(5);
        if (acc.gethero13() >= 2) eligible.add(6);
        if (acc.gethero14() >= 2) eligible.add(7);
        if (acc.gethero05() >= 2) eligible.add(8);
        if (acc.gethero06() >= 2) eligible.add(9);
        if (acc.gethero07() >= 2) eligible.add(10);
        if (acc.getInfo_assm_poly() >= 2) eligible.add(11);
        if (acc.gethero08() >= 2) eligible.add(12);
        if (acc.gethero09() >= 2) eligible.add(13);
        if (acc.getInfo_unik_poly() >= 2) eligible.add(14);
        if (acc.gethero10() >= 2) eligible.add(15);
        if (acc.getInfo_unir_poly() >= 2) eligible.add(16);
        if (acc.getInfo_unim_poly() >= 2) eligible.add(17);
        if (acc.gethero11() >= 2) eligible.add(18);
        if (acc.getInfo_val_poly() >= 2) eligible.add(19);
        if (acc.getInfo_larry_poly() >= 2) eligible.add(20);
        if (acc.getInfo_gunter_poly() >= 2) eligible.add(21);
        if (acc.getInfo_depor_poly() >= 2) eligible.add(22);
        if (acc.getInfo_deathred_poly() >= 2) eligible.add(23);
        if (acc.getInfo_ecil_poly() >= 2) eligible.add(24);
        if (acc.gethero12() >= 2) eligible.add(25);
        if (acc.gethero15() >= 2) eligible.add(26);
        if (acc.gethero16() >= 2) eligible.add(27);
        if (acc.getInfo_yozil_poly() >= 2) eligible.add(28);
        if (acc.getInfo_owen_poly() >= 2) eligible.add(29);
        if (acc.getInfo_helv_poly() >= 2) eligible.add(30);
        if (acc.gethero17() >= 2) eligible.add(31);
        if (acc.gethero18() >= 2) eligible.add(32);
        if (acc.gethero19() >= 2) eligible.add(33);
        if (acc.gethero20() >= 2) eligible.add(34);
        if (acc.getInfo_kerep_poly() >= 2) eligible.add(35);
        if (acc.getInfo_jowo_poly() >= 2) eligible.add(36);
    }
}