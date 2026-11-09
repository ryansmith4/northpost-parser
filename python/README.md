# NorthPost Parser for Python

A standalone Poetry project alongside the Java library. Requires Python 3.11+.
Runtime parsing uses `antlr4-python3-runtime`; Java is **not** required to install
or use the Python package.

## Install and test

From this directory:

```bash
poetry install
poetry run pytest
poetry build
```

Or install directly from the repository root with `pip install ./python`.

## Usage

```python
from northpost_parser import (
    AddressParserService,
    NormalizationStrategy,
    ParsingStrategy,
)

parser = AddressParserService()
result = parser.parse_address(
    "JOHN DOE\n123 MAIN ST W\nTORONTO ON M5V 2Y7",
    ParsingStrategy.STRICT_THEN_LENIENT,
)
if result.successful:
    address = result.components
    print(address.street_number)       # 123
    print(address.street_name)         # MAIN
    print(address.postal_code)         # M5V 2Y7
    print(address.get_formatted_address())
    canonical = address.normalize(NormalizationStrategy.FULL_FORM)
    print(canonical.street_type)       # STREET
else:
    print(result.errors)
```

The Python API uses snake_case names. Immutable dataclasses replace Java records
and builders; instantiate `AddressComponents(...)` directly or use
`dataclasses.replace`. String fields are trimmed, uppercased, and null-safe.
Presence helpers such as `has_unit_number()` are also available.
Formatting helpers return `str | None` instead of Java `Optional`.

`LENIENT` extracts available fields; `STRICT` requires a mailing-valid address;
`STRICT_THEN_LENIENT` retries and records strict rejection in warnings.
Mailing validity follows the Java implementation, including its requirement for
an addressee. Syntax errors and empty input still fail in lenient mode.
Results contain immutable error/warning tuples and the mode actually used.
`parse_addresses` and `get_batch_statistics` provide batch parsing and statistics.
Unknown street types/directions are preserved by normalization.

Site information and rural routes can share a delivery line:
`SITE 4 BOX 23 RR 5` produces `site_info="SITE 4 BOX 23"`,
`rural_route="5"`, and address type `RURAL_ROUTE`. The route prefix also
accepts `R.R.`, `R R`, and `R. R.`. The site box number is not a postal box number.

French postal boxes accept `CP`, `C.P`, `C.P.`, and `CASE POSTALE` before the
box number, including comma-separated forms such as `C.P 2086, QUEBEC, QC`.

### Optional field extraction

Both `parse_address` and `parse_addresses` accept keyword-only
`has_addressee` and `has_postal_code` flags, defaulting to `True` to preserve
automatic extraction:

```python
result = parser.parse_address(
    "MAIN ST\nTORONTO ON", has_addressee=False, has_postal_code=False,
)
results = parser.parse_addresses(
    addresses, has_addressee=False, has_postal_code=False,
)
```

When addressee extraction is disabled, unanchored lines are interpreted as
delivery information instead.
With `has_addressee=False`, a delivery line in `street name, number` format
(for example, `BRIDGE ROAD, 5`) retains the full street name `BRIDGE ROAD` and
extracts civic number `5`, without splitting off a street type.
Disabling postal-code extraction leaves that field
empty and prevents province inference from it. Postal-code tokens can still be
recognized structurally to separate address lines and municipality text.
Strict mailing-validity requirements remain unchanged, so disabling either field
causes strict parsing to reject an otherwise complete address.
The incomplete-address warning respects these flags: intentionally omitted fields
do not trigger it, but missing delivery, municipality, or province still do.
Strict rejection errors and strict-to-lenient fallback warnings are unchanged.

## ANTLR generation

The single source of truth is
[`../src/main/antlr/CanadianAddress.g4`](../src/main/antlr/CanadianAddress.g4).
Generated Python lexer, parser, and visitor sources are checked in, so installing
a wheel does not run Java or download tools.

After changing the grammar, install Java and run:

```bash
poetry run python scripts/generate_parser.py
poetry run python scripts/generate_parser.py --check
poetry run pytest
```

The script downloads ANTLR **4.13.2** into the user cache on first use. To avoid
that download, supply `--antlr-jar /path/to/antlr-4.13.2-complete.jar`.
The generator version and Poetry runtime pin must match. When upgrading ANTLR,
update both, regenerate, and rerun tests. Java regeneration remains
`./gradlew generateGrammarSource` from the repository root.

Tests reuse the repository's curated ODA fixture. The large ODA/NAR dataset
validation runners remain Java tooling; those datasets are not bundled in the
Python distribution. Existing Java releases and Maven publishing are unchanged.

## Releasing to PyPI

Publishing a GitHub release with a `vX.Y.Z` tag runs
[`release-python.yml`](../.github/workflows/release-python.yml) alongside the
Java release. It sets the package version from the tag, verifies the generated
sources, runs the tests, builds, and publishes using
[PyPI trusted publishing](https://docs.pypi.org/trusted-publishers/) (no token).

One-time setup:

1. On PyPI, add a trusted publisher for project `northpost-parser`
   (use *pending publisher* for the first release): owner `ryansmith4`,
   repository `northpost-parser`, workflow `release-python.yml`,
   environment `pypi`.
2. In the GitHub repository settings, create an environment named `pypi`
   (optionally requiring reviewer approval).

To retry a failed publish: `gh workflow run release-python.yml --ref vX.Y.Z`.

Licensed under the [Apache-2.0 license](LICENSE).
