package l1j.server.server.model.shop;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;

/** The wire indices shown to one customer. Retains no shop or item objects. */
public final class PlayerShopView {
    private static final int FIELDS = 4;
    private final long shopIdentity;
    private final int shopVersion;
    private final boolean purchase;
    private final int[] rows;
    private int size;

    public PlayerShopView(L1PcInstance shop, boolean purchase, int capacity) {
        shopIdentity = CWorld.getInstance().smartIdOf(shop);
        shopVersion = shop.getPrivateShopVersion();
        this.purchase = purchase;
        rows = new int[capacity * FIELDS];
    }

    /** Populated on the customer's CIO before publishing the view to the session. */
    public void add(int listingId, int customerItemId, int price, int count) {
        int offset = size * FIELDS;
        rows[offset] = listingId;
        rows[offset + 1] = customerItemId;
        rows[offset + 2] = price;
        rows[offset + 3] = count;
        size++;
    }

    public int size() { return size; }
    public int listingId(int row) { return rows[row * FIELDS]; }
    public int customerItemId(int row) { return rows[row * FIELDS + 1]; }
    public int price(int row) { return rows[row * FIELDS + 2]; }
    public int count(int row) { return rows[row * FIELDS + 3]; }

    public boolean matches(L1PcInstance shop, boolean purchase) {
        return this.purchase == purchase && shop.isPrivateShop()
                && shopVersion == shop.getPrivateShopVersion() && shopIdentity != 0
                && CWorld.getInstance().findObjectBySmartId(shopIdentity) == shop;
    }
}
