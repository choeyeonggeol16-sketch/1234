package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 오만의 탑 이동/지배 부적 핸들러
 */
public class OmanAmuletHandler {

    private static final OmanAmuletHandler instance = new OmanAmuletHandler();
    public static OmanAmuletHandler getInstance() { return instance; }

    // Index = floor - 1. Private tables are never exposed to callers.
    private static final int[] FLOOR_X = {32735, 32727, 32726, 32620, 32601, 32611, 32618, 32602, 32613, 32730};
    private static final int[] FLOOR_Y = {32798, 32803, 32803, 32859, 32866, 32863, 32866, 32867, 32866, 32803};

    private OmanAmuletHandler() {}

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
        if (floor >= 1 && floor <= FLOOR_X.length) {
            // 아덴 마을 동상 거리 체크가 필요하다면 여기서 수행
            // if (!isNearStatue(pc)) { return; }
            pc.start_teleport(FLOOR_X[floor - 1], FLOOR_Y[floor - 1], 100 + floor,
                    pc.getHeading(), 169, true, pc.getAutoHunt());
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