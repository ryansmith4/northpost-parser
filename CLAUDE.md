# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**NorthPost Parser** — Canadian postal address parser library built with **ANTLR4** grammars. Parses free-form Canadian address text (English and French) into structured components (addressee, street, municipality, province, postal code, etc.). Pure Java library with no framework dependencies.

## Git Workflow

GitHub is configured to **squash-and-merge** PRs. After a PR merges, use `git branch -D branch` (force delete) to clean up local branches — `git branch -d` will fail because the squashed commit has a different SHA than the branch's original commits.

## Build & Test Commands

```bash
./gradlew build                    # Build (includes ANTLR code generation)
./gradlew test                     # Run all tests
./gradlew generateGrammarSource    # Regenerate ANTLR parser/lexer from .g4 grammars
./gradlew test --tests "com.guidedbyte.address.AddressParserTest"           # Run a single test class
./gradlew test --tests "com.guidedbyte.address.AddressParserTest.CivicAddressTests.shouldParseCompleteCivicAddress"  # Run a single test method (nested class syntax)
./gradlew cleanTest test -PodaBulk -PodaBulkOnly                  # ODA bulk validation (all provinces)
./gradlew cleanTest test -PodaBulk -PodaBulkOnly -PodaProvinces=BC # ODA bulk validation (specific province)
./gradlew refreshNarSample         # Re-record expected output in the NAR regression sample after an intentional parser change
./gradlew drawNarSample            # Redraw the NAR regression sample from nar-data/ (deterministic, seeded)
```

Java 21 or later is required to build. Whichever JDK runs Gradle also compiles and tests; `options.release = 21` in `build.gradle` makes any of them produce Java 21 class files and reject newer APIs. There is no toolchain, so Gradle never looks for or downloads another JDK. Releases are built with JDK 21 (`release.yml`). Always use the wrapper (`./gradlew`); its Gradle version is defined in `gradle/wrapper/gradle-wrapper.properties` and kept current by Dependabot.

## Architecture

### Grammar (`CanadianAddress.g4`)
- Uses `NL` (newline) to separate address lines and `WS` (spaces/tabs only) within lines
- `WORD` token handles accented characters (`\u00C0-\u00FF`, `\u0100-\u017F`), hyphens, apostrophes, dots within words
- `CATCHALL` rule (`. -> skip`) silently discards unrecognized characters (parentheses, etc.)
- Minimal lexer — produces structural tokens (WORD, NUMBER, POSTAL_CODE, etc.); all semantic interpretation is in the visitor

### Visitor (`AddressComponentVisitor`)
- **Anchor-based line classification**: each pre-region line is classified by content anchors (delivery keywords, care-of markers, site info prefixes) rather than ordinal position; lines with no anchors default to addressee (first) or delivery (subsequent)
- Region identification stays positional (last line) per Canada Post convention
- Province detection on the region line scans right-to-left from the postal code, so the province marker nearest the postal code wins (2-letter code tried first at each position, then full names/informal abbreviations). A municipality can itself contain a province code or name (`NIAGARA ON THE LAKE ON L0S 1J0` → municipality=`NIAGARA ON THE LAKE`, province=`ON`); a province that appears only once is unaffected. If the same 2-letter code is written again before the province, the extra copy is left out of the municipality; commas and the postal code may sit between the copies, nothing else (`LANGLEY BC BC`, `LANGLEY, BC, BC V1M 2B3`, `LANGLEY BC V1M 2B3 BC`, `TORONTO ON ONTARIO`). Only a 2-letter code is dropped this way: a province name before the province stays in the municipality, so `QUEBEC QC` keeps municipality=`QUEBEC`. The cost is that a municipality whose name really ended in its own province's code would lose that word; the ODA and NAR files contain none
- Comprehensive street type lookup tables for EN and FR (includes Calgary/Edmonton abbreviations: GD, CA, CX, PS, PZ, LP, AL, HI, GY, GL, PW, CE, LK, GW; common types: PKWY, CREST, BLUFF, SPUR, ESTATE, GATEWAY, WALKWAY, OUTLOOK; NAR-validated: SIDERD, SIDEROAD, CONC, TLINE, CIRCT, VILLGE, PATHWAY, PTWAY, DRIVEWAY, DRWY, CTR, POINTE, HIGHLANDS, HGHLDS, HARBR, CROIS, RLE, SENT)
- French type-first disambiguation (Rule 3: e.g., "RUE PRINCIPALE" vs "123 RUE DES ÉRABLES")
- Lettered/numbered avenue detection: when `AVENUE`/`AVE`/`AV` is followed by a single bare token (letter A-Z, number, or alphanumeric code), keeps the type word in the street name (`AVENUE P` → name=`AVENUE P`, type=`AVENUE`) instead of applying French type-first split. Direction detection is also guarded to prevent consuming the letter as a direction in 2-word patterns (`AVENUE N` → the N is the street letter, not North).
- French type-as-name guard: when a French type-first split would produce a single-word name that is an unambiguous type abbreviation (RD, DR, ST, BLVD, BV, CRES, CRT, CT), falls through to English pattern instead (`AVENUE RD` → name=`AVENUE`, type=`RD`; `PROMENADE DR` → name=`PROMENADE`, type=`DR`). Full words like ACRES, PARK, LANE are excluded — they can be legitimate street names after a French type.
- French article/adjective prefix guard (Rule 1 extension): when the word before a trailing type is a French article or adjective (GRAND, GRANDE, LE, LA, LES) AND the type is French (either French-only or shared EN+FR when province is QC), the type word is part of the proper name (`GRANDE ALLÉE` → name=`GRANDE ALLÉE`, no type; `GRAND BOULEVARD` in QC → name=`GRAND BOULEVARD`, no type). For shared types (BOULEVARD, PLACE), only fires when province is known to be QC — avoids regressing English-province streets like GRAND AVE. Per Commission de toponymie du Québec, these are complete odonyms where the generic is intentionally absent.
- Direction detection (EN and FR including EST, OUEST, NORD, SUD) — suffix direction preferred over prefix when both present (suffix is unambiguous; prefix letter like E/N could be a street name abbreviation)
- Province name normalization — full names ("ONTARIO"), informal abbreviations ("ONT.", "B.C."), and 2-letter codes
- Smart prefix unit parsing: after a unit designator (APT, SUITE, UNIT, APP, etc.), uses look-ahead to distinguish unit values from civic numbers — ALPHANUMERIC tokens are always treated as unit identifiers; WORD and NUMBER tokens are unit values only when a civic number follows; a bare NUMBER with no following civic is treated as the street number itself. **Adjacency-based compound coalescing** detects adjacent NUMBER+SEPARATOR+NUMBER tokens (no whitespace: `1/2`, `405-2`, `1101.1`) and treats the compound as a single unit value when followed by a civic number
- Units written away from the street line. A unit can sit on a line of its own or between commas, not only on the street line:
  - **Unit-only line** (`unitOnlyLine`): a line that holds nothing but units sets the unit and leaves the street fields alone, before or after the street line. It is one or more unit designators each followed by a value (`APT 4`, `APT4`, `SUITE 200 FLOOR 2`, `BLDG 4, UNIT 12`). A floor may be written as a number or ordinal before the floor word (`2ND FLOOR`, `3E ÉTAGE`, `SUITE 100 2ND FLOOR`). The check comes before the look-ahead above, which would take the value as the street number.
  - **Unit value** (`unitValue`): numbers, single letters and codes that contain a digit (`4`, `A`, `4B`, `A-101`, `4-A`, `405-2`, `1/2`, `12 B`). A second number after a space ends the value, so `APT 6 1236 4E-ET-5E-RANG` stays a street line. The length of a value is not checked, so a telephone number written after a unit word comes back as the unit (`OFFICE 416-555-1234`); the street is left alone.
  - **Penthouse units keep `PH`** (`unitAsRecorded`): a unit word is normally not part of the unit (`APT 12` gives 12), but penthouse 12 is a different unit from unit 12, so `PH 12`, `PH12`, `PH. 12` and `PENTHOUSE 12` all give `PH12`, on the street line or on a line of their own. The register writes penthouse units the same way (`PH04`). A value that already starts with `PH` is not doubled (`APT PH04`, `PENTHOUSE PH2`). A unit word written in front of another only introduces it, on the street line as on a line of its own: `UNIT PH 4` gives `PH4` and `APT FLOOR 2` gives 2 (`introducesSecondUnitWord` decides this for a line that starts that way). A comma at the end of the line and a period at the end of the value are ignored.
  - **Word values** (`isUnitValueWord`): only after `APT`, `APARTMENT`, `UNIT`, `SUITE`, `APP` or `APPARTEMENT`, and only words the National Address Register uses as units (`BSMT`, `UPPR`, `LWR`, `REAR`, `MAIN`, `FRONT`, ...), `BSMT`/`UPPR`/`LWR`/`REAR` with a compass letter (`BSMTW`), a repeated letter (`AA`), or another unit word (`UNIT PH`). Other words are not units: `APARTMENT THERAPY`, `SUITE DREAMS`, `STE ANNE`. The register also writes `SIDE` and `MAIN` with a compass letter (`MAINW`, 34 addresses); those are left out so that `MAINE` stays an ordinary word.
  - **Which unit is kept** when an address gives more than one (`recordUnitFromOwnLine`, `recordUnitFromStreetLine`), in order: a unit proper on the street line (`123 MAIN ST APT 7`, `5-123 MAIN ST`); a unit proper on a unit-only line; a floor, building, tower or department on the street line (`123 MAIN ST FLOOR 2`); one of those on a unit-only line. Of two of the same kind on unit-only lines the first is kept.
  - **A line that starts with a unit designator but holds more than units** is read as a street line, as it always was, unless two things hold. First, another line before the region line is clearly the street (`isNumberedStreetLine`: it starts with a street number, not an ordinal, and has a street type, as `123 MAIN ST` does). Second, the line gives no street number of its own after its unit (`hasStreetNumberAfterLeadingUnit`). Then the unit it starts with is kept (`APT 3 BSMT` gives 3, `SUITE 3400 FIRST CANADIAN PLACE` gives 3400) and the street fields are left alone; if it starts with no unit (`TOWER RD`, `OFFICE PARK`) the line goes to care-of, where the parser keeps lines it cannot place.
    - A line with a unit, then a space or a comma, then a street number and a word (`UNIT 4 250 KING ST W`, `UNIT B 2200 KINGSWAY`) is always read as a street line. The reason: a business name can pass for the street (`24 HOUR PET CENTRE`, `3M CANADA CO`), because the street-type list holds words that names use, and the real street line below it must not be thrown away.
    - Not a clear street line: a name that starts with a number and has no street-type word (`3M CANADA`, `1234567 ONTARIO INC`), a line that starts with an ordinal (`1ST CHOICE AUTO CENTRE`), and a street written without a type (`2200 KINGSWAY`).
    - What is a street number (`isStreetNumberToken`): a number, with letters or not (`123A`, `20E`), but not an ordinal (`2ND`, `1ER`, `3EME`). A number with the single letter E is an ordinal only when a floor word or a street type comes next (`3E ÉTAGE`, `12E AVENUE`); the register has many street numbers that end in E.
  - **Single line with commas** (`tryCommaSplit`): a unit segment before or after the street becomes a unit-only line (`123 MAIN ST, APT 4, TORONTO ON`; `APT A, 123 MAIN ST, TORONTO ON`). A unit at the start that has no plain number (`APT A`, `2ND FLOOR`) is passed over only when what follows it starts like a delivery line (a number, `PO BOX`, another unit), so `2ND FLOOR, TORONTO, ON M5V 2Y7` gives unit 2 and municipality `TORONTO`. If that reading finds no place to split, the line is read again without passing over the unit, so `APT A, 123, RUE PRINCIPALE TORONTO ON M5V 2Y7` keeps its province and postal code as before. A unit with a plain number (`APT 4`) is always passed over, by the older rule for the French comma after a number.
  - **Not handled:**
    - `#4`: the lexer drops `#`, so it is a bare 4 and replaces the street number.
    - A floor in words: `SECOND FLOOR` goes to care-of.
    - `APT 394` above `QUEEN ST` gives unit 394 and no street number.
    - A mixed line beside a street written without a type (`2200 KINGSWAY` then `APT 3 BSMT`) still replaces the street.
    - Under a business name that reads like a street, a unit word and one number is a unit: `24 HOUR PET CENTRE` then `APT 394 QUEEN ST` gives unit 394 and the name as the street. It cannot be told from `100 KING ST W` then `SUITE 3400 FIRST CANADIAN PLACE`.
    - Two street lines (`123 MAIN ST` then `UNIT 4 250 KING ST W`): the later one is kept, as before. The same goes for any unit-word line with two numbers and a word, street or not: `RM 204 3 WEST WING` and `PH 416 555 1234 EXT 5` replace the street.
    - A street number joined to the unit or after a two-part unit (`UNIT 4-250 KING ST W`, `UNIT 12 B 250 KING ST W`) does not make the line a street line, so under a business name that reads like a street the line gives only its unit.
    - A numbered company name is read as unit and number (`9123-4567 QUÉBEC INC` gives unit 9123), and that unit is kept over a unit line (`APP 4B`).
    - Two unit words, two numbers and then a street type at the start of a line: `UNIT PH 4 123 RUE DUPONT` gives unit `PH` and street number 4, as before. The register writes unit `PH` at `123 4 AVE SW` in the same shape (`APT PH 123 4 AVE SW`), and that reading is kept.
    - A telephone number after a unit word is read as the unit (`OFFICE 416-555-1234` gives 416-555-1234, `PH 4165551234` gives PH4165551234).
    - Text after the unit on a unit line is discarded when another line is clearly the street: `APT 204 BUZZER 1234` gives unit 204 and the buzzer code is lost, and `SUITE 3400 FIRST CANADIAN PLACE` loses the building name. The result has no field for telephone numbers, buzzer codes or notes; a telephone or buzzer line after the region line is taken for the region line.
    - A name before the street on a single line (`JOHN SMITH, 123 MAIN ST, TORONTO ON`).
    - A unit and the region alone on a single line: `APT A, TORONTO ON M5V 2Y7` puts the unit in the municipality; `APT 4, TORONTO ON M5V 2Y7` puts the whole line in the addressee and returns no province or postal code.
    - On a single line, a segment after a leading unit that does not start like a delivery line joins the municipality (`2ND FLOOR, MAIN ST, TORONTO ON` gives unit 2 and municipality `MAIN ST TORONTO`).
- Handles informal patterns: fractional street numbers (123 1/2), dual civics (123/125), concatenated units (APT5), trailing units (123 MAIN ST APT 5), unit on its own line, postal code on own line, comma-separated single-line addresses

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
- `OdaAddressTest` — parameterized test using about 150 curated addresses from `oda_test_addresses.csv` (pipe-delimited, `\n` literals for line breaks); expectations were curated against ODA ground truth, empty name/unit/postal code means "not checked"
- `NarSampleRegressionTest` — replays `nar_sample_addresses.csv` (about 2,400 NAR addresses, all 13 provinces/territories, hard-case sections first; same format as the ODA file) and requires every field to equal the parser's recorded output. One test per province so the build stays fast. The file is a behaviour snapshot and the conformance target for ports to other languages: after an intentional parser change run `./gradlew refreshNarSample` and review the diff; `./gradlew drawNarSample` redraws the sample from `nar-data/` (`NarSampleGenerator`, seeded hash ranking, stratified by province × shape plus hard-case strata)
- `LayoutAddressTest` — checks `layout_test_addresses.csv`, hand-written invented addresses that vary the layout (where the unit sits, which lines are present). The bulk tests build every address in one layout, so layout bugs pass them; add a row here when one is found. Same format as the ODA file, but a person wrote the expected values and every column is compared exactly, so an empty column means the field must be empty. A layout that is known to be wrong today stays out of the file and is recorded as a "Known limit" test in `AddressParserTest`
- `NarUnitLayoutTest` — takes the roughly 1,100 NAR sample addresses that have a unit, moves the unit somewhere else (its own line before or after the street, with `UNIT` for `APT`, with a comma at the end of each line, or the whole address on one line with commas) and requires the fields recorded for the original. It gives layout changes a wide range of real unit values and street names. The street fields it expects are the parser's recorded output; the unit it expects is the one written in the address
- `AddressFixtures` reads/writes the shared pipe-delimited fixture format; `NarCsv` turns NAR CSV rows into address text for both the bulk test and the generator

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

Versions live in `gradle/libs.versions.toml` (the Gradle version catalog) and are kept current by Dependabot, so they are not repeated here.

- **ANTLR** — grammar tool at build time and `antlr4-runtime` as an `api` dependency; both share one version entry
- **SLF4J API** — logging facade exposed as an `api` dependency; consumers provide their own implementation
- Everything else (JUnit, AssertJ, Logback, Spotless, Qodana, publishing plugins) is test or build tooling and does not ship in the artifact

## Releases

- **Manual release**: publish a GitHub release with a `vX.Y.Z` tag. `release.yml` publishes to Maven Central (version derived from the tag by axion-release) and opens an auto-merging PR that updates the README version snippets. It publishes only when the repository owner published the release or started the run, and each publish waits for the owner's approval of the `maven-central` environment. The five publishing and signing secrets are stored in that environment and not at repository level, so a copy of the workflow on an older commit, which names no environment, receives none of them. The environment and its required reviewer are repository settings, not files in git. That README PR is still merged with `GITHUB_TOKEN`, so its merge runs no CI on main; the first pull requests after a release see a slow `dependency-review` until another merge lands.
- **Nothing is released automatically.** Every release is published by hand.
- **Security fixes get a reminder**: Dependabot security updates to runtime dependencies (ANTLR runtime, SLF4J API) arrive via the `runtime-security` group in `.github/dependabot.yml` and merge like other updates. When one merges, the `release-reminder` job in `ci.yml` opens a `release-needed` issue assigned to the repository owner, because Maven Central serves the vulnerable version until a release is published. If that issue is still open when the next one merges, the job comments on it instead of opening another: one release covers them all. Close the issue after releasing. A security fix merged from any other branch (for example an ordinary runtime update merged by hand) gets no reminder.
- **What Dependabot merges by itself**: minor and patch updates to test dependencies, build tooling and GitHub Actions, and minor and patch security updates to the runtime dependencies, once the required `Java 21` / `Java 25` checks pass (the requirement lives in the `main protection` ruleset).
- **What waits for a manual merge**: a pull request containing a major update or an update whose size cannot be worked out, an ordinary (non-security) update to a runtime dependency, and any update that edits `dependabot-auto-merge.yml` itself. A major update to a runtime dependency is released as at least a minor version (see the README's Versioning section).
- **Where runtime dependencies are listed**: in three places. `shippedLibraries` in `build.gradle` is the exact list (`org.antlr:antlr4-runtime`, `org.slf4j:slf4j-api`); the labelling step of `dependabot-auto-merge.yml` and the `runtime-security` group of `dependabot.yml` list their groups (`org.antlr`, `org.slf4j`). The `checkRuntimeDependencies` task, part of `./gradlew check`, fails the build if anything else would be published as a dependency (the ANTLR grammar tool, for example), if a listed library is no longer published, or if one of those groups is missing from either file.
- **Why the merge uses a GitHub App**: GitHub starts no workflow for a push made with `GITHUB_TOKEN`, so CI on main, including `release-reminder`, would never run for an auto-merged PR. The app's client ID and private key are the Dependabot secrets `AUTOMERGE_APP_CLIENT_ID` / `AUTOMERGE_APP_PRIVATE_KEY`. The key can write to the repository and merge its own pull requests, so guard it like the release secrets.
- **Fork pull requests** skip the `dependency-review` job (their token is read-only); review their dependency changes by hand.
- **Failed runs that nobody started by hand**: GitHub emails a failed run only to whoever started it, so a run started by Dependabot or by the auto-merge app reaches nobody. For those, the `report-failure` job in `ci.yml` opens a `ci-failure` issue assigned to the repository owner, or comments on the one already open, and the auto-merge job comments on its pull request, mentioning the owner. The usual cause is a download from a package site that failed; the issue gives the command to run the failed jobs again. Close the issue once it is dealt with.
- **Approving a publish**: approve the `maven-central` environment only for a run you started. A release published by anyone else skips the publish job before approval is asked, so a request for a run you did not start means the workflow was changed.
