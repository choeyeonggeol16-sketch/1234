package l1j.server.server.model.network.core.cs.packet;

import l1j.server.IndunSystem.MiniGame.L1Gambling;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.MJINNSystem.Loader.MJINNHelperLoader;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1AuctionBoardInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.npc.L1NpcHtml;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.Packet.SC_MAP_TELEPORT_INFO_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_AuctionBoard;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

public final class CS_NpcTalk {


    private static final byte[] EMPTY_BYTES =
            new byte[0];

    /*
     * 지역별 텔레포트 텍스트.
     */
    private static final String[] ACTION_NAMES = {
            "T_talk island",
            "T_gludio",
            "T_orc",
            "T_woodbec",
            "T_silver knight",
            "T_kent",
            "T_giran",
            "T_heine",
            "T_werldern",
            "T_oren",
            "T_aden",
            "T_scave",
            "T_behemoth",
            "T_silveria",
            "T_pcbang",

            "D_talk island",
            "D_gludio",
            "D_elven",
            "D_training",
            "D_barlog",
            "D_dragon valley",
            "D_eva king",
            "D_ivory tower",
            "D_yahee",

            "F_shelob",
            "F_orc forest",
            "F_ruin of death",
            "F_desert",
            "F_dragon valley",
            "F_halpas",
            "F_valakas",
            "F_jungle",
            "F_heine",
            "F_mirror",
            "F_elmor",
            "F_lindvior",
            "F_giant",
            "F_orenwall",

            "D_eva kingdom"
    };

    private static final int[] COST_9000 =
            new int[39];

    private static final int[] COST_50015 = {
            0, 1175, 1481, 1458, 1884, 1461, 1727, 2123, 2087,
            2470, 2429, 2024, 2107, 2470, 0, 140, 1343, 1717,
            1905, 1717, 1945, 2328, 2532, 2663, 47, 1447, 1327,
            1647, 1799, 1662, 2085, 1928, 2060, 2251, 2247,
            2155, 2493, 539, 735
    };

    private static final int[] COST_50024 = {
            1175, 0, 321, 283, 709, 298, 552, 948, 927,
            1310, 1254, 864, 947, 1405, 0, 1315, 169, 557,
            731, 542, 785, 1153, 1372, 1503, 1207, 287, 167,
            473, 639, 487, 925, 768, 885, 1077, 1087, 981,
            1318, 539, 735
    };

    private static final int[] COST_50082 = {
            1481, 321, 0, 567, 845, 421, 687, 1083, 671,
            989, 1389, 543, 627, 1085, 0, 1554, 340, 237,
            866, 677, 464, 1289, 1051, 1182, 1527, 76, 153,
            608, 328, 623, 605, 874, 1021, 1212, 979, 1116,
            1453, 539, 735
    };

    private static final int[] COST_50054 = {
            1458, 283, 567, 0, 426, 544, 794, 665, 1173,
            1556, 971, 1110, 1193, 1651, 0, 1598, 227, 803,
            447, 259, 1031, 870, 1618, 1749, 1411, 533, 413,
            189, 885, 453, 1171, 1014, 602, 793, 1333, 1147,
            1137, 539, 735
    };

    private static final int[] COST_50056 = {
            1884, 709, 845, 426, 0, 423, 629, 447, 1008,
            1391, 590, 945, 1029, 1487, 0, 2024, 541, 832,
            59, 266, 866, 444, 1453, 1584, 1837, 921, 735,
            237, 721, 289, 1007, 849, 224, 471, 1169, 982,
            973, 539, 735
    };

    private static final int[] COST_50020 = {
            1461, 298, 421, 544, 423, 0, 266, 662, 629,
            1012, 968, 566, 649, 1107, 0, 1601, 317, 409,
            445, 645, 487, 867, 1074, 1205, 1505, 497, 312,
            440, 341, 201, 627, 470, 599, 791, 789, 695,
            1032, 539, 735
    };

    private static final int[] COST_50036 = {
            1727, 552, 687, 794, 629, 266, 0, 396, 379,
            762, 702, 380, 399, 857, 0, 1867, 567, 675,
            571, 895, 275, 601, 824, 855, 1755, 763, 578,
            690, 359, 341, 377, 220, 405, 525, 539, 429,
            766, 539, 735
    };

    private static final int[] COST_50066 = {
            2123, 948, 1083, 665, 447, 662, 396, 0, 561,
            945, 306, 776, 582, 1040, 0, 2263, 779, 1071,
            388, 713, 671, 205, 1007, 1137, 2076, 1159, 974,
            507, 755, 461, 560, 403, 223, 129, 772, 535,
            526, 539, 735
    };

    private static final int[] COST_50039 = {
            2087, 927, 671, 1173, 1008, 629, 379, 561, 0,
            383, 718, 364, 111, 479, 0, 2160, 946, 659,
            949, 1274, 259, 725, 445, 576, 2133, 747, 759,
            1069, 343, 719, 123, 203, 784, 541, 307, 445,
            782, 539, 735
    };

    private static final int[] COST_50051 = {
            2470, 1310, 989, 1556, 1391, 1012, 762, 945, 383,
            0, 801, 453, 363, 95, 0, 2543, 1329, 753,
            133, 1657, 525, 1109, 91, 193, 2517, 1023, 1143,
            1452, 671, 1103, 385, 542, 1167, 920, 223, 409,
            693, 539, 735
    };

    private static final int[] COST_50044 = {
            2429, 1254, 1389, 971, 590, 968, 702, 306, 718,
            801, 0, 1082, 829, 897, 0, 2569, 1085, 1377,
            531, 856, 977, 307, 863, 994, 2382, 1465, 1280,
            781, 1061, 767, 841, 515, 369, 177, 579, 392,
            383, 539, 735
    };

    private static final int[] COST_50079 = {
            2024, 864, 543, 1110, 945, 566, 380, 776, 364,
            453, 1082, 0, 253, 541, 0, 2097, 883, 307,
            887, 1211, 105, 971, 508, 639, 2071, 577, 697,
            1006, 225, 657, 241, 567, 721, 905, 671, 809,
            1146, 539, 735
    };

    private static final int[] COST_3000005 = {
            2107, 947, 627, 1193, 1029, 649, 399, 582, 111,
            363, 829, 253, 0, 458, 0, 2181, 967, 547,
            970, 1295, 163, 746, 425, 555, 2154, 660, 780,
            1089, 308, 740, 22, 314, 805, 652, 419, 556,
            893, 539, 735
    };

    private static final int[] COST_3100005 = {
            2565, 1405, 1085, 1651, 1487, 1107, 857, 1040, 479,
            95, 897, 541, 458, 0, 0, 2639, 1425, 848,
            1428, 1753, 621, 1204, 37, 129, 2612, 1118, 1238,
            1547, 766, 1198, 480, 637, 1263, 1015, 318, 505,
            747, 539, 735
    };

    private CS_NpcTalk() {
    }

    public static void handle(
            CIOClient client,
            int objId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        final L1Object obj =L1World.getInstance().findObject(objId);

        if (!(obj instanceof L1NpcInstance)) {
            return;
        }

        final L1NpcInstance npc =
                (L1NpcInstance) obj;

        final int npcId =
                npc.getNpcTemplate()
                        .get_npcId();

        handleTargetEffect(
                pc,
                npc);

        if (MJINNHelperLoader
                .getInstance()
                .onTalk(npc, pc)) {

            return;
        }

        handleSpecialNpcActions(
                pc,
                npcId,
                objId);

        /*
         * 경매장.
         */
        if (obj instanceof L1AuctionBoardInstance) {

            SC_AuctionBoard.sendAsync(pc, npc);

            return;
        }

        /*
         * NpcActionTable.
         */
        final L1NpcAction action =
                NpcActionTable
                        .getInstance()
                        .get(pc, obj);

        if (action != null) {

            final L1NpcHtml html =
                    action.execute(
                            "",
                            pc,
                            obj,
                            EMPTY_BYTES);

            if (html != null) {

                SC_NPCTalkReturn.send(pc, obj.getId(), html);
            }

            return;
        }

        if (npcId == 400064) {

            new L1Gambling()
                    .dealerTrade(pc);
        }

        /*
         * 기본 NPC 대화.
         */
        obj.onTalkAction(pc);
    }

    private static void handleTargetEffect(
            L1PcInstance pc,
            L1NpcInstance npc) {

        if (!pc.isOnTargetEffect()) {
            return;
        }

        final long now =
                System.currentTimeMillis();

        if (now - pc.getLastNpcClickMs()
                <= 1000L) {

            return;
        }

        pc.setLastNpcClickMs(
                now);

        SC_SkillSound.send(pc, npc.getId(), 14486);
    }

    private static void handleSpecialNpcActions(
            L1PcInstance pc,
            int npcId,
            int objId) {

        final int[] costs =
                teleportCosts(npcId);

        if (costs != null) {

            if (npcId == 50020
                    || npcId == 50036
                    || npcId == 5091) {

                SC_SkillSound.send(pc, pc.getId(), 608);
            }

            SC_MAP_TELEPORT_INFO_NOTI.sendCreate(pc, objId, ACTION_NAMES, costs, ACTION_NAMES.length);

            return;
        }

        final int soundId =
                npcSoundId(npcId);

        if (soundId > 0) {

            SC_SkillSound.send(pc, pc.getId(), soundId);
        }
    }

    /*
     * HashMap<Integer, int[]> 대신 primitive switch.
     *
     * lookup allocation 없음.
     * hashing 없음.
     * containsKey + get 중복 조회 없음.
     */
    private static int[] teleportCosts(
            int npcId) {

        switch (npcId) {

        case 9000:
            return COST_9000;

        case 50015:
            return COST_50015;

        case 50024:
            return COST_50024;

        case 50082:
        case 7320051:
            return COST_50082;

        case 50054:
            return COST_50054;

        case 50056:
            return COST_50056;

        case 50020:
            return COST_50020;

        case 50036:
        case 5069:
        case 5091:
            return COST_50036;

        case 50066:
            return COST_50066;

        case 50039:
            return COST_50039;

        case 50051:
            return COST_50051;

        case 50044:
        case 50046:
            return COST_50044;

        case 50079:
            return COST_50079;

        case 3000005:
            return COST_3000005;

        case 3100005:
            return COST_3100005;

        default:
            return null;
        }
    }

    private static int npcSoundId(
            int npcId) {

        switch (npcId) {

        case 9271:
        case 7000020:
            return 608;

        case 70097:
        case 70030:
            return 598;

        case 460000364:
        case 7700075:
            return 612;

        case 5922026:
        case 81005:
        case 70701:
            return 617;

        case 5922021:
            return 613;

        case 5922020:
        case 605:
        case 460000359:
        case 70662:
        case 1059:
            return 614;

        case 5922201:
            return 618;

        case 60008:
            return 611;

        case 70094:
            return 628;

        case 70022:
        case 70011:
            return 629;

        case 70826:
            return 630;

        case 70614:
            return 619;

        case 9291:
            return 616;

        case 5922001:
            return 621;

        case 70064:
            return 620;

        case 460000368:
            return 632;

        case 700076:
        case 70841:
            return 622;

        case 70852:
            return 625;

        case 70838:
            return 626;

        case 70840:
            return 627;

        case 700074:
            return 623;

        default:
            return -1;
        }
    }
}