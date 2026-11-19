package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.rank.business.RankBusiness;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class CraftingSupportAndTravelItems {
    private CraftingSupportAndTravelItems() {}
    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        int[] loc = null;
        switch (itemId) {
		case 820018:
			SC_NPCTalkReturn.send(pc, pc.getId(), "pureelixir");// 액션창
			break;
			/** 오만의탑 혼돈부적,변이된 부적 **/

		case 30106: {// 숨겨진 계곡 마을 귀환 부적
			loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_HIDDEN_VALLEY);
			pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true,  pc.getAutoHunt());
		}
		break;

		case 3000356:
		case 3000363:
		case 3000364:
		case 3000365:// 장비교환권
			SC_SystemMessage.send(pc, "메티스 에게 전달하세요.");
			break;

		case 410094: { // 마력의 숨결: consume scroll + selected sealed rune atomically
			if (l1iteminstance1 == null || l1iteminstance1 == l1iteminstance
					|| pc.getInventory().getItem(l1iteminstance1.getId()) != l1iteminstance1)
				return;
			final int rewardId = switch (l1iteminstance1.getItemId()) {
				case 410114 -> 22232;
				case 410115 -> 22233;
				case 410116 -> 22234;
				case 410117 -> 22235;
				case 410118 -> 22236;
				case 410119 -> 22237;
				case 410109 -> 22238;
				case 410124 -> 22239;
				case 410110 -> 22240;
				case 410125 -> 22241;
				case 410111 -> 22242;
				case 410126 -> 22243;
				case 410112 -> 22244;
				case 410113 -> 22245;
				case 410120 -> 22246;
				case 410121 -> 22247;
				case 410122 -> 22248;
				case 410123 -> 22249;
				default -> -1;
			};
			if (rewardId < 0) {
				SC_ServerMessage.send(pc, 79);
				return;
			}
			final String unsealedName = l1iteminstance1.getItem().getName();
			ItemReceiptOperations.UseDurableTwoInput(pc, l1iteminstance, l1iteminstance1, rewardId,
					() -> SC_SystemMessage.send(pc, unsealedName + "의 봉인이 해제 되었습니다."));
			break;
		}

		case 210082: { // 균열의 핵: selected material and core must both commit
			if (l1iteminstance1 == null || l1iteminstance1 == l1iteminstance
					|| pc.getInventory().getItem(l1iteminstance1.getId()) != l1iteminstance1) {
				SC_ServerMessage.send(pc, 79);
				break;
			}
			final int resultId = switch (l1iteminstance1.getItemId()) {
				case 210075 -> 210076;
				case 210079 -> 210080;
				case 500208 -> 500202;
				case 500209 -> 500203;
				default -> -1;
			};
			if (resultId < 0) {
				SC_ServerMessage.send(pc, 79);
				break;
			}
			ItemReceiptOperations.UseDurableTwoInput(pc, l1iteminstance, l1iteminstance1, resultId, null);
			break;
		}

        }
    }
}
