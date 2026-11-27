"""Paired open-loop tests complementing the closed-loop saturation measurements."""
import argparse
import csv
import platform
from run_bench import HERE, Process
from pathlib import Path

METRICS = ("requests", "active_clients", "completed_rps", "schedule_p99_us", "rtt_p99_us", "sender_lag_p99_us",
           "valid_completed", "completed_in_window", "dropped", "peak_pending", "peak_waiting",
           "pending_at_end", "unscheduled_at_end", "drain_ms", "drained_rps")
FIELDS = ["rep", "attempt", "order", "backend", "clients", "rate", "warmup_ms", "measure_ms",
          "status", "paired", *METRICS, "accepted", "closed", "frames", "error"]


def trial(args, row):
    backend = row["backend"]
    clients = row["clients"]
    label = args.output / f"{row['rep']}-{row['attempt']}-{row['order']}-{backend}"
    command = ["java", "--enable-preview", "--enable-native-access=ALL-UNNAMED",
               "-Xms96m", "-Xmx384m", "-XX:ActiveProcessorCount=2", "-cp", str(HERE / "classes")]
    server = Process([*command, backend, "258", str(clients), "2"], label.with_suffix(".server.log"))
    driver = None
    try:
        ready = server.line(20).split()
        if len(ready) != 2 or ready[0] != "READY":
            raise ValueError(f"no server port: {ready}")
        driver = Process([*command, "PacedDriver", ready[1], str(clients), str(row["warmup_ms"]),
                          str(row["measure_ms"]), str(row["rate"])], label.with_suffix(".client.log"))
        result = driver.line(120).split()
        if not result or result[0] != "RESULT" or len(result) != len(METRICS)+1 or driver.proc.wait(timeout=10):
            raise ValueError(f"invalid driver result {result}")
        row.update(zip(METRICS, result[1:1+len(METRICS)]))
        expected = row["rate"] * row["measure_ms"] // 1000
        if int(row["requests"]) != expected or int(row["active_clients"]) != clients:
            raise ValueError(f"measured {row['requests']}/{expected} requests for {row['active_clients']}/{clients} clients")
        if int(row["valid_completed"]) != expected or int(row["dropped"]) != 0:
            raise ValueError("scheduled requests were lost or not fully validated")
        server.proc.stdin.write("STOP\n")
        server.proc.stdin.flush()
        reply = server.line(25).split()
        if reply[0] != "CLOSED" or server.proc.wait(timeout=10):
            raise ValueError("unclean server shutdown")
        counters = dict(item.split("=",1) for item in reply[1:] if "=" in item)
        for k in ("accepted","closed","frames"):
            row[k] = int(counters[k])
        if row["accepted"] != clients or row["closed"] != clients or row["frames"] < expected:
            raise ValueError(f"resource or frame accounting {counters}")
        row["status"] = "ok"
    except Exception as err:
        row["status"], row["error"] = "failed", str(err)
    finally:
        if driver is not None:
            driver.close()
        server.close()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--output",required=True,type=Path)
    args=ap.parse_args()
    args.output.mkdir(exist_ok=False,parents=True)
    native = "WinIocpCore" if platform.system() == "Windows" else "NcFfmCore"
    with (args.output / "results.csv").open("w",newline="",encoding="utf-8") as stream:
        writer=csv.DictWriter(stream, FIELDS)
        writer.writeheader()
        for rep in range(1,4):
            for attempt in (1,2):
                pair=[]
                backends=["SharedSelectorCore",native]
                if (rep+attempt)%2:
                    backends.reverse()
                for order,backend in enumerate(backends,1):
                    row=dict(rep=rep,attempt=attempt,order=order,backend=backend,clients=5000,
                             rate=80000,warmup_ms=2500,measure_ms=5000)
                    trial(args,row)
                    pair.append(row)
                    print(rep,attempt,backend,row["status"],row.get("rtt_p99_us",row.get("error")),flush=True)
                good=all(r["status"]=="ok" for r in pair)
                for row in pair:
                    writer.writerow(dict(row,paired=int(good)))
                stream.flush()
                if good:
                    break
            else:
                raise SystemExit("failed paced pair; see client/server logs")
if __name__ == "__main__":
    main()
