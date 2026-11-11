package l1j.server.server.command.executor;

import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Invis;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_WorldObject;

public class GmInvisible implements L1CommandExecutor {

	private GmInvisible() {
	}
	public static L1CommandExecutor getInstance() {
		return new GmInvisible();
	}
	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		// TODO 자동 생성된 메소드 스텁
		try {
			if (!pc.hasSkillEffect(SkillId.인비지블리티)) {
				pc.setGmInvis(true);
				pc.sendPackets(new S_Invis(pc.getId(), 1));
				Broadcaster.broadcastPacket(pc, new S_Invis(pc.getId(), 1));
				Broadcaster.broadcastPacket(pc, S_RemoveObject.get(pc));
				pc.setSkillEffect(SkillId.인비지블리티, 0);
				pc.sendPackets(new S_SystemMessage("투명 상태가 되었습니다."), true);
			} else {
				pc.setGmInvis(false);
				pc.killSkillEffectTimer(SkillId.인비지블리티);
				pc.sendPackets(new S_Invis(pc.getId(), 0));
				for (L1PcInstance pc2 : L1World.getInstance().getVisiblePlayer(pc)) {
					pc2.sendPackets(S_WorldObject.out(pc));
				}
				pc.sendPackets(new S_SystemMessage("투명 상태를 해제했습니다. "), true);
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(cmdName + " 커멘드 에러"), true);
		}		
	}

}
