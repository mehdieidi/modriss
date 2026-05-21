# mde-cli

`mde-cli` is a Java command-line tool for converting [Emfatic](https://eclipse.dev/emfatic/)
metamodels into `.ecore`.

It supports both:

- single-file conversion for standalone `.emf` or `.emfatic` metamodels
- modular metamodel conversion for directories that contain multiple Emfatic modules forming one
  logical metamodel

The tool is designed for practical metamodel engineering work, with strong diagnostics for parse
errors, unresolved imports, type-resolution failures, and other Emfatic or Ecore problems.

## Features

- Converts an individual `.emf` or `.emfatic` file to `.ecore`
- Converts a modular Emfatic directory into one combined final `.ecore`
- Supports modular single-file conversion when a file depends on sibling modules
- Produces human-readable diagnostics with actionable hints
- Writes optional detailed report files for troubleshooting and CI logs
- Returns non-zero exit codes for failures, making it suitable for automation

## Requirements

- Java 17 or newer
- Maven 3.9 or newer for building from source

## Build

From the repository root:

```powershell
mvn -q -pl tools/mde-cli -am package
```

This produces the executable jar:

```text
tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar
```

## Usage

Show help:

```powershell
java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar --help
```

General form:

```powershell
java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar [options] <input>
```

## Convert A Single File

For a standalone Emfatic file:

```powershell
java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
  mde/metamodels/cim/cim-types.emf
```

Specify the output explicitly:

```powershell
java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
  mde/metamodels/cim/cim-kernel.emf `
  --output build/cim-kernel.ecore `
  --overwrite
```

If the input file imports sibling `.ecore` modules, `mde-cli` automatically performs a modular
bootstrap in a temporary workspace and still emits only the requested file’s `.ecore`.

## Convert A Modular Directory

For a directory that contains several Emfatic files representing one modular metamodel:

```powershell
java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
  mde/metamodels/cim `
  --root mde/metamodels/cim/cim-root.emf `
  --output build/cim-combined.ecore `
  --overwrite
```

This mode is intended for cases where:

- the metamodel is split across multiple `.emf` files
- modules import local `.ecore` files
- one root module defines the overall entry package or root aggregation

If `--root` is not supplied, the CLI tries to discover a root file automatically using common
conventions such as `root.emf` or a single `*-root.emf`.

## Options

| Option                  | Description                                 |
|-------------------------|---------------------------------------------|
| `-o`, `--output <path>` | Output `.ecore` file path                   |
| `--root <path>`         | Root Emfatic file for directory conversion  |
| `--overwrite`           | Replace an existing output file             |
| `--verbose`             | Print step-by-step execution details        |
| `--log-file <path>`     | Write a detailed execution report to a file |
| `-h`, `--help`          | Show help                                   |
| `-V`, `--version`       | Show version                                |

## Output Behavior

Default output paths:

- file input: `<input-name>.ecore` next to the input file
- directory input: `<directory-name>-combined.ecore` inside the input directory

Examples:

- `cim-kernel.emf` -> `cim-kernel.ecore`
- `mde/metamodels/cim/` -> `mde/metamodels/cim/cim-combined.ecore`

## Diagnostics

One of the main goals of `mde-cli` is to make Emfatic failures understandable.

When conversion fails, the tool reports:

- the file that failed
- the parser or semantic error message
- line and column when available
- a human-readable hint
- a source excerpt when available

Typical problems the CLI can explain:

- invalid Emfatic syntax
- reserved-keyword usage as an identifier
- unresolved imported `.ecore` files
- unresolved type references
- illegal attribute or reference kinds
- structural validation issues reported from Ecore validation

Example failure shape:

```text
Failed to compile cim-domain-data.emf:
Encountered " "attr" "attr "" at line 102, column 3.
Was expecting:
    "}" ...
The identifier `derived` is an Emfatic keyword. Rename it or escape it as `~derived`.
```

## Report Files

Use `--log-file` to write a detailed machine-readable and human-readable execution log:

```powershell
java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
  mde/metamodels/cim `
  --root mde/metamodels/cim/cim-root.emf `
  --output build/cim-combined.ecore `
  --overwrite `
  --verbose `
  --log-file build/cim-conversion-report.txt
```

The report file includes:

- overall status
- output path
- diagnostics
- execution events
- exception information and stack trace for unexpected failures

## Exit Codes

- `0`: success
- `1`: unexpected/internal failure
- `2`: user-fixable conversion failure such as invalid input, parse errors, unresolved imports, or
  semantic metamodel problems

## Verified Example

The tool has been verified against the sample modular CIM metamodel in:

```text
mde/metamodels/cim/
```

Verified scenarios include:

- generating `.ecore` for each individual CIM `.emf` file
- generating one combined final `.ecore` for the full CIM directory

## Development Notes

This module is built as a standalone Maven subproject:

```text
tools/mde-cli/
```

Run tests:

```powershell
mvn -q -pl tools/mde-cli -am test
```

## Limitations

- The tool currently focuses on local filesystem-based Emfatic and `.ecore` imports.
- If a metamodel relies on external resources outside the working directory structure, those
  resources must still be accessible to the JVM at conversion time.
- The combined directory output is intended for modular metamodels that logically belong to one
  final Ecore model.

## License

This project inherits the repository’s licensing and publication terms. If you plan to publish
`mde-cli` independently, make sure the repository-level license and third-party dependency
obligations are preserved.
