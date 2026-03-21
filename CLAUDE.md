# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**NorthPost Parser** — Canadian postal address parser library built with **ANTLR4** grammars. Parses free-form Canadian address text (English and French) into structured components (addressee, street, municipality, province, postal code, etc.). Pure Java library with no framework dependencies.

## Build & Test Commands

```bash
./gradlew build                    # Build (includes ANTLR code generation)
./gradlew test                     # Run all tests
./gradlew generateGrammarSource    # Regenerate ANTLR parser/lexer from .g4 grammars
./gradlew test --tests "com.guidedbyte.address.AddressParserTest"           # Run a single test class
./gradlew test --tests "com.guidedbyte.address.AddressParserTest.CivicAddressTests.shouldParseCompleteCivicAddress"  # Run a single test method (nested class syntax)
./gradlew cleanTest test -PodaBulk -PodaBulkOnly                  # ODA bulk validation (all provinces)
./gradlew cleanTest test -PodaBulk -PodaBulkOnly -PodaProvinces=BC # ODA bulk validation (specific province)
```

Java 21 required. Gradle 9.4.0 (wrapper included).

## Architecture

### Grammar (`CanadianAddress.g4`)
- Uses `NL` (newline) to separate address lines and `WS` (spaces/tabs only) within lines
- `WORD` token handles accented characters (`\u00C0-\u00FF`, `\u0100-\u017F`), hyphens, apostrophes, dots within words
- `CATCHALL` rule (`. -> skip`) silently discards unrecognized characters (parentheses, etc.)
- Minimal lexer — produces structural tokens (WORD, NUMBER, POSTAL_CODE, etc.); all semantic interpretation is in the visitor

### Visitor (`AddressComponentVisitor`)
- **Anchor-based line classification**: each pre-region line is classified by content anchors (delivery keywords, care-of markers, site info prefixes) rather than ordinal position; lines with no anchors default to addressee (first) or delivery (subsequent)
- Region identification stays positional (last line) per Canada Post convention
- Comprehensive street type lookup tables for EN and FR (includes Calgary/Edmonton abbreviations: GD, CA, CX, PS, PZ, LP, AL, HI, GY, GL, PW, CE, LK, GW; common types: PKWY, CREST, BLUFF, SPUR, ESTATE, GATEWAY, WALKWAY, OUTLOOK; NAR-validated: SIDERD, SIDEROAD, CONC, TLINE, CIRCT, VILLGE, PATHWAY, PTWAY, DRIVEWAY, DRWY, CTR, POINTE, HIGHLANDS, HGHLDS, HARBR, CROIS, RLE, SENT)
- French type-first disambiguation (Rule 3: e.g., "RUE PRINCIPALE" vs "123 RUE DES ÉRABLES")
- Lettered/numbered avenue detection: when `AVENUE`/`AVE`/`AV` is followed by a single bare token (letter A-Z, number, or alphanumeric code), keeps the type word in the street name (`AVENUE P` → name=`AVENUE P`, type=`AVENUE`) instead of applying French type-first split. Direction detection is also guarded to prevent consuming the letter as a direction in 2-word patterns (`AVENUE N` → the N is the street letter, not North).
- French type-as-name guard: when a French type-first split would produce a single-word name that is an unambiguous type abbreviation (RD, DR, ST, BLVD, BV, CRES, CRT, CT), falls through to English pattern instead (`AVENUE RD` → name=`AVENUE`, type=`RD`; `PROMENADE DR` → name=`PROMENADE`, type=`DR`). Full words like ACRES, PARK, LANE are excluded — they can be legitimate street names after a French type.
- French article/adjective prefix guard (Rule 1 extension): when the word before a trailing type is a French article or adjective (GRAND, GRANDE, LE, LA, LES) AND the type is French (either French-only or shared EN+FR when province is QC), the type word is part of the proper name (`GRANDE ALLÉE` → name=`GRANDE ALLÉE`, no type; `GRAND BOULEVARD` in QC → name=`GRAND BOULEVARD`, no type). For shared types (BOULEVARD, PLACE), only fires when province is known to be QC — avoids regressing English-province streets like GRAND AVE. Per Commission de toponymie du Québec, these are complete odonyms where the generic is intentionally absent.
- Direction detection (EN and FR including EST, OUEST, NORD, SUD) — suffix direction preferred over prefix when both present (suffix is unambiguous; prefix letter like E/N could be a street name abbreviation)
- Province name normalization — full names ("ONTARIO"), informal abbreviations ("ONT.", "B.C."), and 2-letter codes
- Smart prefix unit parsing: after a unit designator (APT, SUITE, UNIT, APP, etc.), uses look-ahead to distinguish unit values from civic numbers — ALPHANUMERIC tokens are always treated as unit identifiers; WORD and NUMBER tokens are unit values only when a civic number follows; a bare NUMBER with no following civic is treated as the street number itself. **Adjacency-based compound coalescing** detects adjacent NUMBER+SEPARATOR+NUMBER tokens (no whitespace: `1/2`, `405-2`, `1101.1`) and treats the compound as a single unit value when followed by a civic number
- Handles informal patterns: fractional street numbers (123 1/2), dual civics (123/125), concatenated units (APT5), trailing units (123 MAIN ST APT 5), postal code on own line, comma-separated single-line addresses

### Service (`AddressParserService`)
- Plain Java class — instantiate directly with `new AddressParserService()`
- Normalizes input to uppercase before parsing
- `ParsingStrategy` enum: `LENIENT` (always succeeds), `STRICT` (rejects incomplete addresses), `STRICT_THEN_LENIENT` (tries strict, falls back to lenient)
- Validated against 9.6M+ Statistics Canada ODA addresses with 100% parse success

### Model (`AddressComponents`)
- Immutable record with Builder. Fields are normalized to uppercase and null-safe (empty string)
- `AddressType` enum: CIVIC, POSTAL_BOX, RURAL_ROUTE, GENERAL_DELIVERY, MIXED, INCOMPLETE
- `ParsingMode` enum: STRICT, LENIENT

### ANTLR Code Generation
- Grammar file lives in `src/main/antlr/`
- Generated code goes to `build/generated-src/antlr/main/com/guidedbyte/address/parser/`
- Generated classes: `CanadianAddressLexer`, `CanadianAddressParser`, `CanadianAddressBaseVisitor`
- `compileJava` depends on `generateGrammarSource` — grammar changes are picked up automatically on build

## Testing

- JUnit 6 with AssertJ assertions
- Tests instantiate services directly (no framework context)
- `AddressParserTest` — unit tests for parser service (civic, French, PO box, rural, general delivery, strict/lenient strategies)
- `OdaAddressTest` — parameterized test using 147 curated addresses from `oda_test_addresses.csv` (pipe-delimited, `\n` literals for line breaks)

### ODA Bulk Validation

Bulk test (`OdaBulkValidationTest`) validates the parser against full Statistics Canada ODA datasets. Tagged `oda-bulk`, excluded from normal builds. Checks field-level accuracy (street number, name, type, direction, city, province, postal code) against ODA ground truth. Writes per-province reports and mismatch CSVs to `build/reports/oda-bulk/`. Validated against 9.6M+ addresses with 100% parse success and high field-level accuracy.

```bash
# Run against all ODA zips in oda-data/
./gradlew cleanTest test -PodaBulk -PodaBulkOnly

# Run against specific province(s)
./gradlew cleanTest test -PodaBulk -PodaBulkOnly -PodaProvinces=BC,QC
```

**Setup:** Download ODA zip files from [Statistics Canada ODA](https://www.statcan.gc.ca/en/lode/databases/oda) and place in `oda-data/` (gitignored). The test reads CSVs directly from the zip files — no unzipping needed. Reports are written to `build/reports/oda-bulk/ODA_XX_report.txt`.

### NAR Bulk Validation

Bulk test (`NarBulkValidationTest`) validates the parser against the Statistics Canada National Address Register (NAR). Tagged `nar-bulk`, excluded from normal builds. Tests all fields including **unit numbers** (not available in ODA). Uses MAIL_* fields (mailing-format addresses). NAR provides 17.3M+ addresses nationally with postal codes for all provinces.

```bash
# Run against all provinces in NAR zip
./gradlew cleanTest test -PnarBulk -PnarBulkOnly

# Run against specific province(s) — use 2-letter codes
./gradlew cleanTest test -PnarBulk -PnarBulkOnly -PnarProvinces=AB,BC
```

**Setup:** Download a NAR zip from [Statistics Canada NAR](https://www150.statcan.gc.ca/n1/pub/46-26-0002/462600022022001-eng.htm), rename it to `NAR_YYYYMM.zip` (e.g., `NAR_202512.zip`), and place in `nar-data/` (gitignored). The zip contains CSVs organized by numeric province code (e.g., `Address_35.csv` = Ontario) which the test maps automatically. Reports are written to `build/reports/nar-bulk/NAR_XX_report.txt`.

## Key Dependencies

- ANTLR 4.13.1 (grammar + runtime)
- SLF4J 2.0.9 for logging (consumers provide their own implementation)
