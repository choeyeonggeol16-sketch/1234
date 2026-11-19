package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 영웅 합성 Step 2 (재료 선택 전 초기화 UI) 핸들러
 */
public class L1CardStep2 {

    // 영웅 카드 메타데이터 (아이콘OFF, 아이콘ON, 수량가져오기 함수)
    private static final List<HeroData> HERO_POOL = new ArrayList<>();

    static {

        HERO_POOL.add(new HeroData(176, 177, Account::getInfo_heaven_poly)); // 천상의 기사
        HERO_POOL.add(new HeroData(26,  27,  Account::gethero01));           // 사이하
        HERO_POOL.add(new HeroData(30,  31,  Account::gethero02));           // 에바의 수호자
        HERO_POOL.add(new HeroData(4,   5,   Account::gethero03));           // 경비병(창)그린
        HERO_POOL.add(new HeroData(6,   7,   Account::gethero04));           // 경비병(창)레드
        HERO_POOL.add(new HeroData(8,   9,   Account::gethero13));           // 경비병(활)그린
        HERO_POOL.add(new HeroData(10,  11,  Account::gethero14));           // 경비병(활)레드
        HERO_POOL.add(new HeroData(0,   1,   Account::gethero05));           // 가시공주 아이비
        HERO_POOL.add(new HeroData(2,   3,   Account::gethero06));           // 각성 켄라우헬
        HERO_POOL.add(new HeroData(12,  13,  Account::gethero07));           // 금빛 블레이즈
        HERO_POOL.add(new HeroData(190, 191, Account::getInfo_assm_poly));   // 어쌔신 마스터
        HERO_POOL.add(new HeroData(14,  15,  Account::gethero08));           // 랜스 마스터
        HERO_POOL.add(new HeroData(16,  17,  Account::gethero09));           // 로즈마리
        HERO_POOL.add(new HeroData(196, 197, Account::getInfo_unik_poly));   // 유니크 나이트
        HERO_POOL.add(new HeroData(32,  33,  Account::gethero10));           // 유니크 랜서
        HERO_POOL.add(new HeroData(200, 201, Account::getInfo_unir_poly));   // 유니크 레인저
        HERO_POOL.add(new HeroData(198, 199, Account::getInfo_unim_poly));   // 유니크 매지션
        HERO_POOL.add(new HeroData(34,  35,  Account::gethero11));           // 질서의 연금술사
        HERO_POOL.add(new HeroData(178, 179, Account::getInfo_val_poly));    // 발키리 에이르
        HERO_POOL.add(new HeroData(180, 181, Account::getInfo_larry_poly));  // 필리아
        HERO_POOL.add(new HeroData(182, 183, Account::getInfo_gunter_poly)); // 군터
        HERO_POOL.add(new HeroData(184, 185, Account::getInfo_depor_poly));  // 데포로쥬
        HERO_POOL.add(new HeroData(186, 187, Account::getInfo_deathred_poly));// 진 데스나이트
        HERO_POOL.add(new HeroData(188, 189, Account::getInfo_ecil_poly));   // 이실로테
        HERO_POOL.add(new HeroData(38,  39,  Account::gethero12));           // 크라켄
        HERO_POOL.add(new HeroData(20,  21,  Account::gethero15));           // 메이드 안나
        HERO_POOL.add(new HeroData(24,  25,  Account::gethero16));           // 사단장 하퍼
        HERO_POOL.add(new HeroData(202, 203, Account::getInfo_yozil_poly));  // 질리언
        HERO_POOL.add(new HeroData(204, 205, Account::getInfo_owen_poly));   // 오웬
        HERO_POOL.add(new HeroData(206, 207, Account::getInfo_helv_poly));   // 헬바인
        HERO_POOL.add(new HeroData(18,  19,  Account::gethero17));           // 메르키오르
        HERO_POOL.add(new HeroData(22,  23,  Account::gethero18));           // 발터자르
        HERO_POOL.add(new HeroData(28,  29,  Account::gethero19));           // 세마
        HERO_POOL.add(new HeroData(36,  37,  Account::gethero20));           // 카스파
        HERO_POOL.add(new HeroData(192, 193, Account::getInfo_kerep_poly));  // 케레니스
        HERO_POOL.add(new HeroData(194, 195, Account::getInfo_jowo_poly));   // 조우
    }

    public static void LegendAlchemyF(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
   
        ArrayList<String> args = new ArrayList<>(80);

        // 상단 선택 슬롯 초기화 (4개 모두 빈 이미지 174)
        for (int i = 0; i < 4; i++) {
            args.add("174");
        }

        // 36종 영웅 카드 상태 아이콘 추가 (2장 미만이면 비활성 이미지)
        for (HeroData data : HERO_POOL) {
            int count = data.getter.applyAsInt(acc);
            args.add(count < 2 ? data.offText : data.onText);
        }

        // 36종 영웅 카드 소지 장수 텍스트 추가
        for (HeroData data : HERO_POOL) {
            int count = data.getter.applyAsInt(acc);
            args.add(count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]");
        }

        // 하단 패딩 및 합성 비용
        args.add("174");
        args.add(Config.hero_cost + "원");

        // 패킷 전송 
        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007hallo2", args);
    }

    /** 내부 데이터 관리 클래스 */
    private static class HeroData {
        final int imgOff, imgOn;
        final String offText, onText;
        final ToIntFunction<Account> getter;

        HeroData(int off, int on, ToIntFunction<Account> getter) {
            this.imgOff = off;
            this.imgOn = on;
            this.offText = Integer.toString(off); this.onText = Integer.toString(on);
            this.getter = getter;
        }
    }
}