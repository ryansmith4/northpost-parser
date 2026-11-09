<p align="center">
  <img src="docs/logo.png" alt="NorthPost Parser" width="200">
</p>

<h1 align="center">NorthPost Parser</h1>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.guidedbyte/northpost-parser"><img src="https://img.shields.io/maven-central/v/com.guidedbyte/northpost-parser" alt="Maven Central"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
</p>

A Canadian postal address parser that converts free-form address text into structured components. Built with an [ANTLR4](https://www.antlr.org/) grammar, NorthPost Parser handles the full range of Canadian addressing formats — civic, PO box, rural route, and general delivery — in both English and French.

Pure Java library with no framework dependencies. Validated against **9.6 million+** [Statistics Canada ODA](https://www.statcan.gc.ca/en/lode/databases/oda) and **16.4 million+** [Statistics Canada NAR](https://www150.statcan.gc.ca/n1/pub/46-26-0002/462600022022001-eng.htm) addresses with a **100% parse success rate** and **99.99% true field-level accuracy**.

A standalone [Python Poetry project](python/README.md) is also available in
[`python/`](python/), with a Python port of the library and ANTLR4-generated
Python sources from the same grammar. The Java project and build remain unchanged.
The large-dataset validation figures above describe the Java implementation.

## Installation

### Gradle

```groovy
implementation 'com.guidedbyte:northpost-parser:1.1.0'
```

Kotlin DSL:

```kotlin
implementation("com.guidedbyte:northpost-parser:1.1.0")
```

### Maven

```xml
<dependency>
    <groupId>com.guidedbyte</groupId>
    <artifactId>northpost-parser</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Features

- **Full address decomposition** — addressee, unit, street number/name/type/direction, municipality, province, postal code, and more
- **English and French** — recognizes ~110 English and ~25 French street types, bilingual directionals (N/S/E/W, NORD/SUD/EST/OUEST), and French linking particles (DE, DU, DES, etc.)
- **Smart disambiguation** — French type-first detection (RUE PRINCIPALE), lettered avenues (AVENUE P), opposing direction pairs (EAST WEST RD), French article prefixes (GRANDE ALLEE), type-as-name guard (AVENUE RD → name=AVENUE, type=RD)
- **Preserves what you write** — hyphens (2E-ET-3E), slashes (36/37), ampersands (6 & 10), spaced dashes (RD - RTE 113), and parentheses (DRIVEWAY (THE)) retained in street names
- **All delivery types** — civic addresses, PO boxes (PO BOX / CP / C.P / C.P. / CASE POSTALE), rural routes (RR), general delivery (GD), and mixed
- **Flexible input** — handles multi-line (Canada Post format), single-line with commas, lowercase, extra whitespace, accented characters, and various abbreviations
- **Three parsing modes** — `LENIENT` (extract what's available), `STRICT` (reject incomplete addresses), and `AUTO` (try strict, fall back to lenient)
- **Zero framework dependencies** — depends only on ANTLR runtime and SLF4J
- **All 13 provinces and territories** supported with full-name, abbreviated, and 2-letter code recognition

## Quick Start

### Prerequisites

- **Java 21** or later
- **Gradle 9.x** (wrapper included — no separate install needed)

### Build

```bash
./gradlew build
```

### Usage

```java
var parser = new AddressParserService();
var result = parser.parseAddress("""
    JOHN SMITH
    123 MAIN STREET
    TORONTO ON M5V 2Y7
    """);

var addr = result.components();
addr.addressee();       // "JOHN SMITH"
addr.streetNumber();    // "123"
addr.streetName();      // "MAIN"
addr.streetType();      // "STREET"
addr.municipality();    // "TORONTO"
addr.province();        // "ON"
addr.postalCode();      // "M5V 2Y7"
addr.addressType();     // CIVIC
```

### Parsing Modes

```java
var parser = new AddressParserService();

// Lenient (default) — extracts whatever is available, always succeeds
var result = parser.parseAddress("123 MAIN ST");

// Strict — requires a complete mailing address
var result = parser.parseAddress(address, ParsingStrategy.STRICT);

// Auto — tries strict, falls back to lenient
var result = parser.parseAddress(address, ParsingStrategy.STRICT_THEN_LENIENT);
```

### Optional field extraction

Existing calls automatically extract addressee and postal code. When you know
either field is absent, disable its extraction:

```java
var result = parser.parseAddress("MAIN ST\nTORONTO ON", false, false);
// Arguments after the address: hasAddressee, hasPostalCode.
var strict = parser.parseAddress(address, ParsingStrategy.STRICT, false, false);
var batch = parser.parseAddresses(addresses, ParsingStrategy.LENIENT, false, false);
```

With `hasAddressee=false`, unanchored lines are interpreted as delivery information
instead of addressee text. With `hasPostalCode=false`, the postal-code field stays
empty and cannot be used to infer a province. Postal-code tokens can still be
recognized structurally to keep municipality and delivery lines separate.
Strict mailing-validity requirements are unchanged: disabling either field
causes strict parsing to reject an otherwise complete address.
The incomplete-address warning respects these flags: intentionally omitted fields
do not trigger it, but missing delivery, municipality, or province still do.
Strict rejection errors and strict-to-lenient fallback warnings are unchanged.

With `hasAddressee=false`, `street name, number` delivery lines such as
`BRIDGE ROAD, 5` retain the full street name `BRIDGE ROAD` and extract civic
number `5`, without splitting off a street type.

## Supported Address Formats

NorthPost handles the standard Canada Post multi-line format and many informal variations:

```
Standard multi-line:          Single-line:
  JOHN SMITH                    JOHN SMITH, 123 MAIN ST, TORONTO ON M5V 2Y7
  123 MAIN STREET
  TORONTO ON  M5V 2Y7

With unit (multiple styles):  PO Box:
  APT 5 456 ELM AVE              PO BOX 1234 STN MAIN
  APT A 456 ELM AVE              CP 1234 SUCC CENTRE-VILLE
  APT B15 456 ELM AVE
  APT 1/2 456 ELM AVE
  5-456 ELM AVE                Rural Route:
  456 ELM AVE APT 5
  456 ELM AVE APT5
                                  RR 2 STN MAIN
Lettered avenue (SK/ON):       Fractional civic:
  402 AVENUE P S                  123 1/2 MAIN STREET
                               General Delivery:
Compound identifiers:             GD STN MAIN
  36/37 NOTTAWASAGA SIDERD
  6 & 10 SIDERD                Care-of:
  DRUMMOND RD - RTE 113          C/O JANE DOE
  2E-ET-3E RANG
                               Dual civic:
                                  10/12 MAIN STREET
```

**Province recognition** accepts 2-letter codes (`ON`), full names (`ONTARIO`), and informal abbreviations (`ONT.`, `B.C.`, `QUE.`, `NFLD.`, etc.).

**Postal code inference** — when a postal code is present, the first letter is used to validate or infer the province (e.g., `M` = Ontario, `V` = British Columbia, `H` = Quebec).

## Architecture

```
src/main/
├── antlr/
│   └── CanadianAddress.g4                  # ANTLR4 grammar
└── java/com/guidedbyte/address/
    ├── model/
    │   └── AddressComponents.java          # Immutable record + builder
    ├── parser/
    │   └── AddressComponentVisitor.java    # Semantic visitor
    └── service/
        └── AddressParserService.java       # Parsing orchestration
```

### Design

The parser uses a **minimal grammar + semantic visitor** approach:

1. **Lexer** (`CanadianAddress.g4`) — produces only structural tokens: `WORD`, `NUMBER`, `POSTAL_CODE`, `NL`, etc. No address-specific keywords are baked into the grammar.
2. **Visitor** (`AddressComponentVisitor`) — interprets tokens using comprehensive lookup tables for street types, provinces, directions, and unit designators. All semantic rules live here, making them easy to update without changing the grammar.
3. **Service** (`AddressParserService`) — orchestrates parsing, applies validation rules, and manages parsing strategies.

This separation means the grammar rarely needs to change. Adding a new street type or province abbreviation is a table update in the visitor.

### Address Line Interpretation

Lines are classified using **anchor-based detection** rather than fixed positions:

| Classification | Rule |
|----------------|------|
| Region | Last line (positional — Canada Post convention) |
| Delivery | Lines starting with a civic number, PO/CP/BOX, RR, GD, GENERAL, or unit designator |
| Care-of | Lines matching C/O or A/S pattern |
| Site info | Lines starting with SITE, COMP, or EMPL; a same-line rural route is extracted separately (`SITE 4 BOX 23 RR 5` → site `SITE 4 BOX 23`, route `5`) |
| Addressee | First line with no recognized delivery anchor |
| Subsequent unanchored | Routed through delivery interpretation |

This approach correctly handles addresses with or without an addressee line, regardless of the number of lines.

## Testing

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.guidedbyte.address.AddressParserTest"

# Run a specific nested test
./gradlew test --tests "com.guidedbyte.address.AddressParserTest.FrenchAddressTests"
```

### Test Suite

| Suite | Scope | Description |
|-------|-------|-------------|
| `AddressParserTest` | Unit | 300+ tests across 20+ categories — civic, French, PO box, rural, lettered avenues, direction pairs, type disambiguation, hyphen/slash/ampersand preservation, edge cases, validation, batch, performance |
| `OdaAddressTest` | Parameterized | 150 curated addresses from Statistics Canada ODA datasets |
| `NarSampleRegressionTest` | Parameterized | About 2,400 NAR addresses from all 13 provinces and territories plus known-hard patterns, replayed against the parser's recorded output (one test per province) |

### NAR Regression Sample

`src/test/resources/nar_sample_addresses.csv` holds about 2,400 addresses drawn from the National Address Register across all 13 provinces and territories, each with the exact component values the parser produced for it. `NarSampleRegressionTest` replays the file on every `./gradlew test`, so a change in parser behaviour, including one introduced by an ANTLR runtime bump, fails the build without the multi-gigabyte bulk datasets. The file is also the conformance target for ports of the parser to other languages: feed each `address` column (with `\n` expanded to a line break) to the port and compare the eight remaining columns exactly.

Rows are stratified by province, with small provinces over-weighted so Ontario and Quebec do not dominate, and within a province by shape (unit, street type, direction and civic suffix present or absent). Known-hard patterns get their own sections: New Brunswick `ROUTE nnn`, Quebec `GRANDE ALLÉE`-style odonyms, lettered and numbered streets, fractional civic numbers and compound unit labels.

The expected values are the parser's own output, not NAR ground truth, so the file is a behaviour snapshot rather than a correctness oracle:

```bash
# After an intentional parser change: re-record the expected values, then review the diff
./gradlew refreshNarSample

# Redraw the sample itself (deterministic, seeded; needs nar-data/NAR_*.zip, see NAR Bulk Validation)
./gradlew drawNarSample
```

### ODA Bulk Validation

The parser can be validated against full [Statistics Canada ODA](https://www.statcan.gc.ca/en/lode/databases/oda) datasets (9.6M+ addresses).

**Setup:** Download per-province ODA zip files (named `ODA_XX_vN.zip`, e.g., `ODA_ON_v1.zip`) from the link above and place them in `oda-data/` at the project root. Any version number is accepted automatically. This directory is gitignored. If the directory or zip files are missing, the tests silently skip with no failures.

```bash
# Validate against all provinces
./gradlew cleanTest test -PodaBulk -PodaBulkOnly

# Validate specific provinces
./gradlew cleanTest test -PodaBulk -PodaBulkOnly -PodaProvinces=BC,QC
```

Reports are written to `build/reports/oda-bulk/`, including a mismatch CSV for each province for detailed analysis.

### NAR Bulk Validation

The parser can also be validated against the [Statistics Canada NAR](https://www150.statcan.gc.ca/n1/pub/46-26-0002/462600022022001-eng.htm) (National Address Register, 16.4M+ addresses). NAR provides **unit number** ground truth and **postal codes for all provinces** — fields that ODA lacks.

**Setup:** Download a NAR zip from the link above, rename it to `NAR_YYYYMM.zip` (e.g., `NAR_202512.zip`), and place it in `nar-data/` at the project root. The zip contains CSVs organized by numeric province code (e.g., `Address_35.csv` = Ontario) which the test maps automatically. This directory is gitignored. If the directory or zip file is missing, the tests silently skip with no failures.

```bash
# Validate against all provinces
./gradlew cleanTest test -PnarBulk -PnarBulkOnly

# Validate specific provinces
./gradlew cleanTest test -PnarBulk -PnarBulkOnly -PnarProvinces=AB,ON
```

Reports are written to `build/reports/nar-bulk/`. Both ODA and NAR bulk tests run in parallel and complete in under 5 minutes on modern hardware with 1.5GB heap.

### Data Attribution

- `nar_sample_addresses.csv`: Adapted from Statistics Canada, National Address Register (NAR_202512). This does not constitute an endorsement by Statistics Canada of this product. Used under the [Statistics Canada Open Licence](https://www.statcan.gc.ca/en/reference/licence).
- `oda_test_addresses.csv`: Contains information licensed under the [Open Government Licence – Canada](https://open.canada.ca/en/open-government-licence-canada), from Statistics Canada's Open Database of Addresses.

### Accuracy

Validated against **16.4 million** addresses across all 13 provinces/territories from the Statistics Canada [National Address Register (NAR)](https://www150.statcan.gc.ca/n1/pub/46-26-0002/462600022022001-eng.htm), and **9.6 million** from the [Open Database of Addresses (ODA)](https://www.statcan.gc.ca/en/lode/databases/oda). 100% parse success rate on both datasets.

#### NAR Accuracy (16.4M addresses)

| Field | Accuracy | Notes |
|-------|----------|-------|
| Parse success | **100.000%** | Every address parsed without errors |
| Province | **100.000%** | |
| Postal code | **100.000%** | |
| City | **99.97%** | |
| Street number | **99.998%** | |
| Unit number | **99.99%** | |
| Street direction | **99.99%** | |
| Street type | **99.98%** | |
| Street name | **99.43%** | See note below |

**Street name context:** The 0.57% gap is almost entirely classification differences, not parsing errors:
- **60%** — NAR data quality issue: New Brunswick stores `ROUTE 465` as the full street name instead of splitting name=`465`, type=`ROUTE` per Canada Post convention
- **38%** — Correct decomposition: the parser extracts type/direction as separate fields (e.g., name=`LAKE`, type=`PROMENADE`), while NAR stores the combined form (`LAKE PROMENADE`). Use `getFullStreetName()` for the combined form.
- **2%** — Genuine edge cases (bilingual double-type streets, etc.)

**True parser accuracy** (excluding NAR data issues and correct decomposition): **99.989%**

#### ODA Accuracy (9.6M addresses)

| Field | Accuracy | Addresses Tested | Notes |
|-------|----------|-----------------|-------|
| Parse success | **100.00%** | 9,603,114 | |
| Province | **100.00%** | 9,603,114 | |
| Postal code | **99.78%** | 556,943 | Mismatches are malformed codes in ODA data |
| City | **99.43%** | 9,279,034 | ODA uses underscores in multi-word names |
| Street number | **99.13%** | 9,603,114 | ODA includes parentheses/annotations |
| Street type | **96.77%** | 5,547,130 | Normalization gap (full vs abbreviated forms) |
| Street direction | **96.64%** | 767,869 | Normalization gap (O vs OUEST, E vs EAST) |
| Street name | **93.96%** | 6,076,730 | Ordinal forms and linking particle differences |

Not all ODA provinces provide all structured fields — "Addresses Tested" reflects only rows where ODA provides ground truth for that field.

## Tech Stack

| Component | Role |
|-----------|------|
| Java 21 | Minimum version to build and to run |
| ANTLR 4 | Generates the parser at build time; `antlr4-runtime` ships as a dependency |
| SLF4J API | Logging facade shipped as a dependency; you provide the implementation |
| Gradle | Build, through the included wrapper |
| JUnit, AssertJ, Logback | Tests only; nothing ships in the artifact |

Exact versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and
[`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties), where
Dependabot keeps them current.

## Versioning

NorthPost Parser follows [Semantic Versioning](https://semver.org/). Release tags are `vMAJOR.MINOR.PATCH`.

**What the version number covers.** The public API is the `com.guidedbyte.address.model` and `com.guidedbyte.address.service` packages: `AddressParserService` and its nested types, `AddressComponents` with its builder and enums, and `NormalizationStrategy`. The `com.guidedbyte.address.parser` package and the generated ANTLR classes are implementation details and may change in any release.

- **MAJOR**: a change that breaks code compiled against or written for the public API. Removing or renaming a public type, method, constructor, builder field or enum constant counts, and so does changing what a field means (for example, moving the unit number to a different field).
- **MINOR**: new public API, new enum constants, support for new street types or address formats, and parsing improvements that change the output for some inputs. Upgrading needs no code changes, with the enum caveat below.
- **PATCH**: bug fixes, dependency updates for security advisories, and documentation. A patch release is cut automatically when a dependency shipped in the artifact has a security advisory.

**Parse output can change in a minor release.** The parser is validated against millions of real addresses, and improving accuracy means some inputs parse differently after an upgrade. Such changes are listed in the release notes. If your code depends on exact output for specific inputs, pin the minor version and test before upgrading.

**New enum constants are minor, but check exhaustive switches.** Adding a constant to `AddressType`, `ParsingMode`, `ParsingStrategy` or `NormalizationStrategy` does not break compiled code, but a `switch` expression over the enum with no `default` branch stops compiling. Add a `default` branch if you want to take minor upgrades without source changes.

Releases are made when the parser or a shipped dependency changes, not on a schedule. Updates to build and test tooling do not produce a release.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). See the [LICENSE](LICENSE) file for details.
