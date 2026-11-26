/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.npc.spawn.group;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;

public class MobGroupTable {
	private static final Logger _log = Logger.getLogger(MobGroupTable.class.getName());

	private static volatile MobGroupTable _instance;

	private final TIntObjectMap<L1MobGroup> _mobGroupIndex = new TIntObjectHashMap<>();

	public static MobGroupTable getInstance() {
		if (_instance == null) {
			synchronized (MobGroupTable.class) {
				if (_instance == null) {
					_instance = new MobGroupTable();
				}
			}
		}
		return _instance;
	}

	public static void reload() {
		MobGroupTable oldInstance = _instance;
		_instance = new MobGroupTable();
		if (oldInstance != null) {
			synchronized (oldInstance._mobGroupIndex) {
				oldInstance._mobGroupIndex.clear();
			}
		}
	}

	private MobGroupTable() {
		loadMobGroup();
	}

	private void loadMobGroup() {
		String sql = "SELECT * FROM mobgroup";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			synchronized (_mobGroupIndex) {
				_mobGroupIndex.clear();
				while (rs.next()) {
					int mobGroupId = rs.getInt("id");
					L1MobGroup mobGroup = new L1MobGroup();
					mobGroup.setId(mobGroupId);
					mobGroup.setRemoveGroupIfLeaderDie(rs.getBoolean("remove_group_if_leader_die"));
					mobGroup.setLeaderId(rs.getInt("leader_id"));
					mobGroup.setMinion1Id(rs.getInt("minion1_id"));
					mobGroup.setMinion1Count(rs.getInt("minion1_count"));
					mobGroup.setMinion2Id(rs.getInt("minion2_id"));
					mobGroup.setMinion2Count(rs.getInt("minion2_count"));
					mobGroup.setMinion3Id(rs.getInt("minion3_id"));
					mobGroup.setMinion3Count(rs.getInt("minion3_count"));
					mobGroup.setMinion4Id(rs.getInt("minion4_id"));
					mobGroup.setMinion4Count(rs.getInt("minion4_count"));
					mobGroup.setMinion5Id(rs.getInt("minion5_id"));
					mobGroup.setMinion5Count(rs.getInt("minion5_count"));
					mobGroup.setMinion6Id(rs.getInt("minion6_id"));
					mobGroup.setMinion6Count(rs.getInt("minion6_count"));
					mobGroup.setMinion7Id(rs.getInt("minion7_id"));
					mobGroup.setMinion7Count(rs.getInt("minion7_count"));
					mobGroup.setMinion8Id(rs.getInt("minion8_id"));
					mobGroup.setMinion8Count(rs.getInt("minion8_count"));
					
					_mobGroupIndex.put(mobGroupId, mobGroup);
				}
			}
			_log.config("MOB 그룹 리스트 " + _mobGroupIndex.size() + "건 로드 완료");
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "error while creating mobgroup table", e);
		}
	}

	public L1MobGroup getTemplate(int mobGroupId) {
		synchronized (_mobGroupIndex) {
			return _mobGroupIndex.get(mobGroupId);
		}
	}
}