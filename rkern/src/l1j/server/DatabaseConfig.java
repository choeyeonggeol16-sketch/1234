package l1j.server;

import java.util.logging.Logger;

import l1j.server.server.utils.ConfigReader;

/**
 * 모든 데이터베이스 관련 설정을 로드하는 클래스입니다.
 */
public class DatabaseConfig
{
	// 파일 경로 설정
	private static final String DATABASE_CONFIG_FILE = "./config/Database.ini";
	private static final Logger LOG = Logger.getLogger(DatabaseConfig.class.getName());
	
	// 설정 상수들
	public static String DATABASE_DRIVER;
	public static String DATABASE_URL;
	public static String DATABASE_LOGIN;
	public static String DATABASE_PASSWORD;
	public static int DATABASE_MAX_CONNECTIONS;
	public static int DATABASE_CONNECT_TIMEOUT_MS;
	public static int DATABASE_SOCKET_TIMEOUT_MS;
	public static boolean DATABASE_TEST_CONNECTIONS;
	
	public static void load() {
		final ConfigReader config = new ConfigReader(DATABASE_CONFIG_FILE);
		DATABASE_DRIVER = config.getString("Driver", "com.mysql.jdbc.Driver");
		DATABASE_URL = config.getString("URL", "jdbc:mysql://localhost/net");
		DATABASE_LOGIN = config.getString("Login", "root");
		DATABASE_PASSWORD = config.getString("Password", "");
		DATABASE_MAX_CONNECTIONS = config.getInt("MaximumDatabaseConnections", 10);
		DATABASE_TEST_CONNECTIONS = config.getBoolean("TestDatabaseConnections", false);
		DATABASE_CONNECT_TIMEOUT_MS = positiveTimeout(config, "ConnectTimeoutMillis", 5000);
		DATABASE_SOCKET_TIMEOUT_MS = positiveTimeout(config, "SocketTimeoutMillis", 30000);
	}

	private static int positiveTimeout(ConfigReader config, String key, int defaultValue) {
		final int value = config.getInt(key, defaultValue);
		if (value > 0) return value;
		LOG.warning(key + " must be positive. Using " + defaultValue + " ms.");
		return defaultValue;
	}
}