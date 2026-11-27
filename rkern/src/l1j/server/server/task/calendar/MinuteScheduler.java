package l1j.server.server.task.calendar;

import java.util.Calendar;

import l1j.server.server.model.gametime.L2DateTime;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.model.gametime.L2TimeListener;
import l1j.server.server.task.calendar.acceptor.AbstractDateAcceptor;
import l1j.server.server.task.calendar.acceptor.AcceptorToNpcListenerAdapter;

public class MinuteScheduler extends AbstractDateScheduler implements L2TimeListener{
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
		CL2Time.addRealListener(this, Calendar.MINUTE);
	}







	@Override
	public void onMinuteChanged(L2DateTime time) {
		try{
			Calendar cal = time.getCalendar();
			for(AbstractDateAcceptor acceptor : _acceptors)
				acceptor.accept(cal);
		}catch(Exception e){
			e.printStackTrace();
		}
	}


}
