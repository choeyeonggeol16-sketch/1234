package l1j.server.server.clientpackets;

import l1j.server.CharacterAction.SpellActionHandlerFactory;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.server.GameClient;
import l1j.server.server.model.Instance.L1PcInstance;

public class C_UseSkill extends ClientBasePacket {
	public C_UseSkill(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);
		L1PcInstance pc = client.getActiveChar();
	
		int skillId = (readC() * 8) + readC() + 1;
		PacketParser parser = SpellActionHandlerFactory.create(skillId);		
		parser.parse(pc, this);
		parser.doWork();
	}
}
