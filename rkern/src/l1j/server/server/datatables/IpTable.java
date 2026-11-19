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
package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.BatchHandler;
import l1j.server.server.utils.SQLUtil;
import l1j.server.swing.chocco;

public class IpTable {

	public static IpTable getInstance() {
		if (_instance == null) {
			_instance = new IpTable();
		}
		return _instance;
	}

	private IpTable() {
		if (!isInitialized) {
			_banip = new ArrayList<String>();
			getIpTable();
		}
	}
	
	public static void reload() {
		IpTable oldInstance = _instance;
		chocco.iplist.removeAll();
		_instance = new IpTable();
		oldInstance._banip.clear();
	}

	public void banIp(String ip) {
		Connection con = null;
		PreparedStatement pstm = null;

		try {

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("INSERT INTO ban_ip SET ip=?");
			pstm.setString(1, ip);
			pstm.execute();
			_banip.add(ip);
			chocco.iplist.add(ip);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public boolean isBannedIp(String s) {
		return _banip.contains(s);
	}

	public void getIpTable() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM ban_ip");

			rs = pstm.executeQuery();

			while (rs.next()) {
				String s = rs.getString(1);
				_banip.add(s);
				chocco.iplist.add(s);
			}

			isInitialized = true;

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public boolean liftBanIp(String ip) {
		boolean ret = false;
		Connection con = null;
		PreparedStatement pstm = null;

		try {

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM ban_ip WHERE ip=?");
			pstm.setString(1, ip);
			pstm.execute();
			ret = _banip.remove(ip);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return ret;
	}
	public void rangeBanIp(Integer[] octet){
		Updator.batch("insert ignore into ban_ip SET ip=?", new BatchHandler(){
			@Override
			public void handle(PreparedStatement pstm, int callNumber) throws Exception {
				pstm.setString(1, String.format("%d.%d.%d.%d", octet[0], octet[1], octet[2], callNumber + 1));
			}			
		}, 255);
	}
	
	public void rangeBanIp(String ip) {
		Connection c = null;
		PreparedStatement p = null;
		String ip1, ip2, ip3;
		StringTokenizer st = new StringTokenizer(ip, ".");

		ip1 = st.nextToken();
		ip2 = st.nextToken();
		ip3 = st.nextToken();
		try {
			c = DatabaseFactory.getInstance().getConnection();
			for (int i = 1; i <= 255; i++) {
				String temp = ip1 + "." + ip2 + "." + ip3 + "." + i;

				if (isBannedIp(temp)) {
					continue;
				}
				p = c.prepareStatement("INSERT INTO ban_ip SET ip=?");
				p.setString(1, temp);
				p.execute();
				_banip.add(temp);
			}
		} catch (Exception e) {// TODO: handle exception
		} finally {
			SQLUtil.close(p);
			SQLUtil.close(c);
		}
	}

	private static Logger _log = Logger.getLogger(IpTable.class.getName());

	private static ArrayList<String> _banip;

	public static boolean isInitialized;

	private static IpTable _instance;

}
