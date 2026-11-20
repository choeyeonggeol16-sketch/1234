package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

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
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendWrittenLargeSelfAndVisible(caster,(int)length,
                            b -> writeNativeArea(b,id,x,y,heading,seq,gfx,action,ids));
            return;
        }
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenSelfAndVisibleBestEffort(caster,
                b -> writeNativeArea(b,id,x,y,heading,seq,gfx,action,ids));
    }

    /** Original SC_AreaAttackEffect::send CPacketWriteBuffer byte layout. One buffer
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


    


    
}
