package l1j.server.server.model.network.core.cs.packet;

import java.util.List;

import l1j.server.Config;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.shop.CBuySell;
import l1j.server.server.model.shop.L1ShopBuyOrderList;
import l1j.server.server.model.shop.L1ShopSellOrderList;
import l1j.server.server.model.shop.NpcShopView;
import l1j.server.server.logd.CLogD;
import l1j.server.server.templates.L1ShopItem;

final class MerchantTransactions {
    private MerchantTransactions() {}

    static boolean handleResult(CUserSocket client, L1PcInstance pc, int objectId, int type,
            int size, long first, byte[] rest) {
        final NpcShopView view = client.getNpcShopView();
        if (view == null || view.resultId() != objectId || (type != 0 && type != 1)) return false;
        if (!view.currentFor(client, pc)) {
            client.clearNpcShopView(view);
            return true;
        }
        if (view.buying() != (type == 0)) return true;
        if (size == 0) {
            client.clearNpcShopView(view);
            return true;
        }
        execute(client, pc, view, size, first, rest);
        return true;
    }

    private static void execute(CUserSocket client, L1PcInstance pc, NpcShopView view,
            int size, long first, byte[] rest) {
        final boolean buying = view.buying(), dynamic = view.dynamic();
        final int npcId = view.shopId();
        if (!canUseClanShop(pc, npcId)) return;
        if (npcId == 454599) {
            SC_SystemMessage.send(pc, "\\f3자동사냥 시작 시 캐릭터가 자동으로 구매하는 상점입니다");
            SC_SystemMessage.send(pc, "\\f3일반상태에서는 구입할 수 없습니다.");
            return;
        }
        if (Config.STANDBY_SERVER && (dynamic ? buying : !buying)) {
            pc.sendPackets("오픈대기 상태에선 해당 행동이 불가능 합니다.");
            return;
        }
        if (pc.getLevel() >= Config.PC_SHOP && pc.getClanid() <= 0 && !pc.isGm()) {
            pc.sendPackets("\\fY레벨 " + Config.PC_SHOP + " 이상 무혈 캐릭터는 상점을 이용할 수 없습니다");
            pc.sendPackets("\\fY최소한 혈가입은 서버에 원활한 게임을 위한 시스템입니다");
            return;
        }
        if (view.dogFight()) {
            if (view.isCurrentDogFightRound()) handleDogfight(pc, view, size, first, rest);
            return;
        }
        final CBuySell shop = view.currentShop();
        if (shop == null) {
            client.clearNpcShopView(view);
            return;
        }
        if (buying) {
            final var npc = view.npc();
            buy(pc, shop, view, npc == null ? "" : npc.getName(), size, first, rest);
        } else sell(pc, shop, view, size, first, rest);
    }

    private static boolean canUseClanShop(L1PcInstance pc, int npcId) {
        if (!Config.혈맹포인트상점 || npcId < 66110 || npcId > 66120 || pc.isGm()) return true;
        final L1Clan clan = pc.getClan();
        if (clan != null && pc.getId() == clan.getLeaderId()) return true;
        final String message = "\\f3혈맹의 군주만 구매가능한 상점입니다.";
        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
        SC_SystemMessage.send(pc, message);
        return false;
    }

    private static void buy(L1PcInstance pc, CBuySell shop, NpcShopView view,
            String npcName, int size, long first, byte[] rest) {
        final boolean dynamic = view.dynamic();
        final int npcId = view.shopId();
        final boolean race = !dynamic && (npcId == 70035 || npcId == 70041 || npcId == 70042);
        if (dynamic && size != 1) {
            SC_SystemMessage.send(pc, "한번에 서로 다른아이템을 구입할 수 없습니다.");
            return;
        }
        if (!dynamic && npcId == 7200002)
            SC_ActionUI.send(pc, SC_ActionUI.TAM, pc.getAccount().getTamPoint());
        if (race && BugRaceController.getInstance().getBugState() != 0) return;
        if (race && pc.서버다운중) {
            SC_SystemMessage.send(pc, "서버다운 진행중에는 구매가 불가능합니다.");
            return;
        }
        final List<L1ShopItem> items = shop.getSellingItems();
        if (items.size() < size && !dynamic) {
            if (race) pc.sendPackets("이미 경기가 시작되었습니다.");
            return;
        }
        final L1ShopBuyOrderList orders = shop.newBuyOrderList();
        // Pin the exact products accepted by the order builder. A reload or
        // stock removal can change list indices before the receipt callback.
        final L1ShopItem[] auditItems = size > 1 ? new L1ShopItem[size] : null;
        final int[] auditCounts = size > 1 ? new int[size] : null;
        final int[] auditPrices = size > 1 ? new int[size] : null;
        L1ShopItem singleItem = null;
        boolean fishing = false;
        for (int i = 0; i < size; i++) {
            final int row = CS_Result.param1(first, rest, i);
            final int count = (int) CS_Result.param2D(first, rest, i);
            final L1ShopItem offered = view.offer(row);
            final int catalogIndex = offered == null ? -1 : view.catalogIndex(items, row, offered);
            if (catalogIndex < 0) {
                if (dynamic) pc.sendPackets("현재 구매할 수 없는 상태입니다.");
                return;
            }
            if (count <= 0 || count >= 10000 || repeated(first, rest, i, row)) return;
            if (dynamic && pc.getMapId() == 800 && count > 1000) {
                SC_SystemMessage.send(pc, "최대 구매수량 : 잡템류(1000) / 장비(1)");
                return;
            }
            if (!dynamic && ((npcId >= 6100000 && npcId <= 6100035)
                    || (npcId >= 999500 && npcId <= 999510)) && count > 1) {
                SC_SystemMessage.send(pc, "1개씩 구입할 수 있습니다.");
                return;
            }
            int before = orders.getTotalPriceTaxIncluded();
            orders.add(catalogIndex, count, pc);
            if (orders.BugOk() != 0) {
                if (!dynamic) notifyOrderLimit(pc, count);
                return;
            }
            final L1ShopItem accepted = orders.lastOffer();
            if (accepted != offered) return;
            if (auditItems == null) {
                singleItem = accepted;
            } else {
                auditItems[i] = accepted;
                auditCounts[i] = count;
                auditPrices[i] = orders.getTotalPriceTaxIncluded() - before;
            }
            if (!dynamic && accepted.getItemId() == 41295) fishing = true;
        }
        final boolean updateFishing = fishing;
        final L1ShopItem settledItem = singleItem;
        final int settledPrice = orders.getTotalPriceTaxIncluded();
        shop.sellItems(pc, orders, () -> {
            if (auditItems == null) logBuy(pc, settledItem, npcName, (int) (first >>> 32), settledPrice, dynamic);
            else for (int i = 0; i < auditItems.length; i++)
                logBuy(pc, auditItems[i], npcName, auditCounts[i], auditPrices[i], dynamic);
            if (updateFishing) pc.setFishingShopBuyTime_1(System.currentTimeMillis());
        });
    }

    private static void sell(L1PcInstance pc, CBuySell shop, NpcShopView view,
            int size, long first, byte[] rest) {
        final int npcId = view.shopId();
        final L1ShopSellOrderList orders = shop.newSellOrderList(pc);
        for (int i = 0; i < size; i++) {
            final int id = CS_Result.param1(first, rest, i);
            final int count = (int) CS_Result.param2D(first, rest, i);
            if (!view.includesSale(id) || count <= 0 || count > CInventory.MAX_AMOUNT
                    || repeated(first, rest, i, id)) return;
            final var item = pc.getInventory().GetByServerID(id);
            if (item == null || item.isEquipped()) return;
            if (!view.dynamic() && npcId >= 6100000 && npcId <= 6100035 && !item.isPackage()) {
                SC_SystemMessage.send(pc, "패킷상점에서 구매하지 않은 아이템이 포함되어 있습니다.");
                return;
            }
            orders.add(id, count, pc);
            if (orders.BugOk() != 0) return;
        }
        shop.buyItems(orders);
    }

    private static boolean repeated(long first, byte[] rest, int before, int id) {
        for (int i = 0; i < before; i++)
            if (CS_Result.param1(first, rest, i) == id) return true;
        return false;
    }

    private static void logBuy(L1PcInstance pc, L1ShopItem item, String npcName, int count, int price, boolean dynamic) {
        String name = item.getItem().getName();
        if (dynamic) name = "+" + item.getEnchant() + " " + name;
        CLogD.getInstance().addShop(name, count, price, npcName, pc.getName());
    }

    private static void notifyOrderLimit(L1PcInstance pc, int count) {
        try (var shared = new CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter(pc.getName() + "님 상점 최대구매 수량 (" + count + ")를 초과 하였습니다."),
                Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance recipient : CWorld.getInstance().getAllPlayers())
                if (recipient == pc || recipient.isGm()) CSharedBroadcast.sendRecipient(shared, recipient);
        }
    }

    private static void handleDogfight(L1PcInstance pc, NpcShopView view, int size,
            long first, byte[] rest) {
        final int[] ids = new int[size];
        final long[] counts = new long[size];
        for (int i = 0; i < size; i++) {
            ids[i] = CS_Result.param1(first, rest, i);
            counts[i] = CS_Result.param2D(first, rest, i);
            if (!(view.buying() ? view.includesTicket(ids[i]) : view.includesSale(ids[i]))
                    || counts[i] <= 0 || counts[i] > Integer.MAX_VALUE || repeated(first, rest, i, ids[i])) return;
        }
        if (view.buying()) DogFightHistory.getInstance().on_buy_ticket(pc, ids, counts, size);
        else DogFightHistory.getInstance().on_sell_ticket(pc, ids, counts, size, view.resultId());
    }
}
