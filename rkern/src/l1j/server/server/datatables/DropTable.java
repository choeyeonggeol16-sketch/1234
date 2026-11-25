package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.templates.L1Drop;

public class DropTable {

	private static final Logger _log = Logger.getLogger(DropTable.class.getName());
	private static DropTable _instance;

	private final TIntObjectMap<ArrayList<L1Drop>> _droplists;
	private final TIntObjectMap<L1Drop> _adenalists;

	public static DropTable getInstance() {
		if (_instance == null) {
			_instance = new DropTable();
		}
		return _instance;
	}

	private DropTable() {
		_droplists = allDropList();
		_adenalists = createAdenaDropList();
	}

	public static void reload() {
		DropTable oldInstance = _instance;
		_instance = new DropTable();
		if (oldInstance != null) {
			oldInstance._droplists.clear();
			oldInstance._adenalists.clear();
		}
	}

	public ArrayList<L1Drop> getDropList(int monid) {
		return _droplists.get(monid);
	}

	public boolean isDropListItem(int monid, int itemid) {
		ArrayList<L1Drop> drop = getDropList(monid);
		if (drop == null) return false;
		for (int i = 0; i < drop.size(); i++) {
			L1Drop d = drop.get(i);
			if (d != null && d.getItemid() == itemid) {
				return true;
			}
		}
		return false;
	}

	public L1Drop getDrop(int monid, int itemid) {
		ArrayList<L1Drop> drop = getDropList(monid);
		if (drop == null) return null;
		for (int i = 0; i < drop.size(); i++) {
			L1Drop d = drop.get(i);
			if (d != null && d.getItemid() == itemid) {
				return d;
			}
		}
		return null;
	}

	private TIntObjectMap<L1Drop> createAdenaDropList() {
		TIntObjectMap<L1Drop> map = new TIntObjectHashMap<L1Drop>(1024);
		Selector.exec("SELECT * FROM droplist_adena", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while (rs.next()) {
					int mobId = rs.getInt("mobId");
					int itemId = rs.getInt("itemId");
					int min = rs.getInt("min");
					int max = rs.getInt("max");
					int chance = rs.getInt("chance");
					L1Drop drop = new L1Drop(mobId, itemId, min, max, chance);
					map.put(drop.getMobid(), drop);
				}
			}
		});
		return map;
	}

	private TIntObjectMap<ArrayList<L1Drop>> allDropList() {
		TIntObjectMap<ArrayList<L1Drop>> droplistMap = new TIntObjectHashMap<ArrayList<L1Drop>>(2048);
		String sql = "SELECT * FROM droplist";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int mobId = rs.getInt("mobId");
				int itemId = rs.getInt("itemId");
				int min = rs.getInt("min");
				int max = rs.getInt("max");
				int chance = rs.getInt("chance");

				L1Drop drop = new L1Drop(mobId, itemId, min, max, chance);

				ArrayList<L1Drop> dropList = droplistMap.get(drop.getMobid());
				if (dropList == null) {
					dropList = new ArrayList<L1Drop>(8);
					droplistMap.put(drop.getMobid(), dropList);
				}
				dropList.add(drop);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "DropTable allDropList error", e);
		}
		return droplistMap;
	}

	public void append_drop_adena(L1PcInstance last_attacker, L1NpcInstance monster) {
		if (monster == null) return;

		L1Drop drop = _adenalists.get(monster.getNpcId());
		if (drop == null || monster.isResurrect()) return;

		if (Config.RATE_DROP_ADENA <= 0 || Config.RATE_DROP_ITEMS <= 0) return;

		double amount = DropItemTable.getInstance().getDropAmount(L1ItemId.ADENA);
		
		long min = (long) (drop.getMin() * amount * Config.RATE_DROP_ADENA);
		long max = (long) (drop.getMax() * amount * Config.RATE_DROP_ADENA);

		long cnt = min;
		long add = (max - min + 1);
		if (add > 1) cnt += ThreadLocalRandom.current().nextInt((int) Math.min(Integer.MAX_VALUE, add));

		if (cnt <= 0) return;


		if (cnt <= 0) return;
		if (cnt > MAX_STACKABLE) cnt = MAX_STACKABLE;

		L1ItemInstance item = CObjectDB.CreateItem(L1ItemId.ADENA, false);
		if (item == null) return;
		item.setCount((int) cnt);

		try {
			monster.getInventory().CreateOrAddItem(item);
		} catch (Throwable ignored) {}
	}

	private static final long MAX_STACKABLE = 2_000_000_000L;
	
	public void setDrop(L1NpcInstance npc, CInventory inventory) {
		if (Config.STANDBY_SERVER) return;
		if (npc == null || inventory == null) return;

		int mobId = npc.getNpcTemplate().get_npcId();
		ArrayList<L1Drop> dropList = _droplists.get(mobId);
		if (dropList == null || dropList.isEmpty()) return;

		double droprate = Config.RATE_DROP_ITEMS;
		double adenarate = Config.RATE_DROP_ADENA;
		if (droprate < 0) droprate = 0;
		if (adenarate < 0) adenarate = 0;
		if (droprate <= 0 && adenarate <= 0) return;

		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double rateOfMapId = MapsTable.getInstance().getDropRate(npc.getMap().getBaseMapId());

		for (int i = 0; i < dropList.size(); i++) {
			L1Drop drop = dropList.get(i);
			if (drop == null) continue;

			int itemId = drop.getItemid();
			if (adenarate == 0 && itemId == L1ItemId.ADENA) continue;

			int randomChance = rnd.nextInt(0x0f4240) + 1;
			double rateOfItem = DropItemTable.getInstance().getDropRate(itemId);

			double chance = drop.getChance() * droprate * rateOfMapId * rateOfItem;
			if (droprate == 0 || chance < randomChance) continue;

			double amount = DropItemTable.getInstance().getDropAmount(itemId);

			long min = (long) (drop.getMin() * amount);
			long max = (long) (drop.getMax() * amount);

			if (itemId == L1ItemId.ADENA) {
				min = (long) (min * adenarate);
				max = (long) (max * adenarate);
			}
			long cnt = min;
			long add = (max - min + 1);
			if (add > 1) cnt += rnd.nextInt((int) Math.min(Integer.MAX_VALUE, add));

			if (cnt <= 0) continue;
			if (cnt > MAX_STACKABLE) cnt = MAX_STACKABLE;

			if (CObjectDB.GetItem(itemId) == null) {
				_log.info("[DropTable] unknown itemId=" + itemId);
				continue;
			}

			L1ItemInstance item = CObjectDB.CreateItem(itemId, false);
			if (item == null) continue;

			item.setCount((int) cnt);

			try {
				inventory.CreateOrAddItem(item);
			} catch (Throwable ignored) {}
		}
	}
}
