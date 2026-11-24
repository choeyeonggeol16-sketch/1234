package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1ArmorSets;

public class ArmorSetTable {
	
	private static final Logger _log = Logger.getLogger(ArmorSetTable.class.getName());

	private final List<L1ArmorSets> _armorSetList = new ArrayList<L1ArmorSets>();
	
	public static ArmorSetTable getInstance() {
		return CObjectDB.LoadSetItem();
	}

	ArmorSetTable() {
		load();
	}

	public static void reload() {
		CObjectDB.ReloadSetItem();
	}

	private void load() {
		_armorSetList.clear();
		String sql = "SELECT * FROM armor_set";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			fillTable(rs);
			_log.config("갑옷 세트(ArmorSet) 정보 " + _armorSetList.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "error while creating armor_set table", e);
		}
	}

	private void fillTable(ResultSet rs) throws SQLException {
		while (rs.next()) {
			L1ArmorSets as = new L1ArmorSets();
			as.setId(rs.getInt("id"));
			as.setSets(rs.getString("sets"));
			as.set_main_id(rs.getInt("main_id"));
			as.setPolyId(rs.getInt("polyid"));
			as.setAc(rs.getInt("ac"));
			as.setHp(rs.getInt("hp"));
			as.setMp(rs.getInt("mp"));
			as.setHpr(rs.getInt("hpr"));
			as.setMpr(rs.getInt("mpr"));
			as.setMr(rs.getInt("mr"));
			as.setStr(rs.getInt("str"));
			as.setDex(rs.getInt("dex"));
			as.setCon(rs.getInt("con"));
			as.setWis(rs.getInt("wis"));
			as.setCha(rs.getInt("cha"));
			as.setIntl(rs.getInt("intl"));
			as.set_defense_water(rs.getInt("defense_water"));
			as.set_defense_earth(rs.getInt("defense_earth"));
			as.set_defense_wind(rs.getInt("defense_wind"));
			as.set_defense_fire(rs.getInt("defense_fire"));
			
			_armorSetList.add(as);
		}
	}

	public L1ArmorSets[] getAllList() {
		return _armorSetList.toArray(new L1ArmorSets[_armorSetList.size()]);
	}
}