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

    private static final String[] LEGEND_OFF = {
        "162", "156", "158", "160", "164", "166", "168", "170",
        "172", "44", "46", "48", "50", "54", "56", "58",
        "60", "62", "64", "68", "42", "52", "66"
    };
    private static final String[] LEGEND_ON = {
        "163", "157", "159", "161", "165", "167", "169", "171",
        "173", "45", "47", "49", "51", "55", "57", "59",
        "61", "63", "65", "69", "43", "53", "67"
    };

    public static void cardlist(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        ArrayList<String> args = new ArrayList<>(62);

        // 선택된 2장의 카드 ID (aa, bb)
        int aa = acc.getInfo_card_a();
        int bb = acc.getInfo_card_b();

        // 상단 이미지 출력 (패딩 + 카드 A + 카드 B + 패딩)
        args.add("174"); 
        args.add(getImageId(aa)); 
        args.add(getImageId(bb)); 
        args.add("174");

        // 23종 전설 카드 보유 상태 아이콘 (활성/비활성)
        int[] legendCounts = {
            acc.getInfo_ds_poly(), acc.getInfo_dru_poly(), acc.getInfo_ga_poly(), acc.getInfo_ger_poly(), acc.getInfo_dev_poly(),
            acc.getInfo_pen_poly(), acc.getInfo_atun_poly(), acc.getInfo_moon_poly(), acc.getInfo_mar_poly(), acc.getlegend01(),
            acc.getlegend12(), acc.getlegend02(), acc.getlegend03(), acc.getlegend04(), acc.getlegend05(),
            acc.getlegend06(), acc.getlegend07(), acc.getlegend08(), acc.getlegend09(), acc.getlegend10(),
            acc.getlegend11(), acc.getlegend13(), acc.getlegend14()
        };

        for (int i = 0; i < legendCounts.length; i++) {
            args.add(legendCounts[i] < 2 ? LEGEND_OFF[i] : LEGEND_ON[i]);
        }

        // 전설 카드 소지 장수 텍스트 (아이콘 출력분 23종 + 추가 11종 총 34종)
        // 앞의 23종 텍스트 추가
        for (int count : legendCounts) {
            args.add(count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]");
        }
        
        // 추가 11종 (legend15 ~ legend25) 텍스트 추가
        args.add(countText(acc.getlegend15()));
        args.add(countText(acc.getlegend16()));
        args.add(countText(acc.getlegend17()));
        args.add(countText(acc.getlegend18()));
        args.add(countText(acc.getlegend19()));
        args.add(countText(acc.getlegend20()));
        args.add(countText(acc.getlegend21()));
        args.add(countText(acc.getlegend22()));
        args.add(countText(acc.getlegend23()));
        args.add(countText(acc.getlegend24()));
        args.add(countText(acc.getlegend25()));

        // 하단 비용 처리
        args.add(Config.legend_cost + "원");

        // 패킷 전송
        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007rudolph1", args);
    }

    private static String countText(int count) {
        return count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]";
    }

    /** 카드 ID(101~123)에 따른 이미지 ID 반환 헬퍼 */
    private static String getImageId(int cardId) {
        int index = cardId - 101; // 101번이 index 0
        if (index < 0 || index >= IMAGE_MAP.length) return "174";
        return IMAGE_MAP[index];
    }
}