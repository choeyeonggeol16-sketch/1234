package l1j.server.server.model.item.function;


import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import java.util.Random;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.monitor.LoggerInstance;


import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;
import static l1j.server.server.model.item.function.ItemUsePotions.*;
import static l1j.server.server.model.item.function.ItemUseTransformation.*;
import static l1j.server.server.model.item.function.ItemUseSpellBooks.*;
import static l1j.server.server.model.item.function.ItemUseWorldHelpers.*;
import static l1j.server.server.model.item.function.ItemUseSpecialEnchant.*;
import static l1j.server.server.model.item.function.ItemUseMiscActions.*;
import static l1j.server.server.model.item.function.ItemUseEquipment.*;
import static l1j.server.server.model.item.function.ItemUseDurable.*;
import static l1j.server.server.model.item.function.ItemUseCombatAbilities.*;
import static l1j.server.server.model.item.function.ItemUseDollEffects.*;

import static l1j.server.server.model.item.function.ItemUseCreature.*;

public final class ItemUseEnchantments {
    private static final Random _random = ItemUseCreature._random;
    private static final Logger ITEM_USE2_LOG = ItemUseCreature.ITEM_USE2_LOG;
    private ItemUseEnchantments() {}
	public static void AttrEnchant(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance weapon) {
		if (!isOwnedScrollTarget(pc, scroll, weapon) || scroll.isEquipped()
				|| weapon.isEquipped() || weapon.getItem().getType2() != 1) return;
		final int scrollId = scroll.getItemId();
		final int base;
		switch (scrollId) {
		case 210067: base = 0; break;  // fire
		case 210066: base = 5; break;  // water
		case 210064: base = 10; break; // wind
		case 210065: base = 15; break; // earth
		default: return;
		}
		final int attr = weapon.getAttrEnchantLevel();
		if (attr < 0 || attr > 20 || (attr != 0 && (attr <= base || attr > base + 5))) {
			SC_ServerMessage.send(pc, 1294);
			return;
		}
		final int grade = attr == 0 ? 0 : attr - base;
		if (grade >= 5) {
			pc.sendPackets("더 이상 강화할 수 없습니다.");
			return;
		}
		if ((grade == 3 && weapon.getEnchantLevel() < 9
				|| grade == 4 && weapon.getEnchantLevel() < 10)
				&& !is_legend_weapon(weapon.getItemId())) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		// Preserve the exact old roll: nextInt(80)+1 and <30/<20/<10/<7/<4.
		final int chance = _random.nextInt(80) + 1;
		final int limit = grade == 0 ? 30 : grade == 1 ? 20 : grade == 2 ? 10
				: grade == 3 ? 7 : 4;
		final boolean success = chance < limit;
		final int next = base + grade + 1;
		final String weaponName = weapon.getLogName();
		try (l1j.server.server.model.L1InventoryTransaction tx =
				User.CheckAndSetPendingUseETCItem(pc,
						CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(scroll, 1)) return;
			if (success) tx.change(weapon, image -> image.setAttrEnchantLevel(next));
			tx.onFailure(() -> SC_SystemMessage.send(pc, "속성 강화 주문서 저장에 실패했습니다."));
			CDB.RequestUseItem(pc, tx, () -> {
				if (success)
					SC_PacketBox.send(pc, SC_PacketBox.ITEM_ENCHANT_UPDATE, weapon);
				SC_ServerMessage.send(pc, success ? 1410 : 1411, weaponName);
			});
		} catch (RuntimeException failure) {
			ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
					"Attribute enchant prepare failed: " + pc.getId() + " / " + scrollId, failure);
			SC_SystemMessage.send(pc, "속성 강화 주문서를 처리하지 못했습니다.");
		}
	}

	public static void enchant_insignia(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_enchant = target.getEnchantLevel();
		if (target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("휘장보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.Insignia_Level) {
			pc.sendPackets(String.format("휘장은 +%d 이상 보호주문서로 인챈트할 수 없습니다.", Config.Insignia_Level));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0
				: target_enchant >= Config.Insignia.length ? Config.Insignia.length - 1 : target_enchant;
		if (MJRnd.isWinning(100, Config.Insignia[index])) {
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
		} else {
			SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
		}
		pc.getInventory().removeItem(scroll, 1);
	}

	public static void enchant_should(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_enchant = target.getEnchantLevel();
		if (target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("빛나는 아덴 용사 보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.Should_Level) {
			pc.sendPackets(String.format("빛나는 아덴 용사 방어구 +%d 이상 보호주문서로 인챈트할 수 없습니다.", Config.Should_Level));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0 : target_enchant >= Config.Should.length ? Config.Should.length - 1 : target_enchant;
		if (MJRnd.isWinning(100, Config.Should[index])) {
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
		} else {
			SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
		}
		pc.getInventory().removeItem(scroll, 1);
	}

	public static void enchant_sentence(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_enchant = target.getEnchantLevel();
		if (target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("문장보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.SentenceLevel) {
			pc.sendPackets(String.format("문장은 +%d 이상 보호주문서로 인챈트할 수 없습니다.", Config.SentenceLevel));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0 : target_enchant >= Config.Sentence.length ? Config.Sentence.length - 1 : target_enchant;
		if (MJRnd.isWinning(100, Config.Sentence[index])) {
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
		} else {
			SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
		}
		pc.getInventory().removeItem(scroll, 1);
	}

	public static void enchant_roomtis(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("룸티스보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.RoomtisLevel) {
			pc.sendPackets(String.format("룸티스류 악세사리는 +%d 이상 보호주문서로 인챈트할 수 없습니다.", Config.RoomtisLevel));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0
				: target_enchant >= Config.Roomtis.length ? Config.Roomtis.length - 1 : target_enchant;
		if (MJRnd.isWinning(100, Config.Roomtis[index])) {
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
		} else {
			SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
		}
		pc.getInventory().removeItem(scroll, 1);
	}

	public static void enchant_snapper(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("스냅퍼보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.SnapperLevel) {
			pc.sendPackets(String.format("스냅퍼류 악세사리는 +%d 이상 보호주문서로 인챈트할 수 없습니다.", Config.SnapperLevel));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0
				: target_enchant >= Config.Sanpper.length ? Config.Sanpper.length - 1 : target_enchant;
		if (MJRnd.isWinning(100, Config.Sanpper[index])) {
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
		} else {
			SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
		}
		pc.getInventory().removeItem(scroll, 1);
	}

	
	public static void enchant_insignia1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_enchant = target.getEnchantLevel();
		if (target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("휘장보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.Insignia_Level) {
			pc.sendPackets(String.format("휘장은 +%d 이상 인챈트할 수 없습니다.", Config.Insignia_Level));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			//pc.sendPackets(new SC_SystemMessage("순간 강렬하게 빛을내어 100% 인챈트가 되었습니다."));
		
		pc.getInventory().removeItem(scroll, 1);
	}

	

	public static void enchant_sentence1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_enchant = target.getEnchantLevel();
		if (target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("문장보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.SentenceLevel) {
			pc.sendPackets(String.format("문장은 +%d 이상 인챈트할 수 없습니다.", Config.SentenceLevel));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		SuccessEnchant(pc, target, pc.getNetConnection(), +1);
		//pc.sendPackets(new SC_SystemMessage("순간 강렬하게 빛을내어 100% 인챈트가 되었습니다."));
		pc.getInventory().removeItem(scroll, 1);
	}

	public static void enchant_roomtis1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("룸티스보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.RoomtisLevel) {
			pc.sendPackets(String.format("룸티스류 악세사리는 +%d 이상 인챈트할 수 없습니다.", Config.RoomtisLevel));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		SuccessEnchant(pc, target, pc.getNetConnection(), +1);
		//pc.sendPackets(new SC_SystemMessage("순간 강렬하게 빛을내어 100% 인챈트가 되었습니다."));
		pc.getInventory().removeItem(scroll, 1);
	}

	public static void enchant_snapper1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		/*if (target_enchant < 3) {
			pc.sendPackets(String.format("스냅퍼보호주문서는 +%d 사용이 가능합니다.", 3));
			return;
		}*/
		if (target_enchant >= Config.SnapperLevel) {
			pc.sendPackets(String.format("스냅퍼류 악세사리는 +%d 이상 인챈트할 수 없습니다.", Config.SnapperLevel));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		SuccessEnchant(pc, target, pc.getNetConnection(), +1);
		//pc.sendPackets(new SC_SystemMessage("순간 강렬하게 빛을내어 100% 인챈트가 되었습니다."));
		pc.getInventory().removeItem(scroll, 1);
	}

	
	
	
	public static void enchant_bless_orim(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		int target_type = target.getItem().getType();
		int target_enchant = target.getEnchantLevel();
		if (target_type < 8 || target_type > 12 || target.getBless() >= 128) {
			SC_ServerMessage.send(pc, 79);
			return;
		}
		if (target_enchant >= Config.악세사리) {
			pc.sendPackets(String.format("악세사리는 +%d 이상은 인챈할 수 없습니다.", Config.악세사리));
			pc.sendPackets("제한해제시 공지 하겠습니다.");
			return;
		}

		int index = target_enchant < 0 ? 0
				: target_enchant >= Config.bless_orim.length ? Config.bless_orim.length - 1 : target_enchant;
		if (MJRnd.isWinning(100, Config.bless_orim[index])) {
			SuccessEnchant(pc, target, pc.getNetConnection(), +1);
			SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
		} else {
			SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
		}
		pc.getInventory().removeItem(scroll, 1);
	}

	public static void SuccessEnchant(L1PcInstance pc, L1ItemInstance item, CIOClient client, int i) {
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
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$247";
					break;

				case 2: // '\002'
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$245";
					sb = "$248";
					break;

				case 3: // '\003'
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
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
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$246";
					sb = "$247";
					break;

				case 1: // '\001'
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$247 ";
					break;

				case 2: // '\002'
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
					// \f1%0이%2%1 빛납니다.
					sa = "$252";
					sb = "$248 ";
					break;

				case 3: // '\003'
					s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(s1).toString();
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

	public static void FailureEnchant(L1PcInstance pc, L1ItemInstance item, CIOClient client) {
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
				s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(nameId).toString();
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
				s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(nameId).toString();
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
