#!/usr/bin/env python3
"""Report comment lines this project shares verbatim with its siblings.

An identical comment is a stronger join than identical code: R8 renames
classes but never touches a comment, and nobody writes the same sentence
twice by accident. Run it after editing any of the launch-flow files.

    python tools/comment_overlap.py
    python tools/comment_overlap.py --min 25 --show 40
"""
import argparse
import io
import re
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
PORTFOLIO = REPO.parent
COMMENT = re.compile(r"^\s*(//|\*|/\*)")


def comments(root: Path, min_len: int) -> dict:
    """Normalised comment line → where it was found. Case and punctuation are
    dropped so reformatting or renaming a class cannot hide a match."""
    found = {}
    for path in root.rglob("*.kt"):
        for n, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if not COMMENT.match(line):
                continue
            key = re.sub(r"[^a-z0-9]", "", line.lower())
            if len(key) >= min_len:
                found.setdefault(key, (path, n, line.strip()))
    return found


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--min", type=int, default=25,
                    help="shortest normalised line worth comparing")
    ap.add_argument("--show", type=int, default=15,
                    help="how many matches to print per sibling")
    args = ap.parse_args()

    mine = comments(REPO / "app" / "src" / "main" / "java", args.min)
    print(f"{REPO.name}: {len(mine)} distinct comment lines\n")

    total = 0
    for sibling in sorted(PORTFOLIO.iterdir()):
        if sibling == REPO or not sibling.is_dir():
            continue
        src = sibling / "app" / "src" / "main" / "java"
        if not src.is_dir():
            continue
        shared = sorted(set(mine) & set(comments(src, args.min)))
        total += len(shared)
        print(f"{sibling.name:<26} shared: {len(shared)}")
        for key in shared[:args.show]:
            path, n, text = mine[key]
            print(f"    {path.relative_to(REPO)}:{n}  {text[:96]}")
        if len(shared) > args.show:
            print(f"    … {len(shared) - args.show} more")

    print(f"\ntotal shared lines: {total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
