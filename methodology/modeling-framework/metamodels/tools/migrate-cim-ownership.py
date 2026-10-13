#!/usr/bin/env python3
"""Re-parent legacy root CIM glossary terms under BoundedContextCandidate.glossaryTerms."""

from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

GLOSSARY_LINE = re.compile(r"^\s*<glossary(\s[^>]*)/>\s*$", re.MULTILINE)
CONTEXT_ATTR = re.compile(r'\s*context="[^"]+"')


def strip_context_attr(attrs: str) -> str:
    return CONTEXT_ATTR.sub("", attrs)


def migrate_text(content: str) -> tuple[str, int, list[str]]:
    terms_by_context: dict[str, list[str]] = defaultdict(list)
    warnings: list[str] = []
    moved = 0

    for match in GLOSSARY_LINE.finditer(content):
        attrs = match.group(1)
        context_match = re.search(r'\bcontext="([^"]+)"', attrs)
        if not context_match:
            warnings.append(f"Glossary term without context attribute: {attrs[:80]}")
            continue
        context_id = context_match.group(1)
        terms_by_context[context_id].append(
            f"    <glossaryTerms{strip_context_attr(attrs)} />"
        )
        moved += 1

    if moved == 0:
        return content, moved, warnings

    content = GLOSSARY_LINE.sub("", content)
    for context_id, terms in terms_by_context.items():
        bounded_pattern = re.compile(
            rf'^(?P<indent>\s*)<boundedContexts\b(?P<body>[^>]*\bid="{re.escape(context_id)}"[^>]*)/>\s*$',
            re.MULTILINE,
        )

        def replace_bounded(match: re.Match[str]) -> str:
            indent = match.group("indent")
            body = match.group("body")
            children = "\n".join(terms)
            return f"{indent}<boundedContexts{body}>\n{children}\n{indent}</boundedContexts>"

        updated, count = bounded_pattern.subn(replace_bounded, content, count=1)
        if count == 0:
            warnings.append(f"No bounded context found for glossary terms: {context_id}")
            continue
        content = updated
    return content, moved, warnings


def migrate_file(path: Path, dry_run: bool) -> int:
    original = path.read_text(encoding="utf-8")
    migrated, moved, warnings = migrate_text(original)
    for warning in warnings:
        print(f"WARN {path}: {warning}", file=sys.stderr)
    if moved == 0:
        stripped, count = re.subn(
            r"(<glossaryTerms\b[^>]*)\s*context=\"[^\"]+\"",
            r"\1",
            original,
        )
        if count > 0 and not dry_run:
            path.write_text(stripped, encoding="utf-8", newline="\n")
            print(f"STRIPPED {path}: removed context attribute from {count} nested glossary term(s)")
            return count
        if count > 0:
            print(f"DRY-RUN {path}: would strip context attribute from {count} nested glossary term(s)")
            return count
        print(f"SKIP {path}: no legacy glossary terms")
        return 0
    if dry_run:
        print(f"DRY-RUN {path}: would move {moved} glossary term(s)")
        return moved
    path.write_text(migrated, encoding="utf-8", newline="\n")
    print(f"MIGRATED {path}: moved {moved} glossary term(s)")
    return moved


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", type=Path, help="CIM XMI files to migrate")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    total = 0
    for path in args.paths:
        if not path.exists():
            print(f"ERROR missing file: {path}", file=sys.stderr)
            return 1
        total += migrate_file(path, args.dry_run)
    print(f"Done. Migrated {total} glossary term(s) across {len(args.paths)} file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
