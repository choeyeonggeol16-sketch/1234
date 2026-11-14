"""Compile the full source tree, compare wire goldens, and test strict readers."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--libs', required=True, type=Path)
p.add_argument('--classes', type=Path, default=ROOT / 'build/protocol-classes')
a = p.parse_args()
out = a.classes.resolve()
assert out.is_relative_to((ROOT / 'build').resolve()) and out != (ROOT / 'build').resolve(), 'output must be a child of build/'
if out.exists():
    shutil.rmtree(out)
out.mkdir(parents=True)
jars = sorted(a.libs.resolve().glob('*.jar'))
assert jars, 'dependency jars required'
cp = os.pathsep.join(map(str, jars))
javac = ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '21', '-target', '21', '--enable-preview', '-proc:none', '-encoding', 'UTF-8']
sources = sorted((ROOT / 'src').rglob('*.java'))
args = ['-cp', cp, '-d', str(out), *map(str, sources)]
argfile = out / 'sources.args'
argfile.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in args) + '\n')
subprocess.run([*javac, '@' + str(argfile)], cwd=ROOT, check=True)
cp = str(out) + os.pathsep + cp
subprocess.run([*javac, '-cp', cp, '-d', str(out), *map(str, [ROOT / 'tests/ProtoWireSnapshot.java', ROOT / 'tests/ProtoVarintTest.java', ROOT / 'tests/ExtendedPacketDecoderTest.java'])], cwd=ROOT, check=True)
for name, args in [
    ('ProtoWireSnapshot', ['after', 'research/protocol-names-20261114/classes.tsv', 'research/protocol-names-20261114/wire-before.tsv']),
    ('ProtoVarintTest', []),
    ('l1j.server.server.model.network.core.connection.ExtendedPacketDecoderTest', []),
]:
    subprocess.run(['java', '--enable-preview', '-cp', cp, name, *args], cwd=ROOT, check=True)
subprocess.run(['python', str(ROOT / 'tests/verify_protocol_names.py')], cwd=ROOT, check=True)
print(f'PASS clean full compile:{len(sources)} Java sources;wire/reader/actual dispatcher/source scope')
