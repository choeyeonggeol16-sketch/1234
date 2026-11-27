"""Correctness checks for an already-running, local fixed-frame echo server."""

import argparse
import select
import socket


PAYLOAD_BYTES = 256
FRAME_BYTES = PAYLOAD_BYTES + 2
LOOPBACK = "127.0.0.1"
TIMEOUT = 3.0


def frame(sequence):
    payload = bytes((sequence * 47 + index * (sequence + 3)) & 255 for index in range(PAYLOAD_BYTES))
    return FRAME_BYTES.to_bytes(2, "little") + payload


def receive(stream, count):
    received = bytearray()
    while len(received) < count:
        chunk = stream.recv(count - len(received))
        if not chunk:
            raise AssertionError(f"peer closed after {len(received)} of {count} bytes")
        received.extend(chunk)
    return bytes(received)


def expect(stream, frames, description):
    for position, expected in enumerate(frames, 1):
        actual = receive(stream, len(expected))
        if actual != expected:
            raise AssertionError(f"{description} frame {position} differs from request")


def connect(port):
    stream = socket.create_connection((LOOPBACK, port), timeout=TIMEOUT)
    stream.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    return stream


def expect_waiting(stream):
    ready, _, _ = select.select([stream], [], [], 0.03)
    if ready:
        raise AssertionError("peer closed or replied before the partial frame was complete")


def check(port):
    split = frame(3)
    with connect(port) as stream:
        stream.sendall(split[:1])
        expect_waiting(stream)
        stream.sendall(split[1:41])
        expect_waiting(stream)
        stream.sendall(split[41:])
        expect(stream, [split], "fragmented length and payload")

        pipelined = [frame(sequence) for sequence in range(11, 19)]
        stream.sendall(b"".join(pipelined))
        expect(stream, pipelined, "ordered pipeline")
        stream.shutdown(socket.SHUT_WR)
        if stream.recv(1) != b"":
            raise AssertionError("peer returned trailing data after clean client shutdown")

    # Drop a session in the middle of a frame, then reconnect with distinct data.
    with connect(port) as abandoned:
        abandoned.sendall(frame(31)[:49])
        expect_waiting(abandoned)

    with connect(port) as reconnected:
        fresh = [frame(53), frame(59)]
        reconnected.sendall(b"".join(fresh))
        expect(reconnected, fresh, "fresh stream after abandoned partial frame")
        reconnected.shutdown(socket.SHUT_WR)
        if reconnected.recv(1) != b"":
            raise AssertionError("reconnected peer returned trailing data after shutdown")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("port", type=int, help="TCP server port on 127.0.0.1")
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    check(args.port)
    print("PASS split length/payload, 8 ordered echoes, clean close and reconnect after partial frame")


if __name__ == "__main__":
    main()
