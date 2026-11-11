package l1j.server.server.clientpackets;

import l1j.server.CharacterAction.AttackActionHandlerFactory;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.server.GameClient;
import l1j.server.server.model.Instance.L1PcInstance;

public class C_Attack extends ClientBasePacket {

	public C_Attack(byte[] decrypt, GameClient client) {
		super(decrypt);

		L1PcInstance pc = client.getActiveChar();
		
		PacketParser parser = AttackActionHandlerFactory.create();
		parser.parse(pc, this);
		parser.doWork();
	}
}
