package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1NpcShop;

public class NpcCashShopSpawnTable {
	private static final Logger _log = Logger.getLogger(NpcCashShopSpawnTable.class.getName());

	private static volatile NpcCashShopSpawnTable _instance;

	private final ArrayList<L1NpcShop> npcShoplist = new ArrayList<>();

	public static NpcCashShopSpawnTable getInstance() {
		if (_instance == null) {
			synchronized (NpcCashShopSpawnTable.class) {
				if (_instance == null) {
					_instance = new NpcCashShopSpawnTable();
				}
			}
		}
		return _instance;
	}

	public static void reload() {
		NpcCashShopSpawnTable oldInstance = _instance;
		_instance = new NpcCashShopSpawnTable();
		if (oldInstance != null) {
			synchronized (oldInstance.npcShoplist) {
				oldInstance.npcShoplist.clear();
			}
		}
	}

	private NpcCashShopSpawnTable() {
		load();
	}

	public void load() {
		String sql = "SELECT * FROM spawnlist_npc_cash_shop";
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

				npcShoplist.add(shop);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "spawnlist_npc_cash_shop load error", e);
		}
	}

	public ArrayList<L1NpcShop> getList() {
		return npcShoplist;
	}

	public void Start() {
		try {
			ArrayList<L1NpcShop> list = getList();
			for (int i = 0; i < list.size(); i++) {
				L1NpcShop shop = list.get(i);

				L1NpcInstance npc = CObjectDB.CreateNPC(shop.getNpcId());
				if (npc == null) {
					_log.warning("캐시 샵 NPC 템플릿을 찾을 수 없음 [ID: " + shop.getNpcId() + ", 이름: " + shop.getName() + "]");
					continue;
				}

				npc.setId(IdFactory.getInstance().nextId());
				npc.setMap(shop.getMapId());

				npc.getLocation().set(shop.getX(), shop.getY(), shop.getMapId());
				npc.getLocation().forward(5);

				npc.setHomeX(npc.getX());
				npc.setHomeY(npc.getY());
				npc.setHeading(shop.getHeading());

				npc.setName(shop.getName());
				npc.setTitle(shop.getTitle());

				L1World.getInstance().storeAndEnterWorld(npc);

				npc.getLight().turnOnOffLight();
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "NpcCashShopSpawnTable Start error", e);
		}
	}
}