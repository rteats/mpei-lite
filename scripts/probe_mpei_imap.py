#!/usr/bin/env python3
"""One-shot, unauthenticated IMAP TCP/TLS/greeting probe.

No USER, LOGIN, AUTHENTICATE, IMAP commands or credentials are sent. This
workflow tests basic reachability from an independent runner network, not
account authentication or Android device state.
"""
import ipaddress
import socket
import ssl
import time

HOST="mail.mpei.ru"
PORT=993

try:
    addresses=socket.getaddrinfo(HOST, PORT, type=socket.SOCK_STREAM)
except Exception as error:
    print("DNS failure:", type(error).__name__, str(error))
    raise SystemExit(1)
seen=set()
attempts=0
for family, stype, proto, _, addr in addresses:
    if addr[0] in seen:
        continue
    seen.add(addr[0])
    if attempts >= 3:
        break
    attempts += 1
    label="IPv6" if family==socket.AF_INET6 else "IPv4"
    print("Candidate", label, addr[0], flush=True)
    try:
        started=time.monotonic()
        with socket.socket(family, stype, proto) as sock:
            sock.settimeout(10)
            sock.connect(addr)
            print(label, "TCP-connected in", round(time.monotonic()-started,2), "s", flush=True)
            # The university can use its own CA. This probe is deliberately
            # unauthenticated and never sends any credentials, so inspect
            # TLS/IMAP availability even if system trust does not recognize it.
            context=ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            context.check_hostname=False
            context.verify_mode=ssl.CERT_NONE
            context.minimum_version=ssl.TLSVersion.TLSv1_2
            with context.wrap_socket(sock, server_hostname=HOST) as secure:
                secure.settimeout(10)
                print(label, "TLS negotiated", secure.version(), secure.cipher()[0],
                      "after", round(time.monotonic()-started,2), "s", flush=True)
                # Never send IMAP commands or authentication data.
                greeting=secure.recv(256)
                # Sanitize greeting; server could send arbitrary text.
                sample=''.join(chr(c) if 32<=c<=126 else '?' for c in greeting[:160])
                print(label,"greeting status=", "OK" if greeting.startswith(b'* OK') else
                      ("EOF" if not greeting else "OTHER"),
                      "bytes=",len(greeting), "sample=",repr(sample),flush=True)
    except Exception as error:
        print(label, "failure:", type(error).__name__, str(error)[:180], flush=True)

# Never fail workflow just because university server is unreachable.
