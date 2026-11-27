"""Run a paired RKern Selector / FFM shared-worker request/response benchmark."""
import argparse
import csv
import json
import os
import platform
from pathlib import Path
from queue import Empty, Queue
import subprocess
import threading
import time

HERE = Path(__file__).resolve().parent
METRICS = ("samples", "measured_clients", "requests_per_s", "per_client_per_s", "p50_us",
           "p95_us", "p99_us", "p999_us", "max_us", "cpu_ns_per_request")
FIELDS = ["connections", "rep", "attempt", "implementation", "order", "status", "paired",
          *METRICS, "accepted", "closed", "frames", "error"]


class Process:
    def __init__(self, command, path):
        self.log = path.open("w", encoding="utf-8")
        self.proc = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=self.log, text=True, encoding="utf-8", errors="replace")
        self.lines = Queue()
        def read_lines():
            for line in self.proc.stdout:
                self.lines.put(line.rstrip())
            self.lines.put(None)
        self.reader = threading.Thread(target=read_lines, daemon=True)
        self.reader.start()

    def line(self, timeout):
        try:
            line = self.lines.get(timeout=timeout)
        except Empty as exc:
            raise TimeoutError("process output timed out") from exc
        if line is None:
            raise RuntimeError(f"process closed its output, exit={self.proc.poll()}; see process log")
        self.log.write(line + "\n")
        self.log.flush()
        return line

    def close(self):
        try:
            if self.proc.poll() is None:
                self.proc.kill()
            self.proc.wait(timeout=8)
            self.reader.join(timeout=2)
        finally:
            self.log.close()


def run_case(a, row):
    name = row["implementation"]
    n = row["connections"]
    path = a.output / f"{n}-{row['rep']}-{row['attempt']}-{row['order']}-{name}"
    cmd = [a.java, "--enable-preview", "--enable-native-access=ALL-UNNAMED", "-Xms96m", "-Xmx384m",
           "-XX:ActiveProcessorCount=2", "-cp", str(a.classes)]
    server = Process([*cmd, name, "258", str(n), "2"], path.with_suffix(".server.log"))
    client = None
    try:
        ready = server.line(20).split()
        if len(ready) != 2 or ready[0] != "READY" or not 0 < int(ready[1]) <= 65535:
            raise ValueError(f"invalid readiness {ready}")
        # The driver accepts the historical ticks/s argument on every platform.
        ticks = os.sysconf("SC_CLK_TCK") if hasattr(os,"sysconf") else 100
        client = Process([*cmd, "TransportDriver", ready[1], str(n), str(a.warmup), str(a.measure),
                          "256", "8", str(server.proc.pid), str(ticks)], path.with_suffix(".client.log"))
        marker = client.line(60 + a.warmup / 1000)
        if marker != f"MEASURING {n}":
            raise ValueError(f"invalid measurement start {marker}")
        result = client.line(40 + a.measure / 1000).split()
        if len(result) != 1 + len(METRICS) or result[0] != "RESULT":
            raise ValueError(f"invalid result {result}")
        if client.proc.wait(timeout=10):
            raise RuntimeError("client failed, see log")
        row.update(dict(zip(METRICS, result[1:])))
        if int(row["measured_clients"]) != n or int(row["samples"]) <= 0:
            raise ValueError("not every connected client completed measured work")
        server.proc.stdin.write("STOP\n")
        server.proc.stdin.flush()
        report = server.line(20).split()
        if report[0] != "CLOSED" or server.proc.wait(timeout=10):
            raise RuntimeError("unclean shutdown")
        counters = dict(v.split("=",1) for v in report[1:] if "=" in v)
        for key in ("accepted", "closed", "frames"):
            row[key] = int(counters[key])
        if row["accepted"] != n or row["closed"] != n or row["frames"] < int(row["samples"]):
            raise ValueError(f"connection or frame accounting {counters}")
        row["status"] = "ok"
    except (OSError, ValueError, KeyError, RuntimeError, TimeoutError, subprocess.SubprocessError) as exc:
        row["status"], row["error"] = "failed", str(exc)
    finally:
        if client is not None:
            client.close()
        server.close()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--classes", type=Path, default=HERE / "classes")
    ap.add_argument("--java", default="java")
    ap.add_argument("--connections", nargs="+", type=int, default=[1024, 5000])
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--warmup", type=int, default=3500)
    ap.add_argument("--measure", type=int, default=5500)
    a = ap.parse_args()
    windows = platform.system() == "Windows"
    native = "WinIocpCore" if windows else "NcFfmCore"
    a.output.mkdir(parents=True, exist_ok=False)
    details = dict(platform=platform.platform(), cpu=platform.machine(), shared_workers=2, window=8,
                   bytes=258, warmup_ms=a.warmup, measure_ms=a.measure, repetitions=a.reps,
                   reference="Open_LU4_L2OFF abbb2e5f", selector="RKern d6edf7a6",
                   jdk=subprocess.run([a.java,"-version"],capture_output=True,text=True).stderr.strip())
    quota = Path("/sys/fs/cgroup/cpu.max")
    if quota.is_file():
        details["cpu_quota"] = quota.read_text().strip()
    (a.output / "environment.json").write_text(json.dumps(details, indent=2),encoding="utf-8")
    any_incomplete = False
    with (a.output / "results.csv").open("w",newline="",encoding="utf-8") as out:
        writer = csv.DictWriter(out, FIELDS)
        writer.writeheader()
        for rep in range(1, a.reps+1):
            sizes = list(enumerate(a.connections))
            if rep % 2 == 0:
                sizes.reverse()
            for index, n in sizes:
                for attempt in (1,2):
                    pending = []
                    names = ["SelectorCore",native]
                    if (rep+index+attempt) % 2:
                        names.reverse()
                    for order, name in enumerate(names,1):
                        row = dict(rep=rep, attempt=attempt, order=order, connections=n, implementation=name)
                        run_case(a,row)
                        pending.append(row)
                        print(n, rep, attempt, name, row["status"], row.get("requests_per_s",row.get("error")),
                              row.get("p99_us",""), flush=True)
                    good = all(r["status"]=="ok" for r in pending)
                    for r in pending:
                        writer.writerow(dict(r,paired=int(good)))
                    out.flush()
                    if good:
                        break
                else:
                    any_incomplete = True
    if any_incomplete:
        raise SystemExit("Incomplete pairs; see results and logs")


if __name__ == "__main__":
    main()
