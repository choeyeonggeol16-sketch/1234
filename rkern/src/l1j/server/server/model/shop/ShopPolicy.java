package l1j.server.server.model.shop;

import l1j.server.Config;
import l1j.server.server.model.item.L1ItemId;

enum ShopPolicy {
    ADENA(L1ItemId.ADENA, 2_000_000_000, null, null, Delivery.CONFIGURED),
    STOCK(L1ItemId.ADENA, 2_000_000_000, null, null, Delivery.CONFIGURED),
    FEATHER(41159, 0, null, "픽시의 깃털이 부족합니다.", Delivery.CONFIGURED),
    MARK(3000032, 50_000, "한번에 50,000개 이상 사용할 수 없습니다.", "징표가 부족합니다.", Delivery.PLAIN),
    BERRY(41302, 1_000_000_000, "베리는 한번에 10억개 이상 사용할 수 없습니다.", "베리가 부족합니다.", Delivery.PLAIN),
    PANDORA(90738, 10_000_000, null, "구입: 판도라의 증서가 부족 합니다.", Delivery.PLAIN),
    ORIM(3000246, 10_000_000, null, "오림의 가넷이 부족 합니다.", Delivery.CONFIGURED),
    HARDIN(3000520, 10_000_000, null, "하딘의 가넷이 부족 합니다.", Delivery.PLAIN),
    GUNTER(3000367, 10_000_000, null, "군터의 인장이 부족 합니다.", Delivery.PLAIN),
    CHECK(400254, 1_000_000_000, "한번에 10억 이상 사용할 수 없습니다.", "아데나수표(1억)이 부족 합니다.", Delivery.CONFIGURED),
    CRANE(3000156, 10_000_000, null, "종이학이 부족 합니다.", Delivery.PLAIN),
    CRANE_EQUIPMENT(3000156, 10_000_000, null, "\\f2미리보기 입니다. 구매는 (메티스)에게 문의 바랍니다.", Delivery.CONFIGURED),
    CLAN(0, 10_000_000, null, "혈맹포인트가 부족 합니다.", Delivery.CONFIGURED),
    COLOSSEUM(710, 10_000_000, null, "구입: 콜로세움 코인이 부족 합니다.", Delivery.CONFIGURED),
    ORB(3000180, 10_000_000, null, "여의주가 부족 합니다.", Delivery.UNIDENTIFIED),
    BELL(812, 10_000_000, null, "루돌프의 종이 부족 합니다.", Delivery.UNIDENTIFIED),
    TAM(0, 9_000_000, "TAM은 한번에 '900'만개 이상 사용할 수 없습니다.", "TAM이 부족합니다.", Delivery.PLAIN),
    PACKAGE(0, 2_000_000, "한번에 200만개 이상 사용할 수 없습니다.", "\\aA[GM]: \\aG추가 구입은 \\aA'메티스'\\aG 문의주세요.", Delivery.PACKAGED),
    EQUIPMENT(747, 2_000_000, "한번에 200만개 이상 사용할 수 없습니다.", "장비코인이 부족합니다.", Delivery.PACKAGED),
    MOON(410093, 1_000, "만월의 정기는 한번에 1,000개 이상 사용할 수 없습니다.", null, Delivery.PLAIN);

    enum Delivery { CONFIGURED, PLAIN, UNIDENTIFIED, PACKAGED }

    private final int currency;
    private final int limit;
    private final String overLimit;
    final String insufficient;
    final Delivery delivery;

    ShopPolicy(int currency, int limit, String overLimit, String insufficient, Delivery delivery) {
        this.currency = currency;
        this.limit = limit;
        this.overLimit = overLimit;
        this.insufficient = insufficient;
        this.delivery = delivery;
    }

    int currency(int npcId) {
        return this == PACKAGE ? npcId - 5_299_999 : currency;
    }

    int limit() {
        return this == FEATHER ? Config.FEATHER_SHOP_NUM : limit;
    }

    String overLimit() {
        if (this == FEATHER) return "픽시의 깃털은 한번에 " + limit() + "개 이상 사용할 수 없습니다.";
        return overLimit != null ? overLimit : "한번에 1,000만개 이상 사용할 수 없습니다.";
    }

    int buybackCurrency(int npcId) {
        return this == PACKAGE || this == BERRY ? currency(npcId) : L1ItemId.ADENA;
    }

    boolean capsBuyback(int npcId) {
        return currency(npcId) == buybackCurrency(npcId) && !isRace(npcId);
    }

    static boolean isRace(int npcId) {
        return npcId == 70035 || npcId == 70041 || npcId == 70042;
    }

    static ShopPolicy forNpc(int id) {
        if (id >= 4000001 && id <= 4000061) return STOCK;
        if (id >= 66110 && id <= 66120) return CLAN;
        if (id >= 6100000 && id <= 6100035) return PACKAGE;
        if (id >= 5000001 && id <= 5000004 || id >= 5000007 && id <= 5000018) return CRANE_EQUIPMENT;
        return switch (id) {
            case 200060, 200061, 200062, 200063, 523, 5000000, 900047, 5072, 5073, 519, 224,
                    7310101, 7310113, 7320124, 7310200, 7310201, 7310202 -> FEATHER;
            case 5000006 -> MARK;
            case 7000077 -> BERRY;
            case 91245 -> PANDORA;
            case 70017, 460000360, 460000361 -> ORIM;
            case 7320250 -> HARDIN;
            case 8500140 -> GUNTER;
            case 200005, 7320222, 7320223, 6200010 -> CHECK;
            case 5, 520, 7310119, 7310120, 7310124, 7310126 -> CRANE;
            case 2998, 2999 -> COLOSSEUM;
            case 7310128 -> ORB;
            case 521 -> BELL;
            case 7200002 -> TAM;
            case 7320087, 7320122, 7320123, 7320157 -> STOCK;
            case 2, 3, 4 -> EQUIPMENT;
            case 900107 -> MOON;
            default -> ADENA;
        };
    }
}
