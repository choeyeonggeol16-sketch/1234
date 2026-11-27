package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.TravelUseTables.*;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.CommonUtil;

/** Dedicated item-use handler extracted from a mixed domain. */
final class IslandReturnScrollUse {
    private IslandReturnScrollUse() {}

    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 3000147:// 테베대항전
            final int tebeX = 32769 + CommonUtil.random(-5, 5);
            final int tebeY = 32832 + CommonUtil.random(-5, 5);
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.start_teleport(tebeX, tebeY, 782, pc.getHeading(), 169, true, pc.getAutoHunt());
				SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\aA[알림]: \\aG[주의] 강력한 힘을 가지고 있는 몬스터가 있습니다.");
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			});
			break;

		case 202099: {// 클라우디아 마을
			// if (pc.get_DuelLine() != 0) {
			// pc.sendPackets(new SC_SystemMessage("배틀존지역에서 사용할 수 없습니다."));
			// return;
			// }
			// if (pc.getMap().isEscapable() || pc.isGm()) {
			// int[]
			loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_claudia);
			// L1Teleport.teleport(pc, loc[0], loc[1], (short) loc[2],
			// pc.getHeading(), true);
			pc.start_teleport(loc[0], loc[1], loc[2], pc.getHeading(), 169, true, pc.getAutoHunt());
			// }
		}
		break;

		case 3000229: {// 잊혀진 귀환 주문서
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (!(pc.getMapId() >= 1708 && pc.getMapId() <= 1709)) {
				SC_SystemMessage.send(pc, "잊혀진 섬/대기실 에서만 사용 가능합니다.");
				return;
			}
			int[] x = LOST_ISLAND_X;
			int[] y = LOST_ISLAND_Y;
			int type = GameRandom.nextInt(x.length);
			L1Location base = new L1Location(x[type], y[type], 1710);
			L1Location random = L1Location.randomLocation(base, 0, 3, true);
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(random.getX(), random.getY(), random.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
		}
		break;

		case 875640424:
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (!(pc.getMapId() >= 440 && pc.getMapId() <= 441)) {
				SC_SystemMessage.send(pc, "해적섬에서만 사용할 수 있습니다.");
				return;
			}
			int[] xa = PIRATE_INNER_XY;
			int[] ya = PIRATE_INNER_XY;
			int typea = GameRandom.nextInt(xa.length);
			L1Location basea = new L1Location(xa[typea], ya[typea], 441);
			L1Location randoma = L1Location.randomLocation(basea, 0, 3, true);
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(randoma.getX(), randoma.getY(), randoma.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
			break;

		case 875640415: {// 해적섬 귀환
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (!(pc.getMapId() >= 440 && pc.getMapId() <= 441)) {
				SC_SystemMessage.send(pc, "해적섬에서만 사용할 수 있습니다.");
				return;
			}
			int[] x = PIRATE_RETURN_X;
			int[] y = PIRATE_RETURN_Y;
			int type = GameRandom.nextInt(x.length);
			L1Location base = new L1Location(x[type], y[type], 440);
			L1Location random = L1Location.randomLocation(base, 0, 3, true);
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(random.getX(), random.getY(), random.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
		}
		break;

		case 3000413: {// 강제 귀환 주문서 (잊섬->기란)
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
				SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
				return;
			}
			if (pc.getMapId() >= 1708 && pc.getMapId() <= 1709) {

			} else {
				SC_SystemMessage.send(pc, "잊혀진 섬 필드 에서만 사용이 가능 합니다.");
				return;
			}
			int[] x = LOST_RETURN_X;
			int[] y = LOST_RETURN_Y;
			int type = GameRandom.nextInt(x.length);
			L1Location base = new L1Location(x[type], y[type], 4);
			L1Location random = L1Location.randomLocation(base, 0, 3, true);
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () ->
				pc.start_teleport(random.getX(), random.getY(), random.getMapId(),
					pc.getHeading(), 169, true, pc.getAutoHunt()));
		}
		break;

		case 240100:// 저주해진 텔레포트 스크롤(오리지날 아이템)
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, true, pc.getAutoHunt());
				pc.cancelAbsoluteBarrier(); // 아브소르트바리아의 해제
			});
			break;


        }
    }
}
