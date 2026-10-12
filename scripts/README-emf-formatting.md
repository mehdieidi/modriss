# Emfatic formatter

Run from the repository root with Python 3.10 or newer. No dependencies or Eclipse
installation are required.

The repository-wide `python scripts/format.py` command also calls this formatter.
Its `--check` mode forwards `--check`, and its excluded directory names are passed
through. The generic whitespace normalizer always skips `.emf` files, even if they
are added to `normalizedExtensions`, preserving tabs, column alignment, and exact
comment/string contents. Prettier also excludes `.emf` files.

```sh
python scripts/format-emf.py
```

The script discovers `.emf` files recursively and updates only files whose formatting
changes. Discovery defaults to the project root, even when the script is launched
from another working directory. Files are processed in sorted path order.

Preview changes or check formatting without writing:

```sh
python scripts/format-emf.py --diff
python scripts/format-emf.py --check
```

You can also select individual files or directories, and exclude additional directory
names. Relative paths are relative to the current working directory.

```sh
python scripts/format-emf.py mde/metamodels/pim
python scripts/format-emf.py mde/metamodels/shared/kernel.emf
python scripts/format-emf.py --exclude generated --exclude archive
```

By default, traversal skips `.git`, `.idea`, `.vscode`, `.venv`, `node_modules`,
`target`, and `vendor`. It skips symbolic links during traversal and refuses links
passed explicitly. Explicit file arguments are processed even in excluded directories.

## Formatting rules

In VS Code, use the Emfatic language settings below in `.vscode/settings.json`.
This explicitly sets four-space tab stops and prevents indentation detection from
choosing two-space tabs based on an older file. VS Code needs these settings when
an EditorConfig extension is not installed. The `.vscode` directory is ignored by
Git, so each checkout needs its own workspace settings.

```json
{
  "[emfatic]": {
    "editor.tabSize": 4,
    "editor.insertSpaces": false,
    "editor.detectIndentation": false
  }
}
```

Reopen an already open Emfatic file if its indentation status still shows a tab
size of two, then run the formatter to replace any existing space indentation.

- One literal tab per nesting level, with a tab width of four spaces; opening
  braces on the declaration line. The repository's EditorConfig sets this width
  for Emfatic files.
- One declaration per line, compact empty blocks, a final newline, and LF layout.
- One blank line between classifiers, between package/import/declaration sections,
  and when feature kinds change within a class. Consecutive imports stay together.
- Existing blank lines inside classes/interfaces separate alignment sections, even
  when the sections use the same feature kind. Multiple blank lines become one.
  A section's leading comments and annotations stay with that section.
- A single space separates modifiers, the feature keyword, and its type.
  The multiplicity and optional `#opposite` attach directly to the type, for example
  `ref Item[*]#parent`.
- Feature names align within each section, starting exactly one space after the
  longest complete prefix (modifiers, keyword, type, multiplicity, and opposite).
  Shorter prefixes receive spaces to align their names. Default assignments also
  align one space after the longest feature name.
- Tabs provide indentation; spaces provide column padding and separate words
  within declarations, annotations, and expressions.
- Punctuation spacing is consistent, including annotations and operation parameters.
- Indentation is normalized, and repeated blank lines collapse to one. Section
  boundaries and declaration, modifier, annotation, and import order are preserved.

For example (shown with tabs expanded to four-space stops):

```emfatic
class Item {
    attr String[1] ~id;
    attr String    name;
    attr Boolean   enabled = true;

    val Item[*]#parent children;

    ref Item[?]#children parent;
}
```

The formatter follows the [Emfatic syntax reference](https://eclipse.dev/emfatic/).
It recognizes declarations, nested packages, classes/interfaces, enums, annotations,
and balanced type/parameter expressions. Unfamiliar feature syntax uses the general
layout instead of column alignment. It does not compile or semantically validate models.

Strings and comments retain their exact contents, including internal whitespace and
line endings. Features containing internal comments or multiline literals use the
general layout. UTF-8 BOMs are preserved. Other encodings cause an error.

Before writing, the formatter compares every token, including comments and strings,
against the original input. All files are prepared before any writes: an incomplete
declaration, unbalanced delimiter, encoding error, or token mismatch aborts the run
without changing files. Each changed file is replaced atomically. An I/O failure
during replacement can leave earlier files formatted; the batch is not transactional.

Exit codes: `0` for success/already formatted; `1` for changes found by `--check` or
`--diff`; `2` for input or filesystem errors.

