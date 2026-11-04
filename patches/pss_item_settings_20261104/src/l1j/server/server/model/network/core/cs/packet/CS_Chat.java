package l1j.server.server.model.network.core.cs.packet;

import l1j.server.Config;
import l1j.server.server.GMCommands;
import l1j.server.server.datatables.SpamTable;
import l1j.server.server.model.L1ExcludingList;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_NewChat;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_Chat {

    private CS_Chat() {
    }

    public static void handle(CIOClient client, String chatText) {
        if (client == null || chatText == null || chatText.isEmpty()) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();
        if (pc == null) {
            return;
        }

        if (l1j.server.server.model.character.pss.window.PlaySupportWindow.handleLinkCommand(pc, chatText)) return;

        handleWorldChat(pc, chatText);
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

        for (L1PcInstance listener : L1World.getInstance().getAllPlayers()) {
            if (listener == null) {
                continue;
            }

            final L1ExcludingList spamList =
                    SpamTable.getInstance().getExcludeTable(listener.getId());

            if (spamList != null && !spamList.contains(0, senderName)) {
                cp.send(listener);
                cp2.send(listener);
            }
        }
    }
}
