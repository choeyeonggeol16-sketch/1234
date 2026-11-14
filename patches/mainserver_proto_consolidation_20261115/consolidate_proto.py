#!/usr/bin/env python3
"""Reorganize only protobuf-specific Java code, preserving all behavior."""
import argparse, hashlib, json, re, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = 'l1j.server.server.packets.Protocol'
DST = BASE + '.MainServer.Proto'
CS = 'l1j.server.server.model.network.core.cs.packet'
SC = 'l1j.server.server.model.network.core.sc.packet'
OLD_FILE = 'CTransport_MainServer_PlaySupport_FULL_SRC_20261115.zip'
NEW_FILE = 'CTransport_MainServer_Proto_Consolidated_FULL_SRC_20261115.zip'
EXPECTED = '296ae9bfc20001b13e44d5475c4225dd9fb4999895def4b89a500a8967f1fea7'
CORE = ('CSProto','Proto','ProtoMsg','ProtoPacket','ProtoPacketWriter','ProtoReader','ProtoWriter')
STREAM = ('ProtoInputStream','ProtoOutputStream','WireFormat')
RENAMES = {
    **{BASE+'.'+n:DST+'.'+n for n in CORE},
    **{BASE+'.Stream.'+n:DST+'.Stream.'+n for n in STREAM},
    CS+'.CS_ExtendedProto':DST+'.Dispatch.CS_ExtendedProto',
    SC+'.SC_NOTIFICATION_MESSAGE':DST+'.Adapter.SC_NOTIFICATION_MESSAGE'
}
digest=lambda data:hashlib.sha256(data).hexdigest()
loc=lambda fqcn:'src/'+fqcn.replace('.','/')+'.java'

def migrate(paths):
    moved={loc(o):loc(n) for o,n in RENAMES.items()}
    assert len(moved)==12
    assert set(moved)<=set(paths) and not set(moved.values())&set(paths)
    pattern=re.compile(r'(?<![\w])(?:'+'|'.join(re.escape(s) for s in sorted(RENAMES,key=len,reverse=True))+r')(?!\w)')
    wbase=re.compile(r'(?m)^(\s*import\s+(?:static\s+)?)'+re.escape(BASE)+r'\.\*(\s*;)')
    wstream=re.compile(r'(?m)^(\s*import\s+(?:static\s+)?)'+re.escape(BASE+'.Stream')+r'\.\*(\s*;)')
    target={}
    changed=[]
    count=0
    for path,data in sorted(paths.items()):
        if not path.endswith('.java'):
            target[path]=data
            continue
        dstpath=moved.get(path,path)
        txt=data.decode('utf8')
        if dstpath!=path:
            oldpkg='.'.join(path[4:].split('/')[:-1])
            newpkg='.'.join(dstpath[4:].split('/')[:-1])
            p=re.compile(r'(?m)^(\s*package\s+)'+re.escape(oldpkg)+r'(\s*;)')
            txt,n=p.subn(lambda m:m[1]+newpkg+m[2],txt,count=1)
            assert n==1,(path,'package declaration missing')
        txt,n=pattern.subn(lambda m:RENAMES[m[0]],txt)
        count+=n
        txt=wstream.sub(lambda m:m[1]+DST+'.Stream.*'+m[2],txt)
        txt=wbase.sub(lambda m:m[1]+DST+'.*'+m[2],txt)
        if dstpath!=path or txt.encode('utf8')!=data:
            changed.append(dstpath)
        assert dstpath not in target
        target[dstpath]=txt.encode('utf8')
    assert len(target)==len(paths)
    assert sum(p.endswith('.java') for p in target)==1711
    assert not set(moved)&set(target)
    assert target['CTransport.dll']==paths['CTransport.dll']
    for p,b in target.items():
        if not p.endswith('.java'):
            assert b==paths[p],p
        else:
            s=b.decode('utf8')
            assert not pattern.search(s),p
            assert not wbase.search(s) and not wstream.search(s),p
    assert not any(p.startswith('src/l1j/server/server/model/network/core/transport/') for p in changed)
    return target,dict(moved_java=12,edited_java=len(changed),references_updated=count,classes=RENAMES)

DOC='''# MainServer Proto map

All dedicated Protobuf Java classes live below the Protocol/MainServer tree.
Proto: CSProto, ProtoMsg, Proto, ProtoReader, ProtoWriter, and packet writers.
Proto/Stream: ProtoInputStream, ProtoOutputStream, WireFormat.
Proto/Dispatch: CS_ExtendedProto incoming handler.
Proto/Adapter: SC_NOTIFICATION_MESSAGE compatibility adapter.
Indun/Proto, Inventory/Proto, Companion/Proto, Pledge/Proto:
functional message classes.
PlaySupport: existing PSS implementation and PlaySupport/Proto native window.
MainServer/Proto/Attend, Craft, MonsterBook, Ranker: packet helper groups.

The 12 proto schema texts in schemas/ were reconstructed from client binary
FileDescriptorProto data. They are not original NC-authored source.

Unrelated legacy game packet handlers in network/core/(cs,sc)/packet remain
in place. CIO transport, game logic, message IDs and wire encoding are not
modified. Package grouping is a Java organization decision.
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--input',type=Path,default=ROOT/'downloads'/OLD_FILE)
    p.add_argument('--output',type=Path,default=ROOT/'downloads'/NEW_FILE)
    p.add_argument('--work',type=Path,default=Path('/tmp/mainserver-proto'))
    a=p.parse_args()
    original=a.input.read_bytes()
    assert digest(original)==EXPECTED,'Input source ZIP mismatch'
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    result,report=migrate(files)
    schemas=list((ROOT/'patches/mainserver_protocol_20261114/schemas').glob('*.proto'))
    assert len(schemas)==12
    for s in schemas:
        result['docs/MainServerProto/schemas/'+s.name]=s.read_bytes()
    result['docs/MainServerProto/README.md']=DOC.encode('utf8')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,data in sorted(result.items()):
            z.writestr(path,data)
    with zipfile.ZipFile(a.output) as z:
        assert z.testzip() is None
        assert len(z.namelist())==len(files)+13
    a.work.mkdir(parents=True,exist_ok=True)
    for path,data in result.items():
        if path.startswith('src/'):
            fp=a.work/path
            fp.parent.mkdir(parents=True,exist_ok=True)
            fp.write_bytes(data)
    report.update(input_sha256=EXPECTED,output_sha256=digest(a.output.read_bytes()),
                  java_count=1711,schema_count=len(schemas),
                  dll_sha256_unchanged=digest(files['CTransport.dll']),
                  actual_windows_runtime_tested=False)
    (a.work/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':
    main()
