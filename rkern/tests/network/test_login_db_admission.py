"""Exercise auth, notice, character selection, and world-load admission with controlled DB/CIO queues."""
from pathlib import Path
import subprocess
import tempfile

src = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model/network/core"


def section(path, first, last):
    text = (src / path).read_text(encoding="utf-8")
    return text[text.index(first):text.index(last)]


socket = section("connection/CUserSocket.java",
                 "    private volatile boolean privateStoreRetirementPending;",
                 "    private volatile PlayerShopView playerShopView;")
auth_source = (src.parents[2] / "authd/CAuthD.java").read_text(encoding="utf-8")
auth = auth_source[auth_source.index("    public static void requestLogin("):
                   auth_source.index("    private static boolean finishLogin(")]
notice = section("cs/packet/CS_ReadNews.java", "    private static void sendNotice(", "\n}")
common = section("cs/packet/CS_CommonClick.java", "    private static final class ExpiredCharacter",
                 "    private static void completeCommonClick(")
delete = section("cs/packet/CS_CommonClick.java", "    private static void deleteExpiredCharacter(",
                 "    private static List<ExpiredCharacter> findExpired(")
enter = section("cs/packet/CS_EnterWorld.java", "    public static void doEnterWorld(",
                "    private static boolean currentPrivateStoreLogin(")

fixture = r"""
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoginDbAdmissionCheck {
    static final Logger _log = Logger.getAnonymousLogger();
    static final Logger LOG = _log;
    enum CConnectionState { CONNECTED, AUTHENTICATED, IN_GAME }
    record Account(String getName) {
        void tamUpdate(Account account) { }
        Account getTamHandler() { return this; }
        void refreshCacheOnDbWorker() { }
    }
    static int checks, authReads, authReplies, worldReads, worldReplies, discards, retirements;
    static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    static class CUserSocket {
        boolean connected = true;
        Account account;
        CConnectionState state = CConnectionState.CONNECTED;
        L1PcInstance actor;
        boolean isConnected() { return connected; }
        Account getAccount() { return account; }
        String getAccountName() { return account.getName(); }
        CConnectionState getConnectionState() { return state; }
        L1PcInstance getActiveChar() { return actor; }
        String getIp() { return "local-fixture"; }
        String getHostname() { return "local-fixture"; }
        int affinityShard() { return 0; }
        void close() { connected = false; }
        void kick() { close(); }
""" + socket + r"""
    }
    static class CacheD {
        static final CacheD INSTANCE = new CacheD();
        final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        final List<List<Integer>> lanes = new ArrayList<>();
        boolean reject;
        static CacheD getInstance() { return INSTANCE; }
        static int accountOrderKey(String owner) { return owner.hashCode(); }
        void submitDbJobOrdered(String name, Runnable job) { submitDbJobOrdered(accountOrderKey(name), job); }
        void submitDbJobOrdered(int owner, Runnable job) {
            lanes.add(List.of(owner));
            enqueue(job);
        }
        void submitDbJobOrdered(int owner, int account, Runnable job) {
            lanes.add(List.of(owner, account));
            enqueue(job);
        }
        void submitWarehouseLoginJob(int character, int account, int special, Runnable job) { enqueue(job); }
        void enqueue(Runnable job) {
            if (reject) throw new RejectedExecutionException();
            jobs.addLast(job);
        }
    }
    record OwnerJob(Runnable action, Consumer<Throwable> terminal) {
        void run() {
            try { action.run(); }
            catch (Throwable failure) { terminal.accept(failure); return; }
            terminal.accept(null);
        }
    }
    static class CIOThreadCallback {
        static final CIOThreadCallback INSTANCE = new CIOThreadCallback();
        final ArrayDeque<OwnerJob> jobs = new ArrayDeque<>();
        boolean reject, throwOnAdmission;
        static CIOThreadCallback getInstance() { return INSTANCE; }
        boolean completeSession(CUserSocket client, Runnable action, Consumer<Throwable> terminal) {
            if (throwOnAdmission) throw new IllegalStateException();
            if (reject) {
                terminal.accept(new RejectedExecutionException());
                return false;
            }
            jobs.addLast(new OwnerJob(action, terminal));
            return true;
        }
        boolean completeOnShard(int shard, Runnable action, Consumer<Throwable> terminal) {
            jobs.addLast(new OwnerJob(action, terminal));
            return true;
        }
    }
    static class Config { static final boolean AUTO_CREATE_ACCOUNTS = false; }
    static class CIOConfig { static final int NETWORK_ADDRESS2ACCOUNT = 2; }
    static class CAccountContainer {
        static CAccountContainer getInstance() { return new CAccountContainer(); }
        static String accountKey(String name) { return name; }
        int getIpCount(String ip) { return 0; }
    }
    static class CLoginAttemptGuard { static boolean isBlocked(String ip) { return false; } }
    static int loginIpLimit() { return 1; }
    static void rejectIp(CUserSocket client) { client.close(); }
    static void sendWrongCredentials(CUserSocket client) {}
    static void failCredentials(CUserSocket client, String ip) {}
    static class CAccountDB {
        record LoginResult(boolean valid) {}
        static boolean recentlyMissing(String name) { return false; }
        static LoginResult readLogin(String account, String password, String ip, String hostname,
                boolean allowCreation, int limit) {
            authReads++;
            return new LoginResult(password.equals("valid-fixture"));
        }
    }
    static boolean finishLogin(CUserSocket socket, String ip, CAccountDB.LoginResult loaded) {
        authReplies++;
        if (!loaded.valid()) return false;
        socket.account = new Account("fixture");
        socket.state = CConnectionState.AUTHENTICATED;
        return true;
    }
    static void handle(CUserSocket socket, String name, String password) {
        requestLogin(socket, name, password, client -> sendNotice(client, client.getAccountName()));
    }
    static class SC_CommonNews {
        record NoticeData(String message, String noticeId) { }
        static NoticeData next;
        static int reads, sends, acknowledgements;
        static boolean acceptsSend = true, closesDuringSend;
        static NoticeData loadUnreadNotice(String account) { reads++; return next; }
        static boolean send(CUserSocket client, String message) {
            sends++;
            if (closesDuringSend) client.close();
            return acceptsSend;
        }
        static void updateNoticeDate(String account, String noticeId) { acknowledgements++; }
    }
    static class L1PcInstance {
        final boolean shop;
        L1PcInstance(boolean shop) { this.shop = shop; }
        boolean isPrivateShop() { return shop; }
    }
    static class CWorld {
        static final CWorld INSTANCE = new CWorld();
        static CWorld getInstance() { return INSTANCE; }
        L1PcInstance getPlayer(String name) { return name.equals("shop") ? new L1PcInstance(true) : null; }
        L1Clan findClan(String name) { return new L1Clan(); }
    }
    static class L1Clan {
        static int removed;
        void removeClanMember(int id) { removed = id; }
    }
    static class CharacterTable {
        static CharacterTable getInstance() { return new CharacterTable(); }
        l1j.server.server.templates.L1CharName getCharName(String name) { return null; }
        void deleteCharacter(String account, String name) { CS_CommonClick.deleted.add(name); }
    }
    static final class CS_CommonClick {
        static final Logger LOG = _log;
        static List<ExpiredCharacter> expired = List.of();
        static final List<String> deleted = new ArrayList<>();
        static int scans, lists, writes;
        static boolean failScan, failList;
        static List<ExpiredCharacter> findExpired(String account) throws SQLException {
            scans++;
            if (failScan) throw new SQLException("unavailable");
            return expired;
        }
        static boolean isStillExpired(String account, ExpiredCharacter row) { return row.objectId == 701; }
        static class CharRow { }
        static List<CharRow> loadCharRows(String account) throws SQLException {
            lists++;
            if (failList) throw new IllegalStateException("Network pool outage");
            return List.of();
        }
        static void completeCommonClick(CUserSocket client, Account account, List<CharRow> rows) { writes++; }
""" + common + delete + r"""
    }
    static class EnterData { }
    static EnterData loadEnterData(String name, Account account) { worldReads++; return new EnterData(); }
    static void discardEnterData(EnterData data) { if (data != null) discards++; }
    static void completeEnterWorld(String name, CUserSocket client, Account account, EnterData data, Throwable failure) {
        worldReplies++;
        client.actor = new L1PcInstance(false);
        client.state = CConnectionState.IN_GAME;
    }
    static void retirePrivateStoreForLogin(String name, CUserSocket client, Account account, CWorld world, L1PcInstance shop) {
        if (client.tryBeginPrivateStoreRetirement()) retirements++;
    }
""" + auth + notice + enter + r"""
    private static void testNoticeAndList(CacheD db, CIOThreadCallback owner) {
        final var socket = new CUserSocket();
        socket.account = new Account("fixture");
        socket.state = CConnectionState.AUTHENTICATED;
        sendNotice(socket, "fixture");
        sendNotice(socket, "fixture");
        check(db.jobs.size() == 1, "duplicate notice while waiting for DB coalesced");
        db.jobs.removeFirst().run();
        sendNotice(socket, "fixture");
        check(db.jobs.isEmpty() && socket.isLoginDbLoadPending(), "notice owns the socket through CIO handoff");
        owner.jobs.removeFirst().run();
        sendNotice(socket, "fixture");
        check(db.jobs.size() == 1 && socket.isLoginDbLoadPending(), "no-notice callback transfers ticket to character list");
        final int oldAdmissions = db.lanes.size();
        final int account = CacheD.accountOrderKey("fixture");
        CS_CommonClick.expired = List.of(new CS_CommonClick.ExpiredCharacter(701, "expired", "clan"),
                                        new CS_CommonClick.ExpiredCharacter(702, "changed", null));
        db.jobs.removeFirst().run();
        check(CS_CommonClick.deleted.isEmpty() && db.jobs.size() == 3 && CS_CommonClick.lists == 0,
                "account-only scan defers both deletes and final list");
        check(db.lanes.subList(oldAdmissions, db.lanes.size()).equals(
                List.of(List.of(701, account), List.of(702, account), List.of(account))),
                "each expiry has character plus account lanes before ordered list");
        db.jobs.removeFirst().run();
        db.jobs.removeFirst().run();
        check(CS_CommonClick.deleted.equals(List.of("expired")), "changed expired candidate is kept");
        db.jobs.removeFirst().run();
        owner.jobs.removeFirst().run();
        owner.jobs.removeFirst().run();
        check(L1Clan.removed == 701 && CS_CommonClick.writes == 1 && !socket.isLoginDbLoadPending(),
                "exact expired member removed and character list replies once");

        CS_CommonClick.expired = List.of();
        CS_CommonClick.failList = true;
        new CS_CommonClick(socket);
        db.jobs.removeFirst().run();
        check(!socket.connected && !socket.isLoginDbLoadPending(), "pool exception closes current list and frees ticket");
        CS_CommonClick.failList = false;

        final var unavailable = new CUserSocket();
        unavailable.account = new Account("fixture");
        unavailable.state = CConnectionState.AUTHENTICATED;
        CS_CommonClick.failScan = true;
        final int listsBeforeFailure = CS_CommonClick.lists;
        new CS_CommonClick(unavailable);
        db.jobs.removeFirst().run();
        check(!unavailable.connected && !unavailable.isLoginDbLoadPending()
                && CS_CommonClick.lists == listsBeforeFailure, "failed expiry scan does not retry other login SQL");
        CS_CommonClick.failScan = false;

        final var stale = new CUserSocket();
        stale.account = new Account("fixture");
        stale.state = CConnectionState.AUTHENTICATED;
        new CS_CommonClick(stale);
        db.jobs.removeFirst().run();
        final var previous = owner.jobs.removeFirst();
        stale.account = new Account("fixture");
        previous.terminal().accept(new RejectedExecutionException());
        check(stale.connected && !stale.isLoginDbLoadPending(), "stale list cannot close replacement owner");

        SC_CommonNews.next = new SC_CommonNews.NoticeData("notice", "2");
        SC_CommonNews.acceptsSend = false;
        SC_CommonNews.closesDuringSend = true;
        sendNotice(stale, "fixture");
        db.jobs.removeFirst().run();
        owner.jobs.removeFirst().run();
        check(db.jobs.isEmpty() && !stale.isLoginDbLoadPending() && SC_CommonNews.acknowledgements == 1,
                "closing during notice send neither sets a read acknowledgement nor opens character list");
    }

    public static void main(String[] ignored) {
        _log.setUseParentHandlers(false);
        final CacheD db = CacheD.INSTANCE;
        final CIOThreadCallback owner = CIOThreadCallback.INSTANCE;
        final CUserSocket socket = new CUserSocket();
        handle(socket, "fixture", "wrong-fixture");
        handle(socket, "fixture", "wrong-fixture");
        check(db.jobs.size() == 1, "one auth read per session");
        db.jobs.removeFirst().run();
        handle(socket, "fixture", "wrong-fixture");
        check(db.jobs.isEmpty() && authReads == 1 && socket.isLoginDbLoadPending(), "wait through CIO completion");
        owner.jobs.removeFirst().run();
        check(authReplies == 1 && !socket.isLoginDbLoadPending() && socket.connected, "auth retry on valid session");

        owner.reject = true;
        handle(socket, "fixture", "valid-fixture");
        db.jobs.removeFirst().run();
        check(!socket.isLoginDbLoadPending() && socket.connected, "auth retry after callback rejection");
        owner.reject = false;
        handle(socket, "fixture", "valid-fixture");
        db.jobs.removeFirst().run();
        owner.jobs.removeFirst().run();
        check(socket.state == CConnectionState.AUTHENTICATED && socket.isLoginDbLoadPending(),
                "successful auth transfers admission through original terminal callback");
        SC_CommonNews.next = new SC_CommonNews.NoticeData("notice", "1");
        db.jobs.removeFirst().run();
        owner.jobs.removeFirst().run();
        db.jobs.removeFirst().run();
        SC_CommonNews.next = null;
        handle(socket, "fixture", "valid-fixture");
        check(db.jobs.isEmpty(), "no auth load for established account");

        doEnterWorld("hero", socket);
        doEnterWorld("hero", socket);
        doEnterWorld("shop", socket);
        check(db.jobs.size() == 1 && retirements == 0, "world request excludes duplicate and detached shop retirement");
        db.jobs.removeFirst().run();
        check(!socket.tryBeginPrivateStoreRetirement() && socket.isLoginDbLoadPending(), "shop admission and world read are exclusive");
        owner.jobs.removeFirst().run();
        check(socket.actor != null && worldReads == 1 && worldReplies == 1 && discards == 1
                && !socket.isLoginDbLoadPending(), "world completion released and snapshot discarded");

        final var stale = new CUserSocket();
        handle(stale, "fixture", "valid-fixture");
        db.jobs.removeFirst().run();
        final var delayedAuth = owner.jobs.removeFirst();
        stale.state = CConnectionState.AUTHENTICATED;
        stale.account = new Account("replacement");
        delayedAuth.run();
        check(authReplies == 2 && stale.connected && !stale.isLoginDbLoadPending(), "no auth reply after stage or account changed");
        doEnterWorld("hero", stale);
        db.jobs.removeFirst().run();
        final var delayedEnter = owner.jobs.removeFirst();
        stale.account = new Account("another-owner");
        delayedEnter.terminal().accept(new RejectedExecutionException());
        check(stale.connected && !stale.isLoginDbLoadPending(), "late callback cannot close replacement session");
        doEnterWorld("hero", stale);
        delayedEnter.terminal().accept(new RejectedExecutionException());
        check(stale.connected && stale.isLoginDbLoadPending(), "late callback cannot retire newer reservation");
        owner.throwOnAdmission = true;
        db.jobs.removeFirst().run();
        check(!stale.connected && !stale.isLoginDbLoadPending(), "world ticket released on completion admission exception");
        owner.throwOnAdmission = false;

        final var rejected = new CUserSocket();
        db.reject = true;
        handle(rejected, "fixture", "valid-fixture");
        check(!rejected.connected && !rejected.isLoginDbLoadPending(), "DB admission failure releases auth ticket");
        db.reject = false;
        testNoticeAndList(db, owner);
        System.out.println("PASS login database handoff and admission (" + checks + " checks)");
    }
}
"""

with tempfile.TemporaryDirectory() as tmp:
    work = Path(tmp)
    symbol = work / "l1j/server/server/templates/L1CharName.java"
    symbol.parent.mkdir(parents=True)
    symbol.write_text("""package l1j.server.server.templates;
public class L1CharName { public int getId() { return 1; } public String getName() { return "fixture"; } }
""", encoding="utf-8")
    main = work / "LoginDbAdmissionCheck.java"
    main.write_text(fixture, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(main), str(symbol)], check=True)
    subprocess.run(["java", "-cp", tmp, "LoginDbAdmissionCheck"], check=True)
