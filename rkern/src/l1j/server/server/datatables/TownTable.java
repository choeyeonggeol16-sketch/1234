package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Town;

public class TownTable {
	private static final Logger _log = Logger.getLogger(TownTable.class.getName());
	private static TownTable _instance;

	private final TIntObjectMap<L1Town> _towns = new TIntObjectHashMap<L1Town>(32);

	public static TownTable getInstance() {
		if (_instance == null) {
			_instance = new TownTable();
		}
		return _instance;
	}

	private TownTable() {
		load();
	}

	public void load() {
		_towns.clear();
		String sql = "SELECT * FROM town";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1Town town = new L1Town();
				int townid = rs.getInt("town_id");
				town.set_townid(townid);
				town.set_name(rs.getString("name"));
				town.set_leader_id(rs.getInt("leader_id"));
				town.set_leader_name(rs.getString("leader_name"));
				town.set_tax_rate(rs.getInt("tax_rate"));
				town.set_tax_rate_reserved(rs.getInt("tax_rate_reserved"));
				town.set_sales_money(rs.getInt("sales_money"));
				town.set_sales_money_yesterday(rs.getInt("sales_money_yesterday"));
				town.set_town_tax(rs.getInt("town_tax"));
				town.set_town_fix_tax(rs.getInt("town_fix_tax"));

				_towns.put(townid, town);
			}
			_log.config("마을 정보 " + _towns.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "TownTable load error", e);
		}
	}

	public L1Town[] getTownTableList() {
		return _towns.values(new L1Town[_towns.size()]);
	}

	public L1Town getTownTable(int id) {
		return _towns.get(id);
	}

	public boolean isLeader(L1PcInstance pc, int town_id) {
		L1Town town = getTownTable(town_id);
		if (town == null || pc == null) return false;
		return (town.get_leader_id() == pc.getId());
	}

	public void updateTaxRate() {
		String sql = "UPDATE town SET tax_rate = tax_rate_reserved";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "TownTable updateTaxRate error", e);
		}
	}

	public void updateSalesMoneyYesterday() {
		String sql = "UPDATE town SET sales_money_yesterday = sales_money, sales_money = 0";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "TownTable updateSalesMoneyYesterday error", e);
		}
	}
}