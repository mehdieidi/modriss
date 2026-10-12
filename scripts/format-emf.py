#!/usr/bin/env python3
"""Format Emfatic (.emf) sources with deterministic layout and aligned feature columns."""

from __future__ import annotations

import argparse
import difflib
import os
import re
import stat
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXCLUDED = {".git", ".idea", ".vscode", "node_modules", "target", "vendor", ".venv"}
LEXER = re.compile(
    r"(?P<space>\s+)|(?P<line_comment>//[^\r\n]*)|(?P<block_comment>/\*[\s\S]*?\*/)"
    r"|(?P<string>\"(?:\\[\s\S]|[^\"\\])*\"|'(?:\\[\s\S]|[^'\\])*')"
    r"|(?P<word>[^\W\d]\w*)"
    r"|(?P<number>0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)"
    r"|(?P<symbol>\.\.|->|[^\s])"
)
MODIFIERS = {
    "readonly", "volatile", "transient", "unsettable", "derived", "unique",
    "ordered", "resolve", "id",
}
FEATURE_KINDS = {"attr", "val", "ref", "op"}


class FormatError(ValueError):
    """Input cannot safely be formatted."""


@dataclass(frozen=True)
class Token:
    kind: str
    text: str
    line: int
    end_line: int

    @property
    def comment(self) -> bool:
        return self.kind in {"line_comment", "block_comment"}


def tokenize(source: str) -> list[Token]:
    tokens: list[Token] = []
    line = 1
    for match in LEXER.finditer(source):
        kind = match.lastgroup
        value = match.group()
        end_line = line + len(re.findall(r"\r\n|\r|\n", value))
        if kind != "space":
            if value in {'"', "'"} or (value == "/" and source[match.start():].startswith("/*")):
                raise FormatError(f"line {line}: unterminated string or block comment")
            tokens.append(Token(kind or "symbol", value, line, end_line))
        line = end_line
    return tokens


def joined(tokens: list[Token]) -> str:
    """Apply punctuation spacing without editing literal or comment contents."""
    parts: list[str] = []
    previous: Token | None = None
    for token in tokens:
        current = token.text
        before = previous.text if previous else ""
        separator = " " if previous else ""
        if previous and previous.kind == "line_comment":
            separator = "\n"
        elif token.comment or (previous and previous.comment):
            pass
        elif current in {";", ",", ".", "..", "$", ")", "]", ">", "[", "<"}:
            separator = ""
        elif before in {".", "..", "$", "(", "[", "<", "@", "~", "!", "#"}:
            separator = ""
        elif current == "(" or current == "#":
            separator = ""
        elif current in {"~", "!"} and before in {".", "#"}:
            separator = ""
        elif current == "-" and before == "=":
            separator = " "
        elif before == "-":
            separator = ""
        parts.extend((separator, current))
        previous = token
    return "".join(parts)


@dataclass
class Declaration:
    tokens: list[Token]
    prefix: list[list[Token]] = field(default_factory=list)
    children: list[Declaration] | None = None
    suffix: list[Token] = field(default_factory=list)
    blank_before: bool = False

    @property
    def kind(self) -> str:
        for token in self.tokens:
            if token.comment or token.text in MODIFIERS | {"abstract", "!"}:
                continue
            if token.kind == "word" and token.text in {
                "package", "import", "class", "interface", "enum", "datatype", "mapentry",
                *FEATURE_KINDS,
            }:
                return token.text
            break
        return "literal"


class Parser:
    """Separate declarations and blocks; this is not a semantic Emfatic validator."""

    def __init__(self, tokens: list[Token]):
        self.tokens = tokens
        self.position = 0

    def balanced(self, opening: str, closing: str) -> list[Token]:
        result = [self.tokens[self.position]]
        self.position += 1
        while self.position < len(self.tokens):
            token = self.tokens[self.position]
            if token.text == closing:
                result.append(token)
                self.position += 1
                return result
            if token.text in {"(", "[", "<"}:
                result.extend(self.balanced(token.text, {"(": ")", "[": "]", "<": ">"}[token.text]))
            elif token.text in {")", "]", ">", "{", "}"}:
                raise FormatError(f"line {token.line}: unexpected {token.text!r}")
            else:
                result.append(token)
                self.position += 1
        raise FormatError(f"line {result[0].line}: unclosed {opening!r}")

    def annotation(self) -> list[Token]:
        result = [self.tokens[self.position]]
        self.position += 1
        if self.position >= len(self.tokens):
            raise FormatError(f"line {result[0].line}: incomplete annotation")
        token = self.tokens[self.position]
        if token.kind not in {"word", "string"}:
            raise FormatError(f"line {token.line}: expected annotation name")
        result.append(token)
        self.position += 1
        while self.position < len(self.tokens) and self.tokens[self.position].text == ".":
            result.append(self.tokens[self.position])
            self.position += 1
            if self.position >= len(self.tokens) or self.tokens[self.position].kind != "word":
                raise FormatError(f"line {token.line}: incomplete qualified annotation name")
            result.append(self.tokens[self.position])
            self.position += 1
        if self.position < len(self.tokens) and self.tokens[self.position].text == "(":
            result.extend(self.balanced("(", ")"))
        return result

    def declarations(self, nested: bool = False) -> list[Declaration]:
        result: list[Declaration] = []
        prefix: list[list[Token]] = []
        previous_end_line: int | None = None
        while self.position < len(self.tokens):
            token = self.tokens[self.position]
            if token.comment:
                prefix.append([token])
                self.position += 1
                continue
            if token.text == "@":
                prefix.append(self.annotation())
                continue
            if token.text == "}":
                if not nested:
                    raise FormatError(f"line {token.line}: unexpected '}}'")
                break
            declaration = Declaration([], prefix)
            # A section can begin with a comment or annotation before its feature.
            last_line = previous_end_line
            for part in prefix + [[token]]:
                if last_line is not None and part[0].line > last_line + 1:
                    declaration.blank_before = True
                last_line = part[-1].end_line
            prefix = []
            while self.position < len(self.tokens):
                token = self.tokens[self.position]
                if token.text in {"(", "[", "<"}:
                    declaration.tokens.extend(
                        self.balanced(token.text, {"(": ")", "[": "]", "<": ">"}[token.text])
                    )
                    continue
                if token.text == "{":
                    if declaration.kind not in {"package", "class", "interface", "enum"}:
                        raise FormatError(f"line {token.line}: unsupported block declaration")
                    self.position += 1
                    declaration.children = self.declarations(nested=True)
                    if self.position >= len(self.tokens):
                        raise FormatError(f"line {token.line}: unclosed '{{'")
                    end = self.tokens[self.position]
                    self.position += 1
                    if self.position < len(self.tokens) and self.tokens[self.position].text == ";":
                        end = self.tokens[self.position]
                        declaration.suffix.append(end)
                        self.position += 1
                    break
                if token.text in {"}", ")", "]", ">"}:
                    raise FormatError(f"line {token.line}: expected ';' before {token.text!r}")
                declaration.tokens.append(token)
                self.position += 1
                if token.text == ";":
                    end = token
                    break
            else:
                start = declaration.tokens[0] if declaration.tokens else token
                raise FormatError(f"line {start.line}: incomplete declaration (expected ';' or block)")
            while self.position < len(self.tokens):
                next_token = self.tokens[self.position]
                if not next_token.comment or next_token.line != end.end_line:
                    break
                declaration.suffix.append(next_token)
                self.position += 1
                end = next_token
            result.append(declaration)
            previous_end_line = end.end_line
        if prefix:
            trailing = Declaration([], prefix)
            trailing.blank_before = (
                previous_end_line is not None and prefix[0][0].line > previous_end_line + 1
            )
            result.append(trailing)
        return result


def identifier_end(tokens: list[Token], position: int) -> int:
    if position < len(tokens) and tokens[position].text == "~":
        position += 1
    if position >= len(tokens) or tokens[position].kind != "word":
        raise FormatError("expected identifier")
    return position + 1


def type_end(tokens: list[Token], position: int) -> int:
    position = identifier_end(tokens, position)
    while position < len(tokens):
        value = tokens[position].text
        if value in {".", "$"}:
            position = identifier_end(tokens, position + 1)
        elif value in {"<", "["}:
            closing = ">" if value == "<" else "]"
            depth = 1
            position += 1
            while position < len(tokens) and depth:
                depth += (tokens[position].text == value) - (tokens[position].text == closing)
                position += 1
            if depth:
                raise FormatError("unclosed type expression")
        else:
            break
    return position


def feature_columns(declaration: Declaration) -> list[str] | None:
    tokens = declaration.tokens
    if declaration.children is not None or not tokens or tokens[-1].text != ";":
        return None
    if any(token.comment or "\n" in token.text or "\r" in token.text for token in tokens):
        return None
    try:
        position = 0
        while position < len(tokens) and tokens[position].text not in FEATURE_KINDS:
            if tokens[position].text == "!":
                position += 1
            if tokens[position].text not in MODIFIERS:
                return None
            position += 1
        kind_position = position
        position = type_end(tokens, position + 1)
        if tokens[position].text == "#":
            position = identifier_end(tokens, position + 1)
        # Keep modifiers, keyword and type together, with #opposite attached.
        columns = [joined(tokens[:position])]
        name_start = position
        position = identifier_end(tokens, position)
        if tokens[kind_position].text == "op":
            if tokens[position].text != "(":
                return None
            columns.extend((joined(tokens[name_start:-1]), ""))
        else:
            if tokens[position].text not in {"=", ";"}:
                return None
            columns.extend((joined(tokens[name_start:position]), joined(tokens[position:-1])))
        return columns
    except (FormatError, IndexError):
        # Leave unfamiliar feature syntax in the general token-preserving layout.
        return None


def aligned(columns: list[str], widths: list[int]) -> str:
    prefix, name, default = columns
    text = prefix.ljust(widths[0] + 1) + name
    if default:
        text += " " * (widths[1] - len(name) + 1) + default
    return text + ";"


def render(declarations: list[Declaration], depth: int = 0, context: str = "package") -> list[str]:
    indent = "\t" * depth
    groups: list[list[Declaration]] = [[]]
    previous_kind: str | None = None
    for declaration in declarations:
        kind = declaration.kind
        separate = False
        if declaration.tokens and previous_kind is not None:
            if context == "package":
                separate = kind != previous_kind or kind not in {"import", "datatype", "mapentry"}
            else:
                separate = declaration.blank_before or kind != previous_kind
        elif previous_kind is not None and context != "package":
            separate = declaration.blank_before
        if separate:
            groups.append([])
        groups[-1].append(declaration)
        if declaration.tokens:
            previous_kind = kind

    lines: list[str] = []
    for group in groups:
        if lines:
            lines.append("")
        rows = [feature_columns(declaration) for declaration in group]
        widths = [
            max((
                len(row[index]) for declaration, row in zip(group, rows)
                if row and (index != 1 or declaration.kind != "op")
            ), default=0)
            for index in range(2)
        ]
        for declaration, row in zip(group, rows):
            for prefix in declaration.prefix:
                lines.append(indent + joined(prefix))
            if not declaration.tokens:
                continue
            if declaration.children is None:
                text = aligned(row, widths) if row else joined(declaration.tokens)
                lines.append(indent + text)
            elif not declaration.children:
                lines.append(indent + joined(declaration.tokens) + " {}")
            else:
                lines.append(indent + joined(declaration.tokens) + " {")
                lines.extend(render(declaration.children, depth + 1, declaration.kind))
                lines.append(indent + "}")
            if declaration.suffix:
                suffix = joined(declaration.suffix)
                lines[-1] += ("" if suffix.startswith(";") else " ") + suffix
    return lines


def format_source(source: str) -> str:
    original = tokenize(source)
    output = "\n".join(render(Parser(original).declarations())) + "\n"
    before = [(token.kind, token.text) for token in original]
    after = [(token.kind, token.text) for token in tokenize(output)]
    if before != after:
        raise FormatError("formatting would change tokens; file left unchanged")
    return output


def find_files(paths: list[Path], excluded: set[str]) -> list[Path]:
    files: set[Path] = set()

    def traversal_error(error: OSError) -> None:
        raise error

    for path in paths:
        if path.is_symlink():
            raise FormatError(f"refusing symbolic link: {path}")
        if path.is_file():
            if path.suffix.lower() != ".emf":
                raise FormatError(f"expected an .emf file: {path}")
            files.add(path.resolve())
        elif path.is_dir():
            for directory, directories, filenames in os.walk(path, onerror=traversal_error):
                directories[:] = sorted(
                    name for name in directories
                    if name not in excluded and not (Path(directory) / name).is_symlink()
                )
                for name in filenames:
                    candidate = Path(directory) / name
                    if candidate.suffix.lower() == ".emf" and not candidate.is_symlink():
                        files.add(candidate.resolve())
        else:
            raise FormatError(f"path does not exist: {path}")
    return sorted(files, key=lambda path: path.as_posix())


def write_atomic(path: Path, content: bytes) -> None:
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
        os.chmod(temporary, stat.S_IMODE(path.stat().st_mode))
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path, help="Files/directories; default: project root.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="Report unformatted files without writing.")
    mode.add_argument("--diff", action="store_true", help="Print unified diffs without writing.")
    parser.add_argument("--exclude", action="append", default=[], metavar="NAME",
                        help="Skip an additional directory name (repeatable).")
    args = parser.parse_args(argv)
    changes: list[tuple[Path, bytes, bytes]] = []
    try:
        files = find_files(args.paths or [ROOT], EXCLUDED | set(args.exclude))
        # Prepare all files before writing, so a parse/encoding error causes no writes.
        for path in files:
            try:
                original = path.read_bytes()
                bom = b"\xef\xbb\xbf" if original.startswith(b"\xef\xbb\xbf") else b""
                source = original[len(bom):].decode("utf-8")
                formatted = bom + format_source(source).encode("utf-8")
            except (FormatError, UnicodeError, OSError) as error:
                raise FormatError(f"{path}: {error}") from error
            if original != formatted:
                changes.append((path, original, formatted))
        for path, original, formatted in changes:
            try:
                label = os.path.relpath(path)
            except ValueError:
                # Windows paths on another drive have no relative representation.
                label = str(path)
            if args.diff:
                diff = difflib.unified_diff(
                    original.decode("utf-8-sig").splitlines(keepends=True),
                    formatted.decode("utf-8-sig").splitlines(keepends=True),
                    fromfile=label, tofile=label + " (formatted)",
                )
                for line in diff:
                    sys.stdout.write(line)
                    if not line.endswith("\n"):
                        sys.stdout.write("\n\\ No newline at end of file\n")
            elif args.check:
                print(f"Would format: {label}")
            else:
                write_atomic(path, formatted)
                print(f"Formatted: {label}")
    except (FormatError, OSError) as error:
        print(f"Emfatic formatting failed: {error}", file=sys.stderr)
        return 2
    if not args.diff:
        action = "need formatting" if args.check else "formatted"
        print(f"{len(files)} .emf file(s) checked; {len(changes)} {action}.")
    return 1 if (args.check or args.diff) and changes else 0


if __name__ == "__main__":
    raise SystemExit(main())
