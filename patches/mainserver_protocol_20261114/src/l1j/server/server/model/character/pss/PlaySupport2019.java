package l1j.server.server.model.character.pss;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;

/** Server-side admission, allowance and expiry for the 19.02.15.1002 client PSS. */
public final class PlaySupport2019 {
    private static final Logger LOG = Logger.getLogger(PlaySupport2019.class.getName());
    private static final int MAX_BODY = 8192;
    // Observed on the supplied 2019 client: 2101 can have a zero-byte body.
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
        SC_SystemMessage.send(gm, "2111 테스트 전송큐 접수=" + admitted
                + " / " + (mode == 0 ? "빈 본문" : "mode=" + mode));
    }

    /** Receive the one-way client setting request; observe it without starting either AI. */
    public static void handleChangePlaySupportSettingRequest(CIOClient client, byte[] data, int offset, int length) {
        L1PcInstance pc = player(client);
        if (pc == null || !pc.isGm()) return;
        final String fields;
        try {
            fields = parseSettings(reader(data, offset, length));
        } catch (RuntimeException invalid) {
            malformed(pc, 2111, data, offset, length, invalid);
            return;
        }
        SC_SystemMessage.send(pc, "PSS 2111 설정 수신 확인: " + fields);
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

    public static void handleStartPlaySupportRequest(CIOClient client, byte[] data, int offset, int length) {
        handleStartPlaySupportRequest(client, data, offset, length, System.nanoTime());
    }

    static void handleStartPlaySupportRequest(CIOClient client, byte[] data, int offset, int length, long now) {
        L1PcInstance pc = player(client);
        if (pc == null) return;
        final int mode;
        try {
            ProtoReader in = reader(data, offset, length);
            ClientAutomationSession existing = client.getAutomationSession();
            mode = length == 0
                    ? existing != null && existing.clientDriven() ? existing.mode() : LEGACY_EMPTY_MODE
                    : parseStart(in);
        } catch (RuntimeException invalid) {
            malformed(pc, 2101, data, offset, length, invalid);
            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
            return;
        }
        if (pc.isDead() || pc.get_teleport() || pc.isPrivateShop()) {
            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
            return;
        }
        if (pc.getMap() == null || !pc.getMap().isInMap(pc.getX(), pc.getY())
                || !PssConfig.get().permits(pc)) {
            sendStartPlaySupportAck(client, INVALID_MAP);
            SC_SystemMessage.send(pc, "플레이서포터 맵 또는 레벨 사용 조건에 맞지 않습니다.");
            return;
        }
        ClientAutomationSession current = client.getAutomationSession();
        if (current != null && current.clientDriven()) {
            if (!checkSession(client, pc, current, now)) {
                sendStartPlaySupportAck(client, UNKNOWN_ERROR);
                return;
            }
            // Identical retries never renew a lease or a deadline.
            sendStartPlaySupportAck(client, current.mode() == mode ? VALID : UNKNOWN_ERROR);
            return;
        }
        if (pc.getAutoHunt() || current == ClientAutomationSession.SERVER) {
            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
            SC_SystemMessage.send(pc, "기존 서버 자동사냥을 먼저 중지해 주세요.");
            return;
        }
        // Nonblocking: DB lease admission happens on the existing ordered CacheD lane.
        if (!client.tryBeginPssClaim()) return;
        final int charId = pc.getId();
        try {
            CacheD.getInstance().submitDbJobOrdered(charId, () -> {
                final PssConfig.Settings admissionSettings = PssConfig.get();
                final PssDailyQuota.Grant lease;
                try {
                    lease = PssDailyQuota.claim(charId, System.currentTimeMillis(),
                            admissionSettings.dailySeconds(), admissionSettings.resetMinuteOfDay(),
                            admissionSettings.resetNotBeforeMs());
                } catch (SQLException | RuntimeException failure) {
                    LOG.log(Level.WARNING, "[PSS] quota admission DB failure player=" + charId, failure);
                    failClaim(client, pc, "플레이서포터 이용 시간 확인에 실패했습니다.");
                    return;
                }
                if (lease == null) {
                    failClaim(client, pc, "오늘 플레이서포터 사용 시간이 모두 소진되었습니다. 매일 "
                            + admissionSettings.resetTimeText() + "(한국 시간)에 초기화됩니다.");
                    return;
                }
                final AtomicBoolean accepted = new AtomicBoolean();
                final AtomicBoolean settled = new AtomicBoolean();
                CIOThreadCallback.getInstance().completeSession(client, () -> {
                    try {
                        if (player(client) != pc || PssConfig.get() != admissionSettings
                                || !admissionSettings.permits(pc)
                                || pc.isDead() || pc.get_teleport() || pc.isPrivateShop()
                                || pc.getAutoHunt() || client.getAutomationSession() != null) {
                            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
                            return;
                        }
                        long durationMs = lease.expiresMs() - System.currentTimeMillis();
                        if (durationMs <= 0L) {
                            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
                            return;
                        }
                        final ClientAutomationSession session = new ClientAutomationSession(
                                mode, pc.getSessionStamp(), pc.getMapId(),
                                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs),
                                charId, lease.dayStartMs(), lease.token(), lease.expiresMs(), lease.resetAtMs());
                        if (!client.tryClaimAutomation(session)) {
                            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
                            return;
                        }
                        if (!scheduleLeaseDeadline(client, session, durationMs)) {
                            client.releaseAutomation(session);
                            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
                            return;
                        }
                        if (!sendStartPlaySupportAck(client, VALID)) {
                            client.releaseAutomation(session);
                            return;
                        }
                        accepted.set(true);
                        client.invalidatePssCache();
                    } finally {
                        client.endPssClaim();
                        if (!accepted.get()) settleLease(charId, lease, settled);
                    }
                }, failure -> {
                    if (failure != null) {
                        client.endPssClaim();
                        if (!accepted.get()) settleLease(charId, lease, settled);
                    }
                });
            });
        } catch (RuntimeException rejected) {
            client.endPssClaim();
            sendStartPlaySupportAck(client, UNKNOWN_ERROR);
            LOG.log(Level.WARNING, "[PSS] CacheD admission rejected player=" + charId, rejected);
        }
    }

    private static void failClaim(CIOClient client, L1PcInstance expected, String message) {
        client.endPssClaim();
        CIOThreadCallback.getInstance().completeSessionReply(client, () -> {
            if (player(client) == expected) {
                sendStartPlaySupportAck(client, UNKNOWN_ERROR);
                SC_SystemMessage.send(expected, message);
            }
        });
    }

    private static void settleLease(int charId, PssDailyQuota.Grant grant, AtomicBoolean settled) {
        if (!settled.compareAndSet(false, true)) return;
        enqueueLeaseClose(charId, grant.dayStartMs(), grant.token(), null, false);
    }

    public static void handleFinishPlaySupportRequest(CIOClient client, byte[] data, int offset, int length) {
        handleFinishPlaySupportRequest(client, data, offset, length, System.nanoTime());
    }

    static void handleFinishPlaySupportRequest(CIOClient client, byte[] data, int offset, int length, long now) {
        L1PcInstance pc = player(client);
        if (pc == null) return;
        try {
            ProtoReader in = reader(data, offset, length);
            for (int tag; (tag = next(in)) != 0;) {
                if (tag == 8) in.varint32();
                else in.skip(tag & 7);
            }
        } catch (RuntimeException invalid) {
            malformed(pc, 2103, data, offset, length, invalid);
            return;
        }
        ClientAutomationSession session = client.getAutomationSession();
        if (session != null && session.clientDriven() && client.releaseAutomation(session)) {
            pc.getCreatureController().StopPlaySupportAutoActions();
            enqueueLeaseClose(session.quotaCharId(), session.quotaDayStartMs(),
                    session.quotaToken(), client, true);
        } else {
            queryTime(client, pc, ProtoMsg.SC_FINISH_PLAY_SUPPORT_ACK);
        }
    }

    public static void handlePssTimeCheckRequest(CIOClient client, byte[] data, int offset, int length) {
        handlePssTimeCheckRequest(client, data, offset, length, System.nanoTime());
    }

    static void handlePssTimeCheckRequest(CIOClient client, byte[] data, int offset, int length, long now) {
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
        if (session != null && session.clientDriven() && checkSession(client, pc, session, now)) {
            if (!client.isPssClaimPending() && client.getAutomationSession() == session)
                oneField(client, ProtoMsg.SC_PSS_TIME_CHECK_ACK, session.remainingSeconds(now));
        } else {
            queryTime(client, pc, ProtoMsg.SC_PSS_TIME_CHECK_ACK);
        }
    }

    private static void queryTime(CIOClient client, L1PcInstance pc, int response) {
        final int charId = pc.getId();
        final PssConfig.Settings settings = PssConfig.get();
        final long day = PssDailyQuota.dayStart(System.currentTimeMillis(), settings.resetMinuteOfDay());
        int cached = client.cachedPssSeconds(settings, day);
        
        if (cached > 0) {
            oneField(client, response, cached);
            return;
        }
        if (!client.tryBeginPssQuery()) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(charId, () -> {
                int balance = 0;
                boolean successful = false;
                try {
                    balance = PssDailyQuota.remaining(charId, System.currentTimeMillis(),
                            settings.dailySeconds(), settings.resetMinuteOfDay(), settings.resetNotBeforeMs());
                    successful = true;
                } catch (SQLException | RuntimeException e) {
                    LOG.log(Level.WARNING, "[PSS] quota time lookup failed player=" + charId, e);
                }
                final int remaining = balance;
                final boolean valid = successful;
                client.endPssQuery();
                CIOThreadCallback.getInstance().completeSessionReply(client, () -> {
                    if (player(client) == pc) {
                        if (valid && remaining <= 0) {
                            LOG.warning("[PSS-TIME] DB returned zero remaining: player=" + charId
                                    + " configuredDailySeconds=" + settings.dailySeconds()
                                    + " reset=" + settings.resetTimeText());
                        }
                        boolean current = valid && settings == PssConfig.get()
                                && day == PssDailyQuota.dayStart(System.currentTimeMillis(),
                                        settings.resetMinuteOfDay());
                        if (current) client.cachePssSeconds(settings, day, remaining);
                        oneField(client, response, current ? remaining : 0);
                    }
                });
            });
        } catch (RuntimeException rejected) {
            client.endPssQuery();
            oneField(client, response, 0);
        }
    }

    private static void enqueueLeaseClose(int charId, long day, long token,
            CIOClient responseClient, boolean reply) {
        if (charId <= 0 || token == 0L) return;
        try {
            CacheD.getInstance().submitDbJobRetryOrdered(charId, () -> {
                final int remaining;
                try { remaining = PssDailyQuota.finish(charId, day, token, System.currentTimeMillis()); }
                catch (SQLException ex) { throw new IllegalStateException("PSS close persistence failed", ex); }
                if (reply && responseClient != null)
                    CIOThreadCallback.getInstance().completeSessionReply(responseClient, () -> {
                        if (player(responseClient) != null && player(responseClient).getId() == charId) {
                            PssConfig.Settings currentSettings = PssConfig.get();
                            responseClient.cachePssSeconds(currentSettings,
                                    PssDailyQuota.dayStart(System.currentTimeMillis(),
                                            currentSettings.resetMinuteOfDay()), remaining);
                            oneField(responseClient, ProtoMsg.SC_FINISH_PLAY_SUPPORT_ACK, remaining);
                        }
                    });
            });
        } catch (RuntimeException rejected) {
            // Fail closed: the persisted lease remains charged until its deadline.
            LOG.log(Level.SEVERE, "[PSS] lease close enqueue failed, quota retained player=" + charId, rejected);
        }
    }

    /** Drop an active lease before the client changes PC, logs out or disconnects. */
    public static void onClientDetached(CIOClient client) {
        if (client == null) return;
        ClientAutomationSession session = client.getAutomationSession();
        if (session != null && session.clientDriven() && client.releaseAutomation(session)) {
            enqueueLeaseClose(session.quotaCharId(), session.quotaDayStartMs(),
                    session.quotaToken(), null, false);
        }
        client.invalidatePssCache();
    }

    /** Reload recalculates active leases without stopping the client's PSS automation. */
    public static void refreshForReload() {
        for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
            final CIOClient client = pc.getNetConnection();
            if (client == null) continue;
            final ClientAutomationSession session = client.getAutomationSession();
            if (session == null || !session.clientDriven()) continue;
            CIOThreadCallback.getInstance().completeSession(client, () -> {
                client.invalidatePssCache();
                final L1PcInstance live = player(client);
                if (live != null && client.getAutomationSession() == session) {
                    if (!PssConfig.get().permits(live)) checkSession(client, live, session, System.nanoTime());
                    else refreshLease(client, live, session);
                }
            });
        }
    }

    private static boolean checkSession(CIOClient client, L1PcInstance pc,
            ClientAutomationSession session, long now) {
        int reason = !PssConfig.get().permits(pc) || session.playerStamp() != pc.getSessionStamp()
                || session.mapId() != pc.getMapId() ? INVALID_MAP
                : pc.isDead() ? USER_DEAD : 0;
        if (reason == 0 && (session.remainingSeconds(now) == 0
                || System.currentTimeMillis() >= session.quotaExpiresMs())) {
            // At the daily boundary, renew in CacheD and swap the lease via owner CAS;
            // never notify the client to stop hunting at the reset time.
            if (client.isPssClaimPending()) return true;
            final long reset = session.quotaNextResetMs();
            if (session.quotaExpiresMs() == reset) {
                long remainingMs = reset - System.currentTimeMillis();
                if (remainingMs > 0L) {
                    // A nano-timer can beat the wall-clock millisecond boundary slightly.
                    if (scheduleLeaseDeadline(client, session, remainingMs + 2L)) return true;
                } else {
                    refreshLease(client, pc, session);
                    return true;
                }
            }
            reason = TIME_EXPIRE;
        }
        if (reason == 0) return true;
        if (client.releaseAutomation(session)) {
            enqueueLeaseClose(session.quotaCharId(), session.quotaDayStartMs(),
                    session.quotaToken(), null, false);
            sendForceFinishPlaySupportNoti(client, reason);
        }
        return false;
    }

    private static boolean scheduleLeaseDeadline(CIOClient client,
            ClientAutomationSession session, long delayMs) {
        final var socket = client.getConnection();
        final int shard = socket == null ? -1 : socket.affinityShard();
        final WeakReference<CIOClient> weakClient = new WeakReference<>(client);
        return CIOThreadCallback.getInstance().scheduleNetworkTimeout(shard,
                new CIOThreadCallback.WheelTask() {
                    public boolean isCancelled() {
                        CIOClient c = weakClient.get();
                        return c == null || c.getAutomationSession() != session;
                    }
                    public void run() {
                        CIOClient c = weakClient.get();
                        if (c == null || c.getAutomationSession() != session) return;
                        L1PcInstance pc = player(c);
                        if (pc != null) checkSession(c, pc, session, session.deadlineNanos());
                        else onClientDetached(c);
                    }
                }, Math.max(1L, delayMs));
    }

    /** Existing CacheD ordered lane performs a durable rollover without losing client PSS ownership. */
    private static void refreshLease(CIOClient client, L1PcInstance pc,
            ClientAutomationSession old) {
        if (client.getAutomationSession() != old || !client.tryBeginPssClaim()) return;
        final PssConfig.Settings settings = PssConfig.get();
        final int charId = old.quotaCharId();
        try {
            CacheD.getInstance().submitDbJobOrdered(charId, () -> {
                final PssDailyQuota.Grant next;
                try {
                    next = PssDailyQuota.renew(charId, old.quotaDayStartMs(), old.quotaToken(),
                            System.currentTimeMillis(), settings.dailySeconds(),
                            settings.resetMinuteOfDay(), settings.resetNotBeforeMs());
                } catch (SQLException | RuntimeException failure) {
                    LOG.log(Level.WARNING, "[PSS] lease rollover DB failed player=" + charId, failure);
                    failRenewal(client, old);
                    return;
                }
                final AtomicBoolean installed = new AtomicBoolean();
                final AtomicBoolean settled = new AtomicBoolean();
                CIOThreadCallback.getInstance().completeSession(client, () -> {
                    ClientAutomationSession replacement = null;
                    try {
                        if (player(client) != pc || client.getAutomationSession() != old) return;
                        if (next != null && PssConfig.get().permits(pc)) {
                            long durationMs = next.expiresMs() - System.currentTimeMillis();
                            if (durationMs > 0L) {
                                replacement = new ClientAutomationSession(old.mode(), old.playerStamp(),
                                        old.mapId(), System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs),
                                        charId, next.dayStartMs(), next.token(), next.expiresMs(), next.resetAtMs());
                                if (scheduleLeaseDeadline(client, replacement, durationMs)
                                        && client.replaceAutomation(old, replacement)) {
                                    installed.set(true);
                                    client.invalidatePssCache();
                                    oneField(client, ProtoMsg.SC_PSS_TIME_CHECK_ACK,
                                            replacement.remainingSeconds(System.nanoTime()));
                                }
                            }
                        }
                        if (!installed.get() && client.releaseAutomation(old)) {
                            if (next == null) enqueueLeaseClose(charId, old.quotaDayStartMs(), old.quotaToken(), null, false);
                            sendForceFinishPlaySupportNoti(client, TIME_EXPIRE);
                        }
                    } finally {
                        client.endPssClaim();
                        if (!installed.get() && next != null) settleLease(charId, next, settled);
                        if (installed.get() && settings != PssConfig.get()
                                && client.getAutomationSession() == replacement)
                            refreshLease(client, pc, replacement);
                    }
                }, failure -> {
                    if (failure != null) {
                        client.endPssClaim();
                        if (!installed.get()) {
                            if (next != null) settleLease(charId, next, settled);
                            failRenewal(client, old);
                        }
                    }
                });
            });
        } catch (RuntimeException rejected) {
            client.endPssClaim();
            LOG.log(Level.WARNING, "[PSS] lease rollover admission rejected player=" + charId, rejected);
            failRenewal(client, old);
        }
    }

    private static void failRenewal(CIOClient client, ClientAutomationSession old) {
        CIOThreadCallback.getInstance().completeSession(client, () -> {
            client.endPssClaim();
            if (client.releaseAutomation(old)) {
                enqueueLeaseClose(old.quotaCharId(), old.quotaDayStartMs(), old.quotaToken(), null, false);
                sendForceFinishPlaySupportNoti(client, UNKNOWN_ERROR);
            }
        }, failure -> { if (failure != null) client.endPssClaim(); });
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

    private static boolean sendStartPlaySupportAck(CIOClient client, int result) {
        return oneField(client, ProtoMsg.SC_START_PLAY_SUPPORT_ACK, result);
    }

    private static boolean oneField(CIOClient client, int message, int value) {
        ProtoWriter out = Proto.begin(client, message, 32);
        if (out == null) return false;
        try { out.uint32(1, value); return Proto.commit(out); }
        finally { Proto.abort(out); }
    }

    private static void sendForceFinishPlaySupportNoti(CIOClient client, int reason) {
        final L1PcInstance pc = player(client);
        if (pc != null) pc.getCreatureController().StopPlaySupportAutoActions();
        ProtoWriter out = Proto.begin(client, ProtoMsg.SC_FORCE_FINISH_PLAY_SUPPORT_NOTI, 32);
        if (out == null) return;
        try { out.uint32(1, reason).uint32(2, 0); Proto.commit(out); }
        finally { Proto.abort(out); }
    }
}
