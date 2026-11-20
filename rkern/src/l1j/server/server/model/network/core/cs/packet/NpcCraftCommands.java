package l1j.server.server.model.network.core.cs.packet;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

/** Legacy HTML recipe execution, using the existing owner/receipt boundary. */
final class NpcCraftCommands {
    private static final Logger LOG = Logger.getLogger(NpcCraftCommands.class.getName());
    private NpcCraftCommands() {}

    /** Read-only vectors shared by the HTML recipes that use unit quantities. */
    static final class UnitCounts {
        static final int[] ONE = {1};
        static final int[] TWO_ITEMS = {1, 1};

        private UnitCounts() {}
    }

    static void execute(L1PcInstance pc, L1Object target, int objid,
            int[] materials, int[] counts, int[] rewards, int[] rewardCounts,
            String success, String failure, String tail, String[] data) {
        if (!validRecipe(materials, counts) || !validRecipe(rewards, rewardCounts)) {
            fail(pc, objid, failure, data);
            return;
        }
        boolean enough = true;
        for (int i = 0; i < materials.length; i++) {
            L1Item def = ItemTable.getInstance().getTemplate(materials[i]);
            if (def == null) { fail(pc, objid, failure, data); return; }
            if (!pc.getInventory().checkItemNotEquipped(materials[i], counts[i])) {
                SC_ServerMessage.send(pc, 337, def.getName());
                enough = false;
            }
        }
        if (!enough) {
            if (failure != null) SC_NPCTalkReturn.send(pc, objid, failure, data);
            return;
        }
        // Preserve the original conservative pre-consumption slot/weight limits.
        long slots = 0, weight = 0;
        for (int i = 0; i < rewards.length; i++) {
            L1Item def = ItemTable.getInstance().getTemplate(rewards[i]);
            if (def == null) { fail(pc, objid, failure, data); return; }
            if (def.isStackable()) {
                if (!pc.getInventory().checkItem(rewards[i])) slots++;
            } else slots += rewardCounts[i];
            if (slots > 180) { SC_ServerMessage.send(pc, 263); return; }
            weight += (long) def.getWeight() * rewardCounts[i] / 1000L;
            if (weight > Integer.MAX_VALUE) { SC_ServerMessage.send(pc, 82); return; }
        }
        if ((long) pc.getInventory().getSize() + slots > 180) {
            SC_ServerMessage.send(pc, 263); return;
        }
        if (pc.getMaxWeight() < (long) pc.getInventory().getWeight() + weight) {
            SC_ServerMessage.send(pc, 82); return;
        }
        final String npcName = target instanceof L1NpcInstance npc ? npc.getNpcTemplate().get_name() : "";
        final int resourceKey = target instanceof L1NpcInstance npc ? npc.getNpcId() : objid;
        final CIOClient client = pc.getNetConnection();
        final int dialogueVersion = client == null ? 0 : client.npcDialogueVersion();
        final long dialogueTarget = L1World.getInstance().smartIdOf(target);
        boolean prepared = false;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, resourceKey)) {
            for (int i = 0; i < materials.length; i++) {
                if (!tx.debit(materials[i], counts[i])) return;
            }
            for (int i = 0; i < rewards.length; i++) {
                if (tx.grant(rewards[i], rewardCounts[i]) == null) return;
            }
            tx.onFailure(() -> {
                if (failure == null || HtmlCmdHandler.canReplyToDialogue(pc, dialogueTarget, dialogueVersion))
                    fail(pc, objid, failure, data);
            });
            tx.commitAfterInventoryRelease(() -> complete(pc, objid, npcName, rewards,
                    rewardCounts, success, tail, data, dialogueTarget, dialogueVersion));
            prepared = true;
        } catch (RuntimeException error) {
            LOG.log(Level.WARNING, "NPC recipe preparation failed", error);
        } finally {
            if (!prepared) fail(pc, objid, failure, data);
        }
    }

    private static final int[] TOTEM_PRICE = {50, 100, 50, 30, 200};

    private static final class TotemRecipes {
        static final int[][] MATERIALS = new int[1 << TOTEM_PRICE.length][];
        static final int[][] REWARDS = new int[TOTEM_PRICE.length + 1][];

        static {
            for (int mask = 1; mask < MATERIALS.length; mask++) {
                int[] ids = new int[Integer.bitCount(mask)];
                int index = 0;
                for (int slot = 0; slot < TOTEM_PRICE.length; slot++)
                    if ((mask & (1 << slot)) != 0) ids[index++] = 40131 + slot;
                MATERIALS[mask] = ids;
            }
            for (int count = 1; count < REWARDS.length; count++) {
                int[] ids = new int[count];
                java.util.Arrays.fill(ids, l1j.server.server.model.item.L1ItemId.ADENA);
                REWARDS[count] = ids;
            }
        }
    }

    static void exchangeTotems(L1PcInstance pc, L1Object npc, int objid, String page) {
        final var inventory = pc.getInventory();
        int mask = 0;
        for (int slot = 0; slot < TOTEM_PRICE.length; slot++)
            if (inventory.checkItem(40131 + slot)) mask |= 1 << slot;
        if (mask == 0) {
            SC_NPCTalkReturn.send(pc, objid, page);
            return;
        }
        final int[] materials = TotemRecipes.MATERIALS[mask];
        final int[] counts = new int[materials.length];
        final int[] rewardCounts = new int[materials.length];
        for (int index = 0; index < materials.length; index++) {
            L1ItemInstance item = inventory.findItemId(materials[index]);
            if (item == null) {
                fail(pc, objid, null, null);
                return;
            }
            counts[index] = item.getCount();
            rewardCounts[index] = Math.multiplyExact(counts[index], TOTEM_PRICE[materials[index] - 40131]);
        }
        execute(pc, npc, objid, materials, counts, TotemRecipes.REWARDS[materials.length],
                rewardCounts, null, null, page, null);
    }

    private static boolean validRecipe(int[] ids, int[] counts) {
        if (ids == null || counts == null || ids.length == 0 || ids.length != counts.length) return false;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] <= 0 || counts[i] <= 0 || counts[i] > L1Inventory.MAX_AMOUNT) return false;
        }
        return true;
    }

    private static void complete(L1PcInstance pc, int objid, String npcName, int[] rewards,
            int[] counts, String success, String tail, String[] data, long dialogueTarget, int dialogueVersion) {
        for (int i = 0; i < rewards.length; i++) {
            L1Item def = ItemTable.getInstance().getTemplate(rewards[i]);
            if (def == null) continue;
            String name = def.getName();
            if (counts[i] > 1) name += " (" + counts[i] + ")";
            SC_ServerMessage.send(pc, 143, npcName, name);
        }
        if ((success != null || tail != null)
                && !HtmlCmdHandler.canReplyToDialogue(pc, dialogueTarget, dialogueVersion)) return;
        if (success != null) SC_NPCTalkReturn.send(pc, objid, success, data);
        if (tail != null) SC_NPCTalkReturn.send(pc, objid, tail, data);
    }

    private static void fail(L1PcInstance pc, int objid, String failure, String[] data) {
        if (failure != null) SC_NPCTalkReturn.send(pc, objid, failure, data);
        else SC_SystemMessage.send(pc, "제작을 완료하지 못했습니다.");
    }
}
