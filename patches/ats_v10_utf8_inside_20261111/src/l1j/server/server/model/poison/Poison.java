package l1j.server.server.model.poison;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ServerMessage;

public abstract class Poison {
	public static int POISON_DAMAGE = 1;
	public static int POISON_SILENCE = 2;
	public static int POISON_PARALYSIS = 4;
	private int _poisonType;

	protected static boolean isValidTarget(L1Character cha, int poisonType) {
		// 타겟 체크
		if (cha == null) {
			return false;
		}
		// 같은 종류의 독일때  
		if (cha.getPoison() != null && cha.getPoison().getPoisonType() == poisonType) {
			return false;
		}
		// 타겟이 피시가 아닐때
		if (!(cha instanceof L1PcInstance)) {
			return true;
		}
		// 스킬 체크
		if (cha.hasSkillEffect(SkillId.아이스_랜스) || cha.hasSkillEffect(SkillId.어스_바인드)
				|| cha.hasSkillEffect(SkillId.앱솔루트_배리어) || cha.hasSkillEffect(SkillId.상태_동결)) {
			return false;
		}
		// 독 아이템에 관련된 방어구 체크
		L1PcInstance pc = (L1PcInstance) cha;		
		if (pc.toArmorEquipped().isPoisonRegistArmor() || pc.hasSkillEffect(SkillId.베놈_레지스트)) {
			return false;
		}
		return true;
	}

	protected static void sendMessageIfPlayer(L1Character cha, int msgId) {
		if (!(cha instanceof L1PcInstance)) {
			return;
		}
		L1PcInstance player = (L1PcInstance) cha;
		player.sendPackets(new S_ServerMessage(msgId), true);
	}
	
	public int getPoisonType() {
		return this._poisonType;
	}

	public void setPoisonType(int poisonType) {
		this._poisonType = poisonType;
	}

	public abstract int getEffectId();

	public abstract void cure();
}
