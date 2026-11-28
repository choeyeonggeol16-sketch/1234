package l1j.server.server.model.world.siege;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.model.Instance.L1CrownInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1WarSpawn;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.network.core.sc.packet.SC_CastleMaster;
import l1j.server.server.model.network.core.sc.packet.SC_IconMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_WarStartMessage;
import l1j.server.server.model.world.siege.WarFactory.WAR_TYPE;
import l1j.server.server.packets.Protocol.MainServer.Proto.Proto;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SIEGE_INJURY_TIME_NOIT;
import l1j.server.server.shared.SimpleRgb;
import l1j.server.server.utils.GameUtils;

public final class CCastle extends War {
    private static final Logger _log = Logger.getLogger(CCastle.class.getName());
    private static final int GUARDIAN_TOWER_REVEAL_RANGE = 3;

    private final int _castleId;
    private final String _castleName;
    private volatile CastleSiegeSchedule schedule;
    private volatile int _taxRate;
    private volatile int _publicMoney;
    private volatile int _security;
    private volatile CastleSiegeState _state = CastleSiegeState.IDLE;
    private CServerTimer evacuationRetry;
    private volatile Capture capture;
    private boolean endAfterCapture;

    record Capture(L1Clan oldDefense, L1Clan winner, L1CrownInstance crown,
                   CastleSiegeSchedule round, CastleSiegeSchedule defenseTime, int taxRate) {}

    CCastle(L1Clan defense, int id, CastleSiegeTable.Entry entry) {
        super(WAR_TYPE.CASTLE, id);
        _castleId = entry.castleId();
        _castleName = entry.name();
        _taxRate = entry.taxRate();
        _publicMoney = entry.publicMoney();
        schedule = entry.schedule();
        publish(defense);
    }

    public int getCastleId() {
        return _castleId;
    }

    public String getCastleName() {
        return _castleName;
    }

    public Calendar getNextStartCalendar() {
        return schedule.copyStart();
    }

    long getNextStartMillis() {
        return schedule.startMillis;
    }

    public int getTaxRate() {
        return _taxRate;
    }

    public void setTaxRate(int i) {
        _taxRate = i;
    }

    public int getPublicMoney() {
        return _publicMoney;
    }

    public void setPublicMoney(int i) {
        _publicMoney = i;
    }

    public int getCastleSecurity() {
        return _security;
    }

    public void setCastleSecurity(int i) {
        _security = i;
    }

    public boolean IsUnderSiege() {
        return !disposed && _state == CastleSiegeState.RUN;
    }

    boolean hasGuardianTowerNear(L1PcInstance pc) {
        if (pc.isGmInvis() || pc.isGhost() || pc.isDead() || pc.isLogoutStarted()) return false;
        for (L1Object object : CWorld.getInstance().getClusterGrid().queryNearby(pc, GUARDIAN_TOWER_REVEAL_RANGE)) {
            if (!(object instanceof L1TowerInstance tower) || tower.isDestroyed() || tower.isDead()
                    || tower.getCurrentHp() <= 0 || tower.getMap() != pc.getMap()) continue;
            if (tower.isSubTower()) {
                if (_castleId == L1CastleLocation.ADEN_CASTLE_ID
                        && L1CastleLocation.checkInWarArea(_castleId, tower)) return true;
            } else if (L1CastleLocation.getCastleId(tower.getLocation()) == _castleId) {
                return true;
            }
        }
        return false;
    }

    public boolean isReady() {
        return !disposed && _state == CastleSiegeState.READY;
    }

    public int getRemainingSeconds() {
        if (disposed) return 0;
        long seconds = (schedule.endMillis - System.currentTimeMillis()) / 1000L;
        return (int) Math.clamp(seconds, 0L, Integer.MAX_VALUE);
    }

    public boolean SetSiegeDate(Calendar cal) {
        _warLock.lock();
        try {
            if (disposed || capture != null) return false;
            schedule = new CastleSiegeSchedule(cal);
            return true;
        } finally {
            _warLock.unlock();
        }
    }

    boolean readyForManualAssignment() {
        _warLock.lock();
        try {
            return !disposed && _state != CastleSiegeState.RUN && _state != CastleSiegeState.CLOSING;
        } finally {
            _warLock.unlock();
        }
    }

    void replaceIdleDefense(L1Clan clan) {
        _warLock.lock();
        try {
            if (IsUnderSiege() || disposed) throw new IllegalStateException("Active castle assignment");
            if (_defense != null) _defense.clearCurrentWar(this);
            setDefenseClan(clan);
        } finally {
            _warLock.unlock();
        }
    }

    boolean capturePending() { return capture != null; }

    Capture reserveCapture(L1Clan winner, L1CrownInstance crown) {
        _warLock.lock();
        try {
            final CWorld world = CWorld.getInstance();
            if (capture != null || !IsUnderSiege() || winner == null || winner.getCurrentWar() != this
                    || _offenses.get(winner.getClanId()) != winner || winner.getCastleId() != 0
                    || world.getClan(winner.getClanId()) != winner
                    || world.findObject(crown.getId()) != crown
                    || (_defense != null && (world.getClan(_defense.getClanId()) != _defense
                        || _defense.getCastleId() != _castleId))) return null;
            return capture = new Capture(_defense, winner, crown, schedule,
                    schedule.withDefenseTime(CGameTime.getRealTimeCalendar(), Config.수성시간), _taxRate);
        } finally {
            _warLock.unlock();
        }
    }

    synchronized void confirmCapture(Capture expected) {
        _warLock.lock();
        try {
            final CWorld world = CWorld.getInstance();
            if (capture != expected || !IsUnderSiege() || schedule != expected.round()
                    || _defense != expected.oldDefense() || expected.winner().getCastleId() != 0
                    || world.getClan(expected.winner().getClanId()) != expected.winner()
                    || world.findObject(expected.crown().getId()) != expected.crown()
                    || (_defense != null && (world.getClan(_defense.getClanId()) != _defense
                        || _defense.getCastleId() != _castleId))) {
                throw new IllegalStateException("Castle capture world preimage changed");
            }
            if (_defense != null) _defense.setCastleId(0);
            expected.winner().setCastleId(_castleId);
            schedule = expected.defenseTime();
            super.updateDefense(expected.winner());
        } finally {
            _warLock.unlock();
        }
        expected.crown().deleteMe();
        applyCaptureEffects(expected.winner());
        finishCapture(expected);
    }

    void finishCapture(Capture expected) {
        _warLock.lock();
        try {
            if (capture != expected) return;
            capture = null;
            if (endAfterCapture) {
                endAfterCapture = false;
                requestEndSiege();
            }
        } finally {
            _warLock.unlock();
        }
    }

    private void applyCaptureEffects(L1Clan nextDefense) {
        cancelEvacuationRetry();
        try {
            final L1Location loc = createGetBackLocation();
            try (var shared = new CAsyncSendConstBuffer(
                         SC_ServerMessage.nativeWriter(643), Integer.MIN_VALUE, Integer.MAX_VALUE);
                 var ownerShared = new CAsyncSendConstBuffer(
                         SC_CastleMaster.nativeWriter(_castleId, nextDefense.getLeaderId()),
                         Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
                    if (pc == null) continue;
                    L1Clan clan = pc.getClan();
                    if (clan == null || clan.getCastleId() != _castleId) {
                        if (L1CastleLocation.checkInWarArea(_castleId, pc) && !pc.isGm()) {
                            L1Location nloc = loc.randomLocation(15, true);
                            teleport(pc, nloc.getX(), nloc.getY(), nloc.getMapId());
                        }
                        offLordBuff(pc);
                    } else {
                        CSharedBroadcast.sendRecipientBestEffort(shared, pc);
                    }
                    CCastleSiegeManager.updateSiegeZone(pc);
                    CSharedBroadcast.sendRecipientBestEffort(ownerShared, pc);
                }
            }
            evacuationRetry = CInstanceWork.scheduleRequired(() -> retryEvacuation(nextDefense, loc), 1000L);
        } catch (Exception failure) {
            _log.log(Level.WARNING, "Siege capture notices or evacuation failed", failure);
        }
        try {
            deleteTower();
            spawnTower();
            spawnDoor();
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Siege structures could not be restored after capture", failure);
        }
        try {
            CInstanceWork.execute(() -> {
                if (!IsUnderSiege() || _defense != nextDefense) return;
                byte[] injury = createInjury(nextDefense, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.DEFENCE);
                if (injury != null) CSharedBroadcast.sendWrittenClanBestEffort(nextDefense, out -> out.put(injury));
            });
        } catch (RejectedExecutionException rejected) {
            _log.log(Level.WARNING, "Siege capture notice rejected", rejected);
        }
    }

    private synchronized void retryEvacuation(L1Clan expectedDefense, L1Location loc) {
        if (!IsUnderSiege() || _defense != expectedDefense) return;
        evacuationRetry = null;
        forEachAreaPlayer(pc -> {
            L1Clan clan = pc.getClan();
            if (!pc.isGm() && (clan == null || clan.getCastleId() != _castleId)) {
                L1Location destination = loc.randomLocation(15, true);
                pc.start_teleport(destination.getX(), destination.getY(), destination.getMapId(),
                        pc.getHeading(), 169, true, true);
            }
        });
    }

    private void cancelEvacuationRetry() {
        CServerTimer retry = evacuationRetry;
        evacuationRetry = null;
        if (retry != null) retry.cancel();
    }

    @Override
    public synchronized void dispose() {
        super.dispose();
        cancelEvacuationRetry();
    }

    void onClockTick(long nowMillis, CCastleSiegeManager manager) {
        if (!_warLock.tryLock()) return;
        try {
            if (disposed || _state == CastleSiegeState.CLOSING || (manager != null && manager.transitionPaused())) return;
            CastleSiegeSchedule current = schedule;
            if (_state == CastleSiegeState.RUN) {
                if (capture == null && nowMillis > current.endMillis) submitTransition(CastleSiegeState.CLOSING, this::EndSiege);
            } else if (nowMillis > current.startMillis && nowMillis < current.limitMillis) {
                submitTransition(CastleSiegeState.RUN, this::StartSiege);
            } else if (_state == CastleSiegeState.IDLE
                    && nowMillis > current.readyMillis && nowMillis < current.startMillis) {
                _state = CastleSiegeState.READY;
            }
        } finally {
            _warLock.unlock();
        }
    }

    public void requestEndSiege() {
        _warLock.lock();
        try {
            if (capture != null) endAfterCapture = true;
            else if (IsUnderSiege()) submitTransition(CastleSiegeState.CLOSING, this::EndSiege);
        } finally {
            _warLock.unlock();
        }
    }

    private void submitTransition(CastleSiegeState next, Runnable work) {
        CastleSiegeState previous = _state;
        _state = next;
        try {
            CInstanceWork.execute(work);
        } catch (RejectedExecutionException rejected) {
            _state = previous;
            _log.log(Level.WARNING, "Siege transition rejected for castle " + _castleId, rejected);
        }
    }

    private void teleport(L1PcInstance pc, int x, int y, int mid) {
        boolean effect = !GameUtils.isNonAction(pc) && !pc.hasSkillEffect(L1SkillId.DESPERADO)
                && !pc.hasSkillEffect(L1SkillId.SHOCK_STUN);
        pc.start_teleport(x, y, mid, pc.getHeading(), 169, effect, false);
    }

    public CCastle proclaim(L1Clan clan) {
        if (!registerAttacker(clan)) return null;
        byte[] stream = createInjury(clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK);
        clan.onlinePlayers().forEach(member -> {
            if (stream != null && L1CastleLocation.checkInWarArea(_castleId, member)) Proto.sendRaw(member, stream);
            onLordBuff(member);
        });
        return this;
    }

    public CCastle proclaim(L1PcInstance pc) {
        if (!IsUnderSiege())
            return null;

        L1Clan clan = pc.getRedKnightClanId() > 0
                ? ClanTable.getInstance().getRedKnight(_castleId) : pc.getClan();
        if (!registerAttacker(clan)) {
            if (War.isOffenseClan(clan)) pc.sendPackets("전쟁: 이미 전쟁 중");
            return null;
        }

        byte[] stream = createInjury(clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK);
        clan.onlinePlayers().forEach(member -> {
            if (L1CastleLocation.checkInWarArea(_castleId, member)) {
                if (stream != null) Proto.sendRaw(member, stream);
                onLordBuff(member);
                final L1Location loc = createGetBackLocation();
                L1Location nloc = loc.randomLocation(15, true);
                teleport(member, nloc.getX(), nloc.getY(), nloc.getMapId());
            }
        });
        return this;
    }

    private boolean registerAttacker(L1Clan clan) {
        if (clan == null) return false;
        War previous = clan.getCurrentWar();
        if (previous != null && previous.getOffenseClan(clan.getClanId()) != null) return false;
        _warLock.lock();
        try {
            return IsUnderSiege() && register(clan, previous);
        } finally {
            _warLock.unlock();
        }
    }

    /** 수성/공성측별 injury 패킷 — clan null 가드는 기존 유지 */
    public byte[] createInjury(L1Clan clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind kind) {
        if (clan == null) {
            _log.warning("[CCastle] createInjury — clan is null. (castle: "
                    + getCastleId() + ", kind: " + kind + ")");
            return null;
        }
        final String clanName = clan.getClanName();
        if (clanName == null) {
            _log.warning("[CCastle] createInjury — clanName is null. clanId: "
                    + clan.getClanId());
            return null;
        }
        return SC_SIEGE_INJURY_TIME_NOIT.encodeFields(kind, getRemainingSeconds(), clanName);
    }

    /** off 상태용 — kind=ATTACK, 0초, 혈맹명 없음*/
    public static byte[] createInjury() {
        return SC_SIEGE_INJURY_TIME_NOIT.encodeFields(SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK, 0, null);
    }

    public void onLordBuff(L1PcInstance pc) {
        pc.setSkillEffect(L1SkillId.주군의버프, 3600000);
        SC_PacketBox.send(pc, SC_PacketBox.NONE_TIME_ICON, 1, 490);
    }

    public void offLordBuff(L1PcInstance pc) {
        L1Clan clan = pc.getClan();
        if (clan != null && clan.getCurrentWar() instanceof CCastle otherSiege
                && otherSiege != this && otherSiege.IsUnderSiege()) return;
        pc.removeSkillEffect(L1SkillId.주군의버프);
        SC_PacketBox.send(pc, SC_PacketBox.NONE_TIME_ICON, 0, 490);
    }

    
    private void clearEndedSiege() {
        _warLock.lock();
        try {
            clearOffenses();
            _state = CastleSiegeState.IDLE;
        } finally {
            _warLock.unlock();
        }

        L1Location loc = createGetBackLocation();
        forEachAreaPlayer(pc -> {
            try {
                L1Clan clan = pc.getClan();
                if (!pc.isGm() && (clan == null || clan.getCastleId() != _castleId)) {
                    L1Location destination = loc.randomLocation(15, true);
                    teleport(pc, destination.getX(), destination.getY(), destination.getMapId());
                }
            } catch (Exception failure) {
                _log.log(Level.WARNING, "Siege evacuation failed", failure);
            }
        });
    }

    private void forEachAreaPlayer(Consumer<L1PcInstance> action) {
        CWorld world = CWorld.getInstance();
        int outer = L1CastleLocation.getCastleByMapId(_castleId);
        int inner = L1CastleLocation.getCastleInnerMapId(_castleId);
        for (L1PcInstance pc : world.getVisiblePlayers(outer)) {
            if (L1CastleLocation.checkInWarArea(_castleId, pc)) action.accept(pc);
        }
        if (inner != 0 && inner != outer) {
            for (L1PcInstance pc : world.getVisiblePlayers(inner)) {
                if (L1CastleLocation.checkInWarArea(_castleId, pc)) action.accept(pc);
            }
        }
    }

    private void spawnTower() {
        L1WarSpawn wsp = new L1WarSpawn();
        wsp.SpawnTower(_castleId);
    }

    private void spawnDoor() {
        for (L1DoorInstance door : DoorSpawnTable.getInstance().getCastleDoors(_castleId)) {
            door.setAutoStatus(0);// 자동수리를 해제
            door.repairGate();
        }
    }

    private void spawns() {
        L1WarSpawn wsp = new L1WarSpawn();
        wsp.SpawnFlag(_castleId);
        spawnDoor();
    }

    private void deleteTower() {
        deleteTowers(fieldObjects());
        int innerMap = L1CastleLocation.getCastleInnerMapId(_castleId);
        if (innerMap != 0) deleteTowers(CWorld.getInstance().getMapObjects(innerMap));
    }

    private void deleteTowers(Iterable<L1Object> objects) {
        for (L1Object object : objects) {
            if (object instanceof L1TowerInstance tower && L1CastleLocation.checkInWarArea(_castleId, tower)) {
                tower.deleteMe();
            }
        }
    }

    private List<L1Object> fieldObjects() {
        int[] area = L1CastleLocation.getWarArea(_castleId);
        var terrain = L1WorldMap.getInstance().findMap(area[4]);
        List<L1Object> objects = new ArrayList<>();
        CWorld.getInstance().getClusterGrid().queryRectangle(terrain, area[0], area[2], area[1], area[3], objects);
        return objects;
    }

    public L1Location createGetBackLocation() {
        int[] arr = L1CastleLocation.getGetBackLoc(_castleId);
        return new L1Location(arr[0], arr[1], arr[2]);
    }

    private static final int[] SECURITY_MAP_IDS = {52, 248, 249, 250, 251};

    static void forEachSecurityEvacuee(int defenderId, Consumer<L1PcInstance> evacuate) {
        CWorld world = CWorld.getInstance();
        for (int id : SECURITY_MAP_IDS) {
            var terrain = L1WorldMap.getInstance().findMap(id);
            for (L1PcInstance pc : world.getVisiblePlayers(terrain)) {
                if (pc.getMap() == terrain && pc.isPublishedIn(terrain) && world.findObject(pc.getId()) == pc
                        && !pc.isLogoutStarted() && !pc.isGm() && (defenderId <= 0 || pc.getClanid() != defenderId))
                    evacuate.accept(pc);
            }
        }
    }

    private synchronized void StartSiege() {
        if (!IsUnderSiege()) return;
        try { spawns(); } catch (Exception failure) { _log.log(Level.WARNING, "Siege spawns", failure); }
        try { if (_security == 1) openCastleSecurity(); }
        catch (Exception failure) { _log.log(Level.WARNING, "Siege security", failure); }
        try { broadcastOpening(); }
        catch (Exception failure) { _log.log(Level.WARNING, "Siege opening notices", failure); }
        try { prepareDefenders(); }
        catch (Exception failure) { _log.log(Level.WARNING, "Siege defenders", failure); }
        SC_SystemMessage.broadcast(String.format("현재 진행중인 공성: '%s'", _castleName));
    }

    private void openCastleSecurity() {
        if (_castleId >= 1 && _castleId <= 4) {
            int[] loc = L1CastleLocation.getGetBackLoc(_castleId);
            forEachSecurityEvacuee(_defenseId, pc -> teleport(pc, loc[0], loc[1], loc[2]));
        }
        setCastleSecurity(0);
        CCastleSiegeManager.getInstance().updateCastle(this);
        CharacterTable.getInstance().updateLoc(_castleId, 52, 248, 249, 250, 251);
    }

    private void broadcastOpening() {
        String[] names = SC_WarStartMessage.snapshot();
        try (var icon = new CAsyncSendConstBuffer(
                     SC_IconMessage.nativeIndexWriter(1510, SimpleRgb.red(), 6298, 10),
                     Integer.MIN_VALUE, Integer.MAX_VALUE);
             var roster = new CAsyncSendConstBuffer(
                     SC_WarStartMessage.nativeWriter(names), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
                if (pc == null) continue;
                CSharedBroadcast.sendRecipientBestEffort(icon, pc);
                CSharedBroadcast.sendRecipientBestEffort(roster, pc);
            }
        }
    }

    private void prepareDefenders() {
        L1Clan defense = _defense;
        if (defense == null) {
            _log.info("무소유 성 공성 개시 — 방어측 처리 스킵. castle: " + _castleName);
            return;
        }
        byte[] injury = createInjury(defense, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.DEFENCE);
        int[] loc = L1CastleLocation.getGetBackLoc(_castleId);
        forEachAreaPlayer(pc -> {
            try {
                L1Clan clan = pc.getClan();
                if (clan == null) return;
                if (clan.getCastleId() == _castleId) {
                    CCastleSiegeManager.updateSiegeZone(pc);
                    if (injury != null) Proto.sendRaw(pc, injury);
                    onLordBuff(pc);
                } else {
                    teleport(pc, loc[0], loc[1], loc[2]);
                }
            } catch (Exception failure) {
                _log.log(Level.WARNING, "Siege defender setup failed", failure);
            }
        });
    }

    private synchronized void EndSiege() {
        if (disposed || _state != CastleSiegeState.CLOSING) return;
        try {
            cancelEvacuationRetry();
            _warLock.lock();
            try {
                schedule = schedule.nextOccurrence();
                _taxRate = Config.Tax_Rate;
            } finally {
                _warLock.unlock();
            }
            try {
                CCastleSiegeManager.getInstance().updateCastle(this);
            } catch (RuntimeException failure) {
                _log.log(Level.WARNING, "Could not queue next siege date for castle " + _castleId, failure);
            }

            for (L1PcInstance pc : CWorld.getInstance().getVisiblePlayers(L1CastleLocation.getCastleByMapId(_castleId))) {
                try {
                    CCastleSiegeManager.updateSiegeZone(pc);
                    offLordBuff(pc);
                } catch (RuntimeException failure) {
                    _log.log(Level.WARNING, "Siege cleanup failed for player " + pc.getId(), failure);
                }
            }
            for (L1Object obj : fieldObjects()) {
                if (!(obj instanceof L1FieldObjectInstance || obj instanceof L1CrownInstance
                        || obj instanceof L1TowerInstance)) continue;
                L1NpcInstance npc = (L1NpcInstance) obj;
                try {
                    if (L1CastleLocation.checkInWarArea(_castleId, npc)) npc.deleteMe();
                } catch (RuntimeException failure) {
                    _log.log(Level.WARNING, "Siege cleanup failed for object " + obj.getId(), failure);
                }
            }
            endDeclarations();
            spawnTower();
            spawnDoor();
        } catch (Exception failure) {
            _log.log(Level.WARNING, "Siege finish failed for castle " + _castleId, failure);
        } finally {
            clearEndedSiege();
        }
    }

    private void endDeclarations() {
        final L1Clan defense;
        final List<L1Clan> offenses;
        _warLock.lock();
        try {
            defense = _defense;
            offenses = clearOffenses();
        } finally {
            _warLock.unlock();
        }
        if (defense != null) {
            for (L1Clan clan : offenses) broadcastEndWar(defense, clan);
        }
    }
}
