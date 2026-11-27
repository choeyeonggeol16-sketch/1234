package l1j.server.server.model.world.instance.dungeon.time.loader;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.world.instance.dungeon.time.DungeonTimeInformation;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.shared.arrangement.ArrangeParser;

public final class DungeonTimeInformationLoader {
    private static volatile DungeonTimeInformationLoader instance;

    private final TIntObjectHashMap<DungeonTimeInformation> timers = new TIntObjectHashMap<>(16);
    private final TIntObjectHashMap<DungeonTimeInformation> maps = new TIntObjectHashMap<>();
    private final int[] mapIds;
    private final List<DungeonTimeInformation> enumerator;

    public static DungeonTimeInformationLoader getInstance() {
        DungeonTimeInformationLoader current = instance;
        if (current != null) return current;
        synchronized (DungeonTimeInformationLoader.class) {
            if (instance == null) reload();
            return instance;
        }
    }

    public static synchronized void release() {
        instance = null;
    }

    public static synchronized void reload() {
        instance = new DungeonTimeInformationLoader();
    }

    private DungeonTimeInformationLoader() {
        try (var connection = DatabaseFactory.getInstance().getConnection();
                var query = connection.prepareStatement("select * from tb_dungeon_time_information");
                var rows = query.executeQuery()) {
            while (rows.next()) {
                DungeonTimeInformation info = DungeonTimeInformation.newInstance(rows);
                timers.put(info.get_timer_id(), info);
                Integer[] ids = (Integer[]) ArrangeParser.parsing(rows.getString("map_ids"), ",",
                        ArrangeParseeFactory.createIntArrange()).result();
                for (int id : ids) maps.put(id, info);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not load dungeon timer settings", error);
        }
        mapIds = maps.keys();
        Arrays.sort(mapIds);
        enumerator = List.copyOf(timers.valueCollection());
    }

    public DungeonTimeInformation from_timer_id(int timerId) {
        return timers.get(timerId);
    }

    public DungeonTimeInformation from_map_id(int mapId) {
        return maps.get(mapId);
    }

    public int mapCount() { return mapIds.length; }

    public int mapIdAt(int index) { return mapIds[index]; }

    public Collection<DungeonTimeInformation> get_enumerator() {
        return enumerator;
    }
}
