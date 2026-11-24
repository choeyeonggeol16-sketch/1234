package l1j.server.server.model.npc.ai;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

/** L1 scripted NPC actions, dispatched by the existing NPC AI owner. */
public final class CNpcSpecialAction {
    private CNpcSpecialAction() {}

    public static final byte NONE = 0;
    private static final byte SHIP_MOVE = 1;
    private static final byte SAND_STORM = 2;
    private static final byte FIELD_TRAP = 3;
    private static final int ANCIENT_GUARDIAN = 707026;

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

    /** L1-specific script policy; the common instance retains only hooks. */
    public static void OnCreated(final L1NpcInstance npc, final int npcId) {
        if (npcId == ANCIENT_GUARDIAN) {
            npc.setStatus(6);
        }
    }

    public static boolean CheckCondition(final L1NpcInstance npc) {
        if (npc.getNpcId() == ANCIENT_GUARDIAN && HpPercent(npc) > 90) {
            npc.setSleepTime(200);
            return true;
        }
        return false;
    }

    public static void OnHpChanged(final L1NpcInstance npc, final int hp) {
        if (npc.getNpcId() != ANCIENT_GUARDIAN || hp <= 0) return;
        synchronized (npc) {
            if (npc.isDestroyed() || npc.isDead() || npc.getCurrentHp() <= 0
                    || npc.getStatus() != 6 || HpPercent(npc) > 90) return;
            // Claim the awakening before emitting the one-shot animation.
            npc.setStatus(0);
            SC_DoActionGFX.broadcast(npc, 4);
        }
    }

    private static int HpPercent(final L1NpcInstance npc) {
        // Preserve the original truncation and floating-point boundary.
        return (int) (((double) npc.getCurrentHp() / (double) npc.getMaxHp()) * 100D);
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
