package l1j.server.server.model.item.Card;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.DelaySender;
import l1j.server.server.utils.MessageUtil;

/**
 * 신화/유일 카드 합성 시스템 
 */
public class L1CardAlchemy3 {
	private static final Random _random = new Random(System.nanoTime());

	// 카드 ID별 계정 데이터 차감 매핑 (4장 통합 처리용)
	private static final Map<Integer, Consumer<Account>> _cardConsumers = new HashMap<>();

	static {
		_cardConsumers.put(201, acc -> acc.setInfo_class_poly(acc.getInfo_class_poly() - 1));
		_cardConsumers.put(202, acc -> acc.setInfo_ki_poly(acc.getInfo_ki_poly() - 1));
		_cardConsumers.put(203, acc -> acc.setInfo_el_poly(acc.getInfo_el_poly() - 1));
		_cardConsumers.put(204, acc -> acc.setInfo_wi_poly(acc.getInfo_wi_poly() - 1));
		_cardConsumers.put(205, acc -> acc.setInfo_da_poly(acc.getInfo_da_poly() - 1));
		_cardConsumers.put(206, acc -> acc.setInfo_wa_poly(acc.getInfo_wa_poly() - 1));
		_cardConsumers.put(207, acc -> acc.setInfo_drag_poly(acc.getInfo_drag_poly() - 1));
		_cardConsumers.put(208, acc -> acc.setInfo_il_poly(acc.getInfo_il_poly() - 1));
		_cardConsumers.put(209, acc -> acc.setInfo_dsn_poly(acc.getInfo_dsn_poly() - 1));
		_cardConsumers.put(210, acc -> acc.setInfo_bapo_poly(acc.getInfo_bapo_poly() - 1));
		_cardConsumers.put(211, acc -> acc.setmyth01(acc.getmyth01() - 1));
	}

	public static boolean MythAlchemy(L1PcInstance pc) {
		if (pc == null || pc.getAccount() == null) return false;

		Account acc = pc.getAccount();
		int[] selectedCards = { acc.getInfo_card_a(), acc.getInfo_card_b(), acc.getInfo_card_c(), acc.getInfo_card_d() };

		// 유효성 체크 
		for (int cardId : selectedCards) {
			if (cardId == 0) {
				SC_SystemMessage.send(pc, "합성에 필요한 카드가 부족합니다.");
				return false;
			}
		}

		if (!(pc.getInventory().checkItem(40308, Config.myth_cost))) {
			SC_SystemMessage.send(pc, "합성에 필요한 아데나가 부족합니다.");
			return false;
		}

		if (!pc.getMap().isSafetyZone(pc.getLocation())) {
			SC_SystemMessage.send(pc, "안전한 지역에서만 사용할 수 있습니다.");
			return false;
		}

		// 재료 소모
		pc.getInventory().consumeItem(40308, Config.myth_cost);
		for (int cardId : selectedCards) {
			Consumer<Account> consumer = _cardConsumers.get(cardId);
			if (consumer != null) consumer.accept(acc);
		}

		acc.updateMyth();
		processSynthesis(pc);
		return true;
	}

	private static void processSynthesis(L1PcInstance pc) {
		int chance = Config.sole_per + (pc.신화합성확률증가 ? Config.sole_per_potion : 0);
		int rnd = _random.nextInt(100) + 1;
		boolean isUniqueSuccess = rnd <= chance;

		if (pc.isGm()) SC_SystemMessage.send(pc, "신화 합성 확률: " + chance + "% (주사위: " + rnd + ")");

		// 확률 증가 초기화
		pc.신화합성확률증가 = false;

		// 결과 카드 결정
		int rx = _random.nextInt(11) + 1;
		if (isUniqueSuccess) {
			rx = 1; // 기르타스 고정
		} else if (rx == 1) {
			rx = 11; // 실패 시 기르타스 제외
		}

		ResultCard result = getResultCard(pc.get_sex(), isUniqueSuccess, rx);

		// 카드 정보 초기화 및 저장
		Account acc = pc.getAccount();
		acc.setInfo_card_a(0); acc.setInfo_card_b(0); acc.setInfo_card_c(0); acc.setInfo_card_d(0);
		acc.updatecardpu();

		// 아이템 지급 및 이펙트 처리
		pc.getInventory().storeItem(result.itemId, 1);

		if (isUniqueSuccess) {
			SC_SkillSound.send(pc, pc.getId(), 21175);
			SC_DisplayEffect.send(pc, 4);
			broadcastUniqueSuccess(pc);
		} else {
			SC_SkillSound.send(pc, pc.getId(), 21128);
			SC_DisplayEffect.send(pc, 7);
			SC_DisplayEffect.send(pc, 22);
		}

		// 최종 UI 알림 
		sendResultUI(pc, result, isUniqueSuccess, rx);
	}

	private static ResultCard getResultCard(int sex, boolean isSuccess, int rx) {
		if (isSuccess) {
			return new ResultCard("2857", "[유일]지배자 기르타스", 875640295);
		}

		// 신화 풀 (rx 1~11 매핑)
		if (sex == 0) { // 남
			switch (rx) {
			case 1: case 10: return new ResultCard("145", "[신화]군주", 875640296);
			case 2: return new ResultCard("2859", "[신화]기사", 875640297);
			case 3: return new ResultCard("2863", "[신화]요정", 875640298);
			case 4: return new ResultCard("2867", "[신화]마법사", 875640299);
			case 5: return new ResultCard("2871", "[신화]다크엘프", 875640300);
			case 6: return new ResultCard("2875", "[신화]광전사", 875640301);
			case 7: return new ResultCard("2877", "[신화]투사", 875640303);
			case 8: return new ResultCard("141", "[신화]도플갱어", 875640302);
			case 9: return new ResultCard("149", "[신화]데스나이트", 875640304);
			case 11: return new ResultCard("71", "[신화]총사", 875640380);
			default: return new ResultCard("145", "[신화]군주", 875640296);
			}
		} else { // 여
			switch (rx) {
			case 1: case 10: return new ResultCard("147", "[신화]군주", 875640296);
			case 2: return new ResultCard("2861", "[신화]기사", 875640297);
			case 3: return new ResultCard("2865", "[신화]요정", 875640298);
			case 4: return new ResultCard("2869", "[신화]마법사", 875640299);
			case 5: return new ResultCard("2873", "[신화]다크엘프", 875640300);
			case 6: return new ResultCard("2875", "[신화]전사", 875640301);
			case 7: return new ResultCard("2877", "[신화]용기사", 875640303);
			case 8: return new ResultCard("143", "[신화]도플갱어", 875640302);
			case 9: return new ResultCard("149", "[신화]데스나이트", 875640304);
			case 11: return new ResultCard("73", "[신화]총사", 875640380);
			default: return new ResultCard("147", "[신화]군주", 875640296);
			}
		}
	}

	private static void sendResultUI(L1PcInstance pc, ResultCard result, boolean isSuccess, int rx) {
		ArrayList<String> args = new ArrayList<>();
		Account acc = pc.getAccount();
		int sex = pc.get_sex();

		args.add(result.cardId);
		args.add("174"); args.add("174"); args.add("174"); // 패딩

		// 소지 상태에 따른 아이콘 
		if (sex == 0) {
			args.add(acc.getInfo_class_poly() < 2 ? "144" : "145");
			args.add(acc.getInfo_ki_poly() < 2 ? "2858" : "2859");
			args.add(acc.getInfo_el_poly() < 2 ? "2862" : "2863");
			args.add(acc.getInfo_wi_poly() < 2 ? "2866" : "2867");
			args.add(acc.getInfo_da_poly() < 2 ? "2870" : "2871");
			args.add(acc.getInfo_wa_poly() < 2 ? "2874" : "2875");
			args.add(acc.getInfo_drag_poly() < 2 ? "2876" : "2877");
			args.add(acc.getInfo_il_poly() < 2 ? "140" : "141");
			args.add(acc.getInfo_dsn_poly() < 2 ? "148" : "149");
			args.add(acc.getInfo_bapo_poly() < 2 ? "150" : "151");
		} else {
			args.add(acc.getInfo_class_poly() < 2 ? "146" : "147");
			args.add(acc.getInfo_ki_poly() < 2 ? "2860" : "2861");
			args.add(acc.getInfo_el_poly() < 2 ? "2864" : "2865");
			args.add(acc.getInfo_wi_poly() < 2 ? "2868" : "2869");
			args.add(acc.getInfo_da_poly() < 2 ? "2872" : "2873");
			args.add(acc.getInfo_wa_poly() < 2 ? "2874" : "2875");
			args.add(acc.getInfo_drag_poly() < 2 ? "2876" : "2877");
			args.add(acc.getInfo_il_poly() < 2 ? "142" : "143");
			args.add(acc.getInfo_dsn_poly() < 2 ? "148" : "149");
			args.add(acc.getInfo_bapo_poly() < 2 ? "150" : "151");
		}

		// 수집 갯수 텍스트
		int[] counts = { 
				acc.getInfo_class_poly(), acc.getInfo_ki_poly(), acc.getInfo_el_poly(), acc.getInfo_wi_poly(),
				acc.getInfo_da_poly(), acc.getInfo_wa_poly(), acc.getInfo_drag_poly(), acc.getInfo_il_poly(),
				acc.getInfo_dsn_poly(), acc.getInfo_bapo_poly() 
		};
		for (int c : counts) args.add(c == 0 ? "[ 0장 ]" : "[ " + c + "장 ]");

		args.add("174");
		args.add(Config.myth_cost + "원");

		// 기르타스 소지 정보
		if (sex == 0) args.add(acc.getmyth01() < 2 ? "70" : "71");
		else args.add(acc.getmyth01() < 2 ? "72" : "73");
		args.add(acc.getmyth01() == 0 ? "[ 0장 ]" : "[ " + acc.getmyth01() + "장 ]");

		SC_NPCTalkReturn.sendList(pc, pc.getId(), "2007hallo1", args);

		// 개인 알림 및 메시지
		if (isSuccess && rx == 1) {
			DelaySender.sendGreen(pc, result.name + " 카드를 획득하였습니다.", 2300L);
		} else {
			SC_SkillSound.send(pc, pc.getId(), 7976);
			SC_SkillSound.broadcast(pc, pc.getId(), 7976);
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "" + result.name + " 카드를 획득하였습니다.");
		}
		SC_ChatPacket.sendDirect(pc, pc, result.name + " 카드를 획득하였습니다.", 1);
	}

	private static void broadcastUniqueSuccess(L1PcInstance pc) {
		ThreadPool.getInstance().scheduleRequired(() -> {
			String msg = "어느 아덴 용사 님께서 [유일]지배자 기르타스 카드 합성에 성공하였습니다.";
			MessageUtil.toAllPlayers(msg);
			MessageUtil.greenMessageToAllPlayers(msg);
		}, 2300L);
	}

	private static class ResultCard {
		String cardId, name;
		int itemId;
		ResultCard(String cardId, String name, int itemId) {
			this.cardId = cardId; this.name = name; this.itemId = itemId;
		}
	}
}