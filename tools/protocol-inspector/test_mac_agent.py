import socket
import struct
import time
import sys
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305

def read_frame(s):
    hdr = s.recv(4)
    if not hdr or len(hdr) < 4:
        return None, None
    length = struct.unpack(">I", hdr)[0]
    data = b""
    while len(data) < length:
        chunk = s.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    frame_type = data[0]
    payload = data[1:]
    return frame_type, payload

def encode_frame(frame_type, payload=b""):
    length = 1 + len(payload)
    return struct.pack(">IB", length, frame_type) + payload

class NonceCounter:
    def __init__(self):
        self.counter = 0
    def next_nonce(self):
        self.counter += 1
        return struct.pack(">Q", self.counter).rjust(12, b"\x00")

def main():
    host = "192.168.1.9"
    port = 7878
    print(f"[*] Connecting to Mac Agent at {host}:{port}...")
    s = socket.create_connection((host, port), timeout=5)

    # 1. Generate client X25519 keypair and send HELLO
    client_priv = X25519PrivateKey.generate()
    client_pub_bytes = client_priv.public_key().public_bytes_raw()
    s.sendall(encode_frame(0x01, client_pub_bytes))
    print(f"[+] Sent HELLO with client public key: {client_pub_bytes.hex()}")

    # 2. Receive HELLO_ACK from server
    ftype, ack_payload = read_frame(s)
    print(f"[+] Received frame type 0x{ftype:02X}, payload len={len(ack_payload) if ack_payload else 0}")
    assert ftype == 0x02, f"Expected HELLO_ACK (0x02), got 0x{ftype:02X}"
    server_pub_bytes = ack_payload
    print(f"[+] Server public key: {server_pub_bytes.hex()}")

    # 3. Derive symmetric key
    server_pub = X25519PublicKey.from_public_bytes(server_pub_bytes)
    shared_secret = client_priv.exchange(server_pub)
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=b"elysium-nexus-v1",
        info=b"elysium-channel"
    )
    channel_key = hkdf.derive(shared_secret)
    print(f"[+] Channel key derived: {channel_key.hex()}")

    cipher = ChaCha20Poly1305(channel_key)
    nonce_gen = NonceCounter()

    # 4. Prompt user for PIN shown on Mac screen
    if len(sys.argv) > 1:
        pin = sys.argv[1]
    else:
        pin = input("[?] Enter the 6-digit PIN shown on the Mac window: ").strip()

    print(f"[*] Sending 6 encrypted PIN digits for '{pin}'...")
    for d in pin:
        digit_byte = int(d).to_bytes(1, "big")
        nonce = nonce_gen.next_nonce()
        sealed = cipher.encrypt(nonce, digit_byte, None)
        # On-wire payload: nonce(12) + ciphertext + tag(16)
        encrypted_payload = nonce + sealed
        s.sendall(encode_frame(0x03, encrypted_payload))
        time.sleep(0.05)

    # 5. Read PAIR_OK response
    ftype, resp_payload = read_frame(s)
    print(f"[+] Received response frame type 0x{ftype:02X}")
    assert ftype == 0x04, f"Expected PAIR_OK (0x04), got 0x{ftype:02X}"
    nonce = resp_payload[:12]
    body_and_tag = resp_payload[12:]
    plain = cipher.decrypt(nonce, body_and_tag, None)
    if plain[0] == 1:
        print(" SUCCESS! Mac Agent accepted PIN and pairing is COMPLETE.")
    else:
        print(" FAILED! Mac Agent rejected PIN.")
        return

    # 6. Test Mouse Movement (move relative dx=50, dy=50)
    print("[*] Testing Mouse Move (dx=50, dy=50)...")
    dx, dy = 50.0, 50.0
    mouse_payload = struct.pack(">ff", dx, dy)
    nonce = nonce_gen.next_nonce()
    encrypted_move = nonce + cipher.encrypt(nonce, mouse_payload, None)
    s.sendall(encode_frame(0x05, encrypted_move))
    time.sleep(0.5)

    # 7. Test Mouse Click (Left down + Left up)
    print("[*] Testing Left Click...")
    down_payload = struct.pack(">BB", 0, 1) # left, down
    nonce = nonce_gen.next_nonce()
    s.sendall(encode_frame(0x06, nonce + cipher.encrypt(nonce, down_payload, None)))
    time.sleep(0.1)
    up_payload = struct.pack(">BB", 0, 0) # left, up
    nonce = nonce_gen.next_nonce()
    s.sendall(encode_frame(0x06, nonce + cipher.encrypt(nonce, up_payload, None)))
    time.sleep(0.5)

    # 8. Test Scroll (dx=0, dy=5)
    print("[*] Testing Scroll (dy=5)...")
    scroll_payload = struct.pack(">ff", 0.0, 5.0)
    nonce = nonce_gen.next_nonce()
    s.sendall(encode_frame(0x07, nonce + cipher.encrypt(nonce, scroll_payload, None)))
    time.sleep(0.5)

    # 9. Test Key Press (Space key)
    print("[*] Testing Space key press...")
    # action=0 (down), hidUsage=0x2C (space), mods=0
    key_payload = struct.pack(">BII", 0, 0x2C, 0)
    nonce = nonce_gen.next_nonce()
    s.sendall(encode_frame(0x08, nonce + cipher.encrypt(nonce, key_payload, None)))
    time.sleep(0.1)
    # action=1 (up)
    key_up_payload = struct.pack(">BII", 1, 0x2C, 0)
    nonce = nonce_gen.next_nonce()
    s.sendall(encode_frame(0x08, nonce + cipher.encrypt(nonce, key_up_payload, None)))

    print("\n All control tests (Mouse, Trackpad, Keyboard) SENT SUCCESSFULLY!")
    s.close()

if __name__ == "__main__":
    main()
