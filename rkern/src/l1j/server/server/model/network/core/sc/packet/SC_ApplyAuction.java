package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

import l1j.server.server.Account;

import l1j.server.server.storage.CacheD;

import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_ApplyAuction {

    private SC_ApplyAuction() {}

    private static long loadAuctionPrice(String houseNumber) {
        int price = 0;
        int bidder = 0;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM board_auction WHERE house_id=?")) {
            ps.setInt(1, Integer.parseInt(houseNumber));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    price = rs.getInt(5);
                    bidder = rs.getInt(10);
                }
            }
        } catch (SQLException | NumberFormatException e) {
            e.printStackTrace();
        }
        return (((long) bidder) << 32) | (price & 0xFFFFFFFFL);
    }

    public static boolean send(L1PcInstance receiver, int objectId, String houseNumber) {
        if (receiver == null) return false;
        final CIOClient client = receiver.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        final Account account = client.getAccount();
        if (conn == null || !conn.isClientConnected() || account == null
                || client.getActiveChar() != receiver) return false;
        final int ownerShard = conn.affinityShard();
        try {
            CacheD.getInstance().submitDbJobOrdered(receiver.getId(),
                    CacheD.accountOrderKey(account.getName()), () -> {
                final long auctionValues;
                try {
                    auctionValues = loadAuctionPrice(houseNumber);
                } catch (Throwable e) {
                    java.util.logging.Logger.getLogger(SC_ApplyAuction.class.getName())
                            .log(java.util.logging.Level.SEVERE, "SC_ApplyAuction DB load failed", e);
                    return;
                }
                CIOThreadCallback.getInstance().completeSessionReply(client, () -> {
                    if (client.isConnected() && client.getConnection() == conn
                            && conn.isClientConnected() && client.getAccount() == account
                            && client.getActiveChar() == receiver
                            && receiver.getNetConnection() == client) {
                        sendPrepared(client, conn, objectId, houseNumber, auctionValues);
                    }
                });
            });
            return true;
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(SC_ApplyAuction.class.getName())
                    .log(java.util.logging.Level.WARNING, "SC_ApplyAuction CacheD submit failed", e);
            return false;
        }
    }

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(java.nio.charset.Charset.forName("MS949")).length) + 1;
    }

    private static boolean sendPrepared(CIOClient client, CUserSocket conn,
            int objectId, String houseNumber, long auctionValues) {
        final int price = (int) auctionValues;
        final int bidder = (int) (auctionValues >> 32);
        final int targetPrice = bidder == 0 ? price : price + 1;
        final String label = "agapply " + houseNumber;
        final long needed = 2L + 1L + 4L * 5L + 2L + bytesOf("agapply") + bytesOf(label);
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT_INPUT);
            buffer.writeD(objectId);
            buffer.writeD(0);
            buffer.writeD(targetPrice);
            buffer.writeD(targetPrice);
            buffer.writeD(2000000000);
            buffer.writeH(0);
            buffer.writeS("agapply");
            buffer.writeS(label);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
}
