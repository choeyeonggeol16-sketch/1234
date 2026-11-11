package l1j.server.CharacterAction.Magic;

import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ServerMessage;

public class RunNCallClanActionHandler extends SkillActionHandler{
	@Override
	public void parse(L1PcInstance owner, ClientBasePacket pck) {
		this.owner = owner;
		message = pck.readS();
	}
	
	@Override
	public boolean validation() {
		
		if(!super.validation()) {
			return false;
		}
		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < message.length(); i++) {
			if(message.charAt(i) == '['){
				break;
			}
			sb.append(message.charAt(i));
		}

		L1PcInstance target = L1World.getInstance().getPlayer(sb.toString());

		if(target == null){
			owner.sendPackets(new S_ServerMessage(73, message));
			return false;
		}
		
		if(owner.getClanid() != target.getClanid()){
			owner.sendPackets(new S_ServerMessage(414));
			return false;
		}
		
		tId = target.getId();
		if (skillId == SkillId.ÄÝ_Å¬·£) {
			int callClanId = owner.getCallClanId();
			if (callClanId == 0 || callClanId != tId) {
				owner.setCallClanId(tId);
				owner.setCallClanHeading(owner.getHeading());
			}
		}
		return true;
	}
	
	@Override
	public SkillActionHandler copy() {
		return new RunNCallClanActionHandler();
	}
}
