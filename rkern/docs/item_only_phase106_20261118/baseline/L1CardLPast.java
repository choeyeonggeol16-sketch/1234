package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 전설 합성 대상 카드 자동 선택 시스템 
 */
public class L1CardLPast {

    public static void L1PastSelct(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        
        // 현재 소지 중인 23종 전설 카드의 수량을 확인하여 리스트에 담음 (101~123번 매핑)
        List<Integer> eligibleCards = new ArrayList<>(23);
        
        loadEligibleCards(acc, eligibleCards);

        // 중복 보유(2장 이상)한 카드가 2종류 미만이면 실행 불가
        if (eligibleCards.size() < 2) {
            SC_SystemMessage.send(pc, "보유한 카드가 2종류 이상이어야 자동 등록이 가능합니다.");
            return;
        }

        // 기존 선택 정보 초기화 
        acc.setInfo_card_a(0);
        acc.setInfo_card_b(0);
        acc.setInfo_card_c(0);
        acc.setInfo_card_d(0);

        // 리스트를 무작위로 섞고 상위 2개를 선택 
        Collections.shuffle(eligibleCards);

        acc.setInfo_card_a(eligibleCards.get(0));
        acc.setInfo_card_b(eligibleCards.get(1));

        // UI 업데이트 (B 타입 UI 호출)
        L1CardB.cardlist(pc);
    }

    /**
     * 계정에서 전설 카드 정보를 확인하여 2장 이상인 것만 리스트에 추가
     */
    private static void loadEligibleCards(Account acc, List<Integer> list) {
        if (acc.getInfo_ds_poly() >= 2)      list.add(101);
        if (acc.getInfo_dru_poly() >= 2)     list.add(102);
        if (acc.getInfo_ga_poly() >= 2)      list.add(103);
        if (acc.getInfo_ger_poly() >= 2)     list.add(104);
        if (acc.getInfo_dev_poly() >= 2)     list.add(105);
        if (acc.getInfo_pen_poly() >= 2)     list.add(106);
        if (acc.getInfo_atun_poly() >= 2)    list.add(107);
        if (acc.getInfo_moon_poly() >= 2)    list.add(108);
        if (acc.getInfo_mar_poly() >= 2)     list.add(109);
        if (acc.getlegend01() >= 2)          list.add(110);
        if (acc.getlegend12() >= 2)          list.add(111);
        if (acc.getlegend02() >= 2)          list.add(112);
        if (acc.getlegend03() >= 2)          list.add(113);
        if (acc.getlegend04() >= 2)          list.add(114);
        if (acc.getlegend05() >= 2)          list.add(115);
        if (acc.getlegend06() >= 2)          list.add(116);
        if (acc.getlegend07() >= 2)          list.add(117);
        if (acc.getlegend08() >= 2)          list.add(118);
        if (acc.getlegend09() >= 2)          list.add(119);
        if (acc.getlegend10() >= 2)          list.add(120);
        if (acc.getlegend11() >= 2)          list.add(121);
        if (acc.getlegend13() >= 2)          list.add(122);
        if (acc.getlegend14() >= 2)          list.add(123);
    }
}