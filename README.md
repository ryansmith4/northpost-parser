<p align="center">
  <img src="docs/logo.png" alt="NorthPost Parser" width="200">
</p>

<h1 align="center">NorthPost Parser</h1>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.guidedbyte/northpost-parser"><img src="https://img.shields.io/maven-central/v/com.guidedbyte/northpost-parser" alt="Maven Central"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
</p>

A Canadian postal address parser that converts free-form address text into structured components. Built with an [ANTLR4](https://www.antlr.org/) grammar, NorthPost Parser handles the full range of Canadian addressing formats — civic, PO box, rural route, and general delivery — in both English and French.

Pure Java library with no framework dependencies. Validated against **9.6 million+** [Statistics Canada ODA](https://www.statcan.gc.ca/en/lode/databases/oda) addresses with a **100% parse success rate** and **high field-level accuracy**.

## Installation

Replace `VERSION` with the latest release shown in the Maven Central badge above.

### Gradle

```groovy
implementation 'com.guidedbyte:northpost-parser:VERSION'
```

Kotlin DSL:

```kotlin
implementation("com.guidedbyte:northpost-parser:VERSION")
```

### Maven

```xml
<dependency>
    <groupId>com.guidedbyte</groupId>
    <artifactId>northpost-parser</artifactId>
    <version>VERSION</version>
</dependency>
```

## Features

- **Full address decomposition** — addressee, unit, street number/name/type/direction, municipality, province, postal code, and more
- **English and French** — recognizes ~90 English and ~20 French street types, bilingual directionals (N/S/E/W, NORD/SUD/EST/OUEST), and French linking particles (DE, DU, DES, etc.)
- **All delivery types** — civic addresses, PO boxes (PO BOX / CP / C.P.), rural routes (RR), general delivery (GD), and mixed
- **Flexible input** — handles multi-line (Canada Post format), single-line with commas, lowercase, extra whitespace, accented characters, and various abbreviations
- **Three parsing modes** — `LENIENT` (extract what's available), `STRICT` (reject incomplete addresses), and `AUTO` (try strict, fall back to lenient)
- **Zero framework dependencies** — depends only on ANTLR runtime and SLF4J
- **All 13 provinces and territories** supported with full-name, abbreviated, and 2-letter code recognition

## Quick Start

### Prerequisites

- **Java 21** or later
- **Gradle 8.x** (wrapper included — no separate install needed)

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
  5-456 ELM AVE                  CP 1234 SUCC CENTRE-VILLE
  456 ELM AVE APT 5
  456 ELM AVE APT5             Rural Route:
                                  RR 2 STN MAIN
Fractional civic:
  123 1/2 MAIN STREET          General Delivery:
                                  GD STN MAIN
Dual civic:
  10/12 MAIN STREET            Care-of:
                                  C/O JANE DOE
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

Following Canada Post conventions:

| Line | Interpretation |
|------|----------------|
| First | Addressee (person or organization name) |
| Last | Region (municipality, province, postal code) |
| Middle | Delivery information (street address, PO box, care-of, etc.) |

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
| `AddressParserTest` | Unit | 100+ tests across 14 categories — civic, French, PO box, rural, edge cases, validation, batch, performance |
| `OdaAddressTest` | Parameterized | 147 curated addresses from Statistics Canada ODA datasets |

### ODA Bulk Validation

The parser can be validated against full [Statistics Canada ODA](https://www.statcan.gc.ca/en/lode/databases/oda) datasets (9.6M+ addresses). Download the ODA zip files and place them in `oda-data/`:

```bash
# Validate against all provinces
./gradlew cleanTest test -PodaBulk -PodaBulkOnly

# Validate specific provinces
./gradlew cleanTest test -PodaBulk -PodaBulkOnly -PodaProvinces=BC,QC
```

Reports are written to `build/reports/oda-bulk/`, including a mismatch CSV for each province for detailed analysis.

### Accuracy

Validated against 9,603,114 addresses across 10 provinces/territories from the Statistics Canada ODA dataset. Field accuracy is measured by case-insensitive comparison against ODA's structured ground truth fields.

| Field | Accuracy | Addresses Tested | Notes |
|-------|----------|-----------------|-------|
| Parse success | **100.00%** | 9,603,114 | Every address parsed without errors |
| Province | **100.00%** | 9,603,114 | |
| Postal code | **99.78%** | 556,943 | Mismatches are malformed codes in ODA data |
| City | **99.49%** | 9,279,034 | ODA uses underscores in multi-word names |
| Street number | **99.13%** | 9,603,114 | ODA includes parentheses/annotations the parser strips |
| Street type | **96.49%** | 5,547,130 | Remaining gap is normalization (full vs abbreviated forms) |
| Street direction | **95.67%** | 767,869 | Remaining gap is normalization (O vs OUEST, E vs EAST) |
| Street name | **93.83%** | 6,076,730 | Ordinal forms and French linking particle differences |

**Important context:** Many reported "mismatches" are normalization differences between the ODA ground truth and the parser's output, not parsing errors. For example:
- **Street number**: ODA records `(3195)`, parser correctly extracts `3195`
- **Street name**: ODA stores `TWELFTH`, parser preserves the input form `12TH` — both are correct
- **City**: ODA uses `NORTH_COWICHAN_MUNICIPALITY`, parser outputs `NORTH COWICHAN MUNICIPALITY`
- **Postal code**: ODA contains invalid formats like `VE3 3S3` that don't match the A1A 1A1 pattern

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
