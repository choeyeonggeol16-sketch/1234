package l1j.server.server.model.network.core.cs.packet;

import java.util.Calendar;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TimeZone;

import l1j.server.Config;
import l1j.server.MJINNSystem.Loader.MJINNHelperLoader;
import l1j.server.server.datatables.AuctionBoardTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.npc.L1NpcHtml;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.templates.L1AuctionBoard;
import l1j.server.server.templates.L1House;

public final class CS_Amount {

    private CS_Amount() {}

    public static void handle(CIOClient client, int objectId, int amount, String actionName) {

        if (client == null) {
            return;
        }

        final long longAmount = amount;

        if (longAmount <= 0) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || actionName == null) {
            return;
        }

        final L1Object object = L1World.getInstance().findObject(objectId);

        if (!(object instanceof L1NpcInstance)) {
            return;
        }

        final L1NpcInstance npc = (L1NpcInstance) object;

        if (MJINNHelperLoader.getInstance().onResult(npc, pc, actionName, amount)) {
            return;
        }

        String s1 = "";
        String s2 = "";

        try {
            final StringTokenizer tokenizer = new StringTokenizer(actionName);
            s1 = tokenizer.nextToken();
            s2 = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            s1 = "";
            s2 = "";
        }

        final AuctionBoardTable boardTable = AuctionBoardTable.getInstance();

        /*
         * -------------------------------------------------
         * 경매 입찰 (agapply)
         * -------------------------------------------------
         */
        if (s1.equalsIgnoreCase("agapply")) {
            try {l1j.server.server.model.L1AuctionTransaction.bid(pc,Integer.parseInt(s2),amount);}
            catch(NumberFormatException invalid) {return;}
            return;
        }

        /*
         * -------------------------------------------------
         * 아지트 매도 / 경매 등록 (agsell)
         * -------------------------------------------------
         */
        if (s1.equalsIgnoreCase("agsell")) {
            try {l1j.server.server.model.L1AuctionTransaction.list(pc,
                HouseTable.getInstance().getHouseTable(Integer.parseInt(s2)),amount);}
            catch(NumberFormatException invalid) {return;}
            return;
        }

        /*
         * -------------------------------------------------
         * 일반 NPC amount 액션 (다이얼로그 처리 등)
         * -------------------------------------------------
         */
        final L1NpcAction npcAction = NpcActionTable.getInstance().get(actionName, pc, npc);

        if (npcAction == null) {
            return;
        }

        final L1NpcHtml result = npcAction.executeWithAmount(actionName, pc, npc, amount);

        if (result != null) {
            SC_NPCTalkReturn.send(pc, npc.getId(), result);
        }
    }
}