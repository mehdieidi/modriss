#!/usr/bin/env python3
"""Parse Emfatic modules and emit ownership-audit.csv with containment diagnostics."""

from __future__ import annotations

import csv
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "ownership-audit.csv"

CLASS_RE = re.compile(r"^(?:abstract\s+)?class\s+(\w+)")
INTERFACE_RE = re.compile(r"^interface\s+(\w+)")
FEATURE_RE = re.compile(
    r"^(?:readonly\s+transient\s+)?(?:volatile\s+)?(?:derived\s+)?"
    r"(val|ref)\s+([\w.]+)(\[([?*+])\])?(?:#(\w+))?\s+(\w+)"
)
ATTR_RE = re.compile(r"^attr\s+")


@dataclass
class Feature:
    kind: str  # val or ref
    type_name: str
    multiplicity: str
    opposite: str | None
    name: str
    readonly_transient: bool
    derived: bool


@dataclass
class ClassInfo:
    name: str
    module: str
    level: str
    features: list[Feature] = field(default_factory=list)
    is_abstract: bool = False
    is_interface: bool = False


def level_for(path: Path) -> str:
    parts = path.parts
    if "cim" in parts:
        return "cim"
    if "pim" in parts:
        return "pim"
    if "psm" in parts:
        return "psm"
    if "shared" in parts:
        return "kernel"
    return "unknown"


def short_type(type_name: str) -> str:
    return type_name.split(".")[-1]


def parse_emf(path: Path) -> list[ClassInfo]:
    classes: list[ClassInfo] = []
    current: ClassInfo | None = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("//") or line.startswith("@") or line.startswith("package ") or line.startswith("import "):
            continue
        if line.startswith("enum "):
            current = None
            continue
        m = CLASS_RE.match(line)
        if m:
            current = ClassInfo(
                name=m.group(1),
                module=path.name,
                level=level_for(path),
                is_abstract=line.startswith("abstract "),
            )
            classes.append(current)
            continue
        m = INTERFACE_RE.match(line)
        if m:
            current = ClassInfo(
                name=m.group(1),
                module=path.name,
                level=level_for(path),
                is_interface=True,
            )
            classes.append(current)
            continue
        if current is None or ATTR_RE.match(line):
            continue
        m = FEATURE_RE.match(line)
        if m:
            readonly = line.startswith("readonly transient")
            derived = "derived" in line.split("ref")[0] or "derived" in line.split("val")[0]
            current.features.append(
                Feature(
                    kind=m.group(1),
                    type_name=m.group(2),
                    multiplicity=m.group(4) or "1",
                    opposite=m.group(5),
                    name=m.group(6),
                    readonly_transient=readonly,
                    derived=derived,
                )
            )
    return classes


def collect() -> list[ClassInfo]:
    result: list[ClassInfo] = []
    for path in sorted(ROOT.rglob("*.emf")):
        result.extend(parse_emf(path))
    return result


def incoming_val_owners(classes: list[ClassInfo]) -> dict[str, list[tuple[str, str]]]:
    owners: dict[str, list[tuple[str, str]]] = {}
    for cls in classes:
        if cls.is_interface:
            continue
        for feat in cls.features:
            if feat.kind != "val" or feat.derived:
                continue
            target = short_type(feat.type_name)
            owners.setdefault(target, []).append((cls.name, feat.name))
    return owners


def build_rows(classes: list[ClassInfo]) -> list[dict[str, str]]:
    owners = incoming_val_owners(classes)
    rows: list[dict[str, str]] = []
    for cls in sorted(classes, key=lambda c: (c.level, c.name)):
        if cls.is_interface:
            continue
        incoming = owners.get(cls.name, [])
        current_container = "; ".join(f"{o[0]}.{o[1]}" for o in incoming) if incoming else "(none)"
        proposed = current_container
        flags: list[str] = []
        if not incoming and cls.name not in {
            "PIMModel",
            "CIMModel",
            "AwsPsmModel",
            "SamGlobals",
            "ImplementationProfile",
            "TransformationProfile",
        }:
            flags.append("unreachable")
        if len(incoming) > 1:
            flags.append("multiple-containment")
        for feat in cls.features:
            verdict = feat.kind
            rationale = ""
            if feat.kind == "val" and not feat.derived:
                rationale = "containment owner"
            elif feat.readonly_transient and feat.opposite:
                rationale = "back-pointer"
            elif feat.kind == "ref" and feat.name.startswith("owns"):
                flags.append("owns-ref")
                rationale = "duplicate ownership: remove"
            elif feat.kind == "ref":
                rationale = "cross-link"
            rows.append(
                {
                    "level": cls.level,
                    "module": cls.module,
                    "eclass": cls.name,
                    "abstract": str(cls.is_abstract),
                    "current_container": current_container,
                    "proposed_container": proposed,
                    "feature": feat.name,
                    "feature_kind": verdict,
                    "feature_type": short_type(feat.type_name),
                    "opposite": feat.opposite or "",
                    "verdict": "flag: " + ",".join(flags) if flags and feat.name == cls.features[0].name else verdict,
                    "rationale": rationale,
                    "flags": ",".join(flags) if flags else "",
                }
            )
        if not cls.features:
            rows.append(
                {
                    "level": cls.level,
                    "module": cls.module,
                    "eclass": cls.name,
                    "abstract": str(cls.is_abstract),
                    "current_container": current_container,
                    "proposed_container": proposed,
                    "feature": "",
                    "feature_kind": "",
                    "feature_type": "",
                    "opposite": "",
                    "verdict": "flag: " + ",".join(flags) if flags else "ok",
                    "rationale": "leaf classifier",
                    "flags": ",".join(flags) if flags else "",
                }
            )
    return rows


def main() -> int:
    classes = collect()
    rows = build_rows(classes)
    fieldnames = [
        "level",
        "module",
        "eclass",
        "abstract",
        "current_container",
        "proposed_container",
        "feature",
        "feature_kind",
        "feature_type",
        "opposite",
        "verdict",
        "rationale",
        "flags",
    ]
    with OUTPUT.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    flagged = sum(1 for r in rows if r["flags"])
    print(f"Wrote {len(rows)} rows for {len(classes)} classifiers to {OUTPUT}")
    print(f"Rows with class-level flags: {flagged}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
