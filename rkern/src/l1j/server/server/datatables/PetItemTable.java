
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
import l1j.server.server.templates.L1PetItem;

public class PetItemTable {
	private static final Logger _log = Logger.getLogger(PetItemTable.class.getName());

	private static volatile PetItemTable _instance;

	private final TIntObjectMap<L1PetItem> _petItemIdIndex = new TIntObjectHashMap<>();

	public static PetItemTable getInstance() {
		if (_instance == null) {
			synchronized (PetItemTable.class) {
				if (_instance == null) {
					_instance = new PetItemTable();
				}
			}
		}
		return _instance;
	}

	public static void reload() {
		PetItemTable oldInstance = _instance;
		_instance = new PetItemTable();
		if (oldInstance != null) {
			synchronized (oldInstance._petItemIdIndex) {
				oldInstance._petItemIdIndex.clear();
			}
		}
	}

	private PetItemTable() {
		loadPetItem();
	}

	private void loadPetItem() {
		String sql = "SELECT * FROM petitem";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			synchronized (_petItemIdIndex) {
				_petItemIdIndex.clear();
				while (rs.next()) {
					L1PetItem petItem = new L1PetItem();
					petItem.setItemId(rs.getInt("item_id"));
					petItem.setHitModifier(rs.getInt("hitmodifier"));
					petItem.setDamageModifier(rs.getInt("dmgmodifier"));
					petItem.setAddAc(rs.getInt("ac"));
					petItem.setAddStr(rs.getInt("add_str"));
					petItem.setAddCon(rs.getInt("add_con"));
					petItem.setAddDex(rs.getInt("add_dex"));
					petItem.setAddInt(rs.getInt("add_int"));
					petItem.setAddWis(rs.getInt("add_wis"));
					petItem.setAddHp(rs.getInt("add_hp"));
					petItem.setAddMp(rs.getInt("add_mp"));
					petItem.setAddSp(rs.getInt("add_sp"));
					petItem.setAddMr(rs.getInt("m_def"));
					
					_petItemIdIndex.put(petItem.getItemId(), petItem);
				}
			}
			_log.config("펫 장비 설정 " + _petItemIdIndex.size() + "건 로드 완료");
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "petitem 테이블 로드 중 에러 발생", e);
		}
	}

	public L1PetItem getTemplate(int itemId) {
		synchronized (_petItemIdIndex) {
			return _petItemIdIndex.get(itemId);
		}
	}
}