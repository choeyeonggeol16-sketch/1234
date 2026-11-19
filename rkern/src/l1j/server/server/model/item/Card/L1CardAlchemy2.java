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
 * 영웅 카드 합성 시스템 
 */
public class L1CardAlchemy2 {
    private static final Random _random = new Random(System.nanoTime());

    // 카드 ID별 계정 데이터 차감 매핑 (36종 영웅 카드)
    @SuppressWarnings("unchecked")
    private static final Consumer<Account>[] _cardConsumers =
            (Consumer<Account>[]) new Consumer[36];

    private static Consumer<Account> consumerFor(int cardId) {
        final int index = cardId - 1;
        return index >= 0 && index < _cardConsumers.length ? _cardConsumers[index] : null;
    }

    // Revalidate the collection at commit entry: a selected ID alone does not
    // prove the account still owns a spare copy (one copy is retained in collection).
    private static final CardAlchemyReceipt.CardCount CARD_COUNT = L1CardAlchemy2::copiesFor;

    private static int copiesFor(Account acc, int cardId) {
        return switch (cardId) {
            case 1 -> acc.getInfo_heaven_poly();
            case 2 -> acc.gethero01();
            case 3 -> acc.gethero02();
            case 4 -> acc.gethero03();
            case 5 -> acc.gethero04();
            case 6 -> acc.gethero13();
            case 7 -> acc.gethero14();
            case 8 -> acc.gethero05();
            case 9 -> acc.gethero06();
            case 10 -> acc.gethero07();
            case 11 -> acc.getInfo_assm_poly();
            case 12 -> acc.gethero08();
            case 13 -> acc.gethero09();
            case 14 -> acc.getInfo_unik_poly();
            case 15 -> acc.gethero10();
            case 16 -> acc.getInfo_unir_poly();
            case 17 -> acc.getInfo_unim_poly();
            case 18 -> acc.gethero11();
            case 19 -> acc.getInfo_val_poly();
            case 20 -> acc.getInfo_larry_poly();
            case 21 -> acc.getInfo_gunter_poly();
            case 22 -> acc.getInfo_depor_poly();
            case 23 -> acc.getInfo_deathred_poly();
            case 24 -> acc.getInfo_ecil_poly();
            case 25 -> acc.gethero12();
            case 26 -> acc.gethero15();
            case 27 -> acc.gethero16();
            case 28 -> acc.getInfo_yozil_poly();
            case 29 -> acc.getInfo_owen_poly();
            case 30 -> acc.getInfo_helv_poly();
            case 31 -> acc.gethero17();
            case 32 -> acc.gethero18();
            case 33 -> acc.gethero19();
            case 34 -> acc.gethero20();
            case 35 -> acc.getInfo_kerep_poly();
            case 36 -> acc.getInfo_jowo_poly();
            default -> 0;
        };
    }

    // Schema-backed whitelist. Indexed by card ID; never derived from packet text.
    private static final String[] CARD_COLUMNS = {
        "info_heaven_poly", "hero01", "hero02", "hero03", "hero04", "hero13",
        "hero14", "hero05", "hero06", "hero07", "info_assm_poly", "hero08",
        "hero09", "info_unik_poly", "hero10", "info_unir_poly", "info_unim_poly", "hero11",
        "info_val_poly", "info_larry_poly", "info_gunter_poly", "info_depor_poly", "info_deathred_poly", "info_ecil_poly",
        "hero12", "hero15", "hero16", "info_yozil_poly", "info_owen_poly", "info_helv_poly",
        "hero17", "hero18", "hero19", "hero20", "info_kerep_poly", "info_jowo_poly"
    };

    static {
        _cardConsumers[0] = acc -> acc.setInfo_heaven_poly(acc.getInfo_heaven_poly() - 1);
        _cardConsumers[1] = acc -> acc.sethero01(acc.gethero01() - 1);
        _cardConsumers[2] = acc -> acc.sethero02(acc.gethero02() - 1);
        _cardConsumers[3] = acc -> acc.sethero03(acc.gethero03() - 1);
        _cardConsumers[4] = acc -> acc.sethero04(acc.gethero04() - 1);
        _cardConsumers[5] = acc -> acc.sethero13(acc.gethero13() - 1);
        _cardConsumers[6] = acc -> acc.sethero14(acc.gethero14() - 1);
        _cardConsumers[7] = acc -> acc.sethero05(acc.gethero05() - 1);
        _cardConsumers[8] = acc -> acc.sethero06(acc.gethero06() - 1);
        _cardConsumers[9] = acc -> acc.sethero07(acc.gethero07() - 1);
        _cardConsumers[10] = acc -> acc.setInfo_assm_poly(acc.getInfo_assm_poly() - 1);
        _cardConsumers[11] = acc -> acc.sethero08(acc.gethero08() - 1);
        _cardConsumers[12] = acc -> acc.sethero09(acc.gethero09() - 1);
        _cardConsumers[13] = acc -> acc.setInfo_unik_poly(acc.getInfo_unik_poly() - 1);
        _cardConsumers[14] = acc -> acc.sethero10(acc.gethero10() - 1);
        _cardConsumers[15] = acc -> acc.setInfo_unir_poly(acc.getInfo_unir_poly() - 1);
        _cardConsumers[16] = acc -> acc.setInfo_unim_poly(acc.getInfo_unim_poly() - 1);
        _cardConsumers[17] = acc -> acc.sethero11(acc.gethero11() - 1);
        _cardConsumers[18] = acc -> acc.setInfo_val_poly(acc.getInfo_val_poly() - 1);
        _cardConsumers[19] = acc -> acc.setInfo_larry_poly(acc.getInfo_larry_poly() - 1);
        _cardConsumers[20] = acc -> acc.setInfo_gunter_poly(acc.getInfo_gunter_poly() - 1);
        _cardConsumers[21] = acc -> acc.setInfo_depor_poly(acc.getInfo_depor_poly() - 1);
        _cardConsumers[22] = acc -> acc.setInfo_deathred_poly(acc.getInfo_deathred_poly() - 1);
        _cardConsumers[23] = acc -> acc.setInfo_ecil_poly(acc.getInfo_ecil_poly() - 1);
        _cardConsumers[24] = acc -> acc.sethero12(acc.gethero12() - 1);
        _cardConsumers[25] = acc -> acc.sethero15(acc.gethero15() - 1);
        _cardConsumers[26] = acc -> acc.sethero16(acc.gethero16() - 1);
        _cardConsumers[27] = acc -> acc.setInfo_yozil_poly(acc.getInfo_yozil_poly() - 1);
        _cardConsumers[28] = acc -> acc.setInfo_owen_poly(acc.getInfo_owen_poly() - 1);
        _cardConsumers[29] = acc -> acc.setInfo_helv_poly(acc.getInfo_helv_poly() - 1);
        _cardConsumers[30] = acc -> acc.sethero17(acc.gethero17() - 1);
        _cardConsumers[31] = acc -> acc.sethero18(acc.gethero18() - 1);
        _cardConsumers[32] = acc -> acc.sethero19(acc.gethero19() - 1);
        _cardConsumers[33] = acc -> acc.sethero20(acc.gethero20() - 1);
        _cardConsumers[34] = acc -> acc.setInfo_kerep_poly(acc.getInfo_kerep_poly() - 1);
        _cardConsumers[35] = acc -> acc.setInfo_jowo_poly(acc.getInfo_jowo_poly() - 1);
    }

    public static boolean HeroAlchemy1(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return false;

        Account acc = pc.getAccount();
        final int cardA = acc.getInfo_card_a();
        final int cardB = acc.getInfo_card_b();
        final int cardC = acc.getInfo_card_c();
        final int cardD = acc.getInfo_card_d();

        // 같은 순서로 카드 유효성을 확인하되 매 합성마다 int[4]를 생성하지 않는다.
        if (cardA == 0 || consumerFor(cardA) == null || copiesFor(acc, cardA) <= 1
                || cardB == 0 || consumerFor(cardB) == null || copiesFor(acc, cardB) <= 1
                || cardC == 0 || consumerFor(cardC) == null || copiesFor(acc, cardC) <= 1
                || cardD == 0 || consumerFor(cardD) == null || copiesFor(acc, cardD) <= 1
                || cardA == cardB || cardA == cardC || cardA == cardD
                || cardB == cardC || cardB == cardD || cardC == cardD) {
            SC_SystemMessage.send(pc, "합성에 필요한 카드가 부족하거나 올바르지 않습니다.");
            return false;
        }

        // 아데나 체크
        if (!(pc.getInventory().checkItem(40308, Config.hero_cost))) {
            SC_SystemMessage.send(pc, "합성에 필요한 아데나가 부족합니다.");
            return false;
        }

        // 지역 체크
        if (!pc.getMap().isSafetyZone(pc.getLocation())) {
            SC_SystemMessage.send(pc, "안전한 지역에서만 사용할 수 있습니다.");
            return false;
        }

        final int rx2 = _random.nextInt(100) + 1;
        final int chance = Config.legend_per + (pc.영웅합성확률증가 ? Config.legend_per_potion : 0);
        final boolean isSuccess = rx2 <= chance;
        final ResultCard result = isSuccess ? getLegendResult() : getHeroResult();
        return CardAlchemyReceipt.submit(pc, acc, Config.hero_cost, result.itemId,
                1, CARD_COLUMNS, _cardConsumers, CARD_COUNT,
                cardA, cardB, cardC, cardD, 4,
                () -> processHeroSynthesis(pc, result, rx2, chance, isSuccess));
    }

    private static void processHeroSynthesis(L1PcInstance pc, ResultCard result,
            int rx2, int chance, boolean isSuccess) {
        // Retire the committed bonus even if a subsequent packet or notice fails.
        pc.영웅합성확률증가 = false;
        if (pc.isGm()) SC_SystemMessage.send(pc, "합성 확률: " + chance + "% (주사위: " + rx2 + ")");
        // Inventory reward and account card counters are already durable.
        SC_SkillSound.send(pc, pc.getId(), 21128);
        if (isSuccess) {
            SC_DisplayEffect.send(pc, 1);
            SC_DisplayEffect.send(pc, 21);
            MessageUtil.toAllPlayers("어느 아덴 용사 님께서 " + result.name + " 카드 합성에 성공하였습니다.");
            MessageUtil.greenMessageToAllPlayers("어느 아덴 용사 님께서 " + result.name + " 카드 합성에 성공하였습니다.");
        } else {
            SC_DisplayEffect.send(pc, 20);
            SC_SkillSound.send(pc, pc.getId(), 7976);
            SC_SkillSound.broadcast(pc, pc.getId(), 7976);
        }
        sendResultUI(pc, result, isSuccess);
    }

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
        new ResultCard("69", "[전설]화염의 데스나이트", 875640378),
        new ResultCard("159", "[전설]가드리아", 875640308),
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
        new ResultCard("55", "[전설]로데마이", 875640371)
    };

    private static final ResultCard[] HERO_CARDS = {
        new ResultCard("177", "[영웅]천상의 기사", 875640315),
        new ResultCard("179", "[영웅]발키리 에이르", 875640316),
        new ResultCard("181", "[영웅]필리아", 875640317),
        new ResultCard("183", "[영웅]군터", 875640318),
        new ResultCard("185", "[영웅]데포로쥬", 875640319),
        new ResultCard("187", "[영웅]진 데스나이트", 875640320),
        new ResultCard("189", "[영웅]이실로테", 875640321),
        new ResultCard("191", "[영웅]어쌔신 마스터", 875640322),
        new ResultCard("193", "[영웅]케레니스", 875640323),
        new ResultCard("195", "[영웅]조우", 875640324),
        new ResultCard("197", "[영웅]유니크 나이트", 875640325),
        new ResultCard("199", "[영웅]유니크 매지션", 875640326),
        new ResultCard("201", "[영웅]유니크 레인저", 875640327),
        new ResultCard("203", "[영웅]질리언", 875640328),
        new ResultCard("205", "[영웅]오웬", 875640329),
        new ResultCard("207", "[영웅]헬바인", 875640330),
        new ResultCard("1", "[영웅]아이비", 875640345),
        new ResultCard("3", "[영웅]각성 켄라우헬", 875640346),
        new ResultCard("5", "[영웅]경비병(창)그린", 875640347),
        new ResultCard("7", "[영웅]경비병(창)레드", 875640348),
        new ResultCard("9", "[영웅]경비병(활)그린", 875640349),
        new ResultCard("11", "[영웅]경비병(활)레드", 875640350),
        new ResultCard("13", "[영웅]금빛 블레이즈", 875640351),
        new ResultCard("15", "[영웅]랜스 마스터", 875640352),
        new ResultCard("17", "[영웅]로즈마리", 875640353),
        new ResultCard("19", "[영웅]메르키오르", 875640354),
        new ResultCard("21", "[영웅]메이드 안나", 875640355),
        new ResultCard("23", "[영웅]발터자르", 875640356),
        new ResultCard("25", "[영웅]사단장 하퍼", 875640357),
        new ResultCard("27", "[영웅]사이하", 875640358),
        new ResultCard("29", "[영웅]세마", 875640359),
        new ResultCard("31", "[영웅]에바의 수호자", 875640360),
        new ResultCard("33", "[영웅]유니크 랜서", 875640361),
        new ResultCard("35", "[영웅]질서의 연금술사", 875640362),
        new ResultCard("37", "[영웅]카스파", 875640363),
        new ResultCard("39", "[영웅]크라켄", 875640364)
    };

    private static ResultCard getLegendResult() {
        final int index = _random.nextInt(36);
        return LEGEND_CARDS[index];
    }

    private static ResultCard getHeroResult() {
        final int index = _random.nextInt(36);
        return HERO_CARDS[index];
    }

    private static void sendResultUI(L1PcInstance pc, ResultCard result, boolean isSuccess) {
        ArrayList<String> args = new ArrayList<>(78);
        Account acc = pc.getAccount();
        
        args.add(result.cardId);
        args.add("174"); args.add("174"); args.add("174");

        // 아이콘 매핑
        args.add(acc.getInfo_heaven_poly() < 2 ? "176" : "177");
        args.add(acc.gethero01() < 2 ? "26" : "27");
        args.add(acc.gethero02() < 2 ? "30" : "31");
        args.add(acc.gethero03() < 2 ? "4" : "5");
        args.add(acc.gethero04() < 2 ? "6" : "7");
        args.add(acc.gethero13() < 2 ? "8" : "9");
        args.add(acc.gethero14() < 2 ? "10" : "11");
        args.add(acc.gethero05() < 2 ? "0" : "1");
        args.add(acc.gethero06() < 2 ? "2" : "3");
        args.add(acc.gethero07() < 2 ? "12" : "13");
        args.add(acc.getInfo_assm_poly() < 2 ? "190" : "191");
        args.add(acc.gethero08() < 2 ? "14" : "15");
        args.add(acc.gethero09() < 2 ? "16" : "17");
        args.add(acc.getInfo_unik_poly() < 2 ? "196" : "197");
        args.add(acc.gethero10() < 2 ? "32" : "33");
        args.add(acc.getInfo_unir_poly() < 2 ? "200" : "201");
        args.add(acc.getInfo_unim_poly() < 2 ? "198" : "199");
        args.add(acc.gethero11() < 2 ? "34" : "35");
        args.add(acc.getInfo_val_poly() < 2 ? "178" : "179");
        args.add(acc.getInfo_larry_poly() < 2 ? "180" : "181");
        args.add(acc.getInfo_gunter_poly() < 2 ? "182" : "183");
        args.add(acc.getInfo_depor_poly() < 2 ? "184" : "185");
        args.add(acc.getInfo_deathred_poly() < 2 ? "186" : "187");
        args.add(acc.getInfo_ecil_poly() < 2 ? "188" : "189");
        args.add(acc.gethero12() < 2 ? "38" : "39");
        args.add(acc.gethero15() < 2 ? "20" : "21");
        args.add(acc.gethero16() < 2 ? "24" : "25");
        args.add(acc.getInfo_yozil_poly() < 2 ? "202" : "203");
        args.add(acc.getInfo_owen_poly() < 2 ? "204" : "205");
        args.add(acc.getInfo_helv_poly() < 2 ? "206" : "207");
        args.add(acc.gethero17() < 2 ? "18" : "19");
        args.add(acc.gethero18() < 2 ? "22" : "23");
        args.add(acc.gethero19() < 2 ? "28" : "29");
        args.add(acc.gethero20() < 2 ? "36" : "37");
        args.add(acc.getInfo_kerep_poly() < 2 ? "192" : "193");
        args.add(acc.getInfo_jowo_poly() < 2 ? "194" : "195");

        // 수집 갯수
        args.add(countText(acc.getInfo_heaven_poly()));
        args.add(countText(acc.gethero01()));
        args.add(countText(acc.gethero02()));
        args.add(countText(acc.gethero03()));
        args.add(countText(acc.gethero04()));
        args.add(countText(acc.gethero13()));
        args.add(countText(acc.gethero14()));
        args.add(countText(acc.gethero05()));
        args.add(countText(acc.gethero06()));
        args.add(countText(acc.gethero07()));
        args.add(countText(acc.getInfo_assm_poly()));
        args.add(countText(acc.gethero08()));
        args.add(countText(acc.gethero09()));
        args.add(countText(acc.getInfo_unik_poly()));
        args.add(countText(acc.gethero10()));
        args.add(countText(acc.getInfo_unir_poly()));
        args.add(countText(acc.getInfo_unim_poly()));
        args.add(countText(acc.gethero11()));
        args.add(countText(acc.getInfo_val_poly()));
        args.add(countText(acc.getInfo_larry_poly()));
        args.add(countText(acc.getInfo_gunter_poly()));
        args.add(countText(acc.getInfo_depor_poly()));
        args.add(countText(acc.getInfo_deathred_poly()));
        args.add(countText(acc.getInfo_ecil_poly()));
        args.add(countText(acc.gethero12()));
        args.add(countText(acc.gethero15()));
        args.add(countText(acc.gethero16()));
        args.add(countText(acc.getInfo_yozil_poly()));
        args.add(countText(acc.getInfo_owen_poly()));
        args.add(countText(acc.getInfo_helv_poly()));
        args.add(countText(acc.gethero17()));
        args.add(countText(acc.gethero18()));
        args.add(countText(acc.gethero19()));
        args.add(countText(acc.gethero20()));
        args.add(countText(acc.getInfo_kerep_poly()));
        args.add(countText(acc.getInfo_jowo_poly()));

        args.add("174");
        args.add(Config.hero_cost + "원");

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007hallo2", args);

        // 채팅 메시지
        SC_ChatPacket.sendDirect(pc, pc, result.name + " 카드를 획득하였습니다.", 1);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, (isSuccess ? "\\aF" : "\\aG") + result.name + " 카드를 획득하였습니다.");
    }


    // Keep the original zero-count text and formatting; avoid the per-result int[] snapshot.
    private static String countText(int count) {
        return count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]";
    }

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