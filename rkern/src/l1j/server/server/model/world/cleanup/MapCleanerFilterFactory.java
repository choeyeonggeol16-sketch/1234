package l1j.server.server.model.world.cleanup;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.shared.L1Type;

public class MapCleanerFilterFactory {
	
	public static MapCleanerFilter createDefaultFilter(){
		int flg = L1Type.L1TYPE_SUMMON | L1Type.L1TYPE_DOLL;
		return new MapCleanerFilter(){
			@Override
			public boolean isFilter(L1Object obj){
				if(obj == null || obj.instanceOf(flg))
					return true;
				
				if(obj.instanceOf(L1Type.L1TYPE_PC)){
					L1PcInstance pc = (L1PcInstance)obj;
					if(!pc.isDead())
						//원본 pc.start_teleport(33445, 32794, 4, pc.getHeading(), 169, true, false, true);
						pc.start_teleport(33055, 32748, 4, pc.getHeading(), 169, true, false, true);
					return true;
				}
				return false;
			}
		};
	}
	
	public static MapCleanerFilter createBattleHunterFilter(){
		int flg = L1Type.L1TYPE_SUMMON | L1Type.L1TYPE_DOLL;
		return new MapCleanerFilter(){
			@Override
			public boolean isFilter(L1Object obj){
				if(obj == null || obj.instanceOf(flg))
					return true;
				
				return false;
			}
		};
	}
}
