package l1j.server.server.model.character.scheduler;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class CharTamScheduler implements Runnable {

    // ==========================
    // - 스케줄(run) : 1분마다
    // - 탐 지급     : PAY_INTERVAL_MS(기존 Config.Tam_Time 분)마다만
    // - DB flush    : MIN_FLUSH_INTERVAL_MS(예: 60분) 쿨타임
    // ==========================

    /** run() 스케줄 주기: 1분 고정 */
    private static final long SCHEDULE_PERIOD_MS = 60_000L;

    private static long PAY_INTERVAL_MS = computePayIntervalMs();

    private static long computePayIntervalMs() {
        long cfgMin = 0L;
        try {
            cfgMin = (long) Config.Tam_Time;
        } catch (Throwable ignore) {
        }
        if (cfgMin <= 0) cfgMin = 30L;
        if (cfgMin < 30L) cfgMin = 30L;
        return cfgMin * 60_000L;
    }

    private static final Logger _log = Logger.getLogger(CharTamScheduler.class.getName());

    private static CharTamScheduler _instance;

    public static CharTamScheduler getInstance() {
        if (_instance == null) {
            _instance = new CharTamScheduler();
        }
        return _instance;
    }

    // ====== 서버 시작시 1회 로드(스케줄 등록)용 ======
    private final AtomicBoolean _started = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> _future;

    // run 재진입 방지
    private final AtomicBoolean _running = new AtomicBoolean(false);

    /** 서버 부팅 시 1번만 호출 */
    public void start() {
        if (!_started.compareAndSet(false, true)) {
            return;
        }

        PAY_INTERVAL_MS = computePayIntervalMs();

        final long initialDelay = 1000L;

        try {
            _future = ThreadPool.getInstance().scheduleAtFixedRate(
                    this, initialDelay, SCHEDULE_PERIOD_MS
            );
//            System.out.println("[TamController] 가동 시작. schedulePeriodMs=" + SCHEDULE_PERIOD_MS
//                    + " payIntervalMs=" + PAY_INTERVAL_MS
//                    + " (Config.Tam_Time(min)=" + Config.Tam_Time + ")");
        } catch (Throwable t) {
            _started.set(false);
            _log.log(Level.SEVERE, "[TamController] start failed", t);
        }
    }

    /** 서버 종료 시(원하면) */
    public void stop() {
        ScheduledFuture<?> f = _future;
        _future = null;

        if (f != null) {
            try {
                f.cancel(false);
            } catch (Throwable ignore) {
            }
        }

        _started.set(false);

        synchronized (_targetsLock) {
            _targets.clear();
        }

        synchronized (_dirtyLock) {
            _dirty.clear();
        }

        _dueQueue.clear();
    }

    // ==========================
    // 탐 5개로 한달 800~820만(풀가동 가정) 맞춘 고정값
    // 30분 주기 기준: 탐 1개당 30분마다 1130 (= 15분 565 * 2)
    // ==========================
    private static int TAM_PER_TICK_PER_RING = Config.탐갯수;

    // ===== 튜닝 =====
    private static final long TARGET_TTL_MS = 35L * 60L * 1000L;

    // DB 부하: updateTam() 최소 호출 간격
    private static final long MIN_FLUSH_INTERVAL_MS = 60L * 60L * 1000L;

    // 한 번 run에서 DB flush 최대 처리량
    private static final int MAX_FLUSH_PER_RUN = 200;

    private final TIntObjectHashMap<TargetState> _targets = new TIntObjectHashMap<TargetState>();
    private final Object _targetsLock = new Object();

    private final TIntObjectHashMap<AccountState> _dirty = new TIntObjectHashMap<AccountState>();
    private final Object _dirtyLock = new Object();

    // due 시간이 되면 poll되는 큐
    private final DelayQueue<DueAcc> _dueQueue = new DelayQueue<DueAcc>();

    private int[] _targetSnapshot = new int[64];

    private CharTamScheduler() {
    }

    private static final class TargetState {
        volatile long lastTouchMs;
        volatile L1PcInstance pc;

        TargetState(L1PcInstance pc, long now) {
            this.lastTouchMs = now;
            this.pc = pc;
        }

        L1PcInstance getPc() {
            return pc;
        }

        void touch(long now) {
            lastTouchMs = now;
        }

        void setPc(L1PcInstance p) {
            pc = p;
        }
    }

    private static final class AccountState {
        /* AtomicLong/AtomicBoolean/Object lock을 없애고 AccountState 자신을 lock으로 사용 */
        long stamp;
        boolean queued;

        volatile long lastTouchMs;
        volatile long lastFlushMs;

        /** 마지막 탐 지급 시간 */
        volatile long lastPayMs;

        volatile Account acc;

        AccountState(Account acc, long now) {
            this.lastTouchMs = now;
            this.lastFlushMs = 0L;
            this.lastPayMs = now;
            this.acc = acc;
        }

        Account getAcc() {
            return acc;
        }

        void setAcc(Account a) {
            acc = a;
        }

        void touch(long now) {
            lastTouchMs = now;
        }
    }

    private static final class DueAcc implements Delayed {
        // Integer -> int : DelayQueue 엔트리에서도 boxing 제거
        final int accId;
        final long dueAtMs;

        DueAcc(int accId, long dueAtMs) {
            this.accId = accId;
            this.dueAtMs = dueAtMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remain = dueAtMs - System.currentTimeMillis();
            return unit.convert(remain, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (o == this) {
                return 0;
            }
            long diff = this.dueAtMs - ((DueAcc) o).dueAtMs;
            return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
        }
    }

    @Override
    public void run() {
        if (!_running.compareAndSet(false, true)) {
            return;
        }

        try {
            final long now = System.currentTimeMillis();
            tickTargets(now);
            flushDueAccounts(now);
            cleanupTargetTTL(now);
            cleanupDeadDirty(now);
        } catch (Throwable t) {
            _log.log(Level.WARNING, "[TamController] run failed", t);
        } finally {
            _running.set(false);
        }
    }

    // ==========================
    // 외부 훅: 로그인/로그아웃에서 호출
    // ==========================
    public void onEnterWorld(L1PcInstance pc) {
        if (pc == null) {
            return;
        }

        final long now = System.currentTimeMillis();
        tryRegisterIfEligible(pc, now);
    }

    public void onLeaveWorld(L1PcInstance pc) {
        if (pc == null) {
            return;
        }

        final int pcId = safePcId(pc);
        if (pcId > 0) {
            synchronized (_targetsLock) {
                _targets.remove(pcId);
            }
        }

        tryForceFlushOnLeave(pc);
    }

    private void tryForceFlushOnLeave(L1PcInstance pc) {
        final Account acc;
        final int accId;
        final long now = System.currentTimeMillis();

        try {
            if (pc.getNetConnection() == null) {
                return;
            }

            acc = pc.getNetConnection().getAccount();
            if (acc == null) {
                return;
            }

            accId = acc.getAccountId();
            if (accId <= 0) {
                return;
            }
        } catch (Throwable t) {
            return;
        }

        markDirty(accId, acc, now);
        forceFlush(accId, now);
    }

    // ==========================
    // Target map helpers
    // ==========================


    private int snapshotTargetIds() {
        synchronized (_targetsLock) {
            final int size = _targets.size();
            if (size <= 0) {
                    return 0;
            }

            if (_targetSnapshot.length < size) {
                int newSize = _targetSnapshot.length;
                while (newSize < size) {
                    newSize <<= 1;
                }
                _targetSnapshot = new int[newSize];
            }

            int index = 0;
            TIntObjectIterator<TargetState> it = _targets.iterator();
            while (it.hasNext()) {
                it.advance();
                _targetSnapshot[index++] = it.key();
            }

            return index;
        }
    }

    private TargetState getTargetState(int pcId) {
        synchronized (_targetsLock) {
            return _targets.get(pcId);
        }
    }

    private void removeTargetIfSame(int pcId, TargetState expected) {
        synchronized (_targetsLock) {
            if (_targets.get(pcId) == expected) {
                _targets.remove(pcId);
            }
        }
    }

    // ==========================
    // (1) 대상자 순회 + 지급 + dirty 마킹
    // ==========================
    private void tickTargets(final long nowMs) {
        final int size = snapshotTargetIds();
        if (size <= 0) {
            return;
        }

        for (int i = 0; i < size; i++) {
            final int pcId = _targetSnapshot[i];
            final TargetState ts = getTargetState(pcId);

            if (ts == null) {
                continue;
            }

            final L1PcInstance pc = ts.getPc();
            if (pc == null) {
                removeTargetIfSame(pcId, ts);
                continue;
            }

            if (!isEligibleNow(pc)) {
                continue;
            }

            ts.touch(nowMs);

            final int tamcount;
            try {
                tamcount = pc.tamCount();
            } catch (Throwable t) {
                removeTargetIfSame(pcId, ts);
                continue;
            }

            if (tamcount <= 0) {
                removeTargetIfSame(pcId, ts);
                continue;
            }

            final Account acc;
            try {
                if (pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) {
                    removeTargetIfSame(pcId, ts);
                    continue;
                }
                acc = pc.getNetConnection().getAccount();
            } catch (Throwable ignore) {
                continue;
            }

            if (acc == null) {
                continue;
            }

            final int accId;
            try {
                accId = acc.getAccountId();
            } catch (Throwable t) {
                continue;
            }

            if (accId <= 0) {
                continue;
            }

            final AccountState as = ensureAccountState(accId, acc, nowMs);
            if (as == null) {
                continue;
            }

            synchronized (as) {
                if (as.lastPayMs > 0 && (nowMs - as.lastPayMs) < PAY_INTERVAL_MS) {
                    continue;
                }

                final int addtam = TAM_PER_TICK_PER_RING * tamcount;

                try {
                    acc.addTamPoint(addtam);
                } catch (Throwable t) {
                    continue;
                }

                as.lastPayMs = nowMs;
                markDirtyInternalLocked(accId, as, nowMs);

                try {
                    SC_SystemMessage.send(pc, String.format("[성장의 고리] %d단계 : Tam포인트(%d) 획득", tamcount, addtam));
                } catch (Throwable ignore) {
                }

                try {
                    SC_NewCreateItem.sendTamPoint(pc, pc.getNetConnection());
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private AccountState ensureAccountState(final int accId, final Account acc, final long nowMs) {
        synchronized (_dirtyLock) {
            AccountState st = _dirty.get(accId);

            if (st == null) {
                st = new AccountState(acc, nowMs);
                _dirty.put(accId, st);
                return st;
            }

            synchronized (st) {
                if (st.getAcc() == null || st.getAcc() != acc) {
                    st.setAcc(acc);
                }
                st.touch(nowMs);
            }

            return st;
        }
    }

    private void markDirty(final int accId, final Account acc, final long nowMs) {
        final AccountState st = ensureAccountState(accId, acc, nowMs);
        if (st == null) {
            return;
        }

        synchronized (st) {
            markDirtyInternalLocked(accId, st, nowMs);
        }
    }

    /** 반드시 synchronized(st) 안에서 호출 */
    private void markDirtyInternalLocked(final int accId, final AccountState st, final long nowMs) {
        st.stamp++;

        if (!st.queued) {
            st.queued = true;
            _dueQueue.offer(new DueAcc(accId, nowMs));
        }
    }

    private boolean isEligibleNow(L1PcInstance pc) {
        if (pc == null) {
            return false;
        }

        try {
            if (pc.isDead()) return false;
        } catch (Throwable t) {
            return false;
        }

        try {
            if (pc.isPrivateShop()) return false;
        } catch (Throwable t) {
            return false;
        }

        try {
            if (pc.noPlayerCK) return false;
        } catch (Throwable t) {
            return false;
        }

        try {
            if (pc.noPlayerck2) return false;
        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private void tryRegisterIfEligible(L1PcInstance pc, long nowMs) {
        if (!isEligibleNow(pc)) {
            return;
        }

        final int tamcount;
        try {
            tamcount = pc.tamCount();
        } catch (Throwable t) {
            return;
        }

        if (tamcount <= 0) {
            return;
        }

        final int pcId = safePcId(pc);
        if (pcId <= 0) {
            return;
        }

        synchronized (_targetsLock) {
            TargetState st = _targets.get(pcId);

            if (st == null) {
                _targets.put(pcId, new TargetState(pc, nowMs));
            } else {
                if (st.getPc() == null || st.getPc() != pc) {
                    st.setPc(pc);
                }
                st.touch(nowMs);
            }
        }
    }

    private int safePcId(L1PcInstance pc) {
        try {
            return pc.getId();
        } catch (Throwable t) {
            return 0;
        }
    }

    // ==========================
    // (2) 대상자 TTL 청소
    // ==========================
    private void cleanupTargetTTL(final long nowMs) {
        synchronized (_targetsLock) {
            if (_targets.isEmpty()) {
                return;
            }

            TIntObjectIterator<TargetState> it = _targets.iterator();
            while (it.hasNext()) {
                it.advance();

                final TargetState st = it.value();
                if (st == null) {
                    it.remove();
                    continue;
                }

                if ((nowMs - st.lastTouchMs) > TARGET_TTL_MS) {
                    it.remove();
                }
            }
        }
    }

    // ==========================
    // Dirty map helpers
    // ==========================
    private AccountState getDirtyState(int accId) {
        synchronized (_dirtyLock) {
            return _dirty.get(accId);
        }
    }

    /**
     * state가 여전히 같은 객체이고 flush 이후 추가 변경이 없을 때만 제거한다.
     * DelayQueue에 stale DueAcc가 남아 있어도 map에서 빠지면 poll 시 자연스럽게 폐기된다.
     */
    private void removeDirtyIfUnchanged(int accId, AccountState expected, long expectedStamp) {
        synchronized (_dirtyLock) {
            if (_dirty.get(accId) != expected) {
                return;
            }

            synchronized (expected) {
                if (expected.stamp == expectedStamp) {
                    expected.queued = false;
                    _dirty.remove(accId);
                }
            }
        }
    }

    private void removeDirtyIfSame(int accId, AccountState expected) {
        synchronized (_dirtyLock) {
            if (_dirty.get(accId) == expected) {
                _dirty.remove(accId);
            }
        }
    }

    // ==========================
    // (3) due가 된 dirty만 updateTam() 호출
    // ==========================
    private void flushDueAccounts(final long nowMs) {
        int processed = 0;

        while (processed < MAX_FLUSH_PER_RUN) {
            final DueAcc due = _dueQueue.poll();
            if (due == null) {
                break;
            }

            final int accId = due.accId;
            final AccountState st = getDirtyState(accId);
            if (st == null) {
                continue;
            }

            final Account acc;
            final long before;

            synchronized (st) {
                // 현재 due 하나를 소비했으므로 다시 queue 가능 상태로 전환
                st.queued = false;

                acc = st.getAcc();
                if (acc == null) {
                    // lock 밖에서 제거
                    before = st.stamp;
                } else {
                    // flush 쿨타임
                    if (st.lastFlushMs > 0) {
                        long nextAllowed = st.lastFlushMs + MIN_FLUSH_INTERVAL_MS;
                        if (nowMs < nextAllowed) {
                            if (!st.queued) {
                                st.queued = true;
                                _dueQueue.offer(new DueAcc(accId, nextAllowed));
                            }
                            continue;
                        }
                    }

                    before = st.stamp;
                }
            }

            if (acc == null) {
                removeDirtyIfSame(accId, st);
                continue;
            }

            try {
                // DB I/O 중에는 st lock을 잡지 않는다.
                acc.updateTam();
                processed++;

                long after;
                synchronized (st) {
                    st.lastFlushMs = nowMs;
                    after = st.stamp;

                    if (after != before) {
                        // DB 쓰는 동안 새 변경이 들어왔음 -> 다시 큐잉
                        if (!st.queued) {
                            st.queued = true;
                            _dueQueue.offer(new DueAcc(accId, nowMs));
                        }
                    }
                }

                if (after == before) {
                    removeDirtyIfUnchanged(accId, st, after);
                }
            } catch (Throwable t) {
                _log.log(Level.WARNING, "[TamController] updateTam failed accId=" + accId, t);

                synchronized (st) {
                    if (!st.queued) {
                        st.queued = true;
                        _dueQueue.offer(new DueAcc(accId, nowMs + 5000L));
                    }
                }

                processed++;
            }
        }
    }

    /** 로그아웃/중요 이벤트에서 쿨타임 무시 강제 flush */
    private void forceFlush(final int accId, final long nowMs) {
        final AccountState st = getDirtyState(accId);
        if (st == null) {
            return;
        }

        final Account acc;
        final long before;

        synchronized (st) {
            acc = st.getAcc();
            before = st.stamp;
        }

        if (acc == null) {
            removeDirtyIfSame(accId, st);
            return;
        }

        try {
            acc.updateTam();

            long after;
            synchronized (st) {
                st.lastFlushMs = nowMs;
                after = st.stamp;

                if (after != before) {
                    if (!st.queued) {
                        st.queued = true;
                        _dueQueue.offer(new DueAcc(accId, nowMs));
                    }
                }
            }

            if (after == before) {
                removeDirtyIfUnchanged(accId, st, after);
            }
        } catch (Throwable t) {
            _log.log(Level.WARNING, "[TamController] force updateTam failed accId=" + accId, t);

            synchronized (st) {
                if (!st.queued) {
                    st.queued = true;
                    _dueQueue.offer(new DueAcc(accId, nowMs + 5000L));
                }
            }
        }
    }

    // ==========================
    // dirty 엔트리 약정리
    // ==========================
    private void cleanupDeadDirty(final long nowMs) {
        final long DEAD_MS = 6L * 60L * 60L * 1000L;

        synchronized (_dirtyLock) {
            if (_dirty.isEmpty()) {
                return;
            }

            TIntObjectIterator<AccountState> it = _dirty.iterator();
            while (it.hasNext()) {
                it.advance();

                final AccountState st = it.value();
                if (st == null) {
                    it.remove();
                    continue;
                }

                if (st.getAcc() == null) {
                    it.remove();
                    continue;
                }

                final long last = Math.max(st.lastTouchMs, st.lastFlushMs);
                if (last > 0 && (nowMs - last) > DEAD_MS) {
                    it.remove();
                }
            }
        }
    }
}
