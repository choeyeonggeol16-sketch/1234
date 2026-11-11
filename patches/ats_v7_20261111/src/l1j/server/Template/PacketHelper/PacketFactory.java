package l1j.server.Template.PacketHelper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.Template.Builder.ServerPacketBuilder;
import l1j.server.server.Opcodes.Opcodes;

public class PacketFactory {
	public static final int MSPF_IDX_OFFTIME = 6;

	private static ConcurrentHashMap<Integer, S_BuilderPacket> _packs = new ConcurrentHashMap<Integer, S_BuilderPacket>(
			32);

	public static S_BuilderPacket create(int idx) throws Exception {
		S_BuilderPacket pck = _packs.get(idx);
		if (pck != null)
			return pck;

		ServerPacketBuilder builder;
		switch (idx) {
		case MSPF_IDX_OFFTIME:
			builder = new ServerPacketBuilder(4).addC(
					Opcodes.S_OPCODE_PACKETBOX).addC(0x48);
			break;
		default:
			throw new NullPointerException(String.format(
					"invalid Packet type %d", idx));
		}

		pck = builder.build();
		builder.dispose();
		builder = null;
		_packs.put(idx, pck);
		return pck;
	}
	public static S_BuilderPacket createTime(long l) {
		S_BuilderPacket pck = null;
		ServerPacketBuilder builder = null;
		try {
			builder = new ServerPacketBuilder(8)
					.addC(Opcodes.S_OPCODE_PACKETBOX).addC(0x47)
					.addH((short) l);
			pck = builder.build();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (builder != null) {
				builder.dispose();
				builder = null;
			}
		}
		return pck;
	}
}
