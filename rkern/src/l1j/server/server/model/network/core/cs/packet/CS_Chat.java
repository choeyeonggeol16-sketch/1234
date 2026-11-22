package l1j.server.server.model.network.core.cs.packet;

import l1j.server.Config;
import l1j.server.server.GMCommands;
import l1j.server.server.UserCommands;
import l1j.server.server.datatables.SpamTable;
import l1j.server.server.model.L1ExcludingList;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_NewChat;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_Chat {

    private CS_Chat() {
    }

    public static void handle(CUserSocket client, String chatText) {
        if (client == null || chatText == null || chatText.isEmpty()) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();
        if (pc == null) {
            return;
        }

        if (l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.handleLinkCommand(pc, chatText)) return;

        if (UserCommands.handleLightCommand(pc, chatText)) return;

        // Password commands are not connected to the general chat dispatcher.
        // Never broadcast their arguments as world chat.
        if (isPasswordCommand(chatText)) {
            pc.sendPackets("현재 채팅 명령으로 암호를 변경할 수 없습니다.");
            return;
        }

        handleWorldChat(pc, chatText);
    }

    private static boolean isPasswordCommand(String text) {
        int start = 0;
        final int length = text.length();
        while (start < length && Character.isWhitespace(text.charAt(start))) start++;
        if (start == length || text.charAt(start) != '.') return false;

        final String first = ".암호변경";
        final String second = ".비번변경";
        if (!text.startsWith(first, start) && !text.startsWith(second, start)) return false;
        final int end = start + first.length();
        return end == length || Character.isWhitespace(text.charAt(end));
    }

    private static void handleWorldChat(L1PcInstance pc, String chatText) {
        if (!pc.isGm() && pc.getLevel() < Config.GLOBAL_CHAT_LEVEL) {
            SC_ServerMessage.send(pc, 195, String.valueOf(Config.GLOBAL_CHAT_LEVEL));
            return;
        }

        if (!pc.isGm() && !L1World.getInstance().isWorldChatElabled()) {
            SC_ServerMessage.send(pc, 510);
            return;
        }

        if (pc.get_food() < 12) {
            SC_ServerMessage.send(pc, 462);
            return;
        }

        SC_PacketBox.send(pc, SC_PacketBox.FOOD, pc.get_food());

        if (pc.isGm()) {
            SC_PacketBox.broadcastGreen("\\f=[******] " + chatText);

            SC_NewChat.broadcastAll(pc, 4, 3, chatText, "[******] ");
            return;
        }

        if (pc.hasSkillEffect(1005)) {
            SC_ServerMessage.send(pc, 242);
            return;
        }

        if (GMCommands.전체채팅) {
            broadcastToWorld(pc, chatText);
        }
    }

    private static void broadcastToWorld(L1PcInstance sender, String chatText) {
        final SC_NewChat.ChatData cp = SC_NewChat.prepare(sender, 4, 12, chatText, "");
        final SC_NewChat.ChatData cp2 = SC_NewChat.prepare(sender, 4, 3, chatText, "");
        final String senderName = sender.getName();
        final var firstWriter = SC_NewChat.nativeWriter(cp);
        final var secondWriter = SC_NewChat.nativeWriter(cp2);
        // A frame too large for the L1 16-bit wire length was not deliverable
        // via the old direct sender either. Never create a legacy SEND fallback.
        if (firstWriter == null || secondWriter == null) return;

        try (var first = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                     firstWriter, Integer.MIN_VALUE, Integer.MAX_VALUE);
             var second = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                     secondWriter, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance listener : L1World.getInstance().getAllPlayers()) {
                if (listener == null) continue;

                final L1ExcludingList spamList =
                        SpamTable.getInstance().getExcludeTable(listener.getId());

                if (spamList != null && !spamList.contains(0, senderName)) {
                    // Keep the original recipient-major FIFO order: first 12, then 3.
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(first, listener);
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(second, listener);
                }
            }
        }
    }
}
