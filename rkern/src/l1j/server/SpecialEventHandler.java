package l1j.server;

import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

enum SpecialEvent {
	BugRace, AllBuf, InfinityFight, DoNotChatEveryone, DoChatEveryone
};

// 게임 내, 전체 이벤트에 대한 처리를 담당
public class SpecialEventHandler {

	private static volatile SpecialEventHandler uniqueInstance = null;

	private SpecialEventHandler() {
	}

	public static SpecialEventHandler getInstance() {
		if (uniqueInstance == null) {
			synchronized (SpecialEventHandler.class) {
				if (uniqueInstance == null) {
					uniqueInstance = new SpecialEventHandler();
				}
			}
		}

		return uniqueInstance;
	}

	public void doNotChatEveryone() {
		L1World.getInstance().set_worldChatElabled(false);
		SC_SystemMessage.broadcast("잠시후 월드채팅이 비활성화 상태로 돌입 합니다.");
	}

	public void doChatEveryone() {
		L1World.getInstance().set_worldChatElabled(true);
		SC_SystemMessage.broadcast("잠시후 월드채팅이 활성화 상태로 돌입 합니다.");
	}

}
