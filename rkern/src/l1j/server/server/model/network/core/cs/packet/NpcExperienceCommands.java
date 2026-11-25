package l1j.server.server.model.network.core.cs.packet;


import l1j.server.server.datatables.CObjectDB;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class NpcExperienceCommands {
    private static final Logger LOG = Logger.getLogger(NpcExperienceCommands.class.getName());
    private NpcExperienceCommands() {}

    static String levelSupport(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("0")) {// 한번씩 지급
					if (pc.getLevel() < 51) {
						pc.addExp((ExpTable.getExpByLevel(51) - 1) - pc.getExp()
								+ ((ExpTable.getExpByLevel(51) - 1) / 100));
					} else if (pc.getLevel() >= 51 && pc.getLevel() < Config.경험치지급단) {
						pc.addExp((ExpTable.getExpByLevel(pc.getLevel() + 1) - 1) - pc.getExp() + 100);
						pc.setCurrentHp(pc.getMaxHp());
						pc.setCurrentMp(pc.getMaxMp());
					}else {
						SC_SystemMessage.send(pc, "\\f:경험치를 지급받을 수 없는 레벨입니다.");
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f:경험치를 지급받을 수 없는 레벨입니다.");
						htmlid = "";
					}
				} else if (s.equalsIgnoreCase("1")) {// 한번에 지급
					if (pc.getLevel() < Config.경험치지급단) {
						pc.addExp((ExpTable.getExpByLevel(Config.경험치지급단) - 1) - pc.getExp()
								+ ((ExpTable.getExpByLevel(Config.경험치지급단) - 1) / 30000000));
						pc.setCurrentHp(pc.getMaxHp());
						pc.setCurrentMp(pc.getMaxMp());
					} else {
						SC_SystemMessage.send(pc, "\\f:경험치를 지급받을 수 없는 레벨입니다.");
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f:경험치를 지급받을 수 없는 레벨입니다.");
						htmlid = "";
					}
				} else if (s.equalsIgnoreCase("2")) {// 신규지원
					if (pc.getLevel() >= Config.New_Cha) {// 이하
						SC_SystemMessage.send(pc, "신규가 아니므로, 더 이상 지원은 불가능 합니다.");
						return htmlid;
					} else if (pc.getInventory().HaveItemByClassId(7241, 1) || pc.getInventory().HaveItemByClassId(1000004, 1)) {
						SC_SystemMessage.send(pc, "중복 지급은 불가능합니다.");
						return htmlid;
					}
					봉인템(pc, 7241, 5, 0, 1, 0, true); // 토파즈
					봉인템(pc, 3000231, 3, 0, 1, 0, true); // 최고급 드다
					봉인템(pc, 1000007, 10, 0, 1, 0, true); // 고급드다
				}

        return htmlid;
    }

    // Debit and grant are one receipt; EXP is an owner-side post-commit effect,
    // not itself a durable field of that receipt.
    static String ekins(L1PcInstance pc, int npcObjectId, String command, String tail) {
        final int catalyst, reward;
        final boolean diamond, largeExp;
        if ("d".equalsIgnoreCase(command)) { catalyst = 3000215; reward = 8; diamond = true; largeExp = true; }
        else if ("c".equalsIgnoreCase(command)) { catalyst = 3000215; reward = 5; diamond = false; largeExp = false; }
        else if ("b".equalsIgnoreCase(command)) { catalyst = 810002; reward = 1; diamond = false; largeExp = true; }
        else if ("a".equalsIgnoreCase(command)) { catalyst = 810001; reward = 1; diamond = false; largeExp = false; }
        else return tail;
        if (!pc.getInventory().HaveItemByClassId(catalyst, 1) || (diamond && !pc.getInventory().HaveItemByClassId(1000004, 1))) return "ekins5";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 7200000)) {
            if (!NpcExchangeCommands.debitLegacy(tx, pc, catalyst, 1)
                    || (diamond && !NpcExchangeCommands.debitLegacy(tx, pc, 1000004, 1))
                    || tx.grant(810010, reward) == null) return "ekins5";
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "ekins5"));
            tx.commitAfterInventoryRelease(() -> {
                if (largeExp) 에킨스경험치6(pc);
                else 에킨스경험치2(pc);
                if (tail != null) SC_NPCTalkReturn.send(pc, npcObjectId, tail);
            });
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "NPC Ekins exchange rejected", e);
            return "ekins5";
        }
    }

	private static boolean 봉인템(L1PcInstance pc, int item_id, int count, int EnchantLevel, int Bless, int attr,
			boolean identi) {
		// 봉인템(pc, 5000045, 1, 5, 128);
        L1ItemInstance item = CObjectDB.LoadItem().createItem(item_id);
		if (item != null) {
			try {
				item.setCount(count);
				item.setIdentified(identi);
				item.setEnchantLevel(EnchantLevel);
				item.setAttrEnchantLevel(attr);
				item.setIdentified(true);
				if (pc.getInventory().CheckAddable(item, count) == CInventory.OK) {
					L1ItemInstance stored = pc.getInventory().CreateOrAddItem(item);
					if (stored == null) return false;
					stored.setBless(Bless);
					pc.getInventory().updateItem(stored, CUserInventory.COL_BLESS);
					pc.getInventory().saveItem(stored, CUserInventory.COL_BLESS);
				} else { // 가질 수 없는 경우는 지면에 떨어뜨리는 처리의 캔슬은 하지 않는다(부정 방지)
					SC_ServerMessage.send(pc, 82);
					// 무게 게이지가 부족하거나 인벤토리가 꽉차서 더 들 수 없습니다.
					return false;
				}
				SC_ServerMessage.send(pc, 403, item.getLogName()); //
				return true;
			} finally {
				// ItemTable registers before an inventory decision; rejected or
				// stack-merged item images must not survive in L1World.
				if (pc.getInventory().GetByServerID(item.getId()) != item) {
					L1World.getInstance().removeObject(item);
				}
			}
		} else {
			return false;
		}
	}

	static String arcFreePass(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.getLevel() >= 56 && pc.getLevel() <= 59) {
						if (!pc.getInventory().consumeItem(700016, 3)) {
							SC_SystemMessage.send(pc, "재료 또는 레벨이 맞지 않습니다.");
							htmlid = "archekins2";
							return htmlid;
						}
						pc.addExp((ExpTable.getExpByLevel(60) - 1) - pc.getExp()
								+ ((ExpTable.getExpByLevel(60) - 1) / 30000000));
						pc.setCurrentHp(pc.getMaxHp());
						pc.setCurrentMp(pc.getMaxMp());
						SC_SystemMessage.send(pc, "\\aA레벨업을 축하합니다. 이제 다음 단계를 진행 하시오.");
						// htmlid = "archekins2";
					} else {
						SC_SystemMessage.send(pc, "재료 또는 레벨이 맞지 않습니다.");
						htmlid = "archekins2";
						return htmlid;
					}
				} else if (s.equalsIgnoreCase("b")) {
					if (pc.getLevel() >= 60 && pc.getLevel() <= 64) {
						if (!pc.getInventory().consumeItem(700016, 5)) {
							SC_SystemMessage.send(pc, "재료 또는 레벨이 맞지 않습니다.");
							htmlid = "archekins2";
							return htmlid;
						}
						pc.addExp((ExpTable.getExpByLevel(65) - 1) - pc.getExp()
								+ ((ExpTable.getExpByLevel(65) - 1) / 30000000));
						pc.setCurrentHp(pc.getMaxHp());
						pc.setCurrentMp(pc.getMaxMp());
						SC_SystemMessage.send(pc, "\\aA레벨업을 축하합니다. 이제 다음 단계를 진행 하시오.");
						// htmlid = "archekins2";
					} else {
						SC_SystemMessage.send(pc, "재료 또는 레벨이 맞지 않습니다.");
						htmlid = "archekins2";
						return htmlid;
					}
				} else if (s.equalsIgnoreCase("c")) {
					if (pc.getLevel() >= 65 && pc.getLevel() <= 69) {
						if (!pc.getInventory().consumeItem(700016, 10)) {
							SC_SystemMessage.send(pc, "재료 또는 레벨이 맞지 않습니다.");
							htmlid = "archekins2";
							return htmlid;
						}
						pc.getInventory().CreateOrAddItem(3000213, 1);
						pc.addExp((ExpTable.getExpByLevel(70) - 1) - pc.getExp()
								+ ((ExpTable.getExpByLevel(70) - 1) / 30000000));
						pc.setCurrentHp(pc.getMaxHp());
						pc.setCurrentMp(pc.getMaxMp());
						SC_SystemMessage.send(pc, "\\aA아크프리패스를 완료 하였습니다. 아이템을 지급 하였습니다.");
					} else {
						SC_SystemMessage.send(pc, "재료 또는 레벨이 맞지 않습니다.");
						htmlid = "archekins2";
						return htmlid;
					}
				}
				/** 클라우디아 훈련 군터 **/
		return htmlid;
	}


	private static void 에킨스경험치2(L1PcInstance pc) {
		int needExp = ExpTable.getNeedExpNextLevel(52);
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		int exp = 0;
		if (pc.getLevel() <= 60) {
			exp = (int) (needExp * 0.04D);
		} else if (pc.getLevel() <= 65) {
			exp = (int) (needExp * 0.03D);
		} else if (pc.getLevel() <= 70) {
			exp = (int) (needExp * 0.02D);
		} else if (pc.getLevel() <= 75) {
			exp = (int) (needExp * 0.01D);
		} else {
			exp = (int) (needExp * 16D * exppenalty);
		}
		pc.addExp(exp);
		SC_SkillSound.broadcast(pc, pc.getId(), 3944);
		SC_SkillSound.send(pc, pc.getId(), 3944);
	}

	private static void 에킨스경험치6(L1PcInstance pc) {
		int needExp = ExpTable.getNeedExpNextLevel(52);
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		int exp = 0;
		if (pc.getLevel() <= 60) {
			exp = (int) (needExp * 0.12D);
		} else if (pc.getLevel() <= 65) {
			exp = (int) (needExp * 0.09D);
		} else if (pc.getLevel() <= 70) {
			exp = (int) (needExp * 0.06D);
		} else if (pc.getLevel() <= 75) {
			exp = (int) (needExp * 0.03D);
		} else {
			exp = (int) (needExp * 48D * exppenalty);
		}
		pc.addExp(exp);
		SC_SkillSound.broadcast(pc, pc.getId(), 3944);
		SC_SkillSound.send(pc, pc.getId(), 3944);
	}



    static String 게렝(L1PcInstance pc, String s) {
		String htmlid = "";
		if (s.equalsIgnoreCase("a")) {
			if (pc.getInventory().HaveItemByClassId(6003, 1) && pc.getInventory().HaveItemByClassId(6013, 100) && pc.getLevel() < 70) {
				pc.getInventory().consumeItem(6003, 1);
				pc.getInventory().consumeItem(6013, 100);
				int needExp = ExpTable.getNeedExpNextLevel(52);
				double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
				int exp = 0;
				if (pc.getLevel() <= 60) {
					exp = (int) (needExp * 0.04D);
				} else if (pc.getLevel() <= 65) {
					exp = (int) (needExp * 0.03D);
				} else if (pc.getLevel() <= 70) {
					exp = (int) (needExp * 0.02D);
				} else {
					exp = (int) (needExp * 16D * exppenalty);
				}
				pc.addExp(exp);
				SC_SkillSound.broadcast(pc, pc.getId(), 3944);
				SC_SkillSound.send(pc, pc.getId(), 3944);
			} else {
				htmlid = "gereng02";
			}
		}
		return htmlid;
	}

    static void 마빈경험치지급2(L1PcInstance pc) {
		int needExp = ExpTable.getNeedExpNextLevel(52);
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		int exp = 0;
		if (pc.getLevel() <= 60) {
			exp = (int) (needExp * 0.04D);
		} else if (pc.getLevel() <= 65) {
			exp = (int) (needExp * 0.03D);
		} else if (pc.getLevel() <= 70) {
			exp = (int) (needExp * 0.02D);
		} else if (pc.getLevel() <= 75) {
			exp = (int) (needExp * 0.01D);
		} else {
			exp = (int) (needExp * 16D * exppenalty);
		}
		pc.addExp(exp);
	}

    static void 마빈경험치지급6(L1PcInstance pc) {
		int needExp = ExpTable.getNeedExpNextLevel(52);
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		int exp = 0;
		if (pc.getLevel() <= 60) {
			exp = (int) (needExp * 0.12D);
		} else if (pc.getLevel() <= 65) {
			exp = (int) (needExp * 0.09D);
		} else if (pc.getLevel() <= 70) {
			exp = (int) (needExp * 0.06D);
		} else if (pc.getLevel() <= 75) {
			exp = (int) (needExp * 0.03D);
		} else {
			exp = (int) (needExp * 48D * exppenalty);
		}
		pc.addExp(exp);
	}

    /** The reward and every consumed material share the inventory receipt.
     * Experience remains an existing owner-side post-commit effect. */
    static String marbin(L1PcInstance pc, int objectId, String command, String tail) {
        final boolean small = "a".equalsIgnoreCase(command);
        final boolean gift = "b".equalsIgnoreCase(command);
        final boolean diamond = "d".equalsIgnoreCase(command);
        if (!small && !gift && !diamond && !"c".equalsIgnoreCase(command)) return tail;
        if (gift && pc.getLevel() < 70) return "marbinquest8";
        final int first = small ? 700013 : 700014;
        final int second = diamond ? 1000004 : 700016;
        final String failure = small ? "marbinquest9" : gift ? "marbinquest8" : "marbinquest7";
        final String success = small ? "marbinquest4" : gift ? "marbinquest2" : "marbinquest6";
        if (!gift && (!pc.getInventory().HaveItemByClassId(first, 1) || !pc.getInventory().HaveItemByClassId(second, 1)
                || !pc.getInventory().HaveItemByClassId(700015, 100))) return failure;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 70000)) {
            if (!gift && (!NpcExchangeCommands.debitLegacy(tx, pc, first, 1)
                    || !NpcExchangeCommands.debitLegacy(tx, pc, second, 1)
                    || !NpcExchangeCommands.debitLegacy(tx, pc, 700015, 100))) return failure;
            if (tx.grant(gift ? 700012 : 700017, gift ? 1 : small ? 5 : 15) == null) return failure;
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, objectId, failure));
            tx.commitAfterInventoryRelease(() -> {
                if (!gift) {
                    if (small) 마빈경험치지급2(pc); else 마빈경험치지급6(pc);
                    SC_SkillSound.broadcast(pc, pc.getId(), 3944);
                    SC_SkillSound.send(pc, pc.getId(), 3944);
                }
                SC_NPCTalkReturn.send(pc, objectId, success);
            });
            return null;
        } catch (RuntimeException error) {
            LOG.log(Level.WARNING, "NPC Marbin exchange rejected", error);
            return failure;
        }
    }
}
