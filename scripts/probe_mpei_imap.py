#!/usr/bin/env python3
"""Unauthenticated mail.mpei.ru IMAP TLS compatibility probe.

The diagnostic never sends IMAP commands, account IDs, or passwords.
Testing legacy TLS is observational only: the mobile app must not silently
downgrade its connection security.
"""
import socket
import ssl
import time
import warnings

HOST="mail.mpei.ru"
PORT=993
try:
    addresses=socket.getaddrinfo(HOST, PORT, family=socket.AF_INET, type=socket.SOCK_STREAM)
except Exception as error:
    print("DNS error", type(error).__name__,str(error),flush=True)
    raise SystemExit(0)
if not addresses:
    print("No IPv4 results",flush=True)
    raise SystemExit(0)
addr=addresses[0][4]
print("DNS IPv4",addr[0],flush=True)
tests=[
    ("TLS1.2 only with SNI",ssl.TLSVersion.TLSv1_2,ssl.TLSVersion.TLSv1_2,True,False),
    ("TLS1.2 only without SNI",ssl.TLSVersion.TLSv1_2,ssl.TLSVersion.TLSv1_2,False,False),
    ("TLS1.3 only with SNI",ssl.TLSVersion.TLSv1_3,ssl.TLSVersion.TLSv1_3,True,False),
    ("legacy TLS1.0/1.1 with SNI",ssl.TLSVersion.TLSv1,ssl.TLSVersion.TLSv1_1,True,True),
]
for label,minimum,maximum,sni,legacy in tests:
    begin=time.monotonic()
    stage="TCP"
    try:
        with socket.socket(socket.AF_INET,socket.SOCK_STREAM) as sock:
            sock.settimeout(6)
            sock.connect(addr)
            stage="TLS"
            context=ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            context.check_hostname=False
            context.verify_mode=ssl.CERT_NONE
            with warnings.catch_warnings():
                warnings.simplefilter("ignore",DeprecationWarning)
                context.minimum_version=minimum
                context.maximum_version=maximum
            if legacy:
                context.set_ciphers("DEFAULT:@SECLEVEL=0")
            with context.wrap_socket(sock,server_hostname=HOST if sni else None) as secure:
                print(label,"SUCCESS TLS=",secure.version(),"cipher=",secure.cipher()[0],
                      "elapsed=",round(time.monotonic()-begin,2),"s",flush=True)
                stage="GREETING"
                secure.settimeout(6)
                greeting=secure.recv(160)
                print(label,"GREETING","OK" if greeting.startswith(b"* OK") else
                      ("EOF" if not greeting else "OTHER"),
                      "bytes=",len(greeting),flush=True)
    except Exception as error:
        print(label,"FAILED stage=",stage,"error=",type(error).__name__,
              "detail=",str(error)[:140],
              "elapsed=",round(time.monotonic()-begin,2),"s",flush=True)
