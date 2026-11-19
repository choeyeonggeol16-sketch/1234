package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 전설 합성 Step 1 (재료 선택 전 초기화 UI) 핸들러
 */
public class L1CardStep1 {

    // 23종 전설 카드 메타데이터 (아이콘OFF, 아이콘ON, 수량가져오기 함수)
    private static final List<LegendData> LEGEND_POOL = new ArrayList<>();

    static {

        LEGEND_POOL.add(new LegendData(162, 163, Account::getInfo_ds_poly));   // 드슬
        LEGEND_POOL.add(new LegendData(156, 157, Account::getInfo_dru_poly));  // 드루가
        LEGEND_POOL.add(new LegendData(158, 159, Account::getInfo_ga_poly));   // 가드리아
        LEGEND_POOL.add(new LegendData(160, 161, Account::getInfo_ger_poly));  // 게렝
        LEGEND_POOL.add(new LegendData(164, 165, Account::getInfo_dev_poly));  // 디바인
        LEGEND_POOL.add(new LegendData(166, 167, Account::getInfo_pen_poly));  // 팬텀
        LEGEND_POOL.add(new LegendData(168, 169, Account::getInfo_atun_poly)); // 아툰
        LEGEND_POOL.add(new LegendData(170, 171, Account::getInfo_moon_poly)); // 질리언
        LEGEND_POOL.add(new LegendData(172, 173, Account::getInfo_mar_poly));  // 마커스
        LEGEND_POOL.add(new LegendData(44,  45,  Account::getlegend01));       // 경비창대장
        LEGEND_POOL.add(new LegendData(46,  47,  Account::getlegend12));       // 경비활대장
        LEGEND_POOL.add(new LegendData(48,  49,  Account::getlegend02));       // 나이트발드
        LEGEND_POOL.add(new LegendData(50,  51,  Account::getlegend03));       // 국왕 에오딘
        LEGEND_POOL.add(new LegendData(54,  55,  Account::getlegend04));       // 로데마이
        LEGEND_POOL.add(new LegendData(56,  57,  Account::getlegend05));       // 베리스
        LEGEND_POOL.add(new LegendData(58,  59,  Account::getlegend06));       // 아리아 울프
        LEGEND_POOL.add(new LegendData(60,  61,  Account::getlegend07));       // 젠
        LEGEND_POOL.add(new LegendData(62,  63,  Account::getlegend08));       // 지옥데스
        LEGEND_POOL.add(new LegendData(64,  65,  Account::getlegend09));       // 칠흑데스
        LEGEND_POOL.add(new LegendData(68,  69,  Account::getlegend10));       // 화염데스
        LEGEND_POOL.add(new LegendData(42,  43,  Account::getlegend11));       // 스모커
        LEGEND_POOL.add(new LegendData(52,  53,  Account::getlegend13));       // 엑스터
        LEGEND_POOL.add(new LegendData(66,  67,  Account::getlegend14));       // 하딘영혼
    }

    public static void LegendAlchemyF(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        // UI 인자 갯수에 맞춰 초기 용량 설정 
        ArrayList<String> args = new ArrayList<>(55);

        // 상단 선택 슬롯 초기화 (4개 모두 빈 이미지 174)
        for (int i = 0; i < 4; i++) {
            args.add("174");
        }

        // 23종 전설 카드 상태 아이콘 (2장 미만이면 비활성 이미지 출력)
        for (LegendData data : LEGEND_POOL) {
            int count = data.getter.applyAsInt(acc);
            args.add(String.valueOf(count < 2 ? data.imgOff : data.imgOn));
        }

        // 23종 전설 카드 소지 장수 텍스트
        for (LegendData data : LEGEND_POOL) {
            int count = data.getter.applyAsInt(acc);
            args.add(count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]");
        }

        // 하단 합성 비용
        args.add(Config.legend_cost + "원");

        // 패킷 전송
        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007rudolph1", args);
    }

    /** 내부 데이터 관리 클래스 */
    private static class LegendData {
        final int imgOff, imgOn;
        final ToIntFunction<Account> getter;

        LegendData(int off, int on, ToIntFunction<Account> getter) {
            this.imgOff = off;
            this.imgOn = on;
            this.getter = getter;
        }
    }
}