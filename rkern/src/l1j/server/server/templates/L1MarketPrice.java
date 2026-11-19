package l1j.server.server.templates;

public class L1MarketPrice {
	private int order;

	public int getOrder() {
		return order;
	}

	public void setOrder(int i) {
		this.order = i;
	}

	private int npcid;

	public int getNpcId() {
		return npcid;
	}

	public void setNpcId(int id) {
		this.npcid = id;
	}
	
	private int itemobjid;
	public int getItemObjId(){
		return itemobjid;
	}
	public void setItemObjId(int id){
		this.itemobjid = id;
	}

	private String itemname;

	public String getItemName() {
		return itemname;
	}

	public void setItemName(String name) {
		this.itemname = name;
	}

	private String charname;

	public String getCharName() {
		return charname;
	}

	public void setCharName(String name) {
		this.charname = name;
	}

	private int item_id;

	public int getItemId() {
		return item_id;
	}

	public void setItemId(int i) {
		this.item_id = i;
	}

	private int count;

	public int getCount() {
		return count;
	}

	public void setCount(int i) {
		this.count = i;
	}

	private int enchant;

	public int getEnchant() {
		return enchant;
	}

	public void setEnchant(int i) {
		this.enchant = i;
	}

	private int price;

	public int getPrice() {
		return price;
	}

	public void setPrice(int i) {
		this.price = i;
	}

	private int locx;

	public int getLocX() {
		return locx;
	}

	public void setLocX(int i) {
		this.locx = i;
	}

	private int locy;

	public int getLocY() {
		return locy;
	}

	public void setLocY(int i) {
		this.locy = i;
	}

	private int locm;

	public int getLocM() {
		return locm;
	}

	public void setLocM(int i) {
		this.locm = i;
	}

	private int iden;

	public int getIden() {
		return iden;
	}

	public void setIden(int flag) {
		this.iden = flag;
	}

	private int attr;

	public int getAttr() {
		return attr;
	}

	public void setAttr(int i) {
		this.attr = i;
	}
	
	/** 2016.11.24 앱센터 시세 **/
	private int invGfx;				// 인벤번호
	public int getInvGfx(){
		return invGfx;
	}
	
	public void setInvGfx(int i){
		invGfx = i;
	}
	
	private boolean _isPurchase;	// 매입 정보인지 판매 정보인지.
	public boolean isPurchase(){
		return _isPurchase;
	}
	public void setPurchase(boolean b){
		_isPurchase = b;
	}
	
}