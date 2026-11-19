package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1PetType;
import l1j.server.server.utils.IntRange;

public class PetTypeTable {
	private static final Logger _log = Logger.getLogger(PetTypeTable.class.getName());
	
	private static PetTypeTable _instance;
	
	private final TIntObjectMap<L1PetType> _types = new TIntObjectHashMap<>();
	private final Set<String> _defaultNames = new HashSet<>();

	public static void load() {
		_instance = new PetTypeTable();
	}

	public static PetTypeTable getInstance() {
		return _instance;
	}

	private PetTypeTable() {
		loadTypes();
	}

	private void loadTypes() {
		String sql = "SELECT * FROM pettypes";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			IntRange hpUpRange = null;
			IntRange mpUpRange = null;
			while (rs.next()) {
				int baseNpcId = rs.getInt("BaseNpcId");
				String name = rs.getString("Name");
				int itemIdForTaming = rs.getInt("ItemIdForTaming");
				int hpUpMin = rs.getInt("HpUpMin");
				int hpUpMax = rs.getInt("HpUpMax");
				int mpUpMin = rs.getInt("MpUpMin");
				int mpUpMax = rs.getInt("MpUpMax");
				int npcIdForEvolving = rs.getInt("NpcIdForEvolving");
				
				int[] msgIds = new int[5];
				for (int i = 0; i < 5; i++) {
					msgIds[i] = rs.getInt("MessageId" + (i + 1));
				}
				int defyMsgId = rs.getInt("DefyMessageId");
				
				hpUpRange = new IntRange(hpUpMin, hpUpMax);
				mpUpRange = new IntRange(mpUpMin, mpUpMax);
				
				_types.put(baseNpcId, new L1PetType(baseNpcId, name,
						itemIdForTaming, hpUpRange, mpUpRange,
						npcIdForEvolving, msgIds, defyMsgId));
				
				if (name != null) {
					_defaultNames.add(name.toLowerCase());
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "loadTypes error", e);
		}
	}

	public L1PetType get(int baseNpcId) {
		return _types.get(baseNpcId);
	}

	public boolean isNameDefault(String name) {
		if (name == null) {
			return false;
		}
		return _defaultNames.contains(name.toLowerCase());
	}
}