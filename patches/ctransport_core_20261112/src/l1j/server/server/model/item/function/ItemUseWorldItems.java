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
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;

final class ItemUseWorldItems {

    private ItemUseWorldItems() {}

    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 3000106:// 수련던전1층이동주문서
			if (pc.getLevel() >= 90) {
				SC_SystemMessage.send(pc, "해당 던전은 75까지만 입장 가능합니다.");
				return;
			}
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 32809 + rx;
				int uy = 32727 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 25, pc.getHeading(), 169, true,  pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		case 777000:
			ItemUseDurable.UseDurableExchange(pc, l1iteminstance, 555000);
			break;
		case 777001:
			ItemUseDurable.UseDurableExchange(pc, l1iteminstance, 555001);
			break;
		case 777002:
			ItemUseDurable.UseDurableExchange(pc, l1iteminstance, 555002);
			break;
		case 777003:
			ItemUseDurable.UseDurableExchange(pc, l1iteminstance, 555003);
			break;
		case 5100135:
			ItemUseDurable.UseDurableTowerCharm(pc, l1iteminstance);
			break;
		case 70703:
			ItemUseDurable.UseDurableRevive(pc, 70703, 70702, "기억의 수정(본브레이크:래스트) (소생) 8장이 필요합니다.", "기억의 수정(본브레이크:래스트) 획득하였습니다.");
			break;
		case 875640382:
			L1NeigeItemBox.lowbox(pc, l1iteminstance);
			break;
		case 875640407:
			L1NeigeItemBox.herobox(pc, l1iteminstance);
			break;
		case 4100653:
			ItemUseDurable.UseDurableRevive(pc, 4100653, 68076, "고대의 주문서:무기(소생) 8장이 필요합니다.", "고대의 주문서:무기를 획득하였습니다.");
			break;
		case 4100654:
			ItemUseDurable.UseDurableRevive(pc, 4100654, 68077, "고대의 주문서:방어구(소생) 8장이 필요합니다.", "고대의 주문서:방어구를 획득하였습니다.");
			break;
		case 43321:
			ItemUseDurable.UseDurableRevive(pc, 43321, 900167, "머미로드의 반지 (소생) 8개가 필요합니다.", "머미로드의 반지를 획득하였습니다.");
			break;
		case 32090:
			ItemUseDurable.UseDurableRevive(pc, 32090, 900163, "리치의 반지 (소생) 8개가 필요합니다.", "리치의 반지를 획득하였습니다.");
			break;
		case 32005:
			ItemUseDurable.UseDurableRevive(pc, 32005, 210125, "전사의 인장:데스페라도 (소생) 8개가 필요합니다.", "전사의 인장:데스페라도를 획득하였습니다.");
			break;
		case 32067:
			ItemUseDurable.UseDurableRevive(pc, 32067, 222317, "격분의 장갑 (소생) 8개가 필요합니다.", "격분의 장갑을 획득하였습니다.");
			break;
		case 32063:
			ItemUseDurable.UseDurableRevive(pc, 32063, 900158, "우그누스의 가더 (소생) 8개가 필요합니다.", "우그누스의 가더를 획득하였습니다.");
			break;
		case 32062:
			ItemUseDurable.UseDurableRevive(pc, 32062, 900157, "쿠거의 가더 (소생) 8개가 필요합니다.", "쿠거의 가더를 획득하였습니다.");
			break;
		case 32087:
			ItemUseDurable.UseDurableRevive(pc, 32087, 900075, "신화변신지배반지 (소생) 8개가 필요합니다.", "변신 지배 반지 획득하였습니다.");
			break;
		case 32001:
			ItemUseDurable.UseDurableRevive(pc, 32001, 40222, "마법서 (디스인티그레이트) (소생) 8장이 필요합니다.", "마법서(디스인티그레이트) 획득하였습니다.");
			break;
		case 32002:
			ItemUseDurable.UseDurableRevive(pc, 32002, 41148, "기술서 (카운터 배리어) (소생) 8장이 필요합니다.", "기술서 (카운터 배리어) 획득하였습니다.");
			break;
		case 32075:
			ItemUseDurable.UseDurableRevive(pc, 32075, 20279, "라이아의 반지 (소생) 8개가 필요합니다.", "라이아의 반지를 획득하였습니다.");
			break;
		case 32058:
			ItemUseDurable.UseDurableRevive(pc, 32058, 900019, "실프의 티셔츠 (소생) 8개가 필요합니다.", "실프의 티셔츠를 획득하였습니다.");
			break;
		case 32039:
			ItemUseDurable.UseDurableRevive(pc, 32039, 22263, "반역자의 방패 (소생) 8개가 필요합니다.", "반역자의 방패를 획득하였습니다.");
			break;
		case 32038:
			ItemUseDurable.UseDurableRevive(pc, 32038, 22214, "시어의 심안 (소생) 8개가 필요합니다.", "시어의 심안을 획득하였습니다.");
			break;
		case 32089:
			ItemUseDurable.UseDurableRevive(pc, 32089, 900162, "사신의 반지 (소생) 8개가 필요합니다.", "사신의 반지를 획득하였습니다.");
			break;
		case 32064:
			ItemUseDurable.UseDurableRevive(pc, 32064, 900159, "나이트발드의 각반 (소생) 8개가 필요합니다.", "나이트발드의 각반을 획득하였습니다.");
			break;
		case 32065:
			ItemUseDurable.UseDurableRevive(pc, 32065, 900160, "아이리스의 각반 (소생) 8개가 필요합니다.", "아이리스의 각반을 획득하였습니다.");
			break;
		case 32029:
			ItemUseDurable.UseDurableRevive(pc, 32029, 20107, "리치의 로브 (소생) 8개가 필요합니다.", "리치의 로브를 획득하였습니다.");
			break;
		case 32047:
			ItemUseDurable.UseDurableRevive(pc, 32047, 20017, "머미로드의 왕관 (소생) 8개가 필요합니다.", "머미로드의 왕관을 획득하였습니다.");
			break;
		case 32060:
			ItemUseDurable.UseDurableRevive(pc, 32060, 900155, "아이리스의 부츠 (소생) 8개가 필요합니다.", "아이리스의 부츠를 획득하였습니다.");
			break;
		case 32011:
			ItemUseDurable.UseDurableRevive(pc, 32011, 4100102, "카운터 배리어:베테랑 (소생) 8개가 필요합니다.", "카운터 배리어:베테랑을 획득하였습니다.");
			break;
		case 32061:
			ItemUseDurable.UseDurableRevive(pc, 32061, 900156, "머미로드의 장갑 (소생) 8개가 필요합니다.", "머미로드의 장갑을 획득하였습니다.");
			break;
		case 32048:
			ItemUseDurable.UseDurableRevive(pc, 32048, 22360, "지휘관의 투구 (소생) 8개가 필요합니다.", "지휘관의 투구를 획득하였습니다.");
			break;
		case 32066:
			ItemUseDurable.UseDurableRevive(pc, 32066, 900161, "뱀파이어의 각반 (소생) 8개가 필요합니다.", "뱀파이어의 각반을 획득하였습니다.");
			break;
		case 32104:
			ItemUseDurable.UseDurableRevive(pc, 32104, 40346, "안타라스의 숨결 (소생) 8개가 필요합니다.", "안타라스의 숨결을 획득하였습니다.");
			break;
		case 875640396:
			ItemUseDurable.UseDurableRevive(pc, 875640396, 875640381, "전설 무기 제작 비법서 (소생) 8개가 필요합니다.", "전설 무기 제작 비법서를 획득하였습니다.");
			break;
		case 875640398:
			ItemUseDurable.UseDurableRevive(pc, 875640398, 875640397, "신화 무기 제작 비법서 (소생) 8개가 필요합니다.", "신화 무기 제작 비법서를 획득하였습니다.");
			break;
		case 32105:
			ItemUseDurable.UseDurableRevive(pc, 32105, 40354, "발라카스의 숨결 (소생) 8개가 필요합니다.", "발라카스의 숨결을 획득하였습니다.");
			break;
		case 32106:
			ItemUseDurable.UseDurableRevive(pc, 32106, 40362, "파푸리온의 숨결 (소생) 8개가 필요합니다.", "파푸리온의 숨결을 획득하였습니다.");
			break;
		case 32107:
			ItemUseDurable.UseDurableRevive(pc, 32107, 40370, "린드비오르의 숨결 (소생) 8개가 필요합니다.", "린드비오르의 숨결을 획득하였습니다.");
			break;
		case 3000354:// 에바의왕국 수중던전
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 32734 + rx;
				int uy = 32841 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 63, pc.getHeading(), 169, true, false);
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		case 3000394:// 화룡의 둥지
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 33713 + rx;
				int uy = 32301 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 4, pc.getHeading(), 169, true,  pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		case 3000396:// 풍룡의 둥지
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 34180 + rx;
				int uy = 32857 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		case 3000395:// 발라카스 둥지
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 32779 + rx;
				int uy = 32751 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 2210, pc.getHeading(), 169, true, pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		case 40824:// 수련던전2층이동주문서
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 32807 + rx;
				int uy = 32747 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 26, pc.getHeading(), 169, true, pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		case 40825:// 수련던전3층이동주문서
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 32810 + rx;
				int uy = 32765 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 27, pc.getHeading(), 169, true, pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
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
		case 40003: // 랜턴오일
            ItemUseDurableGoods.useDurableLanternOil(pc, l1iteminstance);
            break;
		case 40566: { // 신비한 소라 껍데기

		    if (pc.isElf()
		            && pc.getX() >= 33971
		            && pc.getX() <= 33975
		            && pc.getY() >= 32324
		            && pc.getY() <= 32328
		            && pc.getMapId() == 4
		            && !pc.getInventory().checkItem(40548)) { // 망령의 봉투

		        final L1NpcInstance npc =
		                L1World.getInstance()
		                        .findNpcByNpcId(45300);

		        if (npc instanceof L1MonsterInstance) {

		            SC_ServerMessage.send(pc, 79); // \f1 아무것도

		        } else {

		            L1SpawnUtil.spawn(
		                    pc,
		                    45300,
		                    0,
		                    0); // 고대인의 망령
		        }

		    } else {

		        SC_ServerMessage.send(pc, 79); // \f1 아무것도
		    }

		    break;
		}		case 40826:// 수련던전4층이동주문서
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ry = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 32799 + rx;
				int uy = 32798 + ry;
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 28, pc.getHeading(), 169, true, pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			} else {
				SC_ServerMessage.send(pc, 647);
				ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			}
			break;
		case 40017:// 비취 물약
		case 40507:// 엔트의 줄기
			if (pc.hasSkillEffect(71)) { // 디케이포션 상태
				SC_ServerMessage.send(pc, 698);
			} else {
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					ItemUseCreature.cancelAbsoluteBarrier(pc);
					SC_SkillSound.send(pc, pc.getId(), 192);
					SC_SkillSound.broadcast(pc, pc.getId(), 192);
					pc.curePoison();
				});
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
		case 40700: { // 실버 플룻

		    SC_Sound.send(pc, 10);
		    SC_Sound.broadcast(pc, 10);

		    if (pc.getX() >= 32619
		            && pc.getX() <= 32623
		            && pc.getY() >= 33120
		            && pc.getY() <= 33124
		            && pc.getMapId() == 440) {

		        final L1NpcInstance npc =
		                L1World.getInstance()
		                        .findNpcByNpcId(45875);

		        if (!(npc instanceof L1MonsterInstance)) {
		            L1SpawnUtil.spawn(
		                    pc,
		                    45875,
		                    0,
		                    0);
		        }
		    }

		    break;
		}
		case 41121:// 카헬의 계약서
			if (pc.getQuest().get_step(L1Quest.QUEST_SHADOWS) == L1Quest.QUEST_END
			|| pc.getInventory().checkItem(41122, 1)) {
				SC_ServerMessage.send(pc, 79);
			} else {
				ItemUseCreature.createNewItem(pc, 41122, 1);
			}
			break;
		case 510012:// 미소피아의 성장 축복
		case 510013:
		case 510014: {
			final int blessingSkill = itemId == 510012 ? L1SkillId.miso1
					: itemId == 510013 ? L1SkillId.miso2 : L1SkillId.miso3;
			ItemUseDurable.UseDurableConsume(pc, l1iteminstance,
					() -> L1SkillUse.cast(pc, blessingSkill, pc.getId(), pc.getX(), pc.getY(),
							null, 0, L1SkillUse.TYPE_GMBUFF));
			break;
		}
		case 41130:// 혈흔의 계약서
			if (pc.getQuest().get_step(L1Quest.QUEST_DESIRE) == L1Quest.QUEST_END
			|| pc.getInventory().checkItem(41131, 1)) {
				SC_ServerMessage.send(pc, 79);
			} else {
				ItemUseCreature.createNewItem(pc, 41131, 1);
			}
			break;
		case 40692:// 완성된 보물의 지도
			if (pc.getInventory().checkItem(40621)) {
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
		case 414100:
            CS_Attr.resetKillDeath(pc, l1iteminstance, true, false);
            break;
        case 414101:
            CS_Attr.resetKillDeath(pc, l1iteminstance, false, true);
            break;
        case 414102:
            CS_Attr.resetKillDeath(pc, l1iteminstance, true, true);
            break;

		case 40964:
		case 41036:
			CItemUse.useTargetedPurification(pc, l1iteminstance, l1iteminstance1);
			break;
		case 30084:// 상아탑의 비취 물약
			if (pc.hasSkillEffect(71)) { // 디케이포션 상태
				SC_ServerMessage.send(pc, 698);
			} else {
				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					ItemUseCreature.cancelAbsoluteBarrier(pc);
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
		case 3000193:// 요던1층
			if (pc.getMap().isEscapable() || pc.isGm()) {
				int rx = java.util.concurrent.ThreadLocalRandom.current().nextInt(2);
				int ux = 32805 + rx;
				int uy = 32724 + rx;

				ItemUseDurable.UseDurableConsume(pc, l1iteminstance, () -> {
					pc.start_teleport(ux, uy, 19, pc.getHeading(), 169, true, pc.getAutoHunt());
					ItemUseCreature.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
				});
			}
			break;
		case 3000432:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 46043);
			break;
		case 3000433:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 46045);
			break;
		case 3000434:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 46046);
			break;
		case 3000435:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45695);
			break;
		case 3000436:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45693);
			break;
		case 3000437:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45692);
			break;
		case 3000438:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45694);
			break;
		case 3000439:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45696);
			break;
		case 3000440:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45697);
			break;
		case 3000441:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45712);
			break;
		case 3000442:
			ItemUseDurable.UsePetAmulet(client, l1iteminstance, 45710);
			break;
		case 3000194:
		case 41299:
		case 41300:
		case 41303:
		case 41304:
			CItemUse.useRandomMoneyBox(pc, l1iteminstance);
			break;

		case 300001:
			ItemUseDollPotential.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		case 700001:
			ItemUseDollPotential.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		case 900001:

			if (!pc.getInventory().checkItem(900001, 100)) {
				SC_SystemMessage.send(pc, "잠재력 강화의 비약이 부족합니다.");
				SC_SystemMessage.send(pc, "100개 모을 시 [잠재력 강화]아이템으로 제작됩니다.");
				return;
			}
			ItemUseDurable.UseDurableCraft(pc, 900001, 100, 300001,
					() -> SC_ChatPacket.sendDirect(pc, pc, "잠재력 강화 아이템을 제작하었습니다."));
			break;
		case 400001:
			ItemUseDollPotential.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		case 500001:
			ItemUseDollPotential.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		case 600001:
			ItemUseDollPotential.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		case 887799:
			ItemUseDurable.UseDurableExchange(pc, l1iteminstance, pc.getType() == 1 ? 41148 : pc.getType() == 3 ? 40222 : pc.getType() == 4 ? 5559 : pc.getType() == 7 ? 210125 : 41148);
			break;
		case 887700:
			ItemUseDurable.UseDurableExchange(pc, l1iteminstance, pc.getType() == 1 ? 4100102 : pc.getType() == 4 ? 4100105 : pc.getType() == 7 ? 4100112 : 4100102);
			break;


        }
    }
}
