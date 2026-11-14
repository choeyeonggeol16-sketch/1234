package l1j.server.server.model.item.function;

import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class CItemUseSupplemental {
    private CItemUseSupplemental() {}

    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int I) {
        // A request must only use the instance validated by the packet entrypoint.
        // Do not accept a separately supplied itemId or look up a different item.
        if (pc == null || client == null || client.getActiveChar() != pc
                || l1iteminstance == null || l1iteminstance.getItem() == null
                || pc.getInventory().getItem(l1iteminstance.getId()) != l1iteminstance) {
            return;
        }
        final int itemId = l1iteminstance.getItem().getItemId();

		int l = 0;

		// TODO 아이템번호 추가해야한다 아니면 사용이안됨
		switch (itemId) {
		case 300001:
		case 3000393:
		case 40964: // 흑마법가루
		case 41036: // 일기 제작 대상 아이템....
		case 60035: // 룬 마력제거제
		case 3000148:
		case 410094: // 마력의 숨결: target is the sealed rune
		case 210082: // 균열의 핵: target is the item to transform
		case 40925: // target earring
		case 40926:
		case 40927:
		case 40928:
		case 40929:

		case 719:
		case 707:
		case 706:
		case 705:
		case 704:
		case 703:
		case 698:
		case 776:
		case 474747:
		case 400001:
		case 500001:
		case 600001:
		case 700001:
			l = I;
			break;
		default:
			break;
		}


		if (itemId >= 40033 && itemId <= 40038) {
			int currentElixirCount = pc.getElixirStats(); // 현재까지 먹은 엘릭서 개수
			int pcLevel = pc.getLevel();
			int maxAllowed = 0;

			// 레벨별 허용 개수 계산
			if (pcLevel >= 80) maxAllowed = 6;
			else if (pcLevel >= 75) maxAllowed = 5;
			else if (pcLevel >= 70) maxAllowed = 4;
			else if (pcLevel >= 65) maxAllowed = 3;
			else if (pcLevel >= 60) maxAllowed = 2;
			else if (pcLevel >= 55) maxAllowed = 1;

			// 1. 최소 레벨 미달 체크
			if (pcLevel < 55) {
				SC_SystemMessage.send(pc, "엘릭서는 55레벨부터 복용 가능합니다.");
				return;
			}

			// 2. 현재 레벨에서 먹을 수 있는 개수 초과 체크
			if (currentElixirCount >= maxAllowed) {
				SC_ServerMessage.send(pc, 4472); 
				return;
			}

			// 3. 전체 최대 개수(6개) 이미 달성 체크
			if (currentElixirCount >= 6) {
				SC_SystemMessage.send(pc, "엘릭서는 최대 6개까지만 복용 가능합니다.");
				return;
			}
		}

		L1ItemInstance l1iteminstance1 = l == 0 ? null : pc.getInventory().getItem(l);

		// Targeted earrings must be a live inventory object, not a null or stale id.
		if (itemId >= 40925 && itemId <= 40929
				&& (l1iteminstance1 == null || l1iteminstance1.getItem() == null
						|| pc.getInventory().getItem(l1iteminstance1.getId()) != l1iteminstance1
						|| l1iteminstance1 == l1iteminstance)) {
			SC_ServerMessage.send(pc, 79);
			return;
		}

		switch (itemId) {
		case 5200101:
		case 5200102:
		case 5200103:
		case 5990:
		case 820018:
		case 30106:
		case 3000356:
		case 3000363:
		case 3000364:
		case 3000365:
		case 753:
		case 40097:
		case 40119:
		case 140119:
		case 140329:
		case 31118:
		case 31119:
		case 31120:
		case 210083:
		case 474747:
		case 60035:
		case 410094:
		case 210082:
		case 40925:
		case 40926:
		case 40927:
		case 40928:
		case 40929:
			ItemUseCraftAndBuff.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
			break;
		case 3000106:
		case 777000:
		case 777001:
		case 777002:
		case 777003:
		case 5100135:
		case 70703:
		case 875640382:
		case 875640407:
		case 4100653:
		case 4100654:
		case 43321:
		case 32090:
		case 32005:
		case 32067:
		case 32063:
		case 32062:
		case 32087:
		case 32001:
		case 32002:
		case 32075:
		case 32058:
		case 32039:
		case 32038:
		case 32089:
		case 32064:
		case 32065:
		case 32029:
		case 32047:
		case 32060:
		case 32011:
		case 32061:
		case 32048:
		case 32066:
		case 32104:
		case 875640396:
		case 875640398:
		case 32105:
		case 32106:
		case 32107:
		case 3000354:
		case 3000394:
		case 3000396:
		case 3000395:
		case 40824:
		case 40825:
		case 43201:
		case 40826:
		case 40003:
		case 40566:
		case 40017:
		case 40507:
		case 40616:
		case 40782:
		case 40783:
		case 40700:
		case 41121:
		case 510012:
		case 510013:
		case 510014:
		case 41130:
		case 40692:
		case 41208:
		case 414100:
		case 414101:
		case 414102:
		case 40964:
		case 41036:
		case 30084:
		case 3000209:
		case 3000213:
		case 3000214:
		case 3000193:
		case 3000432:
		case 3000433:
		case 3000434:
		case 3000435:
		case 3000436:
		case 3000437:
		case 3000438:
		case 3000439:
		case 3000440:
		case 3000441:
		case 3000442:
		case 3000194:
		case 41299:
		case 41300:
		case 41303:
		case 41304:
		case 300001:
		case 700001:
		case 900001:
		case 400001:
		case 500001:
		case 600001:
		case 887799:
		case 887700:
			ItemUseWorldItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
			break;
		case 70701:
		case 70702:
		case 90905:
		case 80889:
		case 80805:
		case 80804:
		case 70716:
		case 70704:
		case 70705:
		case 70706:
		case 70707:
		case 70708:
		case 70733:
		case 70734:
		case 70709:
		case 70710:
		case 210122:
		case 50512:
		case 40228:
		case 40231:
		case 70711:
		case 70712:
		case 70713:
		case 70714:
		case 70715:
		case 70717:
		case 70718:
		case 70719:
		case 70722:
		case 44002:
		case 44001:
		case 70735:
		case 70730:
		case 220013:
		case 10889:
		case 10888:
		case 70731:
		case 70732:
		case 880800:
		case 707778:
		case 707780:
		case 707782:
		case 40271:
		case 40279:
		case 210008:
		case 210013:
		case 41147:
		case 70720:
		case 40224:
		case 4100100:
		case 70740:
		case 70741:
			ItemUseAdvancedAbilities.use(pc, l1iteminstance, itemId);
			break;
		case 3000195:
		case 474748:
		case 474750:
		case 474751:
		case 145001:
		case 145002:
		case 145003:
		case 145004:
		case 145005:
		case 145006:
		case 145007:
		case 145008:
		case 145009:
		case 145010:
		case 474752:
		case 4100203:
		case 4100140:
		case 4100141:
		case 4100142:
		case 4100143:
		case 4100144:
		case 40067:
		case 41414:
		case 40066:
		case 41413:
		case 111556:
		case 500220:
		case 1000010:
		case 1000110:
		case 1000120:
		case 410002:
		case 40735:
		case 40042:
		case 41404:
		case 41412:
		case 810011:
		case 3000048:
		case 40493:
		case 40328:
		case 3000150:
		case 5922794:
		case 41019:
		case 41027:
		case 490028:
		case 490029:
		case 3000175:
		case 45450:
		case 45451:
		case 45452:
		case 45453:
		case 12301:
		case 4100120:
		case 4100119:
		case 3000039:
		case 3000169:
		case 718:
		case 698:
		case 776:
		case 41354:
			ItemUseConsumableRewards.use(pc, l1iteminstance, itemId, l1iteminstance1);
			break;
		case 3000147:
		case 202099:
		case 3000229:
		case 875640424:
		case 875640415:
		case 3000413:
		case 41260:
		case 240100:
		case 748:
		case 731:
		case 3000397:
		case 3000398:
		case 749:
		case 650009:
		case 3000128:
		case 4100040:
		case 60208:
		case 60209:
		case 60210:
		case 700021:
		case 700000:
		case 4100074:
		case 4100075:
		case 40033:
		case 40034:
		case 40035:
		case 40036:
		case 40037:
		case 40038:
		case 3000126:
		case 3000127:
		case 3000123:
		case 40124:
		case 30086:
		case MJINNHelper.INN_KEYID:
		case 60256:
		case 89136:
		case 875640417:
		case 875640450:
		case 123124:
		case 12302:
		case 210104:
		case 700023:
			ItemUseTravelAndRecovery.use(client, pc, l1iteminstance, itemId);
			break;

		}
	}
}
