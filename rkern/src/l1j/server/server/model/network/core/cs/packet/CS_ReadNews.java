package l1j.server.server.model.network.core.cs.packet;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_CommonNews;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;

public final class CS_ReadNews {
    private static final Logger _log = Logger.getLogger(CS_ReadNews.class.getName());

    private CS_ReadNews() { }

    public static void handle(CUserSocket client) {
        if (client != null) sendNotice(client, client.getAccountName());
    }

    private static void sendNotice(CUserSocket client, String account) {
        if (client == null || !client.isConnected()) return;
        if (account == null || account.isEmpty()) {
            new CS_CommonClick(client);
            return;
        }
        final Account expectedAccount = client.getAccount();
        if (expectedAccount == null || !account.equals(expectedAccount.getName())) return;
        final CUserSocket.LoginDbLoad request = client.tryBeginLoginDbLoad(client.getConnectionState(), expectedAccount);
        if (request == null) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(account, () -> {
                if (!client.isCurrentLoginDbLoad(request)) {
                    client.finishLoginDbLoad(request);
                    return;
                }
                try {
                    final SC_CommonNews.NoticeData notice = SC_CommonNews.loadUnreadNotice(account);
                    CIOThreadCallback.getInstance().completeSession(client, () -> {
                        if (!client.isCurrentLoginDbLoad(request)) return;
                        if (notice == null || !SC_CommonNews.send(client, notice.message)) {
                            if (client.isCurrentLoginDbLoad(request)) {
                                client.finishLoginDbLoad(request);
                                new CS_CommonClick(client);
                            }
                            return;
                        }
                        try {
                            CacheD.getInstance().submitDbJobOrdered(account, () ->
                                    SC_CommonNews.updateNoticeDate(account, notice.noticeId));
                        } catch (RuntimeException e) {
                            _log.log(Level.WARNING, "Notice read-state update rejected: " + account, e);
                        }
                    }, failure -> finishNoticeLoad(client, request, account, failure));
                } catch (RuntimeException failure) {
                    finishNoticeLoad(client, request, account, failure);
                }
            });
        } catch (RuntimeException failure) {
            finishNoticeLoad(client, request, account, failure);
        }
    }

    private static void finishNoticeLoad(CUserSocket client, CUserSocket.LoginDbLoad request,
            String account, Throwable failure) {
        try {
            if (failure != null && client.isCurrentLoginDbLoad(request)) {
                _log.log(Level.WARNING, "Notice load failed: " + account, failure);
                client.close();
            }
        } finally {
            client.finishLoginDbLoad(request);
        }
    }
}
