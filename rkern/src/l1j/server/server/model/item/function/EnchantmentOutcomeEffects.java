package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

/** Phase 12: original EnchantmentItems.java SuccessEnchant body, unchanged. */
final class EnchantmentOutcomeEffects {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(EnchantmentOutcomeEffects.class.getName());
    private EnchantmentOutcomeEffects() {}


    private static void sendEnchantUpdate(L1PcInstance pc, L1ItemInstance item) {
        try {
            pc.getInventory().updateItem(item, L1PcInventory.COL_ENCHANTLVL);
        } catch (RuntimeException notificationFailure) {
            // updateItem() also rejects an in-flight CacheD inventory gate.
            // That is a game-state failure, not a harmless packet exception.
            if (notificationFailure instanceof IllegalStateException
                    && "Item mutation refused while CacheD transfer pending".equals(notificationFailure.getMessage()))
                throw notificationFailure;
            LOG.log(java.util.logging.Level.WARNING,
                    "Enchant result item-status notification failed", notificationFailure);
        }
    }

    static void SuccessEnchant(L1PcInstance pc, L1ItemInstance item, CUserSocket client, int i) {
        successEnchant(pc, item, client, i, false);
    }

    /** The receipt already stored and replayed the new enchant level.
     * Apply only its in-world equipment bonuses and non-durable notices.
     */
    static void SuccessEnchantCommitted(L1PcInstance pc, L1ItemInstance item, CUserSocket client, int i) {
        successEnchant(pc, item, client, i, true);
    }

    private static void successEnchant(L1PcInstance pc, L1ItemInstance item, CUserSocket client,
                                       int i, boolean alreadyCommitted) {
        final int oldEnchantLvl = item.getEnchantLevel() - (alreadyCommitted ? i : 0);
		String s = "";
		String sa = "";
		String sb = "";
		String s1 = item.getName();
		String pm = "";
		if (oldEnchantLvl > 0) {
			pm = "+";
		}
		if (item.getItem().getType2() == 1) {
			if (!item.isIdentified() || oldEnchantLvl == 0) {
				switch (i) {
				case -1:
					s = s1;
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = s1;
					sa = "$245";
					sb = "$247";
					break;

				case 2: // '\002'
					s = s1;
					sa = "$245";
					sb = "$248";
					break;

				case 3: // '\003'
					s = s1;
					sa = "$245";
					sb = "$248";
					break;
				}
			} else {
				switch (i) {
				case -1:
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$247";
					break;

				case 2: // '\002'
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$248";
					break;

				case 3: // '\003'
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$248";
					break;
				}
			}
		} else if (item.getItem().getType2() == 2) {
			if (!item.isIdentified() || oldEnchantLvl == 0) {
				switch (i) {
				case -1:
					s = s1;
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = s1;
					sa = "$252";
					sb = "$247 ";
					break;

				case 2: // '\002'
					s = s1;
					sa = "$252";
					sb = "$248 ";
					break;

				case 3: // '\003'
					s = s1;
					sa = "$252";
					sb = "$248 ";
					break;
				}
			} else {
				switch (i) {
				case -1:
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$247 ";
					break;

				case 2: // '\002'
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$248 ";
					break;

				case 3: // '\003'
					s = pm + oldEnchantLvl + " " + s1;
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$248 ";
					break;
				}
			}
		}
        int newEnchantLvl = oldEnchantLvl + i;
        int safe_enchant = item.getItem().get_safeenchant();
        if (alreadyCommitted) {
            if (item.getEnchantLevel() != newEnchantLvl)
                throw new IllegalStateException("Committed enchant replay changed before effects");
        } else {
            item.setEnchantLevel(newEnchantLvl);
            // Legacy non-receipt callers retain their original save path.
            try {
                pc.getInventory().saveItem(item, L1PcInventory.COL_ENCHANTLVL);
            } catch (RuntimeException submissionFailure) {
                item.setEnchantLevel(oldEnchantLvl);
                throw submissionFailure;
            }
            sendEnchantUpdate(pc, item);
        }
		if (newEnchantLvl > safe_enchant) {
			/** 로그파일저장 **/
            try {
			    LoggerInstance.getInstance().addEnchant(pc, item, true);
            } catch (RuntimeException loggingFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant success audit log failed", loggingFailure);
            }
		}

		/*if (Config.MASTERENCHANTMESS) {
		if (item.getItem().getType2() == 1) {
			if (newEnchantLvl >= 10) {
				//String message = String.format("어느 아덴 용사 님께서 +%d %s 인챈트에 성공하였습니다.", oldEnchantLvl, item.getName());
				SC_SystemMessage.broadcast("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\fH인챈트에 성공하였습니다.");
				RPacketBox.broadcastGreen("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\f2인챈트에 성공하였습니다.");
			}
		} else {
			if ((item.getItem().getType() >= 8 && item.getItem().getType() <= 12 ) || (item.getItem().getType() == 30) || (item.getItem().getType() == 28) ) {
				if (newEnchantLvl >= 5) {
					SC_SystemMessage.broadcast("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\fH인챈트에 성공하였습니다.");
					RPacketBox.broadcastGreen("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\f2인챈트에 성공하였습니다.");
				}
			} else {
				if (newEnchantLvl >= 8) {
				SC_SystemMessage.broadcast("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\fH인챈트에 성공하였습니다.");
				RPacketBox.broadcastGreen("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\f2인챈트에 성공하였습니다.");
			}
			}
		}
	
	}*/

		if (item.getItem().getType2() == 1 && Config.LOGGING_WEAPON_ENCHANT != 0) {
			if (safe_enchant == 0 || newEnchantLvl >= Config.LOGGING_WEAPON_ENCHANT) {
			}
		}
		if (item.getItem().getType2() == 2 && Config.LOGGING_ARMOR_ENCHANT != 0) {
			if (safe_enchant == 0 || newEnchantLvl >= Config.LOGGING_ARMOR_ENCHANT) {
			}
		}

		if (item.getItem().getType2() == 2) {
			if (item.isEquipped()) {
				if (item.getItem().getType() >= 8 && item.getItem().getType() <= 12) {
				} else {
					pc.getAC().addAc(-i);
				}
				int i2 = item.getItem().getItemId();
                boolean magicResistChanged = false;
				if (i2 == 20011 || i2 == 20110 || i2 == 120011 || i2 == 22204 || i2 == 22223 || i2 == 22205
						|| i2 == 22206 || i2 == 22207 || i2 == 22213 || i2 == 22365 || i2 == 120110 || i2 == 93001
						|| i2 >= 222300 && i2 <= 222303 || i2 == 222328) {// 타라스의
					// 부츠
					pc.getResistance().addMr(i);
                    magicResistChanged = true;
				}
				if (i2 == 20056 || i2 == 120056 || i2 == 220056 || i2 == 93002 || i2 == 20074 || i2 == 120074) { // 매직
					// 클로크
					pc.getResistance().addMr(i * 2);
                    magicResistChanged = true;
				}
				if (i2 == 20079 || i2 == 20078) {
					pc.getResistance().addMr(i * 3);
                    magicResistChanged = true;
				}
                if (magicResistChanged) {
                    try {
                        SC_SPMR.send(pc);
                    } catch (RuntimeException packetFailure) {
                        LOG.log(java.util.logging.Level.WARNING, "Enchant MR notification failed after MR update", packetFailure);
                    }
                }
			}

			try {
                SC_OwnCharStatus.send(pc);
            } catch (RuntimeException packetFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant status notification failed after result application", packetFailure);
            }
		}
        if (Config.MASTERENCHANTMESS && newEnchantLvl >= 10) {
            try {
                SC_SystemMessage.broadcast("어느 아덴 용사 님께서 +" + oldEnchantLvl + " " + item.getName() + " \\fH인챈트에 성공하였습니다.");
            } catch (RuntimeException packetFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant global notification failed after result application", packetFailure);
            }
            try {
                SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 +" + oldEnchantLvl + " " + item.getName() + " \\f2인챈트에 성공하였습니다.");
            } catch (RuntimeException packetFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant green notification failed after result application", packetFailure);
            }
        }
        if (i != -1 && oldEnchantLvl != 0) {
            try {
                SC_ServerMessage.send(pc, 161, s, sa, sb);
            } catch (RuntimeException packetFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant success notification failed after result application", packetFailure);
            }
        }
	}

    // Failure and success share the same enchant result responsibility.
	static void FailureEnchant(L1PcInstance pc, L1ItemInstance item, CUserSocket client) {
		String s = "";
		String sa = "";
		int itemType = item.getItem().getType2();
		int itemId = item.getItem().getItemId();
		String nameId = item.getName();
		String pm = "";

		if (itemType == 1) { // 무기
			if (!item.isIdentified() || item.getEnchantLevel() == 0) {
				s = nameId;
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = "$245";
			} else {
				if (item.getEnchantLevel() > 0) {
					pm = "+";

				}
				s = pm + item.getEnchantLevel() + " " + nameId;
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = "$245";
			}
		} else if (itemType == 2) { // 방어용 기구
			if (!item.isIdentified() || item.getEnchantLevel() == 0) {
				s = nameId;
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = " $252";
			} else {
				if (item.getEnchantLevel() > 0) {
					pm = "+";
				}
				s = pm + item.getEnchantLevel() + " " + nameId;
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = " $252";
			}
		}
		// if ((itemId >= 1115 && itemId <= 1118) || (itemId >= 22250 && itemId
		// <= 22252)) {//신묘한 날자에 러쉬부분?
		if ((itemId >= 1115 && itemId <= 1118)) {

			pc.getInventory().setEquipped(item, false);
			item.setEnchantLevel(0);
            pc.getInventory().saveItem(item, L1PcInventory.COL_ENCHANTLVL);
            sendEnchantUpdate(pc, item);
            try {
                SC_ServerMessage.send(pc, 1310);
            } catch (RuntimeException packetFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant reset failure notification failed", packetFailure);
            }
			if (itemType == 1) {
			} else if (itemType == 2) {
			}
		} else {
			pc.getInventory().removeItem(item, item.getCount());
			/** 로그파일저장 **/
            try {
                LoggerInstance.getInstance().addEnchant(pc, item, false);
            } catch (RuntimeException loggingFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant destruction audit log failed", loggingFailure);
            }
            try {
                SC_ServerMessage.send(pc, 164, s, sa);
            } catch (RuntimeException packetFailure) {
                LOG.log(java.util.logging.Level.WARNING, "Enchant destruction notification failed", packetFailure);
            }
		}
		try {
            SC_OwnCharStatus.send(pc);
        } catch (RuntimeException packetFailure) {
            LOG.log(java.util.logging.Level.WARNING, "Enchant failure status notification failed", packetFailure);
        }
	}
}
