#!/usr/bin/env python3
"""Generates the sample app's PDFs: small, self-made, and free of third-party content.

    python3 sample/tools/make_sample_pdfs.py

Writes sample.pdf (12 pages, mixed orientation, numbered) and password.pdf (encrypted with
the standard RC4 40-bit security handler, user password "sample") into the sample's
compose resources.
"""
import hashlib
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "composeApp", "src", "commonMain", "composeResources", "files")
PAD = bytes.fromhex("28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A")
COLORS = ["0.20 0.45 0.85", "0.85 0.35 0.25", "0.25 0.65 0.40", "0.60 0.35 0.75"]


def rc4(key, data):
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) % 256
        s[i], s[j] = s[j], s[i]
    i = j = 0
    out = bytearray()
    for byte in data:
        i = (i + 1) % 256
        j = (j + s[i]) % 256
        s[i], s[j] = s[j], s[i]
        out.append(byte ^ s[(s[i] + s[j]) % 256])
    return bytes(out)


def page_content(number, count, width, height):
    color = COLORS[number % len(COLORS)]
    return (
        f"{color} rg 0 {height - 90} {width} 90 re f\n"
        f"0.95 g 36 36 {width - 72} {height - 162} re f\n"
        f"BT /F1 28 Tf 1 g 36 {height - 58} Td (Compose PDF Viewer) Tj ET\n"
        f"BT /F1 64 Tf 0.2 g 72 {height / 2} Td (Page {number} of {count}) Tj ET\n"
        f"BT /F1 14 Tf 0.4 g 72 {height / 2 - 40} Td ({width:.0f} x {height:.0f} pt) Tj ET\n"
        f"0 g 36 36 40 40 re f\n"
    ).encode()


def build(pages, password=None):
    objects = {}  # number -> bytes (body without "n 0 obj")
    file_id = hashlib.md5(b"compose-pdf-viewer-sample").digest()
    key = None
    encrypt_dict = b""
    if password is not None:
        user = (password.encode() + PAD)[:32]
        owner_key = hashlib.md5(user).digest()[:5]
        o_value = rc4(owner_key, user)
        p_value = -4  # all permissions bits set except reserved ones
        key = hashlib.md5(user + o_value + p_value.to_bytes(4, "little", signed=True) + file_id).digest()[:5]
        u_value = rc4(key, PAD)
        encrypt_dict = (
            b"<< /Filter /Standard /V 1 /R 2 /O <" + o_value.hex().encode() + b"> /U <" + u_value.hex().encode()
            + b"> /P " + str(p_value).encode() + b" >>"
        )

    def stream(number, data):
        if key is not None:
            object_key = hashlib.md5(key + number.to_bytes(3, "little") + b"\x00\x00").digest()[:10]
            data = rc4(object_key, data)
        return b"<< /Length " + str(len(data)).encode() + b" >>\nstream\n" + data + b"\nendstream"

    count = len(pages)
    font = 3
    kids = []
    next_number = 4
    for index, (width, height) in enumerate(pages):
        page, content = next_number, next_number + 1
        next_number += 2
        kids.append(page)
        objects[content] = stream(content, page_content(index + 1, count, width, height))
        objects[page] = (
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {width} {height}] "
            f"/Resources << /Font << /F1 {font} 0 R >> >> /Contents {content} 0 R >>"
        ).encode()
    objects[1] = b"<< /Type /Catalog /Pages 2 0 R >>"
    objects[2] = (f"<< /Type /Pages /Count {count} /Kids [" + " ".join(f"{k} 0 R" for k in kids) + "] >>").encode()
    objects[font] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    if key is not None:
        objects[next_number] = encrypt_dict
        encrypt_number = next_number
        next_number += 1

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for number in sorted(objects):
        offsets[number] = len(out)
        out += f"{number} 0 obj\n".encode() + objects[number] + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {next_number}\n0000000000 65535 f \n".encode()
    for number in range(1, next_number):
        out += f"{offsets[number]:010d} 00000 n \n".encode()
    trailer = f"<< /Size {next_number} /Root 1 0 R /ID [<{file_id.hex()}> <{file_id.hex()}>]"
    if key is not None:
        trailer += f" /Encrypt {encrypt_number} 0 R"
    out += f"trailer\n{trailer} >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    portrait, landscape = (612, 792), (792, 612)
    sample = [landscape if n in (4, 9) else portrait for n in range(12)]
    with open(os.path.join(OUT, "sample.pdf"), "wb") as f:
        f.write(build(sample))
    with open(os.path.join(OUT, "password.pdf"), "wb") as f:
        f.write(build([portrait], password="sample"))
    print("Wrote", os.path.normpath(OUT))
