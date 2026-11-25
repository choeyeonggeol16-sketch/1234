package l1j.server.server.model.item.Card;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Random;
import java.util.function.Consumer;
import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.MessageUtil;

/**
 * 전설/신화 카드 합성 시스템
 */
public class L1CardAlchemy1 {
    private static final Random _random = new Random(System.nanoTime());

    // 카드 ID별 계정 데이터 차감 매핑
    @SuppressWarnings("unchecked")
    private static final Consumer<Account>[] _cardConsumers =
            (Consumer<Account>[]) new Consumer[34];

    private static Consumer<Account> consumerFor(int cardId) {
        final int index = cardId - 101;
        return index >= 0 && index < _cardConsumers.length ? _cardConsumers[index] : null;
    }

    // Revalidate the collection at commit entry: a selected ID alone does not
    // prove the account still owns a spare copy (one copy is retained in collection).
    private static final CardAlchemyReceipt.CardCount CARD_COUNT = L1CardAlchemy1::copiesFor;

    private static int copiesFor(Account acc, int cardId) {
        return switch (cardId) {
            case 101 -> acc.getInfo_ds_poly();
            case 102 -> acc.getInfo_dru_poly();
            case 103 -> acc.getInfo_ga_poly();
            case 104 -> acc.getInfo_ger_poly();
            case 105 -> acc.getInfo_dev_poly();
            case 106 -> acc.getInfo_pen_poly();
            case 107 -> acc.getInfo_atun_poly();
            case 108 -> acc.getInfo_moon_poly();
            case 109 -> acc.getInfo_mar_poly();
            case 110 -> acc.getlegend01();
            case 111 -> acc.getlegend12();
            case 112 -> acc.getlegend02();
            case 113 -> acc.getlegend03();
            case 114 -> acc.getlegend04();
            case 115 -> acc.getlegend05();
            case 116 -> acc.getlegend06();
            case 117 -> acc.getlegend07();
            case 118 -> acc.getlegend08();
            case 119 -> acc.getlegend09();
            case 120 -> acc.getlegend10();
            case 121 -> acc.getlegend11();
            case 122 -> acc.getlegend13();
            case 123 -> acc.getlegend14();
            case 124 -> acc.getlegend15();
            case 125 -> acc.getlegend16();
            case 126 -> acc.getlegend17();
            case 127 -> acc.getlegend18();
            case 128 -> acc.getlegend19();
            case 129 -> acc.getlegend20();
            case 130 -> acc.getlegend21();
            case 131 -> acc.getlegend22();
            case 132 -> acc.getlegend23();
            case 133 -> acc.getlegend24();
            case 134 -> acc.getlegend25();
            default -> 0;
        };
    }

    // Schema-backed whitelist. Indexed by card ID; never derived from packet text.
    private static final String[] CARD_COLUMNS = {
        "info_ds_poly", "info_dru_poly", "info_ga_poly", "info_ger_poly", "info_dev_poly", "info_pen_poly",
        "info_atun_poly", "info_moon_poly", "info_mar_poly", "legend01", "legend12", "legend02",
        "legend03", "legend04", "legend05", "legend06", "legend07", "legend08",
        "legend09", "legend10", "legend11", "legend13", "legend14", "legend15",
        "legend16", "legend17", "legend18", "legend19", "legend20", "legend21",
        "legend22", "legend23", "legend24", "legend25"
    };

    static {
        // 전설 라인업
        _cardConsumers[0] = acc -> acc.setInfo_ds_poly(acc.getInfo_ds_poly() - 1);
        _cardConsumers[1] = acc -> acc.setInfo_dru_poly(acc.getInfo_dru_poly() - 1);
        _cardConsumers[2] = acc -> acc.setInfo_ga_poly(acc.getInfo_ga_poly() - 1);
        _cardConsumers[3] = acc -> acc.setInfo_ger_poly(acc.getInfo_ger_poly() - 1);
        _cardConsumers[4] = acc -> acc.setInfo_dev_poly(acc.getInfo_dev_poly() - 1);
        _cardConsumers[5] = acc -> acc.setInfo_pen_poly(acc.getInfo_pen_poly() - 1);
        _cardConsumers[6] = acc -> acc.setInfo_atun_poly(acc.getInfo_atun_poly() - 1);
        _cardConsumers[7] = acc -> acc.setInfo_moon_poly(acc.getInfo_moon_poly() - 1);
        _cardConsumers[8] = acc -> acc.setInfo_mar_poly(acc.getInfo_mar_poly() - 1);
        // 전설 추가 라인업 (번호 규칙성으로 매핑)
        _cardConsumers[9] = acc -> acc.setlegend01(acc.getlegend01() - 1);
        _cardConsumers[10] = acc -> acc.setlegend12(acc.getlegend12() - 1);
        _cardConsumers[11] = acc -> acc.setlegend02(acc.getlegend02() - 1);
        _cardConsumers[12] = acc -> acc.setlegend03(acc.getlegend03() - 1);
        _cardConsumers[13] = acc -> acc.setlegend04(acc.getlegend04() - 1);
        _cardConsumers[14] = acc -> acc.setlegend05(acc.getlegend05() - 1);
        _cardConsumers[15] = acc -> acc.setlegend06(acc.getlegend06() - 1);
        _cardConsumers[16] = acc -> acc.setlegend07(acc.getlegend07() - 1);
        _cardConsumers[17] = acc -> acc.setlegend08(acc.getlegend08() - 1);
        _cardConsumers[18] = acc -> acc.setlegend09(acc.getlegend09() - 1);
        _cardConsumers[19] = acc -> acc.setlegend10(acc.getlegend10() - 1);
        _cardConsumers[20] = acc -> acc.setlegend11(acc.getlegend11() - 1);
        _cardConsumers[21] = acc -> acc.setlegend13(acc.getlegend13() - 1);
        _cardConsumers[22] = acc -> acc.setlegend14(acc.getlegend14() - 1);
        _cardConsumers[23] = acc -> acc.setlegend15(acc.getlegend15() - 1);
        _cardConsumers[24] = acc -> acc.setlegend16(acc.getlegend16() - 1);
        _cardConsumers[25] = acc -> acc.setlegend17(acc.getlegend17() - 1);
        _cardConsumers[26] = acc -> acc.setlegend18(acc.getlegend18() - 1);
        _cardConsumers[27] = acc -> acc.setlegend19(acc.getlegend19() - 1);
        _cardConsumers[28] = acc -> acc.setlegend20(acc.getlegend20() - 1);
        _cardConsumers[29] = acc -> acc.setlegend21(acc.getlegend21() - 1);
        _cardConsumers[30] = acc -> acc.setlegend22(acc.getlegend22() - 1);
        _cardConsumers[31] = acc -> acc.setlegend23(acc.getlegend23() - 1);
        _cardConsumers[32] = acc -> acc.setlegend24(acc.getlegend24() - 1);
        _cardConsumers[33] = acc -> acc.setlegend25(acc.getlegend25() - 1);
    }

    public static boolean LegendAlchemy(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return false;

        Account acc = pc.getAccount();
        int cardA = acc.getInfo_card_a();
        int cardB = acc.getInfo_card_b();

        // 유효성 체크
        if (cardA == 0 || cardB == 0 || cardA == cardB
                || consumerFor(cardA) == null || consumerFor(cardB) == null
                || copiesFor(acc, cardA) <= 1 || copiesFor(acc, cardB) <= 1) {
            SC_SystemMessage.send(pc, "합성에 필요한 카드가 부족합니다.");
            return false;
        }
        if (!(pc.getInventory().HaveItemByClassId(40308, Config.legend_cost))) {
            SC_SystemMessage.send(pc, "합성에 필요한 아데나가 부족합니다.");
            return false;
        }
        if (!pc.getMap().isSafetyZone(pc.getLocation())) {
            SC_SystemMessage.send(pc, "안전한 지역에서만 사용할 수 있습니다.");
            return false;
        }

        // Same two RNG draws as the legacy successful synthesis path.
        final int rx2 = _random.nextInt(100) + 1;
        final int chance = Config.myth_per + (pc.전설합성확률증가 ? Config.myth_per_potion : 0);
        final boolean mythSuccess = rx2 <= chance;
        final ResultCard result = mythSuccess ? getMythCard(pc.get_sex()) : getLegendCard();
        return CardAlchemyReceipt.submit(pc, acc, Config.legend_cost, result.itemId,
                101, CARD_COLUMNS, _cardConsumers, CARD_COUNT,
                cardA, cardB, 0, 0, 2,
                () -> generateResult(pc, result, rx2, chance, mythSuccess));
    }

    private static void generateResult(L1PcInstance pc, ResultCard result,
            int rx2, int chance, boolean isMythSuccess) {
        // This receipt has committed. Retire the consumed probability bonus
        // before any optional send/broadcast can throw.
        pc.전설합성확률증가 = false;
        if (pc.isGm()) SC_SystemMessage.send(pc, "합성 확률: " + chance + "% (주사위: " + rx2 + ")");

        // Card counters, selections, adena and reward are already committed.
        SC_SkillSound.send(pc, pc.getId(), 21128);
        if (isMythSuccess) {
            SC_DisplayEffect.send(pc, 7);
            SC_DisplayEffect.send(pc, 22);
            MessageUtil.toAllPlayers("어느 아덴 용사 님께서 " + result.name + " 카드 합성에 성공하였습니다.");
            MessageUtil.greenMessageToAllPlayers("어느 아덴 용사 님께서 " + result.name + "카드 합성에 성공하였습니다.");
        } else {
            SC_DisplayEffect.send(pc, 21);
            SC_DisplayEffect.send(pc, 1);
        }
        sendResultTalk(pc, result, rx2, isMythSuccess);
    }

    // 신화 카드 데이터 (성별 구분)
    private static final ResultCard[] MALE_MYTH_CARDS = {
        new ResultCard("145", "[신화]군주", 875640296),
        new ResultCard("2859", "[신화]기사", 875640297),
        new ResultCard("2863", "[신화]요정", 875640298),
        new ResultCard("2867", "[신화]마법사", 875640299),
        new ResultCard("2871", "[신화]다크엘프", 875640300),
        new ResultCard("2875", "[신화]광전사", 875640301),
        new ResultCard("2877", "[신화]투사", 875640303),
        new ResultCard("141", "[신화]도플갱어", 875640302),
        new ResultCard("149", "[신화]데스나이트", 875640304),
        new ResultCard("151", "[신화]바포메트", 875640305),
        new ResultCard("71", "[신화]총사", 875640380)
    };

    private static final ResultCard[] FEMALE_MYTH_CARDS = {
        new ResultCard("147", "[신화]군주", 875640296),
        new ResultCard("2861", "[신화]기사", 875640297),
        new ResultCard("2865", "[신화]요정", 875640298),
        new ResultCard("2869", "[신화]마법사", 875640299),
        new ResultCard("2873", "[신화]다크엘프", 875640300),
        new ResultCard("2875", "[신화]전사", 875640301),
        new ResultCard("2877", "[신화]용기사", 875640303),
        new ResultCard("143", "[신화]도플갱어", 875640302),
        new ResultCard("149", "[신화]데스나이트", 875640304),
        new ResultCard("151", "[신화]바포메트", 875640305),
        new ResultCard("73", "[신화]총사", 875640380)
    };

    private static final ResultCard[] LEGEND_CARDS = {
        new ResultCard("163", "[전설]드래곤 슬레이어", 875640306),
        new ResultCard("157", "[전설]드루가", 875640307),
        new ResultCard("159", "[전설]가드리아", 875640308),
        new ResultCard("161", "[전설]게렝", 875640309),
        new ResultCard("165", "[전설]디바인 크루세이더", 875640310),
        new ResultCard("167", "[전설]팬텀 나이트", 875640311),
        new ResultCard("169", "[전설]아툰", 875640312),
        new ResultCard("171", "[전설]달의 질리언", 875640313),
        new ResultCard("173", "[전설]공작 마커스", 875640314),
        new ResultCard("43", "[전설]Mr.스모커", 875640365),
        new ResultCard("45", "[전설]경비병(창)대장", 875640366),
        new ResultCard("47", "[전설]경비병(활)대장", 875640367),
        new ResultCard("49", "[전설]과거의 나이트발드", 875640368),
        new ResultCard("51", "[전설]국왕 에오딘", 875640369),
        new ResultCard("53", "[전설]군단장 엑스터", 875640370),
        new ResultCard("55", "[전설]로데마이", 875640371),
        new ResultCard("57", "[전설]베리스", 875640372),
        new ResultCard("59", "[전설]암살의 아리아 울프", 875640373),
        new ResultCard("61", "[전설]젠", 875640374),
        new ResultCard("63", "[전설]지옥의 데스나이트", 875640375),
        new ResultCard("65", "[전설]샤르나", 875640376),
        new ResultCard("67", "[전설]하딘의 영혼", 875640377),
        new ResultCard("69", "[전설]화염의 데스나이트", 875640378)
    };

    private static ResultCard getMythCard(int sex) {
        final int index = _random.nextInt(11);
        return (sex == 0 ? MALE_MYTH_CARDS : FEMALE_MYTH_CARDS)[index];
    }

    // 전설 카드 데이터
    private static ResultCard getLegendCard() {
        final int index = _random.nextInt(23);
        return LEGEND_CARDS[index];
    }

    private static void sendResultTalk(L1PcInstance pc, ResultCard result, int rx2, boolean isMyth) {
        ArrayList<String> args = new ArrayList<>(62);
        Account acc = pc.getAccount();
        
        args.add(result.cardId); // 결과 카드 이미지
        args.add("174"); args.add("174"); args.add("174"); // 빈공간 패딩

        // 소지 카드 이미지 처리
        args.add(acc.getInfo_ds_poly() < 2 ? "162" : "163");
        args.add(acc.getInfo_dru_poly() < 2 ? "156" : "157");
        args.add(acc.getInfo_ga_poly() < 2 ? "158" : "159");
        args.add(acc.getInfo_ger_poly() < 2 ? "160" : "161");
        args.add(acc.getInfo_dev_poly() < 2 ? "164" : "165");
        args.add(acc.getInfo_pen_poly() < 2 ? "166" : "167");
        args.add(acc.getInfo_atun_poly() < 2 ? "168" : "169");
        args.add(acc.getInfo_moon_poly() < 2 ? "170" : "171");
        args.add(acc.getInfo_mar_poly() < 2 ? "172" : "173");
        args.add(acc.getlegend01() < 2 ? "44" : "45");
        args.add(acc.getlegend12() < 2 ? "46" : "47");
        args.add(acc.getlegend02() < 2 ? "48" : "49");
        args.add(acc.getlegend03() < 2 ? "50" : "51");
        args.add(acc.getlegend04() < 2 ? "54" : "55");
        args.add(acc.getlegend05() < 2 ? "56" : "57");
        args.add(acc.getlegend06() < 2 ? "58" : "59");
        args.add(acc.getlegend07() < 2 ? "60" : "61");
        args.add(acc.getlegend08() < 2 ? "62" : "63");
        args.add(acc.getlegend09() < 2 ? "64" : "65");
        args.add(acc.getlegend10() < 2 ? "68" : "69");
        args.add(acc.getlegend11() < 2 ? "42" : "43");
        args.add(acc.getlegend13() < 2 ? "52" : "53");
        args.add(acc.getlegend14() < 2 ? "66" : "67");

        // 수집 갯수 텍스트 처리
        args.add(countText(acc.getInfo_ds_poly()));
        args.add(countText(acc.getInfo_dru_poly()));
        args.add(countText(acc.getInfo_ga_poly()));
        args.add(countText(acc.getInfo_ger_poly()));
        args.add(countText(acc.getInfo_dev_poly()));
        args.add(countText(acc.getInfo_pen_poly()));
        args.add(countText(acc.getInfo_atun_poly()));
        args.add(countText(acc.getInfo_moon_poly()));
        args.add(countText(acc.getInfo_mar_poly()));
        args.add(countText(acc.getlegend01()));
        args.add(countText(acc.getlegend12()));
        args.add(countText(acc.getlegend02()));
        args.add(countText(acc.getlegend03()));
        args.add(countText(acc.getlegend04()));
        args.add(countText(acc.getlegend05()));
        args.add(countText(acc.getlegend06()));
        args.add(countText(acc.getlegend07()));
        args.add(countText(acc.getlegend08()));
        args.add(countText(acc.getlegend09()));
        args.add(countText(acc.getlegend10()));
        args.add(countText(acc.getlegend11()));
        args.add(countText(acc.getlegend13()));
        args.add(countText(acc.getlegend14()));
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

        args.add(Config.legend_cost + "원");

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007rudolph1", args);
        
        String color = isMyth ? "\\aH" : "\\aF";
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, color + result.name + " 카드를 획득하였습니다.");
        SC_ChatPacket.sendDirect(pc, pc, result.name + " 카드를 획득하였습니다.", 1);
    }


    // Keep the original zero-count text and formatting; avoid the per-result int[] snapshot.
    private static String countText(int count) {
        return count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]";
    }

    // 내부 데이터 클래스
    private static class ResultCard {
        final String cardId, name;
        final int itemId;
        ResultCard(String cardId, String name, int itemId) {
            this.cardId = cardId;
            this.name = name;
            this.itemId = itemId;
        }
    }
}