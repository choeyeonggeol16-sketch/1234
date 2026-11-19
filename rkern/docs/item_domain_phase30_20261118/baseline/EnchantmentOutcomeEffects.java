package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

/** Phase 12: original EnchantmentItems.java SuccessEnchant body, unchanged. */
final class EnchantmentOutcomeEffects {
    private EnchantmentOutcomeEffects() {}

	static void SuccessEnchant(L1PcInstance pc, L1ItemInstance item, CIOClient client, int i) {
		String s = "";
		String sa = "";
		String sb = "";
		String s1 = item.getName();
		String pm = "";
		if (item.getEnchantLevel() > 0) {
			pm = "+";
		}
		if (item.getItem().getType2() == 1) {
			if (!item.isIdentified() || item.getEnchantLevel() == 0) {
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
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$247";
					break;

				case 2: // '\002'
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$248";
					break;

				case 3: // '\003'
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$248";
					break;
				}
			}
		} else if (item.getItem().getType2() == 2) {
			if (!item.isIdentified() || item.getEnchantLevel() == 0) {
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
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$247 ";
					break;

				case 2: // '\002'
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$248 ";
					break;

				case 3: // '\003'
					s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$248 ";
					break;
				}
			}
		}
		if(i != -1 && item.getEnchantLevel() != 0){
		SC_ServerMessage.send(pc, 161, s, sa, sb);
		}
		int oldEnchantLvl = item.getEnchantLevel();
		int newEnchantLvl = item.getEnchantLevel() + i;
		int safe_enchant = item.getItem().get_safeenchant();
		item.setEnchantLevel(newEnchantLvl);
		client.getActiveChar().getInventory().updateItem(item, L1PcInventory.COL_ENCHANTLVL);
		pc.saveInventory();
		if (newEnchantLvl > safe_enchant) {
			client.getActiveChar().getInventory().saveItem(item, L1PcInventory.COL_ENCHANTLVL);
			pc.saveInventory();
			/** 로그파일저장 **/
			LoggerInstance.getInstance().addEnchant(pc, item, true);
		}

		if (Config.MASTERENCHANTMESS && newEnchantLvl >= 10) {
			//String message = String.format("어느 아덴 용사 님께서 +%d %s 인챈트에 성공하였습니다.", oldEnchantLvl, item.getName());
			SC_SystemMessage.broadcast("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\fH인챈트에 성공하였습니다.");
			SC_PacketBox.broadcastGreen("어느 아덴 용사 님께서 +"+oldEnchantLvl+" " +item.getName()+" \\f2인챈트에 성공하였습니다.");
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
				if (i2 == 20011 || i2 == 20110 || i2 == 120011 || i2 == 22204 || i2 == 22223 || i2 == 22205
						|| i2 == 22206 || i2 == 22207 || i2 == 22213 || i2 == 22365 || i2 == 120110 || i2 == 93001
						|| i2 >= 222300 && i2 <= 222303 || i2 == 222328) {// 타라스의
					// 부츠
					pc.getResistance().addMr(i);
					SC_SPMR.send(pc);
				}
				if (i2 == 20056 || i2 == 120056 || i2 == 220056 || i2 == 93002 || i2 == 20074 || i2 == 120074) { // 매직
					// 클로크
					pc.getResistance().addMr(i * 2);
					SC_SPMR.send(pc);
				}
				if (i2 == 20079 || i2 == 20078) {
					pc.getResistance().addMr(i * 3);
					SC_SPMR.send(pc);
				}
			}

			SC_OwnCharStatus.send(pc);
		}
	}

    // Failure and success share the same enchant result responsibility.
	static void FailureEnchant(L1PcInstance pc, L1ItemInstance item, CIOClient client) {
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
				s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(nameId).toString();
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
				s = new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append(nameId).toString();
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = " $252";
			}
		}
		// if ((itemId >= 1115 && itemId <= 1118) || (itemId >= 22250 && itemId
		// <= 22252)) {//신묘한 날자에 러쉬부분?
		if ((itemId >= 1115 && itemId <= 1118)) {

			SC_ServerMessage.send(pc, 1310);
			pc.getInventory().setEquipped(item, false);
			item.setEnchantLevel(0);
			pc.getInventory().updateItem(item, L1PcInventory.COL_ENCHANTLVL);
			pc.saveInventory();
			if (itemType == 1) {
			} else if (itemType == 2) {
			}
		} else {
			SC_ServerMessage.send(pc, 164, s, sa);
			pc.getInventory().removeItem(item, item.getCount());
			/** 로그파일저장 **/
			LoggerInstance.getInstance().addEnchant(pc, item, false);
		}
		SC_OwnCharStatus.send(pc);
	}
}
