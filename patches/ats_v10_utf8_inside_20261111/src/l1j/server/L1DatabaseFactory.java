/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.configure.Config;
import l1j.server.server.utils.LeakCheckedConnection;
import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 * DB에의 액세스하기 위한 각종 인터페이스를 제공한다.
 */
public class L1DatabaseFactory {
	
	/** DB접속 정보를 집계한 것?  */
	private ComboPooledDataSource _source;

	/** 메세지 로그용.  */
	private static Logger _log = Logger.getLogger(L1DatabaseFactory.class.getName());

	/* DB에의 액세스에 필요한 각 정보 */
	/** DB접속 드라이버.  */
	private static String _driver;

	/** DB서버의 URL.  */
	private static String _url;

	/** DB서버에 접속하는 유저명.  */
	private static String _user;

	/** DB서버에 접속하는 패스워드.  */
	private static String _password;
	
	private static L1DatabaseFactory _instance = null;
	/**
	 * @return L1DatabaseFactory
	 * @throws SQLException
	 */public static L1DatabaseFactory getInstance() throws SQLException {
		if(_instance == null) {
			synchronized (L1DatabaseFactory.class) {
				if(_instance  == null) {
					_instance = new L1DatabaseFactory();
				}
			}
		}	
		return _instance ;
	}
	
	
	/**
	 * DB에의 액세스에 필요한 정보 설정
	 * 
	 * @param driver
	 *            DB접속 드라이버
	 * @param url
	 *            DB서버의 URL
	 * @param user
	 *            DB서버에 접속하는 유저명
	 * @param password
	 *            DB서버에 접속하는 패스워드
	 */
	public static void setDatabaseSettings(final String driver,
			final String url, final String user, final String password) {
		_driver = driver;
		_url = url;
		_user = user;
		_password = password;
	}

	/**
	 * DB접속의 정보의 설정과 테스트 접속을 한다.
	 *
	 * @throws SQLException
	 */
	private L1DatabaseFactory() throws SQLException {
		try {
			// DatabaseFactory을 L2J로부터 일부를 제외해 배차
			_source = new ComboPooledDataSource();
			_source.setDriverClass(_driver);
			_source.setJdbcUrl(_url);
			_source.setUser(_user);
			_source.setPassword(_password);
			_source.setInitialPoolSize(10);
			_source.setMinPoolSize(10);
			_source.setMaxPoolSize(100);
			_source.setAcquireIncrement(5);
			_source.setAcquireRetryAttempts(30);
			_source.setAcquireRetryDelay(1000);
			_source.setIdleConnectionTestPeriod(60);
			_source.setPreferredTestQuery("SELECT 1");
			_source.setTestConnectionOnCheckin(true);
			_source.setTestConnectionOnCheckout(false);

			/* Test the connection */
			_source.getConnection().close();
		} catch (SQLException x) {
			_log.fine("Database Connection FAILED");
			// rethrow the exception
			throw x;
		} catch (Exception e) {
			_log.fine("Database Connection FAILED");
			throw new SQLException("could not init DB connection:" + e);
		}
	}

	/**
	 * 서버 슛다운시에 DBconnection를 절단 한다.
	 */
	public void shutdown() {
		try {
			_source.close();
		} catch (Exception e) {
			_log.log(Level.INFO, "", e);
		}
		try {
			_source = null;
		} catch (Exception e) {
			_log.log(Level.INFO, "", e);
		}
	}

	/**
	 * DB접속을 해, connection 오브젝트를 돌려준다.
	 * 
	 * @return Connection connection 오브젝트
	 * @throws SQLException
	 */
    /** ATS uses bounded, independent MySQL connections rather than the legacy infinite retry loop. */
    public Connection getAtsConnection() throws SQLException {
        java.util.Properties properties = new java.util.Properties();
        if (_user != null) properties.setProperty("user", _user);
        if (_password != null) properties.setProperty("password", _password);
        properties.setProperty("connectTimeout", "3000");
        properties.setProperty("socketTimeout", "3000");
        return java.sql.DriverManager.getConnection(_url, properties);
    }

	public Connection getConnection() {
		Connection con = null;

		while (con == null) {
			try {
				con = _source.getConnection();
			} catch (SQLException e) {
				_log.warning("L1DatabaseFactory: getConnection() failed, trying again " + e);
			}
		}
		return Config.DETECT_DB_RESOURCE_LEAKS ?  LeakCheckedConnection.create(con) : con;
	}
}
