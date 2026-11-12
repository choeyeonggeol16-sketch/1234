package l1j.server.server.model.network.core.sc.packet;

import l1j.server.Config;
import l1j.server.MJ3SEx.Loader.SpriteInformationLoader;
import l1j.server.MJInstanceSystem.MJInstanceObject;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJWarSystem.MJWar;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.MJNpcMarkTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SignboardInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

/**
 * Objectless direct serializer for S_WorldPutObject.
 *
 * No ServerPacket / Snapshot object is created. The producer shard captures the
 * required state into local primitives/references and serializes the complete
 * plaintext wire frame directly into its shard-local ROutboundPipe.
 *
 * Test version: no legacy fallback.
 */
public final class SC_WorldPutObject {

    public static final boolean IS_PRESENTATION_MARK = true;

    private static final int SC_WORLD_PUT_OBJECT_NOTI = 0x77;

    /*
     * Full PC/NPC/Builder messages are protobuf-like and variable sized.
     * Every writeS2 field is capped at 255 encoded bytes by WriteBuffer.
     * 4096 bytes covers all fixed fields + all S2 strings with ample headroom.
     * PC adds the current shop-chat byte length because that field is raw bytes.
     */
    /*
     * Variable WorldPut frames cannot use one guessed fixed reserve.
     * WriteBuffer.writeS2() reserves up to 2 bytes per UTF-16 code unit before
     * MS949 encoding, so calculate a safe upper bound from the actual strings.
     * FIXED_COMPLEX_RESERVE covers headers, protobuf keys/varints/bools/trailer.
     */
    private static final int FIXED_COMPLEX_RESERVE = 1024;
    private static final int MINIMAL_RESERVE = 256;

    private SC_WorldPutObject() {
    }

    // =====================================================================
    // COMPATIBILITY-SHAPED DIRECT ENTRY POINTS
    // =====================================================================

    /** Old: pc.sendPackets(S_WorldPutObject.put(pc)); */
    public static boolean put(
            CIOClient client,
            L1PcInstance pc) {

        return sendPcFull(
                client,
                pc,
                pc != null ? pc.getName() : "",
                false);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(npc)); */
    public static boolean get(
            CIOClient client,
            L1NpcInstance npc) {

        return sendNpc(client, npc);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(builder)); */
    public static boolean get(
            CIOClient client,
            WorldPutBuilder builder) {

        return sendBuilder(client, builder);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.gets(target)); */
    public static boolean gets(
            CIOClient client,
            L1PcInstance pc) {

        return sendPcGets(client, pc);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(target)); */
    public static boolean get(
            CIOClient client,
            L1PcInstance pc) {

        return sendPcFull(
                client,
                pc,
                pc != null ? pc.getName() : "",
                false);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(target, a)); */
    public static boolean get(
            CIOClient client,
            L1PcInstance pc,
            int a) {

        return sendPcMinimal(client, pc);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.getinvis(target, invis)); */
    public static boolean getinvis(
            CIOClient client,
            L1PcInstance pc,
            boolean invis) {

        return sendPcFull(
                client,
                pc,
                invis ? "" : (pc != null ? pc.getName() : ""),
                false);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.getDetectedInvis(target)); */
    public static boolean getDetectedInvis(
            CIOClient client,
            L1PcInstance pc) {

        return sendPcFull(
                client,
                pc,
                pc != null ? pc.getName() : "",
                true);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(target, isClan)); */
    public static boolean get(
            CIOClient client,
            L1PcInstance pc,
            boolean isClan) {

        return sendPcFull(
                client,
                pc,
                pc != null ? pc.getName() : "",
                false);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(target, name, isClan)); */
    public static boolean get(
            CIOClient client,
            L1PcInstance pc,
            String name,
            boolean isClan) {

        return sendPcFull(
                client,
                pc,
                name,
                false);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(target, name, isClan, a)); */
    public static boolean get(
            CIOClient client,
            L1PcInstance pc,
            String name,
            boolean isClan,
            int a) {

        return sendPcMinimal(client, pc);
    }

    /** Old: pc.sendPackets(S_WorldPutObject.get(body)); */
    public static boolean get(
            CIOClient client,
            byte[] body) {

        return sendRaw(client, body);
    }

    // =====================================================================
    // DIRECT SEND - PC FULL
    // =====================================================================

    public static boolean sendPcFull(
            CIOClient client,
            L1PcInstance pc) {

        return sendPcFull(
                client,
                pc,
                pc != null ? pc.getName() : "",
                false);
    }

    public static boolean sendPcFull(
            CIOClient client,
            L1PcInstance pc,
            String requestedName,
            boolean revealInvisibleName) {

        if (pc == null
                || client == null
                || !client.isConnected()) {
            return false;
        }

        final CUserSocket connection =
                client.getConnection();

        if (connection == null
                || !connection.isClientConnected()) {
            return false;
        }

        /* Capture once. No snapshot object. */
        final int objectId = pc.getId();
        if (objectId == 0) {
            return false;
        }

        final int x = pc.getX();
        final int y = pc.getY();
        final int heading = pc.getHeading();
        final int spriteId = pc.getCurrentSpriteId();
        final int action = computePcAction(pc);

        final int lightRadius = computePcLightRadius(pc);
        final int lawful = pc.getLawful();
        final int level = pc.getLevel();
        final int speedData = pc.isHaste() ? 1 : 0;
        final int emotion = computePcEmotion(pc);

        final boolean isGhost = pc.isGhost();
        final boolean isParalyzed = pc.isParalyzed();
        final boolean isInvisible = pc.isInvisble();
        final boolean isPoisoned = pc.getPoison() != null;

        final String desc =
                computePcDesc(
                        pc,
                        requestedName,
                        revealInvisibleName);

        final String title = safeString(getTitle(pc));
        final String clanName = safeString(pc.getClanname());
        final String masterName = "";

        final byte[] shopChat = pc.getShopChat();
        final int shopChatLength =
                shopChat != null ? shopChat.length : 0;

        final L1Clan clan = safeClan(pc);
        final boolean combatField = safeCombatField(pc);

        final int emblemId =
                clan != null && !combatField
                        ? safeClanEmblem(clan)
                        : 0;

        final int boundaryIndex =
                safeBoundaryIndex(level, spriteId);

        final int markStatus =
                computePcMarkStatus(pc, combatField);

        final boolean isCastleOffense =
                clan != null
                        && safeClanRank(pc) == L1Clan.군주
                        && safeCastleOffense(clan);

        final int reserve = complexReserve(
                shopChatLength,
                desc,
                title,
                clanName,
                masterName);

        if (reserve <= 0) {
            return false;
        }


        final CIOFrame frame =
                connection.beginDirectFrame(
                        reserve);

        if (frame == null) {
            return false;
        }

        final WriteBuffer b = frame.buffer();

        boolean committed = false;

        try {
            writeHeader(b);

            writeBase(
                    b,
                    x, y,
                    objectId,
                    spriteId,
                    action,
                    heading,
                    lightRadius,
                    1,
                    lawful,
                    desc,
                    title,
                    speedData,
                    emotion);

            writeVarint(b, 13, 0);
            writeBool(b, 14, isGhost);
            writeBool(b, 15, isParalyzed);
            writeBool(b, 16, true);
            writeBool(b, 17, isInvisible);
            writeBool(b, 18, isPoisoned);

            writeVarint(b, 19, emblemId);
            writeString(b, 20, clanName);
            writeString(b, 21, masterName);
            writeVarint(b, 22, 0);
            writeVarint(b, 23, -1);
            writeVarint(b, 24, level);

            writeByteArray(b, 25, shopChat);

            writeVarint(b, 26, -1);
            writeBool(b, 27, false);
            writeVarint(b, 28, boundaryIndex);
            writeVarint(b, 29, 0);
            writeVarint(b, 30, -1);
            writeVarint(b, 31, 0);
            writeVarint(b, 32, 0);
            writeVarint(b, 33, markStatus);
            writeVarint(b, 35, 0);
            writeVarint(b, 36, 0);

            if (isCastleOffense) {
                writeBool(b, 38, true);
            }

            writeTrailer(b);

            committed =
                    connection.commitDirectFrame(
                            frame);

            return committed;

        } catch (Throwable ignored) {
            return false;

        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    // =====================================================================
    // DIRECT SEND - NPC
    // =====================================================================

    public static boolean sendNpc(
            CIOClient client,
            L1NpcInstance npc) {

        if (npc == null
                || client == null
                || !client.isConnected()) {
            return false;
        }

        final CUserSocket connection =
                client.getConnection();

        if (connection == null
                || !connection.isClientConnected()) {
            return false;
        }

        /* Capture once. No NpcSnapshot allocation. */
        final int objectId = npc.getId();
        if (objectId == 0) {
            return false;
        }

        final int x = npc.getX();
        final int y = npc.getY();
        final int spriteId = npc.getCurrentSpriteId();
        final int action = computeNpcAction(npc);
        final int heading = npc.getHeading();

        final int lightSize =
                npc.getNpcTemplate().getLightSize();

        final int lawful = npc.getTempLawful();
        final String desc = computeNpcDesc(npc);
        final String title = computeNpcTitle(npc);
        final int moveSpeed = npc.getMoveSpeed();
        final int braveSpeed = npc.getBraveSpeed();
        final int emblemId = computeNpcEmblem(npc);
        final String shopName = npc.getShopName();
        final int teamId = computeNpcTeamId(npc);

        final boolean isParalyzed =
                npc.getParalysis() != null;

        final boolean isPassObject = npc.isPassObject();
        final boolean isInvisible = npc.isInvisble();
        final boolean isPoisoned = npc.getPoison() != null;

        final L1Character master = npc.getMaster();
        final String masterName =
                master != null
                        ? safeString(master.getName())
                        : "";

        final int level = npc.getLevel();
        final int weakAttr =
                npc.getNpcTemplate().get_weakAttr();

        final long explosionTime = npc.getExplosionTime();
        final int npcClassId = npc instanceof L1MonsterInstance
                ? npc.getNpcTemplate().get_npc_class_id() : 0;

        final int reserve = complexReserve(
                0,
                desc,
                title,
                masterName,
                shopName);

        if (reserve <= 0) {
            return false;
        }


        final CIOFrame frame =
                connection.beginDirectFrame(
                        reserve);

        if (frame == null) {
            return false;
        }

        final WriteBuffer b = frame.buffer();

        boolean committed = false;

        try {
            writeHeader(b);

            writeBase(
                    b,
                    x, y,
                    objectId,
                    spriteId,
                    action,
                    heading,
                    lightSize,
                    1,
                    lawful,
                    desc,
                    title,
                    moveSpeed,
                    braveSpeed);

            writeVarint(b, 13, 0);
            writeBool(b, 14, false);
            writeBool(b, 15, isParalyzed);
            writeBool(
                    b,
                    16,
                    shopName != null || !isPassObject);
            writeBool(b, 17, isInvisible);
            writeBool(b, 18, isPoisoned);

            writeVarint(b, 19, emblemId);
            writeString(b, 20, "");
            writeString(b, 21, masterName);
            writeVarint(b, 22, 5);
            writeVarint(b, 23, -1);
            writeVarint(b, 24, level);

            if (shopName != null) {
                writeString(b, 25, shopName);
            }

            writeVarint(b, 26, 1);
            writeBool(b, 27, false);
            writeVarint(b, 28, 0);
            writeVarint(b, 29, weakAttr);
            writeVarint(b, 30, -1);
            writeVarint(b, 31, 0);
            writeVarint(b, 32, 0);
            writeVarint(b, 33, teamId);
            writeVarint(b, 34, 0);
            writeVarint(b, 35, 0);
            writeVarint(b, 36, 0);

            if (explosionTime != 0L) {
                writeVarint(b, 37, explosionTime);
            }

            if (npcClassId > 0) {
                writeVarint(b, 39, npcClassId);
            }

            writeTrailer(b);

            committed =
                    connection.commitDirectFrame(
                            frame);

            return committed;

        } catch (Throwable ignored) {
            return false;

        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    // =====================================================================
    // DIRECT SEND - PC PARTIAL
    // =====================================================================

    public static boolean sendPcGets(
            CIOClient client,
            L1PcInstance pc) {

        if (pc == null
                || client == null
                || !client.isConnected()) {
            return false;
        }

        final int objectId = pc.getId();
        if (objectId == 0) {
            return false;
        }

        final int x = pc.getX();
        final int y = pc.getY();
        final int spriteId = pc.getCurrentSpriteId();
        final int action = computePcAction(pc);

        return sendPcPartial(
                client,
                x,
                y,
                objectId,
                spriteId,
                action,
                true);
    }

    public static boolean sendPcMinimal(
            CIOClient client,
            L1PcInstance pc) {

        if (pc == null
                || client == null
                || !client.isConnected()) {
            return false;
        }

        final int objectId = pc.getId();
        if (objectId == 0) {
            return false;
        }

        return sendPcPartial(
                client,
                pc.getX(),
                pc.getY(),
                objectId,
                0,
                0,
                false);
    }

    private static boolean sendPcPartial(
            CIOClient client,
            int x,
            int y,
            int objectId,
            int spriteId,
            int action,
            boolean includeVisual) {

        final CUserSocket connection =
                client.getConnection();

        if (connection == null
                || !connection.isClientConnected()) {
            return false;
        }


        final CIOFrame frame =
                connection.beginDirectFrame(
                        MINIMAL_RESERVE);

        if (frame == null) {
            return false;
        }

        final WriteBuffer b = frame.buffer();

        boolean committed = false;

        try {
            writeHeader(b);

            writePoint(b, 1, x, y);
            writeVarint(b, 2, objectId);

            if (includeVisual) {
                writeVarint(b, 3, spriteId);
                writeVarint(b, 4, action);
            }

            writeTrailer(b);

            committed =
                    connection.commitDirectFrame(
                            frame);

            return committed;

        } catch (Throwable ignored) {
            return false;

        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    // =====================================================================
    // DIRECT SEND - BUILDER
    // =====================================================================

    public static boolean sendBuilder(
            CIOClient client,
            WorldPutBuilder s) {

        if (s == null
                || client == null
                || !client.isConnected()) {
            return false;
        }

        final CUserSocket connection =
                client.getConnection();

        if (connection == null
                || !connection.isClientConnected()) {
            return false;
        }

        final int x = s.x;
        final int y = s.y;
        final int objectId = s.object_id;
        final int spriteId = s.spr_id;
        final int action = s.action;
        final int heading = s.h;
        final int lightSize = s.light_size;
        final int lawful = s.law_full;

        final String name = safeString(s.name);
        final String title = safeString(s.title);
        final String masterName = safeString(s.master_name);

        final int moveSpeed = s.move_speed;
        final int braveSpeed = s.brave_speed;
        final int emblemId = s.emblem_id;
        final int altitude = s.altitude;
        final int hitratio = s.hitratio;
        final int level = s.level;
        final int weaponSprite = s.weapon_sprite;

        final boolean coupleState = s.couplestate;
        final boolean isParalyzed = s.is_paralyzed;
        final boolean isUser = s.is_user;
        final boolean isInvisible = s.is_invisible;
        final boolean isPoisoned = s.is_poisoned;

        final int boundaryLevelIndex = s.boundarylevelindex;
        final int weakElemental = s.weakelemental;
        final int manaRatio = s.mana_ratio;
        final int botIndex = s.botindex;

        final int reserve = complexReserve(
                0,
                name,
                title,
                masterName,
                null);

        if (reserve <= 0) {
            return false;
        }


        final CIOFrame frame =
                connection.beginDirectFrame(
                        reserve);

        if (frame == null) {
            return false;
        }

        final WriteBuffer b = frame.buffer();

        boolean committed = false;

        try {
            writeHeader(b);

            writeBase(
                    b,
                    x, y,
                    objectId,
                    spriteId,
                    action,
                    heading,
                    lightSize,
                    1,
                    lawful,
                    name,
                    title,
                    moveSpeed,
                    braveSpeed);

            writeVarint(b, 13, 0);
            writeBool(b, 14, false);
            writeBool(b, 15, isParalyzed);
            writeBool(b, 16, isUser);
            writeBool(b, 17, isInvisible);
            writeBool(b, 18, isPoisoned);

            writeVarint(b, 19, emblemId);
            writeString(b, 20, "");
            writeString(b, 21, masterName);
            writeVarint(b, 22, altitude);
            writeVarint(b, 23, hitratio);
            writeVarint(b, 24, level);
            writeVarint(b, 26, weaponSprite);
            writeBool(b, 27, coupleState);
            writeVarint(b, 28, boundaryLevelIndex);
            writeVarint(b, 29, weakElemental);
            writeVarint(b, 30, manaRatio);
            writeVarint(b, 31, botIndex);
            writeVarint(b, 32, 0);
            writeVarint(b, 33, 0);
            writeVarint(b, 34, 0);
            writeVarint(b, 35, 0);
            writeVarint(b, 36, 0);

            writeTrailer(b);

            committed =
                    connection.commitDirectFrame(
                            frame);

            return committed;

        } catch (Throwable ignored) {
            return false;

        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    // =====================================================================
    // DIRECT SEND - RAW BODY
    // =====================================================================

    public static boolean sendRaw(
            CIOClient client,
            byte[] body) {

        if (body == null
                || body.length == 0
                || client == null
                || !client.isConnected()) {
            return false;
        }

        final long reserveLong =
                (long) ConnectionConfig.HEADER_SIZE
                        + 1L
                        + Short.BYTES
                        + body.length;

        if (reserveLong > ConnectionConfig.MAX_WIRE_PACKET_SIZE) {
            return false;
        }

        final CUserSocket connection =
                client.getConnection();

        if (connection == null
                || !connection.isClientConnected()) {
            return false;
        }


        final CIOFrame frame =
                connection.beginDirectFrame(
                        (int) reserveLong);

        if (frame == null) {
            return false;
        }

        final WriteBuffer b = frame.buffer();

        boolean committed = false;

        try {
            writeHeader(b);
            b.writeByte(body);

            committed =
                    connection.commitDirectFrame(
                            frame);

            return committed;

        } catch (Throwable ignored) {
            return false;

        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    // =====================================================================
    // BROADCAST
    // =====================================================================

    /** Test version: direct only, no S_WorldPutObject fallback. */
    public static void broadcastNpc(
            L1NpcInstance npc) {

        if (npc == null) {
            return;
        }

        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(npc);
            try {
                for (L1PcInstance pc : __cioVisible0) {
        
                    if (pc == null) {
                        continue;
                    }
        
                    final CIOClient client =
                            pc.getNetConnection();
        
                    if (client == null || !client.isConnected()) {
                        continue;
                    }
        
                    sendNpc(client, npc);
                }
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }

    /** Test version: direct only, no S_WorldPutObject fallback. */
    public static void broadcastPc(
            L1PcInstance pc) {

        broadcastPc(
                pc,
                pc != null ? pc.getName() : "",
                false);
    }

    public static void broadcastPc(
            L1PcInstance pc,
            String requestedName,
            boolean revealInvisibleName) {

        if (pc == null) {
            return;
        }

        {
            final L1World __cioWorld1 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible1 = __cioWorld1.borrowVisiblePlayerFast(pc);
            try {
                for (L1PcInstance viewer : __cioVisible1) {
        
                    if (viewer == null) {
                        continue;
                    }
        
                    final CIOClient client =
                            viewer.getNetConnection();
        
                    if (client == null || !client.isConnected()) {
                        continue;
                    }
        
                    sendPcFull(
                            client,
                            pc,
                            requestedName,
                            revealInvisibleName);
                }
            } finally {
                __cioWorld1.releaseVisiblePlayerFast(__cioVisible1);
            }
        }
    }

    public static void broadcastBuilder(
            L1Character anchor,
            WorldPutBuilder builder) {

        if (anchor == null || builder == null) {
            return;
        }

        {
            final L1World __cioWorld2 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible2 = __cioWorld2.borrowVisiblePlayerFast(anchor);
            try {
                for (L1PcInstance pc : __cioVisible2) {
        
                    if (pc == null) {
                        continue;
                    }
        
                    final CIOClient client =
                            pc.getNetConnection();
        
                    if (client == null || !client.isConnected()) {
                        continue;
                    }
        
                    sendBuilder(client, builder);
                }
            } finally {
                __cioWorld2.releaseVisiblePlayerFast(__cioVisible2);
            }
        }
    }

    private static int complexReserve(
            int rawBytes,
            String a,
            String b,
            String c,
            String d) {

        long reserve =
                (long) FIXED_COMPLEX_RESERVE
                        + Math.max(rawBytes, 0);

        reserve += maxMs949Bytes(a);
        reserve += maxMs949Bytes(b);
        reserve += maxMs949Bytes(c);
        reserve += maxMs949Bytes(d);

        if (reserve > ConnectionConfig.MAX_WIRE_PACKET_SIZE) {
            return -1;
        }

        return (int) reserve;
    }

    private static long maxMs949Bytes(String value) {
        return value == null
                ? 0L
                : ((long) value.length() << 1);
    }

    // =====================================================================
    // WIRE HELPERS - IDENTICAL FIELD ORDER TO S_WorldPutObject
    // =====================================================================

    private static void writeHeader(
            WriteBuffer b) {

        b.writeC(Opcodes.S_EXTENDED_PROTOBUF);
        b.writeH(SC_WORLD_PUT_OBJECT_NOTI);
    }

    private static void writeBase(
            WriteBuffer b,
            int x,
            int y,
            int objectId,
            int spriteId,
            int action,
            int heading,
            int lightRadius,
            int objectCount,
            int lawful,
            String desc,
            String title,
            int speedData,
            int emotion) {

        writePoint(b, 1, x, y);
        writeVarint(b, 2, objectId);
        writeVarint(b, 3, spriteId);
        writeVarint(b, 4, action);
        writeVarint(b, 5, heading);
        writeVarint(b, 6, lightRadius);
        writeVarint(b, 7, objectCount);
        writeVarint(b, 8, lawful);
        writeString(b, 9, desc);
        writeString(b, 10, title);
        writeVarint(b, 11, speedData);
        writeVarint(b, 12, emotion);
    }

    private static void writePoint(
            WriteBuffer b,
            int field,
            int x,
            int y) {

        b.writeBit((long) field << 3);
        b.writePoint(x, y);
    }

    private static void writeVarint(
            WriteBuffer b,
            int field,
            long value) {

        b.writeBit((long) field << 3);
        b.writeBit(value);
    }

    private static void writeBool(
            WriteBuffer b,
            int field,
            boolean value) {

        b.writeBit((long) field << 3);
        b.writeB(value);
    }

    private static void writeString(
            WriteBuffer b,
            int field,
            String value) {

        b.writeBit(((long) field << 3) | 2L);
        b.writeS2(safeString(value));
    }

    private static void writeByteArray(
            WriteBuffer b,
            int field,
            byte[] value) {

        b.writeBit(((long) field << 3) | 2L);

        if (value == null || value.length == 0) {
            b.writeBit(0);
            return;
        }

        b.writeBit(value.length);
        b.writeByte(value);
    }

    private static void writeTrailer(
            WriteBuffer b) {

        b.writeH(0);
    }

    // =====================================================================
    // PC HELPERS
    // =====================================================================

    private static int computePcAction(
            L1PcInstance pc) {

        if (pc.isDead()) {
            return ActionCodes.ACTION_Die;
        }

        if (pc.isPrivateShop()) {
            return 70;
        }

        if (pc.isFishing()) {
            return 71;
        }

        return pc.getCurrentWeapon();
    }

    private static int computePcEmotion(
            L1PcInstance pc) {

        if (pc.isBrave()
                || pc.isBlood_lust()
                || pc.isElfBraveMagicShort()) {
            return 0x01;
        }

        if (pc.isElfBrave()) {
            return 0x03;
        }

        if (pc.hasSkillEffect(L1SkillId.맥시멈)
                && pc.hasSkillEffect(L1SkillId.STATUS_ELFBRAVE)) {
            return 0x03;
        }

        if (pc.hasSkillEffect(L1SkillId.에볼루션)
                && pc.hasSkillEffect(L1SkillId.STATUS_ELFBRAVE)) {
            return 0x03;
        }

        if (pc.isPassive(MJPassiveID.DARK_HORSE.toInt())
                && pc.hasSkillEffect(L1SkillId.STATUS_FRUIT)) {
            return 0x03;
        }

        if (pc.isFastMovable() || pc.isFruit()) {
            return 0x04;
        }

        if (pc.isElfBraveMagicLong()) {
            return 0x09;
        }

        if (pc.istest()) {
            return pc.get_focus_wave_level() + 9;
        }

        return 0;
    }

    private static int computePcLightRadius(
            L1PcInstance pc) {

        try {
            return pc.getLight() != null
                    ? pc.getLight().getOwnLightSize()
                    : 0;

        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String computePcDesc(
            L1PcInstance pc,
            String requestedName,
            boolean revealInvisibleName) {

        final String name =
                requestedName != null
                        ? requestedName
                        : safeString(pc.getName());

        try {
            if (!revealInvisibleName && pc.isInvisble()) {
                return "";
            }
        } catch (RuntimeException ignored) {
        }

        return name;
    }

    private static String getTitle(
            L1PcInstance pc) {

        if (Config.수배온오프
                && pc.hasSkillEffect(L1SkillId.USER_WANTED)) {
            return L1PcInstance.WANTED_TITLE;
        }

        if (Config.수배온오프
                && pc.hasSkillEffect(L1SkillId.USER_WANTED1)) {
            return L1PcInstance.WANTED_TITLE1;
        }

        return safeString(pc.getTitle());
    }

    private static int computePcMarkStatus(
            L1PcInstance pc,
            boolean combatField) {

        try {
            final int mark = pc.get_mark_status();

            if (mark > 0 && !combatField) {
                return 0;
            }

            return computeTeamId(pc, mark);

        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int computeTeamId(
            L1PcInstance pc,
            int mark) {

        try {
            final MJInstanceObject instance =
                    MJInstanceSpace.getInstance()
                            .getOpenObject(pc.getMapId());

            if (instance != null) {
                return mark;
            }
        } catch (RuntimeException ignored) {
        }

        try {
            if (Config.영자마크 && pc.isGm()) {
                return 29;
            }
        } catch (RuntimeException ignored) {
        }

        return 0;
    }

    private static L1Clan safeClan(
            L1PcInstance pc) {

        try {
            return pc.getClan();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int safeClanEmblem(
            L1Clan clan) {

        try {
            return clan != null
                    ? clan.getEmblemId()
                    : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int safeClanRank(
            L1PcInstance pc) {

        try {
            return pc.getClanRank();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean safeCastleOffense(
            L1Clan clan) {

        try {
            return clan != null
                    && MJWar.isCastleOffenseClan(clan);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean safeCombatField(
            L1PcInstance pc) {

        try {
            return pc.is_combat_field();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int safeBoundaryIndex(
            int level,
            int spriteId) {

        try {
            return SpriteInformationLoader
                    .levelToIndex(level, spriteId);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // =====================================================================
    // NPC HELPERS
    // =====================================================================

    private static int computeNpcAction(
            L1NpcInstance npc) {

        final int npcId = npc.getNpcId();
        final int spriteId = npc.getCurrentSpriteId();

        if (npc.getShopName() != null) {
            return 70;
        }

        if ((npc.getNpcTemplate().is_doppel() && spriteId != 31)
                || spriteId == 727
                || spriteId == 985
                || spriteId == 986
                || spriteId == 6632
                || spriteId == 6634
                || spriteId == 6636
                || spriteId == 6638) {
            return 4;
        }

        if (spriteId == 110) {
            return 24;
        }

        if (spriteId == 111) {
            return 4;
        }

        if (spriteId == 51 || npcId == 60519) {
            return 24;
        }

        if (spriteId == 816) {
            return 20;
        }

        if (npcId == 203053) {
            return 4;
        }

        if (npcId == 203052) {
            return 20;
        }

        return npc.getStatus();
    }

    private static String computeNpcDesc(
            L1NpcInstance npc) {

        if (npc.getNpcId() == 6200008) {
            try {
                return safeString(
                        ((L1MerchantInstance) npc)
                                .getClanname());
            } catch (RuntimeException e) {
                return "";
            }
        }

        if (npc instanceof L1SignboardInstance) {
            return "";
        }

        final String nameId = npc.getNameId();

        return nameId != null && !nameId.isEmpty()
                ? nameId
                : "";
    }

    private static String computeNpcTitle(
            L1NpcInstance npc) {

        if (npc instanceof L1FieldObjectInstance) {
            try {
                final L1NpcTalkData talk =
                        NPCTalkDataTable.getInstance()
                                .getTemplate(
                                        npc.getNpcTemplate()
                                                .get_npcId());

                return talk != null
                        ? safeString(talk.getNormalAction())
                        : "";

            } catch (RuntimeException e) {
                return "";
            }
        }

        if (npc instanceof L1SignboardInstance) {
            return safeString(npc.getNameId());
        }

        return safeString(npc.getTitle());
    }

    private static int computeNpcEmblem(
            L1NpcInstance npc) {

        if (npc.getNpcId() != 6200008) {
            return 0;
        }

        try {
            for (L1Clan clan
                    : L1World.getInstance().getAllClans()) {

                if (clan != null
                        && clan.getCastleId() == 4) {
                    return clan.getEmblemId();
                }
            }
        } catch (RuntimeException ignored) {
        }

        return 0;
    }

    private static int computeNpcTeamId(
            L1NpcInstance npc) {

        try {
            return MJNpcMarkTable.getInstance()
                    .get(npc.getNpcId());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String safeString(
            String value) {

        return value != null ? value : "";
    }

    // =====================================================================
    // BUILDER
    // =====================================================================

    public static final class WorldPutBuilder {

        int object_id;
        int x, y, h;
        int action, spr_id;
        int light_size, law_full;

        String name, title, pledge_name, master_name;

        int move_speed, brave_speed;

        boolean is_paralyzed;
        boolean is_user;
        boolean is_invisible;
        boolean is_poisoned;

        int emblem_id;
        int altitude, hitratio, level;
        int weapon_sprite;
        boolean couplestate;

        int boundarylevelindex;
        int weakelemental;
        int mana_ratio;
        int botindex;

        public WorldPutBuilder() {
            object_id = -1;
            x = y = h = 0;
            action = spr_id = law_full = 0;
            light_size = 2;
            name = title = pledge_name = master_name = "";
            move_speed = brave_speed = 1;
            altitude = 5;
            hitratio = mana_ratio = -1;
            level = weapon_sprite = 1;
            boundarylevelindex = 0;
            weakelemental = 0;
            botindex = 0;
            emblem_id = 0;
            is_paralyzed = false;
            is_user = false;
            is_invisible = false;
            is_poisoned = false;
            couplestate = false;
        }

        public WorldPutBuilder set_object_id(int value) {
            object_id = value;
            return this;
        }

        public WorldPutBuilder set_object_id() {
            return set_object_id(
                    IdFactory.getInstance().nextId());
        }

        public WorldPutBuilder set_x(int value) {
            x = value;
            return this;
        }

        public WorldPutBuilder set_y(int value) {
            y = value;
            return this;
        }

        public WorldPutBuilder set_h(int value) {
            h = value;
            return this;
        }

        public WorldPutBuilder set_action(int value) {
            action = value;
            return this;
        }

        public WorldPutBuilder set_sprid(int value) {
            spr_id = value;
            return this;
        }

        public WorldPutBuilder set_right_size(int value) {
            light_size = value;
            return this;
        }

        public WorldPutBuilder set_law_full(int value) {
            law_full = value;
            return this;
        }

        public WorldPutBuilder set_name(String value) {
            name = safeString(value);
            return this;
        }

        public WorldPutBuilder set_title(String value) {
            title = safeString(value);
            return this;
        }

        public WorldPutBuilder set_move_speed(int value) {
            move_speed = value;
            return this;
        }

        public WorldPutBuilder set_brave_speed(int value) {
            brave_speed = value;
            return this;
        }

        public WorldPutBuilder set_is_paralyzed(boolean value) {
            is_paralyzed = value;
            return this;
        }

        public WorldPutBuilder set_is_use(boolean value) {
            is_user = value;
            return this;
        }

        public WorldPutBuilder set_is_invisible(boolean value) {
            is_invisible = value;
            return this;
        }

        public WorldPutBuilder set_poisoned(boolean value) {
            is_poisoned = value;
            return this;
        }

        /** Direct replacement for old build()+sendPackets(). */
        public boolean send(CIOClient client) {
            return SC_WorldPutObject.sendBuilder(
                    client,
                    this);
        }

        public void broadcast(L1Character anchor) {
            SC_WorldPutObject.broadcastBuilder(
                    anchor,
                    this);
        }
    }
}
