import java.sql.*;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class QuestLogic {
    private static void questItemExchange(L1PcInstance pc, L1NpcInstance npc,
            int questId, int expectedStep, int nextStep, int materialId, int materialCount,
            int additionalMaterialId, int rewardId, int resetQuestId,
            String successPage, String failurePage) {
        questItemExchange(pc, npc, questId, expectedStep, nextStep, materialId, materialCount,
                additionalMaterialId, rewardId, resetQuestId, successPage, failurePage, null);
    }

    private static void questItemExchange(L1PcInstance pc, L1NpcInstance npc,
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

    private static void stageQuestStep(l1j.server.server.model.L1InventoryTransaction tx,
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

    private static void questGrantOnce(L1PcInstance pc, L1NpcInstance npc,
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

    private static void simizzReceipt(L1PcInstance pc, L1NpcInstance npc) {
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

    private static boolean handleBlessingBuyback(L1PcInstance pc, L1NpcInstance npc, String action) {
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

    private static boolean handleCrystalKeeper(L1PcInstance pc, L1NpcInstance npc, String action) {
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

    private static void crystalKeeperPortal(L1PcInstance pc, L1NpcInstance npc, boolean leave) {
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

    private static void crystalKeeperGrant(L1PcInstance pc, L1NpcInstance npc) {
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

    private static boolean handleCrystalExit(L1PcInstance pc, L1NpcInstance npc, String action) {
        if (!action.equalsIgnoreCase("a")) return false;
        questItemExchange(pc, npc, L1Quest.QUEST_CRYSTAL,
                pc.getQuest().get_step(L1Quest.QUEST_CRYSTAL), L1Quest.QUEST_END,
                40654, 1, 0, 0, 0, "", "",
                () -> pc.start_teleport(32744, 32927, 483, 5, 169, true, false));
        return true;
    }

    private static boolean handleLizardQuest(L1PcInstance pc, L1NpcInstance npc, String action) {
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

    private static boolean handleTionQuest(L1PcInstance pc, L1NpcInstance npc, String action) {
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

    private static boolean handleJeronQuest(L1PcInstance pc, L1NpcInstance npc, String action) {
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
}
class NpcTemplate { String get_name(){return "NPC";} }
class L1NpcInstance { int getId(){return 9001;} int getNpcId(){return 83014;} NpcTemplate getNpcTemplate(){return new NpcTemplate();} }
class Quest { final L1PcInstance pc; Quest(L1PcInstance p){pc=p;} int get_step(int id){return pc.cache.getOrDefault(id,0);} void set_step(int id,int step){pc.db.put(id,step);pc.cache.put(id,step);} void applyCommittedStepIfLoaded(int id,int step){pc.cache.put(id,step);pc.events.add("cache:"+id+":"+step);} }
class Inventory { final L1PcInstance pc; Inventory(L1PcInstance p){pc=p;} boolean checkItem(int id,int qty){return pc.inv.getOrDefault(id,0)>=qty;} boolean checkItemNotEquipped(int id,int qty){return checkItem(id,qty);} boolean checkEquipped(int id){return pc.equipped.contains(id);} boolean checkItem(int id){return checkItem(id,1);} l1j.server.server.model.Instance.L1ItemInstance findItemId(int id){int count=pc.inv.getOrDefault(id,0);return count<=0?null:new l1j.server.server.model.Instance.L1ItemInstance(id,count);} }
class L1PcInstance { final java.util.Map<Integer,Integer> inv=new java.util.HashMap<>(),db=new java.util.HashMap<>(),cache=new java.util.HashMap<>(); final java.util.List<String> events=new java.util.ArrayList<>(); final java.util.Set<Integer> equipped=new java.util.HashSet<>(); boolean failSql, failCommit, failCapacity; final Quest quest=new Quest(this); final Inventory inventory=new Inventory(this); Quest getQuest(){return quest;} Inventory getInventory(){return inventory;} int getId(){return 21;} void sendPackets(String msg){events.add("packet:"+msg);} void start_teleport(int x,int y,int map,int heading,int effect,boolean a,boolean b){events.add("teleport:"+x+":"+y+":"+map);} }
class SC_NPCTalkReturn { static void send(L1PcInstance pc,int obj,String page){pc.events.add("html:"+page);} }
class L1Quest { static final int QUEST_LIZARD=34,QUEST_END=255,QUEST_CRYSTAL=33,QUEST_SIMIZZ=27; }
class L1Item {String getName(){return "i40634";} }
class ItemTable {static ItemTable getInstance(){return new ItemTable();} L1Item getTemplate(int id){return new L1Item();}}
class SC_ServerMessage { static void send(L1PcInstance pc,int msg,String name){pc.events.add("missing:"+name);} static void send(L1PcInstance pc,int msg,String npc,String item){pc.events.add("reward:"+item);} }
class SC_SystemMessage { static void send(L1PcInstance pc,String message){pc.events.add("failure:"+message);} }
