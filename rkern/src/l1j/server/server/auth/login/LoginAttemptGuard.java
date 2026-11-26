package l1j.server.server.auth.login;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 로그인 무차별 대입 방지 — IP당 비번 실패 누적, 10분 내 5회 실패 시 차단.
 */
public final class LoginAttemptGuard {

    private static final int MAX_FAILS = 5;

    private static final Cache<String, AtomicInteger> FAILS = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))   // 실패 기록 1분 후 자동 소멸
            .maximumSize(100_000)
            .build();

    private LoginAttemptGuard() {}

    public static void onFail(String ip) {
        if (ip == null || ip.isEmpty()) return;
        FAILS.asMap().compute(ip, (k, v) -> {
            if (v == null) v = new AtomicInteger();
            v.incrementAndGet();
            return v;
        });
    }

    public static boolean isBlocked(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        AtomicInteger n = FAILS.getIfPresent(ip);
        return n != null && n.get() >= MAX_FAILS;
    }

    public static void clear(String ip) {
        if (ip == null) return;
        FAILS.invalidate(ip);
    }
}