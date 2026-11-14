package l1j.server.server.model.network.core.cs.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.DogFight.MJDogFightSettings;
import l1j.server.DogFight.History.MJDogFightHistory;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.IdFactory;
import l1j.server.server.NpcActionRange;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.MJDShopSystem.MJDShopStorage;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.ElfWarehouse;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.SupplementaryService;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.npc.shop.NpcShopManager;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.model.shop.L1ShopBuyOrder;
import l1j.server.server.model.shop.L1ShopBuyOrderList;
import l1j.server.server.model.shop.L1ShopSellOrderList;
import l1j.server.server.monitor.Logger.WarehouseType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1ShopItem;
import l1j.server.server.utils.SQLUtil;
import l1j.server.MJDShopSystem.MJDShopItem;

public final class CS_Result {

	private CS_Result() {
	}

	public static void handle(CIOClient client, int npcObjectId, int resultType, int size, int hasFirst, long packed0, byte[] params) {

		if (client == null) {
			return;
		}

		if (size < 0) return;

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
		String npcName = "";

		if (findObject != null) {
			int diffLocX = Math.abs(pc.getX() - findObject.getX());
			int diffLocY = Math.abs(pc.getY() - findObject.getY());

			if (diffLocX > NpcActionRange.get() || diffLocY > NpcActionRange.get()) {
				L1NpcInstance targetNpc = (L1NpcInstance) findObject;
				if (resultType == 5 && size == 0 && targetNpc.getNpcTemplate().getImpl().equalsIgnoreCase("L1Dwarf")) {
					L1Clan clan = pc.getClan();
					if (clan == null)
						return;

					clan.deleteClanRetrieveUser(pc.getId());
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
				if (gm.isGm() && resultType == 3) {
					if (hasFirst != 0) {
						int objectId = param1(packed0, params, 0);
						L1Object tmp = L1World.getInstance().findObject(objectId);
						if (tmp != null && tmp instanceof L1PcInstance) {
							gm.start_teleport(tmp.getX(), tmp.getY(), tmp.getMapId(), gm.getHeading(), 169, false, false);
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
					if (pc.getClanid() != 0) {
						if (pc.getId() != pc.getClan().getLeaderId()) {
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

        final boolean asynchronousWarehouse = (npcImpl.equalsIgnoreCase("L1Dwarf")
                && (resultType == 2 || resultType == 3 || resultType == 4 || resultType == 5
                    || resultType == 8 || resultType == 9))
                || (resultType == 20 && pc == findObject);
        final boolean packageWarehouse = npcImpl.equalsIgnoreCase("L1Dwarf") && resultType == 10;
        final boolean warehouseMove = asynchronousWarehouse || packageWarehouse;
        // The package inventory is an older non-Warehouse inventory path.
        // Keep its original one-entry policy until it has durable batch replay.
        if (packageWarehouse && size > 1) {
            SC_SystemMessage.send(pc, "패키지 창고는 아직 한 종류씩 처리합니다.");
            return;
        }
        if (warehouseMove && size > 0
                && (pc.isItemTransferBusy() || pc.isWarehouseBatchActive())) {
            SC_SystemMessage.send(pc, "이전 창고 아이템 처리가 끝난 뒤 다시 시도해 주세요.");
            return;
        }
        if (asynchronousWarehouse && size > 1) {
            if (size > 100 || hasFirst == 0 || params == null
                    || params.length < (size - 1) * 8) {
                SC_SystemMessage.send(pc, "창고 선택 항목이 너무 많거나 올바르지 않습니다.");
                return;
            }
            if (resultType == 2 || resultType == 4 || resultType == 8) {
                if (resultType == 8 && (pc.getLevel() < 5 || !pc.isElf())) return;
                // Decode and validate the whole selection before any DB job is admitted.
                for (int i = 0; i < size; i++) {
                    final long count = param2D(packed0, params, i);
                    final int id = param1(packed0, params, i);
                    if (id <= 0 || count <= 0 || count > L1Inventory.MAX_AMOUNT) {
                        SC_SystemMessage.send(pc, "창고 선택 항목 또는 수량이 올바르지 않습니다.");
                        return;
                    }
                    for (int j = 0; j < i; j++) {
                        if (param1(packed0, params, j) == id) {
                            SC_SystemMessage.send(pc, "중복으로 선택한 창고 아이템이 있습니다.");
                            return;
                        }
                    }
                }
                switch (resultType) {
                case 2: processPrivateWarehouseStore(pc, size, packed0, params, null); break;
                case 4: processClanWarehouseStore(pc, size, packed0, params, null); break;
                case 8: processElfWarehouseStore(pc, size, packed0, params, null); break;
                default: break;
                }
                return;
            }
            if (resultType == 3 || resultType == 5 || resultType == 9) {
                for (int i = 0; i < size; i++) {
                    final long raw = param2D(packed0, params, i);
                    final int id = param1(packed0, params, i);
                    if (id <= 0 || raw <= 0 || raw > L1Inventory.MAX_AMOUNT) {
                        SC_SystemMessage.send(pc, "창고 선택 항목 또는 수량이 올바르지 않습니다.");
                        return;
                    }
                    for (int j = 0; j < i; j++) {
                        if (id == param1(packed0, params, j)) {
                            SC_SystemMessage.send(pc, "중복으로 선택한 창고 아이템이 있습니다.");
                            return;
                        }
                    }
                }
            }
            if ((resultType == 3 || resultType == 5 || resultType == 9)
                    && !requiresSingleWithdrawal(resultType, pc, size, packed0, params)) {
                // General multi-selection uses one durable CacheD receipt instead
                // of one 50ms CIO-wheel tick and SQL COMMIT per selected item.
                switch (resultType) {
                case 3: processPrivateWarehouseRetrieve(pc, size, packed0, params, null); break;
                case 5: if (pc.getLevel() >= 5)
                            processClanWarehouseRetrieve(pc, size, packed0, params, null); break;
                case 9: if (pc.getLevel() >= 5 && pc.isElf())
                            processElfWarehouseRetrieve(pc, size, packed0, params, null); break;
                default: break;
                }
                return;
            }
            final WarehouseBatch batch = new WarehouseBatch(client, pc,
                    npcObjectId, resultType, size, packed0, params);
            if (!batch.isValid()) {
                SC_SystemMessage.send(pc, "창고 선택 항목 또는 수량이 올바르지 않습니다.");
                return;
            }
            if (!pc.tryBeginWarehouseBatch(batch)) {
                SC_SystemMessage.send(pc, "이전 창고 처리가 끝난 뒤 다시 시도해 주세요.");
                return;
            }
            batch.advance();
            return;
        }

		// 아이템 교환 로직 (1 ~ 20)
		if (processChangeItems(pc, size, packed0, params)) {
			return;
		}

		if (resultType == 0 && size != 0 && npcImpl.equalsIgnoreCase("L1Merchant")) {
			if ((pc.getLevel() >= Config.PC_SHOP && pc.getClanid() <= 0) && !pc.isGm()) {
				pc.sendPackets("\\fY레벨 " + Config.PC_SHOP + " 이상 무혈 캐릭터는 상점을 이용할 수 없습니다");
				pc.sendPackets("\\fY최소한 혈가입은 서버에 원활한 게임을 위한 시스템입니다");
				return;
			}

			if (MJDogFightSettings.CASTER_NPC_ID == npcId) {
				do_dogfight_buy_ticket(pc, packed0, params, size);
				return;
			}

			L1Shop shop = ShopTable.getInstance().get(npcId);
			if (shop == null) {
				System.out.println(String.format("[샵]%d 엔피씨를 찾을 수 없습니다.", npcId));
				return;
			}

			L1ShopBuyOrderList orderList = shop.newBuyOrderList();
			final ArrayList<Runnable> committedShopLogs = new ArrayList<>();
			final String auditNpcName = npcName, auditBuyerName = pc.getName();
			if (npcId == 7200002) {
				SC_ActionUI.send(pc, SC_ActionUI.TAM, pc.getAccount().getTamPoint());
			}
			if (npcId == 70035 || npcId == 70041 || npcId == 70042) {
				if (BugRaceController.getInstance().getBugState() != 0) {
					return;
				}
			}
			if (pc.서버다운중 == true) {
				if (npcId == 70035 || npcId == 70041 || npcId == 70042) {
					SC_SystemMessage.send(pc, "서버다운 진행중에는 구매가 불가능합니다.");
					return;
				}
			}

			if (shop.getSellingItems().size() < size) {
				if (npcId == 70035 || npcId == 70041 || npcId == 70042) {
					pc.sendPackets("이미 경기가 시작되었습니다.");
					return;
				}
				System.out.println("■[버그차단]■: " + pc.getName() + "님이 상점이 판매하는 아이템 수(" + shop.getSellingItems().size() + ")보다 더 많이 사려고 함.(" + size + ")개");
				client.kick();
				client.close();
				return;
			}

			if (hasParams(size, hasFirst)) {
				for (int i = 0; i < size; i++) {
					int itemNumber = param1(packed0, params, i);
					long itemcount = param2D(packed0, params, i);
					if (itemcount <= 0) {
						return;
					}
					if ((npcId >= 6100000 && npcId <= 6100035) || (npcId >= 999500 && npcId <= 999510)) {
						if (itemcount > 1) {
							SC_SystemMessage.send(pc, "1개씩 구입할 수 있습니다.");
							return;
						}
					}

					if (itemcount >= 10000) {
						return;
					}
					try {
						orderList.add(itemNumber, (int) itemcount, pc);
						if (shop.getSellingItems().size() > itemNumber) {
							L1ShopItem auditItem = shop.getSellingItems().get(itemNumber);
							final String auditItemName = auditItem.getItem().getName();
							final int auditCount = (int) itemcount;
							final long auditPrice = (long) auditItem.getPrice() * itemcount;
							committedShopLogs.add(() -> LoggerInstance.getInstance().addShop(
									auditItemName, auditCount, auditPrice, auditNpcName, auditBuyerName));
							if (orderList.BugOk() != 0) {
								for (L1PcInstance player : L1World.getInstance().getAllPlayers()) {
									if (player.isGm() || pc == player) {
										SC_SystemMessage.send(player, pc.getName() + "님 상점 최대구매 수량 (" + itemcount + ")를 초과 하였습니다.");
									}
								}
							}
						} else {
							System.out.println(String.format("[상점 구매 예외 정보] %s 인벤토리 검사해보세요.", pc.getName()));
						}
					} catch (Exception e) {
						System.out.println(String.format("[상점 구매 예외 정보] 캐릭터명 : %s, itemNumber : %d, itemcount : %d", pc.getName(), itemNumber, itemcount));
						e.printStackTrace();
						return;
					}
				}
			}
			
			int bugok = orderList.BugOk();
			if (bugok == 0) {
				for (L1ShopBuyOrder sbo : orderList.getList()) {
					if (sbo.getItem().getItemId() == 41295) {
						committedShopLogs.add(() -> pc.setFishingShopBuyTime_1(System.currentTimeMillis()));
					}
				}
				shop.sellItems(pc, orderList, () -> committedShopLogs.forEach(Runnable::run));
				pc.saveInventory();
			}

		} else if (resultType == 1 && size != 0 && npcImpl.equalsIgnoreCase("L1Merchant")) {
			if (Config.STANDBY_SERVER) {
				pc.sendPackets("오픈대기 상태에선 해당 행동이 불가능 합니다.");
				return;
			}
			if ((pc.getLevel() >= Config.PC_SHOP && pc.getClanid() <= 0) && !pc.isGm()) {
				pc.sendPackets("\\fY레벨 " + Config.PC_SHOP + " 이상 무혈 캐릭터는 상점을 이용할 수 없습니다");
				pc.sendPackets("\\fY최소한 혈가입은 서버에 원활한 게임을 위한 시스템입니다");
				return;
			}

			if (MJDogFightSettings.CASTER_NPC_ID == npcId) {
				do_dogfight_sell_ticket(pc, packed0, params, size, npcObjectId);
				return;
			}

			L1Shop shop = ShopTable.getInstance().get(npcId);
			L1ShopSellOrderList orderList = shop.newSellOrderList(pc);

			if (hasParams(size, hasFirst)) {
				for (int i = 0; i < size; i++) {
					int itemNumber = param1(packed0, params, i);
					long itemcount = param2D(packed0, params, i);
					if (itemcount <= 0) {
						return;
					}
					if (npcId >= 6100000 && npcId <= 6100035 && !pc.getInventory().getItem(itemNumber).isPackage()) {
						SC_SystemMessage.send(pc, "패킷상점에서 구매하지 않은 아이템이 포함되어 있습니다.");
						return;
					}
					orderList.add(itemNumber, (int) itemcount, pc);
				}
			}
			int bugok = orderList.BugOk();
			if (bugok == 0) {
				shop.buyItems(orderList);
				pc.saveInventory();
			}

		} else if (resultType == 2 && size != 0 && npcImpl.equalsIgnoreCase("L1Dwarf")) {
			processPrivateWarehouseStore(pc, size, packed0, params, null);

		} else if (resultType == 3 && size != 0 && npcImpl.equalsIgnoreCase("L1Dwarf")) {
			processPrivateWarehouseRetrieve(pc, size, packed0, params, null);

		} else if (resultType == 4 && size != 0 && npcImpl.equalsIgnoreCase("L1Dwarf")) {
			processClanWarehouseStore(pc, size, packed0, params, null);

		} else if (resultType == 5 && size != 0 && npcImpl.equalsIgnoreCase("L1Dwarf") && level >= 5) {
			processClanWarehouseRetrieve(pc, size, packed0, params, null);
			
		} else if (resultType == 8 && size != 0 && npcImpl.equalsIgnoreCase("L1Dwarf") && level >= 5 && pc.isElf()) {
			processElfWarehouseStore(pc, size, packed0, params, null);

		} else if (resultType == 9 && size != 0 && npcImpl.equalsIgnoreCase("L1Dwarf") && level >= 5 && pc.isElf()) {
			processElfWarehouseRetrieve(pc, size, packed0, params, null);

		} else if (resultType == 10 && size != 0 && npcImpl.equalsIgnoreCase("L1Dwarf")) {
			processPackageWarehouseRetrieve(pc, size, packed0, params);

		} else if (resultType == 0 && size != 0 && isPrivateNpcShop) {
			processPrivateNpcShopBuy(pc, size, npcId, npcName, client, packed0, params);

		} else if (resultType == 0 && size != 0 && isPrivateShop) {
			processPrivateShopBuy(pc, size, findObject, packed0, params);

		} else if (resultType == 1 && size != 0 && isPrivateNpcShop) {
			processPrivateNpcShopSell(pc, size, npcId, packed0, params);

		} else if (resultType == 20 && size != 0 && pc == findObject) {
			processSupplementaryService(pc, size, packed0, params, null);

		} else if (resultType == 1 && size != 0 && isPrivateShop) {
			processPrivateShopSell(pc, size, findObject, packed0, params);
		}
	}

    // Charged non-stackable consumables use a distinct charge_count merge
    // contract. Preserve their existing serialized path rather than changing
    // their semantics in an ordinary stack-count batch.
    private static boolean requiresSingleWithdrawal(int kind, L1PcInstance pc,
            int size, long packed0, byte[] params) {
        final l1j.server.server.model.Warehouse.Warehouse source;
        if (kind == 3) source = WarehouseManager.getInstance()
                .getPrivateWarehouse(pc.getAccountName());
        else if (kind == 5) {
            final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            source = clan == null ? null : WarehouseManager.getInstance()
                    .getClanWarehouse(clan.getClanName());
        } else if (kind == 9) source = WarehouseManager.getInstance()
                .getElfWarehouse(pc.getAccountName());
        else return true;
        if (source == null) return false;
        for (int i = 0; i < size; i++) {
            L1ItemInstance item = source.getItem(param1(packed0, params, i));
            if (item == null || item.isStackable()) continue;
            switch (item.getItemId()) {
            case 40006: case 40007: case 40008: case 140006:
            case 140008: case 41401: case 810006: case 810007:
                return true;
            default: break;
            }
        }
        return false;
    }

    /** A selected list is a sequence of individually atomic warehouse receipts.
     * Each item is revalidated against its current inventory before submission;
     * failed/invalid items stop the remainder rather than replaying stale IDs.
     * Successful earlier items stay committed if a later item fails.
     */
    private static final class WarehouseBatch {
        private final CIOClient session;
        private final L1PcInstance pc;
        private final int stamp, npcId, kind;
        private final int[] ids, counts;
        private final Runnable committed = this::onCommitted;
        private final Runnable abandoned = this::finish;
        private int position;
        private boolean valid = true;

        WarehouseBatch(CIOClient session, L1PcInstance pc,
                int npcId, int kind, int size, long packed, byte[] rest) {
            this.session = session;
            this.pc = pc;
            this.npcId = npcId;
            this.kind = kind;
            stamp = pc.getSessionStamp();
            ids = new int[size];
            counts = new int[size];
            for (int i = 0; i < size; i++) {
                final long rawCount = param2D(packed, rest, i);
                final int id = param1(packed, rest, i);
                if (id <= 0 || rawCount <= 0 || rawCount > 2_000_000_000L) {
                    valid = false;
                    break;
                }
                for (int j = 0; j < i; j++) {
                    if (ids[j] == id) { valid = false; break; }
                }
                if (!valid) break;
                ids[i] = id;
                counts[i] = (int) rawCount;
            }
        }

        boolean isValid() { return valid; }

        void finish() { pc.endWarehouseBatch(this); }

        private void onCommitted() {
            if (!pc.isWarehouseBatchOwner(this)) return;
            final boolean complete = ++position == ids.length;
            // Commit callback still owns CacheD's lanes. One CIO-wheel tick
            // guarantees the previous item's terminal apply has returned,
            // including after the final item (before opening another batch).
            try {
                if (!CIOThreadCallback.getInstance().scheduleWarehouseBatchNext(
                        session, pc, stamp, complete ? this::finish : this::advance, abandoned)) {
                    finish();
                }
            } catch (RuntimeException schedulingFailure) {
                finish();
                java.util.logging.Logger.getLogger(CS_Result.class.getName()).log(
                        java.util.logging.Level.WARNING, "Warehouse batch scheduling failed", schedulingFailure);
            }
        }

        void advance() {
            if (!pc.isWarehouseBatchOwner(this)) return;
            if (session.getActiveChar() != pc || pc.getNetConnection() != session
                    || !session.isConnected() || pc.getSessionStamp() != stamp
                    || pc.isItemTransferBusy() || Config.STANDBY_SERVER) {
                finish();
                return;
            }
            if (kind == 20) {
                if (L1World.getInstance().findObject(npcId) != pc) {
                    finish();
                    return;
                }
            } else {
                final L1Object target = L1World.getInstance().findObject(npcId);
                if (!(target instanceof L1NpcInstance)
                        || !((L1NpcInstance) target).getNpcTemplate().getImpl().equalsIgnoreCase("L1Dwarf")
                        || Math.abs(pc.getX() - target.getX()) > NpcActionRange.get()
                        || Math.abs(pc.getY() - target.getY()) > NpcActionRange.get()) {
                    finish();
                    return;
                }
                if ((kind == 5 || kind == 8 || kind == 9) && pc.getLevel() < 5
                        || (kind == 8 || kind == 9) && !pc.isElf()) {
                    finish();
                    return;
                }
            }
            final long item = ((long) counts[position] << 32) | (ids[position] & 0xffffffffL);
            try {
                switch (kind) {
                case 2: processPrivateWarehouseStore(pc, 1, item, null, committed); break;
                case 3: processPrivateWarehouseRetrieve(pc, 1, item, null, committed); break;
                case 4: processClanWarehouseStore(pc, 1, item, null, committed); break;
                case 5: processClanWarehouseRetrieve(pc, 1, item, null, committed); break;
                case 8: processElfWarehouseStore(pc, 1, item, null, committed); break;
                case 9: processElfWarehouseRetrieve(pc, 1, item, null, committed); break;
                case 20: processSupplementaryService(pc, 1, item, null, committed); break;
                default: finish(); return;
                }
            } catch (RuntimeException failure) {
                finish();
                java.util.logging.Logger.getLogger(CS_Result.class.getName()).log(
                        java.util.logging.Level.WARNING, "Warehouse batch item rejected", failure);
                return;
            }
            if (!pc.isItemTransferBusy()) {
                // Validation, capacity or CacheD admission failed. Earlier
                // receipts remain committed; never retry rejected items.
                if (position > 0) SC_SystemMessage.send(pc,
                        "창고 일부 아이템의 이동이 중단됐습니다. 먼저 완료된 아이템은 보존됩니다.");
                finish();
            }
        }
    }

	// 1~20번 아이템 교환 로직 통합 핸들러
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

	private static void processPrivateWarehouseStore(L1PcInstance pc, int size, long packed0, byte[] params,
            Runnable onCommitted) {
        if (size <= 0) return;
        final int[] batchIds = size > 1 ? new int[size] : null;
        final int[] batchCounts = size > 1 ? new int[size] : null;
        for (int i = 0; i < size; i++) {
			boolean tradable = true;
			int objectId = param1(packed0, params, i);
			int count = (int) param2D(packed0, params, i);
			L1ItemInstance item = pc.getInventory().getItem(objectId);
			if (item == null)
				return;
			if (Config.STANDBY_SERVER) {
				pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
				SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "오픈대기 상태에서는 창고 이용이 불가능합니다.");
				return;
			}
			int itemId = item.getItem().getItemId();
			if (!pc.isGm() && NoShopAndWare.getInstance().isNoShopAndWare(itemId)) {
				SC_SystemMessage.send(pc, "해당 아이템은 창고 이용을 할 수 없습니다.");
				return;
			}

			long nowtime = System.currentTimeMillis();
			if (item.getItemdelay3() >= nowtime) {
				break;
			}

			if (objectId != item.getId()) {
				SC_Disconnect.send(pc);
				return;
			}

			if (!item.isStackable() && count != 1) {
				SC_Disconnect.send(pc);
				return;
			}

			if (count <= 0 || item.getCount() <= 0) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count > item.getCount()) {
				count = item.getCount();
			}

			if (item.getCount() > 2000000000 || count > 2000000000) {
				return;
			}

			if (!item.getItem().isTradable()) {
				tradable = false;
				SC_ServerMessage.send(pc, 210, item.getItem().getName());
			}
			if (!MJCompanionInstanceCache.is_companion_oblivion(item.getId())) {
				tradable = false;
				SC_ServerMessage.send(pc, 210, item.getItem().getName());
				break;
			}
			for (Object petObject : pc.getPetList().values()) {
				if (petObject instanceof L1PetInstance) {
					L1PetInstance pet = (L1PetInstance) petObject;
					if (item.getId() == pet.getItemObjId()) {
						tradable = false;
						SC_ServerMessage.send(pc, 210, item.getItem().getName());
						break;
					}
				}
			}
			for (Object dollObject : pc.getDollList().values()) {
				if (dollObject instanceof L1DollInstance) {
					L1DollInstance doll = (L1DollInstance) dollObject;
					if (item.getId() == doll.getItemObjId()) {
						tradable = false;
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "현재 소환 된 인형은 맡길수가 없습니다.");
						break;
					}
				}
			}

			PrivateWarehouse warehouse = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
			if (warehouse == null)
				return;

			if (warehouse.checkAddItemToWarehouse(item, count) == L1Inventory.SIZE_OVER) {
				SC_ServerMessage.send(pc, 75);
				break;
			}

			if (item.getBless() >= 128) {
				SC_ServerMessage.send(pc, 210, item.getItem().getName());
				break;
			}

            if (!tradable) return;
            if (batchIds != null) {
                batchIds[i] = objectId;
                batchCounts[i] = count;
            } else if (pc.getInventory().tradeItem(objectId, count, warehouse, onCommitted) != null) {
                // Light refresh and audit are emitted after durable COMMIT.
            }
        }
        if (batchIds != null) {
            final PrivateWarehouse warehouse = WarehouseManager.getInstance()
                    .getPrivateWarehouse(pc.getAccountName());
            if (warehouse == null || !pc.getInventory().tradeWarehouseDepositBatch(
                    batchIds, batchCounts, warehouse, null))
                SC_SystemMessage.send(pc, "창고 아이템을 한 번에 맡길 수 없습니다. 아이템과 창고 공간을 확인해 주세요.");
        }
    }

    private static void processPrivateWarehouseRetrieve(L1PcInstance pc, int size, long packed0, byte[] params,
            Runnable onCommitted) {
		if (size <= 0) return;
        if (size > 1) {
            final PrivateWarehouse warehouse = WarehouseManager.getInstance()
                    .getPrivateWarehouse(pc.getAccountName());
            if (warehouse == null) return;
            if (Config.STANDBY_SERVER) {
                pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
                return;
            }
            final int[] ids = new int[size], counts = new int[size];
            for (int i = 0; i < size; i++) {
                ids[i] = param1(packed0, params, i);
                int requested = (int) param2D(packed0, params, i);
                final L1ItemInstance item = warehouse.getItem(ids[i]);
                if (item == null) return;
                if (ids[i] != item.getId() || requested <= 0
                        || (!item.isStackable() && requested != 1)) {
                    SC_Disconnect.send(pc);
                    return;
                }
                counts[i] = Math.min(requested, item.getCount());
            }
            // The single-item path checks for 31 adena but debits 30.
            // Preserve the last item's one-adena safety margin.
            if (pc.getInventory().getItemCountDirect(L1ItemId.ADENA) < 30L * size + 1L) {
                SC_SystemMessage.send(pc, "아데나가 부족합니다.");
                return;
            }
            if (!pc.getInventory().tradeWarehouseWithdrawalBatch(
                    ids, counts, warehouse, L1ItemId.ADENA, 30, onCommitted))
                SC_SystemMessage.send(pc, "창고 아이템을 한 번에 찾을 수 없습니다. 무게와 소지 칸을 확인해 주세요.");
            return;
        }
		for (int i = 0; i < size; i++) {
			int objectId = param1(packed0, params, i);
			int count = (int) param2D(packed0, params, i);

			PrivateWarehouse warehouse = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
			if (warehouse == null)
				return;

			if (Config.STANDBY_SERVER) {
				pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
				SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "오픈대기 상태에서는 창고 이용이 불가능합니다.");
				return;
			}

			L1ItemInstance item = warehouse.getItem(objectId);
			if (item == null) {
				return;
			}

			if (!pc.getInventory().checkItem(L1ItemId.ADENA, 31)) {
				SC_SystemMessage.send(pc, "아데나가 부족합니다..");
				return;
			}

			if (objectId != item.getId()) {
				SC_Disconnect.send(pc);
				return;
			}
			if (!item.isStackable() && count != 1) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count <= 0 || item.getCount() <= 0) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count > item.getCount()) {
				count = item.getCount();
			}

			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
                if (warehouse.tradeItem(item, count, pc.getInventory(), L1ItemId.ADENA, 30, onCommitted) != null) {
                    // Audit emitted by warehouse completion after durable COMMIT.
                } else {
					SC_ServerMessage.send(pc, 189);
					break;
				}
			} else {
				SC_ServerMessage.send(pc, 270);
				break;
			}
		}
	}

	private static void processClanWarehouseStore(L1PcInstance pc, int size, long packed0, byte[] params,
            Runnable onCommitted) {
		if (size <= 0) return;
		L1Clan clan = null;
		if (pc.getClanid() != 0) {
            final int[] batchIds = size > 1 ? new int[size] : null;
            final int[] batchCounts = size > 1 ? new int[size] : null;
            final L1ItemInstance[] batchItems = size > 1 ? new L1ItemInstance[size] : null;
			for (int i = 0; i < size; i++) {
				boolean tradable = true;
				int objectId = param1(packed0, params, i);
				int count = (int) param2D(packed0, params, i);

				clan = L1World.getInstance().getClan(pc.getClanid());
				L1ItemInstance item = pc.getInventory().getItem(objectId);
				if (item == null) {
					return;
				}
				if (Config.STANDBY_SERVER) {
					pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "오픈대기 상태에서는 창고 이용이 불가능합니다.");
					return;
				}
				int itemId = item.getItem().getItemId();
				if (!pc.isGm() && NoShopAndWare.getInstance().isNoShopAndWare(itemId)) {
					SC_SystemMessage.send(pc, "해당 아이템은 창고 이용을 할 수 없습니다.");
					return;
				}
				long nowtime = System.currentTimeMillis();
				if (item.getItemdelay3() >= nowtime) {
					break;
				}

				if (objectId != item.getId()) {
					SC_Disconnect.send(pc);
					return;
				}
				if (!item.isStackable() && count != 1) {
					SC_Disconnect.send(pc);
					return;
				}
				if (count <= 0 || item.getCount() <= 0 || item.getCount() > 2000000000 || count > 2000000000) {
					return;
				}
				if (count > item.getCount()) {
					count = item.getCount();
				}

				if (item.getBless() >= 128) {
					SC_ServerMessage.send(pc, 210, item.getItem().getName());
					return;
				}
				if (!MJCompanionInstanceCache.is_companion_oblivion(item.getId())) {
					tradable = false;
					SC_ServerMessage.send(pc, 210, item.getItem().getName());
					return;
				}

				if (clan != null) {
					if (!item.getItem().isTradable()) {
						tradable = false;
						SC_ServerMessage.send(pc, 210, item.getItem().getName());
					}
					for (Object petObject : pc.getPetList().values()) {
						if (petObject instanceof L1PetInstance) {
							L1PetInstance pet = (L1PetInstance) petObject;
							if (item.getId() == pet.getItemObjId()) {
								tradable = false;
								SC_ServerMessage.send(pc, 210, item.getItem().getName());
								break;
							}
						}
					}

					for (Object dollObject : pc.getDollList().values()) {
						if (dollObject instanceof L1DollInstance) {
							L1DollInstance doll = (L1DollInstance) dollObject;
							if (item.getId() == doll.getItemObjId()) {
								tradable = false;
								SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "현재 소환 된 인형은 맡길수가 없습니다.");
								break;
							}
						}
					}

					ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
                    if (clanWarehouse == null) return;
					if (clanWarehouse.checkAddItemToWarehouse(item, count) == L1Inventory.SIZE_OVER) {
						SC_ServerMessage.send(pc, 75);
						return;
					}
                    if (!tradable) return;
                    if (batchIds != null) {
                        batchIds[i] = objectId;
                        batchCounts[i] = count;
                        batchItems[i] = item;
                    } else if (pc.getInventory().tradeItem(objectId, count, clanWarehouse, onCommitted) != null) {
                        history(pc, item, count, 1);
                    }
				}
			}
            if (batchIds != null) {
                if (clan == null) return;
                final ClanWarehouse warehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
                final Runnable audit = prepareClanDepositBatchHistory(pc, batchItems, batchCounts);
                if (warehouse == null || !pc.getInventory().tradeWarehouseDepositBatch(
                        batchIds, batchCounts, warehouse, audit))
                    SC_SystemMessage.send(pc, "혈맹 창고 아이템을 한 번에 맡길 수 없습니다. 아이템과 창고 공간을 확인해 주세요.");
            }
		} else {
			SC_ServerMessage.send(pc, 208);
		}
	}

	private static void processClanWarehouseRetrieve(L1PcInstance pc, int size, long packed0, byte[] params,
            Runnable onCommitted) {
		if (size <= 0) return;
        if (size > 1) {
            if (pc.getInventory().checkEnchantItem(40308, 0, 71)) {
                SC_SystemMessage.send(pc, "아데나가 부족합니다.");
                return;
            }
            if (pc.hasSkillEffect(L1SkillId.SetBuff)) return;
            final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
            if (clan == null) return;
            clan.deleteClanRetrieveUser(pc.getId());
            final ClanWarehouse warehouse = WarehouseManager.getInstance()
                    .getClanWarehouse(clan.getClanName());
            if (warehouse == null) return;
            if (Config.STANDBY_SERVER) {
                pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
                return;
            }
            final int[] ids = new int[size], counts = new int[size];
            final L1ItemInstance[] items = new L1ItemInstance[size];
            for (int i = 0; i < size; i++) {
                ids[i] = param1(packed0, params, i);
                int requested = (int) param2D(packed0, params, i);
                final L1ItemInstance item = warehouse.getItem(ids[i]);
                if (item == null) return;
                if (ids[i] != item.getId() || requested <= 0
                        || (!item.isStackable() && requested != 1)) {
                    SC_Disconnect.send(pc);
                    return;
                }
                items[i] = item;
                counts[i] = Math.min(requested, item.getCount());
            }
            final Runnable audit = prepareClanWarehouseBatchHistory(pc, items, counts, false);
            if (!pc.getInventory().tradeWarehouseWithdrawalBatch(
                    ids, counts, warehouse, L1ItemId.ADENA, 30, audit))
                SC_SystemMessage.send(pc, "혈맹 창고 아이템을 한 번에 찾을 수 없습니다. 무게와 소지 칸을 확인해 주세요.");
            return;
        }
		if (pc.getInventory().checkEnchantItem(40308, 0, 71)) {
			SC_SystemMessage.send(pc, "아데나가 부족합니다.");
			return;
		}

		if (pc.hasSkillEffect(L1SkillId.SetBuff)) {
			return;
		}

		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan != null) {
			clan.deleteClanRetrieveUser(pc.getId());
		}
        if (clan == null) return;
        ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
        if (clanWarehouse == null) return;

        if (clan != null) {
			for (int i = 0; i < size; i++) {
				int objectId = param1(packed0, params, i);
				int count = (int) param2D(packed0, params, i);
				L1ItemInstance item = clanWarehouse.getItem(objectId);

				if (item == null) {
					return;
				}
				if (Config.STANDBY_SERVER) {
					pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "오픈대기 상태에서는 창고 이용이 불가능합니다.");
					return;
				}
				if (objectId != item.getId()) {
					SC_Disconnect.send(pc);
					return;
				}

				if (!item.isStackable() && count != 1) {
					SC_Disconnect.send(pc);
					return;
				}
				if (count <= 0 || item.getCount() <= 0 || item.getCount() > 2000000000) {
					SC_Disconnect.send(pc);
					return;
				}
				if (count >= item.getCount()) {
					count = item.getCount();
				}

				if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
                    if (clanWarehouse.tradeItem(item, count, pc.getInventory(), L1ItemId.ADENA, 30, onCommitted) != null) {
                        history(pc, item, count, 2);
                        // Audit emitted by warehouse completion after durable COMMIT.
                    } else {
						SC_ServerMessage.send(pc, 189);
						break;
					}
				} else {
					SC_ServerMessage.send(pc, 270);
					break;
				}
			}
		}
	}

	private static void processElfWarehouseStore(L1PcInstance pc, int size, long packed0, byte[] params,
            Runnable onCommitted) {
		if (size <= 0) return;
        final int[] batchIds = size > 1 ? new int[size] : null;
        final int[] batchCounts = size > 1 ? new int[size] : null;
		for (int i = 0; i < size; i++) {
			boolean tradable = true;
			int objectId = param1(packed0, params, i);
			int count = (int) param2D(packed0, params, i);

			L1ItemInstance item = pc.getInventory().getItem(objectId);
			if (item == null) {
				return;
			}
			if (Config.STANDBY_SERVER) {
				pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
				SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "오픈대기 상태에서는 창고 이용이 불가능합니다.");
				return;
			}
			int itemId = item.getItem().getItemId();
			if (!pc.isGm() && NoShopAndWare.getInstance().isNoShopAndWare(itemId)) {
				SC_SystemMessage.send(pc, "해당 아이템은 창고 이용을 할 수 없습니다.");
				return;
			}
			if (objectId != item.getId()) {
				SC_Disconnect.send(pc);
				return;
			}
			if (!item.isStackable() && count != 1) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count <= 0 || item.getCount() <= 0) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count > item.getCount()) {
				count = item.getCount();
			}
			if (item.getCount() > 2000000000 || count > 2000000000) {
				return;
			}

			if (!item.getItem().isTradable()) {
				tradable = false;
				SC_ServerMessage.send(pc, 210, item.getItem().getName());
			}

			if (!MJCompanionInstanceCache.is_companion_oblivion(item.getId())) {
				tradable = false;
				SC_ServerMessage.send(pc, 210, item.getItem().getName());
				return;
			}

			Object[] petlist = pc.getPetList().values().toArray();
			for (Object petObject : petlist) {
				if (petObject instanceof L1PetInstance) {
					L1PetInstance pet = (L1PetInstance) petObject;
					if (item.getId() == pet.getItemObjId()) {
						tradable = false;
						SC_ServerMessage.send(pc, 210, item.getItem().getName());
						break;
					}
				}
			}
			ElfWarehouse elfwarehouse = WarehouseManager.getInstance().getElfWarehouse(pc.getAccountName());
            if (elfwarehouse == null) return;
			if (elfwarehouse.checkAddItemToWarehouse(item, count) == L1Inventory.SIZE_OVER) {
				SC_ServerMessage.send(pc, 75);
				return;
			}
            if (!tradable) return;
            if (batchIds != null) {
                batchIds[i] = objectId;
                batchCounts[i] = count;
            } else if (pc.getInventory().tradeItem(objectId, count, elfwarehouse, onCommitted) != null) {
                // Light refresh and audit are emitted after durable COMMIT.
            }
		}
        if (batchIds != null) {
            final ElfWarehouse warehouse = WarehouseManager.getInstance()
                    .getElfWarehouse(pc.getAccountName());
            if (warehouse == null || !pc.getInventory().tradeWarehouseDepositBatch(
                    batchIds, batchCounts, warehouse, null))
                SC_SystemMessage.send(pc, "요정 창고 아이템을 한 번에 맡길 수 없습니다. 아이템과 창고 공간을 확인해 주세요.");
        }
	}

	private static void processElfWarehouseRetrieve(L1PcInstance pc, int size, long packed0, byte[] params,
            Runnable onCommitted) {
		if (size <= 0) return;
        if (size > 1) {
            final ElfWarehouse warehouse = WarehouseManager.getInstance()
                    .getElfWarehouse(pc.getAccountName());
            if (warehouse == null) return;
            if (Config.STANDBY_SERVER) {
                pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
                return;
            }
            final int[] ids = new int[size], counts = new int[size];
            for (int i = 0; i < size; i++) {
                ids[i] = param1(packed0, params, i);
                int requested = (int) param2D(packed0, params, i);
                final L1ItemInstance item = warehouse.getItem(ids[i]);
                if (item == null) return;
                if (ids[i] != item.getId() || requested <= 0
                        || (!item.isStackable() && requested != 1)) {
                    SC_Disconnect.send(pc);
                    return;
                }
                counts[i] = Math.min(requested, item.getCount());
            }
            if (pc.getInventory().getItemCountDirect(40494) < (long) size * 2L) {
                SC_SystemMessage.send(pc, "창고 출금 수수료 아이템이 부족합니다.");
                return;
            }
            if (!pc.getInventory().tradeWarehouseWithdrawalBatch(
                    ids, counts, warehouse, 40494, 2, onCommitted))
                SC_SystemMessage.send(pc, "요정 창고 아이템을 한 번에 찾을 수 없습니다. 무게와 소지 칸을 확인해 주세요.");
            return;
        }
		for (int i = 0; i < size; i++) {
			int objectId = param1(packed0, params, i);
			int count = (int) param2D(packed0, params, i);

			ElfWarehouse elfwarehouse = WarehouseManager.getInstance().getElfWarehouse(pc.getAccountName());
			L1ItemInstance item = elfwarehouse.getItem(objectId);

			if (item == null) {
				return;
			}
			if (Config.STANDBY_SERVER) {
				pc.sendPackets("오픈대기 상태에서는 창고 이용이 불가능합니다.");
				SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "오픈대기 상태에서는 창고 이용이 불가능합니다.");
				return;
			}
            if (!pc.getInventory().checkItem(40494, 2)) {
                SC_SystemMessage.send(pc, "창고 출금 수수료 아이템이 부족합니다.");
                return;
            }
			if (objectId != item.getId()) {
				SC_Disconnect.send(pc);
				return;
			}
			if (!item.isStackable() && count != 1) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count <= 0 || item.getCount() <= 0) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count > item.getCount()) {
				count = item.getCount();
			}

            if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
                if (elfwarehouse.tradeItem(item, count, pc.getInventory(), 40494, 2, onCommitted) != null) {
                    // Audit emitted by warehouse completion after durable COMMIT.
                } else {
					SC_ServerMessage.send(pc, 337, "$767");
					break;
				}
			} else {
				SC_ServerMessage.send(pc, 270);
				break;
			}
		}
	}

	private static void processPackageWarehouseRetrieve(L1PcInstance pc, int size, long packed0, byte[] params) {
		if (size <= 0) return;
		for (int i = 0; i < size; i++) {
			int objectId = param1(packed0, params, i);
			int count = (int) param2D(packed0, params, i);
			L1ItemInstance item = pc.getDwarfForPackageInventory().getItem(objectId);

			if (item == null) {
				return;
			}
			if (objectId != item.getId()) {
				SC_Disconnect.send(pc);
				return;
			}
			if (!item.isStackable() && count != 1) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count <= 0 || item.getCount() <= 0) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count > item.getCount()) {
				count = item.getCount();
			}

			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
				pc.getDwarfForPackageInventory().tradeItem(item, count, pc.getInventory());
			} else {
				SC_ServerMessage.send(pc, 270);
				break;
			}
		}
	}

	private static void processPrivateNpcShopBuy(L1PcInstance pc, int size, int npcId, String npcName, CIOClient client, long packed0, byte[] params) {
		if (Config.STANDBY_SERVER) {
			pc.sendPackets("오픈대기 상태에선 해당 행동이 불가능 합니다.");
			return;
		}
		if ((pc.getLevel() >= Config.PC_SHOP && pc.getClanid() <= 0) && !pc.isGm()) {
			pc.sendPackets("\\fY레벨 " + Config.PC_SHOP + " 이상 무혈 캐릭터는 상점을 이용할 수 없습니다");
			pc.sendPackets("\\fY최소한 혈가입은 서버에 원활한 게임을 위한 시스템입니다");
			return;
		}

		L1Shop shop = NpcShopManager.getInstance().getShop(npcId);
		if (shop == null) return;

		L1ShopBuyOrderList orderList = shop.newBuyOrderList();
		if (size <= 0) return;
		final ArrayList<Runnable> committedShopLogs = new ArrayList<>();
		final String auditBuyerName = pc.getName();

		for (int i = 0; i < size; i++) {
			int itemNumber = param1(packed0, params, i);
			long itemcount = param2D(packed0, params, i);
			if (itemcount <= 0) {
				return;
			}
			if (size >= 2) {
				SC_SystemMessage.send(pc, "한번에 서로 다른아이템을 구입할 수 없습니다.");
				return;
			}
			if (pc.getMapId() == 800) {
				if (itemcount > 1000) {
					SC_SystemMessage.send(pc, "최대 구매수량 : 잡템류(1000) / 장비(1)");
					return;
				}
			}
			orderList.add(itemNumber, (int) itemcount, pc);
			if (shop.getSellingItems().size() <= itemNumber) {
				pc.sendPackets("현재 구매할 수 없는 상태입니다.");
				return;
			}
			L1ShopItem shopItem = shop.getSellingItems().get(itemNumber);
			final String auditItemName = String.format("+%d %s", shopItem.getEnchant(), shopItem.getItem().getName());
			final int auditCount = (int) itemcount;
			final long auditPrice = (long) shopItem.getPrice() * itemcount;
			committedShopLogs.add(() -> LoggerInstance.getInstance().addShop(
					auditItemName, auditCount, auditPrice, npcName, auditBuyerName));
		}
		int bugok = orderList.BugOk();
		if (bugok == 0) {
			shop.sellItems(pc, orderList, () -> committedShopLogs.forEach(Runnable::run));
			pc.saveInventory();
		}
	}

    // Widen before multiplying. Reject malformed prices before either inventory changes.
    private static int privateShopPrice(int unitPrice, int count) {
        final long price = (long) unitPrice * count;
        return unitPrice > 0 && count > 0 && price <= 2000000000L ? (int) price : -1;
    }

    private static boolean privateShopParticipantsReady(L1PcInstance first, L1PcInstance second) {
        return first != second && !first.isItemTransferBusy() && !second.isItemTransferBusy()
                && !first.isTradeResolving() && !second.isTradeResolving()
                && first.getTradeID() == 0 && second.getTradeID() == 0;
    }

    private static final class PrivateShopLine {
        final MJDShopItem listing;
        final MJDShopItem snapshot;
        final L1ItemInstance item;
        int count;
        PrivateShopLine(MJDShopItem listing, L1ItemInstance item, int count) {
            this.listing = listing;
            this.snapshot = MJDShopStorage.copyListing(listing);
            this.item = item;
            this.count = count;
        }
    }

    private static boolean canUsePrivateShop(L1PcInstance pc) {
        if (Config.STANDBY_SERVER) {
            pc.sendPackets("오픈대기 상태에선 해당 행동이 불가능 합니다.");
            return false;
        }
        if (pc.getLevel() >= Config.PC_SHOP && pc.getClanid() <= 0 && !pc.isGm()) {
            pc.sendPackets("개인상점 이용을 위해 혈맹에 가입해 주세요.");
            return false;
        }
        return true;
    }

    private static void processPrivateShopBuy(L1PcInstance pc, int size,
            L1Object findObject, long packed0, byte[] params) {
        if (!canUsePrivateShop(pc) || size <= 0 || !(findObject instanceof L1PcInstance)) return;
        final L1PcInstance seller = (L1PcInstance) findObject;
        if (!privateShopParticipantsReady(pc, seller)) return;
        final ArrayList<MJDShopItem> sells = seller.getSellings();
        if (sells == null) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, seller)) {
            final LinkedHashMap<Integer, PrivateShopLine> lines = new LinkedHashMap<>();
            long total = 0L;
            synchronized (sells) {
                if (seller.getSellings() != sells || pc.getPartnersPrivateShopItemCount() != sells.size()) return;
                for (int i = 0; i < size; i++) {
                    int order = param1(packed0, params, i);
                    int count = (int) param2D(packed0, params, i);
                    if (order < 0 || order >= sells.size() || count <= 0) return;
                    MJDShopItem listing = sells.get(order);
                    if (listing == null || listing.isPurchase) return;
                    L1ItemInstance item = seller.getInventory().getItem(listing.objId);
                    if (item == null || item.isEquipped() || item.getItemdelay3() >= System.currentTimeMillis()
                            || item.getItemId() != listing.itemId || (!item.isStackable() && count != 1)) return;
                    PrivateShopLine line = lines.get(listing.objId);
                    if (line == null) { line = new PrivateShopLine(listing, item, 0); lines.put(listing.objId, line); }
                    long quantity = (long) line.count + count;
                    if (quantity > listing.count || quantity > item.getCount()) return;
                    line.count = (int) quantity;
                    int price = privateShopPrice(listing.price, count);
                    if (price <= 0 || (total += price) > L1Inventory.MAX_AMOUNT) return;
                }
                if (lines.isEmpty()) return;
                final L1ItemInstance adena = pc.getInventory().findItemId(L1ItemId.ADENA);
                if (adena == null || adena.getCount() < total) { SC_ServerMessage.send(pc, 189); return; }
                for (PrivateShopLine line : lines.values()) tx.transfer(seller, line.item, line.count, pc);
                tx.transfer(pc, adena, (int) total, seller);
                tx.requireTables("character_shop");
                for (PrivateShopLine line : lines.values())
                    tx.sql(con -> MJDShopStorage.consumeListing(con, seller.getId(), line.snapshot, line.count));
                tx.commit(() -> {
                    synchronized (sells) {
                        if (seller.getSellings() != sells) throw new IllegalStateException("Private shop changed after commit");
                        for (PrivateShopLine line : lines.values())
                            seller.applyCommittedShopQuantity(false, line.listing, line.snapshot.count, line.count);
                    }
                    for (PrivateShopLine line : lines.values()) {
                        SC_ServerMessage.send(seller, 877, pc.getName(), line.item.getItem().getName() + " (" + line.count + ")");
                        writeLogbuyPrivateShop(pc, seller, line.item, line.count, privateShopPrice(line.snapshot.price, line.count));
                    }
                });
            }
        } catch (RuntimeException rejected) {
            SC_SystemMessage.send(pc, "상점 처리를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

	private static void processPrivateNpcShopSell(L1PcInstance pc, int size, int npcId, long packed0, byte[] params) {
		if ((pc.getLevel() >= Config.PC_SHOP && pc.getClanid() <= 0) && !pc.isGm()) {
			pc.sendPackets("\\fY레벨 " + Config.PC_SHOP + " 이상 무혈 캐릭터는 상점을 이용할 수 없습니다");
			pc.sendPackets("\\fY최소한 혈가입은 서버에 원활한 게임을 위한 시스템입니다");
			return;
		}

		L1Shop shop = NpcShopManager.getInstance().getShop(npcId);
		if (shop == null) return;

		L1ShopSellOrderList orderList = shop.newSellOrderList(pc);
		if (size <= 0) return;

		for (int i = 0; i < size; i++) {
			int itemNumber = param1(packed0, params, i);
			long itemcount = param2D(packed0, params, i);
			if (itemcount <= 0) {
				return;
			}
			orderList.add(itemNumber, (int) itemcount, pc);
		}
		int bugok = orderList.BugOk();
		if (bugok == 0) {
			shop.buyItems(orderList);
			pc.saveInventory();
		}
	}

	private static void processSupplementaryService(L1PcInstance pc, int size, long packed0, byte[] params,
            Runnable onCommitted) {
		if (size <= 0) return;
		SupplementaryService warehouse = WarehouseManager.getInstance().getSupplementaryService(pc.getAccountName());
		if (warehouse == null)
			return;
		if (size > 100)
			return;
		for (int i = 0; i < size; i++) {
			int objectId = param1(packed0, params, i);
			int count = (int) param2D(packed0, params, i);
			L1ItemInstance item = warehouse.getItem(objectId);

			if (item == null) {
				return;
			}

			if (objectId != item.getId()) {
				SC_Disconnect.send(pc);
				return;
			}
			if (!item.isStackable() && count != 1) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count <= 0 || item.getCount() <= 0) {
				SC_Disconnect.send(pc);
				return;
			}
			if (count > item.getCount()) {
				count = item.getCount();
			}

			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
                Timestamp withdrawalEndTime = null;
				if (item.getItem().getItemId() == 3000214) {
					withdrawalEndTime = new Timestamp(System.currentTimeMillis() + 60000L * 8640);
				} else if (item.getItem().getItemId() == 3000213 || (item.getItem().getItemId() >= 758 && item.getItem().getItemId() <= 761)) {
					withdrawalEndTime = new Timestamp(System.currentTimeMillis() + 60000L * 4320);
				} else if (item.getItem().getItemId() == 3000209 || item.getItem().getItemId() == 41922
						|| item.getItem().getItemId() == 41923 || item.getItem().getItemId() == 41924
						|| item.getItem().getItemId() == 41925 || item.getItem().getItemId() == 900077
						|| item.getItem().getItemId() == 772 || item.getItem().getItemId() == 773 || item.getItem().getItemId() == 774 || item.getItem().getItemId() == 775) {
					withdrawalEndTime = new Timestamp(System.currentTimeMillis() + 60000L * 1440);
				} else if (item.getItem().getItemId() == 210095 || (item.getItem().getItemId() == 2100950)) {
					withdrawalEndTime = new Timestamp(System.currentTimeMillis() + 60000L * 180);
				}
                if (warehouse.tradeSupplementaryItem(item, count, pc.getInventory(), withdrawalEndTime, onCommitted) == null) {
                    SC_ServerMessage.send(pc, 189);
                    return;
                }
                // Persistence and audit are part of the committed WarehouseMove.
			} else {
				SC_ServerMessage.send(pc, 270);
				break;
			}
		}
	}

    private static void processPrivateShopSell(L1PcInstance pc, int size,
            L1Object findObject, long packed0, byte[] params) {
        if (!canUsePrivateShop(pc) || size <= 0 || !(findObject instanceof L1PcInstance)) return;
        final L1PcInstance buyer = (L1PcInstance) findObject;
        if (!privateShopParticipantsReady(pc, buyer)) return;
        final ArrayList<MJDShopItem> purchases = buyer.getPurchasings();
        if (purchases == null) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, buyer)) {
            final LinkedHashMap<Integer, PrivateShopLine> lines = new LinkedHashMap<>();
            final LinkedHashMap<Integer, Integer> listingQuantities = new LinkedHashMap<>();
            final LinkedHashMap<Integer, MJDShopItem> listingSnapshots = new LinkedHashMap<>();
            long total = 0L;
            synchronized (purchases) {
                if (buyer.getPurchasings() != purchases) return;
                for (int i = 0; i < size; i++) {
                    int objectId = param1(packed0, params, i);
                    int count = (int) param2CH(packed0, params, i);
                    int order = param3(packed0, params, i);
                    if (order < 0 || order >= purchases.size() || count <= 0) return;
                    MJDShopItem listing = purchases.get(order);
                    L1ItemInstance item = pc.getInventory().getItem(objectId);
                    L1ItemInstance sample = listing == null ? null : buyer.getInventory().getItem(listing.objId);
                    if (listing == null || !listing.isPurchase || item == null || sample == null
                            || item.isEquipped() || item.getItemId() != sample.getItemId()
                            || item.getEnchantLevel() != listing.enchant || item.getAttrEnchantLevel() != listing.attr
                            || (!item.isStackable() && count != 1)) return;
                    int listed = listingQuantities.getOrDefault(listing.objId, 0);
                    long quantity = (long) listed + count;
                    if (quantity > listing.count || count > item.getCount()) return;
                    listingQuantities.put(listing.objId, (int) quantity);
                    listingSnapshots.putIfAbsent(listing.objId, MJDShopStorage.copyListing(listing));
                    PrivateShopLine line = lines.get(objectId);
                    if (line == null) { line = new PrivateShopLine(listing, item, 0); lines.put(objectId, line); }
                    if (line.listing != listing || (long) line.count + count > item.getCount()) return;
                    line.count += count;
                    int price = privateShopPrice(listing.price, count);
                    if (price <= 0 || (total += price) > L1Inventory.MAX_AMOUNT) return;
                }
                if (lines.isEmpty()) return;
                L1ItemInstance adena = buyer.getInventory().findItemId(L1ItemId.ADENA);
                if (adena == null || adena.getCount() < total) { SC_ServerMessage.send(buyer, 189); return; }
                for (PrivateShopLine line : lines.values()) tx.transfer(pc, line.item, line.count, buyer);
                tx.transfer(buyer, adena, (int) total, pc);
                tx.requireTables("character_shop");
                for (java.util.Map.Entry<Integer, Integer> e : listingQuantities.entrySet()) {
                    final MJDShopItem expected = listingSnapshots.get(e.getKey());
                    final int amount = e.getValue();
                    tx.sql(con -> MJDShopStorage.consumeListing(con, buyer.getId(), expected, amount));
                }
                tx.commit(() -> {
                    synchronized (purchases) {
                        if (buyer.getPurchasings() != purchases) throw new IllegalStateException("Private purchase shop changed after commit");
                        for (java.util.Map.Entry<Integer, Integer> e : listingQuantities.entrySet()) {
                            MJDShopItem current = buyer.findPurchasings(e.getKey());
                            buyer.applyCommittedShopQuantity(true, current, listingSnapshots.get(e.getKey()).count, e.getValue());
                        }
                    }
                });
            }
        } catch (RuntimeException rejected) {
            SC_SystemMessage.send(pc, "상점 처리를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

	private static void writeLogbuyPrivateShop(L1PcInstance pc, L1PcInstance targetPc, L1ItemInstance item, int count, int price) {
		LoggerInstance.getInstance().개인상점구매(true, pc, targetPc, item, item.getCount());
	}

    /** One post-COMMIT ordered DB job for the selected clan-warehouse audit rows. */
    private static Runnable prepareClanDepositBatchHistory(L1PcInstance pc,
            L1ItemInstance[] items, int[] counts) {
        return prepareClanWarehouseBatchHistory(pc, items, counts, true);
    }

    private static Runnable prepareClanWarehouseBatchHistory(L1PcInstance pc,
            L1ItemInstance[] items, int[] counts, boolean deposit) {
        final int characterId = pc.getId();
        final int clanId = pc.getClanid();
        final String characterName = pc.getName();
        final int elapsedSec = (int) (System.currentTimeMillis() / 1000L);
        final int size = items.length;
        final int[] logIds = new int[size];
        final String[] itemNames = new String[size];
        for (int i = 0; i < size; i++) {
            final L1ItemInstance item = items[i];
            final int enchant = item.getEnchantLevel();
            logIds[i] = IdFactory.getInstance().nextId();
            itemNames[i] = item.getItem().getType2() != 0
                    ? (enchant >= 0 ? "+" : "") + enchant + " " + item.getName()
                    : item.getName();
        }
        return () -> CacheD.getInstance().submitDbJobOrdered(characterId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                final boolean previousAutoCommit = con.getAutoCommit();
                try {
                    con.setAutoCommit(false);
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO clan_warehousehistory SET id=?, clan_id=?, char_name=?, "
                          + "item_name=?, item_count=?, elapsed_time=?, item_getorput=?")) {
                        for (int i = 0; i < size; i++) {
                            ps.setInt(1, logIds[i]);
                            ps.setInt(2, clanId);
                            ps.setString(3, characterName);
                            ps.setString(4, itemNames[i]);
                            ps.setInt(5, counts[i]);
                            ps.setInt(6, elapsedSec);
                            ps.setString(7, deposit ? "맡겼습니다." : "찾았습니다.");
                            ps.addBatch();
                        }
                        final int[] results = ps.executeBatch();
                        if (results.length != size)
                            throw new SQLException("Incomplete clan warehouse audit batch");
                        for (int result : results)
                            if (result != 1 && result != java.sql.Statement.SUCCESS_NO_INFO)
                                throw new SQLException("Unexpected clan warehouse audit batch count: " + result);
                    }
                    con.commit();
                } catch (SQLException failure) {
                    try { con.rollback(); }
                    catch (SQLException rollback) { failure.addSuppressed(rollback); }
                    throw failure;
                } finally {
                    con.setAutoCommit(previousAutoCommit);
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Clan warehouse batch audit failed: " + characterId, ex);
            }
        });
    }

	private static void history(L1PcInstance pc, L1ItemInstance item, int count, int direction) {
        final int characterId = pc.getId();
        final int clanId = pc.getClanid();
        final String charName = pc.getName();
        final int elapsedSec = (int) (System.currentTimeMillis() / 1000L);
        final int enchant = item.getEnchantLevel();
        final String itemName = item.getItem().getType2() != 0
                ? (enchant >= 0 ? "+" : "") + enchant + " " + item.getName()
                : item.getName();
        final String action = direction == 1 ? "맡겼습니다." : "찾았습니다.";
        final int id = IdFactory.getInstance().nextId();
        CacheD.getInstance().submitDbJobOrdered(characterId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO clan_warehousehistory SET id=?, clan_id=?, char_name=?, item_name=?, item_count=?, elapsed_time=?, item_getorput=?")) {
                ps.setInt(1, id);
                ps.setInt(2, clanId);
                ps.setString(3, charName);
                ps.setString(4, itemName);
                ps.setInt(5, count);
                ps.setInt(6, elapsedSec);
                ps.setString(7, action);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Clan warehouse audit failed: " + characterId, ex);
            }
        });
    }

	private static void SetDeleteTime(L1ItemInstance item, int minute) {
		Timestamp deleteTime = null;
		deleteTime = new Timestamp(System.currentTimeMillis() + (60000 * minute));
		item.setEndTime(deleteTime);
	}

	private static void do_dogfight_buy_ticket(L1PcInstance pc, long packed0, byte[] params, int size) {
		final int[] param1 = materializeParam1(packed0, params, size);
		final long[] param2 = materializeParam2D(packed0, params, size);

		MJDogFightHistory.getInstance().on_buy_ticket(pc, param1, param2, size);
	}

	private static void do_dogfight_sell_ticket(L1PcInstance pc, long packed0, byte[] params, int size, int npc_object_id) {
		final int[] param1 = materializeParam1(packed0, params, size);
		final long[] param2 = materializeParam2D(packed0, params, size);

		MJDogFightHistory.getInstance().on_sell_ticket(pc, param1, param2, size, npc_object_id);
	}

	private static boolean hasParams(int size, int hasFirst) {
		return size > 0 && hasFirst != 0;
	}

	private static int param1(long packed0, byte[] rest, int index) {
		if (index == 0) {
			return (int) packed0;
		}
		return readD(rest, (index - 1) << 3);
	}

	private static long param2D(long packed0, byte[] rest, int index) {
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

	private static int param2CH(long packed0, byte[] rest, int index) {
		if (index == 0) {
			return (int) ((packed0 >>> 32) & 0x00FF_FFFFL);
		}
		final int offset = ((index - 1) << 3) + 4;
		return readCH(rest, offset);
	}

	private static int param3(long packed0, byte[] rest, int index) {
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

	private static int[] materializeParam1(long packed0, byte[] rest, int size) {
		if (size <= 0) {
			return null;
		}
		final int[] result = new int[size];
		for (int i = 0; i < size; i++) {
			result[i] = param1(packed0, rest, i);
		}
		return result;
	}

	private static long[] materializeParam2D(long packed0, byte[] rest, int size) {
		if (size <= 0) {
			return null;
		}
		final long[] result = new long[size];
		for (int i = 0; i < size; i++) {
			result[i] = param2D(packed0, rest, i);
		}
		return result;
	}
}
