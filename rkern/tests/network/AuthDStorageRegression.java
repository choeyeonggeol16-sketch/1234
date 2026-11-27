package l1j.server.server.model.network.core.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.authd.CAccountContainer;
import l1j.server.server.authd.CAuthD;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.storage.CacheD;

import static l1j.server.server.model.network.core.connection.LoginRegistryRegression.await;
import static l1j.server.server.model.network.core.connection.LoginRegistryRegression.check;
import static l1j.server.server.model.network.core.connection.LoginRegistryRegression.connect;

/** Run against an isolated DB. Arguments are the server JDBC URL, user and accounts-table DDL file. */
public final class AuthDStorageRegression {
    public static void main(String[] args) throws Exception {
        var maps = Class.forName("l1j.server.server.model.WorldShapeRegression")
                .getDeclaredMethod("installMapDefinitions");
        maps.setAccessible(true);
        maps.invoke(null);
        final String schema = "rkern_authd_" + UUID.randomUUID().toString().replace("-", "");
        final var poolField = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        poolField.setAccessible(true);
        try (Connection admin = DriverManager.getConnection(args[0], args[1], "")) {
            sql(admin, "CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                sql(admin, Files.readString(Path.of(args[2])));
                sql(admin, "INSERT INTO accounts (login,password,ip,charslot,gamepassword) VALUES ('known','fixturePass','127.0.0.2',4,0)");
                var cfg = new HikariConfig();
                cfg.setJdbcUrl(args[0]);
                cfg.setUsername(args[1]);
                cfg.setConnectionInitSql("USE " + schema);
                cfg.setMaximumPoolSize(4);
                cfg.setMinimumIdle(0);
                try (var pool = new HikariDataSource(cfg)) {
                    poolField.set(null, pool);
                    try { verifyAuth(admin); }
                    finally {
                        CacheD.getInstance().shutdown(5000);
                        poolField.set(null, null);
                    }
                }
            } finally { sql(admin, "DROP DATABASE " + schema); }
        }
        System.out.println("PASS AuthD DB-to-CIO login, wrong-password release, account/IP admission and logout");
    }

    private static void verifyAuth(Connection admin) throws Exception {
        final int limit = 16;
        Config.MAX_ONLINE_USERS = limit;
        Config.ALLOW_2PC = true;
        Config.Characters_CharSlot = 4;
        CPacketFilter.getInstance().load();

        final var owners = CIOThreadCallback.getInstance();
        final var bind = new InetSocketAddress("127.0.0.1", 0);
        final var config = new CIOConfig(bind);
        CIOConfig.NETWORK_CLIENT_PERMISSION = limit;
        CIOConfig.NETWORK_ADDRESS2ACCOUNT = 1;
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        final int count = owners.bindNetworkIngress(config);
        final var buffers = new CIOBufferPool(limit, limit * 2, limit, limit, count);
        final var sockets = CIOServer.class.getDeclaredField("sockets");
        sockets.setAccessible(true);
        try (var listener = new CIOServer(bind, limit, count, limit, buffers, owners, config)) {
            Config.AUTO_CREATE_ACCOUNTS = false;
            try (var peer = new Socket()) {
                var client = connect(listener, peer, sockets);
                CAuthD.requestLogin(client, " KNOWN ", "incorrect", ignored -> {
                    throw new AssertionError("failed credentials published a login");
                });
                await(() -> !client.isLoginDbLoadPending(), "wrong-password ticket released");
                check(client.isConnected() && client.getAccount() == null, "failed credentials have no session");

                var attached = new CountDownLatch(1);
                CAuthD.requestLogin(client, " KNOWN ", "fixturePass", owner -> {
                    check(CIOThreadCallback.currentShardIndex() == owner.affinityShard(), "publication runs on the socket owner");
                    check(owner.getConnectionState() == CConnectionState.AUTHENTICATED
                            && CAccountContainer.getInstance().getClientByAccount("known") == owner,
                            "verified owner was published before client notification");
                    attached.countDown();
                });
                check(attached.await(5, TimeUnit.SECONDS), "real auth worker completed");
                peer.close();
                await(client::isClientCleanupComplete, "real auth socket retired");
            }
            check(CacheD.getInstance().flushAccount("known", 5000), "known account update drained");
            sql(admin, "UPDATE accounts SET ip='127.0.0.2' WHERE login='known'");

            Config.AUTO_CREATE_ACCOUNTS = true;
            try (var a = new Socket(); var b = new Socket();
                    var requests = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = connect(listener, a, sockets);
                var second = connect(listener, b, sockets);
                var start = new CountDownLatch(1);
                var f = requests.submit(() -> { start.await(); CAuthD.requestLogin(first, "autoone", "fixturePass", x -> {}); return null; });
                var g = requests.submit(() -> { start.await(); CAuthD.requestLogin(second, "autotwo", "fixturePass", x -> {}); return null; });
                start.countDown();
                f.get(5, TimeUnit.SECONDS);
                g.get(5, TimeUnit.SECONDS);
                await(() -> !first.isLoginDbLoadPending() && !second.isLoginDbLoadPending(), "parallel creation completed");
                check((first.getAccount() == null) != (second.getAccount() == null), "only one same-IP registration succeeds");
                try (var statement = admin.createStatement();
                        var rows = statement.executeQuery("SELECT COUNT(*) FROM accounts WHERE ip='127.0.0.1'")) {
                    check(rows.next() && rows.getInt(1) == 1, "quota read and insert are serialized across accounts");
                }
                a.close();
                b.close();
                await(first::isClientCleanupComplete, "first creator retired");
                await(second::isClientCleanupComplete, "second creator retired");
                check(CAccountContainer.getInstance().getOnlinePlayerCount() == 0, "authenticated sessions drained");
            }
        } finally {
            owners.shutdown();
            check(owners.awaitTermination(5, TimeUnit.SECONDS), "CIO terminated");
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session cleanup drained");
        }
    }

    private static void sql(Connection db, String statement) throws Exception {
        try (var stmt = db.createStatement()) { stmt.execute(statement); }
    }
}
