package l1j.server.server.model.network.core.cs.packet;

import l1j.server.Config;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_Karma;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.templates.L1Town;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;

/** Quest dialogue, exchanges and event commands. */
final class NpcQuestHtmlCommands {
    private NpcQuestHtmlCommands() {}

    static void handle(CUserSocket client, L1PcInstance pc, L1Object obj, int objid,
            String s, String s2, String htmlid, int templateId) throws Exception {
        int[] materials = null;
        int[] counts = null;
        int[] createitem = null;
        int[] createcount = null;
        String success_htmlid = null;
        String failure_htmlid = null;
        String[] htmldata = null;

        if (templateId >= 71038
                && templateId <= 71044) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            htmlid = NpcQuestTransactions.orcQuestToken(pc, npc, npc.getNpcTemplate().get_npcId(), s, htmlid);
        } else if (templateId == 71078) {
            if (s.equalsIgnoreCase("teleportURL")) {
                htmlid = "usender2";
            }
        } else if (templateId == 71080) {
            if (s.equalsIgnoreCase("teleportURL")) {
                // htmlid = "amisoo2";
                SC_SystemMessage.send(pc, "하이네성 던전은 폐쇠 되었습니다.");
            }
        } else if (templateId == 80049) {
            if (s.equalsIgnoreCase("1")) {
                if (pc.getKarma() <= -10000000) {
                    pc.setKarma(1000000);
                    SC_ServerMessage.send(pc, 1078);
                    SC_Karma.send(pc);
                    htmlid = "betray13";
                }
            }
        } else if (templateId == 80050) {
            if (s.equalsIgnoreCase("1")) {
                htmlid = "meet105";
            } else if (s.equalsIgnoreCase("2")) {
                if (pc.getInventory().checkItem(40718)) {
                    htmlid = "meet106";
                } else {
                    htmlid = "meet110";
                }
            } else if (NpcQuestTransactions.exchangeCrystalForKarma(pc, (L1NpcInstance) obj, s, true)) {
                return;
            } else if (s.equalsIgnoreCase("d")) {
                if (pc.getInventory().checkItem(40066)) {
                    htmlid = "";
                } else {
                    if (pc.getKarmaLevel() < -1) {
                        pc.start_teleport(32683, 32895, 608, 5, 169, true, false);
                    }
                }
            }
        } else if (templateId == 80052) {
            if (s.equalsIgnoreCase("a")) {
                if (pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
                    SC_ServerMessage.send(pc, 79);
                } else {
                    pc.setSkillEffect(STATUS_CURSE_BARLOG, 1020 * 1000); // 1020
                    SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 1, 1020);
                    SC_SkillSound.send(pc, pc.getId(), 750);
                    SC_SkillSound.broadcast(pc, pc.getId(), 750);
//						pc.sendPackets(new SC_ServerMessage(1127));
                     SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "야히의 군사들은 이곳 저곳에 배치가 되어있습니다.");
                    pc.sendPackets("권한 부여: 거대한 마족 공격 가능(발록)");
                }
            }
        } else if (templateId == 80053) {
            int karmaLevel = pc.getKarmaLevel();
            if (s.equalsIgnoreCase("a")) {
                int aliceMaterialId = 0;

                for (int id : Recipes.ALICE_MATERIAL_IDS) {
                    if (pc.getInventory().checkItem(id)) {
                        aliceMaterialId = id;
                        break;
                    }
                }
                if (aliceMaterialId == 0) {
                    htmlid = "alice_no";
                } else if (aliceMaterialId == 40991) {
                    if (karmaLevel <= -1) {
                        materials = Recipes.RECIPE_VALUES_6;
                        counts = Recipes.RECIPE_VALUES_7;
                        createitem = Recipes.RECIPE_VALUES_8;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_1";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "aliceyet";
                    }
                } else if (aliceMaterialId == 196) {
                    if (karmaLevel <= -2) {
                        materials = Recipes.RECIPE_VALUES_9;
                        counts = Recipes.RECIPE_VALUES_7;
                        createitem = Recipes.RECIPE_VALUES_10;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_2";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "alice_1";
                    }
                } else if (aliceMaterialId == 197) {
                    if (karmaLevel <= -3) {
                        materials = Recipes.RECIPE_VALUES_11;
                        counts = Recipes.RECIPE_VALUES_7;
                        createitem = Recipes.RECIPE_VALUES_12;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_3";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "alice_2";
                    }
                } else if (aliceMaterialId == 198) {
                    if (karmaLevel <= -4) {
                        materials = Recipes.RECIPE_VALUES_13;
                        counts = Recipes.RECIPE_VALUES_14;
                        createitem = Recipes.RECIPE_VALUES_15;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_4";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "alice_3";
                    }
                } else if (aliceMaterialId == 199) {
                    if (karmaLevel <= -5) {
                        materials = Recipes.RECIPE_VALUES_16;
                        counts = Recipes.RECIPE_VALUES_14;
                        createitem = Recipes.RECIPE_VALUES_17;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_5";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "alice_4";
                    }
                } else if (aliceMaterialId == 200) {
                    if (karmaLevel <= -6) {
                        materials = Recipes.RECIPE_VALUES_18;
                        counts = Recipes.RECIPE_VALUES_14;
                        createitem = Recipes.RECIPE_VALUES_19;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_6";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "alice_5";
                    }
                } else if (aliceMaterialId == 201) {
                    if (karmaLevel <= -7) {
                        materials = Recipes.RECIPE_VALUES_20;
                        counts = Recipes.RECIPE_VALUES_21;
                        createitem = Recipes.RECIPE_VALUES_22;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_7";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "alice_6";
                    }
                } else if (aliceMaterialId == 202) {
                    if (karmaLevel <= -8) {
                        materials = Recipes.RECIPE_VALUES_23;
                        counts = Recipes.RECIPE_VALUES_21;
                        createitem = Recipes.RECIPE_VALUES_24;
                        createcount = NpcCraftCommands.UnitCounts.ONE;
                        success_htmlid = "alice_8";
                        failure_htmlid = "alice_no";
                    } else {
                        htmlid = "alice_7";
                    }
                } else if (aliceMaterialId == 203) {
                    htmlid = "alice_8";
                }
            }
        } else if (templateId == 71168) {
            // 지.상.의.타.락.을.막.기.위.해.서.라.도.
            // 그.대.와.나.더.이.상.적.일.수.만.은.없.으.리.
            // 어.서.나.를.그.곳.으.로
            if (s.equalsIgnoreCase("a")) {
                // L1Teleport.teleport(pc, 32648, 32921, (short) 535, 5, true);
                if (pc.getInventory().consumeItem(41028, 1))
                    pc.start_teleport(32648, 32921, 535, 5, 169, true, false);
            } else {
                htmlid = "";
            }
        } else if (templateId == 80056) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            if (pc.getKarma() <= -10000000) {
                NpcQuestTransactions.getBloodCrystalByKarma(pc, npc, s);
            }
            htmlid = "";
        } else if (templateId == 80063) {
            if (s.equalsIgnoreCase("a")) {
                if (pc.getInventory().consumeItem(40921, 1)) {
                    pc.start_teleport(32674, 32832, 603, 2, 169, true, false);
                } else {
                    htmlid = "gpass02";
                }
            }
        } else if (templateId == 80064) {
            if (s.equalsIgnoreCase("1")) {
                htmlid = "meet005";
            } else if (s.equalsIgnoreCase("2")) {
                if (pc.getInventory().checkItem(40678)) {
                    htmlid = "meet006";
                } else {
                    htmlid = "meet010";
                }
            } else if (NpcQuestTransactions.exchangeCrystalForKarma(pc, (L1NpcInstance) obj, s, false)) {
                return;
            } else if (s.equalsIgnoreCase("d")) {
                if (pc.getInventory().checkItem(40066) // 잠시 송편으로 대체하자! 송편이있을경우
                                                        // 안날라가지게~
                /*
                 * || pc.getInventory().checkItem(40910) ||
                 * pc.getInventory().checkItem(40911) ||
                 * pc.getInventory().checkItem(40912) ||
                 * pc.getInventory().checkItem(40913) ||
                 * pc.getInventory().checkItem(40914) ||
                 * pc.getInventory().checkItem(40915) ||
                 * pc.getInventory().checkItem(40916) ||
                 * pc.getInventory().checkItem(40917) ||
                 * pc.getInventory().checkItem(40918) ||
                 * pc.getInventory().checkItem(40919) ||
                 * pc.getInventory().checkItem(40920) ||
                 * pc.getInventory().checkItem(40921)
                 */) {
                    htmlid = "";
                } else {
                    if (pc.getKarmaLevel() > 1) {
                        // L1Teleport.teleport(pc, 32674, 32832, (short) 602, 2,
                        // true);
                        pc.start_teleport(32674, 32832, 602, 2, 169, true, false);
                    }
                }
            }
        } else if (templateId == 80066) {
            if (s.equalsIgnoreCase("1")) {
                if (pc.getKarma() >= 10000000) {
                    pc.setKarma(-1000000);
                    SC_ServerMessage.send(pc, 1079);
                    SC_Karma.send(pc);
                    htmlid = "betray03";
                }
            }
        }

        else if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 80073) {
            if (s.equalsIgnoreCase("a")) {
                if (pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
                    SC_ServerMessage.send(pc, 79);
                } else {
                    pc.setSkillEffect(STATUS_CURSE_YAHEE, 1020 * 1000);
                    SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 2, 1020);
                    SC_SkillSound.send(pc, pc.getId(), 750);
                    SC_SkillSound.broadcast(pc, pc.getId(), 750);
//						pc.sendPackets(new SC_ServerMessage(1127));
                    pc.sendPackets("권한 부여: 거대한 마족 공격 가능(혼돈-죽음-타락-야히)");
                }
            }
        } else if (templateId == 80072) {
            int karmaLevel = pc.getKarmaLevel();
            if (s.equalsIgnoreCase("0")) {
                htmlid = "lsmitha";
            } else if (s.equalsIgnoreCase("1")) {
                htmlid = "lsmithb";
            } else if (s.equalsIgnoreCase("2")) {
                htmlid = "lsmithc";
            } else if (s.equalsIgnoreCase("3")) {
                htmlid = "lsmithd";
            } else if (s.equalsIgnoreCase("4")) {
                htmlid = "lsmithe";
            } else if (s.equalsIgnoreCase("5")) {
                htmlid = "lsmithf";
            } else if (s.equalsIgnoreCase("6")) {
                htmlid = "";
            } else if (s.equalsIgnoreCase("7")) {
                htmlid = "lsmithg";
            } else if (s.equalsIgnoreCase("8")) {
                htmlid = "lsmithh";
            } else if (s.equalsIgnoreCase("a") && karmaLevel >= 1) {
                materials = Recipes.RECIPE_VALUES_25;
                counts = Recipes.RECIPE_VALUES_26;
                createitem = Recipes.RECIPE_VALUES_27;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithaa";
            } else if (s.equalsIgnoreCase("b") && karmaLevel >= 2) {
                materials = Recipes.RECIPE_VALUES_28;
                counts = Recipes.RECIPE_VALUES_26;
                createitem = Recipes.RECIPE_VALUES_29;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithbb";
            } else if (s.equalsIgnoreCase("c") && karmaLevel >= 3) {
                materials = Recipes.RECIPE_VALUES_30;
                counts = Recipes.RECIPE_VALUES_26;
                createitem = Recipes.RECIPE_VALUES_31;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithcc";
            } else if (s.equalsIgnoreCase("d") && karmaLevel >= 4) {
                materials = Recipes.RECIPE_VALUES_32;
                counts = Recipes.RECIPE_VALUES_33;
                createitem = Recipes.RECIPE_VALUES_34;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithdd";
            } else if (s.equalsIgnoreCase("e") && karmaLevel >= 5) {
                materials = Recipes.RECIPE_VALUES_35;
                counts = Recipes.RECIPE_VALUES_36;
                createitem = Recipes.RECIPE_VALUES_37;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithee";
            } else if (s.equalsIgnoreCase("f") && karmaLevel >= 6) {
                materials = Recipes.RECIPE_VALUES_38;
                counts = Recipes.RECIPE_VALUES_39;
                createitem = Recipes.RECIPE_VALUES_40;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithff";
            } else if (s.equalsIgnoreCase("g") && karmaLevel >= 7) {
                materials = Recipes.RECIPE_VALUES_41;
                counts = Recipes.RECIPE_VALUES_42;
                createitem = Recipes.RECIPE_VALUES_43;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithgg";
            } else if (s.equalsIgnoreCase("h") && karmaLevel >= 8) {
                materials = Recipes.RECIPE_VALUES_44;
                counts = Recipes.RECIPE_VALUES_42;
                createitem = Recipes.RECIPE_VALUES_45;
                createcount = NpcCraftCommands.UnitCounts.ONE;
                success_htmlid = "";
                failure_htmlid = "lsmithhh";
            }
        } else if (templateId == 205) {// 테베문지기
//				if (s.equalsIgnoreCase("e") && CrockController.getInstance().isBoss()) {
//					if (CrockController.getInstance().add(pc)) {
//						if (pc.getInventory().checkItem(100036, 1)) {
//							pc.getInventory().consumeItem(100036, 1);
//							pc.start_teleport(32735, 32831, 782, 5, 169, true, false);
//						}
//					}
//				}
        } else if (templateId == 7000021) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            if (pc.isInvisble()) {
                SC_NpcChatPacket.sendNpc(pc, npc, "투명 상태에서 받을 수 없어요.", 0);
                return;
            }
            if (s.equalsIgnoreCase("1")) {
                NpcGameEventActions.comaCheck(pc, 3, objid);
            } else if (s.equalsIgnoreCase("2")) {
                NpcGameEventActions.comaCheck(pc, 5, objid);
            } else if (s.equalsIgnoreCase("a")) {
                pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 1);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("b")) {
                pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 2);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("c")) {
                pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 3);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("d")) {
                pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 4);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("e")) {
                pc.setDeathMatchPiece(pc.getDeathMatchPiece() + 5);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("f")) {
                pc.setGhostHousePiece(pc.getGhostHousePiece() + 1);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("g")) {
                pc.setGhostHousePiece(pc.getGhostHousePiece() + 2);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("h")) {
                pc.setGhostHousePiece(pc.getGhostHousePiece() + 3);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("i")) {
                pc.setGhostHousePiece(pc.getGhostHousePiece() + 4);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("j")) {
                pc.setGhostHousePiece(pc.getGhostHousePiece() + 5);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("k")) {
                pc.setPetRacePiece(pc.getPetRacePiece() + 1);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("l")) {
                pc.setPetRacePiece(pc.getPetRacePiece() + 2);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("m")) {
                pc.setPetRacePiece(pc.getPetRacePiece() + 3);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("n")) {
                pc.setPetRacePiece(pc.getPetRacePiece() + 4);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("o")) {
                pc.setPetRacePiece(pc.getPetRacePiece() + 5);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("p")) {
                pc.setPetMatchPiece(pc.getPetMatchPiece() + 1);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("q")) {
                pc.setPetMatchPiece(pc.getPetMatchPiece() + 2);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("s")) {
                pc.setPetMatchPiece(pc.getPetMatchPiece() + 3);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("t")) {
                pc.setPetMatchPiece(pc.getPetMatchPiece() + 4);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("u")) {
                pc.setPetMatchPiece(pc.getPetMatchPiece() + 5);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("v")) {
                pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 1);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("w")) {
                pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 2);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("x")) {
                pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 3);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("y")) {
                pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 4);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("z")) {
                pc.setUltimateBattlePiece(pc.getUltimateBattlePiece() + 5);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("3")) {
                NpcGameEventActions.resetPiece(pc);
                NpcGameEventActions.selectComa(pc, objid);
            } else if (s.equalsIgnoreCase("4")) {
                NpcGameEventActions.giveComaBuff(pc, objid);
            }
        } else if (templateId == 500062) { // 조우 티칼
            if (s.equalsIgnoreCase("A")) {
                htmlid = NpcExchangeCommands.tikalExchange(pc, objid, ((L1NpcInstance) obj).getNpcTemplate().get_name());
            } else if (s.equalsIgnoreCase("B")) {
                pc.start_teleport(34090, 33168, 4, 5, 169, true, false);
            }
        } else if (templateId == 500063) {// 티칼
//				if (s.equalsIgnoreCase("e") && CrockController.getInstance().isBoss())
//					if (pc.getInventory().checkItem(500210, 1)) {
//						pc.getInventory().consumeItem(500210);
//						CrockController.getInstance().add(pc);
//						pc.start_teleport(32732, 32862, 784, 5, 169, true, false);
//					}
        } else if (templateId == 500064) {// 티칼
            if (s.equalsIgnoreCase("1")) {
                NpcQuestTransactions.grantMissingNpcItem(pc, (L1NpcInstance) obj, 500211, null, null, true);
                return;
            }
            // 라폰스시작
        } else if (templateId == 80074) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            if (pc.getKarma() >= 10000000) {
                NpcQuestTransactions.getSoulCrystalByKarma(pc, npc, s);
            }
            htmlid = "";
        } else if (templateId == 50007) { // 에스메랄다
            NpcTravelCommands.esmeralda(pc, s);
        } else if (templateId == 80057) {
            htmlid = NpcQuestTransactions.karmaLevelToHtmlId(pc.getKarmaLevel());
            htmldata = new String[] { String.valueOf(pc.getKarmaPercent()) };
        } else if (templateId == 80059
                || templateId == 80060
                || templateId == 80061
                || templateId == 80062) {
            htmlid = NpcTravelCommands.talkToDimensionDoor(pc, (L1NpcInstance) obj, s);
        } else if (templateId == 81124) {
            if (s.equalsIgnoreCase("1")) {
                NpcLocalCommands.poly(client, 4002);
                htmlid = "";
            } else if (s.equalsIgnoreCase("2")) {
                NpcLocalCommands.poly(client, 4004);
                htmlid = "";
            } else if (s.equalsIgnoreCase("3")) {
                NpcLocalCommands.poly(client, 4950);
                htmlid = "";
            }
        } else if (s.equalsIgnoreCase("contract1")) {
            pc.getQuest().set_step(L1Quest.QUEST_LYRA, 1);
            htmlid = "lyraev2";
        } else if (s.equalsIgnoreCase("contract1yes") || s.equalsIgnoreCase("contract1no")) {

            if (s.equalsIgnoreCase("contract1yes")) {
                htmlid = "lyraev5";
            } else if (s.equalsIgnoreCase("contract1no")) {
                pc.getQuest().set_step(L1Quest.QUEST_LYRA, 0);
                htmlid = "lyraev4";
            }
            NpcCraftCommands.exchangeTotems(pc, obj, objid, htmlid);
            return;
        } else if (s.equalsIgnoreCase("pandora6") || s.equalsIgnoreCase("cold6") || s.equalsIgnoreCase("balsim3")
                || s.equalsIgnoreCase("mellin3") || s.equalsIgnoreCase("glen3")) {
            htmlid = s;
            int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
            int taxRatesCastle = L1CastleLocation.getCastleTaxRateByNpcId(npcid);
            htmldata = new String[] { String.valueOf(taxRatesCastle) };
        } else if (s.equalsIgnoreCase("set")) {
            if (obj instanceof L1NpcInstance) {
                int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                int town_id = L1TownLocation.getTownIdByNpcid(npcid);

                if (town_id >= 1 && town_id <= 10) {
                    if (pc.getHomeTownId() == -1) {
                        SC_ServerMessage.send(pc, 759);
                        htmlid = "";
                    } else if (pc.getHomeTownId() > 0) {
                        if (pc.getHomeTownId() != town_id) {
                            L1Town town = TownTable.getInstance().getTownTable(pc.getHomeTownId());
                            if (town != null) {
                                SC_ServerMessage.send(pc, 758, town.get_name());
                            }
                            htmlid = "";
                        } else {
                            htmlid = "";
                        }
                    } else if (pc.getHomeTownId() == 0) {
                        if (pc.getLevel() < 10) {
                            SC_ServerMessage.send(pc, 757);
                            htmlid = "";
                        } else {
                            int level = pc.getLevel();
                            int cost = level * level * 10;
                            if (pc.getInventory().consumeItem(L1ItemId.ADENA, cost)) {
                                pc.setHomeTownId(town_id);
                                pc.setContribution(0);
                                pc.save();
                            } else {
                                SC_ServerMessage.send(pc, 337, "$4");
                            }
                            htmlid = "";
                        }
                    }
                }
            }
        } else if (s.equalsIgnoreCase("clear")) {
            if (obj instanceof L1NpcInstance) {
                int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                int town_id = L1TownLocation.getTownIdByNpcid(npcid);
                if (town_id > 0) {
                    if (pc.getHomeTownId() > 0) {
                        if (pc.getHomeTownId() == town_id) {
                            pc.setHomeTownId(-1);
                            pc.setContribution(0);
                        } else {
                            SC_ServerMessage.send(pc, 756);
                        }
                    }
                    htmlid = "";
                }
            }
        } else if (s.equalsIgnoreCase("ask")) {
            if (obj instanceof L1NpcInstance) {
                int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
                int town_id = L1TownLocation.getTownIdByNpcid(npcid);

                if (town_id >= 1 && town_id <= 10) {
                    L1Town town = TownTable.getInstance().getTownTable(town_id);
                    String leader = town.get_leader_name();
                    if (leader != null && leader.length() != 0) {
                        htmlid = "owner";
                        htmldata = new String[] { leader };
                    } else {
                        htmlid = "noowner";
                    }
                }
            }
        } else {
            NpcRegionalHtmlCommands.handle(client, pc, obj, objid, s, s2, htmlid, templateId);
            return;
        }
        HtmlCmdHandler.finish(pc, obj, objid, htmlid, materials, counts, createitem, createcount,
                success_htmlid, failure_htmlid, htmldata);
    }

    private static final class Recipes {
        private static final int[] RECIPE_VALUES_6 = {40995, 40718, 40991};
        private static final int[] RECIPE_VALUES_7 = {100, 100, 1};
        private static final int[] RECIPE_VALUES_8 = {196};
        private static final int[] RECIPE_VALUES_9 = {40997, 40718, 196};
        private static final int[] RECIPE_VALUES_10 = {197};
        private static final int[] RECIPE_VALUES_11 = {40990, 40718, 197};
        private static final int[] RECIPE_VALUES_12 = {198};
        private static final int[] RECIPE_VALUES_13 = {40994, 40718, 198};
        private static final int[] RECIPE_VALUES_14 = {50, 100, 1};
        private static final int[] RECIPE_VALUES_15 = {199};
        private static final int[] RECIPE_VALUES_16 = {40993, 40718, 199};
        private static final int[] RECIPE_VALUES_17 = {200};
        private static final int[] RECIPE_VALUES_18 = {40998, 40718, 200};
        private static final int[] RECIPE_VALUES_19 = {201};
        private static final int[] RECIPE_VALUES_20 = {40996, 40718, 201};
        private static final int[] RECIPE_VALUES_21 = {10, 100, 1};
        private static final int[] RECIPE_VALUES_22 = {202};
        private static final int[] RECIPE_VALUES_23 = {40992, 40718, 202};
        private static final int[] RECIPE_VALUES_24 = {203};
        private static final int[] RECIPE_VALUES_25 = {20158, 40669, 40678};
        private static final int[] RECIPE_VALUES_26 = {1, 50, 100};
        private static final int[] RECIPE_VALUES_27 = {20083};
        private static final int[] RECIPE_VALUES_28 = {20144, 40672, 40678};
        private static final int[] RECIPE_VALUES_29 = {20131};
        private static final int[] RECIPE_VALUES_30 = {20075, 40671, 40678};
        private static final int[] RECIPE_VALUES_31 = {20069};
        private static final int[] RECIPE_VALUES_32 = {20183, 40674, 40678};
        private static final int[] RECIPE_VALUES_33 = {1, 20, 100};
        private static final int[] RECIPE_VALUES_34 = {20179};
        private static final int[] RECIPE_VALUES_35 = {20190, 40674, 40678};
        private static final int[] RECIPE_VALUES_36 = {1, 40, 100};
        private static final int[] RECIPE_VALUES_37 = {20209};
        private static final int[] RECIPE_VALUES_38 = {20078, 40674, 40678};
        private static final int[] RECIPE_VALUES_39 = {1, 5, 100};
        private static final int[] RECIPE_VALUES_40 = {20290};
        private static final int[] RECIPE_VALUES_41 = {20078, 40670, 40678};
        private static final int[] RECIPE_VALUES_42 = {1, 1, 100};
        private static final int[] RECIPE_VALUES_43 = {20261};
        private static final int[] RECIPE_VALUES_44 = {40719, 40673, 40678};
        private static final int[] RECIPE_VALUES_45 = {20031};
        private static final int[] ALICE_MATERIAL_IDS = {40991, 196, 197, 198, 199, 200, 201, 202, 203};
    }
}
