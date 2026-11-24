package l1j.server.server.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import java.util.logging.Level;
import java.util.logging.Logger;

/** NC-style two-sided reply ownership. Phase 5 enables only pledge requests.
 * The existing CIO wheel drives expiration; the registry never locks a PC or
 * socket and never invokes game logic while holding its monitor.
 */
public final class CReplyChecker {
    private static final Logger LOG = Logger.getLogger(CReplyChecker.class.getName());
    static final int PLEDGE = 97;
    static final long TIMEOUT_MS = 30_000L;
    static final long TIMEOUT_NS = TIMEOUT_MS * 1_000_000L;
    private static final Registry WAITING = new Registry();

    private CReplyChecker() {}

    static final class Pending implements CIOThreadCallback.WheelTask {
        final Registry registry;
        final int requesterId, replierId, requesterStamp, replierStamp;
        final int sourceClanId, targetClanId, mapId;
        final int token;
        final long expiresAt;
        L1PcInstance requester, replier;
        L1Clan sourceClan, targetClan;
        private volatile boolean cancelled;

        Pending(Registry registry, int requesterId, int replierId,
                L1PcInstance requester, L1PcInstance replier,
                int requesterStamp, int replierStamp, L1Clan sourceClan,
                L1Clan targetClan, int sourceClanId, int targetClanId,
                int mapId, int token, long expiresAt) {
            this.registry = registry;
            this.requesterId = requesterId;
            this.replierId = replierId;
            this.requester = requester;
            this.replier = replier;
            this.requesterStamp = requesterStamp;
            this.replierStamp = replierStamp;
            this.sourceClan = sourceClan;
            this.targetClan = targetClan;
            this.sourceClanId = sourceClanId;
            this.targetClanId = targetClanId;
            this.mapId = mapId;
            this.token = token;
            this.expiresAt = expiresAt;
        }

        @Override public boolean isCancelled() { return cancelled; }

        @Override public void run() {
            // The task carries the original identity, not just a player ID.
            // A late wheel delivery cannot cancel a subsequent request.
            registry.remove(this);
        }

        void clearReferences() {
            requester = null;
            replier = null;
            sourceClan = null;
            targetClan = null;
        }
    }

    // One primitive map entry per participant, one Pending per invitation.
    // Token zero is reserved for legacy dialogs; issued tokens never wrap.
    static final class Registry {
        private final Int2ObjectOpenHashMap<Pending> byId = new Int2ObjectOpenHashMap<>(16);
        private int lastToken;

        private void prune(int id, long now) {
            Pending p = byId.get(id);
            if (p != null && now - p.expiresAt >= 0) remove(p);
        }

        synchronized Pending attach(int requesterId, int replierId,
                L1PcInstance requester, L1PcInstance replier,
                int requesterStamp, int replierStamp, L1Clan sourceClan,
                L1Clan targetClan, int sourceClanId, int targetClanId,
                int mapId, long now) {
            if (requesterId <= 0 || replierId <= 0 || requesterId == replierId
                    || lastToken == Integer.MAX_VALUE) return null;
            if ((requester != null && requester.isLogoutStarted())
                    || (replier != null && replier.isLogoutStarted())) return null;
            prune(requesterId, now);
            prune(replierId, now);
            if (byId.containsKey(requesterId) || byId.containsKey(replierId)) return null;
            Pending p = new Pending(this, requesterId, replierId, requester, replier,
                    requesterStamp, replierStamp, sourceClan, targetClan, sourceClanId,
                    targetClanId, mapId, ++lastToken, now + TIMEOUT_NS);
            byId.put(requesterId, p);
            byId.put(replierId, p);
            return p;
        }

        synchronized Pending finish(int replierId, L1PcInstance session,
                int stamp, int token, long now) {
            Pending p = byId.get(replierId);
            if (p == null || p.replierId != replierId || token != p.token
                    || p.replier != session || p.replierStamp != stamp
                    || byId.get(p.requesterId) != p) return null;
            if (now - p.expiresAt >= 0) {
                remove(p);
                return null;
            }
            detach(p);
            // The caller exclusively owns the detached record and clears
            // session references before it posts any owner completion.
            return p;
        }

        private void detach(Pending p) {
            if (byId.get(p.requesterId) == p) byId.remove(p.requesterId);
            if (byId.get(p.replierId) == p) byId.remove(p.replierId);
            p.cancelled = true;
        }

        synchronized void remove(Pending p) {
            if (p == null || p.cancelled) return;
            detach(p);
            p.clearReferences();
        }

        synchronized void cancelSession(int id, L1PcInstance session, int stamp) {
            Pending p = byId.get(id);
            if (p == null) return;
            if ((p.requesterId == id && p.requester == session && p.requesterStamp == stamp)
                    || (p.replierId == id && p.replier == session && p.replierStamp == stamp)) {
                remove(p);
            }
        }

        synchronized int size() { return byId.size(); }
    }

    private static boolean live(L1PcInstance pc, int stamp) {
        if (pc == null || pc.isLogoutStarted() || pc.getSessionStamp() != stamp
                || pc.isDead() || pc.isGhost()) return false;
        CUserSocket socket = pc.getNetConnection();
        return socket != null && socket.isConnected() && socket.getActiveChar() == pc
                && L1World.getInstance().findObject(pc.getId()) == pc;
    }

    private static boolean valid(Pending p, L1PcInstance requester, L1PcInstance replier,
            L1Clan source, L1Clan target) {
        if (!live(requester, p.requesterStamp) || !live(replier, p.replierStamp)) return false;
        if (requester.getClanid() != p.sourceClanId || replier.getClanid() != p.targetClanId
                || requester.getMapId() != p.mapId || replier.getMapId() != p.mapId
                || target == null || L1World.getInstance().getClan(p.targetClanId) != target
                || (!replier.isCrown() && replier.getClanRank() != L1Clan.수호)) return false;
        if (p.sourceClanId == 0) return source == null;
        return source != null && source != target
                && L1World.getInstance().getClan(p.sourceClanId) == source
                && requester.isCrown() && requester.getId() == source.getLeaderId()
                && source.getCastleId() == 0 && source.getHouseId() == 0;
    }

    public static boolean AttachReplyChecker(L1PcInstance requester, L1PcInstance replier,
            L1Clan target) {
        if (requester == null || replier == null || target == null) return false;
        final int requesterStamp = requester.getSessionStamp();
        final int replierStamp = replier.getSessionStamp();
        if (!live(requester, requesterStamp) || !live(replier, replierStamp)) return false;
        final int sourceClanId = requester.getClanid();
        final L1Clan source = sourceClanId == 0 ? null : L1World.getInstance().getClan(sourceClanId);
        final Pending p = WAITING.attach(requester.getId(), replier.getId(), requester, replier,
                requesterStamp, replierStamp, source, target, sourceClanId, target.getClanId(),
                requester.getMapId(), System.nanoTime());
        if (p == null) return false;
        boolean sent = false;
        try {
            if (!valid(p, requester, replier, source, target) || p.isCancelled()) return false;
            if (!CIOThreadCallback.getInstance().scheduleCharacterEffectTick(replier, p, TIMEOUT_MS))
                return false;
            if (p.isCancelled() || !live(requester, requesterStamp) || !live(replier, replierStamp))
                return false;
            // C_ANSWER decodes and returns msgIdx; never accept a zero or
            // mismatched token as the current request.
            sent = SC_MessageYN.send(replier, p.token, PLEDGE, requester.getName());
            return sent;
        } finally {
            if (!sent) WAITING.remove(p);
        }
    }

    public static void FinishWaitingReplyByReplier(L1PcInstance replier, int token, int answer) {
        if (replier == null || (answer != 0 && answer != 1) || token <= 0) return;
        final Pending p = WAITING.finish(replier.getId(), replier, replier.getSessionStamp(),
                token, System.nanoTime());
        if (p == null) return;
        final L1PcInstance requester = p.requester;
        final L1Clan source = p.sourceClan, target = p.targetClan;
        p.clearReferences();
        if (!valid(p, requester, replier, source, target)) return;
        if (answer == 0) {
            SC_ServerMessage.send(requester, 96, replier.getName());
            return;
        }
        final CUserSocket requesterSocket = requester.getNetConnection();
        // The existing requester CIO owner performs admission. No two-PC
        // locks, new workers or independent timer queues are introduced.
        CIOThreadCallback.getInstance().completeSession(requesterSocket, () -> {
            if (!valid(p, requester, replier, source, target)) return;
            try {
                L1ClanJoin.getInstance().ClanJoin(target, requester);
            } catch (Exception failure) {
                LOG.log(Level.WARNING, "Pledge reply admission failed: " + p.requesterId, failure);
                SC_SystemMessage.send(requester, "혈맹 가입을 시작하지 못했습니다.");
            }
        });
    }

    /** Either participant's exact session may cancel; old logout is a no-op. */
    public static void FinishWaitingReplyByRequester(L1PcInstance pc) {
        if (pc != null) WAITING.cancelSession(pc.getId(), pc, pc.getSessionStamp());
    }
}
