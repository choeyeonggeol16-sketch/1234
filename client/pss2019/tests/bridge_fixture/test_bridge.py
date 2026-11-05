#!/usr/bin/env python3
"""Actual PE32 worker against local socket fixtures, including legacy fallback."""
import os,socket,subprocess,threading,time
from pathlib import Path

def exact(peer,n):
    data=b''
    while len(data)<n:
        piece=peer.recv(n-len(data))
        if not piece:raise AssertionError('truncated frame')
        data+=piece
    return data

def run(legacy):
    ready=threading.Event();nonce=[];errors=[];versions=[]
    def guarded(fn):
        def invoke():
            try:fn()
            except Exception as e:errors.append(repr(e));ready.set()
        return invoke
    with socket.socket() as game,socket.socket() as control:
        for server,port in ((game,5000),(control,5001)):
            server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            server.bind(('127.0.0.1',port));server.listen(2);server.settimeout(20)
        def game_thread():
            peer,_=game.accept()
            with peer:
                peer.settimeout(15);frames=[]
                for _ in range(18):
                    frame=exact(peer,7);assert frame[:3]==b'\x07\x00\x51' and frame[5:]==b'\x00\x00'
                    frames.append(int.from_bytes(frame[3:5],'little'))
                assert frames[0]==0x7df0 and frames[-1]==0x7df1
                assert all(v&0xff00==0x7e00 for v in frames[1:-1])
                nonce.append(bytes(v&255 for v in frames[1:-1]));ready.set()
                assert peer.recv(1)==b''
        def control_thread():
            for attempt in range(2 if legacy else 1):
                peer,_=control.accept()
                with peer:
                    peer.settimeout(15);hello=exact(peer,24)
                    assert ready.wait(5) and nonce
                    version=hello[5];versions.append(version)
                    assert hello[:5]==b'PSSUI' and hello[6:8]==b'\x00\x00' and hello[8:]==nonce[0]
                    if legacy and attempt==0:
                        assert version==2;continue
                    assert version==(1 if legacy else 2)
                    peer.sendall(b'\x10')
                    if not legacy:
                        for byte in b'\x03\x78\x56\x34\x12':peer.sendall(bytes([byte]));time.sleep(.015)
                    peer.sendall(b'\x01');assert peer.recv(1)==b''
        threads=[threading.Thread(target=guarded(fn),daemon=True) for fn in (game_thread,control_thread)]
        for t in threads:t.start()
        env=dict(os.environ,WINEARCH='win32',WINEDEBUG='-all',DISPLAY=':90',WINEPREFIX='/lab/harness-output/wine-shim-prefix',WINELOADERNOEXEC='1',WINELOADER='/work/native/wine32-shim',WINESERVER='/usr/lib/wine/wineserver64',WINEDLLPATH='/usr/lib/i386-linux-gnu/wine/i386-windows:/usr/lib/i386-linux-gnu/wine/i386-unix')
        command=['/work/native/wine32-shim','/work/v5-build/BridgeHarness.exe']+(['legacy'] if legacy else [])
        result=subprocess.run(command,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=25)
        print(result.stdout,end='')
        for t in threads:t.join(3)
        assert result.returncode==0,(result.returncode,errors)
        assert not errors and not any(t.is_alive() for t in threads),errors
        assert versions==([2,1] if legacy else [2]),versions
    print('PASS PE32 bridge '+('v2 rejection -> v1 fallback with same authenticated token' if legacy else 'v2 fragmented blocked-shot payload followed by OPEN'))
run(False)
run(True)
