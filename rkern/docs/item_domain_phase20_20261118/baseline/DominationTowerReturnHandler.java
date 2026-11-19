package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.util.HashMap;
import java.util.Map;

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

    private final Map<Integer, int[]> _towerLocs = new HashMap<>();

    private DominationTowerReturnHandler() {
        // 데이터 초기화 (지배의 탑 1층 ~ 10층)
        _towerLocs.put(12852, new int[]{ 32728, 32800, 12852 });
        _towerLocs.put(12853, new int[]{ 32721, 32803, 12853 });
        _towerLocs.put(12854, new int[]{ 32724, 32802, 12854 });
        _towerLocs.put(12855, new int[]{ 32597, 32863, 12855 });
        _towerLocs.put(12856, new int[]{ 32592, 32866, 12856 });
        _towerLocs.put(12857, new int[]{ 32602, 32865, 12857 });
        _towerLocs.put(12858, new int[]{ 32604, 32866, 12858 });
        _towerLocs.put(12859, new int[]{ 32593, 32866, 12859 });
        _towerLocs.put(12860, new int[]{ 32602, 32866, 12860 });
        _towerLocs.put(12861, new int[]{ 32732, 32802, 12861 });
        _towerLocs.put(12862, new int[]{ 32639, 32797, 12862 });
    }

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

        int[] loc = null;
        int currentMap = pc.getMapId();

        // 지배의 탑 좌표 가져오기 
        if (_towerLocs.containsKey(currentMap)) {
            loc = _towerLocs.get(currentMap);
        } else {
            // 지배의 탑이 아니면 일반 귀환 로직 실행
            if (pc.getMap().isEscapable() || pc.isGm()) {
                loc = Getback.GetBack_Location(pc, false);
            }
        }

        // 최종 텔레포트 처리
        if (loc != null) {
            final int destX = loc[0];
            final int destY = loc[1];
            final int destMap = loc[2];
            ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                pc.start_teleport(destX, destY, destMap, pc.getHeading(), 169, true, pc.getAutoHunt());
                pc.cancelAbsoluteBarrier();
            });
            return true;
        } else {
            SC_ServerMessage.send(pc, 647); // 귀환 불가 메시지
            return false;
        }
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