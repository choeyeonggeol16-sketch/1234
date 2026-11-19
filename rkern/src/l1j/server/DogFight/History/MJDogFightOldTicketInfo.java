package l1j.server.DogFight.History;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MJDogFightOldTicketInfo {
    public static MJDogFightOldTicketInfo newInstance(MJDogFightHistoryInfo hInfo){
        return newInstance()
                .set_item_id(hInfo.get_winner_item_id())
                .set_dividend(hInfo.get_winner_dividend())
                .set_price(hInfo.get_ticket_price());
    }

    public static MJDogFightOldTicketInfo newInstance(ResultSet rs) throws SQLException{
        return newInstance()
                .set_item_id(rs.getInt("winner_item_id"))
                .set_dividend(rs.getDouble("winner_dividend"))
                .set_price(rs.getInt("ticket_price"));
    }

    private static MJDogFightOldTicketInfo newInstance(){
        return new MJDogFightOldTicketInfo();
    }

    private int m_item_id;
    private double m_dividend;
    private int m_price;
    private MJDogFightOldTicketInfo(){}

    public MJDogFightOldTicketInfo set_item_id(int item_id){
        m_item_id = item_id;
        return this;
    }
    public MJDogFightOldTicketInfo set_dividend(double dividend){
        m_dividend = dividend;
        return this;
    }
    public MJDogFightOldTicketInfo set_price(int price){
        m_price = Math.max(0, price);
        return this;
    }
    public int get_item_id(){
        return m_item_id;
    }
    public double get_dividend(){
        return m_dividend;
    }
    public int get_price(){
        return m_price;
    }

    /**
     * 당첨 티켓 1장 단가 × 장수.
     * 버림을 장수 곱셈 전에 적용 — 항상 집이 유리한 방향 (Σ지급 ≤ 정산액 증명됨).
     * long 연산으로 오버플로 방지.
     */
    public int get_price_by_ticket(int count){
        if(count <= 0){
            return 0;
        }
        double dividend = m_dividend;
        if(dividend <= 0D){
            return 0;
        }
        long per_ticket = (long)Math.floor((double)m_price * dividend);
        long total = per_ticket * (long)count;
        if(total > Integer.MAX_VALUE){
            total = Integer.MAX_VALUE;
        }
        return (int)total;
    }
}