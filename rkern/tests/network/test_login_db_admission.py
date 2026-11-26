"""Exercise actual auth/world-load admission and socket tickets with controlled DB/CIO queues."""
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
auth = section("cs/packet/CS_AuthLogin.java", "    public static void handle(",
               "    private static final class AuthLoad")
enter = section("cs/packet/CS_EnterWorld.java", "    public static void doEnterWorld(",
                "    private static boolean currentPrivateStoreLogin(")

fixture = r"""
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoginDbAdmissionCheck {
    static final Logger _log = Logger.getAnonymousLogger();
    enum CConnectionState { CONNECTED, AUTHENTICATED, IN_GAME }
    record Account(String getName) { }
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
        void close() { connected = false; }
        void kick() { close(); }
""" + socket + r"""
    }
    static class CacheD {
        static final CacheD INSTANCE = new CacheD();
        final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        boolean reject;
        static CacheD getInstance() { return INSTANCE; }
        static int accountOrderKey(String owner) { return owner.hashCode(); }
        void submitDbJobOrdered(String name, Runnable job) { enqueue(job); }
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
    }
    static class Config { static final boolean ALLOW_2PC = false; }
    static class CIOConfig {
        static final int NETWORK_CLIENT_PERMISSION = 1;
        static final String NETWORK_ADDRESS2ACCOUNT = "";
    }
    static class LoginController {
        static LoginController getInstance() { return new LoginController(); }
        int getIpCount(String ip) { return 0; }
    }
    static class LoginAttemptGuard { static boolean isBlocked(String ip) { return false; } }
    static class SC_LoginResult {
        static final int REASON_USER_OR_PASS_WRONG = 1;
        static void send(CUserSocket socket, int reason) { }
    }
    static void processDuplication(CUserSocket socket, String account, String ip) { }
    static boolean isRecentlyMissing(String account) { return false; }
    record AuthLoad(boolean valid) { }
    static AuthLoad readAuth(String account, String password, String ip, String hostname) {
        authReads++;
        return new AuthLoad(password.equals("valid-fixture"));
    }
    static boolean isAuthority(CUserSocket socket, String account, AuthLoad loaded) {
        authReplies++;
        if (!loaded.valid()) return false;
        socket.account = new Account(account);
        socket.state = CConnectionState.AUTHENTICATED;
        return true;
    }
    static void sendNotice(CUserSocket client, String account) { }
    static class L1PcInstance {
        final boolean shop;
        L1PcInstance(boolean shop) { this.shop = shop; }
        boolean isPrivateShop() { return shop; }
    }
    static class CWorld {
        static final CWorld INSTANCE = new CWorld();
        static CWorld getInstance() { return INSTANCE; }
        L1PcInstance getPlayer(String name) { return name.equals("shop") ? new L1PcInstance(true) : null; }
    }
    static class CharacterTable {
        static CharacterTable getInstance() { return new CharacterTable(); }
        l1j.server.server.templates.L1CharName getCharName(String name) { return null; }
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
""" + auth + enter + r"""
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
        check(socket.state == CConnectionState.AUTHENTICATED && !socket.isLoginDbLoadPending(), "successful auth released");
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
