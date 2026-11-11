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
/*
 * $Header: /cvsroot/l2j/L2_Gameserver/java/net/sf/l2j/Server.java, v 1.5 2004/11/19 08:54:43 l2chef Exp $
 *
 * $Author: l2chef $
 * $Date: 2004/11/19 08:54:43 $
 * $Revision: 1.5 $
 * $Log: Server.java, v $
 * Revision 1.5  2004/11/19 08:54:43  l2chef
 * database is now used
 *
 * Revision 1.4  2004/07/08 22:42:28  l2chef
 * logfolder is created automatically
 *
 * Revision 1.3  2004/06/30 21:51:33  l2chef
 * using jdk logger instead of println
 *
 * Revision 1.2  2004/06/27 08:12:59  jeichhorn
 * Added copyright notice
 */
package l1j.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import l1j.server.configure.Config;
import l1j.server.server.GameServer;
import l1j.server.server.monitor.LoggerInstance;

/**
 * 한국 서버를 기동한다.
 */
public class Main {
	/** 메세지 로그용. */
	private static Logger _log = Logger.getLogger(Main.class.getName());

	/** 로그 설정 파일의 폴더. */
	private static final String LOG_PROP = "./config/log.properties";
	
	public static long startTime;
	public static Calendar StartTime;
	
	public static Main getInstance() {
		return LazyHolder.instance;
	}
	private static class LazyHolder {
		private static final Main instance = new Main();
	}	
	
	public Main(){
		try {
			Start();
		} catch (Exception e) {
			_log.log(Level.SEVERE, "Failed to Load " + LOG_PROP + " File.", e);
		}
	}
	public static void main(String[] args) throws Exception{
		getInstance();
	}
	
	public static PrintStream _dout;
	
	public class ConsoleScanner implements Runnable {
		@Override
		public void run() {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
				_dout = System.out;
				PrintStream ps = new PrintStream(baos);
				System.setOut(ps);
				System.setErr(ps);
				while (true) {
					Thread.sleep(1000L);
					String s = null;
					synchronized (baos) {
						s = baos.toString();
						baos.reset();
					}
					if (s == null)
						continue;
					s = s.trim();
					if (s.length() > 0) {
						String msg = String.format("%s\r\n", s);
						LoggerInstance.getInstance().addCmd(msg);
						_dout.print(msg);
					}
				}
			} catch (InterruptedException e) {
			} finally {
			}
		}
	}
	/**
	 * 서버 메인.
	 * @param args 커멘드 라인 인수
	 * @throws Exception
	 */
	public void Start() throws Exception{
		File logFolder = new File("log");
		logFolder.mkdir();

		new Thread(new ConsoleScanner()).start();
		try {
			InputStream is = new BufferedInputStream(new FileInputStream(LOG_PROP));
			LogManager.getLogManager().readConfiguration(is);
			is.close();
		} catch (IOException e) {
			_log.log(Level.SEVERE, "Failed to Load " + LOG_PROP + " File.", e);
			System.exit(0);
		}
		try {
			Config.load();
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);    
			System.exit(0); 
		}

		// L1DatabaseFactory 초기설정
		L1DatabaseFactory.setDatabaseSettings(Config.DB_DRIVER, Config.DB_URL, Config.DB_LOGIN, Config.DB_PASSWORD);
		L1DatabaseFactory.getInstance();

		GameServer.getInstance().initialize(); 
		StartTime = Calendar.getInstance();
		StartTime.setTimeInMillis(System.currentTimeMillis());
		startTime = System.currentTimeMillis();
	}
}
