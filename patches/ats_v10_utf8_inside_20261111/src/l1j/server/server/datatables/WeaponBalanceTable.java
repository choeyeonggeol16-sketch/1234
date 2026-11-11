package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastMap;
import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.item.EnchantDamage;
import l1j.server.server.utils.SQLUtil;


public class WeaponBalanceTable {
	
	private static WeaponBalanceTable _instance;
	public static WeaponBalanceTable getInstance(){
		if(_instance == null){
			_instance = new WeaponBalanceTable();
		}
		return _instance;
	}
	private static Logger _log = Logger.getLogger(WeaponBalanceTable.class.getName());

	private final FastMap<String, EnchantDamage> _weaponName = new FastMap<String, EnchantDamage>();
	private final FastMap<Integer, EnchantDamage> _weaponIdList = new FastMap<Integer, EnchantDamage>();
	
	private WeaponBalanceTable(){
		loadWeaponBalance();
	}
	
	public static void reload() {
		WeaponBalanceTable oldInstance = _instance;
		_instance = new  WeaponBalanceTable();
		if (oldInstance != null)
			oldInstance._weaponName.clear();
			oldInstance._weaponIdList.clear();
	}
	
	private void loadWeaponBalance(){
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM weapon_balance");
			rs = pstm.executeQuery();
			fillTable(rs);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "error while creating weapon_balance table", e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	private void fillTable(ResultSet rs) throws SQLException {
		EnchantDamage ed  = null;
		while (rs.next()) {
			int weaponId = rs.getInt("무기_번호");
			String weaponname = rs.getString("무기_이름");
			int e0 = rs.getInt("0");
			int e1 = rs.getInt("1");
			int e2 = rs.getInt("2");
			int e3 = rs.getInt("3");
			int e4 = rs.getInt("4");
			int e5 = rs.getInt("5");
			int e6 = rs.getInt("6");
			int e7 = rs.getInt("7");
			int e8 = rs.getInt("8");
			int e9 = rs.getInt("9");
			int e10 = rs.getInt("10");
			int e11 = rs.getInt("11");
			int e12 = rs.getInt("12");
			int e13 = rs.getInt("13");
			int e14 = rs.getInt("14");
			
			ed = new EnchantDamage(weaponId, weaponname, e0, e1, e2, e3, e4, e5, e6, e7, e8, e9, e10, e11, e12, e13, e14);
			
			_weaponName.put(weaponname, ed);
			_weaponIdList.put(weaponId, ed);
		}
		_log.config("인챈데미지 리스트 " + _weaponName.size() + "건 로드");
		SQLUtil.close(rs);
	}
	
	public EnchantDamage getTemplate(String name) {
		return _weaponName.get(name);
	}

	public EnchantDamage getTemplate(int weaponId) {
		return _weaponIdList.get(weaponId);
	}
}
