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
                    sendRecipient(shared,(L1PcInstance)obj);
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
                sendRecipient(shared,pc);
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
    public static void sendRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc) {
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
