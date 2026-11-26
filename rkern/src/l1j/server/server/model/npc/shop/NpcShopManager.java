package l1j.server.server.model.npc.shop;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.task.TaskLane;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_NpcShopAction;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.model.shop.dynamic.DynamicShopItem;
import l1j.server.server.model.shop.dynamic.DynamicShopStorage;
import l1j.server.server.templates.L1NpcShop;
import l1j.server.server.templates.L1ShopItem;

/**
 * 운영자 상점 통합 매니저.
 */
public class NpcShopManager {

    private static final Logger _log = Logger.getLogger(NpcShopManager.class.getName());
    private static final NpcShopManager _instance = new NpcShopManager();
    public static NpcShopManager getInstance() { return _instance; }

    private NpcShopManager() {}

    /** 불변 가격표 엔트리 */
    public static final class ShopEntry {
        public final int itemId;
        public final long count;
        public final int enchant;
        public final long price;
        public final boolean purchase;
        ShopEntry(int itemId, long count, int enchant, long price, boolean purchase){
            this.itemId = itemId; this.count = count;
            this.enchant = enchant; this.price = price; this.purchase = purchase;
        }
    }

    // npcId → 가격표. reload 때 통째 교체만 함 
    private volatile Map<Integer, List<ShopEntry>> _priceList = Collections.emptyMap();
    // npcId → L1Shop. 구 경로 호환용 파생 캐시 
    private volatile Map<Integer, L1Shop> _l1Shops = Collections.emptyMap();
    // 스폰된 상점 NPC — 스폰(부팅 1회)과 리필
    private final ArrayList<L1NpcInstance> _spawned = new ArrayList<>();
    // 가시성: 스케줄 스레드(read)와 GM 커맨드 스레드(write)가 공유
    private volatile boolean _power = false;
    // 리필 스케줄 핸들 — stop 시 취소 (켬/끔 반복 시 스케줄 누적 방지)
    private CServerTimer _refillTask;
    // 템플릿 없는 아이템 경고 스팸 방지 (reload 시 초기화)
    private final Set<Integer> _warnedItems = new HashSet<>();

    // ═══════════════════ 상태 조회 ═══════════════════

    public boolean isPower() {
        return _power;
    }

    public L1Shop getShop(int npcId) {
        return _l1Shops.get(npcId);
    }

    public Map<Integer, L1Shop> getAllShops() {
        return _l1Shops;
    }

    // ═══════════════════ 가격표 로드/리로드 ═══════════════════

    /** 가격표 재로딩 + NPC 상품 즉시 반영 (스폰 유지) — GM 리로드 커맨드용 */
    public void reloadAndApply() {
        reload();
        refillAll();
    }

    public void reload() {
        Map<Integer, List<ShopEntry>> map = new HashMap<>();
        loadTable(map, "shop_npc");

        if (map.isEmpty() && !_priceList.isEmpty()) {
            _log.warning("[NpcShop] 가격표 로드 결과가 비어 기존 가격표를 유지합니다.");
            return;
        }
        _priceList = Collections.unmodifiableMap(map);
        _warnedItems.clear();
        Map<Integer, L1Shop> shops = new HashMap<>();
        for (Map.Entry<Integer, List<ShopEntry>> e : map.entrySet()) {
            shops.put(e.getKey(), buildL1Shop(e.getKey(), e.getValue()));
        }
        _l1Shops = Collections.unmodifiableMap(shops);

      //  _log.info("[NpcShop] 가격표 로드 완료: 상점 " + map.size() + "개");
    }

    /** ShopEntry 목록 → 기존 L1Shop 구조 변환 */
    private L1Shop buildL1Shop(int npcId, List<ShopEntry> entries) {
        List<L1ShopItem> sells = new ArrayList<>();
        List<L1ShopItem> buys = new ArrayList<>();
        for (ShopEntry e : entries) {
            L1ShopItem item = new L1ShopItem(e.itemId, (int) e.price, 1, e.enchant, false);
            item.set_count((int) e.count);
            if (e.purchase) buys.add(item);
            else sells.add(item);
        }
        return new L1Shop(npcId, sells, buys);
    }

    private void loadTable(Map<Integer, List<ShopEntry>> map, String table) {
        final String sql = "SELECT npc_id, item_id, selling_price, purchasing_price, count, enchant"
                + " FROM " + table + " ORDER BY npc_id, item_id";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                int npcId = rs.getInt("npc_id");
                int itemId = rs.getInt("item_id");
                int sellPrice = rs.getInt("selling_price");
                int buyPrice = rs.getInt("purchasing_price");
                long count = rs.getLong("count");
                int enchant = rs.getInt("enchant");

                List<ShopEntry> list = map.get(npcId);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(npcId, list);
                }
                if (sellPrice >= 0) {
                    list.add(new ShopEntry(itemId, count, enchant, sellPrice, false));
                }
                if (buyPrice >= 0) {
                    list.add(new ShopEntry(itemId, count, enchant, buyPrice, true));
                }
            }
        } catch (SQLException e) {
            _log.log(Level.WARNING, "[NpcShop] " + table + " 로드 실패", e);
        }
    }

    // ═══════════════════ 가동/중지 ═══════════════════

    public void start() {
        if (_power) {
            return; 
        }
        _power = true;
        reload();
        spawnAll();
        scheduleRefill();
     //   _log.info("[NpcShop] 운영자 상점 가동: NPC " + _spawned.size() + "개");
    }

    public void stop() {
        if (!_power) {
            return; 
        }
        _power = false;

        cancelRefill();

  //      int count = _spawned.size();
        ArrayList<L1NpcInstance> snapshot = new ArrayList<>(_spawned);
        for (int i = 0; i < snapshot.size(); i++) {
            L1NpcInstance npc = snapshot.get(i);
            if (npc == null) continue;
            try {
                // DB에 상점 정보 정리(character_shop) 후 NPC 소멸
                TaskLane.PERSISTENCE.execute(new DynamicShopStorage(npc, true));
                npc.deleteMe();
            } catch (Exception e) {
                _log.log(Level.WARNING, "[NpcShop] NPC 제거 실패", e);
            }
        }
        _spawned.clear();
  //      _log.info("[NpcShop] 운영자 상점 중지: NPC " + count + "개 제거 완료");
    }

    /** 리필 스케줄 등록 — 기존 핸들이 있으면 먼저 취소  */
    private synchronized void scheduleRefill() {
        cancelRefill();
        _refillTask = CServerTimer.fixedRate(this::refillAll, 3600000L, 3600000L);
    }

    public synchronized void cancelRefill() {
        if (_refillTask != null) {
            try {
                _refillTask.cancel();
            } catch (Exception ignore) {}
            _refillTask = null;
        }
    }

    // ═══════════════════ 스폰  ═══════════════════

    private void spawnAll() {
        List<L1NpcShop> all = new ArrayList<>();
        loadSpawns(all, "spawnlist_npc_shop");
    //    _log.info("[NpcShop] 스폰 로드 완료: " + all.size() + "개");

        for (int i = 0; i < all.size(); i++) {
            spawnOne(all.get(i));
        }
    }

    private void loadSpawns(List<L1NpcShop> out, String table) {
        final String sql = "SELECT npc_id, name, locx, locy, mapid, heading, title, shop_name FROM " + table;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                L1NpcShop shop = new L1NpcShop();
                shop.setNpcId(rs.getInt("npc_id"));
                shop.setName(rs.getString("name"));
                shop.setX(rs.getInt("locx"));
                shop.setY(rs.getInt("locy"));
                shop.setMapId(rs.getShort("mapid"));
                shop.setHeading(rs.getInt("heading"));
                shop.setTitle(rs.getString("title"));
                shop.setShopName(rs.getString("shop_name"));
                out.add(shop);
            }
        } catch (SQLException e) {
            _log.log(Level.WARNING, "[NpcShop] " + table + " 스폰 로드 실패", e);
        }
    }

    private void spawnOne(L1NpcShop shop) {
        try {
            L1NpcInstance npc = CObjectDB.CreateNPC(shop.getNpcId());
            npc.setId(IdFactory.getInstance().nextId());
            npc.setMap(shop.getMapId());
            npc.getLocation().set(shop.getX(), shop.getY(), shop.getMapId());
            npc.getLocation().forward(5);
            npc.setHomeX(npc.getX());
            npc.setHomeY(npc.getY());
            npc.setHeading(shop.getHeading());
            npc.setName(shop.getName());
            npc.setTitle(shop.getTitle());
            L1NpcShopInstance obj = (L1NpcShopInstance) npc;
            obj.setShopName(shop.getShopName());
            L1World.getInstance().storeAndEnterWorld(npc);
            npc.getLight().turnOnOffLight();
            obj.setState(1);
            SC_NpcShopAction.broadcast(npc, npc.getId(), ActionCodes.ACTION_Shop, shop.getShopName());
            refillOne(npc);
            _spawned.add(npc);
        } catch (Exception e) {
            _log.log(Level.WARNING, "[NpcShop] NPC 스폰 실패 (npc_id=" + shop.getNpcId() + ") — 건너뜀", e);
        }
    }

    // ═══════════════════ 리필 ═══════════════════

    private void refillAll() {
        if (!_power) return;

        try {
            ArrayList<L1NpcInstance> snapshot = new ArrayList<>(_spawned);
            for (int i = 0; i < snapshot.size(); i++) {
                try {
                    refillOne(snapshot.get(i));
                } catch (Exception e) {
                    _log.log(Level.WARNING, "[NpcShop] 개별 NPC 리필 실패: " + snapshot.get(i).getId(), e);
                }
            }
            DynamicShopStorage.updateProcess(snapshot);
        } catch (Exception e) {
            _log.log(Level.WARNING, "[NpcShop] 리필 주기 오류 — 다음 주기에 재시도됨", e);
        }
    }
    /** 새 리스트 만들어 통째 교체 — add 누적 소멸 */
    private void refillOne(L1NpcInstance npcInstance) {
        if (!(npcInstance instanceof L1NpcShopInstance)) return;
        L1NpcShopInstance npc = (L1NpcShopInstance) npcInstance;

        List<ShopEntry> entries = _priceList.get(npc.getNpcId());
        if (entries == null) {
            npc.setSellings(new ArrayList<DynamicShopItem>(0));
            npc.setPurchasings(new ArrayList<DynamicShopItem>(0));
            return;
        }

        ArrayList<DynamicShopItem> sells = new ArrayList<>(entries.size());
        ArrayList<DynamicShopItem> buys = new ArrayList<>(entries.size());
        int idx = 0;
        for (ShopEntry e : entries) {
            DynamicShopItem item = DynamicShopItem.create(e, ++idx);
            if (item == null) {
                if (_warnedItems.add(e.itemId)) {
                    _log.warning("[NpcShop] 템플릿 없는 아이템 스킵: " + e.itemId);
                }
                continue;
            }
            if (e.purchase) buys.add(item);
            else sells.add(item);
        }
        npc.setSellings(sells);
        npc.setPurchasings(buys);
    }
}
