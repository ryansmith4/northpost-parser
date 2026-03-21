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
    <version>1.0.0</version>
</dependency>
```

## Features

- **Full address decomposition** — addressee, unit, street number/name/type/direction, municipality, province, postal code, and more
- **English and French** — recognizes ~110 English and ~25 French street types, bilingual directionals (N/S/E/W, NORD/SUD/EST/OUEST), and French linking particles (DE, DU, DES, etc.)
- **Smart disambiguation** — French type-first detection (RUE PRINCIPALE), lettered avenues (AVENUE P), opposing direction pairs (EAST WEST RD), French article prefixes (GRANDE ALLEE), type-as-name guard (AVENUE RD → name=AVENUE, type=RD)
- **Preserves what you write** — hyphens (2E-ET-3E), slashes (36/37), ampersands (6 & 10), spaced dashes (RD - RTE 113), and parentheses (DRIVEWAY (THE)) retained in street names
- **All delivery types** — civic addresses, PO boxes (PO BOX / CP / C.P.), rural routes (RR), general delivery (GD), and mixed
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
| Site info | Lines starting with SITE, COMP, or EMPL |
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
| `OdaAddressTest` | Parameterized | 147 curated addresses from Statistics Canada ODA datasets |

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

| Component | Version |
|-----------|---------|
| Java | 21 |
| ANTLR | 4.13.1 |
| Gradle | 9.4.0 |
| JUnit | 5 |
| AssertJ | 3.24.2 |

## License

Licensed under the [Apache License, Version 2.0](LICENSE). See the [LICENSE](LICENSE) file for details.
