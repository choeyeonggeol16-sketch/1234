package l1j.server.server.model.character.combat.arena.dogfight.history;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.game.DogFightGameManager;
import l1j.server.server.model.character.combat.arena.dogfight.history.log.DogFightTicketLogInfo;
import l1j.server.server.model.character.combat.arena.dogfight.history.log.DogFightTicketLogger;
import l1j.server.server.model.character.combat.arena.dogfight.instance.DogFightInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_DogFightBoard;
import l1j.server.server.model.network.core.sc.packet.SC_DogFightBuyList;
import l1j.server.server.model.network.core.sc.packet.SC_DogFightSellList;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NoSell;
import l1j.server.server.shared.KeyValuePair;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.CommonUtil;

public class DogFightHistory {
    private static final Logger _log = Logger.getLogger(DogFightHistory.class.getName());

    /** 패킷 한 번에 처리할 최대 페어/슬롯 수 */
    private static final int MAX_PACKET_SLOTS = 64;

    private static volatile DogFightHistory m_instance;

    public static DogFightHistory getInstance(){
        if(m_instance == null){
            synchronized(DogFightHistory.class){
                if(m_instance == null){
                    m_instance = new DogFightHistory();
                }
            }
        }
        return m_instance;
    }

    private int m_current_game_number;
    private volatile DogFightHistoryInfo m_current_history_info;
    private DogFightGameManager m_current_manager;
    private HashMap<Integer, DogFightOldTicketInfo> m_winners_item_id;

    private DogFightHistory(){
        m_winners_item_id = new HashMap<Integer, DogFightOldTicketInfo>();
        Selector.exec("select max(game_number) + 1 as next_number from dogfight_history", new FullSelectorHandler(){
            @Override
            public void result(ResultSet rs) throws Exception {
                if(rs.next()){
                    m_current_game_number = rs.getInt("next_number");
                }else{
                    m_current_game_number = 0;
                }
            }
        });
        Selector.exec("select winner_item_id, winner_dividend, ticket_price from dogfight_history", new FullSelectorHandler(){
            @Override
            public void result(ResultSet rs) throws Exception {
                while(rs.next()){
                    DogFightOldTicketInfo otInfo = DogFightOldTicketInfo.newInstance(rs);
                    m_winners_item_id.put(otInfo.get_item_id(), otInfo);
                }
            }
        });
    }

    public int get_current_game_number(){
        return m_current_game_number;
    }

    public DogFightHistoryInfo create_new_history(DogFightGameManager manager){
        m_current_manager = manager;
        DogFightHistoryInfo info = DogFightHistoryInfo.newInstance(++m_current_game_number, manager.get_game_info());
        m_current_history_info = info;
        return info;
    }

    public DogFightHistoryInfo get_current_history(){
        return m_current_history_info;
    }

    public DogFightGameManager get_current_manager(){
        return m_current_manager;
    }

    public ArrayList<DogFightTicketInfo> get_current_tickets_info(){
        DogFightHistoryInfo info = m_current_history_info;
        return info == null ? null : info.get_tickets_info();
    }

    public DogFightOldTicketInfo get_old_ticket_info(int item_id){
        return m_winners_item_id.get(item_id);
    }

    public DogFightInstance get_leader(int corner_id){
        if(m_current_manager == null)
            return null;
        return m_current_manager.get_leader(corner_id);
    }

    public String get_leader_name(int corner_id){
        DogFightInstance dogfight = get_leader(corner_id);
        return dogfight == null ? "" : dogfight.getName();
    }

    public boolean is_fight(){
        return m_current_manager != null && m_current_manager.is_fight();
    }

    public boolean is_run(){
        return m_current_manager != null && m_current_manager.is_run();
    }

    /**★ 현재 진행 중 회차의 티켓인지 — 판매 삭제 방지용 */
    private boolean is_current_round_ticket(int item_id){
        DogFightHistoryInfo info = m_current_history_info;
        if(info == null)
            return false;
        ArrayList<DogFightTicketInfo> tickets = info.get_tickets_info();
        if(tickets == null)
            return false;
        for(DogFightTicketInfo tInfo : tickets){
            L1Item template = tInfo.get_template();
            if(template != null && template.getItemId() == item_id)
                return true;
        }
        return false;
    }

    public ArrayList<KeyValuePair<Integer, Integer>> find_sellable_tickets(L1PcInstance pc){
        ArrayList<KeyValuePair<Integer, Integer>> pairs = new ArrayList<KeyValuePair<Integer, Integer>>();
        for(L1ItemInstance item : pc.getInventory().getItems()){
            int item_id = item.getItemId();
            if(!DogFightTicketIdManager.getInstance().is_ticket_item_id(item_id))
                continue;
            //  진행 중 회차 티켓은 판매 목록에서 제외 — 0원 삭제 피해 방지
            if(is_current_round_ticket(item_id))
                continue;

            DogFightOldTicketInfo otInfo = get_old_ticket_info(item_id);
            pairs.add(otInfo == null ?
                    new KeyValuePair<Integer, Integer>(item.getId(), 0) :
                    new KeyValuePair<Integer, Integer>(item.getId(), otInfo.get_price_by_ticket(1)));
        }
        return pairs;
    }

    public void do_cancel_game(){
        m_current_history_info = null;
        m_current_manager = null;
    }

    public void update_game(){
        // 로컬 캡처 — 처리 중 다른 스레드가 null로 바꿔도 안전
        final DogFightHistoryInfo info = m_current_history_info;
        if(info == null)
            return;

        final int gameNumber = info.get_game_number();
        final int gameId = info.get_game_id();
        final String gameName = info.get_game_name();
        final int winnerId = info.get_winner_id();
        final String winnerMembers = info.get_winner_members();
        final int winnerTicketCount = info.get_winner_ticket_count();
        final String winnerDividend = String.format(Locale.US, "%.2f", info.get_winner_dividend());
        final int winnerPrice = info.get_winner_price();
        final int winnerItemId = info.get_winner_item_id();
        final int totalTicketCount = info.get_total_ticket_count();
        final int totalPrice = info.get_pure_total_price();
        final String recoveryRate = String.format(Locale.US, "%.2f", info.get_recovery_rate());
        final int recoveryPrice = info.get_recovery_price();
        final int ticketPrice = info.get_ticket_price();

        try {
            CacheD.getInstance().submitDbJob(() -> {
                try (Connection con = DatabaseFactory.getInstance().getConnection();
                     PreparedStatement pstm = con.prepareStatement(
                             "REPLACE INTO dogfight_history SET "
                                     + "game_number=?, game_id=?, game_name=?, winner_id=?, "
                                     + "winner_members=?, winner_ticket_count=?, winner_dividend=?, "
                                     + "winner_price=?, winner_item_id=?, total_ticket_count=?, "
                                     + "total_price=?, recovery_rate=?, recovery_price=?, ticket_price=?")) {
                    pstm.setInt(1, gameNumber);
                    pstm.setInt(2, gameId);
                    pstm.setString(3, gameName);
                    pstm.setInt(4, winnerId);
                    pstm.setString(5, winnerMembers);
                    pstm.setInt(6, winnerTicketCount);
                    pstm.setString(7, winnerDividend);
                    pstm.setInt(8, winnerPrice);
                    pstm.setInt(9, winnerItemId);
                    pstm.setInt(10, totalTicketCount);
                    pstm.setInt(11, totalPrice);
                    pstm.setString(12, recoveryRate);
                    pstm.setInt(13, recoveryPrice);
                    pstm.setInt(14, ticketPrice);
                    pstm.executeUpdate();
                } catch (SQLException e) {
                    throw new IllegalStateException("DogFight history write failed: round=" + gameNumber, e);
                }
            });
        } catch (RuntimeException e) {
            _log.log(Level.SEVERE, "[DogFight] CacheD 기록 요청 실패 - 회차: " + gameNumber, e);
        } finally {
            //  DB 실패와 무관하게 당첨 티켓 판매 정보는 등록 — DB만 터져도 유저 티켓이 죽지 않게
            DogFightOldTicketInfo otInfo = DogFightOldTicketInfo.newInstance(info);
            m_winners_item_id.put(otInfo.get_item_id(), otInfo);
            m_current_history_info = null;
        }
    }

    public void on_show_ticket_ration_view(L1PcInstance pc, L1NpcInstance npc){
        if(!is_run() || m_current_history_info == null){
            SC_NPCTalkReturn.send(pc, npc.getId(), "maeno3");
            return;
        }
        if(is_fight()){
            SC_NPCTalkReturn.send(pc, npc.getId(), "maeno3");
            return;
        }
        SC_DogFightBoard.send(pc, pc.isGm(), m_current_history_info.get_tickets_info());
    }

    public void on_show_ticket_view(L1PcInstance pc, L1NpcInstance npc){
        if(!is_run() || m_current_history_info == null){
            SC_NPCTalkReturn.send(pc, npc.getId(), "maeno3");
            return;
        }
        if(is_fight()){
            SC_NPCTalkReturn.send(pc, npc.getId(), "maeno3");
            return;
        }
        SC_DogFightBuyList.send(pc, npc.getId(), m_current_history_info.get_tickets_info(), m_current_history_info.get_ticket_price());
    }

    public void on_show_sells_view(L1PcInstance pc, L1NpcInstance npc){
        if(is_fight()){
            SC_NPCTalkReturn.send(pc, npc.getId(), "maeno3");
            return;
        }

        ArrayList<KeyValuePair<Integer, Integer>> pairs = find_sellable_tickets(pc);
        if(pairs.isEmpty()){
            SC_NoSell.send(pc, npc);
            return;
        }
        SC_DogFightSellList.send(pc, npc.getId(), pairs);
    }

    public void on_sell_ticket(L1PcInstance pc, int[] param1, long[] param2, int size, int npc_object_id){
        if(is_fight()){
            SC_NPCTalkReturn.send(pc, npc_object_id, "maeno3");
            return;
        }
        if(param1 == null || param2 == null || size <= 0 || param1.length < size || param2.length < size){
            return;
        }
        //  size 검증 — 클라 값이라 무조건 상한, 할당 전에 체크
        if(size <= 0 || size > MAX_PACKET_SLOTS){
            return;
        }

        ArrayList<DogFightTicketLogInfo> logs_info = new ArrayList<DogFightTicketLogInfo>(size);
        long total_price = 0;
        try {
            for(int i = 0; i < size; ++i){
                int item_object_id = param1[i];
                int item_count = (int)param2[i];
                if(item_object_id <= 0 || item_count <= 0)
                    return;

                L1ItemInstance item = pc.getInventory().GetByServerID(item_object_id);
                if(item == null || item.getItem() == null)
                    continue;

                if(item.getCount() < item_count){
                    pc.sendPackets("아이템 판매에 실패했습니다.");
                    _log.warning(String.format("%s님 투견 티켓 판매 수량 버그 시도. (아이템: %s, 보유: %d, 시도: %d)",
                            pc.getName(), item.getName(), item.getCount(), item_count));
                    return;
                }
                if(!DogFightTicketIdManager.getInstance().is_ticket_item_id(item.getItemId())){
                    pc.sendPackets("아이템 판매에 실패했습니다.");
                    _log.warning(String.format("%s님이 투견 티켓이 아닌 아이템(%s)을 티켓 상인에게 판매 시도.",
                            pc.getName(), item.getName()));
                    return;
                }
                //  진행 중 회차 티켓 차단 — 0원에 삭제되는 피해 방지
                if(is_current_round_ticket(item.getItemId())){
                    pc.sendPackets("진행 중인 회차의 티켓은 판매할 수 없습니다.");
                    return;
                }

                DogFightOldTicketInfo otInfo = get_old_ticket_info(item.getItemId());
                int removed_count = pc.getInventory().removeItem(item, item_count);
                if(removed_count <= 0){
                    pc.sendPackets("아이템 판매에 실패했습니다.");
                    _log.warning(String.format("%s님 투견 티켓 판매 실패. (아이템: %s, 시도: %d, 삭제: %d)",
                            pc.getName(), item.getName(), item_count, removed_count));
                    return;
                }

                int price = otInfo == null ? 0 : otInfo.get_price_by_ticket(removed_count);
                total_price += price;
                if(total_price > Integer.MAX_VALUE){
                    // 지급 가능한 만큼만 받고 차단
                    _log.warning(String.format("%s님 투견 티켓 판매 아데나 상한 도달. (총액: %d)", pc.getName(), total_price));
                    break;
                }
                if(price <= 0)
                    continue; // 낙첨 티켓 무상 회수 — 로그 없음

                logs_info.add(DogFightTicketLogInfo
                        .newInstance()
                        .set_action_date(CommonUtil.getLocalTime())
                        .set_character_object_id(pc.getId())
                        .set_character_name(pc.getName())
                        .set_item_id(item.getItemId())
                        .set_item_name(item.getName())
                        .set_count(removed_count)
                        .set_price(otInfo.get_price())
                        .set_total_price(price)
                        );
            }
        } finally {
            // ★루프 도중 예외가 나도 이미 삭제된 티켓만큼은 반드시 지급
            if(total_price > 0){
                pc.getInventory().CreateOrAddItem(L1ItemId.ADENA, (int)Math.min(total_price, Integer.MAX_VALUE));
            }
        }

        if(!logs_info.isEmpty())
            DogFightTicketLogger.do_update(logs_info, true);
    }

    public void on_buy_ticket(L1PcInstance pc, int[] param1, long[] param2, int size){
        if(!check_state(pc))
            return;

        DogFightHistoryInfo info = m_current_history_info;
        if(info == null){
            pc.sendPackets("현재 티켓을 구매할 수 없습니다.");
            return;
        }
        if(param1 == null || param2 == null || size <= 0 || param1.length < size || param2.length < size){
            return;
        }

        int ticket_width = info.get_ticket_width();
        if(size > Math.min(ticket_width, MAX_PACKET_SLOTS)){
            return;
        }

        ArrayList<KeyValuePair<Integer, Integer>> pairs = new ArrayList<KeyValuePair<Integer, Integer>>(size);
        long total_price = 0;
        for(int i = 0; i < size; ++i){
            KeyValuePair<Integer, Integer> pair = new KeyValuePair<Integer, Integer>();
            pair.key = param1[i];
            pair.value = (int)param2[i];  

            if(pair.value <= 0) {
                break;
            }
            if(pair.value >= 10000){
                pc.sendPackets("구매 수량이 너무 많습니다.");
                return;
            }
            if(pair.key < 0 || pair.key >= ticket_width){
                pc.sendPackets("존재하지 않는 코너입니다.");
                return;
            }
            long price = (long)DogFightSettings.TICKET_PRICE * pair.value;
            total_price += price;
            if(total_price > Integer.MAX_VALUE){
                pc.sendPackets("구매 수량이 너무 많습니다.");
                return;
            }
            pairs.add(pair);
        }
        if(total_price <= 0){
            pc.sendPackets("구매 수량이 너무 많습니다.");
            return;
        }

        int final_price = (int)total_price;
        if(!pc.getInventory().HaveItemByClassId(L1ItemId.ADENA, final_price)){
            pc.sendPackets("구입에 필요한 아데나가 부족합니다.");
            return;
        }

        ArrayList<DogFightTicketLogInfo> logs_info = info.on_buy_ticket(pc, pairs, final_price);
        if(logs_info == null || logs_info.isEmpty()) {
            return;
        }
        DogFightTicketLogger.do_update(logs_info, false);
    }

    private boolean check_state(L1PcInstance pc){
        if(!is_run() || m_current_history_info == null){
            pc.sendPackets("현재 티켓을 구매할 수 없습니다.");
            return false;
        }
        if(is_fight()){
            pc.sendPackets("이미 경기가 진행중입니다.");
            return false;
        }
        if(pc.서버다운중){
            pc.sendPackets("서버 다운이 진행중입니다.");
            return false;
        }
        return true;
    }
}