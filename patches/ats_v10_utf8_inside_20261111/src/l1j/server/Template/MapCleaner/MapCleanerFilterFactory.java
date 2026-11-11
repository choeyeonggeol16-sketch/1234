package l1j.server.Template.MapCleaner;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;

public class MapCleanerFilterFactory {
	public static MapCleanerFilter createDefaultFilter() { // 기본 필터
		return null;
//		int flg = MJL1Type.L1TYPE_SUMMON | MJL1Type.L1TYPE_DOLL;
//		return new MJMapCleanerFilter(){
//			@Override
//			public boolean isFilter(L1Object obj){
//				if(obj == null || obj.instanceOf(flg))
//					return true;
//				
//				if(obj.instanceOf(MJL1Type.L1TYPE_PC)){
//					L1PcInstance pc = (L1PcInstance)obj;
//					if(!pc.isDead())
//						pc.start_teleport(33445, 32794, 4, pc.getHeading(), 169, true, false, true);
//					return true;
//				}
//				return false;
//			}
//		};
	}
}
