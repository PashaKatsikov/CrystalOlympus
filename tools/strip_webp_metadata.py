#!/usr/bin/env python3
"""Remove ICCP / EXIF / XMP chunks from the WebP files this app ships.

Every image in this project carried the same 456-byte sRGB ICC profile, and so
did the images in the sibling projects, because they all came out of the same
conversion step. An identical byte run at the head of forty files is a join
that survives recompression, renaming and re-cropping, and none of those bytes
do anything: the decoder treats untagged RGB as sRGB regardless.

Only metadata chunks are touched. The image chunks (VP8, VP8L, ALPH, ANMF and
friends) are copied across byte for byte and the tool verifies that afterwards,
so the decoded pixels cannot change.

    python tools/strip_webp_metadata.py --dry-run
    python tools/strip_webp_metadata.py
"""
import argparse
import struct
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SEARCH = [
    REPO / "app" / "src" / "main" / "assets",
    REPO / "app" / "src" / "main" / "res",
]

DROP = {b"ICCP", b"EXIF", b"XMP "}

# VP8X flag bits, MSB first: Rsv Rsv I L E X A R
VP8X_FLAGS = {b"ICCP": 0x20, b"EXIF": 0x08, b"XMP ": 0x04}


def read_chunks(blob: bytes):
    if blob[:4] != b"RIFF" or blob[8:12] != b"WEBP":
        raise ValueError("not a RIFF/WEBP file")
    chunks = []
    pos = 12
    end = min(len(blob), 8 + struct.unpack("<I", blob[4:8])[0])
    while pos + 8 <= end:
        tag = blob[pos:pos + 4]
        size = struct.unpack("<I", blob[pos + 4:pos + 8])[0]
        payload = blob[pos + 8:pos + 8 + size]
        if len(payload) != size:
            raise ValueError(f"chunk {tag!r} is truncated")
        chunks.append((tag, payload))
        pos += 8 + size + (size & 1)
    return chunks


def rebuild(chunks) -> bytes:
    body = bytearray(b"WEBP")
    for tag, payload in chunks:
        body += tag + struct.pack("<I", len(payload)) + payload
        if len(payload) & 1:
            body += b"\x00"
    return b"RIFF" + struct.pack("<I", len(body)) + bytes(body)


def strip(blob: bytes):
    """Returns (new_bytes, removed_tags). None when there was nothing to do."""
    chunks = read_chunks(blob)
    removed = [tag for tag, _ in chunks if tag in DROP]
    if not removed:
        return None, []

    kept = []
    for tag, payload in chunks:
        if tag in DROP:
            continue
        if tag == b"VP8X":
            # The flags byte still advertises chunks that are now gone; a
            # decoder that trusts it and goes looking will reject the file.
            mask = 0
            for gone in removed:
                mask |= VP8X_FLAGS.get(gone, 0)
            payload = bytes([payload[0] & ~mask]) + payload[1:]
        kept.append((tag, payload))

    rebuilt = rebuild(kept)

    before = {t: p for t, p in chunks if t not in DROP and t != b"VP8X"}
    after = {t: p for t, p in read_chunks(rebuilt) if t != b"VP8X"}
    if before != after:
        raise ValueError("image chunks changed — refusing to write")

    return rebuilt, removed


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    touched = saved = 0
    for root in SEARCH:
        for path in sorted(root.rglob("*.webp")):
            blob = path.read_bytes()
            try:
                rebuilt, removed = strip(blob)
            except ValueError as e:
                print(f"  !! {path.relative_to(REPO)}: {e}")
                continue
            if rebuilt is None:
                continue
            touched += 1
            saved += len(blob) - len(rebuilt)
            tags = ", ".join(t.decode().strip() for t in removed)
            print(f"  {path.relative_to(REPO)}  -{len(blob) - len(rebuilt)}B  ({tags})")
            if not args.dry_run:
                path.write_bytes(rebuilt)

    verb = "would strip" if args.dry_run else "stripped"
    print(f"\n{verb} metadata from {touched} file(s), {saved} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
