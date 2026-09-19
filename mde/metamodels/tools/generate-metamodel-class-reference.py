#!/usr/bin/env python3
"""Parse Emfatic metamodels and emit a class reference markdown document."""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = (
    Path(__file__).resolve().parents[3]
    / "docs"
    / "internal"
    / "mde"
    / "reference"
    / "metamodel-class-reference.md"
)

CLASS_RE = re.compile(
    r"^(abstract\s+)?class\s+(\w+)(?:\s+extends\s+(.+?))?\s*\{?\s*$"
)
INTERFACE_RE = re.compile(r"^interface\s+(\w+)")
FEATURE_RE = re.compile(
    r"^(?:readonly\s+transient\s+)?(?:volatile\s+)?(?:derived\s+)?"
    r"(val|ref)\s+([\w.]+)(\[([?*+]|\d+)\])?(?:#(\w+))?\s+(\w+)"
)
ATTR_RE = re.compile(r"^attr\s+")


@dataclass
class Feature:
    kind: str
    type_name: str
    name: str


@dataclass
class ClassInfo:
    name: str
    module: str
    level: str
    package: str = ""
    supertypes: list[str] = field(default_factory=list)
    is_abstract: bool = False
    is_interface: bool = False
    features: list[Feature] = field(default_factory=list)


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
    package = ""
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.startswith("package "):
            package = line.replace("package ", "").replace(";", "").strip()
            continue
        if (
            not line
            or line.startswith("//")
            or line.startswith("@")
            or line.startswith("import ")
        ):
            continue
        if line.startswith("enum "):
            current = None
            continue
        match = CLASS_RE.match(line)
        if match:
            supertypes: list[str] = []
            if match.group(3):
                supertypes = [item.strip() for item in match.group(3).split(",")]
            current = ClassInfo(
                name=match.group(2),
                module=path.stem,
                level=level_for(path),
                package=package,
                supertypes=supertypes,
                is_abstract=bool(match.group(1)),
            )
            classes.append(current)
            continue
        match = INTERFACE_RE.match(line)
        if match:
            current = ClassInfo(
                name=match.group(1),
                module=path.stem,
                level=level_for(path),
                package=package,
                is_interface=True,
            )
            classes.append(current)
            continue
        if current is None or ATTR_RE.match(line):
            continue
        match = FEATURE_RE.match(line)
        if match:
            current.features.append(
                Feature(
                    kind=match.group(1),
                    type_name=match.group(2),
                    name=match.group(6),
                )
            )
    return classes


def collect() -> list[ClassInfo]:
    result: list[ClassInfo] = []
    for path in sorted(ROOT.rglob("*.emf")):
        result.extend(parse_emf(path))
    return result


def format_list(items: list[str]) -> str:
    if not items:
        return "_none_"
    return ", ".join(f"`{item}`" for item in sorted(items))


def format_supertypes(supertypes: list[str]) -> str:
    if not supertypes:
        return "_none_"
    return ", ".join(f"`{item}`" for item in supertypes)


def build_markdown(classes: list[ClassInfo]) -> str:
    incoming_ref: dict[str, list[str]] = defaultdict(list)
    incoming_val: dict[str, list[str]] = defaultdict(list)
    for cls in classes:
        for feat in cls.features:
            target = short_type(feat.type_name)
            owner = f"{cls.name}.{feat.name}"
            if feat.kind == "ref":
                incoming_ref[target].append(owner)
            else:
                incoming_val[target].append(owner)

    level_titles = {
        "cim": "CIM (Computation-Independent Model)",
        "pim": "PIM (Platform-Independent Model)",
        "psm": "PSM (Platform-Specific Model, AWS)",
    }

    lines: list[str] = [
        "# Metamodel Class Reference",
        "",
        "Auto-generated reference for every class in the CIM, PIM, and PSM metamodels.",
        "For each class this document lists whether it is abstract, which other concepts",
        "reference it (`ref`), which concepts contain it (`val`), and its direct supertypes.",
        "",
        "> Source: `mde/metamodels/**/*.emf`",
        "",
    ]

    for level in ("cim", "pim", "psm"):
        level_classes = [
            cls
            for cls in classes
            if cls.level == level and not cls.is_interface
        ]
        modules = sorted({cls.module for cls in level_classes})

        lines.append(f"## {level_titles[level]}")
        lines.append("")
        lines.append(
            f"**Modules:** {', '.join(f'`{module}.emf`' for module in modules)}  "
        )
        lines.append(f"**Classes:** {len(level_classes)}")
        lines.append("")

        for module in modules:
            module_classes = sorted(
                [cls for cls in level_classes if cls.module == module],
                key=lambda item: item.name,
            )
            if not module_classes:
                continue
            lines.append(f"### `{module}.emf`")
            lines.append("")
            for cls in module_classes:
                abstract = "Yes (abstract)" if cls.is_abstract else "No (concrete)"
                lines.append(f"#### `{cls.name}`")
                lines.append("")
                lines.append(f"- **Abstract:** {abstract}")
                lines.append(
                    f"- **Incoming `ref`:** {format_list(incoming_ref.get(cls.name, []))}"
                )
                lines.append(
                    f"- **Incoming `val`:** {format_list(incoming_val.get(cls.name, []))}"
                )
                lines.append(
                    f"- **Inherits:** {format_supertypes(cls.supertypes)}"
                )
                lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    classes = collect()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(build_markdown(classes), encoding="utf-8")
    level_counts = {
        level: sum(1 for cls in classes if cls.level == level and not cls.is_interface)
        for level in ("cim", "pim", "psm")
    }
    print(f"Wrote {OUTPUT}")
    print(
        "Class counts, "
        + ", ".join(f"{level}: {count}" for level, count in level_counts.items())
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
