package l1j.server.server.model.network.core;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import l1j.server.server.model.character.pss.PssConfig;
import l1j.server.server.model.character.pss.PlaySupport2019;
import l1j.server.server.model.character.pss.ClientAutomationSession;

import java.net.InetAddress;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.client.manager.NetworkClientCounter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.packet.BytesWritablePacket;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.packet.WritablePacket;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.packets.Protocol.Packet.SC_CraftListAllAck;
import l1j.server.server.packets.Protocol.Stream.ProtoOutputStream;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;

public class CIOClient implements CIOThreadCallback.WheelTask {

	private static final Logger _log = Logger.getLogger(CIOClient.class.getName());

	private final CIOUserSocket _connection;
	private final boolean _ipReservationCounted;

	private volatile int _packetShard = -1;

	// Authentication-only connections need no separate AtomicBoolean object.
	private volatile int _shardLogged;
	private static final VarHandle SHARD_LOGGED;
	static {
		try {
			SHARD_LOGGED = MethodHandles.lookup().findVarHandle(
					CIOClient.class, "_shardLogged", int.class);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private int _emptyPacketCount;

	private volatile Account account;
	private volatile L1PcInstance _activeChar;
    // Published only after the full asynchronous character-enter completion.
    // Prevents a half-initialized IN_GAME state from bypassing session guard.
    private volatile boolean _gameSessionReady;
	private int chatCount;
	private String _inetAddress;

	private final AtomicBoolean isClosed = new AtomicBoolean(false);
    // Shutdown must not dispose a PC until its network packet has finished and
    // the asynchronous logout cleanup has also completed.
    private volatile boolean logoutComplete;

    public boolean isLogoutComplete() { return logoutComplete; }
	

	private int _packetCount = 0;
	private long _lastCheckTime = System.currentTimeMillis();

	public CIOClient(CIOUserSocket connection, boolean ipReservationCounted) {
		if (connection == null || !connection.isOpen()) {
			throw new IllegalArgumentException("The connection is null or closed.");
		}
		_connection = connection;
		_ipReservationCounted = ipReservationCounted;
		_inetAddress = connection.getRemoteAddress();
	}

    /** An admitted session must have its initial key packet staged for SEND. */
    public boolean onConnected() {
        final CIOUserSocket socket = _connection;
        try {
            if (socket == null || !socket.isOpen()) return false;
            socket.initializeCipher(Opcodes.getSeed());
            // sendPacket(byte[]) swallows staging failures. ACCEPT must not treat
            // a failed initial SEND reservation as a successful login handshake.
            if (!socket.tryWritePacket(this, new BytesWritablePacket(Opcodes.S_KEYBYTES))) {
                socket.clearCipher();
                return false;
            }
            return socket.isOpen();
        } catch (Exception e) {
            if (socket != null) socket.clearCipher();
            _log.log(Level.SEVERE,
                    String.format("[GameClient Handshake Err] 초기 핸드셰이크 키 전송 실패 - IP: %s", getIp()), e);
            return false;
        }
    }

	protected void onDisconnection() {
		close();
	}

    public boolean encrypt(WriteBuffer data, int offset, int size) {
        final CIOUserSocket socket = _connection;
        return socket != null && socket.encrypt(data, offset, size);
    }

    public boolean decrypt(ReadBuffer data, int offset, int size) {
        final CIOUserSocket socket = _connection;
        return socket != null && socket.decrypt(data, offset, size);
    }

	public void kick() {
		_log.info(String.format(
				"[GameClient Kick] 강제 강퇴 처리 (DISCONNECT 전송) - IP: %s, Account: %s",
				getIp(), getAccountName()));

		SC_Disconnect.send(this);
		close();
	}

    /** Public close/kick/disconnect only request transport shutdown. The socket
     * callback starts logout after every accepted packet releases its lease. */
    public void close() {
        _gameSessionReady = false;
        _connection.close();
    }

    /** Called by the transport's terminal callback, independently of the
     * disconnect-request CAS (which may already have been set by disconnect()). */
    void onTransportClosed() {
        if (!_connection.isCloseComplete())
            throw new IllegalStateException("logout before transport/packet drain");
        _gameSessionReady = false;
		if (!isClosed.compareAndSet(false, true)) {
			// 종료 과정에서는 여러 경로에서 close()가 재진입할 수 있으므로 로그 도배 방지
			_log.fine(String.format(
					"[GameClient Close Dup] 이미 close 처리 중인 클라이언트 - IP: %s", getIp()));
			return;
		}

        PlaySupport2019.onClientDetached(this);
        automationSession = null;
		final L1PcInstance pc = _activeChar;

		try {
			if (pc != null) {
				ThreadPool.getInstance().execute(() -> {
					try {
						if (!pc.isPrivateShop() && !pc.isAutoClanjoin()) {
							pc.logout();
						}
					} catch (Exception e) {
						_log.log(Level.SEVERE,
								String.format(
										"[GameClient Logout Err] 캐릭터 로그아웃 도중 에러 발생 - Char: %s",
										pc.getName()),
								e);
					} finally {
						cleanupAfterLogout(pc);
					}
				});

				return;
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE,
					String.format(
							"[GameClient Close Err] 스레드풀 로그아웃 요청 실패 - IP: %s",
							getIp()),
					e);
			// Shutdown or saturation must not skip PC logout, even though the
			// general instant executor can no longer execute on its caller.
			// This rare mandatory-cleanup fallback preserves the prior contract.
			if (pc != null) {
                final Runnable rescue = () -> {
                    try {
                        if (!pc.isPrivateShop() && !pc.isAutoClanjoin()) pc.logout();
                    } catch (Throwable failure) {
                        _log.log(Level.SEVERE, "[GameClient Close] fallback logout failed", failure);
                    } finally {
                        cleanupAfterLogout(pc);
                    }
                };
                // IOCP callbacks must never synchronously perform DB/world
                // teardown when the general executor rejects its task.
                // Only this emergency path creates a one-shot virtual thread.
                if (CIOWinServer.isCompletionThread()) {
                    try {
                        Thread.ofVirtual().name("CIO-logout-recovery").start(rescue);
                        return;
                    } catch (Throwable startFailure) {
                        _log.log(Level.SEVERE,
                                "[GameClient Close] emergency logout thread unavailable", startFailure);
                    }
                }
                rescue.run();
                return;
            }
		}

		cleanupAfterLogout(pc);
	}

	private void cleanupAfterLogout(L1PcInstance pc) {
		try {
			if (_ipReservationCounted && _inetAddress != null
					&& !_inetAddress.isEmpty()
					&& !_inetAddress.equals("0.0.0.0")) {

				NetworkClientCounter.getInstance()
						.decrease(InetAddress.getByName(_inetAddress));
			}
		} catch (Exception e) {
			_log.log(Level.WARNING, "NetworkClientCounter decrease 예외 발생", e);
		}

		try {
			LoginController.getInstance().logout(this);
		} catch (Throwable e) {
			_log.log(Level.WARNING, "LoginController logout 예외 발생", e);
		}

		try {
			if (_activeChar == pc) {
				setActiveChar(null);
			}
		} catch (Throwable ignored) {
		}

		try {
			// 메모리 제거
			SC_CraftListAllAck.onDisconnect(this);
            final CIOUserSocket socket = _connection;
            if (socket != null) socket.clearCipher();
			setAccount(null);

			disconnect();

			setConnectionState(ConnectionState.DISCONNECTED);
		} catch (Throwable e) {
			_log.log(Level.WARNING, "disconnect 처리 중 예외 발생", e);
		} finally {
            // Hybrid handoff is allowed only after full logout/session removal.
            try { DominationHybrid.onClientClosed(this); }
            finally { logoutComplete = true; }
		}
	}

	public void setActiveChar(L1PcInstance pc) {
        if (_activeChar != pc || pc == null) {
            _gameSessionReady = false;
            PlaySupport2019.onClientDetached(this);
            automationSession = null;
            clearPssWindowSession();
        }
		_activeChar = pc;
	}

    private volatile ClientAutomationSession automationSession;
    private volatile int pssClaimPending, pssQueryPending;
    private static final AtomicIntegerFieldUpdater<CIOClient> PSS_CLAIM =
            AtomicIntegerFieldUpdater.newUpdater(CIOClient.class, "pssClaimPending");
    private static final AtomicIntegerFieldUpdater<CIOClient> PSS_QUERY =
            AtomicIntegerFieldUpdater.newUpdater(CIOClient.class, "pssQueryPending");
    private volatile PssConfig.Settings pssCacheSettings;
    private volatile long pssCacheDay;
    private volatile int pssCacheSeconds = -1;

    public boolean tryBeginPssClaim() { return PSS_CLAIM.compareAndSet(this, 0, 1); }
    public void endPssClaim() { pssClaimPending = 0; }
    public boolean isPssClaimPending() { return pssClaimPending != 0; }
    public boolean tryBeginPssQuery() { return PSS_QUERY.compareAndSet(this, 0, 1); }
    public void endPssQuery() { pssQueryPending = 0; }
    public int cachedPssSeconds(PssConfig.Settings settings, long day) {
        return pssCacheSettings == settings && pssCacheDay == day ? pssCacheSeconds : -1;
    }
    public void cachePssSeconds(PssConfig.Settings settings, long day, int seconds) {
        pssCacheSeconds = seconds;
        pssCacheDay = day;
        pssCacheSettings = settings;
    }
    public void invalidatePssCache() { pssCacheSettings = null; }

    private volatile l1j.server.server.model.character.pss.window.PlaySupportWindow.Session pssWindowSession;

    public l1j.server.server.model.character.pss.window.PlaySupportWindow.Session peekPssWindowSession() {
        return pssWindowSession;
    }

    public synchronized l1j.server.server.model.character.pss.window.PlaySupportWindow.Session pssWindowSession() {
        if (pssWindowSession == null)
            pssWindowSession = new l1j.server.server.model.character.pss.window.PlaySupportWindow.Session(this);
        return pssWindowSession;
    }

    private void clearPssWindowSession() {
        final var current = pssWindowSession;
        pssWindowSession = null;
        if (current != null) current.close();
    }
    private volatile boolean clientPssMapRules;
    private static final AtomicReferenceFieldUpdater<CIOClient, ClientAutomationSession> AUTOMATION =
            AtomicReferenceFieldUpdater.newUpdater(CIOClient.class, ClientAutomationSession.class, "automationSession");

    public ClientAutomationSession getAutomationSession() { return automationSession; }
    public boolean requiresPlayerTeleportRules() { return clientPssMapRules; }

    public boolean tryClaimAutomation(ClientAutomationSession requested) {
        if (requested == null) throw new NullPointerException("requested");
        if (requested.clientDriven() && (!isConnected() || !_gameSessionReady)) return false;
        if (!AUTOMATION.compareAndSet(this, null, requested)) return false;
        if (requested.clientDriven()) clientPssMapRules = true;
        if (requested.clientDriven() && (!isConnected() || !_gameSessionReady)) {
            AUTOMATION.compareAndSet(this, requested, null);
            return false;
        }
        return true;
    }

    public boolean releaseAutomation(ClientAutomationSession expected) {
        return expected != null && AUTOMATION.compareAndSet(this, expected, null);
    }

    /** CAS replacement keeps PSS running while the persisted daily lease rotates. */
    public boolean replaceAutomation(ClientAutomationSession expected, ClientAutomationSession next) {
        return expected != null && next != null && expected.clientDriven()
                && next.clientDriven() && AUTOMATION.compareAndSet(this, expected, next);
    }


    public boolean isGameSessionReady() {
        return _gameSessionReady;
    }

    public void publishGameSessionReady(L1PcInstance pc) {
        if (pc != null && _activeChar == pc &&
                _connectionState == ConnectionState.IN_GAME && isConnected()) {
            _gameSessionReady = true;
        }
    }

    public void clearGameSessionReady() {
        _gameSessionReady = false;
        PlaySupport2019.onClientDetached(this);
        automationSession = null;
        clearPssWindowSession();
    }

	public L1PcInstance getActiveChar() {
		return _activeChar;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public Account getAccount() {
		return account;
	}

	public String getAccountName() {
		return account != null ? account.getName() : null;
	}

	public String getIp() {
		return _inetAddress;
	}

	public String getHostname() {
		return getIp();
	}

	public boolean isConnected() {
		return _connection.isClientConnected() && !isClosed.get();
	}

	public boolean isClosed() {
		return !isConnected();
	}

	private void enqueue(ServerPacket data) {
		if (data == null || !isConnected()) return;

		try {
			_connection.enqueuePacket(data);
		} catch (Exception e) {
			_log.log(Level.SEVERE, "Failed to enqueue packet write", e);
		}
	}

	public void sendPacket(byte[] data) {
		enqueue(data);
	}

	public void sendPacket(ServerPacket bp) {
		enqueue(bp);
	}

	private void enqueue(byte[] data) {
		if (data == null || data.length == 0 || !isConnected()) return;

		try {
            _connection.enqueuePacket(new BytesWritablePacket(data));
		} catch (Exception e) {
			_log.log(Level.SEVERE, "Failed to enqueue packet write", e);
		}
	}

	    

    

    

    public void sendPacket(ProtoOutputStream stream, boolean isClear) {
		if (stream == null) return;

		try {
			if (!stream.isCreated()) {
				stream.createProtoBytes();
			}
			enqueue(stream.getProtoBytes());

		} finally {
			if (isClear) {
				stream.dispose();
			}
		}
	}

	public void directSendPacketBytes(Collection<byte[]> col) {
		if (col == null || col.isEmpty()) return;

		for (byte[] data : col) {
			if (data == null) continue;

			try {
				enqueue(data);
			} catch (Exception e) {
				_log.log(Level.WARNING, "directSendPacketBytes 실패", e);
			}
		}
	}


	// ------------------------------------------------------------------
	// Game-session operations
	// ------------------------------------------------------------------

	protected void writePacket(
			WritablePacket<CIOClient> packet) {

		if (packet == null || !isConnected()) {
			return;
		}

		_connection.enqueuePacket(packet);
	}

	public int drainOnOwner() {
		return _connection.drainOnOwner();
	}

	public void onSendDrainedOwner() {
		_connection.onSendDrainedOwner();
	}

	public boolean hasDrainWorkOnOwner() {
		return _connection.hasDrainWorkOnOwner();
	}

	public boolean whenWriteDrained(Runnable callback) {
		return _connection.whenWriteDrained(callback);
	}

	public void cancelWriteDrainCallback() {
		_connection.cancelWriteDrainCallback();
	}

	public void read() {
		_connection.read();
	}

	public void close(
			WritablePacket<CIOClient> packet) {

		if (!isConnected()) {
			_log.warning(
					String.format(
							"[GameClient Close Skip] 이미 종료 중 - IP: %s",
							getHostAddress()));
			return;
		}

		_connection.requestClientClose(packet);
	}

	public void disconnect() {
		if (!_connection.beginClientDisconnect()) {
			return;
		}

		try {
			onDisconnection();

		} catch (Throwable t) {
			/*
			 * RWorker owner close path에서도 들어올 수 있다.
			 * 상위 게임 life-cycle 예외가 owner pump까지 빠져나가지 않게 한다.
			 */
			_log.log(
					Level.SEVERE,
					String.format(
							"[GameClient Disconnect Err] onDisconnection() 중 예외 발생 - IP: %s",
							getHostAddress()),
					t);

		} finally {
			_connection.finishClientDisconnect();
		}
	}

	public CIOUserSocket getConnection() {
		return _connection;
	}

	/** Socket shard chosen when the connection was admitted. */
	public int affinityShard() {
		return _connection.affinityShard();
	}

	public String getHostAddress() {
		return _connection.getRemoteAddress();
	}

	public int getEstimateQueueSize() {
		return _connection.getEstimateQueueSize();
	}

	public int incrementEmptyPacketCount() {
		return ++_emptyPacketCount;
	}

	public void resetEmptyPacketCount() {
		_emptyPacketCount = 0;
	}

	public boolean markShardLogged() {
		return SHARD_LOGGED.compareAndSet(this, 0, 1);
	}

	public void initPacketShard(int shardMask) {
		if (_packetShard >= 0) {
			return;
		}

		_packetShard = _connection.affinityShard();
	}

	public int getPacketShard() {
		return _packetShard;
	}

	// 현재 클라이언트의 연결 상태 (기본값: 최초 접속인 CONNECTED)
	private volatile ConnectionState _connectionState = ConnectionState.CONNECTED;

	// 소켓이 생성된 절대 시간 기록
	private final long _connectionTime = System.currentTimeMillis();

	// 로그인 전 대기 허용 시간 (1분)
	private static final long PRE_LOGIN_TIMEOUT = 60_000L;

	public ConnectionState getConnectionState() {
		return _connectionState;
	}

	public void setConnectionState(ConnectionState connectionState) {
        if (connectionState != ConnectionState.IN_GAME) {
            _gameSessionReady = false;
            PlaySupport2019.onClientDetached(this);
            clearPssWindowSession();
        }
		_connectionState = connectionState;
	}

	public boolean checkPreLoginTimeout() {
		if (_connectionState == ConnectionState.IN_GAME || _connectionState == ConnectionState.AUTHENTICATED
				|| _connectionState == ConnectionState.OUT_GAME || _connectionState == ConnectionState.DISCONNECTED) {
			return false;
		}
		return System.currentTimeMillis() - _connectionTime > PRE_LOGIN_TIMEOUT;
	}

    // Existing CIO wheel accepts this session directly as the one-shot login task.
    // No dedicated LoginTimeout wrapper is allocated on every ACCEPT.
    @Override
    public boolean isCancelled() {
        return !isConnected() || isLoggedIn();
    }

    @Override
    public void run() {
        if (!isCancelled() && checkPreLoginTimeout()) disconnect();
    }

	public boolean isLoggedIn() {
		return _connectionState == ConnectionState.IN_GAME || _connectionState == ConnectionState.AUTHENTICATED
				|| _connectionState == ConnectionState.OUT_GAME || _connectionState == ConnectionState.DISCONNECTED;
	}

	// 패킷 도배 방지
	//
	// 일반 호출 호환용. network completion hot path는 completion당 한 번 읽은 now를
	// checkPacketRateLimit(long)으로 넘겨 System.currentTimeMillis()를
	// 패킷마다 호출하지 않는다.
	public boolean checkPacketRateLimit() {
		return checkPacketRateLimit(System.currentTimeMillis());
	}

	public boolean checkPacketRateLimit(long now) {
		if (now < _lastCheckTime) {
			_lastCheckTime = now;
			_packetCount = 0;
			return false;
		}

		if (now - _lastCheckTime > 1000L) {
			_packetCount = 0;
			_lastCheckTime = now;
		}

		_packetCount++;
		return _packetCount > ConnectionConfig.PACKET_LIMIT_PER_SEC;
	}

	// 2차 비번 인증 캐릭터 이름
	private String _lastSelectedCharName;
	public String getLastSelectedCharName() {
		return _lastSelectedCharName;
	}
	public void setLastSelectedCharName(String name) {
		_lastSelectedCharName = name;
	}

	public int getChatCount() {
		return chatCount;
	}

	public void setChatCount(int i) {
		chatCount = i;
	}

	private int secondpasswordfailurecount = 0;
	public int getSecondPasswordFailureCount() {
		return secondpasswordfailurecount;
	}
	public int incSecondPasswordFailureCount() {
		return ++secondpasswordfailurecount;
	}
	public void resetSecondPasswordFailureCount() {
		secondpasswordfailurecount = 0;
	}
}
