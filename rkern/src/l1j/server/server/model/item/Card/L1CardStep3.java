package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 신화 합성 Step 3 (재료 선택 전 초기화 UI) 핸들러
 */
public class L1CardStep3 {

    // 10종 신화 카드 메타데이터 (남Off, 남On, 여Off, 여On, 수량가져오기 함수)
    private static final List<MythData> MYTH_POOL = new ArrayList<>();

    static {
        MYTH_POOL.add(new MythData(144, 145, 146, 147, Account::getInfo_class_poly)); // 군주
        MYTH_POOL.add(new MythData(2858, 2859, 2860, 2861, Account::getInfo_ki_poly)); // 기사
        MYTH_POOL.add(new MythData(2862, 2863, 2864, 2865, Account::getInfo_el_poly)); // 요정
        MYTH_POOL.add(new MythData(2866, 2867, 2868, 2869, Account::getInfo_wi_poly)); // 법사
        MYTH_POOL.add(new MythData(2870, 2871, 2872, 2873, Account::getInfo_da_poly)); // 다엘
        MYTH_POOL.add(new MythData(2874, 2875, 2874, 2875, Account::getInfo_wa_poly)); // 전사
        MYTH_POOL.add(new MythData(2876, 2877, 2876, 2877, Account::getInfo_drag_poly)); // 용기사
        MYTH_POOL.add(new MythData(140, 141, 142, 143, Account::getInfo_il_poly));     // 환술사
        MYTH_POOL.add(new MythData(148, 149, 148, 149, Account::getInfo_dsn_poly));    // 플레 데스
        MYTH_POOL.add(new MythData(150, 151, 150, 151, Account::getInfo_bapo_poly));   // 플레 바포
    }

    public static void LegendAlchemyF(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        int sex = pc.get_sex(); // 0:남, 1:여
        ArrayList<String> args = new ArrayList<>(30);

        // 상단 선택 슬롯 초기화 (4개 모두 빈 이미지 174)
        for (int i = 0; i < 4; i++) args.add("174");

        // 10종 신화 카드 상태 아이콘 (성별/수량 체크 자동화)
        for (MythData data : MYTH_POOL) {
            args.add(data.getIcon(acc, sex));
        }

        // 10종 신화 카드 소지 장수 텍스트
        for (MythData data : MYTH_POOL) {
            args.add(formatCount(data.getter.applyAsInt(acc)));
        }

        // 하단 패딩 및 합성 비용
        args.add("174");
        args.add(Config.myth_cost + "원");

        // 기르타스(유일) 정보 (아이콘 + 장수)
        int girtasCount = acc.getmyth01();
        if (sex == 0) args.add(girtasCount < 2 ? "70" : "71");
        else          args.add(girtasCount < 2 ? "72" : "73");
        
        args.add(formatCount(girtasCount));

        //  패킷 전송 
        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007hallo1", args);
    }

    /** 장수 표시 텍스트 포맷 */
    private static String formatCount(int count) {
        return "[ " + count + "장 ]";
    }

    /** 내부 데이터 관리 클래스 */
    private static class MythData {
        final int mOff, mOn, fOff, fOn;
        final String mOffText, mOnText, fOffText, fOnText;
        final ToIntFunction<Account> getter;

        MythData(int mOff, int mOn, int fOff, int fOn, ToIntFunction<Account> getter) {
            this.mOff = mOff; this.mOn = mOn;
            this.fOff = fOff; this.fOn = fOn;
            this.mOffText = Integer.toString(mOff); this.mOnText = Integer.toString(mOn);
            this.fOffText = Integer.toString(fOff); this.fOnText = Integer.toString(fOn);
            this.getter = getter;
        }

        String getIcon(Account acc, int sex) {
            int count = getter.applyAsInt(acc);
            if (sex == 0) return count < 2 ? mOffText : mOnText;
            else          return count < 2 ? fOffText : fOnText;
        }
    }
}