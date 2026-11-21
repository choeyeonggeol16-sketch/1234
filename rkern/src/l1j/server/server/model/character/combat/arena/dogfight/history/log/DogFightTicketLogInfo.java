package l1j.server.server.model.character.combat.arena.dogfight.history.log;
public class DogFightTicketLogInfo {
	public static DogFightTicketLogInfo newInstance(){
		return new DogFightTicketLogInfo();
	}

	private String m_action_date;
	private int m_character_object_id;
	private String m_character_name;
	private int m_item_id;
	private String m_item_name;
	private int m_count;
	private int m_price;
	private int m_total_price;
	private DogFightTicketLogInfo(){}

	public DogFightTicketLogInfo set_action_date(String action_date){
		m_action_date = action_date;
		return this;
	}
	public DogFightTicketLogInfo set_character_object_id(int character_object_id){
		m_character_object_id = character_object_id;
		return this;
	}
	public DogFightTicketLogInfo set_character_name(String character_name){
		m_character_name = character_name;
		return this;
	}
	public DogFightTicketLogInfo set_item_id(int item_id){
		m_item_id = item_id;
		return this;
	}
	public DogFightTicketLogInfo set_item_name(String item_name){
		m_item_name = item_name;
		return this;
	}
	public DogFightTicketLogInfo set_count(int count){
		m_count = count;
		return this;
	}
	public DogFightTicketLogInfo set_price(int price){
		m_price = price;
		return this;
	}
	public DogFightTicketLogInfo set_total_price(int total_price){
		m_total_price = total_price;
		return this;
	}
	public String get_action_date(){
		return m_action_date;
	}
	public int get_character_object_id(){
		return m_character_object_id;
	}
	public String get_character_name(){
		return m_character_name;
	}
	public int get_item_id(){
		return m_item_id;
	}
	public String get_item_name(){
		return m_item_name;
	}
	public int get_count(){
		return m_count;
	}
	public int get_price(){
		return m_price;
	}
	public int get_total_price(){
		return m_total_price;
	}

}
