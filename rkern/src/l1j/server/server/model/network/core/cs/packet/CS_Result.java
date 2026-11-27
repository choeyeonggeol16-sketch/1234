package l1j.server.server.model.network.core.cs.packet;

import java.util.ArrayList;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.inventory.warehouse.CWareHouseRequest;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.shop.CEquipmentExchange;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

public final class CS_Result {

	private CS_Result() {
	}

	public static void handle(CUserSocket client, int npcObjectId, int resultType, int size, int hasFirst, long packed0, byte[] params) {

		if (client == null || !client.ownsReadyGameSession()) {
			return;
		}

        if (size < 0 || size > 255 || (size > 0 && (hasFirst == 0
                || (size > 1 && (params == null || params.length < (size - 1) * Long.BYTES))))) return;

		L1PcInstance pc = client.getActiveChar();
		if (pc == null)
			return;

		boolean isPrivateShop = false;

		String npcImpl = "";

		if (pc.getFindcharno() != 0) {
			ArrayList<String> savechar = new ArrayList<String>();
			savechar.add(pc.getFindchar());
			SC_NPCTalkReturn.send(pc, pc.getId(), "nesavechar", (String[]) savechar.toArray(new String[savechar.size()]));
			pc.setFindcharno(0);
		}

		if (npcObjectId == -1) {
            return; // Retired legacy auto-potion selection packet.
        }

        if (CEquipmentExchange.handleResult(client, pc, npcObjectId, resultType, size,
                param1(packed0, params, 0), param2D(packed0, params, 0))) return;
        if (MerchantTransactions.handleResult(client, pc, npcObjectId, resultType, size, packed0, params)) return;

        if (size == 0 && (resultType == 0 || resultType == 1)) {
            final var view = client.getPlayerShopView();
            if (view != null && view.currentFor(client, pc) && view.targets(npcObjectId, resultType == 1)) {
                client.clearPlayerShopView(view);
                return;
            }
        }

		L1Object findObject = CWorld.getInstance().findObject(npcObjectId);
        // Also handle cancel after the NPC disappeared; only the matching view is affected.
        if (size == 0 && (resultType == 3 || resultType == 5 || resultType == 9
                || resultType == 10 || resultType == 20)
                && client.CloseWarehouseView(pc, npcObjectId, resultType)) return;

		if (findObject != null) {
            if (pc.getMapId() != findObject.getMapId()
                    || Math.abs((long) pc.getX() - findObject.getX()) > 12
                    || Math.abs((long) pc.getY() - findObject.getY()) > 12) {
                if (resultType == 5 && size == 0 && findObject instanceof L1NpcInstance dwarf
                        && dwarf.getNpcTemplate().getImpl().equalsIgnoreCase("L1Dwarf")) {
                    client.CloseWarehouseView(pc, npcObjectId, resultType);
                    pc.sendPackets("창고와 거리가 멀어 아이템을 찾을 수 없습니다.");
                } else {
                    pc.sendPackets("판매/구매 NPC와 거리가 멀어 판매가 취소 되었습니다.");
                }
                return;
            }

			if (findObject instanceof L1NpcInstance targetNpc) {
				npcImpl = targetNpc.getNpcTemplate().getImpl();
				} else if (findObject instanceof L1PcInstance) {
				L1PcInstance gm = (L1PcInstance) findObject;
				if (gm == pc && pc.isGm() && resultType == 3) {
					if (hasFirst != 0) {
						int objectId = param1(packed0, params, 0);
						L1Object tmp = CWorld.getInstance().findObject(objectId);
						if (tmp != null && tmp instanceof L1PcInstance) {
							pc.start_teleport(tmp.getX(), tmp.getY(), tmp.getMapId(), pc.getHeading(), 169, false, false);
                            return;
						} else {
							return;
						}
					}
				}
				isPrivateShop = true;
			}
		}

        final boolean offline;
        pc.lockUserState();
        try {
            offline = client.getActiveChar() != pc || pc.getOnlineStatus() == 0;
        } finally {
            pc.unlockUserState();
        }
        if (offline) {
            client.kick();
            return;
        }

        final boolean warehouse = (resultType == 20 && pc == findObject)
                || (npcImpl.equalsIgnoreCase("L1Dwarf") && CWareHouseRequest.IsDwarfAction(resultType));
        // Warehouse result kinds are disjoint from ordinary shop/exchange kinds.
        // Route every selection count, including one, before legacy exchange flags.
        if (warehouse) {
            CWareHouseRequest.Request(client, pc, npcObjectId, resultType, size, packed0, params);
            return;
        }

        if (isPrivateShop && (findObject != pc || pc.isPrivateShop())) {
            if (size > 0 && (resultType == 0 || resultType == 1))
                User.PrivateStoreBuy(pc, (L1PcInstance) findObject, resultType == 1, size, packed0, params);
            return;
        }

	}

	public static int param1(long packed0, byte[] rest, int index) {
		if (index == 0) {
			return (int) packed0;
		}
		return readD(rest, (index - 1) << 3);
	}

	public static long param2D(long packed0, byte[] rest, int index) {
		if (index == 0) {
			/*
			 * legacy:
			 * long x = readD();
			 *
			 * readD() returns signed int, then widens to long.
			 */
			return (long) (int) (packed0 >>> 32);
		}
		return (long) readD(rest, ((index - 1) << 3) + 4);
	}

	static int param2CH(long packed0, byte[] rest, int index) {
		if (index == 0) {
			return (int) ((packed0 >>> 32) & 0x00FF_FFFFL);
		}
		final int offset = ((index - 1) << 3) + 4;
		return readCH(rest, offset);
	}

	static int param3(long packed0, byte[] rest, int index) {
		if (index == 0) {
			return (int) ((packed0 >>> 56) & 0xFFL);
		}

		final int offset = ((index - 1) << 3) + 7;
		if (rest == null || offset < 0 || offset >= rest.length) {
			return 0;
		}
		return rest[offset] & 0xFF;
	}

	private static int readD(byte[] data, int offset) {
		if (data == null || offset < 0 || offset + 3 >= data.length) {
			return 0;
		}

		return (data[offset] & 0xFF)
				| ((data[offset + 1] & 0xFF) << 8)
				| ((data[offset + 2] & 0xFF) << 16)
				| ((data[offset + 3] & 0xFF) << 24);
	}

	private static int readCH(byte[] data, int offset) {
		if (data == null || offset < 0 || offset + 2 >= data.length) {
			return 0;
		}

		return (data[offset] & 0xFF)
				| ((data[offset + 1] & 0xFF) << 8)
				| ((data[offset + 2] & 0xFF) << 16);
	}

}
