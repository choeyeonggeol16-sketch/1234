package l1j.server.CharacterAction.Magic;

import l1j.server.Templates.L1Skills;
import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;

public class DirectionActionHandler extends SkillActionHandler {
	@Override
	public void parse(L1PcInstance owner, ClientBasePacket pck) {
		this.owner = owner;
		if (pck.isRead(4))
			tId = pck.readD();
		if (pck.isRead(2))
			tX = pck.readH();
		if (pck.isRead(2))
			tY = pck.readH();
	}

	@Override
	public boolean validation() {
		if (!super.validation()) {
			return false;
		}
		L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
		double range = skill.getRanged();
		if (range < 0) {
			range = 15D;
		}
		range += 4.5D;

		L1Object target = L1World.getInstance().findObject(tId);
		if (target != null) {
			if (target instanceof L1Character) {
				if (owner.getMapId() != target.getMapId() || owner.getLocation().getLineDistance(target.getLocation()) > range) {
					return false;
				}
			}

			if ((owner.getMapId() == 612 || owner.getMapId() == 254 || owner.getMapId() == 1930) && target instanceof L1PcInstance) {
				owner.sendPackets(new S_ServerMessage(563)); // 여기에서는 사용할 수 없습니다.
				return false;
			}
		}
		return true;
	}

	@Override
	public SkillActionHandler copy() {
		return new DirectionActionHandler();
	}
}
