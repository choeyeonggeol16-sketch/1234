package l1j.server.server.model.network.core.cs.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

final class WarehouseTransactions {
    private static final int MAX_SELECTION = 100;
    private static final Logger LOG = Logger.getLogger(WarehouseTransactions.class.getName());

    private WarehouseTransactions() {}

    static boolean isDwarfAction(int kind) {
        return switch (kind) {
            case 2, 3, 4, 5, 8, 9, 10 -> true;
            default -> false;
        };
    }

    static void execute(L1PcInstance pc, int kind, int size, long first, byte[] rest) {
        if (size == 0) {
            if (kind == 5 && pc.getClan() != null) pc.getClan().deleteClanRetrieveUser(pc.getId());
            return;
        }
        if (size < 0 || size > MAX_SELECTION) {
            SC_SystemMessage.send(pc, "창고 선택 항목이 너무 많거나 올바르지 않습니다.");
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
            if (pc.getClanRank() == L1Clan.수련 || pc.hasSkillEffect(L1SkillId.SetBuff)
                    || !vault.releaseWarehouseUser(pc.getId())) return;
        }

        final boolean deposit = kind == 2 || kind == 4 || kind == 8;
        final int feeItem = kind == 3 || kind == 5 ? L1ItemId.ADENA : kind == 9 ? 40494 : 0;
        final int feeEach = kind == 3 || kind == 5 ? 30 : kind == 9 ? 2 : 0;
        final var inventory = pc.getInventory();
        final int minimumFee = feeEach * size + (kind == 3 ? 1 : 0);
        if (minimumFee != 0 && inventory.getItemCountDirect(feeItem) < minimumFee) {
            SC_SystemMessage.send(pc, kind == 9 ? "창고 출금 수수료 아이템이 부족합니다." : "아데나가 부족합니다.");
            return;
        }

        final long now = System.currentTimeMillis();
        if (size == 1 && !clan) {
            final int id = CS_Result.param1(first, rest, 0);
            final L1ItemInstance item = deposit ? inventory.getItem(id) : warehouse.getItem(id);
            final int count = selectedCount(pc, item, (int) CS_Result.param2D(first, rest, 0));
            if (count == 0) return;
            moveSingle(pc, kind, warehouse, deposit, feeItem, feeEach, item, count,
                    kind == 20 ? supplementaryDeadline(item, now) : null, null, now);
            return;
        }

        final int[] ids = new int[size];
        final int[] counts = new int[size];
        final L1ItemInstance[] audited = clan ? new L1ItemInstance[size] : null;
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
            final L1ItemInstance item = deposit ? inventory.getItem(id) : warehouse.getItem(id);
            final int count = selectedCount(pc, item, (int) CS_Result.param2D(first, rest, i));
            if (count == 0) return;
            if (deposit && !canDeposit(pc, kind, warehouse, item, count, now)) return;
            counts[i] = count;
            if (audited != null) audited[i] = item;
            if (kind == 20) {
                final Timestamp deadline = supplementaryDeadline(item, now);
                if (deadline != null) {
                    if (deadlines == null) deadlines = new Timestamp[size];
                    deadlines[i] = deadline;
                }
            }
        }

        final Runnable audit = clan ? clanAudit(pc, audited, counts, deposit) : null;
        if (size == 1) {
            moveSingle(pc, kind, warehouse, deposit, feeItem, feeEach, audited[0], counts[0], null, audit, now);
            return;
        }
        final boolean admitted = deposit
                ? inventory.tradeWarehouseDepositBatch(ids, counts, warehouse, audit)
                : inventory.tradeWarehouseWithdrawalBatch(ids, counts, warehouse, feeItem, feeEach, deadlines, audit);
        if (!admitted) SC_SystemMessage.send(pc, deposit
                ? "창고 아이템을 한 번에 맡길 수 없습니다. 아이템과 창고 공간을 확인해 주세요."
                : "창고 아이템을 한 번에 찾을 수 없습니다. 무게와 소지 칸을 확인해 주세요.");
    }

    private static int selectedCount(L1PcInstance pc, L1ItemInstance item, int requested) {
        if (item == null) return 0;
        if (requested <= 0 || requested > L1Inventory.MAX_AMOUNT || item.getCount() <= 0
                || item.getCount() > L1Inventory.MAX_AMOUNT || (!item.isStackable() && requested != 1)) {
            SC_Disconnect.send(pc);
            return 0;
        }
        return Math.min(requested, item.getCount());
    }

    private static boolean canDeposit(L1PcInstance pc, int kind, Warehouse warehouse,
            L1ItemInstance item, int count, long now) {
        if (!pc.isGm() && NoShopAndWare.getInstance().isNoShopAndWare(item.getItemId())) {
            SC_SystemMessage.send(pc, "해당 아이템은 창고 이용을 할 수 없습니다.");
            return false;
        }
        if (kind != 8 && item.getItemdelay3() >= now) return false;
        if (item.isEquipped() || !item.getItem().isTradable() || (kind != 8 && item.getBless() >= 128)
                || !MJCompanionInstanceCache.is_companion_oblivion(item.getId())
                || CS_PersonalShop.isPetItem(pc, item.getId())) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return false;
        }
        if (CS_PersonalShop.isDollItem(pc, item.getId())) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "현재 소환 된 인형은 맡길수가 없습니다.");
            return false;
        }
        if (warehouse.checkAddItemToWarehouse(item, count) == L1Inventory.SIZE_OVER) {
            SC_ServerMessage.send(pc, 75);
            return false;
        }
        return true;
    }

    private static void moveSingle(L1PcInstance pc, int kind, Warehouse warehouse, boolean deposit,
            int feeItem, int feeEach, L1ItemInstance item, int count, Timestamp deadline, Runnable audit, long now) {
        final var inventory = pc.getInventory();
        if (deposit) {
            if (canDeposit(pc, kind, warehouse, item, count, now))
                inventory.tradeItem(item.getId(), count, warehouse, audit);
            return;
        }
        if (inventory.checkAddItem(item, count) != L1Inventory.OK) {
            SC_ServerMessage.send(pc, 270);
            return;
        }
        L1ItemInstance admitted = deadline == null ? warehouse.tradeItem(item, count, inventory, feeItem, feeEach, audit)
                : warehouse.tradeSupplementaryItem(item, count, inventory, deadline, audit);
        if (admitted == null) {
            if (kind == 9) SC_ServerMessage.send(pc, 337, "$767");
            else SC_ServerMessage.send(pc, 189);
        }
    }

    private static Timestamp supplementaryDeadline(L1ItemInstance item, long now) {
        int minutes = switch (item.getItemId()) {
            case 3000214 -> 8640;
            case 3000213, 758, 759, 760, 761 -> 4320;
            case 3000209, 41922, 41923, 41924, 41925, 900077, 772, 773, 774, 775 -> 1440;
            case 210095, 2100950 -> 180;
            default -> 0;
        };
        return minutes == 0 ? null : new Timestamp(now + 60_000L * minutes);
    }

    private static Runnable clanAudit(L1PcInstance pc, L1ItemInstance[] items, int[] counts, boolean deposit) {
        final int characterId = pc.getId();
        final int clanId = pc.getClanid();
        final String characterName = pc.getName();
        final int timestamp = (int) (System.currentTimeMillis() / 1000L);
        final int[] logIds = new int[items.length];
        final String[] itemNames = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            final L1ItemInstance item = items[i];
            final int enchant = item.getEnchantLevel();
            logIds[i] = IdFactory.getInstance().nextId();
            itemNames[i] = item.getItem().getType2() != 0
                    ? (enchant >= 0 ? "+" : "") + enchant + " " + item.getName() : item.getName();
        }
        return () -> {
            try {
                CacheD.getInstance().submitDbJobOrdered(characterId, () -> {
                    try (Connection con = DatabaseFactory.getInstance().getConnection();
                         PreparedStatement sql = con.prepareStatement(
                                 "INSERT INTO clan_warehousehistory SET id=?,clan_id=?,char_name=?,"
                               + "item_name=?,item_count=?,elapsed_time=?,item_getorput=?")) {
                        con.setAutoCommit(false);
                        try {
                            for (int i = 0; i < counts.length; i++) {
                                sql.setInt(1, logIds[i]);
                                sql.setInt(2, clanId);
                                sql.setString(3, characterName);
                                sql.setString(4, itemNames[i]);
                                sql.setInt(5, counts[i]);
                                sql.setInt(6, timestamp);
                                sql.setString(7, deposit ? "맡겼습니다." : "찾았습니다.");
                                sql.addBatch();
                            }
                            final int[] result = sql.executeBatch();
                            if (result.length != counts.length) throw new SQLException("Incomplete clan warehouse audit");
                            for (int row : result)
                                if (row != 1 && row != java.sql.Statement.SUCCESS_NO_INFO)
                                    throw new SQLException("Clan warehouse audit write failed");
                            con.commit();
                        } catch (SQLException failure) {
                            try { con.rollback(); }
                            catch (SQLException rollback) { failure.addSuppressed(rollback); }
                            throw failure;
                        }
                    } catch (SQLException failure) {
                        throw new IllegalStateException("Clan warehouse audit failed: " + characterId, failure);
                    }
                });
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Committed clan warehouse audit could not be queued", failure);
            }
        };
    }
}
