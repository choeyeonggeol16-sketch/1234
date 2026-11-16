package l1j.server.server.model.network.core.broadcast;

import java.util.List;
import java.util.function.Predicate;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

/** One immutable encoded frame, shared across every eligible recipient.
 * Packet-specific old serializers are never invoked during broadcast.
 */
public final class CSharedBroadcast {
    private CSharedBroadcast() {}
    // L1-specific formatting selectors for the existing Native Assemble bridge.
    // These are RKern tags, NOT NC opcode/ABI identifiers.
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
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> recipients=fast
                ? world.borrowVisiblePlayerFast(anchor)
                : world.getVisiblePlayer(anchor);
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                if(!CTLMFilter.sameWorldOther(anchor,pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(shared==null) {
                    if(socket==null||!socket.isClientConnected())continue;
                    shared=CAsyncSendConstBufferExceptSelf.forExtended(
                            socket,anchor.getId(),kind,opcode,
                            p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
                    if(shared==null)throw new IllegalStateException(
                            "Native pooled combat source unavailable: "+kind);
                }
                shared.send(socket,pc.getId());
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
     * One NC-style pooled plaintext, lazily assembled only after first valid
     * recipient. Keep the exact original fast/radius/filter AOI traversal,
     * ExceptSelf, recipient order and socket FIFO; no alternate SEND path.
     */
    public static void sendFormatted(
            L1Object anchor, int format, int opcode,
            int p0, int p1, int p2, int p3,
            boolean fast, int radius, Predicate<L1PcInstance> filter) {
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
                if (!CTLMFilter.sameWorldOther(anchor, pc)
                        || filter != null && !filter.test(pc)) continue;
                final CIOClient client = pc.getNetConnection();
                if (client == null || !client.isConnected()) continue;
                final var socket = client.getConnection();
                if (shared == null) {
                    if (socket == null || !socket.isClientConnected()) continue;
                    shared = CAsyncSendConstBufferExceptSelf.forFormatted(
                            socket, anchor.getId(), format, opcode,
                            p0, p1, p2, p3);
                    if (shared == null) throw new IllegalStateException(
                            "pooled S-packet source unavailable: " + format);
                }
                shared.send(socket, pc.getId());
            }
        } finally {
            try {
                if (shared != null) shared.close();
            } finally {
                if (borrowed) world.releaseVisiblePlayerFast(recipients);
            }
        }
    }


    /**
     * Movement-only fast AOI fanout. Defer the owned immutable frame until the
     * first eligible connected socket; no packet/clone/wrapper if no viewer.
     * Use the exact existing borrowed AOI list, filter and recipient order.
     * No lambda or per-viewer serializer.
     */
    /**
     * NC-style action S-packet: one immutable 8-byte plaintext per event,
     * shared by all eligible sockets, each socket encrypts in its own FIFO.
     * A 0-recipient event creates no packet array/clone/shared wrapper.
     */
    public static void sendAction(L1Object anchor, int objectId, int actionId) {
        if (anchor == null) return;
        final L1World world = L1World.getInstance();
        final List<L1PcInstance> recipients = world.borrowVisiblePlayerFast(anchor);
        CAsyncSendConstBufferExceptSelf shared = null;
        try {
            for (L1PcInstance pc : recipients) {
                if (!CTLMFilter.sameWorldOther(anchor, pc)) continue;
                final CIOClient client = pc.getNetConnection();
                if (client == null || !client.isConnected()) continue;
                final var socket = client.getConnection();
                if (shared == null) {
                    if (socket == null || !socket.isClientConnected()) continue;
                    shared = CAsyncSendConstBufferExceptSelf.forAction(
                            socket, objectId, l1j.server.server.Opcodes.S_ACTION,
                            objectId, actionId);
                    if (shared == null) throw new IllegalStateException("S_ACTION shared Native source unavailable (allocation or shutdown)");
                }
                shared.send(socket, pc.getId());
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
                if (!CTLMFilter.sameWorldOther(anchor, pc)) continue;
                final CIOClient client = pc.getNetConnection();
                if (client == null || !client.isConnected()) continue;
                final var socket = client.getConnection();
                if (shared == null) {
                    if (socket == null || !socket.isClientConnected()) continue;
                    shared = CAsyncSendConstBufferExceptSelf.forMove(
                            socket, anchor.getId(),
                            l1j.server.server.Opcodes.S_MOVE_OBJECT,
                            objectId, x, y, heading);
                    if (shared == null) throw new IllegalStateException("S_MOVE shared Native source unavailable (allocation or shutdown)");
                }
                shared.send(socket, pc.getId());
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
     * Encodes once directly into an NC-style Native pooled CIOBufferEx.
     * The encoder is invoked only after a connected viewer is eligible.
     * A failed source acquire/encode/commit never silently drops a broadcast.
     */
    @FunctionalInterface
    public interface NativeWireWriter {
        void write(java.nio.ByteBuffer output);
        /** A positive full framed length is mandatory for >16KiB Proto data. */
        default int wireLength(){return -1;}
    }

    private static final java.nio.charset.Charset MS949 =
            java.nio.charset.Charset.forName("MS949");

    /** Exact MS949 replacement behavior of legacy String.getBytes(MS949). */
    public static void writeMS949(java.nio.ByteBuffer out,String text) {
        final var encoder=MS949.newEncoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
        var result=encoder.encode(java.nio.CharBuffer.wrap(text==null?"":text),out,true);
        if(result.isOverflow())throw new java.nio.BufferOverflowException();
        if(result.isError())throw new IllegalArgumentException("MS949 encode error");
        result=encoder.flush(out);
        if(result.isOverflow())throw new java.nio.BufferOverflowException();
        if(result.isError())throw new IllegalArgumentException("MS949 flush error");
    }

    public static void sendWritten(L1Object anchor,NativeWireWriter writer,boolean fast,
            int radius,Predicate<L1PcInstance> filter) {
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
                if(!CTLMFilter.sameWorldOther(anchor,pc)
                        ||filter!=null&&!filter.test(pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(shared==null) {
                    if(socket==null||!socket.isClientConnected())continue;
                    shared=CAsyncSendConstBufferExceptSelf.forWritten(
                            socket,anchor.getId(),writer);
                    if(shared==null)throw new IllegalStateException("Native SC writable source unavailable");
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            try {
                if(shared!=null)shared.close();
            } finally {
                if(borrowed)world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

    /** Literal recipient order, including duplicates and self: no AOI override. */
    public static void sendWrittenRecipients(
            Iterable<? extends L1PcInstance> recipients,NativeWireWriter writer) {
        if(recipients==null||writer==null)return;
        if(writer.wireLength()>16384) {
            for(L1PcInstance pc:recipients) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                sendLargeDirectFrame(client.getConnection(),writer.wireLength(),writer);
            }
            return;
        }
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    final CAsyncSendConstBuffer.Source source=
                            CAsyncSendConstBuffer.openWrittenSource(socket,writer);
                    if(source==null)throw new IllegalStateException("Native recipient source unavailable");
                    try {
                        shared=new CAsyncSendConstBuffer(source,Integer.MIN_VALUE,Integer.MAX_VALUE);
                    } catch(RuntimeException|Error failure) {
                        source.release();
                        throw failure;
                    }
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

    /** writeS2 exact MS949 byte count, with Native pooled direct output. */
    public static void writeMS949S2(java.nio.ByteBuffer out,String msg) {
        final int lenPos=out.position();
        out.put((byte)0);
        writeMS949(out,msg);
        final int encoded=out.position()-lenPos-1;
        if(encoded>255)throw new IllegalArgumentException(
                "Native writeS2 encoded length "+encoded+" exceeds 255");
        out.put(lenPos,(byte)encoded);
    }

    /** Original clan-member traversal and every online-member duplicate/order. */
    public static void sendWrittenClan(
            l1j.server.server.model.L1Clan clan,NativeWireWriter writer) {
        if(clan==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            try(var members=clan.createOnlineMembers()) {
                final var it=members.iterator();
                while(it.hasNext()) {
                final var member=it.next();
                final L1PcInstance pc=member.player;
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    final CAsyncSendConstBuffer.Source source=
                            CAsyncSendConstBuffer.openWrittenSource(socket,writer);
                    if(source==null)throw new IllegalStateException(
                            "Native clan immutable source unavailable");
                    try {
                        shared=new CAsyncSendConstBuffer(source,Integer.MIN_VALUE,Integer.MAX_VALUE);
                    } catch(RuntimeException|Error failure) {
                        source.release();
                        throw failure;
                    }
                }
                shared.send(socket,pc.getId());
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
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1Object object:objects) {
                if(!(object instanceof L1PcInstance pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    shared=CAsyncSendConstBufferExceptSelf.forExtended(
                            socket,Integer.MIN_VALUE,kind,opcode,
                            p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
                    if(shared==null)throw new IllegalStateException(
                            "Native explicit combat source unavailable");
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

    // One lazily assembled immutable source; self is sent first, then every
    // original AOI member (including duplicates). No ExceptSelf suppression.
    private static CAsyncSendConstBuffer sendWrittenTo(
            CAsyncSendConstBuffer shared,L1PcInstance pc,NativeWireWriter writer) {
        if(pc==null)return shared;
        final CIOClient client=pc.getNetConnection();
        if(client==null||!client.isConnected())return shared;
        final var socket=client.getConnection();
        if(socket==null||!socket.isClientConnected())return shared;
        final int oversized=writer.wireLength();
        if(oversized>16384) {
            sendLargeDirectFrame(socket,oversized,writer);
            return shared;
        }
        if(shared==null) {
            final CAsyncSendConstBuffer.Source source=
                    CAsyncSendConstBuffer.openWrittenSource(socket,writer);
            if(source==null)throw new IllegalStateException(
                    "Native self-and-visible source unavailable");
            try {
                shared=new CAsyncSendConstBuffer(source,Integer.MIN_VALUE,Integer.MAX_VALUE);
            } catch(RuntimeException|Error failure) {
                source.release();
                throw failure;
            }
        }
        shared.send(socket,pc.getId());
        return shared;
    }

    public static void sendWrittenSelfAndVisible(
            l1j.server.server.model.L1Character anchor,NativeWireWriter writer) {
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
            l1j.server.server.model.Instance.L1ItemInstance item,
            boolean addKnownObject,NativeWireWriter writer) {
        if(item==null||writer==null)return 0;
        int viewers=0;
        CAsyncSendConstBuffer shared=null;
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> visible=world.borrowVisiblePlayerFast(item);
        try {
            for(L1PcInstance pc:visible) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    final CAsyncSendConstBuffer.Source source=
                            CAsyncSendConstBuffer.openWrittenSource(socket,writer);
                    if(source==null)throw new IllegalStateException(
                            "Native ground item source unavailable");
                    try {
                        shared=new CAsyncSendConstBuffer(source,item.getId(),Integer.MAX_VALUE);
                    } catch(RuntimeException|Error failure) {
                        source.release();
                        throw failure;
                    }
                }
                if(shared.send(socket,pc.getId())) {
                    if(addKnownObject)pc.addKnownObject(item);
                    viewers++;
                }
            }
        } finally {
            try {if(shared!=null)shared.close();}
            finally {world.releaseVisiblePlayerFast(visible);}
        }
        return viewers;
    }

    /** Rare >16KiB range-skill frame.
     * Native ConstBuffer slabs are deliberately fixed at 16KiB. For a
     * protocol-valid larger attack use the SAME existing CIO direct-frame
     * writer/FIFO; never reinstate a legacy byte[] broadcaster or worker.
     * The 2-byte frame header remains socket-owned.
     */
    static boolean sendLargeDirectFrame(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int length,NativeWireWriter writer) {
        if(socket==null||!socket.isClientConnected()
                ||length<=16384||length>0xFFFF)return false;
        final var frame=socket.beginDirectFrame(length);
        if(frame==null)return false;
        boolean committed=false;
        try {
            final var target=frame.buffer();
            final java.nio.ByteBuffer output=target.getBuffer()
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            output.position(target.baseOffset()+target.position());
            writer.write(output);
            if(output.position()!=target.baseOffset()+length)
                throw new IllegalStateException("large SC attack frame length mismatch");
            target.position(length);
            committed=socket.commitDirectFrame(frame);
            return committed;
        } finally {
            if(!committed)socket.abortDirectFrame(frame);
        }
    }

    public static void sendWrittenLarge(
            L1Object anchor,int length,NativeWireWriter writer) {
        if(anchor==null||writer==null)return;
        if(length<=16384||length>0xFFFF)
            throw new IllegalArgumentException("large SC wire length "+length);
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> visible=world.borrowVisiblePlayerFast(anchor);
        try {
            for(L1PcInstance pc:visible) {
                if(!CTLMFilter.sameWorldOther(anchor,pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                sendLargeDirectFrame(client.getConnection(),length,writer);
            }
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }

    public static void sendWrittenAll(NativeWireWriter writer) {
        sendWrittenRecipients(L1World.getInstance().getAllPlayers(),writer);
    }

    public static void send(L1Object anchor, byte[] wire, boolean fast, int radius,
            Predicate<L1PcInstance> filter) {
        if (anchor == null || wire == null) return;
        final L1World world=L1World.getInstance();
        final boolean borrowed=fast && radius < 0;
        final List<L1PcInstance> recipients=borrowed
                ? world.borrowVisiblePlayerFast(anchor)
                : radius < 0 ? world.getVisiblePlayer(anchor)
                : world.getVisiblePlayer(anchor, radius);
        try (var shared=new CAsyncSendConstBufferExceptSelf(wire,anchor.getId(),Integer.MAX_VALUE)) {
            for (L1PcInstance pc:recipients) {
                if (!CTLMFilter.sameWorldOther(anchor,pc)
                        || filter != null && !filter.test(pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        } finally {
            if(borrowed)world.releaseVisiblePlayerFast(recipients);
        }
    }


    /**
     * Broadcast an already framed immutable packet to a specified recipient
     * collection. Retains its exact inclusion/ordering semantics (no AOI or
     * self-exclusion override). The packet-specific serializer never runs in
     * the recipient loop.
     */

    /** One immutable GREEN_MESSAGE or other framed packet for the existing
     * L1Object registry value traversal. Only PCs receive the frame.
     * No AOI selection replacement, sorting, deduplication or copy.
     */
    public static void sendObjectPlayers(
            Iterable<? extends L1Object> objects, byte[] wire) {
        if(objects==null || wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1Object obj:objects){
                if(obj instanceof L1PcInstance)
                    sendWrittenRecipient(shared,(L1PcInstance)obj);
            }
        }
    }

    public static void sendRecipients(Iterable<? extends L1PcInstance> recipients,
            byte[] wire) {
        if (recipients == null || wire == null) return;
        try (var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1PcInstance pc:recipients) {
                if (pc == null) continue;
                final CIOClient client=pc.getNetConnection();
                if (client != null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }

    /** Proto.sendRaw uses an unframed already-encoded payload. Convert once
     * to its 2-byte little-endian wire length, then broadcast shared bytes.
     */
    private static byte[] frameRaw(byte[] encoded) {
        if(encoded==null || encoded.length==0 || encoded.length>65533)return null;
        final int size=encoded.length+2;
        final byte[] frame=new byte[size];
        frame[0]=(byte)size;
        frame[1]=(byte)(size>>>8);
        System.arraycopy(encoded,0,frame,2,encoded.length);
        return frame;
    }


    /** Borrow one immutable raw Proto payload as a framed shared SEND.
     * Input is exactly the argument accepted by Proto.sendRaw:
     * its opcode/message ID/trailer are included, header is not.
     */
    public static CAsyncSendConstBuffer openRaw(byte[] encoded) {
        final byte[] wire=frameRaw(encoded);
        return wire==null?null:new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE);
    }

    /** Array fanout without an iterable adapter, one frame for all members. */
    public static void sendRawArray(L1PcInstance[] recipients,byte[] encoded) {
        if(recipients==null || recipients.length==0)return;
        try(var shared=openRaw(encoded)){
            if(shared==null)return;
            for(L1PcInstance pc:recipients)
                sendWrittenRecipient(shared,pc);
        }
    }

    public static void sendRawRecipients(Iterable<? extends L1PcInstance> recipients,
            byte[] encoded) {
        final byte[] framed=frameRaw(encoded);
        if(framed!=null)sendRecipients(recipients,framed);
    }


    /**
     * Send a single cached protobuf frame to the triggering PC first, then to
     * every visible PC in the original world traversal order. This preserves
     * the exact recipients and the prior own-send-first order, while removing
     * repeated ServerPacket.writeImpl() calls. No per-recipient encoder.
     */
    public static void sendRawSelfAndVisible(L1PcInstance self,byte[] encoded) {
        if(self==null)return;
        final byte[] wire=frameRaw(encoded);
        if(wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            final CIOClient own=self.getNetConnection();
            if(own!=null && own.isConnected())shared.send(own.getConnection(),self.getId());
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayer(self)) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }


    /**
     * One identical frame for online GM recipients. The original world
     * traversal order and per-connection FIFO are preserved, and non-GMs
     * receive no packet. No temporary recipient collection is allocated.
     */
    public static void sendAllGms(byte[] wire) {
        if (wire == null) return;
        try (var shared = new CAsyncSendConstBuffer(
                wire, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc == null || !pc.isGm()) continue;
                final CIOClient client = pc.getNetConnection();
                if (client != null && client.isConnected()) {
                    shared.send(client.getConnection(), pc.getId());
                }
            }
        }
    }


    /** An identical system-message frame for one combat team on a single map.
     * Retain the original getVisiblePlayers iteration and team visibility.
     */
    public static void sendMapTeam(int mapId, int teamId, byte[] wire) {
        if (wire == null) return;
        try (var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1PcInstance pc : L1World.getInstance().getVisiblePlayers(mapId)) {
                if (pc==null || pc.get_current_combat_team_id()!=teamId) continue;
                final CIOClient client=pc.getNetConnection();
                if (client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }

    /**
     * Send identical frames in recipient-major order. Previous behavior was
     * for each visible PC: send all display effects in input array order.
     * Team filtering, map iteration order, and socket FIFO are unchanged.
     * This method never serializes inside the recipient iteration.
     */
    public static void sendMapTeamFrames(
            int mapId, int teamId, boolean teamOnly, byte[][] wires) {
        if (wires==null || wires.length==0) return;
        final CAsyncSendConstBuffer[] shared=
                new CAsyncSendConstBuffer[wires.length];
        int opened=0;
        try {
            for(int i=0;i<wires.length;i++) {
                if(wires[i]==null) return;
                shared[i]=new CAsyncSendConstBuffer(
                        wires[i],Integer.MIN_VALUE,Integer.MAX_VALUE);
                opened++;
            }
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(mapId)) {
                if(pc==null || (teamOnly && pc.get_current_combat_team_id()!=teamId))
                    continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null || !client.isConnected()) continue;
                final var socket=client.getConnection();
                if(socket==null || !socket.isClientConnected()) continue;
                for(int i=0;i<opened;i++)
                    shared[i].send(socket,pc.getId());
            }
        } finally {
            for(int i=0;i<opened;i++) shared[i].close();
        }
    }


    /** Global screen effects: keep the original live-world and private-shop
     * predicate, walking the same all-players view and retaining duplicate
     * recipients without a temporary selection array.
     */
    public static void sendAllPlayable(byte[] wire) {
        if(wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers()) {
                if(pc.isPrivateShop() || !pc.is_world())continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null || !client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket!=null && socket.isClientConnected())
                    shared.send(socket,pc.getId());
            }
        }
    }

    /**
     * One frame for two existing recipient lists. Crucially preserve the
     * original first-list then second-list order, including duplicate members,
     * without constructing a temporary combined list.
     */
    public static void sendGroups(Iterable<? extends L1PcInstance> first,
            Iterable<? extends L1PcInstance> second, byte[] wire) {
        if(wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            sendGroup(first,shared);
            sendGroup(second,shared);
        }
    }

    /** One recipient of an already opened native shared frame. Allows
     * side effects or other packets between fanout recipients without
     * changing per-client FIFO ordering or allocating new lists.
     */
    public static void sendWrittenRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc) {
        if(shared==null || pc==null)return;
        final CIOClient client=pc.getNetConnection();
        if(client==null || !client.isConnected())return;
        final var socket=client.getConnection();
        if(socket!=null && socket.isClientConnected())
            shared.send(socket,pc.getId());
    }

    /** Array event recipients, no temporary iterable or per-recipient encoder.
     * Original order and duplicate members are maintained.
     */
    public static void sendArray(L1PcInstance[] members,byte[] wire) {
        if(members==null || wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:members) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null || !client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket!=null && socket.isClientConnected())
                    shared.send(socket,pc.getId());
            }
        }
    }

    private static void sendGroup(Iterable<? extends L1PcInstance> members,
            CAsyncSendConstBuffer shared) {
        if(members==null)return;
        for(L1PcInstance pc:members) {
            if(pc==null)continue;
            final CIOClient client=pc.getNetConnection();
            if(client==null || !client.isConnected())continue;
            final var socket=client.getConnection();
            if(socket!=null && socket.isClientConnected())
                shared.send(socket,pc.getId());
        }
    }


    /** Preserve the original nested AOI semantics used by treasure-chest
     * cloud effects: for each chest viewer, rebroadcast around that viewer.
     * Do not coalesce, deduplicate, or change the iteration sequence.
     * The same immutable frame is nevertheless serialized only once.
     */
    public static void sendNestedVisible(L1Object origin,byte[] wire) {
        if(origin==null || wire==null)return;
        final L1World world=L1World.getInstance();
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance anchor:world.getVisiblePlayer(origin)) {
                if(anchor==null)continue;
                for(L1PcInstance recipient:world.getVisiblePlayer(anchor)) {
                    if(!CTLMFilter.sameWorldOther(anchor,recipient))continue;
                    final CIOClient client=recipient.getNetConnection();
                    if(client!=null && client.isConnected())
                        shared.send(client.getConnection(),recipient.getId());
                }
            }
        }
    }


    // Phase82: the source payload is composed directly into the borrowed
    // Native CIOBufferEx. Every writer starts immediately AFTER the 2-byte
    // transport length; sealPooledWritableConstBuffer fills that header.
    public static NativeWireWriter greenWriter(String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)84).put((byte)2);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter systemWriter(String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_MESSAGE)
                        .put((byte)0x09);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter displayEffectWriter(int effect) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                     .put((byte)0x53).putInt(effect).putShort((short)0);
    }
    public static NativeWireWriter lfcTrapWriter(int id,int gfx) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EFFECT)
                     .putInt(id).putShort((short)gfx);
    }
    public static NativeWireWriter effectLocationWriter(int x,int y,int gfx) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EFFECT_LOC)
                     .putShort((short)x).putShort((short)y)
                     .putShort((short)gfx).put((byte)0);
    }
    public static NativeWireWriter scoreWriter(String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)84).put((byte)4);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter colorNoticeWriter(int color,String msg) {
        if(color!=51 && color!=61)
            throw new IllegalArgumentException("invalid color notice");
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)84).put((byte)2).putShort((short)26204)
                        .put((byte)color);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter roundWriter(int stage) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)156).putInt(stage).putInt(12);
    }
    public static NativeWireWriter mapTimerWriter(int time) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)153).putInt(time);
    }
    public static NativeWireWriter miniGameTimeWriter(int time) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)71).putShort((short)time);
    }
    public static NativeWireWriter hadinDisplayWriter(int value) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)83).put((byte)value);
    }
    public static NativeWireWriter miniGameEndWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)70).put((byte)147).put((byte)92).put((byte)151)
                .put((byte)220).put((byte)42).put((byte)74);
    }
    public static NativeWireWriter dragonRaidBuffWriter(int seconds) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)179).put((byte)1).put((byte)0x27).put((byte)0x0e)
                .putInt(seconds).putShort((short)0x63ef);
    }
    public static NativeWireWriter emblemStatusWriter(int status) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                         .put((byte)173).put((byte)1);
            if(status==0||status==1)b.put((byte)status);
            b.putInt(0); };
    }
    public static NativeWireWriter townLeaderWriter(String leader) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)23);
            writeMS949(b,leader);b.put((byte)0); };
    }
    public static NativeWireWriter chatWriter(int opcode,int type,int id,
                                             String message,boolean withId) {
        return b -> { b.put((byte)opcode).put((byte)type);
            if(withId)b.putInt(id);
            writeMS949(b,message);b.put((byte)0); };
    }
    public static NativeWireWriter serverMessageWriter(int type) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_MESSAGE_CODE)
                     .putShort((short)type).put((byte)0);
    }
    public static NativeWireWriter serverMessageWriter(int type,String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_MESSAGE_CODE)
                        .putShort((short)type).put((byte)(msg==null?0:1));
            if(msg!=null){writeMS949(b,msg);b.put((byte)0);} };
    }
    public static NativeWireWriter serverMessageWriter(int type,String m1,String m2,String m3) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_MESSAGE_CODE)
                .putShort((short)type)
                .put((byte)((m1==null?0:1)+(m2==null?0:1)+(m3==null?0:1)));
            if(m1!=null){writeMS949(b,m1);b.put((byte)0);}
            if(m2!=null){writeMS949(b,m2);b.put((byte)0);}
            if(m3!=null){writeMS949(b,m3);b.put((byte)0);} };
    }

    private static CAsyncSendConstBuffer sendWrittenListTo(
            CAsyncSendConstBuffer shared,Iterable<? extends L1PcInstance> list,
            NativeWireWriter writer) {
        if(list==null)return shared;
        for(L1PcInstance pc:list)shared=sendWrittenTo(shared,pc,writer);
        return shared;
    }
    public static void sendWrittenArray(L1PcInstance[] pcs,NativeWireWriter writer) {
        if(pcs==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try { for(L1PcInstance pc:pcs)shared=sendWrittenTo(shared,pc,writer); }
        finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenObjectPlayers(Iterable<? extends L1Object> objects,
                                                NativeWireWriter writer) {
        if(objects==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1Object obj:objects)
                if(obj instanceof L1PcInstance pc)
                    shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenGroups(
            Iterable<? extends L1PcInstance> first,
            Iterable<? extends L1PcInstance> second,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            shared=sendWrittenListTo(shared,first,writer);
            shared=sendWrittenListTo(shared,second,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenAllPlayable(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers())
                if(pc!=null&&!pc.isPrivateShop()&&pc.is_world())
                    shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenAllGms(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers())
                if(pc!=null&&pc.isGm())shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenMapTeam(int map,int team,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(map))
                if(pc!=null&&pc.get_current_combat_team_id()==team)
                    shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenNestedVisible(L1Object origin,NativeWireWriter writer) {
        if(origin==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        final L1World world=L1World.getInstance();
        try {
            for(L1PcInstance anchor:world.getVisiblePlayer(origin)) {
                if(anchor==null)continue;
                for(L1PcInstance recipient:world.getVisiblePlayer(anchor)) {
                    if(CTLMFilter.sameWorldOther(anchor,recipient))
                        shared=sendWrittenTo(shared,recipient,writer);
                }
            }
        } finally {if(shared!=null)shared.close();}
    }


    private static final byte[] GM_ICON_RGB_NATIVE=
            new l1j.server.MJTemplate.MJSimpleRgb(249,68,253).get_bytes();

    public static NativeWireWriter changeAttrWriter(int x,int y,
            boolean width,boolean closed) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_CHANGE_ATTR)
                .putShort((short)x).putShort((short)y)
                .put((byte)(width?1:0)).put((byte)(closed?1:0));
    }
    public static NativeWireWriter iconIndexWriter(int index,
            l1j.server.MJTemplate.MJSimpleRgb color,int surface,int duration) {
        // Match the existing SC_IconMessage.sharedIndexWire varint wire.
        final byte[] rgb=color==null?null:color.get_bytes().clone();
        return b -> {
            b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
             .putShort((short)0x67).put((byte)0x08);
            nativeVarint(b,(long)surface*2);
            b.put((byte)0x10);nativeVarint(b,(long)index*2);
            b.put((byte)0x1a);
            if(rgb!=null)b.put(rgb);
            b.put((byte)0x20);nativeVarint(b,duration);
            b.putShort((short)0);
        };
    }
    private static void nativeVarint(java.nio.ByteBuffer b,long value){
        if(value<0L)value&=0xffffffffL;
        while((value & ~0x7fL)!=0L){
            b.put((byte)((value&0x7fL)|0x80L));
            value>>>=7;
        }
        b.put((byte)value);
    }

    public static NativeWireWriter moveObjectWriter(int id,int x,int y,int heading) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_MOVE_OBJECT)
                .putInt(id).putShort((short)x).putShort((short)y)
                .put((byte)heading).put((byte)129).putInt(0);
    }
    public static NativeWireWriter actionWriter(int id,int action) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_ACTION)
                .putInt(id).put((byte)action);
    }
    public static NativeWireWriter removeObjectWriter(int id) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_REMOVE_OBJECT)
                .putInt(id).putShort((short)0);
    }
    public static NativeWireWriter skillSoundWriter(int id,int gfx) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EFFECT)
                .putInt(id).putShort((short)gfx)
                .putShort((short)0).putInt(0);
    }
    public static NativeWireWriter actionUiFlagWriter(byte[] flag) {
        if(flag!=null&&flag.length>65528)return null;
        final byte[] snapshot=flag==null?null:flag.clone();
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                          .putShort((short)339);
            if(snapshot!=null)b.put(snapshot);
            b.putShort((short)0); };
    }
    public static NativeWireWriter targetChatWriter(String target,int type,String chat) {
        final String value="["+(target==null?"":target)+"] "+(chat==null?"":chat);
        if(32L+4L*value.length()>65535L)return null;
        return chatWriter(l1j.server.server.Opcodes.S_MESSAGE,type,0,value,false);
    }
    public static NativeWireWriter askWriter(int type,String msg) {
        return askIndexedWriter(0,type,msg);
    }
    public static NativeWireWriter askIndexedWriter(int idx,int type,String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_ASK)
                       .putShort((short)0).putInt(idx).putShort((short)type);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter gmIconWriter(String message) {
        final String text=message==null?"":message;
        final int max=text.length();
        if(max>127&&text.getBytes(MS949).length>255)
            throw new IllegalStateException("writeS2 overflow in macro icon");
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                         .putShort((short)0x40)
                         .put((byte)8).put((byte)56).put((byte)0x12);
            writeMS949S2(b,text);
            b.put((byte)0x1a).put(GM_ICON_RGB_NATIVE);
            b.put((byte)0x20).put((byte)60).putShort((short)0);
        };
    }
    public static NativeWireWriter macroWriter(int type,String msg,String sender) {
        final String text=msg==null?"":msg;
        if(text.length()>127&&text.getBytes(MS949).length>255)
            throw new IllegalStateException("macro text S2 overflow");
        if(sender!=null&&sender.length()>127&&sender.getBytes(MS949).length>255)
            throw new IllegalStateException("macro sender S2 overflow");
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                         .putShort((short)0x0204)
                         .put((byte)0x08).put((byte)0)
                         .put((byte)0x10).put((byte)type).put((byte)0x1a);
            writeMS949S2(b,text);
            if(sender!=null){b.put((byte)0x2a);writeMS949S2(b,sender);}
            b.put((byte)0x30).put((byte)0).putShort((short)0);
        };
    }
    public static NativeWireWriter showPkWriter(byte[] killer,byte[] victim) {
        if(killer==null||victim==null||killer.length>255||victim.length>255)
            return null;
        final byte[] a=killer.clone(),z=victim.clone();
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                         .putShort((short)0x247).put((byte)0x08)
                         .put((byte)0).put((byte)0x12).put((byte)a.length).put(a)
                         .put((byte)0x18).put((byte)0).put((byte)0x22)
                         .put((byte)z.length).put(z).put((byte)0x28)
                         .put((byte)1).putShort((short)0);
        };
    }
    public static NativeWireWriter clanMentWriter(boolean on,String name) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_PLEDGE_WATCH)
                         .put((byte)(on?32:31)).putShort((short)269);
            writeMS949(b,name);b.put((byte)0); };
    }
    public static NativeWireWriter clanListWriter(int count,
            java.util.List<String> source) {
        final String[] snapshot;
        if(source==null)snapshot=null;
        else synchronized(source){snapshot=source.toArray(new String[0]);}
        return b -> {
            b.put((byte)l1j.server.server.Opcodes.S_PLEDGE_WATCH)
             .putShort((short)2).putInt(count);
            if(snapshot!=null)for(String name:snapshot){
                if(name==null)continue;
                writeMS949(b,name);b.put((byte)0);
            }
        };
    }
    public static NativeWireWriter castleMasterWriter(int type,int id) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_CASTLE_OWNER)
                .put((byte)type).putInt(id);
    }
    public static NativeWireWriter npcChatWriter(
            l1j.server.server.model.Instance.L1NpcInstance npc,String msg,int type) {
        if(npc==null||msg==null)return null;
        final String formatted;
        final String name=npc.getName();
        switch(type){
        case 0:case 4:
            formatted=npc.getNpcId()==8500200?msg:name+": "+msg;break;
        case 2:
            final int templateId=npc.getNpcTemplate()!=null
                 ? npc.getNpcTemplate().get_npcId():0;
            formatted=templateId==70518||templateId==70506
                 ? name+": "+msg:"<"+name+"> "+msg;break;
        case 3: formatted="["+name+"] "+msg;break;
        default:return null;
        }
        final int id=npc.getId(),color=type==4?21:type;
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_SAY_CODE)
                         .put((byte)color).putInt(id);
            writeMS949(b,formatted);b.put((byte)0); };
    }
    public static NativeWireWriter partyColorWriter(int id,int color) {
        if(color<0||color>2)return null;
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x6c).putInt(id).putShort((short)color);
    }
    public static NativeWireWriter gameStartWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x40).put((byte)0x05).putInt(0).put((byte)0);
    }
    public static NativeWireWriter gameRapWriter(int value) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x43).putShort((short)4).putShort((short)value);
    }
    public static NativeWireWriter racingWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x41).put((byte)100).putInt(0).put((byte)0);
    }
    public static NativeWireWriter gameEndWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x46).put((byte)147).put((byte)92).put((byte)151)
                .put((byte)220).put((byte)42).put((byte)74);
    }
    public static NativeWireWriter gameOverWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x45).put((byte)10).put((byte)109).put((byte)85)
                .put((byte)208).put((byte)2).put((byte)220);
    }
    public static NativeWireWriter trueTargetWriter(int id,boolean active) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0xc2).putInt(id).put((byte)0x4f).put((byte)0x33)
                .putShort((short)0).putInt(active?1:0).putShort((short)0);
    }

    /** Exactly one immutable raw Proto payload, without the former new
     * Java framed array. The existing encoded data is snapshotted once;
     * payloads >16KiB use existing per-socket CIO direct FIFO (no old queue).
     */
    public static NativeWireWriter rawWriter(byte[] encoded) {
        if(encoded==null||encoded.length==0||encoded.length>65533)return null;
        final byte[] snapshot=encoded.clone();
        return new NativeWireWriter() {
            @Override public void write(java.nio.ByteBuffer out) {out.put(snapshot);}
            @Override public int wireLength() {return snapshot.length+2;}
        };
    }
    public static CAsyncSendConstBuffer openWrittenRaw(byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        return writer==null?null:
                new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE);
    }
    public static void sendWrittenRawArray(L1PcInstance[] recipients,byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        if(writer!=null)sendWrittenArray(recipients,writer);
    }
    public static void sendWrittenRawRecipients(
            Iterable<? extends L1PcInstance> recipients,byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        if(writer!=null)sendWrittenRecipients(recipients,writer);
    }
    public static void sendWrittenRawSelfAndVisible(L1PcInstance self,byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        if(writer!=null)sendWrittenSelfAndVisible(self,writer);
    }

    /** Recipient-major order: for each viewer, enqueue every original effect,
     * preserving pending packet order and duplicates. Native writers stay
     * lazy until a valid socket accepts its first frame.
     */
    public static void sendWrittenMapTeamEffects(
            int map,int team,boolean teamOnly,int[] effects) {
        if(effects==null||effects.length==0)return;
        final CAsyncSendConstBuffer[] frames=new CAsyncSendConstBuffer[effects.length];
        int opened=0;
        try {
            for(int i=0;i<effects.length;i++) {
                frames[i]=new CAsyncSendConstBuffer(
                        displayEffectWriter(effects[i]),
                        Integer.MIN_VALUE,Integer.MAX_VALUE);
                opened++;
            }
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(map)) {
                if(pc==null||(teamOnly&&pc.get_current_combat_team_id()!=team))
                    continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                for(int i=0;i<opened;i++)frames[i].send(socket,pc.getId());
            }
        } finally {
            for(int i=0;i<opened;i++)frames[i].close();
        }
    }

    /** Global broadcast includes the sender and uses the normal socket FIFO. */
    public static void sendAll(byte[] wire) {
        if(wire==null)return;
        try (var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1PcInstance pc:L1World.getInstance().getAllPlayers()) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }
}
