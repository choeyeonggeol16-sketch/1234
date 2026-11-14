package l1j.server.server.packets.Protocol;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Packet.CS_AchievementTeleportReq;
import l1j.server.server.packets.Protocol.Packet.CS_AlchemyDesignReq;
import l1j.server.server.packets.Protocol.Packet.CS_AlchemyMakeReq;
import l1j.server.server.packets.Protocol.Packet.CS_AttendanceBonusRequestExtend;
import l1j.server.server.packets.Protocol.Packet.CS_AttendanceRewardReq;
import l1j.server.server.packets.Protocol.Packet.CS_ClientVersionInfo;
import l1j.server.server.packets.Protocol.Packet.CS_CompanionNameChangeReq;
import l1j.server.server.packets.Protocol.Packet.CS_CompanionSkillEnchantReq;
import l1j.server.server.packets.Protocol.Packet.CS_CompanionSkillNextTierOpenReq;
import l1j.server.server.packets.Protocol.Packet.CS_CompanionStatIncreaseReq;
import l1j.server.server.packets.Protocol.Packet.CS_CompanionTmCommandReq;
import l1j.server.server.packets.Protocol.Packet.CS_CompletedAchievementRewardReq;
import l1j.server.server.packets.Protocol.Packet.CS_CraftBatchReq;
import l1j.server.server.packets.Protocol.Packet.CS_CraftIdListReq;
import l1j.server.server.packets.Protocol.Packet.CS_CraftListAllReq;
import l1j.server.server.packets.Protocol.Packet.CS_CraftMakeReq;
import l1j.server.server.packets.Protocol.Packet.CS_MonsterBookV2RewardReq;
import l1j.server.server.packets.Protocol.Packet.CS_MonsterBookV2TeleportReq;
import l1j.server.server.packets.Protocol.Packet.CS_MyRankingReq;
import l1j.server.server.packets.Protocol.Packet.CS_PartyControl;
import l1j.server.server.packets.Protocol.Packet.CS_TopRankerReq;
import l1j.server.server.packets.Protocol.Packet.CS_UserPlayInfo;
import l1j.server.server.packets.Protocol.Packet.GludioDungeonPackets;

/**
 * CS 프로토 디스패처.
 */
public final class CSProto {

    private static final Logger LOGGER = Logger.getLogger(CSProto.class.getName());
    private static final int HEADER_SIZE = 5;
    private static final int DENSE_LIMIT = 0x0800; // 0x0000 ~ 0x07FF 직접 인덱싱

    private CSProto() {}

    // ------------------------------------------------------------------
    // Handler signatures
    // ------------------------------------------------------------------

    @FunctionalInterface
    public interface FrameHandler {
        void handle(CIOClient client, ProtoReader in) throws Exception;
    }

    @FunctionalInterface
    public interface RawFrameHandler {
        void handle(CIOClient client, byte[] buf, int bodyPos, int bodyLen) throws Exception;
    }

    // ------------------------------------------------------------------
    // Handler Entry
    // ------------------------------------------------------------------

    private static final class HandlerEntry {
        private final RawFrameHandler raw;
        private final FrameHandler reader;

        private HandlerEntry(RawFrameHandler raw, FrameHandler reader) {
            this.raw = raw;
            this.reader = reader;
        }

        static HandlerEntry raw(RawFrameHandler handler) {
            if (handler == null) throw new NullPointerException("handler");
            return new HandlerEntry(handler, null);
        }

        static HandlerEntry reader(FrameHandler handler) {
            if (handler == null) throw new NullPointerException("handler");
            return new HandlerEntry(null, handler);
        }

        boolean isRaw() { return raw != null; }

        void handle(CIOClient client, byte[] bytes, int bodyPos, int bodyLen) throws Exception {
            if (raw != null) {
                raw.handle(client, bytes, bodyPos, bodyLen); // ProtoReader allocation 없음
                return;
            }
            reader.handle(client, new ProtoReader(bytes, bodyPos, bodyLen));
        }
    }

    // ------------------------------------------------------------------
    // Registry
    // ------------------------------------------------------------------

    private static final class Table {
        private final HandlerEntry[] dense = new HandlerEntry[DENSE_LIMIT];
        private final ConcurrentHashMap<Integer, HandlerEntry> overflow = new ConcurrentHashMap<>(8);

        void putRaw(int msgId, RawFrameHandler handler) { put(msgId, HandlerEntry.raw(handler)); }
        void putReader(int msgId, FrameHandler handler) { put(msgId, HandlerEntry.reader(handler)); }

        private void put(int msgId, HandlerEntry entry) {
            if (msgId < 0) throw new IllegalArgumentException("[CSProto] negative msgId");

            if (msgId < dense.length) {
                if (dense[msgId] != null) throw failDuplicate(msgId);
                dense[msgId] = entry;
                return;
            }

            if (overflow.putIfAbsent(msgId, entry) != null) {
                throw failDuplicate(msgId);
            }
        }

        HandlerEntry get(int msgId) {
            if (msgId < 0) return null;
            if (msgId < dense.length) return dense[msgId];
            return overflow.get(msgId);
        }

        private IllegalStateException failDuplicate(int msgId) {
            return new IllegalStateException(String.format("[CSProto] duplicate handler msgId=0x%04X", msgId));
        }
    }

    private static final Table TABLE = build();

    // ------------------------------------------------------------------
    // Registry build
    // ------------------------------------------------------------------

    private static Table build() {
        final Table t = new Table();

        t.putRaw(ProtoMsg.CS_START_PLAY_SUPPORT_REQ,
                l1j.server.server.model.character.pss.PlaySupport2019::start);
        t.putRaw(ProtoMsg.CS_FINISH_PLAY_SUPPORT_REQ,
                l1j.server.server.model.character.pss.PlaySupport2019::finish);
        t.putRaw(ProtoMsg.CS_PSS_TIME_CHECK_REQ,
                l1j.server.server.model.character.pss.PlaySupport2019::time);
        t.putRaw(ProtoMsg.CS_CHANGE_PLAY_SUPPORT_SETTING_REQ,
                l1j.server.server.model.character.pss.PlaySupport2019::settings);
        t.putRaw(l1j.server.server.model.character.pss.window.PlaySupportWindow.REGISTER_START,
                l1j.server.server.model.character.pss.window.PlaySupportWindow::registerStart);
        t.putRaw(l1j.server.server.model.character.pss.window.PlaySupportWindow.REGISTER_COMMIT,
                l1j.server.server.model.character.pss.window.PlaySupportWindow::registerCommit);
        for (int value = 0; value < 256; value++) {
            final int octet = value;
            t.putRaw(l1j.server.server.model.character.pss.window.PlaySupportWindow.REGISTER_BYTE | octet,
                    (client, data, offset, length) ->
                        l1j.server.server.model.character.pss.window.PlaySupportWindow.registerByte(client, octet, data, offset, length));
        }

        t.putReader(ProtoMsg.CS_HIBREED_AUTH_REQ, (c, in) -> {
            long reserved = -1L;
            String token = null;
            for (int tag; (tag = in.next()) != 0;) {
                switch (tag) {
                case 0x08: reserved = in.varint64(); break;
                case 0x12: token = in.readString(); break;
                default: in.skip(tag & 7); break;
                }
            }
            l1j.server.server.model.network.core.session.DominationHybrid.onHybridAuth(c, reserved, token);
        });

        // Source44: Orim/Gludio instance room protocol, independent from HIBREED transfer.
        final int[] gludioMessages = {
            GludioDungeonPackets.CS_LIST, GludioDungeonPackets.CS_DETAIL,
            GludioDungeonPackets.CS_CREATE, GludioDungeonPackets.CS_CHANGE,
            GludioDungeonPackets.CS_ENTER, GludioDungeonPackets.CS_READY,
            GludioDungeonPackets.CS_START, GludioDungeonPackets.CS_INVITE,
            GludioDungeonPackets.CS_NOTIFY, GludioDungeonPackets.CS_EXIT,
            GludioDungeonPackets.CS_KICK
        };
        for (int msg : gludioMessages) {
            final int id = msg;
            t.putReader(id, (client, reader) -> GludioDungeonPackets.handle(client, reader, id));
        }

        // bodyless: ProtoReader가 필요 없으므로 raw로 등록
        t.putRaw(ProtoMsg.CS_USER_PLAY_INFO_REQ, (c, buf, pos, len) -> CS_UserPlayInfo.handle(c));

        // raw handlers
        t.putReader(ProtoMsg.CS_CLIENT_VERSION_INFO_REQ, CS_ClientVersionInfo::handle);
        t.putReader(ProtoMsg.CS_PARTY_CONTROL_REQ, CS_PartyControl::handle);
        t.putReader(ProtoMsg.CS_MY_RANKING_REQ, CS_MyRankingReq::handle);
        t.putReader(ProtoMsg.CS_TOP_RANKER_REQ, CS_TopRankerReq::handle);
        t.putReader(ProtoMsg.CS_ALCHEMY_DESIGN_REQ, CS_AlchemyDesignReq::handle);
        t.putReader(ProtoMsg.CS_ALCHEMY_MAKE_REQ, CS_AlchemyMakeReq::handle);
        t.putReader(ProtoMsg.CS_ATTENDANCE_BONUS_REQUEST_EXTEND, CS_AttendanceBonusRequestExtend::handle);
        t.putReader(ProtoMsg.CS_ATTENDANCE_REWARD_REQ, CS_AttendanceRewardReq::handle);
        t.putReader(ProtoMsg.CS_CRAFT_LIST_ALL_REQ, CS_CraftListAllReq::handle);
        t.putReader(ProtoMsg.CS_CRAFT_ID_LIST_REQ, CS_CraftIdListReq::handle);
        t.putReader(ProtoMsg.CS_CRAFT_MAKE_REQ, CS_CraftMakeReq::handle);
        t.putReader(ProtoMsg.CS_CRAFT_BATCH_REQ, CS_CraftBatchReq::handle);
        t.putReader(ProtoMsg.CS_MONSTER_BOOK_V2_REWARD_REQ, CS_MonsterBookV2RewardReq::handle);
        t.putReader(ProtoMsg.CS_MONSTER_BOOK_V2_TELEPORT_REQ, CS_MonsterBookV2TeleportReq::handle);
        t.putReader(ProtoMsg.CS_ACHIEVEMENT_TELEPORT_REQ, CS_AchievementTeleportReq::handle);
        t.putReader(ProtoMsg.CS_COMPLETED_ACHIEVEMENT_REWARD_REQ, CS_CompletedAchievementRewardReq::handle);
        t.putReader(ProtoMsg.CS_COMPANION_NAME_CHANGE_REQ, CS_CompanionNameChangeReq::handle);
        t.putReader(ProtoMsg.CS_COMPANION_STAT_INCREASE_REQ, CS_CompanionStatIncreaseReq::handle);
        t.putReader(ProtoMsg.CS_COMPANION_TM_COMMAND_REQ, CS_CompanionTmCommandReq::handle);
        t.putReader(ProtoMsg.CS_COMPANION_SKILL_NEXT_TIER_OPEN_REQ, CS_CompanionSkillNextTierOpenReq::handle);
        t.putReader(ProtoMsg.CS_COMPANION_SKILL_ENCHANT_REQ, CS_CompanionSkillEnchantReq::handle);

        return t;
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    public static void processFrame(CIOClient client, byte[] bytes, int pos, int msgId, int len) {
        if (client == null || bytes == null) return;

        // 비정상 패킷 조용히 무시
        if (pos < 0 || pos > bytes.length - HEADER_SIZE) return; 
        if (len < 0) return;

        final int available = bytes.length - pos - HEADER_SIZE;
        if (len > available) return;

        final HandlerEntry handler = TABLE.get(msgId);
        if (handler == null) return; // 등록되지 않은 msgId 무시

        try {
            handler.handle(client, bytes, pos + HEADER_SIZE, len);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, String.format(
                    "[CSProto] handler failure msgId=0x%04X owner=%s raw=%s",
                    msgId, owner(client), handler.isRaw()), e);
        }
    }

    public static boolean isRoutable(int msgId) {
        return TABLE.get(msgId) != null;
    }

    private static String owner(CIOClient client) {
        if (client == null) return "null";
        try {
            return client.getActiveChar() != null ? client.getActiveChar().getName() : client.getIp();
        } catch (Throwable ignored) {
            return "?";
        }
    }
}
