package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Gendor scroll crafting and village buff. Static recipe data, existing owner/CacheD only. */
final class NpcSpellScrollCommands {
    private NpcSpellScrollCommands() {}
    private static final String[] EMPTY_PAGE_ARGS = new String[6];
    private static final String[][] COST_PAGES = {
        costPage(1), costPage(5), costPage(10), costPage(100), costPage(500)
    };
    private static final int[] AMOUNTS = {1, 5, 10, 100, 500};
    private static final String[] RECIPE_COMMANDS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y"};
    private static final int[] REWARD_IDS = {40860, 40861, 40862, 40866, 40859, 40872, 40871, 40870, 40867, 40873, 40875, 40879, 40877, 40880, 40876, 40890, 40883, 40884, 40889, 40887, 40893, 40895, 40897, 40896, 40892};

    private static String[] costPage(int amount) {
        return new String[] {String.valueOf(50 * amount), String.valueOf(100 * amount),
                String.valueOf(100 * amount), String.valueOf(200 * amount),
                String.valueOf(200 * amount), String.valueOf(amount)};
    }

    private static int selectionIndex(String command) {
        if (command.length() != 1) return -1;
        int index = command.charAt(0) - '1';
        return index >= 0 && index < AMOUNTS.length ? index : -1;
    }

    private static int recipeIndex(String command) {
        if (command.length() != 1) return -1;
        // Same String.equalsIgnoreCase semantics as the legacy A--Y branches.
        for (int i = 0; i < RECIPE_COMMANDS.length; i++) {
            if (command.equalsIgnoreCase(RECIPE_COMMANDS[i])) return i;
        }
        return -1;
    }

    static void handle(L1PcInstance pc, int objid, String command) {
        final int selection = selectionIndex(command);
        if (selection >= 0) {
            pc._x = AMOUNTS[selection];
            SC_NPCTalkReturn.send(pc, objid, "bs_m4", COST_PAGES[selection]);
            return;
        }
        final int amount = pc._x;
        if (amount == 0) {
            if (command.equals("a")) buff(pc, objid);
            else if (command.equals("z")) SC_SystemMessage.send(pc, "흑사의 코인을 더블클릭 하세요.");
            return;
        }
        pc._x = 0;
        final int recipe = recipeIndex(command);
        if (recipe < 0) return;
        if (amount != 1 && amount != 5 && amount != 10 && amount != 100 && amount != 500) {
            sendResult(pc, objid, false);
            return;
        }
        final int material = 40090 + recipe / 5;
        final int fee = amount * (recipe < 5 ? 50 : recipe < 15 ? 100 : 200);
        final boolean extra = recipe == 18 || recipe == 19;
        if (!pc.getInventory().HaveItemByClassId(material, amount)
                || !pc.getInventory().HaveItemByClassId(40308, fee)
                || (extra && !pc.getInventory().HaveItemByClassId(40318, amount))) {
            sendResult(pc, objid, false);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 900088)) {
            if (!tx.debit(material, amount) || !tx.debit(40308, fee)
                    || (extra && !tx.debit(40318, amount))
                    || tx.grant(REWARD_IDS[recipe], amount) == null) {
                sendResult(pc, objid, false);
                return;
            }
            tx.onFailure(() -> sendResult(pc, objid, false));
            tx.commitAfterInventoryRelease(() -> sendResult(pc, objid, true));
        } catch (RuntimeException failure) {
            sendResult(pc, objid, false);
        }
    }

    private static void sendResult(L1PcInstance pc, int objid, boolean success) {
        SC_NPCTalkReturn.send(pc, objid, success ? "bs_m1" : "bs_m2", EMPTY_PAGE_ARGS);
    }

    private static void buff(L1PcInstance pc, int objid) {
        L1ItemInstance weapon = pc.getWeapon();
        if (weapon == null) {
            SC_ChatPacket.sendDirect(pc, pc, "무기를 착용하고 받을 수 있습니다.");
            return;
        }
        if (!pc.getInventory().HaveItemByClassId(40308, 1000) || !pc.getInventory().consumeItem(40308, 1000)) {
            SC_SystemMessage.send(pc, "아데나가 부족합니다.");
            return;
        }
        final boolean bow = weapon.getItem().getType1() == 20;
        cast(pc, 26);
        cast(pc, 37);
        cast(pc, 42);
        cast(pc, bow ? 149 : 48);
        L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
        if (clan != null && clan.getCastleId() != 0) cast(pc, L1SkillId.성혈버프);
        SC_NPCTalkReturn.send(pc, objid, "", EMPTY_PAGE_ARGS);
    }

    private static void cast(L1PcInstance pc, int skillId) {
        L1SkillUse.cast(pc, skillId, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_GMBUFF);
    }
}
