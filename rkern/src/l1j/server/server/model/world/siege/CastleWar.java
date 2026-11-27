package l1j.server.server.model.world.siege;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
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
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_CastleMaster;
import l1j.server.server.model.network.core.sc.packet.SC_IconMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_WarStartMessage;
import l1j.server.server.model.world.siege.WarFactory.WAR_TYPE;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SIEGE_INJURY_TIME_NOIT;
import l1j.server.server.shared.L1Type;
import l1j.server.server.shared.SimpleRgb;
import l1j.server.server.utils.GameUtils;

public class CastleWar extends War {
    
    private static Logger _log = Logger.getLogger(CastleWar.class.getName());

    private static final String[] CASTLE_ANNOUNCEMENTS = {
        "데포로쥬: 이번에 공격할 성은 켄트성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 오크성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 윈다우드성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 기란성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 하이네성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 지저성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 아덴성이다. 수호성과 함께 성을 함락하라!"
    };

    public static CastleWar newInstance(L1Clan defense, int id, int castleId, String castleName) {
        return new CastleWar(defense, id, castleId, castleName);
    }

    private int _castleId;
    private String _castleName;
    private Calendar _nextCal;
    private Calendar _readyCal;
    private Calendar _endCal;
    private Calendar _limitCal;
    private int _taxRate;
    private int _publicMoney;
    private int _security;
    private CastleWarEState _state;

    protected CastleWar(L1Clan defense, int id, int castleId, String castleName) {
        super(defense, WAR_TYPE.CASTLE, id);

        _state = CastleWarEState.IDLE;
        _castleId = castleId;
        _castleName = castleName;
    }

    public int getCastleId() {
        return _castleId;
    }

    public String getCastleName() {
        return _castleName;
    }

    public Calendar nextCal() {
        return _nextCal;
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

    public void setState(CastleWarEState state) {
        _state = state;
    }

    public boolean isIdle() {
        return _state.equals(CastleWarEState.IDLE);
    }

    public boolean isRun() {
        return _state.equals(CastleWarEState.RUN);
    }

    public boolean isReady() {
        return _state.equals(CastleWarEState.READY);
    }

    public boolean isClosing() {
        return _state.equals(CastleWarEState.CLOSING);
    }

    public int getSpareSeconds() {
        Calendar curCal = RealTimeClock.getInstance().getRealTimeCalendar();
        int result = (int) ((_endCal.getTimeInMillis() - curCal.getTimeInMillis()) / 1000L);
        if (result > 0)
            return result;
        return 0;
    }

    public void nextCalendar(Timestamp ts) {
        Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
        cal.setTimeInMillis(ts.getTime());
        nextCalendar(cal);
    }

    public void nextCalendar(Calendar cal) {
        disposesCalendar();
        _nextCal = cal;
        _readyCal = (Calendar) _nextCal.clone();
        _readyCal.add(Calendar.MINUTE, -5);
        _endCal = (Calendar) _nextCal.clone();
        _endCal.add(Config.ALT_WAR_TIME_UNIT, Config.ALT_WAR_TIME);
        _limitCal = (Calendar) _endCal.clone();
    }

    public void updateTime(int increaseMinute) {
        Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
        Calendar tmp = (Calendar) cal.clone();
        tmp.add(Calendar.MINUTE, increaseMinute);
        if (tmp.getTimeInMillis() > _limitCal.getTimeInMillis()) {
            _endCal = (Calendar) _limitCal.clone();
            tmp.clear();
        } else {
            _endCal = tmp;
        }
    }

    @Override
    public void updateDefense(L1Clan nextDefense) {
        L1Clan oldDefense = _defense;
        if (oldDefense != null) {
            oldDefense.setCastleId(0);
        }
        super.updateDefense(nextDefense);
        _defense.setCastleId(_castleId);
        updateTime(Config.수성시간);
        CastleWarBusiness.getInstance().updateCastle(_castleId);
        
        try {
            final L1Location loc = createGetBackLocation();
            var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
      SC_ServerMessage.nativeWriter(643),Integer.MIN_VALUE,Integer.MAX_VALUE);
try {
var ownerShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_CastleMaster.nativeWriter(_castleId,nextDefense.getLeaderId()),Integer.MIN_VALUE,Integer.MAX_VALUE);
try {
for(L1PcInstance pc : CWorld.getInstance().getAllPlayers()){
                if(pc == null)
                    continue;
                L1Clan clan = pc.getClan();
                if ((clan == null || clan.getCastleId() != _castleId)) {
                    if (L1CastleLocation.checkInWarArea(_castleId, pc) && !pc.isGm()) {
                        L1Location nloc = loc.randomLocation(15, true);
                        teleport(pc, nloc.getX(), nloc.getY(), nloc.getMapId());
                    }
                    offLordBuff(pc);
                } else {
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(shared,pc);
                }
                CastleWarBusiness.move(pc);
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(ownerShared,pc);
            }
} finally {
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.closeOptional(ownerShared);
}
} finally {
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.closeOptional(shared);
}
            CInstanceWork.scheduleRequired(new Runnable(){
                @Override
                public void run(){
                    try{
                        for(L1PcInstance pc : CWorld.getInstance().getAllPlayers()){
                            if(pc == null)
                                continue;

                            L1Clan clan = pc.getClan();
                            if ((clan == null || clan.getCastleId() != _castleId)) {
                                if (L1CastleLocation.checkInWarArea(_castleId, pc) && !pc.isGm()) {
                                    L1Location nloc = loc.randomLocation(15, true);
                                    pc.start_teleport(nloc.getX(), nloc.getY(), nloc.getMapId(), pc.getHeading(), 169, true, true);
                                }
                            }
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }, 1000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
        deleteTower();
        spawnTower();
        spawnDoor();
        ClanTable.getInstance().updateClan(nextDefense);
        if (oldDefense != null)
            ClanTable.getInstance().updateClan(oldDefense);

        CInstanceWork.execute(() -> {
            if (_defense != nextDefense) return;
            byte[] injury = createInjury(nextDefense, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.DEFENCE);
            if (injury != null) CSharedBroadcast.sendWrittenClanBestEffort(nextDefense, out -> out.put(injury));
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        disposesCalendar();
    }

    private void disposesCalendar() {
        if (_nextCal != null) {
            _nextCal.clear();
            _nextCal = null;
        }
        if (_readyCal != null) {
            _readyCal.clear();
            _readyCal = null;
        }
        if (_endCal != null) {
            _endCal.clear();
            _endCal = null;
        }
        if (_limitCal != null) {
            _limitCal.clear();
            _limitCal = null;
        }
    }

    public boolean isRunTime(Calendar cal) {
        return !isRun() && cal.after(_nextCal) && (cal.before(_endCal) || cal.before(_limitCal));
    }

    public boolean isReadyTime(Calendar cal) {
        return isIdle() && cal.after(_readyCal) && cal.before(_nextCal);
    }

    public boolean isClosingTime(Calendar cal) {
        return isRun() && (cal.after(_limitCal) || cal.after(_endCal));
    }

    public void ready() {
        if (!isReady()) {
            setState(CastleWarEState.READY);
        }
    }

    public void run() {
        if (!isRun()) {
            final CastleWarEState previous = _state;
            setState(CastleWarEState.RUN);
            try {
                CInstanceWork.execute(new WarOpener());
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                if (_state == CastleWarEState.RUN) setState(previous);
                _log.warning("Castle war start rejected: " + _castleId + ": " + rejected);
                throw rejected;
            }
        }
    }

    public void close() {
        if (!isClosing() && isRun()) {
            final CastleWarEState previous = _state;
            setState(CastleWarEState.CLOSING);
            try {
                CInstanceWork.execute(new WarCloser());
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                if (_state == CastleWarEState.CLOSING) setState(previous);
                _log.warning("Castle war close rejected: " + _castleId + ": " + rejected);
                throw rejected;
            }
        }
    }

    private void teleport(L1PcInstance pc, int x, int y, int mid) {
        boolean effect = !GameUtils.isNonAction(pc) && !pc.hasSkillEffect(L1SkillId.DESPERADO)
                && !pc.hasSkillEffect(L1SkillId.SHOCK_STUN);
        pc.start_teleport(x, y, mid, pc.getHeading(), 169, effect, false);
    }

    public CastleWar proclaim(L1Clan clan) {
        if (!isRun())
            return null;

        if (War.isOffenseClan(clan))
            return null;

        register(clan);
        byte[] stream = createInjury(clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK);
        clan.onlinePlayers().forEach(member -> {
            if (L1CastleLocation.checkInWarArea(_castleId, member))
                l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(member, stream);
            onLordBuff(member);
        });
        return this;
    }

    public CastleWar proclaim(L1PcInstance pc) {
        if (!isRun())
            return null;

        L1Clan clan = null;
        if (pc.getRedKnightClanId() > 0)
            clan = ClanTable.getInstance().getRedKnight(_castleId);
        else
            clan = pc.getClan();
        if (War.isOffenseClan(clan)) {
            pc.sendPackets("전쟁: 이미 전쟁 중");
            return null;
        }

        register(clan);
        byte[] stream = createInjury(clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK);
        clan.onlinePlayers().forEach(member -> {
            if (L1CastleLocation.checkInWarArea(_castleId, member)) {
                l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(member, stream);
                onLordBuff(member);
                final L1Location loc = createGetBackLocation();
                L1Location nloc = loc.randomLocation(15, true);
                teleport(member, nloc.getX(), nloc.getY(), nloc.getMapId());
            }
        });
        return this;
    }

    /** 수성/공성측별 injury 패킷 — clan null 가드는 기존 유지 */
    public byte[] createInjury(L1Clan clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind kind) {
        if (clan == null) {
            _log.warning("[CastleWar] createInjury — clan is null. (castle: "
                    + getCastleId() + ", kind: " + kind + ")");
            return null;
        }
        final String clanName = clan.getClanName();
        if (clanName == null) {
            _log.warning("[CastleWar] createInjury — clanName is null. clanId: "
                    + clan.getClanId());
            return null;
        }
        return SC_SIEGE_INJURY_TIME_NOIT.encodeFields(kind, getSpareSeconds(), clanName);
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
        pc.removeSkillEffect(L1SkillId.주군의버프);
        SC_PacketBox.send(pc, SC_PacketBox.NONE_TIME_ICON, 0, 490);
    }

    
    public void exit() {
        setState(CastleWarEState.IDLE);
        initializeOffenses();

        final L1Location loc = createGetBackLocation();
        CWorld.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> pc != null)
        .forEach((L1PcInstance pc) -> {
            try {
                L1Clan clan = pc.getClan();
                if ((clan == null || clan.getCastleId() != _castleId)) {
                    if (L1CastleLocation.checkInWarArea(_castleId, pc) && !pc.isGm()) {
                        L1Location nloc = loc.randomLocation(15, true);
                        teleport(pc, nloc.getX(), nloc.getY(), nloc.getMapId());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void spawnTower() {
        L1WarSpawn wsp = new L1WarSpawn();
        wsp.SpawnTower(_castleId);
    }

    private void spawnDoor() {
        List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
        for (L1DoorInstance door : doors) {
            if (L1CastleLocation.checkInWarArea(_castleId, door)) {
                door.setAutoStatus(0);// 자동수리를 해제
                door.repairGate();
            }
        }
        doors = null;
    }

    private void spawns() {
        L1WarSpawn wsp = new L1WarSpawn();
        wsp.SpawnFlag(_castleId);
        spawnDoor();
    }

    private void deleteTower() {
        for (var npc : CWorld.getInstance().getAllNpc()) {
            if (npc instanceof L1TowerInstance tower && L1CastleLocation.checkInWarArea(_castleId, tower))
                tower.deleteMe();
        }
    }

    public L1Location createGetBackLocation() {
        int[] arr = L1CastleLocation.getGetBackLoc(_castleId);
        return new L1Location(arr[0], arr[1], arr[2]);
    }

    class WarReadier implements Runnable {
        @Override
        public void run() {
            int idx = -1;
            while (isReady()) {
                if (idx++ == -1) {
                    SC_IconMessage.broadcast(CASTLE_ANNOUNCEMENTS[_castleId - 1], SimpleRgb.red(), 6298, 10);
                } else if (idx >= 1) {
                    idx = -1;
                } else {
                    SC_IconMessage.broadcast(3757, SimpleRgb.red(), 6298, 10);
                }
                try {
                    Thread.sleep(30000L);
                } catch (Exception e) {
                }
            }
        }
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

    class WarOpener implements Runnable {
        @Override
        public void run() {
            try { spawns(); } catch (Exception e) { e.printStackTrace(); }
            try { if (_security == 1) onSecurity(); } catch (Exception e) { e.printStackTrace(); }
            try { selectMode(); } catch (Exception e) { e.printStackTrace(); }
            try { doStartup(); } catch (Exception e) { e.printStackTrace(); }
            try {
                SC_SystemMessage.broadcast(String.format("현재 진행중인 공성: '%s'", _castleName));
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void onSecurity() {
            if (_castleId >= 1 && _castleId <= 4) {
                final int[] loc = L1CastleLocation.getGetBackLoc(_castleId);
                forEachSecurityEvacuee(_defenseId, pc -> teleport(pc, loc[0], loc[1], loc[2]));

            }

            setCastleSecurity(0);
            CastleWarBusiness.getInstance().updateCastle(_castleId);
            CharacterTable.getInstance().updateLoc(_castleId, 52, 248, 249, 250, 251);
        }

        private void selectMode() {
        final String[] names=SC_WarStartMessage.snapshot();
        final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter iconWire=SC_IconMessage.nativeIndexWriter(
                1510,SimpleRgb.red(),6298,10);
        final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter rosterWire=SC_WarStartMessage.nativeWriter(names);
        var iconShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    iconWire,Integer.MIN_VALUE,Integer.MAX_VALUE);
var rosterShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    rosterWire,Integer.MIN_VALUE,Integer.MAX_VALUE);
try {
            for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
                if(pc!=null){
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(iconShared,pc);
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(rosterShared,pc);
                }
            }
        } finally {
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.closeOptional(rosterShared);
    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.closeOptional(iconShared);
}
    }

        private void doStartup() {
            final L1Clan defenseClan = _defense;
            if (defenseClan != null) {
                byte[] injury = createInjury(defenseClan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.DEFENCE);
                int[] loc = L1CastleLocation.getGetBackLoc(_castleId);
                CWorld.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> pc != null)
                .forEach((L1PcInstance pc) -> {
                    try {
                        if (L1CastleLocation.checkInWarArea(_castleId, pc)) {
                            L1Clan clan = pc.getClan();
                            if (clan != null) {
                                if (clan.getCastleId() == _castleId) {
                                    CastleWarBusiness.move(pc);
                                    if (injury != null)
                                        l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(pc, injury);
                                    onLordBuff(pc);
                                } else {
                                    teleport(pc, loc[0], loc[1], loc[2]);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } else {
                _log.info("[CastleWar] 무소유 성 공성 개시 — 방어측 처리 스킵. castle: " + _castleName);
            }
        }
    }

    class WarCloser implements Runnable {
        @Override
        public void run() {
            try {
                Calendar cal = (Calendar) _nextCal.clone();
                cal.add(Config.ALT_WAR_INTERVAL_UNIT, Config.ALT_WAR_INTERVAL);
                nextCalendar(cal);
                _taxRate = Config.Tax_Rate;
                CastleWarBusiness.getInstance().updateCastle(_castleId);

                CWorld.getInstance().createVisibleObjectsStream(L1CastleLocation.getCastleByMapId(_castleId))
                .filter((L1Object obj) -> obj != null).forEach((L1Object obj) -> {
                    if (obj.instanceOf(L1Type.L1TYPE_PC)) {
                        L1PcInstance pc = (L1PcInstance) obj;
                        CastleWarBusiness.move(pc);
                        offLordBuff(pc);
                    } else if (obj instanceof L1FieldObjectInstance || obj instanceof L1CrownInstance
                            || obj instanceof L1TowerInstance) {
                        L1NpcInstance npc = (L1NpcInstance) obj;
                        if (L1CastleLocation.checkInWarArea(_castleId, npc))
                            npc.deleteMe();
                    }
                });
                notifyTheEnd();
                spawnTower();
                spawnDoor();
              //  rewardDefense();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                exit();
            }
        }
    }

    public void notifyTheEnd() {
        try {
            _warLock.lock();
            final L1Clan defense = _defense;
            for (L1Clan clan : _offenses.values()) {
                if (defense != null) {
                    broadcastEndWar(defense, clan);
                }
                clan.clearCurrentWar(this);
            }
            _offenses.clear();
        } finally {
            _warLock.unlock();
        }
    }
}