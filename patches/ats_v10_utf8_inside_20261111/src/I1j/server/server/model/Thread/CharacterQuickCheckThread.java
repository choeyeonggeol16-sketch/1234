package I1j.server.server.model.Thread;

import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

public class CharacterQuickCheckThread extends Thread {
	
	private static CharacterQuickCheckThread _instance;
	
	private static final int SLEEP = 60000;
	
	public static CharacterQuickCheckThread getInstance() {
		if (_instance == null) {
			_instance = new CharacterQuickCheckThread();
			_instance.start();
		}
		return _instance;
	}
	public CharacterQuickCheckThread() {
		super("server.threads.pc.CharacterQuickCheckThread");
	}

	public void run() {
		while (true) {
			try {
				for (L1PcInstance _client : L1World.getInstance().getAllPlayers()) {
					if (_client.isPrivateShop() || _client.noPlayerck2 || _client.getNetConnection() == null) { 
						continue;
					} else {
						try {
							if (_client.getNetConnection().isClosed()) {
								System.out.println(_client.getName() + " << 종료안됨 : 이멘트가 동시에 여러명이 뜨는지 확인.");
								_client.logout();
								_client.getNetConnection().close();
							}
						} catch (Exception e) {
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					Thread.sleep(SLEEP);//1분
				} catch (InterruptedException e) {
					// TODO 자동 생성된 catch 블록
					e.printStackTrace();
				}
			}
		}
	}
}
