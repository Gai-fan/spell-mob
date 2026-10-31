"""Minimal RCON client used by the dev smoke tests."""
import socket
import struct
import sys


class Rcon:
    def __init__(self, host="127.0.0.1", port=25575, password="spellmob", timeout=10.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self._send(3, password)
        rid, _ = self._recv()
        if rid == -1:
            raise RuntimeError("rcon auth failed")

    def _send(self, typ, body):
        data = struct.pack("<ii", 0, typ) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _recv(self):
        raw = self._read(4)
        (length,) = struct.unpack("<i", raw)
        payload = self._read(length)
        rid, _typ = struct.unpack("<ii", payload[:8])
        return rid, payload[8:-2].decode("utf-8", "replace")

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RuntimeError("rcon closed")
            buf += chunk
        return buf

    def command(self, cmd):
        self._send(2, cmd)
        return self._recv()[1]


if __name__ == "__main__":
    rcon = Rcon()
    for cmd in sys.argv[1:]:
        print(f"> {cmd}\n{rcon.command(cmd)}")
