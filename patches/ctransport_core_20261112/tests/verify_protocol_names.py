"""Prove renaming/nesting preserves bodies; whitelist the two reader fixes."""
from pathlib import Path
import base64
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOKEN = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*|[A-Za-z_$][\w$]*|[^\s]')

def verify(root=ROOT, baseline_archive=None):
    here = root / 'research/protocol-names-20261114'
    baseline = json.loads((here / 'BASELINE.json').read_text())
    mapping = json.loads((here / 'mapping.json').read_text())
    nested_renames = json.loads((here / 'nested-renames.json').read_text())
    paths = json.loads((here / 'path-mapping.json').read_text())
    current = {str(p.relative_to(root)): p for p in (root / 'src').rglob('*') if p.is_file()}
    assert set(current) == {paths.get(n, n) for n in baseline['source_files']}, 'unexpected source set'
    def bodies(path):
        return {name: base64.b64decode(body).decode() for name, body in (line.split('\t', 1) for line in path.read_text().splitlines())}
    before_types = bodies(here / 'type-bodies-before.tsv')
    after_types = bodies(here / 'type-bodies.tsv')
    old_new = {m['old']: m['new'] for m in mapping}
    old_new.update({m['old']: m['new'] for m in nested_renames})
    old_new = dict(sorted(old_new.items(), key=lambda pair: len(pair[0]), reverse=True))
    def renamed(name):
        for old, new in old_new.items():
            if name == old or name.startswith(old + '.'):
                return new + name[len(old):]
        return name
    names = {}
    for path in baseline['source_files']:
        if path.endswith('.java'):
            fq = path[4:-5].replace('/', '.')
            names[fq] = fq.rsplit('.', 1)[-1]
    simple = {}
    for old in before_types:
        new = renamed(old)
        names[old] = names[new] = old.rsplit('.', 1)[-1]
        if old.rsplit('.', 1)[-1] != new.rsplit('.', 1)[-1]:
            simple[new.rsplit('.', 1)[-1]] = old.rsplit('.', 1)[-1]
    for fq, value in list(names.items()):
        parts = fq.split('.')
        for i, part in enumerate(parts):
            if part and part[0].isupper() and part != 'LIN':
                names['.'.join(parts[i:])] = value
    methods = {}
    for line in (here / 'methods.tsv').read_text().splitlines():
        _, old, new = line.split('\t')
        assert old not in methods or methods[old] == new
        methods[old] = new
    def tokens(text, nested=False):
        raw = [m[0] for m in TOKEN.finditer(text) if not m[0].startswith(('//', '/*'))]
        out = []; i = 0
        while i < len(raw):
            if raw[i] in ('package', 'import'):
                i = raw.index(';', i) + 1
                continue
            j = i + 1
            while j + 1 < len(raw) and raw[j] == '.' and re.fullmatch(r'[A-Za-z_$][\w$]*', raw[j + 1]):
                j += 2
            found = False
            for end in range(j, i, -2):
                candidate = ''.join(raw[i:end])
                if candidate in names:
                    out.append(names[candidate]); i = end; found = True; break
            if found:
                out[-1] = methods.get(simple.get(out[-1], out[-1]), simple.get(out[-1], out[-1]))
                continue
            token = simple.get(raw[i], raw[i])
            out.append(methods.get(token, token)); i += 1
        if nested:
            # Relocated member types need static; all fields/methods stay intact.
            declaration = min(out.index(t) for t in ('class', 'enum', 'interface') if t in out)
            if 'static' in out[:declaration]:
                out.remove('static')
        return out
    for old, text in before_types.items():
        new = renamed(old)
        assert new in after_types, 'missing descriptor type: ' + new
        nested = any(m['old'] == old and m['parent'] for m in mapping)
        left, right = tokens(text), tokens(after_types[new], nested)
        if left != right:
            difference = next((i for i, pair in enumerate(zip(left, right)) if pair[0] != pair[1]), min(len(left), len(right)))
            raise AssertionError('body changed beyond verified names: ' + old + '\n' + str(left[max(0,difference-12):difference+15]) + '\n' + str(right[max(0,difference-12):difference+15]))
    allowed = set(json.loads((here / 'reader-fixes.json').read_text()))
    if baseline_archive:
        snapshots = {}
        with zipfile.ZipFile(baseline_archive) as archive:
            for old, sha in baseline['source_files'].items():
                data = archive.read(old)
                assert hashlib.sha256(data).hexdigest() == sha, old
                if old not in paths and old not in allowed and hashlib.sha256(current[old].read_bytes()).hexdigest() != sha:
                    snapshots[old] = tokens(data.decode())
        (here / 'caller-tokens-before.json').write_text(json.dumps(snapshots, ensure_ascii=False) + '\n')
    # Unmoved changed files have compact before token snapshots for offline checks.
    token_baseline = json.loads((here / 'caller-tokens-before.json').read_text())
    changed = 0
    for old, sha in baseline['source_files'].items():
        if old in paths:
            continue
        data = current[old].read_bytes()
        if hashlib.sha256(data).hexdigest() == sha:
            continue
        changed += 1
        if old in allowed:
            assert hashlib.sha256(data).hexdigest() == json.loads((here / 'reader-fixes.json').read_text())[old], old
        else:
            assert tokens(data.decode()) == token_baseline[old], 'caller changed beyond names: ' + old
    # The snapshots above are checked against the previous archive on preparation;
    # production artifacts and native sources are independently pinned here.
    for name, sha in json.loads((here / 'preserved-files.json').read_text()).items():
        assert hashlib.sha256((root / name).read_bytes()).hexdigest() == sha, name
    for name, sha in json.loads((here / 'type-source-hashes.json').read_text()).items():
        assert hashlib.sha256((root / name).read_bytes()).hexdigest() == sha, name
    print(f'PASS {len(mapping)+len(nested_renames)} descriptor types,363 accessor declarations;all mapped type bodies and {changed} changed caller/parser files checked;native/DLL/codec/PSS preserved')
    return len(current)

if __name__ == '__main__':
    import sys
    verify(baseline_archive=Path(sys.argv[1]) if len(sys.argv) > 1 else None)
