package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1PolyMorph;

public class PolyTable {
	private static final Logger _log = Logger.getLogger(PolyTable.class.getName());

	private final Map<String, L1PolyMorph> _polymorphs = new HashMap<String, L1PolyMorph>(256);

	private final TIntObjectMap<L1PolyMorph> _polyIdIndex = new TIntObjectHashMap<L1PolyMorph>(256);
	private final TIntObjectMap<Integer> _rank_polys = new TIntObjectHashMap<Integer>(64);

	private boolean _polyEvent;

	private static class SingletonHolder {
		private static PolyTable INSTANCE = new PolyTable();
	}

	public static PolyTable getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private PolyTable() {
		loadPolymorphs();
	}

	public static void reload() {
		SingletonHolder.INSTANCE = new PolyTable();
	}

	private void loadPolymorphs() {
		String sql = "SELECT * FROM polymorphs";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement(sql);
				ResultSet rs = pstm.executeQuery()) {

			fillPolyTable(rs);

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "error while creating polymorph table", e);
		}
	}

	private void fillPolyTable(ResultSet rs) throws SQLException {
		while (rs.next()) {
			int id = rs.getInt("id");
			String name = rs.getString("name");
			int polyId = rs.getInt("polyid");
			int minLevel = rs.getInt("minlevel");
			int weaponEquipFlg = rs.getInt("weaponequip");
			int armorEquipFlg = rs.getInt("armorequip");
			boolean canUseSkill = rs.getBoolean("isSkillUse");
			int causeFlg = rs.getInt("cause");

			// 변신 버그 방지
			int option = 0;
			String optStr = rs.getString("option");
			if (optStr != null) {
				option = PolyOption(optStr);
			}

			L1PolyMorph poly = new L1PolyMorph(id, name, polyId, minLevel, weaponEquipFlg, armorEquipFlg, canUseSkill, causeFlg, option);

			if (name != null) {
				_polymorphs.put(name, poly);
			}
			_polyIdIndex.put(polyId, poly);

			if (name != null && name.startsWith("rangking")) {
				_rank_polys.put(polyId, polyId);
			}
		}
		_log.config("변신 리스트 " + _polymorphs.size() + "건 로드 완료");
	}

	public L1PolyMorph getTemplate(String name) {
		if (name == null) return null;
		return _polymorphs.get(name);
	}

	public L1PolyMorph getTemplate(int polyId) {
		return _polyIdIndex.get(polyId);
	}

	public boolean isPolyEvent() {
		return _polyEvent;
	}

	public void setPolyEvent(boolean i) {
		_polyEvent = i;
	}

	public L1PolyMorph find(int polyId) {
		return _polyIdIndex.get(polyId);
	}

	// 변신 버그 방지
	private int PolyOption(String type) {
		if (type.equalsIgnoreCase("Poly_Scroll")) {
			return 1;
		}
		if (type.equalsIgnoreCase("Poly_RingMaster")) {
			return 2;
		}
		if (type.equalsIgnoreCase("Poly_Event")) {
			return 3;
		}
		if (type.equalsIgnoreCase("Poly_Class")) {
			return 4;
		}
		if (type.equalsIgnoreCase("Poly_EV")) {
			return 5;
		}
		return 0;
	}

	public boolean isRankingPoly(int sprId) {
		return _rank_polys.containsKey(sprId);
	}
}