package l1j.server.server.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1GetBackRestart;
import l1j.server.server.model.world.residence.L1HouseLocation;

public class Getback {

	private static final Logger _log = Logger.getLogger(Getback.class.getName());

	private static final TIntObjectMap<Bucket> _getback = new TIntObjectHashMap<Bucket>(1024);

	private int _areaX1;
	private int _areaY1;
	private int _areaX2;
	private int _areaY2;
	private int _areaMapId;

	private int _getbackX1;
	private int _getbackY1;
	private int _getbackX2;
	private int _getbackY2;
	private int _getbackX3;
	private int _getbackY3;
	private int _getbackMapId;

	private int _getbackTownId;
	private int _getbackTownIdForElf;
	private int _getbackTownIdForDarkelf;

	private Getback() {
	}

	private boolean isSpecifyArea() {
		return (_areaX1 != 0 && _areaY1 != 0 && _areaX2 != 0 && _areaY2 != 0);
	}

	/**
	 * getback 테이블 로드
	 */
	public static void loadGetBack() {
		_getback.clear();
		String sql = "SELECT * FROM getback ORDER BY area_mapid, area_x1 DESC";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				final Getback gb = new Getback();

				gb._areaX1 = rs.getInt("area_x1");
				gb._areaY1 = rs.getInt("area_y1");
				gb._areaX2 = rs.getInt("area_x2");
				gb._areaY2 = rs.getInt("area_y2");
				gb._areaMapId = rs.getInt("area_mapid");

				gb._getbackX1 = rs.getInt("getback_x1");
				gb._getbackY1 = rs.getInt("getback_y1");
				gb._getbackX2 = rs.getInt("getback_x2");
				gb._getbackY2 = rs.getInt("getback_y2");
				gb._getbackX3 = rs.getInt("getback_x3");
				gb._getbackY3 = rs.getInt("getback_y3");
				gb._getbackMapId = rs.getInt("getback_mapid");

				gb._getbackTownId = rs.getInt("getback_townid");
				gb._getbackTownIdForElf = rs.getInt("getback_townid_elf");
				gb._getbackTownIdForDarkelf = rs.getInt("getback_townid_darkelf");

				// 읽기만 하고 미사용하는 컬럼 소비
				rs.getBoolean("scrollescape");

				int key = gb._areaMapId;
				Bucket bucket = _getback.get(key);
				if (bucket == null) {
					bucket = new Bucket();
					_getback.put(key, bucket);
				}
				bucket.add(gb);
			}

			for (Bucket b : _getback.valueCollection()) {
				if (b != null) b.compact();
			}

		} catch (Exception e) {
			_log.log(Level.SEVERE, "could not Get Getback data", e);
		}
	}

	/**
	 * pc의 현재지로부터 귀환 포인트를 취득한다.
	 * @param pc
	 * @param bScroll_Escape(미사용)
	 * @return locx, locy, mapid의 순서에 격납되고 있는 배열
	 */
	public static int[] GetBack_Location(L1PcInstance pc, boolean bScroll_Escape) {
		final int[] loc = new int[3];

		if (pc == null) {
			loc[0] = 33442; loc[1] = 32798; loc[2] = 4;
			return loc;
		}

		if (pc.isInParty()) {
			if (pc.isDead()) {
				pc.getParty().refresh(pc);
			}
		}

		final Bucket bucket = _getback.get(pc.getMapId());
		if (bucket == null) {
			loc[0] = 33442; loc[1] = 32798; loc[2] = 4;
			return loc;
		}

		final Getback gb = bucket.find(pc.getX(), pc.getY());
		if (gb == null) {
			loc[0] = 33442; loc[1] = 32798; loc[2] = 4;
			return loc;
		}

		// 0~2 랜덤 선택
		final int pos = ThreadLocalRandom.current().nextInt(3);
		readGetbackInfoInto(gb, pos, loc);

		// town_id가 지정되고 있는 경우는 거기에 귀환시킨다
		if (pc.isElf() && gb._getbackTownIdForElf > 0) {
			return L1TownLocation.getGetBackLoc(gb._getbackTownIdForElf);
		} else if (pc.isDarkelf() && gb._getbackTownIdForDarkelf > 0) {
			return L1TownLocation.getGetBackLoc(gb._getbackTownIdForDarkelf);
		} else if (gb._getbackTownId > 0) {
			return L1TownLocation.getGetBackLoc(gb._getbackTownId);
		}

		// 안전장치
		if (loc[0] == 0 || loc[1] == 0) {
			loc[0] = 33442; loc[1] = 32798; loc[2] = 4;
		}
		return loc;
	}

	public static int[] GetBack_Restart(L1PcInstance pc) {
		int[] loc = new int[3];
		try {
			int currentMapId = pc.getMapId();

			L1GetBackRestart gbr = GetBackRestartTable.getInstance().getRestartInfo(currentMapId);

			if (gbr != null) {
				loc[0] = gbr.getLocX();
				loc[1] = gbr.getLocY();
				loc[2] = gbr.getMapId();
				return loc;
			}
			// 테이블에 없으면 기본 귀환 위치 계산
			loc = GetBack_Location(pc, true);

			// 혈맹 소속(성/아지트) 체크
			if (pc != null && pc.getClanid() != 0) {
				int castle_id = 0;
				int house_id = 0;
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					castle_id = clan.getCastleId();
					house_id = clan.getHouseId();
				}
				if (castle_id != 0) {
					loc = L1CastleLocation.getCastleLoc(castle_id);
				} else if (house_id != 0) {
					loc = L1HouseLocation.getHouseLoc(house_id);
				}
			}

			return loc;
		} catch (Exception e) {
			loc[0] = 33437;
			loc[1] = 32812;
			loc[2] = 4;
			return loc;
		}
	}

	// =========================================================
	// ultra-fast helpers
	// =========================================================

	private static void readGetbackInfoInto(Getback gb, int pos, int[] out) {
		if (pos == 0) {
			out[0] = gb._getbackX1;
			out[1] = gb._getbackY1;
		} else if (pos == 1) {
			out[0] = gb._getbackX2;
			out[1] = gb._getbackY2;
		} else {
			out[0] = gb._getbackX3;
			out[1] = gb._getbackY3;
		}
		out[2] = gb._getbackMapId;
	}

	private static final class Bucket {
		private Getback defaultRule;
		private Getback[] areas;
		private int areaCount;

		void add(Getback gb) {
			if (gb == null) return;

			if (gb.isSpecifyArea()) {
				ensureAreaCapacity(areaCount + 1);
				areas[areaCount++] = gb;
			} else {
				if (defaultRule == null) defaultRule = gb;
			}
		}

		Getback find(int x, int y) {
			if (areaCount == 0) return defaultRule;

			final Getback[] a = areas;
			for (int i = 0; i < areaCount; i++) {
				final Getback gb = a[i];
				if (gb == null) continue;
				if (gb._areaX1 <= x && x <= gb._areaX2 && gb._areaY1 <= y && y <= gb._areaY2) {
					return gb;
				}
			}
			return defaultRule;
		}

		void compact() {
			if (areas != null && areaCount < areas.length) {
				final Getback[] n = new Getback[areaCount];
				for (int i = 0; i < areaCount; i++) n[i] = areas[i];
				areas = n;
			}
		}

		private void ensureAreaCapacity(int need) {
			if (areas == null) {
				areas = new Getback[Math.max(4, need)];
				return;
			}
			if (need <= areas.length) return;
			int ncap = areas.length << 1;
			if (ncap < need) ncap = need;
			final Getback[] n = new Getback[ncap];
			for (int i = 0; i < areaCount; i++) n[i] = areas[i];
			areas = n;
		}
	}
}