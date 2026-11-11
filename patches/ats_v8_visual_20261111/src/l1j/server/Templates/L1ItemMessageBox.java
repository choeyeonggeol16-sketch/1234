package l1j.server.Templates;

public class L1ItemMessageBox {
	private int _itemid;
	private String _itemName;
	private String _bonusitem;
	private String _enchant;
	private String _mentoption;
	private String _ment;
	
	public int getItemId(){
		return _itemid;
	}
	
	public void setItemId(int id){
		_itemid = id;
	}
	
	public String getItemName(){
		return _itemName;
	}
	
	public void setItemName(String name){
		_itemName = name;
	}
	
	public String getBonusItem(){
		return _bonusitem;
	}
	
	public void setBonusItem(String item){
		_bonusitem = item;
	}
	
	public String getEnchant(){
		return _enchant;
	}
	
	public void setEnchant(String enchant){
		_enchant = enchant;
	}
	
	public String getMentoption(){
		return _mentoption;
	}
	
	public void setMentoption(String mentop){
		_mentoption = mentop;
	}
	
	public String getMent(){
		return _ment;
	}
	
	public void setMent(String ment){
		_ment = ment;
	}

}