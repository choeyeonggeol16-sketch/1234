package l1j.server.server.clientpackets;

import java.io.IOException;
import java.util.logging.Logger;
import l1j.server.configure.Config;
import l1j.server.server.Account;
import l1j.server.server.AccountAlreadyLoginException;
import l1j.server.server.GameClient;
import l1j.server.server.GameServerFullException;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.serverpackets.S_CommonNews;
import l1j.server.server.serverpackets.S_LoginResult;

public class Authorization {
	private static final Authorization uniqueInstance = new Authorization();
	private static Logger _log = Logger.getLogger(C_AuthLogin.class.getName());
	
	public static Authorization getInstance() { return uniqueInstance; }
	
	private void authenticate(final GameClient client, String accountName, String password, String ip, String host) throws IOException {
		if (!Config.ALLOW_2PC) {
			if (LoginController.getInstance().getIpCount(ip) > 0) {
				_log.info("동일 IP로 접속한 두 PC의 로그인 거부. account=" + accountName + " ip=" + ip);
				client.sendPacket(new S_CommonNews("이미 사용 중입니다."));
				try {
					client.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return;
			}
		}
		
		if (accountName.equals("") || accountName.trim().length() == 0) {
			client.close();
			return;
		}

		Account account = Account.load(accountName);

		if (account == null) {
			if (Config.AUTO_CREATE_ACCOUNTS) {
				if (Account.Check_LoginIP(ip)) { 
					_log.info("    ■■■■■■■■ 시스템: 계정 생성 초과 ■■■■■■■■ " + ip);
					_log.info("    ■■■■■■■■ 시스템: 계정 생성 초과 : 아디정확한지 확인- > accountName = " + accountName);
				client.sendPacket(new S_CommonNews("고객님의 계정은 이미 "+Config.ACCOUNT_LIMIT+"개 이상 생성되었습니다. 보다 자세한 사항은 게임마스터에게 문의를 이용해 주시기 바랍니다."));
					try {
						client.kickLater(1500);  // 팅기는 딜레이 시간
					} catch (Exception e1) {
					}
					return;
				} else { 
					if (!isValidPassword(password)) {
						client.sendPacket(new S_LoginResult(S_LoginResult.REASON_WRONG_PASSWORD));
						return;
					}
					account = Account.create(accountName, password, ip, host);
					account = Account.load(accountName);
				}
			} else {
				_log.warning("account missing for user " + accountName);
			}
		}
		if (account == null || !account.validatePassword(accountName, password)) {
			client.sendPacket(new S_LoginResult(S_LoginResult.REASON_USER_OR_PASS_WRONG));
			return;
		}

		if (account.isBanned()) { // BAN 어카운트
			System.out.println("\n┌───────────────────────────────┐");
			System.out.println("\t 압류된 계정의 로그인을 거부했습니다. account=" + accountName + " ip=" + ip);
			System.out.println("└───────────────────────────────┘\n");
			client.sendPacket(new S_CommonNews("고객님의 계정은 게임 내 시스템의 취약점 및 버그 등을 악용하였거나 이와 관련된 내용으로 인해 이용이 제한되었습니다. 보다 자세한 사항은 게임마스터에게 문의를 이용해 주시기 바랍니다."));
			return;
		}
		if (Account.checkLoginBanIP(ip)) {
			System.out.println("\n┌───────────────────────────────┐");
			System.out.println("\t 차단된 IP 접속 차단! 계정=" + accountName + " 아이피=" + ip);
			System.out.println("└───────────────────────────────┘\n");
			client.sendPacket(new S_CommonNews(""+ip+"해당 아이피 주소는 차단된 상태입니다. 보다 자세한 사항은 게임마스터에게 문의를 이용해 주시기 바랍니다."));
			return;
		}
		
		try {
			LoginController.getInstance().login(client, account);
			Account.updateLastActive(account); // 최종 로그인일을 갱신한다
			client.setAccount(account);
			client.sendPacket(new S_LoginResult(S_LoginResult.REASON_LOGIN_OK)); 

			if (S_CommonNews.NoticeCount(accountName) > 0) 
				client.sendPacket(new S_CommonNews(accountName, client));			
			else 
				new C_CommonClick(client);		
				client.setLoginAvailable();  		
		} catch (GameServerFullException e) {
			client.kick();
			_log.info("최대 접속인원 초과 : (" + client.getIp() + ") 로그인을 절단했습니다. ");
			return;
		} catch (AccountAlreadyLoginException e) {		
			_log.info("동일한 ID의 접속 : (" + client.getIp() + ") 강제 절단 했습니다. ");
			client.sendPacket(new S_CommonNews("이미 사용 중입니다."));
			client.kick();
			return;
		} catch (Exception e) {
			_log.info("비정상적인 로그인 에러 . account=" + accountName + " host=" + host);
			client.kick();
			return;
		} finally {
			account = null;
		}
	}
	 

	private static final Object[] AUTH_LOCKS = createLocks();
    private static Object[] createLocks() {
        Object[] locks = new Object[256];
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
        return locks;
    }
    private static int stripe(String key) { int h = key.hashCode(); return (h ^ (h >>> 16)) & 255; }
    public void auth(GameClient client, String accountName, String password, String ip, String host) throws IOException {
        if (accountName == null || password == null || ip == null) { client.close(); return; }
        int a = stripe(accountName), b = stripe(ip);
        synchronized (AUTH_LOCKS[Math.min(a, b)]) {
            synchronized (AUTH_LOCKS[Math.max(a, b)]) {
                if (!client.isConnected()) return;
                if (client.getAccount() != null) { client.close(); return; }
                authenticate(client, accountName, password, ip, host);
            }
        }
    }

    private boolean isValidPassword(String password) {
		if (password.length() < 6) {
			return false;
		}
		if (password.length() > 16) {
			return false;
		}

		boolean hasLetter = false;
		boolean hasDigit = false;

		char[] chars = password.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (Character.isLetter(chars[i])) {
				hasLetter = true;
			} else if (Character.isDigit(chars[i])) {
				hasDigit = true;
			} else {
				return false;
			}
		}

		if (!hasLetter || !hasDigit) {
			return false;
		}

		return true;
	}
}
