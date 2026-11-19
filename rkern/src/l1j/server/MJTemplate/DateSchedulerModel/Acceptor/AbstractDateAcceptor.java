package l1j.server.MJTemplate.DateSchedulerModel.Acceptor;

import java.util.Calendar;

import l1j.server.MJTemplate.MJSimpleDateTime;
import l1j.server.MJTemplate.DateSchedulerModel.MinuteScheduler;

public abstract class AbstractDateAcceptor {
	public static final int INCLUDE_DAYWEEK_SUNDAY = to_include_day_week(Calendar.SUNDAY);
	public static final int INCLUDE_DAYWEEK_MONDAY = to_include_day_week(Calendar.MONDAY);
	public static final int INCLUDE_DAYWEEK_TUESDAY = to_include_day_week(Calendar.TUESDAY);
	public static final int INCLUDE_DAYWEEK_WEDNESDAY = to_include_day_week(Calendar.WEDNESDAY);
	public static final int INCLUDE_DAYWEEK_THURSDAY = to_include_day_week(Calendar.THURSDAY);
	public static final int INCLUDE_DAYWEEK_FRIDAY = to_include_day_week(Calendar.FRIDAY);
	public static final int INCLUDE_DAYWEEK_SATURDAY = to_include_day_week(Calendar.SATURDAY);

	public static int to_include_day_week(int day_week){
		return (1 << (day_week - 1));
	}
	
	protected int	_include_day_week;
	protected MJSimpleDateTime _opened_time;
	protected Calendar _closed_cal;
	protected int	_remain_type;
	protected int _remain_time;
	protected boolean _is_opened;
	
	public AbstractDateAcceptor add_include_day_week(int day_week){
		_include_day_week |= to_include_day_week(day_week);
		return this;
	}
	
	public AbstractDateAcceptor add_include_day_week(Integer[] day_weeks){
		for(int i : day_weeks)
			add_include_day_week(i);
		return this;
	}
	
	public boolean is_include_day_week(int day_week){
		int include_day_week = to_include_day_week(day_week);
		return (_include_day_week & include_day_week) == include_day_week;
	}
	public Calendar create_closed_calendar(Calendar cal){
		return create_closed_calendar(cal, _remain_type, _remain_time);
	}
	public Calendar create_closed_calendar(Calendar cal, int remain_type, int remain_time){
		Calendar closed_cal = (Calendar)cal.clone();
		closed_cal.set(Calendar.HOUR_OF_DAY, _opened_time.hour);
		closed_cal.set(Calendar.MINUTE, _opened_time.minute);
		closed_cal.set(Calendar.SECOND, _opened_time.second);
		closed_cal.add(remain_type, remain_time);
		return closed_cal;
	}
	public boolean is_include_sunday(){
		return (_include_day_week & INCLUDE_DAYWEEK_SUNDAY) == INCLUDE_DAYWEEK_SUNDAY;
	}
	public boolean is_include_monday(){
		return (_include_day_week & INCLUDE_DAYWEEK_MONDAY) == INCLUDE_DAYWEEK_MONDAY;
	}
	public boolean is_include_tuesday(){
		return (_include_day_week & INCLUDE_DAYWEEK_TUESDAY) == INCLUDE_DAYWEEK_TUESDAY;
	}
	public boolean is_include_wedneday(){
		return (_include_day_week & INCLUDE_DAYWEEK_WEDNESDAY) == INCLUDE_DAYWEEK_WEDNESDAY;
	}
	public boolean is_include_thursday(){
		return (_include_day_week & INCLUDE_DAYWEEK_THURSDAY) == INCLUDE_DAYWEEK_THURSDAY;
	}
	public boolean is_include_friday(){
		return (_include_day_week & INCLUDE_DAYWEEK_FRIDAY) == INCLUDE_DAYWEEK_FRIDAY;
	}
	public boolean is_include_saturday(){
		return (_include_day_week & INCLUDE_DAYWEEK_SATURDAY) == INCLUDE_DAYWEEK_SATURDAY;
	}
	
	public AbstractDateAcceptor set_opened_time(int hour, int minute, int second){
		return set_opened_time(MJSimpleDateTime.newInstance(hour, minute, second));
	}
	
	public AbstractDateAcceptor set_opened_time(MJSimpleDateTime opened_time){
		_opened_time = opened_time;
		return this;
	}
	
	public AbstractDateAcceptor set_remain_type(int remain_type){
		_remain_type = remain_type;
		return this;
	}
	
	public AbstractDateAcceptor set_remain_time(int remain_time){
		_remain_time = remain_time;
		return this;
	}
	
	public AbstractDateAcceptor set_closed_cal(Calendar cal){
		_closed_cal = cal;
		return this;
	}
	
	public Calendar get_closed_cal(){
		return _closed_cal;
	}
	
	public void accept(Calendar cal){
		if(is_opened()){
			if(_closed_cal.after(cal))
				return;
			
			set_is_opened(false);
			do_close(cal);
		}else{
			if(!is_include_day_week(cal.get(Calendar.DAY_OF_WEEK)))
				return;
			
			if(_opened_time.after(cal) || cal.before(_opened_time.to_calendar()))
				return;
			
			if(_closed_cal != null)
				_closed_cal.clear();
			_closed_cal = create_closed_calendar(cal);
			if(_closed_cal.before(cal))
				return;
			set_is_opened(true);
			do_open(cal);
		}
	}
	
	public boolean is_opened(){
		return _is_opened;
	}
	public boolean is_closed(){
		return !_is_opened;
	}
	public AbstractDateAcceptor set_is_opened(boolean is_opened){
		_is_opened = is_opened;
		return this;
	}
	
	public AbstractDateAcceptor do_register(){
		MinuteScheduler.getInstance().register(this);
		return this;
	}
	
	public AbstractDateAcceptor do_removed(){
		MinuteScheduler.getInstance().removed(this);
		return this;
	}
	
	public abstract AbstractDateAcceptor do_open(Calendar cal);
	public abstract AbstractDateAcceptor do_close(Calendar cal);
}
