package l1j.server.server.model.world.residence;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.types.Point;

public final class L1HouseLocation {

    // =========================
    // Teleport Locs 
    // =========================
    private static final int[] TELEPORT_LOC_MAPID = { 4, 4, 4, 350 };

    private static final Point[] TELEPORT_LOC_GIRAN = {
        new Point(33419, 32810), new Point(33343, 32723),
        new Point(33553, 32712), new Point(32702, 32842),
    };

    private static final Point[] TELEPORT_LOC_HEINE = {
        new Point(33604, 33236), new Point(33649, 33413),
        new Point(33553, 32712), new Point(32702, 32842),
    };

    private static final Point[] TELEPORT_LOC_ADEN = {
        new Point(33966, 33253), new Point(33921, 33177),
        new Point(33553, 32712), new Point(32702, 32842),
    };

    private static final Point[] TELEPORT_LOC_GLUDIN = {
        new Point(32628, 32807), new Point(32623, 32729),
        new Point(33553, 32712), new Point(32702, 32842),
    };

    private static final List<Integer> HOUSE_IDS = HouseTable.getHouseIdList();

    private L1HouseLocation() {}

    // =========================
    // 내부 하우스 맵 범위 (원본: 5068~5123)
    // =========================
    private static final short INNER_MAP_MIN = 5068;
    private static final short INNER_MAP_MAX = 5123;
    private static final int INNER_MAP_SIZE = (INNER_MAP_MAX - INNER_MAP_MIN) + 1;

    private static final class Cache {
        final short[] innerMapToHouseIndex;  // [mapId - INNER_MAP_MIN] -> houseIndex
        final int[] indexToHouseId;          // [houseIndex] -> houseId (0은 없음)

        final int map4MinX, map4MinY, map4W, map4H;
        final short[] map4TileHouseIndex;    // [(y-minY)*W + (x-minX)] -> houseIndex

        Cache(short[] inner, int[] idxToId, int minX, int minY, int w, int h, short[] tile) {
            this.innerMapToHouseIndex = inner;
            this.indexToHouseId = idxToId;
            this.map4MinX = minX;
            this.map4MinY = minY;
            this.map4W = w;
            this.map4H = h;
            this.map4TileHouseIndex = tile;
        }
    }

    private static final Object REBUILD_LOCK = new Object();
    private static volatile Cache C = buildCache();

    // 디버그/검증을 켜고 싶으면 true
    private static final boolean VERIFY_NO_OVERLAP_ON_BUILD = false;

    // =========================
    // 외부에서 리로드 시 호출 가능
    // =========================
    public static void rebuildCaches() {
        synchronized (REBUILD_LOCK) {
            C = buildCache();
        }
    }

    // =========================
    //  API: houseId를 O(1)로 반환
    // =========================
    public static int getHouseIdAt(int x, int y, short mapId) {
        final Cache c = C;

        // 1) 내부맵(5068~5123)
        if (mapId >= INNER_MAP_MIN && mapId <= INNER_MAP_MAX) {
            final short idx = c.innerMapToHouseIndex[mapId - INNER_MAP_MIN];
            return (idx == 0 || idx >= c.indexToHouseId.length) ? 0 : c.indexToHouseId[idx];
        }

        // 2) 외부맵: map4만
        if (mapId != 4) return 0;

        final int dx = x - c.map4MinX;
        final int dy = y - c.map4MinY;

        // 음수 빠른 컷(분기 최소)
        if ((dx | dy) < 0) return 0;
        if (dx >= c.map4W || dy >= c.map4H) return 0;

        final int pos = dy * c.map4W + dx;
        final short idx = c.map4TileHouseIndex[pos];
        return (idx == 0 || idx >= c.indexToHouseId.length) ? 0 : c.indexToHouseId[idx];
    }

    public static boolean isInHouse(int x, int y, short mapId) {
        return getHouseIdAt(x, y, mapId) != 0;
    }

    // “아지트 번호=하우스ID” 판정: 진짜 O(1)
    public static boolean isInHouseLoc(int houseId, int x, int y, short mapId) {
        return getHouseIdAt(x, y, mapId) == houseId;
    }

    // =========================
    // Regen Location:
    // =========================
    public static boolean isRegenLoc(L1PcInstance pc, int x, int y, short mapId) {
    	if (pc == null) return false;

    	switch (mapId) {
    	case 4: { // 엄마나무(엘프만)
    		final int ls = x + y;
    		if (ls < 65374 || ls > 65408) return false;
    		final int lm = y - x;
    		if (lm < -734 || lm > -701) return false;
    		return pc.isElf();
    	}
    	case 613: { // 수상한 마을
    		final int ls = x + y;
    		if (ls < 65638 || ls > 65672) return false;
    		final int lm = y - x;
    		return (lm >= 78 && lm <= 112);
    	}
    	case 1001: { // 실베리아(흑마), 베히모스(용기사)
    		final int ls = x + y;
    		if (ls < 65649 || ls > 65679) return false;
    		final int lm = y - x;
    		if (lm >= 47 && lm <= 76) return pc.isBlackwizard();
    		if (lm >= 49 && lm <= 78) return pc.isDragonknight();
    		return false;
    	}
    	case 2005: // 숨겨진 계곡
    		return (x >= 32685 && x <= 32694) && (y >= 32845 && y <= 32854);
    	case 622:  // 수상한 하늘정원
    		return (x >= 32763 && x <= 32772) && (y >= 32824 && y <= 32834);
    	default:
    		return false;
    	}
    }

    // =========================
    // 텔레포트: 인덱스 방어
    // =========================
    public static int[] getHouseTeleportLoc(int houseId, int number) {
        if (number < 0) number = 0;
        if (number >= TELEPORT_LOC_MAPID.length) number = TELEPORT_LOC_MAPID.length - 1;

        int[] loc = new int[3];
        if (houseId >= 262145 && houseId <= 262189) {
            loc[0] = TELEPORT_LOC_GIRAN[number].getX();
            loc[1] = TELEPORT_LOC_GIRAN[number].getY();
            loc[2] = TELEPORT_LOC_MAPID[number];
        } else if (houseId >= 327681 && houseId <= 327691) {
            loc[0] = TELEPORT_LOC_HEINE[number].getX();
            loc[1] = TELEPORT_LOC_HEINE[number].getY();
            loc[2] = TELEPORT_LOC_MAPID[number];
        } else if (houseId >= 458753 && houseId <= 458819) {
            loc[0] = TELEPORT_LOC_ADEN[number].getX();
            loc[1] = TELEPORT_LOC_ADEN[number].getY();
            loc[2] = TELEPORT_LOC_MAPID[number];
        } else if (houseId >= 524289 && houseId <= 524294) {
            loc[0] = TELEPORT_LOC_GLUDIN[number].getX();
            loc[1] = TELEPORT_LOC_GLUDIN[number].getY();
            loc[2] = TELEPORT_LOC_MAPID[number];
        }
        return loc;
    }

    public static List<Integer> getHouseIdList() {
        return HOUSE_IDS;
    }

    // =========================
    // getHouseLoc / getBasementLoc 유지 
    // =========================
    public static int[] getHouseLoc(int houseId) {
        int[] loc = new int[3];
        switch (houseId) {
            case 262145: loc[0]=33371; loc[1]=32652; loc[2]=4; break;
            case 262146: loc[0]=33384; loc[1]=32655; loc[2]=4; break;
            case 262147: loc[0]=33395; loc[1]=32656; loc[2]=4; break;
            case 262148: loc[0]=33428; loc[1]=32659; loc[2]=4; break;
            case 262149: loc[0]=33439; loc[1]=32666; loc[2]=4; break;
            case 262150: loc[0]=33457; loc[1]=32654; loc[2]=4; break;
            case 262151: loc[0]=33477; loc[1]=32668; loc[2]=4; break;
            case 262152: loc[0]=33471; loc[1]=32679; loc[2]=4; break;
            case 262153: loc[0]=33459; loc[1]=32700; loc[2]=4; break;
            case 262154: loc[0]=33424; loc[1]=32691; loc[2]=4; break;
            case 262155: loc[0]=33409; loc[1]=32675; loc[2]=4; break;
            case 262156: loc[0]=33420; loc[1]=32709; loc[2]=4; break;
            case 262157: loc[0]=33375; loc[1]=32698; loc[2]=4; break;
            case 262158: loc[0]=33363; loc[1]=32684; loc[2]=4; break;
            case 262159: loc[0]=33360; loc[1]=32670; loc[2]=4; break;
            case 262160: loc[0]=33341; loc[1]=32661; loc[2]=4; break;
            case 262161: loc[0]=33346; loc[1]=32675; loc[2]=4; break;
            case 262162: loc[0]=33341; loc[1]=32710; loc[2]=4; break;
            case 262163: loc[0]=33355; loc[1]=32734; loc[2]=4; break;
            case 262164: loc[0]=33366; loc[1]=32714; loc[2]=4; break;
            case 262165: loc[0]=33381; loc[1]=32715; loc[2]=4; break;
            case 262166: loc[0]=33404; loc[1]=32739; loc[2]=4; break;
            case 262167: loc[0]=33424; loc[1]=32718; loc[2]=4; break;
            case 262168: loc[0]=33449; loc[1]=32732; loc[2]=4; break;
            case 262169: loc[0]=33405; loc[1]=32757; loc[2]=4; break;
            case 262170: loc[0]=33366; loc[1]=32761; loc[2]=4; break;
            case 262171: loc[0]=33351; loc[1]=32775; loc[2]=4; break;
            case 262172: loc[0]=33358; loc[1]=32789; loc[2]=4; break;
            case 262173: loc[0]=33372; loc[1]=32792; loc[2]=4; break;
            case 262174: loc[0]=33384; loc[1]=32776; loc[2]=4; break;
            case 262175: loc[0]=33403; loc[1]=32794; loc[2]=4; break;
            case 262176: loc[0]=33485; loc[1]=32794; loc[2]=4; break;
            case 262177: loc[0]=33499; loc[1]=32804; loc[2]=4; break;
            case 262178: loc[0]=33382; loc[1]=32804; loc[2]=4; break;
            case 262179: loc[0]=33376; loc[1]=32828; loc[2]=4; break;
            case 262180: loc[0]=33399; loc[1]=32813; loc[2]=4; break;
            case 262181: loc[0]=33397; loc[1]=32822; loc[2]=4; break;
            case 262182: loc[0]=33437; loc[1]=32844; loc[2]=4; break;
            case 262183: loc[0]=33459; loc[1]=32840; loc[2]=4; break;
            case 262184: loc[0]=33391; loc[1]=32851; loc[2]=4; break;
            case 262185: loc[0]=33399; loc[1]=32860; loc[2]=4; break;
            case 262186: loc[0]=33415; loc[1]=32853; loc[2]=4; break;
            case 262187: loc[0]=33375; loc[1]=32873; loc[2]=4; break;
            case 262188: loc[0]=33428; loc[1]=32871; loc[2]=4; break;
            case 262189: loc[0]=33443; loc[1]=32870; loc[2]=4; break;

            case 327681: loc[0]=33609; loc[1]=33217; loc[2]=4; break;
            case 327682: loc[0]=33630; loc[1]=33209; loc[2]=4; break;
            case 327683: loc[0]=33628; loc[1]=33226; loc[2]=4; break;
            case 327684: loc[0]=33633; loc[1]=33248; loc[2]=4; break;
            case 327685: loc[0]=33619; loc[1]=33265; loc[2]=4; break;
            case 327686: loc[0]=33575; loc[1]=33233; loc[2]=4; break;
            case 327687: loc[0]=33584; loc[1]=33306; loc[2]=4; break;
            case 327688: loc[0]=33581; loc[1]=33338; loc[2]=4; break;
            case 327689: loc[0]=33620; loc[1]=33381; loc[2]=4; break;
            case 327690: loc[0]=33625; loc[1]=33398; loc[2]=4; break;
            case 327691: loc[0]=33625; loc[1]=33445; loc[2]=4; break;

            case 524289: loc[0]=32564; loc[1]=32675; loc[2]=4; break;
            case 524290: loc[0]=32549; loc[1]=32707; loc[2]=4; break;
            case 524291: loc[0]=32538; loc[1]=32782; loc[2]=4; break;
            case 524292: loc[0]=32558; loc[1]=32786; loc[2]=4; break;
            case 524293: loc[0]=32536; loc[1]=32809; loc[2]=4; break;
            case 524294: loc[0]=32554; loc[1]=32819; loc[2]=4; break;
            default: break;
        }
        return loc;
    }

    public static int[] getBasementLoc(int houseId) {
        int[] loc = new int[3];
        if (houseId >= 262145 && houseId <= 262189) {
            loc[0] = 32768; loc[1] = 32832; loc[2] = houseId - 257077;
        } else if (houseId >= 327681 && houseId <= 327691) {
            loc[0] = 32766; loc[1] = 32827; loc[2] = houseId - 322568;
        } else if (houseId >= 458753 && houseId <= 458819) {
            loc[0] = 32768; loc[1] = 32759; loc[2] = houseId - 453752;
        } else if (houseId >= 524289 && houseId <= 524294) {
            loc[0] = 32800; loc[1] = 32785; loc[2] = houseId - 519294;
        }
        return loc;
    }

    // =========================
    // Cache Builder
    // =========================
    private static final class Rect {
        final int x1, y1, x2, y2; // inclusive
        final short houseIndex;
        Rect(int x1, int y1, int x2, int y2, short houseIndex) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.houseIndex = houseIndex;
        }
    }

    private static Cache buildCache() {
        // houseIndex 1..N 할당 + houseId 매핑 테이블
        final Builder b = new Builder(128);

        // 내부맵(5068~5123) -> houseIndex 매핑
        final short[] inner = new short[INNER_MAP_SIZE];

        // map4 외부 rect 수집
        final ArrayList<Rect> rects = new ArrayList<>(128);

        // ===== 데이터는 “네가 준 전부” 기준 =====
        // GIRAN 262145~262189 (inner 5068~5112)
        b.addHouse(262145); inner[5068 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33368,32651,33375,32654,b.lastIndex()));
        rects.add(new Rect(33373,32655,33375,32657,b.lastIndex()));

        b.addHouse(262146); inner[5069 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33381,32653,33387,32656,b.lastIndex()));

        b.addHouse(262147); inner[5070 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33392,32650,33404,32656,b.lastIndex()));

        b.addHouse(262148); inner[5071 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33427,32656,33430,32662,b.lastIndex()));

        b.addHouse(262149); inner[5072 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33439,32665,33445,32667,b.lastIndex()));
        rects.add(new Rect(33442,32668,33445,32672,b.lastIndex()));

        b.addHouse(262150); inner[5073 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33454,32648,33466,32654,b.lastIndex()));

        b.addHouse(262151); inner[5074 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33476,32665,33479,32671,b.lastIndex()));

        b.addHouse(262152); inner[5075 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33471,32678,33477,32680,b.lastIndex()));
        rects.add(new Rect(33474,32681,33477,32685,b.lastIndex()));

        b.addHouse(262153); inner[5076 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33453,32694,33460,32697,b.lastIndex()));
        rects.add(new Rect(33458,32698,33460,32700,b.lastIndex()));

        b.addHouse(262154); inner[5077 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33421,32685,33433,32692,b.lastIndex()));

        b.addHouse(262155); inner[5078 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33409,32674,33415,32676,b.lastIndex()));
        rects.add(new Rect(33412,32677,33415,32681,b.lastIndex()));

        b.addHouse(262156); inner[5079 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33414,32703,33421,32706,b.lastIndex()));
        rects.add(new Rect(33419,32707,33421,32709,b.lastIndex()));

        b.addHouse(262157); inner[5080 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33372,32692,33384,32698,b.lastIndex()));

        b.addHouse(262158); inner[5081 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33362,32681,33365,32687,b.lastIndex()));

        b.addHouse(262159); inner[5082 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33360,32669,33366,32671,b.lastIndex()));
        rects.add(new Rect(33363,32672,33366,32676,b.lastIndex()));

        b.addHouse(262160); inner[5083 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33341,32660,33347,32662,b.lastIndex()));
        rects.add(new Rect(33344,32663,33347,32667,b.lastIndex()));

        b.addHouse(262161); inner[5084 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33345,32672,33348,32678,b.lastIndex()));

        b.addHouse(262162); inner[5085 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33338,32704,33350,32711,b.lastIndex()));

        b.addHouse(262163); inner[5086 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33349,32728,33356,32731,b.lastIndex()));
        rects.add(new Rect(33354,32732,33356,32734,b.lastIndex()));

        b.addHouse(262164); inner[5087 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33366,32713,33372,32715,b.lastIndex()));
        rects.add(new Rect(33369,32716,33372,32720,b.lastIndex()));

        b.addHouse(262165); inner[5088 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33380,32712,33383,32718,b.lastIndex()));

        b.addHouse(262166); inner[5089 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33401,32733,33413,32739,b.lastIndex()));

        b.addHouse(262167); inner[5090 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33424,32717,33430,32719,b.lastIndex()));
        rects.add(new Rect(33427,32720,33430,32724,b.lastIndex()));

        b.addHouse(262168); inner[5091 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33448,32729,33451,32735,b.lastIndex()));

        b.addHouse(262169); inner[5092 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33404,32754,33407,32760,b.lastIndex()));

        b.addHouse(262170); inner[5093 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33363,32755,33375,32761,b.lastIndex()));

        b.addHouse(262171); inner[5094 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33351,32774,33357,32776,b.lastIndex()));
        rects.add(new Rect(33354,32777,33357,32781,b.lastIndex()));

        b.addHouse(262172); inner[5095 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33355,32787,33361,32790,b.lastIndex()));

        b.addHouse(262173); inner[5096 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33366,32786,33373,32789,b.lastIndex()));
        rects.add(new Rect(33371,32790,33373,32792,b.lastIndex()));

        b.addHouse(262174); inner[5097 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33383,32773,33386,32779,b.lastIndex()));

        b.addHouse(262175); inner[5098 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33397,32788,33404,32791,b.lastIndex()));
        rects.add(new Rect(33402,32792,33404,32794,b.lastIndex()));

        b.addHouse(262176); inner[5099 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33479,32788,33486,32791,b.lastIndex()));
        rects.add(new Rect(33484,32792,33486,32794,b.lastIndex()));

        b.addHouse(262177); inner[5100 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33498,32801,33501,32807,b.lastIndex()));

        b.addHouse(262178); inner[5101 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33379,32802,33385,32805,b.lastIndex()));

        b.addHouse(262179); inner[5102 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33373,32822,33385,32829,b.lastIndex()));

        b.addHouse(262180); inner[5103 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33398,32810,33401,32816,b.lastIndex()));

        b.addHouse(262181); inner[5104 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33397,32821,33403,32823,b.lastIndex()));
        rects.add(new Rect(33400,32824,33403,32828,b.lastIndex()));

        b.addHouse(262182); inner[5105 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33431,32838,33438,32841,b.lastIndex()));
        rects.add(new Rect(33436,32842,33438,32844,b.lastIndex()));

        b.addHouse(262183); inner[5106 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33456,32838,33462,32841,b.lastIndex()));

        b.addHouse(262184); inner[5107 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33385,32845,33392,32848,b.lastIndex()));
        rects.add(new Rect(33390,32849,33392,32851,b.lastIndex()));

        b.addHouse(262185); inner[5108 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33399,32859,33405,32861,b.lastIndex()));
        rects.add(new Rect(33402,32862,33405,32866,b.lastIndex()));

        b.addHouse(262186); inner[5109 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33414,32850,33417,32856,b.lastIndex()));

        b.addHouse(262187); inner[5110 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33372,32867,33384,32873,b.lastIndex()));

        b.addHouse(262188); inner[5111 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33425,32865,33437,32871,b.lastIndex()));

        b.addHouse(262189); inner[5112 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33443,32869,33449,32871,b.lastIndex()));
        rects.add(new Rect(33446,32872,33449,32876,b.lastIndex()));

        // HEINE 327681~327691 (inner 5113~5123)
        b.addHouse(327681); inner[5113 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33599,33213,33601,33214,b.lastIndex()));
        rects.add(new Rect(33602,33213,33610,33218,b.lastIndex()));

        b.addHouse(327682); inner[5114 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33627,33206,33632,33209,b.lastIndex()));

        b.addHouse(327683); inner[5115 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33626,33225,33627,33227,b.lastIndex()));
        rects.add(new Rect(33628,33221,33632,33230,b.lastIndex()));

        b.addHouse(327684); inner[5116 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33628,33241,33636,33244,b.lastIndex()));
        rects.add(new Rect(33632,33245,33635,33250,b.lastIndex()));
        rects.add(new Rect(33634,33251,33634,33252,b.lastIndex()));

        b.addHouse(327685); inner[5117 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33616,33262,33621,33265,b.lastIndex()));

        b.addHouse(327686); inner[5118 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33570,33228,33580,33232,b.lastIndex()));
        rects.add(new Rect(33574,33233,33576,33234,b.lastIndex()));

        b.addHouse(327687); inner[5119 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33583,33305,33588,33314,b.lastIndex()));
        rects.add(new Rect(33587,33315,33588,33316,b.lastIndex()));

        b.addHouse(327688); inner[5120 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33577,33337,33578,33337,b.lastIndex()));
        rects.add(new Rect(33579,33335,33588,33339,b.lastIndex()));
        rects.add(new Rect(33585,33340,33588,33343,b.lastIndex()));

        b.addHouse(327689); inner[5121 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33615,33374,33623,33377,b.lastIndex()));
        rects.add(new Rect(33619,33378,33622,33383,b.lastIndex()));
        rects.add(new Rect(33621,33384,33621,33385,b.lastIndex()));

        b.addHouse(327690); inner[5122 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33624,33397,33625,33399,b.lastIndex()));
        rects.add(new Rect(33626,33393,33630,33403,b.lastIndex()));

        b.addHouse(327691); inner[5123 - INNER_MAP_MIN] = b.lastIndex();
        rects.add(new Rect(33621,33444,33622,33444,b.lastIndex()));
        rects.add(new Rect(33622,33442,33632,33446,b.lastIndex()));
        rects.add(new Rect(33629,33447,33632,33450,b.lastIndex()));

        // GLUDIN 524289~524294
        b.addHouse(524289); rects.add(new Rect(32559,32669,32566,32676,b.lastIndex()));
        b.addHouse(524290); rects.add(new Rect(32548,32705,32556,32716,b.lastIndex())); rects.add(new Rect(32547,32710,32547,32716,b.lastIndex()));
        b.addHouse(524291); rects.add(new Rect(32537,32781,32544,32791,b.lastIndex()));
        b.addHouse(524292); rects.add(new Rect(32550,32780,32560,32787,b.lastIndex()));
        b.addHouse(524293); rects.add(new Rect(32535,32807,32543,32818,b.lastIndex())); rects.add(new Rect(32534,32812,32534,32818,b.lastIndex()));
        b.addHouse(524294); rects.add(new Rect(32553,32814,32560,32821,b.lastIndex()));

        // map4 타일 범위 계산
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            if (r.x1 < minX) minX = r.x1;
            if (r.y1 < minY) minY = r.y1;
            if (r.x2 > maxX) maxX = r.x2;
            if (r.y2 > maxY) maxY = r.y2;
        }

        if (minX == Integer.MAX_VALUE) {
            // 데이터가 없으면 빈 캐시
            return new Cache(inner, b.buildIndexToHouseId(), 0, 0, 0, 0, new short[0]);
        }

        final int w = (maxX - minX) + 1;
        final int h = (maxY - minY) + 1;
        final long sizeL = (long) w * (long) h;
        if (sizeL <= 0 || sizeL > Integer.MAX_VALUE) {
            // 안전장치
            return new Cache(inner, b.buildIndexToHouseId(), 0, 0, 0, 0, new short[0]);
        }

        final short[] tile = new short[(int) sizeL];

        //  오버랩 검증: 빌드 때만 실행
        if (VERIFY_NO_OVERLAP_ON_BUILD) {
            fillTilesWithOverlapCheck(tile, minX, minY, w, rects);
        } else {
            fillTiles(tile, minX, minY, w, rects);
        }

        return new Cache(inner, b.buildIndexToHouseId(), minX, minY, w, h, tile);
    }

    private static void fillTiles(short[] tile, int baseX, int baseY, int width, ArrayList<Rect> rects) {
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);

            int x1 = r.x1 - baseX;
            int x2 = r.x2 - baseX;
            int y1 = r.y1 - baseY;
            int y2 = r.y2 - baseY;

            short idx = r.houseIndex;

            for (int y = y1; y <= y2; y++) {
                int row = y * width;
                int p = row + x1;
                int end = row + x2;
                for (; p <= end; p++) {
                    tile[p] = idx;
                }
            }
        }
    }

    // 빌드 시 오버랩(겹침) 검사: 원본 데이터가 깨졌을 때 조기 폭발시켜 “버그 0”에 도움
    private static void fillTilesWithOverlapCheck(short[] tile, int baseX, int baseY, int width, ArrayList<Rect> rects) {
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);

            int x1 = r.x1 - baseX;
            int x2 = r.x2 - baseX;
            int y1 = r.y1 - baseY;
            int y2 = r.y2 - baseY;

            short idx = r.houseIndex;

            for (int y = y1; y <= y2; y++) {
                int row = y * width;
                int p = row + x1;
                int end = row + x2;
                for (; p <= end; p++) {
                    short prev = tile[p];
                    if (prev != 0 && prev != idx) {
                        throw new IllegalStateException("House tile overlap detected: prevIndex=" + prev + ", newIndex=" + idx);
                    }
                    tile[p] = idx;
                }
            }
        }
    }

    // houseIndex / houseId 테이블 생성기
    private static final class Builder {
        private int[] idxToHouse; // 0은 없음
        private short last;

        Builder(int capHint) {
            int cap = 1;
            while (cap < capHint) cap <<= 1;
            idxToHouse = new int[Math.max(64, cap)];
            idxToHouse[0] = 0;
            last = 0;
        }

        void addHouse(int houseId) {
            short next = (short) (last + 1);
            ensure(next + 1);
            idxToHouse[next] = houseId;
            last = next;
        }

        short lastIndex() {
            return last;
        }

        int[] buildIndexToHouseId() {
            int size = (int) last + 1;
            int[] out = new int[size];
            System.arraycopy(idxToHouse, 0, out, 0, size);
            return out;
        }

        private void ensure(int need) {
            if (need <= idxToHouse.length) return;
            int n = idxToHouse.length;
            while (n < need) n <<= 1;
            int[] nn = new int[n];
            System.arraycopy(idxToHouse, 0, nn, 0, idxToHouse.length);
            idxToHouse = nn;
        }
    }
}
