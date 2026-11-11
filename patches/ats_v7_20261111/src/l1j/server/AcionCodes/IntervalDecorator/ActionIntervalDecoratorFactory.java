package l1j.server.AcionCodes.IntervalDecorator;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;

public class ActionIntervalDecoratorFactory {
	public static final double HASTE_RETARDATION = 1.3325D;
	public static final double BRAVE_RETARDATION = 1.36D;
	public static final double THIRD_RETARDATION = 1.178D;
	public static final double HASTE_ACCELERATION = 1D / HASTE_RETARDATION;
	public static final double BRAVE_ACCELERATION = 1D / BRAVE_RETARDATION;
	public static final double THIRD_ACCELERATION = 1D / THIRD_RETARDATION;
	
	public static final ActionIntervalDecorator<L1PcInstance> PC_WALK_DECORATOR = new ActionIntervalDecorator<L1PcInstance>(){
		@Override
		public double decoration(L1PcInstance owner, double interval) {
			if(owner.isHaste()) {
				interval *= HASTE_ACCELERATION;	
			}		
			else if(owner.isSlow() || owner.isSlows()) {
				interval *= HASTE_RETARDATION;
			}	
			if(owner.isBrave() || owner.isFastMovable() || owner.isElfBrave()) {
				interval *= BRAVE_ACCELERATION;  
			}
			if(owner.isDragonPearl()) {
				interval *= THIRD_ACCELERATION;
			}			
			return interval;
		}
	};
	
	public static final double ATT_HASTE_RETARDATION = 1.3325D;
	public static final double ATT_BRAVE_RETARDATION = 1.36D;
	public static final double ATT_THIRD_RETARDATION = 1.178D;
	public static final double ATT_ELF_BRAVE_RETARDATION = 1.2D;
	public static final double ATT_HASTE_ACCELERATION = 1D / ATT_HASTE_RETARDATION;
	public static final double ATT_BRAVE_ACCELERATION = 1D / ATT_BRAVE_RETARDATION;
	public static final double ATT_THIRD_ACCELERATION = 1D / ATT_THIRD_RETARDATION;
	public static final double ATT_ELF_BRAVE_ACCELERATION = 1D / ATT_ELF_BRAVE_RETARDATION;
	public static final ActionIntervalDecorator<L1PcInstance> PC_ATT_DECORATOR = new ActionIntervalDecorator<L1PcInstance>(){
		@Override
		public double decoration(L1PcInstance owner, double interval) {
			if(owner.isHaste()) {
				interval *= ATT_HASTE_ACCELERATION;
			}
			else if(owner.isSlow()) {
				interval *= ATT_HASTE_RETARDATION;
			}
			if(owner.isBrave()) {
				interval *= ATT_BRAVE_ACCELERATION;
			}
			if(owner.isDragonPearl()) {
				interval *= ATT_THIRD_ACCELERATION;
			}
			if(owner.isWindShackle()) {
				interval *= ATT_HASTE_RETARDATION;
			}
			if(owner.isElfBrave()) {
				interval *= ATT_ELF_BRAVE_ACCELERATION;
			}
			return interval;	
		}
	};
	

	public static final ActionIntervalDecorator<L1Character> CHA_WALK_DECORATOR = new ActionIntervalDecorator<L1Character>() {
		@Override
		public double decoration(L1Character owner, double interval) {
			if(owner.isHaste()) {
				interval *= HASTE_ACCELERATION;
			}
			else if(owner.isSlow() || owner.isSlows()) {
				interval *= HASTE_RETARDATION;
			}
			if(owner.getBraveSpeed() == 1) {
				interval *= BRAVE_ACCELERATION;
			}
			return interval;
		}
	};
	
	public static final ActionIntervalDecorator<L1Character> CHA_ATT_DECORATOR = new ActionIntervalDecorator<L1Character>() {
		@Override
		public double decoration(L1Character owner, double interval) {
			if(owner.isHaste()) {
				interval *= HASTE_ACCELERATION;
			}
			else if(owner.isSlow()) {
				interval *= HASTE_RETARDATION;
			}
			if(owner.getBraveSpeed() == 1) {
				interval *= BRAVE_ACCELERATION;
			}
			if(owner.isWindShackle()) {
				interval *= HASTE_RETARDATION;
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
