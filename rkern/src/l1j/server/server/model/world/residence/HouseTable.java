package l1j.server.server.model.world.residence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;

public final class HouseTable {

    private static final Logger _log = Logger.getLogger(HouseTable.class.getName());

    private static volatile HouseTable _instance;

    private static final TimeZone TZ = TimeZone.getTimeZone(Config.TIME_ZONE);

    private final ConcurrentHashMap<Integer, L1House> _houses = new ConcurrentHashMap<>(256);

    private final AtomicReference<L1House[]> _snapshot = new AtomicReference<>(new L1House[0]);
    private final AtomicBoolean _snapshotDirty = new AtomicBoolean(true);

    private volatile List<Integer> _houseIdView = Collections.emptyList();
    private final AtomicBoolean _idCacheDirty = new AtomicBoolean(true);

    public static HouseTable getInstance() {
        HouseTable inst = _instance;
        if (inst == null) {
            synchronized (HouseTable.class) {
                inst = _instance;
                if (inst == null) {
                    inst = new HouseTable();
                    _instance = inst;
                }
            }
        }
        return inst;
    }


    public static void reload() {
        final HouseTable loaded;
        synchronized (HouseTable.class) {
            _instance = loaded = new HouseTable();
        }
        CWorldAgitTimer.housesReloaded(loaded);
    }

    private HouseTable() {
        loadFromDb();
        rebuildIdCache();      // 초기 1회
        rebuildSnapshot();     // 초기 1회
    }

    private void loadFromDb() {
        _houses.clear();

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement("SELECT * FROM house ORDER BY house_id");
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                L1House house = new L1House();
                house.setHouseId(rs.getInt(1));
                house.setHouseName(rs.getString(2));
                house.setHouseArea(rs.getInt(3));
                house.setLocation(rs.getString(4));
                house.setKeeperId(rs.getInt(5));
                house.setOnSale(rs.getInt(6) == 1);
                house.setPurchaseBasement(rs.getInt(7) == 1);

                Timestamp ts = rs.getTimestamp(8);
                house.setTaxDeadline(timestampToCalendar(ts));

                _houses.put(house.getHouseId(), house);
            }

            // 구조가 바뀐 상태
            _idCacheDirty.set(true);
            _snapshotDirty.set(true);

        } catch (SQLException e) {
            _log.log(Level.SEVERE, "HouseTable loadFromDb() failed", e);
            // The constructor must fail: reload() keeps the previous singleton,
            // and cold start must not publish a partial house ownership image.
            throw new IllegalStateException("House table DB load failed", e);
        }
    }

    private static Calendar timestampToCalendar(Timestamp ts) {
        if (ts == null) return null;
        Calendar cal = Calendar.getInstance(TZ);
        cal.setTimeInMillis(ts.getTime());
        return cal;
    }

    private void rebuildSnapshot() {
        _snapshot.set(_houses.values().toArray(new L1House[_houses.size()]));
        _snapshotDirty.set(false);
    }

    private void rebuildIdCache() {
        ArrayList<Integer> ids = new ArrayList<>(_houses.size());
        ids.addAll(_houses.keySet());
        Collections.sort(ids);
        _houseIdView = Collections.unmodifiableList(ids);
        _idCacheDirty.set(false);
    }

    private void ensureSnapshot() {
        if (_snapshotDirty.get()) {
            synchronized (this) {
                if (_snapshotDirty.get()) {
                    rebuildSnapshot();
                }
            }
        }
    }

    private void ensureIdCache() {
        if (_idCacheDirty.get()) {
            synchronized (this) {
                if (_idCacheDirty.get()) {
                    rebuildIdCache();
                }
            }
        }
    }

    public L1House[] getHouseTableList() {
        ensureSnapshot();
        return _snapshot.get();
    }

    public L1House getHouseTable(int houseId) {
        return _houses.get(houseId);
    }

    /** Rename only after the SQL row is committed; reject stale ownership. */
    public boolean renameHouse(int houseId, int clanId, int leaderId, String name) {
        if (houseId <= 0 || clanId <= 0 || leaderId <= 0 || name == null
                || name.isEmpty() || name.length() > 16) return false;
        L1House house = _houses.get(houseId);
        if (house == null) return false;
        synchronized (house) {
            if (_houses.get(houseId) != house || house.isOnSale()) return false;
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "UPDATE house SET house_name=? WHERE house_id=? AND is_on_sale=0 "
                     + "AND EXISTS (SELECT 1 FROM clan_data WHERE clan_id=? AND leader_id=? AND hashouse=?)")) {
                ps.setString(1, name);
                ps.setInt(2, houseId);
                ps.setInt(3, clanId);
                ps.setInt(4, leaderId);
                ps.setInt(5, houseId);
                if (ps.executeUpdate() != 1) return false;
                house.setHouseName(name);
                return true;
            } catch (SQLException e) {
                _log.log(Level.WARNING, "House rename rejected houseId=" + houseId, e);
                return false;
            }
        }
    }

    public void updateHouse(L1House house) {
        if (house == null) return;

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(
                 "UPDATE house SET house_name=?, house_area=?, location=?, keeper_id=?, " +
                 "is_on_sale=?, is_purchase_basement=?, tax_deadline=? WHERE house_id=?")) {

            pstm.setString(1, house.getHouseName());
            pstm.setInt(2, house.getHouseArea());
            pstm.setString(3, house.getLocation());
            pstm.setInt(4, house.getKeeperId());
            pstm.setInt(5, house.isOnSale() ? 1 : 0);
            pstm.setInt(6, house.isPurchaseBasement() ? 1 : 0);

            Calendar dl = house.getTaxDeadline();
            pstm.setTimestamp(7, dl != null ? new Timestamp(dl.getTimeInMillis()) : null);

            pstm.setInt(8, house.getHouseId());
            pstm.executeUpdate();

            _houses.put(house.getHouseId(), house);

            _snapshotDirty.set(true);
            CWorldAgitTimer.houseChanged(house.getHouseId());

        } catch (SQLException e) {
            _log.log(Level.SEVERE, "HouseTable updateHouse() failed. houseId=" + house.getHouseId(), e);
        }
    }

    /**
     * 외부에서 리스트 수정해도 내부 캐시는 안 깨짐 (복사본 반환)
     */
    public static List<Integer> getHouseIdList() {
        HouseTable t = HouseTable.getInstance();
        t.ensureIdCache();
        return new ArrayList<>(t._houseIdView);
    }

    /**
     * 내부용: 복사 없이 읽기(절대 수정하면 안됨)
     */
    public List<Integer> getHouseIdListView() {
        ensureIdCache();
        return _houseIdView;
    }

}
