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
        int debited;
        int durableKarma;
        int revision;
        boolean currentDialogue = true;
        int attempts;
        boolean busy;
        Outcome outcome;
        Runnable finish;

        int getKarma() { return karma; }
        int applyKarmaReceipt(int delta) { karma = L1Karma.clamp(karma + delta); return ++revision; }
        int getId() { return 1; }
        long getCacheDSessionEpoch() { return 7; }
        Client getNetConnection() { return new Client(); }
    }

    static final class Client {
        int npcDialogueVersion() { return 3; }
    }

    static final class HtmlCmdHandler {
        static boolean canReplyToDialogue(L1PcInstance pc, long id, int version) {
            return pc.currentDialogue && version == 3;
        }
    }

    static final class CWorld {
        static CWorld getInstance() { return new CWorld(); }
        long smartIdOf(L1NpcInstance npc) { return npc.getId(); }
    }

    static final class L1Karma {
        static int clamp(int value) { return Math.max(-15_500_000, Math.min(value, 15_500_000)); }
    }

    static final class MySqlCharacterStorage {
        static void writeKarmaReceipt(L1PcInstance pc, int id, int karma) {
            check(id == pc.getId(), "karma written for receipt owner");
            pc.durableKarma = karma;
        }
    }

    static final class CacheD {
        static CacheD getInstance() { return new CacheD(); }
        void publishKarmaReceipt(int id, long epoch, int revision) {
            check(id == 1 && epoch == 7 && revision == 1, "publish with session epoch/revision");
        }
    }

    static final class SC_NPCTalkReturn {
        static void send(L1PcInstance pc, int objId, String page) {
            pc.events.add("page:" + objId + ":" + page);
        }
    }

    static final class L1NpcInstance {
        final int id;
        L1NpcInstance(int id) { this.id = id; }
        int getNpcId() { return id; }
        int getId() { return id + 1000; }
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
        private int debit;
        private Runnable failure;
        private Runnable durable;
        private java.util.function.Consumer<L1PcInstance> sql;
        private boolean submitted;

        private L1InventoryTransaction(L1PcInstance pc) { this.pc = pc; }

        static L1InventoryTransaction begin(L1PcInstance pc, int resource) {
            if (pc.busy) throw new IllegalStateException("busy");
            check(resource == 80050 || resource == 80056 || resource == 80064 || resource == 80074,
                    "NPC receipt key");
            pc.busy = true;
            return new L1InventoryTransaction(pc);
        }

        L1ItemInstance grant(int id, int count) {
            pc.attempts++;
            if (pc.outcome == Outcome.MISSING_ITEM) return null;
            return reward = new L1ItemInstance(id, count);
        }

        boolean debit(int id, int count) {
            check(id == 40718 || id == 40678, "crystal template");
            if (pc.outcome == Outcome.MISSING_ITEM) return false;
            debit = count;
            return true;
        }

        void requireTables(String table) { check(table.equals("characters"), "require transactional character rows"); }
        void sql(java.util.function.Consumer<L1PcInstance> action) { sql = action; }
        void onDurableCommit(Runnable action) { durable = action; }
        void onFailure(Runnable failure) { this.failure = failure; }

        void commitAfterInventoryRelease(Runnable success) {
            if (pc.outcome == Outcome.REJECT_SUBMISSION) throw new IllegalStateException("rejected");
            submitted = true;
            pc.finish = () -> {
                if (pc.outcome == Outcome.UNKNOWN) return;
                pc.busy = false;
                if (pc.outcome == Outcome.ROLLBACK) failure.run();
                else {
                    sql.accept(pc);
                    if (reward != null) pc.granted += reward.count();
                    pc.debited += debit;
                    durable.run();
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
            check(pc.karma == after && pc.durableKarma == after && pc.granted == count && !pc.busy,
                    "both rows and memory committed");
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

    static void reverse(boolean blood, String command, int count, Outcome outcome, boolean dialogue) {
        cases++;
        Config.RATE_KARMA = 2.5;
        var pc = new L1PcInstance();
        pc.outcome = outcome;
        pc.karma = blood ? -15_500_000 : 15_500_000;
        pc.durableKarma = pc.karma;
        pc.busy = outcome == Outcome.BUSY;
        var npc = new L1NpcInstance(blood ? 80050 : 80064);
        int before = pc.karma;
        check(exchangeCrystalForKarma(pc, npc, command, blood), "recognized reverse command");
        check(pc.karma == before && pc.debited == 0, "no reverse effects before COMMIT");
        if (pc.finish != null) {
            pc.currentDialogue = dialogue;
            // Ordinary karma can change between admission and the SQL worker.
            if (outcome == Outcome.COMMIT) pc.karma = blood ? -120 : 120;
            pc.finish.run();
        }
        if (outcome == Outcome.COMMIT) {
            int expected = (blood ? -120 : 120) + (int) ((blood ? -100 : 100) * count * Config.RATE_KARMA);
            check(pc.debited == count && pc.karma == expected && pc.durableKarma == expected,
                    "debit and latest ordinary karma committed together");
            int page = count == 1 ? 7 : count == 10 ? 8 : 9;
            String success = "page:" + npc.getId() + ":meet" + (blood ? "10" : "00") + page;
            List<String> notices = new ArrayList<>(List.of("code:" + (blood ? 1079 : 1078), "karma:" + expected));
            if (dialogue) notices.add(success);
            check(pc.events.equals(notices), "legacy packet order and stale HTML suppression");
        } else {
            check(pc.karma == before && pc.durableKarma == before && pc.debited == 0, "no partial charge");
            String failure = "page:" + npc.getId() + ":" + (blood ? "meet104" : "meet004");
            boolean rejected = outcome == Outcome.UNKNOWN || (outcome == Outcome.ROLLBACK && !dialogue);
            check(pc.events.equals(rejected ? List.of() : List.of(failure)), "existing failure page");
        }
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
            for (int i = 0; i < counts.length; i++) {
                String command = String.valueOf("aBc".charAt(i));
                for (Outcome outcome : Outcome.values()) {
                    reverse(blood, command, counts[i], outcome, true);
                    reverse(blood, command, counts[i], outcome, false);
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
