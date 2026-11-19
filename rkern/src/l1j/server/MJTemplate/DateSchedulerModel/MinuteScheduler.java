package l1j.server.MJTemplate.DateSchedulerModel;

import java.util.Calendar;

import l1j.server.MJTemplate.DateSchedulerModel.Acceptor.AbstractDateAcceptor;
import l1j.server.MJTemplate.DateSchedulerModel.Acceptor.AcceptorToNpcListenerAdapter;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;

public class MinuteScheduler extends AbstractDateScheduler implements TimeListener{
	private static MinuteScheduler _instance;
	public static MinuteScheduler getInstance(){
		if(_instance == null)
			_instance = new MinuteScheduler();
		return _instance;
	}
	
	private MinuteScheduler(){
		super();
	}
	
	public void clear_action_listener(){
		for(AbstractDateAcceptor acceptor : _acceptors){
			if(acceptor instanceof AcceptorToNpcListenerAdapter)
				acceptor.do_removed();
		}
	}
	
	public void run(){
		RealTimeClock.getInstance().addListener(this, Calendar.MINUTE);
	}

	@Override
	public void onMonthChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onDayChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onHourChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onMinuteChanged(BaseTime time) {
		try{
			Calendar cal = time.getCalendar();
			for(AbstractDateAcceptor acceptor : _acceptors)
				acceptor.accept(cal);
		}catch(Exception e){
			e.printStackTrace();
		}
	}


	@Override
	public void onSecondChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}
}
