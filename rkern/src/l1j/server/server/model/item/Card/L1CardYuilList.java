package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 유일(기르타스) 변신 카드 리스트 및 도감 정보 핸들러 
 */
public class L1CardYuilList {

    // 유일 등급 카드 이미지 번호 (2856: OFF, 2857: ON)
    private static final int GIRTAS_IMG_OFF = 2856;
    private static final int GIRTAS_IMG_ON  = 2857;

    /**
     * 유일 카드 간편 리스트
     */
    public static void showSimpleList(L1PcInstance pc) {
        if (isInvalid(pc)) return;

        Account acc = pc.getAccount();
        List<String> args = new ArrayList<>();

        // 보유 여부에 따른 아이콘 결정
        int girtasCount = acc.getInfo_kir_poly();
        args.add(girtasCount == 0 ? String.valueOf(GIRTAS_IMG_OFF) : String.valueOf(GIRTAS_IMG_ON));

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007santa7", args);
    }

    /**
     * 유일 카드 상세 도감 정보 
     */
    public static void showDetailedInfo(L1PcInstance pc) {
        if (isInvalid(pc)) return;

        Account acc = pc.getAccount();
        List<String> args = new ArrayList<>();

        // 아이콘 추가
        int girtasCount = acc.getInfo_kir_poly();
        args.add(girtasCount == 0 ? String.valueOf(GIRTAS_IMG_OFF) : String.valueOf(GIRTAS_IMG_ON));
        
        // 수량 텍스트 추가
        args.add(formatCount(girtasCount));

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "neiyuilt", args);
    }

    /**
     * 공통 예외 체크 
     */
    private static boolean isInvalid(L1PcInstance pc) {
        return pc == null || pc.isDead() || pc.getAccount() == null;
    }

    /**
     * 수량 표시 포맷 (시리즈 공통 스타일)
     */
    private static String formatCount(int count) {
        return "[ " + count + "장 ]";
    }
}