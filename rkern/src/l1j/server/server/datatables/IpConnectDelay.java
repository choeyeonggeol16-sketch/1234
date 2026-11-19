package l1j.server.server.datatables;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP 접속 딜레이 가드 —
 *
 * IP당 1초에 3회 이상 접속 시도 시 차단 대상.
 * 서버 재부팅 시 상태 초기화 — 재부팅 후 연속 공격 재차단 (오히려 장점).
 */
public final class IpConnectDelay {

    private static final IpConnectDelay _instance = new IpConnectDelay();
    public static IpConnectDelay getInstance() { return _instance; }

    private IpConnectDelay() {}

    private static final int BAN_THRESHOLD = 3;          // 3회 위반 시 밴
    private static final long CONNECT_COOLDOWN_MS = 1000L; // 1초 딜레이

    private static final class IpState {
        long lastConnectAtMs;      // 마지막 접속 시각 (딜레이 체크용)
        final AtomicInteger rapidCount = new AtomicInteger(0);  // 빠른 재접속 횟수
        volatile boolean banned;   // 밴 여부
    }

    private final ConcurrentHashMap<String, IpState> states = new ConcurrentHashMap<>();

    /** 상태 취득 — 원자적 getOrCreate */
    private IpState stateOf(String ip) {
        IpState s = states.get(ip);
        if (s == null) {
            s = states.computeIfAbsent(ip, k -> new IpState());
        }
        return s;
    }

    /**
     * 접속 허용 여부 판정 — 기존 ipDelayChech + isDelayBan 통합
     * @return true = 접속 허용
     */
    public boolean allowConnect(String ip) {
        final IpState s = stateOf(ip);

        if (s.banned) {
            return false;   // 밴된 IP
        }

        final long now = System.currentTimeMillis();
        final long elapsed = now - s.lastConnectAtMs;

        if (elapsed < CONNECT_COOLDOWN_MS) {
            // 1초 내 재접속 시도 — 급격한 재시도 카운트
            final int count = s.rapidCount.incrementAndGet();
            if (count >= BAN_THRESHOLD) {
                s.banned = true;
                return false;
            }
            return false;   // 딜레이 미충족 — 거절
        }
        // 1초 경과 — 정상 재접속, 카운트 리셋
        s.rapidCount.set(0);
        s.lastConnectAtMs = now;
        return true;
    }

    public void setBanIp(String ip) {
        stateOf(ip).banned = true;
    }

    public boolean isBanned(String ip) {
        return stateOf(ip).banned;
    }

    // ── 접속 카운트 (기존 _ip_list 역할 — 동시성 안전 버전) ──
    private static final ConcurrentHashMap<String, AtomicInteger> _connCounts = new ConcurrentHashMap<>();

    public int getConnetCount(String ip) {
        final AtomicInteger c = _connCounts.get(ip);
        return c != null ? c.get() : 0;
    }

    public void addConnetCount(String ip) {
        _connCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void setConnetCount(String ip) {
        _connCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).set(0);
    }
}