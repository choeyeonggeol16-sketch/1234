package l1j.server.server.model.item.function;

import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.character.skill.L1SkillId;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_PUPLE;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_TOPAZ;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.Account;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** Item-use behavior extracted without changing invocation order. */
final class AinBlessingGemConsumables {
    private AinBlessingGemConsumables() {}



	public static boolean useGemCase(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
		if (itemId == 410139 && pc.getLevel() >= 82) {
			pc.sendPackets("레벨 82이상은 사용이 불가능 합니다.");
			return true;
		}
		/** 드래곤의 최고급 다이아몬드 **/
		if (itemId == L1ItemId.DRAGON_DIAMOND2) {
			if (pc.hasSkillEffect(L1SkillId.EMERALD_YES) == true) {
				SC_ServerMessage.send(pc, 2146);
				return true;
			}
			//						if (pc.getAccount().getBlessOfAin() <= 12000000) {
			if (pc.getAccount().getBlessOfAin() <= 34000000) {
				AinBlessingItems.consume(pc, l1iteminstance, 16000000, 34000000, () -> {
					SC_SystemMessage.send(pc, "아인하사드의 축복이 1600% 추가되었습니다.");
					SC_SkillSound.send(pc, pc.getId(), 15357);
					SC_SkillSound.broadcast(pc, pc.getId(), 15357);
				});
			} else {
				SC_SystemMessage.send(pc, "축복지수 3400미만에서 사용할 수 있습니다.");
			}
		} else if (itemId == L1ItemId.DRAGON_DIAMOND || itemId == L1ItemId.DRAGON_DIAMOND1 || itemId == 1000007) {
			if (pc.hasSkillEffect(L1SkillId.EMERALD_YES) == true) {
				SC_ServerMessage.send(pc, 2146);
				return true;
			}
			if (pc.getAccount().getBlessOfAin() <= 45000000) {
				AinBlessingItems.consume(pc, l1iteminstance,
						itemId == 1000007 ? 6000000 : 1000000, 45000000, () -> {
							if (itemId == 1000007) {
								SC_SystemMessage.send(pc, "아인하사드의 축복이 600% 추가되었습니다.");
							} else {
								SC_SystemMessage.send(pc, "아인하사드의 축복이 100% 추가되었습니다.");
							}
							SC_SkillSound.send(pc, pc.getId(), 15357);
							SC_SkillSound.broadcast(pc, pc.getId(), 15357);
						});
			} else {
				SC_SystemMessage.send(pc, "축복지수 4500미만에서 사용할 수 있습니다.");
			}
		} else if (itemId == L1ItemId.DRAGON_SAPPHIRE) {
			if (pc.hasSkillEffect(L1SkillId.EMERALD_YES) == true) {
				SC_ServerMessage.send(pc, 2146);
				return true;
			}
			if (pc.getAccount().getBlessOfAin() <= 47000000) {
				AinBlessingItems.consume(pc, l1iteminstance, 500000, 47000000, () -> {
					SC_SystemMessage.send(pc, "아인하사드의 축복: 50% 추가.");
					SC_SkillSound.send(pc, pc.getId(), 15357);
					SC_SkillSound.broadcast(pc, pc.getId(), 15357);
				});
			} else {
				SC_SystemMessage.send(pc, "축복지수 4700미만에서만 사용하실수 있습니다.");
			}
		} else if (itemId == L1ItemId.DRAGON_RUBY || itemId == 6017) {
			if (pc.hasSkillEffect(L1SkillId.EMERALD_YES) == true) {
				SC_ServerMessage.send(pc, 2146);
				return true;
			}
			if (pc.getAccount().getBlessOfAin() <= 47000000) {
				AinBlessingItems.consume(pc, l1iteminstance, 300000, 47000000, () -> {
					SC_SystemMessage.send(pc, "아인하사드의 축복: 30% 추가.");
					SC_SkillSound.send(pc, pc.getId(), 15357);
					SC_SkillSound.broadcast(pc, pc.getId(), 15357);
				});
			} else {
				SC_SystemMessage.send(pc, "축복지수 4700미만에서만 사용하실수 있습니다.");
			}
		} else if (itemId == L1ItemId.EMERALD || itemId == L1ItemId.EMERALD1) {
			if (pc.hasSkillEffect(L1SkillId.EMERALD_NO) == true) {
				SC_ServerMessage.send(pc, 2145);
				return true;
			} else if (pc.hasSkillEffect(L1SkillId.EMERALD_YES) == true) {
				SC_ServerMessage.send(pc, 2147);
				return true;
			} else if (pc.hasSkillEffect(L1SkillId.DRAGON_PUPLE)
					|| pc.hasSkillEffect(L1SkillId.DRAGON_TOPAZ)) {
				SC_ServerMessage.send(pc, 2147);
				return true;
			}
			AinBlessingItems.consume(pc, l1iteminstance, 1000000, 50000000, () -> {
				pc.setSkillEffect(L1SkillId.EMERALD_YES, 1800 * 1000);
				SC_PacketBox.send(pc, SC_PacketBox.EMERALD_ICON, 0x02, 1800);
				SC_ServerMessage.send(pc, 2140);
			});
		} else if (itemId == 60255) {
			if (pc.hasSkillEffect(L1SkillId.EMERALD_YES) == true) {
				SC_ServerMessage.send(pc, 2146);
				return true;
			}
			if (pc.getAccount().getBlessOfAin() <= 10000) {
				SC_SystemMessage.send(pc, "축복지수가 있어야 사용하실수 있습니다.");
				return true;
			}
			UseDurableConsume(pc, l1iteminstance, () -> {
				if (pc.hasSkillEffect(L1SkillId.DRAGON_TOPAZ)) {
					pc.removeSkillEffect(L1SkillId.DRAGON_TOPAZ);
				}
				pc.setSkillEffect(DRAGON_PUPLE, 1800 * 1000);
				SC_PacketBox.send(pc, 1800, 1, true, true);
				SC_SkillSound.send(pc, pc.getId(), 197);
				SC_SkillSound.broadcast(pc, pc.getId(), 197);
			});

		} else if (itemId == 7241 || itemId == 3000457) {
			if (pc.hasSkillEffect(L1SkillId.EMERALD_YES) == true) {
				SC_ServerMessage.send(pc, 2146);
				return true;
			}
			if (pc.getAccount().getBlessOfAin() <= 10000) {
				SC_SystemMessage.send(pc, "축복지수가 있어야 사용하실수 있습니다.");
				return true;
			}
			UseDurableConsume(pc, l1iteminstance, () -> {
				if (pc.hasSkillEffect(L1SkillId.DRAGON_PUPLE)) {
					pc.removeSkillEffect(L1SkillId.DRAGON_PUPLE);
				}
				pc.setSkillEffect(DRAGON_TOPAZ, 1800 * 1000);
				SC_PacketBox.send(pc, 1800, 2, true, true);
				SC_SkillSound.send(pc, pc.getId(), 197);
				SC_SkillSound.broadcast(pc, pc.getId(), 197);
			});
		}
		return false;
	}
}
