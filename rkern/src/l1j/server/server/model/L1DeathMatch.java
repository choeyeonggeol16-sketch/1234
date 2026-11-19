package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Random;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public class L1DeathMatch implements Runnable {
    /** Optional game notices must not terminate the timed game-state transition. */
    private static final java.util.logging.Logger GAME_NOTICE_LOG =
            java.util.logging.Logger.getLogger(L1DeathMatch.class.getName());

    private static l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer
    openGameBroadcast(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter writer) {
        if (writer == null) return null;
        try {
            return new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    writer, Integer.MIN_VALUE, Integer.MAX_VALUE);
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Optional game notice source unavailable", failure);
            return null;
        }
    }

    private static void sendGameNotice(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer shared,
            L1PcInstance pc) {
        if (shared == null || pc == null) return;
        try {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, pc);
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Optional game notice SEND failed", failure);
        }
    }

    private static void closeGameBroadcast(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer shared) {
        if (shared == null) return;
        try {
            shared.close();
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Optional game notice source release failed", failure);
        }
    }


	public static final int STATUS_NONE = 0;
	public static final int STATUS_READY = 1;
	public static final int STATUS_PLAYING = 2;

	public static final int EXECUTE_STATUS_NONE = 0;
	public static final int EXECUTE_STATUS_PREPARE = 1;
	public static final int EXECUTE_STATUS_READY = 2;
	public static final int EXECUTE_STATUS_PROGRESS = 3;
	public static final int EXECUTE_STATUS_FINALIZE = 4;

	private final ArrayList<L1PcInstance> _members = new ArrayList<L1PcInstance>();
	private int _deathmatchStatus = STATUS_NONE;
	private int _executeStatus = EXECUTE_STATUS_NONE;

	private static L1DeathMatch _instance;

	private static int Count;

	private static int playerCount;

	private static int executeCount1;
	private static int executeCount2;

	public static L1DeathMatch getInstance() {
		if (_instance == null) {
			_instance = new L1DeathMatch();
		}
		return _instance;
	}

	@Override
	public void run() {
		try {
			switch (_executeStatus) {
			case EXECUTE_STATUS_NONE: {
				setDeathMatchStatus(STATUS_READY);

				_executeStatus = EXECUTE_STATUS_PREPARE;
				ThreadPool.getInstance().scheduleRequired(this, 120000L);
			}
				break;

			case EXECUTE_STATUS_PREPARE: {
				removeRetiredMembers();
				readyDeathMatch();

				_executeStatus = EXECUTE_STATUS_READY;
				ThreadPool.getInstance().scheduleRequired(this, 30000L);
			}
				break;

			case EXECUTE_STATUS_READY: {
				startDeathMatch();
				executeCount1 = 0;
				executeCount2 = 0;

				removeRetiredMembers();

				_executeStatus = EXECUTE_STATUS_PROGRESS;
				ThreadPool.getInstance().scheduleRequired(this, 3000L);
			}
				break;

			case EXECUTE_STATUS_PROGRESS: {
				if (executeCount2 >= 20) {
					executeCount2 = 0;
					++executeCount1;

					if (executeCount1 >= 30) {
						if (getMembersCount() > 1) {
							endDeathMatch();
						}

						_executeStatus = EXECUTE_STATUS_FINALIZE;
						ThreadPool.getInstance().scheduleRequired(this, 30000L);

						break;
					} else {
						removeRetiredMembers();
					}
				}

				대미지감소(executeCount1 * 10);
				남은사람();
				if (playerCount == 1) {
					우승자종료();
				}
				++executeCount2;
				ThreadPool.getInstance().scheduleRequired(this, 3000L);
			}
				break;

			case EXECUTE_STATUS_FINALIZE: {
				_executeStatus = EXECUTE_STATUS_NONE;
				setDeathMatchStatus(STATUS_NONE);
			}
				break;
			}
		} catch (Exception e) {
		}
	}

	private void 남은사람() {
		playerCount = 0;
		String str;
		for (L1PcInstance pc : getMembersArray()) {
			if (pc.getMapId() == 5153) {
				if (!pc.isGhost()) {
					playerCount += 1;
				}
			}
		}
		if (executeCount2 == 5 || executeCount2 == 10 || executeCount2 == 15 || executeCount2 == 20) {
			str = String.valueOf(playerCount);
			sendMessage(1274, str);
		}
	}

	private void 대미지감소(int i) {
		for (L1PcInstance pc : getMembersArray()) {
			if (pc.isDead()) {
				return;
			}
			if (pc.isGhost()) {
				return;
			}
			if (pc.getMapId() != 5153) {
				return;
			}
			pc.setCurrentHp(pc.getCurrentHp() - i);
		}
	}

	private void readyDeathMatch() {
		if (Count < 1) {
			final var shared = openGameBroadcast(SC_ServerMessage.nativeWriter(1270));
			try {
            for (L1PcInstance pc : getMembersArray()) {
				if (pc.getMapId() == 5153) {
					// 경기 최소 인원이 6명이 만족하지 않아 경기를 강제 종료 합니다. 1000 아데나를 돌려 드렸습니다.
					sendGameNotice(shared,pc);
					removeMember(pc);
					pc.getInventory().storeItem(40308, 1000); // 1000 아데나 지급
//					L1Teleport.teleport(pc, 32624, 32813, (short) 4, 5, true);
					pc.start_teleport(32624, 32813, 4, 5, 169, true, false);
				} else {
					removeMember(pc);
				}
			}
        } finally { closeGameBroadcast(shared); }
			return;
		}
		final var shared = openGameBroadcast(SC_ServerMessage.nativeWriter(1269));
		try {
            for (L1PcInstance pc : getMembersArray()) {
			pc.getInventory().storeItem(410018, 1); // 이거뭐임?
			sendGameNotice(shared,pc);
		}
        } finally { closeGameBroadcast(shared); }
	}

	private void startDeathMatch() {
		setDeathMatchStatus(STATUS_PLAYING);
		final var shared = openGameBroadcast(SC_PacketBox.nativeMiniGameTimeWriter(1800));
		try {
            for (L1PcInstance pc : getMembersArray()) {
			if (pc.getMapId() == 5153) {
				pc.setDeathMatch(true);
//				L1Teleport.teleport(pc, 32639, 32897, (short) 5153, 5, true);
				pc.start_teleport(32639, 32897, 5153, 5, 169, true, false);
				sendGameNotice(shared,pc);
			} else {
				removeMember(pc);
			}
		}
        } finally { closeGameBroadcast(shared); }
	}

	public void 우승자종료() {
		final var shared = openGameBroadcast(SC_PacketBox.nativeMiniGameEndWriter());
		try {
            for (L1PcInstance pc : getMembersArray()) {
			if (pc.getMapId() == 5153) {
				if (pc.isGhost()) {
					pc.getInventory().storeItem(410007, 1);
					pc.DeathMatchEndGhost();
					pc.setDeathMatch(false);
				} else {
					SC_ServerMessage.send(pc, 1272, pc.getName());
					pc.getInventory().storeItem(41402, 10);
					pc.getInventory().storeItem(410007, 1);
//					L1Teleport.teleport(pc, 32624, 32813, (short) 4, 5, true);
					pc.start_teleport(32624, 32813, 4, 5, 169, true, false);
					pc.setDeathMatch(false);

					Random random = new Random(System.nanoTime()); // 펫레이싱

					if (random.nextInt() < 33) {
						// The stored award already carries the template needed for its message.
						// No second world-registered dummy object is necessary.
						L1ItemInstance reward = pc.getInventory().storeItem(3000022, 1);
						if (reward != null) {
							SC_ServerMessage.send(pc, 403, reward.getNumberedName(1));
						}
					}
				}
				sendGameNotice(shared,pc);
			}
		}
        } finally { closeGameBroadcast(shared); }
		clearMembers();
		Count = 0;
	}

	public void endDeathMatch() {
		sendMessage(1275, "");
		final var shared = openGameBroadcast(SC_PacketBox.nativeMiniGameEndWriter());
		try {
            for (L1PcInstance pc : getMembersArray()) {
			if (pc.getMapId() == 5153) {
				if (pc.isGhost()) {
					pc.getInventory().storeItem(410007, 1);
					pc.DeathMatchEndGhost();
					pc.setDeathMatch(false);
				} else {
					pc.getInventory().storeItem(410007, 1);
//					L1Teleport.teleport(pc, 32624, 32813, (short) 4, 5, true);
					pc.start_teleport(32624, 32813, 4, 5, 169, true, false);
					pc.setDeathMatch(false);
				}
				sendGameNotice(shared,pc);
			}
		}
        } finally { closeGameBroadcast(shared); }
		clearMembers();
		Count = 0;
	}

	public void removeRetiredMembers() {
		for (L1PcInstance pc : getMembersArray()) {
			if (pc.getMapId() != 5153) {
				pc.setDeathMatch(false);
				removeMember(pc);
			}
		}
	}

	public void sendMessage(int type, String msg) {
        try {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(
                getMembersArray(),SC_ServerMessage.nativeWriter(type,msg));
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Death Match common notice failed", failure);
        }
    }

	public void addMember(L1PcInstance pc) {
		if (!_members.contains(pc)) {
			_members.add(pc);
			// %d번째 순번으로 입장 예약되었습니다.
			SC_ServerMessage.send(pc, 1265, Integer.toString(getMembersCount()));
			// 이미 있다면..
		} else {
			// 이미 데스매치 입장 예약되어있습니다.
			SC_ServerMessage.send(pc, 1266);
		}
		if (getDeathMatchStatus() == STATUS_READY) {
			if (pc.getMap().getId() != 5153)
				SC_MessageYN.send(pc, 1268, "");
			return;
		}
		if (getMembersCount() > 1 && getDeathMatchStatus() == STATUS_NONE) { // 4명 이상이고 게임상태가 대기상태라면
			ThreadPool.getInstance().execute(this);
			final var shared = openGameBroadcast(SC_MessageYN.nativeWriter(1268,""));
			try {
for (L1PcInstance player : getMembersArray()) {
				// 데스매치에 입장하시겠습니까? (Y/N)
				if (player.getMap().getId() != 5153)
					sendGameNotice(shared,player);
			}
} finally { closeGameBroadcast(shared); }
		}
	}

	public void removeMember(L1PcInstance pc) {
		_members.remove(pc);
	}

	public void clearMembers() {
		_members.clear();
	}

	public boolean isMember(L1PcInstance pc) {
		return _members.contains(pc);
	}

	public L1PcInstance[] getMembersArray() {
		return _members.toArray(new L1PcInstance[_members.size()]);
	}

	public int getMembersCount() {
		return _members.size();
	}

	public void addPlayer() {
		Count += 1;
	}

	private void setDeathMatchStatus(int i) {
		_deathmatchStatus = i;
	}

	public int getDeathMatchStatus() {
		return _deathmatchStatus;
	}
}