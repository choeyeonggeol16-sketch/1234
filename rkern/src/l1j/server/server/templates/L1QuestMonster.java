package l1j.server.server.templates;

// 퀘스트 몬스터 시스템
public class L1QuestMonster {
	private int _npcid;
	private String _npcName;
	private int _npccount;
	private int _effid;
	private boolean alleffect;
	private boolean _isment;
	private String _ment;
	
	private boolean _isItem;
	private int _itemid;
	private int _itemcount;
	
	public boolean isGiveItem(){
		return _isItem;
	}
	public L1QuestMonster setGiveItem(boolean flag){
		_isItem = flag;
		return this;
	}
	
	public int getItemId(){
		return _itemid;
	}
	
	public void setItemId(int id){
		_itemid = id;
	}
	
	public int getItemCount(){
		return _itemcount;
	}
	public void setItemCount(int i){
		_itemcount = i;
	}
	
	public int getNpcId(){
		return _npcid;
	}
	public void setNpcId(int id){
		_npcid = id;
	}
	
	public String getNpcName(){
		return _npcName;
	}
	public void setNpcName(String name){
		_npcName = name;
	}
	
	public int getNpcCount(){
		return _npccount;
	}
	public void setNpcCount(int count){
		_npccount = count;
	}
	
	public int getEffectNum(){
		return _effid;
	}
	public void setEffectNum(int num){
		_effid = num;
	}
	
	public boolean isAllEffect(){
		return alleffect;
	}
	public L1QuestMonster setAllEffect(boolean flag){
		alleffect = flag;
		return this;
	}
	
	public boolean isMentuse(){
		return _isment;
	}
	
	public L1QuestMonster setMentuse(boolean flag){
		_isment = flag;
		return this;
	}
	
	public String getMent(){
		return _ment;
	}
	
	public void setMent(String ment){
		_ment = ment;
	}
}