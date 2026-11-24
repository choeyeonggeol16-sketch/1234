package l1j.server.server.model.character.combat.arena.dogfight.history;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayList;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.game.DogFightGame;
import l1j.server.server.model.character.combat.arena.dogfight.game.DogFightGameInfo;
import l1j.server.server.model.character.combat.arena.dogfight.history.log.DogFightTicketLogInfo;
import l1j.server.server.model.character.combat.arena.dogfight.instance.DogFightInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.shared.KeyValuePair;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.CommonUtil;

public class DogFightHistoryInfo {
	public static DogFightHistoryInfo newInstance(int game_number, DogFightGameInfo gInfo){
		return newInstance()
				.set_game_number(game_number)
				.set_game_id(gInfo.get_game_id())
				.set_game_name(gInfo.get_game_name())
				.set_winner_id(0)
				.set_winner_members("")
				.set_winner_ticket_count(0)
				.set_winner_dividend(0D)
				.set_winner_price(0)
				.set_winner_item_id(0)
				.set_total_ticket_count(0)
				.set_total_price(0)
				.set_pure_total_price(0)
				.set_recovery_rate(0D)
				.set_recovery_price(0)
				.set_ticket_price(DogFightSettings.TICKET_PRICE)
				.create_tickets(gInfo);
	}
	
	private static DogFightHistoryInfo newInstance(){
		return new DogFightHistoryInfo();
	}

	private int m_game_number;
	private int m_game_id;
	private String m_game_name;
	private int m_winner_id;
	private String m_winner_members;
	private int m_winner_ticket_count;
	private double m_winner_dividend;
	private int m_winner_price;
	private int m_winner_item_id;
	private int m_total_ticket_count;
	private int m_total_price;
	private int m_pure_total_price;
	private double m_recovery_rate;
	private int m_recovery_price;
	private int m_ticket_width;
	private int m_ticket_price;
	private ArrayList<DogFightTicketInfo> m_tickets_info;

	private DogFightHistoryInfo(){}

	public DogFightHistoryInfo set_game_number(int game_number){
		m_game_number = game_number;
		return this;
	}
	public DogFightHistoryInfo set_game_id(int game_id){
		m_game_id = game_id;
		return this;
	}
	public DogFightHistoryInfo set_game_name(String game_name){
		m_game_name = game_name;
		return this;
	}
	public DogFightHistoryInfo set_winner_id(int winner_id){
		m_winner_id = winner_id;
		return this;
	}
	public DogFightHistoryInfo set_winner_members(String winner_members){
		m_winner_members = winner_members;
		return this;
	}
	public DogFightHistoryInfo set_winner_ticket_count(int winner_ticket_count){
		m_winner_ticket_count = winner_ticket_count;
		return this;
	}
	public DogFightHistoryInfo set_winner_dividend(double winner_dividend){
		m_winner_dividend = winner_dividend;
		return this;
	}
	public DogFightHistoryInfo set_winner_price(int winner_price){
		m_winner_price = winner_price;
		return this;
	}
	public DogFightHistoryInfo set_winner_item_id(int winner_item_id){
		m_winner_item_id = winner_item_id;
		return this;
	}
	public DogFightHistoryInfo set_total_ticket_count(int total_ticket_count){
		m_total_ticket_count = total_ticket_count;
		return this;
	}
	public DogFightHistoryInfo set_total_price(int total_price){
		m_total_price = total_price;
		return this;
	}
	public DogFightHistoryInfo set_pure_total_price(int pure_total_price){
		m_pure_total_price = pure_total_price;
		return this;
	}
	public DogFightHistoryInfo set_recovery_rate(double recovery_rate){
		m_recovery_rate = recovery_rate;
		return this;
	}
	public DogFightHistoryInfo set_recovery_price(int recovery_price){
		m_recovery_price = recovery_price;
		return this;
	}
	public DogFightHistoryInfo set_ticket_price(int ticket_price){
		m_ticket_price = ticket_price;
		return this;
	}
	public int get_game_number(){
		return m_game_number;
	}
	public int get_game_id(){
		return m_game_id;
	}
	public String get_game_name(){
		return m_game_name;
	}
	public int get_winner_id(){
		return m_winner_id;
	}
	public String get_winner_members(){
		return m_winner_members;
	}
	public int get_winner_ticket_count(){
		return m_winner_ticket_count;
	}
	public double get_winner_dividend(){
		return m_winner_dividend;
	}
	public int get_winner_price(){
		return m_winner_price;
	}
	public int get_winner_item_id(){
		return m_winner_item_id;
	}
	public int get_total_ticket_count(){
		return m_total_ticket_count;
	}
	public int get_total_price(){
		return m_total_price;
	}
	public int get_pure_total_price(){
		return m_pure_total_price;
	}
	public double get_recovery_rate(){
		return m_recovery_rate;
	}
	public int get_recovery_price(){
		return m_recovery_price;
	}
	public int get_ticket_width(){
		return m_ticket_width;
	}
	public int get_ticket_price(){
		return m_ticket_price;
	}
	public ArrayList<DogFightTicketInfo> get_tickets_info(){
		return m_tickets_info;
	}
	public DogFightHistoryInfo create_tickets(DogFightGameInfo gInfo){
		java.util.Set<Integer> keys = gInfo.get_fighters_keys();
		m_ticket_width = keys.size();
		m_tickets_info = new ArrayList<DogFightTicketInfo>(m_ticket_width);
		int[] tick_ids = DogFightTicketIdManager.getInstance().next_ids(m_ticket_width);
		
        L1Item template = CObjectDB.LoadItem().getTemplate(DogFightSettings.TICKET_ITEM_ID);
		int idx = 0;
		double win_rate = create_win_rate();
		for(Integer corner_id : keys){
			String corner_name = gInfo.get_corner_name(corner_id);
            L1Item ticket_template = CObjectDB.LoadItem().create_dogfight_ticket(template, tick_ids[idx], String.format(DogFightSettings.TICKET_NAME,
					DogFightHistory.getInstance().get_current_game_number(), corner_id + 1, corner_name));
			int count = DogFightSettings.DEFAULT_TICKET_COUNT;
			int price = count * get_ticket_price();
			m_tickets_info.add(
					DogFightTicketInfo
					.newInstance()
					.set_corner_id(corner_id)
					.set_corner_name(corner_name)
					.set_template(ticket_template)
					//.set_win_rate(create_win_rate())
					.set_win_rate(win_rate)
					.update_ticket_count(count, price)
					);

			m_total_ticket_count += count;
			m_total_price += price;
			m_pure_total_price += price;
			++idx;
		}
		update_ticket_dividend();
		return this;
	}
	
	public DogFightHistoryInfo do_calculate(int corner_id){
	    for(DogFightTicketInfo tInfo : m_tickets_info){
	        if(tInfo.get_corner_id() != corner_id)
	            continue;

	        set_winner_item_id(tInfo.get_template().getItemId());
	        long payout = (long)Math.floor((double)tInfo.get_price() * tInfo.get_dividend());
	        if(payout > get_pure_total_price()){
	            payout = get_pure_total_price();
	        }
	        if(payout > Integer.MAX_VALUE){
	            payout = Integer.MAX_VALUE;
	        }
	        set_winner_price((int)payout);
	        set_winner_ticket_count(tInfo.get_ticket_count());
	        set_winner_dividend(tInfo.get_dividend());
	        set_recovery_price(get_pure_total_price() - get_winner_price());
	        set_recovery_rate(100D - (((double)get_winner_price() / (double)get_pure_total_price()) * 100));
	        break;
	    }
	    return this;
	}
	
	public synchronized ArrayList<DogFightTicketLogInfo> on_buy_ticket(L1PcInstance pc, ArrayList<KeyValuePair<Integer, Integer>> pairs, int total_price){
	    ArrayList<DogFightTicketLogInfo> logs_info = new ArrayList<DogFightTicketLogInfo>(pairs == null ? 0 : pairs.size());
	    boolean paid = false;
	    try{
	        // ── 사전 검증 (아무 상태도 변경 안 함) ──
	        if(pairs == null || pairs.isEmpty()){
	            pc.sendPackets("구입할 티켓이 없습니다.");
	            return null;
	        }

	        // 클라가 보낸 total_price 신뢰 금지 — 서버에서 직접 계산해서 대조
	        int expected_price = 0;
	        for(KeyValuePair<Integer, Integer> pair : pairs){
	            if(pair == null || pair.value <= 0){
	                pc.sendPackets("구입 수량이 올바르지 않습니다.");
	                return null;
	            }
	            DogFightTicketInfo tInfo = m_tickets_info.get(pair.key);
	            if(tInfo == null){
	                pc.sendPackets("존재하지 않는 코너입니다.");
	                return null;
	            }
	            expected_price += get_ticket_price() * pair.value;
	        }
	        if(expected_price != total_price){
	            pc.sendPackets("구입 가격이 올바르지 않습니다.");
	            return null;
	        }

	        if(!pc.getInventory().checkItem(L1ItemId.ADENA, total_price)){
	            pc.sendPackets("구입에 필요한 아데나가 부족합니다.");
	            return null;
	        }

	        // 티켓 인스턴스 선생성 — 템플릿 문제는 이 단계에서 다 터지게 함
	        ArrayList<L1ItemInstance> tickets = new ArrayList<L1ItemInstance>(pairs.size());
	        for(KeyValuePair<Integer, Integer> pair : pairs){
	            DogFightTicketInfo tInfo = m_tickets_info.get(pair.key);
                L1ItemInstance ticket = CObjectDB.LoadItem().createItem(tInfo.get_template());
	            ticket.setIdentified(true);
	            ticket.setCount(pair.value);
	            tickets.add(ticket);
	        }

	        // ── 결제 ──
	        if(!pc.getInventory().consumeItem(L1ItemId.ADENA, total_price)){
	            pc.sendPackets("구입에 필요한 아데나를 소비 할 수 없습니다.");
	            return null;
	        }
	        paid = true;

	        // ── 지급 + 상태 갱신 (여기서부턴 실패하면 안 되는 구간) ──
	        for(int i = 0; i < pairs.size(); ++i){
	            KeyValuePair<Integer, Integer> pair = pairs.get(i);
	            L1ItemInstance ticket = tickets.get(i);
	            DogFightTicketInfo tInfo = m_tickets_info.get(pair.key);

	            pc.getInventory().storeItem(ticket);
	            L1World.getInstance().storeObject(ticket);

	            m_total_ticket_count += pair.value;
	            int price = get_ticket_price() * pair.value;
	            DogFightInstance fight = DogFightGame.getDogFight(tInfo.get_corner_id());
	            if(fight != null){
	                fight.addTotalPrice(price);
	            }
	            tInfo.update_ticket_count(pair.value, price);

	            logs_info.add(DogFightTicketLogInfo
	                    .newInstance()
	                    .set_action_date(CommonUtil.getLocalTime())
	                    .set_character_object_id(pc.getId())
	                    .set_character_name(pc.getName())
	                    .set_item_id(ticket.getItemId())
	                    .set_item_name(ticket.getName())
	                    .set_count(pair.value)
	                    .set_price(get_ticket_price())
	                    .set_total_price(price)
	                    );
	        }
	        m_total_price += (total_price - (total_price * DogFightSettings.EXCHANGE_RATE));
	        m_pure_total_price += total_price;
	        update_ticket_dividend();

	    }catch(Exception e){
	        if(paid){
	            try{
	                pc.getInventory().storeItem(L1ItemId.ADENA, total_price);
	                pc.sendPackets("구매 처리 중 오류가 발생하여 아데나를 환불했습니다.");
	            }catch(Exception ignore){}
	        }else{
	            pc.sendPackets("구매에 실패했습니다.");
	        }
	        e.printStackTrace();
	        return null; 
	    }
	    return logs_info;
	}
	private void update_ticket_dividend(){
		for(DogFightTicketInfo tInfo : m_tickets_info)
			tInfo.update_ticket_dividend(get_total_price());
	}
	
	private double create_win_rate(){
	    int corners = m_tickets_info != null ? m_tickets_info.size() : 0;
	    return corners <= 0 ? 0D : 100D / corners;
	}
}

