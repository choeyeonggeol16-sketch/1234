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

    public static void cardlist(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        int sex = (pc.get_sex() == 0) ? 0 : 1; // 남:0, 여:1
        ArrayList<String> args = new ArrayList<>();

        // 선택된 4장의 카드 ID (201~211)
        int[] selected = { acc.getInfo_card_a(), acc.getInfo_card_b(), acc.getInfo_card_c(), acc.getInfo_card_d() };

        // 상단 이미지 출력 (패딩 + 카드 A, B, C)
        args.add("174"); // 패딩
        args.add(getCardImg(selected[0], sex)); // 카드 A
        args.add(getCardImg(selected[1], sex)); // 카드 B
        args.add(getCardImg(selected[2], sex)); // 카드 C

        // 10종 신화 카드 보유 상태 아이콘 (활성/비활성)
        int[][] mythData = {
            { acc.getInfo_class_poly(), 144, 145, 146, 147 }, // 군주
            { acc.getInfo_ki_poly(),    2858, 2859, 2860, 2861 }, // 기사
            { acc.getInfo_el_poly(),    2862, 2863, 2864, 2865 }, // 요정
            { acc.getInfo_wi_poly(),    2866, 2867, 2868, 2869 }, // 법사
            { acc.getInfo_da_poly(),    2870, 2871, 2872, 2873 }, // 다엘
            { acc.getInfo_wa_poly(),    2874, 2875, 2874, 2875 }, // 전사 (공용)
            { acc.getInfo_drag_poly(),  2876, 2877, 2876, 2877 }, // 용기사 (공용)
            { acc.getInfo_il_poly(),    140, 141, 142, 143 },     // 환술사
            { acc.getInfo_dsn_poly(),   148, 149, 148, 149 },     // 데스나이트 (공용)
            { acc.getInfo_bapo_poly(),  150, 151, 150, 151 }      // 바포메트 (공용)
        };

        for (int[] data : mythData) {
            if (sex == 0) args.add(data[0] < 2 ? String.valueOf(data[1]) : String.valueOf(data[2]));
            else          args.add(data[0] < 2 ? String.valueOf(data[3]) : String.valueOf(data[4]));
        }

        // 10종 신화 카드 소지 장수 텍스트
        for (int[] data : mythData) {
            args.add(data[0] == 0 ? "[ 0장 ]" : "[ " + data[0] + "장 ]");
        }

        // 마무리 (카드 D 이미지 + 비용 + 기르타스 아이콘 + 기르타스 장수)
        args.add(getCardImg(selected[3], sex)); // 카드 D
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