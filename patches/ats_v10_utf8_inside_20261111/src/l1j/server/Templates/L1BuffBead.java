package l1j.server.Templates;

public class L1BuffBead{
	private int _itemid;
	private int[] _buffid;
	private boolean _isdelete;
	private boolean _istimeuse;
	
	private int _useItemid;
	private int _useItemCount;
	
	public int getUseItemId(){
		return _useItemid;
	}
	public void setUseItemId(int i){
		_useItemid = i;
	}
	
	public int getUseItemCount(){
		return _useItemCount;
	}
	public void setUseItemCount(int i){
		_useItemCount = i;
	}
	
	public int getItemId(){
		return _itemid;
	}
	public void setItemId(int id){
		_itemid = id;
	}
	
	public int[] getBuffId(){
		return _buffid;
	}
	public void setBuffId(int[] id){
		_buffid = id;
	}
	
	public boolean isDelete(){
		return _isdelete;
	}
	public void setDelete(boolean flag){
		_isdelete = flag;
	}
	
	public boolean isTimeUse(){
		return _istimeuse;
	}
	public void setTimeUse(boolean flag){
		_istimeuse = flag;
	}
}