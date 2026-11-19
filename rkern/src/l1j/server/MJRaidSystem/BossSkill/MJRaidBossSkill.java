package l1j.server.MJRaidSystem.BossSkill;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_OnlyEffect;

import l1j.server.server.utils.MJCommons;

public abstract class MJRaidBossSkill{
	// bsr is boss skill range.
	public static final int BSRTYPE_ONCE	= 1;	// 한 대상만을
	public static final int BSRTYPE_LINE	= 2;	// 한 라인을
	public static final int BSRTYPE_FRONT	= 4;	// 전방
	public static final int BSRTYPE_RECT	= 8;	// 정 사각형
	public static final int BSRTYPE_SCREEN	= 16;	// 화면 내 전체.
	
	protected int			_actid; 
	protected int			_type;
	protected int			_range;
	protected int			_dmgMin;
	protected int			_dmgMax;
	protected int			_effectId;
	protected int			_ratio;
	protected boolean		_isMagic;

	public void setActionId(int actid){
		_actid 	= actid;
	}
	public void setType(int type){
		_type	= type;
	}
	public void setRange(int range){
		_range 	= range;
	}
	public void setDamageMin(int dmg){
		_dmgMin = dmg;
	}
	public void setDamageMax(int dmg){
		_dmgMax = dmg;
	}
	public void setEffectId(int effectId){
		_effectId = effectId;
	}
	public void setRatio(int raito){
		_ratio = raito;
	}
	public void setIsMagic(boolean b){
		_isMagic = b;
	}
	
	protected int getSafeActId(){
		return 19;
	}

	public void sleepAction(L1NpcInstance own, int actionId) throws Exception{
		EActionCodes code = EActionCodes.fromInt(actionId);
		if(code == null){
			Thread.sleep(500);
			return;
		}
		Thread.sleep(own.getCurrentSpriteInterval(code));
	}
	
	public void sleep(long l) throws Exception{
		Thread.sleep(l);
	}
	public abstract void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs);
	public abstract void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs);
	
	protected boolean isRange(L1NpcInstance own, L1PcInstance pc){
		if(own.getMapId() != pc.getMapId())
			return false;
		
		if((_type & BSRTYPE_ONCE) > 0)
			return isDistance(own.getX(), own.getY(), pc.getX(), pc.getY());
		else if((_type & BSRTYPE_LINE) > 0)
			return isLine(own, pc);
		else if((_type & BSRTYPE_FRONT) > 0)
			return isInFront(own, pc);
		else if((_type & BSRTYPE_RECT) > 0){
			int div = _range / 2;
			return isInRectangle(own.getX() - div, own.getY() - div, own.getX() + div, own.getY() + div, pc.getLocation());
		}else if((_type & BSRTYPE_SCREEN) > 0)
			return isInScreen(own.getLocation(), pc.getLocation());
		else
			return false;
	}
	
	protected boolean isInRectangle(int left, int top, int right, int bottom, L1Location tLoc){
		int x 	= tLoc.getX();
		int y	= tLoc.getY();
		return (left <= x && top <= y && right >= x && bottom >= y); 
	}
	
	protected boolean isInFront(L1Character own, L1Character tar){
		if(!MJCommons.isWideAroundDirection(own, tar))
			return false;
		return isDistance(own.getX(), own.getY(), tar.getX(), tar.getY());
	}
	
	protected boolean isInScreen(L1Location oloc, L1Location tloc){
		return oloc.isInScreen(tloc);
	}
	
	protected boolean isLine(L1Character own, L1Character tar){
		return own.glanceCheck(tar.getX(), tar.getY());
	}
	
	protected boolean isDistance(int ox, int oy, int tx, int ty){
		return MJCommons.getDistance(ox, oy, tx, ty) <= _range;
	}
	
	protected int calcDamageForValakas(L1Character attacker, L1PcInstance pc){
		if(pc.hasSkillEffect(L1SkillId.IMMUNE_TO_HARM) && ThreadLocalRandom.current().nextInt(100) <= MJRaidLoadManager.MRS_BS_VALA_IMMUNEBLADE){
			SC_OnlyEffect.send(pc, pc.getId(), 15961);
            SC_OnlyEffect.broadcastFast(pc, pc.getId(), 15961);
			pc.removeSkillEffect(L1SkillId.IMMUNE_TO_HARM);
		}
		
		double imm = pc.getImmuneReduction();
		int min = _dmgMin;
		int max = _dmgMax;
		if(imm != 0){
			min -= (int)((double)min * imm);
			max -= (int)((double)max * imm);
		}

		int dmg = 0;
		if(_isMagic){
			dmg = MJCommons.getMagicDamage(attacker, pc, min, max);
			if(pc.hasSkillEffect(L1SkillId.FAFU_MAAN) || pc.hasSkillEffect(L1SkillId.LIFE_MAAN) || 
					pc.hasSkillEffect(L1SkillId.SHAPE_MAAN) || pc.hasSkillEffect(L1SkillId.BIRTH_MAAN)){
				int MaanMagicCri = ThreadLocalRandom.current().nextInt(100) + 1;
			    if (MaanMagicCri <= 35)
			    	dmg /= 2;
			}
		}else{
			dmg = ThreadLocalRandom.current().nextInt(Math.max(1, max - min)) + min;
		}
		
		dmg -= pc.getDamageReductionByArmor();

		if (pc.hasSkillEffect(L1SkillId.REDUCTION_ARMOR)) {
			int targetPcLvl = pc.getLevel();
			if (targetPcLvl < 50) 
				targetPcLvl = 50;
			dmg -= (targetPcLvl - 50) / 5 + 1;
		}
		
		if (pc.hasSkillEffect(L1SkillId.MAJESTY)) {
			int targetPcLvl = pc.getLevel();
			if (targetPcLvl < 50) {
				targetPcLvl = 50;
			}
			int r = (targetPcLvl - 50) / 5 + 1;
			dmg -= r;
		}

		/*수호성 아툰 추가하기
		if(pc.getAccount().getTree_cri() == 1){
			
		}
		*/
		/**수호성 아툰**/
		if(pc.getAccount().getTree_select4() != 0){
			int a = pc.getAccount().getTree_select4();
			int r = ThreadLocalRandom.current().nextInt(100) + 1;
			if(r <= 2){
				switch(a){
				case 20796:
					dmg -= dmg/20;
					break;
				case 19052:
					dmg -= dmg/10;
					break;
				case 19055:
					dmg -= dmg/5;
					break;
				}
				if(pc.getLinMeff() != 2){
					SC_SkillSound.send(pc, pc.getId(), a);
				}
				SC_SkillSound.broadcast(pc, pc.getId(), a);
			}
		}
		if (pc.hasSkillEffect(L1SkillId.IllUSION_AVATAR))
			dmg += dmg / 5;

		if (pc.hasSkillEffect(L1SkillId.PATIENCE))
			dmg -= 4;

		if (pc.hasSkillEffect(L1SkillId.DRAGON_SKIN))
			dmg -= 3;

		return dmg;
	}
	
	protected int calcDamage(L1Character attacker, L1PcInstance pc){
		double imm = pc.getImmuneReduction();
		int min = _dmgMin;
		int max = _dmgMax;
		if(imm != 0){
			min -= (int)((double)min * imm);
			max -= (int)((double)max * imm);
		}

		int dmg = 0;
		if(_isMagic){
			dmg = MJCommons.getMagicDamage(attacker, pc, min, max);
			if(pc.hasSkillEffect(L1SkillId.FAFU_MAAN) || pc.hasSkillEffect(L1SkillId.LIFE_MAAN) || 
					pc.hasSkillEffect(L1SkillId.SHAPE_MAAN) || pc.hasSkillEffect(L1SkillId.BIRTH_MAAN)){
				int MaanMagicCri = ThreadLocalRandom.current().nextInt(100) + 1;
			    if (MaanMagicCri <= 35)
			    	dmg /= 2;
			}
		}else{
			dmg = ThreadLocalRandom.current().nextInt(Math.max(1, max - min)) + min;
		}
		
		dmg -= pc.getDamageReductionByArmor();

		if (pc.hasSkillEffect(L1SkillId.REDUCTION_ARMOR)) {
			int targetPcLvl = pc.getLevel();
			if (targetPcLvl < 50) 
				targetPcLvl = 50;
			dmg -= (targetPcLvl - 50) / 5 + 1;
		}
		
		if (pc.hasSkillEffect(L1SkillId.IllUSION_AVATAR))
			dmg += dmg / 5;
		
		if (pc.hasSkillEffect(L1SkillId.PATIENCE))
			dmg -= 4;
		
		if (pc.hasSkillEffect(L1SkillId.DRAGON_SKIN))
			dmg -= 3;
		return dmg;
	}
	
	protected boolean isSkillSet(L1NpcInstance own, L1PcInstance pc){
		if(pc == null || pc.isDead() || !isRange(own, pc))
			return false;
		
		if(MJCommons.isUnbeatable(pc))
			return false;
		
		if(_isMagic){
			if(MJCommons.isCounterMagic(pc))
				return false;
			
			if(!MJCommons.isMagicSuccess(own, pc, _ratio))
				return false;
		}
		
		return true;
	}
	
	public boolean isAbsoluteBarrier(L1PcInstance pc){
		return pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
	}
	
	public boolean setAbsoluteBarrierDestroy(L1PcInstance pc){
		if(ThreadLocalRandom.current().nextInt(100) <= MJRaidLoadManager.MRS_BS_VALA_ABSOLUTEBLADE){
			pc.removeSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
			SC_OnlyEffect.send(pc, pc.getId(), 15835);
            SC_OnlyEffect.broadcast(pc, pc.getId(), 15835);
			return true;
		}
		return false;
	}
}

