package l1j.server.server.model.network.core.cs.packet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.shop.PlayerShopView;
import l1j.server.server.model.shop.dynamic.DynamicShopItem;
import l1j.server.server.model.shop.dynamic.DynamicShopStorage;
import l1j.server.server.monitor.LoggerInstance;

final class PlayerShopTransactions {
    private static final Logger LOG = Logger.getLogger(PlayerShopTransactions.class.getName());

    private PlayerShopTransactions() {}

    static boolean canReach(L1PcInstance customer, L1PcInstance shop) {
        return customer != shop && shop.isPrivateShop() && !customer.isGhost() && !customer.isDead()
                && customer.getMapId() == shop.getMapId()
                && Math.abs((long) customer.getX() - shop.getX()) <= 12
                && Math.abs((long) customer.getY() - shop.getY()) <= 12;
    }

    static void execute(L1PcInstance customer, L1PcInstance shop, boolean purchase,
            int size, long first, byte[] rest) {
        if (size <= 0 || !canReach(customer, shop) || !canTrade(customer)) return;
        if (customer.getAccountName().equalsIgnoreCase(shop.getAccountName())) {
            SC_SystemMessage.send(customer, "한 계정 내 무인상점은 이용할 수 없습니다.");
            return;
        }
        final var client = customer.getNetConnection();
        final PlayerShopView view = client == null ? null : client.getPlayerShopView();
        if (view == null || !view.matches(shop, purchase)) {
            if (client != null) client.setPlayerShopView(null);
            SC_SystemMessage.send(customer, "상점 목록을 다시 열어 주세요.");
            return;
        }
        final ArrayList<DynamicShopItem> listings = purchase ? shop.getPurchasings() : shop.getSellings();
        if (listings == null) return;
        final L1PcInstance seller = purchase ? customer : shop;
        final L1PcInstance buyer = purchase ? shop : customer;

        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(customer, shop)) {
            synchronized (listings) {
                if (!view.matches(shop, purchase)
                        || listings != (purchase ? shop.getPurchasings() : shop.getSellings())) return;
                if (size == 1) {
                    executeSingle(tx, customer, shop, seller, buyer, purchase, listings, view, first);
                    return;
                }
                final int rowCount = view.size();
                final int[] requestedByRow = new int[rowCount];
                final int capacity = Math.max(1, Math.min(size, rowCount));
                final var items = new Int2ObjectLinkedOpenHashMap<Transfer>(capacity);
                final var debits = new Int2ObjectLinkedOpenHashMap<ListingDebit>(capacity);
                final long now = System.currentTimeMillis();
                long total = 0;
                for (int i = 0; i < size; i++) {
                    final int row = purchase ? CS_Result.param3(first, rest, i) : CS_Result.param1(first, rest, i);
                    final int count = purchase ? CS_Result.param2CH(first, rest, i)
                            : (int) CS_Result.param2D(first, rest, i);
                    if (row < 0 || row >= rowCount || count <= 0 || count > view.count(row)) return;
                    if ((long) requestedByRow[row] + count > view.count(row)) return;
                    requestedByRow[row] += count;
                    final int listingId = view.listingId(row);
                    ListingDebit debit = debits.get(listingId);
                    if (debit == null) {
                        DynamicShopItem listing = findListing(listings, listingId);
                        if (listing == null || listing.isPurchase != purchase || listing.price != view.price(row)) return;
                        debit = new ListingDebit(listing);
                        debits.put(listingId, debit);
                    }
                    final int itemId = purchase ? CS_Result.param1(first, rest, i) : listingId;
                    if (purchase && itemId != view.customerItemId(row)) return;
                    Transfer transfer = items.get(itemId);
                    if (transfer == null) {
                        L1ItemInstance item = seller.getInventory().getItem(itemId);
                        if (!canTransfer(seller, item, debit.expected, count, now)) return;
                        transfer = new Transfer(debit, item);
                        items.put(itemId, transfer);
                    }
                    if (transfer.listing != debit || (long) transfer.count + count > transfer.item.getCount()
                            || (long) debit.count + count > debit.expected.count
                            || (!transfer.item.isStackable() && transfer.count != 0)) return;
                    transfer.count += count;
                    debit.count += count;
                    total += (long) debit.expected.price * count;
                    if (total <= 0 || total > L1Inventory.MAX_AMOUNT) return;
                }
                final L1ItemInstance adena = buyer.getInventory().findItemId(L1ItemId.ADENA);
                if (adena == null || adena.getCount() < total) {
                    SC_ServerMessage.send(buyer, 189);
                    return;
                }
                for (Transfer transfer : items.values()) {
                    if (tx.transfer(seller, transfer.item, transfer.count, buyer) == null) return;
                }
                if (tx.transfer(buyer, adena, (int) total, seller) == null) return;
                tx.requireTables("character_shop");
                final int shopId = shop.getId();
                final int[] listingIds = debits.keySet().toIntArray();
                Arrays.sort(listingIds);
                final DynamicShopItem[] expected = new DynamicShopItem[listingIds.length];
                final int[] amounts = new int[listingIds.length];
                for (int i = 0; i < listingIds.length; i++) {
                    final ListingDebit debit = debits.get(listingIds[i]);
                    expected[i] = debit.expected;
                    amounts[i] = debit.count;
                }
                tx.sql(con -> DynamicShopStorage.consumeListings(con, shopId, expected, amounts));
                tx.onFailure(() -> sendFailure(customer));
                tx.commit(() -> {
                    synchronized (listings) {
                        requireList(shop, purchase, listings);
                        for (ListingDebit debit : debits.values())
                            requireListing(listings, debit.listing, debit.expected);
                        for (ListingDebit debit : debits.values())
                            shop.applyCommittedShopQuantity(purchase, debit.listing, debit.expected.count, debit.count);
                    }
                    if (!purchase) {
                        for (Transfer transfer : items.values())
                            notifySale(customer, shop, transfer.item, transfer.count);
                    }
                });
            }
        } catch (IllegalStateException | java.util.concurrent.RejectedExecutionException rejected) {
            sendFailure(customer);
        }
    }

    private static void executeSingle(L1InventoryTransaction tx, L1PcInstance customer,
            L1PcInstance shop, L1PcInstance seller, L1PcInstance buyer, boolean purchase,
            ArrayList<DynamicShopItem> listings, PlayerShopView view, long first) {
        final int row = purchase ? CS_Result.param3(first, null, 0) : CS_Result.param1(first, null, 0);
        final int count = purchase ? CS_Result.param2CH(first, null, 0) : (int) CS_Result.param2D(first, null, 0);
        if (row < 0 || row >= view.size() || count <= 0 || count > view.count(row)) return;
        final int listingId = view.listingId(row);
        final DynamicShopItem listing = findListing(listings, listingId);
        if (listing == null || listing.isPurchase != purchase || listing.price != view.price(row)
                || listing.price <= 0 || count > listing.count) return;
        final int itemId = purchase ? CS_Result.param1(first, null, 0) : listingId;
        if (purchase && itemId != view.customerItemId(row)) return;
        final L1ItemInstance item = seller.getInventory().getItem(itemId);
        if (!canTransfer(seller, item, listing, count, System.currentTimeMillis())
                || count > item.getCount()) return;
        final long total = (long) listing.price * count;
        if (total > L1Inventory.MAX_AMOUNT) return;
        final L1ItemInstance adena = buyer.getInventory().findItemId(L1ItemId.ADENA);
        if (adena == null || adena.getCount() < total) {
            SC_ServerMessage.send(buyer, 189);
            return;
        }
        if (tx.transfer(seller, item, count, buyer) == null
                || tx.transfer(buyer, adena, (int) total, seller) == null) return;
        final DynamicShopItem expected = DynamicShopStorage.copyListing(listing);
        final int shopId = shop.getId();
        tx.requireTables("character_shop");
        tx.sql(con -> DynamicShopStorage.consumeListing(con, shopId, expected, count));
        tx.onFailure(() -> sendFailure(customer));
        tx.commit(() -> {
            synchronized (listings) {
                requireList(shop, purchase, listings);
                requireListing(listings, listing, expected);
                shop.applyCommittedShopQuantity(purchase, listing, expected.count, count);
            }
            if (!purchase) notifySale(customer, shop, item, count);
        });
    }

    private static void requireList(L1PcInstance shop, boolean purchase, ArrayList<DynamicShopItem> listings) {
        if (listings != (purchase ? shop.getPurchasings() : shop.getSellings()))
            throw new IllegalStateException("Player shop changed after commit");
    }

    private static void requireListing(ArrayList<DynamicShopItem> listings, DynamicShopItem listing, DynamicShopItem expected) {
        if (!listings.contains(listing) || listing.objId != expected.objId || listing.itemId != expected.itemId
                || listing.count != expected.count || listing.price != expected.price
                || listing.isPurchase != expected.isPurchase || listing.enchant != expected.enchant
                || listing.attr != expected.attr || listing.iden != expected.iden)
            throw new IllegalStateException("Player shop listing changed after commit");
    }

    private static void notifySale(L1PcInstance customer, L1PcInstance shop, L1ItemInstance item, int count) {
        try {
            SC_ServerMessage.send(shop, 877, customer.getName(), item.getItem().getName() + " (" + count + ")");
            LoggerInstance.getInstance().개인상점구매(true, customer, shop, item, count);
        } catch (RuntimeException notificationFailure) {
            LOG.log(Level.WARNING, "Committed shop purchase notification failed", notificationFailure);
        }
    }

    private static boolean canTrade(L1PcInstance customer) {
        if (Config.STANDBY_SERVER) {
            customer.sendPackets("오픈대기 상태에선 해당 행동이 불가능 합니다.");
            return false;
        }
        if (customer.getLevel() >= Config.PC_SHOP && customer.getClanid() <= 0 && !customer.isGm()) {
            customer.sendPackets("개인상점 이용을 위해 혈맹에 가입해 주세요.");
            return false;
        }
        return true;
    }

    private static DynamicShopItem findListing(ArrayList<DynamicShopItem> listings, int id) {
        for (int i = 0; i < listings.size(); i++) {
            DynamicShopItem listing = listings.get(i);
            if (listing != null && listing.objId == id) return listing;
        }
        return null;
    }

    private static boolean canTransfer(L1PcInstance seller, L1ItemInstance item, DynamicShopItem listing,
            int count, long now) {
        return item != null && !item.isEquipped() && item.getItem().isTradable() && item.getBless() < 128
                && item.getItemdelay3() < now && item.getItemId() == listing.itemId
                && item.getEnchantLevel() == listing.enchant && item.getAttrEnchantLevel() == listing.attr
                && (item.isStackable() || count == 1)
                && CompanionInstanceCache.is_companion_oblivion(item.getId())
                && !CS_PersonalShop.isPetItem(seller, item.getId()) && !CS_PersonalShop.isDollItem(seller, item.getId());
    }

    private static void sendFailure(L1PcInstance customer) {
        SC_SystemMessage.send(customer, "상점 처리를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static final class ListingDebit {
        final DynamicShopItem listing;
        final DynamicShopItem expected;
        int count;

        ListingDebit(DynamicShopItem listing) {
            this.listing = listing;
            expected = DynamicShopStorage.copyListing(listing);
        }
    }

    private static final class Transfer {
        final ListingDebit listing;
        final L1ItemInstance item;
        int count;

        Transfer(ListingDebit listing, L1ItemInstance item) {
            this.listing = listing;
            this.item = item;
        }
    }
}
