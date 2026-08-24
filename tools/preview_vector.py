#!/usr/bin/env python3
"""Rasterise a single-path Android VectorDrawable to a PNG for eyeballing.

Hand-written Bezier path data is easy to get subtly wrong — a control point
with a transposed digit still parses, still builds, and only shows up as a
misshapen glyph in the status bar on a device. This renders the path with
nothing but the standard library so the shape can be checked before shipping.

    python tools/preview_vector.py app/src/main/res/drawable/atl_flame_mark.xml
"""
import argparse
import re
import struct
import sys
import zlib
from pathlib import Path

SCALE = 16          # samples per curve segment
SUPERSAMPLE = 4     # anti-aliasing factor


def parse_path(data: str):
    """Flatten M/C/Z path data into a list of polygons in viewport units."""
    tokens = re.findall(r"[MCZmcz]|-?\d*\.?\d+", data)
    polys, current = [], []
    cursor = (0.0, 0.0)
    i = 0
    while i < len(tokens):
        cmd = tokens[i]
        i += 1
        if cmd in "Zz":
            if current:
                polys.append(current)
                current = []
            continue
        if cmd in "Mm":
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            cursor = (x, y) if cmd == "M" else (cursor[0] + x, cursor[1] + y)
            current = [cursor]
            continue
        if cmd in "Cc":
            nums = [float(t) for t in tokens[i:i + 6]]
            i += 6
            if cmd == "c":
                nums = [n + cursor[k % 2] for k, n in enumerate(nums)]
            p0, p1, p2, p3 = cursor, tuple(nums[0:2]), tuple(nums[2:4]), tuple(nums[4:6])
            for s in range(1, SCALE + 1):
                t = s / SCALE
                u = 1 - t
                current.append((
                    u**3 * p0[0] + 3*u*u*t * p1[0] + 3*u*t*t * p2[0] + t**3 * p3[0],
                    u**3 * p0[1] + 3*u*u*t * p1[1] + 3*u*t*t * p2[1] + t**3 * p3[1],
                ))
            cursor = p3
            continue
        raise SystemExit(f"unsupported path command: {cmd!r}")
    if current:
        polys.append(current)
    return polys


def rasterise(polys, size: int, viewport: float):
    """Even-odd scanline fill at SUPERSAMPLE resolution, then box-downsample."""
    hi = size * SUPERSAMPLE
    k = hi / viewport
    coverage = [[0] * hi for _ in range(hi)]
    edges = [
        (a[0] * k, a[1] * k, b[0] * k, b[1] * k)
        for poly in polys
        for a, b in zip(poly, poly[1:] + poly[:1])
        if a[1] != b[1]
    ]
    for row in range(hi):
        y = row + 0.5
        xs = sorted(
            x0 + (y - y0) * (x1 - x0) / (y1 - y0)
            for x0, y0, x1, y1 in edges
            if min(y0, y1) <= y < max(y0, y1)
        )
        for left, right in zip(xs[::2], xs[1::2]):
            for col in range(max(0, int(left)), min(hi, int(right) + 1)):
                coverage[row][col] = 1

    out = []
    for row in range(size):
        line = []
        for col in range(size):
            hits = sum(
                coverage[row * SUPERSAMPLE + dy][col * SUPERSAMPLE + dx]
                for dy in range(SUPERSAMPLE) for dx in range(SUPERSAMPLE)
            )
            line.append(255 - int(255 * hits / (SUPERSAMPLE ** 2)))
        out.append(line)
    return out


def write_png(pixels, path: Path):
    size = len(pixels)
    raw = b"".join(b"\x00" + bytes(row) for row in pixels)

    def chunk(tag, payload):
        body = tag + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body))

    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 0, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("vector", type=Path)
    ap.add_argument("--size", type=int, default=192)
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args()

    xml = args.vector.read_text(encoding="utf-8")
    viewport = float(re.search(r'viewportWidth="([\d.]+)"', xml).group(1))
    data = re.search(r'android:pathData="([^"]+)"', xml, re.S).group(1)

    polys = parse_path(" ".join(data.split()))
    out = args.out or args.vector.with_suffix(".preview.png")
    write_png(rasterise(polys, args.size, viewport), out)
    print(f"{sum(len(p) for p in polys)} points in {len(polys)} contour(s) -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
