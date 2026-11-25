package l1j.server.server.model.network.core.cs.packet;

import java.util.List;

import l1j.server.Config;
import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightHistory;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.npc.shop.NpcShopManager;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.model.shop.L1ShopBuyOrderList;
import l1j.server.server.model.shop.L1ShopSellOrderList;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.templates.L1ShopItem;

final class MerchantTransactions {
    private MerchantTransactions() {}

    static void execute(CUserSocket client, L1PcInstance pc, boolean buying, boolean dynamic,
            int objectId, int npcId, String npcName, int size, long first, byte[] rest) {
        if (pc.isGhost() || pc.isDead()) return;
        if (Config.STANDBY_SERVER && (dynamic ? buying : !buying)) {
            pc.sendPackets("오픈대기 상태에선 해당 행동이 불가능 합니다.");
            return;
        }
        if (pc.getLevel() >= Config.PC_SHOP && pc.getClanid() <= 0 && !pc.isGm()) {
            pc.sendPackets("\\fY레벨 " + Config.PC_SHOP + " 이상 무혈 캐릭터는 상점을 이용할 수 없습니다");
            pc.sendPackets("\\fY최소한 혈가입은 서버에 원활한 게임을 위한 시스템입니다");
            return;
        }
        if (!dynamic && DogFightSettings.CASTER_NPC_ID == npcId) {
            handleDogfight(pc, buying, objectId, size, first, rest);
            return;
        }
        final L1Shop shop = dynamic ? NpcShopManager.getInstance().getShop(npcId) : ShopTable.getInstance().get(npcId);
        if (shop == null) return;
        if (buying) buy(client, pc, shop, dynamic, npcId, npcName, size, first, rest);
        else sell(pc, shop, dynamic, npcId, size, first, rest);
    }

    private static void buy(CUserSocket client, L1PcInstance pc, L1Shop shop, boolean dynamic,
            int npcId, String npcName, int size, long first, byte[] rest) {
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
            else client.kick();
            return;
        }
        final L1ShopBuyOrderList orders = shop.newBuyOrderList();
        // Pin the exact products accepted by the order builder. A reload or
        // stock removal can change list indices before the receipt callback.
        final L1ShopItem[] auditItems = size > 1 ? new L1ShopItem[size] : null;
        final int[] auditCounts = size > 1 ? new int[size] : null;
        L1ShopItem singleItem = null;
        boolean fishing = false;
        for (int i = 0; i < size; i++) {
            final int row = CS_Result.param1(first, rest, i);
            final int count = (int) CS_Result.param2D(first, rest, i);
            if (row < 0 || row >= items.size()) {
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
            orders.add(row, count, pc);
            if (orders.BugOk() != 0) {
                if (!dynamic) notifyOrderLimit(pc, count);
                return;
            }
            final L1ShopItem accepted = orders.getList().getLast().getItem();
            if (auditItems == null) {
                singleItem = accepted;
            } else {
                auditItems[i] = accepted;
                auditCounts[i] = count;
            }
            if (!dynamic && accepted.getItemId() == 41295) fishing = true;
        }
        final boolean updateFishing = fishing;
        final L1ShopItem settledItem = singleItem;
        shop.sellItems(pc, orders, () -> {
            if (auditItems == null) logBuy(pc, settledItem, npcName, (int) (first >>> 32), dynamic);
            else for (int i = 0; i < auditItems.length; i++)
                logBuy(pc, auditItems[i], npcName, auditCounts[i], dynamic);
            if (updateFishing) pc.setFishingShopBuyTime_1(System.currentTimeMillis());
        });
    }

    private static void sell(L1PcInstance pc, L1Shop shop, boolean dynamic,
            int npcId, int size, long first, byte[] rest) {
        final L1ShopSellOrderList orders = shop.newSellOrderList(pc);
        for (int i = 0; i < size; i++) {
            final int id = CS_Result.param1(first, rest, i);
            final int count = (int) CS_Result.param2D(first, rest, i);
            if (count <= 0 || count > CInventory.MAX_AMOUNT || repeated(first, rest, i, id)) return;
            final var item = pc.getInventory().GetByServerID(id);
            if (item == null || item.isEquipped()) return;
            if (!dynamic && npcId >= 6100000 && npcId <= 6100035 && !item.isPackage()) {
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

    private static void logBuy(L1PcInstance pc, L1ShopItem item, String npcName, int count, boolean dynamic) {
        String name = item.getItem().getName();
        if (dynamic) name = "+" + item.getEnchant() + " " + name;
        LoggerInstance.getInstance().addShop(name, count, (long) item.getPrice() * count, npcName, pc.getName());
    }

    private static void notifyOrderLimit(L1PcInstance pc, int count) {
        try (var shared = new CAsyncSendConstBuffer(
                SC_SystemMessage.nativeWriter(pc.getName() + "님 상점 최대구매 수량 (" + count + ")를 초과 하였습니다."),
                Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance recipient : L1World.getInstance().getAllPlayers())
                if (recipient == pc || recipient.isGm()) CSharedBroadcast.sendRecipient(shared, recipient);
        }
    }

    private static void handleDogfight(L1PcInstance pc, boolean buying, int objectId, int size,
            long first, byte[] rest) {
        final int[] ids = new int[size];
        final long[] counts = new long[size];
        for (int i = 0; i < size; i++) {
            ids[i] = CS_Result.param1(first, rest, i);
            counts[i] = CS_Result.param2D(first, rest, i);
        }
        if (buying) DogFightHistory.getInstance().on_buy_ticket(pc, ids, counts, size);
        else DogFightHistory.getInstance().on_sell_ticket(pc, ids, counts, size, objectId);
    }
}
