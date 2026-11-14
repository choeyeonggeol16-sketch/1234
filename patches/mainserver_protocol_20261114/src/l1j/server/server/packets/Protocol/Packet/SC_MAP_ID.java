package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.MJRaidSystem.MJRaidSpace;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

public final class SC_MAP_ID extends ProtoPacket {

	private final int _mapId;
	private final boolean _underwater;

	public static SC_MAP_ID create(int mapId, boolean underwater) {
		return new SC_MAP_ID(mapId, underwater);
	}

	public SC_MAP_ID(int mapId, boolean underwater) {
		_mapId = resolveMapId(mapId);
		_underwater = underwater;
	}

	@Override
	public int msgId() {
		return ProtoMsg.SC_MAP_ID;
	}

	@Override
	protected void body(ProtoWriter w) {
		w.int32(1, _mapId)
		 .int32(2, 0)
		 .bool(3, _underwater)
		 .int32(4, 0)
		 .int32(5, 0)
		 .int32(6, 0);
	}

	private static int resolveMapId(int mapId) {
		if (mapId > 6000 && mapId < 6499)
			return 1005;		// 안타

		if (mapId > 6501 && mapId < 6999)
			return 1011;		// 파푸

		if (mapId > 1017 && mapId < 1023)
			return 1017;

		if (mapId > 9000 && mapId < 9099)
			return 9000;		// 말섬

		if (mapId > 2101 && mapId < 2151)
			return 2101;		// 얼녀

		if (mapId > 2151 && mapId < 2201)
			return 2151;		// 데몬

		if (mapId > 2699 && mapId < 2798)
			return 2699;

		if (mapId > 2600 && mapId < 2698)
			return 2600;

		if (mapId >= 7783 && mapId <= 7793)
			return 7783;		// 클라우디아

		if (mapId >= 12152 && mapId <= 12252)
			return 12152;		// 클라우디아 공성장

		if (mapId >= 12257 && mapId <= 12357)
			return 12257;		// 클라우디아 데스나이트

		final int idenMap = MJRaidSpace.getInstance().getIdenMap(mapId);
		return MJInstanceSpace.getInstance().getIdenMap(idenMap);
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _mapId, boolean _underwater) {
        w.int32(1, _mapId).int32(2, 0).bool(3, _underwater).int32(4, 0).int32(5, 0).int32(6, 0);
    }

    public static boolean sendFields(L1PcInstance recipient, int _mapId, boolean _underwater) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _mapId, _underwater);
    }

    public static boolean sendFields(CIOClient recipient, int _mapId, boolean _underwater) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_MAP_ID, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _mapId, _underwater);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _mapId, boolean _underwater) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _mapId, _underwater);
        return writer.finishWithTrailer(ProtoMsg.SC_MAP_ID);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int mapId, boolean underwater) {
        int proto34_mapId = 0;
        boolean proto34_underwater = false;

            proto34_mapId = resolveMapId(mapId);
            proto34_underwater = underwater;

        return sendFields(protoRecipient, proto34_mapId, proto34_underwater);
    }

    public static boolean sendCreate(CIOClient protoRecipient, int mapId, boolean underwater) {
        return sendCtor(protoRecipient, mapId, underwater);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, int mapId, boolean underwater) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), mapId, underwater);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int mapId, boolean underwater) { return sendCtor(protoRecipient, mapId, underwater); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int mapId, boolean underwater) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), mapId, underwater); }

}
