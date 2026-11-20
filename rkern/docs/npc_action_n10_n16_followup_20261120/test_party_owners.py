"""Run the production multi-owner admission/release code with fake sessions."""
from pathlib import Path
import subprocess
import tempfile


PACKAGE = "l1j.server.server.model"
MODEL = Path(PACKAGE.replace(".", "/"))
SOURCE = Path(__file__).resolve().parents[2] / "src" / MODEL


def put(path, body):
    out = root / MODEL / path
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(body, encoding="utf-8")


with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    put("InventoryTransactionOwners.java", (SOURCE / "InventoryTransactionOwners.java").read_text(encoding="utf-8"))
    put("L1World.java", """
package l1j.server.server.model;
final class L1World {
    static L1World getInstance() { return new L1World(); }
    Object findObject(int id) { return null; }
}
""")
    put("network/core/session/CIOClient.java", """
package l1j.server.server.model.network.core.session;
import l1j.server.server.model.Instance.L1PcInstance;
public class CIOClient {
    public boolean connected = true;
    public int kicked;
    public final L1PcInstance pc;
    public CIOClient(L1PcInstance pc) { this.pc = pc; }
    public boolean isConnected() { return connected; }
    public L1PcInstance getActiveChar() { return pc; }
    public void kick() { kicked++; connected = false; }
}
""")
    put("Instance/L1PcInstance.java", """
package l1j.server.server.model.Instance;
import l1j.server.server.model.network.core.session.CIOClient;
public class L1PcInstance {
    public final int id;
    public final CIOClient client = new CIOClient(this);
    public int stamp = 1;
    public boolean busy;
    public int releases;
    public L1PcInstance(int id) { this.id = id; }
    public int getId() { return id; }
    public int getSessionStamp() { return stamp; }
    public CIOClient getNetConnection() { return client; }
    public boolean isPrivateShop() { return false; }
    public int getTradeID() { return 0; }
    public boolean isTradeResolving() { return false; }
    public Object getInventory() { return this; }
    public boolean tryBeginItemTransfer() {
        if (busy) return false;
        return busy = true;
    }
    public void endItemTransfer() {
        if (!busy) throw new AssertionError("released a gate twice");
        releases++;
        busy = false;
    }
}
""")
    put("PartyOwnersCheck.java", """
package l1j.server.server.model;
import l1j.server.server.model.Instance.L1PcInstance;
public final class PartyOwnersCheck {
    static int checks;
    static void check(boolean valid) { if (++checks > 0 && !valid) throw new AssertionError(checks); }
    static L1PcInstance[] party(int count) {
        var owners = new L1PcInstance[count];
        for (int i = 0; i < owners.length; i++) owners[i] = new L1PcInstance(count - i);
        return owners;
    }
    public static void main(String[] args) {
        for (int count : new int[]{2, 3, 6, 7, 8, 35}) {
            var owners = party(count);
            var receipt = count == 2 ? new InventoryTransactionOwners(owners[0], owners[1], false)
                    : new InventoryTransactionOwners(owners[count / 2], owners);
            check(receipt.size() == count);
            for (int i = 0; i < count; i++) {
                var owner = receipt.get(i);
                check(owner.id == i + 1 && owner.busy && receipt.current(owner));
            }
            owners[0].stamp++;
            receipt.release();
            receipt.release();
            check(owners[0].busy && owners[0].releases == 0);
            for (int i = 1; i < count; i++) check(!owners[i].busy && owners[i].releases == 1);
            receipt.quarantine();
            for (var owner : owners) check(owner.client.kicked == 1 && !receipt.current(owner));
        }

        for (int blocked = 0; blocked < 8; blocked++) {
            var owners = party(8);
            boolean disconnected = (blocked & 1) == 0;
            owners[blocked].busy = !disconnected;
            owners[blocked].client.connected = !disconnected;
            try {
                new InventoryTransactionOwners(owners[0], owners);
                throw new AssertionError("accepted unavailable participant");
            } catch (IllegalStateException expected) {
                for (int i = 0; i < owners.length; i++)
                    check(owners[i].busy == (i == blocked && !disconnected));
            }
        }
        var owners = party(8);
        owners[7] = new L1PcInstance(owners[0].getId());
        try {
            new InventoryTransactionOwners(owners[0], owners);
            throw new AssertionError("accepted duplicate participant ID");
        } catch (IllegalArgumentException expected) {
            for (var owner : owners) check(!owner.busy && owner.releases == 0);
        }
        System.out.println("PASS party owner admission/release checks=" + checks);
    }
}
""")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, *map(str, root.rglob("*.java"))], check=True)
    subprocess.run(["java", "-cp", tmp, PACKAGE + ".PartyOwnersCheck"], check=True)
