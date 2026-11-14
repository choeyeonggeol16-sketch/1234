package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.AccountAlreadyLoginException;
import l1j.server.server.GameServerFullException;
import l1j.server.server.Controller.LoginAttemptGuard;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.datatables.IpConnectDelay;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.model.network.core.sc.packet.SC_CommonNews;

import l1j.server.server.utils.MJCommons;

public final class CS_AuthLogin {

    private static final Logger _log = Logger.getLogger(CS_AuthLogin.class.getName());

    private static final Cache<String, Boolean> _missingAccounts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    private CS_AuthLogin() {
    }

    public static void handle(CIOClient client, String rawAccountName, String password) {
        if (client == null) {
            return;
        }

        final String accountName =
                rawAccountName != null ? rawAccountName.toLowerCase().trim() : null;

        if (accountName == null || password == null ||
                accountName.isEmpty() || password.isEmpty()) {

            client.close();
            return;
        }

        final String ip = client.getIp();
        final LoginController lc = LoginController.getInstance();
        final int count = lc.getIpCount(ip);
        if (!isWhitelistedIp(ip)) {
            if ((!Config.ALLOW_2PC && count > 0)
                    || (Config.ALLOW_2PC && count > ConnectionConfig.NETWORK_CLIENT_PERMISSION
                        && !ip.equals(ConnectionConfig.NETWORK_ADDRESS2ACCOUNT))) {
                try { processDuplication(client, accountName, ip); }
                catch (Exception e) { client.close(); }
                return;
            }
            if (LoginAttemptGuard.isBlocked(ip)) {
                SC_LoginResult.send(client, SC_LoginResult.REASON_USER_OR_PASS_WRONG);
                return;
            }
        }
        if (isRecentlyMissing(accountName)) {
            SC_LoginResult.send(client, SC_LoginResult.REASON_USER_OR_PASS_WRONG);
            return;
        }

        try {
            CacheD.getInstance().submitDbJobOrdered(accountName, () -> {
                final AuthLoad loaded;
                final Throwable failure;
                AuthLoad result = null;
                Throwable error = null;
                try { result = readAuth(accountName, password, client.getIp(), client.getHostname()); }
                catch (Throwable e) { error = e; }
                loaded = result;
                failure = error;
                if (!CIOThreadCallback.getInstance().completeSession(client, () -> {
                    if (!client.isConnected()) return;
                    if (failure != null) {
                        _log.log(Level.SEVERE, "Auth DB load failed: " + accountName, failure);
                        client.close();
                        return;
                    }
                    try {
                        if (isAuthority(client, accountName, loaded))
                            sendNotice(client, client.getAccountName());
                    } catch (Exception e) {
                        _log.log(Level.WARNING, "RAuthLoginHandler flow exception", e);
                        client.close();
                    }
                })) {
                    _log.warning("Auth DB callback rejected: " + accountName);
                }
            });
        } catch (RuntimeException e) {
            _log.log(Level.SEVERE, "Auth DB request rejected: " + accountName, e);
            client.close();
        }
    }

    private static final class AuthLoad {
        Account account;
        boolean missing;
        boolean passwordValid;
        int errorCode;
    }

    private static AuthLoad readAuth(String accountName, String password, String ip, String hostname) {
        AuthLoad result = new AuthLoad();
        Account account = Account.load(accountName);
        if (account == null) {
            result.missing = true;
            if (Config.AUTO_CREATE_ACCOUNTS) {
                if (getAccountIpCount(ip) >= ConnectionConfig.NETWORK_ADDRESS2ACCOUNT)
                    result.errorCode = 1;
                else if (!MJCommons.isLetterOrDigitString(accountName, 5, 12))
                    result.errorCode = 2;
                else if (!MJCommons.isLetterOrDigitString(password, 6, 16))
                    result.errorCode = 3;
                else {
                    Account.create(accountName, password, ip, hostname);
                    account = Account.load(accountName);
                }
            }
        }
        result.account = account;
        if (account != null) result.passwordValid = account.validatePassword(accountName, password);
        return result;
    }

    private static boolean isAuthority(
            CIOClient client,
            String accountName,
            AuthLoad loaded) throws Exception {

        final String ip = client.getIp();
        final LoginController lc = LoginController.getInstance();
        final int count = lc.getIpCount(ip);

        if (!isWhitelistedIp(ip)) {
            if (!Config.ALLOW_2PC) {
                if (count > 0) {
                    processDuplication(client, accountName, ip);
                    return false;
                }
            } else if (count > ConnectionConfig.NETWORK_CLIENT_PERMISSION &&
                    !ip.equals(ConnectionConfig.NETWORK_ADDRESS2ACCOUNT)) {

                processDuplication(client, accountName, ip);
                return false;
            }
        }

        if (!isWhitelistedIp(ip) && LoginAttemptGuard.isBlocked(ip)) {
            _log.warning(String.format(
                    "로그인 반복 실패 IP 차단. account=%s ip=%s",
                    accountName, ip));

            SC_LoginResult.send(client, 
                            SC_LoginResult.REASON_USER_OR_PASS_WRONG);

            return false;
        }

        if (isRecentlyMissing(accountName)) {
            SC_LoginResult.send(client, 
                            SC_LoginResult.REASON_USER_OR_PASS_WRONG);

            return false;
        }

        if (loaded.missing) {
            markMissing(accountName);
            if (loaded.account != null) _missingAccounts.invalidate(accountName);
        }
        if (loaded.errorCode == 1) {
            processAccountOver(client, accountName);
            return false;
        }
        if (loaded.errorCode == 2 || loaded.errorCode == 3) {
            SC_LoginResult.send(client, loaded.errorCode == 2
                    ? SC_LoginResult.REASON_WRONG_ACCOUNT : SC_LoginResult.REASON_WRONG_PASSWORD);
            return false;
        }
        final Account account = loaded.account;
        if (account == null) {
            SC_LoginResult.send(client, SC_LoginResult.REASON_USER_OR_PASS_WRONG);
            return false;
        }
        if (!loaded.passwordValid) {
            LoginAttemptGuard.onFail(ip);
            SC_LoginResult.send(client, SC_LoginResult.REASON_USER_OR_PASS_WRONG);
            return false;
        }

        final int reason = account.getBannedCode();

        if (reason != 0) {
            _log.info(String.format(
                    "압류 계정 로그인 거부. account=%s ip=%s",
                    accountName, ip));

            SC_LoginResult.send(client, reason);

            return false;
        }

        try {
            lc.login(client, account);

            Account.updateLastActive(account, ip);

            client.setConnectionState(
                    ConnectionState.AUTHENTICATED);

            client.setAccount(account);

            LoginAttemptGuard.clear(ip);

        } catch (GameServerFullException e) {

            _log.info(String.format(
                    "최대 접속인원 초과 : (%s) 로그인을 절단했습니다.",
                    ip));

            SC_CommonNews.send(client, 
                            "서버 접속 인원이 가득 찼습니다.");

            client.close();
            return false;

        } catch (AccountAlreadyLoginException e) {

            _log.info(String.format(
                    "동일한 ID의 접속 : (%s) 기존 세션 킥 예약됨.",
                    ip));

            SC_CommonNews.send(client, 
                            "이미 접속 중 입니다.");

            client.close();
            return false;

        } catch (Exception e) {

            _log.info(String.format(
                    "비정상적인 로그인 에러. account=%s host=%s",
                    accountName,
                    client.getHostname()));

            client.close();
            return false;
        }

        return true;
    }

    private static boolean isRecentlyMissing(String account) {
        return _missingAccounts.getIfPresent(account) != null;
    }

    private static void markMissing(String account) {
        _missingAccounts.put(account, Boolean.TRUE);
    }

    private static boolean isWhitelistedIp(String ip) {
        if (ip == null) {
            return false;
        }

        try {
            return ip.equals(Config.허가ip1)
                    || ip.equals(Config.허가ip2)
                    || ip.equals(Config.허가ip3)
                    || ip.equals(Config.허가ip4)
                    || ip.equals(Config.허가ip5)
                    || ip.equals(Config.허가ip6)
                    || ip.equals(Config.허가ip7)
                    || ip.equals(Config.허가ip8);

        } catch (Exception e) {
            return false;
        }
    }

    public static void sendNotice(CIOClient client, String account) {
        if (client == null || !client.isConnected()) return;
        if (account == null || account.isEmpty()) {
            new CS_CommonClick(client);
            return;
        }
        try {
            CacheD.getInstance().submitDbJobOrdered(account, () -> {
                try {
                    final SC_CommonNews.NoticeData notice = SC_CommonNews.NoticeCount(account) > 0
                            ? SC_CommonNews.loadNotice(account) : null;
                    if (!CIOThreadCallback.getInstance().completeSession(client, () -> {
                        if (!client.isConnected() || !account.equals(client.getAccountName())) return;
                        if (notice != null) {
                            SC_CommonNews.send(client, notice.message);
                            final String noticeId = notice.noticeId;
                            if (noticeId != null) {
                                try {
                                    CacheD.getInstance().submitDbJobOrdered(account, () ->
                                            SC_CommonNews.updateNoticeDate(account, noticeId));
                                } catch (RuntimeException e) {
                                    _log.log(Level.WARNING, "Notice read-state update rejected: " + account, e);
                                }
                            }
                        } else new CS_CommonClick(client);
                    })) {
                        _log.warning("Notice DB callback rejected: " + account);
                    }
                } catch (Exception e) {
                    _log.log(Level.SEVERE, "Notice DB failed: " + account, e);
                    CIOThreadCallback.getInstance().completeSession(client, client::close);
                }
            });
        } catch (RuntimeException e) {
            _log.log(Level.SEVERE, "Notice DB request rejected: " + account, e);
            client.close();
        }
    }

    private static int getAccountIpCount(String ip) {
        final String sql =
                "SELECT count(ip) as cnt FROM accounts WHERE ip=?";

        try (Connection con =
                     DatabaseFactory.getInstance().getConnection();

             PreparedStatement pstm =
                     con.prepareStatement(sql)) {

            pstm.setString(1, ip);

            try (ResultSet rs = pstm.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }

        } catch (Exception e) {
            // A failed quota SELECT is not evidence that the IP owns zero
            // accounts. Abort auto-creation instead of bypassing the limit.
            throw new IllegalStateException("Account IP quota unavailable: " + ip, e);
        }

        throw new IllegalStateException("Account IP quota query returned no count: " + ip);
    }

    private static void processDuplication(
            CIOClient client,
            String accountName,
            String ip) throws Exception {

        _log.info(String.format(
                "동일 IP 다중 계정 로그인 거부. account=%s, ip=%s",
                accountName, ip));

        SC_CommonNews.send(client, 
                        "현재 IP로 다른 계정이 이미 접속중입니다.");

        client.close();
    }

    private static void processAccountOver(
            CIOClient client,
            String accountName) {

        _log.info(String.format(
                "계정생성 초과. 계정=%s 최대=%d",
                accountName,
                ConnectionConfig.NETWORK_ADDRESS2ACCOUNT));

        SC_CommonNews.send(client, 
                        String.format(
                                "더이상 계정을 생성 할 수 없습니다.(최대 %d개)",
                                ConnectionConfig.NETWORK_ADDRESS2ACCOUNT));
    }
}