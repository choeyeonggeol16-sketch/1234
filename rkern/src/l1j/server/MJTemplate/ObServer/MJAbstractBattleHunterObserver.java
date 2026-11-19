package l1j.server.MJTemplate.ObServer;

import l1j.server.server.model.L1Object;

public abstract class MJAbstractBattleHunterObserver extends MJAbstractInstanceObserver{
	protected static final int	BH_STATUS_OPEN 		= 1;
	protected static final int	BH_STATUS_PALYING 	= 2;
	protected static final int	BH_STATUS_CLOSE 	= 4;
	protected static final int	BH_STATUS_COMPLETED	= 8;
	
	private 	int		_status;
	protected 	int 	_left;
	protected 	int 	_top;
	protected 	int 	_right;
	protected 	int 	_bottom;
	protected 	short	_mapId;
	protected void initialize(int left, int top, int right, int bottom, short mapId){
		setStatus(BH_STATUS_CLOSE);
		_left 		= left;
		_top		= top;
		_right		= right;
		_bottom		= bottom;
		_mapId		= mapId;
	}
	
	public boolean isOpen(){
		return (_status & BH_STATUS_OPEN) > 0;
	}
	
	public boolean isPlaying(){
		return (_status & BH_STATUS_PALYING) > 0;		
	}
	
	public boolean isClose(){
		return (_status & BH_STATUS_CLOSE) > 0;		
	}
	
	public boolean isCompleted(){
		return (_status & BH_STATUS_COMPLETED) > 0;		
	}
	
	protected void setStatus(int i){
		_status = i;
	}
	
	public int getLeft(){
		return _left;
	}
	
	public int getTop(){
		return _top;
	}
	
	public int getRight(){
		return _right;
	}
	
	public int getBottom(){
		return _bottom;
	}
	
	public boolean isInArea(L1Object obj){
		int x = obj.getX(), y = obj.getY();
		return _left <= x && _top <= y && x <= _right && y <= _bottom;
	}
	
	public int getCenterWidth(){
		return ((_right - _left) / 2) + _left;
	}
	
	public int getCenterHeight(){
		return ((_bottom - _top) / 2) + _top;
	}
	
	public short getMapId(){
		return _mapId;
	}
	
	public abstract void open();
	public abstract void playing(int pack);
	public abstract void close();
	public abstract void completed();
	
	@Override
	public abstract void dispose();
}
