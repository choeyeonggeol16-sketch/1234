package l1j.server.server.model.item.Card;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
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
	@SuppressWarnings("unchecked")
    private static final Consumer<Account>[] _cardConsumers =
            (Consumer<Account>[]) new Consumer[11];

    private static Consumer<Account> consumerFor(int cardId) {
        final int index = cardId - 201;
        return index >= 0 && index < _cardConsumers.length ? _cardConsumers[index] : null;
    }

    // Revalidate the collection at commit entry: a selected ID alone does not
    // prove the account still owns a spare copy (one copy is retained in collection).
    private static int copiesFor(Account acc, int cardId) {
        return switch (cardId) {
            case 201 -> acc.getInfo_class_poly();
            case 202 -> acc.getInfo_ki_poly();
            case 203 -> acc.getInfo_el_poly();
            case 204 -> acc.getInfo_wi_poly();
            case 205 -> acc.getInfo_da_poly();
            case 206 -> acc.getInfo_wa_poly();
            case 207 -> acc.getInfo_drag_poly();
            case 208 -> acc.getInfo_il_poly();
            case 209 -> acc.getInfo_dsn_poly();
            case 210 -> acc.getInfo_bapo_poly();
            case 211 -> acc.getmyth01();
            default -> 0;
        };
    }

    // Schema-backed whitelist. Indexed by card ID; never derived from packet text.
    private static final String[] CARD_COLUMNS = {
        "info_class_poly", "info_ki_poly", "info_el_poly", "info_wi_poly", "info_da_poly", "info_wa_poly",
        "info_drag_poly", "info_il_poly", "info_dsn_poly", "info_bapo_poly", "myth01"
    };

	static {
		_cardConsumers[0] = acc -> acc.setInfo_class_poly(acc.getInfo_class_poly() - 1);
		_cardConsumers[1] = acc -> acc.setInfo_ki_poly(acc.getInfo_ki_poly() - 1);
		_cardConsumers[2] = acc -> acc.setInfo_el_poly(acc.getInfo_el_poly() - 1);
		_cardConsumers[3] = acc -> acc.setInfo_wi_poly(acc.getInfo_wi_poly() - 1);
		_cardConsumers[4] = acc -> acc.setInfo_da_poly(acc.getInfo_da_poly() - 1);
		_cardConsumers[5] = acc -> acc.setInfo_wa_poly(acc.getInfo_wa_poly() - 1);
		_cardConsumers[6] = acc -> acc.setInfo_drag_poly(acc.getInfo_drag_poly() - 1);
		_cardConsumers[7] = acc -> acc.setInfo_il_poly(acc.getInfo_il_poly() - 1);
		_cardConsumers[8] = acc -> acc.setInfo_dsn_poly(acc.getInfo_dsn_poly() - 1);
		_cardConsumers[9] = acc -> acc.setInfo_bapo_poly(acc.getInfo_bapo_poly() - 1);
		_cardConsumers[10] = acc -> acc.setmyth01(acc.getmyth01() - 1);
	}

	public static boolean MythAlchemy(L1PcInstance pc) {
		if (pc == null || pc.getAccount() == null) return false;

		Account acc = pc.getAccount();
		final int cardA = acc.getInfo_card_a();
		final int cardB = acc.getInfo_card_b();
		final int cardC = acc.getInfo_card_c();
		final int cardD = acc.getInfo_card_d();

		// 기존 0 카드 검사 순서만 유지하고 반복 할당되는 int[4]는 제거한다.
		if (cardA == 0 || cardB == 0 || cardC == 0 || cardD == 0
                || consumerFor(cardA) == null || consumerFor(cardB) == null
                || consumerFor(cardC) == null || consumerFor(cardD) == null
                || copiesFor(acc, cardA) <= 1 || copiesFor(acc, cardB) <= 1
                || copiesFor(acc, cardC) <= 1 || copiesFor(acc, cardD) <= 1
                || cardA == cardB || cardA == cardC || cardA == cardD
                || cardB == cardC || cardB == cardD || cardC == cardD) {
			SC_SystemMessage.send(pc, "합성에 필요한 카드가 부족합니다.");
			return false;
		}

		if (!(pc.getInventory().checkItem(40308, Config.myth_cost))) {
			SC_SystemMessage.send(pc, "합성에 필요한 아데나가 부족합니다.");
			return false;
		}

		if (!pc.getMap().isSafetyZone(pc.getLocation())) {
			SC_SystemMessage.send(pc, "안전한 지역에서만 사용할 수 있습니다.");
			return false;
		}

        final int chance = Config.sole_per + (pc.신화합성확률증가 ? Config.sole_per_potion : 0);
        final int rnd = _random.nextInt(100) + 1;
        final boolean isUniqueSuccess = rnd <= chance;
        int selectedRx = _random.nextInt(11) + 1;
        if (isUniqueSuccess) selectedRx = 1;
        else if (selectedRx == 1) selectedRx = 11;
        final int rx = selectedRx;
        final ResultCard result = getResultCard(pc.get_sex(), isUniqueSuccess, rx);

        return CardAlchemyReceipt.submit(pc, acc, Config.myth_cost, result.itemId,
                201, CARD_COLUMNS, _cardConsumers, id -> copiesFor(acc, id),
                cardA, cardB, cardC, cardD, 4,
                () -> processSynthesis(pc, result, chance, rnd, isUniqueSuccess, rx));
    }

    private static void processSynthesis(L1PcInstance pc, ResultCard result,
            int chance, int rnd, boolean isUniqueSuccess, int rx) {
        if (pc.isGm()) SC_SystemMessage.send(pc, "신화 합성 확률: " + chance + "% (주사위: " + rnd + ")");
        pc.신화합성확률증가 = false;
        // Durable reward has been published before any effect or global notice.
        if (isUniqueSuccess) {
            SC_SkillSound.send(pc, pc.getId(), 21175);
            SC_DisplayEffect.send(pc, 4);
            broadcastUniqueSuccess(pc);
        } else {
            SC_SkillSound.send(pc, pc.getId(), 21128);
            SC_DisplayEffect.send(pc, 7);
            SC_DisplayEffect.send(pc, 22);
        }
        sendResultUI(pc, result, isUniqueSuccess, rx);
    }

	// One immutable descriptor per reward slot; no allocation in a synthesis result selector.
	private static final ResultCard UNIQUE_CARD = new ResultCard("2857", "[유일]지배자 기르타스", 875640295);
	private static final ResultCard[] MALE_MYTH_REWARDS = {
		new ResultCard("145", "[신화]군주", 875640296),
		new ResultCard("145", "[신화]군주", 875640296),
		new ResultCard("2859", "[신화]기사", 875640297),
		new ResultCard("2863", "[신화]요정", 875640298),
		new ResultCard("2867", "[신화]마법사", 875640299),
		new ResultCard("2871", "[신화]다크엘프", 875640300),
		new ResultCard("2875", "[신화]광전사", 875640301),
		new ResultCard("2877", "[신화]투사", 875640303),
		new ResultCard("141", "[신화]도플갱어", 875640302),
		new ResultCard("149", "[신화]데스나이트", 875640304),
		new ResultCard("145", "[신화]군주", 875640296),
		new ResultCard("71", "[신화]총사", 875640380)
	};
	private static final ResultCard[] FEMALE_MYTH_REWARDS = {
		new ResultCard("147", "[신화]군주", 875640296),
		new ResultCard("147", "[신화]군주", 875640296),
		new ResultCard("2861", "[신화]기사", 875640297),
		new ResultCard("2865", "[신화]요정", 875640298),
		new ResultCard("2869", "[신화]마법사", 875640299),
		new ResultCard("2873", "[신화]다크엘프", 875640300),
		new ResultCard("2875", "[신화]전사", 875640301),
		new ResultCard("2877", "[신화]용기사", 875640303),
		new ResultCard("143", "[신화]도플갱어", 875640302),
		new ResultCard("149", "[신화]데스나이트", 875640304),
		new ResultCard("147", "[신화]군주", 875640296),
		new ResultCard("73", "[신화]총사", 875640380)
	};

	private static ResultCard getResultCard(int sex, boolean isSuccess, int rx) {
		if (isSuccess) return UNIQUE_CARD;
		final int index = (rx >= 1 && rx <= 11) ? rx : 0;
		return (sex == 0 ? MALE_MYTH_REWARDS : FEMALE_MYTH_REWARDS)[index];
	}

	private static void sendResultUI(L1PcInstance pc, ResultCard result, boolean isSuccess, int rx) {
		ArrayList<String> args = new ArrayList<>(28);
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
		args.add(countText(acc.getInfo_class_poly()));
		args.add(countText(acc.getInfo_ki_poly()));
		args.add(countText(acc.getInfo_el_poly()));
		args.add(countText(acc.getInfo_wi_poly()));
		args.add(countText(acc.getInfo_da_poly()));
		args.add(countText(acc.getInfo_wa_poly()));
		args.add(countText(acc.getInfo_drag_poly()));
		args.add(countText(acc.getInfo_il_poly()));
		args.add(countText(acc.getInfo_dsn_poly()));
		args.add(countText(acc.getInfo_bapo_poly()));

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

    private static final Runnable UNIQUE_BROADCAST = () -> {
        final String msg = "어느 아덴 용사 님께서 [유일]지배자 기르타스 카드 합성에 성공하였습니다.";
        MessageUtil.toAllPlayers(msg);
        MessageUtil.greenMessageToAllPlayers(msg);
    };

    private static void broadcastUniqueSuccess(L1PcInstance pc) {
        ThreadPool.getInstance().scheduleRequired(UNIQUE_BROADCAST, 2300L);
    }


    // Keep the original zero-count text and formatting; avoid the per-result int[] snapshot.
    private static String countText(int count) {
        return count == 0 ? "[ 0장 ]" : "[ " + count + "장 ]";
    }

	private static class ResultCard {
		final String cardId, name;
		final int itemId;
		ResultCard(String cardId, String name, int itemId) {
			this.cardId = cardId; this.name = name; this.itemId = itemId;
		}
	}
}