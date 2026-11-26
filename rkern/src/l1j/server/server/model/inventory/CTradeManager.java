package l1j.server.server.model.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Active trade registry. Only participants in a live exchange occupy entries.
 * The registry monitor never calls a trade method that acquires its monitor.
 */
public final class CTradeManager {
    private static final CTradeManager INSTANCE = new CTradeManager();
    private final Int2ObjectOpenHashMap<CTrade> trades = new Int2ObjectOpenHashMap<>(16);
    private int lastId;
    private CTradeManager() {}
    public static CTradeManager getInstance() { return INSTANCE; }
    public synchronized CTrade FindTrade(int id) { return trades.get(id); }

    public boolean CreateTrade(L1PcInstance requester, L1PcInstance replier) {
        final CTrade trade = attach(requester, replier);
        if (trade == null) return false;
        try {
            final Timeout timeout = new Timeout(trade.GetID(), trade.TimerShard());
            if (!CIOThreadCallback.getInstance().scheduleNetworkTimeout(timeout.shard, timeout, CTrade.TIMEOUT_MS)) {
                trade.Canceled(requester);
                return false;
            }
            return trade.SendRequest();
        } catch (RuntimeException failure) {
            trade.Canceled(requester);
            throw failure;
        }
    }
    private synchronized CTrade attach(L1PcInstance first, L1PcInstance second) {
        if (first == null || second == null || first == second || first.getId() == second.getId()
                || first.GetTrade() != null || second.GetTrade() != null
                || lastId == Integer.MAX_VALUE) return null;
        final CUserSocket a = first.getNetConnection(), b = second.getNetConnection();
        final int as = first.getSessionStamp(), bs = second.getSessionStamp();
        if (a == null || b == null || first.isDead() || second.isDead() || first.isGhost() || second.isGhost()
                || first.isPrivateShop() || second.isPrivateShop() || first.getMapId() != second.getMapId()
                || first.getAccountName() == null || second.getAccountName() == null
                || first.getAccountName().equalsIgnoreCase(second.getAccountName())) return null;
        // Exclude a racing non-trade receipt before publishing either PC's trade.
        // These are the existing short CAS item gates, not an additional per-PC lock.
        final int at = first.tryReserveItemTransfer(a, as);
        if (at == 0) return null;
        int bt = 0;
        try {
            bt = second.tryReserveItemTransfer(b, bs);
            if (bt == 0 || !first.isItemTransferSession(a, as) || !second.isItemTransferSession(b, bs)) return null;
            final CTrade trade = new CTrade(++lastId, first, second, a, b, as, bs);
            trades.put(trade.GetID(), trade);
            first.SetTrade(trade);
            second.SetTrade(trade);
            return trade;
        } finally {
            if (bt != 0) second.endItemTransfer(bt);
            first.endItemTransfer(at);
        }
    }
    synchronized void DeleteTrade(CTrade trade) {
        if (trades.get(trade.GetID()) != trade || !trade.IsEnded()) return;
        trades.remove(trade.GetID());
        if (trade.first().GetTrade() == trade) trade.first().SetTrade(null);
        if (trade.second().GetTrade() == trade) trade.second().SetTrade(null);
    }
    public void CancelTrade(L1PcInstance player) {
        if (player == null) return;
        CTrade trade = player.GetTrade();
        if (trade != null) trade.Canceled(player);
    }

    /** The existing CIO wheel stores only a trade ID, never a disconnected PC. */
    private final class Timeout implements CIOThreadCallback.WheelTask {
        private final int tradeId, shard;
        Timeout(int tradeId, int shard) { this.tradeId = tradeId; this.shard = shard; }
        @Override public boolean isCancelled() { return FindTrade(tradeId) == null; }
        @Override public void run() {
            CTrade trade = FindTrade(tradeId);
            if (trade == null) return;
            long delay = trade.CheckTradeTimeout();
            if (delay > 0 && !CIOThreadCallback.getInstance().scheduleNetworkTimeout(shard, this, delay))
                trade.Canceled(trade.first());
        }
    }
}
