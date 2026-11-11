package l1j.server.CharacterAction.Magic;

import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.model.Instance.L1PcInstance;

public class SummonMonsterActionHandler extends SkillActionHandler{
	@Override
	public void parse(L1PcInstance owner, ClientBasePacket pck) {
		this.owner = owner;
		tX = pck.readC();
	}
	
	@Override
	public SkillActionHandler copy() {
		return new SummonMonsterActionHandler();
	}
}
