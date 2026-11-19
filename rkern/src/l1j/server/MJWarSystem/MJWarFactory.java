package l1j.server.MJWarSystem;

import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.L1Clan;

public class MJWarFactory {
	public enum WAR_TYPE{
		CASTLE(0),
		NORMAL(1);
		
		private int _val;
		WAR_TYPE(int i){
			_val = i;
		}
		
		public int toInt(){
			return _val;
		}
		
		public boolean equals(WAR_TYPE type){
			return _val == type.toInt();
		}
	}
	
	private static final AtomicInteger warSerial = new AtomicInteger(0);
	private static Integer nextSerial(){
		return warSerial.incrementAndGet();
	}
	
	public static MJCastleWar createCastleWar(L1Clan defense, int castleId, String castleName){
		return MJCastleWar.newInstance(defense, nextSerial(), castleId, castleName);
	}
	
	public static MJWar createNormalWar(L1Clan defense){
		return MJWar.newInstance(defense, WAR_TYPE.NORMAL, nextSerial());
	}
}
