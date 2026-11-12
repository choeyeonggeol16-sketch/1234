package l1j.server.MJTemplate.PacketHelper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.MJTemplate.Builder.MJServerPacketBuilder;
import l1j.server.server.model.network.core.sc.packet.SC_BuilderPacket;
import l1j.server.server.Opcodes;

public class MJPacketFactory {
	public static final int MSPF_IDX_OFFTIME 	= 6;
	
	private static ConcurrentHashMap<Integer, SC_BuilderPacket> _packs = new ConcurrentHashMap<Integer, SC_BuilderPacket>(32);
	
	public static SC_BuilderPacket create(int idx) throws Exception{
		SC_BuilderPacket pck = _packs.get(idx);
		if(pck != null)
			return pck;
		
		MJServerPacketBuilder builder;
		switch(idx){
		case MSPF_IDX_OFFTIME:
			builder	= new MJServerPacketBuilder(4)
			.addC(Opcodes.S_EVENT)
			.addC(0x48);
			break;
		default:
			throw new NullPointerException(String.format("invalid Packet type %d", idx));
		}
		
		pck = builder.build();
		builder.dispose();
		builder = null;
		_packs.put(idx, pck);
		return pck;
	}
	
	@SuppressWarnings("resource")
	public static SC_BuilderPacket createTime(long l){
		SC_BuilderPacket 				pck 	= null;
		MJServerPacketBuilder 	builder = null;
		try {
			builder = new MJServerPacketBuilder(8)
			.addC(Opcodes.S_EVENT)
			.addC(0x47)
			.addH((short)l);
			pck = builder.build();
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			if(builder != null){
				builder.dispose();
				builder = null;
			}
		}
		return pck;
	}

    public static boolean sendOffTime(l1j.server.server.model.Instance.L1PcInstance pc) {
        if (pc == null) return false;
        final l1j.server.server.model.network.core.CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final l1j.server.server.model.network.core.CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final l1j.server.server.model.network.core.CIOFrame frame = connection.beginDirectFrame(8);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final l1j.server.server.model.network.buffer.WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x48);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendTime(l1j.server.server.model.Instance.L1PcInstance pc, long seconds) {
        if (pc == null) return false;
        final l1j.server.server.model.network.core.CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final l1j.server.server.model.network.core.CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final l1j.server.server.model.network.core.CIOFrame frame = connection.beginDirectFrame(8);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final l1j.server.server.model.network.buffer.WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x47);
            buffer.writeH((int) seconds);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
