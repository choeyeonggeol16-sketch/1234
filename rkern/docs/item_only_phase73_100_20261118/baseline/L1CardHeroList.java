package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 영웅 변신 카드 도감 및 카테고리별 리스트 출력 
 */
public class L1CardHeroList {

    // 카드 카테고리 정의 
    private static final int CAT_MELEE = 1;  // 근거리
    private static final int CAT_RANGE = 2;  // 원거리
    private static final int CAT_MAGIC = 4;  // 마법
    private static final int CAT_ALL   = 7;  // 전체

    // 영웅 카드 메타데이터 리스트
    private static final List<HeroCardData> HERO_POOL = new ArrayList<>();

    static {
        // [이미지OFF, 이미지ON, 카테고리] 순서로 등록
        HERO_POOL.add(new HeroCardData(176, 177, CAT_ALL));   // 천상의 기사
        HERO_POOL.add(new HeroCardData(26,  27,  CAT_ALL));   // 사이하
        HERO_POOL.add(new HeroCardData(30,  31,  CAT_ALL));   // 에바의 수호자
        HERO_POOL.add(new HeroCardData(4,   5,   CAT_MELEE)); // 경비병(창)그린
        HERO_POOL.add(new HeroCardData(6,   7,   CAT_MELEE)); // 경비병(창)레드
        HERO_POOL.add(new HeroCardData(8,   9,   CAT_RANGE)); // 경비병(활)그린
        HERO_POOL.add(new HeroCardData(10,  11,  CAT_RANGE)); // 경비병(활)레드
        HERO_POOL.add(new HeroCardData(0,   1,   CAT_MELEE)); // 가시공주 아이비
        HERO_POOL.add(new HeroCardData(2,   3,   CAT_MELEE)); // 각성 켄라우헬
        HERO_POOL.add(new HeroCardData(12,  13,  CAT_MELEE)); // 실버 쉐도우 (금빛 블레이즈)
        HERO_POOL.add(new HeroCardData(190, 191, CAT_MELEE)); // 어쌔신 마스터
        HERO_POOL.add(new HeroCardData(14,  15,  CAT_MELEE)); // 랜스 마스터
        HERO_POOL.add(new HeroCardData(16,  17,  CAT_MELEE)); // 로즈마리
        HERO_POOL.add(new HeroCardData(196, 197, CAT_MELEE)); // 유니크 나이트
        HERO_POOL.add(new HeroCardData(32,  33,  CAT_MELEE)); // 유니크 랜서
        HERO_POOL.add(new HeroCardData(200, 201, CAT_RANGE)); // 유니크 레인저
        HERO_POOL.add(new HeroCardData(198, 199, CAT_MAGIC)); // 유니크 매지션
        HERO_POOL.add(new HeroCardData(34,  35,  CAT_MELEE)); // 질서의 연금술사
        HERO_POOL.add(new HeroCardData(178, 179, CAT_MELEE)); // 발키리 에이르
        HERO_POOL.add(new HeroCardData(180, 181, CAT_MELEE)); // 필리아
        HERO_POOL.add(new HeroCardData(182, 183, CAT_MELEE)); // 군터
        HERO_POOL.add(new HeroCardData(184, 185, CAT_MELEE)); // 데포로쥬
        HERO_POOL.add(new HeroCardData(186, 187, CAT_MELEE)); // 진 데스나이트
        HERO_POOL.add(new HeroCardData(188, 189, CAT_MELEE)); // 이실로테
        HERO_POOL.add(new HeroCardData(38,  39,  CAT_MELEE)); // 크라켄
        HERO_POOL.add(new HeroCardData(20,  21,  CAT_RANGE)); // 메이드 안나
        HERO_POOL.add(new HeroCardData(24,  25,  CAT_RANGE)); // 사단장 하퍼
        HERO_POOL.add(new HeroCardData(202, 203, CAT_RANGE)); // 질리언
        HERO_POOL.add(new HeroCardData(204, 205, CAT_RANGE)); // 오웬
        HERO_POOL.add(new HeroCardData(206, 207, CAT_RANGE)); // 헬바인
        HERO_POOL.add(new HeroCardData(18,  19,  CAT_MAGIC)); // 메르키오르
        HERO_POOL.add(new HeroCardData(22,  23,  CAT_MAGIC)); // 발터자르
        HERO_POOL.add(new HeroCardData(28,  29,  CAT_MAGIC)); // 세마
        HERO_POOL.add(new HeroCardData(36,  37,  CAT_MAGIC)); // 카스파
        HERO_POOL.add(new HeroCardData(192, 193, CAT_MAGIC)); // 케레니스
        HERO_POOL.add(new HeroCardData(194, 195, CAT_MAGIC)); // 조우
    }

    /** 도감 전체 정보 (아이콘 36개 + 텍스트 36개) */
    public static void cardinfo(L1PcInstance pc) {
        if (pc == null || pc.getAccount() == null) return;
        
        Account acc = pc.getAccount();
        int[] counts = getCounts(acc);
        ArrayList<String> args = new ArrayList<>(HERO_POOL.size() * 2);

        // 아이콘 이미지 36개 추가
        for (int i = 0; i < HERO_POOL.size(); i++) {
            HeroCardData data = HERO_POOL.get(i);
            args.add(counts[i] == 0 ? String.valueOf(data.imgOff) : String.valueOf(data.imgOn));
        }

        //  소지 장수 텍스트 36개 추가
        for (int count : counts) {
            args.add(count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]");
        }

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "neiherot", args);
    }

    /** 근거리 전용 (rudolph2) */
    public static void cardlistA(L1PcInstance pc) {
        sendFilteredList(pc, CAT_MELEE, "2007rudolph2");
    }

    /** 원거리 전용 (rudolph3) */
    public static void cardlistB(L1PcInstance pc) {
        sendFilteredList(pc, CAT_RANGE, "2007rudolph3");
    }

    /** 마법 전용 (rudolph4) */
    public static void cardlistC(L1PcInstance pc) {
        sendFilteredList(pc, CAT_MAGIC, "2007rudolph4");
    }

    /** 필터링된 리스트 전송 공통 로직 */
    private static void sendFilteredList(L1PcInstance pc, int category, String html) {
        if (pc == null || pc.getAccount() == null || pc.isDead()) return;

        Account acc = pc.getAccount();
        int[] counts = getCounts(acc);
        ArrayList<String> args = new ArrayList<>();

        for (int i = 0; i < HERO_POOL.size(); i++) {
            HeroCardData data = HERO_POOL.get(i);
            if ((data.category & category) != 0) {
                args.add(counts[i] == 0 ? String.valueOf(data.imgOff) : String.valueOf(data.imgOn));
            }
        }
        SC_NPCTalkReturn.sendList(pc, pc.getId(), html, args);
    }

    /** 계정의 모든 영웅 카드 수량을 배열로 반환 */
    private static int[] getCounts(Account acc) {
        return new int[] {
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
    }

    /** 내부 데이터 클래스 */
    private static class HeroCardData {
        int imgOff, imgOn, category;
        HeroCardData(int off, int on, int cat) {
            this.imgOff = off; this.imgOn = on; this.category = cat;
        }
    }
}