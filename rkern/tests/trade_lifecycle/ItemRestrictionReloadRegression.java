import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.NoTradable;

/** Fresh JVM. Inject a JDBC cursor failure after one row, and a failure on result close. */
public final class ItemRestrictionReloadRegression {
    private static final class TestPool extends HikariDataSource {
        int[] ids = {101, 102};
        boolean failRead, failClose;

        @Override public Connection getConnection() {
            return proxy(Connection.class, (ignored, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> proxy(PreparedStatement.class, (ps, op, parameters) -> switch (op.getName()) {
                    case "executeQuery" -> rows();
                    case "close" -> null;
                    default -> throw new AssertionError(op);
                });
                case "close" -> null;
                default -> throw new AssertionError(method);
            });
        }

        private ResultSet rows() {
            int[] index = {-1};
            return proxy(ResultSet.class, (ignored, method, args) -> switch (method.getName()) {
                case "next" -> {
                    if (++index[0] == 1 && failRead) throw new SQLException("partial restriction result");
                    yield index[0] < ids.length;
                }
                case "getInt" -> ids[index[0]];
                case "close" -> {
                    if (failClose) throw new SQLException("restriction result close failed");
                    yield null;
                }
                default -> throw new AssertionError(method);
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Incorrect restriction snapshot"); }

    public static void main(String[] args) throws Exception {
        var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        field.setAccessible(true);
        try (var pool = new TestPool()) {
            field.set(null, pool);
            var rules = NoTradable.getInstance();
            check(rules.isNoTradable(101) && rules.isNoTradable(102));
            pool.ids = new int[]{201, 202};
            pool.failRead = true;
            rules.reload();
            check(rules.isNoTradable(101) && rules.isNoTradable(102) && !rules.isNoTradable(201));
            pool.failRead = false;
            pool.failClose = true;
            rules.reload();
            check(rules.isNoTradable(101) && !rules.isNoTradable(202));
            pool.failClose = false;
            rules.reload();
            check(!rules.isNoTradable(101) && !rules.isNoTradable(102));
            check(NoTradable.blocksPlayerTransfer(false, 201) && NoTradable.blocksPlayerTransfer(false, 202)
                    && !NoTradable.blocksPlayerTransfer(true, 201));
        } finally { field.set(null, null); }
        System.out.println("PASS restriction read/close failures retain full last policy until successful reload");
    }
}
