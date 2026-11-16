package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_AreaAttackEffect {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private SC_AreaAttackEffect() {}

    public static void sendNoDirAndBroadcast(L1Character caster, ArrayList<L1Character> targets,
                                              int gfx, int action) {
        if(caster==null)return;
        final int id=caster.getId(),x=caster.getX(),y=caster.getY();
        final int heading=caster.getHeading();
        final int count=targets==null?0:targets.size();
        final int[] ids=new int[count];
        for(int i=0;i<count;i++) {
            final L1Character target=targets.get(i);
            if(target!=null)ids[i]=target.getId();
        }
        final int seq=SEQUENCE.incrementAndGet();
        final long length=26L+6L*ids.length;
        if(length>0xffffL)return;
        // The 16-KiB Native pool is fixed. Large multi-target frames retain
        // the existing explicit large-frame socket FIFO, not another SC
        // broadcast serializer. This case needs >=2727 targets at once.
        if(length>16384L) {
            final byte[] wire=sharedWire(id,x,y,heading,seq,gfx,action,ids);
            if(wire==null)return;
            final byte[] payload=java.util.Arrays.copyOfRange(wire,2,wire.length);
            if(caster instanceof L1PcInstance)
                sendLarge((L1PcInstance)caster,wire.length,payload);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(caster))
                sendLarge(viewer,wire.length,payload);
            return;
        }
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenSelfAndVisible(caster,
                b -> writeNativeArea(b,id,x,y,heading,seq,gfx,action,ids));
    }

    /** Original SC_AreaAttackEffect::send WriteBuffer byte layout. One buffer
     * per attack; per-socket encryption and SEND FIFO remain native-owned.
     */
    private static void writeNativeArea(java.nio.ByteBuffer b,
            int id,int x,int y,int heading,int seq,int gfx,int action,int[] ids) {
        b.put((byte)Opcodes.S_ATTACK_MANY).put((byte)action).putInt(id);
        b.putShort((short)x).putShort((short)y).put((byte)heading);
        b.putInt(seq).putShort((short)gfx).put((byte)0)
         .putShort((short)0).putShort((short)ids.length);
        for(int targetId:ids)
            b.putInt(targetId).put((byte)0x20)
             .put((byte)ActionCodes.ACTION_Damage);
        b.putShort((short)0);
    }

    public static byte[] sharedWire(int id,int x,int y,int heading,int seq,int gfx,
                                    int action,int[] ids) {
        if(ids==null)return null;
        final long length=26L+6L*ids.length;
        if(length>0xffff)return null;
        final int size=(int)length;
        final byte[] wire=new byte[size];
        put16(wire,0,size);
        wire[2]=(byte)Opcodes.S_ATTACK_MANY;
        wire[3]=(byte)action;
        put32(wire,4,id);
        put16(wire,8,x);
        put16(wire,10,y);
        wire[12]=(byte)heading;
        put32(wire,13,seq);
        put16(wire,17,gfx);
        // 19: byte 0; 20-21: short 0
        put16(wire,22,ids.length);
        int at=24;
        for(int targetId:ids) {
            put32(wire,at,targetId);at+=4;
            wire[at++]=0x20;
            wire[at++]=(byte)ActionCodes.ACTION_Damage;
        }
        // at..at+1: terminal short 0
        return wire;
    }

    private static void put16(byte[] wire,int at,int value) {
        wire[at]=(byte)value;
        wire[at+1]=(byte)(value>>>8);
    }
    private static void put32(byte[] wire,int at,int value) {
        wire[at]=(byte)value;
        wire[at+1]=(byte)(value>>>8);
        wire[at+2]=(byte)(value>>>16);
        wire[at+3]=(byte)(value>>>24);
    }
    /** Full 65,535-byte game frames use the existing large direct FIFO lease.
     * The encoded body is shared across all recipients; only the mandatory
     * per-socket buffer copy is performed, and the original encoder is gone.
     */
    private static boolean sendLarge(L1PcInstance pc,int length,byte[] payload) {
        if(pc==null)return false;
        final CIOClient client=pc.getNetConnection();
        if(client==null || !client.isConnected())return false;
        final CUserSocket connection=client.getConnection();
        if(connection==null || !connection.isClientConnected())return false;
        final CIOFrame frame=connection.beginDirectFrame(length);
        if(frame==null)return false;
        boolean committed=false;
        try {
            frame.buffer().writeBytes(payload);
            committed=connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if(!committed)connection.abortDirectFrame(frame);
        }
    }



    
}
