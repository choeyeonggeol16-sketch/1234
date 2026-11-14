#!/usr/bin/env python3
import hashlib,zipfile,re,collections,ast
P='downloads/CTransport_SharedBroadcast_FinalFive_FULL_SRC_20261115.zip'
with open(P,'rb') as f:blob=f.read()
print('BASE_SHA',hashlib.sha256(blob).hexdigest())
with zipfile.ZipFile(P) as z:
    assert z.testzip() is None
    files={i.filename:z.read(i).decode('utf8',errors='replace') for i in z.infolist() if i.filename.endswith('.java')}
print('JAVA',len(files))
pfx='src/l1j/server/server/model/network/core/'
for short in ['broadcast/CSharedBroadcast.java','broadcast/CAsyncSendConstBuffer.java','broadcast/CAsyncSendConstBufferExceptSelf.java','connection/CUserSocket.java','sc/packet/SC_DropItem.java','sc/packet/SC_RangeSkill.java','sc/packet/SC_UseArrowSkill.java','sc/packet/SC_UseAttackSkill.java','sc/packet/SC_WorldPutObject.java']:
    s=files[pfx+short]
    print('\n###',short,'LEN',len(s))
    for i,line in enumerate(s.splitlines(),1):
        if re.search(r'CSharedBroadcast\.(send|sendAll|sendPlayers)\(|shared\.apply|shared\.applyConfirmed|legacy|wire\s*==\s*null|frozen\s*==\s*null|sendFrozen\(|send\(',line):
            if short in ['connection/CUserSocket.java'] and not any(w in line for w in ['enqueueConstBuffer','sendFrozen']):continue
            print(i,line.strip()[:160])
counter=collections.Counter()
sites=[]
for p,s in files.items():
    if not p.startswith(pfx):continue
    calls=len(re.findall(r'CSharedBroadcast\.(?:send|sendAll|sendPlayers)\s*\(',s))
    if calls:
        counter[p.split('/')[-1]]+=calls
        for line,m in enumerate(s.splitlines(),1):
            if 'CSharedBroadcast.send' in m:
                sites.append((p.split('/')[-1],line,m.strip()[:220]))
print('\nSUMMARY total sites',len(sites),'classes',len(counter))
for n in sorted(counter):print('COUNT',n,counter[n])
for (n,l,x) in sites:print('SITE',n,l,x)
