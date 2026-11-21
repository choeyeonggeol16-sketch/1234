package l1j.server.server.shared.adapter;

public class BattleHunterOToOAdapter extends ObservableToObserverAdapter{
	public static final int OTO_REASON_CHANGED 		= 1;
	public static final int OTO_REASON_COMPLETED 	= 2;
	public static final int OTO_REASON_TIMEOVER		= 3;
	
	public static BattleHunterOToOAdapter create(){
		return new BattleHunterOToOAdapter();
	}
}
