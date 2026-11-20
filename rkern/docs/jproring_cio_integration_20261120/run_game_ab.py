#!/usr/bin/env python3
"""Run the real game listener and cipher client in separate JDK 21 processes."""
import argparse
import csv
import json
import os
import platform
from pathlib import Path
import select
import shutil
import subprocess
import tempfile
import time
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DEPS = (
    'com/zaxxer/HikariCP/5.1.0', 'org/slf4j/slf4j-api/2.0.13',
    'org/jctools/jctools-core/4.0.5', 'javolution/javolution/5.5.1',
    'net/sf/trove4j/trove4j/3.0.3', 'org/agrona/agrona/1.23.1',
    'it/unimi/dsi/fastutil/8.5.15', 'javax/xml/bind/jaxb-api/2.3.1',
    'javax/activation/javax.activation-api/1.2.0', 'com/github/ben-manes/caffeine/caffeine/3.1.8',
)
MODES = dict(off=(False, False), commit=(True, False), append=(False, True), both=(True, True))
WORK = dict(single=(1, 1, 2, 32, 'ordinary'), burst=(16, 16, 2, 256, 'mixed'),
            spread=(128, 1, 2, 64, 'bytes'), large=(16, 8, 2, 4096, 'ordinary'),
            gather=(16, 32, 2, 4096, 'ordinary'),
            cache64=(16, 16, 2, 64, 'cached'), cache256=(16, 16, 2, 256, 'cached'),
            load1024=(1024, 8, 2, 256, 'mixed'), mix5000=(5000, 1, 2, 64, 'mixed'))
FIELDS = ('workload', 'trial', 'mode', 'clients', 'active_clients', 'pace_ms', 'window', 'owners', 'payload', 'kind',
          'requests', 'requests_per_s', 'p50_us', 'p95_us', 'p99_us', 'p999_us', 'server_cpu_ns_per_op',
          'server_alloc_bytes_per_op', 'commit_hits', 'byte_append_hits', 'send_rejections',
          'raw_writes', 'raw_gather_writes', 'raw_write_segments',
          'direct_packet_tails', 'adjacent_copied_bytes', 'compacted_copied_bytes', 'write_burst_copied_bytes', 'variant')
RESOURCES = ('elapsed_ms', 'phase', 'sockets', 'leases', 'direct_bytes', 'direct_cap_bytes',
             'borrows', 'returns', 'heap_used_bytes', 'rss_kib', 'peak_rss_kib', 'file_descriptors')


def classpath(classes, deps):
    deps.mkdir(parents=True, exist_ok=True)
    jars = []
    for coordinate in DEPS:
        _, artifact, version = coordinate.rsplit('/', 2)
        name = f'{artifact}-{version}.jar'
        path = deps / name
        if not path.exists():
            url = f'https://repo.maven.apache.org/maven2/{coordinate}/{name}'
            with urllib.request.urlopen(url, timeout=20) as stream:
                path.write_bytes(stream.read())
        jars.append(str(path))
    return os.pathsep.join((str(classes), *jars))


def compile_java(cp, classes, reuse, source_root=ROOT):
    compiler = [shutil.which('javac')] if shutil.which('javac') else [
        'java', '--module', 'jdk.compiler/com.sun.tools.javac.Main']
    fixtures = [(HERE.parent / 'jproring_selector_transport_20261120/SelectorTransportTest.java'),
                HERE / 'GameSendBenchmark.java']
    sources = fixtures if reuse else [*sorted((source_root / 'src').rglob('*.java')), *fixtures]
    with tempfile.NamedTemporaryFile('w', encoding='utf-8') as arguments:
        arguments.writelines('"' + str(path) + '"\n' for path in sources)
        arguments.flush()
        subprocess.run([*compiler, '-encoding', 'UTF-8', '-source', '21', '-target', '21', '--enable-preview',
                        '-cp', cp, '-d', str(classes), '@' + arguments.name], check=True)


def line(process, prefix, timeout=15):
    read, _, _ = select.select([process.stdout], [], [], timeout)
    result = process.stdout.readline().decode().strip() if read else ''
    if not result.startswith(prefix):
        raise RuntimeError(f'expected {prefix!r}, received {result!r}, status {process.poll()}')
    return result


def send(process, command):
    process.stdin.write(command.encode() + b'\n')
    process.stdin.flush()


def sample_resources(server, writer, started, phase):
    send(server, 'POOL')
    pool = list(map(int, line(server, 'POOL ').split()[1:]))
    with open(f'/proc/{server.pid}/status', encoding='ascii') as status:
        rss = {field.split(':', 1)[0]: int(field.split()[1]) for field in status
               if field.startswith(('VmRSS:', 'VmHWM:'))}
    descriptors = len(os.listdir(f'/proc/{server.pid}/fd'))
    writer.writerow([round((time.monotonic() - started) * 1000), phase, *pool,
                     rss['VmRSS'], rss['VmHWM'], descriptors])
    return pool


def wait_for_end(client, server, writer, started, measure_ms, clients):
    if writer is None:
        return line(client, 'END', measure_ms / 1000 + 10)
    deadline = time.monotonic() + measure_ms / 1000 + 10
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError('measurement did not end')
        ready, _, _ = select.select([client.stdout], [], [], min(2, remaining))
        if ready:
            return line(client, 'END')
        sockets, *_ = sample_resources(server, writer, started, 'steady')
        if sockets != clients:
            raise RuntimeError(f'expected {clients} live sockets, sampled {sockets}')


def measure(cp, location, args, trial, load, mode, logs):
    clients, window, owners, payload, kind = WORK[load]
    active = clients // 5 if load == 'mix5000' else clients
    pace_ms = 40 if load == 'mix5000' else 0
    commit, append = MODES[mode]
    base = ['java', '--enable-preview', '-Xms128m', '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp', cp]
    server_name = f'{load}-{trial}-{mode}'
    server_flags = [f'-Drkern.network.javaDirectPacketCommit={str(commit).lower()}',
                    f'-Drkern.network.javaDirectByteAppend={str(append).lower()}']
    if args.jfr:
        server_flags.append('-Xlog:jfr+startup=off')
        server_flags.append(f'-XX:StartFlightRecording=filename={logs / (server_name + ".jfr")},settings=profile,dumponexit=true')
    if args.profile_writes:
        server_flags.append('-Drkern.network.measureWrites=true')
    server_command = [*base, f'-Xlog:gc,safepoint:file={logs / (server_name + "-gc.log")}',
                      *server_flags, 'GameSendBenchmark', 'server', str(clients), str(owners), str(payload), kind]
    with tempfile.TemporaryDirectory(dir=location) as runtime, \
            (logs / (server_name + '-server.log')).open('w') as server_log, \
            (logs / (server_name + '-client.log')).open('w') as client_log, \
            (logs / (server_name + '-resources.csv')).open('w', newline='') as resources_file, \
            subprocess.Popen(server_command, cwd=runtime, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=server_log, bufsize=0) as server:
        try:
            started = time.monotonic()
            resources = csv.writer(resources_file, lineterminator='\n') if args.sample_resources else None
            if resources is not None:
                resources.writerow(RESOURCES)
            port = line(server, 'READY ').split()[1]
            if resources is not None:
                sample_resources(server, resources, started, 'idle')
            client_command = [*base, 'GameSendBenchmark', 'client', port, str(clients), str(window),
                              str(payload), str(args.warmup_ms), str(args.measure_ms), str(active), str(pace_ms)]
            with subprocess.Popen(client_command, cwd=runtime, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=client_log, bufsize=0) as client:
                try:
                    try:
                        line(client, 'READY', 3 if clients <= 16 else 40)
                    except RuntimeError:
                        send(server, 'DIAG')
                        print(server_name, line(server, 'DIAG ', 2), flush=True)
                        raise
                    if resources is not None:
                        sockets, *_ = sample_resources(server, resources, started, 'connected')
                        if sockets != clients:
                            raise RuntimeError(f'expected {clients} live sockets, sampled {sockets}')
                    send(client, '')
                    line(client, 'MEASURE', args.warmup_ms / 1000 + 10)
                    send(server, 'SAMPLE')
                    before = list(map(int, line(server, 'SAMPLE ').split()[1:]))
                    wait_for_end(client, server, resources, started, args.measure_ms, clients)
                    send(server, 'SAMPLE')
                    after = list(map(int, line(server, 'SAMPLE ').split()[1:]))
                    metrics = line(client, 'RESULT ').split()[1:]
                    if client.wait(timeout=5) != 0:
                        raise RuntimeError('client failed')
                finally:
                    if client.poll() is None:
                        client.kill()
            if resources is not None:
                deadline = time.monotonic() + 5
                while True:
                    pool = sample_resources(server, resources, started, 'drained')
                    if pool[0] == pool[1] == 0 and (pool[4] == pool[5] == -1 or pool[4] >= 0 and pool[4] == pool[5]):
                        break
                    if time.monotonic() >= deadline:
                        raise RuntimeError('pool or connections retained after clients closed')
                    time.sleep(.05)
            send(server, 'STOP')
            line(server, 'DRAINED ')
            if server.wait(timeout=8) != 0:
                raise RuntimeError('server shutdown failed')
            cpu, allocation, committed, appended, rejects, *writes = (second - first for first, second in zip(before, after))
            samples = int(metrics[0])
            return [load, trial, mode, clients, active, pace_ms, window, owners, payload, kind, *metrics,
                    round(cpu / samples, 2), round(allocation / samples, 2) if before[1] >= 0 else 'NA',
                    committed, appended, rejects, *writes]
        except (OSError, RuntimeError, subprocess.SubprocessError):
            if server.poll() is None:
                try:
                    send(server, 'DIAG')
                    print(server_name, line(server, 'DIAG ', 2), flush=True)
                except (OSError, RuntimeError):
                    pass
            raise
        finally:
            if server.poll() is None:
                server.kill()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-root', type=Path, help='compile a second checkout for paired comparisons')
    parser.add_argument('--classes', type=Path, help='reuse a previously compiled full source tree')
    parser.add_argument('--deps', type=Path, default=Path(tempfile.gettempdir()) / 'rkern-game-ab-deps')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workload', action='append', choices=WORK)
    parser.add_argument('--mode', action='append', choices=MODES)
    parser.add_argument('--trials', type=int, default=5)
    parser.add_argument('--warmup-ms', type=int, default=30000)
    parser.add_argument('--measure-ms', type=int, default=30000)
    parser.add_argument('--jfr', action='store_true')
    parser.add_argument('--profile-writes', action='store_true')
    parser.add_argument('--sample-resources', action='store_true')
    args = parser.parse_args()
    if min(args.trials, args.warmup_ms, args.measure_ms) < 1:
        parser.error('counts and intervals must be positive')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    logs = output / 'logs'
    logs.mkdir(exist_ok=True)
    workloads = args.workload or ['single', 'burst']
    quota = Path('/sys/fs/cgroup/cpu.max')
    environment = dict(revision=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, cwd=HERE).strip(),
                       kernel=platform.release(), affinity=sorted(os.sched_getaffinity(0)),
                       jdk=subprocess.run(['java', '-version'], text=True, capture_output=True).stderr.splitlines()[0],
                       workloads=workloads, trials=args.trials, warmup_ms=args.warmup_ms,
                       measure_ms=args.measure_ms, jfr=args.jfr, write_profile=args.profile_writes,
                       resource_samples=args.sample_resources,
                       cpu_quota=quota.read_text().strip() if quota.exists() else None)
    failures = []
    with tempfile.TemporaryDirectory(prefix='rkern-game-ab-') as temporary:
        work = Path(temporary)
        classes = args.classes.resolve() if args.classes else work / 'classes'
        classes.mkdir(exist_ok=True)
        cp = classpath(classes, args.deps.resolve())
        compile_java(cp, classes, args.classes)
        socket_class = classes / 'l1j/server/server/model/network/core/connection/CUserSocket.class'
        server_class = (classes / 'l1j/server/Server.class').read_bytes()
        if args.profile_writes and b'networkWriteSnapshot' not in server_class:
            parser.error('--profile-writes requires network write diagnostics in the server classes')
        if args.sample_resources and b'networkPoolSnapshot' not in server_class:
            parser.error('--sample-resources requires network pool diagnostics in the server classes')
        candidates_available = b'rkern.network.javaDirectPacketCommit' in socket_class.read_bytes()
        targets = {'current': cp}
        if args.baseline_root:
            baseline_root = args.baseline_root.resolve()
            baseline_classes = work / 'baseline'
            baseline_classes.mkdir()
            baseline_cp = classpath(baseline_classes, args.deps.resolve())
            compile_java(baseline_cp, baseline_classes, False, baseline_root / 'rkern')
            targets['baseline'] = baseline_cp
            environment['baseline_revision'] = subprocess.check_output(
                ['git', 'rev-parse', 'HEAD'], text=True, cwd=baseline_root).strip()
        modes = args.mode or (list(MODES) if candidates_available else ['off'])
        if not candidates_available and any(mode != 'off' for mode in modes):
            parser.error('candidate modes are available at benchmark baseline 02b0d4e')
        environment.update(modes=modes, candidates_available=candidates_available)
        (output / 'environment.json').write_text(json.dumps(environment, indent=2) + '\n')
        with (output / 'results.csv').open('w', newline='') as file:
            writer = csv.writer(file, lineterminator="\n")
            writer.writerow(FIELDS)
            for load in workloads:
                cases = [(variant, mode) for variant in targets for mode in modes]
                for i in range(args.trials):
                    offset = i % len(cases)
                    for variant, mode in cases[offset:] + cases[:offset]:
                        trial_logs = logs / variant
                        trial_logs.mkdir(exist_ok=True)
                        try:
                            row = measure(targets[variant], work, args, i + 1, load, mode, trial_logs)
                        except (OSError, RuntimeError, subprocess.SubprocessError) as error:
                            failures.append(dict(workload=load, trial=i + 1, mode=mode, variant=variant, error=str(error)))
                            print('FAILED', variant, load, i + 1, mode, error, flush=True)
                        else:
                            writer.writerow([*row, variant])
                            file.flush()
                            print('PASS', variant, *row, flush=True)
    (output / 'failures.json').write_text(json.dumps(failures, indent=2) + '\n')
    return bool(failures)


if __name__ == '__main__':
    raise SystemExit(main())
