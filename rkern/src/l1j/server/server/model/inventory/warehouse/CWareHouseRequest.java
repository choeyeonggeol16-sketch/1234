package l1j.server.server.model.inventory.warehouse;

import java.sql.Timestamp;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_Result;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Common packet selection decoding and existing owner/CacheD admission. */
public final class CWareHouseRequest {
    private static final int MAX_SELECTION = 100;

    private CWareHouseRequest() {}

    public static boolean IsDwarfAction(int kind) {
        return switch (kind) {
            case 2, 3, 4, 5, 8, 9, 10 -> true;
            default -> false;
        };
    }

    public static void Request(CUserSocket client, L1PcInstance pc, int objectId, int kind, int size, long first, byte[] rest) {
        if (size == 0) {
            client.CloseWarehouseView(pc, objectId, kind);
            return;
        }
        if (size < 0 || size > MAX_SELECTION) {
            SC_SystemMessage.send(pc, "창고 선택 항목이 너무 많거나 올바르지 않습니다.");
            return;
        }
        if (!client.CanUseWarehouse(pc, objectId, kind)) {
            SC_SystemMessage.send(pc, "창고를 다시 열어 주세요.");
            return;
        }
        if (pc.isItemTransferBusy()) {
            SC_SystemMessage.send(pc, "이전 창고 아이템 처리가 끝난 뒤 다시 시도해 주세요.");
            return;
        }
        if (pc.isGhost() || pc.isDead()) return;
        if ((kind == 8 || kind == 9) && (pc.getLevel() < 5 || !pc.isElf())) return;
        if (kind == 5 && pc.getLevel() < 5) return;
        if (Config.STANDBY_SERVER && kind != 10 && kind != 20) {
            pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "오픈대기 상태에서는 창고 이용이 불가능합니다.");
            return;
        }

        final WarehouseManager manager = WarehouseManager.getInstance();
        final boolean clan = kind == 4 || kind == 5;
        final L1Clan membership = clan ? pc.getClan() : null;
        if (clan && membership == null) {
            if (kind == 4) SC_ServerMessage.send(pc, 208);
            return;
        }
        final Warehouse warehouse = switch (kind) {
            case 2, 3 -> manager.getPrivateWarehouse(pc.getAccountName());
            case 4, 5 -> manager.getClanWarehouse(membership.getClanName());
            case 8, 9 -> manager.getElfWarehouse(pc.getAccountName());
            case 10 -> pc.getDwarfForPackageInventory();
            case 20 -> manager.getSupplementaryService(pc.getAccountName());
            default -> null;
        };
        if (warehouse == null) return;
        if (kind == 5) {
            final ClanWarehouse vault = (ClanWarehouse) warehouse;
            // Same lock order as CacheD owner replay: session -> inventory -> vault.
            // Keep the view lease until the existing receipt has reserved BOTH lanes.
            synchronized (client) {
                synchronized (pc.getInventory()) {
                    synchronized (vault) {
                        if (!client.CanUseWarehouse(pc, objectId, kind)
                                || !client.OwnsPledgeWarehouse(pc, vault)) return;
                        try {
                            if (pc.getClan() != membership) return;
                            RequestItems(pc, kind, size, first, rest, vault);
                        } finally {
                            // Submission establishes the CacheD pending-apply fence before
                            // returning. A failed admission also closes the consumed view.
                            client.ClearWarehouseView();
                        }
                    }
                }
            }
            return;
        }
        synchronized (client) {
            // Recheck at admission, not only before loading the warehouse reference.
            if (!client.CanUseWarehouse(pc, objectId, kind)) return;
            if (clan && (pc.getClan() != membership || !((ClanWarehouse) warehouse).HasPledgeMember(pc))) return;
            RequestItems(pc, kind, size, first, rest, warehouse);
        }
    }

    private static void RequestItems(L1PcInstance pc, int kind, int size, long first,
            byte[] rest, Warehouse warehouse) {
        final boolean deposit = CWareHouseDeposit.IsDeposit(kind);
        final int feeItem = deposit ? 0 : CWareHouseWithdraw.FeeItem(kind);
        final int feeEach = deposit ? 0 : CWareHouseWithdraw.FeeEach(kind);
        final var inventory = pc.getInventory();
        if (!deposit && !CWareHouseWithdraw.CheckFee(pc, kind, size, feeItem, feeEach)) return;

        final long now = (deposit && kind != 8) || kind == 20 ? System.currentTimeMillis() : 0L;
        if (size == 1) {
            final int id = CS_Result.param1(first, rest, 0);
            final L1ItemInstance item = deposit ? inventory.GetByServerID(id) : warehouse.GetByServerID(id);
            final int count = selectedCount(pc, item, (int) CS_Result.param2D(first, rest, 0));
            if (count == 0) return;
            if (deposit) CWareHouseDeposit.Request(pc, kind, warehouse, item, count, now);
            else CWareHouseWithdraw.Request(pc, kind, warehouse, feeItem, feeEach, item, count,
                    kind == 20 ? CWareHouseWithdraw.SupplementaryDeadline(item, now) : null);
            return;
        }

        final int[] ids = new int[size];
        final int[] counts = new int[size];
        Timestamp[] deadlines = null;
        for (int i = 0; i < size; i++) {
            final int id = CS_Result.param1(first, rest, i);
            ids[i] = id;
            for (int earlier = 0; earlier < i; earlier++) {
                if (ids[earlier] == id) {
                    SC_SystemMessage.send(pc, "중복으로 선택한 창고 아이템이 있습니다.");
                    return;
                }
            }
            final L1ItemInstance item = deposit ? inventory.GetByServerID(id) : warehouse.GetByServerID(id);
            final int count = selectedCount(pc, item, (int) CS_Result.param2D(first, rest, i));
            if (count == 0) return;
            if (deposit && !CWareHouseDeposit.CanDeposit(pc, kind, warehouse, item, count, now)) return;
            counts[i] = count;
            if (kind == 20) {
                final Timestamp deadline = CWareHouseWithdraw.SupplementaryDeadline(item, now);
                if (deadline != null) {
                    if (deadlines == null) deadlines = new Timestamp[size];
                    deadlines[i] = deadline;
                }
            }
        }

        if (deposit) CWareHouseDeposit.Request(pc, warehouse, ids, counts);
        else CWareHouseWithdraw.Request(pc, warehouse, ids, counts, feeItem, feeEach, deadlines);
    }

    private static int selectedCount(L1PcInstance pc, L1ItemInstance item, int requested) {
        if (item == null) return 0;
        final int available = item.getCount();
        if (requested <= 0 || requested > CInventory.MAX_AMOUNT || available <= 0
                || available > CInventory.MAX_AMOUNT || (!item.isStackable() && requested != 1)) {
            SC_Disconnect.send(pc);
            return 0;
        }
        return Math.min(requested, available);
    }

}
