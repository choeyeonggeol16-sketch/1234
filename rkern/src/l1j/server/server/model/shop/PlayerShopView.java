package l1j.server.server.model.shop;

import java.util.Arrays;

import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** The wire indices shown to one customer. Retains no shop or item objects. */
public final class PlayerShopView {
    public static final int MAX_ROWS = 255;
    private static final int FIELDS = 4;
    private final long viewerIdentity;
    private final int viewerStamp;
    private final long shopIdentity;
    private final int shopObjectId;
    private final int shopVersion;
    private final boolean purchase;
    private int[] rows;
    private int size;

    public PlayerShopView(L1PcInstance viewer, L1PcInstance shop, boolean purchase, int capacity) {
        final CWorld world = CWorld.getInstance();
        viewerIdentity = world.smartIdOf(viewer);
        viewerStamp = viewer.getSessionStamp();
        shopIdentity = world.smartIdOf(shop);
        shopObjectId = shop.getId();
        shopVersion = shop.getPrivateShopVersion();
        this.purchase = purchase;
        rows = new int[Math.clamp(capacity, 1, MAX_ROWS) * FIELDS];
    }

    /** Populated on the customer's CIO before publishing the view to the session. */
    public void add(int listingId, int customerItemId, int price, int count) {
        final int offset = size * FIELDS;
        if (size == MAX_ROWS) throw new IllegalStateException("Player shop view row limit");
        if (offset == rows.length) rows = Arrays.copyOf(rows, Math.min(MAX_ROWS * FIELDS, rows.length * 2));
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

    public boolean targets(int objectId, boolean purchase) {
        return shopObjectId == objectId && this.purchase == purchase;
    }

    public boolean currentFor(CUserSocket client, L1PcInstance viewer) {
        return viewer != null && client != null && client.getActiveChar() == viewer
                && client.ownsReadyGameSession() && viewer.getSessionStamp() == viewerStamp
                && CWorld.getInstance().findObjectBySmartId(viewerIdentity) == viewer;
    }

    public boolean matches(L1PcInstance shop, boolean purchase) {
        return this.purchase == purchase && shop.isPrivateShop()
                && shopVersion == shop.getPrivateShopVersion() && shopIdentity != 0
                && CWorld.getInstance().findObjectBySmartId(shopIdentity) == shop;
    }
}
