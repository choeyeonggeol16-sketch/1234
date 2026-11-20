#!/usr/bin/env python3
import argparse
import concurrent.futures
import csv
import io
import json
import os
from pathlib import Path
import platform
import select
import shutil
import subprocess
import tempfile
import urllib.request

HERE = Path(__file__).resolve().parent
RKERN = HERE.parents[1]
NETWORK = RKERN / 'src/l1j/server/server/model/network'
NETTY = '4.2.21.Final'
BACKENDS = ('rkern', 'netty-nio', 'netty-epoll', 'netty-io_uring')
WORKLOADS = (('persistent', 1), ('persistent', 16), ('churn', 8))
FIELDS = ('backend', 'trial', 'workload', 'clients', 'samples', 'ops_per_s', 'ops_per_s_per_client',
          'rtt_p50_us', 'rtt_p95_us', 'rtt_p99_us', 'connect_p50_us', 'connect_p95_us', 'connect_p99_us',
          'server_rss_kib', 'server_peak_rss_kib', 'jdk_direct_bytes', 'echo_or_netty_pool_bytes')


def netty_classpath(cache):
    arch = {'x86_64': 'x86_64', 'aarch64': 'aarch_64'}[platform.machine()]
    artifacts = ['common', 'buffer', 'transport', 'resolver', 'transport-native-unix-common',
                 'transport-classes-epoll', 'transport-classes-io_uring']
    native = ('transport-native-epoll', 'transport-native-io_uring')
    cache.mkdir(parents=True, exist_ok=True)

    def fetch(artifact):
        name = 'netty-' + artifact
        suffix = '-linux-' + arch if artifact in native else ''
        jar = f'{name}-{NETTY}{suffix}.jar'
        path = cache / jar
        if not path.exists():
            url = f'https://repo.maven.apache.org/maven2/io/netty/{name}/{NETTY}/{jar}'
            with urllib.request.urlopen(url, timeout=20) as download:
                path.write_bytes(download.read())
        return path

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as workers:
        return os.pathsep.join(str(path) for path in workers.map(fetch, [*artifacts, *native]))


def compile_sources(classes, netty):
    compiler = [shutil.which('javac')] if shutil.which('javac') else [
        'java', '--module', 'jdk.compiler/com.sun.tools.javac.Main']
    sources = [NETWORK / 'buffer/ReadBuffer.java', NETWORK / 'buffer/WriteBuffer.java',
               NETWORK / 'codec/Cryptor.java', NETWORK.parents[1] / 'Opcodes.java',
               *sorted((NETWORK / 'core/transport/selector').glob('*.java')),
               HERE / 'TransportServer.java', HERE / 'TransportDriver.java']
    subprocess.run([*compiler, '-encoding', 'UTF-8', '-cp', netty, '-d', str(classes), *sources], check=True)


def rss(pid):
    values = {}
    for line in Path(f'/proc/{pid}/status').read_text().splitlines():
        key, _, value = line.partition(':')
        if key in ('VmRSS', 'VmHWM'):
            values[key] = int(value.split()[0])
    return values['VmRSS'], values['VmHWM']


def server_message(server, timeout):
    readable, _, _ = select.select([server.stdout], [], [], timeout)
    return server.stdout.readline().strip() if readable else ''


def server_round(classpath, backend, trial, args, work):
    java = ['java', '-Xms64m', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', classpath]
    stderr_path = work / f'{backend}-{trial}.log'
    with stderr_path.open('w+') as stderr:
        with subprocess.Popen([*java, 'TransportServer', backend], stdin=subprocess.PIPE,
                              stdout=subprocess.PIPE, stderr=stderr, text=True) as server:
            try:
                first = server_message(server, 15)
                if first.startswith('UNAVAILABLE '):
                    if server.wait(timeout=5):
                        raise RuntimeError(first)
                    return first.removeprefix('UNAVAILABLE '), []
                if not first.startswith('READY '):
                    raise RuntimeError(f'{backend}: no ready signal ({first!r})')
                _, port, provider = first.split()
                rows = []
                for workload, clients in args.workloads:
                    data = subprocess.run([*java, 'TransportDriver', port, str(clients), workload,
                                           str(args.warmup_ms), str(args.measure_ms)],
                                          check=True, timeout=20, text=True, capture_output=True)
                    tag, samples, *metrics = data.stdout.strip().split()
                    if tag != 'RESULT' or int(samples) == 0:
                        raise RuntimeError(data.stdout)
                    server.stdin.write('MEMORY\n')
                    server.stdin.flush()
                    sample = server_message(server, 5).split()
                    if len(sample) != 3 or sample[0] != 'MEMORY':
                        raise RuntimeError(f'{backend}: missing workload memory sample')
                    rows.append([backend, trial, workload, clients, samples, *metrics, *rss(server.pid), *sample[1:]])
                server.communicate('STOP\n', timeout=10)
                if server.returncode:
                    raise RuntimeError(f'{backend}: server shutdown failed ({server.returncode})')
                return provider, rows
            except Exception as failure:
                stderr.seek(0)
                print(f'{backend} server stderr:\n{stderr.read()}')
                if isinstance(failure, subprocess.CalledProcessError):
                    print(failure.stderr)
                raise
            finally:
                if server.poll() is None:
                    server.kill()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--trials', type=int, default=3)
    parser.add_argument('--warmup-ms', type=int, default=750)
    parser.add_argument('--measure-ms', type=int, default=1500)
    parser.add_argument('--workload', choices=('persistent', 'churn'))
    parser.add_argument('--backend', choices=BACKENDS, action='append')
    parser.add_argument('--cache', type=Path, default=Path(tempfile.gettempdir()) / f'rkern-netty-{NETTY}')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if min(args.trials, args.warmup_ms, args.measure_ms) <= 0:
        parser.error('trials and durations must be positive')
    args.workloads = [work for work in WORKLOADS if not args.workload or work[0] == args.workload]
    backends = tuple(args.backend or BACKENDS)
    with tempfile.TemporaryDirectory(prefix='rkern-echo-') as temporary:
        work = Path(temporary)
        classes = work / 'classes'
        classes.mkdir()
        netty = netty_classpath(args.cache)
        compile_sources(classes, netty)
        classpath = os.pathsep.join((str(classes), netty))
        providers = {}
        unavailable = {}
        failed_rounds = {}
        rows = []
        for i in range(args.trials):
            offset = i % len(backends)
            for backend in backends[offset:] + backends[:offset]:
                if backend in unavailable:
                    continue
                try:
                    provider, batch = server_round(classpath, backend, i + 1, args, work)
                except (OSError, RuntimeError, subprocess.SubprocessError) as failure:
                    failed_rounds[f'{backend}/{i + 1}'] = str(failure)
                    print(f'FAILED {backend} trial={i + 1} {failure}', flush=True)
                    continue
                if not batch:
                    unavailable[backend] = provider
                else:
                    providers[backend] = provider
                    rows.extend(batch)
                print(f'PROGRESS {backend} trial={i + 1} {provider}', flush=True)
    models = [line.partition(':')[2].strip() for line in Path('/proc/cpuinfo').read_text().splitlines()
              if line.startswith('model name')]
    quota = Path('/sys/fs/cgroup/cpu.max')
    linux_tcp = Path('/proc/sys/net/ipv4')
    env = dict(kernel=platform.release(), architecture=platform.machine(), cpu=models[0] if models else None,
               available_cpus=len(os.sched_getaffinity(0)), cpu_quota=quota.read_text().strip() if quota.exists() else None,
               ephemeral_ports=(linux_tcp / 'ip_local_port_range').read_text().split(),
               tcp_tw_reuse=(linux_tcp / 'tcp_tw_reuse').read_text().strip(),
               jdk=subprocess.run(['java', '-version'], text=True, capture_output=True, check=True).stderr.splitlines()[0],
               netty=NETTY, revision=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=HERE, text=True).strip(),
               trials=args.trials, warmup_ms=args.warmup_ms, measure_ms=args.measure_ms,
               workloads=args.workloads, memory_sample='after each workload, before server shutdown',
               providers=providers, unavailable=unavailable, failed_rounds=failed_rounds)
    result = io.StringIO(newline='')
    writer = csv.writer(result, lineterminator='\n')
    writer.writerow(FIELDS)
    writer.writerows(sorted(rows, key=lambda row: (row[0], row[2], row[3], row[1])))
    print('ENV ' + json.dumps(env, ensure_ascii=False))
    print('CSV_BEGIN', result.getvalue(), 'CSV_END', sep='\n')
    if args.output:
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / 'results.csv').write_text(result.getvalue())
        (args.output / 'environment.json').write_text(json.dumps(env, indent=2, ensure_ascii=False) + '\n')
    if failed_rounds:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
