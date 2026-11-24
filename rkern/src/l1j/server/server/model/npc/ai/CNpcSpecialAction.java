package l1j.server.server.model.npc.ai;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** L1 scripted NPC actions, dispatched by the existing NPC AI owner. */
public final class CNpcSpecialAction {
    private CNpcSpecialAction() {}

    public static final byte NONE = 0;
    private static final byte SHIP_MOVE = 1;
    private static final byte SAND_STORM = 2;
    private static final byte FIELD_TRAP = 3;

    public static byte GetActionId(final int npcId) {
        return switch (npcId) {
            case 91236, 91234, 91237 -> SHIP_MOVE;
            case 5095 -> SAND_STORM;
            case 7210040 -> FIELD_TRAP;
            default -> NONE;
        };
    }

    public static void Execute(final L1NpcInstance npc, final byte action) {
        switch (action) {
            case SHIP_MOVE -> MoveShip(npc);
            case SAND_STORM -> SandStorm(npc);
            case FIELD_TRAP -> FieldTrap(npc);
            default -> { }
        }
    }

    private static void MoveShip(final L1NpcInstance npc) {
        if (Config.seadunship == 1) {
            final int dir = npc.moveDirection(npc.getX(), npc.getY() - 10);
            npc.setDirectionMove(dir);
        }
    }

    private static void SandStorm(final L1NpcInstance npc) {
        for (L1Object obj : npc.getKnownObjects()) {
            if (obj instanceof L1PcInstance pc
                    && !pc.isDead()
                    && npc.getLocation().getTileLineDistance(pc.getLocation()) <= 2) {
                final L1Location loc = pc.getLocation().randomLocation(30, true);
                pc.start_teleport(loc.getX(), loc.getY(), (short) loc.getMapId(),
                        pc.getHeading(), 169, true, pc.getAutoHunt());
            }
        }
    }

    private static void FieldTrap(final L1NpcInstance npc) {
        for (L1Object obj : npc.getKnownObjects()) {
            if (obj instanceof L1PcInstance pc
                    && !pc.isDead()
                    && npc.getLocation().getTileLineDistance(pc.getLocation()) <= 2) {
                final int chance = ThreadLocalRandom.current().nextInt(100) + 1;
                if (chance < 40) pc.start_teleport(33445, 33130, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
                else if (chance < 60) pc.start_teleport(33428, 33244, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
                else if (chance < 70) pc.start_teleport(33474, 33165, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
                else if (chance < 80) pc.start_teleport(33495, 33197, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
            }
        }
    }
}
