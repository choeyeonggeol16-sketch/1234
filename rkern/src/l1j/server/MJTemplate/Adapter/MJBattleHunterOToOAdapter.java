package l1j.server.MJTemplate.Adapter;

public class MJBattleHunterOToOAdapter extends MJObservableToObserverAdapter{
	public static final int OTO_REASON_CHANGED 		= 1;
	public static final int OTO_REASON_COMPLETED 	= 2;
	public static final int OTO_REASON_TIMEOVER		= 3;
	
	public static MJBattleHunterOToOAdapter create(){
		return new MJBattleHunterOToOAdapter();
	}
}
