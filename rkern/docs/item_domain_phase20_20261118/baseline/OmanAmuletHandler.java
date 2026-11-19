package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 오만의 탑 이동/지배 부적 핸들러
 */
public class OmanAmuletHandler {

    private static final OmanAmuletHandler instance = new OmanAmuletHandler();
    public static OmanAmuletHandler getInstance() { return instance; }

    // 층별 좌표 정보 저장 전용 클래스
    private static class OmanLoc {
        final int mapId, x, y;
        OmanLoc(int mapId, int x, int y) {
            this.mapId = mapId; this.x = x; this.y = y;
        }
    }

    private final Map<Integer, OmanLoc> _floorLocs = new HashMap<>();

    private OmanAmuletHandler() {
        // 1층 ~ 10층 좌표 세팅
        _floorLocs.put(1,  new OmanLoc(101, 32735, 32798));
        _floorLocs.put(2,  new OmanLoc(102, 32727, 32803));
        _floorLocs.put(3,  new OmanLoc(103, 32726, 32803));
        _floorLocs.put(4,  new OmanLoc(104, 32620, 32859));
        _floorLocs.put(5,  new OmanLoc(105, 32601, 32866));
        _floorLocs.put(6,  new OmanLoc(106, 32611, 32863));
        _floorLocs.put(7,  new OmanLoc(107, 32618, 32866));
        _floorLocs.put(8,  new OmanLoc(108, 32602, 32867));
        _floorLocs.put(9,  new OmanLoc(109, 32613, 32866));
        _floorLocs.put(10, new OmanLoc(110, 32730, 32803));
    }

    public void clickItem(L1PcInstance pc, int itemId) {
        // 1. 기본 안전성 체크
        if (pc == null) return;
        if (pc.isParalyzed() || pc.isSleeped() || pc.isDead()) return;
        
        // 2. 텔레포트 금지 스킬 체크 
        if (isTeleportBlocked(pc)) {
            SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
            return;
        }

        if (!pc.getMap().isEscapable()) {
            SC_SystemMessage.send(pc, "이곳에서는 부적을 사용할 수 없습니다.");
            return;
        }

        // 이동부적(830012~830021) -> 층 = itemId - 830011
        // 지배부적(830022~830031) -> 층 = itemId - 830021
        int floor;
        if (itemId >= 830012 && itemId <= 830021) {
            floor = itemId - 830011;
        } else if (itemId >= 830022 && itemId <= 830031) {
            floor = itemId - 830021;
        } else {
            return;
        }

        //  좌표 데이터 획득 및 텔레포트
        OmanLoc loc = _floorLocs.get(floor);
        if (loc != null) {
            // 아덴 마을 동상 거리 체크가 필요하다면 여기서 수행
            // if (!isNearStatue(pc)) { return; }

            pc.start_teleport(loc.x, loc.y, loc.mapId, pc.getHeading(), 169, true, pc.getAutoHunt());
        }
    }

    private boolean isTeleportBlocked(L1PcInstance pc) {
        return pc.hasSkillEffect(L1SkillId.쉐도우제약) || 
               pc.hasSkillEffect(L1SkillId.이터디스) || 
               pc.hasSkillEffect(L1SkillId.버닝샷);
    }
    
    // 필요 시 동상 좌표 체크 로직 활성화 가능
    /*
    private boolean isNearStatue(L1PcInstance pc) {
        return (pc.getX() >= 33926 && pc.getX() <= 33930 && pc.getY() >= 33342 && pc.getY() <= 33347);
    }
    */
}