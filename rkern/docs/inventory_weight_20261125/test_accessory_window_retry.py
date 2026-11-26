"""Exercise accessory slot updates with rejected and failed equipment frames."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "src/l1j/server/server/model/inventory/CUserEquipment.java").read_text(encoding="utf-8")
fields = source[source.index("private static final int RING_WINDOW_SLOTS") : source.index("private int displayedPrimaryWeapon")]
methods = source[source.index("    private void publishAccessory(") : source.index("\tprivate void syncWeaponWindow(")]

harness = r'''
import java.util.ArrayList;
import java.util.List;

public final class AccessoryWindowAudit {
    private final Object _owner = new Object();

    record L1ItemInstance(int getId, boolean isEquipped) {}
    record EquipmentFrame(int id, int slot, boolean equipped) {}

    static final class SC_EquipmentWindow {
        static final int EQUIPMENT_INDEX_RING1 = 19, EQUIPMENT_INDEX_EARRING = 13, EQUIPMENT_INDEX_RUNE1 = 23;
        static final List<EquipmentFrame> accepted = new ArrayList<>();
        static boolean rejectNext, throwNext;

        static boolean send(Object owner, int id, int slot, boolean equipped) {
            if (throwNext) {
                throwNext = false;
                throw new IllegalStateException("frame failure");
            }
            if (rejectNext) {
                rejectNext = false;
                return false;
            }
            accepted.add(new EquipmentFrame(id, slot, equipped));
            return true;
        }
    }
'''
checks = r'''
    private void ring(int id, boolean equipped) {
        publishAccessory(new L1ItemInstance(id, equipped), 0, RING_WINDOW_SLOTS);
    }

    private static void require(boolean passed, String situation) {
        if (!passed) throw new AssertionError(situation);
    }

    public static void main(String[] args) {
        var window = new AccessoryWindowAudit();
        window.ring(1, true);
        window.ring(2, true);
        require(window.lastSentAccessories == null, "normal sends need one slot array");

        SC_EquipmentWindow.rejectNext = true;
        window.ring(1, false);
        window.ring(3, true);
        require(SC_EquipmentWindow.accepted.equals(List.of(
            new EquipmentFrame(1, 19, true), new EquipmentFrame(2, 20, true),
            new EquipmentFrame(1, 19, false), new EquipmentFrame(3, 19, true)
        )), "remove the stale item before reusing the same slot");
        require(window.lastSentAccessories == null, "release the sent-state copy after replay");

        SC_EquipmentWindow.throwNext = true;
        try {
            window.ring(4, true);
            throw new AssertionError("expected the simulated frame failure");
        } catch (IllegalStateException expected) {
            window.ring(4, false);
        }
        require(SC_EquipmentWindow.accepted.size() == 4, "no removal for a frame never accepted");
        require(window.lastSentAccessories == null, "release the copy without phantom frames");

        require(accessoryWindowIndex(RING_WINDOW_SLOTS) == 13, "earring range");
        require(accessoryWindowIndex(RING_WINDOW_SLOTS + EARRING_WINDOW_SLOTS) == 23, "rune range");
        System.out.println("PASS accessory window rejection, replay order and slot indices");
    }
}
'''

with tempfile.TemporaryDirectory() as work:
    java = Path(work) / "AccessoryWindowAudit.java"
    java.write_text(harness + fields + methods + checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", work, "AccessoryWindowAudit"], check=True, timeout=30)
