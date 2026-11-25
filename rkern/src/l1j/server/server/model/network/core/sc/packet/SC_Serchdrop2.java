package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;

import l1j.server.server.Account;

import l1j.server.server.storage.CacheD;

import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_Serchdrop2 {

	private static final Logger _log = Logger.getLogger(SC_Serchdrop2.class.getName());

    private SC_Serchdrop2() {}

    private static String loadDescription(int npcid) {
        String npcName = "알 수 없음";
        final List<String> items = new ArrayList<>(30);

        try {
            final L1Npc npc = CObjectDB.GetNPC(npcid);
            if (npc != null && npc.get_name() != null) npcName = npc.get_name();
        } catch (Exception ignored) {}

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT `itemId` FROM `droplist` WHERE mobId=? ORDER BY `itemId` DESC LIMIT 30")) {
            ps.setInt(1, npcid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final L1Item item = CObjectDB.GetItem(rs.getInt(1));
                    items.add(item != null && item.getName() != null ? item.getName() : "알수없는아이템");
                }
            }
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "SC_Serchdrop2[]Error", e);
        }

        final StringBuilder sb = new StringBuilder(256);
        sb.append("\r검색할 몹 : ").append(npcName).append("\n\n\r****** 드랍하는아이템 ******\n\n\r");
        for (int i = 0; i < 15; i++) {
            sb.append(i < items.size() ? items.get(i) : "").append(" |\n ");
        }
        return sb.toString();
    }

    public static boolean send(L1PcInstance receiver, int npcid) {
        if (receiver == null) return false;
        final CUserSocket client = receiver.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client;
        final Account account = client.getAccount();
        if (conn == null || !conn.isClientConnected() || account == null
                || client.getActiveChar() != receiver) return false;
        final int ownerShard = conn.affinityShard();
        try {
            CacheD.getInstance().submitDbJobOrdered(receiver.getId(),
                    CacheD.accountOrderKey(account.getName()), () -> {
                final String description;
                try {
                    description = loadDescription(npcid);
                } catch (Throwable e) {
                    java.util.logging.Logger.getLogger(SC_Serchdrop2.class.getName())
                            .log(java.util.logging.Level.SEVERE, "SC_Serchdrop2 DB load failed", e);
                    return;
                }
                CIOThreadCallback.getInstance().completeSessionReply(client, () -> {
                    if (client.isConnected() && client == conn
                            && conn.isClientConnected() && client.getAccount() == account
                            && client.getActiveChar() == receiver
                            && receiver.getNetConnection() == client) {
                        sendPrepared(client, conn, description);
                    }
                });
            });
            return true;
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(SC_Serchdrop2.class.getName())
                    .log(java.util.logging.Level.WARNING, "SC_Serchdrop2 CacheD submit failed", e);
            return false;
        }
    }

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(java.nio.charset.Charset.forName("MS949")).length) + 1;
    }

    private static boolean sendPrepared(CUserSocket client, CUserSocket conn, String description) {
        final long size = 2L + 1L + 4L + bytesOf("메티스")
                + bytesOf("드랍리스트") + bytesOf("") + bytesOf(description);
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BOARD_READ);
            buffer.writeD(0);
            buffer.writeS("메티스");
            buffer.writeS("드랍리스트");
            buffer.writeS("");
            buffer.writeS(description);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
}
