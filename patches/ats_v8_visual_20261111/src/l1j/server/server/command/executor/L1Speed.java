package l1j.server.server.command.executor;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Liquor;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillBrave;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1Speed implements L1CommandExecutor {
	

	private L1Speed() {	}
	
	public static L1CommandExecutor getInstance() {
		return new L1Speed();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			int time = 3600 * 1000;
			int objectId = pc.getId();
			
			if (pc.hasSkillEffect(SkillId.상태_1단_가속)
					|| pc.hasSkillEffect(SkillId.헤이스트)
					|| pc.hasSkillEffect(SkillId.그레이터_헤이스트) 
					|| pc.hasSkillEffect(SkillId.슬로우)
					|| pc.hasSkillEffect(SkillId.매스_슬로우)
					|| pc.hasSkillEffect(SkillId.인탱글)) {
				pc.killSkillEffectTimer(SkillId.상태_1단_가속);
				pc.killSkillEffectTimer(SkillId.헤이스트);
				pc.killSkillEffectTimer(SkillId.그레이터_헤이스트);
				pc.killSkillEffectTimer(SkillId.매스_슬로우);
				pc.killSkillEffectTimer(SkillId.인탱글);
				pc.killSkillEffectTimer(SkillId.슬로우);
				S_SkillHaste haste = S_SkillHaste.get(pc.getId(), 0, 0);
				pc.sendPackets(haste);
				pc.broadcastPacket(haste, true);
				pc.setMoveSpeed(0);
			}
			
			if (pc.hasSkillEffect(SkillId.윈드_워크)
					|| pc.hasSkillEffect(SkillId.홀리_워크)
					|| pc.hasSkillEffect(SkillId.무빙_악셀레이션) 
					|| pc.hasSkillEffect(SkillId.상태_요정_2단_가속) 
					|| pc.hasSkillEffect(SkillId.상태_2단_가속)) {
				pc.killSkillEffectTimer(SkillId.윈드_워크);
				pc.killSkillEffectTimer(SkillId.홀리_워크);
				pc.killSkillEffectTimer(SkillId.무빙_악셀레이션);
				pc.killSkillEffectTimer(SkillId.상태_요정_2단_가속);
				pc.killSkillEffectTimer(SkillId.상태_2단_가속);
				S_SkillBrave sss = S_SkillBrave.get(pc.getId(), 0, 0);
				pc.sendPackets(sss);
				pc.broadcastPacket(sss, true);
				pc.setBraveSpeed(0);

			}			
			if (pc.hasSkillEffect(SkillId.상태_드래곤_진주_3단계)) {
				pc.killSkillEffectTimer(SkillId.상태_드래곤_진주_3단계);
				pc.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, 0, 0));
				pc.broadcastPacket(new S_Liquor(pc.getId(), 0));
				pc.sendPackets(new S_Liquor(pc.getId(), 0));
			}
			S_SkillBrave ssss = S_SkillBrave.get(objectId, 1, 9999);
			pc.sendPackets(ssss);
			pc.broadcastPacket(ssss, true);
			S_SkillSound ss = S_SkillSound.get(objectId, 751);
			pc.sendPackets(ss);
			pc.broadcastPacket(ss);
			pc.setSkillEffect(SkillId.상태_2단_가속, time);
			pc.setBraveSpeed(1);
			S_SkillHaste haste = S_SkillHaste.get(objectId, 1, 9999);
			pc.sendPackets(haste);
			pc.broadcastPacket(haste, true);
			S_SkillSound ss1 = S_SkillSound.get(objectId, 191);
			pc.sendPackets(ss1);
			pc.broadcastPacket(ss1);
			pc.setSkillEffect(SkillId.상태_1단_가속, time);
			pc.setMoveSpeed(1);
			
			
			int timea = 600 * 1000;
			int stime = ((timea / 1000) / 4) - 2;
			
			pc.setSkillEffect(SkillId.상태_드래곤_진주_3단계, timea);
			pc.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, stime, 8));
			pc.sendPackets(new S_Liquor(pc.getId(), 8));
			pc.broadcastPacket(new S_Liquor(pc.getId(), 8));
			S_SkillSound ss2 = S_SkillSound.get(objectId, 197);
			pc.sendPackets(ss2);
			pc.broadcastPacket(ss2);
			pc.sendPackets(new S_ServerMessage(1065));
			
		} catch (Exception e) {
			e.printStackTrace();
			pc.sendPackets(new S_SystemMessage(".속도 커멘드 에러"));
		}
	}
}
