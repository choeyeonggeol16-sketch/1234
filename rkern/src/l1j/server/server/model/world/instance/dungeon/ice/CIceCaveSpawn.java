package l1j.server.server.model.world.instance.dungeon.ice;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.world.cleanup.MapCleaner;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.shared.GameRandom;

/** Shared door and NPC placement for encounters using the ice-cave template. */
public final class CIceCaveSpawn {
    private static final Logger LOG = Logger.getLogger(CIceCaveSpawn.class.getName());

    private CIceCaveSpawn() {}

    public static L1NpcInstance spawn(int x, int y, short mapId, int heading, int npcId, int radius) {
        final L1NpcInstance npc = CObjectDB.CreateNPC(npcId);
        if (npc == null) throw new IllegalStateException("Missing ice-cave NPC template " + npcId);
        npc.setId(IdFactory.getInstance().nextId());
        npc.setMap(mapId);
        if (radius == 0) {
            npc.getLocation().set(x, y, mapId);
            npc.getLocation().forward(heading);
        } else {
            boolean placed = false;
            for (int attempt = 0; attempt < 50; attempt++) {
                npc.setX(x + GameRandom.nextInt(radius) - GameRandom.nextInt(radius));
                npc.setY(y + GameRandom.nextInt(radius) - GameRandom.nextInt(radius));
                if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                npc.getLocation().set(x, y, mapId);
                npc.getLocation().forward(heading);
            }
        }
        if (npc instanceof L1DoorInstance door) {
            switch (npcId) {
                case 5147 -> setDoor(door, 32816, 32821, 1);
                case 5148 -> setDoor(door, 32804, 32809, 1);
                case 5149 -> setDoor(door, 32853, 32858, 1);
                case 5150 -> setDoor(door, 32760, 32764, 0);
                case 5151 -> setDoor(door, 32918, 32923, 1);
                default -> { }
            }
        }
        npc.setHomeX(npc.getX());
        npc.setHomeY(npc.getY());
        npc.setHeading(heading);

        CWorld.getInstance().storeAndEnterWorld(npc);
        npc.refreshLight();
        npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);
        return npc;
    }

    public static boolean pause(long delayMs) {
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void finish(L1Map map, int exitX, int exitY) {
        final CWorld world = CWorld.getInstance();
        for (L1Object object : world.snapshotZoneOccupants(map)) {
            if (object.getMap() != map) continue;
            try {
                if (object instanceof L1PcInstance pc) {
                    pc.start_teleport(exitX, exitY, 4, 5, 169, true, false);
                } else if (object instanceof L1NpcInstance npc && !CInstantZoneManager.isPlayerCompanion(npc)) {
                    npc.deleteMe();
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Ice-cave cleanup failed for map " + map.getId(), failure);
            }
        }
        MapCleaner.removeGroundItems(map);
    }

    private static void setDoor(L1DoorInstance door, int left, int right, int direction) {
        door.setLeftEdgeLocation(left);
        door.setRightEdgeLocation(right);
        door.setDirection(direction);
    }
}
