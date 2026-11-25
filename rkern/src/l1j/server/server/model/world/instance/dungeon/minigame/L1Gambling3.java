package l1j.server.server.model.world.instance.dungeon.minigame;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * 신용소막딜러(300027) 몹배팅 게임.
 * [19fix-gamble] 배선 복구: 채팅 진입 → 베팅금 아데나 차감 → 백그라운드 판정.
 * 9종 몹 중 하나에 배팅, 3마리가 랜덤 등장하여 1마리 맞출 때마다 2배(최대 6배).
 */
public class L1Gambling3 {
	private static final Logger _log = Logger.getLogger(L1Gambling3.class.getName());
	private static final int DEALER = 300027;
	private static final int MAX_BET = 499999;
	// type 1~9 -> 몹이름 / npcId(300040+type)
	private static final String[] MOB_NAMES = { "오크전사", "스파토이", "멧돼지", "슬라임", "해골", "늑대인간", "버그베어", "장로", "괴물눈" };

	// 딜러 클릭 → 즉시 베팅 입력 모드 진입 (AttackActionHandler에서 호출)
	public void dealerTrade(L1PcInstance player) {
		startGame(player);
	}

	// 딜러와 거래(Y) → 베팅금 입력 대기 상태
	public void startGame(L1PcInstance pc) {
		pc.setGambling3(true);
		pc.setGamblingMoney3(0);
		SC_SystemMessage.send(pc, "\\f2[신용소막딜러] 배팅할 아데나를 채팅으로 입력하세요. (1 ~ " + MAX_BET + ")");
	}

	// 베팅금 입력 → 아데나 차감 후 몹선택 단계로
	public void placeBet(L1PcInstance pc, String text) {
		pc.setGambling3(false);
		int bet;
		try {
			bet = Integer.parseInt(text.replaceAll("[^0-9]", ""));
		} catch (Exception e) {
			SC_SystemMessage.send(pc, "\\f3배팅이 취소되었습니다. (숫자를 입력해야 합니다)");
			return;
		}
		if (bet <= 0 || bet > MAX_BET) {
			SC_SystemMessage.send(pc, "\\f3배팅이 취소되었습니다. (1 ~ " + MAX_BET + ")");
			return;
		}
		if (!pc.getInventory().HaveItemByClassId(L1ItemId.ADENA, bet)) {
			SC_SystemMessage.send(pc, "\\f3아데나가 부족하여 취소되었습니다.");
			return;
		}
		pc.getInventory().DeleteItemInInventory(L1ItemId.ADENA, bet);
		pc.setGambling3(true);
		pc.setGamblingMoney3(bet);
		SC_SystemMessage.send(pc, "\\f2[신용소막딜러] " + bet + " 아데나 배팅! 배팅할 몹 이름을 입력하세요. (버그베어,장로,멧돼지,스파토이,슬라임,해골,늑대인간,괴물눈,오크전사) - 1마리 맞출 때마다 2배!");
	}

	// 몹 선택 → 백그라운드 스레드에서 판정(접속 멈춤 방지)
	public void Gambling3(final L1PcInstance pc, String chatText, final int type) {
		final String senderName=pc.getName();
        final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter chatWire=SC_ChatPacket.nativeWriter(Opcodes.S_SAY,0,pc.getId(),
                    (senderName==null?"":senderName)+": "+(chatText==null?"":chatText),true);
        try(var chatShared=chatWire==null?null:new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    chatWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(chatShared,pc);
            for (L1PcInstance listner : L1World.getInstance().getRecognizePlayer(pc)) {
			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(chatShared,listner);
		}
        }
		final int bet = pc.getGamblingMoney3();
		pc.setGambling3(false); // 잠금(중복 입력 방지)
		pc.setGamblingMoney3(0);
		if (bet <= 0 || type < 1 || type > 9) {
			return;
		}
		final int target = 300040 + type; // type1=300041 ... type9=300049
		final short mapId = pc.getMapId();
		ThreadPool.getInstance().execute(new Runnable() {
			@Override
			public void run() {
				try {
					Random random = new Random();
					L1NpcInstance dealer = findDealer();
					say(pc, dealer, MOB_NAMES[type - 1] + "에 배팅! 출발합니다~ 과연? ㅋㅋㅋ");
					int m1 = 300041 + random.nextInt(9);
					int m2 = 300041 + random.nextInt(9);
					int m3 = 300041 + random.nextInt(9);
					L1EffectSpawn.getInstance().spawnEffect(m1, 3500, 33513, 32851, mapId);
					L1EffectSpawn.getInstance().spawnEffect(m2, 3500, 33511, 32851, mapId);
					L1EffectSpawn.getInstance().spawnEffect(m3, 3500, 33509, 32851, mapId);
					Thread.sleep(1800);
					int hits = (m1 == target ? 1 : 0) + (m2 == target ? 1 : 0) + (m3 == target ? 1 : 0);
					if (hits > 0) {
						int payout = bet * 2 * hits;
						pc.getInventory().CreateOrAddItem(L1ItemId.ADENA, payout);
						say(pc, dealer, "오!굿... " + hits + "마리 적중! 배당금 " + payout + " 아데나 지급했습니다.");
					} else {
						say(pc, dealer, "아쉽군요ㅋㅋ 다음 기회에 도전해주세요~!");
					}
				} catch (Throwable e) {
					_log.log(Level.WARNING, e.getLocalizedMessage(), e);
				}
			}
		});
	}

	private L1NpcInstance findDealer() {
		for (L1Object o : L1World.getInstance().getObject()) {
			if (o instanceof L1NpcInstance && ((L1NpcInstance) o).getNpcTemplate().get_npcId() == DEALER) {
				return (L1NpcInstance) o;
			}
		}
		return null;
	}

	private void say(L1PcInstance pc, L1NpcInstance dealer, String msg) {
		if (dealer != null) {
			SC_NpcChatPacket.sendNpc(pc, dealer, msg, 0);
			SC_NpcChatPacket.broadcastNpc(pc, dealer, msg, 0);
		} else {
			SC_SystemMessage.send(pc, msg);
		}
	}
}
