package l1j.server.server.model;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.templates.L1NpcChat;

public class L1NpcChatTimer implements Runnable {
    private static final Logger _log = Logger.getLogger(L1NpcChatTimer.class.getName());

    private final L1NpcInstance _npc;
    private final L1NpcChat _npcChat;

    private final String[] _chatIds = new String[5];

    private final boolean _isShout;
    private final boolean _isWorldChat;
    private final int _chatTiming;
    private final long _repeat;
    private final long _chatInterval;

    private int _currentChat = 0;   

    public L1NpcChatTimer(L1NpcInstance npc, L1NpcChat npcChat) {
        this(npc, npcChat, 0);
    }

    public L1NpcChatTimer(L1NpcInstance npc, L1NpcChat npcChat, long repeat) {
        _npc = npc;
        _npcChat = npcChat;
        _repeat = repeat;
        _chatTiming = npcChat.getChatTiming();
        _isShout = npcChat.isShout();
        _isWorldChat = npcChat.isWorldChat();
        _chatInterval = npcChat.getChatInterval();

        _chatIds[0] = npcChat.getChatId1();
        _chatIds[1] = npcChat.getChatId2();
        _chatIds[2] = npcChat.getChatId3();
        _chatIds[3] = npcChat.getChatId4();
        _chatIds[4] = npcChat.getChatId5();
    }

    public void startChat(long delay) {
        if (_npc == null || _npcChat == null) return;
        if (_npc.getHiddenStatus() != L1NpcInstance.HIDDEN_STATUS_NONE
                || _npc._destroyed) {
            return;
        }
        ThreadPool.getInstance().scheduleRequired(this, delay);
    }

    @Override
    public void run() {
        try {
            if (_npc == null || _npc._destroyed) {
                return;
            }
            if (_chatTiming == L1NpcInstance.CHAT_TIMING_APPEARANCE && _npc.isDead()) return;
            if (_chatTiming == L1NpcInstance.CHAT_TIMING_HIDE && _npc.isDead()) return;
            if (_chatTiming == L1NpcInstance.CHAT_TIMING_DEAD && !_npc.isDead()) return;

            while (_currentChat < _chatIds.length) {
                final String chatId = _chatIds[_currentChat];
                _currentChat++;

                if (chatId != null && !chatId.isEmpty()) {
                    chat(_npc, _chatTiming, chatId, _isShout, _isWorldChat);

                    if (_currentChat < _chatIds.length && hasMoreChat()) {
                        final long delay = Math.max(_chatInterval, 100); 
                        ThreadPool.getInstance().scheduleRequired(this, delay);
                        return;
                    }
                    break;
                }
            }
            
            if (_repeat > 0) {
                _currentChat = 0;
                ThreadPool.getInstance().scheduleRequired(this, _repeat);
            }
        } catch (Throwable e) {
            _log.log(Level.WARNING, e.getLocalizedMessage(), e);
        }
    }

    private boolean hasMoreChat() {
        for (int i = _currentChat; i < _chatIds.length; i++) {
            if (_chatIds[i] != null && !_chatIds[i].isEmpty()) return true;
        }
        return false;
    }

    private void chat(L1NpcInstance npc, int chatTiming, String chatId,
            boolean isShout, boolean isWorldChat) {
        if (!isShout) {
            SC_NpcChatPacket.broadcastNpc(npc, npc, chatId, 4);
        } else {
            SC_NpcChatPacket.broadcastNpcWide(npc, npc, chatId, 2);
        }
        if (isWorldChat) {
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc != null) {
                    SC_NpcChatPacket.sendNpc(pc, npc, chatId, 3);
                }
            }
        }
    }
}