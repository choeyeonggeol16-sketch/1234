package l1j.server.MJ3SEx.IntervalDecorator;

import l1j.server.Config;
import l1j.server.DogFight.Instance.MJDogFightInstance;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import java.util.logging.Logger;

public class ActionIntervalDecoratorFactory {
	
	private static final Logger _log = Logger.getLogger(ActionIntervalDecoratorFactory.class.getName());
	
	public static final double HASTE_WALK_RETERADATION = 1.33D;
	public static final double BRAVE_WALK_RETERADATION = 1.33D;
	public static final double THIRD_WALK_RETERADATION = 1.1D;  //9프로
	
	public static final double HASTE_WALK_ACCELERATION = 1D / HASTE_WALK_RETERADATION;
	public static final double BRAVE_WALK_ACCELERATION = 1D / BRAVE_WALK_RETERADATION;
	public static final double THIRD_WALK_ACCELERATION = 1D / THIRD_WALK_RETERADATION;
	
	public static final double HASTE_RETARDATION = 1.33D;
	public static final double BRAVE_RETARDATION = 1.33D;
	public static final double THIRD_RETARDATION = 1.1D;
	public static final double ELF_RETARDATION = 1.12D;   
	
	public static final double ELF_ACCELERATION = 1D / ELF_RETARDATION;
	public static final double HASTE_ACCELERATION = 1D / HASTE_RETARDATION;
	public static final double BRAVE_ACCELERATION = 1D / BRAVE_RETARDATION;
	public static final double THIRD_ACCELERATION = 1D / THIRD_RETARDATION;
	
	
	
	public static final ActionIntervalDecorator<L1PcInstance> PC_WALK_DECORATOR = new ActionIntervalDecorator<L1PcInstance>(){
		@Override
		public double decoration(L1PcInstance owner, double interval) {
			if(owner.isHaste()) {
				interval *= HASTE_WALK_ACCELERATION;
			}
			else if(owner.hasSkillEffect(L1SkillId.SLOW)) {
				interval *= HASTE_WALK_RETERADATION;
			}
			// 2단가속, 블러드러스트, 허리케인, 댄싱, 와퍼, 홀리, 
			if(owner.isBrave() || owner.isBlood_lust() || owner.isElfBraveMagicShort() || owner.isElfBraveMagicLong() || 
					owner.isElfBrave() || owner.isFastMovable() || owner.isFruit() || owner.istest()) {
				interval *= BRAVE_WALK_ACCELERATION;
			}
			if(owner.isDragonPearl()) {
				interval *= THIRD_WALK_ACCELERATION;
			}
			if(Config.USE_ACTION_TIME_LOGGING){
			    final double d = interval;
			    _log.fine(() -> String.format("이동속도 : %.2fms", d));
			}
			
			return interval;
		}
	};
	
	public static final ActionIntervalDecorator<L1PcInstance> PC_ATT_DECORATOR = new ActionIntervalDecorator<L1PcInstance>(){
		@Override
		public double decoration(L1PcInstance owner, double interval) {
			if(owner.isHaste()) {
				interval *= HASTE_ACCELERATION;
			}
			else if(owner.hasSkillEffect(L1SkillId.SLOW)) {
				interval *= HASTE_RETARDATION;
			}
			if(owner.istest()){
				for(int i=owner.get_focus_wave_level() - 1; i>=0; --i) {
					interval *= THIRD_ACCELERATION;
				}
			}  
			if (owner.isFruit() && owner.isPassive(MJPassiveID.DARK_HORSE.toInt())) {
				interval *= THIRD_ACCELERATION;
			}
			
			if(owner.isBrave() || owner.isBlood_lust() || owner.isElfBraveMagicShort() || owner.isElfBraveMagicLong()) {
				interval *= BRAVE_ACCELERATION;
			}
			if(owner.isElfBrave()) {
				interval *= ELF_ACCELERATION;
			}
			if(owner.isDragonPearl()) {
				interval *= THIRD_ACCELERATION;
			}
			if(owner.hasSkillEffect(L1SkillId.WIND_SHACKLE)) {
				interval *= HASTE_WALK_RETERADATION;
			}
			
			if(Config.USE_ACTION_TIME_LOGGING){
			    final double d = interval;
			    _log.fine(() -> String.format("공격속도 : %.2fms", d));
			}

			return interval;	
		}
	};
	
	public static final ActionIntervalDecorator<L1Character> CHA_WALK_DECORATOR = new ActionIntervalDecorator<L1Character>(){
		@Override
		public double decoration(L1Character owner, double interval) {
			if(owner instanceof L1CompanionInstance){
				interval -= (interval * ((L1CompanionInstance) owner).get_movedelay_reduce() * 0.01);
			}else if(owner instanceof MJDogFightInstance){
				interval -= (interval * ((MJDogFightInstance) owner).get_movedelay_reduce() * 0.01);
			}else{
				if(owner.isHaste()) {
					interval *= HASTE_WALK_ACCELERATION;
				}
				else if(owner.hasSkillEffect(L1SkillId.SLOW)) {
					interval *= HASTE_WALK_RETERADATION;
				}
				if(owner.getBraveSpeed() == 1) {
					interval *= BRAVE_WALK_ACCELERATION;
				}
				if(owner.hasSkillEffect(L1SkillId.WIND_SHACKLE)) {
					interval *= HASTE_WALK_RETERADATION;
				}
			}
			return interval;
		}
	};
	
	public static final ActionIntervalDecorator<L1Character> CHA_ATT_DECORATOR = new ActionIntervalDecorator<L1Character>(){
		@Override
		public double decoration(L1Character owner, double interval) {
			if(owner instanceof L1CompanionInstance){
				interval -= (interval * ((L1CompanionInstance) owner).get_attackdelay_reduce() * 0.01);
			}else if(owner instanceof MJDogFightInstance){
				interval -= (interval * ((MJDogFightInstance) owner).get_attackdelay_reduce() * 0.01);
			}else{
				if(owner.isHaste()) {
					interval *= HASTE_WALK_ACCELERATION;
				}
				else if(owner.hasSkillEffect(L1SkillId.SLOW)) {
					interval *= HASTE_WALK_RETERADATION;
				}
				if(owner.getBraveSpeed() == 1) {
					interval *= BRAVE_WALK_ACCELERATION;
				}
				if(owner.hasSkillEffect(L1SkillId.WIND_SHACKLE)) {
					interval *= HASTE_WALK_RETERADATION;
				}
			}
			return interval;	
		}
	};
	
	public static final ActionIntervalDecorator<L1Character> NULL_DECORATOR = new ActionIntervalDecorator<L1Character>(){
		@Override
		public double decoration(L1Character owner, double interval) {
			return interval;	
		}
	};
}
