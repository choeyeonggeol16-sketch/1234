package l1j.server.server.model;

import java.util.Locale;
import l1j.server.server.model.Instance.L1PcInstance;

public final class WorldUsersRegression {
    private static final class User extends L1PcInstance {
        boolean shop;
        @Override public boolean isPrivateShop() { return shop; }
    }

    public static void main(String[] args) throws Exception {
        WorldShapeRegression.installMapDefinitions();
        CWorld world = CWorld.getInstance();
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            User buyer = register(world, 1001, "Alice", "계정I", false);
            User seller = register(world, 1002, "Market", "계정i", true);
            check(world.getPlayer("ALICE") == buyer && world.findObject("alice") == buyer, "player key");
            check(world.hasOtherPrivateShop(buyer) && !world.hasOtherPrivateShop(seller), "same account and self");
            seller.setName("Renamed");
            seller.setAccountName("separate");
            check(world.getPlayer("Market") == null && world.getPlayer("RENAMED") == seller, "live rename");
            check(!world.hasOtherPrivateShop(buyer), "old account detached");
            seller.setAccountName("계정İ");
            check(world.hasOtherPrivateShop(buyer), "Unicode case-insensitive account");
            User replacement = register(world, seller.getId(), "New", "계정I", false);
            world.removeObject(seller);
            seller.setName("Stale");
            seller.setAccountName("계정I");
            check(!world.hasOtherPrivateShop(buyer) && world.getPlayer("Stale") == null, "late removal and rename");
            replacement.shop = true;
            check(world.hasOtherPrivateShop(buyer), "live shop state");
            User shadow = register(world, 1003, "NEW", "unrelated", false);
            check(!world.hasOtherPrivateShop(buyer) && world.getPlayer("NEW") == shadow, "displaced by name");
            replacement.setAccountName("계정i");
            check(!world.hasOtherPrivateShop(buyer), "displaced user stays out of the visible roster");
            replacement.setName("Online");
            check(world.hasOtherPrivateShop(buyer), "new distinct name");
            world.removeObject(replacement);
            world.removeObject(shadow);
            world.removeObject(buyer);
            check(world.getAllPlayers().isEmpty() && !world.hasOtherPrivateShop(buyer), "empty directory");
            System.out.println("PASS world users by name and account, live shop, rename, replacement and removal");
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    private static User register(CWorld world, int id, String name, String account, boolean shop) {
        User pc = new User();
        pc.setId(id);
        pc.setName(name);
        pc.setAccountName(account);
        pc.shop = shop;
        world.storeObject(pc);
        return pc;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
