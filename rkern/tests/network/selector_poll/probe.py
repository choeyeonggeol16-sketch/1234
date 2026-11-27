#!/usr/bin/env python3
"""Compile a test-only CSelectorTransport overlay for the benchmark's POLLS command."""
import argparse
from pathlib import Path
import subprocess

SOURCE = "rkern/src/l1j/server/server/model/network/core/transport/selector/CSelectorTransport.java"
METRICS = ("now waits advance await eligibleAdvance eligibleAwait skipAdvance skipAwait atSeven "
           "forceReady resetSeven maxSkips readArms writeArms interestOps scheduled turns acceptTurns").split()

SNAPSHOT = """
    private int snapshotSequence;

    public synchronized String selectorProbeCounters() {
        int sequence = ++snapshotSequence;
        for (Owner owner : owners) {
            owner.requested = sequence;
            owner.selector.wakeup();
        }
        String[] names = "NAMES".split(" ");
        long[] totals = new long[names.length];
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        for (Owner owner : owners) {
            while (owner.published != sequence) {
                if (System.nanoTime() >= deadline)
                    throw new IllegalStateException("No selector sample from owner " + owner.index);
                LockSupport.parkNanos(50_000L);
            }
            for (int i = 0; i < names.length; i++) {
                if (i == HIGH_WATER) totals[i] = Math.max(totals[i], owner.snapshot[i]);
                else totals[i] += owner.snapshot[i];
            }
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i != 0) result.append(' ');
            result.append(names[i]).append('=').append(totals[i]);
        }
        return result.toString();
    }
"""


def instrument(source, candidate):
    def replace(old, new, count=1):
        nonlocal source
        if source.count(old) != count:
            raise ValueError(f"Unexpected source for probe anchor {old!r}; expected {count} matches")
        source = source.replace(old, new)

    def count(metric):
        return f"metrics[{METRICS.index(metric)}]++;"

    owners = "    private final Owner[] owners;"
    snapshot = SNAPSHOT.replace("NAMES", " ".join(METRICS)).replace("HIGH_WATER", str(METRICS.index("maxSkips")))
    replace(owners, owners + snapshot)
    replace("        private final int index;", f"""        private final int index;
        private final long[] metrics = new long[{len(METRICS)}];
        private volatile int requested, published;
        private long[] snapshot;""")
    work = "        private void work() throws IOException {"
    replace(work, work + """
            if (requested != published) {
                int sequence = requested;
                snapshot = metrics.clone();
                published = sequence;
            }""")
    for anchor, name, owner in (
            ("readTarget = target;", "readArms", "owner."),
            ("singleWrite = count == 1 ?", "writeArms", "owner."),
            ("connection.linked = true;", "scheduled", ""),
            ("connection.turn();", "turns", ""),
            ("acceptReady = false;", "acceptTurns", "")):
        replace(anchor, owner + count(name) + " " + anchor)
    replace("if (key.interestOps() != interests) key.interestOps(interests);",
            "if (key.interestOps() != interests) { key.interestOps(interests); owner." + count("interestOps") + " }")

    enter_advance = count("advance") + " if (first != null || acceptReady) " + count("eligibleAdvance") + "\n                    "
    enter_await = count("await") + " if (first != null || acceptReady) " + count("eligibleAwait") + "\n                "
    if candidate:
        for old, prefix, event in (
                ("if (!trySkipPoll()) pollNow();", enter_advance, "skipAdvance"),
                ("if (!trySkipPoll()) select(Math.max(0, maxWaitMillis));", enter_await, "skipAwait")):
            replace(old, prefix + old + " else " + count(event))
        limit = "if (consecutivePollSkips >= MAX_CONSECUTIVE_POLL_SKIPS)"
        replace(limit + " return false;", limit + " { " + count("forceReady") + " return false; }")
        high = f"metrics[{METRICS.index('maxSkips')}]"
        replace("++consecutivePollSkips;", f"++consecutivePollSkips; {high} = Math.max({high}, consecutivePollSkips); "
                + "if (consecutivePollSkips == 7) " + count("atSeven"))
        replace("consecutivePollSkips = 0;", "if (consecutivePollSkips == 7) " + count("resetSeven")
                + " consecutivePollSkips = 0;", 2)
    else:
        advance = "selector.selectNow(collector);\n                    work();"
        replace(advance, enter_advance + advance)
        await_work = "select(Math.max(0, maxWaitMillis));"
        replace(await_work, enter_await + await_work)

    nonblocking = "selector.selectNow(collector);"
    replace(nonblocking, nonblocking + " " + count("now"), 1 if candidate else 3)
    wait = "if (waitMillis < 0) selector.select(collector);\n            else selector.select(collector, waitMillis);"
    replace(wait, wait + "\n            " + count("waits"))
    return source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--variant", choices=("baseline", "candidate"), required=True)
    parser.add_argument("--ref", required=True, help="Source commit, e.g. b29bf3b3 or f13a564c")
    parser.add_argument("--out", type=Path, required=True, help="Dedicated variant output directory")
    parser.add_argument("--classpath", required=True, help="Existing Java build and its dependencies")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    source = subprocess.check_output(["git", "-C", str(args.repo), "show", f"{args.ref}:{SOURCE}"]).decode("utf-8")
    output = args.out / "src" / "CSelectorTransport.java"
    output.parent.mkdir(exist_ok=True, parents=True)
    output.write_text(instrument(source, args.variant == "candidate"), encoding="utf-8")
    classes = args.out / "classes"
    classes.mkdir(exist_ok=True, parents=True)
    subprocess.run([args.javac, "--enable-preview", "--release", "21", "-encoding", "UTF-8",
                    "-cp", args.classpath, "-sourcepath", str(output.parent),
                    "-d", str(classes), str(output)], check=True)
    print(classes.resolve())


if __name__ == "__main__":
    main()
