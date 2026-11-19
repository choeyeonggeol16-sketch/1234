package l1j.server.server.model;

import java.util.concurrent.ScheduledFuture;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.network.core.sc.packet.SC_PinkName;

/**
 * Pink name controller
 */
public class L1PinkName {

    private L1PinkName() {}

    // =========================================
    // PC별 타이머 핸들(중복 방지용)
    // =========================================
    private static ScheduledFuture<?> getPinkFuture(L1PcInstance pc) {
        return pc.getPinkNameFuture();
    }

    private static void setPinkFuture(L1PcInstance pc, ScheduledFuture<?> f) {
        pc.setPinkNameFuture(f);
    }

    // =========================================
    // 시작 조건 공통
    // =========================================
    private static boolean isWarAreaNow(L1PcInstance pc) {
        int castleId = L1CastleLocation.getCastleIdByArea(pc);
        return castleId != 0 && MJCastleWarBusiness.getInstance().isNowWar(castleId);
    }

    private static boolean canApplyPink(L1PcInstance targetAreaPc, L1PcInstance attacker) {
        if (attacker == null) return false;
        if (attacker.getZoneType() != 0) return false;             // 안전지대/특수존 제외
        if (isWarAreaNow(targetAreaPc != null ? targetAreaPc : attacker)) return false;
        return true;
    }

    // =========================================
    // PinkName 엔진
    // =========================================
    private static void startOrExtendPink(L1PcInstance attacker, int seconds) {
        if (attacker == null) return;

        // 시간만 올려주고, 타이머는 1개만 보장
        int prev = attacker.SetPinkNameTime(seconds);
        if (prev <= 0) {
            // 최초 시작
            if (!attacker.isPinkName()) {
                attacker.setPinkName(true);
                attacker.send_pink_name(attacker.getPinkNameTime());
            }

            ScheduledFuture<?> old = getPinkFuture(attacker);
            if (old != null && !old.isDone() && !old.isCancelled()) {
                return;
            }

            PinkTicker ticker = new PinkTicker(attacker);
            ScheduledFuture<?> f = ThreadPool.getInstance().scheduleAtFixedRate(ticker, 1000L, 1000L);
            setPinkFuture(attacker, f);
        } else {
            attacker.send_pink_name(attacker.getPinkNameTime());
        }
    }

    private static void stopPink(L1PcInstance pc) {
        if (pc == null) return;

        pc.setPinkName(false);
        pc.SetPinkNameTime(0);

        ScheduledFuture<?> f = getPinkFuture(pc);
        if (f != null) {
            try { f.cancel(false); } catch (Exception ignored) {}
            setPinkFuture(pc, null);
        }
        SC_PinkName.send(pc, pc.getId(), 0);
        SC_PinkName.broadcast(pc, pc.getId(), 0);
    }

    private static final class PinkTicker implements Runnable {
        private final L1PcInstance pc;

        // “중간 갱신”을 1회만 보내고 싶으면(클라가 남은시간 표시를 강하게 원할 때)
        private int nextOptionalSendTime = -1;

        PinkTicker(L1PcInstance pc) {
            this.pc = pc;
            int now = (int) L1GameTimeClock.getInstance().getGameTime().getSeconds();
            this.nextOptionalSendTime = now + pc.getPinkNameTime() + 1;
        }

        @Override
        public void run() {
            if (pc == null) return;

            if (pc.isDead()) {
                stopPink(pc);
                return;
            }
            // 남은 시간 감소
            int left = pc.DecrementPinkNameTime();

            if (left > 0) {
                if (!pc.isPinkName()) {
                    pc.setPinkName(true);
                    pc.send_pink_name(left);
                }
                int now = (int) L1GameTimeClock.getInstance().getGameTime().getSeconds();
                if (nextOptionalSendTime > 0 && now >= nextOptionalSendTime) {
                    pc.send_pink_name(left);
                    nextOptionalSendTime = -1;
                }
                return;
            }
            stopPink(pc);
        }
    }

    // =========================================
    // Public API
    // =========================================
    public static void onAction(L1PcInstance attacker) {
        if (attacker == null) return;

        if (!canApplyPink(attacker, attacker)) return;
        if (attacker.getZoneType() == 0) {
            startOrExtendPink(attacker, 30);
        }
    }

    public static void onAction(L1PcInstance pc, L1PcInstance attacker) {
        if (pc == null || attacker == null) return;
        if (pc.getId() == attacker.getId()) return;
        if (attacker.getFightId() == pc.getId()) return;

        if (pc.getLawful() < 0) return; // 기존 로직 유지
        if (pc.getZoneType() != 0 || attacker.getZoneType() != 0) return;
        if (isWarAreaNow(pc)) return;

        startOrExtendPink(attacker, 30);
    }

    public static void onAction(L1PcInstance target, L1Character attacker) {
        if (attacker.instanceOf(MJL1Type.L1TYPE_PC))
            onAction(target, (L1PcInstance) attacker);
    }

    public static void onAction(L1Character target, L1PcInstance attacker) {
        if (target.instanceOf(MJL1Type.L1TYPE_PC))
            onAction((L1PcInstance) target, attacker);
    }

    public static void onAction(L1Character target, L1Character attacker) {
        if (target.instanceOf(MJL1Type.L1TYPE_PC) && attacker.instanceOf(MJL1Type.L1TYPE_PC))
            onAction((L1PcInstance) target, (L1PcInstance) attacker);
    }

    public static void onHelp(L1Character target, L1Character helper) {
        if (target == null || helper == null) return;
        if (!(helper instanceof L1PcInstance)) return;
        if (!(target instanceof L1PcInstance)) return;

        L1PcInstance helperPc = (L1PcInstance) helper;
        L1PcInstance targetPc = (L1PcInstance) target;

        if (!targetPc.isPinkName()) return;
        if (helperPc.getId() == targetPc.getId()) return;

        if (targetPc.getZoneType() != 0 || helperPc.getZoneType() != 0) return;
        if (isWarAreaNow(helperPc)) return;

        startOrExtendPink(helperPc, 30);
    }
}