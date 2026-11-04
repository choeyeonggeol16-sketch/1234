#!/usr/bin/env python3
"""Run the actual PE32 bridge against bounded local protocol fixtures under Wine.

This checks its Windows ABI/worker, not a live game or the native UI renderer.
"""
import os
import socket
import subprocess
import threading
import time
from pathlib import Path

nonce = None
ready = threading.Event()
errors = []
frames = []

def read_exact(peer, size):
    data = bytearray()
    while len(data) < size:
        chunk = peer.recv(size - len(data))
        if not chunk:
            raise AssertionError("unexpected EOF")
        data.extend(chunk)
    return bytes(data)

def game_channel(listener):
    global nonce
    try:
        peer, _ = listener.accept()
        with peer:
            peer.settimeout(15)
            for _ in range(18):
                frame = read_exact(peer, 7)
                assert frame[:3] == b'\x07\x00\x51' and frame[5:] == b'\x00\x00', frame.hex()
                frames.append(int.from_bytes(frame[3:5], 'little'))
            assert frames[0] == 0x7df0 and frames[-1] == 0x7df1
            assert all(i & 0xff00 == 0x7e00 for i in frames[1:-1])
            nonce = bytes(i & 255 for i in frames[1:-1])
            ready.set()
            assert peer.recv(1) == b'', "unexpected game operation after UI open"
    except Exception as error:
        errors.append(error)
        ready.set()

def control_channel(listener):
    try:
        peer, _ = listener.accept()
        with peer:
            peer.settimeout(15)
            hello = read_exact(peer, 24)
            assert ready.wait(5) and nonce is not None
            assert hello == b'PSSUI\x01\x00\x00' + nonce, hello.hex()
            peer.sendall(b'\x10')
            time.sleep(.1)
            peer.sendall(b'\x01')
            assert peer.recv(1) == b'', "unexpected companion operation after UI open"
    except Exception as error:
        errors.append(error)

with socket.socket() as game, socket.socket() as control:
    for peer, port in ((game, 5000), (control, 5001)):
        peer.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        peer.bind(('127.0.0.1', port))
        peer.listen(1)
        peer.settimeout(20)
    threads = [threading.Thread(target=game_channel, args=(game,)),
               threading.Thread(target=control_channel, args=(control,))]
    for thread in threads:
        thread.start()
    env = dict(PATH='/usr/bin:/bin', DISPLAY=':88', XDG_CACHE_HOME='/tmp/cache',
               WINEARCH='win32', WINEDEBUG='-all', WINEDLLOVERRIDES='mscoree,mshtml=',
               WINEPREFIX='/lab/harness-output/wine-shim-prefix',
               WINELOADER='/work/native/wine32-shim', WINELOADERNOEXEC='1',
               WINESERVER='/usr/lib/wine/wineserver64',
               WINEDLLPATH='/usr/lib/i386-linux-gnu/wine')
    command = [str(Path(__file__).resolve().with_name('BridgeHarness.exe'))] if os.name == 'nt' else [
        '/work/native/wine32-shim', r'Z:\work\native\BridgeHarness.exe']
    result = subprocess.run(command, env=os.environ if os.name == 'nt' else env,
                            timeout=20, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True)
    print(result.stdout, end='')
    for thread in threads:
        thread.join(21)
    assert result.returncode == 0, result.returncode
    assert not errors, errors
    assert not any(thread.is_alive() for thread in threads)
    print('PASS actual PE32 native ABI + 18 bootstrap frames + token-bound OPEN; memory/TCP-owner/UI test adapters')
