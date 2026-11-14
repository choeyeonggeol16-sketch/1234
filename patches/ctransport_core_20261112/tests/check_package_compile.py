"""Compile the exact Java source groups used by run_windows.ps1 on Linux."""
from pathlib import Path
import os
import shlex
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/package-layout-windows-matrix'
out.mkdir(exist_ok=True)
variables = {
    '$core': 'src/l1j/server/server/model/network/core',
    '$pss': 'src/l1j/server/server/model/character/pss/window/PssWindowTransport.java',
    '$ownerSources': (root / 'tests/owner-production-sources.txt').read_text().splitlines(),
    '$ownerFixtures': [str(p) for p in (root / 'tests/owner-fixtures').glob('*.java')],
    '$copyFixtures': [str(p) for p in (root / 'tests/send-copy-contract').glob('*.java')],
    '$broadcastFixtures': [str(p) for p in (root / 'tests/broadcast-followup').glob('*.java')],
    '$managerFixtures': [str(p) for p in (root / 'tests/manager-fixtures').glob('*.java')],
}
commands = [line.strip()[len('& javac '):] for line in (root / 'tests/run_windows.ps1').read_text().splitlines()
            if line.strip().startswith('& javac ')]
log = root / 'logs/package-layout-windows-matrix.log'
with log.open('w') as stream:
    for index, command in enumerate(commands, 1):
        args = []
        for token in shlex.split(command):
            if isinstance(variables.get(token), list):
                args.extend(variables[token])
            else:
                for variable, value in variables.items():
                    if isinstance(value, str):
                        token = token.replace(variable, value)
                args.append(token)
        for n, token in enumerate(args):
            if token == '--release':
                args[n:n+2] = ['-source', '21', '-target', '21']
                break
        for n, token in enumerate(args):
            if n and args[n-1] == '-d':
                args[n] = str(out / token)
            elif n and args[n-1] == '-cp':
                args[n] = os.pathsep.join(str(out / item) for item in token.split(';'))
            elif token.endswith('.java') and not Path(token).is_absolute():
                args[n] = str(root / token)
        stream.write('GROUP ' + str(index) + '\n')
        stream.flush()
        result = subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', *args],
                                cwd=root, stdout=stream, stderr=subprocess.STDOUT, timeout=60)
        if result.returncode:
            raise SystemExit('Java group ' + str(index) + ' failed; see ' + str(log))
        print('PASS Windows Java source group', index, flush=True)
print('PASS all', len(commands), 'Windows Java compile groups;DLL execution is tested separately on Windows')
