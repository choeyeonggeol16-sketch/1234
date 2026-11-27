package l1j.server.server.model.item.function;

import l1j.server.server.shared.GameRandom;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1NeigeItemBox;
import l1j.server.server.model.CWorld;
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

final class WorldTravelScrollItems {
    private WorldTravelScrollItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
case 3000106:// 수련던전1층이동주문서
			if (pc.getLevel() >= 90) {
				SC_SystemMessage.send(pc, "해당 던전은 75까지만 입장 가능합니다.");
				return;
			}
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 32809 + rx;
				int uy = 32727 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 25, pc.getHeading(), 169, true,  pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 3000354:// 에바의왕국 수중던전
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 32734 + rx;
				int uy = 32841 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 63, pc.getHeading(), 169, true, false);
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 3000394:// 화룡의 둥지
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 33713 + rx;
				int uy = 32301 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 4, pc.getHeading(), 169, true,  pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 3000396:// 풍룡의 둥지
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 34180 + rx;
				int uy = 32857 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 3000395:// 발라카스 둥지
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 32779 + rx;
				int uy = 32751 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 2210, pc.getHeading(), 169, true, pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 40824:// 수련던전2층이동주문서
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 32807 + rx;
				int uy = 32747 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 26, pc.getHeading(), 169, true, pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 40825:// 수련던전3층이동주문서
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 32810 + rx;
				int uy = 32765 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 27, pc.getHeading(), 169, true, pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 43201: // 클라우디아 마을 부적
			if (pc.getLevel() >= 56) {
				SC_SystemMessage.send(pc, "레벨 55 이하만 사용이 가능합니다.");
				return;
			}
			pc.start_teleport(32649, 32865, 7783, pc.getHeading(), 169, true, pc.getAutoHunt());
			//			pc.start_teleport(l1iteminstance.getItem().get_locx(), l1iteminstance.getItem().get_locy(), l1iteminstance.getItem().get_mapid(), pc.getHeading(), 169, true, false);
			break;
		
case 40826:// 수련던전4층이동주문서
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ry = GameRandom.nextInt(2);
				int ux = 32799 + rx;
				int uy = 32798 + ry;
				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 28, pc.getHeading(), 169, true, pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		
case 40616:
		case 40782:
		case 40783:// 그림자의 신전 3층의 열쇠
			if ((pc.getX() >= 32698 && pc.getX() <= 32702) && (pc.getY() >= 32894 && pc.getY() <= 32898)
					&& pc.getMapId() == 523) {
				pc.start_teleport(l1iteminstance.getItem().get_locx(), l1iteminstance.getItem().get_locy(),
						l1iteminstance.getItem().get_mapid(), 5, 169, true, pc.getAutoHunt());
			} else {
				SC_ServerMessage.send(pc, 79);
			}
			break;
		
case 40692:// 완성된 보물의 지도
			if (pc.getInventory().HaveItemByClassId(40621)) {
				// \f1 아무것도 일어나지 않았습니다.
				SC_ServerMessage.send(pc, 79);
			} else if ((pc.getX() >= 32856 && pc.getX() <= 32858) && (pc.getY() >= 32857 && pc.getY() <= 32858)
					&& pc.getMapId() == 443) { // 해적섬의
				pc.start_teleport(l1iteminstance.getItem().get_locx(), l1iteminstance.getItem().get_locy(),
						l1iteminstance.getItem().get_mapid(), 5, 169, true, pc.getAutoHunt());
			} else {
				SC_ServerMessage.send(pc, 79);
			}
			break;
		
case 41208:// 사그러지는 영혼
			if ((pc.getX() >= 32844 && pc.getX() <= 32845) && (pc.getY() >= 32693 && pc.getY() <= 32694)
					&& pc.getMapId() == 550) {
				pc.start_teleport(l1iteminstance.getItem().get_locx(), l1iteminstance.getItem().get_locy(),
						l1iteminstance.getItem().get_mapid(), 5, 169, true, pc.getAutoHunt());
			} else {
				SC_ServerMessage.send(pc, 79);
			}
			break;
		
case 3000193:// 요던1층
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = GameRandom.nextInt(2);
				int ux = 32805 + rx;
				int uy = 32724 + rx;

				ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 19, pc.getHeading(), 169, true, pc.getAutoHunt());
					CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			}
			break;
		
        }
    }
}
