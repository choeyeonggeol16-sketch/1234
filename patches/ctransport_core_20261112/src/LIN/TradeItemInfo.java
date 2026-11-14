package LIN;

import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * 거래 아이템 정보 서브메시지 — 0x0407(개인상점)/0x0409(교환) 공용.
 * index(1) count(2) price(3) item_info(4) trade_info(5) buy_count(6)
 */
public final class TradeItemInfo {

    private Integer index;      // 1 int32
    private Integer count;      // 2 int32
    private Integer price;      // 3 int32
    private LIN.ItemInfo itemInfo;  // 4 message
    private Integer tradeInfo;  // 5 int32
    private Integer buyCount;   // 6 int32

    private TradeItemInfo() {}
    public static LIN.TradeItemInfo newInstance() { return new LIN.TradeItemInfo(); }

    public LIN.TradeItemInfo set_index(int v)          { index = v; return this; }
    public LIN.TradeItemInfo set_count(int v)          { count = v; return this; }
    public LIN.TradeItemInfo set_price(int v)          { price = v; return this; }
    public LIN.TradeItemInfo set_item_info(LIN.ItemInfo v) { itemInfo = v; return this; }
    public LIN.TradeItemInfo set_trade_info(int v)     { tradeInfo = v; return this; }
    public LIN.TradeItemInfo set_buy_count(int v)      { buyCount = v; return this; }

    public void body(ProtoWriter w) {
        if (index != null)     w.int32(1, index);
        if (count != null)     w.int32(2, count);
        if (price != null)     w.int32(3, price);
        if (itemInfo != null) {
            w.msg(4);
            itemInfo.body(w);
            w.end();
        }
        if (tradeInfo != null) w.int32(5, tradeInfo);
        if (buyCount != null)  w.int32(6, buyCount);
    }
}