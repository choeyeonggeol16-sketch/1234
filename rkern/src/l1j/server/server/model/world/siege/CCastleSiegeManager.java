package l1j.server.server.model.world.siege;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1CrownInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.gametime.CDateTime;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.gametime.CTimeListener;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.packets.Protocol.MainServer.Proto.Proto;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SIEGE_INJURY_TIME_NOIT;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SIEGE_ZONE_UPDATE_NOT;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class CCastleSiegeManager implements CTimeListener {

    private static final Logger _log = Logger.getLogger(CCastleSiegeManager.class.getName());

    private static final String[] _viewMessages = new String[]{
        "공성전이 진행중입니다.",
        "성을 소유하고 있는 혈맹은 다음과 같습니다."
    };

    private static volatile byte[] _zoneBegin, _zoneEnd, _offInjury;

    private static byte[] zoneBegin() {
        byte[] d = _zoneBegin;
        if (d == null) {
            _zoneBegin = d = SC_SIEGE_ZONE_UPDATE_NOT.encodeFields(
                    SC_SIEGE_ZONE_UPDATE_NOT.SiegeZoneKind.BEGIN);
        }
        return d;
    }

    private static byte[] zoneEnd() {
        byte[] d = _zoneEnd;
        if (d == null) {
            _zoneEnd = d = SC_SIEGE_ZONE_UPDATE_NOT.encodeFields(
                    SC_SIEGE_ZONE_UPDATE_NOT.SiegeZoneKind.END);
        }
        return d;
    }
    private static byte[] offInjury() {
        byte[] d = _offInjury;
        if (d == null) {
            _offInjury = d = CCastle.createInjury();
        }
        return d;
    }

    public static boolean isNearActiveGuardianTower(L1PcInstance pc) {
        int castleId = L1CastleLocation.getCastleIdByArea(pc);
        CCastle war = castleId == 0 ? null : getInstance().get(castleId);
        return war != null && war.IsUnderSiege() && war.hasGuardianTowerNear(pc);
    }

    public static void updateSiegeZone(L1PcInstance pc) {
        int castleId = L1CastleLocation.getCastleIdByArea(pc);
        CCastle war = castleId == 0 ? null : getInstance().get(castleId);
        int previous = pc.siegeZoneWarId;
        int current = war != null && war.IsUnderSiege() ? war.getId() : 0;
        if (current != 0 && pc.isInvisble() && war.hasGuardianTowerNear(pc)) pc.delInvis();
        if (previous == current) return;
        pc.siegeZoneWarId = current;

        if (previous != 0) {
            Proto.sendRaw(pc, zoneEnd());
            Proto.sendRaw(pc, offInjury());
        }
        if (current == 0) return;
        Proto.sendRaw(pc, zoneBegin());

        L1Clan clan = pc.getClan();
        var kind = SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK;
        String pledgeName = null;
        if (clan != null && clan.getCurrentWar() == war) {
            if (war.getDefenseClan() == clan) {
                kind = SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.DEFENCE;
                pledgeName = clan.getClanName();
            } else if (war.getOffenseClan(clan.getClanId()) == clan) {
                pledgeName = clan.getClanName();
            }
        }
        SC_SIEGE_INJURY_TIME_NOIT.sendConstructed(pc, kind, war.getRemainingSeconds(), pledgeName);
    }

    private static final class Holder {
        private static final CCastleSiegeManager INSTANCE = new CCastleSiegeManager();
    }

    public static CCastleSiegeManager getInstance() {
        return Holder.INSTANCE;
    }

    private volatile Map<Integer, CCastle> _castleWars = Map.of();
    private volatile boolean manualAssignmentPending;
    private volatile boolean reloading;
    private boolean disposed;

    private CCastleSiegeManager() {
        reload();
    }

    public boolean reload() {
        synchronized (this) {
            if (disposed || reloading || manualAssignmentPending
                    || _castleWars.values().stream().anyMatch(CCastle::capturePending)) return false;
            reloading = true;
        }
        try {
            final List<CastleSiegeTable.Entry> entries = CastleSiegeTable.load();
            final ClanTable clans = ClanTable.getInstance();
            synchronized (this) {
                if (disposed) return false;
                for (CCastle siege : _castleWars.values()) siege.dispose();
                Map<Integer, CCastle> replacement = new HashMap<>(entries.size());
                for (CastleSiegeTable.Entry entry : entries) {
                    L1Clan defense = clans.findCastleClan(entry.castleId());
                    replacement.put(entry.castleId(), WarFactory.createCastleSiege(defense, entry));
                }
                _castleWars = replacement;
                return true;
            }
        } catch (SQLException | RuntimeException failure) {
            _log.log(Level.WARNING, "Castle siege load failed", failure);
            return false;
        } finally {
            reloading = false;
        }
    }

    public static final int TREASURY_ORDER_KEY = CacheD.accountOrderKey("$castle-treasury-global");

    public synchronized boolean requestCapture(CCastle siege, L1Clan winner, L1CrownInstance crown) {
        if (disposed || reloading || manualAssignmentPending || get(siege.getCastleId()) != siege) return false;
        final CCastle.Capture capture = siege.reserveCapture(winner, crown);
        if (capture == null) return false;
        final CWorld world = CWorld.getInstance();
        final int castleId = siege.getCastleId();
        final int winnerId = winner.getClanId();
        final int defenderId = capture.oldDefense() == null ? 0 : capture.oldDefense().getClanId();
        final int[] extraOwners = defenderId == 0 ? new int[0] : new int[] {defenderId};
        try (var move = CacheD.beginEconomyMove(TREASURY_ORDER_KEY, winnerId, extraOwners)) {
            move.stageWarehouseSql(con -> CastleSiegeTable.capture(con, castleId, defenderId, winnerId,
                    siege.getCastleName(), capture.round().startMillis, capture.taxRate()));
            move.onError(failure -> {
                if (!isUncertain(failure)) siege.finishCapture(capture);
                _log.log(Level.WARNING, "Castle capture could not finish: " + castleId, failure);
            });
            move.commit(() -> CIOThreadCallback.getInstance().completeWorldMutation(world,
                    () -> siege.confirmCapture(capture), move::finishOwnerApply));
            return true;
        } catch (RuntimeException failure) {
            siege.finishCapture(capture);
            _log.log(Level.FINE, "Castle capture admission deferred: " + castleId, failure);
            return false;
        }
    }

    private static boolean isUncertain(Throwable failure) {
        if (failure instanceof CTransaction.OutcomeUnknownException || failure instanceof CacheD.CommittedReplayException)
            return true;
        for (Throwable suppressed : failure.getSuppressed()) {
            if (suppressed instanceof CTransaction.OutcomeUnknownException) return true;
        }
        return false;
    }

    public synchronized boolean assignCastle(L1Clan clan, int destination, Runnable success, Runnable failure) {
        if (disposed || reloading || manualAssignmentPending || destination < 0 || (destination != 0 && get(destination) == null)
                || _castleWars.values().stream().anyMatch(CCastle::capturePending)) return false;
        final CWorld world = CWorld.getInstance();
        final int clanId = clan.getClanId();
        final int previous = clan.getCastleId();
        if (previous == destination) {
            success.run();
            return true;
        }
        final CCastle oldCastle = get(previous);
        final CCastle newCastle = get(destination);
        manualAssignmentPending = true;
        if ((oldCastle != null && !oldCastle.readyForManualAssignment())
                || (newCastle != null && !newCastle.readyForManualAssignment())) {
            manualAssignmentPending = false;
            return false;
        }
        try (var move = CacheD.beginEconomyMove(TREASURY_ORDER_KEY, clanId)) {
            move.stageWarehouseSql(con -> CastleSiegeTable.assign(con, clanId, previous, destination));
            move.onError(error -> {
                if (!isUncertain(error)) finishManualAssignment();
                failure.run();
            });
            move.commit(() -> CIOThreadCallback.getInstance().completeWorldMutation(world, () -> {
                if (world.getClan(clanId) != clan || clan.getCastleId() != previous)
                    throw new IllegalStateException("GM castle assignment preimage changed");
                clan.setCastleId(destination);
                if (oldCastle != null) oldCastle.replaceIdleDefense(null);
                if (newCastle != null) newCastle.replaceIdleDefense(clan);
            }, error -> move.finishOwnerApply(error, outcome -> {
                if (outcome == null) {
                    finishManualAssignment();
                    success.run();
                } else failure.run();
            })));
            return true;
        } catch (RuntimeException unavailable) {
            manualAssignmentPending = false;
            return false;
        }
    }

    boolean transitionPaused() { return manualAssignmentPending || reloading; }

    private synchronized void finishManualAssignment() { manualAssignmentPending = false; }

    /** Stage treasury arithmetic on the inventory receipt's connection. */
    public Runnable stageTreasury(L1InventoryTransaction tx, int castleId, int delta) {
        final CCastle war = get(castleId);
        if (war == null) throw new IllegalStateException("Castle missing");
        final int before = war.getPublicMoney();
        final long after = (long) before + delta;
        if (before < 0 || after < 0 || after > 2000000000L)
            throw new IllegalArgumentException("Castle treasury limit");
        tx.requireTables("castle");
        tx.sql(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE castle SET public_money=? WHERE castle_id=? AND public_money=?")) {
                ps.setInt(1,(int)after);ps.setInt(2,castleId);ps.setInt(3,before);
                if(ps.executeUpdate()!=1)throw new java.sql.SQLException("Castle treasury changed");
            }
        });
        return () -> publishTreasury(castleId, (int) after);
    }

    private synchronized void publishTreasury(int castleId, int amount) {
        CCastle current = get(castleId);
        if (current != null) current.setPublicMoney(amount);
    }

    public void updateCastle(int castleId) {
        CCastle siege = get(castleId);
        if (siege != null) updateCastle(siege);
    }

    public void updateCastle(CCastle siege) {
        int castleId = siege.getCastleId();
        if (siege.disposed || siege.capturePending() || get(castleId) != siege) return;
        CacheD.getInstance().submitDbJobOrdered(TREASURY_ORDER_KEY, () -> {
            if (!siege.disposed && get(castleId) == siege)
                CastleSiegeTable.saveMetadata(castleId, siege.getCastleName(), siege.getNextStartMillis(), siege.getTaxRate());
        });
    }

    public synchronized void start() {
        CGameTime.addRealListener(this, Calendar.SECOND);
    }

    @Override
    public void onSecondChanged(CDateTime time) {
        if (manualAssignmentPending) return;
        long nowMillis = time.getSeconds() * 1000L;
        for (CCastle siege : _castleWars.values()) {
            siege.onClockTick(nowMillis, this);
        }
    }

    public synchronized void dispose() {
        disposed = true;
        CGameTime.removeRealListener(this, Calendar.SECOND);
        Map<Integer, CCastle> previous = _castleWars;
        _castleWars = Map.of();
        for (CCastle siege : previous.values()) siege.dispose();
    }

    public boolean isNowWar(int castleId){
        CCastle war = get(castleId);
        if(war == null)
            return false;
        return war.IsUnderSiege();
    }

    public boolean isNowReady(int castleId){
        CCastle war = get(castleId);
        return war != null && war.isReady();
    }

    public CCastle get(int castleId){
        return _castleWars.get(castleId);
    }

    public void proclaim(L1PcInstance pc, int castleId){
        L1Clan clan = pc.getClan();
        if(clan == null){
            pc.sendPackets(272);
            return;
        }

        if(pc.getRedKnightClanId() != 0 || !pc.isCrown() || pc.getId() != clan.getLeaderId()){
            pc.sendPackets(478);
            return;
        }

        if(clan.getCastleId() != 0){
            pc.sendPackets(474);
            return;
        }

        CCastle siege = get(castleId);
        if(siege == null || !siege.IsUnderSiege()){
            pc.sendPackets("공성전이 진행중이지 않습니다.");
            return;
        }

        if(pc.getLevel() < Config.선포레벨){
            pc.sendPackets(String.format("공성: 군주/공주 레벨 [%d]부터 선포할 수 있습니다.", Config.선포레벨));
            return;
        }

        if(clan.getCurrentOnlineMemebers() < Config.혈맹접속인원){
            pc.sendPackets(String.format("공성: 접속중인 혈맹 구성원이 [%d]명 이상되어야 선포가 가능합니다.", Config.혈맹접속인원));
            return;
        }

        siege.proclaim(pc);
    }

    public void viewNowCastleWarState(L1PcInstance pc){
        boolean isFirst = true;
        for(CCastle war : _castleWars.values()){
            if(!war.IsUnderSiege())
                continue;

            final L1Clan defenseClan = war.getDefenseClan();
            if (defenseClan == null) {
                _log.warning("[CCastle] 진행 중 공성의 방어클랜이 null! castle: "
                        + war.getCastleName());
                continue;
            }

            if(isFirst){
                isFirst = false;
                for (String text : _viewMessages) SC_SystemMessage.send(pc, text);
            }
            pc.sendPackets(String.format("[%s=%s혈맹]", war.getCastleName(), defenseClan.getClanName()));
        }
    }

    public int getTaxRate(int castleId){
        return get(castleId).getTaxRate();
    }

    public void setTaxRate(int castleId, int tax){
        get(castleId).setTaxRate(tax);
    }

    public int getPublicMoney(int castleId){
        return get(castleId).getPublicMoney();
    }

    public void setPublicMoney(int castleId, int publicMoney){
        get(castleId).setPublicMoney(publicMoney);
    }

    public int getSecurity(int castleId){
        return get(castleId).getCastleSecurity();
    }

    public void setSecurity(int castleId, int security){
        get(castleId).setCastleSecurity(security);
    }

    public CCastle findSiege(String name){
        for(CCastle war : _castleWars.values()){
            if(war.getCastleName().startsWith(name))
                return war;
        }
        return null;
    }
}
