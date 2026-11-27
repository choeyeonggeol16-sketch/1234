#!/usr/bin/env python3
"""Paired production CIO loopback measurements on Java 21, Windows or Linux."""
import argparse
import json
import math
import os
from pathlib import Path
import platform
import queue
import socket
import subprocess
import tempfile
import threading
import time

# sockets, active, symmetric payload B, window, offered RPS (0 = closed loop), warmup/measurement ms, pairs
PROFILES = {
    'smoke': (16, 16, 256, 1, 512, 400, 900, 1),
    'single-low': (1, 1, 256, 1, 192, 1200, 2200, 1),
    'single-high': (1, 1, 256, 1, 0, 1200, 2200, 1),
    'small-4k': (16, 16, 4096, 8, 0, 1500, 2600, 1),
    'middle': (1024, 1024, 256, 1, 0, 1800, 3000, 1),
    'large': (5000, 5000, 256, 1, 0, 2200, 3800, 3),
    'burst': (5000, 5000, 256, 8, 0, 2200, 3800, 2),
    'mixed-4k': (5000, 1000, 4096, 8, 6000, 2000, 3000, 1),
    'soak': (5000, 1000, 256, 8, 1280, 2500, 60000, 1),
    'probe': (5000, 5000, 256, 8, 0, 1800, 2800, 1),
}
MATRIX = ('single-low', 'single-high', 'small-4k', 'middle', 'large', 'burst', 'mixed-4k', 'soak')


class Process:
    def __init__(self, command, directory, stderr):
        self.command = subprocess.Popen(command, cwd=directory, stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=stderr, text=True, bufsize=1)
        self.lines = queue.Queue()
        self.reader = threading.Thread(target=self.read, daemon=True)
        self.reader.start()

    def read(self):
        for line in self.command.stdout:
            self.lines.put(line.strip())
        self.lines.put(None)

    def message(self, prefix, timeout):
        try:
            line = self.lines.get(timeout=timeout)
        except queue.Empty as failure:
            raise TimeoutError(f'timed out waiting for {prefix}') from failure
        if line is None or not line.startswith(prefix):
            raise RuntimeError(f'expected {prefix}, received {line!r}, exit={self.command.poll()}')
        return line.split()[1:]

    def send(self, line):
        self.command.stdin.write(line + '\n')
        self.command.stdin.flush()

    def finish(self):
        status = self.command.wait(timeout=10)
        if status:
            raise RuntimeError(f'JVM returned {status}')

    def close(self):
        if self.command.poll() is None:
            self.command.kill()
        self.command.wait(timeout=8)
        self.reader.join(timeout=1)
        self.command.stdin.close()
        self.command.stdout.close()


def key_handshake(port):
    start = time.monotonic_ns()
    with socket.create_connection(('127.0.0.1', port), timeout=1.5) as peer:
        peer.settimeout(1.5)
        with peer.makefile('rb') as stream:
            size = int.from_bytes(stream.read(2), 'little')
            payload = stream.read(size - 2) if 3 <= size <= 256 else b''
            if len(payload) != size - 2 or not payload:
                raise RuntimeError('invalid new-connection handshake')
    return payload, (time.monotonic_ns() - start) / 1000


def values(fields):
    result = {}
    for field in fields:
        key, value = field.split('=', 1)
        result[key] = float(value) if '.' in value else int(value)
    return result


def pool(server):
    server.send('POOL')
    return [int(v) for v in server.message('POOL ', 3)]


def sample(server):
    server.send('SAMPLE')
    return [int(v) for v in server.message('SAMPLE ', 4)]


def run(args, path, profile, classpath, variant, trial):
    sockets, active, payload, window, rate, warmup, measure, _ = PROFILES[profile]
    prefix = f'{profile}-{trial}-{variant}'
    vm = [args.java, '--enable-preview', '-ea', '-Xms128m', '-Xmx640m',
          '-XX:ActiveProcessorCount=2', '-cp', classpath]
    server_vm, client_vm = vm, vm
    if args.linux_cpus:
        cpu0, cpu1 = args.linux_cpus.split(',')
        server_vm = ['taskset', '-c', cpu0, *vm]
        client_vm = ['taskset', '-c', cpu1, *vm]
    warmup = args.warmup_ms or warmup
    measure_ms = args.measure_ms or measure
    if profile == 'soak' and measure_ms < 60_000:
        raise ValueError('soak requires 60 seconds or longer')
    with tempfile.TemporaryDirectory(dir=path, prefix='work-') as cwd, \
            (path / (prefix + '.server.log')).open('w') as server_log, \
            (path / (prefix + '.client.log')).open('w') as client_log:
        server = Process([*server_vm, 'SelectorPollBenchmark', 'server', str(sockets), '2',
                          str(payload), 'mixed'], cwd, server_log)
        client = None
        try:
            port = int(server.message('READY ', 20)[0])
            key, _ = key_handshake(port)
            client = Process([*client_vm, f'-Drkern.test.fixedBurst={window}', 'SelectorPollBenchmark',
                              'client-rate' if rate else 'client', str(port), str(sockets), str(window), str(payload),
                              str(warmup), str(measure_ms), str(active), str(rate), '2200'], cwd, client_log)
            client.message('READY', 45)
            if pool(server)[0] != sockets:
                raise RuntimeError(f'expected {sockets} simultaneous sockets')
            client.send('')
            client.message('MEASURE', warmup / 1000 + 10)
            before = sample(server)
            before_polls = None
            if args.polls:
                server.send('POLLS')
                before_polls = server.message('POLLS ', 5)
            accepts = []
            deadline = time.monotonic() + measure_ms / 1000 + 6
            while True:
                if time.monotonic() > deadline:
                    raise TimeoutError('measurement end marker')
                try:
                    client.message('END', 0.5)
                    break
                except TimeoutError:
                    same_key, elapsed = key_handshake(port)
                    if same_key != key:
                        raise RuntimeError('new connection received different handshake')
                    accepts.append(elapsed)
            after = sample(server)
            timers = values(server.message('TIMERS ', 5))
            polls = None
            if args.polls:
                server.send('POLLS')
                end_polls = server.message('POLLS ', 5)
                if before_polls == ['unavailable'] or end_polls == ['unavailable']:
                    raise RuntimeError('requested poll instrumentation is absent')
                start, end = values(before_polls), values(end_polls)
                polls = {name: count if name == 'maxSkips' else count - start[name] for name, count in end.items()}
            fixed = values(client.message('RATE_RESULT ', 12)) if rate else None
            progress = values(client.message('PROGRESS ', 12))
            count, throughput, rtt50, rtt95, rtt99, rtt999 = map(float, client.message('RESULT ', 10))
            client.finish()
            if count <= 0 or (fixed and fixed['io_errors']):
                raise RuntimeError(f'no verified samples or client IO failure: {fixed}')
            server.send('STOP')
            server.message('DRAINED', 10)
            server.finish()
            host, owner, alloc, own_alloc, reject = (end - start for start, end in zip(before, after))
            accepts.sort()
            p99_accept = accepts[math.ceil(0.99 * len(accepts)) - 1] if accepts else None
            return {'profile': profile, 'trial': trial, 'variant': variant, 'sockets': sockets, 'active': active,
                    'payload_per_direction': payload, 'window': window, 'offered_rps': rate,
                    'warmup_ms': warmup, 'measure_ms': measure_ms,
                    'timely_requests': int(count), 'throughput_rps': throughput,
                    'rtt_p50_us': rtt50, 'rtt_p95_us': rtt95, 'rtt_p99_us': rtt99, 'rtt_p999_us': rtt999,
                    'host_cpu_ns_per_op': host / count, 'owner_cpu_ns_per_op': owner / count,
                    'host_alloc_bytes_per_op': alloc / count, 'owner_alloc_bytes_per_op': own_alloc / count,
                    'send_rejects': reject, 'new_connections': len(accepts), 'new_connection_p99_us': p99_accept,
                    'timers': timers, 'progress': progress, 'fixed': fixed, 'polls': polls}
        finally:
            if client is not None:
                client.close()
            server.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--baseline-cp', required=True)
    parser.add_argument('--candidate-cp', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--profile', choices=(*PROFILES, 'matrix'), action='append', required=True)
    parser.add_argument('--linux-cpus', help='comma-separated server and client CPU IDs, Linux only')
    parser.add_argument('--polls', action='store_true', help='request separate test-only selector counters')
    parser.add_argument('--warmup-ms', type=int, help='warmup override')
    parser.add_argument('--measure-ms', type=int, help='measurement override, at least 60000 for soak')
    parser.add_argument('--pairs', type=int, help='number of alternating A/B pairs per profile')
    parser.add_argument('--candidate-first', action='store_true', help='reverse the first pair')
    args = parser.parse_args()
    if any(value is not None and value <= 0 for value in (args.warmup_ms, args.measure_ms, args.pairs)):
        parser.error('warmup, measurement, and pair count must be positive')
    if args.linux_cpus is not None:
        values_cpu = args.linux_cpus.split(',')
        if platform.system() != 'Linux' or len(values_cpu) != 2 or not all(item.isdigit() for item in values_cpu):
            parser.error('--linux-cpus requires two Linux CPU IDs')
    args.output.mkdir(parents=True, exist_ok=True)
    args.output = args.output.resolve()
    settings = {'os': platform.platform(), 'cpu_count': os.cpu_count(), 'linux_cpus': args.linux_cpus,
                'java': subprocess.run(
        [args.java, '-version'], capture_output=True, text=True, check=True).stderr.splitlines()[0]}
    if Path('/sys/fs/cgroup/cpu.max').exists():
        settings['cpu_limit'] = Path('/sys/fs/cgroup/cpu.max').read_text().strip()
    (args.output / 'environment.json').write_text(json.dumps(settings, indent=2) + '\n')
    targets = [('base', args.baseline_cp), ('skip7', args.candidate_cp)]
    names = [name for profile in args.profile for name in (MATRIX if profile == 'matrix' else (profile,))]
    failures = 0
    with (args.output / 'results.jsonl').open('w') as data:
        for index, name in enumerate(names):
            for trial in range(1, (args.pairs or PROFILES[name][-1]) + 1):
                order = targets if (index + trial + args.candidate_first) % 2 else targets[::-1]
                for variant, classpath in order:
                    try:
                        row = run(args, args.output, name, classpath, variant, trial)
                    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
                        row = {'profile': name, 'trial': trial, 'variant': variant, 'error': str(error)}
                        failures += 1
                    output = json.dumps(row, allow_nan=False, sort_keys=True)
                    data.write(output + '\n')
                    data.flush()
                    print(output, flush=True)
    return int(bool(failures))


if __name__ == '__main__':
    raise SystemExit(main())
