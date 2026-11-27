package l1j.server.server.model.world.siege;

import java.util.ArrayList;

import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;

public final class ClanSiegeRosterRegression {
    public static void main(String[] args) throws Exception {
        var mapSetup = Class.forName("l1j.server.server.model.WorldVisibilityRegression")
                .getDeclaredMethod("installMapMetadata");
        mapSetup.setAccessible(true);
        mapSetup.invoke(null);
        var world = CWorld.getInstance();
        var maps = L1WorldMap.getInstance();
        var actors = new ArrayList<L1PcInstance>();
        var terrains = new ArrayList<L1V1Map>();
        try {
            var evacuees = new ArrayList<L1PcInstance>();
            var clan = new L1Clan();
            clan.setClanId(71);
            for (int id : new int[] { 4, 52, 248, 249, 250, 251 }) {
                var map = new L1V1Map(id, new byte[32][32], 80, 80, false, true, true,
                        true, true, true, true, true, true, true, true, false);
                maps.addMap(map);
                terrains.add(map);
                for (int clanId : new int[] { 0, 72, 71, 0 }) {
                    var pc = new L1PcInstance();
                    pc.setId(9101 + actors.size());
                    pc.setGm(actors.size() % 4 == 3);
                    pc.setName("SiegeTest" + pc.getId());
                    pc.setClanid(clanId);
                    pc.setOnlineStatus(1);
                    pc.getLocation().set(90, 90, map);
                    actors.add(pc);
                    world.storeAndEnterWorld(pc);
                    if (id != 4 && !pc.isGm() && clanId != 71) evacuees.add(pc);
                    if (clanId == 71) clan.addLoadedClanMember(pc.getName(), 1, 1, "", pc.getId(), 0);
                }
            }
            var actual = new ArrayList<L1PcInstance>();
            CastleWar.forEachSecurityEvacuee(71, actual::add);
            check(actual.equals(evacuees), "evacuation roster across five siege maps and normal field");
            check(clan.hasAtLeastOnlineMembers(0) && clan.hasAtLeastOnlineMembers(6)
                    && !clan.hasAtLeastOnlineMembers(7), "online threshold bounds");
            actors.get(2).setOnlineStatus(0);
            actors.get(6).setClanid(0);
            check(clan.getOnlineMemberCount() == 4 && !clan.hasAtLeastOnlineMembers(5)
                    && clan.hasAtLeastOnlineMembers(4) && clan.createOnlineMembers().count() == 4,
                    "logout, departure and legacy roster parity");
            System.out.println("PASS five siege maps, off-map clanless, defenders, GM and clan count thresholds");
        } finally {
            for (var pc : actors) world.leaveAndRemoveObject(pc);
            for (var map : terrains) maps.removeMap(map);
        }
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
