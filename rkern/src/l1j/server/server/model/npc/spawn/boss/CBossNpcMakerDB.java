package l1j.server.server.model.npc.spawn.boss;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.templates.L1Npc;

/** Immutable published definitions; each row is identified by its full spawnlist key. */
public final class CBossNpcMakerDB {
    private static final Logger LOG = Logger.getLogger(CBossNpcMakerDB.class.getName());
    record Catalog(List<CNpcSpawnDefine> rows, Map<CBossSpawnKey, CNpcSpawnDefine> byPlacement, int[] templateIds) {
        Catalog(List<CNpcSpawnDefine> rows, Map<CBossSpawnKey, CNpcSpawnDefine> byPlacement, Set<Integer> ids) {
            this(rows, byPlacement, ids.stream().mapToInt(Integer::intValue).sorted().toArray());
        }
    }
    private static final Catalog EMPTY = new Catalog(List.of(), Map.of(), Set.of());
    private static volatile Catalog catalog = EMPTY;
    private static volatile boolean invitesActive;

    private CBossNpcMakerDB() {}

    static Catalog read() throws SQLException {
        var rows = new ArrayList<CNpcSpawnDefine>();
        var byPlacement = new HashMap<CBossSpawnKey, CNpcSpawnDefine>();
        var templates = new HashSet<Integer>();
        String query = "SELECT npcid,monname,mapname,spawn_x,spawn_y,spawn_map,rnd_loc,spawn_time,"
                + "spawn_yoil,`랜덤시간부여`,isment,ment,isyn,yn_ment,display_effect FROM spawnlist_boss_hot";
        try (var con = DatabaseFactory.getConnectionInstance();
                var stmt = con.prepareStatement(query); var rs = stmt.executeQuery()) {
            while (rs.next()) {
                int npcId = rs.getInt("npcid");
                L1Npc npc = CObjectDB.GetNPC(npcId);
                if (npc == null) {
                    LOG.warning("Skipping boss definition without NPC template " + npcId);
                    continue;
                }
                var define = new CNpcSpawnDefine();
                define.setNpcId(npcId);
                define.setMonName(rs.getString("monname"));
                define.setMapName(rs.getString("mapname"));
                define.setX(rs.getInt("spawn_x"));
                define.setY(rs.getInt("spawn_y"));
                define.setMap(rs.getInt("spawn_map"));
                define.setRndLoc(rs.getInt("rnd_loc"));
                define.setTime(readTimes(rs.getString("spawn_time"), npcId));
                String weekdays = rs.getString("spawn_yoil");
                define.setYoil(weekdays == null || weekdays.isBlank() ? new String[]{"전체"} : weekdays.split(","));
                define.setnonespawntime(rs.getInt("랜덤시간부여"));
                define.setMent(rs.getInt("isment") == 1);
                define.setMentMessage(rs.getString("ment"));
                define.setYn(rs.getInt("isyn") == 1);
                define.setYnMessage(rs.getString("yn_ment"));
                define.set_display_effect(rs.getInt("display_effect"));
                rows.add(define);
                byPlacement.put(CBossSpawnKey.of(define), define);
                templates.add(npcId);
            }
        }
        return new Catalog(List.copyOf(rows), Map.copyOf(byPlacement), templates);
    }

    private static int[][] readTimes(String times, int npcId) {
        var valid = new ArrayList<int[]>();
        if (times != null) {
            for (String token : times.split(",")) {
                String[] parts = token.trim().split(":", -1);
                if (parts.length == 2) {
                    try {
                        int h = Integer.parseInt(parts[0].trim());
                        int m = Integer.parseInt(parts[1].trim());
                        if (h >= 0 && h < 24 && m >= 0 && m < 60) {
                            valid.add(new int[]{h, m});
                            continue;
                        }
                    } catch (NumberFormatException ignored) {
                        // Report the malformed token, preserving the other configured times.
                    }
                }
                LOG.warning("Invalid boss spawn time npcId=" + npcId + " value=" + token);
            }
        }
        return valid.toArray(int[][]::new);
    }

    static void install(Catalog loaded) {
        catalog = loaded;
        invitesActive = true;
    }

    static void closeInvitations() { invitesActive = false; }

    public static boolean isBossTemplate(int npcId) { return Arrays.binarySearch(catalog.templateIds(), npcId) >= 0; }

    static boolean isCurrent(CBossSpawnKey key, CNpcSpawnDefine define) {
        return invitesActive && catalog.byPlacement().get(key) == define;
    }
}
