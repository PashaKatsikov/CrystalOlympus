#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
#  tools/rebrand.py — one-shot per-project renamer.
#
#  A companion to gray.properties: what the build derives from `gray.seed`
#  covers preference files, encoded arrays, sentinels, timings and cipher
#  parameters. This script covers what the build cannot: the *shape* of the
#  code — the package name, class names, folder layout, and their references
#  in the manifest, ProGuard rules, google-services.json and build script.
#
#  Two apps in the portfolio must not share the class name `WelcomePortal`,
#  the package `com.example.grayshell`, the drawable set name, or the folder
#  `startup/portal/reach/signal/vault/wire/blueprint`. This script rotates
#  every one of them in one pass, with a plan file for review before the
#  changes are written to disk.
#
#  Usage:
#      python tools/rebrand.py --config gray.properties       # plan only
#      python tools/rebrand.py --config gray.properties --apply
#      python tools/rebrand.py --config gray.properties --apply --package com.acme.reef --theme reef
#
#  Themes are just word-banks. Add your own to THEMES to broaden the vocabulary.
# ─────────────────────────────────────────────────────────────────────────────
import argparse
import hashlib
import io
import json
import os
import random
import re
import shutil
import sys
from pathlib import Path

# Force UTF-8 output so this works from a cp1251 Windows console.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
APP  = REPO / "app"
SRC  = APP / "src" / "main"
JAVA_ROOT = SRC / "java"


# ── Word banks per theme ────────────────────────────────────────────────────
#
#  Every theme must satisfy three properties, enforced by check_themes():
#
#    1. At least as many package words as there are template packages, so the
#       shuffle never falls back to the generic `pkg0`, `pkg1` … names. A
#       folder literally called `pkg0` in a shipped APK is a louder marker
#       than the themed name it replaced.
#    2. No word — package or class — shared with any other theme. The first
#       three themes below violated this (`Env`, `AttrHub`, `GameHost` were
#       common to all of them, so those classes were identical in every
#       project regardless of the theme drawn) and that is exactly the join
#       this file exists to prevent.
#    3. A word for every key in CURRENT["classes"], including the four that
#       used to be exempt — Trace, UrlGuard, UserAgent, Secrets. "Too
#       generic to bother renaming" was wrong: generic or not, an identical
#       name in two APKs is an identical name.
#
#  BURNED marks themes already spent on a shipped project. They stay here so
#  --from-theme can describe an existing tree, but the picker refuses them.
THEMES = {
    "reef": {
        "packages": ["core", "screens", "net", "attribution", "push", "prefs", "config", "connectivity"],
        "app":      "ReefApp",
        "router":   "TideRouter",
        "shell":    "WaveShell",
        "alert":    "TideAlert",
        "offline":  "NoCurrentScreen",
        "keyboard": "KeyboardTide",
        "reach":    "ConfigClient",
        "tracker":  "AttrHub",
        "push":     "FcmReef",
        "bus":      "PushBusReef",
        "vault":    "Prefs",
        "wire":     "NetMon",
        "blueprint":"Env",
        "loading":  "TideLoader",
        "fullscreen":"Immersive",
        "native":   "GameHost",
        "channel":  "ChannelReef",
        "result":   "ConfigResult",
        "trace":    "Trace",
        "guard":    "UrlGuard",
        "agent":    "UserAgent",
        "cipher":   "Secrets",
        "drawables_prefix": "reef",
    },
    "canyon": {
        "packages": ["boot", "surface", "relay", "attr", "notif", "store", "env", "netmon"],
        "app":      "CanyonApp",
        "router":   "GorgeRouter",
        "shell":    "CanyonShell",
        "alert":    "GorgeAlert",
        "offline":  "GorgeOffline",
        "keyboard": "CanyonPan",
        "reach":    "GorgeClient",
        "tracker":  "AttrCanyon",
        "push":     "FcmCanyon",
        "bus":      "PushBusCanyon",
        "vault":    "PrefStore",
        "wire":     "LinkMon",
        "blueprint":"Env",
        "loading":  "GorgeLoader",
        "fullscreen":"FullScreen",
        "native":   "GameHost",
        "channel":  "GorgeChannel",
        "result":   "GorgeResult",
        "trace":    "Trace",
        "guard":    "UrlGuard",
        "agent":    "UserAgent",
        "cipher":   "Secrets",
        "drawables_prefix": "cn",
    },
    "orbit": {
        "packages": ["boot", "view", "attr", "push", "prefs", "net", "cfg"],
        "app":      "OrbitApp",
        "router":   "LaunchGate",
        "shell":    "OrbitShell",
        "alert":    "OptInPrompt",
        "offline":  "SignalLostScreen",
        "keyboard": "KeyboardSlide",
        "reach":    "CfgClient",
        "tracker":  "AttrHub",
        "push":     "FcmReceiver",
        "bus":      "PushRelay",
        "vault":    "Store",
        "wire":     "Uplink",
        "blueprint":"Env",
        "loading":  "OrbitLoader",
        "fullscreen":"WindowGlue",
        "native":   "GameHost",
        "channel":  "GateChannel",
        "result":   "GateResult",
        "trace":    "Trace",
        "guard":    "UrlGuard",
        "agent":    "UserAgent",
        "cipher":   "Secrets",
        "drawables_prefix": "orb",
    },
    "atlas": {
        "packages": ["compass", "waypoint", "survey", "terrain", "legend",
                     "meridian", "sextant", "almanac", "bearing"],
        "app":      "AtlasApp",
        "router":   "Landfall",
        "shell":    "MeridianView",
        "alert":    "ConsentCard",
        "offline":  "LinkDownCard",
        "keyboard": "ViewGlider",
        "reach":    "SurveyClient",
        "tracker":  "OriginSurvey",
        "push":     "NoticeService",
        "bus":      "NoticeBridge",
        "vault":    "Cartouche",
        "wire":     "LinkSensor",
        "blueprint":"Bearings",
        "loading":  "AtlasProgress",
        "fullscreen":"ScreenFit",
        "native":   "PlayHost",
        "channel":  "RouteKind",
        "result":   "SurveyVerdict",
        "trace":    "Journal",
        "guard":    "HostRule",
        "agent":    "ClientTag",
        "cipher":   "Cloak",
        "drawables_prefix": "atl",
    },
    "foundry": {
        "packages": ["forge", "crucible", "anvil", "bellows", "ingot",
                     "temper", "quench", "alloy", "smelt"],
        "app":      "FoundryApp",
        "router":   "Kindling",
        "shell":    "AnvilView",
        "alert":    "PermitPlate",
        "offline":  "NoLinkPlate",
        "keyboard": "PaneShifter",
        "reach":    "IngotClient",
        "tracker":  "OriginTemper",
        "push":     "SparkService",
        "bus":      "SparkBridge",
        "vault":    "Strongbin",
        "wire":     "LinkGauge",
        "blueprint":"Castings",
        "loading":  "FoundryProgress",
        "fullscreen":"ScreenTrim",
        "native":   "PlayForge",
        "channel":  "PourKind",
        "result":   "IngotVerdict",
        "trace":    "Scribe",
        "guard":    "HostAssay",
        "agent":    "ClientStamp",
        "cipher":   "Quenchbox",
        "drawables_prefix": "fnd",
    },
    "thicket": {
        "packages": ["bramble", "glade", "burrow", "canopy", "fern",
                     "hollow", "moss", "tendril", "spore"],
        "app":      "ThicketApp",
        "router":   "Clearing",
        "shell":    "CanopyView",
        "alert":    "AssentLeaf",
        "offline":  "NoLinkLeaf",
        "keyboard": "PaneSway",
        "reach":    "GladeClient",
        "tracker":  "OriginFern",
        "push":     "ChirpService",
        "bus":      "ChirpBridge",
        "vault":    "Burrowbox",
        "wire":     "LinkRoot",
        "blueprint":"Groundwork",
        "loading":  "ThicketProgress",
        "fullscreen":"ScreenHush",
        "native":   "PlayGlade",
        "channel":  "TrailKind",
        "result":   "GladeVerdict",
        "trace":    "Twig",
        "guard":    "HostThorn",
        "agent":    "ClientMoss",
        "cipher":   "Husk",
        "drawables_prefix": "thk",
    },
    "pressroom": {
        "packages": ["galley", "platen", "folio", "quire", "serif",
                     "kerning", "stanza", "colophon", "imprint"],
        "app":      "PressApp",
        "router":   "Frontispiece",
        "shell":    "FolioView",
        "alert":    "ConsentSlip",
        "offline":  "NoLinkSlip",
        "keyboard": "PaneKern",
        "reach":    "QuireClient",
        "tracker":  "OriginGalley",
        "push":     "DispatchService",
        "bus":      "DispatchBridge",
        "vault":    "Archivebox",
        "wire":     "LinkRule",
        "blueprint":"Imposition",
        "loading":  "PressProgress",
        "fullscreen":"ScreenBleed",
        "native":   "PlayFolio",
        "channel":  "EditionKind",
        "result":   "QuireVerdict",
        "trace":    "Marginalia",
        "guard":    "HostImprint",
        "agent":    "ClientColophon",
        "cipher":   "Cipherplate",
        "drawables_prefix": "prs",
    },
}

# Themes already spent. `reef`, `canyon` and `orbit` additionally share
# `Env` / `GameHost` / `AttrHub` / `Trace` / `UrlGuard` / `UserAgent` /
# `Secrets` between them, so they are kept only to describe existing trees.
BURNED = {"reef", "canyon", "orbit"}

# What each *current* file/class is called in the template. Every rebrand is a
# mapping from these names into new ones under the chosen theme.
CURRENT = {
    "package_root": "com.example.grayshell",
    "packages": {
        "startup":  "startup",
        "portal":   "portal",
        "reach":    "reach",
        "signal":   "signal",
        "vault":    "vault",
        "wire":     "wire",
        "blueprint":"blueprint",
        "core":     "core",
        "root":     "",
    },
    "classes": {
        "AppEntry":            "app",
        "WelcomePortal":       "router",
        "StreamPortal":        "shell",
        "AlertPortal":         "alert",
        "OfflinePortal":       "offline",
        "KeyboardPan":         "keyboard",
        "ReachDispatch":       "reach",
        "TrackingDispatch":    "tracker",
        "PushRelay":           "push",
        "PushBus":             "bus",
        "DataVault":           "vault",
        "Secrets":             "cipher",
        "NetWire":             "wire",
        "AppBlueprint":        "blueprint",
        "ChannelResult":       "result",
        "LoadingView":         "loading",
        "Fullscreen":          "fullscreen",
        "NativeContentActivity": "native",
        "Trace":               "trace",
        "UrlGuard":            "guard",
        "UserAgent":           "agent",
    },
    "drawables_prefix": "gray_",
}

CLASS_KEYS = sorted({k for k in CURRENT["classes"].values() if k})


def theme_words(theme: str) -> set:
    """Every rotatable token a theme contributes, packages and classes alike."""
    words = THEMES[theme]
    tokens = set(words["packages"])
    tokens.update(words[key] for key in CLASS_KEYS)
    tokens.add(words["drawables_prefix"])
    return tokens


def check_themes() -> None:
    """Fail loudly on a theme that cannot do its job.

    A missing word would crash build_plan halfway through; a shared word
    would silently ship two projects with the same class name, which is the
    failure this script exists to prevent and the one nobody notices.
    """
    needed = len([k for k in CURRENT["packages"] if k != "root"])
    problems = []

    for name, words in THEMES.items():
        missing = [k for k in CLASS_KEYS if k not in words]
        if missing:
            problems.append(f"theme '{name}' has no word for: {', '.join(missing)}")
        # Burned themes are exempt: `orbit` really does come up two words
        # short, which is where the shipped `pkg0` folder came from. It has to
        # keep describing the tree it produced.
        if name not in BURNED and len(words["packages"]) < needed:
            problems.append(
                f"theme '{name}' offers {len(words['packages'])} package words "
                f"but {needed} are needed — the shuffle would fall back to pkg0/pkg1"
            )

    fresh = sorted(set(THEMES) - BURNED)
    for i, a in enumerate(fresh):
        for b in fresh[i + 1:] + sorted(BURNED):
            shared = theme_words(a) & theme_words(b)
            if shared:
                problems.append(
                    f"themes '{a}' and '{b}' share: {', '.join(sorted(shared))}"
                )

    if problems:
        raise SystemExit("theme bank is broken:\n  - " + "\n  - ".join(problems))


def slug(name: str) -> str:
    """PascalCase → snake_case for file names."""
    return re.sub(r"(?<!^)([A-Z])", r"_\1", name).lower()


def load_config(path: Path) -> dict:
    if not path.exists():
        raise SystemExit(f"config file not found: {path}")
    props = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    return props


def build_plan(seed: str, package: str, theme: str) -> dict:
    if theme not in THEMES:
        raise SystemExit(f"unknown theme '{theme}'. Available: {', '.join(THEMES)}")
    rng = random.Random(int(hashlib.sha256(seed.encode()).hexdigest()[:16], 16))
    words = THEMES[theme]

    # Deterministic package layout: pick the same words for the same seed.
    pkg_pool = list(words["packages"])
    rng.shuffle(pkg_pool)
    if len(pkg_pool) < len(CURRENT["packages"]):
        pkg_pool += [f"pkg{i}" for i in range(len(CURRENT["packages"]) - len(pkg_pool))]

    pkg_map = {}
    for i, (old, _) in enumerate(CURRENT["packages"].items()):
        if old == "root":
            pkg_map[old] = ""
            continue
        # Ensure "core" stays if used, else a themed name.
        pkg_map[old] = pkg_pool[i]

    # Classes: pick the word-bank name where provided.
    class_map = {}
    for old, key in CURRENT["classes"].items():
        class_map[old] = words[key] if key else old

    return {
        "seed": seed,
        "theme": theme,
        "package_root": {
            "old": CURRENT["package_root"],
            "new": package,
        },
        "packages": pkg_map,
        "classes": class_map,
        "drawables_prefix": {
            "old": CURRENT["drawables_prefix"],
            "new": words["drawables_prefix"] + "_",
        },
    }


def build_rotation(seed: str, package: str, from_theme: str, to_theme: str,
                   extra: dict) -> dict:
    """A plan that renames an *already rebranded* tree onto another theme.

    build_plan is deterministic in (seed, theme), so replaying it with the
    theme the project was originally branded with reproduces the names that
    are on disk today. Rotating is then just pairing the two plans up key by
    key. Doing it this way means the shuffled folder layout does not have to
    be recovered by guesswork — it falls out of the same RNG that produced it.
    """
    src = build_plan(seed, package, from_theme)
    dst = build_plan(seed, package, to_theme)

    packages = {}
    for key in CURRENT["packages"]:
        if key == "root":
            continue
        old, new = src["packages"][key], dst["packages"][key]
        if old != new:
            packages[old] = new

    classes = dict(extra)
    for template_name in CURRENT["classes"]:
        old, new = src["classes"][template_name], dst["classes"][template_name]
        if old != new:
            classes[old] = new

    # A rename map is only safe to apply in one pass if no target is also a
    # source: otherwise the second substitution eats the first one's output.
    collisions = set(classes.values()) & set(classes)
    if collisions:
        raise SystemExit(
            "rotation is not one-pass safe, these names are both a source and a "
            "target: " + ", ".join(sorted(collisions))
        )

    return {
        "seed": seed,
        "theme": f"{from_theme} → {to_theme}",
        "package_root": {"old": package, "new": package},
        "packages": packages,
        "classes": classes,
        "drawables_prefix": {
            "old": THEMES[from_theme]["drawables_prefix"] + "_",
            "new": THEMES[to_theme]["drawables_prefix"] + "_",
        },
    }


def audit_rotation(plan: dict) -> list:
    """Check the plan's sources actually exist before anything is moved.

    If the recorded seed or --from-theme is wrong, the reproduced names will
    not match the tree and the rotation would half-apply. Cheaper to find out
    here than after 40 files have moved.
    """
    root = JAVA_ROOT / plan["package_root"]["old"].replace(".", "/")
    warnings = []
    for folder in plan["packages"]:
        if not (root / folder).is_dir():
            warnings.append(f"package folder not on disk: {folder}/")
    on_disk = {p.stem for p in root.rglob("*.kt")}
    for name in plan["classes"]:
        if name not in on_disk:
            warnings.append(f"class not on disk: {name}")
    return warnings


def collect_targets() -> list:
    """Files rebrand.py rewrites — one list, easy to audit."""
    patterns = [
        "app/build.gradle.kts",
        "app/proguard-rules.pro",
        "app/src/main/AndroidManifest.xml",
        # All of values/, not just strings.xml: theme names embed class tokens
        # ("Theme.App.Fullscreen"), and renaming them in the manifest while
        # leaving themes.xml alone breaks the resource link.
        "app/src/main/res/values*/*.xml",
        "app/src/main/res/xml/*.xml",
        "app/src/main/res/drawable*/**/*.xml",
        "app/src/main/java/**/*.kt",
        "settings.gradle.kts",
        "gray.properties",
        "gray.properties.example",
    ]
    files = set()
    for p in patterns:
        for f in REPO.glob(p):
            if f.is_file():
                files.add(f)
    return sorted(files)


def rewrite_source(text: str, plan: dict) -> str:
    """Textual substitution — safe because every replaced token is unique."""
    for old, new in plan["classes"].items():
        if old == new:
            continue
        text = re.sub(rf"\b{re.escape(old)}\b", new, text)
    old_root = plan["package_root"]["old"]
    new_root = plan["package_root"]["new"]
    if old_root != new_root:
        text = text.replace(old_root, new_root)
    for old_pkg, new_pkg in plan["packages"].items():
        if old_pkg in ("root", ""):
            continue
        if old_pkg == new_pkg:
            continue
        # Only replace when preceded by the new root package + '.'
        text = re.sub(
            rf"{re.escape(new_root)}\.{re.escape(old_pkg)}\b",
            f"{new_root}.{new_pkg}",
            text,
        )
        # The manifest names components relative to the namespace
        # (android:name=".startup.WelcomePortal"), which the fully-qualified
        # pattern above never sees. Left alone, every gray component points at
        # a package that no longer exists and the app dies on launch with a
        # ClassNotFoundException.
        text = re.sub(
            rf'(android:name=")\.{re.escape(old_pkg)}\.',
            rf"\g<1>.{new_pkg}.",
            text,
        )
    old_dp = plan["drawables_prefix"]["old"]
    new_dp = plan["drawables_prefix"]["new"]
    if old_dp != new_dp:
        text = text.replace(old_dp, new_dp)
    return text


def move_files(plan: dict, apply: bool) -> list:
    """Physical folder + file renames. Returns the list of moves."""
    moves = []
    old_root = plan["package_root"]["old"].replace(".", "/")
    new_root = plan["package_root"]["new"].replace(".", "/")

    src_root = JAVA_ROOT / old_root
    dst_root = JAVA_ROOT / new_root

    if not src_root.exists():
        raise SystemExit(f"source root not found: {src_root}")

    for path in sorted(src_root.rglob("*.kt")):
        rel = path.relative_to(src_root)
        parts = list(rel.parts)
        # Rename the leaf package folder.
        if len(parts) >= 2:
            old_pkg = parts[0]
            new_pkg = plan["packages"].get(old_pkg, old_pkg)
            parts[0] = new_pkg
        # Rename the file if its class was renamed.
        stem = path.stem
        new_stem = plan["classes"].get(stem, stem)
        parts[-1] = new_stem + ".kt"
        dst = dst_root.joinpath(*parts)
        # A rotation runs with src_root == dst_root, so most of the tree (the
        # game packages) resolves to where it already is. Moving a file onto
        # itself is at best a no-op and on Windows an error.
        if dst != path:
            moves.append((path, dst))

    # Drawable resource files carrying the "gray_" prefix.
    old_dp = plan["drawables_prefix"]["old"]
    new_dp = plan["drawables_prefix"]["new"]
    if old_dp != new_dp:
        for path in sorted(SRC.glob("res/drawable*/**/*")):
            if path.is_file() and path.name.startswith(old_dp):
                new_name = new_dp + path.name[len(old_dp):]
                moves.append((path, path.with_name(new_name)))

    if apply:
        for src, dst in moves:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(src), str(dst))
        # Prune empty old-root directories.
        if src_root.exists():
            for dirpath, _, _ in os.walk(src_root, topdown=False):
                p = Path(dirpath)
                if not any(p.iterdir()):
                    p.rmdir()
    return moves


def apply_rewrites(plan: dict, apply: bool) -> list:
    edited = []
    for path in collect_targets():
        original = path.read_text(encoding="utf-8")
        updated  = rewrite_source(original, plan)
        if updated != original:
            edited.append(path)
            if apply:
                path.write_text(updated, encoding="utf-8")
    return edited


def main() -> int:
    ap = argparse.ArgumentParser(description="Rebrand the gray template.")
    ap.add_argument("--config", default="gray.properties",
                    help="path to gray.properties (default: repo root)")
    ap.add_argument("--package", help="new applicationId; overrides gray.bundleId")
    ap.add_argument("--theme", help=f"one of {list(THEMES)}", default=None)
    ap.add_argument("--from-theme", dest="from_theme", default=None,
                    help="theme this project was already branded with; turns "
                         "the run into a rotation instead of a fresh rebrand")
    ap.add_argument("--also", action="append", default=[], metavar="Old=New",
                    help="extra class rename, repeatable — for classes added "
                         "after the original rebrand")
    ap.add_argument("--allow-burned", action="store_true",
                    help="permit a theme listed in BURNED as the target")
    ap.add_argument("--apply", action="store_true",
                    help="actually write changes; without it, prints the plan only")
    args = ap.parse_args()

    check_themes()

    cfg_path = (REPO / args.config).resolve() if not Path(args.config).is_absolute() else Path(args.config)
    props = load_config(cfg_path)
    seed = props.get("gray.seed", "")
    if not seed or seed == "CHANGE-ME-EVERY-PROJECT":
        raise SystemExit("gray.seed missing or default — set it first (gradlew graySeed).")
    package = args.package or props.get("gray.bundleId", "")
    if not package:
        raise SystemExit("no package: pass --package or set gray.bundleId in gray.properties.")

    theme = args.theme
    if theme is None:
        rng = random.Random(int(hashlib.sha256(seed.encode()).hexdigest()[:16], 16))
        theme = rng.choice(sorted(set(THEMES) - BURNED))
    if theme in BURNED and not args.allow_burned:
        raise SystemExit(
            f"theme '{theme}' is already spent on a shipped project. Pick another "
            f"({', '.join(sorted(set(THEMES) - BURNED))}) or pass --allow-burned."
        )

    extra = {}
    for item in args.also:
        if "=" not in item:
            raise SystemExit(f"--also expects Old=New, got '{item}'")
        old, new = item.split("=", 1)
        extra[old.strip()] = new.strip()

    if args.from_theme:
        if args.from_theme not in THEMES:
            raise SystemExit(f"unknown --from-theme '{args.from_theme}'")
        plan = build_rotation(seed, package, args.from_theme, theme, extra)
    else:
        plan = build_plan(seed, package, theme)
        plan["classes"].update(extra)

    print("═══ REBRAND PLAN ═══")
    print(f"seed         : {seed[:10]}…  ({len(seed)} chars)")
    print(f"theme        : {plan['theme']}")
    print(f"package root : {plan['package_root']['old']}  →  {plan['package_root']['new']}")
    print(f"drawables    : {plan['drawables_prefix']['old']}  →  {plan['drawables_prefix']['new']}")
    print("packages     :")
    for old, new in plan["packages"].items():
        if old == "root": continue
        print(f"  {old:<10} → {new}")
    print("classes      :")
    for old, new in plan["classes"].items():
        if old == new: continue
        print(f"  {old:<24} → {new}")

    if args.from_theme:
        warnings = audit_rotation(plan)
        if warnings:
            print("\n⚠ the plan does not match the tree:")
            for w in warnings:
                print(f"  - {w}")
            print("  Check --from-theme and gray.seed before applying.")

    moves = move_files(plan, apply=False)
    edits = apply_rewrites(plan, apply=False)
    print(f"\nfile renames : {len(moves)}")
    print(f"text edits   : {len(edits)}")

    if not args.apply:
        print("\n(plan only — pass --apply to write the changes)")
        return 0

    move_files(plan, apply=True)
    apply_rewrites(plan, apply=True)
    print("\n✓ rebrand applied. Verify with `gradlew clean assembleDebug`.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
