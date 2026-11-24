package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Npc;

public class NpcTable {
	private static final Logger _log = Logger.getLogger(NpcTable.class.getName());

	private final boolean _initialized;

	private final TIntObjectMap<L1Npc> _npcs = new TIntObjectHashMap<L1Npc>(4096);

	private final Map<String, CObjectDB.NpcConstructor> _constructorCache = new ConcurrentHashMap<>();
	private static final Map<String, Integer> _familyTypes = NpcTable.buildFamily();

	public static NpcTable getInstance() {
		return CObjectDB.LoadNPC();
	}

	public static void reload() {
		CObjectDB.ReloadNPC();
	}

	public boolean isInitialized() {
		return _initialized;
	}

	NpcTable() {
		loadNpcData();
		_initialized = true;
	}

	CObjectDB.NpcConstructor resolveConstructor(String implName) {
		if (implName == null) return null;
		CObjectDB.NpcConstructor constructor = _constructorCache.get(implName);
		if (constructor != null) return constructor;
		constructor = CObjectDB.ResolveNPCConstructor(implName);
		if (constructor == null) return null;
		CObjectDB.NpcConstructor previous = _constructorCache.putIfAbsent(implName, constructor);
		return previous != null ? previous : constructor;
	}

	private void registerConstructorCache(String implName) {
		if (implName != null && !implName.isEmpty()) {
			resolveConstructor(implName);
		}
	}

	private void loadNpcBorn() {
		String sql = "SELECT * FROM npc_born";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int npcid = rs.getInt("npcid");
				L1Npc npc = getTemplate(npcid);
				if (npc == null) {
					_log.warning(String.format("[npc_born load fail] 엔피씨 아이디를 찾을 수 없습니다: %d", npcid));
					continue;
				}
				npc.createBorn(rs);
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "NpcTable loadNpcBorn error", e);
		}
	}

	private void loadNpcData() {
		String sql = "SELECT * FROM npc";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

            ResultSetMetaData columns = rs.getMetaData();
            int classIdColumn = 0;
            for (int i = 1; i <= columns.getColumnCount(); i++) {
                if ("class_id".equalsIgnoreCase(columns.getColumnLabel(i))) {
                    classIdColumn = i;
                    break;
                }
            }
            if (classIdColumn == 0) {
                _log.warning("[NPC class_id] npc.class_id column missing; apply APPLY_Monsterbook_ClassIds.sql");
            }
			while (rs.next()) {
				L1Npc npc = new L1Npc();
				int npcId = rs.getInt("npcid");
				npc.set_npcId(npcId);
                npc.set_class_id(classIdColumn == 0 ? 0 : rs.getInt(classIdColumn));
 
				npc.set_name(rs.getString("name"));
				npc.set_nameid(rs.getString("nameid"));

				String impl = rs.getString("impl");
				npc.setImpl(impl);

				npc.set_gfxid(rs.getInt("gfxid"));
				npc.set_level(rs.getInt("lvl"));
				npc.set_hp(rs.getInt("hp"));
				npc.set_mp(rs.getInt("mp"));
				npc.set_ac(rs.getInt("ac"));
				npc.set_str(rs.getByte("str"));
				npc.set_con(rs.getByte("con"));
				npc.set_dex(rs.getByte("dex"));
				npc.set_wis(rs.getByte("wis"));
				npc.set_int(rs.getByte("intel"));
				npc.set_mr(rs.getInt("mr"));
				npc.set_exp(rs.getInt("exp"));
				npc.set_lawful(rs.getInt("lawful"));
				npc.set_size(rs.getString("size"));
				npc.set_weakAttr(rs.getInt("weakAttr"));
				npc.set_ranged(rs.getInt("ranged"));
				npc.setTamable(rs.getBoolean("tamable"));
				npc.setUseAction(rs.getString("use_action"));
				npc.set_undead(rs.getInt("undead"));
				npc.set_poisonatk(rs.getInt("poison_atk"));
				npc.set_paralysisatk(rs.getInt("paralysis_atk"));
				npc.set_agro(rs.getBoolean("agro"));
				npc.set_agrososc(rs.getBoolean("agrososc"));
				npc.set_agrocoi(rs.getBoolean("agrocoi"));

				Integer family = _familyTypes.get(rs.getString("family"));
				npc.set_family(family == null ? 0 : family.intValue());

				int agrofamily = rs.getInt("agrofamily");
				if (npc.get_family() == 0 && agrofamily == 1) {
					npc.set_agrofamily(0);
				} else {
					npc.set_agrofamily(agrofamily);
				}

				npc.set_agrogfxid1(rs.getInt("agrogfxid1"));
				npc.set_agrogfxid2(rs.getInt("agrogfxid2"));
				npc.set_picupitem(rs.getBoolean("picupitem"));
				npc.set_digestitem(rs.getInt("digestitem"));
				npc.set_hprinterval(rs.getInt("hprinterval"));
				npc.set_hpr(rs.getInt("hpr"));
				npc.set_mprinterval(rs.getInt("mprinterval"));
				npc.set_mpr(rs.getInt("mpr"));
				npc.set_teleport(rs.getBoolean("teleport"));
				npc.set_randomlevel(rs.getInt("randomlevel"));
				npc.set_randomhp(rs.getInt("randomhp"));
				npc.set_randommp(rs.getInt("randommp"));
				npc.set_randomac(rs.getInt("randomac"));
				npc.set_randomexp(rs.getInt("randomexp"));
				npc.set_randomlawful(rs.getInt("randomlawful"));
				npc.set_damagereduction(rs.getInt("damage_reduction"));
				npc.set_hard(rs.getBoolean("hard"));
				npc.set_doppel(rs.getBoolean("doppel"));
				npc.set_IsTU(rs.getBoolean("IsTU"));
				npc.set_IsErase(rs.getBoolean("IsErase"));
				npc.setBowActId(rs.getInt("bowActId"));
				npc.setKarma(rs.getInt("karma"));
				npc.setTransformId(rs.getInt("transform_id"));
				npc.setTransformGfxId(rs.getInt("transform_gfxid"));
				npc.setLightSize(rs.getInt("light_size"));
				npc.setAmountFixed(rs.getBoolean("amount_fixed"));
				npc.setChangeHead(rs.getBoolean("change_head"));
				npc.setDoor(rs.getInt("spawnlist_door"));
				npc.setCountId(rs.getInt("count_map"));
				npc.setCantResurrect(rs.getBoolean("cant_resurrect"));
				npc.setShapeChange(rs.getBoolean("shape_change"));

				registerConstructorCache(impl);

				_npcs.put(npcId, npc);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "NpcTable loadNpcData error", e);
		} finally {
			loadNpcBorn();
		}
	}

	public L1Npc getTemplate(int id) {
		return _npcs.get(id);
	}

	public L1NpcInstance newNpcInstance(int id) {
		L1Npc npcTemp = getTemplate(id);
		if (npcTemp == null) {
			return null;
		}
		return newNpcInstance(npcTemp);
	}

	public L1NpcInstance newNpcInstance(L1Npc npcTemp) {
		return CObjectDB.CreateNPC(this, npcTemp);
	}

	public static Map<String, Integer> buildFamily() {
		Map<String, Integer> result = new HashMap<String, Integer>(256);
		String sql = "SELECT DISTINCT(family) AS family FROM npc WHERE NOT trim(family) = ''";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			int id = 1;
			while (rs.next()) {
				String family = rs.getString("family");
				result.put(family, id++);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "NpcTable buildFamily error", e);
		}
		return result;
	}

	public int findNpcIdByName(String name) {
		int nl = name.length();
		for (L1Npc npc : _npcs.valueCollection()) {
			String s = npc.get_name();
			if (s.length() != nl)
				continue;

			if (s.equals(name))
				return npc.get_npcId();
		}
		return 0;
	}

	public int findNpcIdByNameWithoutSpace(String name) {
		for (L1Npc npc : _npcs.valueCollection()) {
			if (npc.get_name().replace(" ", "").equals(name)) {
				return npc.get_npcId();
			}
		}
		return 0;
	}
}