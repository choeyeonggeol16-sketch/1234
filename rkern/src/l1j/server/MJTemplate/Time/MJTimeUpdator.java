package l1j.server.MJTemplate.Time;

import java.util.Calendar;

public abstract class MJTimeUpdator {
	protected boolean 	_isUpdate;
	protected Calendar 	_updateCal;
	
	public MJTimeUpdator(){
		_isUpdate = false;
		updateCal();
	}
	
	public void dispose(){
		if(_updateCal != null){
			_updateCal.clear();
			_updateCal = null;
		}
		_isUpdate = true;
	}
	
	public boolean isBefore(Calendar cal){
		if(_updateCal == null)
			return false;
		
		return _updateCal.before(cal);
	}
	
	public boolean isUpdate(){
		return _isUpdate;
	}
	
	public abstract void updateCal();
	public abstract void update();
}
