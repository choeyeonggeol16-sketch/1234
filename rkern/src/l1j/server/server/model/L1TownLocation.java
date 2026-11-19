
package l1j.server.server.model;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.datatables.TownTable;
import l1j.server.server.templates.L1Town;
import l1j.server.server.types.Point;

public class L1TownLocation {
	// town_id
	public static final int TOWNID_TALKING_ISLAND = 1;
	public static final int TOWNID_SILVER_KNIGHT_TOWN = 2;
	public static final int TOWNID_GLUDIO = 3;
	public static final int TOWNID_ORCISH_FOREST = 4;
	public static final int TOWNID_WINDAWOOD = 5;
	public static final int TOWNID_KENT = 6;
	public static final int TOWNID_GIRAN = 7;
	public static final int TOWNID_HEINE = 8;
	public static final int TOWNID_WERLDAN = 9;
	public static final int TOWNID_OREN = 10;
	public static final int TOWNID_ELVEN_FOREST = 11;
	public static final int TOWNID_ADEN = 12;
	public static final int TOWNID_SILENT_CAVERN = 13;
	public static final int TOWNID_OUM_DUNGEON = 14;
	public static final int TOWNID_RESISTANCE = 15;
	public static final int TOWNID_PIRATE_ISLAND = 16;
	public static final int TOWNID_RECLUSE_VILLAGE = 17;
	public static final int TOWNID_HIDDEN_VALLEY = 18;
	public static final int TOWNID_claudia = 19;

	private static final short GETBACK_MAP_TALKING_ISLAND = 0;
	private static final Point[] GETBACK_LOC_TALKING_ISLAND = {
			new Point(32600, 32942), new Point(32574, 32944), new Point(32580, 32923),
			new Point(32557, 32975), new Point(32597, 32914), new Point(32580, 32974),
	};

	private static final short GETBACK_MAP_SILVER_KNIGHT_TOWN = 4;
	private static final Point[] GETBACK_LOC_SILVER_KNIGHT_TOWN = {
			new Point(33071, 33402), new Point(33084, 33392), new Point(33085, 33402),
			new Point(33097, 33366), new Point(33110, 33365), new Point(33072, 33392),
	};

	private static final short GETBACK_MAP_GLUDIO = 4;
	private static final Point[] GETBACK_LOC_GLUDIO = {
			new Point(32601, 32757), new Point(32625, 32809), new Point(32609, 32728),
			new Point(32612, 32781), new Point(32605, 32761), new Point(32614, 32739),
			new Point(32611, 32795),
	};

	private static final short GETBACK_MAP_ORCISH_FOREST = 4;
	private static final Point[] GETBACK_LOC_ORCISH_FOREST = {
			new Point(32750, 32435), new Point(32745, 32447), new Point(32738, 32452),
			new Point(32741, 32436), new Point(32749, 32446),
	};

	private static final short GETBACK_MAP_WINDAWOOD = 4;
	private static final Point[] GETBACK_LOC_WINDAWOOD = {
			new Point(32617, 33176), new Point(32621, 33188), new Point(32630, 33179),
			new Point(32623, 33193), new Point(32638, 33203), new Point(32621, 33179),
	};

	private static final short GETBACK_MAP_KENT = 4;
	private static final Point[] GETBACK_LOC_KENT = {
			new Point(33048, 32750), new Point(33059, 32768), new Point(33047, 32761),
			new Point(33059, 32759), new Point(33051, 32775), new Point(33048, 32778),
			new Point(33064, 32773), new Point(33057, 32748),
	};

	private static final short GETBACK_MAP_GIRAN = 4;
	private static final Point[] GETBACK_LOC_GIRAN = {
			new Point(33435, 32803), new Point(33439, 32817), new Point(33440, 32809),
			new Point(33419, 32810), new Point(33426, 32823), new Point(33418, 32818),
			new Point(33432, 32824),
	};

	// 클라우디아
	private static final short TOWNID_MAP_claudia = 7783;
	private static final Point[] GETBACK_LOC_claudia = {
			new Point(32640, 32872), new Point(32638, 32869), new Point(32639, 32857),
			new Point(32647, 32865), new Point(32648, 32861), new Point(32631, 32862),
			new Point(32635, 32865),
	};

	private static final short GETBACK_MAP_HEINE = 4;
	private static final Point[] GETBACK_LOC_HEINE = {
			new Point(33593, 33242), new Point(33593, 33248), new Point(33604, 33236),
			new Point(33599, 33236), new Point(33610, 33247), new Point(33610, 33241),
			new Point(33599, 33252), new Point(33605, 33252),
	};

	private static final short GETBACK_MAP_WERLDAN = 4;
	private static final Point[] GETBACK_LOC_WERLDAN = {
			new Point(33702, 32492), new Point(33747, 32508), new Point(33696, 32498),
			new Point(33723, 32512), new Point(33710, 32521), new Point(33724, 32488),
			new Point(33693, 32513),
	};

	private static final short GETBACK_MAP_OREN = 4;
	private static final Point[] GETBACK_LOC_OREN = {
			new Point(34086, 32280), new Point(34037, 32230), new Point(34022, 32254),
			new Point(34021, 32269), new Point(34044, 32290), new Point(34049, 32316),
			new Point(34081, 32249), new Point(34074, 32313), new Point(34064, 32230),
	};

	private static final short GETBACK_MAP_ELVEN_FOREST = 4;
	private static final Point[] GETBACK_LOC_ELVEN_FOREST = {
			new Point(33065, 32358), new Point(33052, 32313), new Point(33030, 32342),
			new Point(33068, 32320), new Point(33071, 32314), new Point(33030, 32370),
			new Point(33076, 32324), new Point(33068, 32336),
	};

	private static final short GETBACK_MAP_ADEN = 4;
	private static final Point[] GETBACK_LOC_ADEN = {
			new Point(33915, 33114), new Point(34061, 33115), new Point(34090, 33168),
			new Point(34011, 33136), new Point(34093, 33117), new Point(33959, 33156),
			new Point(33992, 33120), new Point(34047, 33156),
	};

	private static final short GETBACK_MAP_SILENT_CAVERN = 304;
	private static final Point[] GETBACK_LOC_SILENT_CAVERN = {
			new Point(32856, 32898), new Point(32860, 32916), new Point(32868, 32893),
			new Point(32875, 32903), new Point(32855, 32898),
	};

	private static final short GETBACK_MAP_OUM_DUNGEON = 310;
	private static final Point[] GETBACK_LOC_OUM_DUNGEON = {
			new Point(32818, 32805), new Point(32800, 32798), new Point(32815, 32819),
			new Point(32823, 32811), new Point(32817, 32828),
	};

	private static final short GETBACK_MAP_RESISTANCE = 400;
	private static final Point[] GETBACK_LOC_RESISTANCE = {
			new Point(32570, 32667), new Point(32559, 32678), new Point(32564, 32683),
			new Point(32574, 32661), new Point(32576, 32669), new Point(32572, 32662),
	};

	private static final short GETBACK_MAP_PIRATE_ISLAND = 440;
	private static final Point[] GETBACK_LOC_PIRATE_ISLAND = {
			new Point(32431, 33058), new Point(32407, 33054),
	};

	private static final short GETBACK_MAP_RECLUSE_VILLAGE = 400;
	private static final Point[] GETBACK_LOC_RECLUSE_VILLAGE = {
			new Point(32599, 32916), new Point(32599, 32923), new Point(32603, 32908),
			new Point(32595, 32908), new Point(32591, 32918),
	};

	private static final short GETBACK_MAP_HIDDEN_VALLEY = 2005;
	private static final Point[] GETBACK_LOC_HIDDEN_VALLEY = {
			new Point(32691, 32853), new Point(32692, 32864), new Point(32688, 32876),
			new Point(32673, 32871), new Point(32670, 32857),
	};

	// ===== getGetBackLoc 최적화 테이블 =====
	private static final int MAX_TOWN_ID = TOWNID_claudia;

	private static final short[] GETBACK_MAP_BY_TOWN = new short[MAX_TOWN_ID + 1];
	private static final Point[][] GETBACK_LOCS_BY_TOWN = new Point[MAX_TOWN_ID + 1][];

	static {
		GETBACK_MAP_BY_TOWN[TOWNID_TALKING_ISLAND] = GETBACK_MAP_TALKING_ISLAND;
		GETBACK_LOCS_BY_TOWN[TOWNID_TALKING_ISLAND] = GETBACK_LOC_TALKING_ISLAND;

		GETBACK_MAP_BY_TOWN[TOWNID_SILVER_KNIGHT_TOWN] = GETBACK_MAP_SILVER_KNIGHT_TOWN;
		GETBACK_LOCS_BY_TOWN[TOWNID_SILVER_KNIGHT_TOWN] = GETBACK_LOC_SILVER_KNIGHT_TOWN;

		GETBACK_MAP_BY_TOWN[TOWNID_GLUDIO] = GETBACK_MAP_GLUDIO;
		GETBACK_LOCS_BY_TOWN[TOWNID_GLUDIO] = GETBACK_LOC_GLUDIO;

		GETBACK_MAP_BY_TOWN[TOWNID_ORCISH_FOREST] = GETBACK_MAP_ORCISH_FOREST;
		GETBACK_LOCS_BY_TOWN[TOWNID_ORCISH_FOREST] = GETBACK_LOC_ORCISH_FOREST;

		GETBACK_MAP_BY_TOWN[TOWNID_WINDAWOOD] = GETBACK_MAP_WINDAWOOD;
		GETBACK_LOCS_BY_TOWN[TOWNID_WINDAWOOD] = GETBACK_LOC_WINDAWOOD;

		GETBACK_MAP_BY_TOWN[TOWNID_KENT] = GETBACK_MAP_KENT;
		GETBACK_LOCS_BY_TOWN[TOWNID_KENT] = GETBACK_LOC_KENT;

		GETBACK_MAP_BY_TOWN[TOWNID_GIRAN] = GETBACK_MAP_GIRAN;
		GETBACK_LOCS_BY_TOWN[TOWNID_GIRAN] = GETBACK_LOC_GIRAN;

		GETBACK_MAP_BY_TOWN[TOWNID_HEINE] = GETBACK_MAP_HEINE;
		GETBACK_LOCS_BY_TOWN[TOWNID_HEINE] = GETBACK_LOC_HEINE;

		GETBACK_MAP_BY_TOWN[TOWNID_WERLDAN] = GETBACK_MAP_WERLDAN;
		GETBACK_LOCS_BY_TOWN[TOWNID_WERLDAN] = GETBACK_LOC_WERLDAN;

		GETBACK_MAP_BY_TOWN[TOWNID_OREN] = GETBACK_MAP_OREN;
		GETBACK_LOCS_BY_TOWN[TOWNID_OREN] = GETBACK_LOC_OREN;

		GETBACK_MAP_BY_TOWN[TOWNID_ELVEN_FOREST] = GETBACK_MAP_ELVEN_FOREST;
		GETBACK_LOCS_BY_TOWN[TOWNID_ELVEN_FOREST] = GETBACK_LOC_ELVEN_FOREST;

		GETBACK_MAP_BY_TOWN[TOWNID_ADEN] = GETBACK_MAP_ADEN;
		GETBACK_LOCS_BY_TOWN[TOWNID_ADEN] = GETBACK_LOC_ADEN;

		GETBACK_MAP_BY_TOWN[TOWNID_SILENT_CAVERN] = GETBACK_MAP_SILENT_CAVERN;
		GETBACK_LOCS_BY_TOWN[TOWNID_SILENT_CAVERN] = GETBACK_LOC_SILENT_CAVERN;

		GETBACK_MAP_BY_TOWN[TOWNID_OUM_DUNGEON] = GETBACK_MAP_OUM_DUNGEON;
		GETBACK_LOCS_BY_TOWN[TOWNID_OUM_DUNGEON] = GETBACK_LOC_OUM_DUNGEON;

		GETBACK_MAP_BY_TOWN[TOWNID_RESISTANCE] = GETBACK_MAP_RESISTANCE;
		GETBACK_LOCS_BY_TOWN[TOWNID_RESISTANCE] = GETBACK_LOC_RESISTANCE;

		GETBACK_MAP_BY_TOWN[TOWNID_PIRATE_ISLAND] = GETBACK_MAP_PIRATE_ISLAND;
		GETBACK_LOCS_BY_TOWN[TOWNID_PIRATE_ISLAND] = GETBACK_LOC_PIRATE_ISLAND;

		GETBACK_MAP_BY_TOWN[TOWNID_RECLUSE_VILLAGE] = GETBACK_MAP_RECLUSE_VILLAGE;
		GETBACK_LOCS_BY_TOWN[TOWNID_RECLUSE_VILLAGE] = GETBACK_LOC_RECLUSE_VILLAGE;

		GETBACK_MAP_BY_TOWN[TOWNID_HIDDEN_VALLEY] = GETBACK_MAP_HIDDEN_VALLEY;
		GETBACK_LOCS_BY_TOWN[TOWNID_HIDDEN_VALLEY] = GETBACK_LOC_HIDDEN_VALLEY;

		// 클라우디아는 별도 맵
		GETBACK_MAP_BY_TOWN[TOWNID_claudia] = TOWNID_MAP_claudia;
		GETBACK_LOCS_BY_TOWN[TOWNID_claudia] = GETBACK_LOC_claudia;
	}

	// ===== npcId -> townId 초고속 고정 해시 =====
	private static final int NPC_HASH_SIZE = 256; // power of two, load factor 여유있게
	private static final int NPC_HASH_MASK = NPC_HASH_SIZE - 1;
	private static final int[] NPC_KEYS = new int[NPC_HASH_SIZE];
	private static final byte[] NPC_TOWN = new byte[NPC_HASH_SIZE];

	static {
		// TALKING_ISLAND
		putNpcTown(70528, TOWNID_TALKING_ISLAND);
		putNpcTown(50015, TOWNID_TALKING_ISLAND);
		putNpcTown(70011, TOWNID_TALKING_ISLAND);
		putNpcTown(70012, TOWNID_TALKING_ISLAND);
		putNpcTown(70014, TOWNID_TALKING_ISLAND);
		putNpcTown(70532, TOWNID_TALKING_ISLAND);
		putNpcTown(70536, TOWNID_TALKING_ISLAND);

		// SILVER_KNIGHT_TOWN
		putNpcTown(70799, TOWNID_SILVER_KNIGHT_TOWN);
		putNpcTown(50056, TOWNID_SILVER_KNIGHT_TOWN);
		putNpcTown(70073, TOWNID_SILVER_KNIGHT_TOWN);
		putNpcTown(70074, TOWNID_SILVER_KNIGHT_TOWN);
		putNpcTown(70075, TOWNID_SILVER_KNIGHT_TOWN);

		// KENT
		putNpcTown(70546, TOWNID_KENT);
		putNpcTown(50020, TOWNID_KENT);
		putNpcTown(70016, TOWNID_KENT);
		putNpcTown(70544, TOWNID_KENT);
		putNpcTown(70018, TOWNID_KENT);

		// GLUDIO
		putNpcTown(70567, TOWNID_GLUDIO);
		putNpcTown(50024, TOWNID_GLUDIO);
		putNpcTown(70019, TOWNID_GLUDIO);
		putNpcTown(70022, TOWNID_GLUDIO);

		// ORCISH_FOREST
		putNpcTown(70815, TOWNID_ORCISH_FOREST);
		putNpcTown(70079, TOWNID_ORCISH_FOREST);
		putNpcTown(70836, TOWNID_ORCISH_FOREST);

		// WINDAWOOD
		putNpcTown(70774, TOWNID_WINDAWOOD);
		putNpcTown(50054, TOWNID_WINDAWOOD);
		putNpcTown(70070, TOWNID_WINDAWOOD);
		putNpcTown(70071, TOWNID_WINDAWOOD);
		putNpcTown(70072, TOWNID_WINDAWOOD);
		putNpcTown(70773, TOWNID_WINDAWOOD);

		// GIRAN
		putNpcTown(224, TOWNID_GIRAN);
		putNpcTown(533, TOWNID_GIRAN);
		putNpcTown(70594, TOWNID_GIRAN);
		putNpcTown(50036, TOWNID_GIRAN);
		putNpcTown(5069, TOWNID_GIRAN);
		putNpcTown(70026, TOWNID_GIRAN);
		putNpcTown(70028, TOWNID_GIRAN);
		putNpcTown(70029, TOWNID_GIRAN);
		putNpcTown(70031, TOWNID_GIRAN);
		putNpcTown(70032, TOWNID_GIRAN);
		putNpcTown(70033, TOWNID_GIRAN);
		putNpcTown(70038, TOWNID_GIRAN);
		putNpcTown(70039, TOWNID_GIRAN);
		putNpcTown(70043, TOWNID_GIRAN);
		putNpcTown(70617, TOWNID_GIRAN);
		putNpcTown(70632, TOWNID_GIRAN);
		putNpcTown(7320051, TOWNID_GIRAN);
		putNpcTown(80091, TOWNID_GIRAN);
		putNpcTown(200006, TOWNID_GIRAN);
		putNpcTown(7320002, TOWNID_GIRAN);
		putNpcTown(200001, TOWNID_GIRAN);
		putNpcTown(900173, TOWNID_GIRAN);
		putNpcTown(900172, TOWNID_GIRAN);

		// claudia
		putNpcTown(708601, TOWNID_claudia);

		// HEINE
		putNpcTown(70860, TOWNID_HEINE);
		putNpcTown(50066, TOWNID_HEINE);
		putNpcTown(70082, TOWNID_HEINE);
		putNpcTown(70083, TOWNID_HEINE);
		putNpcTown(70084, TOWNID_HEINE);
		putNpcTown(70873, TOWNID_HEINE);

		// WERLDAN
		putNpcTown(70654, TOWNID_WERLDAN);
		putNpcTown(50039, TOWNID_WERLDAN);
		putNpcTown(70044, TOWNID_WERLDAN);
		putNpcTown(70664, TOWNID_WERLDAN);
		putNpcTown(70045, TOWNID_WERLDAN);

		// OREN
		putNpcTown(70748, TOWNID_OREN);
		putNpcTown(50051, TOWNID_OREN);
		putNpcTown(70059, TOWNID_OREN);
		putNpcTown(70060, TOWNID_OREN);
		putNpcTown(70061, TOWNID_OREN);
		putNpcTown(70062, TOWNID_OREN);
		putNpcTown(70063, TOWNID_OREN);
		putNpcTown(70065, TOWNID_OREN);
		putNpcTown(70066, TOWNID_OREN);
		putNpcTown(70067, TOWNID_OREN);
		putNpcTown(70068, TOWNID_OREN);
		putNpcTown(70749, TOWNID_OREN);

		// ADEN
		putNpcTown(50044, TOWNID_ADEN);
		putNpcTown(70057, TOWNID_ADEN);
		putNpcTown(70048, TOWNID_ADEN);
		putNpcTown(70052, TOWNID_ADEN);
		putNpcTown(70053, TOWNID_ADEN);
		putNpcTown(70049, TOWNID_ADEN);
		putNpcTown(70051, TOWNID_ADEN);
		putNpcTown(70047, TOWNID_ADEN);
		putNpcTown(70058, TOWNID_ADEN);
		putNpcTown(70054, TOWNID_ADEN);
		putNpcTown(70055, TOWNID_ADEN);
		putNpcTown(70056, TOWNID_ADEN);

		// OUM_DUNGEON
		putNpcTown(70092, TOWNID_OUM_DUNGEON);
		putNpcTown(70093, TOWNID_OUM_DUNGEON);
	}

	private L1TownLocation() {}

	public static int[] getGetBackLoc(int town_id) {
		final int[] loc = new int[3];

		if (town_id < 0 || town_id > MAX_TOWN_ID) {
			return fillDefaultSilverKnight(loc);
		}
		final Point[] candidates = GETBACK_LOCS_BY_TOWN[town_id];
		if (candidates == null || candidates.length == 0) {
			return fillDefaultSilverKnight(loc);
		}

		final int rnd = ThreadLocalRandom.current().nextInt(candidates.length);
		final Point p = candidates[rnd];
		loc[0] = p.getX();
		loc[1] = p.getY();
		loc[2] = GETBACK_MAP_BY_TOWN[town_id];
		return loc;
	}

	private static int[] fillDefaultSilverKnight(int[] loc) {
		final Point[] candidates = GETBACK_LOC_SILVER_KNIGHT_TOWN;
		final int rnd = ThreadLocalRandom.current().nextInt(candidates.length);
		final Point p = candidates[rnd];
		loc[0] = p.getX();
		loc[1] = p.getY();
		loc[2] = GETBACK_MAP_SILVER_KNIGHT_TOWN;
		return loc;
	}

	public static int getTownTaxRateByNpcid(int npcid) {
		int town_id = getTownIdByNpcid(npcid);
		if (town_id >= 1 && town_id <= 10) {
			L1Town town = TownTable.getInstance().getTownTable(town_id);
			return (town != null) ? town.get_tax_rate() : 0;
		}
		return 0;
	}

	public static int getTownIdByNpcid(int npcid) {
		if (npcid <= 0) return 0;

		int idx = mix32(npcid) & NPC_HASH_MASK;
		for (;;) {
			final int k = NPC_KEYS[idx];
			if (k == 0) return 0;          // not found
			if (k == npcid) return NPC_TOWN[idx] & 0xFF;
			idx = (idx + 1) & NPC_HASH_MASK;
		}
	}

	// ===== 내부: 해시 삽입/믹서 =====
	private static void putNpcTown(int npcid, int townId) {
		int idx = mix32(npcid) & NPC_HASH_MASK;
		for (;;) {
			final int k = NPC_KEYS[idx];
			if (k == 0 || k == npcid) {
				NPC_KEYS[idx] = npcid;
				NPC_TOWN[idx] = (byte) townId;
				return;
			}
			idx = (idx + 1) & NPC_HASH_MASK;
		}
	}

	// 빠른 32-bit mixing 
	private static int mix32(int x) {
		x ^= (x >>> 16);
		x *= 0x7feb352d;
		x ^= (x >>> 15);
		x *= 0x846ca68b;
		x ^= (x >>> 16);
		return x;
	}
}
