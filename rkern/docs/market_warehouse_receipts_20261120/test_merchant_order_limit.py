"""The production NPC order builder must reject uncarryable split orders."""
from pathlib import Path
import subprocess
import tempfile

src = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model/shop"
classes = []
for name in ["L1ShopBuyOrderList", "L1ShopBuyOrder"]:
    content = (src / f"{name}.java").read_text(encoding="utf-8")
    classes.append(content[content.index("public class "):].replace("public class ", "static final class ", 1))

java = r'''
import java.util.ArrayList;
import java.util.List;

public final class MerchantLimitCheck {
    static int checks;
    static void check(boolean success) {
        checks++;
        if (!success) throw new AssertionError("check " + checks);
    }
    static final class Config { static double RATE_SHOP_SELLING_PRICE = 1.0; }
    static final class CUserInventory { static int MAX_SIZE = 180; }
    static final class L1PcInstance {}
    record Item(boolean isStackable, int getWeight) {}
    record L1ShopItem(Item getItem, int getPackCount, int getPrice) {}
    record L1Shop(List<L1ShopItem> getSellingItems) { int getNpcId() { return 1; } }
    static final class L1TaxCalculator {
        L1TaxCalculator(int npc) {}
        int layTax(int price) { return price; }
    }
''' + "\n".join(classes) + r'''
    public static void main(String[] args) {
        var shop = new L1Shop(List.of(new L1ShopItem(new Item(false, 1), 2, 5),
                new L1ShopItem(new Item(true, 1), 4, 11),
                new L1ShopItem(new Item(false, 1), 1, 5)));
        for (int count = 1; count < 180; count++) {
            var orders = new L1ShopBuyOrderList(shop);
            orders.add(0, count, null);
            check(count <= 90 ? orders.getList().size() == count * 2 && orders.BugOk() == 0
                    : orders.getList().isEmpty() && orders.BugOk() != 0);
        }
        var orders = new L1ShopBuyOrderList(shop);
        orders.add(0, 89, null);
        orders.add(1, 150, null);
        orders.add(2, 2, null);
        check(orders.BugOk() == 0 && orders.getList().size() == 181);
        check(orders.getList().get(178).getCount() == 600);
        orders.add(2, 1, null);
        check(orders.BugOk() != 0 && orders.getList().size() == 181);
        System.out.println("PASS merchant order checks=" + checks);
    }
}
'''
with tempfile.TemporaryDirectory() as path:
    file = Path(path, "MerchantLimitCheck.java")
    file.write_text(java, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", path, str(file)], check=True)
    subprocess.run(["java", "-cp", path, "MerchantLimitCheck"], check=True)
