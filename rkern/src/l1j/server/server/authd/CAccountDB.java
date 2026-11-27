package l1j.server.server.authd;

import java.sql.SQLException;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.utils.GameUtils;

/** Credential and registration I/O. Called only from the CacheD account lane. */
final class CAccountDB {
    enum Status { AUTHENTICATED, NO_ACCOUNT, WRONG_PASSWORD, CREATION_LIMIT, INVALID_NAME, INVALID_PASSWORD }

    record LoginResult(Account account, Status status) { }

    private static final Cache<String, Boolean> MISSING = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    private CAccountDB() { }

    static boolean recentlyMissing(String name) {
        return MISSING.getIfPresent(name) != null;
    }

    static LoginResult readLogin(String name, String password, String ip, String host,
            boolean allowCreation, int creationLimit) {
        Account account = Account.load(name);
        if (account == null) {
            if (!allowCreation) {
                MISSING.put(name, Boolean.TRUE);
                return new LoginResult(null, Status.NO_ACCOUNT);
            }
            if (creationLimitReached(ip, creationLimit)) return new LoginResult(null, Status.CREATION_LIMIT);
            if (!GameUtils.isLetterOrDigitString(name, 5, 12)) return new LoginResult(null, Status.INVALID_NAME);
            if (!GameUtils.isLetterOrDigitString(password, 6, 16)) return new LoginResult(null, Status.INVALID_PASSWORD);

            Account.create(name, password, ip, host);
            account = Account.load(name);
            if (account == null) throw new IllegalStateException("Created account could not be loaded");
        }

        MISSING.invalidate(name);
        return account.validatePassword(name, password)
                ? new LoginResult(account, Status.AUTHENTICATED)
                : new LoginResult(null, Status.WRONG_PASSWORD);
    }

    private static boolean creationLimitReached(String ip, int limit) {
        if (limit <= 0) return true;
        try (var con = DatabaseFactory.getInstance().getConnection();
                var query = con.prepareStatement("SELECT 1 FROM accounts WHERE ip=? LIMIT ?,1")) {
            query.setString(1, ip);
            query.setInt(2, limit - 1);
            try (var rows = query.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Account creation quota unavailable", failure);
        }
    }
}
