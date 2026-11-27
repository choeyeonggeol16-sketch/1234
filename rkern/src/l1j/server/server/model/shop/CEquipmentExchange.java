package l1j.server.server.model.shop;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.logd.CLogD;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_PremiumShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** Settles a server-issued, session-bound equipment exchange selection. */
public final class CEquipmentExchange {
    private static final Logger LOG = Logger.getLogger(CEquipmentExchange.class.getName());

    private CEquipmentExchange() {}

    private static final int FIRST_TICKET = 77100;
    private static final int FIRST_SHOP = 88001;
    private static final int TYPES = 20;

    public record Selection(int shopId, L1ItemInstance ticket, L1ItemInstance source) {}

    public static void open(L1PcInstance pc, L1ItemInstance ticket, L1ItemInstance source) {
        final CUserSocket session = pc.getNetConnection();
        if (ticket == null || session == null || !session.ownsReadyGameSession() || session.getActiveChar() != pc
                || pc.isItemTransferBusy() || pc.getTradeID() != 0) return;
        final int index = ticket.getItemId() - FIRST_TICKET;
        if (index < 0 || index >= TYPES || !owned(pc, ticket) || !owned(pc, source)
                || ticket == source || source.getEndTime() != null
                || pc.getInventory().getSize() >= 180 || !allowsSource(index, source.getItemId())) {
            SC_SystemMessage.send(pc, "교환할 수 없는 원본 아이템 또는 변경권입니다.");
            return;
        }
        if (!SC_PremiumShopSellList.sendEquipmentExchange(session, new Selection(FIRST_SHOP + index, ticket, source))) {
            SC_SystemMessage.send(pc, "교환할 수 있는 결과 목록이 없습니다.");
        }
    }

    private static boolean owned(L1PcInstance pc, L1ItemInstance item) {
        return item != null && item.getCount() > 0 && !item.isEquipped()
                && pc.getInventory().GetByServerID(item.getId()) == item;
    }

    private static boolean allowsSource(int index, int itemId) {
        final String ids = switch (index) {
            case 0 -> Config.ChangeItemList1;
            case 1 -> Config.ChangeItemList2;
            case 2 -> Config.ChangeItemList3;
            case 3 -> Config.ChangeItemList4;
            case 4 -> Config.ChangeItemList5;
            case 5 -> Config.ChangeItemList6;
            case 6 -> Config.ChangeItemList7;
            case 7 -> Config.ChangeItemList8;
            case 8 -> Config.ChangeItemList9;
            case 9 -> Config.ChangeItemList10;
            case 10 -> Config.ChangeItemList11;
            case 11 -> Config.ChangeItemList12;
            case 12 -> Config.ChangeItemList13;
            case 13 -> Config.ChangeItemList14;
            case 14 -> Config.ChangeItemList15;
            case 15 -> Config.ChangeItemList16;
            case 16 -> Config.ChangeItemList17;
            case 17 -> Config.ChangeItemList18;
            case 18 -> Config.ChangeItemList19;
            case 19 -> Config.ChangeItemList20;
            default -> null;
        };
        if (ids == null || ids.isBlank()) return false;
        for (String value : ids.split(",")) {
            try {
                if (Integer.parseInt(value.trim()) == itemId) return true;
            } catch (NumberFormatException ignored) { /* Ignore invalid configuration entries. */ }
        }
        return false;
    }

    public static boolean isOpen(L1PcInstance pc) {
        final CUserSocket session = pc.getNetConnection();
        final NpcShopView view = session == null ? null : session.getNpcShopView();
        return view != null && view.equipmentExchange() != null && view.currentFor(session, pc);
    }

    public static boolean handleResult(CUserSocket session, L1PcInstance pc, int objectId, int resultType,
            int count, int row, long amount) {
        final NpcShopView view = session.getNpcShopView();
        if (view == null || view.equipmentExchange() == null || view.resultId() != objectId) return false;
        if (resultType != 0 || pc.isItemTransferBusy()) return true;
        final Selection selection = view.equipmentExchange();
        if (count == 0 || !view.currentFor(session, pc)) {
            session.clearNpcShopView(view);
            return true;
        }
        if (count != 1 || amount != 1) {
            reject(session, pc, view, "아이템은 한 개씩 교환할 수 있습니다.");
            return true;
        }
        final var offer = view.offer(row);
        final var shop = view.currentShop();
        if (offer == null || shop == null || !shop.canOffer(offer, pc)
                || view.catalogIndex(shop.getSellingItems(), row, offer) < 0
                || pc.getTradeID() != 0) {
            reject(session, pc, view, "교환할 수 없는 선택입니다.");
            return true;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("equipment-exchange:", pc.getId()))) {
            final L1ItemInstance source = selection.source();
            if (session.getNpcShopView() != view || !view.currentFor(session, pc)
                    || view.currentShop() != shop || !owned(pc, source) || !owned(pc, selection.ticket())
                    || source.getEndTime() != null
                    || !allowsSource(selection.ticket().getItemId() - FIRST_TICKET, source.getItemId())
                    || !tx.debit(source, 1) || !tx.debit(selection.ticket(), 1)) {
                reject(session, pc, view, "교환에 필요한 원본 장비 또는 변경권을 사용할 수 없습니다.");
                return true;
            }
            final L1ItemInstance reward = CObjectDB.LoadItem().createItem(offer.getItem().getItemId());
            if (reward == null) {
                reject(session, pc, view, "교환 결과 아이템을 만들 수 없습니다.");
                return true;
            }
            reward.setEnchantLevel(source.getEnchantLevel());
            reward.setBless(source.getBless());
            reward.setAttrEnchantLevel(source.getAttrEnchantLevel());
            reward.setIdentified(true);
            reward.set_bless_level(source.get_bless_level());
            reward.set_item_level(source.get_item_level());
            if (tx.grant(reward) == null) {
                reject(session, pc, view, "교환 결과 아이템을 소지할 수 없습니다.");
                return true;
            }
            if (session.getNpcShopView() != view || !view.currentFor(session, pc)
                    || view.currentShop() != shop) return true;
            tx.onFailure(() -> reject(session, pc, view, "장비 교환을 저장하지 못했습니다."));
            tx.commit(() -> {
                session.clearNpcShopView(view);
                try {
                    CLogD.getInstance().addShop(reward.getLogName(), 1, 0, "장비 교환", pc.getName());
                } catch (RuntimeException failure) {
                    LOG.log(Level.WARNING, "Committed equipment exchange notice failed: " + pc.getId(), failure);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Could not prepare equipment exchange for " + pc.getId(), failure);
            reject(session, pc, view, "장비 교환을 처리하지 못했습니다.");
        }
        return true;
    }

    private static void reject(CUserSocket session, L1PcInstance pc, NpcShopView view, String message) {
        if (session.takeNpcShopView(view) && session.isCurrentCharacter(pc)) SC_SystemMessage.send(pc, message);
    }
}
