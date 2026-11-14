package l1j.server.server.model.item.function;


import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.MJCommons;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;

import static l1j.server.server.model.item.function.ItemUseCreature.*;

public final class ItemUseDurable {
    private static final Logger ITEM_USE2_LOG = ItemUseCreature.ITEM_USE2_LOG;
    private ItemUseDurable() {}
    public static void UseDurableChargedBox(L1PcInstance pc, L1ItemInstance box) {
        if (pc == null || box == null || pc.getInventory().getItem(box.getId()) != box)
            return;
        final int id = box.getItemId();
        switch (id) {
        case 42656: case 90736: case 90735: case 3000245: case 3000244:
        case 3000242: case 90737: case 6012: case 6016:
            break;
        default:
            return;
        }
        final int charge = box.getChargeCount();
        if (charge <= 0) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final boolean exhausted = charge == 1;
        final String boxName = exhausted ? box.getLogName() : null;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (exhausted) {
                // Legacy removeItem(box) removed the entire exhausted instance.
                if (!tx.debit(box, box.getCount())) return;
            } else {
                tx.changeChargeCount(box, charge - 1);
            }
            L1ItemInstance r1 = null, r2 = null, r3 = null, r4 = null;
            L1ItemInstance r5 = null, r6 = null;
            if (id == 90737) {
                r1 = tx.grant(700013, 1);
            } else if (id == 6012) {
                r1 = tx.grant(6003, 1);
            } else if (id == 6016) {
                r1 = tx.grant(40098, 10);
                r2 = tx.grant(40099, 10);
                r3 = tx.grant(40029, 100);
                r4 = tx.grant(40096, 3);
                r5 = tx.grant(40096, 2);
                int classItem = pc.isElf() ? 42651
                        : (pc.isWizard() || pc.isBlackwizard()) ? 42652 : 42650;
                r6 = tx.grant(classItem, 3);
            }
            if ((id == 90737 || id == 6012) && r1 == null
                    || id == 6016 && (r1 == null || r2 == null || r3 == null
                            || r4 == null || r5 == null || r6 == null)) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            final L1ItemInstance reward1 = r1, reward2 = r2, reward3 = r3;
            final L1ItemInstance reward4 = r4, reward5 = r5, reward6 = r6;
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "상자 사용 내역을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (id == 90737) {
                    SC_SystemMessage.send(pc, "획득: " + reward1.getLogName());
                } else if (id == 6012 || id == 6016) {
                    if (reward1 != null) SC_ServerMessage.send(pc, 403, reward1.getLogName());
                    if (reward2 != null) SC_ServerMessage.send(pc, 403, reward2.getLogName());
                    if (reward3 != null) SC_ServerMessage.send(pc, 403, reward3.getLogName());
                    if (reward4 != null) SC_ServerMessage.send(pc, 403, reward4.getLogName());
                    if (reward5 != null) SC_ServerMessage.send(pc, 403, reward5.getLogName());
                    if (reward6 != null) SC_ServerMessage.send(pc, 403, reward6.getLogName());
                }
                if (exhausted) SC_SystemMessage.send(pc, boxName + " 다 사용 하였습니다.");
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Charged box prepare failed: " + pc.getId() + " / " + id, failure);
            SC_SystemMessage.send(pc, "상자 사용을 처리하지 못했습니다.");
        }
    }

    public static void UseDurableRepair(L1PcInstance pc, L1ItemInstance whetstone,
            L1ItemInstance target, Runnable afterCommitted) {
        if (pc == null || whetstone == null || target == null || whetstone == target
                || pc.getInventory().getItem(whetstone.getId()) != whetstone
                || pc.getInventory().getItem(target.getId()) != target
                || target.getItem().getType2() == 0 || target.get_durability() <= 0) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(whetstone, 1)) return;
            tx.repairDurability(target);
            tx.onFailure(() -> SC_SystemMessage.send(pc, "숫돌 사용을 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, afterCommitted);
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Whetstone prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "숫돌 사용을 처리하지 못했습니다.");
        }
    }

    public static void UseDurableConsume(L1PcInstance pc, L1ItemInstance input, Runnable afterCommitted) {
        ItemUseTransactions.consume(pc, input, afterCommitted);
    }

    public static void UseDurableConsumeTwoInput(L1PcInstance pc, L1ItemInstance first,
            L1ItemInstance second, Runnable afterCommitted) {
        ItemUseTransactions.consumeTwo(pc, first, second, afterCommitted);
    }

	public static void UseDurableExchange(L1PcInstance pc, L1ItemInstance input, int rewardId) {
		UseDurableExchange(pc, input, rewardId, null);
	}

	public static void UseDurableExchange(L1PcInstance pc, L1ItemInstance input, int rewardId,
			Runnable afterCommitted) {
		UseDurableExchange(pc, input, rewardId, 1, afterCommitted);
	}

	public static void UseDurableExchange(L1PcInstance pc, L1ItemInstance input, int rewardId,
			int rewardCount, Runnable afterCommitted) {
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(input, 1)) return;
			if (tx.grant(rewardId, rewardCount) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 교환이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, afterCommitted);
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Item exchange prepare failed: " + pc.getId(), failure);
			SC_SystemMessage.send(pc, "아이템 교환을 처리하지 못했습니다.");
		}
	}



    public static void UseDurableEnchantedBag(L1PcInstance pc, L1ItemInstance input,
            int rewardId, int enchantLevel) {
        if (pc == null || input == null
                || pc.getInventory().getItem(input.getId()) != input
                || (input.getItemId() != 3000126 && input.getItemId() != 3000127)
                || (input.getItemId() == 3000126 ? rewardId != 900021 : rewardId != 900020)
                || enchantLevel != 3) return;
        l1j.server.server.templates.L1Item definition = ItemTable.getInstance().getTemplate(rewardId);
        if (definition == null) return;
        // A detached image is required: createItem() publishes to L1World and
        // is invalid for a receipt-backed NEW grant.
        L1ItemInstance reward = new L1ItemInstance(definition, 1);
        // grant() snapshots via Warehouse.transferCopy(), which requires a
        // positive ID, but this ID must not already exist in the world.
        reward.setId(IdFactory.getInstance().nextId());
        reward.setBless(definition.getBless());
        reward.setEnchantLevel(enchantLevel);
        reward.setIdentified(true);
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            L1ItemInstance prepared = tx.grant(reward);
            if (prepared == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "문장 주머니 교환이 저장되지 않았습니다."));
            CDB.RequestUseItem(pc, tx, () -> SC_ServerMessage.send(pc, 403, prepared.getLogName()));
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Enchanted bag prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "문장 주머니 사용을 처리하지 못했습니다.");
        }
    }


    public static void UseLawfulPotionAtomic(L1PcInstance pc, L1ItemInstance input) {
        if (pc == null || input == null || pc.getInventory().getItem(input.getId()) != input)
            return;
        final int itemId = input.getItemId();
        if (itemId != 490028 && itemId != 490029) return;
        // addLawful() refuses to act during standby. Do not consume the potion.
        if (Config.STANDBY_SERVER) return;
        final int before = pc.getLawful();
        if (itemId == 490028 && before >= 32767) {
            pc.sendPackets("현재 사용할수있는 조건이 아닙니다.");
            return;
        }
        if (itemId == 490029 && before <= -32768) {
            pc.sendPackets("현재 사용할수있는 조건이 아닙니다.");
            return;
        }
        if (before < -32768 || before > 32767) return;
        // Both legacy +70000 / -70000 operations always clamp to the limit.
        final int next = itemId == 490028 ? 32767 : -32768;
        final int characterId = pc.getId();
        try {
            // Flush the earlier character snapshot before the durable item+stat
            // receipt so a delayed save cannot restore the old lawful value.
            CacheD.getInstance().saveCharacter(
                    new l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot(pc));
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    l1j.server.server.model.L1InventoryTransaction.begin(pc, input.getId())) {
                if (!tx.debit(input, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET Lawful=? WHERE objid=? AND Lawful=?")) {
                        ps.setInt(1, next);
                        ps.setInt(2, characterId);
                        ps.setInt(3, before);
                        if (ps.executeUpdate() != 1)
                            throw new SQLException("Lawful preimage changed: " + characterId);
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "성향 물약을 저장하지 못했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.getLawful() != before)
                        throw new IllegalStateException("Lawful changed before receipt apply: " + characterId);
                    pc.setLawful(next);
                    pc.onChangeLawful();
                    if (itemId == 490028) SC_ServerMessage.send(pc, 674);
                    else pc.sendPackets("영혼이 지저분해 지는것을 느낍니다.");
                    pc.save();
                });
            }
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Lawful potion prepare failed: " + characterId, failure);
            SC_SystemMessage.send(pc, "성향 물약 사용을 처리하지 못했습니다.");
        }
    }

    public static void UseSpecialSizeExpansion(L1PcInstance pc, L1ItemInstance input) {
        if (pc == null || input == null || input.getItemId() != 89136
                || pc.getInventory().getItem(input.getId()) != input) return;
        final int before = pc.get_SpecialSize();
        if (before == 40) {
            SC_ServerMessage.send(pc, 1622);
            return;
        }
        if (before != 0 && before != 20) return;
        final int next = before + 20;
        final int level = pc.getLevel();
        try {
            CacheD.getInstance().saveCharacter(
                    new l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot(pc));
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    l1j.server.server.model.L1InventoryTransaction.begin(pc, input.getId())) {
                if (!tx.debit(input, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET SpecialSize=? "
                            + "WHERE objid=? AND SpecialSize=? AND level=?")) {
                        ps.setInt(1, next);
                        ps.setInt(2, pc.getId());
                        ps.setInt(3, before);
                        ps.setInt(4, level);
                        if (ps.executeUpdate() != 1)
                            throw new SQLException("SpecialSize preimage changed: " + pc.getId());
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "슬롯 확장 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.get_SpecialSize() != before || pc.getLevel() != level)
                        throw new IllegalStateException("SpecialSize changed before receipt apply");
                    pc.set_SpecialSize(next);
                    SC_ServerMessage.send(pc, 1624, Integer.toString(next));
                    pc.save();
                });
            }
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "SpecialSize expansion prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "슬롯 확장을 처리하지 못했습니다.");
        }
    }

	public static void UseDurableTwoInput(L1PcInstance pc, L1ItemInstance catalyst,
			L1ItemInstance target, int rewardId, Runnable afterCommitted) {
		UseDurableTwoInput(pc, catalyst, target, rewardId, 1, afterCommitted);
	}

	static void UseDurableTwoInput(L1PcInstance pc, L1ItemInstance catalyst,
			L1ItemInstance target, int rewardId, int rewardCount, Runnable afterCommitted) {
		if (catalyst == null || target == null || catalyst == target || rewardCount <= 0)
			return;
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(catalyst, 1) || !tx.debit(target, 1)) {
				SC_SystemMessage.send(pc, "사용할 재료가 부족합니다.");
				return;
			}
			if (tx.grant(rewardId, rewardCount) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 교환이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, afterCommitted);
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Two-input item use failed: " + pc.getId() + " / " + rewardId, failure);
			SC_SystemMessage.send(pc, "아이템 사용을 처리하지 못했습니다.");
		}
	}

	public static void UseDurableCraft(L1PcInstance pc, int inputId, int inputCount,
			int rewardId, Runnable afterCommitted) {
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(inputId, inputCount)) {
				SC_SystemMessage.send(pc, "제작 재료가 부족합니다.");
				return;
			}
			if (tx.grant(rewardId, 1) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 제작이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, afterCommitted);
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Item craft prepare failed: " + pc.getId() + " / " + inputId, failure);
			SC_SystemMessage.send(pc, "아이템 제작을 처리하지 못했습니다.");
		}
	}

	public static void UseDurableRevive(L1PcInstance pc, int inputId, int rewardId,
			String missingMessage, String successMessage) {
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(inputId, 8)) {
				SC_SystemMessage.send(pc, missingMessage);
				return;
			}
			if (tx.grant(rewardId, 1) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "제작을 저장하지 못했습니다. 재료를 확인해 주세요."));
			CDB.RequestUseItem(pc, tx, () -> {
				SC_SystemMessage.send(pc, "아이템 제작에 성공했습니다.");
				SC_SkillSound.send(pc, pc.getId(), 7976);
				SC_SkillSound.broadcast(pc, pc.getId(), 7976);
				SC_ChatPacket.sendDirect(pc, pc, successMessage, 1);
			});
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Item revival prepare failed: " + pc.getId() + " / " + inputId, failure);
			SC_SystemMessage.send(pc, "아이템 제작을 처리하지 못했습니다.");
		}
	}

	public static void UseDurableTowerCharm(L1PcInstance pc, L1ItemInstance input) {
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(560028, 1) || !tx.debit(830011, 200)
					|| !tx.debit(40308, 500000000) || !tx.debit(input, 1)) {
				SC_SystemMessage.send(pc, "재료가 부족합니다.");
				return;
			}
			if (tx.grant(4100135, 1) == null) {
				SC_ServerMessage.send(pc, 82);
				return;
			}
			tx.onFailure(() -> SC_SystemMessage.send(pc, "부적 제작이 저장되지 않았습니다."));
			CDB.RequestUseItem(pc, tx, () -> SC_ChatPacket.sendDirect(pc, pc,
					"환상의 지배의 탑 지배 부적을 제작 하였습니다.", 1));
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING, "Tower charm prepare failed: " + pc.getId(), failure);
			SC_SystemMessage.send(pc, "부적 제작을 처리하지 못했습니다.");
		}
	}




	public static void UseImmuneBlade(L1PcInstance pc) {

		if (pc.getLevel() >= Config.이뮨투함블레이드레벨) {
			if (MJCommons.isNonAction(pc)) {
				return;
			}
			//			if (pc.hasSkillEffect(L1SkillId.SHOCK_STUN) || pc.hasSkillEffect(L1SkillId.EMPIRE)
			//					|| pc.hasSkillEffect(L1SkillId.EARTH_BIND) || pc.hasSkillEffect(L1SkillId.BONE_BREAK)) {
			//				return;
			//			}

			if (pc.getWeapon() == null) {
				pc.sendPackets("무기를 착용하고 사용 가능 합니다.");
				return;
			}


			if (pc.getType() == 0) { // 자네 군주?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드군주피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(군주): 한손검 및 양손검 착용");
					return;
				}
			}
			if (pc.getType() == 1) { // 자네 기사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드기사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(기사): 한손검 및 양손검 착용");
					return;
				}
			}
			if (pc.getType() == 2) { // 자네 요정?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드요정피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || 
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(요정): 활, 한손검, 양손검 중 착용");
					return;
				}
			}
			if (pc.getType() == 3) { // 자네 마법사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드법사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 
						||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 
						) {
					SC_SystemMessage.send(pc, "사용조건(마법사): 지팡이, 한손검 중 착용");
					return;
				}
			}
			if (pc.getType() == 4) { // 자네 다크엘프?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드다엘피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 1) >= 1 || pc.getInventory().getTypeEquipped(1, 3) >= 1 || pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(다크엘프): 이도류, 크로우 중 착용");
					return;
				}

			}
			if (pc.getType() == 5) { // 자네 용기사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드용기사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||pc.getInventory().getTypeEquipped(1, 17) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(용기사): 체인소드, 한손검, 양손검 중 착용");
					return;
				}
			}
			if (pc.getType() == 6) { // 자네 환술사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드환술피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 1) >= 1 ||pc.getInventory().getTypeEquipped(1, 3) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 6) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 15) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(환술사): 키링크 착용");
					return;
				}
			}
			if (pc.getType() == 7) { // 자네 환술사?
				if (pc.getCurrentHp() < Config.이뮨투함블레이드전사피) {
					pc.sendPackets("HP가 부족합니다.");
					return;
				}
				if (pc.getInventory().getTypeEquipped(1, 1) >= 1 ||pc.getInventory().getTypeEquipped(1, 3) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1||
						pc.getInventory().getTypeEquipped(1, 5) >= 1 ||pc.getInventory().getTypeEquipped(1, 18) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 7) >= 1 ||pc.getInventory().getTypeEquipped(1, 8) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 11) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 12) >= 1 ||pc.getInventory().getTypeEquipped(1, 13) >= 1 ||
						pc.getInventory().getTypeEquipped(1, 14) >= 1 ||

						pc.getInventory().getTypeEquipped(1, 16) >= 1 ) {
					SC_SystemMessage.send(pc, "사용조건(전사): 도끼 착용");
					return;
				}
			}
			if (pc.hasSkillEffect(L1SkillId.이뮨투함블레이드대기)) {
				//
				int n = pc.getSkillEffectTimeSec(L1SkillId.이뮨투함블레이드대기);
				SC_SystemMessage.send(pc, String.format("\\fY이뮨 투 함 블레이드 : %d초 후에 사용가능합니다.", n));
				return;
			}



			if (pc.getType() == 0) { // 자네 군주?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드군주피);
			} if (pc.getType() == 1) { // 자네 기사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드기사피);
			} if ( pc.getType() == 2) { // 자네 요정?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드요정피);
			} if ( pc.getType() == 3) { // 자네 마법사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드법사피);
			} if ( pc.getType() == 4) { // 자네 다크엘프?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드다엘피);
			} if (pc.getType() == 5) { // 자네 용기사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드용기사피);
			} if (pc.getType() == 6) { // 자네 환술사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드환술피);
			} if (pc.getType() == 7) { // 자네 환술사?
				pc.setCurrentHp(pc.getCurrentHp() - Config.이뮨투함블레이드전사피);
			}

			if (pc.hasSkillEffect(L1SkillId.이뮨투함블레이드)) {
				pc.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			
			L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(),
					pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
			
			SC_SkillSound.send(pc, pc.getId(), 16518);
			SC_SkillSound.broadcast(pc, pc.getId(), 16518);
			pc.setSkillEffect(L1SkillId.이뮨투함블레이드, Config.이뮨투함블레이드지속 * 1000);
			pc.setSkillEffect(L1SkillId.이뮨투함블레이드대기, Config.이뮨투함블레이드대기 * 1000);
			SC_InventoryIcon.icoNew(pc, L1SkillId.이뮨투함블레이드, 5125, Config.이뮨투함블레이드지속, true);
			pc.sendPackets("HP를 소모하며 이뮨 투 함 블레이드가 효과가 적용됩니다.");

		} else {
			pc.sendPackets("현재 [이뮨 투 함 블레이드]를 사용할 수 없는 조건입니다.");

		}

	}



    // Immutable reward IDs: boss summon uses this table on every activation.
    private static final int[][] BOSS_SUMMON_TABLE = {
					{ 45573, 81201, 45609, 7320217, 7310015, 7310021, 7310028, 7310034, 7310041, 7310046, 7310051,
						7310056, 7310061, 7310066 },
					{ 45546, 45600, 45601, 7320072, 7320073, 7320074, 7320075, 7320076, 7320077, 7320078, 7320079,
							7320080, 7320081 },
					{ 5136, 5135, 5146, 45529, 7000093, 7310148, 7310154, 7310160, 45684 } };

	public static void UseMobEventSpawnWand(L1PcInstance pc, L1ItemInstance item, Random random) {
		try {


			int category = 0;
			int rnd = random.nextInt(BOSS_SUMMON_TABLE[category].length);
			L1SpawnUtil.spawn(pc, BOSS_SUMMON_TABLE[category][rnd], 0, 0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


    /** An eligible pet voucher, its amulet and its pets record commit in one
     * CacheD receipt. A world pet is created only after the DB has committed.
     * No nearby NPC may be adopted as a side effect of this voucher.
     */
    public static void UsePetAmulet(CIOClient client, L1ItemInstance input, int npcId) {
        final L1PcInstance pc = client == null ? null : client.getActiveChar();
        if (pc == null || input == null
                || pc.getInventory().getItem(input.getId()) != input) return;
        final l1j.server.server.templates.L1Npc template =
                l1j.server.server.datatables.NpcTable.getInstance().getTemplate(npcId);
        if (template == null || PetTypeTable.getInstance().get(npcId) == null) return;

        int charisma = pc.getAbility().getTotalCha();
        int petCost = 0;
        for (L1NpcInstance pet : pc.getPetList().values()) petCost += pet.getPetcost();
        if (pc.isElf()) charisma += 12;
        else if (pc.isCrown() || pc.isWizard() || pc.isDarkelf()
                || pc.isDragonknight() || pc.isBlackwizard()) charisma += 6;
        if ((charisma - petCost) / 6 <= 0) {
            SC_ServerMessage.send(pc, 489);
            return;
        }
        if (pc.getInventory().getSize() >= 180) {
            SC_ServerMessage.send(pc, 82);
            return;
        }

        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            final L1ItemInstance amulet = tx.grant(40314, 1);
            if (amulet == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            final l1j.server.server.templates.L1Pet pet = new l1j.server.server.templates.L1Pet();
            pet.set_itemobjid(amulet.getId());
            pet.set_objid(IdFactory.getInstance().nextId());
            pet.set_npcid(npcId);
            pet.set_name(template.get_nameid());
            pet.set_level(template.get_level());
            pet.set_hp(template.get_hp());
            pet.set_mp(template.get_mp());
            pet.set_exp(750);
            pet.set_lawful(0);

            tx.requireTables("pets");
            tx.sql(con -> {
                try (PreparedStatement insert = con.prepareStatement(
                        "INSERT INTO pets(item_obj_id,objid,npcid,name,lvl,hp,mp,exp,lawful) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)")) {
                    insert.setInt(1, pet.get_itemobjid());
                    insert.setInt(2, pet.get_objid());
                    insert.setInt(3, pet.get_npcid());
                    insert.setString(4, pet.get_name());
                    insert.setInt(5, pet.get_level());
                    insert.setInt(6, pet.get_hp());
                    insert.setInt(7, pet.get_mp());
                    insert.setInt(8, pet.get_exp());
                    insert.setInt(9, pet.get_lawful());
                    if (insert.executeUpdate() != 1)
                        throw new SQLException("Pet voucher row insert failed");
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "펫 소환권 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                // Offline/relogin can recover the committed amulet and pets row
                // if the world pet could not be created after COMMIT.
                PetTable.getInstance().registerCommittedPet(pet);
                final L1PetInstance spawned = new L1PetInstance(template, pc, pet);
                spawned.setPetcost(6);
                final L1ItemInstance current = pc.getInventory().getItem(amulet.getId());
                if (current != null) SC_ItemName.send(pc, current);
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Pet voucher prepare failed: " + pc.getId() + " / " + npcId, failure);
            SC_SystemMessage.send(pc, "펫 소환권을 처리하지 못했습니다.");
        }
    }



    /** Keep one elixir's character stat, consumed count, and physical item
     * in the same durable CacheD receipt. Never grant a stat before COMMIT.
     */
    public static void UseElixirAtomic(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || pc.getInventory().getItem(item.getId()) != item)
            return;
        final int type = item.getItemId() - 40033;
        if (type < 0 || type > 5) return;
        final int level = pc.getLevel();
        final int allowed = level >= 80 ? 6 : level >= 75 ? 5 : level >= 70 ? 4
                : level >= 65 ? 3 : level >= 60 ? 2 : level >= 55 ? 1 : 0;
        final int beforeCount = pc.getElixirStats();
        if (allowed == 0 || beforeCount >= allowed || beforeCount >= 6) {
            SC_ServerMessage.send(pc, 4472);
            return;
        }
        final int beforeStat;
        final String column;
        switch (type) {
        case 0: beforeStat = pc.getAbility().getStr(); column = "Str"; break;
        case 1: beforeStat = pc.getAbility().getCon(); column = "Con"; break;
        case 2: beforeStat = pc.getAbility().getDex(); column = "Dex"; break;
        case 3: beforeStat = pc.getAbility().getInt(); column = "Intel"; break;
        case 4: beforeStat = pc.getAbility().getWis(); column = "Wis"; break;
        case 5: beforeStat = pc.getAbility().getCha(); column = "Cha"; break;
        default: return;
        }
        if (beforeStat >= 45) {
            SC_ServerMessage.send(pc, 481);
            return;
        }
        final int nextStat = beforeStat + 1;
        final int nextCount = beforeCount + 1;
        try {
            // The full character snapshot is ordered before the item receipt;
            // it must not overwrite the new stat and ElixirStatus afterward.
            CacheD.getInstance().saveCharacter(
                    new l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot(pc));
            try (l1j.server.server.model.L1InventoryTransaction tx =
                    l1j.server.server.model.L1InventoryTransaction.begin(pc, item.getId())) {
                if (!tx.debit(item, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET " + column + "=?, ElixirStatus=? "
                            + "WHERE objid=? AND " + column + "=? AND ElixirStatus=? AND level=?")) {
                        ps.setInt(1, nextStat);
                        ps.setInt(2, nextCount);
                        ps.setInt(3, pc.getId());
                        ps.setInt(4, beforeStat);
                        ps.setInt(5, beforeCount);
                        ps.setInt(6, level);
                        if (ps.executeUpdate() != 1)
                            throw new SQLException("Elixir stat preimage changed: " + pc.getId());
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "엘릭서 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    final int liveStat;
                    switch (type) {
                    case 0: liveStat = pc.getAbility().getStr(); break;
                    case 1: liveStat = pc.getAbility().getCon(); break;
                    case 2: liveStat = pc.getAbility().getDex(); break;
                    case 3: liveStat = pc.getAbility().getInt(); break;
                    case 4: liveStat = pc.getAbility().getWis(); break;
                    default: liveStat = pc.getAbility().getCha(); break;
                    }
                    if (pc.getLevel() != level || pc.getElixirStats() != beforeCount
                            || liveStat != beforeStat)
                        throw new IllegalStateException("Elixir live stat changed before receipt apply");
                    switch (type) {
                    case 0: pc.getAbility().addStr(1); break;
                    case 1: pc.getAbility().addCon(1); break;
                    case 2: pc.getAbility().addDex(1); pc.resetBaseAc(); break;
                    case 3: pc.getAbility().addInt(1); break;
                    case 4: pc.getAbility().addWis(1); pc.resetBaseMr(); break;
                    case 5: pc.getAbility().addCha(1); break;
                    }
                    pc.setElixirStats(nextCount);
                    SC_OwnCharStatus2.send(pc);
                    SC_ActionUI2.send(pc, SC_ActionUI2.Elixir, nextCount);
                    pc.save();
                });
            }
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Elixir transaction prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "엘릭서 사용을 처리하지 못했습니다.");
        }
    }

	static boolean UseSealedItem(L1PcInstance pc, int item_id, int count, int EnchantLevel, int Bless, int attr,
			boolean identi) {
		// 봉인템(pc, 5000045, 1, 5, 128);
		L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			item.setCount(count);
			item.setIdentified(identi);
			item.setEnchantLevel(EnchantLevel);
			item.setAttrEnchantLevel(attr);
			item.setIdentified(true);
			if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
				pc.getInventory().storeItem(item);
				item.setBless(Bless);
				pc.getInventory().updateItem(item, L1PcInventory.COL_BLESS);
				pc.getInventory().saveItem(item, L1PcInventory.COL_BLESS);
			} else { // 가질 수 없는 경우는 지면에 떨어뜨리는 처리의 캔슬은 하지 않는다(부정 방지)
				SC_ServerMessage.send(pc, 82);
				// 무게 게이지가 부족하거나 인벤토리가 꽉차서 더 들 수 없습니다.
				return false;
			}
			SC_ServerMessage.send(pc, 403, item.getLogName()); //
			return true;
		} else {
			return false;
		}
	}


}
