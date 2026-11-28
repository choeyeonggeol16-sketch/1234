package l1j.server.server.model.world.siege;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import l1j.server.Config;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.templates.L1Npc;

public final class ClanSiegeRosterRegression {
    public static void main(String[] args) throws Exception {
        var mapSetup = Class.forName("l1j.server.server.model.WorldVisibilityRegression")
                .getDeclaredMethod("installMapMetadata");
        mapSetup.setAccessible(true);
        mapSetup.invoke(null);
        var world = CWorld.getInstance();
        var maps = L1WorldMap.getInstance();
        checkScheduleAndRetirement(world);
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
            CCastle.forEachSecurityEvacuee(71, actual::add);
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
        checkIndexedTowerLookup(world, maps);
    }

    private static void checkIndexedTowerLookup(CWorld world, L1WorldMap maps) throws Exception {
        var speeds = NpcSpeedData.class.getDeclaredField("m_npc_speed_data");
        speeds.setAccessible(true);
        if (speeds.get(null) == null) speeds.set(null, new HashMap<>());
        int x = L1CastleLocation.ADEN_X1;
        int y = L1CastleLocation.ADEN_Y1;
        var field = new L1V1Map(L1CastleLocation.ADEN_MAP, new byte[192][192], x - 1, y - 1,
                false, true, true, true, true, true, true, true, true, true, true, false);
        var interior = new L1V1Map(L1CastleLocation.ADEN_INNER_CASTLE_MAP, new byte[32][32], 80, 80,
                false, true, true, true, true, true, true, true, true, true, true, false);
        maps.addMap(field);
        maps.addMap(interior);
        var stored = new ArrayList<L1NpcInstance>();
        var schedule = new CastleSiegeSchedule(CGameTime.getRealTimeCalendar());
        var entry = new CastleSiegeTable.Entry(L1CastleLocation.ADEN_CASTLE_ID, "Aden test", schedule, 10, 0);
        CCastle siege = WarFactory.createCastleSiege(null, entry);
        try {
            var first = new L1TowerInstance(npcTemplate(81190));
            var second = new L1TowerInstance(npcTemplate(81191));
            var third = new L1TowerInstance(npcTemplate(81192));
            var unrelated = new L1NpcInstance(npcTemplate(81193));
            var indoorTower = new L1TowerInstance(npcTemplate(81111));
            var outsideTower = new L1TowerInstance(npcTemplate(81111));
            for (L1NpcInstance actor : new L1NpcInstance[] {first, second, third, unrelated, indoorTower, outsideTower}) {
                actor.setId(9011 + stored.size());
                stored.add(actor);
                if (actor == indoorTower) actor.getLocation().set(90, 90, interior);
                else actor.getLocation().set(actor == outsideTower ? x - 1 : x + stored.size() - 1, y, field);
                world.storeAndEnterWorld(actor);
            }
            first.setDead(true);
            second.setDead(true);
            unrelated.setDead(true);
            var destroyed = L1TowerInstance.class.getDeclaredMethod("hasDestroyedAdenSubTowers");
            destroyed.setAccessible(true);
            check(!(boolean) destroyed.invoke(null), "two destroyed Aden subtowers are insufficient");
            third.setDead(true);
            check((boolean) destroyed.invoke(null), "three destroyed Aden subtowers admit main tower damage");
            world.leaveAndRemoveObject(third);
            check(!(boolean) destroyed.invoke(null), "removed subtower no longer qualifies");

            var deleteTower = CCastle.class.getDeclaredMethod("deleteTower");
            deleteTower.setAccessible(true);
            deleteTower.invoke(siege);
            check(world.findObject(first.getId()) == null && world.findObject(second.getId()) == null
                    && world.findObject(indoorTower.getId()) == null, "target castle clears exterior and interior towers");
            check(world.findObject(unrelated.getId()) == unrelated
                    && world.findObject(outsideTower.getId()) == outsideTower, "off-field tower and other NPC are preserved");
            System.out.println("PASS indexed Aden threshold and castle tower cleanup");
        } finally {
            siege.dispose();
            for (var actor : stored) world.leaveAndRemoveObject(actor);
            maps.removeMap(field);
            maps.removeMap(interior);
            CIOThreadCallback.getInstance().shutdown();
        }
    }

    private static L1Npc npcTemplate(int id) {
        var template = new L1Npc();
        template.set_npcId(id);
        template.set_name("Siege fixture " + id);
        template.set_nameid(template.get_name());
        template.set_hp(100);
        return template;
    }

    private static void checkScheduleAndRetirement(CWorld world) {
        int oldDuration = Config.ALT_WAR_TIME;
        int oldDurationUnit = Config.ALT_WAR_TIME_UNIT;
        int oldInterval = Config.ALT_WAR_INTERVAL;
        int oldIntervalUnit = Config.ALT_WAR_INTERVAL_UNIT;
        Config.ALT_WAR_TIME = 60;
        Config.ALT_WAR_TIME_UNIT = Calendar.MINUTE;
        Config.ALT_WAR_INTERVAL = 7;
        Config.ALT_WAR_INTERVAL_UNIT = Calendar.DAY_OF_MONTH;
        try {
            Calendar start = CGameTime.getRealTimeCalendar();
            long startMillis = start.getTimeInMillis();
            CastleSiegeSchedule schedule = new CastleSiegeSchedule(start);
            start.add(Calendar.HOUR_OF_DAY, 2);
            Calendar copy = schedule.copyStart();
            copy.clear();
            check(schedule.copyStart().getTimeInMillis() == startMillis, "independent siege date copies");
            check(schedule.readyMillis == startMillis - 300_000L
                    && schedule.endMillis == startMillis + 3_600_000L, "original ready and end windows");
            check(schedule.withDefenseTime(start, 30).endMillis == schedule.limitMillis,
                    "capture extension respects the original end limit");
            check(schedule.nextOccurrence().startMillis == startMillis + 7 * 86_400_000L,
                    "next siege follows the configured interval");
            for (int id = 1; id <= 8; id++) {
                int innerMap = L1CastleLocation.getCastleInnerMapId(id);
                if (innerMap != 0) {
                    check(L1CastleLocation.getCastleIdByArea(0, 0, (short) innerMap) == id,
                            "indexed inner map matches castle area " + id);
                }
            }

            L1Clan attacker = new L1Clan();
            attacker.setClanId(71);
            var entry = new CastleSiegeTable.Entry(1, "Regression castle", schedule, 10, 0);
            CCastle siege = WarFactory.createCastleSiege(null, entry);
            try {
                siege.onClockTick(schedule.readyMillis);
                check(!siege.isReady(), "ready window preserves the original strict boundary");
                siege.onClockTick(schedule.readyMillis + 1);
                check(siege.isReady(), "siege enters its ready window");
                check(siege.register(attacker) && !siege.register(attacker)
                        && attacker.getCurrentWar() == siege, "unowned castle and duplicate declaration");
                siege.updateDefense(attacker);
                check(siege.getDefenseClan() == attacker && siege.getOffenseClan(71) == null
                        && attacker.getCurrentWar() == siege, "unowned castle can acquire its first defender");
            } finally {
                siege.dispose();
            }
            siege.onClockTick(startMillis + 1);
            check(!siege.isReady() && !siege.IsUnderSiege() && !siege.register(attacker)
                    && attacker.getCurrentWar() == null
                    && world.createWarStream().noneMatch(war -> war == siege), "retired siege releases war links");
            System.out.println("PASS siege schedule, unowned castle, declarations and retirement");
        } finally {
            Config.ALT_WAR_TIME = oldDuration;
            Config.ALT_WAR_TIME_UNIT = oldDurationUnit;
            Config.ALT_WAR_INTERVAL = oldInterval;
            Config.ALT_WAR_INTERVAL_UNIT = oldIntervalUnit;
        }
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
