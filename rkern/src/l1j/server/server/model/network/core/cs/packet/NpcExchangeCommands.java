package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.CInventory;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.templates.L1Item;

/** NPC-specific item exchanges; one existing receipt per accepted command. */
final class NpcExchangeCommands {
    private static final Logger LOG = Logger.getLogger(NpcExchangeCommands.class.getName());
    private static final int[] GOLEM_LEFT = {5, 145, 52, 125, 99, 32};
    private static final int[] GOLEM_RIGHT = {6, 148, 64, 129, 104, 42};
    private static final int[] GOLEM_REWARD = {602, 605, 601, 603, 604, 600};
    private static final String[] GOLEM_PAGES = {"joegolem9", "joegolem10", "joegolem11", "joegolem12", "joegolem13", "joegolem14"};
    private static final String[] RUNE_MESSAGES = {"힘의 엘릭서 룬을 획득했습니다.", "민첩의 엘릭서 룬을 획득했습니다.",
            "체력의 엘릭서 룬을 획득했습니다.", "지식의 엘릭서 룬을 획득했습니다.", "지혜의 엘릭서 룬을 획득했습니다."};
    private NpcExchangeCommands() {}

    static String golem(L1PcInstance pc, int npcObjectId, String command, String tail) {
        final int i = switch (command.length() == 1 ? Character.toUpperCase(command.charAt(0)) : 0) {
            case 'A' -> 0; case 'B' -> 1; case 'C' -> 2;
            case 'D' -> 3; case 'E' -> 4; case 'F' -> 5; default -> -1;
        };
        if (i < 0) return tail;
        // Original A requires possession of 30,000 but charges 3,000. Do not change price policy.
        if (!pc.getInventory().HaveUnequippedItemByClassIdEnchanted(GOLEM_LEFT[i], 7, 1)
                || !pc.getInventory().HaveUnequippedItemByClassIdEnchanted(GOLEM_RIGHT[i], 7, 1)
                || !has(pc, 41246, 30000)) return "joegolem15";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 200201)) {
            if (!debitEnchant(tx, pc, GOLEM_LEFT[i], 7) || !debitEnchant(tx, pc, GOLEM_RIGHT[i], 7)
                    || !debitLegacy(tx, pc, 41246, i == 0 ? 3000 : 30000)
                    || tx.grant(GOLEM_REWARD[i], 1) == null) return "joegolem15";
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "joegolem15"));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, npcObjectId, GOLEM_PAGES[i]));
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC golem exchange rejected", e);
            return "joegolem15";
        }
    }

    static String diary(L1PcInstance pc, int npcObjectId, boolean orim) {
        final int first = orim ? 410144 : 410097;
        final int last = orim ? 410161 : 410106;
        final int reward = orim ? 410143 : 410107;
        final String missing = orim ? "18권의 일기가 모두 필요합니다." : "10권의 일기가 모두 필요합니다.";
        for (int id = first; id <= last; id++) {
            if (!has(pc, id, 1)) {
                SC_SystemMessage.send(pc, missing);
                return "j_html06";
            }
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 900135)) {
            for (int id = first; id <= last; id++) {
                if (!debitLegacy(tx, pc, id, 1)) {
                    SC_SystemMessage.send(pc, missing);
                    return "j_html06";
                }
            }
            if (tx.grant(reward, 1) == null) return "j_html06";
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "j_html06"));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "j_html04"));
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC diary exchange rejected", e);
            return "j_html06";
        }
    }

    static String rune(L1PcInstance pc, int npcId, String command, String tail) {
        // Original rune menu is case-sensitive.
        if (command.length() != 1) return tail;
        final int i = command.charAt(0) - 'A';
        if (i < 0 || i >= RUNE_MESSAGES.length) return tail;
        final boolean pouch = npcId == 7000055;
        if (!pouch && pc.getLevel() < 55) {
            SC_NPCTalkReturn.send(pc, pc.getId(), "seirune5");
            return tail;
        }
        if (pouch ? !has(pc, 60034, 1) : !has(pc, 60031, 1) || !has(pc, 60032, 1)) {
            runeFailure(pc, pouch, true);
            return tail;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npcId)) {
            if (pouch ? !debitLegacy(tx, pc, 60034, 1)
                    : !debitLegacy(tx, pc, 60031, 1) || !debitLegacy(tx, pc, 60032, 1)) {
                runeFailure(pc, pouch, true);
                return tail;
            }
            if (tx.grant(60036 + i, 1) == null) {
                runeFailure(pc, pouch, false);
                return tail;
            }
            tx.onFailure(() -> runeFailure(pc, pouch, false));
            tx.commitAfterInventoryRelease(() -> {
                if (pouch) SC_SystemMessage.send(pc, RUNE_MESSAGES[i]);
                else SC_NPCTalkReturn.send(pc, pc.getId(), "seirune6");
            });
            // Original caller initializes htmlid to null; no additional NPC HTML is due.
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC rune exchange rejected", e);
            runeFailure(pc, pouch, false);
            return tail;
        }
    }

    static String prism(L1PcInstance pc, int npcObjectId, Random random) {
        if (!has(pc, L1ItemId.ADENA, 10000)) return "camus3";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 200007)) {
            if (!debitLegacy(tx, pc, L1ItemId.ADENA, 10000)) return "camus3";
            // Same four equally likely, consecutive IDs and same shared RNG.
            L1ItemInstance reward = tx.grant(210106 + random.nextInt(4), 1);
            if (reward == null) return "camus3";
            // Use the staged reward rather than createItem(), which registers a
            // second object in L1World even for a name-only lookup.
            final String rewardName = reward.getNumberedName(1);
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "camus3"));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 403, rewardName);
                SC_NPCTalkReturn.send(pc, npcObjectId, "camus2");
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC prism exchange rejected", e);
            return "camus3";
        }
    }

    static String moonItem(L1PcInstance pc, int npcObjectId) {
        if (pc.getLevel() <= 30) return "rabbita2";
        if (has(pc, 22253, 1)) return "rabbita3";
        if (!has(pc, L1ItemId.ADENA, 5000)) return "rabbita4";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 900106)) {
            if (!debitLegacy(tx, pc, L1ItemId.ADENA, 5000)
                    || tx.grant(22253, 1) == null) return "rabbita4";
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "rabbita4"));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "rabbita5"));
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC moon exchange rejected", e);
            return "rabbita4";
        }
    }

    // Immutable-by-convention recipe tables, initialized once rather than per click.
    private static final int[] JAPE_REWARDS = {41093, 41094, 41095, 41096, 41098, 41099, 41100, 41101};
    private static final int[] JAPE_COSTS = {1000, 5000, 10000, 100000};
    private static final int[] EVA_COSTS = {300, 500, 1000};
    private static final int[] EVA_COUNTS = {1, 2, 5};
    private static final String[] EVA_MISSING = {"에바의 은총 총 [300]개가 필요하다오..", "에바의 은총 총 [500]개가 필요하다오..", "에바의 은총 총 [1000]개가 필요하다오.."};
    // Rows: reward id, required gem/scroll id, required count, optional scale id.
    // 'c' was unreachable in the original: a second 'b' branch. Preserve reachability.
    private static final int[][] BAMUT = {
        {222307, 40053, 10, 40393}, {22359, 40052, 10, 40396}, null,
        {222309, 40054, 10, 40395}, {222307, 560030, 1, 0}, {22359, 560033, 1, 0},
        {222308, 560032, 1, 0}, {222309, 560031, 1, 0}
    };
    private static final String[] BAMUT_MISSING = {
        "마물의 기운(50), 최고급 루비(10), 화룡 비늘(5)", "마물의 기운(50), 최고급 다이아몬드(10), 지룡 비늘(5)", null,
        "마물의 기운(50), 최고급 사파이어(10), 수룡 비늘(5)", "마물의 기운(50), 화령 속성 변환 주문서(1)",
        "마물의 기운(50), 지령 속성 변환 주문서(1)", "마물의 기운(50), 풍령 속성 변환 주문서(1)", "마물의 기운(50), 수령 속성 변환 주문서(1)"
    };
    private static final java.util.Comparator<L1ItemInstance> ENCHANT_ORDER =
            java.util.Comparator.comparingInt(L1ItemInstance::getEnchantLevel);

    private static int menuIndex(String command, int size) {
        if (command.length() != 1) return -1;
        int i = Character.toUpperCase(command.charAt(0)) - 'A';
        return i >= 0 && i < size ? i : -1;
    }

    static String jape(L1PcInstance pc, int npcObjectId, String command, String tail) {
        int i = menuIndex(command, JAPE_REWARDS.length);
        if (i < 0) return tail;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 71180)) {
            if (!debitRecipe(tx, pc, 49026, JAPE_COSTS[i & 3])
                    || tx.grant(JAPE_REWARDS[i], 1) == null) return "jp5";
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "jp5"));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, npcObjectId, i < 4 ? "jp6" : "jp8"));
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC Jape exchange rejected", e);
            return "jp5";
        }
    }

    static String eva(L1PcInstance pc, int npcObjectId, String command, String tail) {
        int i = menuIndex(command, EVA_COSTS.length);
        if (i < 0) return tail;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 7310174)) {
            if (!debitRecipe(tx, pc, 3000211, EVA_COSTS[i]) || tx.grant(3000210, EVA_COUNTS[i]) == null) {
                SC_SystemMessage.send(pc, EVA_MISSING[i]);
                return tail;
            }
            tx.onFailure(() -> { SC_SystemMessage.send(pc, EVA_MISSING[i]); sendTail(pc, npcObjectId, tail); });
            tx.commitAfterInventoryRelease(() -> {
                SC_SystemMessage.send(pc, "감사합니다. 다음에 또 오세요.");
                sendTail(pc, npcObjectId, tail);
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC Eva exchange rejected", e);
            SC_SystemMessage.send(pc, EVA_MISSING[i]);
            return tail;
        }
    }

    static String bamut(L1PcInstance pc, int npcObjectId, String command, String tail) {
        int i = menuIndex(command, BAMUT.length);
        if (i < 0 || BAMUT[i] == null) return tail;
        int[] row = BAMUT[i];
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 70690)) {
            if (!debitRecipe(tx, pc, 410061, 50) || !debitRecipe(tx, pc, row[1], row[2])
                    || (row[3] != 0 && !debitRecipe(tx, pc, row[3], 5))
                    || tx.grant(row[0], 1) == null) {
                bamutFailure(pc, i);
                return tail;
            }
            tx.onFailure(() -> { bamutFailure(pc, i); sendTail(pc, npcObjectId, tail); });
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, npcObjectId, ""));
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC Bamut exchange rejected", e);
            bamutFailure(pc, i);
            return tail;
        }
    }

    private static void bamutFailure(L1PcInstance pc, int i) {
        SC_SystemMessage.send(pc, "제작 아이템이 부족합니다.");
        SC_SystemMessage.send(pc, BAMUT_MISSING[i]);
    }

    static String memoryCrystal(L1PcInstance pc, int npcObjectId, String command, String tail) {
        if (!command.equalsIgnoreCase("request memory crystal")) return tail;
        final String missing = "아데나(20,000) 또는 기억의파편(1)개 가 부족합니다";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 7310149)) {
            if (!debitRecipe(tx, pc, 3000200, 1) || !debitRecipe(tx, pc, 40308, 20000)
                    || tx.grant(700022, 1) == null) {
                SC_SystemMessage.send(pc, missing);
                return tail;
            }
            tx.onFailure(() -> { SC_SystemMessage.send(pc, missing); sendTail(pc, npcObjectId, tail); });
            tx.commitAfterInventoryRelease(() -> {
                SC_SystemMessage.send(pc, "제작이 완료 되었습니다.");
                SC_NPCTalkReturn.send(pc, npcObjectId, "");
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC memory crystal exchange rejected", e);
            SC_SystemMessage.send(pc, missing);
            return tail;
        }
    }

    static String habert(L1PcInstance pc, int npcObjectId, String command, String tail) {
        int i = menuIndex(command, 2);
        if (i < 0) return tail;
        boolean upgrade = i == 1;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 70641)) {
            if ((upgrade && !debitEnchant(tx, pc, 20273, 7)) || !debitRecipe(tx, pc, 40395, 1)
                    || !debitRecipe(tx, pc, 410061, 10) || !debitRecipe(tx, pc, 820004, 300)
                    || (upgrade && !debitRecipe(tx, pc, 820005, 1))
                    || tx.grant(upgrade ? 20274 : 20273, 1) == null) {
                habertFailure(pc, upgrade);
                return tail;
            }
            tx.onFailure(() -> { habertFailure(pc, upgrade); sendTail(pc, npcObjectId, tail); });
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc, npcObjectId, ""));
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC Habert exchange rejected", e);
            habertFailure(pc, upgrade);
            return tail;
        }
    }

    private static void habertFailure(L1PcInstance pc, boolean upgrade) {
        SC_SystemMessage.send(pc, "제작 아이템이 부족합니다.");
        SC_SystemMessage.send(pc, "수룡 비늘(1)");
        SC_SystemMessage.send(pc, "마물의 기운(10)");
        SC_SystemMessage.send(pc, "마력의 실타래(300)");
        if (upgrade) {
            SC_SystemMessage.send(pc, "마력의 핵(1)");
            SC_SystemMessage.send(pc, "+7 마력의 장갑(1)");
        }
    }


    // Fire golem (5066): exact original candidate priority and case-sensitive axe menus.
    private static final int[][] FIRE_WEAPON_INPUTS = {
        {5, 6, 32, 37, 41, 42, 52, 64, 99, 104, 125, 129, 131, 145, 148, 180, 181},
        {500, 501}, {503, 504}, {81, 177, 194, 13}, {81, 177, 194, 13}, {605}, {151}
    };
    private static final int[] FIRE_WEAPON_OUTPUTS = {602, 202001, 1135, 1124, 1125};
    private static final int[] FIRE_AXE_INPUT_ENCHANT = {8, 9, 10, 0, 3, 5};
    private static final int[] FIRE_AXE_OUTPUT_ENCHANT = {0, 8, 9, 0, 1, 3};
    private static final String[] FIRE_AXE_MISSING = {
        "+8 광풍의도끼, 결정체(100,000)개 필요합니다.", "+9 광풍의도끼, 결정체(100,000)개 필요합니다.",
        "+10 광풍의도끼, 결정체(100,000)개 필요합니다.", "+0 데몬 액스, 결정체(100,000)개 필요합니다.",
        "+3 데몬 액스, 결정체(100,000)개 필요합니다.", "+5 데몬 액스, 결정체(100,000)개 필요합니다."
    };

    static String fireGolemWeapon(L1PcInstance pc, int npcObjectId, String command, String tail) {
        final int index = switch (command) {
            case "1" -> 0; case "2" -> 1; case "3" -> 2; case "4" -> 3;
            case "5" -> 4; case "6" -> 5; case "7" -> 6; case "8" -> 7;
            case "9" -> 8; case "10" -> 9; case "11" -> 10; case "12" -> 11; case "13" -> 12;
            case "a" -> 13; case "b" -> 14; case "c" -> 15;
            case "d" -> 16; case "e" -> 17; case "f" -> 18; default -> -1;
        };
        if (index < 0) return tail;
        final boolean staff = index >= 10 && index <= 12;
        final boolean axe = index >= 13;
        final int axeIndex = index - 13;
        final String missing = axe ? FIRE_AXE_MISSING[axeIndex] : "제작 아이템이 부족합니다.";
        final int output = staff ? 202003 : axe ? (axeIndex < 3 ? 203015 : 203016) : FIRE_WEAPON_OUTPUTS[index >>> 1];
        final int outputEnchant = staff ? (index == 10 ? 0 : index == 11 ? 8 : 9)
                : axe ? FIRE_AXE_OUTPUT_ENCHANT[axeIndex] : 7 + (index & 1);
        // Gear is nonstackable. Never change enchant on a grant which could merge into an existing stack.
        L1Item definition = CObjectDB.LoadItem().getTemplate(output);
        if (definition == null || definition.isStackable()) {
            SC_SystemMessage.send(pc, missing);
            return tail;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 5066)) {
            boolean ready;
            if (staff) {
                ready = debitEnchant(tx, pc, 119, 5) && debitEnchant(tx, pc, 121, index - 1)
                        && debitRecipe(tx, pc, 700077, 1) && debitRecipe(tx, pc, 41246, 100000);
            } else {
                int[] candidates = FIRE_WEAPON_INPUTS[axe ? (axeIndex < 3 ? 5 : 6) : index >>> 1];
                int enchant = axe ? FIRE_AXE_INPUT_ENCHANT[axeIndex] : 8 + (index & 1);
                ready = debitFirstEnchant(tx, pc, candidates, enchant)
                        && debitRecipe(tx, pc, axe ? 41246 : 40308,
                                axe ? (axeIndex < 3 ? 100000 : 200000) : ((index & 1) == 0 ? 5000000 : 10000000));
            }
            if (!ready) {
                SC_SystemMessage.send(pc, missing);
                return tail;
            }
            L1ItemInstance reward = tx.grant(output, 1);
            if (reward == null) {
                SC_SystemMessage.send(pc, missing);
                return tail;
            }
            reward.setEnchantLevel(outputEnchant);
            // Original command 11 uses storeItem(int,int) without an explicit 403 message.
            final String rewardName = index == 10 ? null : reward.getLogName();
            tx.onFailure(() -> { SC_SystemMessage.send(pc, missing); sendTail(pc, npcObjectId, tail); });
            tx.commitAfterInventoryRelease(() -> {
                if (rewardName != null) SC_ServerMessage.send(pc, 403, rewardName);
                SC_NPCTalkReturn.send(pc, npcObjectId, "");
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC fire golem weapon exchange rejected", e);
            SC_SystemMessage.send(pc, missing);
            return tail;
        }
    }

    private static boolean debitFirstEnchant(L1InventoryTransaction tx, L1PcInstance pc, int[] candidates, int enchant) {
        for (int template : candidates) {
            for (L1ItemInstance item : pc.getInventory().getItems()) {
                if (item != null && !item.isEquipped() && item.getItemId() == template && item.getEnchantLevel() == enchant)
                    return tx.debit(item, item.getCount());
            }
        }
        return false;
    }

    private static void sendTail(L1PcInstance pc, int npcObjectId, String tail) {
        if (tail != null) SC_NPCTalkReturn.send(pc, npcObjectId, tail);
    }

    private static boolean debitEnchant(L1InventoryTransaction tx, L1PcInstance pc, int template, int enchant) {
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            if (item == null || item.isEquipped() || item.getItemId() != template || item.getEnchantLevel() != enchant) continue;
            final int remaining = tx.GetPreparedItemAmount(pc, item);
            if (remaining > 0) return tx.debit(pc, item, remaining);
        }
        return false;
    }

    /** Legacy first-stack and stable lowest-enchant order. Multi-unit nonstackable
     * templates use a temporary snapshot only on that uncommon path; no shared scratch state. */
    static boolean debitRecipe(L1InventoryTransaction tx, L1PcInstance pc, int template, int count) {
        L1Item def = CObjectDB.LoadItem().getTemplate(template);
        if (def == null || count <= 0) return false;
        if (def.isStackable() || count == 1) return debitLegacy(tx, pc, template, count);
        L1ItemInstance[] items = pc.getInventory().GetUnequippedItemListByClassID(template);
        int eligible = 0;
        for (L1ItemInstance item : items) {
            if (tx.GetPreparedItemAmount(pc, item) > 0) items[eligible++] = item;
        }
        if (eligible < count) return false;
        if (eligible > count) java.util.Arrays.sort(items, 0, eligible, ENCHANT_ORDER);
        for (int i = 0; i < count; i++) if (!tx.debit(pc, items[i], 1)) return false;
        return true;
    }

    private static void runeFailure(L1PcInstance pc, boolean pouch, boolean missing) {
        SC_NPCTalkReturn.send(pc, pc.getId(), pouch ? "riddle2" : "seirune5");
        if (!pouch && missing) SC_SystemMessage.send(pc,
                "낡은 고서와 마법사의 돌이 필요하네. 마법사의 돌은 우측으로가서 포이에게 구매하게나.");
    }

    private static boolean has(L1PcInstance pc, int itemId, int count) {
        return CObjectDB.LoadItem().getTemplate(itemId) != null && pc.getInventory().HaveItemByClassId(itemId, count);
    }

    /** Preserve legacy first stack / lowest-enchant single-item selection without temporary arrays. */
    static boolean debitLegacy(L1InventoryTransaction tx, L1PcInstance pc, int template, int count) {
        L1Item def = CObjectDB.LoadItem().getTemplate(template);
        if (def == null) return false;
        if (def.isStackable()) {
            L1ItemInstance item = pc.getInventory().GetFirstItemByClassID(template);
            return count > 0 && item != null && tx.GetPreparedItemAmount(pc, item) >= count
                    && tx.debit(pc, item, count);
        }
        if (count != 1) return false; // no multi-unit nonstackable recipes in these handlers
        L1ItemInstance chosen = null;
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            if (item == null || item.isEquipped() || item.getItemId() != template
                    || tx.GetPreparedItemAmount(pc, item) <= 0) continue;
            if (chosen == null || item.getEnchantLevel() < chosen.getEnchantLevel()) chosen = item;
        }
        return chosen != null && tx.debit(pc, chosen, 1);
    }

    static String fireGolem(L1PcInstance pc, L1Object obj, String command, String tail) {
        if (command.length() != 1 || command.charAt(0) < 'A' || command.charAt(0) > 'P')
            return fireGolemWeapon(pc, obj.getId(), command, tail);
        final L1NpcInstance npc = (L1NpcInstance) obj;
        final char c = command.charAt(0);
        final int index = (c - 'A') >>> 2;
        final int enchant = (c - 'A') & 3;
        final int oldArmor = ANTON_ARMOR[index];
        final int rewardId = 222300 + index;
        final int objectId = npc.getId();
        final String npcName = npc.getNpcTemplate().get_name();
        final L1Item definition = CObjectDB.LoadItem().getTemplate(rewardId);
        // Never apply an enchant to a grant that could have merged with a live stack.
        if (definition == null || definition.isStackable()) return fireArmorFailure(pc, tail);
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 5066)) {
            if (!debitEnchant(tx, pc, 20110, enchant + 7)
                    || !debitLegacy(tx, pc, 41246, 100000)) {
                return fireArmorFailure(pc, tail);
            }
            if (!debitLegacy(tx, pc, oldArmor, 1)) return fireArmorFailure(pc, tail);
            L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) return fireArmorFailure(pc, tail);
            reward.setEnchantLevel(enchant);
            final String name = reward.getLogName();
            tx.onFailure(() -> { fireArmorFailure(pc, tail); sendTail(pc, objectId, tail); });
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, npcName, name);
                SC_NPCTalkReturn.send(pc, objectId, "");
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC fire golem armor exchange rejected", e);
            return fireArmorFailure(pc, tail);
        }
    }

    private static String fireArmorFailure(L1PcInstance pc, String tail) {
        SC_SystemMessage.send(pc, "제작 아이템이 부족합니다.");
        return tail;
    }

    static boolean createNewItem(L1PcInstance pc, String npcName, int item_id, int count, int enchant) {
        if (count <= 0 || count > CInventory.MAX_AMOUNT) return false;
        final L1ItemInstance item = CObjectDB.LoadItem().createItem(item_id);
        if (item == null) return false;
        final L1World world = L1World.getInstance();
        CInventory destination = null;
        L1ItemInstance stored = null;
        try {
            item.setCount(count);
            item.setEnchantLevel(enchant);
            destination = pc.getInventory().CheckAddable(item, count) == CInventory.OK
                    ? pc.getInventory() : world.getInventory(pc.getX(), pc.getY(), pc.getMapId());
            stored = destination.CreateOrAddItem(item);
            if (stored == null) return false;
            SC_ServerMessage.send(pc, 143, npcName, item.getLogName());
            return true;
        } finally {
            // A merged stack never adopts the newly registered world object.
            // On a rejected insertion, retain it only if the destination did adopt it.
            if (stored != item && (destination == null || destination.GetByServerID(item.getId()) != item)
                    && world.findObject(item.getId()) == item) {
                world.removeObject(item);
            }
        }
    }


    static String 비자야(L1PcInstance pc, Object obj, String command) {
        if (command.length() != 1 || command.charAt(0) < 'A' || command.charAt(0) > 'D') return "vjaya04";
        final int i = command.charAt(0) - 'A';
        final int enchant = 8 + (i & 1);
        final int input = 503 + (i >>> 1);
        final int price = (i & 1) == 0 ? 5000000 : 10000000;
        final int rewardId = 30119 + (i & 1);
        final L1NpcInstance npc = (L1NpcInstance) obj;
        final int objectId = npc.getId();
        final String npcName = npc.getNpcTemplate().get_name();
        final L1Item definition = CObjectDB.LoadItem().getTemplate(rewardId);
        if (definition == null || definition.isStackable()) return "vjaya04";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 5161)) {
            if (!debitEnchant(tx, pc, input, enchant) || !debitLegacy(tx, pc, 40308, price)) return "vjaya04";
            L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) return "vjaya04";
            final String name = reward.getLogName();
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, objectId, "vjaya04"));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, npcName, name);
                SC_NPCTalkReturn.send(pc, objectId, "vjaya05");
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC Vijaya exchange rejected", e);
            return "vjaya04";
        }
    }

    static void PiersItem(L1PcInstance pc, int objid, String npcName, int objectId, int checkEnchant, int adena) {
        final L1ItemInstance input = objectId > 0 ? pc.getInventory().GetByServerID(objectId) : null;
        if (input == null || input.isEquipped() || input.getEnchantLevel() != checkEnchant
                || !validPiersSelection(pc, input.getItemId(), checkEnchant, adena)) {
            SC_NPCTalkReturn.send(pc, objid, "piers04");
            return;
        }
        final int rewardId = pc.PiersItemId[16];
        final int rewardEnchant = pc.PiersItemId[17];
        final L1Item definition = CObjectDB.LoadItem().getTemplate(rewardId);
        if (definition == null || definition.isStackable()) {
            SC_NPCTalkReturn.send(pc, objid, "piers04");
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 70908)) {
            if (!tx.debit(input, 1) || !debitLegacy(tx, pc, 40308, adena)) {
                SC_NPCTalkReturn.send(pc, objid, "piers04");
                return;
            }
            L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) {
                SC_NPCTalkReturn.send(pc, objid, "piers04");
                return;
            }
            reward.setEnchantLevel(rewardEnchant);
            final String name = reward.getLogName();
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, objid, "piers04"));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, npcName, name);
                SC_NPCTalkReturn.send(pc, objid, "");
            });
            // Invalidate on admission, not on a delayed response which could clear a newer menu.
            clearPiersSelection(pc);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC Piers exchange rejected", e);
            SC_NPCTalkReturn.send(pc, objid, "piers04");
        }
    }

    static void PiersCheckItem(L1PcInstance pc, int objid, int enchant, int adena, int newItem, int newItemEnchant) {
		clearPiersSelection(pc);
        int listcount = 0;
		String[] list = new String[10];
		for (int i = 0; i < 10; i++) {
			list[i] = " ";
		}
		for (L1ItemInstance item : pc.getInventory().getItems()) {
			if (listcount == list.length) break;
            if (item.isEquipped()) continue;
			switch (item.getItemId()) {
			case 13:
			case 81:
			case 162:
			case 177:
			case 194:
				if (item.getEnchantLevel() == enchant && pc.getInventory().HaveItemByClassId(40308, adena)) {
					list[listcount] = "+" + item.getEnchantLevel() + " " + item.getName();
					pc.PiersItemId[listcount] = item.getId(); // bind exact owned object
					pc.PiersEnchant[listcount] = enchant;
					listcount++;
				}
				break;
			default:
				break;
			}
		}
		if (listcount == 0 || !pc.getInventory().HaveItemByClassId(40308, adena)) {
			SC_NPCTalkReturn.send(pc, objid, "piers04");
		} else {
			pc.PiersItemId[16] = newItem;
			pc.PiersItemId[17] = newItemEnchant;
			pc.PiersItemId[18] = adena;
			SC_NPCTalkReturn.send(pc, objid, "piers00", list);
		}
	}
    private static void clearPiersSelection(L1PcInstance pc) {
        java.util.Arrays.fill(pc.PiersItemId, 0);
        java.util.Arrays.fill(pc.PiersEnchant, 0);
    }

    private static boolean validPiersSelection(L1PcInstance pc, int item, int enchant, int price) {
        if (item != 13 && item != 81 && item != 162 && item != 177 && item != 194) return false;
        final int reward = pc.PiersItemId[16];
        final int rewardEnchant = pc.PiersItemId[17];
        return price == pc.PiersItemId[18] && ((enchant == 8 && price == 5000000
                && rewardEnchant == 7 && (reward == 30018 || reward == 30019))
                || (enchant == 9 && price == 10000000 && rewardEnchant == 8
                && (reward == 30020 || reward == 30021)));
    }


    static String piers(L1PcInstance pc, L1NpcInstance npc, int objid, String command, String htmlid) {
        if (command.equals("a")) {
            clearPiersSelection(pc); // opening a new top-level menu invalidates previous selections
            int flags = 0;
            for (L1ItemInstance item : pc.getInventory().getItems()) {
                if (item.isEquipped()) continue;
                switch (item.getItemId()) {
                    case 13, 81, 162, 177, 194 -> {
                        if (item.getEnchantLevel() == 8 && pc.getInventory().HaveItemByClassId(40308, 5000000)) flags |= 1;
                        else if (item.getEnchantLevel() == 9 && pc.getInventory().HaveItemByClassId(40308, 10000000)) flags |= 2;
                    }
                    default -> { }
                }
            }
            return switch (flags) {
                case 1 -> "piers03";
                case 2 -> "piers02";
                case 3 -> "piers01";
                default -> "piers04";
            };
        }
        if (command.length() == 1) {
            final char c = command.charAt(0);
            if (c >= '0' && c <= '9') {
                final int slot = c - '0';
                PiersItem(pc, objid, npc.getNpcTemplate().get_name(), pc.PiersItemId[slot], pc.PiersEnchant[slot], pc.PiersItemId[18]);
            } else {
                switch (c) {
                    case 'A' -> PiersCheckItem(pc, objid, 8, 5000000, 30018, 7);
                    case 'B' -> PiersCheckItem(pc, objid, 8, 5000000, 30019, 7);
                    case 'C' -> PiersCheckItem(pc, objid, 9, 10000000, 30020, 8);
                    case 'D' -> PiersCheckItem(pc, objid, 9, 10000000, 30021, 8);
                    default -> { }
                }
            }
        }
        return htmlid;
    }

    /** The donation and (if present) ring charge image share one receipt. */
    static void rechargeWeddingRing(L1PcInstance pc) {
        if (!pc.getInventory().HaveItemByClassId(L1ItemId.ADENA, 1000)) {
            SC_SystemMessage.send(pc, "아데나가 부족합니다.");
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 70702)) {
            if (!tx.debit(L1ItemId.ADENA, 1000)) {
                SC_SystemMessage.send(pc, "아데나가 부족합니다.");
                return;
            }
            L1ItemInstance ring = null;
            if (pc.getPartnerId() != 0) {
                for (int id = 40906; id >= 40903 && ring == null; --id)
                    ring = pc.getInventory().GetFirstItemByClassID(id);
                if (ring != null) tx.changeChargeCount(ring, ring.getItem().getMaxChargeCount());
            }
            final boolean recharged = ring != null;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "결혼 반지 충전이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (recharged) SC_SystemMessage.send(pc, "결혼 반지가 충전되었습니다.");
                SC_SystemMessage.send(pc, "기부해주셔서 감사합니다.");
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "결혼 반지 충전을 진행할 수 없습니다.");
        }
    }

    static String tikalExchange(L1PcInstance pc, int objectId, String npcName) {
        return paidPurchase(pc, 500062, objectId, 210081, 100, 210082, 1,
                "joegolem18", "joegolem19", npcName, null);
    }

    static String hugrint(L1PcInstance pc, int objectId) {
        if (pc.getInventory().HaveItemByClassId(L1ItemId.MAGIC_BREATH, 1)) return "hugrint4";
        return paidPurchase(pc, 900146, objectId, L1ItemId.ADENA, 1000, L1ItemId.MAGIC_BREATH, 1,
                "hugrint2", "hugrint3", null, null);
    }

    static String contract(L1PcInstance pc, int objectId, boolean killton) {
        return paidPurchase(pc, killton ? 777849 : 777848, objectId, L1ItemId.ADENA, 500000,
                killton ? 87050 : 87051, 1, "", killton ? "killton3" : "merin3", null,
                killton ? "킬톤이 당신에게 킬톤의 계약서를 주었습니다." : "메린이 당신에게 메린의 계약서를 주었습니다.");
    }

    static int sharnaItem(int level) {
        if (level < 30) return 0;
        if (level < 40) return 210097;
        if (level < 52) return 210098;
        if (level < 55) return 210099;
        if (level < 60) return 210100;
        if (level < 65) return 210101;
        if (level < 70) return 210102;
        if (level < 75) return 210103;
        if (level < 80) return 210116;
        return 210117;
    }

    static int sharnaCount(String command) {
        // Original 0..9, :..@, A..C select 1..20; all other commands default to 1.
        if (command.length() != 1) return 1;
        char c = Character.toUpperCase(command.charAt(0));
        return c >= '0' && c <= 'C' ? c - '0' + 1 : 1;
    }

    static String sharna(L1PcInstance pc, int objectId, String command, String npcName) {
        int itemId = sharnaItem(pc.getLevel());
        if (itemId == 0) return "sharna5"; // no valid reward at this level; never charge.
        int count = sharnaCount(command);
        return paidPurchase(pc, 200075, objectId, L1ItemId.ADENA, count * 2500,
                itemId, count, "sharna3", "sharna5", npcName, null);
    }

    /** One paid receipt; don't retain the target NPC or send a success page before commit. */
    private static String paidPurchase(L1PcInstance pc, int npcId, int objectId,
            int costId, int cost, int rewardId, int count, String success, String failure,
            String npcName, String message) {
        if (!has(pc, costId, cost)) return failure;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npcId)) {
            if (!debitRecipe(tx, pc, costId, cost)) return failure;
            L1ItemInstance reward = tx.grant(rewardId, count);
            if (reward == null) return failure;
            final String rewardName = npcName == null ? null : reward.getItem().getName();
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, objectId, failure));
            tx.commitAfterInventoryRelease(() -> {
                if (npcName != null) SC_ServerMessage.send(pc, 143, npcName, rewardName);
                if (message != null) SC_SystemMessage.send(pc, message);
                SC_NPCTalkReturn.send(pc, objectId, success);
            });
            return null;
        } catch (RuntimeException error) {
            LOG.log(Level.WARNING, "NPC purchase rejected", error);
            return failure;
        }
    }

    static void rechargeCrownRelic(L1PcInstance pc, int objectId) {
        if (!pc.isCrown() || !pc.getInventory().HaveItemByClassId(20383,1)) return;
        if (!pc.getInventory().HaveItemByClassId(L1ItemId.ADENA,100000)) {
            SC_ServerMessage.send(pc,337,"$4");
            return;
        }
        final L1ItemInstance relic=pc.getInventory().GetFirstItemByClassID(20383);
        if (relic == null || relic.getChargeCount()==50) return;
        try (L1InventoryTransaction tx=L1InventoryTransaction.begin(pc,80105)) {
            if (!debitLegacy(tx,pc,L1ItemId.ADENA,100000)) {
                SC_ServerMessage.send(pc,337,"$4");
                return;
            }
            tx.changeChargeCount(relic,50);
            tx.onFailure(() -> SC_SystemMessage.send(pc,"충전이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> SC_NPCTalkReturn.send(pc,objectId,""));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc,"충전을 진행할 수 없습니다.");
        }
    }


    private static final int[] ANTON_ARMOR = {20095, 20094, 20092, 20093};

    /** Original class eligibility and command case are retained. The receipt
     * never removes equipped gear and never publishes partially paid rewards. */
    static String veteran(L1PcInstance pc, L1NpcInstance npc, String command, String tail) {
        if (command.length() != 1) return tail;
        final char c = command.charAt(0);
        final boolean equipment = c >= 'a' && c <= 'p';
        if (!equipment && (c < '1' || c > '6')) return tail;
        final int index = equipment ? c - 'a' : c - '1';
        if (equipment) {
            if (!has(pc, 30065, 1)) return "veteranE1";
            if (!veteranClassAllowed(pc, index)) return "veteranE6";
        }
        final String failure = equipment ? "veteranE1" : "veteranE4";
        final String success = equipment ? "veteranE2" : "veteranE3";
        final int objectId = npc.getId();
        final String npcName = npc.getNpcTemplate().get_name();
        final int rewardId = equipment ? (index < 8 ? 1126 + index : 22328 + index - 8)
                : switch (index % 3) { case 0 -> 60029; case 1 -> 30068; default -> 30069; };
        final int count = !equipment && index % 3 == 0 ? 50 : 1;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 5111)) {
            if (equipment) {
                if (!debitLegacy(tx, pc, 30065, 1)) return failure;
            } else {
                final int first = index < 3 ? 1126 : 22328;
                L1ItemInstance chosen = null;
                // First available template ID, then lowest enchant, in inventory order.
                for (L1ItemInstance item : pc.getInventory().getItems()) {
                    if (item == null || item.isEquipped() || item.getCount() <= 0
                            || item.getItemId() < first || item.getItemId() >= first + 8) continue;
                    if (chosen == null || item.getItemId() < chosen.getItemId()
                            || (item.getItemId() == chosen.getItemId()
                            && item.getEnchantLevel() < chosen.getEnchantLevel())) chosen = item;
                }
                if (chosen == null || !tx.debit(chosen, 1)) return failure;
            }
            L1ItemInstance reward = tx.grant(rewardId, count);
            if (reward == null) return failure;
            final String name = reward.getNumberedName(count);
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, objectId, failure));
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, npcName, name);
                SC_NPCTalkReturn.send(pc, objectId, success);
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC veteran exchange rejected", e);
            return failure;
        }
    }

    private static boolean veteranClassAllowed(L1PcInstance pc, int index) {
        return switch (index) {
            case 0 -> pc.isCrown() || pc.isKnight() || pc.isWizard() || pc.isElf() || pc.isDarkelf();
            case 1 -> pc.isCrown() || pc.isKnight() || pc.isElf() || pc.isDragonknight();
            case 2, 14 -> pc.isCrown() || pc.isKnight() || pc.isDragonknight();
            case 3, 15 -> pc.isElf() || pc.isDarkelf() || pc.isBlackwizard();
            case 4, 10 -> pc.isWizard() || pc.isBlackwizard();
            case 5 -> pc.isDarkelf();
            case 6 -> pc.isDragonknight();
            case 7 -> pc.isBlackwizard();
            case 8 -> pc.isKnight();
            case 9 -> pc.isCrown() || pc.isElf() || pc.isDarkelf() || pc.isDragonknight() || pc.isBlackwizard();
            case 11 -> pc.isKnight() || pc.isDragonknight();
            case 12, 13 -> true;
            default -> false;
        };
    }

    static String anton(L1PcInstance pc, L1NpcInstance npc, String command, String tail) {
        if (command.length() != 1) return tail;
        final char c = command.charAt(0);
        final boolean enchanted = c >= 'A' && c <= 'P';
        if (!enchanted && (c < '1' || c > '4')) return tail;
        final int index = enchanted ? (c - 'A') >>> 2 : c - '1';
        final int enchant = enchanted ? (c - 'A') & 3 : 0;
        final int oldArmor = ANTON_ARMOR[index];
        final int rewardId = 222300 + index;
        final int objectId = npc.getId();
        final String npcName = npc.getNpcTemplate().get_name();
        final L1Item definition = CObjectDB.LoadItem().getTemplate(rewardId);
        // Never apply an enchant to a grant that could have merged with a live stack.
        if (definition == null || definition.isStackable()) return antonFailure(pc, tail);
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 70614)) {
            if (enchanted ? !debitEnchant(tx, pc, 20110, enchant + 7)
                    || !debitLegacy(tx, pc, 41246, 100000) : !debitLegacy(tx, pc, 49015, 1)) {
                return antonFailure(pc, tail);
            }
            if (!debitLegacy(tx, pc, oldArmor, 1)) return antonFailure(pc, tail);
            L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) return antonFailure(pc, tail);
            reward.setEnchantLevel(enchant);
            final String name = reward.getNumberedName(1);
            tx.onFailure(() -> { antonFailure(pc, tail); sendTail(pc, objectId, tail); });
            tx.commitAfterInventoryRelease(() -> {
                SC_ServerMessage.send(pc, 143, npcName, name);
                SC_NPCTalkReturn.send(pc, objectId, "");
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC anton exchange rejected", e);
            return antonFailure(pc, tail);
        }
    }

    private static String antonFailure(L1PcInstance pc, String tail) {
        SC_NPCTalkReturn.send(pc, pc.getId(), "anton9");
        return tail;
    }
}
