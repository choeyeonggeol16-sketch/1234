
package l1j.server.server;

import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.configure.Config;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.encryptions.LineageEncryption;
import l1j.server.server.encryptions.LineageKeys;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.GameTimeClock;
import l1j.server.server.network.CloseReason;
import l1j.server.server.network.NettySession;
import l1j.server.server.network.SessionApplication;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_GameTime;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.types.UByte8;
import l1j.server.server.types.UChar8;
import xnetwork.Connection;

import java.util.concurrent.atomic.AtomicBoolean;



public class GameClient implements SessionApplication, PacketOutput {

	private static Logger _log = Logger.getLogger(GameClient.class.getName());
	private PacketHandler _packetHandler;
    private NettySession _session;
    private final AtomicBoolean _closedOnce = new AtomicBoolean();
    private final AtomicBoolean _kickScheduled = new AtomicBoolean();
    private volatile ScheduledFuture<?> _delayedKick;
	private volatile Account _account;
	private volatile L1PcInstance _activeChar;
	private String _ip;
	private String _hostname;
	private Connection _connection;
	private ClientThreadObserver _observer;
	private DollObserver _dollobserver;
	private AutoSaveTimer _autoSaveTimer;
	private NullPacketCarrier _nullPacketCarrier;


	private int _loginStatus = 0;

	// ui update 2

	static byte[] FIRST_PACKET = { 
			(byte) 0x12, (byte) 0x00, 
			
			(byte) 0x58, 
			
			(byte) 0x88, (byte) 0x9e,(byte) 0x44, 
			(byte) 0x2d, 
			(byte) 0xac, (byte) 0x37,(byte) 0x1c,
			(byte) 0x65, 
			(byte) 0xa5, (byte) 0x13,(byte) 0xdf,(byte) 0xae,
			(byte) 0xfe, (byte) 0xda, (byte) 0x54, 
			};

	protected GameClient() {
	}
 

	public GameClient(NettySession session) {
        _session = session;
        _connection = new Connection(session);
        _ip = session.remoteIp();
        _hostname = _ip; // DNS is never performed on a Netty event loop.
        _packetHandler = new PacketHandler(this);
        _clkey = LineageEncryption.initKeys(0x2d449e88L);
    }

	@Override public byte[] initialHandshake() { return FIRST_PACKET.clone(); }
    @Override public byte[] decode(byte[] body) throws Exception {
        return LineageEncryption.decrypt(body, body.length, _clkey);
    }
    @Override public byte[] encode(byte[] body) throws Exception {
        return LineageEncryption.encrypt(body, _clkey);
    }
    @Override public void onOpen() {
        if (Config.AUTOMATIC_KICK > 0) {
            _observer = new ClientThreadObserver(Math.multiplyExact(Config.AUTOMATIC_KICK, 60000));
            _observer.start();
        }
        _dollobserver = new DollObserver(30000); _dollobserver.start();
        _autoSaveTimer = new AutoSaveTimer(); _autoSaveTimer.start();
        _nullPacketCarrier = new NullPacketCarrier(); _nullPacketCarrier.start();
    }
    @Override public void onPacket(byte[] body) throws Exception { onPacket(_connection, body); }
    @Override public long closeDelayMillis() {
        L1PcInstance pc = _activeChar;
        return pc != null && (pc.isPinkName() || pc.isParalyzed()) ? 1000 : 0;
    }
    @Override public void onClosed(CloseReason reason) { onDisconnect(_connection); }

    abstract class SessionRepeatTask extends RepeatTask {
        private final AtomicBoolean queued = new AtomicBoolean();
        SessionRepeatTask(long interval) { super(interval); }
        @Override public final void execute() {
            if (_session == null || !_session.isOpen()) { cancel(); return; }
            if (!queued.compareAndSet(false, true)) return;
            if (!_session.executeOnOwner(() -> {
                try { if (isActive() && _session.isOpen()) executeOnOwner(); }
                finally { queued.set(false); }
            })) {
                queued.set(false);
                cancel();
                if (_session.isOpen()) _session.close(CloseReason.INBOUND_OVERLOAD);
            }
        }
        protected abstract void executeOnOwner();
    }

	public void setIp(String ip) {
		_ip = ip;
	}

	public int getRemotePort() {
		return _session == null ? -1 : _session.remotePort();
	}

	public String getIp() {
		return _ip;
	}

	public String getHostname() {
		return _hostname;
	}

	private boolean _charRestart = true;

	public void CharReStart(boolean flag) {
		_charRestart = flag;
	}

	private LineageKeys _clkey;

	private void doAutoSave() throws Exception {
		if (_activeChar == null || _charRestart) {
			return;
		}
		try {
			// 캐릭터 정보
			L1PcInstance pc = _activeChar;  
			pc.save();
			pc.saveInventory();
		} catch (Exception e) {
			_log.warning("Client autosave failure.");
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			throw e;
		}
	}

	/* 섭폭방지1 될라나 */
	private int _kick = 0;

	public void kick() {
        if (_session == null || _session.isClosing()) return;
        sendPacket(new S_Disconnect());
        _kick = 1;
        _session.closeGracefully(CloseReason.KICK);
    }
    public void kickLater(long delayMillis) {
        if (_session == null || _session.isClosing() || !_kickScheduled.compareAndSet(false, true)) return;
       ScheduledFuture<?> future = GeneralThreadPool.getInstance().schedule(this::kick, Math.max(1, delayMillis));
        _delayedKick = future;
        if (future != null && _closedOnce.get()) future.cancel(false);
    }

	

	class NullPacketCarrier extends SessionRepeatTask {
		public NullPacketCarrier() {
			super(2000); // 2초마다 한번씩
		}

		public void start() {
			GeneralThreadPool.getInstance().schedule(NullPacketCarrier.this, 3000); // 3초부터 시작.
		}

		@Override
		protected void executeOnOwner() {
			try {
				if (!_connection.isOpen()) {
					cancel();
					return;
				}
				if(getActiveChar() == null) return;		
				int serverTime = GameTimeClock.getInstance().getGameTime().getSeconds();
				
				sendPacket(new S_GameTime(serverTime));
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				cancel();
			}
		}
	}

	// 클라이언트 thread의 감시 타이머
	class AutoSaveTimer extends SessionRepeatTask {

		public AutoSaveTimer() {
			super(600000); // 10분마다 한번씩.
		}

		public void start() {
			GeneralThreadPool.getInstance().schedule(AutoSaveTimer.this, 600000);
		}

		@Override
		protected void executeOnOwner() {
			try {
				if (!_connection.isOpen()) {
					cancel();
					return;
				}

				doAutoSave();
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				cancel();
			}
		}
	}

	// 클라이언트 thread의 감시 타이머
	class ClientThreadObserver extends SessionRepeatTask {
		private int _checkct = 1;

		public ClientThreadObserver(int disconnectTimeMillis) {
			super(disconnectTimeMillis);
		}

		public void start() {
			GeneralThreadPool.getInstance().schedule(ClientThreadObserver.this, getInterval());
		}

		@Override
		protected void executeOnOwner() {
			try {
				if (!_connection.isOpen()) {
					cancel();
					return;
				}

				if (_checkct > 0) {
					_checkct = 0;
					return;
				}

				if (_activeChar == null || !_activeChar.isPrivateShop()) { // 개인 상점중
					kick();
					_log.warning("일정시간 응답을 얻을 수 없었기 때문에(" + _ip + ")과(와)의 접속을 강제 절단 했습니다.");
					cancel();
					return;
				}
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				cancel();
			}
		}

		public void packetReceived() {
			_checkct++;
		}
	}

	// 마법인형 30초 액션
	class DollObserver extends SessionRepeatTask {
		private int _checkct = 1;

		public DollObserver(int sendgfxtime) {
			super(sendgfxtime);
		}

		public void start() {
			GeneralThreadPool.getInstance().schedule(DollObserver.this, 1);
		}

		@Override
		protected void executeOnOwner() {
			try {
				if (!_connection.isOpen()) {
					cancel();
					return;
				}

				if (_checkct > 0) {
					_checkct = 0;
					return;
				}

				if (_activeChar == null) {
					return;
				}

				DollInstance doll = _activeChar.getMagicDoll();
				if (doll != null) {
					S_DoActionGFX s = S_DoActionGFX.get(doll.getId(), 66);
					_activeChar.sendPackets(s);
					_activeChar.broadcastPacket(s);
					S_DoActionGFX ss = S_DoActionGFX.get(doll.getId(), 67);
					_activeChar.sendPackets(ss);
					_activeChar.broadcastPacket(ss);
				}
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				cancel();
			}
		}

		public void packetReceived() {
			_checkct++;
		}
	}
  	@Override
	public void sendPacket(ServerBasePacket packet) {
        if (_session == null || _session.isClosing() || packet == null) return;
        try {
            byte[] body = packet.getContent();
            if (body.length < 4) return; // Preserve the legacy cipher's minimum-body policy.
            L1PcInstance pc = _activeChar;
            if (pc != null) pc.setLastPacket(packet.toString());
            _session.send(body); // Snapshot and byte admission happen before asynchronous encoding.
        } catch (Exception failure) {
            _log.log(Level.WARNING, "Packet serialization failed", failure);
            _session.close(CloseReason.HANDLER_ERROR);
        }
    }
	
	public String DataToPacket(byte[] data, int len) {
		StringBuffer result = new StringBuffer();
		int counter = 0;
		for (int i=0;i< len;i++) { 
			if (counter % 16 == 0) { 
				result.append(HexToDex(i,4)+": ");
			}
			result.append(HexToDex(data[i] & 0xff, 2) + " ");
			counter++;
			if(counter == 16) {
				result.append("   ");
				int charpoint = i-15;
				for (int a=0; a<16;a++) { 
					int t1 = data[charpoint++]; 
					if(t1 > 0x1f && t1 < 0x80) { 
						result.append((char)t1); 
					} else { 
						result.append('.'); 
					} 
				}
				result.append("\n"); counter = 0; 
			} 
		}
		int rest = data.length % 16;
		if (rest > 0 ) { 
			for (int i=0; i<17-rest;i++ ) { 
				result.append("   "); 
			}
			int charpoint = data.length-rest;
			for (int a=0; a<rest;a++) { 
				int t1 = data[charpoint++]; 
				if(t1 > 0x1f && t1 < 0x80) { 
					result.append((char)t1); 
				} else { 
					result.append('.'); 
				} 
			}
			result.append("\n");
		}
		return result.toString();
	}

	private String HexToDex(int data, int digits) {
		String number = Integer.toHexString(data);
		for (int i=number.length(); i< digits; i++) number = "0" + number;
		return number;
	}


	public void close() {
		_connection.close();
	}
	
	public boolean isConnected() {
		return _connection != null && _connection.isOpen();
	}
	
	public boolean isClosed() {
		if (!_connection.isOpen())
			return true;
		else {
			return false;
		}
	}


	public void setActiveChar(L1PcInstance pc) {
		_activeChar = pc;
	}

	public L1PcInstance getActiveChar() {
		return _activeChar;
	}

	public void setAccount(Account account) {
        _account = account;
        if (_session != null) {
            if (account != null) _session.markAuthenticated();
            else { _loginStatus = 0; _session.markUnauthenticated(); }
        }
    }

	public Account getAccount() {
		return _account;
	}

	public String getAccountName() {
		if (_account == null) {
			return null;
		}
		return _account.getName();
	}

	

	public void onDisconnect(Connection connection) {
        if (!_closedOnce.compareAndSet(false, true)) return;
        ScheduledFuture<?> delayedKick = _delayedKick;
        _delayedKick = null;
        if (delayedKick != null) delayedKick.cancel(false);
        if (_observer != null) _observer.cancel();
        if (_dollobserver != null) _dollobserver.cancel();
        if (_autoSaveTimer != null) _autoSaveTimer.cancel();
        if (_nullPacketCarrier != null) _nullPacketCarrier.cancel();
        // Transport has stopped and the serial game mailbox has drained before this callback.
        // Dedicated bounded close workers prevent database logout from occupying I/O/game workers.
        try {
            L1PcInstance pc = _activeChar;
            if (pc != null) pc.logout();
        } catch (Exception failure) {
            _log.log(Level.SEVERE, "Logout cleanup failed", failure);
        } finally {
            try { LoginController.getInstance().logout(this); }
            finally {
                _activeChar = null;
                _account = null;
                _packetHandler = null;
                if (_clkey != null) {
                   Arrays.fill(_clkey.encodeKey, 0L);
                   Arrays.fill(_clkey.decodeKey, 0L);
                    _clkey = null;
                }
            }
        }
    }

	public static void kickByAccount(String accountName) {
 
	}

	public void setLoginAvailable() {
		_loginStatus = 1;
	}

	public void onPacket(Connection connection, byte[] data) throws Exception {
		int opcode = data[0] & 0xFF;

		if (opcode == Opcodes.C_OPCODE_LOGINTOSERVER) {
			if (_loginStatus != 1) {
				return;
			}
		}
		if (opcode == Opcodes.C_OPCODE_LOGINTOSERVEROK || opcode == Opcodes.C_OPCODE_RETURNTOLOGIN) {
			_loginStatus = 0;
		}
		if (opcode != Opcodes.C_OPCODE_KEEPALIVE) { 
		if (_observer != null) _observer.packetReceived();
		}
		if (opcode == Opcodes.C_OPCODE_MOVECHAR) {
			_dollobserver.packetReceived();
		}
		_packetHandler.handlePacket(data, getActiveChar());
	}
	 
}
