package l1j.server.server.model.character.combat.statistics.chart;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.combat.statistics.Kda;
import l1j.server.server.model.character.combat.statistics.KdaLoadManager;
import l1j.server.server.model.character.combat.statistics.KdaLoader;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.network.core.sc.packet.SC_ChartRank;

public class KdaChartUpdator {
	private static final Logger LOG = Logger.getLogger(KdaChartUpdator.class.getName());
	private boolean 			_isUpdate;
	private volatile boolean _disposed;
	private Calendar 			_updateCal;
	private volatile SC_ChartRank.Snapshot	_pck;
	private Comparator<Kda>	_sorter;
	public KdaChartUpdator(){
		_sorter = new ChartSorter();
		updateCal();
	}
	
	public synchronized void dispose(){
		_disposed = true;
//		if(_pck != null){
//			_pck.clear();
//			_pck = null;
//		}
		if(_updateCal != null){
			_updateCal.clear();
			_updateCal = null;
		}
		_sorter 	= null;
		_isUpdate 	= true;
	}
	
	public void updateCal(){
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		if(_updateCal != null){
			_updateCal.clear();
			_updateCal = null;
		}
		_updateCal = (Calendar)cal.clone();
		_updateCal.add(Calendar.SECOND, KdaLoadManager.KDA_CHART_DELAY_SEC);
	}
	
	public boolean isBefore(Calendar cal){
		if(_updateCal == null)
			return false;
		
		return _updateCal.before(cal);
	}
	
	public void update(){
		synchronized (this) {
			if (_disposed || _isUpdate) return;
			_isUpdate = true;
		}
		try {
			CWorldWork.execute(new UpdateChart());
		} catch (RejectedExecutionException rejected) {
			synchronized (this) { _isUpdate = false; }
			LOG.log(Level.WARNING, "KDA chart refresh admission rejected", rejected);
		}
	}
	
	public void onLoginUser(L1PcInstance pc){
		if(_pck != null)
			SC_ChartRank.send(pc, _pck);
	}
	
	class UpdateChart implements Runnable{
		@Override
		public void run() {
			SC_ChartRank.Snapshot pck = _pck;
			try{
				ArrayList<Kda> list = KdaLoader.getInstance().createSnapshot();
				if(list == null || list.size() <= 0){
					_pck = SC_ChartRank.getZero();
				}else{
					list.sort(_sorter);
					_pck = SC_ChartRank.get(list);
				}
			}catch(Exception e){
				e.printStackTrace();
			}finally{
//				if(pck != null){
//					pck.clear();
//					pck = null;
//				}
				
				if(_pck != null){
                    Collection<L1PcInstance> cols=CWorld.getInstance().getAllPlayers();
                    final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter wire=SC_ChartRank.nativeWriter(_pck);
                    if(wire!=null){
                        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
                            for(L1PcInstance pc:cols){
                                if(pc==null || pc.getKDA()==null || !pc.getKDA().isChartView)
                                    continue;
                                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
                            }
                        }
                    }
                }
				synchronized (KdaChartUpdator.this) {
					if (!_disposed) updateCal();
					_isUpdate = false;
				}
			}
		}
	}
	
	class ChartSorter implements Comparator<Kda>{
		@Override
		public int compare(Kda o1, Kda o2) {
			if(o1.kill > o2.kill)
				return -1;
			else if(o1.kill < o2.kill)
				return 1;
			return 0;
		}
	}
}
