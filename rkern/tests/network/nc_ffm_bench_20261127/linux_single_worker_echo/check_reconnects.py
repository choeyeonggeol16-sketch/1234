import os
from pathlib import Path
import socket
import subprocess
import time

from check_frames import check, frame, receive


def socket_files(pid):
    sockets = set()
    for descriptor in Path('/proc', str(pid), 'fd').iterdir():
        try:
            target = os.readlink(descriptor)
        except FileNotFoundError:
            continue
        if target.startswith('socket:['):
            sockets.add(target)
    return sockets


def exercise(implementation):
    command = ['java', '--enable-preview', '--enable-native-access=ALL-UNNAMED',
               '-cp', 'classes', implementation, '258', '64']
    with subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True) as server:
        try:
            port = int(server.stdout.readline().split()[1])
            check(port)
            time.sleep(.1)
            before = socket_files(server.pid)
            for i in range(2000):
                data = frame(i)
                with socket.create_connection(('127.0.0.1', port), timeout=2) as channel:
                    channel.sendall(data)
                    assert receive(channel, len(data)) == data
            deadline = time.monotonic() + 2
            while socket_files(server.pid) != before and time.monotonic() < deadline:
                time.sleep(.02)
            assert socket_files(server.pid) == before
        finally:
            msg = server.communicate('STOP\n', timeout=5)[0].strip()
        assert server.returncode == 0 and 'accepted=2003 closed=2003' in msg and '2011' in msg
        print(f'{implementation}: split reads, ordered pipeline, reconnect PASS. '
              f'2000 reconnects, socket FD set restored. {msg}')


if __name__ == '__main__':
    for implementation in ['FfmEcho', 'SelectorEcho']:
        exercise(implementation)
