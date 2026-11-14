package l1j.server.MJWarSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import LIN.SC_SIEGE_INJURY_TIME_NOIT;
import LIN.SC_SIEGE_ZONE_UPDATE_NOT;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class MJCastleWarBusiness implements TimeListener{

    private static Logger _log = Logger.getLogger(MJCastleWarBusiness.class.getName());

    private static final String[] _viewMessages = new String[]{
        "공성전이 진행중입니다.",
        "성을 소유하고 있는 혈맹은 다음과 같습니다."
    };

    private static volatile byte[] _zoneBegin, _zoneEnd, _offInjury;

    private static byte[] zoneBegin() {
        byte[] d = _zoneBegin;
        if (d == null) {
            _zoneBegin = d = new LIN.SC_SIEGE_ZONE_UPDATE_NOT(
                    LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND.BEGIN).encode();
        }
        return d;
    }

    private static byte[] zoneEnd() {
        byte[] d = _zoneEnd;
        if (d == null) {
            _zoneEnd = d = new LIN.SC_SIEGE_ZONE_UPDATE_NOT(
                    LIN.SC_SIEGE_ZONE_UPDATE_NOT.SIEGE_ZONE_KIND.END).encode();
        }
        return d;
    }
    private static byte[] offInjury() {
        byte[] d = _offInjury;
        if (d == null) {
            _offInjury = d = new LIN.SC_SIEGE_INJURY_TIME_NOIT(
                    LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND.ATTACK, 0, null).encode();
        }
        return d;
    }

    public static void move(L1PcInstance pc){
        if(pc.war_zone){
            int castleId = L1CastleLocation.getCastleIdByArea(pc);
            if(castleId == 0 || !getInstance().isNowWar(castleId)){
                pc.war_zone = false;
                l1j.server.server.packets.Protocol.Proto.sendRaw(pc, zoneEnd());
                l1j.server.server.packets.Protocol.Proto.sendRaw(pc, offInjury());
            }
        }else{
            int castleId = L1CastleLocation.getCastleIdByArea(pc);
            MJCastleWar war = getInstance().get(castleId);
            if(castleId != 0 && war != null && war.isRun()){
                pc.war_zone = true;
                l1j.server.server.packets.Protocol.Proto.sendRaw(pc, zoneBegin());

                L1Clan clan = pc.getClan();
                final L1Clan defenseClan = war.getDefenseClan();

                LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND kind;
                String pledgeName = null;
                if(clan != null && MJWar.isSameWar(clan, defenseClan)){
                    if(war.getOffenseClan(clan.getClanId()) != null)
                        kind = LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND.ATTACK;
                    else
                        kind = LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND.DEFENCE;
                    pledgeName = clan.getClanName();
                }else{
                    kind = LIN.SC_SIEGE_INJURY_TIME_NOIT.SIEGE_KIND.ATTACK;   // 무혈맹/무소속 → 공격측 표시
                }
                LIN.SC_SIEGE_INJURY_TIME_NOIT.sendConstructed(pc, kind, war.getSpareSeconds(), pledgeName);
            }
        }
    }

    // ─────────── 이하 전부 원본 그대로 ───────────

    private static MJCastleWarBusiness _instance;
    public static MJCastleWarBusiness getInstance(){
        if(_instance == null)
            _instance = new MJCastleWarBusiness();
        return _instance;
    }

    private ConcurrentHashMap<Integer, MJCastleWar> _castleWars;
    private MJCastleWarBusiness(){
        _castleWars = loadedCastleWars();
    }

    public void reload(){
        ConcurrentHashMap<Integer, MJCastleWar> tmp = _castleWars;
        _castleWars = loadedCastleWars();
        if(tmp != null){
            for(MJCastleWar war : tmp.values())
                war.dispose();
            tmp.clear();
            tmp = null;
        }
    }

    public ConcurrentHashMap<Integer, MJCastleWar> loadedCastleWars(){
        ConcurrentHashMap<Integer, MJCastleWar> wars = new ConcurrentHashMap<Integer, MJCastleWar>(6);
        Connection con = null;
        PreparedStatement pstm = null;
        ResultSet rs = null;
        try{
            con = DatabaseFactory.getInstance().getConnection();
            pstm = con.prepareStatement("SELECT * FROM castle");
            rs = pstm.executeQuery();
            while(rs.next()){
                int castleId = rs.getInt("castle_id");
                L1Clan clan = ClanTable.getInstance().findCastleClan(castleId);
                MJCastleWar war = MJWarFactory.createCastleWar(clan, castleId, rs.getString("name"));
                war.nextCalendar(rs.getTimestamp("war_time"));
                war.setTaxRate(rs.getInt("tax_rate"));
                war.setPublicMoney(rs.getInt("public_money"));
                wars.put(castleId, war);
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            SQLUtil.close(rs, pstm, con);
        }
        return wars;
    }

    public static final int TREASURY_ORDER_KEY = l1j.server.server.storage.CacheD.accountOrderKey("$castle-treasury-global");

    /** Stage treasury arithmetic on the inventory receipt's connection. */
    public Runnable stageTreasury(l1j.server.server.model.L1InventoryTransaction tx,
            int castleId, int delta) {
        final MJCastleWar war = get(castleId);
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
        return () -> war.setPublicMoney((int)after);
    }

    public void updateCastle(int castleId) {
        final MJCastleWar war = _castleWars.get(castleId);
        if(war==null)return;
        final String name=war.getCastleName();
        final Timestamp next=new Timestamp(war.nextCal().getTimeInMillis());
        final int tax=war.getTaxRate();
        l1j.server.server.storage.CacheD.getInstance().submitDbJobOrdered(TREASURY_ORDER_KEY,()->{
            try(Connection con=DatabaseFactory.getInstance().getConnection();
                PreparedStatement ps=con.prepareStatement("UPDATE castle SET name=?,war_time=?,tax_rate=? WHERE castle_id=?")) {
                ps.setString(1,name);ps.setTimestamp(2,next);ps.setInt(3,tax);ps.setInt(4,castleId);
                if(ps.executeUpdate()>1)throw new java.sql.SQLException("Duplicate castle identity");
            } catch(java.sql.SQLException failure) {throw new IllegalStateException("Castle metadata save",failure);}
        });
    }

    public void run(){
        RealTimeClock.getInstance().addListener(this, Calendar.SECOND);
    }

    @Override
    public void onMonthChanged(BaseTime time) {}

    @Override
    public void onDayChanged(BaseTime time) {}

    @Override
    public void onHourChanged(BaseTime time) {}

    @Override
    public void onMinuteChanged(BaseTime time) {}

    @Override
    public void onSecondChanged(BaseTime time) {
        Calendar cal = time.getCalendar();
        for(MJCastleWar war : _castleWars.values()){
            if(war.isClosing()){
                continue;
            }else if(war.isRun()){
                if(war.isClosingTime(cal))
                    war.close();
            }else{
                if(war.isReadyTime(cal))
                    war.ready();
                else if(war.isRunTime(cal))
                    war.run();
            }
        }
    }

    public void dispose(){
        if(_castleWars != null){
            for(MJCastleWar war : _castleWars.values())
                war.dispose();
            _castleWars.clear();
            _castleWars = null;
        }
    }

    public boolean isNowWar(int castleId){
        MJCastleWar war = get(castleId);
        if(war == null)
            return false;
        return war.isRun();
    }

    public boolean isNowReady(int castleId){
        MJCastleWar war = get(castleId);
        return war != null && war.isReady();
    }

    public MJCastleWar get(int castleId){
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

        if(!isNowWar(castleId)){
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

        get(castleId).proclaim(pc);
    }

    public void viewNowCastleWarState(L1PcInstance pc){
        boolean isFirst = true;
        for(MJCastleWar war : _castleWars.values()){
            if(!war.isRun())
                continue;

            final L1Clan defenseClan = war.getDefenseClan();
            if (defenseClan == null) {
                _log.warning("[MJCastleWar] 진행 중 공성의 방어클랜이 null! castle: "
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

    public MJCastleWar findWar(String name){
        for(MJCastleWar war : _castleWars.values()){
            if(war.getCastleName().startsWith(name))
                return war;
        }
        return null;
    }
}