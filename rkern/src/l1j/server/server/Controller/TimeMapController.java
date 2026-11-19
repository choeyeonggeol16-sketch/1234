/** 타임맵의 만료·추방을 처리하는 기존 주기 컨트롤러. */
package l1j.server.server.Controller;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1TimeMap;

public final class TimeMapController implements Runnable {
    public static final int SLEEP_TIME = 1000;
    private static final Logger LOG = Logger.getLogger(TimeMapController.class.getName());
    private static final TimeMapController INSTANCE = new TimeMapController();

    // Single controller monitor protects registration and tick iteration.
    // No per-second snapshot array or iterator allocation.
    private final ArrayList<L1TimeMap> mapList = new ArrayList<>();

    public static TimeMapController getInstance() {
        return INSTANCE;
    }

    private TimeMapController() { }

    @Override
    public synchronized void run() {
        for (int i = 0; i < mapList.size();) {
            final L1TimeMap timeMap = mapList.get(i);
            try {
                if (!timeMap.count()) {
                    i++;
                    continue;
                }
                for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                    if (pc == null || timeMap.getId() != pc.getMapId()) continue;
                    switch (pc.getMapId()) {
                    case 72:
                    case 73:
                    case 74:
                        pc.start_teleport(34056, 32279, 4, 5, 169, true, false);
                        break;
                    case 460:
                    case 461:
                    case 462:
                    case 463:
                    case 464:
                    case 465:
                    case 466:
                        pc.start_teleport(32664, 32855, 457, 5, 169, true, false);
                        break;
                    case 470:
                    case 471:
                    case 472:
                    case 473:
                    case 474:
                        pc.start_teleport(32663, 32853, 467, 5, 169, true, false);
                        break;
                    case 475:
                    case 476:
                    case 477:
                    case 478:
                        pc.start_teleport(32660, 32876, 468, 5, 169, true, false);
                        break;
                    default:
                        break;
                    }
                }
                final var door = DoorSpawnTable.getInstance().getDoor(timeMap.getDoor());
                if (door != null) door.close();
                mapList.remove(i);
            } catch (Exception failure) {
                i++;
                // A bad map must not prevent unrelated expiry events from
                // running on this tick. Retain it for a later retry.
                LOG.log(Level.WARNING, "Time map expiry failed, map=" + timeMap.getId(), failure);
            }
        }
    }

    public synchronized void add(L1TimeMap map) {
        if (map == null) return;
        final int id = map.getId();
        for (int i = 0, n = mapList.size(); i < n; i++) {
            if (mapList.get(i).getId() == id) return;
        }
        mapList.add(map);
    }
}
