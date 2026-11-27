package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.shared.GameRandom;


import l1j.server.Config;
import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.model.world.instance.dungeon.ice.demon.DemonSystem;
import l1j.server.server.model.world.instance.dungeon.ice.queen.QueenSystem;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.action.listener.ActionListener;
import l1j.server.server.model.character.action.listener.ActionListenerLoader;
import l1j.server.server.model.character.classinfo.EClassesType;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_RaceBoard;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.CommonUtil;

/** Special NPCs, events and the template fallback. */
final class NpcEventHtmlCommands {
    private NpcEventHtmlCommands() {}

    private static final String[] CLASS_LISTENER_COMMANDS = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};

    private static String classListenerCommand(int classId) {
        var locale = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
        String language = locale.getLanguage();
        if (locale.getUnicodeLocaleType("nu") == null
                && (language.isEmpty() || "ko".equals(language) || "en".equals(language)))
            return CLASS_LISTENER_COMMANDS[classId];
        return String.format(locale, "a%d", classId);
    }

    static void handle(CUserSocket client, L1PcInstance pc, L1Object obj, int objid,
            String s, String s2, String htmlid, int templateId) throws Exception {
        int[] materials = null;
        int[] counts = null;
        int[] createitem = null;
        int[] createcount = null;

        if (templateId == 70644 || templateId == 70538
                || templateId == 70560 || templateId == 70667
                || templateId == 70725 || templateId == 70790
                || templateId == 70884) {
                if (s.equalsIgnoreCase("request blood of evil")) {
                    SC_SystemMessage.send(pc, "아직 시기가 아닌 것 같습니다.");
                    htmlid = "";
//						return;
                }
        } else if (templateId == 71119) {
            if (s.equalsIgnoreCase("request las history book")) {
                materials = Recipes.RECIPE_VALUES_88;
                counts = Recipes.RECIPE_VALUES_89;
                createitem = Recipes.RECIPE_VALUES_90;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                htmlid = "";
            }
        } else if (templateId == 71170) {
            if (s.equalsIgnoreCase("request las weapon manual")) {
                materials = Recipes.RECIPE_VALUES_90;
                counts = NpcCraftCommands.UnitCounts.ONE;
                createitem = Recipes.RECIPE_VALUES_91;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                htmlid = "";
            }
        } else if (templateId == 6000015) {
            if (NpcTravelCommands.towerGate(pc, objid, s)) return;
            // 첩보원(욕망의 동굴측)
        } else if (templateId == 80067) {
            if (s.equalsIgnoreCase("n")) {
                NpcQuestTransactions.spyStart(pc, client, (L1NpcInstance) obj, false);
                return;
            } else if (s.equalsIgnoreCase("d")) {
                NpcQuestTransactions.spyDiscard(pc, (L1NpcInstance) obj, false);
                return;
                // 「초기화한다」
            } else if (s.equalsIgnoreCase("k")) {
                NpcQuestTransactions.spyReset(pc, (L1NpcInstance) obj, false);
                return;
            } else if (s.equalsIgnoreCase("e")) {
                NpcQuestTransactions.spyComplete(pc, (L1NpcInstance) obj, false);
                return;
                // 선물을 받는다
            } else if (s.equalsIgnoreCase("g")) {
                NpcQuestTransactions.spyGift(pc, (L1NpcInstance) obj, false);
                return;
            }
            /** 드루가 리뉴얼 **/
        } else if (templateId == 900005) { // 드루가
            if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")
                    || s.equalsIgnoreCase("c") || s.equalsIgnoreCase("d")) {
                if (pc.getInventory().HaveItemByClassId(L1ItemId.DRUGA_POKET)) {
                    SC_NPCTalkReturn.send(pc, objid, "veil3");
                    return;
                }
                final int rewardId = s.equalsIgnoreCase("a") ? 14000000
                        : s.equalsIgnoreCase("b") ? 1400001
                        : s.equalsIgnoreCase("c") ? 1400002 : 1400003;
                NpcQuestTransactions.npcSingleMaterialExchange(pc, (L1NpcInstance) obj,
                        L1ItemId.ADENA, 10000000, rewardId, "veil8", "veil4", null);
                return;
            }

        } else if (templateId == 900017) {// 반쿠
            if (s.equalsIgnoreCase("buy 7")) {
                NpcPetCommands.petbuy(client, 900043, 410045, 1);// 녹색 해츨링 7
            } else if (s.equalsIgnoreCase("buy 8")) {
                NpcPetCommands.petbuy(client, 900044, 410046, 1);// 황색 해츨링 8
            }
            htmlid = "";
        } else if (templateId == 900048) { // 수상한 요리사
            if (s.equalsIgnoreCase("0")) {
                NpcQuestTransactions.npcSingleMaterialExchange(pc, (L1NpcInstance) obj,
                        41921, 45, 410055, null, "suschef5", "$8538");
                return;
            } else if (s.equalsIgnoreCase("1")) {
                htmlid = NpcBuffCommands.featherBuff(pc);
            }

        } else if (templateId == 900000) { // 슈에르메
            L1ItemInstance item = null;
            // 60차: 표식 32개 + 100,000 아데나 교환 4종.
            if (s.equalsIgnoreCase("H")) {
                NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410162, 32, 100000, 410034, "sherme10", false);
                return;
            } else if (s.equalsIgnoreCase("I")) {
                NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410163, 32, 100000, 410032, "sherme10", false);
                return;
            } else if (s.equalsIgnoreCase("J")) {
                NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410164, 32, 100000, 410035, "sherme10", false);
                return;
            } else if (s.equalsIgnoreCase("K")) {
                NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, 410165, 32, 100000, 410033, "sherme10", false);
                return;
            // 61차: 봉인 마안 + 100,000 아데나 교환 4종.
            } else if (s.equalsIgnoreCase("A") || s.equalsIgnoreCase("B")
                    || s.equalsIgnoreCase("C") || s.equalsIgnoreCase("D")) {
                final int materialId = s.equalsIgnoreCase("A") ? 410030
                        : s.equalsIgnoreCase("B") ? 410028
                        : s.equalsIgnoreCase("C") ? 410031 : 410029;
                final int rewardId = s.equalsIgnoreCase("A") ? 410034
                        : s.equalsIgnoreCase("B") ? 410032
                        : s.equalsIgnoreCase("C") ? 410035 : 410033;
                if (pc.getInventory().HaveItemByClassId(rewardId)) {
                    SC_NPCTalkReturn.send(pc, objid, "sherme0");
                    return;
                }
                NpcQuestTransactions.dragonEyeExchange(pc, (L1NpcInstance) obj, materialId, 1, 100000, rewardId, "sherme1", true);
                return;
                } else if (s.equalsIgnoreCase("E")) {
                NpcQuestTransactions.dragonEyeUpgrade(pc, (L1NpcInstance) obj, 410034, 410032, 410036, 40);
                return;
            } else if (s.equalsIgnoreCase("F")) {
                NpcQuestTransactions.dragonEyeUpgrade(pc, (L1NpcInstance) obj, 410036, 410033, 410037, 30);
                return;
            } else if (s.equalsIgnoreCase("G")) {
                NpcQuestTransactions.dragonEyeUpgrade(pc, (L1NpcInstance) obj, 410037, 410035, 410038, 20);
                return;
            }
        } else if (templateId == 900105) { // 마에노브
            // Nine floor tickets, same item IDs/coordinates as the original branches.
            final int floor = s.length() == 1 ? Character.toUpperCase(s.charAt(0)) - 'A' : -1;
            if (floor >= 0 && floor <= 8) {
                final int x = floor == 0 || floor == 2 || floor == 3 ? 32744
                        : floor == 1 ? 32745 : floor == 5 ? 32737 : 32738;
                final int y = floor <= 1 ? 32862 : floor <= 3 ? 32863
                        : floor == 5 ? 32797 : 32798;
                NpcQuestTransactions.npcPaidFloorTeleport(pc, (L1NpcInstance) obj, 40104 + floor,
                        x, y, 116 + floor * 10);
                return;
            }
        } else if (templateId >= 900090
                && templateId <= 900098) { // 오만의탑
                                                                                    // 리뉴얼
                                                                                    // 마에노브의불꽃
                                                                                    // 16
                                                                                    // ~
                                                                                    // 96
            if (s.equalsIgnoreCase("A")) {
                NpcTravelCommands.towerUpperFloor(pc, objid, ((L1NpcInstance) obj).getNpcId());
                return;
            }

        } else if (templateId == 900143) { // 말섬 인던 보조원
            if (s.equalsIgnoreCase("a")) {
                // L1Teleport.teleport(pc, 32583, 32924, (short) 0, 5, true);
                pc.start_teleport(32583, 32924, 0, 5, 169, true, false);
                htmlid = "";
            }
        } else if (templateId == 70009) { // 게렝
            htmlid = NpcExperienceCommands.게렝(pc, s);
            // 클라우디아 수정요청 //엔피시 번호확인하세요
        } else if (templateId == 900146) { // 휴그린트
            if (s.equalsIgnoreCase("0")) htmlid = NpcExchangeCommands.hugrint(pc, objid);
        } else if (templateId == 70842) { // 마르바
            htmlid = NpcQuestTransactions.마르바(pc, obj, s);
        } else if (templateId == 70840) { //로빈후드
            NpcQuestTransactions.moonbow(pc, (L1NpcInstance) obj, s, false);
            return;
        } else if (templateId == 600005) {// 지브릴
            NpcQuestTransactions.moonbow(pc, (L1NpcInstance) obj, s, true);
            return;
        } else if (templateId == 71179) { // 디에츠(빛고목제작)
            if (s.equalsIgnoreCase("A")) {// 복원된 고대의 목걸이
                if (pc.getInventory().HaveItemByClassId(49028, 1) && pc.getInventory().HaveItemByClassId(49029, 1)
                        && pc.getInventory().HaveItemByClassId(49030, 1) && pc.getInventory().HaveItemByClassId(41139, 1)) { // 보석과
                                                                                                                // 볼품없는
                    // 목걸이 확인
                    if (GameRandom.nextInt(10) > 6) {
                        materials = Recipes.RECIPE_VALUES_92;
                        counts = Recipes.RECIPE_VALUES_93;
                        createitem = Recipes.RECIPE_VALUES_94; // 복원된 고대의 목걸이
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        htmlid = "dh8";
                    } else { // 실패의 경우 아이템만 사라짐
                        materials = Recipes.RECIPE_VALUES_92;
                        counts = Recipes.RECIPE_VALUES_93;
                        createitem = Recipes.RECIPE_VALUES_95; // 보석 가루
                        createcount = Recipes.RECIPE_VALUES_96;
                        htmlid = "dh7";
                    }
                } else { // 재료가 부족한 경우
                    htmlid = "dh6";
                }
            } else if (s.equalsIgnoreCase("B")) {// 빛나는 고대의 목걸이 제작을 부탁한다.
                if (pc.getInventory().HaveItemByClassId(49027, 1) && pc.getInventory().HaveItemByClassId(41140, 1)) { // 다이아몬드와
                    // 복원된 목걸이
                    if (GameRandom.nextInt(10) > 7) {
                        materials = Recipes.RECIPE_VALUES_97;
                        counts = NpcCraftCommands.UnitCounts.TWO_ITEMS;
                        createitem = Recipes.RECIPE_VALUES_98; // 빛나는 고대 목걸이
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        htmlid = "dh9";
                    } else {
                        materials = Recipes.RECIPE_VALUES_97;
                        counts = NpcCraftCommands.UnitCounts.TWO_ITEMS;
                        createitem = Recipes.RECIPE_VALUES_95; // 보석가루
                        createcount = Recipes.RECIPE_VALUES_96;
                        htmlid = "dh7";
                    }
                } else { // 재료가 부족한 경우
                    htmlid = "dh6";
                }
            }
            // 첩보원(그림자의 신전측)
        } else if (templateId == 81202) {
            // 「화가 나지만 승낙한다」
            if (s.equalsIgnoreCase("n")) {
                NpcQuestTransactions.spyStart(pc, client, (L1NpcInstance) obj, true);
                return;
            } else if (s.equalsIgnoreCase("d")) {
                NpcQuestTransactions.spyDiscard(pc, (L1NpcInstance) obj, true);
                return;
                // 「초기화한다」
            } else if (s.equalsIgnoreCase("k")) {
                NpcQuestTransactions.spyReset(pc, (L1NpcInstance) obj, true);
                return;
            } else if (s.equalsIgnoreCase("e")) {
                NpcQuestTransactions.spyComplete(pc, (L1NpcInstance) obj, true);
                return;
                // 재빠르게 받는다
            } else if (s.equalsIgnoreCase("g")) {
                NpcQuestTransactions.spyGift(pc, (L1NpcInstance) obj, true);
                return;
            }
            // 초보자 도우미
        } else if (templateId == 9274) {
            if (s.equals("A")) {
                NpcQuestTransactions.tutorialArmor(pc, (L1NpcInstance) obj);
                return;
            }
        } else if(((L1NpcInstance) obj).getNpcTemplate().get_npcId() == DogFightSettings.CASTER_NPC_ID){
            if (s.equals("status")) {
                DogFightHistory.getInstance().on_show_ticket_ration_view(pc, (L1NpcInstance) obj);
            }
            // 버경 승률처리 start
        } else if (templateId == 70035
                || templateId == 70041
                || templateId == 70042) {
            if (s.equals("status")) {
                SC_RaceBoard.send(pc, 1, pc.isGm());
            }
        } else if (templateId == 777849) { // 킬톤
            if (s.equalsIgnoreCase("0")) htmlid = NpcExchangeCommands.contract(pc, objid, true);
        } else if (templateId == 777848) { // 메린
            if (s.equalsIgnoreCase("0")) htmlid = NpcExchangeCommands.contract(pc, objid, false);
        } else if (templateId == 70000) { // 마빈
            htmlid = NpcExperienceCommands.marbin(pc, objid, s, htmlid);
        } else if (templateId == 70908) {
            htmlid = NpcExchangeCommands.piers(pc, (L1NpcInstance) obj, objid, s, htmlid);
            // 시장 경비병/시장 중심지 이동
        } else if (templateId == 50025
                || templateId == 50032
                || templateId == 50048
                || templateId == 50058
                || templateId == 5067
                || templateId == 5070) {
            if (s.equals("market-giran") || s.equals("teleport Mobjtele2")) {
                int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_GIRAN);
                pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, false);
            } else if (s.equals("EnterSeller")) {
                L1PcInstance targetShop = CWorld.getInstance().getPlayer(s2);
                if (targetShop != null && targetShop.isPrivateShop() && pc.getMapId() == targetShop.getMapId()) {
                   pc.setFindMerchantId(targetShop.getId());
                   pc.start_teleport(targetShop.getX() - 1, targetShop.getY() + 1, targetShop.getMapId(), 1, 169, true, false);
                }else{
                   L1NpcShopInstance nn = CWorld.getInstance().getShopNpc(s2);
                   if (nn != null && nn.getMapId() == 800 && nn.getState() == 1) {
                      pc.setFindMerchantId(nn.getId());
                      pc.start_teleport(nn.getX() + CommonUtil.random(3)-1, nn.getY() + CommonUtil.random(3)-1, nn.getMapId(), 0, 169, false, false);
                   }
                }
            } else if (s.equals("teleport Mobjtele1")) {
                pc.start_teleport(32800, 32927, 800, pc.getHeading(), 169, true, false);
            }
            // 적대적인 얼음 여왕 근위병
        } else if (templateId == 5079) {
            if (s.equalsIgnoreCase("enter")) {
                QueenSystem.getInstance().startQueen(pc);
            }
            // 우호적인 얼음 여왕 근위병
        } else if (templateId == 5078) {
            if (s.equalsIgnoreCase("enter")) {
                DemonSystem.getInstance().startDemon(pc);
            }
            // 상아탑 첩보원
        } else if (templateId == 5086) {
            if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")) {
                NpcQuestTransactions.iceCaveSupplies(pc, (L1NpcInstance) obj, s.equalsIgnoreCase("a"));
                return;
            }
            // 스빈
        } else if (templateId == 5087) {
            if (s.equalsIgnoreCase("a")) {
                // NpcExchangeCommands.createNewItem(pc, npcName, 49031, 1, 0); // 얼음 결정체
                // NpcExchangeCommands.createNewItem(pc, npcName, 21081, 1, 0); // 얼음 귀걸이
                int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_OREN);
                // L1Teleport.teleport(pc, loc[0], loc[1], (short) loc[2],
                // pc.getHeading(), true);
                pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, false);
                htmlid = "";
            }
            // 베테르랑
        } else if (templateId == 5111) {
            htmlid = NpcExchangeCommands.veteran(pc, (L1NpcInstance) obj, s, htmlid);
            // 캐릭터 지원단
        } else if (templateId == 5112) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            String npcName = npc.getNpcTemplate().get_name();
            if (s.equals("a")) { // 1.레벨 상승(최대 75레벨)
                if (pc.getLevel() < 51) {
                    pc.setExp(ExpTable.getExpByLevel(51));
                } else if (pc.getLevel() >= 51 && pc.getLevel() < 75) {
                    pc.setExp(ExpTable.getExpByLevel(pc.getLevel() + 1));
                } else if (pc.getLevel() >= 75) {
                    htmlid = "lind_sm1";
                }
            } else if (s.equals("b") || s.equals("c")) {
                NpcQuestTransactions.veteranSupport(pc, (L1NpcInstance) obj, s.equals("b"));
                return;
            } else if (s.equals("d")) { // 1.드래곤의 혈흔(안타라스) 받기
                SC_SkillSound.send(pc, pc.getId(), 7783);
                pc.setSkillEffect(L1SkillId.ANTA_BUFF, 7200 * 1000);
                SC_OwnCharAttrDef.send(pc);
                SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 82, 7200 / 60);
                htmlid = "lind_sm8";
                SC_NPCTalkReturn.send(pc, objid, "lind_sm8");
            } else if (s.equals("e")) { // 2.드래곤의 혈흔(파푸리온) 받기.
                SC_SkillSound.send(pc, pc.getId(), 7783);
                pc.setSkillEffect(L1SkillId.FAFU_BUFF, 7200 * 1000);
                SC_OwnCharAttrDef.send(pc);
                SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, 85, 7200 / 60);
                htmlid = "lind_sm8";
            }
            // 반지개방
        } else if (templateId == 5162) {
            if (NpcQuestTransactions.unlockEquipmentSlot(pc, s)) return;
            // 안톤
        } else if (templateId == 70614) {
            htmlid = NpcExchangeCommands.anton(pc, (L1NpcInstance) obj, s, htmlid);

            // 카너스
        } else if (templateId == 5125) {
            htmlid = NpcTravelCommands.카너스(pc, s);
            // 다크엘프 생존자
        } else if (templateId == 5131) {
            if (s.equals("exitghost")) {
                pc.endGhost();
            }
            // 수호병사 OUT
        } else if (templateId == 5128) {
            if (s.equals("a")) {
                // L1Teleport.teleport(pc, 32614, 33195, (short) 4,
                // pc.getHeading(), true);
                pc.start_teleport(32614, 33195, 4, pc.getHeading(), 169, true, false);
                htmlid = "";
            }

            // 수호병사 IN
        } else if (templateId == 5130) {
            if (s.equals("a")) {
                pc.start_teleport(32617, 33215, 4, pc.getHeading(), 169, true, false);
                htmlid = "";
            }
            // 감시자의 눈
        } else if (templateId == 5133) {
            if (s.equals("exitghost")) {
                pc.endGhost();
            }
            // 비자야
        } else if (templateId == 470000350) {
            if (!NpcGameEventActions.enterClanRaid(pc, s)) return;
        } else if (templateId == 460000350) {
            if (!NpcGameEventActions.enterBossRaid(pc)) return;
        } else if (templateId == 5161) {
            htmlid = NpcExchangeCommands.비자야(pc, obj, s);
            /** 마을 강화 버프사 통합 **/
        } else if (templateId == 900088
                || templateId == 7310100
                || templateId == 7132
                || templateId >= 5000
                && templateId <= 5012) {
            NpcSpellScrollCommands.handle(pc, objid, s);
            return;
        } else {
            int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();

            ActionListener listener = null;
            /** 액션 리스너에서 최대 레벨이 걸릴경우 **/
            if((npcid == 7320204 || npcid == 7320054 || npcid == 7310085) && pc.getLevel() >= Config.New_Cha1){
                pc.sendPackets(String.format("%d레벨 이상은 입장할 수 없습니다.", Config.New_Cha1));
                return;
            }

            if(npcid == 70751 && s.equalsIgnoreCase("a")){
                int classId = EClassesType.fromGfx(pc.getClassId()).toInt();
                listener = ActionListenerLoader.getInstance().findListener(npcid, classListenerCommand(classId));
            }else{
                listener = ActionListenerLoader.getInstance().findListener(npcid, s);
            }

            if(listener == null){
                htmlid = NpcTemplateCommands.dispatch(pc, obj, s, htmlid);
            }else{
                htmlid = listener.to_action(pc, obj);
            }
        }
        HtmlCmdHandler.finish(pc, obj, objid, htmlid, materials, counts, createitem, createcount,
                null, null, null);
    }

    private static final class Recipes {
        private static final int[] RECIPE_VALUES_88 = {41019, 41020, 41021, 41022, 41023, 41024, 41025, 41026};
        private static final int[] RECIPE_VALUES_89 = {1, 1, 1, 1, 1, 1, 1, 1};
        private static final int[] RECIPE_VALUES_90 = {41027};
        private static final int[] RECIPE_VALUES_91 = {40965};
        private static final int[] RECIPE_VALUES_92 = {49028, 49029, 49030, 41139};
        private static final int[] RECIPE_VALUES_93 = {1, 1, 1, 1};
        private static final int[] RECIPE_VALUES_94 = {41140};
        private static final int[] RECIPE_VALUES_95 = {410027};
        private static final int[] RECIPE_VALUES_96 = {5};
        private static final int[] RECIPE_VALUES_97 = {49027, 41140};
        private static final int[] RECIPE_VALUES_98 = {20422};
    }
}
