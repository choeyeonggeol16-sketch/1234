package l1j.server.server.authd;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.thread.CServerTimer;

/**
 * 로그인 세션 레지스트리
 */
public final class CAccountContainer {

    private static final Logger _log = Logger.getLogger(CAccountContainer.class.getName());

    // ---------------- 코어 ----------------

    private static volatile CAccountContainer _instance;

    public static CAccountContainer getInstance() {
        CAccountContainer inst = _instance;
        if (inst == null) {
            synchronized (CAccountContainer.class) {
                inst = _instance;
                if (inst == null) {
                    inst = new CAccountContainer();
                    _instance = inst;
                }
            }
        }
        return inst;
    }

    /** 정규화된 accountName -> session */
    private final ConcurrentHashMap<String, CClientSession> _sessions =
            new ConcurrentHashMap<>(8192);

    /** IP -> 접속 수 */
    private final ConcurrentHashMap<String, AtomicInteger> _perIp =
            new ConcurrentHashMap<>(4096);

    /** C-Class -> 접속 수 */
    private final ConcurrentHashMap<String, AtomicInteger> _perCClass =
            new ConcurrentHashMap<>(4096);

    /** accountName -> kick 예정 시각(ms) */
    private final ConcurrentHashMap<String, Long> _pendingKickUntil =
            new ConcurrentHashMap<>();

    private final AtomicInteger _online = new AtomicInteger();

    private volatile int _maxAllowedOnlinePlayers;
    private volatile long _reloginGraceMs = 1_000L;

    private CAccountContainer() {
    }

    // ---------------- 값/키 유틸 ----------------

    /**
     * 기존 AccountId.of()와 같은 정규화 규칙.
     * AccountId 객체 자체는 만들지 않는다.
     */
    static String accountKey(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        return name.isEmpty() ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * 로그인 시점 IP 유효성 검증.
     * 기존 SourceAddress.of()와 동일하게 null/빈 문자열을 거부한다.
     */
    private static String requireIp(String rawIp) {
        Objects.requireNonNull(rawIp, "ip");
        if (rawIp.isEmpty()) {
            throw new IllegalArgumentException("빈 IP");
        }
        return rawIp;
    }

    /**
     * 기존 SourceAddress.cClass() 계산과 동일.
     * 세션마다 cClass String을 장기 보관하지 않고 필요할 때만 만든다.
     */
    private static String cClassOf(String ip) {
        final int idx = ip.lastIndexOf('.');
        return (idx > 0) ? ip.substring(0, idx) : ip;
    }

    /** 세션 레코드: client + 로그인 당시 IP만 유지 */
    private static final class CClientSession {
        final CUserSocket client;
        final String ip;

        CClientSession(CUserSocket client, String ip) {
            this.client = client;
            this.ip = ip;
        }
    }

    // ---------------- 설정 ----------------

    public void setMaxAllowedOnlinePlayers(int max) {
        _maxAllowedOnlinePlayers = max;
    }

    public int getMaxAllowedOnlinePlayers() {
        return _maxAllowedOnlinePlayers;
    }

    public void setReloginGrace(Duration d) {
        _reloginGraceMs = d.toMillis();
    }

    // ---------------- 읽기 ----------------

    public int getOnlinePlayerCount() {
        return _online.get();
    }

    public CUserSocket getClientByAccount(String accountName) {
        final String key = accountKey(accountName);
        if (key == null) return null;
        final CClientSession r = _sessions.get(key);
        return (r != null) ? r.client : null;
    }

    public int getIpCount(String ip) {
        final AtomicInteger c = (ip != null) ? _perIp.get(ip) : null;
        return (c != null) ? c.get() : 0;
    }

    public int getCClassCount(String ip) {
        if (ip == null) {
            return 0;
        }

        final String validIp = requireIp(ip);
        final AtomicInteger c = _perCClass.get(cClassOf(validIp));
        return (c != null) ? c.get() : 0;
    }

    // ---------------- 로그인 ----------------

    public void login(CUserSocket client, Account account)
            throws GameServerFullException, AccountAlreadyLoginException {
        if (!loginWithinIpLimit(client, account, Integer.MAX_VALUE))
            throw new IllegalStateException("AuthD IP counter overflow");
    }

    boolean loginWithinIpLimit(CUserSocket client, Account account, int limit)
            throws GameServerFullException, AccountAlreadyLoginException {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(account, "account");

        if (!account.isValid()) {
            throw new IllegalArgumentException("인증되지 않은 계정입니다");
        }

        final String id = accountKey(account.getName());
        if (id == null) throw new IllegalArgumentException("빈 계정명");
        final String ip = requireIp(client.getIp());
        synchronized (client) {
            if (!client.isConnected()) throw new IllegalStateException("Login session already closed");
            if (client.loginRegistryKey() != null) throw new AccountAlreadyLoginException();
            reserveOnline(account.isGameMaster());

            boolean reservedIp = false;
            boolean registered = false;
            try {
                reservedIp = reserveIp(ip, limit);
                if (!reservedIp) return false;
                final CClientSession newRec = new CClientSession(client, ip);
                for (;;) {
                    final CClientSession existing = _sessions.putIfAbsent(id, newRec);
                    if (existing == null) break;
                    if (isDeadSession(existing.client)) {
                        if (!_sessions.replace(id, existing, newRec)) continue;
                        _log.warning("[CAccountContainer] 좀비 세션 교체 - account=" + id
                                + " old=" + System.identityHashCode(existing.client)
                                + " new=" + System.identityHashCode(client));
                        decrementSessionCounts(existing);
                        break;
                    }
                    _log.warning("[CAccountContainer] 중복 로그인 거부 - account=" + id);
                    scheduleKickIfNeeded(existing.client, id);
                    throw new AccountAlreadyLoginException();
                }

                // Terminal cleanup uses this key even if sending the login reply
                // or publishing the Account object has not completed.
                increment(_perCClass, cClassOf(ip));
                client.setLoginRegistryKey(id);
                registered = true;
                return true;
            } finally {
                if (!registered) {
                    if (reservedIp) decrement(_perIp, ip);
                    decrementOnline();
                }
            }
        }
    }

    private boolean reserveIp(String ip, int limit) {
        if (limit <= 0) return false;
        if (limit == Integer.MAX_VALUE) {
            increment(_perIp, ip);
            return true;
        }
        final AtomicBoolean reserved = new AtomicBoolean();
        _perIp.compute(ip, (key, count) -> {
            if (count == null) {
                reserved.set(true);
                return new AtomicInteger(1);
            }
            if (count.get() < limit) {
                count.incrementAndGet();
                reserved.set(true);
            }
            return count;
        });
        return reserved.get();
    }

    private void reserveOnline(boolean gm) throws GameServerFullException {
        if (gm) {
            _online.incrementAndGet();
            return;
        }
        for (;;) {
            final int current = _online.get();
            if (current >= _maxAllowedOnlinePlayers) throw new GameServerFullException();
            if (_online.compareAndSet(current, current + 1)) return;
        }
    }

    // ---------------- 로그아웃 ----------------

    public boolean logout(CUserSocket client) {
        if (client == null) return false;
        synchronized (client) {
            final String id = client.loginRegistryKey();
            if (id == null) return false; // never registered, including pre-auth transport close
            client.setLoginRegistryKey(null);
            final CClientSession existing = _sessions.get(id);
            if (existing == null || existing.client != client || !_sessions.remove(id, existing))
                return false;
            decrementSessionCounts(existing);
            return true;
        }
    }

    private void decrementSessionCounts(CClientSession removed) {
        // 로그인 시점에 저장한 IP 기준으로 차감
        decrement(_perIp, removed.ip);
        decrement(_perCClass, cClassOf(removed.ip));
        decrementOnline();
    }

    // ---------------- 킥 ----------------

    /**
     * 같은 계정에 이미 유효한 kick 예약이 있으면 Runnable을 추가로 만들지 않는다.
     * 예약 시각이 이미 지났는데 작업이 아직 실행되지 않았다면 CAS로 새 예약을 건다.
     */
    private void scheduleKickIfNeeded(final CUserSocket old, final String id) {
        final long now = System.currentTimeMillis();
        final long dueAt = now + _reloginGraceMs;
        final Long dueBox = Long.valueOf(dueAt);

        for (;;) {
            final Long existing = _pendingKickUntil.putIfAbsent(id, dueBox);

            if (existing == null) {
                scheduleKick(old, id, dueAt, dueBox);
                return;
            }

            if (now < existing.longValue()) {
                return;
            }

            // 이전 예약은 시간이 지났다. 그 값이 그대로일 때만 새 예약으로 교체.
            if (_pendingKickUntil.replace(id, existing, dueBox)) {
                scheduleKick(old, id, dueAt, dueBox);
                return;
            }
        }
    }

    private void scheduleKick(
            final CUserSocket old,
            final String id,
            final long dueAt,
            final Long dueBox) {

        long delay = dueAt - System.currentTimeMillis();
        if (delay < 0L) {
            delay = 0L;
        }

        final Runnable clearKick = () -> _pendingKickUntil.computeIfPresent(id,
                (key, current) -> current == dueBox ? null : current);
        final CServerTimer scheduled = CServerTimer.onceSignalOnShard(old.affinityShard(), new Runnable() {
            @Override
            public void run() {
                try {
                    // An earlier grace-period callback must not close a new
                    // session or bypass a newer kick reservation.
                    if (_pendingKickUntil.get(id) != dueBox) return;
                    final CClientSession current = _sessions.get(id);
                    if (current == null || current.client != old) return;
                    try {
                        if (old.getActiveChar() != null) {
                            SC_ServerMessage.send(old.getActiveChar(), 357);
                        }
                    } catch (Throwable ignore) {
                        // Message failure cannot prevent the network close.
                    }
                    old.kick();
                } finally {
                    clearKick.run();
                }
            }
        }, delay, clearKick);
        if (scheduled == null) {
            // Schedule reports overload with null rather than
            // throwing. Never keep an unscheduled deadline in the map: it
            // would suppress every further kick attempt until it expires.
            clearKick.run();
            _log.warning("[CAccountContainer] duplicate session kick not scheduled: " + id);
        }
    }

    // ---------------- 좀비 판정 ----------------

    /** A login reserves the account before Account publication. Only cleanup
     * completion identifies an old socket that can no longer own a session. */
    private static boolean isDeadSession(CUserSocket c) {
        return c == null || c.isClientCleanupComplete();
    }

    // ---------------- 카운터 ----------------

    private static void increment(
            ConcurrentHashMap<String, AtomicInteger> map,
            String key) {

        map.compute(key, (k, count) -> {
            if (count == null) return new AtomicInteger(1);
            count.incrementAndGet();
            return count;
        });
    }

    private static void decrement(
            ConcurrentHashMap<String, AtomicInteger> map,
            String key) {

        if (key == null) {
            return;
        }

        map.computeIfPresent(
                key,
                (k, v) -> (v.decrementAndGet() <= 0) ? null : v);
    }

    /**
     * 비정상 중복 정리에서도 음수까지 내려가지 않게 방어.
     */
    private void decrementOnline() {
        for (;;) {
            final int current = _online.get();

            if (current <= 0) {
                return;
            }

            if (_online.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    // ---------------- 목록 순회 (콘솔/종료 용도) ----------------

    public Iterable<CUserSocket> sessions() {
        final List<CUserSocket> snapshot =
                new ArrayList<>(_sessions.size());

        for (CClientSession r : _sessions.values()) {
            if (r != null) {
                snapshot.add(r.client);
            }
        }

        return snapshot;
    }

    // ---------------- 관리 ----------------

    public int kickAll() {
        final List<CUserSocket> all =
                new ArrayList<>(_sessions.size());

        for (CClientSession r : _sessions.values()) {
            if (r != null) {
                all.add(r.client);
            }
        }

        _pendingKickUntil.clear();

        for (CUserSocket c : all) {
            try {
                try { logout(c); }
                finally { c.kick(); }
            } catch (Throwable ignore) {
            }
        }

        return all.size();
    }
}
