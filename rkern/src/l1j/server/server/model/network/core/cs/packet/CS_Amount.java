package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.model.Instance.L1AuctionBoardInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1AuctionTransaction;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.model.world.instance.inn.loader.InnHelperLoader;
import l1j.server.server.templates.L1House;

public final class CS_Amount {
    private CS_Amount() {}

    public static void handle(CUserSocket client, int objectId, int amount, String actionName) {
        if (client == null || actionName == null || amount <= 0 || amount > CInventory.MAX_AMOUNT) return;
        final L1PcInstance pc = client.getActiveChar();
        if (!HtmlCmdHandler.canInteractWithNpc(client, pc)) return;
        final L1Object object = L1World.getInstance().findObject(objectId);
        if (!(object instanceof L1NpcInstance npc) || !HtmlCmdHandler.isNpcTargetInRange(pc, npc)) return;
        HtmlCmdHandler.trackDialogue(client, npc, false);

        if (InnHelperLoader.getInstance().onResult(npc, pc, actionName, amount)) return;

        // Preserve the old StringTokenizer whitespace and case rules without
        // allocating a tokenizer or throwing on ordinary one-word XML actions.
        final int len = actionName.length();
        int start = 0;
        while (start < len && separator(actionName.charAt(start))) start++;
        int end = start;
        while (end < len && !separator(actionName.charAt(end))) end++;
        boolean bid = end - start == 7 && actionName.regionMatches(true, start, "agapply", 0, 7);
        boolean sell = end - start == 6 && actionName.regionMatches(true, start, "agsell", 0, 6);
        if (bid || sell) {
            int numberStart = end;
            while (numberStart < len && separator(actionName.charAt(numberStart))) numberStart++;
            int numberEnd = numberStart;
            while (numberEnd < len && !separator(actionName.charAt(numberEnd))) numberEnd++;
            if (numberStart == numberEnd) return;
            final int houseId;
            try { houseId = Integer.parseInt(actionName, numberStart, numberEnd, 10); }
            catch (NumberFormatException invalid) { return; }
            if (houseId <= 0) return;
            if (bid) {
                if (npc instanceof L1AuctionBoardInstance) L1AuctionTransaction.bid(pc, houseId, amount);
            } else {
                if (amount < 100000) return; // SC_SellHouse advertised minimum
                L1House house = HouseTable.getInstance().getHouseTable(houseId);
                if (house != null && house.getKeeperId() == npc.getNpcId())
                    L1AuctionTransaction.list(pc, house, amount);
            }
            return;
        }

        final L1NpcAction npcAction = NpcActionTable.getInstance().get(actionName, pc, npc);
        if (npcAction != null) npcAction.dispatch(actionName, pc, npc, null, amount);
    }

    private static boolean separator(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }
}
