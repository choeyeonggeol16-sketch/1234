package l1j.server.server.model.character.combat.arena.dogfight.history;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.templates.L1Item;

public class DogFightTicketInfo {
    public static DogFightTicketInfo newInstance(){
        return new DogFightTicketInfo();
    }

    private int m_corner_id;
    private String m_corner_name;
    private volatile int m_ticket_count;
    private volatile double m_dividend;
    private volatile long m_price; 
    private L1Item m_template;
    private double m_win_rate;
    private DogFightTicketInfo(){}

    public DogFightTicketInfo set_corner_id(int corner_id){
        m_corner_id = corner_id;
        return this;
    }
    public DogFightTicketInfo set_corner_name(String corner_name){
        m_corner_name = corner_name;
        return this;
    }
    public DogFightTicketInfo set_ticket_count(int ticket_count){
        m_ticket_count = ticket_count;
        return this;
    }
    public DogFightTicketInfo set_price(int price){
        m_price = price;
        return this;
    }
    public DogFightTicketInfo set_template(L1Item template){
        m_template = template;
        return this;
    }
    public DogFightTicketInfo set_win_rate(double win_rate){
        m_win_rate = win_rate;
        return this;
    }
    public int get_corner_id(){
        return m_corner_id;
    }
    public String get_corner_name(){
        return m_corner_name;
    }
    public int get_ticket_count(){
        return m_ticket_count;
    }
    public double get_dividend(){
        return m_dividend;
    }
    public long get_price(){
        return m_price;
    }
    public L1Item get_template(){
        return m_template;
    }
    public double get_win_rate(){
        return m_win_rate;
    }
    public DogFightTicketInfo update_ticket_count(int amount, int price){
        m_ticket_count += amount;
        m_price += price;
        return this;
    }

    /**
     * 배당 = 총풀 / 이 코너 풀 (패리뮈추엘).
     * 계산값이 LIMIT_DIVIDEND 초과 시에만 상한 적용 
     * → 지급액은 항상 총풀 이하 → 집 구조적으로 적자 불가능.
     */
    public void update_ticket_dividend(int total_price){
    	long price = m_price;
    	if(price <= 0){
    		m_dividend = 0D;
    		return;
    	}
    	double calculated = (double)total_price / (double)price;
    	if(calculated > DogFightSettings.LIMIT_DIVIDEND){
    		calculated = DogFightSettings.LIMIT_DIVIDEND; 
    	}
    	m_dividend = calculated;
    }
}