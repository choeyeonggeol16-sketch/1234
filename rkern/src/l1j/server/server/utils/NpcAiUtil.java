package l1j.server.server.utils;

import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public class NpcAiUtil {

    public static final int[] DX8 = { 0, 1, 1, 1, 0, -1, -1, -1 };
    public static final int[] DY8 = { -1, -1, 0, 1, 1, 1, 0, -1 };
    
    public static int checkObject(int x, int y, short m, int d) {
        final L1Map map = L1WorldMap.getInstance().getMap(m);
        final TileOccupancyNpc occNpc = TileOccupancyNpc.getInstance();
        final TileOccupancy occPc = TileOccupancy.getInstance();

        switch (d) {
        case 1:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 1)) return 1;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 0)) return 0;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 2)) return 2;
            break;
        case 2:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 2)) return 2;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 1)) return 1;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 3)) return 3;
            break;
        case 3:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 3)) return 3;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 2)) return 2;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 4)) return 4;
            break;
        case 4:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 4)) return 4;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 3)) return 3;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 5)) return 5;
            break;
        case 5:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 5)) return 5;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 4)) return 4;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 6)) return 6;
            break;
        case 6:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 6)) return 6;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 5)) return 5;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 7)) return 7;
            break;
        case 7:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 7)) return 7;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 6)) return 6;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 0)) return 0;
            break;
        case 0:
            if (isFreeStep(map, occNpc, occPc, x, y, m, 0)) return 0;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 7)) return 7;
            if (isFreeStep(map, occNpc, occPc, x, y, m, 1)) return 1;
            break;
        }
        return -1;
    }
    
    private static boolean isFreeStep(
            L1Map map,
            TileOccupancyNpc occNpc,
            TileOccupancy occPc,
            int x, int y, short mapId, int dir) {

        if (!map.isMovePassableGeoNpc(x, y, dir)) {
            return false;
        }

        final int nx = x + DX8[dir];
        final int ny = y + DY8[dir];

        if (occNpc.getOccupier(mapId, nx, ny) != 0) {
            return false;
        }
        if (occPc.getOccupier(mapId, nx, ny) != 0) {
            return false;
        }
        return true;
    }
}
