package l1j.server.server.model.character.pss;

import java.util.HexFormat;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;

/** GM-only bridge for the 19.02.15.1002 native PSS trial, not server-driven AH. */
public final class PlaySupport2019 {
    private static final Logger LOG = Logger.getLogger(PlaySupport2019.class.getName());
    private static final int MAX_BODY = 8192;
    static final int TRIAL_SECONDS = 600;
    // Observed on the supplied 2019 client: 2101 can have a zero-byte body.
    // This is server trial bookkeeping; the client keeps its own PSS settings.
    private static final int LEGACY_EMPTY_MODE = 2; // LOCAL
    private static final int VALID = 0, INVALID_MAP = 1, UNKNOWN_ERROR = 99;
    private static final int TIME_EXPIRE = 3, USER_DEAD = 4;
    private static final String[] SETTING_FIELDS = {
        "mode", "off_condition_death", "off_condition_peace", "off_condition_comeback",
        "tel_condition_hp", "tel_condition_peace", "tel_condition_surrounded",
        "tel_condition_pvp", "exp_recovery"
    };

    private PlaySupport2019() {}

    /** Explicit GM experiment: send the C->S settings ID in the opposite direction. */
    public static void gmProbe2111(L1PcInstance gm, String param) {
        if (gm == null || !gm.isGm()) return;
        CIOClient client = gm.getNetConnection();
        if (player(client) != gm) return;
        int mode = 0;
        String argument = param == null ? "" : param.trim();
        if (!argument.isEmpty()) {
            try {
                mode = Integer.parseInt(argument);
                if (mode < 1 || mode > 3) throw new NumberFormatException();
            } catch (NumberFormatException invalid) {
                SC_SystemMessage.send(gm, "사용법: .pss2111 또는 .pss2111 <1~3>");
                return;
            }
        }
        ProtoWriter out = Proto.begin(client, ProtoMsg.CS_CHANGE_PLAY_SUPPORT_SETTING_REQ, 32);
        if (out == null) {
            SC_SystemMessage.send(gm, "2111 테스트 패킷 생성 실패");
            return;
        }
        final boolean admitted;
        try {
            if (mode != 0) out.msg(1).enumV(1, mode).end();
            admitted = Proto.commit(out);
        } finally {
            Proto.abort(out);
        }
        LOG.info("[PSS-2111-TEST] S->C id=2111 body=" + (mode == 0 ? "empty" : "setting_info.mode=" + mode)
                + " admitted=" + admitted + " player=" + gm.getId()
                + " (experimental direction; not a verified UI-open packet)");
        SC_SystemMessage.send(gm, "2111 테스트 전송큐 접수=" + admitted
                + " / " + (mode == 0 ? "빈 본문" : "mode=" + mode));
    }

    /** Receive the one-way client setting request; observe it without starting either AI. */
    public static void settings(CIOClient client, byte[] data, int offset, int length) {
        L1PcInstance pc = player(client);
        if (pc == null || !pc.isGm()) return;
        final String fields;
        try {
            fields = parseSettings(reader(data, offset, length));
        } catch (RuntimeException invalid) {
            malformed(pc, 2111, data, offset, length, invalid);
            return;
        }
        LOG.info("[PSS-2019] recv 2111 player=" + pc.getId() + " bytes=" + length
                + " settings={" + fields + "} body64="
                + HexFormat.of().formatHex(data, offset, offset + Math.min(length, 64)));
        SC_SystemMessage.send(pc, "PSS 2111 설정 수신 확인. 서버 로그를 확인해 주세요.");
    }

    static String parseSettings(ProtoReader in) {
        StringBuilder fields = new StringBuilder(192);
        boolean found = false;
        for (int tag; (tag = next(in)) != 0;) {
            if (tag != 10) {
                in.skip(tag & 7);
                continue;
            }
            found = true;
            ProtoReader settings = in.subReader();
            for (int field; (field = next(settings)) != 0;) {
                int number = field >>> 3;
                if ((field & 7) == 0 && number >= 1 && number <= SETTING_FIELDS.length) {
                    int value = settings.varint32();
                    // These are observed values, not validated controls for the server AI.
                    if (fields.length() != 0) fields.append(' ');
                    fields.append(SETTING_FIELDS[number - 1]).append('=').append(value);
                } else settings.skip(field & 7);
            }
        }
        if (!found) throw new IllegalArgumentException("missing PSS setting_info");
        return fields.length() == 0 ? "no known fields" : fields.toString();
    }

    public static void start(CIOClient client, byte[] data, int offset, int length) {
        start(client, data, offset, length, System.nanoTime());
    }

    static void start(CIOClient client, byte[] data, int offset, int length, long now) {
        L1PcInstance pc = player(client);
        if (pc == null) return;
        if (!pc.isGm()) {
            startAck(client, UNKNOWN_ERROR);
            SC_SystemMessage.send(pc, "PSS 연결은 현재 GM 테스트 전용입니다.");
            return;
        }
        final int mode;
        try {
            ProtoReader in = reader(data, offset, length);
            ClientAutomationSession existing = client.getAutomationSession();
            mode = length == 0
                    ? existing != null && existing.clientDriven() ? existing.mode() : LEGACY_EMPTY_MODE
                    : parseStart(in);
        } catch (RuntimeException invalid) {
            malformed(pc, 2101, data, offset, length, invalid);
            startAck(client, UNKNOWN_ERROR);
            return;
        }
        LOG.info("[PSS-2019] recv 2101 player=" + pc.getId() + " mode=" + mode + " bytes=" + length
                + " modeSource=" + (length == 0 ? "legacy-empty-trial" : "request"));
        if (pc.isDead() || pc.get_teleport() || pc.isPrivateShop()) {
            startAck(client, UNKNOWN_ERROR);
            return;
        }
        if (pc.getMap() == null || !pc.getMap().isInMap(pc.getX(), pc.getY())) {
            startAck(client, INVALID_MAP);
            return;
        }
        ClientAutomationSession current = client.getAutomationSession();
        if (current != null && current.clientDriven()) {
            if (!checkSession(client, pc, current, now)) {
                startAck(client, UNKNOWN_ERROR);
                return;
            }
            // Retries acknowledge the same session without extending its clock.
            startAck(client, current.mode() == mode ? VALID : UNKNOWN_ERROR);
            return;
        }
        if (pc.getAutoHunt() || current == ClientAutomationSession.SERVER) {
            startAck(client, UNKNOWN_ERROR);
            SC_SystemMessage.send(pc, "기존 서버 자동사냥을 먼저 중지한 뒤 PSS를 시작해 주세요.");
            return;
        }
        ClientAutomationSession session = new ClientAutomationSession(mode, pc.getSessionStamp(),
                pc.getMapId(), now + TRIAL_SECONDS * 1_000_000_000L);
        if (!client.tryClaimAutomation(session)) {
            startAck(client, UNKNOWN_ERROR);
            return;
        }
        boolean accepted = false;
        try {
            if (player(client) != pc || pc.getAutoHunt()) return;
            accepted = startAck(client, VALID);
        } finally {
            if (!accepted) client.releaseAutomation(session);
        }
        LOG.info("[PSS-2019] send 2102 result=0 admitted=" + accepted + " player=" + pc.getId()
                + " map=" + pc.getMapId() + " mode=" + mode + " trialSeconds=" + TRIAL_SECONDS
                + " (client movement/attack still requires live verification)");
    }

    public static void finish(CIOClient client, byte[] data, int offset, int length) {
        finish(client, data, offset, length, System.nanoTime());
    }

    static void finish(CIOClient client, byte[] data, int offset, int length, long now) {
        L1PcInstance pc = player(client);
        if (pc == null) return;
        int reason = 0;
        try {
            ProtoReader in = reader(data, offset, length);
            for (int tag; (tag = next(in)) != 0;) {
                if (tag == 8) reason = in.varint32();
                else in.skip(tag & 7);
            }
        } catch (RuntimeException invalid) {
            malformed(pc, 2103, data, offset, length, invalid);
            return;
        }
        ClientAutomationSession session = client.getAutomationSession();
        int remaining = 0;
        if (session != null && session.clientDriven()) {
            if (session.playerStamp() == pc.getSessionStamp()) remaining = session.remainingSeconds(now);
            client.releaseAutomation(session);
        }
        oneField(client, ProtoMsg.SC_FINISH_PLAY_SUPPORT_ACK, remaining);
        if (pc.isGm()) LOG.info("[PSS-2019] recv 2103/send 2104 player=" + pc.getId()
                + " reason=" + reason + " remain=" + remaining);
    }

    public static void time(CIOClient client, byte[] data, int offset, int length) {
        time(client, data, offset, length, System.nanoTime());
    }

    static void time(CIOClient client, byte[] data, int offset, int length, long now) {
        L1PcInstance pc = player(client);
        if (pc == null) return;
        try {
            ProtoReader in = reader(data, offset, length);
            for (int tag; (tag = next(in)) != 0;) in.skip(tag & 7);
        } catch (RuntimeException invalid) {
            malformed(pc, 2106, data, offset, length, invalid);
            return;
        }
        ClientAutomationSession session = client.getAutomationSession();
        int remaining = session != null && session.clientDriven()
                && checkSession(client, pc, session, now) ? session.remainingSeconds(now) : 0;
        oneField(client, ProtoMsg.SC_PSS_TIME_CHECK_ACK, remaining);
    }

    private static boolean checkSession(CIOClient client, L1PcInstance pc,
            ClientAutomationSession session, long now) {
        int reason = !pc.isGm() || session.playerStamp() != pc.getSessionStamp()
                || session.mapId() != pc.getMapId() ? INVALID_MAP
                : pc.isDead() ? USER_DEAD
                : session.remainingSeconds(now) == 0 ? TIME_EXPIRE : 0;
        if (reason == 0) return true;
        if (client.releaseAutomation(session)) {
            forceFinish(client, reason);
            LOG.info("[PSS-2019] send 2105 player=" + pc.getId() + " reason=" + reason);
        }
        return false;
    }

    private static L1PcInstance player(CIOClient client) {
        if (client == null || !client.isConnected() || !client.isGameSessionReady()
                || client.getConnectionState() != ConnectionState.IN_GAME) return null;
        L1PcInstance pc = client.getActiveChar();
        return pc == null || pc.getNetConnection() != client || pc.isLogoutStarted() ? null : pc;
    }

    private static ProtoReader reader(byte[] data, int offset, int length) {
        if (length > MAX_BODY) throw new IllegalArgumentException("PSS body exceeds " + MAX_BODY);
        return new ProtoReader(data, offset, length);
    }

    static int parseStart(ProtoReader in) {
        int requestMode = 0, settingMode = 0;
        for (int tag; (tag = next(in)) != 0;) {
            if (tag == 8) requestMode = mode(in.varint64());
            else if (tag == 18) {
                ProtoReader settings = in.subReader();
                for (int field; (field = next(settings)) != 0;) {
                    if (field == 8) settingMode = mode(settings.varint64());
                    else settings.skip(field & 7);
                }
            } else in.skip(tag & 7);
        }
        // Remaster references use setting_info.mode; support the older root-only form too.
        int selected = settingMode != 0 ? settingMode : requestMode;
        if (selected == 0) throw new IllegalArgumentException("missing PSS mode");
        return selected;
    }

    private static int mode(long value) {
        if (value < 1L || value > 3L) throw new IllegalArgumentException("unsupported PSS mode " + value);
        return (int) value;
    }

    private static int next(ProtoReader in) {
        if (in.isAtEnd()) return 0;
        int offset = in.origin() + in.frameLength() - in.remaining();
        // Accept the legacy zero trailer only at a completed field boundary.
        // Trimming raw trailing zeros would corrupt valid scalar-zero values.
        if (in.array()[offset] == 0) {
            if (in.remaining() > 2) throw new IllegalArgumentException("excess PSS padding");
            for (int i = 0; i < in.remaining(); i++)
                if (in.array()[offset + i] != 0) throw new IllegalArgumentException("invalid PSS padding");
            return 0;
        }
        return in.next();
    }

    private static void malformed(L1PcInstance pc, int id, byte[] data, int offset,
            int length, RuntimeException invalid) {
        if (!pc.isGm()) return;
        String hex = data != null && offset >= 0 && length >= 0 && offset <= data.length
                && length <= data.length - offset
                ? HexFormat.of().formatHex(data, offset, offset + Math.min(length, 64)) : "invalid-slice";
        LOG.warning("[PSS-2019] rejected " + id + " player=" + pc.getId() + " bytes=" + length
                + " detail=" + invalid.getMessage() + " body64=" + hex);
    }

    private static boolean startAck(CIOClient client, int result) {
        return oneField(client, ProtoMsg.SC_START_PLAY_SUPPORT_ACK, result);
    }

    private static boolean oneField(CIOClient client, int message, int value) {
        ProtoWriter out = Proto.begin(client, message, 32);
        if (out == null) return false;
        try { out.uint32(1, value); return Proto.commit(out); }
        finally { Proto.abort(out); }
    }

    private static void forceFinish(CIOClient client, int reason) {
        ProtoWriter out = Proto.begin(client, ProtoMsg.SC_FORCE_FINISH_PLAY_SUPPORT_NOTI, 32);
        if (out == null) return;
        try { out.uint32(1, reason).uint32(2, 0); Proto.commit(out); }
        finally { Proto.abort(out); }
    }
}
