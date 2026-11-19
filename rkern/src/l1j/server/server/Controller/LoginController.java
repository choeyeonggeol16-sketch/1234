package l1j.server.server.Controller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.AccountAlreadyLoginException;
import l1j.server.server.GameServerFullException;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/**
 * 로그인 세션 레지스트리
 */
public final class LoginController {

    private static final Logger _log = Logger.getLogger(LoginController.class.getName());

    // ---------------- 코어 ----------------

    private static volatile LoginController _instance;

    public static LoginController getInstance() {
        LoginController inst = _instance;
        if (inst == null) {
            synchronized (LoginController.class) {
                inst = _instance;
                if (inst == null) {
                    inst = new LoginController();
                    _instance = inst;
                }
            }
        }
        return inst;
    }

    /** 정규화된 accountName -> session */
    private final ConcurrentHashMap<String, SessionRec> _sessions =
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

    private LoginController() {
    }

    // ---------------- 값/키 유틸 ----------------

    /**
     * 기존 AccountId.of()와 같은 정규화 규칙.
     * AccountId 객체 자체는 만들지 않는다.
     */
    private static String accountKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("빈 계정명");
        }
        return raw.trim().toLowerCase();
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
    private static final class SessionRec {
        final CIOClient client;
        final String ip;

        SessionRec(CIOClient client, String ip) {
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

    public CIOClient getClientByAccount(String accountName) {
        if (accountName == null) {
            return null;
        }

        final SessionRec r = _sessions.get(accountKey(accountName));
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

    public void login(CIOClient client, Account account)
            throws GameServerFullException, AccountAlreadyLoginException {

        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(account, "account");

        if (!account.isValid()) {
            throw new IllegalArgumentException("인증되지 않은 계정입니다");
        }

        final String id = accountKey(account.getName());
        final String ip = requireIp(client.getIp());
        final String cClass = cClassOf(ip);

        // 용량 — GM 우회
        if (_online.get() >= _maxAllowedOnlinePlayers && !account.isGameMaster()) {
            throw new GameServerFullException();
        }

        final SessionRec newRec = new SessionRec(client, ip);

        SessionRec replaced = null;
        SessionRec duplicate = null;

        for (;;) {
            final SessionRec existing = _sessions.putIfAbsent(id, newRec);

            if (existing == null) {
                break;
            }

            if (isDeadSession(existing.client)) {
                if (_sessions.replace(id, existing, newRec)) {
                    replaced = existing;
                    break;
                }
                continue;
            }

            duplicate = existing;
            break;
        }

        // 좀비를 교체했다면 기존 세션 몫 카운터 정리
        if (replaced != null) {
            _log.warning("[LoginController] 좀비 세션 교체 - account=" + id
                    + " old=" + System.identityHashCode(replaced.client)
                    + " new=" + System.identityHashCode(client));

            decrement(_perIp, replaced.ip);
            decrement(_perCClass, cClassOf(replaced.ip));
            decrementOnline();
        }

        // 살아있는 진짜 중복만 거부
        if (duplicate != null) {
            _log.warning("[LoginController] 중복 로그인 거부 - account=" + id);
            scheduleKickIfNeeded(duplicate.client, id);
            throw new AccountAlreadyLoginException();
        }

        increment(_perIp, ip);
        increment(_perCClass, cClass);
        _online.incrementAndGet();
    }

    // ---------------- 로그아웃 ----------------

    public boolean logout(CIOClient client) {
        if (client == null) {
            return false;
        }

        final String rawName = client.getAccountName();
        SessionRec removed = null;

        if (rawName != null) {
            final String id = accountKey(rawName);
            final SessionRec existing = _sessions.get(id);

            if (existing != null
                    && existing.client == client
                    && _sessions.remove(id, existing)) {
                removed = existing;
            }

        } else {
         
            for (Map.Entry<String, SessionRec> e : _sessions.entrySet()) {
                final SessionRec rec = e.getValue();

                if (rec != null && rec.client == client) {
                    if (_sessions.remove(e.getKey(), rec)) {
                        removed = rec;
                    }
                    break;
                }
            }
        }

        if (removed == null) {
            _log.info("[LoginController] logout: 제거할 세션 없음 - account="
                    + ((rawName != null) ? rawName : "(null)"));
            return false;
        }

        // 로그인 시점에 저장한 IP 기준으로 차감
        decrement(_perIp, removed.ip);
        decrement(_perCClass, cClassOf(removed.ip));
        decrementOnline();

        return true;
    }

    // ---------------- 킥 ----------------

    /**
     * 같은 계정에 이미 유효한 kick 예약이 있으면 Runnable을 추가로 만들지 않는다.
     * 예약 시각이 이미 지났는데 작업이 아직 실행되지 않았다면 CAS로 새 예약을 건다.
     */
    private void scheduleKickIfNeeded(final CIOClient old, final String id) {
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
            final CIOClient old,
            final String id,
            final long dueAt,
            final Long dueBox) {

        long delay = dueAt - System.currentTimeMillis();
        if (delay < 0L) {
            delay = 0L;
        }

        final ScheduledFuture<?> scheduled = ThreadPool.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    // An earlier grace-period callback must not close a new
                    // session or bypass a newer kick reservation.
                    if (_pendingKickUntil.get(id) != dueBox) return;
                    final SessionRec current = _sessions.get(id);
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
                    _pendingKickUntil.computeIfPresent(id, (key, current) -> current == dueBox ? null : current);
                }
            }
        }, delay);
        if (scheduled == null) {
            // ThreadPool.schedule reports overload with null rather than
            // throwing. Never keep an unscheduled deadline in the map: it
            // would suppress every further kick attempt until it expires.
            _pendingKickUntil.computeIfPresent(id, (key, current) -> current == dueBox ? null : current);
            _log.warning("[LoginController] duplicate session kick not scheduled: " + id);
        }
    }

    // ---------------- 좀비 판정 ----------------

    /**
     * 이미 끊긴(정리 진행 중이거나 끝난) 연결인지 판정.
     * GameClient 정리 과정에서 계정명이 null로 바뀌는 현상을 이용한다.
     * GameClient에 종료 플래그 getter(disconnected 류)가 있다면
     * 추후 그 getter로 교체하는 편이 더 정확하다.
     */
    private static boolean isDeadSession(CIOClient c) {
        if (c == null) {
            return true;
        }

        try {
            return c.getAccountName() == null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------- 카운터 ----------------

    private static void increment(
            ConcurrentHashMap<String, AtomicInteger> map,
            String key) {

        map.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
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

    public Iterable<CIOClient> sessions() {
        final List<CIOClient> snapshot =
                new ArrayList<>(_sessions.size());

        for (SessionRec r : _sessions.values()) {
            if (r != null) {
                snapshot.add(r.client);
            }
        }

        return snapshot;
    }

    // ---------------- 관리 ----------------

    public int kickAll() {
        final List<CIOClient> all =
                new ArrayList<>(_sessions.size());

        for (SessionRec r : _sessions.values()) {
            if (r != null) {
                all.add(r.client);
            }
        }

        _sessions.clear();
        _perIp.clear();
        _perCClass.clear();
        _pendingKickUntil.clear();
        _online.set(0);

        for (CIOClient c : all) {
            try {
                c.kick();
            } catch (Throwable ignore) {
            }
        }

        return all.size();
    }
}
