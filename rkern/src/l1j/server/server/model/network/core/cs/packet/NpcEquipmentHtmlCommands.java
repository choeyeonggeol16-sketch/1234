package l1j.server.server.model.network.core.cs.packet;

import static l1j.server.server.model.network.core.cs.packet.HtmlCmdHandler._random;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** Equipment exchanges, crafting and their NPC dialogue. */
final class NpcEquipmentHtmlCommands {
    private NpcEquipmentHtmlCommands() {}

    static void handle(CUserSocket client, L1PcInstance pc, L1Object obj, int objid,
            String s, String s2, String htmlid, int templateId) throws Exception {
        int[] materials = null;
        int[] counts = null;
        int[] createitem = null;
        int[] createcount = null;

        if (templateId == 71126) {
            /*int TeleportType = 0, TeleportX = 0, TeleportY = 0, TeleportMap = 0;
            if (s.equals("0")) { // 중앙광장 외각 부근 이동
                TeleportType = 1;
                TeleportX = 32746;
                TeleportY = 32764;
                TeleportMap = 479;
            } else if (s.equals("1")) { // 명법군 입구 부근 이동
                TeleportType = 1;
                TeleportX = 32788;
                TeleportY = 32682;
                TeleportMap = 479;
            } else if (s.equals("2")) { // 마령군 입구 부근 이동
                TeleportType = 1;
                TeleportX = 32922;
                TeleportY = 32701;
                TeleportMap = 479;
            } else if (s.equals("3")) { // 마수군 입구 부근 이동
                TeleportType = 1;
                TeleportX = 32910;
                TeleportY = 32822;
                TeleportMap = 479;
            } else if (s.equals("4")) { // 암살군 입구 부근 이동
                TeleportType = 1;
                TeleportX = 32739;
                TeleportY = 32843;
                TeleportMap = 479;
            } else*/ if (s.equalsIgnoreCase("B")) {
                if (pc.getInventory().checkItem(41007, 1)) {
                    htmlid = "eris10";
                } else {
                    NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41007, 1, true, "eris6");
                    return;
                }
            } else if (s.equalsIgnoreCase("C")) {
                if (pc.getInventory().checkItem(41009, 1)) {
                    htmlid = "eris10";
                } else {
                    NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41009, 1, true, "eris8");
                    return;
                }
            } else if (s.equalsIgnoreCase("A")) {
                if (pc.getInventory().checkItem(41007, 1)) {
                    if (pc.getInventory().checkItem(40969, 20)) {
                        htmlid = "eris18";
                        materials = Recipes.RECIPE_VALUES_48;
                        counts = Recipes.RECIPE_VALUES_49;
                        createitem = Recipes.RECIPE_VALUES_50;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                    } else {
                        htmlid = "eris5";
                    }
                } else {
                    htmlid = "eris2";
                }
            } else if (s.equalsIgnoreCase("E")) {
                if (pc.getInventory().checkItem(41010, 1)) {
                    htmlid = "eris19";
                } else {
                    htmlid = "eris7";
                }
            } else if (s.equalsIgnoreCase("D")) {
                if (pc.getInventory().checkItem(41010, 1)) {
                    htmlid = "eris19";
                } else {
                    if (pc.getInventory().checkItem(41009, 1)) {
                        if (pc.getInventory().checkItem(40959, 1)) {
                            htmlid = "eris17";
                            materials = Recipes.RECIPE_VALUES_51;
                            counts = NpcCraftCommands.UnitCounts.TWO_ITEMS;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else if (pc.getInventory().checkItem(40960, 1)) {
                            htmlid = "eris16";
                            materials = Recipes.RECIPE_VALUES_54;
                            counts = NpcCraftCommands.UnitCounts.TWO_ITEMS;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else if (pc.getInventory().checkItem(40961, 1)) {
                            htmlid = "eris15";
                            materials = Recipes.RECIPE_VALUES_55;
                            counts = NpcCraftCommands.UnitCounts.TWO_ITEMS;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else if (pc.getInventory().checkItem(40962, 1)) {
                            htmlid = "eris14";
                            materials = Recipes.RECIPE_VALUES_56;
                            counts = NpcCraftCommands.UnitCounts.TWO_ITEMS;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else if (pc.getInventory().checkItem(40635, 10)) {
                            htmlid = "eris12";
                            materials = Recipes.RECIPE_VALUES_57;
                            counts = Recipes.RECIPE_VALUES_58;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else if (pc.getInventory().checkItem(40638, 10)) {
                            htmlid = "eris11";
                            materials = Recipes.RECIPE_VALUES_59;
                            counts = Recipes.RECIPE_VALUES_58;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else if (pc.getInventory().checkItem(40642, 10)) {
                            htmlid = "eris13";
                            materials = Recipes.RECIPE_VALUES_60;
                            counts = Recipes.RECIPE_VALUES_58;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else if (pc.getInventory().checkItem(40667, 10)) {
                            htmlid = "eris13";
                            materials = Recipes.RECIPE_VALUES_61;
                            counts = Recipes.RECIPE_VALUES_58;
                            createitem = Recipes.RECIPE_VALUES_53;
                            createcount = NpcCraftCommands.UnitCounts.ONE;
                        } else {
                            htmlid = "eris8";
                        }
                    } else {
                        htmlid = "eris7";
                    }
                }
            }
            /*if (TeleportType == 1) {
                if (pc.getRadungeonTime() >= 120) {
                    SC_ChatPacket.sendDirect(pc, pc, "라스타바드 던전 시간이 만료 되었습니다.");
                    return;
                } else {
                    // L1Teleport.teleport(pc, TeleportX, TeleportY, (short)
                    // TeleportMap, 0, true);
                    pc.start_teleport(TeleportX, TeleportY, TeleportMap, 5, 169, true, false);
                }
            }*/

            // 넘어진 항해사
        } else if (templateId == 80076) {
            if (s.equalsIgnoreCase("A")) {
                final int variant = _random.nextInt(2);
                NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj,
                        49082 + variant, 1, false, variant == 0 ? "voyager6a" : "voyager6b");
                return;
            }
            // 연금 술사 페리타
        } else if (templateId == 71128) {
            if (s.equals("A")) {
                if (pc.getInventory().checkItem(41010, 1)) { // 이리스의 추천서
                    htmlid = "perita2";
                } else {
                    htmlid = "perita3";
                }
            } else if (s.equals("p")) {
                // 저주해진 블랙 귀 링 판별
                if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(40988, 1) // 나이트 클래스
                        && pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
                    htmlid = "perita43";
                } else if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
                    htmlid = "perita44";
                } else if (pc.getInventory().checkItem(40987, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(40988, 1)) { // 나이트 클래스
                    htmlid = "perita45";
                } else if (pc.getInventory().checkItem(40988, 1) // 나이트 클래스
                        && pc.getInventory().checkItem(40989, 1)) { // 워리아크라스
                    htmlid = "perita47";
                } else if (pc.getInventory().checkItem(40987, 1)) { // 위저드 클래스
                    htmlid = "perita46";
                } else if (pc.getInventory().checkItem(40988, 1)) { // 나이트 클래스
                    htmlid = "perita49";
                } else if (pc.getInventory().checkItem(40987, 1)) { // 워리아크라스
                    htmlid = "perita48";
                } else {
                    htmlid = "perita50";
                }
            } else if (s.equals("q")) {
                // 블랙 귀 링 판별
                if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(41174, 1) // 나이트 클래스
                        && pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
                    htmlid = "perita54";
                } else if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
                    htmlid = "perita55";
                } else if (pc.getInventory().checkItem(41173, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(41174, 1)) { // 나이트 클래스
                    htmlid = "perita56";
                } else if (pc.getInventory().checkItem(41174, 1) // 나이트 클래스
                        && pc.getInventory().checkItem(41175, 1)) { // 워리아크라스
                    htmlid = "perita58";
                } else if (pc.getInventory().checkItem(41174, 1)) { // 위저드 클래스
                    htmlid = "perita57";
                } else if (pc.getInventory().checkItem(41175, 1)) { // 나이트 클래스
                    htmlid = "perita60";
                } else if (pc.getInventory().checkItem(41176, 1)) { // 워리아크라스
                    htmlid = "perita59";
                } else {
                    htmlid = "perita61";
                }
            } else if (s.equals("s")) {
                // 신비적인 블랙 귀 링 판별
                if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(41162, 1) // 나이트 클래스
                        && pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
                    htmlid = "perita62";
                } else if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
                    htmlid = "perita63";
                } else if (pc.getInventory().checkItem(41161, 1) // 위저드 클래스
                        && pc.getInventory().checkItem(41162, 1)) { // 나이트 클래스
                    htmlid = "perita64";
                } else if (pc.getInventory().checkItem(41162, 1) // 나이트 클래스
                        && pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
                    htmlid = "perita66";
                } else if (pc.getInventory().checkItem(41161, 1)) { // 위저드 클래스
                    htmlid = "perita65";
                } else if (pc.getInventory().checkItem(41162, 1)) { // 나이트 클래스
                    htmlid = "perita68";
                } else if (pc.getInventory().checkItem(41163, 1)) { // 워리아크라스
                    htmlid = "perita67";
                } else {
                    htmlid = "perita69";
                }
            } else if (s.equals("B")) {
                // 정화의 일부
                if (pc.getInventory().checkItem(40651, 10) // 불의 숨결
                        && pc.getInventory().checkItem(40643, 10) // 수의 숨결
                        && pc.getInventory().checkItem(40618, 10) // 대지의 숨결
                        && pc.getInventory().checkItem(40645, 10) // 돌풍이 심함 취
                        && pc.getInventory().checkItem(40676, 10) // 어둠의 숨결
                        && pc.getInventory().checkItem(40442, 5) // 프롭브의 위액
                        && pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
                    htmlid = "perita7";
                    materials = Recipes.RECIPE_VALUES_62;
                    counts = Recipes.RECIPE_VALUES_63;
                    createitem = Recipes.RECIPE_VALUES_64; // 정화의 일부
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita8";
                }
            } else if (s.equals("G") || s.equals("h") || s.equals("i")) {
                // 신비적인 일부：1 단계
                if (pc.getInventory().checkItem(40651, 5) // 불의 숨결
                        && pc.getInventory().checkItem(40643, 5) // 수의 숨결
                        && pc.getInventory().checkItem(40618, 5) // 대지의 숨결
                        && pc.getInventory().checkItem(40645, 5) // 돌풍이 심함 취
                        && pc.getInventory().checkItem(40676, 5) // 어둠의 숨결
                        && pc.getInventory().checkItem(40675, 5) // 어둠의 광석
                        && pc.getInventory().checkItem(40049, 3) // 고급 루비
                        && pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
                    htmlid = "perita27";
                    materials = Recipes.RECIPE_VALUES_65;
                    counts = Recipes.RECIPE_VALUES_66;
                    createitem = Recipes.RECIPE_VALUES_67; // 신비적인 일부：1 단계
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita28";
                }
            } else if (s.equals("H") || s.equals("j") || s.equals("k")) {
                // 신비적인 일부：2 단계
                if (pc.getInventory().checkItem(40651, 10) // 불의 숨결
                        && pc.getInventory().checkItem(40643, 10) // 수의 숨결
                        && pc.getInventory().checkItem(40618, 10) // 대지의 숨결
                        && pc.getInventory().checkItem(40645, 10) // 돌풍이 심함 취
                        && pc.getInventory().checkItem(40676, 20) // 어둠의 숨결
                        && pc.getInventory().checkItem(40675, 10) // 어둠의 광석
                        && pc.getInventory().checkItem(40048, 3) // 고급 다이아몬드
                        && pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
                    htmlid = "perita29";
                    materials = Recipes.RECIPE_VALUES_68;
                    counts = Recipes.RECIPE_VALUES_69;
                    createitem = Recipes.RECIPE_VALUES_70; // 신비적인 일부：2 단계
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita30";
                }
            } else if (s.equals("I") || s.equals("l") || s.equals("m")) {
                // 신비적인 일부：3 단계
                if (pc.getInventory().checkItem(40651, 20) // 불의 숨결
                        && pc.getInventory().checkItem(40643, 20) // 수의 숨결
                        && pc.getInventory().checkItem(40618, 20) // 대지의 숨결
                        && pc.getInventory().checkItem(40645, 20) // 돌풍이 심함 취
                        && pc.getInventory().checkItem(40676, 30) // 어둠의 숨결
                        && pc.getInventory().checkItem(40675, 10) // 어둠의 광석
                        && pc.getInventory().checkItem(40050, 3) // 고급 사파이어
                        && pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
                    htmlid = "perita31";
                    materials = Recipes.RECIPE_VALUES_71;
                    counts = Recipes.RECIPE_VALUES_72;
                    createitem = Recipes.RECIPE_VALUES_73; // 신비적인 일부：3 단계
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita32";
                }
            } else if (s.equals("J") || s.equals("n") || s.equals("o")) {
                // 신비적인 일부：4 단계
                if (pc.getInventory().checkItem(40651, 30) // 불의 숨결
                        && pc.getInventory().checkItem(40643, 30) // 수의 숨결
                        && pc.getInventory().checkItem(40618, 30) // 대지의 숨결
                        && pc.getInventory().checkItem(40645, 30) // 돌풍이 심함 취
                        && pc.getInventory().checkItem(40676, 30) // 어둠의 숨결
                        && pc.getInventory().checkItem(40675, 20) // 어둠의 광석
                        && pc.getInventory().checkItem(40052, 1) // 최고급 다이아몬드
                        && pc.getInventory().checkItem(40051, 1)) { // 고급 에메랄드
                    htmlid = "perita33";
                    materials = Recipes.RECIPE_VALUES_74;
                    counts = Recipes.RECIPE_VALUES_75;
                    createitem = Recipes.RECIPE_VALUES_73; // 신비적인 일부：4 단계
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita34";
                }
            } else if (s.equals("K")) { // 1 단계 귀 링(영혼의 귀 링)
                if (pc.getInventory().checkEquipped(21014) || pc.getInventory().checkEquipped(21006)
                        || pc.getInventory().checkEquipped(21007)) {
                    htmlid = "perita36";
                } else if (pc.getInventory().checkItem(21014, 1)) { // 위저드 클래스
                    materials = EarringRecipes.WIZARD_INPUT;
                    createitem = EarringRecipes.WIZARD_REWARD;
                } else if (pc.getInventory().checkItem(21006, 1)) { // 나이트 클래스
                    materials = EarringRecipes.KNIGHT_INPUT;
                    createitem = EarringRecipes.KNIGHT_REWARD;
                } else if (pc.getInventory().checkItem(21007, 1)) { // 워리아크라스
                    materials = EarringRecipes.WARRIOR_INPUT;
                    createitem = EarringRecipes.WARRIOR_REWARD;
                } else {
                    htmlid = "perita36";
                }
                if (materials != null) {
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                }
            } else if (s.equals("L")) { // 2 단계 귀 링(지혜의 귀 링)
                if (pc.getInventory().checkEquipped(21015)) {
                    htmlid = "perita22";
                } else if (pc.getInventory().checkItem(21015, 1)) {
                    materials = Recipes.RECIPE_VALUES_76;
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createitem = Recipes.RECIPE_VALUES_77;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita22";
                }
            } else if (s.equals("M")) { // 3 단계 귀 링(진실의 귀 링)
                if (pc.getInventory().checkEquipped(21016)) {
                    htmlid = "perita26";
                } else if (pc.getInventory().checkItem(21016, 1)) {
                    materials = Recipes.RECIPE_VALUES_78;
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createitem = Recipes.RECIPE_VALUES_79;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita26";
                }
            } else if (s.equals("b")) { // 2 단계 귀 링(정열의 귀 링)
                if (pc.getInventory().checkEquipped(21009)) {
                    htmlid = "perita39";
                } else if (pc.getInventory().checkItem(21009, 1)) {
                    materials = Recipes.RECIPE_VALUES_80;
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createitem = Recipes.RECIPE_VALUES_81;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita39";
                }
            } else if (s.equals("d")) { // 3 단계 귀 링(명예의 귀 링)
                if (pc.getInventory().checkEquipped(21012)) {
                    htmlid = "perita41";
                } else if (pc.getInventory().checkItem(21012, 1)) {
                    materials = Recipes.RECIPE_VALUES_82;
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createitem = Recipes.RECIPE_VALUES_83;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita41";
                }
            } else if (s.equals("a")) { // 2 단계 귀 링(분노의 귀 링)
                if (pc.getInventory().checkEquipped(21008)) {
                    htmlid = "perita38";
                } else if (pc.getInventory().checkItem(21008, 1)) {
                    materials = Recipes.RECIPE_VALUES_84;
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createitem = Recipes.RECIPE_VALUES_85;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita38";
                }
            } else if (s.equals("c")) { // 3 단계 귀 링(용맹의 귀 링)
                if (pc.getInventory().checkEquipped(21010)) {
                    htmlid = "perita40";
                } else if (pc.getInventory().checkItem(21010, 1)) {
                    materials = Recipes.RECIPE_VALUES_86;
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createitem = Recipes.RECIPE_VALUES_87;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "perita40";
                }
            }

            // 보석 세공인 룸스
        } else if (templateId == 71129) {
            if (s.equals("Z")) {
                htmlid = "rumtis2";
            } else if (s.equals("Y")) {
                if (pc.getInventory().checkItem(41010, 1)) { // 이리스의 추천서
                    htmlid = "rumtis3";
                } else {
                    htmlid = "rumtis4";
                }
            } else if (s.equals("q")) {
                htmlid = "rumtis92";
            } else if (s.equals("A")) {
                if (pc.getInventory().checkItem(41161, 1)) {
                    // 신비적인 블랙 귀 링
                    htmlid = "rumtis6";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("B")) {
                if (pc.getInventory().checkItem(41164, 1)) {
                    // 신비적인 위저드 귀 링
                    htmlid = "rumtis7";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("C")) {
                if (pc.getInventory().checkItem(41167, 1)) {
                    // 신비적인 회색 위저드 귀 링
                    htmlid = "rumtis8";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("T")) {
                if (pc.getInventory().checkItem(41167, 1)) {
                    // 신비적인 화이트 위저드 귀 링
                    htmlid = "rumtis9";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("w")) {
                if (pc.getInventory().checkItem(41162, 1)) {
                    // 신비적인 블랙 귀 링
                    htmlid = "rumtis14";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("x")) {
                if (pc.getInventory().checkItem(41165, 1)) {
                    // 신비적인 나이트 귀 링
                    htmlid = "rumtis15";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("y")) {
                if (pc.getInventory().checkItem(41168, 1)) {
                    // 신비적인 회색 나이트 귀 링
                    htmlid = "rumtis16";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("z")) {
                if (pc.getInventory().checkItem(41171, 1)) {
                    // 신비적인 화이트 나이트 귀 링
                    htmlid = "rumtis17";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("U")) {
                if (pc.getInventory().checkItem(41163, 1)) {
                    // 신비적인 블랙 귀 링
                    htmlid = "rumtis10";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("V")) {
                if (pc.getInventory().checkItem(41166, 1)) {
                    // 미스테리아스워리아이아링
                    htmlid = "rumtis11";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("W")) {
                if (pc.getInventory().checkItem(41169, 1)) {
                    // 미스테리아스그레이워리아이아링
                    htmlid = "rumtis12";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("X")) {
                if (pc.getInventory().checkItem(41172, 1)) {
                    // 미스테리아스화이워리아이아링
                    htmlid = "rumtis13";
                } else {
                    htmlid = "rumtis101";
                }
            } else if (s.equals("D") || s.equals("E") || s.equals("F") || s.equals("G")) {
                final boolean seals = pc.getInventory().checkItem(40959, 1)
                        && pc.getInventory().checkItem(40960, 1)
                        && pc.getInventory().checkItem(40961, 1)
                        && pc.getInventory().checkItem(40962, 1);
                final boolean badges = !seals && pc.getInventory().checkItem(40642, 10)
                        && pc.getInventory().checkItem(40635, 10)
                        && pc.getInventory().checkItem(40638, 10)
                        && pc.getInventory().checkItem(40667, 10);
                if (pc.getInventory().checkItem(40046, 1)
                        && pc.getInventory().checkItem(40618, 5)
                        && pc.getInventory().checkItem(40643, 5)
                        && pc.getInventory().checkItem(40645, 5)
                        && pc.getInventory().checkItem(40651, 5)
                        && pc.getInventory().checkItem(40676, 5)) {
                    if (seals || badges) {
                        htmlid = "rumtis60";
                        materials = seals ? Recipes.RUMTIS_SEAL_MATERIALS : Recipes.RUMTIS_BADGE_MATERIALS;
                        counts = seals ? Recipes.RUMTIS_SEAL_COUNTS : Recipes.RUMTIS_BADGE_COUNTS;
                        createitem = Recipes.RECIPE_VALUES_67;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                    } else {
                        htmlid = "rumtis18";
                    }
                }
            }
        } else {
            NpcEventHtmlCommands.handle(client, pc, obj, objid, s, s2, htmlid, templateId);
            return;
        }
        HtmlCmdHandler.finish(pc, obj, objid, htmlid, materials, counts, createitem, createcount,
                null, null, null);
    }

    private static final class EarringRecipes {
        private static final int[] WIZARD_INPUT = {21014};
        private static final int[] WIZARD_REWARD = {41176};
        private static final int[] KNIGHT_INPUT = {21006};
        private static final int[] KNIGHT_REWARD = {41177};
        private static final int[] WARRIOR_INPUT = {21007};
        private static final int[] WARRIOR_REWARD = {41178};
    }

    private static final class Recipes {
        private static final int[] RUMTIS_SEAL_MATERIALS = {40959,40960,40961,40962,40046,40618,40643,40645,40651,40676};
        private static final int[] RUMTIS_BADGE_MATERIALS = {40642,40635,40638,40667,40046,40618,40643,40645,40651,40676};
        private static final int[] RUMTIS_SEAL_COUNTS = {1,1,1,1,1,5,5,5,5,5};
        private static final int[] RUMTIS_BADGE_COUNTS = {10,10,10,10,1,5,5,5,5,5};
        private static final int[] RECIPE_VALUES_48 = {40969, 41007};
        private static final int[] RECIPE_VALUES_49 = {20, 1};
        private static final int[] RECIPE_VALUES_50 = {41008};
        private static final int[] RECIPE_VALUES_51 = {40959, 41009};
        private static final int[] RECIPE_VALUES_53 = {41010};
        private static final int[] RECIPE_VALUES_54 = {40960, 41009};
        private static final int[] RECIPE_VALUES_55 = {40961, 41009};
        private static final int[] RECIPE_VALUES_56 = {40962, 41009};
        private static final int[] RECIPE_VALUES_57 = {40635, 41009};
        private static final int[] RECIPE_VALUES_58 = {10, 1};
        private static final int[] RECIPE_VALUES_59 = {40638, 41009};
        private static final int[] RECIPE_VALUES_60 = {40642, 41009};
        private static final int[] RECIPE_VALUES_61 = {40667, 41009};
        private static final int[] RECIPE_VALUES_62 = {40651, 40643, 40618, 40645, 40676, 40442, 40051};
        private static final int[] RECIPE_VALUES_63 = {10, 10, 10, 10, 20, 5, 1};
        private static final int[] RECIPE_VALUES_64 = {40925};
        private static final int[] RECIPE_VALUES_65 = {40651, 40643, 40618, 40645, 40676, 40675, 40049, 40051};
        private static final int[] RECIPE_VALUES_66 = {5, 5, 5, 5, 10, 10, 3, 1};
        private static final int[] RECIPE_VALUES_67 = {40926};
        private static final int[] RECIPE_VALUES_68 = {40651, 40643, 40618, 40645, 40676, 40675, 40048, 40051};
        private static final int[] RECIPE_VALUES_69 = {10, 10, 10, 10, 20, 10, 3, 1};
        private static final int[] RECIPE_VALUES_70 = {40927};
        private static final int[] RECIPE_VALUES_71 = {40651, 40643, 40618, 40645, 40676, 40675, 40050, 40051};
        private static final int[] RECIPE_VALUES_72 = {20, 20, 20, 20, 30, 10, 3, 1};
        private static final int[] RECIPE_VALUES_73 = {40928};
        private static final int[] RECIPE_VALUES_74 = {40651, 40643, 40618, 40645, 40676, 40675, 40052, 40051};
        private static final int[] RECIPE_VALUES_75 = {30, 30, 30, 30, 30, 20, 1, 1};
        private static final int[] RECIPE_VALUES_76 = {21015};
        private static final int[] RECIPE_VALUES_77 = {41179};
        private static final int[] RECIPE_VALUES_78 = {21016};
        private static final int[] RECIPE_VALUES_79 = {41182};
        private static final int[] RECIPE_VALUES_80 = {21009};
        private static final int[] RECIPE_VALUES_81 = {41180};
        private static final int[] RECIPE_VALUES_82 = {21012};
        private static final int[] RECIPE_VALUES_83 = {41183};
        private static final int[] RECIPE_VALUES_84 = {21008};
        private static final int[] RECIPE_VALUES_85 = {41181};
        private static final int[] RECIPE_VALUES_86 = {21010};
        private static final int[] RECIPE_VALUES_87 = {41184};
    }
}
