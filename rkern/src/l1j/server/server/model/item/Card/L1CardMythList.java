package l1j.server.server.model.item.Card;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/**
 * 신화 변신 카드 리스트 및 도감 정보 핸들러 
 */
public class L1CardMythList {

    // Card icons never change between players. Keep only account counts local.
    private static final int[] MALE_OFF = {144, 2858, 2862, 2866, 2870, 2874, 2876, 140};
    private static final int[] MALE_ON  = {145, 2859, 2863, 2867, 2871, 2875, 2877, 141};
    private static final int[] FEMALE_OFF = {146, 2860, 2864, 2868, 2872, 2874, 2876, 142};
    private static final int[] FEMALE_ON  = {147, 2861, 2865, 2869, 2873, 2875, 2877, 143};

    private static final String[] MALE_OFF_TEXT = toText(MALE_OFF);
    private static final String[] MALE_ON_TEXT = toText(MALE_ON);
    private static final String[] FEMALE_OFF_TEXT = toText(FEMALE_OFF);
    private static final String[] FEMALE_ON_TEXT = toText(FEMALE_ON);

    private static String[] toText(int[] ints) {
        final String[] result = new String[ints.length];
        for (int i = 0; i < ints.length; i++) result[i] = Integer.toString(ints[i]);
        return result;
    }

    private static String imageText(int count, int sex, int slot) {
        return sex == 0 ? (count == 0 ? MALE_OFF_TEXT[slot] : MALE_ON_TEXT[slot])
                : (count == 0 ? FEMALE_OFF_TEXT[slot] : FEMALE_ON_TEXT[slot]);
    }

    /**
     * 특정 사냥터/이벤트용 간편 리스트 
     */
    public static void cardlist(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        int sex = pc.get_sex();
        List<String> args = new ArrayList<>(5);

        int classImg = 0;
        if (pc.isCrown())          classImg = getImg(acc.getInfo_class_poly(), sex, 144, 145, 146, 147);
        else if (pc.isKnight())    classImg = getImg(acc.getInfo_ki_poly(),    sex, 2858, 2859, 2860, 2861);
        else if (pc.isElf())       classImg = getImg(acc.getInfo_el_poly(),    sex, 2862, 2863, 2864, 2865);
        else if (pc.isWizard())    classImg = getImg(acc.getInfo_wi_poly(),    sex, 2866, 2867, 2868, 2869);
        else if (pc.isDarkelf())   classImg = getImg(acc.getInfo_da_poly(),    sex, 2870, 2871, 2872, 2873);
        else if (pc.isWarrior())       classImg = getImg(acc.getInfo_wa_poly(),    sex, 2874, 2875, 2874, 2875);
        else if (pc.isDragonknight()) classImg = getImg(acc.getInfo_drag_poly(), sex, 2876, 2877, 2876, 2877);
        else if (pc.isBlackwizard()) classImg = getImg(acc.getInfo_il_poly(),   sex, 140, 141, 142, 143);
        
        args.add(String.valueOf(classImg));

        // 고정 신화 아이콘 (3개: 플레데스, 플레바포, 총사)
        args.add(acc.getInfo_dsn_poly() == 0 ? "148" : "149");
        args.add(acc.getInfo_bapo_poly() == 0 ? "150" : "151");
        
        args.add(String.valueOf(getImg(acc.getmyth01(), sex, 70, 71, 72, 73)));
        args.add(String.valueOf(getImg(acc.getInfo_il_poly(), sex, 140, 141, 142, 143)));

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007santa5", args);
    }

    /**
     * 신화 카드 전체 도감 정보
     */
    public static void cardinfo(L1PcInstance pc) {
        if (pc == null || pc.isDead() || pc.getAccount() == null) return;

        Account acc = pc.getAccount();
        int sex = pc.get_sex();
        List<String> args = new ArrayList<>(22);

        // Snapshot the same 8 account values in the original order.
        // The previous int[8][5] constructed nine arrays on every UI open.
        final int[] counts = {
            acc.getInfo_class_poly(), acc.getInfo_ki_poly(), acc.getInfo_el_poly(),
            acc.getInfo_wi_poly(), acc.getInfo_da_poly(), acc.getInfo_wa_poly(),
            acc.getInfo_drag_poly(), acc.getInfo_il_poly()
        };

        for (int i = 0; i < counts.length; i++) {
            args.add(imageText(counts[i], sex, i));
        }

        // 특수 신화 아이콘 2개 (데스, 바포)
        args.add(acc.getInfo_dsn_poly() == 0 ? "148" : "149");
        args.add(acc.getInfo_bapo_poly() == 0 ? "150" : "151");

        // 수량 텍스트 10개 추가
        for (int count : counts) args.add(formatCount(count));
        args.add(formatCount(acc.getInfo_dsn_poly()));
        args.add(formatCount(acc.getInfo_bapo_poly()));

        // 총사 정보 (아이콘 + 수량)
        args.add(String.valueOf(getImg(acc.getmyth01(), sex, 70, 71, 72, 73)));
        args.add(formatCount(acc.getmyth01()));

        // 성별에 따른 HTML 호출
        String html = (sex == 0) ? "neimytht" : "neimytht2";
        SC_NPCTalkReturn.sendList(pc, pc.getId(), html, args);
    }

    private static int getImg(int count, int sex, int mOff, int mOn, int fOff, int fOn) {
        if (sex == 0) return (count == 0) ? mOff : mOn;
        else          return (count == 0) ? fOff : fOn;
    }

    private static String formatCount(int count) {
        return "[ " + count + "장 ]";
    }
}