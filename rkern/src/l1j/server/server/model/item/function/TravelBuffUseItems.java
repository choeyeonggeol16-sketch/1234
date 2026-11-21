package l1j.server.server.model.item.function;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.TravelUseTables.*;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
final class TravelBuffUseItems {
    private TravelBuffUseItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 731: {
			if (pc.hasSkillEffect(L1SkillId.TOGO_BUFF)) {
				SC_ServerMessage.send(pc, 79);
				return;
			}
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				SC_InventoryIcon.icoReset(pc, L1SkillId.TOGO_BUFF, 4992, 1800L, true);
				SC_SkillSound.send(pc, pc.getId(), 7470);
				SC_SkillSound.broadcast(pc, pc.getId(), 7470);
			});
		}
		break;

		case 3000397: {// 야히군사 버프주문서
			if (pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
				SC_ServerMessage.send(pc, 79);
				return;
			}
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setSkillEffect(STATUS_CURSE_BARLOG, 1020 * 1000); // 1020
				SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 1, 1020);
				SC_SkillSound.send(pc, pc.getId(), 750);
				SC_SkillSound.broadcast(pc, pc.getId(), 750);
				SC_ServerMessage.send(pc, 1127);
			});
		}
		break;

		case 3000398: {// 발록군사 버프주문서
			if (pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
				SC_ServerMessage.send(pc, 79);
				return;
			}
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setSkillEffect(STATUS_CURSE_YAHEE, 1020 * 1000);
				SC_PacketBox.send(pc, SC_PacketBox.ICON_AURA, 2, 1020);
				SC_SkillSound.send(pc, pc.getId(), 750);
				SC_SkillSound.broadcast(pc, pc.getId(), 750);
				SC_ServerMessage.send(pc, 1127);
			});
		}
		break;

		case 650009: {
			if (Config.STANDBY_SERVER) {
				SC_ChatPacket.sendDirect(pc, pc, "오픈대기시간동안은 사용할 수 없습니다.");
				return;
			}
			if (pc.getWeapon() == null) {
				SC_ChatPacket.sendDirect(pc, pc, "무기를 착용하고 사용가능합니다.");
				return;
			}
			final boolean bow = pc.getWeapon().getItem().getType1() == 20;
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
                final int[] skills = bow ? AUTO_BUFF_BOW : AUTO_BUFF_MELEE;
				pc.setBuffnoch(1);
				for (int skill : skills) {
					L1SkillUse.cast(pc, skill, pc.getId(), pc.getX(), pc.getY(), null, 0,
							L1SkillUse.TYPE_GMBUFF);
				}
				SC_SkillSound.send(pc, pc.getId(), 830);
				pc.curePoison();
			});
			break;
		}

		case 3000128: {// 메티스의 축복주문서
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () ->
				L1SkillUse.cast(pc, L1SkillId.메티스축복주문서, pc.getId(), pc.getX(), pc.getY(), null, 0,
					L1SkillUse.TYPE_GMBUFF));
		}
		break;

		case 4100040:
		case 60208:
		case 60209:
		case 60210:
			IceBuffItem.use(pc, l1iteminstance);
			break;

        }
    }
}
