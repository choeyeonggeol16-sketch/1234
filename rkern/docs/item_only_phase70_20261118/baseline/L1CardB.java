package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 전설 합성 선택 카드 리스트 출력 UI
 */
public class L1CardB {

    private static final String[] IMAGE_MAP = {
        "163", "157", "159", "161", "165", "167", "169", "171", "173", // 101~109
        "45",  "47",  "49",  "51",  "55",  "57",  "59",  "61",  "63",  "65",  // 110~119
        "69",  "43",  "53",  "67"                                          // 120~123
    };

    public static void cardlist(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        ArrayList<String> args = new ArrayList<>();

        // 선택된 2장의 카드 ID (aa, bb)
        int aa = acc.getInfo_card_a();
        int bb = acc.getInfo_card_b();

        // 상단 이미지 출력 (패딩 + 카드 A + 카드 B + 패딩)
        args.add("174"); 
        args.add(getImageId(aa)); 
        args.add(getImageId(bb)); 
        args.add("174");

        // 23종 전설 카드 보유 상태 아이콘 (활성/비활성)
        int[][] legendData = {
            {acc.getInfo_ds_poly(), 162, 163}, {acc.getInfo_dru_poly(), 156, 157}, {acc.getInfo_ga_poly(), 158, 159},
            {acc.getInfo_ger_poly(), 160, 161}, {acc.getInfo_dev_poly(), 164, 165}, {acc.getInfo_pen_poly(), 166, 167},
            {acc.getInfo_atun_poly(), 168, 169}, {acc.getInfo_moon_poly(), 170, 171}, {acc.getInfo_mar_poly(), 172, 173},
            {acc.getlegend01(), 44, 45}, {acc.getlegend12(), 46, 47}, {acc.getlegend02(), 48, 49},
            {acc.getlegend03(), 50, 51}, {acc.getlegend04(), 54, 55}, {acc.getlegend05(), 56, 57},
            {acc.getlegend06(), 58, 59}, {acc.getlegend07(), 60, 61}, {acc.getlegend08(), 62, 63},
            {acc.getlegend09(), 64, 65}, {acc.getlegend10(), 68, 69}, {acc.getlegend11(), 42, 43},
            {acc.getlegend13(), 52, 53}, {acc.getlegend14(), 66, 67}
        };

        for (int[] data : legendData) {
            args.add(data[0] < 2 ? String.valueOf(data[1]) : String.valueOf(data[2]));
        }

        // 전설 카드 소지 장수 텍스트 (아이콘 출력분 23종 + 추가 11종 총 34종)
        // 앞의 23종 텍스트 추가
        for (int[] data : legendData) {
            args.add(data[0] == 0 ? "[ 0장 ]" : "[ " + data[0] + "장 ]");
        }
        
        // 추가 11종 (legend15 ~ legend25) 텍스트 추가
        int[] extraCounts = {
            acc.getlegend15(), acc.getlegend16(), acc.getlegend17(), acc.getlegend18(), acc.getlegend19(),
            acc.getlegend20(), acc.getlegend21(), acc.getlegend22(), acc.getlegend23(), acc.getlegend24(), acc.getlegend25()
        };
        for (int count : extraCounts) {
            args.add(count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]");
        }

        // 하단 비용 처리
        args.add(Config.legend_cost + "원");

        // 패킷 전송
        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007rudolph1", args);
    }

    /** 카드 ID(101~123)에 따른 이미지 ID 반환 헬퍼 */
    private static String getImageId(int cardId) {
        int index = cardId - 101; // 101번이 index 0
        if (index < 0 || index >= IMAGE_MAP.length) return "174";
        return IMAGE_MAP[index];
    }
}