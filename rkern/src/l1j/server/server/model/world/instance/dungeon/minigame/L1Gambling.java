package l1j.server.server.model.world.instance.dungeon.minigame;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.Opcodes;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;

/**
 * 주사위 딜러(400064) 홀짝/숫자 게임.
 * [19fix-gamble] 배선 복구: 채팅 진입 → 베팅금 아데나 차감 → 백그라운드 판정.
 * 홀/짝 적중 2배, 1~6 숫자 적중 3배. 베팅은 placeBet에서 차감, 승리 시 배당 지급.
 */
public class L1Gambling {
	private static final Logger _log = Logger.getLogger(L1Gambling.class.getName());
	private static final int DEALER = 400064;
	private static final int MAX_BET = 499999;

	// 딜러 클릭 → 즉시 베팅 입력 모드 진입 (C_NPCTalk에서 호출)
	public void dealerTrade(L1PcInstance player) {
		startGame(player);
	}

	// 딜러와 거래(Y) → 베팅금 입력 대기 상태
	public void startGame(L1PcInstance pc) {
		pc.setGambling(true);
		pc.setGamblingMoney(0);
		SC_SystemMessage.send(pc, "\\f2[주사위 딜러] 배팅할 아데나를 채팅으로 입력하세요. (1 ~ " + MAX_BET + ")");
	}

	// 베팅금 입력 → 아데나 차감 후 선택 단계로
	public void placeBet(L1PcInstance pc, String text) {
		pc.setGambling(false); // 성공 시 아래에서 다시 켬. 실패하면 그대로 게임취소.
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
		pc.setGambling(true);
		pc.setGamblingMoney(bet);
		SC_SystemMessage.send(pc, "\\f2[주사위 딜러] " + bet + " 아데나 배팅! '홀' 또는 '짝'(2배), 1~6 숫자(3배)를 입력하세요.");
	}

	// 홀/짝/숫자 선택 → 백그라운드 스레드에서 판정(접속 멈춤 방지)
	public void Gambling2(final L1PcInstance pc, String chatText, final int type) {
	//	ChatLogTable.getInstance().storeChat(pc, null, chatText, 0);
		final String senderName=pc.getName();
        final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter chatWire=SC_ChatPacket.nativeWriter(Opcodes.S_SAY,0,pc.getId(),
                    (senderName==null?"":senderName)+": "+(chatText==null?"":chatText),true);
        try(var chatShared=chatWire==null?null:new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    chatWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(chatShared,pc);
            for (L1PcInstance listner : CWorld.getInstance().getRecognizePlayer(pc)) {
			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(chatShared,listner);
		}
        }
		final int bet = pc.getGamblingMoney();
		pc.setGambling(false); // 잠금(중복 입력 방지)
		pc.setGamblingMoney(0);
		if (bet <= 0) {
			return;
		}
		CInstanceWork.execute(new Runnable() {
			@Override
			public void run() {
				try {
					L1NpcInstance dealer = findDealer();
					int gfxid = 3204 + GameRandom.nextInt(6); // 3204~3209 = 주사위 눈 1~6
					String pick;
					boolean win;
					int mult;
					switch (type) {
					case 1: pick = "홀"; mult = 2; win = (gfxid == 3204 || gfxid == 3206 || gfxid == 3208); break;
					case 2: pick = "짝"; mult = 2; win = (gfxid == 3205 || gfxid == 3207 || gfxid == 3209); break;
					case 3: pick = "1"; mult = 3; win = (gfxid == 3204); break;
					case 4: pick = "2"; mult = 3; win = (gfxid == 3205); break;
					case 5: pick = "3"; mult = 3; win = (gfxid == 3206); break;
					case 6: pick = "4"; mult = 3; win = (gfxid == 3207); break;
					case 7: pick = "5"; mult = 3; win = (gfxid == 3208); break;
					case 8: pick = "6"; mult = 3; win = (gfxid == 3209); break;
					default: return;
					}
					say(pc, dealer, pc.getName() + "님 [" + pick + "] 선택! 주사위를 굴립니다...");
					Thread.sleep(1500);
					if (dealer != null) {
						SC_SkillSound.send(pc, dealer.getId(), gfxid);
						SC_SkillSound.broadcast(pc, dealer.getId(), gfxid);
					}
					Thread.sleep(2500);
					int face = gfxid - 3203; // 1~6
					if (win) {
						int payout = bet * mult;
						pc.getInventory().CreateOrAddItem(L1ItemId.ADENA, payout);
						say(pc, dealer, "결과 [" + face + "]! " + pc.getName() + "님 적중! " + payout + " 아데나 지급했습니다.");
					} else {
						say(pc, dealer, "결과 [" + face + "]! " + pc.getName() + "님 아쉽네요. 뽀찌는 없습니다. 즐~");
					}
				} catch (Throwable e) {
					_log.log(Level.WARNING, e.getLocalizedMessage(), e);
				}
			}
		});
	}

	private L1NpcInstance findDealer() {
		return CWorld.getInstance().findNpcByNpcId(DEALER);
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
