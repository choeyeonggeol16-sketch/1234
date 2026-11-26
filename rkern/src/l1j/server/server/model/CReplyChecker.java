package l1j.server.server.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Two-sided reply ownership for pledge, party, chat party and duel requests.
 * The existing CIO wheel drives expiration; the registry never locks a PC or
 * socket and never invokes game logic while holding its monitor.
 */
public final class CReplyChecker {
    private static final Logger LOG = Logger.getLogger(CReplyChecker.class.getName());
    static final int PLEDGE = 97;
    public static final int DUEL = 630, CHAT_PARTY = 951, PARTY = 953, RANDOM_PARTY = 954;
    static final long TIMEOUT_MS = 30_000L;
    static final long TIMEOUT_NS = TIMEOUT_MS * 1_000_000L;
    private static final Registry WAITING = new Registry();

    private CReplyChecker() {}

    static final class Pending implements CIOThreadCallback.WheelTask {
        final Registry registry;
        final int requesterId, replierId, requesterStamp, replierStamp;
        final int sourceClanId, targetClanId, mapId;
        final int token, kind, replierMapId;
        CParty party;
        L1ChatParty chatParty;
        private boolean claimed;
        final long expiresAt;
        L1PcInstance requester, replier;
        L1Clan sourceClan, targetClan;
        private volatile boolean cancelled;
        private boolean ready;

        Pending(Registry registry, int requesterId, int replierId,
                L1PcInstance requester, L1PcInstance replier,
                int requesterStamp, int replierStamp, L1Clan sourceClan,
                L1Clan targetClan, int sourceClanId, int targetClanId,
                int mapId, int token, long expiresAt, int kind) {
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
            this.kind = kind;
            this.replierMapId = replier == null ? mapId : replier.getMapId();
            this.party = requester != null && (kind == PARTY || kind == RANDOM_PARTY) ? requester.getParty() : null;
            this.chatParty = requester != null && kind == CHAT_PARTY ? requester.getChatParty() : null;
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
            party = null;
            chatParty = null;
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
            return attach(requesterId, replierId, requester, replier, requesterStamp, replierStamp,
                    sourceClan, targetClan, sourceClanId, targetClanId, mapId, now, PLEDGE);
        }

        synchronized Pending attach(int requesterId, int replierId,
                L1PcInstance requester, L1PcInstance replier,
                int requesterStamp, int replierStamp, L1Clan sourceClan,
                L1Clan targetClan, int sourceClanId, int targetClanId,
                int mapId, long now, int kind) {
            if (kind != PLEDGE && kind != DUEL && kind != CHAT_PARTY && kind != PARTY && kind != RANDOM_PARTY)
                return null;
            if (requesterId <= 0 || replierId <= 0 || requesterId == replierId
                    || lastToken == Integer.MAX_VALUE) return null;
            if ((requester != null && requester.isLogoutStarted())
                    || (replier != null && replier.isLogoutStarted())) return null;
            prune(requesterId, now);
            prune(replierId, now);
            if (byId.containsKey(requesterId) || byId.containsKey(replierId)) return null;
            Pending p = new Pending(this, requesterId, replierId, requester, replier,
                    requesterStamp, replierStamp, sourceClan, targetClan, sourceClanId,
                    targetClanId, mapId, ++lastToken, now + TIMEOUT_NS, kind);
            byId.put(requesterId, p);
            byId.put(replierId, p);
            return p;
        }

        synchronized boolean publish(Pending p, long now) {
            if (p.cancelled || byId.get(p.requesterId) != p || byId.get(p.replierId) != p)
                return false;
            if (now - p.expiresAt >= 0) {
                remove(p);
                return false;
            }
            if (p.ready) return false;
            p.ready = true;
            return true;
        }

        synchronized boolean claimAuto(Pending p, long now) {
            if (p.kind != PLEDGE || p.cancelled || p.ready || byId.get(p.requesterId) != p
                    || byId.get(p.replierId) != p) return false;
            if (now - p.expiresAt >= 0) {
                remove(p);
                return false;
            }
            detach(p);
            p.clearReferences();
            return true;
        }

        synchronized Pending finish(int replierId, L1PcInstance session,
                int stamp, int token, long now) {
            Pending p = byId.get(replierId);
            if (p == null || p.kind != PLEDGE || !p.ready || p.replierId != replierId || token != p.token
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

        // Keep both participants reserved while a social reply is being
        // dispatched. A duplicate reply or a different dialog cannot claim it.
        synchronized Pending claimSocial(int replierId, L1PcInstance session,
                int stamp, int kind, int token, long now) {
            Pending p = byId.get(replierId);
            if (p == null || p.kind == PLEDGE || p.kind != kind || !p.ready || p.claimed
                    || p.replierId != replierId || p.token != token || p.replier != session
                    || p.replierStamp != stamp || byId.get(p.requesterId) != p) return null;
            if (now - p.expiresAt >= 0) { remove(p); return null; }
            p.claimed = true;
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
                || target == null || target.IsDismissed() || L1World.getInstance().getClan(p.targetClanId) != target
                || (!replier.isCrown() && replier.getClanRank() != L1Clan.수호)) return false;
        if (p.sourceClanId == 0) return source == null;
        return source != null && !source.IsDismissed() && source != target
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
        final String account = requester.getAccountName();
        final CUserSocket requesterSocket = requester.getNetConnection();
        boolean admitted = false;
        try {
            if (account == null || account.isEmpty() || !valid(p, requester, replier, source, target)
                    || p.isCancelled()) return false;
            if (!CIOThreadCallback.getInstance().scheduleCharacterEffectTick(replier, p, TIMEOUT_MS))
                return false;
            if (p.isCancelled()) return false;
            CacheD.getInstance().submitDbJobOrdered(p.requesterId, CacheD.accountOrderKey(account), () -> {
                if (p.isCancelled()) return;
                final boolean allowed;
                try {
                    allowed = L1ClanJoin.CheckAccountPledge(account, p.requesterId,
                            p.sourceClanId, p.targetClanId);
                } catch (SQLException | RuntimeException failure) {
                    LOG.log(Level.WARNING, "Pledge account check failed: " + p.requesterId, failure);
                    completeAccountCheck(p, requesterSocket, requester, replier, source, target,
                            account, false, true);
                    return;
                }
                completeAccountCheck(p, requesterSocket, requester, replier, source, target,
                        account, allowed, false);
            });
            admitted = true;
            return true;
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Pledge account check not admitted: " + p.requesterId, failure);
            return false;
        } finally {
            if (!admitted) WAITING.remove(p);
        }
    }

    private static void completeAccountCheck(Pending p, CUserSocket requesterSocket,
            L1PcInstance requester, L1PcInstance replier, L1Clan source, L1Clan target,
            String account, boolean allowed, boolean dbFailure) {
        if (p.isCancelled()) return;
        try {
            boolean accepted = CIOThreadCallback.getInstance().completeSession(requesterSocket, () -> {
                boolean keep = false;
                try {
                    if (p.isCancelled() || System.nanoTime() - p.expiresAt >= 0
                            || !valid(p, requester, replier, source, target)
                            || !account.equals(requester.getAccountName())) return;
                    if (!allowed) {
                        SC_SystemMessage.send(requester, dbFailure
                                ? "혈맹 가입 정보를 확인하지 못했습니다. 다시 시도해 주세요."
                                : "\\fY한 계정 내 캐릭들은 같은 혈맹에만 가입이 가능합니다.");
                        return;
                    }
                    if (replier.isAutoClanjoin()) {
                        if (WAITING.claimAuto(p, System.nanoTime()))
                            L1ClanJoin.getInstance().ClanJoinChecked(replier, requester, account);
                        return;
                    }
                    if (!WAITING.publish(p, System.nanoTime())) return;
                    // The original unique token remains tied to this exact
                    // checked request, never to a shared temporary PC field.
                    keep = SC_MessageYN.send(replier, p.token, PLEDGE, requester.getName());
                } finally {
                    if (!keep) WAITING.remove(p);
                }
            }, failure -> {
                if (failure != null) WAITING.remove(p);
            });
            if (!accepted) WAITING.remove(p);
        } catch (RuntimeException failure) {
            WAITING.remove(p);
            LOG.log(Level.WARNING, "Pledge account check owner completion failed: " + p.requesterId, failure);
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
        final String account = requester.getAccountName();
        if (account == null || account.isEmpty()) return;
        // The existing requester CIO owner performs admission. No two-PC
        // locks, new workers or independent timer queues are introduced.
        CIOThreadCallback.getInstance().completeSession(requesterSocket, () -> {
            if (System.nanoTime() - p.expiresAt >= 0
                    || !valid(p, requester, replier, source, target)
                    || !account.equals(requester.getAccountName())) return;
            try {
                L1ClanJoin.getInstance().ClanJoinChecked(target, requester, account);
            } catch (Exception failure) {
                LOG.log(Level.WARNING, "Pledge reply admission failed: " + p.requesterId, failure);
                SC_SystemMessage.send(requester, "혈맹 가입을 시작하지 못했습니다.");
            }
        });
    }

    private static boolean validSocial(Pending p, L1PcInstance requester, L1PcInstance replier) {
        if (p.isCancelled() || !live(requester, p.requesterStamp) || !live(replier, p.replierStamp)
                || requester.getMapId() != p.mapId || replier.getMapId() != p.replierMapId)
            return false;
        if (p.kind == DUEL) {
            return requester.getFightId() == 0 && replier.getFightId() == 0
                    && !replier.isParalyzed() && requester.getMapId() == replier.getMapId()
                    && requester.getLocation().getTileLineDistance(replier.getLocation()) <= 1;
        }
        if (!requester.is_world() || !replier.is_world() || requester.getAutoHunt()) return false;
        if (p.kind == CHAT_PARTY) {
            final L1ChatParty current = requester.getChatParty();
            return replier.getChatParty() == null && current == p.chatParty
                    && (current == null || current.isLeader(requester));
        }
        final CParty current = requester.getParty();
        return !replier.getAutoHunt() && replier.getParty() == null
                && current == p.party && (current == null || current.isLeader(requester))
                && partyMapAllowed(requester.getMapId()) && partyMapAllowed(replier.getMapId());
    }

    private static boolean partyMapAllowed(int map) {
        return map != 5554 && map != 5153 && map != 5001;
    }

    /** Only the request record holds pending IDs and the division mode. */
    public static boolean AttachReplyChecker(L1PcInstance requester, L1PcInstance replier, int kind) {
        if (requester == null || replier == null || kind == PLEDGE) return false;
        final int rs = requester.getSessionStamp(), ts = replier.getSessionStamp();
        if (!live(requester, rs) || !live(replier, ts)) return false;
        final Pending p = WAITING.attach(requester.getId(), replier.getId(), requester, replier,
                rs, ts, null, null, 0, 0, requester.getMapId(), System.nanoTime(), kind);
        if (p == null) return false;
        boolean sent = false;
        try {
            if (!validSocial(p, requester, replier)
                    || !CIOThreadCallback.getInstance().scheduleCharacterEffectTick(replier, p, TIMEOUT_MS)
                    || !WAITING.publish(p, System.nanoTime())) return false;
            sent = SC_MessageYN.send(replier, p.token, kind, requester.getName());
            return sent;
        } finally {
            if (!sent) WAITING.remove(p);
        }
    }

    public static void FinishWaitingReplyByReplier(L1PcInstance replier, int kind, int token, int answer) {
        if (replier == null || token <= 0 || (answer != 0 && answer != 1)) return;
        final Pending p = WAITING.claimSocial(replier.getId(), replier, replier.getSessionStamp(),
                kind, token, System.nanoTime());
        if (p == null) return;
        final L1PcInstance requester = p.requester;
        if (requester == null) { WAITING.remove(p); return; }
        try {
            CIOThreadCallback.getInstance().completeSession(requester.getNetConnection(), () -> {
                try {
                    if (System.nanoTime() - p.expiresAt >= 0 || !validSocial(p, requester, replier))
                        return;
                    if (answer == 0) {
                        SC_ServerMessage.send(requester, kind == DUEL ? 631 : 423, replier.getName());
                        return;
                    }
                    // The existing shared party/duel operations retain their
                    // packet and membership behavior; only approved replies enter.
                    if (kind == DUEL) {
                        requester.setFightId(replier.getId());
                        replier.setFightId(requester.getId());
                        if (!live(requester, p.requesterStamp) || !live(replier, p.replierStamp)) {
                            if (requester.getFightId() == replier.getId()) requester.setFightId(0);
                            if (replier.getFightId() == requester.getId()) replier.setFightId(0);
                            return;
                        }
                        SC_PacketBox.send(requester, SC_PacketBox.MSG_DUEL, requester.getFightId(), requester.getId());
                        SC_PacketBox.send(replier, SC_PacketBox.MSG_DUEL, replier.getFightId(), replier.getId());
                    } else if (kind == CHAT_PARTY) {
                        L1ChatParty current = requester.getChatParty();
                        if (current == null) {
                            current = new L1ChatParty();
                            current.addMember(requester);
                        }
                        if (current.isVacancy() || requester.isGm()) current.addMember(requester, replier);
                        else SC_ServerMessage.send(requester, 417);
                        if (!live(requester, p.requesterStamp) && requester.getChatParty() == current)
                            current.leaveMember(requester);
                        else if (!live(replier, p.replierStamp) && replier.getChatParty() == current)
                            current.leaveMember(replier);
                    } else {
                        CParty current = requester.getParty();
                        if (current == null) {
                            requester.setPartyType(kind == RANDOM_PARTY ? 1 : 0);
                            current = new CParty();
                            current.addMember(requester);
                        }
                        if (current.isVacancy() || requester.isGm()) current.addMember(replier);
                        else SC_ServerMessage.send(requester, 417);
                        if (!live(requester, p.requesterStamp) && requester.getParty() == current)
                            current.leaveMember(requester);
                        else if (!live(replier, p.replierStamp) && replier.getParty() == current)
                            current.leaveMember(replier);
                    }
                } finally { WAITING.remove(p); }
            }, failure -> { if (failure != null) WAITING.remove(p); });
        } catch (RuntimeException failure) {
            WAITING.remove(p);
            LOG.log(Level.WARNING, "Social reply dispatch failed: " + p.requesterId, failure);
        }
    }

    /** Either participant's exact session may cancel; old logout is a no-op. */
    public static void FinishWaitingReplyByRequester(L1PcInstance pc) {
        if (pc != null) WAITING.cancelSession(pc.getId(), pc, pc.getSessionStamp());
    }
}
