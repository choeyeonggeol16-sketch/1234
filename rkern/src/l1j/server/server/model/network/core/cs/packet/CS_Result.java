package l1j.server.server.model.network.core.cs.packet;

import java.util.ArrayList;

import l1j.server.Config;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Warehouse.CWareHouseRequest;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.model.shop.L1ShopBuyOrder;
import l1j.server.server.model.shop.L1ShopBuyOrderList;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_Result {

	private CS_Result() {
	}

	public static void handle(CUserSocket client, int npcObjectId, int resultType, int size, int hasFirst, long packed0, byte[] params) {

		if (client == null) {
			return;
		}

        if (size < 0 || size > 255 || (size > 0 && (hasFirst == 0
                || (size > 1 && (params == null || params.length < (size - 1) * Long.BYTES))))) return;

		L1PcInstance pc = client.getActiveChar();
		if (pc == null)
			return;

		boolean isPrivateShop = false;
		boolean isPrivateNpcShop = false;

        int level;
		int npcId = 0;
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

		L1Object findObject = L1World.getInstance().findObject(npcObjectId);
        // Also handle cancel after the NPC disappeared; only the matching view is affected.
        if (size == 0 && (resultType == 3 || resultType == 5 || resultType == 9
                || resultType == 10 || resultType == 20)
                && client.CloseWarehouseView(pc, npcObjectId, resultType)) return;
		String npcName = "";

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

			if (findObject instanceof L1NpcInstance) {
				L1NpcInstance targetNpc = (L1NpcInstance) findObject;
				npcId = targetNpc.getNpcTemplate().get_npcId();
				npcImpl = targetNpc.getNpcTemplate().getImpl();
				npcName = targetNpc.getName();

				if (npcImpl.equals("L1NpcShop"))
					isPrivateNpcShop = true;

			} else if (findObject instanceof L1PcInstance) {
				L1PcInstance gm = (L1PcInstance) findObject;
				if (gm == pc && pc.isGm() && resultType == 3) {
					if (hasFirst != 0) {
						int objectId = param1(packed0, params, 0);
						L1Object tmp = L1World.getInstance().findObject(objectId);
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
            level = pc.getLevel();
            offline = client.getActiveChar() != pc || pc.getOnlineStatus() == 0;
        } finally {
            pc.unlockUserState();
        }
        if (offline) {
            client.kick();
            return;
        }

		if (Config.혈맹포인트상점) {
			if (npcId >= 66110 && npcId <= 66120) {
				if (!pc.isGm()) {
                    final L1Clan clan = pc.getClan();
					if (clan != null) {
						if (pc.getId() != clan.getLeaderId()) {
							SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3혈맹의 군주만 구매가능한 상점입니다.");
							SC_SystemMessage.send(pc, "\\f3혈맹의 군주만 구매가능한 상점입니다.");
							return;
						}
					} else {
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3혈맹의 군주만 구매가능한 상점입니다.");
						SC_SystemMessage.send(pc, "\\f3혈맹의 군주만 구매가능한 상점입니다.");
					}
				}
			}
		}

		if (npcId == 454599) {
			SC_SystemMessage.send(pc, "\\f3자동사냥 시작 시 캐릭터가 자동으로 구매하는 상점입니다");
			SC_SystemMessage.send(pc, "\\f3일반상태에서는 구입할 수 없습니다.");
			return;
		}

		if (npcObjectId == 7626) {
			npcId = 5;
			npcImpl = "L1Merchant";
		}
		if (npcObjectId == 8626) {
			npcId = 8;
			npcImpl = "L1Merchant";
		}

		if (npcObjectId >= 73201211 && npcObjectId <= 73201218) {
			npcId = npcObjectId;
			npcImpl = "L1Merchant";
		}

		if (npcObjectId >= 2020562 && npcObjectId <= 2020577) {
			npcId = npcObjectId;
			npcImpl = "L1Merchant";
		}

        final boolean warehouse = (resultType == 20 && pc == findObject)
                || (npcImpl.equalsIgnoreCase("L1Dwarf") && CWareHouseRequest.IsDwarfAction(resultType));
        // Warehouse result kinds are disjoint from ordinary shop/exchange kinds.
        // Route every selection count, including one, before legacy exchange flags.
        if (warehouse) {
            CWareHouseRequest.Request(client, pc, npcObjectId, resultType, size, packed0, params);
            return;
        }

		// 아이템 교환 로직 (1 ~ 20)
		if (processChangeItems(pc, size, packed0, params)) {
			return;
		}

        if (size == 0 || (resultType != 0 && resultType != 1)) return;
        if (npcImpl.equalsIgnoreCase("L1Merchant") || isPrivateNpcShop) {
            MerchantTransactions.execute(client, pc, resultType == 0, isPrivateNpcShop,
                    npcObjectId, npcId, npcName, size, packed0, params);
        } else if (isPrivateShop) {
            PlayerShopTransactions.execute(pc, (L1PcInstance) findObject, resultType == 1, size, packed0, params);
        }
    }

	private static boolean processChangeItems(L1PcInstance pc, int size, long packed0, byte[] params) {
		for (int n = 1; n <= 20; n++) {
			boolean isChange = false;
			int changeNpcId = 0;
			L1ItemInstance defaultItem = null;
			int consumeMaterialId = 77100 + (n == 5 ? 0 : (n == 1 ? 1 : (n == 2 ? 2 : (n == 3 ? 3 : (n == 4 ? 4 : (n == 6 ? 5 : (n == 7 ? 6 : (n == 8 ? 7 : (n == 9 ? 8 : (n == 10 ? 9 : (n == 11 ? 10 : (n == 12 ? 11 : (n == 13 ? 12 : (n == 14 ? 13 : (n == 15 ? 14 : (n == 16 ? 15 : (n == 17 ? 16 : (n == 18 ? 17 : (n == 19 ? 18 : 19)))))))))))))))))));

			// 각 교환 번호별 세팅 연동
			switch (n) {
				case 1: isChange = pc.getIsChangeItem1(); changeNpcId = pc.changeNpcId1; defaultItem = pc.getDefaultItem1(); consumeMaterialId = 77101; break;
				case 2: isChange = pc.getIsChangeItem2(); changeNpcId = pc.changeNpcId2; defaultItem = pc.getDefaultItem2(); consumeMaterialId = 77102; break;
				case 3: isChange = pc.getIsChangeItem3(); changeNpcId = pc.changeNpcId3; defaultItem = pc.getDefaultItem3(); consumeMaterialId = 77103; break;
				case 4: isChange = pc.getIsChangeItem4(); changeNpcId = pc.changeNpcId4; defaultItem = pc.getDefaultItem4(); consumeMaterialId = 77104; break;
				case 5: isChange = pc.getIsChangeItem5(); changeNpcId = pc.changeNpcId5; defaultItem = pc.getDefaultItem5(); consumeMaterialId = 77100; break;
				case 6: isChange = pc.getIsChangeItem6(); changeNpcId = pc.changeNpcId6; defaultItem = pc.getDefaultItem6(); consumeMaterialId = 77105; break;
				case 7: isChange = pc.getIsChangeItem7(); changeNpcId = pc.changeNpcId7; defaultItem = pc.getDefaultItem7(); consumeMaterialId = 77106; break;
				case 8: isChange = pc.getIsChangeItem8(); changeNpcId = pc.changeNpcId8; defaultItem = pc.getDefaultItem8(); consumeMaterialId = 77107; break;
				case 9: isChange = pc.getIsChangeItem9(); changeNpcId = pc.changeNpcId9; defaultItem = pc.getDefaultItem9(); consumeMaterialId = 77108; break;
				case 10: isChange = pc.getIsChangeItem10(); changeNpcId = pc.changeNpcId10; defaultItem = pc.getDefaultItem10(); consumeMaterialId = 77109; break;
				case 11: isChange = pc.getIsChangeItem11(); changeNpcId = pc.changeNpcId11; defaultItem = pc.getDefaultItem11(); consumeMaterialId = 77110; break;
				case 12: isChange = pc.getIsChangeItem12(); changeNpcId = pc.changeNpcId12; defaultItem = pc.getDefaultItem12(); consumeMaterialId = 77111; break;
				case 13: isChange = pc.getIsChangeItem13(); changeNpcId = pc.changeNpcId13; defaultItem = pc.getDefaultItem13(); consumeMaterialId = 77112; break;
				case 14: isChange = pc.getIsChangeItem14(); changeNpcId = pc.changeNpcId14; defaultItem = pc.getDefaultItem14(); consumeMaterialId = 77113; break;
				case 15: isChange = pc.getIsChangeItem15(); changeNpcId = pc.changeNpcId15; defaultItem = pc.getDefaultItem15(); consumeMaterialId = 77114; break;
				case 16: isChange = pc.getIsChangeItem16(); changeNpcId = pc.changeNpcId16; defaultItem = pc.getDefaultItem16(); consumeMaterialId = 77115; break;
				case 17: isChange = pc.getIsChangeItem17(); changeNpcId = pc.changeNpcId17; defaultItem = pc.getDefaultItem17(); consumeMaterialId = 77116; break;
				case 18: isChange = pc.getIsChangeItem18(); changeNpcId = pc.changeNpcId18; defaultItem = pc.getDefaultItem18(); consumeMaterialId = 77117; break;
				case 19: isChange = pc.getIsChangeItem19(); changeNpcId = pc.changeNpcId19; defaultItem = pc.getDefaultItem19(); consumeMaterialId = 77118; break;
				case 20: isChange = pc.getIsChangeItem20(); changeNpcId = pc.changeNpcId20; defaultItem = pc.getDefaultItem20(); consumeMaterialId = 77119; break;
			}

			if (isChange) {
				if (defaultItem == null) {
					resetChangeFlags(pc, n);
					return true;
				}
				L1Shop shop = ShopTable.getInstance().get(changeNpcId);
				if (shop != null) {
					L1ShopBuyOrderList oList = shop.newBuyOrderList();
					int totalcount = 0;
					int itemno = defaultItem.getItemId();
					if (size > 0) {
						for (int i = 0; i < size; i++) {
							int itemNumber = param1(packed0, params, i);
							long itemcount = param2D(packed0, params, i);

							totalcount++;
							if (totalcount > 1) {
								SC_SystemMessage.send(pc, "아이템은 한개씩 교환할 수 있습니다");
								resetChangeFlags(pc, n);
								return true;
							}
							if (itemcount <= 0L) {
								resetChangeFlags(pc, n);
								return true;
							}
							oList.add(itemNumber, 1, pc);
						}
					}
					if (pc.getInventory().checkItem(consumeMaterialId) && pc.getInventory().checkItem(itemno)) {
						L1ItemInstance item = null;
						for (L1ShopBuyOrder order : oList.getList()) {
							int itemId = order.getItem().getItemId();
							item = ItemTable.getInstance().createItem(itemId);
						}
						if (item != null) {
							item.setEnchantLevel(defaultItem.getEnchantLevel());
							item.setBless(defaultItem.getBless());
							item.setAttrEnchantLevel(defaultItem.getAttrEnchantLevel());
							item.setIdentified(true);
							item.set_bless_level(defaultItem.get_bless_level());
							item.set_item_level(defaultItem.get_item_level());
							pc.getInventory().storeItem(item);
							pc.getInventory().consumeItem(consumeMaterialId, 1);
							pc.getInventory().removeItem(defaultItem, 1);
						}
					} else {
						SC_SystemMessage.send(pc, "재료 아이템이 존재하지 않습니다.");
					}
				}
				resetChangeFlags(pc, n);
				return true;
			}
		}
		return false;
	}

	private static void resetChangeFlags(L1PcInstance pc, int n) {
		switch (n) {
			case 1: pc.setIsChangeItem1(false); pc.setDefaultItem1(null); break;
			case 2: pc.setIsChangeItem2(false); pc.setDefaultItem2(null); break;
			case 3: pc.setIsChangeItem3(false); pc.setDefaultItem3(null); break;
			case 4: pc.setIsChangeItem4(false); pc.setDefaultItem4(null); break;
			case 5: pc.setIsChangeItem5(false); pc.setDefaultItem5(null); break;
			case 6: pc.setIsChangeItem6(false); pc.setDefaultItem6(null); break;
			case 7: pc.setIsChangeItem7(false); pc.setDefaultItem7(null); break;
			case 8: pc.setIsChangeItem8(false); pc.setDefaultItem8(null); break;
			case 9: pc.setIsChangeItem9(false); pc.setDefaultItem9(null); break;
			case 10: pc.setIsChangeItem10(false); pc.setDefaultItem10(null); break;
			case 11: pc.setIsChangeItem11(false); pc.setDefaultItem11(null); break;
			case 12: pc.setIsChangeItem12(false); pc.setDefaultItem12(null); break;
			case 13: pc.setIsChangeItem13(false); pc.setDefaultItem13(null); break;
			case 14: pc.setIsChangeItem14(false); pc.setDefaultItem14(null); break;
			case 15: pc.setIsChangeItem15(false); pc.setDefaultItem15(null); break;
			case 16: pc.setIsChangeItem16(false); pc.setDefaultItem16(null); break;
			case 17: pc.setIsChangeItem17(false); pc.setDefaultItem17(null); break;
			case 18: pc.setIsChangeItem18(false); pc.setDefaultItem18(null); break;
			case 19: pc.setIsChangeItem19(false); pc.setDefaultItem19(null); break;
			case 20: pc.setIsChangeItem20(false); pc.setDefaultItem20(null); break;
		}
		pc.setIsChangeItem(false);
		pc.setDefaultItem(null);
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
