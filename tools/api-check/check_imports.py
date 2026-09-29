#!/usr/bin/env python3
"""Cross-checks every Minecraft/Fabric class Galaxy MC imports against 26.2 reference sources.

Without network access to Mojang or Fabric's maven the mod cannot be compiled here, so this is the
next best thing: a class is "seen" if the 26.2 branch of Fabric API or NeoForge imports it, patches it
(NeoForge patch files mirror vanilla paths) or declares it. Anything unseen is printed for review.

    python3 tools/api-check/check_imports.py [fabric-api-dir] [neoforge-dir]
"""
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
FABRIC = sys.argv[1] if len(sys.argv) > 1 else "/home/user/fabricmc/fabric-api"
NEO = sys.argv[2] if len(sys.argv) > 2 else "/home/user/neoforged/NeoForge"

IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.]+)\s*;", re.M)
DECL = re.compile(r"^package\s+([\w.]+);.*?(?:class|interface|record|enum)\s+(\w+)", re.S | re.M)


def known_classes():
    seen = set()
    for base in (FABRIC, NEO):
        for d, _, files in os.walk(base):
            for f in files:
                path = os.path.join(d, f)
                if f.endswith(".java"):
                    try:
                        text = open(path, encoding="utf-8", errors="ignore").read()
                    except OSError:
                        continue
                    seen.update(IMPORT.findall(text))
                    m = DECL.search(text)
                    if m:
                        seen.add(m.group(1) + "." + m.group(2))
                    # Fully qualified references inside code, e.g. net.minecraft.world.level.Level
                    seen.update(re.findall(r"\b(net\.(?:minecraft|fabricmc)\.[\w.]+\.[A-Z]\w*)", text))
                elif f.endswith(".java.patch"):
                    rel = os.path.relpath(path, os.path.join(NEO, "patches"))
                    seen.add(rel[:-len(".java.patch")].replace(os.sep, "."))
                    text = open(path, encoding="utf-8", errors="ignore").read()
                    seen.update(IMPORT.findall(text))
                    seen.update(re.findall(r"\b(net\.minecraft\.[\w.]+\.[A-Z]\w*)", text))
    return seen


def main():
    seen = known_classes()
    # Nested classes: accept an import if its outer class is known.
    def ok(name):
        if name in seen:
            return True
        parts = name.split(".")
        for i in range(len(parts) - 1, 0, -1):
            if parts[i][:1].isupper() and ".".join(parts[:i + 1]) in seen:
                return True
        return False

    unknown = {}
    for d, _, files in os.walk(os.path.join(ROOT, "src")):
        for f in files:
            if not f.endswith(".java"):
                continue
            path = os.path.join(d, f)
            for name in IMPORT.findall(open(path, encoding="utf-8").read()):
                if not (name.startswith("net.minecraft") or name.startswith("net.fabricmc") or name.startswith("com.mojang")):
                    continue
                if name.startswith("com.mojang") and not name.startswith("com.mojang.blaze3d"):
                    continue
                if not ok(name):
                    unknown.setdefault(name, []).append(os.path.relpath(path, ROOT))
    for name in sorted(unknown):
        print(f"UNSEEN {name}  <- {', '.join(sorted(set(unknown[name])))}")
    print(f"{len(unknown)} unseen imports")


if __name__ == "__main__":
    main()
