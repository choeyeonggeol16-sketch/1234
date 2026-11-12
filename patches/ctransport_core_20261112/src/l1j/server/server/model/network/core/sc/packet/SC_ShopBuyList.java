package l1j.server.server.model.network.core.sc.packet;


import java.util.List;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.shop.L1AssessedItem;
import l1j.server.server.model.shop.L1Shop;

public final class SC_ShopBuyList {

    private SC_ShopBuyList() {}

    public static boolean send(int objid, L1PcInstance pc) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        final L1Object object = L1World.getInstance().findObject(objid);
        if (!(object instanceof L1NpcInstance)) {
            throw new IllegalArgumentException(String.format(
                    "알 수 없는 오브젝트가 상점 구매목록으로 전송되었습니다.[objectid : %d, L1Object : %d]",
                    objid, object == null ? 0 : object.getL1Type()));
        }
        final L1NpcInstance npc = (L1NpcInstance) object;
        final int npcId = npc.getNpcTemplate().get_npcId();
        final L1Shop shop = ShopTable.getInstance().get(npcId);
        if (shop == null) {
            SC_NoSell.send(pc, npc);
            throw new IllegalArgumentException(
                    String.format("판매 샵을 찾을 수 없습니다.[npcid : %d]", npcId));
        }

        final List<L1AssessedItem> assessedItems = shop.assessItems(pc.getInventory());
        int count = 0;
        if (assessedItems != null) {
            for (int i = 0, n = assessedItems.size(); i < n; ++i) {
                if (assessedItems.get(i) != null) ++count;
            }
        }
        if (count == 0) SC_NoSell.send(pc, npc);
        if (count > 65535) return false;
        final int[] itemData = new int[count << 1];
        if (assessedItems != null) {
            int offset = 0;
            for (int i = 0, n = assessedItems.size(); i < n; ++i) {
                final L1AssessedItem item = assessedItems.get(i);
                if (item == null) continue;
                itemData[offset++] = item.getTargetId();
                itemData[offset++] = item.getAssessedPrice();
            }
        }

        int adenDesc = 0x07;
         if (npcId == 7000077) {
            adenDesc = 14921;
        } else if (npcId == 7320055) {
            try {
                if (client.getAccount() != null) adenDesc = client.getAccount().Ncoin_point;
            } catch (Exception ignored) {}
        }

        final long needed = 11L + (long) count * 8L;
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_SELL_LIST);
            buffer.writeD(objid);
            buffer.writeH(count);
            for (int i = 0; i < itemData.length; i += 2) {
                buffer.writeD(itemData[i]);
                buffer.writeD(itemData[i + 1]);
            }
            buffer.writeH(adenDesc);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
