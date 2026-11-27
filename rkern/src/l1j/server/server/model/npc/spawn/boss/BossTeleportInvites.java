package l1j.server.server.model.npc.spawn.boss;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.shared.geometry.GamePoint;

/** Single-flight boss teleport invitation for the existing unkeyed client replies. */
public final class BossTeleportInvites {
    private static final Logger LOG = Logger.getLogger(BossTeleportInvites.class.getName());
    private static final long LIFETIME_NANOS = TimeUnit.SECONDS.toNanos(10);

    private BossTeleportInvites() {}

    private record Spawn(CWorld world, long smartId, CBossSpawnKey key, CNpcSpawnDefine definition, int x, int y,
            L1Map map, long expiresAt) {
        boolean expired(long now) {
            return now - expiresAt >= 0;
        }

        boolean isCurrent() {
            return CWorld.getInstance() == world && CBossNpcMakerDB.isCurrent(key, definition);
        }

        boolean isLive() {
            return !expired(System.nanoTime()) && isCurrent()
                    && world.findObjectBySmartId(smartId) instanceof L1MonsterInstance live
                    && !live.isDead() && !live.isDestroyed() && live.getCurrentHp() > 0
                    && live.getMap() == map;
        }
    }

    public static final class Invitation {
        private final Spawn spawn;
        private final CUserSocket session;
        private final int sessionStamp;
        private boolean delivered; // guarded by session

        private Invitation(Spawn spawn, CUserSocket session, int sessionStamp) {
            this.spawn = spawn;
            this.session = session;
            this.sessionStamp = sessionStamp;
        }

        public boolean isFor(CUserSocket expected, int stamp) {
            return session == expected && sessionStamp == stamp;
        }
    }

    static void announce(CNpcSpawnDefine definition, L1MonsterInstance npc, int displayEffect) {
        if (displayEffect <= 0 || Config.STANDBY_SERVER) return;
        final CWorld world = CWorld.getInstance();
        final long smartId = world.smartIdOf(npc);
        final L1Map map = npc.getMap();
        if (smartId == 0 || map == null || map.getId() != definition.getMap()) return;
        // Preserve the configured meeting point, including for bosses that
        // spawn at a random offset or move before the player answers.
        final Spawn spawn = new Spawn(world, smartId, CBossSpawnKey.of(definition), definition, definition.getX(), definition.getY(),
                map, System.nanoTime() + LIFETIME_NANOS);
        if (!spawn.isLive()) return;

        try (var display = new CAsyncSendConstBuffer(SC_DisplayEffect.nativeWriter(displayEffect),
                     Integer.MIN_VALUE, Integer.MAX_VALUE);
             var question = new CAsyncSendConstBuffer(
                     SC_MessageYN.nativeIndexedWriter(CS_Attr.MSGCODE_622_BOSS, 622, definition.getYnMessage()),
                     Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance pc : world.getAllPlayers()) {
                if (!spawn.isCurrent() || spawn.expired(System.nanoTime())) break;
                if (pc == null) continue;
                final CUserSocket session = pc.getNetConnection();
                final int stamp = pc.getSessionStamp();
                if (!eligible(pc, session, stamp)) continue;
                send(display, pc, session);
                offer(spawn, question, pc, session, stamp);
            }
        }
    }

    private static boolean eligible(L1PcInstance pc, CUserSocket session, int stamp) {
        final L1Map map = pc.getMap();
        return session != null && pc.getNetConnection() == session && pc.getSessionStamp() == stamp
                && session.getActiveChar() == pc && session.ownsReadyGameSession() && pc.is_world()
                && !pc.isPrivateShop() && pc.isBossNotify() && map != null && map.isEscapable();
    }

    private static void offer(Spawn spawn, CAsyncSendConstBuffer question,
            L1PcInstance pc, CUserSocket session, int stamp) {
        // Use the same session boundary as the ActionUI ingress. A response
        // may arrive before a foreign broadcast caller returns from SEND.
        synchronized (session) {
            final long now = System.nanoTime();
            if (!eligible(pc, session, stamp) || !spawn.isCurrent() || spawn.expired(now)) return;
            final Invitation current = pc.bossTeleportInvitation();
            if (current != null && current.isFor(session, stamp) && !current.spawn.expired(now)) return;
            final Invitation next = new Invitation(spawn, session, stamp);
            if (!pc.replaceBossTeleportInvitation(current, next)) return;
            try {
                next.delivered = send(question, pc, session);
            } finally {
                if (!next.delivered) pc.replaceBossTeleportInvitation(next, null);
            }
        }
    }

    private static boolean send(CAsyncSendConstBuffer buffer, L1PcInstance pc, CUserSocket session) {
        try {
            return buffer.sendBestEffort(session, pc.getId());
        } catch (RuntimeException failure) {
            LOG.log(Level.FINE, "Boss teleport notice could not be sent", failure);
            return false;
        }
    }

    public static void answer(L1PcInstance pc, CUserSocket session, boolean accepted) {
        respond(pc, session, accepted, true);
    }

    public static void teleportFromActionUi(L1PcInstance pc, CUserSocket session) {
        respond(pc, session, true, false);
    }

    private static void respond(L1PcInstance pc, CUserSocket session, boolean accepted, boolean popup) {
        final int stamp = pc.getSessionStamp();
        if (!eligible(pc, session, stamp)) return;
        if (pc.getLevel() < Config.YN_pclevel) {
            SC_SystemMessage.send(pc, "레벨 " + Config.YN_pclevel + " 부터 이용가능 합니다.");
            return;
        }
        final Invitation invitation = pc.bossTeleportInvitation();
        if (invitation == null || !consume(pc, session, stamp, invitation, accepted)) {
            SC_SystemMessage.send(pc, "선택 딜레이가 지나 이용이 불가능 합니다(10초)");
            return;
        }
        if (!accepted) {
            SC_SystemMessage.send(pc, "보스이동 참가를 거절 하셨습니다.");
            return;
        }
        teleport(pc, session, stamp, invitation.spawn, popup);
    }

    private static boolean consume(L1PcInstance pc, CUserSocket session, int stamp,
            Invitation invitation, boolean accepted) {
        synchronized (session) {
            if (!invitation.isFor(session, stamp) || !invitation.delivered || !eligible(pc, session, stamp)) {
                return false;
            }
            if (invitation.spawn.expired(System.nanoTime())) {
                pc.replaceBossTeleportInvitation(invitation, null);
                return false;
            }
            return (!accepted || invitation.spawn.isLive()) && pc.replaceBossTeleportInvitation(invitation, null);
        }
    }

    private static void teleport(L1PcInstance pc, CUserSocket session, int stamp, Spawn spawn, boolean popup) {
        int x = spawn.x;
        int y = spawn.y;
        for (int attempt = 0; attempt < 99; attempt++) {
            final int candidateX = spawn.x + (GameRandom.isBoolean() ? GameRandom.next(5) : -GameRandom.next(5));
            final int candidateY = spawn.y + (GameRandom.isBoolean() ? GameRandom.next(5) : -GameRandom.next(5));
            if (GamePoint.isValidPosition(spawn.map, candidateX, candidateY)) {
                x = candidateX;
                y = candidateY;
                break;
            }
        }
        synchronized (session) {
            if (!eligible(pc, session, stamp) || !spawn.isLive() || pc.get_teleport()) return;
            pc.start_teleport(x, y, spawn.map.getId(), pc.getHeading(), 169, true, !popup && pc.getAutoHunt());
            if (popup && pc.get_teleport()) {
                SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "해당 보스구역으로 이동 되었습니다.");
            }
        }
    }
}
