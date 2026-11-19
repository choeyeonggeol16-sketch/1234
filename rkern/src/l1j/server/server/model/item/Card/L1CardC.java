package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 신화 합성 선택 카드 리스트 출력 UI
 */
public class L1CardC {

    // [성별(0:남, 1:여)][카드ID-201] 매핑 테이블 (211번까지 총 11종)
    private static final String[][] IMAGE_MAP = {
        { "145", "2859", "2863", "2867", "2871", "2875", "2877", "141", "149", "151", "71" }, // 남성용
        { "147", "2861", "2865", "2869", "2873", "2875", "2877", "143", "149", "151", "73" }  // 여성용
    };

    private static final String[] MYTH_MALE_OFF = {
        "144", "2858", "2862", "2866", "2870", "2874", "2876", "140",
        "148", "150"
    };

    private static final String[] MYTH_MALE_ON = {
        "145", "2859", "2863", "2867", "2871", "2875", "2877", "141",
        "149", "151"
    };

    private static final String[] MYTH_FEMALE_OFF = {
        "146", "2860", "2864", "2868", "2872", "2874", "2876", "142",
        "148", "150"
    };

    private static final String[] MYTH_FEMALE_ON = {
        "147", "2861", "2865", "2869", "2873", "2875", "2877", "143",
        "149", "151"
    };

    public static void cardlist(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        int sex = (pc.get_sex() == 0) ? 0 : 1; // 남:0, 여:1
        ArrayList<String> args = new ArrayList<>(28);

        // 선택된 4장의 카드 ID (201~211)
        int selectedA = acc.getInfo_card_a();
        int selectedB = acc.getInfo_card_b();
        int selectedC = acc.getInfo_card_c();
        int selectedD = acc.getInfo_card_d();

        // 상단 이미지 출력 (패딩 + 카드 A, B, C)
        args.add("174"); // 패딩
        args.add(getCardImg(selectedA, sex)); // 카드 A
        args.add(getCardImg(selectedB, sex)); // 카드 B
        args.add(getCardImg(selectedC, sex)); // 카드 C

        // 10종 신화 카드 보유 상태 아이콘 (활성/비활성)
        int[] mythCounts = {
            acc.getInfo_class_poly(), acc.getInfo_ki_poly(), acc.getInfo_el_poly(), acc.getInfo_wi_poly(), acc.getInfo_da_poly(),
            acc.getInfo_wa_poly(), acc.getInfo_drag_poly(), acc.getInfo_il_poly(), acc.getInfo_dsn_poly(), acc.getInfo_bapo_poly()
        };

        for (int i = 0; i < mythCounts.length; i++) {
            if (sex == 0) args.add(mythCounts[i] < 2 ? MYTH_MALE_OFF[i] : MYTH_MALE_ON[i]);
            else          args.add(mythCounts[i] < 2 ? MYTH_FEMALE_OFF[i] : MYTH_FEMALE_ON[i]);
        }

        // 10종 신화 카드 소지 장수 텍스트
        for (int count : mythCounts) {
            args.add(count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]");
        }

        // 마무리 (카드 D 이미지 + 비용 + 기르타스 아이콘 + 기르타스 장수)
        args.add(getCardImg(selectedD, sex)); // 카드 D
        args.add(Config.myth_cost + "원"); // 합성 비용
        
        // 기르타스(유일) 보유 정보
        int girtasCount = acc.getmyth01();
        if (sex == 0) args.add(girtasCount < 2 ? "70" : "71");
        else          args.add(girtasCount < 2 ? "72" : "73");
        
        args.add(girtasCount == 0 ? "[ 0장 ]" : "[ " + girtasCount + "장 ]");

        // 패킷 전송 (hallo1 UI)
        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007hallo1", args);
    }

    /** 카드 ID와 성별에 따른 이미지 ID 반환 */
    private static String getCardImg(int cardId, int sex) {
        int index = cardId - 201; 
        if (index < 0 || index >= IMAGE_MAP[sex].length) return "174";
        return IMAGE_MAP[sex][index];
    }
}