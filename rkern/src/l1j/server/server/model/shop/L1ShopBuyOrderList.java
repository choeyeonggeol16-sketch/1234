package l1j.server.server.model.shop;

import java.util.ArrayList;
import java.util.List;

import l1j.server.Config;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1ShopItem;

public class L1ShopBuyOrderList {

    private final L1Shop _shop;
    private final List<L1ShopBuyOrder> _list;
    private final L1TaxCalculator _taxCalc;

    private long _totalWeight = 0L;
    private long _totalPrice = 0L;
    private long _totalPriceTaxIncluded = 0L;

    private int bugok = 0;

    private static final boolean COMPAT_SPLIT_NONSTACKABLE = true;

    // 상점 패킷 방어 상수
    private static final int MAX_BUY_COUNT = 9999;
    private static final long MAX_ADENA = 2_000_000_000L; 

    L1ShopBuyOrderList(L1Shop shop) {
        _shop = shop;
        _taxCalc = new L1TaxCalculator(shop.getNpcId());
        _list = new ArrayList<L1ShopBuyOrder>(8); 
    }

    public void add(int orderNumber, int count, L1PcInstance pc) {
        if (bugok != 0) return; // 한번이라도 비정상 감지되면 더 쌓지 않음
        if (count <= 0 || count > MAX_BUY_COUNT) { bugok = 1; return; }

        final List<L1ShopItem> selling = _shop.getSellingItems();
        if (selling == null || selling.isEmpty()) { bugok = 1; return; }
        if (orderNumber < 0 || orderNumber >= selling.size()) { bugok = 1; return; }

        final L1ShopItem shopItem = selling.get(orderNumber);
        if (shopItem == null || shopItem.getItem() == null) { bugok = 1; return; }

        final int pack = Math.max(1, shopItem.getPackCount());
        final long unitPriceL = safeUnitPrice(shopItem);
        if (unitPriceL <= 0L || unitPriceL > MAX_ADENA) { bugok = 1; return; }

        // 실제 지급 개수(팩 포함)
        final long giveCountL;
        try {
            giveCountL = Math.multiplyExact((long) count, (long) pack);
        } catch (ArithmeticException ae) {
            bugok = 1;
            return;
        }
        if (giveCountL <= 0L || giveCountL > MAX_ADENA) { // 과도한 입력 방어
            bugok = 1;
            return;
        }

        // 총 가격(세금 미포함)
        final long addPrice;
        try {
            addPrice = Math.multiplyExact(unitPriceL, (long) count);
        } catch (ArithmeticException ae) {
            bugok = 1;
            return;
        }

        // 세금 포함 가격(여기도 long으로 안전하게)
        final long taxedUnit = (long) _taxCalc.layTax((int) clampToInt(unitPriceL));
        final long addPriceTaxed;
        try {
            addPriceTaxed = Math.multiplyExact(taxedUnit, (long) count);
        } catch (ArithmeticException ae) {
            bugok = 1;
            return;
        }

        // weight 누적
        final long addWeight;
        try {
            long w = (long) shopItem.getItem().getWeight();
            addWeight = Math.multiplyExact(Math.multiplyExact(w, (long) count), (long) pack);
        } catch (ArithmeticException ae) {
            bugok = 1;
            return;
        }

        // 누적 합계
        _totalPrice = safeAdd(_totalPrice, addPrice);
        _totalPriceTaxIncluded = safeAdd(_totalPriceTaxIncluded, addPriceTaxed);
        _totalWeight = safeAdd(_totalWeight, addWeight);

        // 총액 한도 방어
        if (_totalPrice < 0L || _totalPrice > (long) Integer.MAX_VALUE * 4L) {
            bugok = 1;
            return;
        }
        if (_totalPriceTaxIncluded < 0L || _totalPriceTaxIncluded > (long) Integer.MAX_VALUE * 4L) {
            bugok = 1;
            return;
        }

        // 주문 추가
        if (shopItem.getItem().isStackable()) {
            ensureCapacity(1);
            _list.add(new L1ShopBuyOrder(shopItem, (int) clampToInt(giveCountL), orderNumber));
            return;
        }

        if (!COMPAT_SPLIT_NONSTACKABLE) {
            // 성능 모드: 1개 주문에 수량을 넣음(하류 코드가 count=1 가정이면 켜면 안 됨)
            ensureCapacity(1);
            _list.add(new L1ShopBuyOrder(shopItem, (int) clampToInt(giveCountL), orderNumber));
            return;
        }
        
        // 호환 모드: 기존처럼 1개씩 쪼개서 넣기
        if (giveCountL > 100_000L) { // 비정상 보호
            bugok = 1;
            return;
        }
        ensureCapacity((int) giveCountL);
        for (int i = 0; i < (int) giveCountL; i++) {
            _list.add(new L1ShopBuyOrder(shopItem, 1, orderNumber));
        }
    }

    private long safeUnitPrice(L1ShopItem shopItem) {
        // Config 배율 적용
        double raw = shopItem.getPrice() * Config.RATE_SHOP_SELLING_PRICE;
        if (raw <= 0) return -1L;
        // double -> long 변환 시 과대값 방어
        long v = (long) raw;
        return v;
    }

    private static long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ae) {
            return Long.MIN_VALUE; // 상위에서 bugok 처리
        }
    }

    private void ensureCapacity(int add) {
        // ArrayList는 ensureCapacity가 public이라 직접 호출 가능
        if (_list instanceof ArrayList) {
            ((ArrayList<L1ShopBuyOrder>) _list).ensureCapacity(_list.size() + add);
        }
    }

    private static int clampToInt(long v) {
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (v < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) v;
    }

    public List<L1ShopBuyOrder> getList() {
        return _list;
    }

    public int BugOk() {
        return bugok;
    }

    public int getTotalWeight() {
        return clampToInt(_totalWeight);
    }

    public int getTotalPrice() {
        return clampToInt(_totalPrice);
    }

    public int getTotalPriceTaxIncluded() {
        return clampToInt(_totalPriceTaxIncluded);
    }

    L1TaxCalculator getTaxCalculator() {
        return _taxCalc;
    }
}
