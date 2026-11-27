package l1j.server.server.model.item.function;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.shared.GameRandom;

final class ClanRaidNpcSpawn {
    private ClanRaidNpcSpawn() {}

    static void spawn(
            int x,
            int y,
            short mapId,
            int heading,
            int npcId,
            int randomRange,
            int roomnumber) {

        try {
            final L1NpcInstance npc =
                    CObjectDB.CreateNPC(npcId);

            npc.setId(IdFactory.getInstance().nextId());
            npc.setMap(mapId);

            if (randomRange == 0) {
                npc.getLocation().set(x, y, mapId);
                npc.getLocation().forward(heading);

            } else {
                int tryCount = 0;

                do {
                    tryCount++;

                    npc.setX(
                            x
                            + GameRandom.nextInt(randomRange)
                            - GameRandom.nextInt(randomRange));

                    npc.setY(
                            y
                            + GameRandom.nextInt(randomRange)
                            - GameRandom.nextInt(randomRange));

                    if (npc.getMap().isInMap(npc.getLocation())
                            && npc.getMap().isPassable(npc.getLocation())) {
                        break;
                    }

                } while (tryCount < 50);

                if (tryCount >= 50) {
                    npc.getLocation().forward(heading);
                }
            }

            if (npc instanceof L1DoorInstance) {
                final L1DoorInstance door =
                        (L1DoorInstance) npc;

                switch (npc.getNpcId()) {
                case 5147:
                    door.setLeftEdgeLocation(32816);
                    door.setRightEdgeLocation(32821);
                    door.setDirection(1);
                    break;

                case 5148:
                    door.setLeftEdgeLocation(32804);
                    door.setRightEdgeLocation(32809);
                    door.setDirection(1);
                    break;

                case 5149:
                    door.setLeftEdgeLocation(32853);
                    door.setRightEdgeLocation(32858);
                    door.setDirection(1);
                    break;

                case 5150:
                    door.setLeftEdgeLocation(32760);
                    door.setRightEdgeLocation(32764);
                    door.setDirection(0);
                    break;

                case 5151:
                    door.setLeftEdgeLocation(32918);
                    door.setRightEdgeLocation(32923);
                    door.setDirection(1);
                    break;

                default:
                    break;
                }
            }

            npc.setHomeX(npc.getX());
            npc.setHomeY(npc.getY());
            npc.setHeading(heading);

            CWorld.getInstance().storeAndEnterWorld(npc);

            npc.refreshLight();
            npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
