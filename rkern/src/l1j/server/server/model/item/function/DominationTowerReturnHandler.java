package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;

import l1j.server.server.model.Getback;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 지배의 탑 전용 귀환 로직 핸들러
 */
public class DominationTowerReturnHandler {

    private static final DominationTowerReturnHandler instance = new DominationTowerReturnHandler();
    public static DominationTowerReturnHandler getInstance() { return instance; }

    // 12852~12862: immutable tower destinations; no HashMap or per-floor int[] retained.
    private DominationTowerReturnHandler() {}

    /**
     * 귀환 주문서 실행 메인 메서드
     * @return 귀환 성공 여부
     */
    public boolean handleReturn(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null) return false;

        // 텔포 금지 스킬 체크
        if (isTeleportBlocked(pc)) {
            SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
            return false;
        }

        // 특수 맵 체크 
        if (pc.getMapId() == 13005) {
            SC_SystemMessage.send(pc, "이곳에서 사용할 수 없습니다.");
            return false;
        }

        final int destX, destY, destMap;
        final int currentMap = pc.getMapId();
        switch (currentMap) {
            case 12852 -> { destX = 32728; destY = 32800; destMap = 12852; }
            case 12853 -> { destX = 32721; destY = 32803; destMap = 12853; }
            case 12854 -> { destX = 32724; destY = 32802; destMap = 12854; }
            case 12855 -> { destX = 32597; destY = 32863; destMap = 12855; }
            case 12856 -> { destX = 32592; destY = 32866; destMap = 12856; }
            case 12857 -> { destX = 32602; destY = 32865; destMap = 12857; }
            case 12858 -> { destX = 32604; destY = 32866; destMap = 12858; }
            case 12859 -> { destX = 32593; destY = 32866; destMap = 12859; }
            case 12860 -> { destX = 32602; destY = 32866; destMap = 12860; }
            case 12861 -> { destX = 32732; destY = 32802; destMap = 12861; }
            case 12862 -> { destX = 32639; destY = 32797; destMap = 12862; }
            default -> {
                // Preserve the original escapable/GM test and Getback fallback.
                if (!(pc.getMap().isEscapable() || pc.isGm())) {
                    SC_ServerMessage.send(pc, 647);
                    return false;
                }
                int[] loc = Getback.GetBack_Location(pc, false);
                if (loc == null) {
                    SC_ServerMessage.send(pc, 647);
                    return false;
                }
                destX = loc[0];
                destY = loc[1];
                destMap = loc[2];
            }
        }

        ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
            try {
                pc.start_teleport(destX, destY, destMap, pc.getHeading(), 169, true, pc.getAutoHunt());
                pc.cancelAbsoluteBarrier();
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return true;
    }

    /**
     * 텔레포트 차단 스킬 상태 확인
     */
    private boolean isTeleportBlocked(L1PcInstance pc) {
        return pc.hasSkillEffect(L1SkillId.쉐도우제약) || 
               pc.hasSkillEffect(L1SkillId.이터디스) || 
               pc.hasSkillEffect(L1SkillId.버닝샷);
    }
}