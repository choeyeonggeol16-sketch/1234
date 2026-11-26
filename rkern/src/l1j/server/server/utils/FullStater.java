package l1j.server.server.utils;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class FullStater implements Runnable {
    private static final long STEP_DELAY_MS = 100L;

    private final L1PcInstance player;
    private final String stat;
    private final CUserSocket session;
    private final int stamp;
    private int remaining;

    public static void running(L1PcInstance pc, String stat, int count) {
        final int available = calcBonus(pc);
        if (available <= 0 || count <= 0 || available < count) {
            sendInsufficient(pc, available);
            return;
        }

        if (count == 1) {
            try {
                if (!pc.onStat(stat)) {
                    sendInsufficient(pc, calcBonus(pc));
                    return;
                }
                sendSuccess(pc, stat);
                sendRemaining(pc);
            } catch (Exception failure) {
                sendInsufficient(pc, calcBonus(pc));
            }
            return;
        }

        final CUserSocket session = pc.getNetConnection();
        if (session != null) new FullStater(pc, stat, count, session).schedule(1L);
    }

    private FullStater(L1PcInstance player, String stat, int count, CUserSocket session) {
        this.player = player;
        this.stat = stat;
        this.remaining = count;
        this.session = session;
        this.stamp = player.getSessionStamp();
    }

    private void schedule(long delayMs) {
        if (!ownsCurrentPlayer()) return;
        CIOThreadCallback.getInstance().completeOnShard(session.affinityShard(), delayMs, this, failure -> {
            if (failure != null && ownsCurrentPlayer()) finish(false);
        });
    }

    private boolean ownsCurrentPlayer() {
        return player.getSessionStamp() == stamp && player.getNetConnection() == session
                && session.getActiveChar() == player && session.ownsReadyGameSession();
    }

    @Override
    public void run() {
        if (!ownsCurrentPlayer()) return;
        if (remaining == 0) {
            finish(true);
            return;
        }
        try {
            if (!player.onStat(stat)) {
                finish(false);
                return;
            }
        } catch (Exception failure) {
            finish(false);
            return;
        }
        remaining--;
        schedule(STEP_DELAY_MS);
    }

    private void finish(boolean complete) {
        if (complete) sendSuccess(player, stat);
        else sendInsufficient(player, calcBonus(player));
        sendRemaining(player);
    }

    private static void sendSuccess(L1PcInstance pc, String stat) {
        SC_SystemMessage.send(pc, String.format("%s(이)가 증가되었습니다. [남은 스텟 : %d]", stat, calcBonus(pc)));
    }

    private static void sendInsufficient(L1PcInstance pc, int available) {
        SC_SystemMessage.send(pc, String.format("스텟이 충분하지 않습니다. [남은 스텟 : %d]", available));
    }

    private static void sendRemaining(L1PcInstance pc) {
        SC_MessageYN.send(pc, 479, Integer.toString(calcBonus(pc)));
    }

    private static int calcBonus(L1PcInstance pc) {
        return pc.getLevel() - 50 - pc.getBonusStats();
    }
}
