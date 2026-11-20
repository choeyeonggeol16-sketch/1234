package l1j.server.server.model.shop;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

class L1ShopSellOrder {
    private final L1AssessedItem _item;
    private final int _count;

    public L1ShopSellOrder(L1AssessedItem item, int count) {
        _item = item;
        _count = count;
    }

    public L1AssessedItem getItem() {
        return _item;
    }

    public int getCount() {
        return _count;
    }
}

public class L1ShopSellOrderList {

    private final L1Shop _shop;
    private final L1PcInstance _pc;
    private List<L1ShopSellOrder> _list = List.of();

    private int bugok = 0; // 상점 판매 비셔스 방어

    // 튜닝/정책
    private static final int MAX_SELL_COUNT = 10_000; // 서버 정책에 맞게
    private static final int MAX_SELL_COUNT_NORMAL = 500; // 일반 아이템 상점판매 500 제한
    private static final int EXCEPTION_ITEM_ID = 41246; // 예외 아이템

    L1ShopSellOrderList(L1Shop shop, L1PcInstance pc) {
        _shop = shop;
        _pc = pc;
    }

    public void add(int itemObjectId, int count, L1PcInstance ignoredPcParam) {
        if (bugok != 0) return; // 한번 이상 감지되면 더 받지 않음

        // 기본 입력 방어
        if (itemObjectId <= 0 || count <= 0 || count > MAX_SELL_COUNT) {
            bugok = 1;
            return;
        }

        // _pc 기준으로 통일 (외부 param 혼용 방지)
        final L1ItemInstance item = _pc.getInventory().getItem(itemObjectId);
        if (item == null || item.getItem() == null) {
            bugok = 1;
            return;
        }

        // 수량/스택 검증
        long have;
        try { have = item.getCount(); } catch (Throwable t) { bugok = 1; return; }
        if (have <= 0 || have < (long) count) {
            bugok = 1;
            return;
        }
        if (!item.isStackable() && count != 1) {
            bugok = 1;
            return;
        }

        // 원래 있던 500 제한 로직이 비어있어서 실제로 적용
        if (count > MAX_SELL_COUNT_NORMAL && item.getItemId() != EXCEPTION_ITEM_ID) {
            bugok = 1;
            return;
        }

        // 착용/특수 상태 방어 
        try {
            if (item.isDollOn()) {
                bugok = 1;
                return;
            }
        } catch (Throwable t) {
            bugok = 1;
            return;
        }

        try {
            if (item.getBless() >= 128) {
                bugok = 1;
                return;
            }
        } catch (Throwable t) {
            bugok = 1;
            return;
        }

        // 평가(가격) 산출
        final L1AssessedItem assessedItem;
        try {
            assessedItem = _shop.assessItem(item);
        } catch (Throwable t) {
            bugok = 1;
            return;
        }

        if (assessedItem == null) {
            // 예외 던져서 서버 흐름 깨지지 않게
            bugok = 1;
            return;
        }

        final L1ShopSellOrder order = new L1ShopSellOrder(assessedItem, count);
        if (_list.isEmpty()) {
            _list = List.of(order);
        } else {
            if (!(_list instanceof ArrayList)) {
                final ArrayList<L1ShopSellOrder> expanded = new ArrayList<>(8);
                expanded.add(_list.getFirst());
                _list = expanded;
            }
            _list.add(order);
        }
    }

    // 상점 판매 비셔스 방어
    public int BugOk() {
        return bugok;
    }

    L1PcInstance getPc() {
        return _pc;
    }

    List<L1ShopSellOrder> getList() {
        return _list;
    }
}
