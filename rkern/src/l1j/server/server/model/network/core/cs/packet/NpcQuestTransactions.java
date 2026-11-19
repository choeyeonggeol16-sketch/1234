package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

import l1j.server.Config;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.core.sc.packet.SC_Karma;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

final class NpcQuestTransactions {
    private NpcQuestTransactions() {}

    /** NPC 535: a single receipt for the gun debit and garnet grant. */
    static String exchangeProtonGun(L1PcInstance pc, int npcObjectId, String command, String htmlid) {
        final int count;
        if (command.equalsIgnoreCase("a")) count = 1;
        else if (command.equalsIgnoreCase("b")) count = 5;
        else if (command.equalsIgnoreCase("c")) count = 10;
        else return htmlid;
        final int rewardCount = count * 3;
        if (!pc.getInventory().checkItem(3000506, count)) {
            sendProtonGunRequirement(pc, count);
            return "falllovef";
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, 535)) {
            if (!tx.debit(3000506, count)) {
                sendProtonGunRequirement(pc, count);
                return "falllovef";
            }
            if (tx.grant(3000246, rewardCount) == null) {
                SC_SystemMessage.send(pc, "아이템 교환을 진행할 수 없습니다.");
                return "falllovef";
            }
            tx.onFailure(() -> {
                SC_SystemMessage.send(pc, "아이템 교환이 확정되지 않았습니다.");
                SC_NPCTalkReturn.send(pc, npcObjectId, "falllovef");
            });
            tx.commitAfterInventoryRelease(() -> {
                SC_SystemMessage.send(pc, "획득:오림의가넷(" + rewardCount + ") 감사합니다. 다음에 또 오세요.");
                SC_NPCTalkReturn.send(pc, npcObjectId, "");
            });
            // The dispatcher must not send the success page before receipt replay.
            return null;
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 교환을 진행할 수 없습니다.");
            return "falllovef";
        }
    }

    private static void sendProtonGunRequirement(L1PcInstance pc, int count) {
        SC_SystemMessage.send(pc, "프로톤 건(" + count + ")개 를 가져다주시면 오림의 가넷(" + (count * 3) + ")로 드리겠습니다.");
    }

    /** NPC 71198/71199 item exchanges: item rows and quest rows share one durable receipt.
     * Do not send a success page or advance the in-memory quest before confirmation.
     * This executes on the existing CIO owner; no new worker/queue is involved.
     */
    static void questItemExchange(L1PcInstance pc, L1NpcInstance npc,
            int questId, int expectedStep, int nextStep, int materialId, int materialCount,
            int additionalMaterialId, int rewardId, int resetQuestId,
            String successPage, String failurePage) {
        questItemExchange(pc, npc, questId, expectedStep, nextStep, materialId, materialCount,
                additionalMaterialId, rewardId, resetQuestId, successPage, failurePage, null);
    }

    static void questItemExchange(L1PcInstance pc, L1NpcInstance npc,
            int questId, int expectedStep, int nextStep, int materialId, int materialCount,
            int additionalMaterialId, int rewardId, int resetQuestId,
            String successPage, String failurePage, Runnable afterCommitted) {
        final int npcObjectId = npc.getId();
        final String npcName = npc.getNpcTemplate().get_name();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, questId)) {
            if (!tx.debit(materialId, materialCount)
                    || (additionalMaterialId != 0 && !tx.debit(additionalMaterialId, 1))) {
                SC_NPCTalkReturn.send(pc, npcObjectId, failurePage);
                return;
            }
            final L1ItemInstance reward = rewardId == 0 ? null : tx.grant(rewardId, 1);
            if (rewardId != 0 && reward == null) {
                SC_NPCTalkReturn.send(pc, npcObjectId, failurePage);
                return;
            }
            stageQuestStep(tx, pc, questId, expectedStep, nextStep, resetQuestId);
            tx.onFailure(() -> {
                SC_SystemMessage.send(pc, "퀘스트 아이템 거래가 확정되지 않았습니다.");
                SC_NPCTalkReturn.send(pc, npcObjectId, failurePage);
            });
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, nextStep);
                if (resetQuestId != 0) pc.getQuest().applyCommittedStepIfLoaded(resetQuestId, 0);
                if (reward != null) SC_ServerMessage.send(pc, 143, npcName, reward.getItem().getName());
                if (afterCommitted != null) afterCommitted.run();
                SC_NPCTalkReturn.send(pc, npcObjectId, successPage);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템 거래를 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npcObjectId, failurePage);
        }
    }

    /** The quest preimage is checked under the same InnoDB receipt as inventory rows. */
    static void stageQuestStep(l1j.server.server.model.L1InventoryTransaction tx,
            L1PcInstance pc, int questId, int expectedStep, int nextStep, int resetQuestId) {
            // Quest and character_items must be transactional in the SAME database.
            // A stale quest step aborts the entire receipt instead of consuming items.
            tx.requireTables("character_quests");
            tx.sql(con -> {
                final int durableStep;
                try (PreparedStatement query = con.prepareStatement(
                        "SELECT quest_step FROM character_quests WHERE char_id=? AND quest_id=? FOR UPDATE")) {
                    query.setInt(1, pc.getId());
                    query.setInt(2, questId);
                    try (ResultSet rows = query.executeQuery()) {
                        durableStep = rows.next() ? rows.getInt(1) : 0;
                    }
                }
                if (durableStep != expectedStep)
                    throw new SQLException("Quest step changed while preparing item receipt: " + questId);
                try (PreparedStatement update = con.prepareStatement(
                        "INSERT INTO character_quests (char_id,quest_id,quest_step) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE quest_step=VALUES(quest_step)")) {
                    update.setInt(1, pc.getId());
                    update.setInt(2, questId);
                    update.setInt(3, nextStep);
                    update.executeUpdate();
                    if (resetQuestId != 0) {
                        update.setInt(2, resetQuestId);
                        update.setInt(3, 0);
                        update.executeUpdate();
                    }
                }
            });
    }

    /** Lukein treasure chests: map debit, reward grant and BOTH quest rows in one receipt.
     * Keep the original shared Random and reward IDs; no per-click reward arrays.
     */
    static void lukeinChestReceipt(L1PcInstance pc, L1NpcInstance npc,
            int expectedStep, int nextFirst, int nextCount, int boxQuestId,
            int rewardId, String successPage) {
        final int npcObjectId = npc.getId();
        final int mainQuest = L1Quest.QUEST_LUKEIN1;
        if (pc.getQuest().get_step(mainQuest) != expectedStep
                || pc.getQuest().get_step(boxQuestId) != 0
                || (boxQuestId == L1Quest.QUEST_TBOX2
                        && pc.getQuest().get_step(L1Quest.QUEST_TBOX1) != L1Quest.QUEST_END)
                || (boxQuestId == L1Quest.QUEST_TBOX3
                        && pc.getQuest().get_step(L1Quest.QUEST_TBOX2) != L1Quest.QUEST_END)) {
            SC_NPCTalkReturn.send(pc, npcObjectId, "maptbox0");
            return;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, mainQuest)) {
            if (!tx.debit(40701, 1)) {
                SC_ServerMessage.send(pc, 337, ItemTable.getInstance().getTemplate(40701).getName());
                SC_NPCTalkReturn.send(pc, npcObjectId, successPage);
                return;
            }
            final L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) {
                SC_SystemMessage.send(pc, "보상 아이템을 지급할 수 없습니다.");
                return;
            }
            // Same nextInt range as the old nextbox/nextbox2 arrays.
            final int nextStep = nextFirst + (nextCount == 1 ? 0 : HtmlCmdHandler._random.nextInt(nextCount));
            tx.requireTables("character_quests");
            tx.sql(con -> {
                // Lock the quest rows in stable order (main=23, box=24..26).
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT quest_step FROM character_quests WHERE char_id=? AND quest_id=? FOR UPDATE")) {
                    check.setInt(1, pc.getId());
                    check.setInt(2, mainQuest);
                    try (ResultSet rs = check.executeQuery()) {
                        if ((rs.next() ? rs.getInt(1) : 0) != expectedStep)
                            throw new SQLException("Lukein quest step changed before item commit");
                    }
                    check.setInt(2, boxQuestId);
                    try (ResultSet rs = check.executeQuery()) {
                        if ((rs.next() ? rs.getInt(1) : 0) != 0)
                            throw new SQLException("Lukein box already claimed");
                    }
                }
                try (PreparedStatement update = con.prepareStatement(
                        "INSERT INTO character_quests (char_id,quest_id,quest_step) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE quest_step=VALUES(quest_step)")) {
                    update.setInt(1, pc.getId());
                    update.setInt(2, mainQuest);
                    update.setInt(3, nextStep);
                    update.executeUpdate();
                    update.setInt(2, boxQuestId);
                    update.setInt(3, L1Quest.QUEST_END);
                    update.executeUpdate();
                }
            });
            tx.onFailure(() -> {
                SC_SystemMessage.send(pc, "보물지도 교환이 확정되지 않았습니다.");
                SC_NPCTalkReturn.send(pc, npcObjectId, "maptbox0");
            });
            final String npcName = npc.getNpcTemplate().get_name();
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(mainQuest, nextStep);
                pc.getQuest().applyCommittedStepIfLoaded(boxQuestId, L1Quest.QUEST_END);
                SC_ServerMessage.send(pc, 143, npcName, reward.getItem().getName());
                SC_NPCTalkReturn.send(pc, npcObjectId, successPage);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "보물지도 교환을 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npcObjectId, "maptbox0");
        }
    }

    /** Keplisha horoscope: charge both ingredients and grant the ticket atomically.
     * On success the legacy two RNG draws and horoscope page are unchanged.
     */
    static void keplishaHoroscopeReceipt(L1PcInstance pc, L1NpcInstance npc) {
        if (pc.getInventory().checkItem(41314)) return;
        if (!pc.getInventory().checkItem(L1ItemId.ADENA, 1000)
                || !pc.getInventory().checkItem(41313, 1)) {
            SC_NPCTalkReturn.send(pc, npc.getId(), "keplisha8");
            return;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (!tx.debit(L1ItemId.ADENA, 1000) || !tx.debit(41313, 1)) {
                SC_NPCTalkReturn.send(pc, npc.getId(), "keplisha8");
                return;
            }
            final L1ItemInstance reward = tx.grant(41314, 1);
            if (reward == null) {
                SC_NPCTalkReturn.send(pc, npc.getId(), "keplisha8");
                return;
            }
            final int variant = HtmlCmdHandler._random.nextInt(3);
            final int detail = HtmlCmdHandler._random.nextInt(100) + 1;
            final String page = (variant == 0 ? "horosa" : variant == 1 ? "horosb" : "horosc") + detail;
            final String npcName = npc.getNpcTemplate().get_name();
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), "keplisha8"));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, npcName, reward.getItem().getName());
                SC_NPCTalkReturn.send(pc, npc.getId(), page);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "점괘 교환을 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npc.getId(), "keplisha8");
        }
    }

    /** Dragon-eye unseal: material, adena and reward must commit together.
     * Reuses the existing item transaction instead of separate consumeItem/storeItem calls.
     */
    static void dragonEyeExchange(L1PcInstance pc, L1NpcInstance npc,
            int materialId, int count, int adena, int rewardId,
            String failurePage, boolean showResult) {
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (!tx.debit(materialId, count) || !tx.debit(L1ItemId.ADENA, adena)) {
                SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                return;
            }
            final L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) {
                SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                return;
            }
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), failurePage));
            tx.commitAfterInventoryRelease(() -> {
                if (showResult) {
                    SC_ServerMessage.send(pc, 403, reward.getLogName());
                    SC_NPCTalkReturn.send(pc, npc.getId(), "");
                }
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "마안 거래를 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
        }
    }

    /** Higher-tier eyes: materials and adena are always spent on a valid roll,
     * while a successful roll also grants the new eye within the SAME receipt.
     */
    static void dragonEyeUpgrade(L1PcInstance pc, L1NpcInstance npc,
            int firstMaterial, int secondMaterial, int rewardId, int percent) {
        if (pc.getInventory().checkItem(rewardId)) {
            SC_NPCTalkReturn.send(pc, npc.getId(), "sherme0");
            return;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (!tx.debit(firstMaterial, 1) || !tx.debit(secondMaterial, 1)
                    || !tx.debit(L1ItemId.ADENA, 200000)) {
                SC_NPCTalkReturn.send(pc, npc.getId(), "sherme1");
                return;
            }
            final boolean success = HtmlCmdHandler._random.nextInt(100) + 1 <= percent;
            final L1ItemInstance reward = success ? tx.grant(rewardId, 1) : null;
            if (success && reward == null) {
                SC_NPCTalkReturn.send(pc, npc.getId(), "sherme1");
                return;
            }
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), "sherme1"));
            tx.commitAfterInventoryRelease(() -> {
                if (success) {
                    SC_ServerMessage.send(pc, 403, reward.getLogName());
                    SC_NPCTalkReturn.send(pc, npc.getId(), "");
                } else {
                    SC_NPCTalkReturn.send(pc, npc.getId(), "sherme5");
                }
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "마안 합성 거래를 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npc.getId(), "sherme1");
        }
    }

    /** NPC one-input purchases: the cost and reward must share one receipt.
     * Success messages are emitted only after durable inventory publication.
     */
    static void npcSingleMaterialExchange(L1PcInstance pc, L1NpcInstance npc,
            int materialId, int count, int rewardId, String successPage,
            String failurePage, String fixedRewardName) {
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (!tx.debit(materialId, count)) {
                SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                return;
            }
            final L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) {
                SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                return;
            }
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), failurePage));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 403,
                        fixedRewardName == null ? reward.getLogName() : fixedRewardName);
                if (successPage != null) SC_NPCTalkReturn.send(pc, npc.getId(), successPage);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 교환을 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
        }
    }

    /** Maenob floor teleport: 300 adena and two floor tokens share one receipt;
     * the world transition cannot start until the item debit is durable.
     */
    static void npcPaidFloorTeleport(L1PcInstance pc, L1NpcInstance npc,
            int floorToken, int x, int y, int destinationMap) {
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (!tx.debit(L1ItemId.ADENA, 300) || !tx.debit(floorToken, 2)) {
                SC_NPCTalkReturn.send(pc, npc.getId(), "maetnob2");
                return;
            }
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), "maetnob2"));
            tx.commitAfterInventoryRelease(() -> {
                pc.start_teleport(x, y, destinationMap, 5, 169, true, false);
                SC_NPCTalkReturn.send(pc, npc.getId(), "");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "탑 이동 거래를 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npc.getId(), "maetnob2");
        }
    }

    /** NPC starter quest gifts: exactly one durable award per expected quest stage. */
    static void questGrantOnce(L1PcInstance pc, L1NpcInstance npc,
            int questId, int expectedStep, int nextStep, int itemId, int count, String page) {
        if (pc.getQuest().get_step(questId) != expectedStep) {
            SC_NPCTalkReturn.send(pc, npc.getId(), page);
            return;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, questId)) {
            final L1ItemInstance reward = tx.grant(itemId, count);
            if (reward == null) {
                SC_SystemMessage.send(pc, "퀘스트 아이템을 수령할 수 없습니다.");
                return;
            }
            stageQuestStep(tx, pc, questId, expectedStep, nextStep, 0);
            tx.onFailure(() -> SC_SystemMessage.send(pc, "퀘스트 아이템 지급이 확정되지 않았습니다."));
            final String npcName = npc.getNpcTemplate().get_name();
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, nextStep);
                SC_ServerMessage.send(pc, 143, npcName, reward.getItem().getName());
                SC_NPCTalkReturn.send(pc, npc.getId(), page);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템 지급을 진행할 수 없습니다.");
        }
    }

    /** NPC 71056: all three Simizz clues and the reward share one receipt. */
    static void simizzReceipt(L1PcInstance pc, L1NpcInstance npc) {
        final int questId = L1Quest.QUEST_SIMIZZ;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, questId)) {
            if (!tx.debit(40661, 1) || !tx.debit(40662, 1) || !tx.debit(40663, 1)) {
                SC_NPCTalkReturn.send(pc, npc.getId(), "SIMIZZ9");
                return;
            }
            final L1ItemInstance reward = tx.grant(20044, 1);
            if (reward == null) {
                SC_NPCTalkReturn.send(pc, npc.getId(), "SIMIZZ9");
                return;
            }
            stageQuestStep(tx, pc, questId, pc.getQuest().get_step(questId), 2, 0);
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), "SIMIZZ9"));
            final String npcName = npc.getNpcTemplate().get_name();
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, 2);
                SC_ServerMessage.send(pc, 143, npcName, reward.getItem().getName());
                SC_NPCTalkReturn.send(pc, npc.getId(), "SIMIZZ8");
            });
        } catch (RuntimeException failure) {
            SC_NPCTalkReturn.send(pc, npc.getId(), "SIMIZZ9");
        }
    }

    /** NPC 83014: multiple eligible buyback lines form ONE durable debit+grant. */
    static boolean handleBlessingBuyback(L1PcInstance pc, L1NpcInstance npc, String action) {
        final boolean firstGroup = action.equalsIgnoreCase("1");
        final boolean secondGroup = action.equalsIgnoreCase("2");
        final boolean thirdGroup = action.equalsIgnoreCase("3");
        if (!firstGroup && !secondGroup && !thirdGroup) return false;
        final int firstId = firstGroup ? 510012 : secondGroup ? 3000128 : 30104;
        final int secondId = firstGroup ? 510013 : 3000129;
        final int thirdId = firstGroup ? 510014 : 3000130;
        final boolean first = pc.getInventory().checkItem(firstId, 300);
        final boolean second = !thirdGroup && pc.getInventory().checkItem(secondId, 300);
        final boolean third = !thirdGroup && pc.getInventory().checkItem(thirdId, 300);
        if (!first && !second && !third) {
            pc.sendPackets("매입가능한 품목이 부족 하다구!");
            if (thirdGroup) SC_NPCTalkReturn.send(pc, npc.getId(), "");
            return true;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if ((first && !tx.debit(firstId, 300)) || (second && !tx.debit(secondId, 300))
                    || (third && !tx.debit(thirdId, 300))
                    || tx.grant(400254, (first ? 1 : 0) + (second ? 1 : 0) + (third ? 1 : 0)) == null) {
                pc.sendPackets("매입 품목 또는 수표 지급 공간이 부족합니다.");
                return true;
            }
            tx.onFailure(() -> pc.sendPackets("매입 거래가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (first) pc.sendPackets(firstGroup
                        ? "미소피아(성장의축복) 300개 매입 완료 / 수표 1장 지급"
                        : secondGroup ? "메티스의 축복 주문서 300개 매입 완료 / 수표 1장 지급"
                        : "코마의 축복 코인 300개 매입 완료 / 수표 1장 지급");
                if (second) pc.sendPackets(firstGroup
                        ? "미소피아(방어의축복) 300개 매입 완료 / 수표 1장 지급"
                        : "메티스의 정성스런 스프 300개 매입 완료 / 수표 1장 지급");
                if (third) pc.sendPackets(firstGroup
                        ? "미소피아(공격의축복) 300개 매입 완료 / 수표 1장 지급"
                        : "메티스의 정성스런 요리 300개 매입 완료 / 수표 1장 지급");
                SC_NPCTalkReturn.send(pc, npc.getId(), "");
            });
        } catch (RuntimeException failure) {
            pc.sendPackets("매입 거래를 진행할 수 없습니다.");
        }
        return true;
    }

    /** NPC 71090: material receipt and teleport succeed together, not separately. */
    static boolean handleCrystalKeeper(L1PcInstance pc, L1NpcInstance npc, String action) {
        if (action.equalsIgnoreCase("a")) {
            crystalKeeperGrant(pc, npc);
            return true;
        }
        final boolean leave = action.equalsIgnoreCase("c");
        if (!leave && !action.equalsIgnoreCase("b")) return false;
        if (pc.getInventory().checkEquipped(246) || pc.getInventory().checkEquipped(247)
                || pc.getInventory().checkEquipped(248) || pc.getInventory().checkEquipped(249)) {
            SC_NPCTalkReturn.send(pc, npc.getId(), "jcrystal5");
            return true;
        }
        if (!leave && pc.getInventory().checkItem(40660)) {
            SC_NPCTalkReturn.send(pc, npc.getId(), "jcrystal4");
            return true;
        }
        crystalKeeperPortal(pc, npc, leave);
        return true;
    }

    static void crystalKeeperPortal(L1PcInstance pc, L1NpcInstance npc, boolean leave) {
        final int questId = L1Quest.QUEST_CRYSTAL;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, questId)) {
            // Reclaim existing crystal stones only on the return route.
            final L1ItemInstance stones = leave ? pc.getInventory().findItemId(40660) : null;
            if ((stones != null && stones.getCount() > 0 && !tx.debit(stones, stones.getCount()))
                    || !tx.debit(246, 1) || !tx.debit(247, 1) || !tx.debit(248, 1)
                    || !tx.debit(249, 1) || !tx.debit(40620, 1)) {
                SC_SystemMessage.send(pc, "퀘스트 이동에 필요한 아이템이 부족합니다.");
                SC_NPCTalkReturn.send(pc, npc.getId(), "");
                return;
            }
            final int step = leave ? 0 : 2;
            stageQuestStep(tx, pc, questId, pc.getQuest().get_step(questId), step, 0);
            tx.onFailure(() -> SC_SystemMessage.send(pc, "퀘스트 이동 아이템 거래가 취소되었습니다."));
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, step);
                if (leave) pc.start_teleport(32736, 32800, 483, 5, 169, true, false);
                else pc.start_teleport(32801, 32895, 483, 5, 169, true, false);
                SC_NPCTalkReturn.send(pc, npc.getId(), "");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 이동 아이템 거래를 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npc.getId(), "");
        }
    }

    /** Original five reward IDs and counts preserved, now granted atomically once. */
    static void crystalKeeperGrant(L1PcInstance pc, L1NpcInstance npc) {
        final int questId = L1Quest.QUEST_CRYSTAL;
        if (pc.getQuest().get_step(questId) != 0) {
            SC_NPCTalkReturn.send(pc, npc.getId(), "");
            return;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, questId)) {
            final L1ItemInstance first = tx.grant(246, 1);
            final L1ItemInstance second = tx.grant(247, 1);
            final L1ItemInstance third = tx.grant(248, 1);
            final L1ItemInstance fourth = tx.grant(249, 1);
            final L1ItemInstance stones = tx.grant(40660, 5);
            if (first == null || second == null || third == null || fourth == null || stones == null) {
                SC_SystemMessage.send(pc, "퀘스트 아이템 지급 설정을 확인해 주세요.");
                SC_NPCTalkReturn.send(pc, npc.getId(), "");
                return;
            }
            stageQuestStep(tx, pc, questId, 0, 1, 0);
            tx.onFailure(() -> SC_SystemMessage.send(pc, "퀘스트 아이템 지급이 확정되지 않았습니다."));
            final String npcName = npc.getNpcTemplate().get_name();
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, 1);
                SC_ServerMessage.send(pc, 143, npcName, first.getItem().getName());
                SC_ServerMessage.send(pc, 143, npcName, second.getItem().getName());
                SC_ServerMessage.send(pc, 143, npcName, third.getItem().getName());
                SC_ServerMessage.send(pc, 143, npcName, fourth.getItem().getName());
                SC_ServerMessage.send(pc, 143, npcName, stones.getItem().getName());
                SC_NPCTalkReturn.send(pc, npc.getId(), "");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템을 지급할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, npc.getId(), "");
        }
    }

    /** The crystal quest exit must not teleport or advance after a failed debit. */
    static boolean handleCrystalExit(L1PcInstance pc, L1NpcInstance npc, String action) {
        if (!action.equalsIgnoreCase("a")) return false;
        questItemExchange(pc, npc, L1Quest.QUEST_CRYSTAL,
                pc.getQuest().get_step(L1Quest.QUEST_CRYSTAL), L1Quest.QUEST_END,
                40654, 1, 0, 0, 0, "", "",
                () -> pc.start_teleport(32744, 32927, 483, 5, 169, true, false));
        return true;
    }

    /** Lizard rewards are earned only if material debit, quest step and item grant commit. */
    static boolean handleLizardQuest(L1PcInstance pc, L1NpcInstance npc, String action) {
        if (action.equalsIgnoreCase("A")) {
            pc.getQuest().set_step(L1Quest.QUEST_LIZARD, 1);
            SC_NPCTalkReturn.send(pc, npc.getId(), "lelder5");
            return true;
        }
        if (action.equalsIgnoreCase("B")) {
            questItemExchange(pc, npc, L1Quest.QUEST_LIZARD,
                    pc.getQuest().get_step(L1Quest.QUEST_LIZARD), 3,
                    40633, 1, 0, 0, 0, "lelder10", "lelder10");
            return true;
        }
        if (action.equalsIgnoreCase("C")) {
            if (!pc.getInventory().checkItemNotEquipped(40634, 1)) {
                final L1Item required = ItemTable.getInstance().getTemplate(40634);
                if (required != null) SC_ServerMessage.send(pc, 337, required.getName());
                SC_NPCTalkReturn.send(pc, npc.getId(), "lelder13");
                return true;
            }
            questItemExchange(pc, npc, L1Quest.QUEST_LIZARD,
                    pc.getQuest().get_step(L1Quest.QUEST_LIZARD), L1Quest.QUEST_END,
                    40634, 1, 0, 20167, 0, "lelder13", "lelder13");
            return true;
        }
        return false;
    }

    static boolean handleTionQuest(L1PcInstance pc, L1NpcInstance npc, String action) {
        final int step = pc.getQuest().get_step(71198);
        if (action.equalsIgnoreCase("A")) {
            if (step == 0 && !pc.getInventory().checkItem(21059, 1))
                questItemExchange(pc, npc, 71198, 0, 1, 41339, 5, 0, 41340, 0, "tion4", "tion9");
            return true;
        } else if (action.equalsIgnoreCase("B")) {
            if (step == 1 && !pc.getInventory().checkItem(21059, 1))
                questItemExchange(pc, npc, 71198, 1, 2, 41341, 1, 0, 0, 0, "tion5", "tion10");
            return true;
        } else if (action.equalsIgnoreCase("C")) {
            if (step == 2 && !pc.getInventory().checkItem(21059, 1))
                questItemExchange(pc, npc, 71198, 2, 3, 41343, 1, 0, 21057, 0, "tion6", "tion12");
            return true;
        } else if (action.equalsIgnoreCase("D")) {
            if (step == 3 && !pc.getInventory().checkItem(21059, 1))
                questItemExchange(pc, npc, 71198, 3, 4, 41344, 1, 21057, 21058, 0, "tion7", "tion13");
            return true;
        } else if (action.equalsIgnoreCase("E")) {
            if (step == 4 && !pc.getInventory().checkItem(21059, 1))
                questItemExchange(pc, npc, 71198, 4, 0, 41345, 1, 21058, 21059, 71199, "tion8", "tion15");
            return true;
        }
        return false;
    }

    static boolean handleJeronQuest(L1PcInstance pc, L1NpcInstance npc, String action) {
        if (action.equalsIgnoreCase("A")) {
            if (pc.getQuest().get_step(71199) != 0 || pc.getInventory().checkItem(21059, 1)) return true;
            if (pc.getInventory().checkItem(41340, 1)) {
                pc.getQuest().set_step(71199, 1);
                SC_NPCTalkReturn.send(pc, npc.getId(), "jeron2");
            } else {
                SC_NPCTalkReturn.send(pc, npc.getId(), "jeron10");
            }
            return true;
        } else if (action.equalsIgnoreCase("B")) {
            if (pc.getQuest().get_step(71199) == 1 && !pc.getInventory().checkItem(21059, 1))
                questItemExchange(pc, npc, 71199, 1, 255, 40308, 1000000,
                        41340, 41341, 0, "jeron6", "jeron8");
            return true;
        } else if (action.equalsIgnoreCase("C")) {
            if (pc.getQuest().get_step(71199) == 1 && !pc.getInventory().checkItem(21059, 1))
                questItemExchange(pc, npc, 71199, 1, 255, 41342, 1,
                        41340, 41341, 0, "jeron5", "jeron9");
            return true;
        }
        return false;
    }


    /** Phase49: a one-for-one NPC exchange is one durable debit/grant. */
    static void npcItemExchange(L1PcInstance pc, L1NpcInstance npc,
            int materialId, int rewardId, String successPage, String failurePage) {
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (!tx.debit(materialId, 1) || tx.grant(rewardId, 1) == null) {
                SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                return;
            }
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), failurePage));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, npc.getId(), successPage));
        } catch (RuntimeException failure) {
            SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
        }
    }

    /** Phase50/51: a quest debit and step update share a single InnoDB receipt. */
    static void npcQuestDebit(L1PcInstance pc, L1NpcInstance npc,
            int questId, int expectedStep, int nextStep, int materialId, int count,
            String successPage, String failurePage) {
        if (pc.getQuest().get_step(questId) != expectedStep) {
            SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
            return;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, questId)) {
            if (!tx.debit(materialId, count)) {
                SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                return;
            }
            stageQuestStep(tx, pc, questId, expectedStep, nextStep, 0);
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), failurePage));
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, nextStep);
                SC_NPCTalkReturn.send(pc, npc.getId(), successPage);
            });
        } catch (RuntimeException failure) {
            SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
        }
    }

    /** Phase52/53: keep both level-15 quest grants and exchanges in one
     *  receipt per click; no per-request reward/material arrays are allocated. */
    static void level15ItemReceipt(L1PcInstance pc, L1NpcInstance npc,
            boolean silrein, boolean start) {
        final int questId = L1Quest.QUEST_LEVEL15;
        final int expectedStep = start ? 0 : 1;
        final int nextStep = start ? 1 : L1Quest.QUEST_END;
        final String successPage = silrein ? (start ? "silrein4" : "silrein7")
                : (start ? "prokel3" : "prokel5");
        final String failurePage = silrein ? (start ? "silrein4" : "silrein8")
                : (start ? "prokel3" : "prokel6");
        if (pc.getQuest().get_step(questId) != expectedStep) {
            SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
            return;
        }
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(pc, questId)) {
            if (!start) {
                final boolean ready = silrein
                        ? tx.debit(210091, 10) && tx.debit(40510, 1)
                                && tx.debit(40511, 1) && tx.debit(40512, 1)
                                && tx.debit(41080, 1)
                        : tx.debit(210088, 1) && tx.debit(210089, 1)
                                && tx.debit(210090, 1);
                if (!ready) {
                    SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                    return;
                }
            }
            final L1ItemInstance first = tx.grant(silrein
                    ? (start ? 210092 : 505) : (start ? 210087 : 502),
                    silrein && start ? 5 : 1);
            final L1ItemInstance second = silrein
                    ? tx.grant(start ? 210093 : 210004, 1)
                    : (start ? null : tx.grant(210020, 1));
            if (first == null || (silrein || !start) && second == null) {
                SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
                return;
            }
            stageQuestStep(tx, pc, questId, expectedStep, nextStep, 0);
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npc.getId(), failurePage));
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, nextStep);
                SC_NPCTalkReturn.send(pc, npc.getId(), successPage);
            });
        } catch (RuntimeException failure) {
            SC_NPCTalkReturn.send(pc, npc.getId(), failurePage);
        }
    }



    static String oldBook(L1PcInstance pc, String s, String htmlid) {
		if (s.equalsIgnoreCase("a")) {
			if (pc.getInventory().checkItem(60032)) {
				SC_ChatPacket.sendDirect(pc, pc, "이미 낡은 고서를 가지고있네요");
				htmlid = "";
			} else {
				if (pc.getInventory().storeItem(60032, 1) != null) {
					htmlid = "oldbook2";
				}
			}
		}
		// 수룡의던전
        return htmlid;
    }

    /** Claim the Hamo pouch and quest completion in one existing durable receipt. */
    static String hamo(L1PcInstance pc, int npcObjectId, String s, String htmlid) {
        if (!s.equalsIgnoreCase("a")) return htmlid;
        if (pc.getLevel() < 60) {
            SC_SystemMessage.send(pc, "60이상의 캐릭터만 받을 수 있습니다.");
            return "hamo3";
        }
        final int questId = L1Quest.QUEST_HAMO;
        final int expected = pc.getQuest().get_step(questId);
        if (pc.getInventory().checkItem(820000) || expected == L1Quest.QUEST_END) return "hamo1";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, questId)) {
            if (tx.grant(820000, 1) == null) {
                SC_SystemMessage.send(pc, "퀘스트 아이템을 수령할 수 없습니다.");
                return htmlid;
            }
            stageQuestStep(tx, pc, questId, expected, L1Quest.QUEST_END, 0);
            tx.onFailure(() -> SC_SystemMessage.send(pc, "퀘스트 아이템 지급이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(questId, L1Quest.QUEST_END);
                SC_NPCTalkReturn.send(pc, npcObjectId, "");
            });
            return null;
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템 지급을 진행할 수 없습니다.");
            return htmlid;
        }
    }

    static String claudiaScroll(L1PcInstance pc, String s, String htmlid) {
		if (s.equalsIgnoreCase("a")) {
			if (pc.getInventory().checkItem(99115, 5)) {
				SC_SystemMessage.send(pc, "'클라우디아 이동 주문서' 을 소지하고 계십니다.");
				htmlid = "";
			} else {
				if (pc.getInventory().storeItem(99115, 5) != null) {
					SC_SystemMessage.send(pc, "'클라우디아 이동 주문서'을 지급 하였습니다..");
				}
			}
		}
		// 군터
        return htmlid;
    }


    // Required dialogue steps from L1MerchantInstance.
    private static final int[][] MOONBOW_COSTS = {
        {40068,1}, {}, {41348,1}, {40048,10,40049,10,40050,10,40051,10},
        {40514,10,41353,1}, {41349,1},
        {41346,1,41351,1,40651,30,40643,30,40645,30,40618,30,40676,30},
        {41350,1,41347,1,40491,30,40495,40,100,1,40509,12,40052,1,40053,1,40054,1,40055,1}
    };
    private static final int[][] MOONBOW_REWARDS = {
        {}, {41346,41348}, {}, {41353}, {41354}, {41351}, {41350,41347}, {205}
    };
    private static final String[] MOONBOW_SUCCESS = {
        "robinhood4", "robinhood13", "zybril12", "zybril13", "zybril9", "zybril10", "robinhood10", "robinhood12"
    };
    private static final String[] MOONBOW_FAILURE = {
        "robinhood19", "", "zybril11", "", "zybril13", "zybril14", "robinhood15", "robinhood17"
    };
    private static final String[] MOONBOW_NOTICE = {
        null, "로빈후드의 메모지와 소개장을 얻었습니다.", null, "에바의 단검을 얻었습니다.",
        "신성한 에바의 물을 얻었습니다.", "달빛의 정기를 얻었습니다.",
        "로빈후드의 반지와 메모지를 얻었습니다.", "달의 장궁을 얻었습니다."
    };

    /** Case-sensitive legacy commands; reject out-of-order or repeated steps. */
    static void moonbow(L1PcInstance pc, L1NpcInstance npc, String command, boolean zybril) {
        final int objectId = npc.getId();
        final int step;
        if (command.length() != 1) step = -1;
        else if (zybril) {
            char c = command.charAt(0);
            step = c >= 'A' && c <= 'D' ? c - 'A' + 2 : -1;
        } else {
            step = switch (command.charAt(0)) {
                case 'A' -> 0;
                case 'B' -> 1;
                case 'C' -> 6;
                case 'E' -> 7;
                default -> -1;
            };
        }
        final int quest = L1Quest.QUEST_MOONBOW;
        if (step < 0 || pc.getQuest().get_step(quest) != step) {
            SC_NPCTalkReturn.send(pc, objectId, "");
            return;
        }
        final int next = (step + 1) & 7;
        final String failurePage = MOONBOW_FAILURE[step];
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, quest)) {
            final int[] costs = MOONBOW_COSTS[step];
            for (int i = 0; i < costs.length; i += 2) {
                if (!tx.debit(costs[i], costs[i + 1])) {
                    SC_NPCTalkReturn.send(pc, objectId, failurePage);
                    return;
                }
            }
            for (int id : MOONBOW_REWARDS[step]) {
                if (tx.grant(id, 1) == null) {
                    SC_SystemMessage.send(pc, "퀘스트 아이템을 수령할 수 없습니다.");
                    SC_NPCTalkReturn.send(pc, objectId, failurePage);
                    return;
                }
            }
            stageQuestStep(tx, pc, quest, step, next, 0);
            tx.onFailure(() -> {
                SC_SystemMessage.send(pc, "퀘스트 아이템 거래가 확정되지 않았습니다.");
                SC_NPCTalkReturn.send(pc, objectId, failurePage);
            });
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(quest, next);
                if (MOONBOW_NOTICE[step] != null) SC_SystemMessage.send(pc, MOONBOW_NOTICE[step]);
                SC_NPCTalkReturn.send(pc, objectId, MOONBOW_SUCCESS[step]);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템 거래를 진행할 수 없습니다.");
            SC_NPCTalkReturn.send(pc, objectId, failurePage);
        }
    }

    static String 마르바(L1PcInstance pc, L1Object obj, String s) {
		String htmlid = "";
		L1NpcInstance npc = (L1NpcInstance) obj;
		if (pc.getInventory().checkItem(40665)) {
			htmlid = "marba17";
			if (s.equalsIgnoreCase("B")) {
				htmlid = "marba7";
				if (pc.getInventory().checkItem(214) && pc.getInventory().checkItem(20389)
						&& pc.getInventory().checkItem(20393) && pc.getInventory().checkItem(20401)
						&& pc.getInventory().checkItem(20406) && pc.getInventory().checkItem(20409)) {
					htmlid = "marba15";
				}
			}
		} else if (s.equalsIgnoreCase("A")) {
			if (pc.getInventory().checkItem(40637)) {
				htmlid = "marba20";
			} else {
				L1ItemInstance item = pc.getInventory().storeItem(40637, 1);
				if (item == null) return htmlid;
				String itemName = item.getItem().getName();
				String npcName = npc.getNpcTemplate().get_name();
				SC_ServerMessage.send(pc, 143, npcName, itemName);
				htmlid = "marba6";
			}
		}
		return htmlid;
	}

    static String karmaLevelToHtmlId(int level) {
		if (level == 0 || level < -7 || 7 < level) {
			return "";
		}
		String htmlid = "";
		if (0 < level) {
			htmlid = "vbk" + level;
		} else if (level < 0) {
			htmlid = "vyk" + Math.abs(level);
		}
		return htmlid;
	}

    static void getBloodCrystalByKarma(L1PcInstance pc, L1NpcInstance npc, String command) {
        exchangeKarmaCrystal(pc, npc, command, true);
    }

    static void getSoulCrystalByKarma(L1PcInstance pc, L1NpcInstance npc, String command) {
        exchangeKarmaCrystal(pc, npc, command, false);
    }

    private static void exchangeKarmaCrystal(L1PcInstance pc, L1NpcInstance npc, String command, boolean blood) {
        if (command == null) return;
        final int count = switch (command) {
            case "1" -> 1;
            case "2" -> 10;
            case "3" -> 100;
            default -> 0;
        };
        if (count == 0) return;
        final int delta = (int) ((blood ? 500 : -500) * count * Config.RATE_KARMA);
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            final int karma = pc.getKarma();
            if (blood ? karma > -10_000_000 : karma < 10_000_000) return;
            final L1ItemInstance reward = tx.grant(blood ? 40718 : 40678, count);
            if (reward == null) {
                sendKarmaExchangeFailure(pc);
                return;
            }
            final String npcName = npc.getNpcTemplate().get_name();
            final String rewardName = reward.getLogName();
            tx.onFailure(() -> sendKarmaExchangeFailure(pc));
            tx.commitAfterInventoryRelease(() -> {
                pc.addKarma(delta);
                SC_Karma.send(pc);
                SC_ServerMessage.send(pc, 143, npcName, rewardName);
                SC_ServerMessage.send(pc, blood ? 1081 : 1080);
            });
        } catch (RuntimeException failure) {
            sendKarmaExchangeFailure(pc);
        }
    }

    private static void sendKarmaExchangeFailure(L1PcInstance pc) {
        SC_SystemMessage.send(pc, "결정 교환을 완료하지 못했습니다.");
    }

    /** Orc village quest tokens, preserving the original NPC-specific pages. */
    static String orcQuestToken(L1PcInstance pc, L1NpcInstance npc, int npcId, String command, String initialPage) {
        switch (npcId) {
        case 71038: return orcQuestToken(pc, npc, command, 41060, "orcfnoname9", "orcfnoname11", initialPage);
        case 71040: return orcQuestToken(pc, npc, command, 41065, "orcfnoa4", "orcfnoa7", initialPage);
        case 71041: return orcQuestToken(pc, npc, command, 41064, "orcfhuwoomo4", "orcfhuwoomo6", initialPage);
        case 71042: return orcQuestToken(pc, npc, command, 41062, "orcfbakumo4", "orcfbakumo6", initialPage);
        case 71043: return orcQuestToken(pc, npc, command, 41063, "orcfbuka4", "orcfbuka6", initialPage);
        case 71044: return orcQuestToken(pc, npc, command, 41061, "orcfkame4", "orcfkame6", initialPage);
        case 71039: return command.equalsIgnoreCase("teleportURL") ? "orcfbuwoo2" : initialPage;
        default: return initialPage;
        }
    }

    private static String orcQuestToken(L1PcInstance pc, L1NpcInstance npc, String command,
            int itemId, String grantPage, String redeemPage, String initialPage) {
        if (command.equalsIgnoreCase("A")) {
            L1ItemInstance item = pc.getInventory().storeItem(itemId, 1);
            if (item == null) return null;
            SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getItem().getName());
            return grantPage;
        }
        if (command.equalsIgnoreCase("Z") && pc.getInventory().consumeItem(itemId, 1)) {
            return redeemPage;
        }
        return initialPage;
    }

    /** Initial spy supplies and quest step are one receipt. Step 1 may re-morph,
     * but never grants the supplies again. Explicit reset permits a fresh start. */
    static void spyStart(L1PcInstance pc, CIOClient client, L1NpcInstance npc, boolean shadow) {
        final int quest=shadow ? L1Quest.QUEST_SHADOWS : L1Quest.QUEST_DESIRE;
        final int step=pc.getQuest().get_step(quest);
        final int morph=shadow ? 6035 : 6034;
        final int objectId=npc.getId();
        if (step != 0) {
            if (step == 1 && client.getActiveChar()==pc) NpcLocalCommands.poly(client,morph);
            SC_NPCTalkReturn.send(pc,objectId,"");
            return;
        }
        final int first=shadow ? 41123 : 41132;
        final String name=npc.getNpcTemplate().get_name();
        try (L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,quest)) {
            final L1ItemInstance a=tx.grant(first,1), b=tx.grant(first+1,1), c=tx.grant(first+2,1);
            if (a==null || b==null || c==null) {
                SC_SystemMessage.send(pc,"퀘스트 아이템을 수령할 수 없습니다.");
                return;
            }
            stageQuestStep(tx,pc,quest,0,1,0);
            tx.onFailure(() -> SC_SystemMessage.send(pc,"퀘스트 시작이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(quest,1);
                if (client.getActiveChar()==pc) NpcLocalCommands.poly(client,morph);
                SC_ServerMessage.send(pc,143,name,a.getItem().getName());
                SC_ServerMessage.send(pc,143,name,b.getItem().getName());
                SC_ServerMessage.send(pc,143,name,c.getItem().getName());
                SC_NPCTalkReturn.send(pc,objectId,"");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc,"퀘스트 시작을 진행할 수 없습니다.");
        }
    }

    static void spyReset(L1PcInstance pc, L1NpcInstance npc, boolean shadow) {
        final int quest=shadow ? L1Quest.QUEST_SHADOWS : L1Quest.QUEST_DESIRE;
        final int step=pc.getQuest().get_step(quest);
        final int first=shadow ? 41123 : 41132;
        final int objectId=npc.getId();
        if (step == L1Quest.QUEST_END) {
            SC_NPCTalkReturn.send(pc,objectId,"");
            return;
        }
        try (L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,quest)) {
            for (int id=first; id<first+7; id++) {
                if (pc.getInventory().checkItem(id,1) && !NpcExchangeCommands.debitLegacy(tx,pc,id,1)) {
                    SC_SystemMessage.send(pc,"퀘스트 아이템을 회수할 수 없습니다.");
                    return;
                }
            }
            stageQuestStep(tx,pc,quest,step,0,0);
            tx.onFailure(() -> SC_SystemMessage.send(pc,"퀘스트 초기화가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(quest,0);
                SC_NPCTalkReturn.send(pc,objectId,"");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc,"퀘스트 초기화를 진행할 수 없습니다.");
        }
    }

    /** Slot flag and all costs share the existing quest/inventory receipt. */
    static boolean unlockEquipmentSlot(L1PcInstance pc, String command) {
        final int quest, level, coupon, discounted, value, sound;
        final String failurePage;
        final boolean earring;
        if (command.equals("A")) {
            quest = L1Quest.QUEST_SLOT76; level = 76; coupon = 3000400;
            discounted = 5000000; value = 1; sound = 12003; failurePage = "slot6"; earring = false;
        } else if (command.equals("B")) {
            quest = L1Quest.QUEST_SLOT81; level = 81; coupon = 3000401;
            discounted = 15000000; value = 2; sound = 12003; failurePage = "slot6"; earring = false;
        } else if (command.equals("C") || command.equals("D")) {
            quest = L1Quest.QUEST_SLOT59; level = 59; coupon = 3000399;
            discounted = 1000000; value = 16; sound = 12004; failurePage = "slot11"; earring = true;
        } else if (command.equalsIgnoreCase("E")) {
            quest = L1Quest.QUEST_SLOT_SHOULD; level = 83; coupon = 3000402;
            discounted = 15000000; value = SC_ReturnedStat.OPEN_SLOT_SHOULD; sound = 12004; failurePage = "slot11"; earring = false;
        } else if (command.equalsIgnoreCase("F")) {
            quest = L1Quest.QUEST_SLOT_BADGE; level = 70; coupon = 3000403;
            discounted = 1000000; value = SC_ReturnedStat.OPEN_SLOT_BADGE; sound = 12004; failurePage = "slot11"; earring = false;
        } else return false;
        final int step = pc.getQuest().get_step(quest);
        if (step == L1Quest.QUEST_END) {
            SC_ServerMessage.send(pc, 3255);
            return true;
        }
        final boolean useCoupon = pc.getInventory().checkItem(coupon, 1)
                && pc.getInventory().checkItem(L1ItemId.ADENA, discounted);
        final int price = useCoupon ? discounted : discounted * 2;
        if (pc.getLevel() < level || !pc.getInventory().checkItem(L1ItemId.ADENA, price)) {
            SC_NPCTalkReturn.send(pc, pc.getId(), failurePage);
            return true;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 5162)) {
            if ((useCoupon && !NpcExchangeCommands.debitLegacy(tx, pc, coupon, 1))
                    || !NpcExchangeCommands.debitLegacy(tx, pc, L1ItemId.ADENA, price)) {
                SC_NPCTalkReturn.send(pc, pc.getId(), failurePage);
                return true;
            }
            stageQuestStep(tx, pc, quest, step, L1Quest.QUEST_END, 0);
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, pc.getId(), failurePage));
            tx.commitAfterInventoryRelease(() -> {
                pc.getQuest().applyCommittedStepIfLoaded(quest, L1Quest.QUEST_END);
                if (earring) SC_ReturnedStat.sendSlot(pc, 67, 1, value);
                SC_NPCTalkReturn.send(pc, pc.getId(), "slot9");
                if (!earring) SC_ReturnedStat.sendSlot(pc, SC_ReturnedStat.RING_RUNE_SLOT, SC_ReturnedStat.SUBTYPE_RING, value);
                SC_SkillSound.send(pc, pc.getId(), sound);
            });
        } catch (RuntimeException failure) {
            SC_NPCTalkReturn.send(pc, pc.getId(), failurePage);
        }
        return true;
    }

    /** Legacy free grants have no debit. Retain their original store policy and repeat
     * eligibility; don't emit successful HTML or dereference a missing reward. */
    static void grantLegacyNpcItem(L1PcInstance pc, L1NpcInstance npc, int itemId,
            int count, boolean onlyIfAbsent, String page) {
        if (onlyIfAbsent && pc.getInventory().checkItem(itemId, 1)) return;
        final L1ItemInstance item = pc.getInventory().storeItem(itemId, count);
        if (item == null) {
            SC_SystemMessage.send(pc, "아이템을 수령할 수 없습니다.");
            return;
        }
        SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getItem().getName());
        SC_NPCTalkReturn.send(pc, npc.getId(), page);
    }

    /** NPC 71005: one quest token buys 20 potions, or neither row changes. */
    static void jpePotionExchange(L1PcInstance pc, L1NpcInstance npc) {
        if (!pc.getInventory().checkItem(41213, 1)) return;
        final int objectId = npc.getId();
        final String name = npc.getNpcTemplate().get_name();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 71005)) {
            if (!NpcExchangeCommands.debitLegacy(tx, pc, 41213, 1)) return;
            final L1ItemInstance reward = tx.grant(40029, 20);
            if (reward == null) {
                SC_SystemMessage.send(pc, "아이템 교환을 진행할 수 없습니다.");
                return;
            }
            final String rewardName = reward.getItem().getName();
            tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 교환이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, name, rewardName + " (20)");
                SC_NPCTalkReturn.send(pc, objectId, "");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 교환을 진행할 수 없습니다.");
        }
    }

    /** Discard both existing documents in one receipt. */
    static void spyDiscard(L1PcInstance pc, L1NpcInstance npc, boolean shadow) {
        int first = shadow ? 41121 : 41130;
        int objectId = npc.getId();
        String page = shadow ? "minitos09" : "minicod09";
        if (!pc.getInventory().checkItem(first, 1) && !pc.getInventory().checkItem(first + 1, 1)) {
            SC_NPCTalkReturn.send(pc, objectId, page);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            for (int id = first; id <= first + 1; id++) {
                if (pc.getInventory().checkItem(id, 1) && !NpcExchangeCommands.debitLegacy(tx, pc, id, 1)) {
                    SC_SystemMessage.send(pc, "퀘스트 아이템을 회수할 수 없습니다.");
                    return;
                }
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "퀘스트 아이템 회수가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, objectId, page));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템을 회수할 수 없습니다.");
        }
    }

    /** A replacement contract is available if lost, not repeatedly while held. */
    static void spyGift(L1PcInstance pc, L1NpcInstance npc, boolean shadow) {
        int itemId = shadow ? 41121 : 41130;
        int objectId = npc.getId();
        if (pc.getInventory().checkItem(itemId, 1)) {
            SC_NPCTalkReturn.send(pc, objectId, "");
            return;
        }
        String name = npc.getNpcTemplate().get_name();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            L1ItemInstance reward = tx.grant(itemId, 1);
            if (reward == null) {
                SC_SystemMessage.send(pc, "퀘스트 아이템을 수령할 수 없습니다.");
                return;
            }
            String rewardName = reward.getItem().getName();
            tx.onFailure(() -> SC_SystemMessage.send(pc, "퀘스트 아이템 수령이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, name, rewardName);
                SC_NPCTalkReturn.send(pc, objectId, "");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템을 수령할 수 없습니다.");
        }
    }

    /** Keep the eligibility marker and all support items in the same inventory receipt. */
    static void veteranSupport(L1PcInstance pc, L1NpcInstance npc, boolean bundle) {
        final int objectId = npc.getId();
        if (bundle && pc.getLevel() < 75) {
            SC_NPCTalkReturn.send(pc, objectId, "lind_sm9");
            return;
        }
        if (pc.getInventory().checkItem(bundle ? 30070 : 200000, 1)) {
            SC_NPCTalkReturn.send(pc, objectId, bundle ? "lind_sm4" : "lind_sm5");
            return;
        }
        final String npcName = npc.getNpcTemplate().get_name();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 5112)) {
            final String boxName;
            final String markerName;
            if (bundle) {
                L1ItemInstance box = tx.grant(30071, 1);
                L1ItemInstance marker = tx.grant(30070, 1);
                if (box == null || marker == null) {
                    SC_SystemMessage.send(pc, "지원품을 수령할 수 없습니다.");
                    return;
                }
                boxName = box.getLogName();
                markerName = marker.getLogName();
            } else {
                boxName = null;
                markerName = null;
            }
            L1ItemInstance candle = tx.grant(200000, 1);
            if (candle == null) {
                SC_SystemMessage.send(pc, "지원품을 수령할 수 없습니다.");
                return;
            }
            final String candleName = candle.getLogName();
            tx.onFailure(() -> SC_SystemMessage.send(pc, "지원품 지급이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (bundle) {
                    SC_ServerMessage.send(pc, 143, npcName, boxName);
                    SC_ServerMessage.send(pc, 143, npcName, markerName);
                }
                SC_ServerMessage.send(pc, 143, npcName, candleName);
                SC_NPCTalkReturn.send(pc, objectId, bundle ? "lind_sm2" : "lind_sm6");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "지원품을 수령할 수 없습니다.");
        }
    }

    /** The three optional horoscope items are discarded together or not at all. */
    static void discardKeplisha(L1PcInstance pc, L1NpcInstance npc, String tail) {
        final int objectId = npc.getId();
        if (!pc.getInventory().checkItem(41312, 1) && !pc.getInventory().checkItem(41313, 1)
                && !pc.getInventory().checkItem(41314, 1)) {
            if (tail != null) SC_NPCTalkReturn.send(pc, objectId, tail);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 80079)) {
            for (int id = 41312; id <= 41314; id++) {
                if (pc.getInventory().checkItem(id, 1) && !NpcExchangeCommands.debitLegacy(tx, pc, id, 1)) {
                    SC_SystemMessage.send(pc, "퀘스트 아이템을 회수할 수 없습니다.");
                    return;
                }
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "퀘스트 아이템 회수가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, objectId, ""));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "퀘스트 아이템을 회수할 수 없습니다.");
        }
    }
    /** Repeatable replacement gifts must not escape to the floor or overlap a pending grant. */
    static void grantMissingNpcItem(L1PcInstance pc, L1NpcInstance npc, int itemId,
            String successPage, String alreadyPage, boolean announce) {
        final int objectId = npc.getId();
        final String npcName = npc.getNpcTemplate().get_name();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (pc.getInventory().checkItem(itemId, 1)) {
                if (alreadyPage != null) SC_NPCTalkReturn.send(pc, objectId, alreadyPage);
                return;
            }
            final L1ItemInstance reward = tx.grant(itemId, 1);
            if (reward == null) {
                SC_SystemMessage.send(pc, "아이템을 수령할 수 없습니다.");
                return;
            }
            final String rewardName = reward.getItem().getName();
            tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 지급이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (announce) SC_ServerMessage.send(pc, 143, npcName, rewardName);
                if (successPage != null) SC_NPCTalkReturn.send(pc, objectId, successPage);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템을 수령할 수 없습니다.");
        }
    }

    /** Free support grants must enter inventory, never the floor. */
    static void tutorialArmor(L1PcInstance pc, L1NpcInstance npc) {
        final int first = pc.isKnight() || pc.isCrown() || pc.isDarkelf() || pc.isDragonknight()
                ? 22300 : 22306;
        grantMissingRange(pc, npc, first, 5, 1, "tutorrw5", "tutorrw6");
    }

    static void iceCaveSupplies(L1PcInstance pc, L1NpcInstance npc, boolean wand) {
        grantMissingRange(pc, npc, wand ? 30055 : 30056, 1, wand ? 120 : 100,
                wand ? "icqwand2" : "icqwand3", "icqwand4");
    }

    private static void grantMissingRange(L1PcInstance pc, L1NpcInstance npc, int first,
            int size, int quantity, String successPage, String alreadyPage) {
        final int objectId = npc.getId();
        int missing = 0;
        for (int i = 0; i < size; i++) {
            if (!pc.getInventory().checkItem(first + i, 1)) missing |= 1 << i;
        }
        if (missing == 0) {
            SC_NPCTalkReturn.send(pc, objectId, alreadyPage);
            return;
        }
        final String npcName = npc.getNpcTemplate().get_name();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            final String[] names = new String[Integer.bitCount(missing)];
            int n = 0;
            for (int i = 0; i < size; i++) {
                if ((missing & (1 << i)) == 0 || pc.getInventory().checkItem(first + i, 1)) continue;
                final L1ItemInstance reward = tx.grant(first + i, quantity);
                if (reward == null) {
                    SC_SystemMessage.send(pc, "지원품을 수령할 수 없습니다.");
                    return;
                }
                names[n++] = reward.getLogName();
            }
            if (n == 0) {
                SC_NPCTalkReturn.send(pc, objectId, alreadyPage);
                return;
            }
            final int count = n;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "지원품 지급이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                for (int i = 0; i < count; i++) SC_ServerMessage.send(pc, 143, npcName, names[i]);
                SC_NPCTalkReturn.send(pc, objectId, successPage);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "지원품을 수령할 수 없습니다.");
        }
    }
}
