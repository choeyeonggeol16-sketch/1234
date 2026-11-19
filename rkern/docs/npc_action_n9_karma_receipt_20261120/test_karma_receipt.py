"""Exercise the production karma exchange methods with a controllable receipt."""
import argparse
from pathlib import Path
import subprocess
import tempfile


parser = argparse.ArgumentParser()
parser.add_argument("--source-root", type=Path, default=Path(__file__).resolve().parents[2])
args = parser.parse_args()
file = args.source_root / "src/l1j/server/server/model/network/core/cs/packet/NpcQuestTransactions.java"
source = file.read_text(encoding="utf-8")
start = source.index("    static void getBloodCrystalByKarma(")
end = source.index("    /** Orc village quest tokens", start)
methods = source[start:end]

java = r'''
import java.util.ArrayList;
import java.util.List;

public final class KarmaReceiptCheck {
    enum Outcome { COMMIT, ROLLBACK, UNKNOWN, MISSING_ITEM, BUSY, REJECT_SUBMISSION }

    static final class Config {
        static double RATE_KARMA;
    }

    static final class L1PcInstance {
        final List<String> events = new ArrayList<>();
        int karma;
        int granted;
        int attempts;
        boolean busy;
        Outcome outcome;
        Runnable finish;

        int getKarma() { return karma; }
        void addKarma(int delta) { karma += delta; }
        L1PcInstance getInventory() { return this; }
        L1ItemInstance storeItem(int id, int count) { return null; }
    }

    static final class L1NpcInstance {
        final int id;
        L1NpcInstance(int id) { this.id = id; }
        int getNpcId() { return id; }
        L1NpcInstance getNpcTemplate() { return this; }
        String get_name() { return "npc-" + id; }
    }

    record L1ItemInstance(int id, int count) {
        String getLogName() { return id + " x" + count; }
    }

    static final class SC_Karma {
        static void send(L1PcInstance pc) { pc.events.add("karma:" + pc.karma); }
    }

    static final class SC_ServerMessage {
        static void send(L1PcInstance pc, int code) { pc.events.add("code:" + code); }
        static void send(L1PcInstance pc, int code, String npc, String item) {
            pc.events.add("grant:" + code + ":" + npc + ":" + item);
        }
    }

    static final class SC_SystemMessage {
        static void send(L1PcInstance pc, String message) { pc.events.add("failure"); }
    }

    static final class L1InventoryTransaction implements AutoCloseable {
        private final L1PcInstance pc;
        private L1ItemInstance reward;
        private Runnable failure;
        private boolean submitted;

        private L1InventoryTransaction(L1PcInstance pc) { this.pc = pc; }

        static L1InventoryTransaction begin(L1PcInstance pc, int resource) {
            if (pc.busy) throw new IllegalStateException("busy");
            check(resource == 80056 || resource == 80074, "NPC receipt key");
            pc.busy = true;
            return new L1InventoryTransaction(pc);
        }

        L1ItemInstance grant(int id, int count) {
            pc.attempts++;
            if (pc.outcome == Outcome.MISSING_ITEM) return null;
            return reward = new L1ItemInstance(id, count);
        }

        void onFailure(Runnable failure) { this.failure = failure; }

        void commitAfterInventoryRelease(Runnable success) {
            if (pc.outcome == Outcome.REJECT_SUBMISSION) throw new IllegalStateException("rejected");
            submitted = true;
            pc.finish = () -> {
                if (pc.outcome == Outcome.UNKNOWN) return;
                pc.busy = false;
                if (pc.outcome == Outcome.ROLLBACK) failure.run();
                else {
                    pc.granted += reward.count();
                    success.run();
                }
            };
        }

        @Override public void close() { if (!submitted) pc.busy = false; }
    }
''' + methods + r'''
    static int cases;
    static int checks;

    static void check(boolean condition, String detail) {
        checks++;
        if (!condition) throw new AssertionError("case " + cases + ": " + detail);
    }

    static void exchange(boolean blood, L1PcInstance pc, L1NpcInstance npc, String command) {
        if (blood) getBloodCrystalByKarma(pc, npc, command);
        else getSoulCrystalByKarma(pc, npc, command);
    }

    static void runCase(boolean blood, int count, String command, int before, double rate, Outcome outcome) {
        cases++;
        Config.RATE_KARMA = rate;
        var pc = new L1PcInstance();
        pc.karma = before;
        pc.outcome = outcome;
        pc.busy = outcome == Outcome.BUSY;
        var npc = new L1NpcInstance(blood ? 80056 : 80074);
        exchange(blood, pc, npc, command);
        check(pc.karma == before && pc.granted == 0, "no effects before confirmed receipt");

        if (outcome == Outcome.BUSY || outcome == Outcome.MISSING_ITEM || outcome == Outcome.REJECT_SUBMISSION) {
            check(pc.finish == null, "rejected exchange has no completion");
            check(pc.events.equals(List.of("failure")), "only failure notice");
            check(pc.busy == (outcome == Outcome.BUSY), "transfer gate preserved or released");
            return;
        }
        check(pc.events.isEmpty() && pc.finish != null && pc.busy, "pending exchange emits no result");
        pc.finish.run();
        if (outcome == Outcome.COMMIT) {
            int after = before + (int) ((blood ? 500 : -500) * count * rate);
            check(pc.karma == after && pc.granted == count && !pc.busy, "commit applied once");
            check(pc.events.equals(List.of("karma:" + after,
                    "grant:143:npc-" + npc.id + ":" + (blood ? 40718 : 40678) + " x" + count,
                    "code:" + (blood ? 1081 : 1080))), "legacy success order and IDs");
        } else {
            check(pc.karma == before && pc.granted == 0, "rollback and unknown outcomes have no effects");
            check(pc.busy == (outcome == Outcome.UNKNOWN), "unknown retains the transfer gate");
            check(pc.events.equals(outcome == Outcome.ROLLBACK ? List.of("failure") : List.of()),
                    "notification follows the outcome");
        }
    }

    static void rejectedInput(boolean blood, int karma, String command) {
        cases++;
        var pc = new L1PcInstance();
        pc.karma = karma;
        pc.outcome = Outcome.COMMIT;
        exchange(blood, pc, new L1NpcInstance(blood ? 80056 : 80074), command);
        check(pc.karma == karma && pc.granted == 0 && pc.attempts == 0, "invalid input has no effects");
        check(pc.finish == null && !pc.busy && pc.events.isEmpty(), "invalid input does not submit");
    }

    public static void main(String[] args) {
        int[] counts = {1, 10, 100};
        for (boolean blood : new boolean[] {true, false}) {
            int minimum = blood ? -10_000_000 : 10_000_000;
            for (int i = 0; i < counts.length; i++) {
                for (double rate : new double[] {1.0, 0.0, 0.75, 2.5}) {
                    for (Outcome outcome : Outcome.values()) {
                        runCase(blood, counts[i], Integer.toString(i + 1), minimum, rate, outcome);
                    }
                }
            }
            rejectedInput(blood, minimum + (blood ? 1 : -1), "1");
            for (String command : new String[] {null, "", "0", "4"}) rejectedInput(blood, minimum, command);
        }
        System.out.println("PASS karma receipt cases=" + cases + " checks=" + checks);
    }
}
'''

with tempfile.TemporaryDirectory() as tmp:
    java_file = Path(tmp, "KarmaReceiptCheck.java")
    java_file.write_text(java, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(java_file)], check=True)
    subprocess.run(["java", "-cp", tmp, "KarmaReceiptCheck"], check=True)
