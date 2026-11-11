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
package l1j.server.server.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.Account;
import l1j.server.server.AccountAlreadyLoginException;
import l1j.server.server.GameClient;
import l1j.server.server.GameServerFullException;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.serverpackets.S_ServerMessage;

public class LoginController {
	
	private static final LoginController _instance = new LoginController();
	
	private Map<String, GameClient> _accounts = new ConcurrentHashMap<String, GameClient>();
	private Map<String, Integer> _countIp = new ConcurrentHashMap<String, Integer>();
	private Map<String, Integer> _countCClass = new ConcurrentHashMap<String, Integer>();

	private volatile int _maxAllowedOnlinePlayers;

	private LoginController() {
	}

	public static LoginController getInstance() { return _instance; }

	public GameClient[] getAllAccounts() {
		return _accounts.values().toArray(new GameClient[0]);
	}

	public int getOnlinePlayerCount() {
		return _accounts.size();
	}

	public int getMaxAllowedOnlinePlayers() {
		return _maxAllowedOnlinePlayers;
	}

	public void setMaxAllowedOnlinePlayers(int maxAllowedOnlinePlayers) {
		_maxAllowedOnlinePlayers = maxAllowedOnlinePlayers;
	}

	private void kickClient(final GameClient client) {
		if (client == null) {
			return;
		}

		if (client.getActiveChar() != null) {
			client.getActiveChar().sendPackets(new S_ServerMessage(357), true);
		}
		

		client.kickLater(1000);
	}

	public static String getCClass(String ip) { int dot = ip.lastIndexOf('.'); if (dot >= 0) return ip.substring(0, dot); int colon = ip.lastIndexOf(':'); return colon >= 0 ? ip.substring(0, colon) : ip; }


	public void login(GameClient client, Account account) throws GameServerFullException, AccountAlreadyLoginException {
		synchronized (this) {
			if (!client.isConnected() || client.getAccount() != null) {
                throw new IllegalStateException("Session closed or already authenticated");
            } else if (!account.isValid()) {
				// 패스워드 인증이되어 있지 않은, 혹은 인증에 실패한 어카운트가 지정되었다.
				// 이 코드는, 버그 검출을 위해 마셔 존재한다.
				throw new IllegalArgumentException("인증되지 않은 계정입니다");
			} else if ((getMaxAllowedOnlinePlayers() <= getOnlinePlayerCount()) && !account.isGameMaster()) {
				throw new GameServerFullException();
			} else if (_accounts.containsKey(account.getName())) {
				kickClient(_accounts.get(account.getName()));
				throw new AccountAlreadyLoginException();
			} else {
				String ip = client.getIp();
				_accounts.put(account.getName(), client);
                client.setAccount(account);

				_countIp.put(ip, getIpCount(ip) + 1);

				String cClass = getCClass(ip);

				if (_countCClass.containsKey(cClass)) {
					_countCClass.put(cClass, _countCClass.get(cClass) + 1);
				} else {
					_countCClass.put(cClass, 1);
				}
			}
		}
	}

	public GameClient getClientByAccount(String name) {
		if (_accounts.containsKey(name)) {
			return _accounts.get(name);
		}	
		return null;
	}

	public int getIpCount(String ip) { return _countIp.getOrDefault(ip, 0); }

	public int getCClassCount(String ip) { return _countCClass.getOrDefault(getCClass(ip), 0); }

	public boolean logout(GameClient client) {
        synchronized (this) {
            if (client == null || client.getAccountName() == null
                    || !_accounts.remove(client.getAccountName(), client)) return false;
            String ip = client.getIp();
            int count = getIpCount(ip);
            if (count <= 1) _countIp.remove(ip); else _countIp.put(ip, count - 1);
            String cClass = getCClass(ip);
            Integer subnet = _countCClass.get(cClass);
            if (subnet == null || subnet <= 1) _countCClass.remove(cClass);
            else _countCClass.put(cClass, subnet - 1);
            client.setAccount(null);
            return true;
        }
    }
}
