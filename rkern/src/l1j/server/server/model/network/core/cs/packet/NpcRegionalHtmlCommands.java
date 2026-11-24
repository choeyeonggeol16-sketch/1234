package l1j.server.server.model.network.core.cs.packet;

import static l1j.server.server.model.network.core.cs.packet.HtmlCmdHandler._random;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** Regional NPCs, class quests and training services. */
final class NpcRegionalHtmlCommands {
    private NpcRegionalHtmlCommands() {}

    static void handle(CUserSocket client, L1PcInstance pc, L1Object obj, int objid,
            String s, String s2, String htmlid, int templateId) throws Exception {
        int[] materials = null;
        int[] counts = null;
        int[] createitem = null;
        int[] createcount = null;

        if (templateId == 70534
                || templateId == 70556
                || templateId == 70572
                || templateId == 70631
                || templateId == 70663
                || templateId == 70761
                || templateId == 70788
                || templateId == 70806
                || templateId == 70830
                || templateId == 70876) {
            if (s.equalsIgnoreCase("r")) {
                if (obj instanceof L1NpcInstance) {
                }
            } else if (s.equalsIgnoreCase("t")) {

            } else if (s.equalsIgnoreCase("c")) {

            }

        } else if (templateId == 71005) {
            if (s.equalsIgnoreCase("0")) {
                if (pc.getInventory().checkItem(41209)) htmlid = "";
                else {
                    NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41209, 1, true, "");
                    return;
                }
            } else if (s.equalsIgnoreCase("1")) {
                NpcQuestTransactions.jpePotionExchange(pc, (L1NpcInstance) obj);
                return;
            }
        } else if (templateId == 71006) {
            if (s.equalsIgnoreCase("0")) {
                if (pc.getLevel() > 25) {
                    htmlid = "jpe0057";
                } else if (pc.getInventory().checkItem(41213)) {
                    htmlid = "jpe0056";
                } else if (pc.getInventory().checkItem(41210) || pc.getInventory().checkItem(41211)) {
                    htmlid = "jpe0055";
                } else if (pc.getInventory().checkItem(41209)) {
                    htmlid = "jpe0054";
                } else if (pc.getInventory().checkItem(41212)) {
                    htmlid = "jpe0056";
                    materials = Recipes.RECIPE_VALUES_46;
                    counts = NpcCraftCommands.UnitCounts.ONE;
                    createitem = Recipes.RECIPE_VALUES_47;
                    createcount = NpcCraftCommands.UnitCounts.ONE;
                } else {
                    htmlid = "jpe0057";
                }
            }
        } else if (templateId == 70512) {
            if (s.equalsIgnoreCase("0") || s.equalsIgnoreCase("fullheal")) {
                int hp = _random.nextInt(21) + 70;
                pc.setCurrentHp(pc.getCurrentHp() + hp);
                SC_ServerMessage.send(pc, 77);
                SC_SkillSound.send(pc, pc.getId(), 830);
                htmlid = "";
            }
        } else if (templateId == 71025) {
            if (s.equalsIgnoreCase("0")) {
                NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41225, 1, false, "jpe0083");
                return;
            }
        } else if (templateId == 454512) {

            if (s.equalsIgnoreCase("a")) {
                pc.start_teleport(32734, 32859, 13005, 5, 169, true, false);
                htmlid = "";
            }

        } else if (templateId == 454513) {
            if (s.equalsIgnoreCase("a")) {
                pc.setCurrentHp(pc.getMaxHp() / 5);
                pc.setCurrentMp(pc.getMaxMp() / 5);
                pc.start_teleport(33431, 32798, 4, 5, 169, true, false);
                htmlid = "";
            }

        } else if (templateId == 454514) {

            if (s.equalsIgnoreCase("a")) {

                int hp = pc.getMaxHp() - pc.getCurrentHp();
                int mp = pc.getMaxMp() - pc.getCurrentMp();

                pc.setCurrentHp(pc.getCurrentHp() + hp);
                pc.setCurrentMp(pc.getCurrentMp() + mp);
                SC_SkillSound.send(pc, pc.getId(), 832);
                htmlid = "";

            }

        } else if (templateId == 71055) {
            if (s.equalsIgnoreCase("0")) {
                NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_LUKEIN1,
                        0, 1, 40701, 1, "lukein8");
                return;
            }
            if (s.equalsIgnoreCase("1")) {
                if (pc.getQuest().get_step(CQuestSystem.QUEST_TBOX3) != CQuestSystem.QUEST_END
                        || pc.getQuest().get_step(CQuestSystem.QUEST_LUKEIN1) != 11) {
                    SC_NPCTalkReturn.send(pc, objid, "lukein0");
                    return;
                }
                NpcQuestTransactions.questItemExchange(pc, (L1NpcInstance) obj,
                        CQuestSystem.QUEST_LUKEIN1, 11, CQuestSystem.QUEST_END,
                        40716, 1, 0, 20269, 0, "lukein0", "lukein0");
                return;
            } else if (s.equalsIgnoreCase("2")) {
                NpcQuestTransactions.requestQuestPage(pc, obj, objid, CQuestSystem.QUEST_RESTA,
                        pc.getQuest().get_step(CQuestSystem.QUEST_RESTA), 3, "lukein12");
                return;
            }
        } else if (templateId == 71063) {
            if (s.equalsIgnoreCase("0")) {
                NpcQuestTransactions.lukeinChestReceipt(pc, (L1NpcInstance) obj,
                        1, 2, 3, CQuestSystem.QUEST_TBOX1, 40702, "maptbox1");
                return;
            }
        } else if (templateId >= 71064
                && templateId <= 71066) {
            if (s.equalsIgnoreCase("0")) {
                final int npcId = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                NpcQuestTransactions.lukeinChestReceipt(pc, (L1NpcInstance) obj,
                        npcId - 71062, 5, 6, CQuestSystem.QUEST_TBOX2, 40702, "maptbox1");
                return;
            }
        } else if (templateId >= 71067
                && templateId <= 71072) {
            if (s.equalsIgnoreCase("0")) {
                final int npcId = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                NpcQuestTransactions.lukeinChestReceipt(pc, (L1NpcInstance) obj,
                        npcId - 71062, 11, 1, CQuestSystem.QUEST_TBOX3, 40716, "maptboxi");
                return;
            }

            // 시미즈(해적섬)
        } else if (templateId == 71056) {
            // 아들을 찾는다
            if (s.equalsIgnoreCase("a")) {
                NpcQuestTransactions.requestQuestPage(pc, obj, objid, CQuestSystem.QUEST_SIMIZZ,
                        pc.getQuest().get_step(CQuestSystem.QUEST_SIMIZZ), 1, "SIMIZZ7");
                return;
            } else if (s.equalsIgnoreCase("b")) {
                if (pc.getInventory().checkItem(40661) && pc.getInventory().checkItem(40662)
                        && pc.getInventory().checkItem(40663)) {
                    NpcQuestTransactions.simizzReceipt(pc, (L1NpcInstance) obj);
                    return;
                }
                htmlid = "SIMIZZ9";
            } else if (s.equalsIgnoreCase("d")) {
                NpcQuestTransactions.requestQuestPage(pc, obj, objid, CQuestSystem.QUEST_SIMIZZ,
                        pc.getQuest().get_step(CQuestSystem.QUEST_SIMIZZ), CQuestSystem.QUEST_END, "SIMIZZ12");
                return;
            }

            // 도일(해적섬)
        } else if (templateId == 71057) {
            // 러쉬에 대해 듣는다
            if (s.equalsIgnoreCase("3")) {
                htmlid = "doil4";
            } else if (s.equalsIgnoreCase("6")) {
                htmlid = "doil6";
            } else if (s.equalsIgnoreCase("1")) {
                if (pc.getInventory().checkItem(40714)) {
                    NpcQuestTransactions.questItemExchange(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_DOIL,
                            pc.getQuest().get_step(CQuestSystem.QUEST_DOIL), CQuestSystem.QUEST_END,
                            40714, 1, 0, 40647, 0, "doil8", "doil7");
                    return;
                }
                htmlid = "doil7";
            }

            // 루디 안(해적섬)
        } else if (templateId == 83014) {
            if (NpcQuestTransactions.handleBlessingBuyback(pc, (L1NpcInstance) obj, s)) return;
        } else if (templateId == 71059) {
            // 루디 안의 부탁을 받아들인다
            if (s.equalsIgnoreCase("A")) {
                NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_RUDIAN,
                        0, 1, 40700, 1, "rudian6");
                return;
            } else if (s.equalsIgnoreCase("B")) {
                if (pc.getInventory().checkItem(40710)) {
                    NpcQuestTransactions.questItemExchange(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_RUDIAN,
                            pc.getQuest().get_step(CQuestSystem.QUEST_RUDIAN), CQuestSystem.QUEST_END,
                            40700, 1, 40710, 40647, 0, "rudian8", "rudian9");
                    return;
                }
                htmlid = "rudian9";
            }

            // 레스타(해적섬)
        } else if (templateId == 71060) {
            // 동료들에 대해
            if (s.equalsIgnoreCase("A")) {
                if (pc.getQuest().get_step(CQuestSystem.QUEST_RUDIAN) == CQuestSystem.QUEST_END) {
                    htmlid = "resta6";
                } else {
                    htmlid = "resta4";
                }
            } else if (s.equalsIgnoreCase("B")) {
                NpcQuestTransactions.requestQuestPage(pc, obj, objid, CQuestSystem.QUEST_RESTA,
                        pc.getQuest().get_step(CQuestSystem.QUEST_RESTA), 2, "resta10");
                return;
            }

            // 카좀스(해적섬)
        } else if (templateId == 71061) {
            if (s.equalsIgnoreCase("A")) {
                final int step = pc.getQuest().get_step(CQuestSystem.QUEST_CADMUS);
                if (step >= 2) {
                    SC_NPCTalkReturn.send(pc, objid, "cadmus6");
                } else if (pc.getInventory().checkItem(40647, 3)) {
                    NpcQuestTransactions.npcQuestDebit(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_CADMUS,
                            step, 2, 40647, 3, "cadmus6", "cadmus5");
                } else {
                    if (step == 0) {
                        NpcQuestTransactions.requestQuestPage(pc, obj, objid, CQuestSystem.QUEST_CADMUS,
                                0, 1, "cadmus5");
                    } else SC_NPCTalkReturn.send(pc, objid, "cadmus5");
                }
                return;
            }
        } else if (templateId == 71036) {
            if (s.equalsIgnoreCase("a")) {
                NpcQuestTransactions.requestQuestPage(pc, obj, objid, CQuestSystem.QUEST_KAMYLA,
                        pc.getQuest().get_step(CQuestSystem.QUEST_KAMYLA), 1, "kamyla7");
                return;
            } else if (s.equalsIgnoreCase("c")) {
                NpcQuestTransactions.npcQuestDebit(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_KAMYLA,
                        2, 3, 40644, 1, "kamyla10", "kamyla10");
                return;
            } else if (s.equalsIgnoreCase("e")) {
                NpcQuestTransactions.npcQuestDebit(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_KAMYLA,
                        3, 4, 40630, 1, "kamyla13", "kamyla13");
                return;
            } else if (s.equalsIgnoreCase("i")) {
                htmlid = "kamyla25";
            } else if (s.equalsIgnoreCase("b")) { // 카 미라(흐랑코의 미궁)
                if (pc.getQuest().get_step(CQuestSystem.QUEST_KAMYLA) == 1) {
                    // L1Teleport.teleport(pc, 32679, 32742, (short) 482, 5,
                    // true);
                    pc.start_teleport(32679, 32742, 482, 5, 169, true, false);
                }
            } else if (s.equalsIgnoreCase("d")) { // 카 미라(디에고가 닫힌 뇌)
                if (pc.getQuest().get_step(CQuestSystem.QUEST_KAMYLA) == 3) {
                    // L1Teleport.teleport(pc, 32736, 32800, (short) 483, 5,
                    // true);
                    pc.start_teleport(32736, 32800, 483, 5, 169, true, false);
                }
            } else if (s.equalsIgnoreCase("f")) { // 카 미라(호세 지하소굴)
                if (pc.getQuest().get_step(CQuestSystem.QUEST_KAMYLA) == 4) {
                    // L1Teleport.teleport(pc, 32746, 32807, (short) 484, 5,
                    // true);
                    pc.start_teleport(32746, 32807, 484, 5, 169, true, false);
                }
            }
        } else if (templateId == 71089) {
            if (s.equalsIgnoreCase("a")) {
                NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_KAMYLA,
                        1, 2, 40644, 1, "francu10");
                return;
            }
        } else if (templateId == 71090) {
            if (NpcQuestTransactions.handleCrystalKeeper(pc, (L1NpcInstance) obj, s)) return;
        } else if (templateId == 71091) {
            if (NpcQuestTransactions.handleCrystalExit(pc, (L1NpcInstance) obj, s)) return;
        } else if (templateId == 71074) {
            if (NpcQuestTransactions.handleLizardQuest(pc, (L1NpcInstance) obj, s)) return;
        } else if (templateId == 71198) {
            if (NpcQuestTransactions.handleTionQuest(pc, (L1NpcInstance) obj, s)) return;
        } else if (templateId == 71199) {
            if (NpcQuestTransactions.handleJeronQuest(pc, (L1NpcInstance) obj, s)) return;
        } else if (templateId == 80079) {
            if (s.equalsIgnoreCase("0")) {
                if (!pc.getInventory().checkItem(41312)) {
                    // The first clue is a one-time quest award; after it was
                    // consumed, another click must not mint a second clue.
                    if (pc.getQuest().get_step(CQuestSystem.QUEST_KEPLISHA) == 0)
                        NpcQuestTransactions.questGrantOnce(pc, (L1NpcInstance) obj, CQuestSystem.QUEST_KEPLISHA,
                                0, CQuestSystem.QUEST_END, 41312, 1, "keplisha7");
                    else
                        SC_NPCTalkReturn.send(pc, objid, "keplisha7");
                    return;
                }
            } else if (s.equalsIgnoreCase("1")) {
                NpcQuestTransactions.keplishaHoroscopeReceipt(pc, (L1NpcInstance) obj);
                return;
            } else if (s.equalsIgnoreCase("2")) {
                if (pc.getCurrentSpriteId() != pc.getClassId()) {
                    htmlid = "keplisha9";
                } else {
                    if (pc.getInventory().consumeItem(41314, 1)) {
                        int html = _random.nextInt(9) + 1;
                        int PolyId = 6180 + _random.nextInt(40);
                        NpcLocalCommands.polyByKeplisha(client, PolyId);
                        switch (html) {
                        case 1:
                            htmlid = "horomon11";
                            break;
                        case 2:
                            htmlid = "horomon12";
                            break;
                        case 3:
                            htmlid = "horomon13";
                            break;
                        case 4:
                            htmlid = "horomon21";
                            break;
                        case 5:
                            htmlid = "horomon22";
                            break;
                        case 6:
                            htmlid = "horomon23";
                            break;
                        case 7:
                            htmlid = "horomon31";
                            break;
                        case 8:
                            htmlid = "horomon32";
                            break;
                        case 9:
                            htmlid = "horomon33";
                            break;
                        default:
                            break;
                        }
                    }
                }
            } else if (s.equalsIgnoreCase("3")) {
                NpcQuestTransactions.discardKeplisha(pc, (L1NpcInstance) obj, htmlid);
                return;
            }

        } else if (templateId == 71181) { // 에마이
            final int materialId;
            final int rewardId;
            final String page;
            if (s.equalsIgnoreCase("A")) { materialId = 41093; rewardId = 41097; page = "my5"; }
            else if (s.equalsIgnoreCase("B")) { materialId = 41094; rewardId = 41918; page = "my6"; }
            else if (s.equalsIgnoreCase("C")) { materialId = 41095; rewardId = 41919; page = "my7"; }
            else if (s.equalsIgnoreCase("D")) { materialId = 41096; rewardId = 41920; page = "my8"; }
            else { materialId = 0; rewardId = 0; page = null; }
            if (page != null) {
                NpcQuestTransactions.npcItemExchange(pc, (L1NpcInstance) obj, materialId, rewardId, page, "my4");
                return;
            }
        } else if (templateId == 80084) {
            if (s.equalsIgnoreCase("q")) {
                if (pc.getInventory().checkItem(41356, 1)) htmlid = "rparum4";
                else {
                    NpcQuestTransactions.grantLegacyNpcItem(pc, (L1NpcInstance) obj, 41356, 1, true, "rparum3");
                    return;
                }
            }
        } else if (templateId == 80105) {
            if (s.equalsIgnoreCase("c")) {
                NpcExchangeCommands.rechargeCrownRelic(pc, objid);
                return;
            }

        } else if (templateId == 4202000) { // 용기사
            // 피에나
            if (s.equalsIgnoreCase("teleportURL") && pc.isDragonknight()) {
                htmlid = "feaena3";
            }
        } else if (templateId == 4201000) { // 환술사
            // 아샤
            if (s.equalsIgnoreCase("teleportURL") && pc.isBlackwizard()) {
                htmlid = "asha3";
            }
        } else if (templateId == 200015) {
            if (s.equalsIgnoreCase("0")) {
                NpcQuestTransactions.grantMissingNpcItem(pc, (L1NpcInstance) obj, 200000,
                        "candleg2", "candleg3", false);
                return;
            }
        } else if (templateId == 200016) {
            if (s.equalsIgnoreCase("B")) {
                if (pc.getInventory().consumeItem(210081, 1)) {
                    // L1Teleport.teleport(pc, 32618, 32736, (short) 4, 4,
                    // true);
                    pc.start_teleport(32618, 32736, 4, 5, 169, true, false);
                } else {
                    htmlid = "joegolem20";
                }
            }
        }

        else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 3000003) {
            if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")) {
                NpcQuestTransactions.level15ItemReceipt(pc, (L1NpcInstance) obj, false, s.equalsIgnoreCase("a"));
                return;
            }
        } else if (templateId == 3100004) {
            if (s.equalsIgnoreCase("a") || s.equalsIgnoreCase("b")) {
                NpcQuestTransactions.level15ItemReceipt(pc, (L1NpcInstance) obj, true, s.equalsIgnoreCase("a"));
                return;
            }
        } else if (templateId == 3200014) {
            if (s.equalsIgnoreCase("buy 5")) {
                NpcPetCommands.petbuy(client, 46044, 41159, 1000);
                htmlid = "subsusp3";
            } else if (s.equalsIgnoreCase("buy 6")) {
                NpcPetCommands.petbuy(client, 46042, 41159, 1000);
                htmlid = "subsusp4";
            }

        } else if (templateId == 200075) { // 샤르나
            htmlid = NpcExchangeCommands.sharna(pc, objid, s, ((L1NpcInstance) obj).getNpcTemplate().get_name());
        } else {
            NpcEquipmentHtmlCommands.handle(client, pc, obj, objid, s, s2, htmlid, templateId);
            return;
        }
        HtmlCmdHandler.finish(pc, obj, objid, htmlid, materials, counts, createitem, createcount,
                null, null, null);
    }

    private static final class Recipes {
        private static final int[] RECIPE_VALUES_46 = {41212};
        private static final int[] RECIPE_VALUES_47 = {41213};
    }
}
