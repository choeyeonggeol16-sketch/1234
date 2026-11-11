package l1j.server.CharacterAction.Magic;

import l1j.server.commons;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.ActionCodes.Loader.SpriteInformationLoader;
import l1j.server.CharacterAction.AbstractActionHandler;
import l1j.server.CharacterAction.Executor.CharacterActionExecutor;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.Templates.L1Skills;
import l1j.server.configure.Config;
import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_Poison;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.utils.SkillUtil;
import manager.SJManager;

public abstract class SkillActionHandler extends AbstractActionHandler implements PacketParser {
	protected int skillId;
	protected int tId;
	protected int tX;
	protected int tY;
	protected String message;

	public SkillActionHandler setSkillId(int skillId) {
		this.skillId = skillId;
		return this;
	}
	
	@Override
	public void doWork() {
		
		// 사용 유무 체크 
		if(!validation()) { 
			owner.setRegenState(L1PcInstance.STATE_NONE);
			return;
		}	
		// 메디테이션 체크 
		if(owner.hasSkillEffect(SkillId.메디테이션)) {
			owner.removeSkillEffect(SkillId.메디테이션);
		}		
		// 앱솔루트 해제 
		SkillUtil.cancelBarrier(owner);	
		// 현재 캐릭터 상태 hp/mp 변동 체크
		owner.setRegenState(L1PcInstance.STATE_ATTACK);
		handle();
	}
	
	@Override
	public void handle() { 

		SkillUse l1skilluse = new SkillUse();
		l1skilluse.handleCommands(owner, skillId, tId, tX, tY, message, 0, SkillUse.TYPE_NORMAL);
		l1skilluse = null;
	}
	
	@Override
	public boolean validation() {
		// 피시가 없을때
		if (owner == null) {
			return false;
		}
		// 피시가 텔레포트 상태거나 , 죽엇을때
		if(owner.getTeleport() || owner.isDead()) {
			return false;
		}
		// 스킬이 사용가능한 맵인지 체크 
		L1Map m = owner.getMap();
		if (!m.isUsableSkill()) {
			owner.sendPackets(new S_ServerMessage(563)); // 여기에서는 사용할 수 없습니다.
			return false;
		}
		// 스킬 배웠는지에 대한 체크 
		if (!owner.isSkillMastery(skillId)) {
			return false;
		}
		// 클라이언트 스킬 딜레이 체크 
		if (skill_delay()) { 
			return false;
		}		
		// 서버 측 스킬 딜레이 체크
		if (owner.isSkillDelay()) { 
			return false;
		}
		// 피시가 스킬 효과 상태일때
		if (commons.isNonAction(owner)) {
			return false;
		}
		return check_morph();
	}
	
	private boolean check_morph(){
		if(owner.isGm() || tId == owner.getId() || tId == 0) {
			return true;
		}
		int spr_id = owner.getCurrentSpriteId();
		if(spr_id != 12015 && spr_id != 5641 && 
				spr_id != 11685 && spr_id != 11620 && spr_id != 11621)
			return true;
		
		L1Object target = L1World.getInstance().findObject(tId);
		if(target == null || !(target instanceof L1PcInstance)) {
			return true;
		}
		return false;
	}
	
	public boolean skill_delay() {
		long currentMillis = System.currentTimeMillis();
		long current_value = owner.lastSpellUseMillis;

		// TODO 마법딜레이 측정시(모든 버프 해제하고 측정.) / 컨피그 확인설명
		if (Config.ServerInfo.MAGIC_SPEED_LOGG) {
			if (owner.isGm()) {
//				System.out.println(getInterval());
//				System.out.println(String.format("[디비에 입력할 딜레이 값] : %d", currentMillis - owner.lastCheckSpellUseMillis - getInterval()));
//				System.out.println(String.format("[딜레이 값 체크] : %d", currentMillis - owner.lastCheckSpellUseMillis));
//				System.out.println("스킬 아이디 : " + skillId);
			}
		}

		if (Config.ServerInfo.MAGIC_SPEED_CHECK) {
			if (!(skillId == SkillId.텔레포트 || skillId == SkillId.매스_텔레포트 || skillId == SkillId.텔레포트_투_마더)) {
				if (current_value > currentMillis) {
					if (++owner.lastSpellUsePending > Config.ServerInfo.MAGIC_SPEED_OVERCOUNT) {
						
						if (Config.ServerInfo.MAGIC_SPEED_CHECKTYPE == 1) {			//1. 3초간스턴
							owner.killSkillEffectTimer(SkillId.아이스_랜스);
							owner.sendPackets(new S_Poison(owner.getId(), 2));
							owner.broadcastPacket(new S_Poison(owner.getId(), 2));
							owner.sendPackets(new S_Paralysis(S_Paralysis.TYPE_FREEZE, true));
							owner.setSkillEffect(SkillId.아이스_랜스, 3 * 1000);// 
						} else if (Config.ServerInfo.MAGIC_SPEED_CHECKTYPE == 2) {	//2. 감옥이동
							owner.teleport(32736, 32799, (short) 34, owner.getHeading(),169, false, false); 
						} else if (Config.ServerInfo.MAGIC_SPEED_CHECKTYPE == 3) {	//3. 팅기게
							owner.sendPackets(new S_Disconnect()); 
						} 
						  //L1Teleport.teleport(owner, owner._Insert_speed_over_loc[0], owner._Insert_speed_over_loc[1], (short) owner._Insert_speed_over_loc[2], owner.getHeading(), false, true);
					//	}  
						System.out.println(String.format("[딜레이+][노딜/반딜] 스킬 딜레이 오버(렉체크):[%s] 스킬아이디 : [%d], 오차 : [%d] [감시바람]", owner.getName(), skillId, current_value - currentMillis));
						
						SJManager.getInstance().bugAppend(owner.getName(), " 노딜버그시도  번호 " + skillId);
						
						return true;
					}
				} else {
					owner.lastSpellUsePending = 0;
				}
			}
		}

		long delay = SpriteInformationLoader.getInstance().getUseSpellInterval(owner, skillId);
		delay = getInterval() + delay;

		if (Config.ServerInfo.MAGIC_SPEED_LOGG) {
//			if (owner.isGm()) {
//				System.out.println("[최종 딜레이값] : " + delay);
//			}
		}
		owner.lastCheckSpellUseMillis = currentMillis;
		owner.lastSpellUseMillis = currentMillis + delay;
		return false;
	}
	
	@Override
	public void dispose(){
		message = null;
		super.dispose();
	}
	
	@Override
	public int getRegistIndex(){
		return CharacterActionExecutor.ACTION_IDX_SPELL;
	}
	
	@Override
	public long getInterval() {
		L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
		return owner.getCurrentSpriteInterval(skill.getTarget().equals("buff") || skill.getTarget().equals("attack") ? EActionCodes.spell_dir : EActionCodes.spell_nodir);
	}
	
	@Override
	public abstract void parse(L1PcInstance owner, ClientBasePacket pck);
	public abstract SkillActionHandler copy();
	
	
}
