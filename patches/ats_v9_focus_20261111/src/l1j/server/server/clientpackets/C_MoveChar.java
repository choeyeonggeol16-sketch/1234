package l1j.server.server.clientpackets;

import l1j.server.CharacterAction.WalkActionHandlerFactory;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.server.GameClient;
import l1j.server.server.model.Instance.L1PcInstance;

public class C_MoveChar extends ClientBasePacket {
	public C_MoveChar(byte decrypt[], GameClient client) throws Exception {
		super(decrypt);
		
		L1PcInstance pc = client.getActiveChar();
		
		PacketParser parser = WalkActionHandlerFactory.create();
		parser.parse(pc, this);
		parser.doWork();
	}
}