package l1j.server.server.model.item.Card;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.HashMap;
import java.util.Map;
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
    private static final Map<Integer, Consumer<Account>> _cardConsumers = new HashMap<>();

    static {
        _cardConsumers.put(1,  acc -> acc.setInfo_heaven_poly(acc.getInfo_heaven_poly() - 1));
        _cardConsumers.put(2,  acc -> acc.sethero01(acc.gethero01() - 1));
        _cardConsumers.put(3,  acc -> acc.sethero02(acc.gethero02() - 1));
        _cardConsumers.put(4,  acc -> acc.sethero03(acc.gethero03() - 1));
        _cardConsumers.put(5,  acc -> acc.sethero04(acc.gethero04() - 1));
        _cardConsumers.put(6,  acc -> acc.sethero13(acc.gethero13() - 1));
        _cardConsumers.put(7,  acc -> acc.sethero14(acc.gethero14() - 1));
        _cardConsumers.put(8,  acc -> acc.sethero05(acc.gethero05() - 1));
        _cardConsumers.put(9,  acc -> acc.sethero06(acc.gethero06() - 1));
        _cardConsumers.put(10, acc -> acc.sethero07(acc.gethero07() - 1));
        _cardConsumers.put(11, acc -> acc.setInfo_assm_poly(acc.getInfo_assm_poly() - 1));
        _cardConsumers.put(12, acc -> acc.sethero08(acc.gethero08() - 1));
        _cardConsumers.put(13, acc -> acc.sethero09(acc.gethero09() - 1));
        _cardConsumers.put(14, acc -> acc.setInfo_unik_poly(acc.getInfo_unik_poly() - 1));
        _cardConsumers.put(15, acc -> acc.sethero10(acc.gethero10() - 1));
        _cardConsumers.put(16, acc -> acc.setInfo_unir_poly(acc.getInfo_unir_poly() - 1));
        _cardConsumers.put(17, acc -> acc.setInfo_unim_poly(acc.getInfo_unim_poly() - 1));
        _cardConsumers.put(18, acc -> acc.sethero11(acc.gethero11() - 1));
        _cardConsumers.put(19, acc -> acc.setInfo_val_poly(acc.getInfo_val_poly() - 1));
        _cardConsumers.put(20, acc -> acc.setInfo_larry_poly(acc.getInfo_larry_poly() - 1));
        _cardConsumers.put(21, acc -> acc.setInfo_gunter_poly(acc.getInfo_gunter_poly() - 1));
        _cardConsumers.put(22, acc -> acc.setInfo_depor_poly(acc.getInfo_depor_poly() - 1));
        _cardConsumers.put(23, acc -> acc.setInfo_deathred_poly(acc.getInfo_deathred_poly() - 1));
        _cardConsumers.put(24, acc -> acc.setInfo_ecil_poly(acc.getInfo_ecil_poly() - 1));
        _cardConsumers.put(25, acc -> acc.sethero12(acc.gethero12() - 1));
        _cardConsumers.put(26, acc -> acc.sethero15(acc.gethero15() - 1));
        _cardConsumers.put(27, acc -> acc.sethero16(acc.gethero16() - 1));
        _cardConsumers.put(28, acc -> acc.setInfo_yozil_poly(acc.getInfo_yozil_poly() - 1));
        _cardConsumers.put(29, acc -> acc.setInfo_owen_poly(acc.getInfo_owen_poly() - 1));
        _cardConsumers.put(30, acc -> acc.setInfo_helv_poly(acc.getInfo_helv_poly() - 1));
        _cardConsumers.put(31, acc -> acc.sethero17(acc.gethero17() - 1));
        _cardConsumers.put(32, acc -> acc.sethero18(acc.gethero18() - 1));
        _cardConsumers.put(33, acc -> acc.sethero19(acc.gethero19() - 1));
        _cardConsumers.put(34, acc -> acc.sethero20(acc.gethero20() - 1));
        _cardConsumers.put(35, acc -> acc.setInfo_kerep_poly(acc.getInfo_kerep_poly() - 1));
        _cardConsumers.put(36, acc -> acc.setInfo_jowo_poly(acc.getInfo_jowo_poly() - 1));
    }

    public static boolean HeroAlchemy1(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return false;

        Account acc = pc.getAccount();
        int[] cards = { acc.getInfo_card_a(), acc.getInfo_card_b(), acc.getInfo_card_c(), acc.getInfo_card_d() };

        // 모든 카드 유효성 검사 
        for (int cardId : cards) {
            if (cardId == 0 || !_cardConsumers.containsKey(cardId)) {
                SC_SystemMessage.send(pc, "합성에 필요한 카드가 부족하거나 올바르지 않습니다.");
                return false;
            }
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

        pc.getInventory().consumeItem(40308, Config.hero_cost);
        for (int cardId : cards) {
            _cardConsumers.get(cardId).accept(acc);
        }

        acc.updateHero2();
        acc.updateHero();
        
        // 결과 처리
        processHeroSynthesis(pc);
        return true;
    }

    private static void processHeroSynthesis(L1PcInstance pc) {
        int rx2 = _random.nextInt(100) + 1;
        int chance = Config.legend_per + (pc.영웅합성확률증가 ? Config.legend_per_potion : 0);

        if (pc.isGm()) SC_SystemMessage.send(pc, "합성 확률: " + chance + "% (주사위: " + rx2 + ")");

        boolean isSuccess = rx2 <= chance;
        ResultCard result = isSuccess ? getLegendResult() : getHeroResult();

        // 계정 정보 초기화 및 저장
        Account acc = pc.getAccount();
        acc.setInfo_card_a(0); acc.setInfo_card_b(0); acc.setInfo_card_c(0); acc.setInfo_card_d(0);
        acc.updatecardpu();

        // 6아이템 지급 및 패킷
        pc.getInventory().storeItem(result.itemId, 1);
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
        pc.영웅합성확률증가 = false;

        // 결과 UI 업데이트
        sendResultUI(pc, result, isSuccess);
    }

    private static ResultCard getLegendResult() {
        int index = _random.nextInt(36); 
        Object[][] pool = {
            {"163", "[전설]드래곤 슬레이어", 875640306}, {"157", "[전설]드루가", 875640307}, {"159", "[전설]가드리아", 875640308},
            {"161", "[전설]게렝", 875640309}, {"165", "[전설]디바인 크루세이더", 875640310}, {"167", "[전설]팬텀 나이트", 875640311},
            {"169", "[전설]아툰", 875640312}, {"171", "[전설]달의 질리언", 875640313}, {"173", "[전설]공작 마커스", 875640314},
            {"43", "[전설]Mr.스모커", 875640365}, {"45", "[전설]경비병(창)대장", 875640366}, {"47", "[전설]경비병(활)대장", 875640367},
            {"49", "[전설]과거의 나이트발드", 875640368}, {"51", "[전설]국왕 에오딘", 875640369}, {"53", "[전설]군단장 엑스터", 875640370},
            {"55", "[전설]로데마이", 875640371}, {"57", "[전설]베리스", 875640372}, {"59", "[전설]암살의 아리아 울프", 875640373},
            {"61", "[전설]젠", 875640374}, {"63", "[전설]지옥의 데스나이트", 875640375}, {"65", "[전설]샤르나", 875640376},
            {"67", "[전설]하딘의 영혼", 875640377}, {"69", "[전설]화염의 데스나이트", 875640378}, {"159", "[전설]가드리아", 875640308},
            {"165", "[전설]디바인 크루세이더", 875640310}, {"167", "[전설]팬텀 나이트", 875640311}, {"169", "[전설]아툰", 875640312},
            {"171", "[전설]달의 질리언", 875640313}, {"173", "[전설]공작 마커스", 875640314}, {"43", "[전설]Mr.스모커", 875640365},
            {"45", "[전설]경비병(창)대장", 875640366}, {"47", "[전설]경비병(활)대장", 875640367}, {"49", "[전설]과거의 나이트발드", 875640368},
            {"51", "[전설]국왕 에오딘", 875640369}, {"53", "[전설]군단장 엑스터", 875640370}, {"55", "[전설]로데마이", 875640371}
        };
        return new ResultCard(pool[index]);
    }

    private static ResultCard getHeroResult() {
        int index = _random.nextInt(36);
        Object[][] pool = {
            {"177", "[영웅]천상의 기사", 875640315}, {"179", "[영웅]발키리 에이르", 875640316}, {"181", "[영웅]필리아", 875640317},
            {"183", "[영웅]군터", 875640318}, {"185", "[영웅]데포로쥬", 875640319}, {"187", "[영웅]진 데스나이트", 875640320},
            {"189", "[영웅]이실로테", 875640321}, {"191", "[영웅]어쌔신 마스터", 875640322}, {"193", "[영웅]케레니스", 875640323},
            {"195", "[영웅]조우", 875640324}, {"197", "[영웅]유니크 나이트", 875640325}, {"199", "[영웅]유니크 매지션", 875640326},
            {"201", "[영웅]유니크 레인저", 875640327}, {"203", "[영웅]질리언", 875640328}, {"205", "[영웅]오웬", 875640329},
            {"207", "[영웅]헬바인", 875640330}, {"1", "[영웅]아이비", 875640345}, {"3", "[영웅]각성 켄라우헬", 875640346},
            {"5", "[영웅]경비병(창)그린", 875640347}, {"7", "[영웅]경비병(창)레드", 875640348}, {"9", "[영웅]경비병(활)그린", 875640349},
            {"11", "[영웅]경비병(활)레드", 875640350}, {"13", "[영웅]금빛 블레이즈", 875640351}, {"15", "[영웅]랜스 마스터", 875640352},
            {"17", "[영웅]로즈마리", 875640353}, {"19", "[영웅]메르키오르", 875640354}, {"21", "[영웅]메이드 안나", 875640355},
            {"23", "[영웅]발터자르", 875640356}, {"25", "[영웅]사단장 하퍼", 875640357}, {"27", "[영웅]사이하", 875640358},
            {"29", "[영웅]세마", 875640359}, {"31", "[영웅]에바의 수호자", 875640360}, {"33", "[영웅]유니크 랜서", 875640361},
            {"35", "[영웅]질서의 연금술사", 875640362}, {"37", "[영웅]카스파", 875640363}, {"39", "[영웅]크라켄", 875640364}
        };
        return new ResultCard(pool[index]);
    }

    private static void sendResultUI(L1PcInstance pc, ResultCard result, boolean isSuccess) {
        ArrayList<String> args = new ArrayList<>();
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
        int[] counts = {
            acc.getInfo_heaven_poly(), acc.gethero01(), acc.gethero02(), acc.gethero03(), acc.gethero04(),
            acc.gethero13(), acc.gethero14(), acc.gethero05(), acc.gethero06(), acc.gethero07(),
            acc.getInfo_assm_poly(), acc.gethero08(), acc.gethero09(), acc.getInfo_unik_poly(),
            acc.gethero10(), acc.getInfo_unir_poly(), acc.getInfo_unim_poly(), acc.gethero11(),
            acc.getInfo_val_poly(), acc.getInfo_larry_poly(), acc.getInfo_gunter_poly(), acc.getInfo_depor_poly(),
            acc.getInfo_deathred_poly(), acc.getInfo_ecil_poly(), acc.gethero12(), acc.gethero15(),
            acc.gethero16(), acc.getInfo_yozil_poly(), acc.getInfo_owen_poly(), acc.getInfo_helv_poly(),
            acc.gethero17(), acc.gethero18(), acc.gethero19(), acc.gethero20(), acc.getInfo_kerep_poly(),
            acc.getInfo_jowo_poly()
        };
        for (int c : counts) args.add(c == 0 ? "[ 0장 ]" : "[ " + c + "장 ]");

        args.add("174");
        args.add(Config.hero_cost + "원");

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007hallo2", args);

        // 채팅 메시지
        SC_ChatPacket.sendDirect(pc, pc, result.name + " 카드를 획득하였습니다.", 1);
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, (isSuccess ? "\\aF" : "\\aG") + result.name + " 카드를 획득하였습니다.");
    }

    private static class ResultCard {
        String cardId, name;
        int itemId;
        ResultCard(Object[] data) {
            this.cardId = (String)data[0];
            this.name = (String)data[1];
            this.itemId = (int)data[2];
        }
    }
}