package l1j.server.server.model.character.skill.helper;

import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;

public final class SummonHelper {
    private SummonHelper() {}

    public static final int MAX_SUMMON_ORDER = 55;

    // order -> summon npcId (0이면 미정의)
    private static final int[] NPC_ID_BY_ORDER = new int[MAX_SUMMON_ORDER + 1];
    // order -> 최소 레벨 (0이면 미정의)
    private static final int[] MIN_LV_BY_ORDER = new int[MAX_SUMMON_ORDER + 1];
    // order -> 요구 카리 (0이면 미정의)
    private static final int[] REQ_CHA_BY_ORDER = new int[MAX_SUMMON_ORDER + 1];

    static {
        // ===== 카리 규칙(구간) =====
        // 1~25: CHA 25
        fillReqCha(1, 25, 25);
        // 31~55: CHA 35
        fillReqCha(31, 55, 35);

        // ===== 소환 스펙 =====
        put(1,  7320137, 28);
        put(2,  7320138, 28);
        put(7,  7320139, 40);
        put(13, 7320140, 52);
        put(19, 7320141, 64);
        put(25, 7320142, 76);
        put(31, 7320143, 80);
        put(37, 7320144, 82);
        put(43, 7320145, 84);
        put(49, 7320146, 86);
        put(55, 7320147, 88);
    }

    private static void fillReqCha(int fromOrder, int toOrder, int chaReq) {
        if (fromOrder < 0) fromOrder = 0;
        if (toOrder > MAX_SUMMON_ORDER) toOrder = MAX_SUMMON_ORDER;
        for (int i = fromOrder; i <= toOrder; i++) {
            REQ_CHA_BY_ORDER[i] = chaReq;
        }
    }

    private static void put(int order, int npcId, int minLv) {
        if (order <= 0 || order > MAX_SUMMON_ORDER) return; // 0은 사용 안 하는 전제
        NPC_ID_BY_ORDER[order] = npcId;
        MIN_LV_BY_ORDER[order] = minLv;
    }

    /** 성공 시 true */
    public static boolean summonByOrder(L1PcInstance pc, int order) {
        if (pc == null) return false;
        if (order <= 0 || order > MAX_SUMMON_ORDER) return false;

        final int summonId = NPC_ID_BY_ORDER[order];
        final int minLv    = MIN_LV_BY_ORDER[order];
        final int chaReq   = REQ_CHA_BY_ORDER[order];

        // 정의 안 된 order
        if (summonId == 0 || minLv == 0 || chaReq == 0) return false;

        // 카리 체크
        if (pc.getAbility().getTotalCha() < chaReq) {
            SC_SystemMessage.send(pc, "카리스마가 부족합니다.");
            return false;
        }

        // 레벨 체크
        if (pc.getLevel() < minLv) {
            SC_ServerMessage.send(pc, 743);
            return false;
        }

        // NPC 템플릿
        final L1Npc npcTemp = NpcTable.getInstance().getTemplate(summonId);
        if (npcTemp == null) return false;

        // 소환
        final L1SummonInstance summon = new L1SummonInstance(npcTemp, pc);
        summon.setPetcost(0);
        return true;
    }
}
