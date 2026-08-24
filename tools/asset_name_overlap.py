#!/usr/bin/env python3
"""Report asset file names this project shares with its siblings.

A file name that appears in two APKs is a join whether or not the pixels match:
the resource table and the asset directory are both plain text inside the
package, so `Horizontal_Loading_Screen.webp` in forty apps reads as forty apps
built from one template. This walks the sibling checkouts next to this repo and
prints the overlap so the shared names can be renamed.

    python tools/asset_name_overlap.py
"""
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SUBDIRS = ("app/src/main/assets", "app/src/main/res")


def names_in(project: Path) -> dict:
    found = {}
    for sub in SUBDIRS:
        root = project / sub
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if path.is_file():
                found.setdefault(path.name, []).append(str(path.relative_to(project)))
    return found


def main() -> int:
    mine = names_in(REPO)
    siblings = {}
    for project in sorted(REPO.parent.iterdir()):
        if not project.is_dir() or project.resolve() == REPO:
            continue
        for name in names_in(project):
            if name in mine:
                siblings.setdefault(name, set()).add(project.name)

    if not siblings:
        print(f"{len(mine)} asset names, none shared with siblings")
        return 0

    print(f"{len(siblings)} of {len(mine)} asset names are shared:\n")
    for name, projects in sorted(siblings.items(), key=lambda kv: (-len(kv[1]), kv[0])):
        where = ", ".join(sorted(projects))
        print(f"  {len(projects):2d}x  {name}")
        print(f"        {where}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
