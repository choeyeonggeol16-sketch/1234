package l1j.server.server.model.world.siege;

import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.L1Clan;

public class WarFactory {
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
	private static int nextSerial(){
		return warSerial.incrementAndGet();
	}
	
	static CCastle createCastleSiege(L1Clan defense, CastleSiegeTable.Entry entry) {
		return new CCastle(defense, nextSerial(), entry);
	}
	
	public static War createNormalWar(L1Clan defense){
		return War.newInstance(defense, WAR_TYPE.NORMAL, nextSerial());
	}
}
