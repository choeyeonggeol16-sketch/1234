package l1j.server.server.model.network.core.broadcast;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** One immutable encoded frame, shared across every eligible recipient.
 * Packet-specific old serializers are never invoked during broadcast.
 */
public final class CSharedBroadcast {
    private CSharedBroadcast() {}
    // L1-specific formatting selectors for the existing Native Assemble bridge.
    public static final int FORMAT_HEADING = 3;
    public static final int FORMAT_SOUND = 4;
    public static final int FORMAT_ONLY_EFFECT = 5;
    public static final int FORMAT_REMOVE_OBJECT = 6;
    public static final int FORMAT_SKILL_SOUND = 7;
    public static final int FORMAT_EFFECT_LOCATION = 8;
    // Additional fixed L1 formats, all assembled into one refcounted Native source.
    public static final int FORMAT_LIGHT = 9;
    public static final int FORMAT_POISON = 10;
    public static final int FORMAT_HP_METER = 11;
    public static final int FORMAT_CHAR_SPEED = 12;
    public static final int FORMAT_INVIS = 13;
    public static final int FORMAT_FISHING = 14;
    public static final int FORMAT_DOOR = 15;
    public static final int FORMAT_LAWFUL = 16;
    public static final int FORMAT_LIQUOR = 17;
    public static final int FORMAT_PINKNAME = 18;
    public static final int FORMAT_SKILLBRAVE = 19;
    public static final int FORMAT_SKILLHASTE = 20;
    public static final int FORMAT_SKILLICONAURA = 21;
    public static final int FORMAT_SKILLICONBLESSOFEVA = 22;
    public static final int FORMAT_SKILLICONSHIELD = 23;
    public static final int FORMAT_SKILLICONWINDSHACKLE = 24;
    public static final int FORMAT_SKILLICONWISDOMPOTION = 25;
    public static final int FORMAT_RESURRECTION = 26;
    public static final int FORMAT_DEXUP = 27;
    public static final int FORMAT_STRUP = 28;
    public static final int FORMAT_CHARVISUALUPDATE = 29;
    public static final int FORMAT_TRUETARGETNEW = 30;
    public static final int FORMAT_CHANGESHAPE = 31;
    public static final int FORMAT_ATTACKMISS = 32;
    public static final int FORMAT_ATTACKNPC = 33;
    public static final int FORMAT_ATTACKSTATUS = 34;
    public static final int FORMAT_ATTACK = 35;
    public static final int FORMAT_CRITICAL1 = 36;
    public static final int FORMAT_CRITICAL2 = 37;
    public static final int FORMAT_ATTACKPACKET1 = 38;
    public static final int FORMAT_ATTACKPACKET2 = 39;
    public static final int FORMAT_ATTACKPACKET3 = 40;
    public static final int FORMAT_ARROWSKILL = 41;
    public static final int FORMAT_ATTACKSKILL = 42;
    /**
     * L1 combat variants need up to eleven integer fields, still assembled
     * directly into one Native pool slab without any Java wire array.
     */
    public static void sendExtended(
            L1Object anchor, int kind, int opcode,
            int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10,boolean fast) {
        if(anchor==null)return;
        final boolean optional = kind != FORMAT_CHANGESHAPE;
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> recipients=fast
                ? world.borrowVisiblePlayerFast(anchor)
                : world.getVisiblePlayer(anchor);
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                final CUserSocket socket;
                try {
                    if(!CTLMFilter.sameWorldOther(anchor,pc))continue;
                    socket = eligibleBroadcastSocket(pc);
                } catch (RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if (socket == null) continue;
                if(shared==null) {
                    shared=CAsyncSendConstBufferExceptSelf.forExtended(
                            socket,anchor.getId(),kind,opcode,
                            p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
                    if (shared == null) {
                        if (!optional) throw new IllegalStateException("Shared packet source unavailable: " + kind);
                        continue;
                    }
                }
                try { sendVisual(socket, pc.getId(), shared, optional); }
                catch (RuntimeException failure) { logOptionalNoticeFailure(failure); }
            }
        } finally {
            try {
                if(shared!=null)shared.close();
            } finally {
                if(fast)world.releaseVisiblePlayerFast(recipients);
            }
        }
    }



    /**
     * One reference design pooled plaintext, lazily assembled only after first valid
     * recipient. Keep the exact original fast/radius/filter AOI traversal,
     * ExceptSelf, recipient order and socket FIFO; no alternate SEND path.
     */
    public static void sendFormatted(
            L1Object anchor, int format, int opcode,
            int p0, int p1, int p2, int p3,
            boolean fast, int radius, Predicate<L1PcInstance> filter) {
        sendFormatted(anchor, format, opcode, p0, p1, p2, p3, fast, radius, filter, false);
    }

    public static void sendFormattedBestEffort(
            L1Object anchor, int format, int opcode,
            int p0, int p1, int p2, int p3,
            boolean fast, int radius, Predicate<L1PcInstance> filter) {
        sendFormatted(anchor, format, opcode, p0, p1, p2, p3, fast, radius, filter, true);
    }

    private static void sendFormatted(
            L1Object anchor, int format, int opcode,
            int p0, int p1, int p2, int p3,
            boolean fast, int radius, Predicate<L1PcInstance> filter, boolean optional) {
        if (anchor == null) return;
        final L1World world = L1World.getInstance();
        final boolean borrowed = fast && radius < 0;
        final List<L1PcInstance> recipients = borrowed
                ? world.borrowVisiblePlayerFast(anchor)
                : radius < 0 ? world.getVisiblePlayer(anchor)
                             : world.getVisiblePlayer(anchor, radius);
        CAsyncSendConstBufferExceptSelf shared = null;
        try {
            for (L1PcInstance pc : recipients) {
                final CUserSocket socket;
                try {
                    if (!CTLMFilter.sameWorldOther(anchor, pc)
                        || filter != null && !filter.test(pc)) continue;
                    socket = eligibleBroadcastSocket(pc);
                } catch (RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if (socket == null) continue;
                if (shared == null) {
                    shared = CAsyncSendConstBufferExceptSelf.forFormatted(
                            socket, anchor.getId(), format, opcode,
                            p0, p1, p2, p3);
                    if (shared == null) {
                        if (!optional) throw new IllegalStateException("Shared packet source unavailable: " + format);
                        continue;
                    }
                }
                try { sendVisual(socket, pc.getId(), shared, optional); }
                catch (RuntimeException failure) { logOptionalNoticeFailure(failure); }
            }
        } finally {
            try {
                if (shared != null) shared.close();
            } finally {
                if (borrowed) world.releaseVisiblePlayerFast(recipients);
            }
        }
    }


    public static void sendAction(L1Object anchor, int objectId, int actionId) {
        if (anchor == null) return;
        final L1World world = L1World.getInstance();
        final List<L1PcInstance> recipients = world.borrowVisiblePlayerFast(anchor);
        CAsyncSendConstBufferExceptSelf shared = null;
        try {
            for (L1PcInstance pc : recipients) {
                final CUserSocket socket;
                try {
                    if (!CTLMFilter.sameWorldOther(anchor, pc)) continue;
                    socket = eligibleBroadcastSocket(pc);
                } catch (RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if (socket == null) continue;
                if (shared == null) {
                    shared = CAsyncSendConstBufferExceptSelf.forAction(
                            socket, objectId, Opcodes.S_ACTION,
                            objectId, actionId);
                    if (shared == null) continue;
                }
                try { shared.sendBestEffort(socket, pc.getId()); }
                catch (RuntimeException failure) { logOptionalNoticeFailure(failure); }
            }
        } finally {
            try {
                if (shared != null) shared.close();
            } finally {
                world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

    public static void sendMove(L1Object anchor, int objectId, int x, int y, int heading) {
        if (anchor == null) return;
        final L1World world = L1World.getInstance();
        final List<L1PcInstance> recipients = world.borrowVisiblePlayerFast(anchor);
        CAsyncSendConstBufferExceptSelf shared = null;
        try {
            for (L1PcInstance pc : recipients) {
                final CUserSocket socket;
                try {
                    if (!CTLMFilter.sameWorldOther(anchor, pc)) continue;
                    socket = eligibleBroadcastSocket(pc);
                } catch (RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if (socket == null) continue;
                if (shared == null) {
                    shared = CAsyncSendConstBufferExceptSelf.forMove(
                            socket, anchor.getId(),
                            Opcodes.S_MOVE_OBJECT,
                            objectId, x, y, heading);
                    if (shared == null) throw new IllegalStateException("S_MOVE shared Native source unavailable (allocation or shutdown)");
                }
                try { shared.send(socket, pc.getId()); }
                catch (RuntimeException failure) { logOptionalNoticeFailure(failure); }
            }
        } finally {
            try {
                if (shared != null) shared.close();
            } finally {
                world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

    /**
     * Encodes once directly into an reference design Native pooled CIOBufferEx.
     * The encoder is invoked only after a connected viewer is eligible.
     * A failed source acquire/encode/commit never silently drops a broadcast.
     */
    @FunctionalInterface
    public interface NativeWireWriter {
        void write(ByteBuffer output);
    }

    private static final Charset MS949 =
            Charset.forName("MS949");

    /** Exact MS949 replacement behavior of legacy String.getBytes(MS949). */
    public static void writeMS949(ByteBuffer out,String text) {
        final var encoder=MS949.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        var result=encoder.encode(CharBuffer.wrap(text==null?"":text),out,true);
        if(result.isOverflow())throw new BufferOverflowException();
        if(result.isError())throw new IllegalArgumentException("MS949 encode error");
        result=encoder.flush(out);
        if(result.isOverflow())throw new BufferOverflowException();
        if(result.isError())throw new IllegalArgumentException("MS949 flush error");
    }

    public static void sendWritten(L1Object anchor,NativeWireWriter writer,boolean fast,
            int radius,Predicate<L1PcInstance> filter) {
        sendWritten(anchor, writer, fast, radius, filter, false);
    }

    public static void sendWrittenBestEffort(L1Object anchor, NativeWireWriter writer, boolean fast,
            int radius, Predicate<L1PcInstance> filter) {
        sendWritten(anchor, writer, fast, radius, filter, true);
    }

    private static void sendWritten(L1Object anchor, NativeWireWriter writer, boolean fast,
            int radius, Predicate<L1PcInstance> filter, boolean optional) {
        if(anchor==null||writer==null)return;
        final L1World world=L1World.getInstance();
        final boolean borrowed=fast&&radius<0;
        final List<L1PcInstance> recipients=borrowed
                ? world.borrowVisiblePlayerFast(anchor)
                : radius<0?world.getVisiblePlayer(anchor)
                          :world.getVisiblePlayer(anchor,radius);
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                final CUserSocket socket;
                try {
                    if(!CTLMFilter.sameWorldOther(anchor,pc)
                            ||filter!=null&&!filter.test(pc))continue;
                    socket=eligibleBroadcastSocket(pc);
                } catch (RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if(socket==null)continue;
                if(shared==null) {
                    shared = new CAsyncSendConstBufferExceptSelf(writer, anchor.getId(), Integer.MAX_VALUE);
                }
                try { sendVisual(socket, pc.getId(), shared, optional); }
                catch (RuntimeException failure) {
                    if (!shared.hasFrozenSource()) {
                        if (!optional) throw failure;
                        logOptionalNoticeFailure(failure);
                        break;
                    }
                    logOptionalNoticeFailure(failure);
                }
            }
        } finally {
            try {
                if(shared!=null)shared.close();
            } finally {
                if(borrowed)world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

    /** Recipient socket lookup for the reference design visual fanouts. */
    private static CUserSocket eligibleBroadcastSocket(L1PcInstance pc) {
        if (pc == null) return null;
        final CUserSocket socket = pc.getNetConnection();
        return socket != null && socket.isConnected() && socket.isClientConnected() ? socket : null;
    }

    private static void sendVisual(CUserSocket socket, int recipientId,
            CAsyncSendConstBuffer shared, boolean optional) {
        if (optional) shared.sendBestEffort(socket, recipientId);
        else shared.send(socket, recipientId);
    }

    /**
     * Visual/notificational SEND boundary for paths whose game-state progress
     * must not depend on optional socket delivery. Normal sendRecipient keeps
     * its strict error contract. No alternate serializer or SEND route.
     * Rate-limit exceptional logs during global Native-pool pressure.
     */
    private static final Logger OPTIONAL_NOTICE_LOG =
            Logger.getLogger(CSharedBroadcast.class.getName());
    private static final AtomicLong OPTIONAL_NOTICE_FAILURES =
            new AtomicLong();

    public static void sendRecipientBestEffort(CAsyncSendConstBuffer shared,L1PcInstance pc) {
        if (shared == null) return;
        try {
            final CUserSocket socket = eligibleBroadcastSocket(pc);
            if (socket != null) shared.sendBestEffort(socket, pc.getId());
        } catch (RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        }
    }

    private static void logOptionalNoticeFailure(RuntimeException failure) {
        final long failures=OPTIONAL_NOTICE_FAILURES.incrementAndGet();
        if(failures==1 || (failures&1023L)==0L)
            OPTIONAL_NOTICE_LOG.log(Level.WARNING,
                    "Optional shared broadcast failed (count="+failures+")",failure);
    }

    /**
     * Per-recipient failure isolation for optional, identical visual notices.
     * Do not stop the remaining recipients when one disconnected/failed socket
     * raises.  Keep one lazily encoded Native source and the original traversal
     * order (including duplicates), with no alternate serialization path.
     */
    /** One invalid source is a broadcast-wide failure, not 5,000 independent
     * recipient failures.  Allocated only on the exceptional slow path.
     */
    private static final class UnavailableOptionalSource extends RuntimeException {
        private static final long serialVersionUID = 1L;
        UnavailableOptionalSource(RuntimeException cause) { super(cause); }
    }

    private static CAsyncSendConstBuffer sendWrittenOptionalTo(
            CAsyncSendConstBuffer shared,L1PcInstance pc,NativeWireWriter writer) {
        final CUserSocket socket;
        final int recipient;
        try {
            socket = eligibleBroadcastSocket(pc);
            if (socket == null) return shared;
            recipient = pc.getId();
        } catch (RuntimeException failure) {
            logOptionalNoticeFailure(failure);
            return shared;
        }
        // The caller owns a source only after this helper returns it. If a
        // fatal failure escapes the FIRST send, its newly allocated Native
        // source would otherwise be lost without a matching producer release.
        CAsyncSendConstBuffer first=null;
        try {
            if(shared==null) {
                first=new CAsyncSendConstBuffer(
                        writer,Integer.MIN_VALUE,Integer.MAX_VALUE);
                shared=first;
            }
            shared.sendBestEffort(socket, recipient);
        } catch(RuntimeException failure) {
            // No frozen plaintext exists after an encoder/acquire/seal error.
            // Trying the same broken writer on every later recipient multiplies
            // Native pool traffic and can stall a whole-world broadcast.
            if(shared!=null && !shared.hasFrozenSource()) {
                if(first!=null) {
                    try { first.close(); }
                    catch(RuntimeException|Error releaseFailure) {
                        if(releaseFailure!=failure)failure.addSuppressed(releaseFailure);
                    }
                }
                logOptionalNoticeFailure(failure);
                throw new UnavailableOptionalSource(failure);
            }
            logOptionalNoticeFailure(failure);
        } catch(Error failure) {
            if(first!=null) {
                try {first.close();}
                catch(RuntimeException|Error releaseFailure) {
                    if(releaseFailure!=failure)failure.addSuppressed(releaseFailure);
                }
            }
            throw failure;
        }
        return shared;
    }

    /** Optional visual-only broadcast. No fallback or packet duplication. */
    public static void sendWrittenRecipientsBestEffort(
            Iterable<? extends L1PcInstance> recipients,NativeWireWriter writer) {
        if(recipients==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for (L1PcInstance pc : recipients)
                shared = sendWrittenOptionalTo(shared, pc, writer);
        } catch (UnavailableOptionalSource invalid) {
            // Already logged once at the failed source. Do not retry it.
        } catch (RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    public static void sendWrittenArrayBestEffort(L1PcInstance[] members,NativeWireWriter writer) {
        if(members==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:members)
                shared=sendWrittenOptionalTo(shared,pc,writer);
        } catch (UnavailableOptionalSource invalid) {
            // Source has already been released by the helper.
        } finally { closeOptional(shared); }
    }

    public static void sendWrittenGroupsBestEffort(
            Iterable<? extends L1PcInstance> first,
            Iterable<? extends L1PcInstance> second,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            if (first != null) try {
                for (L1PcInstance pc : first)
                    shared = sendWrittenOptionalTo(shared, pc, writer);
            } catch (UnavailableOptionalSource invalid) {
                return; // Do not retry a broken writer in the next group.
            } catch (RuntimeException failure) {
                logOptionalNoticeFailure(failure);
            }
            if (second != null) try {
                for (L1PcInstance pc : second)
                    shared = sendWrittenOptionalTo(shared, pc, writer);
            } catch (UnavailableOptionalSource invalid) {
                return;
            } catch (RuntimeException failure) {
                logOptionalNoticeFailure(failure);
            }
        } finally { closeOptional(shared); }
    }

    public static void sendWrittenAllPlayableBestEffort(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc == null) continue;
                try {
                    if (pc.isPrivateShop() || !pc.is_world()) continue;
                    shared = sendWrittenOptionalTo(shared, pc, writer);
                } catch (UnavailableOptionalSource invalid) {
                    return; // A broken writer has no valid packet for later recipients.
                } catch (RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                }
            }
        } catch (RuntimeException failure) {
            // Includes a failure to obtain or traverse the world collection.
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    /** Failure of one GM notification never skips the next GM. */
    public static void sendWrittenAllGmsBestEffort(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc == null) continue;
                try {
                    if (pc.isGm())
                        shared = sendWrittenOptionalTo(shared, pc, writer);
                } catch (UnavailableOptionalSource invalid) {
                    return; // A broken writer has no valid packet for later recipients.
                } catch (RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                }
            }
        } catch (RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    public static void closeOptional(CAsyncSendConstBuffer shared) {
        if(shared==null)return;
        try { shared.close(); }
        catch (RuntimeException failure) { logOptionalNoticeFailure(failure); }
    }

    /** Optional object-list notice. Keep the input's order, PC filtering and
     * duplicates; one bad socket does not suppress the next participant.
     */
    public static void sendWrittenObjectPlayersBestEffort(
            Iterable<? extends L1Object> objects,NativeWireWriter writer) {
        if(objects==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1Object object:objects)
                if(object instanceof L1PcInstance pc)
                    shared=sendWrittenOptionalTo(shared,pc,writer);
        } catch(UnavailableOptionalSource invalid) {
            // Broken plaintext; no valid frame to fan out.
        } catch(RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    /** Keep explicit self-first, then the original visible list (duplicates
     * included). Errors in one optional area visual only affect that viewer.
     */
    public static void sendWrittenSelfAndVisibleBestEffort(
            L1Character anchor,NativeWireWriter writer) {
        if(anchor==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            if(anchor instanceof L1PcInstance pc)
                shared=sendWrittenOptionalTo(shared,pc,writer);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(anchor))
                shared=sendWrittenOptionalTo(shared,viewer,writer);
        } catch(UnavailableOptionalSource invalid) {
            // Broken source must not be regenerated for every viewer.
        } catch(RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    /** Nested AOI visuals preserve original duplicates, filtering, and order.
     * Receiver/eligibility errors must not abort a chest visual for all PCs.
     */
    public static void sendWrittenNestedVisibleBestEffort(
            L1Object origin,NativeWireWriter writer) {
        if(origin==null||writer==null)return;
        final L1World world=L1World.getInstance();
        CAsyncSendConstBuffer shared=null;
        try {
            final List<L1PcInstance> anchors = world.getVisiblePlayer(origin);
            if (anchors.isEmpty()) return;
            final List<L1PcInstance> recipients = new ArrayList<>(anchors.size());
            for(L1PcInstance anchor:anchors) {
                if(anchor==null)continue;
                world.collectVisiblePlayers(anchor, -1, recipients);
                for(L1PcInstance recipient:recipients) {
                    try {
                        if(!CTLMFilter.sameWorldOther(anchor,recipient))continue;
                        shared=sendWrittenOptionalTo(shared,recipient,writer);
                    } catch(UnavailableOptionalSource invalid) {
                        return;
                    } catch(RuntimeException failure) {
                        logOptionalNoticeFailure(failure);
                    }
                }
            }
        } catch(RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    /** Optional team notice: identical recipient-major order and team filter. */
    public static void sendWrittenMapTeamBestEffort(
            int mapId,int teamId,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(mapId)) {
                if(pc==null)continue;
                try {
                    if(pc.get_current_combat_team_id()!=teamId)continue;
                    shared=sendWrittenOptionalTo(shared,pc,writer);
                } catch(UnavailableOptionalSource invalid) {
                    return;
                } catch(RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                }
            }
        } catch(RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    /** Literal recipient order, including duplicates and self: no AOI override. */
    public static void sendWrittenRecipients(
            Iterable<? extends L1PcInstance> recipients,NativeWireWriter writer) {
        if(recipients==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                final CUserSocket socket = eligibleBroadcastSocket(pc);
                if (socket == null) continue;
                if(shared==null) {
                    shared=new CAsyncSendConstBuffer(
                            writer,Integer.MIN_VALUE,Integer.MAX_VALUE);
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

    /** writeS2 exact MS949 byte count, with Native pooled direct output. */
    public static void writeMS949S2(ByteBuffer out,String msg) {
        final int lenPos=out.position();
        out.put((byte)0);
        writeMS949(out,msg);
        final int encoded=out.position()-lenPos-1;
        if(encoded>255)throw new IllegalArgumentException(
                "Native writeS2 encoded length "+encoded+" exceeds 255");
        out.put(lenPos,(byte)encoded);
    }

    /** Clan-wide visual/war notice: preserve online-member cursor ownership,
     * duplicates, and source sharing while isolating one member's SEND failure.
     * Invalid plaintext is attempted only once for the entire clan.
     */
    public static void sendWrittenClanBestEffort(
            L1Clan clan,NativeWireWriter writer) {
        if(clan==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try(var members=clan.createOnlineMembers()) {
            final var it=members.iterator();
            while(it.hasNext()) {
                final var member=it.next();
                if(member==null)continue;
                shared=sendWrittenOptionalTo(shared,member.player,writer);
            }
        } catch(UnavailableOptionalSource invalid) {
            // Already logged; invalid writer must not retry for each member.
        } catch(RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        } finally { closeOptional(shared); }
    }

    /** Original clan-member traversal and every online-member duplicate/order. */
    public static void sendWrittenClan(
            L1Clan clan,NativeWireWriter writer) {
        if(clan==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            try(var members=clan.createOnlineMembers()) {
                final var it=members.iterator();
                while(it.hasNext()) {
                    final var member = it.next();
                    if (member != null) shared = sendWrittenTo(shared, member.player, writer);
                }
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

    // One Native pool source across explicit L1Object recipients. Reuse the
    // phase76 combat formatter; no AOI/self filter or duplicate suppression.
    public static void sendExtendedObjects(Iterable<? extends L1Object> objects,
            int kind,int opcode,int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        if(objects==null)return;
        final boolean optional = kind != FORMAT_CHANGESHAPE;
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1Object object:objects) {
                if(!(object instanceof L1PcInstance pc))continue;
                final CUserSocket socket;
                try { socket=eligibleBroadcastSocket(pc); }
                catch(RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if(socket==null)continue;
                if(shared==null) {
                    shared=CAsyncSendConstBufferExceptSelf.forExtended(
                            socket,Integer.MIN_VALUE,kind,opcode,
                            p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
                    if (shared == null) {
                        if (!optional) throw new IllegalStateException("Shared packet source unavailable: " + kind);
                        continue;
                    }
                }
                try { sendVisual(socket, pc.getId(), shared, optional); }
                catch(RuntimeException failure) { logOptionalNoticeFailure(failure); }
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

    // One lazily assembled immutable source; self is sent first, then every
    // original AOI member (including duplicates). No ExceptSelf suppression.
    private static CAsyncSendConstBuffer sendWrittenTo(
            CAsyncSendConstBuffer shared,L1PcInstance pc,NativeWireWriter writer) {
        final CUserSocket socket = eligibleBroadcastSocket(pc);
        if (socket == null) return shared;
        if(shared==null) {
            // If the first recipient raises after Native allocation, the
            // caller cannot yet own this just-created source. Release it here
            // before rethrowing; subsequent shared sources remain caller-owned.
            final CAsyncSendConstBuffer first=new CAsyncSendConstBuffer(
                    writer,Integer.MIN_VALUE,Integer.MAX_VALUE);
            try {
                first.send(socket,pc.getId());
                return first;
            } catch(RuntimeException|Error failure) {
                try {first.close();}
                catch(RuntimeException|Error releaseFailure) {
                    if(releaseFailure!=failure)failure.addSuppressed(releaseFailure);
                }
                throw failure;
            }
        }
        shared.send(socket,pc.getId());
        return shared;
    }

    public static void sendWrittenSelfAndVisible(
            L1Character anchor,NativeWireWriter writer) {
        if(anchor==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            if(anchor instanceof L1PcInstance pc)
                shared=sendWrittenTo(shared,pc,writer);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(anchor))
                shared=sendWrittenTo(shared,viewer,writer);
        } finally {
            if(shared!=null)shared.close();
        }
    }

    // Ground item AOI: do not make the item 'known' until SEND was admitted.
    // Preserve the visible-list borrow, all duplicate entries and recipient FIFO.
    public static int sendWrittenGroundItem(
            L1ItemInstance item,
            boolean addKnownObject,NativeWireWriter writer) {
        if(item==null||writer==null)return 0;
        int viewers=0;
        CAsyncSendConstBuffer shared=null;
        final L1World world=L1World.getInstance();
        final int range=Config.PC_RECOGNIZE_RANGE;
        // L1's screen test is directional: user -> item, not item -> user.
        final List<L1PcInstance> visible=world.borrowVisiblePlayerFast(item,range<0?22:range);
        try {
            for(L1PcInstance pc:visible) {
                if(pc==null||pc.isLogoutStarted()||!pc.canSeeObject(item))continue;
                final int sightEpoch=pc.groundSightEpoch();
                // Do not send ground sprites before the client's map/self
                // initialization has completed or during a teleport reset.
                if((sightEpoch&1)==0)continue;
                final CUserSocket socket;
                try { socket=eligibleBroadcastSocket(pc); }
                catch(RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if(socket==null)continue;
                if(shared==null) {
                    shared=new CAsyncSendConstBuffer(
                            writer,item.getId(),Integer.MAX_VALUE);
                }
                final boolean admitted;
                if(pc.groundSightEpoch()!=sightEpoch)continue;
                try {
                    admitted = addKnownObject ? shared.sendBestEffort(socket, pc.getId())
                            : shared.send(socket, pc.getId());
                }
                catch(RuntimeException failure) {
                    // The writer is lazy: failure before source freeze is
                    // a broadcast-wide invalid packet, not a bad recipient.
                    if(!shared.hasFrozenSource())throw failure;
                    if(addKnownObject&&pc.groundSightEpoch()==sightEpoch)pc.markGroundSightRetry();
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if(admitted) {
                    if(addKnownObject&&pc.groundSightEpoch()==sightEpoch)pc.addKnownObject(item);
                    viewers++;
                } else if(addKnownObject&&pc.groundSightEpoch()==sightEpoch)
                    pc.markGroundSightRetry();
            }
        } finally {
            try {if(shared!=null)shared.close();}
            finally {world.releaseVisiblePlayerFast(visible);}
        }
        return viewers;
    }


    /** Native-pooled fanout to the original explicit L1Object sequence. */
    public static void sendWrittenObjectPlayers(
            Iterable<? extends L1Object> objects, NativeWireWriter writer) {
        if(objects==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1Object obj:objects)
                if(obj instanceof L1PcInstance pc)shared=sendWrittenTo(shared,pc,writer);
        } finally { if(shared!=null)shared.close(); }
    }

    /** Array traversal without an allocating adapter. */
    public static void sendWrittenArray(L1PcInstance[] members,NativeWireWriter writer) {
        if(members==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:members)shared=sendWrittenTo(shared,pc,writer);
        } finally { if(shared!=null)shared.close(); }
    }

    /** The first recipient list is drained before the second, including duplicates. */
    public static void sendWrittenGroups(
            Iterable<? extends L1PcInstance> first,
            Iterable<? extends L1PcInstance> second,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            if(first!=null)for(L1PcInstance pc:first)
                shared=sendWrittenTo(shared,pc,writer);
            if(second!=null)for(L1PcInstance pc:second)
                shared=sendWrittenTo(shared,pc,writer);
        } finally { if(shared!=null)shared.close(); }
    }

    public static void sendWrittenAllPlayable(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers()) {
                if(pc==null||pc.isPrivateShop()||!pc.is_world())continue;
                shared=sendWrittenTo(shared,pc,writer);
            }
        } finally { if(shared!=null)shared.close(); }
    }

    public static void sendWrittenAllGms(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers()) {
                if(pc!=null&&pc.isGm())shared=sendWrittenTo(shared,pc,writer);
            }
        } finally { if(shared!=null)shared.close(); }
    }

    /** Preserves original nested-visible iteration and repeated recipients. */
    public static void sendWrittenNestedVisible(L1Object origin,NativeWireWriter writer) {
        if(origin==null||writer==null)return;
        final L1World world=L1World.getInstance();
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance anchor:world.getVisiblePlayer(origin)) {
                if(anchor==null)continue;
                for(L1PcInstance recipient:world.getVisiblePlayer(anchor)) {
                    if(!CTLMFilter.sameWorldOther(anchor,recipient))continue;
                    shared=sendWrittenTo(shared,recipient,writer);
                }
            }
        } finally { if(shared!=null)shared.close(); }
    }

    /** Rare >16KiB area attack: freeze ONE immutable off-heap wire frame.
     * Source is copied into the existing direct CIO FIFO lease per socket;
     * no Java byte[] broadcaster, no worker, no duplicate-suppression.
     * This preserves self-first followed by exact visible-player traversal.
     */
    public static void sendWrittenLargeSelfAndVisible(
            L1Character anchor,
            int length,NativeWireWriter writer) {
        if(anchor==null||writer==null)return;
        if(length<=16384||length>0xFFFF)
            throw new IllegalArgumentException("large SC area frame length "+length);
        try (var shared = new CAsyncSendConstBuffer(writer, anchor.getId(), Integer.MAX_VALUE, length)) {
            if (anchor instanceof L1PcInstance pc) sendLargeTo(pc, shared);
            for (L1PcInstance viewer : L1World.getInstance().getVisiblePlayer(anchor)) sendLargeTo(viewer, shared);
        }
    }

    private static void sendLargeTo(L1PcInstance pc, CAsyncSendConstBuffer shared) {
        final CUserSocket socket;
        try { socket = eligibleBroadcastSocket(pc); }
        catch (RuntimeException failure) {
            logOptionalNoticeFailure(failure);
            return;
        }
        if (socket != null) sendLargeTo(socket, pc.getId(), shared);
    }

    private static void sendLargeTo(CUserSocket socket, int recipient, CAsyncSendConstBuffer shared) {
        try { shared.sendBestEffort(socket, recipient); }
        catch (RuntimeException failure) {
            // Only a frozen frame can safely be reused for the remaining recipients.
            if (!shared.hasFrozenSource()) throw failure;
            logOptionalNoticeFailure(failure);
        }
    }

    public static void sendWrittenLarge(
            L1Object anchor,int length,NativeWireWriter writer) {
        if(anchor==null||writer==null)return;
        if(length<=16384||length>0xFFFF)
            throw new IllegalArgumentException("large SC wire length "+length);
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> visible=world.borrowVisiblePlayerFast(anchor);
        try (var shared = new CAsyncSendConstBuffer(writer, anchor.getId(), Integer.MAX_VALUE, length)) {
            for(L1PcInstance pc:visible) {
                try {
                    if(!CTLMFilter.sameWorldOther(anchor,pc))continue;
                } catch(RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                sendLargeTo(pc, shared);
            }
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }

    /** World-wide public notices: isolate one socket's enqueue failure, not
     * a broken packet writer. All successful recipients retain their original
     * order and share a single frozen Native source. No alternate SEND route.
     */
    public static void sendWrittenAll(NativeWireWriter writer) {
        if (writer == null) return;
        try {
            sendWrittenRecipientsBestEffort(L1World.getInstance().getAllPlayers(), writer);
        } catch(RuntimeException failure) {
            logOptionalNoticeFailure(failure);
        }
    }

    /** Proto.sendRaw payload already has its opcode and trailer but no
     * 2-byte framing. Freeze it once, write straight into the Native slab.
     * Protocol-valid >16KiB payload uses existing direct FIFO.
     */
    public static CAsyncSendConstBuffer openRaw(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > CIOBufferPool.MAX_WIRE_BYTES - CIOBufferPool.HEADER_BYTES) return null;
        final byte[] snapshot=encoded.clone(); // protobuf encoder owns input
        final int wireLength = encoded.length + CIOBufferPool.HEADER_BYTES;
        return new CAsyncSendConstBuffer(
                b -> b.put(snapshot), Integer.MIN_VALUE, Integer.MAX_VALUE,
                wireLength > CIOBufferPool.SMALL_SEND_BYTES ? wireLength : 0);
    }

    /** Array fanout without an iterable adapter, one frame for all members. */
    public static void sendRawArray(L1PcInstance[] recipients,byte[] encoded) {
        if(recipients==null || recipients.length==0)return;
        try(var shared=openRaw(encoded)){
            if(shared==null)return;
            for(L1PcInstance pc:recipients)
                sendRecipient(shared,pc);
        }
    }

    public static void sendRawRecipients(Iterable<? extends L1PcInstance> recipients,
            byte[] encoded) {
        if(recipients==null)return;
        try(var shared=openRaw(encoded)) {
            if(shared==null)return;
            for(L1PcInstance pc:recipients)sendRecipient(shared,pc);
        }
    }

    public static void sendRawSelfAndVisible(L1PcInstance self,byte[] encoded) {
        if(self==null)return;
        try(var shared=openRaw(encoded)) {
            if(shared==null)return;
            sendRecipient(shared,self);
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayer(self))
                sendRecipient(shared,pc);
        }
    }

            /**
     * Send identical frames in recipient-major order. Previous behavior was
     * for each visible PC: send all display effects in input array order.
     * Team filtering, map iteration order, and socket FIFO are unchanged.
     * This method never serializes inside the recipient iteration.
     */
    public static void sendWrittenMapTeam(int mapId,int teamId,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try{
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(mapId)){
                if(pc==null||pc.get_current_combat_team_id()!=teamId)continue;
                shared=sendWrittenTo(shared,pc,writer);
            }
        }finally{if(shared!=null)shared.close();}
    }

    public static void sendWrittenMapTeamFrames(int mapId,int teamId,boolean teamOnly,
                                                 NativeWireWriter[] writers){
        if(writers==null||writers.length==0)return;
        final CAsyncSendConstBuffer[] shared=new CAsyncSendConstBuffer[writers.length];
        Throwable primaryFailure = null;
        try{
            for(int i=0;i<writers.length;i++){
                if(writers[i]==null)return;
                shared[i]=new CAsyncSendConstBuffer(writers[i],Integer.MIN_VALUE,Integer.MAX_VALUE);
            }
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(mapId)){
                final CUserSocket socket;
                try {
                    if(pc==null||(teamOnly&&pc.get_current_combat_team_id()!=teamId))continue;
                    socket=eligibleBroadcastSocket(pc);
                } catch(RuntimeException failure) {
                    logOptionalNoticeFailure(failure);
                    continue;
                }
                if(socket==null)continue;
                for(CAsyncSendConstBuffer source:shared) {
                    try {
                        if (!source.sendBestEffort(socket, pc.getId())) break;
                    }
                    catch(RuntimeException failure) {
                        // Invalid source is fatal for the entire effect set;
                        // a single failed recipient must not skip the next team member.
                        if(!source.hasFrozenSource())throw failure;
                        logOptionalNoticeFailure(failure);
                        break; // stop this recipient's remaining frames only
                    }
                }
            }
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            closeAllFrameSources(shared, primaryFailure);
        }
    }

    /** Always release every Native source; a failed release must not strand
     * the remaining frames or conceal an earlier SEND/encode failure. */
    private static void closeAllFrameSources(
            CAsyncSendConstBuffer[] sources, Throwable pending) {
        Throwable failure = pending;
        for (CAsyncSendConstBuffer source : sources) {
            if (source == null) continue;
            try { source.close(); }
            catch (RuntimeException | Error closeFailure) {
                if (failure == null) failure = closeFailure;
                else if (failure != closeFailure) failure.addSuppressed(closeFailure);
            }
        }
        if (pending == null && failure != null) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw (Error) failure;
        }
    }

            /** One recipient of an already opened native shared frame. Allows
     * side effects or other packets between fanout recipients without
     * changing per-client FIFO ordering or allocating new lists.
     */
    public static void sendRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc) {
        if (shared == null) return;
        final CUserSocket socket = eligibleBroadcastSocket(pc);
        if (socket != null) shared.send(socket, pc.getId());
    }

}
