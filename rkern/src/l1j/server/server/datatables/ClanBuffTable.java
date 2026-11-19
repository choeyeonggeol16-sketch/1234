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
import l1j.server.server.model.L1Clan;
import l1j.server.server.utils.CommonUtil;

public final class ClanBuffTable {
	public static class ClanBuff {
		public int id = 0;
		public int buffnumber = 0;
		public String mapname = null;
		public int teleportX = 0;
		public int teleportY = 0;
		public int teleportM = 0;
		public String buffmaplist = null;
	}

	private static ClanBuffTable _instance;
	private static final Logger _log = Logger.getLogger(ClanBuffTable.class.getName());

	private final TIntObjectMap<ClanBuff> _ClanBuff = new TIntObjectHashMap<ClanBuff>(32);

	public static ClanBuffTable getInstance() {
		if (_instance == null) {
			_instance = new ClanBuffTable();
		}
		return _instance;
	}

	private ClanBuffTable() {
		loadMapsFromDatabase();
	}

	public static void reload() {
		ClanBuffTable oldInstance = _instance;
		_instance = new ClanBuffTable();
		if (oldInstance != null) {
			oldInstance._ClanBuff.clear();
		}
	}

	/**
	 * MAP 정보를 받아와서 트로브 맵에 정보 저장한다.
	 */
	private void loadMapsFromDatabase() {
		String sql = "SELECT * FROM clan_bless_buff";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				ClanBuff data = new ClanBuff();
				data.id = rs.getInt("number");
				data.buffnumber = rs.getInt("buff_id");
				data.mapname = rs.getString("map_name");
				data.teleportX = rs.getInt("teleport_x");
				data.teleportY = rs.getInt("teleport_y");
				data.teleportM = rs.getInt("teleport_map_id");
				data.buffmaplist = rs.getString("buff_map_list");
				
				_ClanBuff.put(data.id, data);
			}
			_log.config("ClanBuff 설정 " + _ClanBuff.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanBuffTable load error", e);
		}
	}

	public int getBuff(int number) {
		ClanBuff buff = _ClanBuff.get(number);
		if (buff == null)
			return 0;
		return buff.buffnumber;
	}

	public static ClanBuff getBuffList(int number) {
		ClanBuff[] values = _instance._ClanBuff.values(new ClanBuff[_instance._ClanBuff.size()]);
		for (int i = 0; i < values.length; i++) {
			ClanBuff buff = values[i];
			if (buff != null && buff.buffnumber == number) {
				return buff;
			}
		}
		return null;
	}

	public static boolean isClanBuffMap(int number, int mapid) {
		ClanBuff buff = getBuffList(number);
		if (buff == null || buff.buffmaplist == null) {
			return false;
		}

		String[] mapsList = buff.buffmaplist.split(",");
		for (int i = 0; i < mapsList.length; i++) {
			try {
				if (Integer.parseInt(mapsList[i].trim()) == mapid) {
					return true;
				}
			} catch (NumberFormatException ignored) {}
		}
		return false;
	}

	public static int getRandomBuff(L1Clan clan) {
		ClanBuff[] values = _instance._ClanBuff.values(new ClanBuff[_instance._ClanBuff.size()]);
		if (values == null || values.length == 0) {
			return 0;
		}

		// 최대 50번 시도하여 중복되지 않는 버프 추출
		for (int attempt = 0; attempt < 50; attempt++) {
			ClanBuff temp = values[CommonUtil.random(0, values.length - 1)];
			if (temp != null) {
				int bNum = temp.buffnumber;
				if (bNum != clan.getBuffFirst() && bNum != clan.getBuffSecond() && bNum != clan.getBuffThird()) {
					return bNum;
				}
			}
		}
		// 만약 조건을 만족하는 버프를 못 찾았다면 첫 번째 유효한 버프 반환
		return values[0].buffnumber;
	}

	public int getBuffSize() {
		return _ClanBuff.size();
	}
}