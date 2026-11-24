package l1j.server.server.model;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.templates.L1NpcChat;

/**
 * Active dialogue sequence, not a scheduled Runnable.
 * CNPC::AddTimer / TimerExpired split: the existing NPC wheel owns the
 * deadline, the NPC owner executes the line, and the DB template owns text.
 */
public final class L1NpcChatTimer {
    private final L1NpcChat _npcChat;
    private final int _lifeEpoch;
    private int _currentChat;
    public long deadlineMs;
    public L1NpcChatTimer next;

    public L1NpcChatTimer(L1NpcChat npcChat, int lifeEpoch, long nowMs) {
        _npcChat = npcChat;
        _lifeEpoch = lifeEpoch;
        deadlineMs = nowMs + Math.max(1L, npcChat.getStartDelayTime());
    }

    public boolean isValid(L1NpcInstance npc) {
        if (npc.isDestroyed() || npc.npcEventEpoch() != _lifeEpoch) return false;
        switch (_npcChat.getChatTiming()) {
        case L1NpcInstance.CHAT_TIMING_APPEARANCE:
        case L1NpcInstance.CHAT_TIMING_HIDE:
            return !npc.isDead();
        case L1NpcInstance.CHAT_TIMING_DEAD:
            return npc.isDead();
        default:
            return true;
        }
    }

    private String chatId(int index) {
        return switch (index) {
        case 0 -> _npcChat.getChatId1();
        case 1 -> _npcChat.getChatId2();
        case 2 -> _npcChat.getChatId3();
        case 3 -> _npcChat.getChatId4();
        case 4 -> _npcChat.getChatId5();
        default -> null;
        };
    }

    private boolean hasMoreChat() {
        for (int i = _currentChat; i < 5; i++) {
            final String id = chatId(i);
            if (id != null && !id.isEmpty()) return true;
        }
        return false;
    }

    /** One due line on the NPC owner. Zero means retire this sequence. */
    public long TimerExpired(L1NpcInstance npc) {
        if (!isValid(npc)) return 0L;
        while (_currentChat < 5) {
            final String id = chatId(_currentChat++);
            if (id == null || id.isEmpty()) continue;
            chat(npc, id, _npcChat.isShout(), _npcChat.isWorldChat());
            if (hasMoreChat()) {
                return Math.max(100L, _npcChat.getChatInterval());
            }
            break;
        }
        if (_npcChat.isRepeat() && _npcChat.getRepeatInterval() > 0) {
            _currentChat = 0;
            return _npcChat.getRepeatInterval();
        }
        return 0L;
    }

    private static void chat(L1NpcInstance npc, String chatId,
            boolean isShout, boolean isWorldChat) {
        if (!isShout) {
            SC_NpcChatPacket.broadcastNpc(npc, npc, chatId, 4);
        } else {
            SC_NpcChatPacket.broadcastNpcWide(npc, npc, chatId, 2);
        }
        if (isWorldChat) {
            try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_NpcChatPacket.nativeNpcWriter(npc,chatId,3),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc != null) {
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
                }
            }
}
        }
    }
}