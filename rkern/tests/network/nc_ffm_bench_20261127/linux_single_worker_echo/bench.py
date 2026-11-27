#!/usr/bin/env python3
"""Compare two standalone one-owner framed echo handlers over loopback TCP."""
import argparse
import csv
import hashlib
import json
import os
import platform
from pathlib import Path
import resource
import select
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
BACKENDS = ("SelectorEcho", "FfmEcho")
DRIVER_METRICS = (
    "samples", "measured_clients", "rps", "rps_per_client", "rtt_p50_us",
    "rtt_p95_us", "rtt_p99_us", "rtt_p999_us", "rtt_max_us", "server_cpu_ns_per_op",
)
FIELDS = (
    "backend", "trial", "case_order", "clients", "payload_bytes", "frame_bytes",
    "window", "warmup_ms", "measure_ms", *DRIVER_METRICS, "server_rss_kib",
    "server_peak_rss_kib", "accepted", "closed", "server_frames", "status", "error",
)


class LineReader:
    def __init__(self, stream):
        self.fd = stream.fileno()
        self.buffer = b""

    def read(self, timeout):
        deadline = time.monotonic() + timeout
        while b"\n" not in self.buffer:
            remaining = deadline - time.monotonic()
            if remaining <= 0 or not select.select([self.fd], [], [], remaining)[0]:
                raise TimeoutError("Timed out waiting for process output")
            chunk = os.read(self.fd, 4096)
            if not chunk:
                raise EOFError("Process exited before sending a completion line")
            self.buffer += chunk
        line, self.buffer = self.buffer.split(b"\n", 1)
        return line.decode("utf-8", errors="replace").strip()


def rss(pid):
    values = {}
    for line in Path(f"/proc/{pid}/status").read_text().splitlines():
        label, _, value = line.partition(":")
        if label in ("VmRSS", "VmHWM"):
            values[label] = int(value.split()[0])
    return values["VmRSS"], values["VmHWM"]


def stop(process):
    if process.poll() is None:
        process.kill()
    process.wait(timeout=5)


def command(args, entry, *parameters):
    return [
        args.java, "--enable-preview", "--enable-native-access=ALL-UNNAMED",
        "-Xms64m", "-Xmx256m", "-XX:ActiveProcessorCount=2", "-cp",
        str(args.classes), entry, *map(str, parameters),
    ]


def run_case(args, row, log_dir):
    backend, clients = row["backend"], row["clients"]
    tag = f"{row['trial']:02d}-{row['case_order']:02d}-{backend}-{clients}"
    digest = {}
    with (log_dir / f"{tag}.server.log").open("w") as server_err:
        with (log_dir / f"{tag}.driver.log").open("w") as driver_err:
            server = subprocess.Popen(
                command(args, backend, row["frame_bytes"], max(args.clients)),
                stdout=subprocess.PIPE, stderr=server_err, stdin=subprocess.PIPE,
            )
            driver = None
            try:
                replies = LineReader(server.stdout)
                ready = replies.read(15)
                words = ready.split()
                if len(words) < 2 or words[0] != "READY" or not 1 <= int(words[1]) <= 65535:
                    raise RuntimeError(f"Invalid startup reply {ready!r}")
                port = int(words[1])
                digest["ready"] = ready
                driver = subprocess.Popen(
                    command(args, "TransportDriver", port, clients, args.warmup_ms,
                            args.measure_ms, args.payload_bytes, args.window, server.pid, os.sysconf("SC_CLK_TCK")),
                    stdout=subprocess.PIPE, stderr=driver_err, stdin=subprocess.DEVNULL,
                )
                driver_out = LineReader(driver.stdout)
                active = driver_out.read(35 + args.warmup_ms / 1000)
                if active != f"MEASURING {clients}":
                    raise RuntimeError(f"Invalid measurement start {active!r}")
                digest["start"] = active
                row["server_rss_kib"], first_peak = rss(server.pid)
                result_line = driver_out.read(20 + args.measure_ms / 1000)
                result = result_line.split()
                if len(result) != len(DRIVER_METRICS) + 1 or result[0] != "RESULT":
                    raise RuntimeError(f"Invalid measurement result {result_line!r}")
                digest["result"] = result_line
                row.update(zip(DRIVER_METRICS, result[1:], strict=True))
                if int(row["samples"]) <= 0 or int(row["measured_clients"]) != clients:
                    raise RuntimeError(f"{row['measured_clients']}/{clients} clients completed measured echoes")
                if driver.wait(timeout=5) != 0:
                    raise RuntimeError(f"Driver exited with status {driver.returncode}")
                _, last_peak = rss(server.pid)
                row["server_peak_rss_kib"] = max(first_peak, last_peak)

                server.stdin.write(b"STOP\n")
                server.stdin.flush()
                shutdown = replies.read(15)
                digest["shutdown"] = shutdown
                words = shutdown.split()
                if not words or words[0] != "CLOSED":
                    raise RuntimeError(f"Invalid shutdown reply {shutdown!r}")
                counts = dict(word.split("=", 1) for word in words[1:] if "=" in word)
                row["accepted"] = int(counts["accepted"])
                row["closed"] = int(counts["closed"])
                row["server_frames"] = int(counts.get("frames", counts.get("echoes", "0")))
                if row["accepted"] != clients or row["closed"] != clients:
                    raise RuntimeError("Server did not release exactly the admitted sockets")
                if row["server_frames"] < int(row["samples"]):
                    raise RuntimeError("Server replied-frame count is smaller than the client sample count")
                if server.wait(timeout=5) != 0:
                    raise RuntimeError(f"Server exited with status {server.returncode}")
            finally:
                if driver is not None:
                    stop(driver)
                stop(server)
                (log_dir / f"{tag}.json").write_text(json.dumps(digest, indent=2) + "\n")


def startup_environment(args):
    files = {name: args.classes / f"{name}.class" for name in (*BACKENDS, "TransportDriver")}
    if missing := [str(path) for path in files.values() if not path.is_file()]:
        raise FileNotFoundError(f"Compile benchmark classes first: {', '.join(missing)}")
    soft, hard = resource.getrlimit(resource.RLIMIT_NOFILE)
    if soft != resource.RLIM_INFINITY and soft < max(args.clients) + 256:
        if hard != resource.RLIM_INFINITY and hard < max(args.clients) + 256:
            raise RuntimeError(f"File descriptor limit {hard} cannot support {max(args.clients)} sockets")
        soft = max(args.clients) + 256
        resource.setrlimit(resource.RLIMIT_NOFILE, (soft, hard))
    quota = Path("/sys/fs/cgroup/cpu.max")
    return {
        "kernel": platform.release(), "architecture": platform.machine(),
        "affinity_cpus": sorted(os.sched_getaffinity(0)),
        "cpu_quota": quota.read_text().strip() if quota.exists() else None,
        "open_file_limit": soft, "server_threads": 1, "jdk_visible_cpus": 2, "tcp": "localhost IPv4",
        "mode": "persistent closed-loop framed echo", "frame_size": args.payload_bytes + 2,
        "window": args.window, "clients": args.clients, "trials": args.trials,
        "warmup_ms": args.warmup_ms, "measure_ms": args.measure_ms,
        "classes_sha256": {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in files.items()},
        "java": subprocess.run([args.java, "-version"], capture_output=True, check=True, text=True).stderr.strip(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes", type=Path, default=HERE / "classes")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--clients", type=int, nargs="+", default=[1024, 5000])
    parser.add_argument("--trials", type=int, default=3)
    parser.add_argument("--warmup-ms", type=int, default=2000)
    parser.add_argument("--measure-ms", type=int, default=3000)
    parser.add_argument("--payload-bytes", type=int, default=256)
    parser.add_argument("--window", type=int, default=8)
    args = parser.parse_args()
    args.classes = args.classes.resolve()
    if not sys.platform.startswith("linux"):
        parser.error("the native epoll path and process counters require Linux")
    if len(set(args.clients)) != len(args.clients) or any(not 1 <= count <= 5000 for count in args.clients):
        parser.error("provide distinct connection counts in 1–5000")
    if min(args.trials, args.warmup_ms, args.measure_ms, args.window) < 1 or not 1 <= args.payload_bytes <= 65_533:
        parser.error("positive trial counts, durations, payload size and window are required")
    if (args.payload_bytes + 2) * args.window > 65_536:
        parser.error("the framed send window must fit in 64 KiB")
    env = startup_environment(args)
    args.output.mkdir(parents=True, exist_ok=False)
    log_dir = args.output / "logs"
    log_dir.mkdir()
    (args.output / "environment.json").write_text(json.dumps(env, indent=2) + "\n")
    any_failed = False
    with (args.output / "results.csv").open("w", newline="") as raw:
        writer = csv.DictWriter(raw, FIELDS)
        writer.writeheader()
        for trial in range(1, args.trials + 1):
            sizes = list(enumerate(args.clients))
            if trial % 2 == 0:
                sizes.reverse()
            order = 0
            for size_index, clients in sizes:
                backends = BACKENDS if (trial + size_index) % 2 else tuple(reversed(BACKENDS))
                for backend in backends:
                    order += 1
                    row = dict(backend=backend, trial=trial, case_order=order, clients=clients,
                               payload_bytes=args.payload_bytes, frame_bytes=args.payload_bytes + 2,
                               window=args.window, warmup_ms=args.warmup_ms, measure_ms=args.measure_ms)
                    try:
                        run_case(args, row, log_dir)
                        row["status"] = "ok"
                    except (EOFError, KeyError, OSError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
                        row["status"], row["error"] = "failed", str(error)
                        any_failed = True
                    writer.writerow(row)
                    raw.flush()
                    if row["status"] == "ok":
                        print(f"{backend} trial={trial} clients={clients} "
                              f"rps={row['rps']} p99_us={row['rtt_p99_us']}", flush=True)
                    else:
                        print(f"{backend} trial={trial} clients={clients} FAILED {row['error']}", file=sys.stderr, flush=True)
    return int(any_failed)


if __name__ == "__main__":
    raise SystemExit(main())
