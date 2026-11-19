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

    public static void cardlist(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        ArrayList<String> args = new ArrayList<>();

        // 선택된 4장의 카드 ID 가져오기
        int[] selected = {
            acc.getInfo_card_a(), 
            acc.getInfo_card_b(), 
            acc.getInfo_card_c(), 
            acc.getInfo_card_d()
        };

        // 상단 이미지 출력 (패딩 + 카드 A, B, C)
        args.add("174"); // 패딩
        args.add(getImageId(selected[0])); // 카드 A
        args.add(getImageId(selected[1])); // 카드 B
        args.add(getImageId(selected[2])); // 카드 C

        // 36종 영웅 카드 보유 상태 아이콘 (활성/비활성)
        int[][] heroData = {
            {acc.getInfo_heaven_poly(), 176, 177}, {acc.gethero01(), 26, 27}, {acc.gethero02(), 30, 31},
            {acc.gethero03(), 4, 5}, {acc.gethero04(), 6, 7}, {acc.gethero13(), 8, 9},
            {acc.gethero14(), 10, 11}, {acc.gethero05(), 0, 1}, {acc.gethero06(), 2, 3},
            {acc.gethero07(), 12, 13}, {acc.getInfo_assm_poly(), 190, 191}, {acc.gethero08(), 14, 15},
            {acc.gethero09(), 16, 17}, {acc.getInfo_unik_poly(), 196, 197}, {acc.gethero10(), 32, 33},
            {acc.getInfo_unir_poly(), 200, 201}, {acc.getInfo_unim_poly(), 198, 199}, {acc.gethero11(), 34, 35},
            {acc.getInfo_val_poly(), 178, 179}, {acc.getInfo_larry_poly(), 180, 181}, {acc.getInfo_gunter_poly(), 182, 183},
            {acc.getInfo_depor_poly(), 184, 185}, {acc.getInfo_deathred_poly(), 186, 187}, {acc.getInfo_ecil_poly(), 188, 189},
            {acc.gethero12(), 38, 39}, {acc.gethero15(), 20, 21}, {acc.gethero16(), 24, 25},
            {acc.getInfo_yozil_poly(), 202, 203}, {acc.getInfo_owen_poly(), 204, 205}, {acc.getInfo_helv_poly(), 206, 207},
            {acc.gethero17(), 18, 19}, {acc.gethero18(), 22, 23}, {acc.gethero19(), 28, 29},
            {acc.gethero20(), 36, 37}, {acc.getInfo_kerep_poly(), 192, 193}, {acc.getInfo_jowo_poly(), 194, 195}
        };

        for (int[] hero : heroData) {
            args.add(hero[0] < 2 ? String.valueOf(hero[1]) : String.valueOf(hero[2]));
        }

        // 36종 영웅 카드 소지 장수 텍스트
        for (int[] hero : heroData) {
            args.add(hero[0] == 0 ? "[ 0장 ]" : "[ " + hero[0] + "장 ]");
        }

        // 하단 마무리 
        args.add(getImageId(selected[3])); // 카드 D
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