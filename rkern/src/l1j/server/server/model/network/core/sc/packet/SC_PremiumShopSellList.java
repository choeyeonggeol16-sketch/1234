package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.shop.NpcShopView;
import l1j.server.server.model.shop.CEquipmentExchange;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CObjectDB;
import java.nio.charset.Charset;
import java.util.List;

import l1j.server.server.Opcodes;
import l1j.server.server.model.shop.ShopTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.shop.CBuySell;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

public final class SC_PremiumShopSellList {
    private static final Charset MS949 = Charset.forName("MS949");
    private static final byte[] EMPTY = new byte[0];

    private SC_PremiumShopSellList() {}

    public static boolean send(L1PcInstance pc, int npcId) {
        return pc != null && send(pc.getNetConnection(), npcId, 0);
    }

    public static boolean send(L1PcInstance pc, int npcId, int objId) {
        return pc != null && send(pc.getNetConnection(), npcId, objId);
    }

    public static boolean send(CUserSocket client, int npcId) {
        return send(client, npcId, 0);
    }

    public static boolean send(CUserSocket client, int npcId, int objId) {
        return send(client, npcId, objId, null);
    }

    public static boolean sendEquipmentExchange(CUserSocket client, CEquipmentExchange.Selection exchange) {
        return send(client, exchange.shopId(), IdFactory.getInstance().nextId(), exchange);
    }

    private static boolean send(CUserSocket client, int npcId, int objId, CEquipmentExchange.Selection exchange) {
        if (npcId == 7320055) return false;
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CBuySell shop = ShopTable.getInstance().get(npcId);
        List<L1ShopItem> list = null;
        if (shop != null) {
            try { list = shop.getSellingItems(); } catch (Exception ignored) {}
        }
        final int limit = list == null ? 0 : list.size();
        final L1ShopItem[] offers = new L1ShopItem[limit];
        final int[] values = new int[limit << 2];
        final byte[][] encodedNames = new byte[limit][];
        final byte[][] statuses = new byte[limit][];
        int count = 0;
        final int tailSize = premiumShopTailSize(npcId);
        long capacity = CIOConfig.HEADER_SIZE + 7L;
        final L1PcInstance pc = connection.getActiveChar();
        final L1TaxCalculator taxes = new L1TaxCalculator(shop == null ? 0 : shop.getNpcId());
        for (int i = 0; i < limit; ++i) {
            final L1ShopItem shopItem = list.get(i);
            if (!shop.canOffer(shopItem, pc)) continue;
            final int price = shop.checkoutPrice(shopItem, taxes);
            if (price < 0) continue;
            final L1Item item = shopItem.getItem();
            final String name;
            if (shopItem.getPackCount() > 1) {
                name = item.getName() + " (" + shopItem.getPackCount() + ")";
            } else if (shopItem.getEnchant() > 0) {
                name = "+" + shopItem.getEnchant() + " " + item.getName();
            } else if (item.getMaxUseTime() > 0) {
                name = item.getName() + " [" + item.getMaxUseTime() + "]";
            } else {
                name = item.getName();
            }
            byte[] status = null;
            final L1Item template = CObjectDB.GetItem(item.getItemId());
            if (template != null) {
                final L1ItemInstance dummy = new L1ItemInstance();
                dummy.setItem(template);
                dummy.setEnchantLevel(shopItem.getEnchant());
                status = dummy.getStatusBytes();
            }
            if (status == null) status = EMPTY;
            if (status.length > 255 || count >= 65535) return false;
            final byte[] encodedName = name == null ? EMPTY : name.getBytes(MS949);
            capacity += 16L + encodedName.length + status.length;
            if (capacity + tailSize > CIOConfig.MAX_WIRE_PACKET_SIZE) return false;
            final int j = count << 2;
            values[j] = i;
            offers[i] = shopItem;
            values[j + 1] = item.getGfxId();
            values[j + 2] = price;
            values[j + 3] = Math.max(0, item.getUseType());
            encodedNames[count] = encodedName;
            statuses[count++] = status;
        }
        capacity += tailSize;
        final NpcShopView view;
        if (exchange != null) {
            if (count == 0 || pc == null || !connection.ownsReadyGameSession()) return false;
            view = NpcShopView.equipmentExchange(connection, pc, objId, shop, offers, exchange);
            if (!view.currentFor(connection, pc)) return false;
        } else {
            view = pc != null && shop != null && CWorld.getInstance().findObject(objId) instanceof L1NpcInstance npc
                    ? NpcShopView.purchase(connection, pc, npc, objId, shop, offers) : null;
        }
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUY_LIST);
            buffer.writeD(objId);
            buffer.writeH(count);
            for (int i = 0; i < count; ++i) {
                final int j = i << 2;
                buffer.writeD(values[j]);
                buffer.writeH(values[j + 1]);
                buffer.writeD(values[j + 2]);
                buffer.writeByte(encodedNames[i]);
                buffer.writeC(0);
                buffer.writeD(values[j + 3]);
                buffer.writeC(statuses[i].length);
                buffer.writeByte(statuses[i]);
            }
            writePremiumShopTail(buffer, npcId);
            if (buffer.position() != (int) capacity) {
                System.err.println("[RPremiumShopSellList] SEND length mismatch npcId="
                        + npcId + " reserved=" + capacity
                        + " written=" + buffer.position());
                return false;
            }
            committed = connection.commitDirectFrame(frame);
            if (committed) connection.publishNpcShopView(pc, view);
            return committed;
        } catch (Assemble.RegisteredCapacityExceededException e) {
            System.err.println("[RPremiumShopSellList] SEND capacity exceeded npcId="
                    + npcId + " reserved=" + capacity
                    + " written=" + frame.buffer().position());
            return false;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int premiumShopTailSize(int npcId) {
        return npcId == 900107 ? 4 : 2;
    }

    private static void writePremiumShopTail(Assemble buffer, int npcId) {
        if (npcId == 7200002) {
            buffer.writeC(253);
            buffer.writeC(255);
        } else if (npcId == 7000077) {
            buffer.writeC(73);
            buffer.writeC(58);
        } else if (npcId == 900107) {
            buffer.writeC(255);
            buffer.writeC(255);
            buffer.writeC(0);
            buffer.writeC(0);
        } else if (npcId == 6000002) {
            buffer.writeH(0x3ccf);
        } else if (npcId == 7310119 || npcId == 7310120
                || npcId == 7310126 || npcId == 7310124 || npcId == 520) {
            buffer.writeH(0x4215);
        } else if (npcId == 7310128) {
            buffer.writeH(0x41D8);
        } else if (npcId == 91245) {
            buffer.writeH(0x3F23);
        } else if (npcId == 70017 || npcId == 460000360 || npcId == 460000361) {
            buffer.writeH(0x4115);
        } else if (npcId == 7320250) {
            buffer.writeH(0x45AF);
        } else if (npcId == 521) {
            buffer.writeH(17619);
        } else if (npcId == 8500140) {
            buffer.writeH(17368);
        } else if (npcId == 2999 || npcId == 2998) {
            buffer.writeH(30050);
        } else if (npcId == 200005 || npcId == 7320222 || npcId == 7320223) {
            buffer.writeH(7);
        } else {
            buffer.writeC(111);
            buffer.writeC(10);
        }
    }
}
