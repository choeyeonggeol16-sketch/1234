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
package l1j.server.server.model;

import java.util.Random;

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

	// 아래와 같이, 조세 없음

	public static final int TOWNID_ELVEN_FOREST = 11;

	public static final int TOWNID_ADEN = 12;

	public static final int TOWNID_SILENT_CAVERN = 13;

	public static final int TOWNID_OUM_DUNGEON = 14;

	public static final int TOWNID_RESISTANCE = 15;

	public static final int TOWNID_PIRATE_ISLAND = 16;

	public static final int TOWNID_RECLUSE_VILLAGE = 17;

	public static final int TOWNID_RASTABAD_GATE = 18;

	public static final int TELEPORT_TO_MOTHER = 19;

	// 귀환 로케이션
	private static final short GETBACK_MAP_TALKING_ISLAND = 0;
	private static final Point[] GETBACK_LOC_TALKING_ISLAND = {
		new Point(32600, 32942), new Point(32574, 32944),
		new Point(32580, 32923), new Point(32557, 32975), 
		new Point(32597, 32914), new Point(32580, 32974), };

	private static final short GETBACK_MAP_SILVER_KNIGHT_TOWN = 4;
	private static final Point[] GETBACK_LOC_SILVER_KNIGHT_TOWN = { 
		new Point(33071, 33402), new Point(33091, 33396),
		new Point(33085, 33402), new Point(33097, 33366),
		new Point(33110, 33365), new Point(33072, 33392), };

	private static final short GETBACK_MAP_GLUDIO = 4;
	private static final Point[] GETBACK_LOC_GLUDIO = { 
		new Point(32601, 32757),
		new Point(32625, 32809),
		new Point(32609, 32727),
		new Point(32612, 32781), 
		new Point(32605, 32761), 
		new Point(32614, 32739),
		new Point(32612, 32773), };

	private static final short GETBACK_MAP_ORCISH_FOREST = 4;
	private static final Point[] GETBACK_LOC_ORCISH_FOREST = { 
		new Point(32750, 32435), new Point(32745, 32447),
		new Point(32738, 32452), new Point(32741, 32436),
		new Point(32749, 32446), };

	private static final short GETBACK_MAP_WINDAWOOD = 4;
	private static final Point[] GETBACK_LOC_WINDAWOOD = {
		new Point(32608, 33178), new Point(32626, 33185),
		new Point(32630, 33179), new Point(32625, 33207),
		new Point(32638, 33203), new Point(32621, 33179), };


	private static final short GETBACK_MAP_KENT = 4;
	private static final Point[] GETBACK_LOC_KENT = { 
		new Point(33048, 32750), new Point(33059, 32768),
		new Point(33047, 32761), new Point(33059, 32759),
		new Point(33051, 32775), new Point(33048, 32778),
		new Point(33064, 32773), new Point(33057, 32748), };

	private static final short GETBACK_MAP_GIRAN = 4;
	public static final Point[] GETBACK_LOC_GIRAN = { 
		new Point(33418, 32818),
		new Point(33442, 32790), 
		new Point(33442, 32831), 
		new Point(33419, 32792), 
		new Point(33451, 32795), 
		new Point(33439, 32813), 
		new Point(33410, 32806), 
		new Point(33415, 32814), 
	};

	private static final short GETBACK_MAP_HEINE = 4;
	private static final Point[] GETBACK_LOC_HEINE = { 
		new Point(33593, 33242), new Point(33593, 33248),
		new Point(33604, 33236), new Point(33599, 33236),
		new Point(33610, 33247), new Point(33610, 33241),
		new Point(33599, 33252), new Point(33605, 33252), };

	private static final short GETBACK_MAP_WERLDAN = 4;
	private static final Point[] GETBACK_LOC_WERLDAN = { 
		new Point(33702, 32492), new Point(33747, 32508),
		new Point(33696, 32498), new Point(33723, 32512), 
		new Point(33710, 32521), new Point(33724, 32488),
		new Point(33693, 32513), };

	private static final short GETBACK_MAP_OREN = 4;
	private static final Point[] GETBACK_LOC_OREN = { 
		new Point(34086, 32280), new Point(34037, 32230),
		new Point(34022, 32254), new Point(34021, 32269), 
		new Point(34044, 32290), new Point(34049, 32316),
		new Point(34081, 32249), new Point(34074, 32313),
		new Point(34064, 32230), };

	private static final short GETBACK_MAP_ELVEN_FOREST = 4;
	private static final Point[] GETBACK_LOC_ELVEN_FOREST = { 
		new Point(33065, 32358), new Point(33052, 32313),
		new Point(33030, 32342), new Point(33068, 32320), 
		new Point(33071, 32314), new Point(33030, 32370),
		new Point(33076, 32324), new Point(33068, 32336), };

	private static final short GETBACK_MAP_ADEN = 4;
	private static final Point[] GETBACK_LOC_ADEN = { 
		new Point(33915, 33114), new Point(34061, 33115),
		new Point(34090, 33168), new Point(34011, 33136), 
		new Point(34093, 33117), new Point(33959, 33156),
		new Point(33992, 33120), new Point(34047, 33156), };

	private static final short GETBACK_MAP_SILENT_CAVERN = 304;
	private static final Point[] GETBACK_LOC_SILENT_CAVERN = { 
		new Point(32856, 32898), new Point(32860, 32916),
		new Point(32868, 32893), new Point(32875, 32903), 
		new Point(32855, 32898), };

	private static final short GETBACK_MAP_OUM_DUNGEON = 310;
	private static final Point[] GETBACK_LOC_OUM_DUNGEON = {
		new Point(32818, 32805), new Point(32800, 32798),
		new Point(32815, 32819), new Point(32823, 32811), 
		new Point(32817, 32828), };

	private static final short GETBACK_MAP_RESISTANCE = 400;
	private static final Point[] GETBACK_LOC_RESISTANCE = { 
		new Point(32570, 32667), new Point(32559, 32678),
		new Point(32564, 32683), new Point(32574, 32661), 
		new Point(32576, 32669), new Point(32572, 32662), };

	private static final short GETBACK_MAP_PIRATE_ISLAND = 440;
	private static final Point[] GETBACK_LOC_PIRATE_ISLAND = { 
		new Point(32431, 33058), new Point(32407, 33054), };

	private static final short GETBACK_MAP_RECLUSE_VILLAGE = 400;
	private static final Point[] GETBACK_LOC_RECLUSE_VILLAGE = { 
		new Point(32599, 32916), new Point(32599, 32923),
		new Point(32603, 32908), new Point(32595, 32908), 
		new Point(32591, 32918), };

	private static final short GETBACK_MAP_RASTABAD_GATE = 450;
	private static final Point[] GETBACK_LOC_RASTABAD_GATE = { 
		new Point(32694, 32795),
		new Point(32692, 32795),
		new Point(32694, 32798),
		new Point(32695, 32793),
		new Point(32674, 32806), 
		new Point(32675, 32807),
		new Point(32677, 32808),};

	private L1TownLocation() {
	}

	public static int[] getGetBackLoc(int town_id) { // town_id로부터 귀환처의 좌표를 랜덤에 돌려준다
		Random random = new Random();
	int[] loc = new int[3];

	if (town_id == TOWNID_TALKING_ISLAND) { // TI
		int rnd = random.nextInt(GETBACK_LOC_TALKING_ISLAND.length);
		loc[0] = GETBACK_LOC_TALKING_ISLAND[rnd].getX();
		loc[1] = GETBACK_LOC_TALKING_ISLAND[rnd].getY();
		loc[2] = GETBACK_MAP_TALKING_ISLAND;
	} else if (town_id == TOWNID_SILVER_KNIGHT_TOWN) { // SKT
		int rnd = random.nextInt(GETBACK_LOC_SILVER_KNIGHT_TOWN.length);
		loc[0] = GETBACK_LOC_SILVER_KNIGHT_TOWN[rnd].getX();
		loc[1] = GETBACK_LOC_SILVER_KNIGHT_TOWN[rnd].getY();
		loc[2] = GETBACK_MAP_SILVER_KNIGHT_TOWN;
	} else if (town_id == TOWNID_KENT) { // 켄트
		int rnd = random.nextInt(GETBACK_LOC_KENT.length);
		loc[0] = GETBACK_LOC_KENT[rnd].getX();
		loc[1] = GETBACK_LOC_KENT[rnd].getY();
		loc[2] = GETBACK_MAP_KENT;
	} else if (town_id == TOWNID_GLUDIO) { // 한패
		int rnd = random.nextInt(GETBACK_LOC_GLUDIO.length);
		loc[0] = GETBACK_LOC_GLUDIO[rnd].getX();
		loc[1] = GETBACK_LOC_GLUDIO[rnd].getY();
		loc[2] = GETBACK_MAP_GLUDIO;
	} else if (town_id == TOWNID_ORCISH_FOREST) { // 화전마을
		int rnd = random.nextInt(GETBACK_LOC_ORCISH_FOREST.length);
		loc[0] = GETBACK_LOC_ORCISH_FOREST[rnd].getX();
		loc[1] = GETBACK_LOC_ORCISH_FOREST[rnd].getY();
		loc[2] = GETBACK_MAP_ORCISH_FOREST;
	} else if (town_id == TOWNID_WINDAWOOD) { // 우드 베크
		int rnd = random.nextInt(GETBACK_LOC_WINDAWOOD.length);
		loc[0] = GETBACK_LOC_WINDAWOOD[rnd].getX();
		loc[1] = GETBACK_LOC_WINDAWOOD[rnd].getY();
		loc[2] = GETBACK_MAP_WINDAWOOD;
	} else if (town_id == TOWNID_GIRAN) { // 기란
		int rnd = random.nextInt(GETBACK_LOC_GIRAN.length);
		loc[0] = GETBACK_LOC_GIRAN[rnd].getX();
		loc[1] = GETBACK_LOC_GIRAN[rnd].getY();
		loc[2] = GETBACK_MAP_GIRAN;
	} else if (town_id == TOWNID_HEINE) { // Heine
		int rnd = random.nextInt(GETBACK_LOC_HEINE.length);
		loc[0] = GETBACK_LOC_HEINE[rnd].getX();
		loc[1] = GETBACK_LOC_HEINE[rnd].getY();
		loc[2] = GETBACK_MAP_HEINE;
	} else if (town_id == TOWNID_WERLDAN) { // 완숙
		int rnd = random.nextInt(GETBACK_LOC_WERLDAN.length);
		loc[0] = GETBACK_LOC_WERLDAN[rnd].getX();
		loc[1] = GETBACK_LOC_WERLDAN[rnd].getY();
		loc[2] = GETBACK_MAP_WERLDAN;
	} else if (town_id == TOWNID_OREN) { // 오렌
		int rnd = random.nextInt(GETBACK_LOC_OREN.length);
		loc[0] = GETBACK_LOC_OREN[rnd].getX();
		loc[1] = GETBACK_LOC_OREN[rnd].getY();
		loc[2] = GETBACK_MAP_OREN;
	} else if (town_id == TOWNID_ELVEN_FOREST) { // 에르프의 숲
		int rnd = random.nextInt(GETBACK_LOC_ELVEN_FOREST.length);
		loc[0] = GETBACK_LOC_ELVEN_FOREST[rnd].getX();
		loc[1] = GETBACK_LOC_ELVEN_FOREST[rnd].getY();
		loc[2] = GETBACK_MAP_ELVEN_FOREST;
	} else if (town_id == TOWNID_ADEN) { // 에덴
		int rnd = random.nextInt(GETBACK_LOC_ADEN.length);
		loc[0] = GETBACK_LOC_ADEN[rnd].getX();
		loc[1] = GETBACK_LOC_ADEN[rnd].getY();
		loc[2] = GETBACK_MAP_ADEN;
	} else if (town_id == TOWNID_SILENT_CAVERN) { // 침묵의 동굴
		int rnd = random.nextInt(GETBACK_LOC_SILENT_CAVERN.length);
		loc[0] = GETBACK_LOC_SILENT_CAVERN[rnd].getX();
		loc[1] = GETBACK_LOC_SILENT_CAVERN[rnd].getY();
		loc[2] = GETBACK_MAP_SILENT_CAVERN;
	} else if (town_id == TOWNID_OUM_DUNGEON) { // 오옴 지하 감옥
		int rnd = random.nextInt(GETBACK_LOC_OUM_DUNGEON.length);
		loc[0] = GETBACK_LOC_OUM_DUNGEON[rnd].getX();
		loc[1] = GETBACK_LOC_OUM_DUNGEON[rnd].getY();
		loc[2] = GETBACK_MAP_OUM_DUNGEON;
	} else if (town_id == TOWNID_RESISTANCE) { // 레시스탄스마을
		int rnd = random.nextInt(GETBACK_LOC_RESISTANCE.length);
		loc[0] = GETBACK_LOC_RESISTANCE[rnd].getX();
		loc[1] = GETBACK_LOC_RESISTANCE[rnd].getY();
		loc[2] = GETBACK_MAP_RESISTANCE;
	} else if (town_id == TOWNID_PIRATE_ISLAND) { // 해적섬
		int rnd = random.nextInt(GETBACK_LOC_PIRATE_ISLAND.length);
		loc[0] = GETBACK_LOC_PIRATE_ISLAND[rnd].getX();
		loc[1] = GETBACK_LOC_PIRATE_ISLAND[rnd].getY();
		loc[2] = GETBACK_MAP_PIRATE_ISLAND;
	} else if (town_id == TOWNID_RECLUSE_VILLAGE) { // 벽촌
		int rnd = random.nextInt(GETBACK_LOC_RECLUSE_VILLAGE.length);
		loc[0] = GETBACK_LOC_RECLUSE_VILLAGE[rnd].getX();
		loc[1] = GETBACK_LOC_RECLUSE_VILLAGE[rnd].getY();
		loc[2] = GETBACK_MAP_RECLUSE_VILLAGE;
	} else if (town_id == TOWNID_RASTABAD_GATE) { // 라스타바드 정문 이동
		int rnd = random.nextInt(GETBACK_LOC_RASTABAD_GATE.length);
		loc[0] = GETBACK_LOC_RASTABAD_GATE[rnd].getX();
		loc[1] = GETBACK_LOC_RASTABAD_GATE[rnd].getY();
		loc[2] = GETBACK_MAP_RASTABAD_GATE;	

	} else { // 그 외는 SKT
		int rnd = random.nextInt(GETBACK_LOC_SILVER_KNIGHT_TOWN.length);
		loc[0] = GETBACK_LOC_SILVER_KNIGHT_TOWN[rnd].getX();
		loc[1] = GETBACK_LOC_SILVER_KNIGHT_TOWN[rnd].getY();
		loc[2] = GETBACK_MAP_SILVER_KNIGHT_TOWN;
	}
	return loc;
	}

	public static int getTownTaxRateByNpcid(int npcid) { // npcid로부터 조세율을 돌려준다
		int tax_rate = 0;

		//	int town_id = getTownIdByNpcid(npcid);
		//		if (town_id >= 1 && town_id <= 10) {
		//			L1Town town = TownTable.getInstance().getTownTable(town_id);
		//			tax_rate = town.get_tax_rate() + 2; // 2%는 고정세
		//		}
		return tax_rate;
	}

	public static int getTownIdByNpcid(int npcid) { // npcid로부터 town_id를 돌려준다
		// 에덴성：에덴 왕국 전역
		// 켄트성：켄트, 그르딘
		// 윈다웃드성：우드 베크, 오아시스, 실버 나이트 타운
		// 기란성：기란, 이야기할 수 있는 섬
		// Heine성：Heine
		// 드워후성：완숙, 상아의 탑, 상아의 탑의 마을
		// 오크사이：화전마을
		// 디아드 요새：전쟁세의 일부

		// XXX: 아직 NPC는 L1CastleLocation로부터 가져온 채로 상태(미정리)
		int town_id = 0;

		switch (npcid) {
		case 70528: // 타운 마스터(TI)
		case 50015: // 루카스(텔레 포터)
		case 70010: // 바르심(개집 뒷길구 가게)
		case 70011: // 선착장 관리인
		case 70012: // 세레나(여인숙)
		case 70014: // 판도라(항구 고물상)
		case 70532: // 죤슨(애완동물가게)
		case 70536: // 토마(대장간)
			town_id = TOWNID_TALKING_ISLAND;
			break;

		case 70799: // 타운 마스터(SKT)
		case 50056: // 멧트(텔레 포터)
		case 70073: // 그렌(무기가게)
		case 70074: // 메린(고물상)
		case 70075: // 미란다(여인숙)
			town_id = TOWNID_SILVER_KNIGHT_TOWN;
			break;

		case 70546: // 타운 마스터(KENT)
		case 50020: // 스탠리(텔레 포터)
		case 70018: // ISO-리어(고물상)
		case 70016: // 안딘(무기가게)
		case 70544: // 릭크(애완동물가게)
			town_id = TOWNID_KENT;
			break;

		case 70567: // 타운 마스터(한패)
		case 50024: // 스티브(한패 텔레 포터)
		case 70019: // 로리아(한패 여인숙)
		case 70020: // 로르코(한패 고대 물품 상인)
		case 70021: // 롯데(한패 고물상)
		case 70022: // 선착장 관리인
		case 70024: // 케티(한패 무기가게)
			town_id = TOWNID_GLUDIO;
			break;

		case 70815: // 화전마을 타운 마스터
		case 70079: // 잭슨(고물상)
		case 70836: // 한스(애완동물가게)
			town_id = TOWNID_ORCISH_FOREST;
			break;

		case 70774: // 타운 마스터(WB)
		case 50054: // 트레이(텔레 포터)
		case 70070: // 베릿사(여인숙)
		case 70071: // 아슈르(오아시스)
		case 70072: // 에르미나(고물상)
		case 70773: // 마빈(애완동물가게)
			town_id = TOWNID_WINDAWOOD;
			break;

			//		case 70594: // 타운 마스터(기란)
			//		case 50036: // 위르마(텔레 포터)
			//		case 70026: // 데렉크(헌터)
			//		case 70028: // 런 달(약품 상인)
			//		case 70029: // 마가레트(식료품 상인)
			//		case 70030: // 메이아(고물상)
			//		case 70031: // 모리(여인숙)
			//		case 70032: // 바질(방어용 기구가게)
			//		case 70033: // 베리타(고물상)
			//		case 70038: // 에바트(옷감 상인)
			//		case 70039: // 워너(무기가게)
			//		case 70043: // 필립(가죽 상인)
			//		case 70617: // 아르몬(애완동물가게)
			//		case 70632: // 케빈(애완동물가게)
			//			town_id = TOWNID_GIRAN;
			//			break;

		case 70860: // 타운 마스터(Heine)
		case 50066: // 리올(텔레 포터)
		case 70082: // 소총탄(고물상)
		case 70083: // 시반(무기가게)
		case 70084: // 엘리(여인숙)
		case 70873: // 에란(애완동물가게)
			town_id = TOWNID_HEINE;
			break;

		case 70654: // 타운 마스터(완숙)
		case 50039: // 레스리(텔레 포터)
		case 70045: // 베리(고물상)
		case 70044: // 랄프(무기가게)
		case 70664: // 코브(애완동물가게)
			town_id = TOWNID_WERLDAN;
			break;

		case 70748: // 타운 마스터(오렌)
		case 50051: // 키리우스(텔레 포터)
		case 70059: // 디코(국경 요새 고물상)
		case 70060: // 린다(상아의 탑정령 마법가게)
		case 70061: // 만드라(무기가게)
		case 70062: // 바리에스(상아의 탑마법가게)
		case 70063: // 비우스(고물상)
		case 70065: // 엔케(여인숙)
		case 70066: // 크리스트(상아의 탑마법가게)
		case 70067: // 파골(상아의 탑고물상)
		case 70068: // 흐랑코(고대 물품 상인)
		case 70749: // 마일드(애완동물가게)
			town_id = TOWNID_OREN;
			break;

		case 50044: // 시리우스(텔레 포터)
		case 70057: // 캐서린(고물상)
		case 70048: // 라온(고물상)
		case 70052: // 메리사(고물상)
		case 70053: // 샤르(식료품가게)
		case 70049: // 로젠(일부가게)
		case 70051: // 마그스(고물상)
		case 70047: // 차동 맨(무기가게)
		case 70058: // 페가(방어용 기구가게)
		case 70054: // 스빈(여인숙)
		case 70055: // 에이시누(애완동물 숍)
		case 70056: // 조드(짚시 타운 고대 물품 상인)
			town_id = TOWNID_ADEN;
			break;

		case 70092: // 상인 에마르트
		case 70093: // 상인 카르프
			town_id = TOWNID_OUM_DUNGEON;
			break;

		default:
			break;
		}
		return town_id;
	}
}
