package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 영웅 합성 선택 카드 리스트 출력 UI
 */
public class L1CardA {

    // 1~36번 카드에 대응하는 클라이언트 이미지 번호 매핑
    private static final String[] IMAGE_MAP = {
        "174", // 0번
        "177", "27", "31", "5", "7", "9", "11", "1", "3", "13", // 1~10
        "191", "15", "17", "197", "33", "201", "199", "35", "179", "181", // 11~20
        "183", "185", "187", "189", "39", "21", "25", "203", "205", "207", // 21~30
        "19", "23", "29", "37", "193", "195" // 31~36
    };

    private static final String[] HERO_OFF = {
        "176", "26", "30", "4", "6", "8", "10", "0",
        "2", "12", "190", "14", "16", "196", "32", "200",
        "198", "34", "178", "180", "182", "184", "186", "188",
        "38", "20", "24", "202", "204", "206", "18", "22",
        "28", "36", "192", "194"
    };
    private static final String[] HERO_ON = {
        "177", "27", "31", "5", "7", "9", "11", "1",
        "3", "13", "191", "15", "17", "197", "33", "201",
        "199", "35", "179", "181", "183", "185", "187", "189",
        "39", "21", "25", "203", "205", "207", "19", "23",
        "29", "37", "193", "195"
    };

    public static void cardlist(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        ArrayList<String> args = new ArrayList<>(78);

        // 선택된 4장의 카드 ID 가져오기
        int selectedA = acc.getInfo_card_a();
        int selectedB = acc.getInfo_card_b();
        int selectedC = acc.getInfo_card_c();
        int selectedD = acc.getInfo_card_d();

        // 상단 이미지 출력 (패딩 + 카드 A, B, C)
        args.add("174"); // 패딩
        args.add(getImageId(selectedA)); // 카드 A
        args.add(getImageId(selectedB)); // 카드 B
        args.add(getImageId(selectedC)); // 카드 C

        // 36종 영웅 카드 보유 상태 아이콘 (활성/비활성)
        int[] heroCounts = {
            acc.getInfo_heaven_poly(), acc.gethero01(), acc.gethero02(), acc.gethero03(), acc.gethero04(),
            acc.gethero13(), acc.gethero14(), acc.gethero05(), acc.gethero06(), acc.gethero07(),
            acc.getInfo_assm_poly(), acc.gethero08(), acc.gethero09(), acc.getInfo_unik_poly(), acc.gethero10(),
            acc.getInfo_unir_poly(), acc.getInfo_unim_poly(), acc.gethero11(), acc.getInfo_val_poly(), acc.getInfo_larry_poly(),
            acc.getInfo_gunter_poly(), acc.getInfo_depor_poly(), acc.getInfo_deathred_poly(), acc.getInfo_ecil_poly(), acc.gethero12(),
            acc.gethero15(), acc.gethero16(), acc.getInfo_yozil_poly(), acc.getInfo_owen_poly(), acc.getInfo_helv_poly(),
            acc.gethero17(), acc.gethero18(), acc.gethero19(), acc.gethero20(), acc.getInfo_kerep_poly(),
            acc.getInfo_jowo_poly()
        };

        for (int i = 0; i < heroCounts.length; i++) {
            args.add(heroCounts[i] < 2 ? HERO_OFF[i] : HERO_ON[i]);
        }

        // 36종 영웅 카드 소지 장수 텍스트
        for (int count : heroCounts) {
            args.add(count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]");
        }

        // 하단 마무리 
        args.add(getImageId(selectedD)); // 카드 D
        args.add(Config.hero_cost + "원");

        // 패킷 전송
        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007hallo2", args);
    }

    /** 카드 ID에 따른 이미지 ID 반환 헬퍼 */
    private static String getImageId(int cardId) {
        if (cardId <= 0 || cardId >= IMAGE_MAP.length) return "174";
        return IMAGE_MAP[cardId];
    }
}