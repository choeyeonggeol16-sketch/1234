package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1NeigeItemBox;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;

final class WorldBuffAndCureItems {
    private WorldBuffAndCureItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
case 40017:// 비취 물약
		case 40507:// 엔트의 줄기
			if (pc.hasSkillEffect(71)) { // 디케이포션 상태
				SC_ServerMessage.send(pc, 698);
			} else {
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					CreatureItemActions.cancelAbsoluteBarrier(pc);
					SC_SkillSound.send(pc, pc.getId(), 192);
					SC_SkillSound.broadcast(pc, pc.getId(), 192);
					pc.curePoison();
				});
			}
			break;
		
case 510012:// 미소피아의 성장 축복
		case 510013:
		case 510014: {
			final int blessingSkill = itemId == 510012 ? L1SkillId.miso1
					: itemId == 510013 ? L1SkillId.miso2 : L1SkillId.miso3;
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance,
					() -> L1SkillUse.cast(pc, blessingSkill, pc.getId(), pc.getX(), pc.getY(),
							null, 0, L1SkillUse.TYPE_GMBUFF));
			break;
		}
		
case 30084:// 상아탑의 비취 물약
			if (pc.hasSkillEffect(71)) { // 디케이포션 상태
				SC_ServerMessage.send(pc, 698);
			} else {
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					CreatureItemActions.cancelAbsoluteBarrier(pc);
					SC_SkillSound.send(pc, pc.getId(), 192);
					SC_SkillSound.broadcast(pc, pc.getId(), 192);
					pc.curePoison();
				});
			}
			break;
		
case 3000209: // Retired legacy server auto-hunt tickets.
        case 3000213:
        case 3000214:
            pc.sendPackets("기존 서버 자동사냥은 제거되었습니다. 플레이서포터를 사용하세요.");
            break;
		
        }
    }
}
