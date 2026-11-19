package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 전설 변신 카드 리스트 및 도감 정보 핸들러
 */
public class L1CardLegendList {

    private static final int MELEE = 1;  // 근거리 (A)
    private static final int RANGE = 2;  // 원거리 (B)
    private static final int MAGIC = 4;  // 마법 (C)
    private static final int ALL   = 7;

    // 전설 카드 메타데이터 리스트
    private static final List<LegendCard> LEGEND_POOL = new ArrayList<>();

    static {
        // [이름, 이미지OFF, 이미지ON, 카테고리, 수량가져오기 함수]
        LEGEND_POOL.add(new LegendCard(162, 163, ALL,   Account::getInfo_ds_poly));   // 드슬
        LEGEND_POOL.add(new LegendCard(156, 157, MELEE, Account::getInfo_dru_poly));  // 드루가
        LEGEND_POOL.add(new LegendCard(158, 159, RANGE, Account::getInfo_ga_poly));   // 가드리아
        LEGEND_POOL.add(new LegendCard(160, 161, MAGIC, Account::getInfo_ger_poly));  // 게렝
        LEGEND_POOL.add(new LegendCard(164, 165, MELEE, Account::getInfo_dev_poly));  // 디바인
        LEGEND_POOL.add(new LegendCard(166, 167, MELEE, Account::getInfo_pen_poly));  // 팬텀
        LEGEND_POOL.add(new LegendCard(168, 169, MELEE, Account::getInfo_atun_poly)); // 아툰
        LEGEND_POOL.add(new LegendCard(170, 171, RANGE, Account::getInfo_moon_poly)); // 질리언
        LEGEND_POOL.add(new LegendCard(172, 173, MELEE, Account::getInfo_mar_poly));  // 마커스
        LEGEND_POOL.add(new LegendCard(44,  45,  MELEE, Account::getlegend01));       // 경비창대장
        LEGEND_POOL.add(new LegendCard(46,  47,  RANGE, Account::getlegend12));       // 경비활대장
        LEGEND_POOL.add(new LegendCard(48,  49,  MELEE, Account::getlegend02));       // 나이트발드
        LEGEND_POOL.add(new LegendCard(50,  51,  MELEE, Account::getlegend03));       // 국왕 에오딘
        LEGEND_POOL.add(new LegendCard(54,  55,  MELEE, Account::getlegend04));       // 로데마이
        LEGEND_POOL.add(new LegendCard(56,  57,  MELEE, Account::getlegend05));       // 베리스
        LEGEND_POOL.add(new LegendCard(58,  59,  MELEE, Account::getlegend06));       // 아리아 울프
        LEGEND_POOL.add(new LegendCard(60,  61,  MELEE, Account::getlegend07));       // 젠
        LEGEND_POOL.add(new LegendCard(62,  63,  MELEE, Account::getlegend08));       // 지옥데스
        LEGEND_POOL.add(new LegendCard(64,  65,  ALL,   Account::getlegend09));       // 칠흑데스
        LEGEND_POOL.add(new LegendCard(68,  69,  MELEE, Account::getlegend10));       // 화염데스
        LEGEND_POOL.add(new LegendCard(42,  43,  RANGE, Account::getlegend11));       // 스모커
        LEGEND_POOL.add(new LegendCard(52,  53,  RANGE, Account::getlegend13));       // 엑스터
        LEGEND_POOL.add(new LegendCard(66,  67,  MAGIC, Account::getlegend14));       // 하딘영혼
    }

    /** 빈 리스트 전송 */
    public static void cardlist(L1PcInstance pc) {
        if (pc == null) return;
        SC_NPCTalkReturn.send(pc, pc.getId(), "2007santa3", new String[0]);
    }

    /** 전체 도감 정보 (아이콘 23개 + 수량텍스트 23개) */
    public static void cardinfo(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        ArrayList<String> args = new ArrayList<>();

        // 아이콘 23종 추가
        for (LegendCard card : LEGEND_POOL) {
            args.add(card.getIcon(acc));
        }

        // 수량 텍스트 23종 추가
        for (LegendCard card : LEGEND_POOL) {
            args.add(card.getCountText(acc));
        }

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "neilegendt", args);
    }

    /** 근거리 목록 (A) */
    public static void cardlistA(L1PcInstance pc) {
        sendFilteredList(pc, MELEE, "2007rudolph5");
    }

    /** 원거리 목록 (B) */
    public static void cardlistB(L1PcInstance pc) {
        sendFilteredList(pc, RANGE, "2007santa1");
    }

    /** 마법 목록 (C) */
    public static void cardlistC(L1PcInstance pc) {
        sendFilteredList(pc, MAGIC, "2007santa2");
    }

    /** 필터링된 리스트 전송 공통 로직 */
    private static void sendFilteredList(L1PcInstance pc, int filter, String html) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        ArrayList<String> args = new ArrayList<>();

        for (LegendCard card : LEGEND_POOL) {
            if ((card.category & filter) != 0) {
                args.add(card.getIcon(acc));
            }
        }
        SC_NPCTalkReturn.sendList(pc, pc.getId(), html, args);
    }

    /** 내부 데이터 클래스 */
    private static class LegendCard {
        int imgOff, imgOn, category;
        Function<Account, Integer> countGetter;

        LegendCard(int off, int on, int cat, Function<Account, Integer> getter) {
            this.imgOff = off; this.imgOn = on; 
            this.category = cat; this.countGetter = getter;
        }

        String getIcon(Account acc) {
            return countGetter.apply(acc) == 0 ? String.valueOf(imgOff) : String.valueOf(imgOn);
        }

        String getCountText(Account acc) {
            int count = countGetter.apply(acc);
            return "[ " + count + "장 ]";
        }
    }
}