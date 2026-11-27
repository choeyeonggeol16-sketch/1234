package l1j.server.server.model;

import l1j.server.server.shared.GameRandom;


import l1j.server.server.model.gametime.L2DateTime;
import l1j.server.server.model.gametime.L2TimeListener;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.model.world.siege.CastleWar;
import l1j.server.server.model.world.siege.CastleWarBusiness;


public class L1CastleLocation {

	// castle_id
	public static final int KENT_CASTLE_ID  = 1;
	public static final int OT_CASTLE_ID    = 2;
	public static final int WW_CASTLE_ID    = 3;
	public static final int GIRAN_CASTLE_ID = 4;
	public static final int HEINE_CASTLE_ID = 5;
	public static final int DOWA_CASTLE_ID  = 6;
	public static final int ADEN_CASTLE_ID  = 7;
	public static final int DIAD_CASTLE_ID  = 8;

	// Tower coords
	public static final int  KENT_TOWER_X = 33168;
	public static final int  KENT_TOWER_Y = 32779;
	public static final short KENT_TOWER_MAP = 4;

	public static final int  OT_TOWER_X = 32798;
	public static final int  OT_TOWER_Y = 32285;
	public static final short OT_TOWER_MAP = 4;

	public static final int  WW_TOWER_X = 32623;
	public static final int  WW_TOWER_Y = 33379;
	public static final short WW_TOWER_MAP = 4;

	public static final int  GIRAN_TOWER_X = 33631;
	public static final int  GIRAN_TOWER_Y = 32678;
	public static final short GIRAN_TOWER_MAP = 4;

	public static final int  HEINE_TOWER_X = 33524;
	public static final int  HEINE_TOWER_Y = 33396;
	public static final short HEINE_TOWER_MAP = 4;

	public static final int  DOWA_TOWER_X = 32828;
	public static final int  DOWA_TOWER_Y = 32818;
	public static final short DOWA_TOWER_MAP = 66;

	public static final int  ADEN_TOWER_X = 34090;
	public static final int  ADEN_TOWER_Y = 33260;
	public static final short ADEN_TOWER_MAP = 4;

	public static final int  DIAD_TOWER_X = 33033;
	public static final int  DIAD_TOWER_Y = 32895;
	public static final short DIAD_TOWER_MAP = 320;

	// War areas (x1,y1,x2,y2,map)
	public static final int KENT_X1 = 33089, KENT_X2 = 33219, KENT_Y1 = 32717, KENT_Y2 = 32827; public static final short KENT_MAP = 4;
	public static final int OT_X1   = 32750, OT_X2   = 32850, OT_Y1   = 32250, OT_Y2   = 32350; public static final short OT_MAP   = 4;
	public static final int WW_X1   = 32571, WW_X2   = 32721, WW_Y1   = 33350, WW_Y2   = 33460; public static final short WW_MAP   = 4;
	public static final int GIRAN_X1= 33559, GIRAN_X2= 33686, GIRAN_Y1= 32615, GIRAN_Y2= 32755; public static final short GIRAN_MAP= 4;
	public static final int HEINE_X1= 33458, HEINE_X2= 33583, HEINE_Y1= 33315, HEINE_Y2= 33490; public static final short HEINE_MAP= 4;
	public static final int DOWA_X1 = 32755, DOWA_X2 = 32870, DOWA_Y1 = 32790, DOWA_Y2 = 32920; public static final short DOWA_MAP = 66;
	public static final int ADEN_X1 = 34007, ADEN_X2 = 34162, ADEN_Y1 = 33172, ADEN_Y2 = 33332; public static final short ADEN_MAP = 4;
	public static final int DIAD_X1 = 32888, DIAD_X2 = 33070, DIAD_Y1 = 32839, DIAD_Y2 = 32953; public static final short DIAD_MAP = 320;

	// Inner castle maps
	public static final short KENT_INNER_CASTLE_MAP  = 15;
	public static final short WW_INNER_CASTLE_MAP    = 29;
	public static final short GIRAN_INNER_CASTLE_MAP = 52;
	public static final short HEINE_INNER_CASTLE_MAP = 64;
	public static final short ADEN_INNER_CASTLE_MAP  = 300;
	public static final short DIAD_INNER_CASTLE_MAP  = 330;

	// Aden Sub Towers
	public static final int ADEN_SUB_TOWER1_X = 34057, ADEN_SUB_TOWER1_Y = 33291;
	public static final int ADEN_SUB_TOWER2_X = 34123, ADEN_SUB_TOWER2_Y = 33291;
	public static final int ADEN_SUB_TOWER3_X = 34057, ADEN_SUB_TOWER3_Y = 33230;
	public static final int ADEN_SUB_TOWER4_X = 34123, ADEN_SUB_TOWER4_Y = 33230;

	// =========================
	// 배열 캐시 (castleId 1~8)
	// =========================
	private static final L1Location[] TOWERS = new L1Location[9];
	private static final L1MapArea[]  AREAS  = new L1MapArea[9];

	// innerMapId -> castleId 
	private static int castleIdByInnerMapId(short mapId) {
		switch (mapId) {
		case KENT_INNER_CASTLE_MAP:  return KENT_CASTLE_ID;
		case WW_INNER_CASTLE_MAP:    return WW_CASTLE_ID;
		case GIRAN_INNER_CASTLE_MAP: return GIRAN_CASTLE_ID;
		case HEINE_INNER_CASTLE_MAP: return HEINE_CASTLE_ID;
		case ADEN_INNER_CASTLE_MAP:  return ADEN_CASTLE_ID;
		case DIAD_INNER_CASTLE_MAP:  return DIAD_CASTLE_ID;
		default: return 0;
		}
	}

	// sub tower [1..4]
	private static final L1Location[] SUB_TOWERS = new L1Location[5];

	// shop tax rate [1..8]
	private static volatile int[] CASTLE_TAX_RATE = new int[9];

	private static final L2TimeListener CASTLE_TAX_CLOCK = new CResidenceTaxListener();

	static {
		// towers
		TOWERS[KENT_CASTLE_ID]  = new L1Location(KENT_TOWER_X,  KENT_TOWER_Y,  KENT_TOWER_MAP);
		TOWERS[OT_CASTLE_ID]    = new L1Location(OT_TOWER_X,    OT_TOWER_Y,    OT_TOWER_MAP);
		TOWERS[WW_CASTLE_ID]    = new L1Location(WW_TOWER_X,    WW_TOWER_Y,    WW_TOWER_MAP);
		TOWERS[GIRAN_CASTLE_ID] = new L1Location(GIRAN_TOWER_X, GIRAN_TOWER_Y, GIRAN_TOWER_MAP);
		TOWERS[HEINE_CASTLE_ID] = new L1Location(HEINE_TOWER_X, HEINE_TOWER_Y, HEINE_TOWER_MAP);
		TOWERS[DOWA_CASTLE_ID]  = new L1Location(DOWA_TOWER_X,  DOWA_TOWER_Y,  DOWA_TOWER_MAP);
		TOWERS[ADEN_CASTLE_ID]  = new L1Location(ADEN_TOWER_X,  ADEN_TOWER_Y,  ADEN_TOWER_MAP);
		TOWERS[DIAD_CASTLE_ID]  = new L1Location(DIAD_TOWER_X,  DIAD_TOWER_Y,  DIAD_TOWER_MAP);

		// areas
		AREAS[KENT_CASTLE_ID]  = new L1MapArea(KENT_X1,  KENT_Y1,  KENT_X2,  KENT_Y2,  KENT_MAP);
		AREAS[OT_CASTLE_ID]    = new L1MapArea(OT_X1,    OT_Y1,    OT_X2,    OT_Y2,    OT_MAP);
		AREAS[WW_CASTLE_ID]    = new L1MapArea(WW_X1,    WW_Y1,    WW_X2,    WW_Y2,    WW_MAP);
		AREAS[GIRAN_CASTLE_ID] = new L1MapArea(GIRAN_X1, GIRAN_Y1, GIRAN_X2, GIRAN_Y2, GIRAN_MAP);
		AREAS[HEINE_CASTLE_ID] = new L1MapArea(HEINE_X1, HEINE_Y1, HEINE_X2, HEINE_Y2, HEINE_MAP);
		AREAS[DOWA_CASTLE_ID]  = new L1MapArea(DOWA_X1,  DOWA_Y1,  DOWA_X2,  DOWA_Y2,  DOWA_MAP);
		AREAS[ADEN_CASTLE_ID]  = new L1MapArea(ADEN_X1,  ADEN_Y1,  ADEN_X2,  ADEN_Y2,  ADEN_MAP);
		AREAS[DIAD_CASTLE_ID]  = new L1MapArea(DIAD_X1,  DIAD_Y1,  DIAD_X2,  DIAD_Y2,  DIAD_MAP);

		// sub towers
		SUB_TOWERS[1] = new L1Location(ADEN_SUB_TOWER1_X, ADEN_SUB_TOWER1_Y, ADEN_TOWER_MAP);
		SUB_TOWERS[2] = new L1Location(ADEN_SUB_TOWER2_X, ADEN_SUB_TOWER2_Y, ADEN_TOWER_MAP);
		SUB_TOWERS[3] = new L1Location(ADEN_SUB_TOWER3_X, ADEN_SUB_TOWER3_Y, ADEN_TOWER_MAP);
		SUB_TOWERS[4] = new L1Location(ADEN_SUB_TOWER4_X, ADEN_SUB_TOWER4_Y, ADEN_TOWER_MAP);
	}

	private L1CastleLocation() {}

	// =========================
	// ID helpers
	// =========================
	public static int getCastleId(L1Location loc) {
		if (loc == null) return 0;
		return getCastleId(loc.getX(), loc.getY(), (short) loc.getMapId());
	}

	public static int getCastleId(int locx, int locy, short mapid) {
		for (int i = 1; i <= 8; i++) {
			L1Location t = TOWERS[i];
			if (t != null && t.getMapId() == mapid && t.getX() == locx && t.getY() == locy) {
				return i;
			}
		}
		return 0;
	}

	public static int getCastleByMapId(int castleId) {
		L1MapArea a = (castleId >= 1 && castleId <= 8) ? AREAS[castleId] : null;
		return (a != null) ? a.getMapId() : 0;
	}

	public static int getCastleIdByArea(L1Location loc) {
		if (loc == null) return 0;
		return getCastleIdByArea(loc.getX(), loc.getY(), (short) loc.getMapId());
	}

	public static int getCastleIdByArea(L1Character cha) {
		if (cha == null) return 0;
		L1Location loc = cha.getLocation();
		return (loc != null) ? getCastleIdByArea(loc) : 0;
	}

	public static int getCastleIdByArea(int x, int y, short mapId) {
		// 1) 외부 전쟁영역 체크
		// mapId가 다르면 바로 컷(contains(int,int,short) 사용)
		if (AREAS[KENT_CASTLE_ID].contains(x, y, mapId))  return KENT_CASTLE_ID;
		if (AREAS[OT_CASTLE_ID].contains(x, y, mapId))    return OT_CASTLE_ID;
		if (AREAS[WW_CASTLE_ID].contains(x, y, mapId))    return WW_CASTLE_ID;
		if (AREAS[GIRAN_CASTLE_ID].contains(x, y, mapId)) return GIRAN_CASTLE_ID;
		if (AREAS[HEINE_CASTLE_ID].contains(x, y, mapId)) return HEINE_CASTLE_ID;
		if (AREAS[DOWA_CASTLE_ID].contains(x, y, mapId))  return DOWA_CASTLE_ID;
		if (AREAS[ADEN_CASTLE_ID].contains(x, y, mapId))  return ADEN_CASTLE_ID;
		if (AREAS[DIAD_CASTLE_ID].contains(x, y, mapId))  return DIAD_CASTLE_ID;

		// 2) 내부성 맵 체크
		return castleIdByInnerMapId(mapId);
	}

	public static boolean checkInWarArea(int castleId, L1Location loc) {
		if (loc == null) return false;
		return castleId == getCastleIdByArea(loc);
	}

	public static boolean checkInWarArea(int castleId, L1Character cha) {
		if (cha == null) return false;
		return checkInWarArea(castleId, cha.getLocation());
	}

	public static boolean checkInAllWarArea(L1Location loc) {
		return getCastleIdByArea(loc) != 0;
	}

	public static boolean checkInAllWarArea(int x, int y, short mapId) {
		return getCastleIdByArea(x, y, mapId) != 0;
	}

	// =========================
	// Locations
	// =========================
	public static int[] getTowerLoc(int castleId) {
		int[] result = new int[3];
		if (castleId < 1 || castleId > 8) return result;
		L1Location loc = TOWERS[castleId];
		if (loc == null) return result;
		result[0] = loc.getX();
		result[1] = loc.getY();
		result[2] = loc.getMapId();
		return result;
	}

	public static int[] getWarArea(int castleId) {
		int[] loc = new int[5];
		if (castleId < 1 || castleId > 8) return loc;
		L1MapArea a = AREAS[castleId];
		if (a == null) return loc;
		loc[0] = a.getLeft();
		loc[1] = a.getRight();
		loc[2] = a.getTop();
		loc[3] = a.getBottom();
		loc[4] = a.getMapId();
		return loc;
	}

	public static int[] getCastleLoc(int castleId) {
		int[] loc = new int[3];
		switch (castleId) {
		case KENT_CASTLE_ID:  loc[0] = 32731; loc[1] = 32810; loc[2] = 15;  break;
		case OT_CASTLE_ID:    loc[0] = 32800; loc[1] = 32277; loc[2] = 4;   break;
		case WW_CASTLE_ID:    loc[0] = 32730; loc[1] = 32814; loc[2] = 29;  break;
		case GIRAN_CASTLE_ID: loc[0] = 32724; loc[1] = 32827; loc[2] = 52;  break;
		case HEINE_CASTLE_ID: loc[0] = 32568; loc[1] = 32855; loc[2] = 64;  break;
		case DOWA_CASTLE_ID:  loc[0] = 32853; loc[1] = 32810; loc[2] = 66;  break;
		case ADEN_CASTLE_ID:  loc[0] = 32892; loc[1] = 32572; loc[2] = 300; break;
		case DIAD_CASTLE_ID:  loc[0] = 32733; loc[1] = 32985; loc[2] = 330; break;
		default: break;
		}
		return loc;
	}

	public static int[] getGetBackLoc(int castleId) {
		switch (castleId) {
		case KENT_CASTLE_ID:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_KENT);
		case OT_CASTLE_ID:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_ORCISH_FOREST);
		case WW_CASTLE_ID:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_WINDAWOOD);
		case GIRAN_CASTLE_ID:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_GIRAN);
		case HEINE_CASTLE_ID:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_HEINE);
		case DOWA_CASTLE_ID:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_WERLDAN);
		case ADEN_CASTLE_ID:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_ADEN);
		case DIAD_CASTLE_ID: {
			int rnd = GameRandom.nextInt(3);
			int[] loc = new int[3];
			if (rnd == 0) {
				loc[0] = 32792; loc[1] = 32807; loc[2] = 310;
			} else if (rnd == 1) {
				loc[0] = 32816; loc[1] = 32820; loc[2] = 310;
			} else {
				loc[0] = 32823; loc[1] = 32797; loc[2] = 310;
			}
			return loc;
		}
		default:
			return L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_SILVER_KNIGHT_TOWN);
		}
	}

	// =========================
	// Npc -> castle
	// =========================
	public static int getCastleIdByNpcid(int npcid) {
		int town_id = L1TownLocation.getTownIdByNpcid(npcid);
		switch (town_id) {
		case L1TownLocation.TOWNID_KENT:
		case L1TownLocation.TOWNID_GLUDIO:
			return KENT_CASTLE_ID;

		case L1TownLocation.TOWNID_ORCISH_FOREST:
			return OT_CASTLE_ID;

		case L1TownLocation.TOWNID_SILVER_KNIGHT_TOWN:
		case L1TownLocation.TOWNID_WINDAWOOD:
			return WW_CASTLE_ID;

		case L1TownLocation.TOWNID_TALKING_ISLAND:
		case L1TownLocation.TOWNID_GIRAN:
			return GIRAN_CASTLE_ID;

		case L1TownLocation.TOWNID_HEINE:
			return HEINE_CASTLE_ID;

		case L1TownLocation.TOWNID_WERLDAN:
		case L1TownLocation.TOWNID_OREN:
			return DOWA_CASTLE_ID;

		case L1TownLocation.TOWNID_ADEN:
			return ADEN_CASTLE_ID;

		case L1TownLocation.TOWNID_OUM_DUNGEON:
			return DIAD_CASTLE_ID;

		default:
			return 0;
		}
	}

	public static int getCastleTaxRateByNpcId(int npcId) {
		int castleId = getCastleIdByNpcid(npcId);
		return (castleId != 0) ? CASTLE_TAX_RATE[castleId] : 0;
	}

    public static void setCastleTaxRate() {
        refreshCastleTaxRates();
        CL2Time.getInstance().addGameListener(CASTLE_TAX_CLOCK, java.util.Calendar.DAY_OF_MONTH);
    }

    private static void refreshCastleTaxRates() {
        int[] rates = new int[9];
        for (int i = 1; i < rates.length; i++) {
            CastleWar war = CastleWarBusiness.getInstance().get(i);
            rates[i] = war == null ? 0 : war.getTaxRate();
        }
        CASTLE_TAX_RATE = rates;
    }

    private static final class CResidenceTaxListener implements L2TimeListener {
        @Override public void onDayChanged(L2DateTime time) {
            refreshCastleTaxRates();
        }
    }

	public static int[] getSubTowerLoc(int no) {
		int[] result = new int[3];
		if (no < 1 || no > 4) return result;
		L1Location loc = SUB_TOWERS[no];
		if (loc == null) return result;
		result[0] = loc.getX();
		result[1] = loc.getY();
		result[2] = loc.getMapId();
		return result;
	}
}
