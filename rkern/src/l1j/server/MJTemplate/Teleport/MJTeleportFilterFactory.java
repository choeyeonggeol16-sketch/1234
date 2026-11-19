package l1j.server.MJTemplate.Teleport;

import l1j.server.server.model.Instance.L1PcInstance;

public class MJTeleportFilterFactory {
	public static MJTeleportFilter create(short mapId){
		return new MJTeleportFilter(){
			@Override
			public boolean isFilter(L1PcInstance pc){
				return pc.getMapId() != mapId;
			}
		};
	}
}
