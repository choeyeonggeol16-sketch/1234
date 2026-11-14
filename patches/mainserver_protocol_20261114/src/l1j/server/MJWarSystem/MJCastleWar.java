package l1j.server.MJWarSystem;
import l1j.server.server.model.network.core.sc.packet.SC_CastleMaster;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.MJWarSystem.MJWarFactory.WAR_TYPE;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1WarSpawn;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CrownInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.Packet.SC_SIEGE_INJURY_TIME_NOIT;
import l1j.server.server.model.network.core.sc.packet.SC_IconMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_WarStartMessage;
import l1j.server.server.utils.MJCommons;

public class MJCastleWar extends MJWar {
    
    private static Logger _log = Logger.getLogger(MJCastleWar.class.getName());

    private static final String[] CASTLE_ANNOUNCEMENTS = {
        "데포로쥬: 이번에 공격할 성은 켄트성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 오크성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 윈다우드성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 기란성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 하이네성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 지저성이다. 수호성과 함께 성을 함락하라!",
        "데포로쥬: 이번에 공격할 성은 아덴성이다. 수호성과 함께 성을 함락하라!"
    };

    public static MJCastleWar newInstance(L1Clan defense, int id, int castleId, String castleName) {
        return new MJCastleWar(defense, id, castleId, castleName);
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
    private MJCastleWarEState _state;

    protected MJCastleWar(L1Clan defense, int id, int castleId, String castleName) {
        super(defense, WAR_TYPE.CASTLE, id);

        _state = MJCastleWarEState.IDLE;
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

    public void setState(MJCastleWarEState state) {
        _state = state;
    }

    public boolean isIdle() {
        return _state.equals(MJCastleWarEState.IDLE);
    }

    public boolean isRun() {
        return _state.equals(MJCastleWarEState.RUN);
    }

    public boolean isReady() {
        return _state.equals(MJCastleWarEState.READY);
    }

    public boolean isClosing() {
        return _state.equals(MJCastleWarEState.CLOSING);
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
        MJCastleWarBusiness.getInstance().updateCastle(_castleId);
        
        try {
            final L1Location loc = createGetBackLocation();
            for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
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
                    SC_ServerMessage.send(pc, 643);
                }
                MJCastleWarBusiness.move(pc);
                SC_CastleMaster.send(pc, _castleId, nextDefense.getLeaderId());
            }
            ThreadPool.getInstance().scheduleRequired(new Runnable(){
                @Override
                public void run(){
                    try{
                        for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
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

        ThreadPool.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                byte[] injury = createInjury(_defense, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.DEFENCE);
                if (injury == null) return;
                byte[] raw = injury;
                for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                    if (pc == null || pc.getNetConnection() == null) continue;
                    L1Clan c = pc.getClan();
                    if (c != null && c.getClanId() == _defense.getClanId()) {
                        l1j.server.server.packets.Protocol.Proto.sendRaw(pc, raw);
                    }
                }
            }
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
            setState(MJCastleWarEState.READY);
            //GeneralThreadPool.getInstance().execute(new WarReadier());
        }
    }

    public void run() {
        if (!isRun()) {
            final MJCastleWarEState previous = _state;
            setState(MJCastleWarEState.RUN);
            try {
                ThreadPool.getInstance().execute(new WarOpener());
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                if (_state == MJCastleWarEState.RUN) setState(previous);
                _log.warning("Castle war start rejected: " + _castleId + ": " + rejected);
                throw rejected;
            }
        }
    }

    public void close() {
        if (!isClosing() && isRun()) {
            final MJCastleWarEState previous = _state;
            setState(MJCastleWarEState.CLOSING);
            try {
                ThreadPool.getInstance().execute(new WarCloser());
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                if (_state == MJCastleWarEState.CLOSING) setState(previous);
                _log.warning("Castle war close rejected: " + _castleId + ": " + rejected);
                throw rejected;
            }
        }
    }

    private void teleport(L1PcInstance pc, int x, int y, int mid) {
        if(MJCommons.isNonAction(pc) || pc.hasSkillEffect(L1SkillId.DESPERADO) || pc.hasSkillEffect(L1SkillId.SHOCK_STUN))
            pc.start_teleport(x, y, mid, pc.getHeading(), 169, false, false);
        pc.start_teleport(x, y, mid, pc.getHeading(), 169, true, false);
    }

    public MJCastleWar proclaim(L1Clan clan) {
        if (!isRun())
            return null;

        if (MJWar.isOffenseClan(clan))
            return null;

        register(clan);
        byte[] stream = createInjury(clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK);
        clan.createOnlineMembers().forEach((L1Clan.ClanMember m) -> {
            if (m.player != null) {
                if (L1CastleLocation.checkInWarArea(_castleId, m.player))
                    l1j.server.server.packets.Protocol.Proto.sendRaw(m.player, stream);
                onLordBuff(m.player);
            }
        });
        return this;
    }

    public MJCastleWar proclaim(L1PcInstance pc) {
        if (!isRun())
            return null;

        L1Clan clan = null;
        if (pc.getRedKnightClanId() > 0)
            clan = ClanTable.getInstance().getRedKnight(_castleId);
        else
            clan = pc.getClan();
        if (MJWar.isOffenseClan(clan)) {
            pc.sendPackets("전쟁: 이미 전쟁 중");
            return null;
        }

        register(clan);
        byte[] stream = createInjury(clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK);
        clan.createOnlineMembers().forEach((L1Clan.ClanMember m) -> {
            if (m.player != null) {
                if (L1CastleLocation.checkInWarArea(_castleId, m.player)){
                    l1j.server.server.packets.Protocol.Proto.sendRaw(m.player, stream);
                    onLordBuff(m.player);
                    final L1Location loc = createGetBackLocation();
                    L1Location nloc = loc.randomLocation(15, true);
                    teleport(m.player, nloc.getX(), nloc.getY(), nloc.getMapId());
                }
            }
        });
        return this;
    }

    /** 수성/공성측별 injury 패킷 — clan null 가드는 기존 유지 */
    public byte[] createInjury(L1Clan clan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind kind) {
        if (clan == null) {
            _log.warning("[MJCastleWar] createInjury — clan is null. (castle: "
                    + getCastleId() + ", kind: " + kind + ")");
            return null;
        }
        final String clanName = clan.getClanName();
        if (clanName == null) {
            _log.warning("[MJCastleWar] createInjury — clanName is null. clanId: "
                    + clan.getClanId());
            return null;
        }
        return new SC_SIEGE_INJURY_TIME_NOIT(kind, getSpareSeconds(), clanName).encode();
    }

    /** off 상태용 — kind=ATTACK, 0초, 혈맹명 없음*/
    public static byte[] createInjury() {
        return new SC_SIEGE_INJURY_TIME_NOIT(SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.ATTACK, 0, null).encode();
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
        setState(MJCastleWarEState.IDLE);
        initializeOffenses();

        final L1Location loc = createGetBackLocation();
        L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> pc != null)
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
        L1TowerInstance tower = null;
        for (L1Object l1object : L1World.getInstance().getObject()) {
            if (l1object instanceof L1TowerInstance) {
                tower = (L1TowerInstance) l1object;
                if (L1CastleLocation.checkInWarArea(_castleId, tower)) {
                    tower.deleteMe();
                }
            }
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
                    SC_IconMessage.broadcast(CASTLE_ANNOUNCEMENTS[_castleId - 1], MJSimpleRgb.red(), 6298, 10);
                } else if (idx >= 1) {
                    idx = -1;
                } else {
                    SC_IconMessage.broadcast(3757, MJSimpleRgb.red(), 6298, 10);
                }
                try {
                    Thread.sleep(30000L);
                } catch (Exception e) {
                }
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
                final int defenseId = _defenseId;
                L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> {
                    if (pc == null || pc.isGm() || pc.getClanid() == defenseId)
                        return false;

                    int mapId = pc.getMapId();
                    return (mapId == 52 || mapId == 248 || mapId == 249 || mapId == 250 || mapId == 251)
                            || pc.getClanid() <= 0;

                }
                        ).forEach((L1PcInstance pc) -> {teleport(pc, loc[0], loc[1], loc[2]);
                        });

            }

            setCastleSecurity(0);
            MJCastleWarBusiness.getInstance().updateCastle(_castleId);
            CharacterTable.getInstance().updateLoc(_castleId, 52, 248, 249, 250, 251);
        }

        private void selectMode() {
            
            final String[] names = SC_WarStartMessage.snapshot();
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc != null) {
                    SC_IconMessage.send(pc, 1510, MJSimpleRgb.red(), 6298, 10);
                    SC_WarStartMessage.send(pc, names);
                }
            }
        }

        private void doStartup() {
            final L1Clan defenseClan = _defense;
            if (defenseClan != null) {
                byte[] injury = createInjury(defenseClan, SC_SIEGE_INJURY_TIME_NOIT.SiegeKind.DEFENCE);
                int[] loc = L1CastleLocation.getGetBackLoc(_castleId);
                L1World.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> pc != null)
                .forEach((L1PcInstance pc) -> {
                    try {
                        if (L1CastleLocation.checkInWarArea(_castleId, pc)) {
                            L1Clan clan = pc.getClan();
                            if (clan != null) {
                                if (clan.getCastleId() == _castleId) {
                                    MJCastleWarBusiness.move(pc);
                                    if (injury != null)
                                        l1j.server.server.packets.Protocol.Proto.sendRaw(pc, injury);
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
                _log.info("[MJCastleWar] 무소유 성 공성 개시 — 방어측 처리 스킵. castle: " + _castleName);
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
                MJCastleWarBusiness.getInstance().updateCastle(_castleId);

                L1World.getInstance().createVisibleObjectsStream(L1CastleLocation.getCastleByMapId(_castleId))
                .filter((L1Object obj) -> obj != null).forEach((L1Object obj) -> {
                    if (obj.instanceOf(MJL1Type.L1TYPE_PC)) {
                        L1PcInstance pc = (L1PcInstance) obj;
                        MJCastleWarBusiness.move(pc);
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
                clan.setCurrentWar(null);
            }
            _offenses.clear();
        } finally {
            _warLock.unlock();
        }
    }
}