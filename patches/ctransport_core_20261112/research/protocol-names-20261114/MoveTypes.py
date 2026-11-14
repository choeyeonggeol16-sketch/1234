"""Apply the verified descriptor nesting after ProtoRename resolves callers."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
mapping = json.loads((HERE / 'mapping.json').read_text())
texts = {m['new']: (ROOT / m['path']).read_text() for m in mapping}
imports = {name: set(re.findall(r'^import [^\n]+;', text, re.M)) for name, text in texts.items()}

for m in sorted(mapping, key=lambda item: item['new'].count('.'), reverse=True):
    if not m['parent']:
        continue
    name, parent = m['new'], m['parent']
    text = texts[name]
    match = re.search(r'\bpublic\s+(?:(?:final|abstract)\s+)?(?:class|enum|interface)\s+\w+', text)
    assert match, name
    body = text[match.start():].rstrip()
    body = body.replace('public ', 'public static ', 1)
    outer = texts[parent]
    end = outer.rfind('}')
    assert end >= 0, parent
    texts[parent] = outer[:end] + '\n\n    // Descriptor: ' + name + '\n' + '\n'.join('    ' + line if line else '' for line in body.splitlines()) + '\n' + outer[end:]
    imports[parent].update(imports[name])

paths = {}
for m in mapping:
    new_path = 'src/' + m['new'].split('.')[0] + '/' + m['new'].split('.')[1] + '.java'
    paths[m['path']] = new_path
    if m['parent']:
        continue
    text = re.sub(r'^import [^\n]+;\n?', '', texts[m['new']], flags=re.M)
    text = text.replace('package LIN;', 'package LIN;\n\n' + '\n'.join(sorted(imports[m['new']])), 1)
    dest = ROOT / new_path
    dest.parent.mkdir(parents=True, exist_ok=True)
    assert not dest.exists(), dest
    dest.write_text(text)

for m in mapping:
    (ROOT / m['path']).unlink()
(HERE / 'path-mapping.json').write_text(json.dumps(paths, indent=2) + '\n')
print(f'Moved {len(mapping)} types into {sum(not m["parent"] for m in mapping)} LIN source files with descriptor nesting')
