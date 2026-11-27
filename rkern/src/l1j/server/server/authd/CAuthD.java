package l1j.server.server.authd;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_CommonNews;
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.storage.CacheD;

/** In-process authentication. CacheD reads credentials, and the socket owner publishes the session. */
public final class CAuthD {
    private static final Logger LOG = Logger.getLogger(CAuthD.class.getName());

    private CAuthD() { }

    public static void requestLogin(CUserSocket client, String rawName, String password,
            Consumer<CUserSocket> authenticated) {
        if (client == null || !client.isConnected() || client.getAccount() != null
                || client.getConnectionState() != CConnectionState.CONNECTED
                || client.isLoginDbLoadPending()) return;
        final String name = CAccountContainer.accountKey(rawName);
        if (name == null || password == null || password.isEmpty()) {
            client.close();
            return;
        }

        final String ip = client.getIp();
        final boolean allowCreation = Config.AUTO_CREATE_ACCOUNTS;
        final int creationLimit = CIOConfig.NETWORK_ADDRESS2ACCOUNT;
        if (CAccountContainer.getInstance().getIpCount(ip) >= loginIpLimit()) {
            rejectIp(client);
            return;
        }
        if (CLoginAttemptGuard.isBlocked(ip)) {
            sendWrongCredentials(client);
            return;
        }
        if (!allowCreation && CAccountDB.recentlyMissing(name)) {
            failCredentials(client, ip);
            return;
        }

        final CUserSocket.LoginDbLoad ticket = client.tryBeginLoginDbLoad(CConnectionState.CONNECTED, null);
        if (ticket == null) return;
        try {
            final int accountLane = CacheD.accountOrderKey(name);
            // Auto-creation shares the existing CacheD IP lane with other
            // registrations so the quota read and INSERT cannot race each other.
            final int creationLane = allowCreation ? CacheD.accountOrderKey("auth-create-ip:" + ip) : 0;
            CacheD.getInstance().submitDbJobOrdered(accountLane, creationLane, () -> {
                if (!client.isCurrentLoginDbLoad(ticket)) {
                    client.finishLoginDbLoad(ticket);
                    return;
                }

                final CAccountDB.LoginResult loaded;
                try {
                    loaded = CAccountDB.readLogin(name, password, ip, client.getHostname(), allowCreation, creationLimit);
                } catch (RuntimeException failure) {
                    rejectLoad(client, ticket, failure);
                    return;
                }

                try {
                    CIOThreadCallback.getInstance().completeSession(client, () -> {
                        if (!client.isCurrentLoginDbLoad(ticket)) return;
                        try {
                            if (finishLogin(client, ip, loaded)) {
                                client.finishLoginDbLoad(ticket);
                                authenticated.accept(client);
                            }
                        } catch (RuntimeException failure) {
                            LOG.log(Level.WARNING, "AuthD login response failed", failure);
                            client.close();
                        }
                    }, failure -> finishAuthLoad(client, ticket, failure));
                } catch (RuntimeException failure) {
                    finishAuthLoad(client, ticket, failure);
                }
            });
        } catch (RuntimeException failure) {
            rejectLoad(client, ticket, failure);
        }
    }

    private static void rejectLoad(CUserSocket client, CUserSocket.LoginDbLoad ticket, RuntimeException failure) {
        try {
            // Only the exact pending login may close this unauthenticated socket.
            synchronized (client) {
                if (client.isCurrentLoginDbLoad(ticket)) {
                    LOG.log(Level.WARNING, "AuthD account load failed", failure);
                    client.close();
                }
            }
        } finally {
            client.finishLoginDbLoad(ticket);
        }
    }

    private static void finishAuthLoad(CUserSocket client, CUserSocket.LoginDbLoad ticket, Throwable failure) {
        try {
            if (failure != null && client.isCurrentLoginDbLoad(ticket)) {
                LOG.log(Level.WARNING, "AuthD socket completion failed", failure);
            }
        } finally {
            client.finishLoginDbLoad(ticket);
        }
    }

    private static boolean finishLogin(CUserSocket client, String ip, CAccountDB.LoginResult loaded) {
        if (CLoginAttemptGuard.isBlocked(ip)) {
            sendWrongCredentials(client);
            return false;
        }
        switch (loaded.status()) {
            case NO_ACCOUNT, WRONG_PASSWORD -> failCredentials(client, ip);
            case CREATION_LIMIT -> SC_CommonNews.send(client, "더이상 계정을 생성 할 수 없습니다.(최대 "
                    + CIOConfig.NETWORK_ADDRESS2ACCOUNT + "개)");
            case INVALID_NAME -> SC_LoginResult.send(client, SC_LoginResult.REASON_WRONG_ACCOUNT);
            case INVALID_PASSWORD -> SC_LoginResult.send(client, SC_LoginResult.REASON_WRONG_PASSWORD);
            case AUTHENTICATED -> { return admitPasswordLogin(client, loaded.account(), ip); }
        }
        return false;
    }

    private static boolean admitPasswordLogin(CUserSocket client, Account account, String ip) {
        final int banned = account.getBannedCode();
        if (banned != 0) {
            SC_LoginResult.send(client, banned);
            return false;
        }
        try {
            if (!CAccountContainer.getInstance().loginWithinIpLimit(client, account, loginIpLimit())) {
                rejectIp(client);
                return false;
            }
            Account.updateLastActive(account, ip);
            publishAuthenticated(client, account);
            CLoginAttemptGuard.clear(ip);
            return true;
        } catch (GameServerFullException full) {
            SC_CommonNews.send(client, "서버 접속 인원이 가득 찼습니다.");
        } catch (AccountAlreadyLoginException duplicate) {
            SC_CommonNews.send(client, "이미 접속 중 입니다.");
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "AuthD session admission failed", failure);
        }
        client.close();
        return false;
    }

    /** Existing in-server transfers use the already validated account after the old owner retires. */
    public static void attachHandoff(CUserSocket client, Account account)
            throws GameServerFullException, AccountAlreadyLoginException {
        Objects.requireNonNull(client);
        Objects.requireNonNull(account);
        synchronized (client) {
            if (client.getAccount() != null || client.getActiveChar() != null || client.isLoginDbLoadPending()
                    || client.getConnectionState() != CConnectionState.CONNECTED)
                throw new IllegalStateException("AuthD handoff requires an unclaimed connection");
            CAccountContainer.getInstance().login(client, account);
            publishAuthenticated(client, account);
        }
    }

    private static void publishAuthenticated(CUserSocket client, Account account) {
        client.setAccount(account);
        client.setConnectionState(CConnectionState.AUTHENTICATED);
    }

    private static int loginIpLimit() {
        return Config.ALLOW_2PC ? Math.max(1, CIOConfig.NETWORK_CLIENT_PERMISSION) : 1;
    }

    private static void failCredentials(CUserSocket client, String ip) {
        CLoginAttemptGuard.onFail(ip);
        sendWrongCredentials(client);
    }

    private static void sendWrongCredentials(CUserSocket client) {
        SC_LoginResult.send(client, SC_LoginResult.REASON_USER_OR_PASS_WRONG);
    }

    private static void rejectIp(CUserSocket client) {
        try {
            SC_CommonNews.send(client, "현재 IP로 다른 계정이 이미 접속중입니다.");
        } finally {
            client.close();
        }
    }
}
