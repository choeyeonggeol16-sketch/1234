package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import java.util.logging.Level;
import java.util.logging.Logger;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.CNpcMaker;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.NumberUtil;

/** 몬스터 스폰 정의를 읽고 행별 Maker를 등록한다. */
public final class CNpcMakerDB {
	private static final Logger _log = Logger.getLogger(CNpcMakerDB.class.getName());

	private volatile TIntObjectMap<CNpcMaker> _spawntable = new TIntObjectHashMap<CNpcMaker>(10240);

	private int _highestId;

	private static final class SingletonHolder {
		private static final CNpcMakerDB INSTANCE = new CNpcMakerDB();
	}

	public static CNpcMakerDB getInstance() {
		return SingletonHolder.INSTANCE;
	}

	public static boolean reload() {
		return getInstance().reloadScope(-1);
	}

	private CNpcMakerDB() {
		final List<CNpcMaker> definitions = LoadDefinitions(-1, false);
		if (definitions != null) PublishAndStart(definitions, -1);
	}

	public boolean reload(int mapId) {
		return mapId >= 0 && reloadScope(mapId);
	}

	/** SQL 조회를 완료하기 전에는 기존 스폰에 영향을 주지 않는다. */
	private static List<CNpcMaker> LoadDefinitions(int mapId, boolean reloading) {
		final List<CNpcMaker> definitions = new ArrayList<>();
		final String sql = mapId < 0 ? "SELECT * FROM spawnlist" : "SELECT * FROM spawnlist WHERE mapid = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
		     PreparedStatement pstm = con.prepareStatement(sql)) {
			if (mapId >= 0) pstm.setInt(1, mapId);
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					final int spawnId = rs.getInt("id");
					final int npcId = rs.getInt("npc_templateid");
					final L1Npc template = CObjectDB.GetNPC(npcId);
					if (template == null) {
						if (reloading) throw new IllegalStateException("스폰 원형 없음: " + npcId);
						_log.warning("스폰 원형 없음: " + npcId);
						continue;
					}
					final int amount = rs.getInt("count");
					if (amount <= 0) continue;
					final int count = calcCount(template, amount,
							MapsTable.getInstance().getMonsterAmount(rs.getShort("mapid")));
					if (count <= 0) continue;
					definitions.add(readSpawnDefine(rs, template, spawnId, count, mapId >= 0));
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "몬스터 스폰 정의 로딩 실패", e);
			return null;
		}
		return definitions;
	}

	/** 폐기한 Maker에 속하는 몬스터만 제거한다. 드롭 처리 중인 개체는 그대로 둔다. */
	private boolean RetireScope(int mapId) {
		final TIntObjectMap<CNpcMaker> current = _spawntable;
		for (CNpcMaker maker : current.valueCollection()) {
			if (mapId < 0 || maker.getMapId() == mapId) maker.Retire();
		}
		final L1World world = L1World.getInstance();
		for (L1NpcInstance npc : world.getAllNpc()) {
			if (!BelongsToRetiredScope(npc, current, mapId)) continue;
			if (!npc.isDestroyed() && !npc.isDeathProcessing()) npc.NpcDie();
		}
		// 정산이나 삭제가 아직 끝나지 않았다면 새 세대를 생성하지 않는다.
		for (L1NpcInstance npc : world.getAllNpc()) {
			if (BelongsToRetiredScope(npc, current, mapId)) return false;
		}
		return true;
	}

	private static boolean BelongsToRetiredScope(L1NpcInstance npc, TIntObjectMap<CNpcMaker> current, int mapId) {
		final CNpcMaker maker = npc.getSpawn();
		return maker != null && maker.IsRetired()
				&& (mapId < 0 || maker.getMapId() == mapId)
				&& current.get(maker.getId()) == maker;
	}

	private synchronized boolean reloadScope(int mapId) {
		final List<CNpcMaker> definitions = LoadDefinitions(mapId, true);
		if (definitions == null) return false;
		// 다른 맵에서 이동한 행은 해당 맵의 기존 Maker까지 정리해야 한다.
		// 부분 갱신으로 등록 키만 덮어쓰지 않고 전체 갱신을 요구한다.
		if (mapId >= 0) {
			for (CNpcMaker definition : definitions) {
				final CNpcMaker previous = _spawntable.get(definition.getId());
				if (previous != null && previous.getMapId() != mapId) {
					_log.warning("스폰 정의의 맵이 변경되었습니다. 전체 리로드 필요: " + definition.getId());
					return false;
				}
			}
		}
		if (!RetireScope(mapId)) {
			_log.warning("기존 몬스터 삭제 대기 중: 스폰 리로드 보류, map=" + mapId);
			return false;
		}
		PublishAndStart(definitions, mapId);
		return true;
	}

	/** 월드 재배치 및 삭제 명령에서도 기존 예약을 먼저 무효화한다. */
	public synchronized boolean DespawnAll() {
		return RetireScope(-1);
	}

	private void PublishAndStart(List<CNpcMaker> definitions, int mapId) {
		final TIntObjectMap<CNpcMaker> next = new TIntObjectHashMap<>(Math.max(16, _spawntable.size() + definitions.size()));
		if (mapId >= 0) {
			for (CNpcMaker maker : _spawntable.valueCollection()) {
				if (maker.getMapId() != mapId) next.put(maker.getId(), maker);
			}
		}
		for (CNpcMaker maker : definitions) {
			next.put(maker.getId(), maker);
			if (maker.getId() > _highestId) _highestId = maker.getId();
		}
		_spawntable = next;
		int spawnCount = 0;
		for (CNpcMaker maker : definitions) {
			maker.init();
			spawnCount += maker.getAmount();
		}
		_log.config("몬스터 스폰 정의 로드: " + spawnCount + "마리");
	}

	/** 최초 로딩과 맵별 갱신의 공통 정의 로딩. 기존 방향 및 화면 설정 차이는 유지한다. */
	private static CNpcMaker readSpawnDefine(ResultSet rs, L1Npc template1, int spawnid, int count, boolean mapReload) throws Exception {
		CNpcMaker spawnDat = new CNpcMaker(template1);
		spawnDat.setId(spawnid);
		spawnDat.setAmount(count);
		spawnDat.setGroupId(rs.getInt("group_id"));
		spawnDat.setLocX(rs.getInt("locx"));
		spawnDat.setLocY(rs.getInt("locy"));
		spawnDat.setRandomx(rs.getInt("randomx"));
		spawnDat.setRandomy(rs.getInt("randomy"));
		spawnDat.setLocX1(rs.getInt("locx1"));
		spawnDat.setLocY1(rs.getInt("locy1"));
		spawnDat.setLocX2(rs.getInt("locx2"));
		spawnDat.setLocY2(rs.getInt("locy2"));
		spawnDat.setHeading(mapReload ? 0 : rs.getInt("heading"));
		spawnDat.setMinRespawnDelay(rs.getInt("min_respawn_delay"));
		spawnDat.setMaxRespawnDelay(rs.getInt("max_respawn_delay"));
		spawnDat.setMapId(rs.getShort("mapid"));
		if (!mapReload) spawnDat.setRespawnScreen(rs.getBoolean("respawn_screen"));
		spawnDat.setMovementDistance(rs.getInt("movement_distance"));
		spawnDat.setRest(rs.getBoolean("rest"));
		spawnDat.setName(template1.get_name());

		if (count > 1 && spawnDat.getLocX1() == 0) {
			int range = Math.min(count * 6, 30);
			spawnDat.setLocX1(spawnDat.getLocX() - range);
			spawnDat.setLocY1(spawnDat.getLocY() - range);
			spawnDat.setLocX2(spawnDat.getLocX() + range);
			spawnDat.setLocY2(spawnDat.getLocY() + range);
		}
		return spawnDat;
	}

	public CNpcMaker getTemplate(int id) {
		return _spawntable.get(id);
	}

	public synchronized void delspawntable(int id) {
		final CNpcMaker maker = _spawntable.get(id);
		if (maker == null) return;
		maker.Retire();
		final TIntObjectMap<CNpcMaker> next = new TIntObjectHashMap<>(_spawntable);
		next.remove(id);
		_spawntable = next;
	}

	public synchronized void addNewSpawn(CNpcMaker spawn) {
		_highestId++;
		spawn.setId(_highestId);
		final TIntObjectMap<CNpcMaker> next = new TIntObjectHashMap<>(_spawntable);
		next.put(spawn.getId(), spawn);
		_spawntable = next;
	}

	public static void storeSpawn(L1PcInstance pc, L1Npc npc) {
		int count = 1;
		int randomXY = 12;
		int minRespawnDelay = 60;
		int maxRespawnDelay = 120;
		String note = npc.get_name();

		String sql = "INSERT INTO spawnlist SET location=?, count=?, npc_templateid=?, group_id=?, locx=?, locy=?, randomx=?, randomy=?, heading=?, min_respawn_delay=?, max_respawn_delay=?, mapid=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setString(1, note);
			pstm.setInt(2, count);
			pstm.setInt(3, npc.get_npcId());
			pstm.setInt(4, 0);
			pstm.setInt(5, pc.getX());
			pstm.setInt(6, pc.getY());
			pstm.setInt(7, randomXY);
			pstm.setInt(8, randomXY);
			pstm.setInt(9, pc.getHeading());
			pstm.setInt(10, minRespawnDelay);
			pstm.setInt(11, maxRespawnDelay);
			pstm.setInt(12, pc.getMapId());
			pstm.execute();

		} catch (Exception e) {
			_log.log(Level.SEVERE, "CNpcMakerDB storeSpawn error", e);
		}
	}

	private static int calcCount(L1Npc npc, int count, double rate) {
		if (rate == 0) {
			return 0;
		}
		if (rate == 1 || npc.isAmountFixed()) {
			return count;
		} else {
			return NumberUtil.randomRound((count * rate));
		}
	}
}