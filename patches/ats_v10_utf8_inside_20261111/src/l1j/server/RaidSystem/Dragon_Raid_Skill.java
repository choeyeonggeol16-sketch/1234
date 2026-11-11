package l1j.server.RaidSystem;

import java.util.ArrayList;
import java.util.Random;

import l1j.server.commons;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.configure.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;
import l1j.server.server.utils.SkillUtil;

public abstract class Dragon_Raid_Skill {

	public static final int BSRTYPE_ONCE	= 1;	// 한 대상만을
	public static final int BSRTYPE_LINE	= 2;	// 한 라인을
	public static final int BSRTYPE_FRONT	= 4;	// 전방
	public static final int BSRTYPE_RECT	= 8;	// 정 사각형
	public static final int BSRTYPE_SCREEN	= 16;	// 화면 내 전체.

	protected static Random	_rnd = new Random(System.nanoTime());
	protected int			_actid; 
	protected int			_type;
	protected int			_range;
	protected int			_dmgMin;
	protected int			_dmgMax;
	protected int			_effectId;
	protected int			_ratio;
	protected boolean		_isMagic;


	public void setActionId(int actid) {
		_actid 	= actid;
	}
	public void setType(int type) {
		_type	= type;
	}
	public void setRange(int range) {
		_range 	= range;
	}
	public void setDamageMin(int dmg) {
		_dmgMin = dmg;
	}
	public void setDamageMax(int dmg) {
		_dmgMax = dmg;
	}
	public void setEffectId(int effectId) {
		_effectId = effectId;
	}
	public void setRatio(int raito) {
		_ratio = raito;
	}
	public void setIsMagic(boolean b) {
		_isMagic = b;
	}

	public void sleepAction(L1NpcInstance own, int actionId) throws Exception{
		Thread.sleep(own.getCurrentSpriteInterval(EActionCodes.fromInt(actionId)));
	}

	public void sleep(long l) throws Exception{
		Thread.sleep(l);
	}

	public abstract void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs);
	public abstract void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs);

	protected boolean isRange(L1NpcInstance own, L1PcInstance pc) {
		if(own.getMapId() != pc.getMapId()) {
			return false;
		}
		if((_type & BSRTYPE_ONCE) > 0) {
			return isDistance(own.getX(), own.getY(), pc.getX(), pc.getY());
		}
		else if((_type & BSRTYPE_LINE) > 0) {
			return isLine(own, pc);
		}
		else if((_type & BSRTYPE_FRONT) > 0) {
			return isInFront(own, pc);
		}
		else if((_type & BSRTYPE_RECT) > 0) { 
			int div = _range / 2;
			return isInRectangle(own.getX() - div, own.getY() - div, own.getX() + div, own.getY() + div, pc.getLocation());
		}
		else if((_type & BSRTYPE_SCREEN) > 0) {
			return isInScreen(own.getLocation(), pc.getLocation());
		}	
		else {
			return false;
		}

	}

	protected boolean isInRectangle(int left, int top, int right, int bottom, L1Location tLoc){
		int x 	= tLoc.getX();
		int y	= tLoc.getY();
		return (left <= x && top <= y && right >= x && bottom >= y); 
	}

	protected boolean isInFront(L1Character own, L1Character tar) {
		if(!commons.isWideAroundDirection(own, tar)) {
			return false;
		}
		return isDistance(own.getX(), own.getY(), tar.getX(), tar.getY());
	}

	protected boolean isInScreen(L1Location oloc, L1Location tloc){
		return oloc.isInScreen(tloc);
	}

	protected boolean isLine(L1Character own, L1Character tar){
		return own.glanceCheck(tar.getX(), tar.getY());
	}

	protected boolean isDistance(int ox, int oy, int tx, int ty){
		return commons.getDistance(ox, oy, tx, ty) <= _range;
	}

	// 일반 공격 용
	protected boolean isNormalSkillSet(L1NpcInstance own, L1PcInstance pc) {
		if(pc == null || pc.isDead() || !isRange(own, pc)) {
			return false;		
		}
		// 스킬 체크
		if(commons.isLock(pc)) {
			return false;	
		}
		return true;
	}
	// 독 구름 스킬 용
	protected boolean isPoisonCloudSkillSet(L1NpcInstance own, L1PcInstance pc) {
		if(pc == null || pc.isDead() || !isRange(own, pc)) {
			return false;		
		}
		// 현재 상태 중인 스킬 체크
		if(commons.isLock(pc)) {
			return false;
		}
		// 스킬이 마법 일경우 
		if(_isMagic) {
			// 마법 확률 체크 
			if(SkillUtil.isCounterMagic(pc)) {
				return false;
			}
		}
		return true;
	}
	// 확률계 스킬용
	protected boolean isMagicSkillSet(L1NpcInstance own, L1PcInstance pc) {
		if(pc == null || pc.isDead() || !isRange(own, pc)) {
			return false;		
		}
		// 현재 상태 중인 스킬 체크
		if(commons.isDragonRaidLock(pc)) {
			return false;
		}	
     	return true;
	}
	// 리콜 스킬용
	protected boolean isReCallSkillSet(L1NpcInstance own, L1PcInstance pc) {
		if(pc == null || pc.isDead() || !isRange(own, pc)) {
			return false;		
		}
		// 현재 상태 중인 스킬 체크
		if(commons.isDragonRaidLock(pc)) {
			return false;
		}
		// 스킬이 마법 일경우 
		if(_isMagic) {
			// 카운터 매직 상태 여부
			if(SkillUtil.isCounterMagic(pc)) {
				return false;
			}
			// 마법 확률 체크 
			if (!(CommonUtil.randomRange(1, 100) <= _ratio)) {
				return false;
			}
		}
		return true;
	}

	protected int calcDamage(L1Character attacker, L1PcInstance targetPc) {
		int dmg = 0, min = _dmgMin, max = _dmgMax;
		// 마법 공격일때 
		if(_isMagic) { 
			// 몬스터 상태에 따른 마법 데미지 
			dmg = commons.getDragonRaidMagicDamage(attacker, targetPc, min, max);
			// 이레이즈 매직 유무 판별
			SkillUtil.cancelEraseMagic(targetPc);
			// 카운터 매직 처리 
			if(targetPc.hasSkillEffect(SkillId.카운터_매직)) {
				targetPc.removeSkillEffect(SkillId.카운터_매직);
				S_SkillSound ss = S_SkillSound.get(targetPc.getId(), EffectUtil.카운터_매직);
				targetPc.sendPackets(ss, false); 
				targetPc.broadcastPacket(ss);
				dmg = 0;
			}
			// 마안 처리
			if(targetPc.hasSkillEffect(SkillId.수룡_마안)) { 
				if(CommonUtil.randomRange(1, 100) <= 10) {
					dmg >>= 1;
				}
			}
		}
		else { // 마법 공격이 아닐때
			dmg = _rnd.nextInt(max - min) + min;
		}
		//확률 데미지 리덕션, 데미지 리덕션 처리.
		if(targetPc != null && dmg > 0) {
			commons.getReducIitems(targetPc, dmg);
			dmg -= targetPc.getDamageReduction();

			if (targetPc.hasSkillEffect(SkillId.이뮨_투함)) {
				dmg *= Config.IMMNUE_TO_HARM_RATE;
			}
		}
		// 버그 처리 	
		return dmg < 0 ? 0 : dmg;
	}
}
