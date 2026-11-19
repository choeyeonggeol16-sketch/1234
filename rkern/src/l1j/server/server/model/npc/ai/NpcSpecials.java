package l1j.server.server.model.npc.ai;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 특수 NPC 행동 레지스트리.
 */
public final class NpcSpecials {

    public interface Special {
        /** true = 이 틱 소비(일반 AI 스킵), false = 일반 AI 계속 */
        boolean tick(L1NpcInstance npc);
    }

    private static final HashMap<Integer, Special> REGISTRY = new HashMap<>();

    static {
        // ── 시던 관련 ──
        register(91236, npc -> {
            if (Config.seadunship == 1) {
                int dir = npc.moveDirection(npc.getX(), npc.getY() - 10);
                npc.setDirectionMove(dir);
            }
            return false;   // 기존 동작 유지: seadunship==0이어도 일반 AI 계속
        });
        register(91234, npc -> {
            if (Config.seadunship == 1) {
                int dir = npc.moveDirection(npc.getX(), npc.getY() - 10);
                npc.setDirectionMove(dir);
            }
            return false;
        });
        register(91237, npc -> {
            if (Config.seadunship == 1) {
                int dir = npc.moveDirection(npc.getX(), npc.getY() - 10);
                npc.setDirectionMove(dir);
            }
            return false;
        });

        // ── 모래 폭풍 (5095) 
        register(5095, npc -> {
        	for (L1Object obj : npc.getKnownObjects()) {
        	    if (obj instanceof L1PcInstance) {
        	        L1PcInstance pc = (L1PcInstance) obj;
        	        if (!pc.isDead() && npc.getLocation().getTileLineDistance(pc.getLocation()) <= 2) {
        	            L1Location loc = pc.getLocation().randomLocation(30, true);
                        pc.start_teleport(loc.getX(), loc.getY(), (short) loc.getMapId(),
                                pc.getHeading(), 169, true, pc.getAutoHunt());
        	        }
        	    }
        	}
            return false;
        });

        // ── 하이네 필드 트랩 (7210040)
        register(7210040, npc -> {
        	for (L1Object obj : npc.getKnownObjects()) {
        	    if (obj instanceof L1PcInstance) {
        	        L1PcInstance pc = (L1PcInstance) obj;
        	        if (!pc.isDead() && npc.getLocation().getTileLineDistance(pc.getLocation()) <= 2) {
        	                int chance = ThreadLocalRandom.current().nextInt(100) + 1;
        	                if (chance < 40)      pc.start_teleport(33445, 33130, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
        	                else if (chance < 60) pc.start_teleport(33428, 33244, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
        	                else if (chance < 70) pc.start_teleport(33474, 33165, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
        	                else if (chance < 80) pc.start_teleport(33495, 33197, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
        	        }
        	    }
        	}
            return false;
        });
    }

    public static void register(int npcId, Special s) {
        REGISTRY.put(npcId, s);
    }

    public static Special get(int npcId) {
        return REGISTRY.get(npcId);
    }
}