# NAR Bulk Validation History

Tracks field-level accuracy changes across releases, measured against the Statistics
Canada National Address Register (NAR). All runs use the full NAR dataset with 100%
parse success rate maintained throughout.

## Hyphen, Slash, and Ampersand Preservation (2026-03-21)

**Changes:**
- Extracted shared `collectStreetWords` helper for `interpretStreetTokens` and
  `interpretStreetOnlyLine`.
- Adjacent hyphen-connected tokens (no whitespace) joined into compounds:
  `2E-ET-3E`, `6E-RANG`, `27-28`. Preserves Quebec ordinal compounds and Ontario
  sideroad identifiers.
- Adjacent slash-connected tokens joined: `36/37`, `PETIT/LITTLE`, `11/17`.
  Fractions and dual civics already consumed upstream, so remaining slashes are
  compound identifiers.
- Ampersand tokens (`&`) preserved in street names instead of being dropped.

**Branch:** `missing-addressee-support`

### Impact (vs prior run)

| Province | streetName prior | streetName new | Fixed |
|----------|-----------------|---------------|-------|
| QC | 12,896 | **6,671** | **-6,225** |
| ON | 27,347 | **24,870** | **-2,477** |
| BC | 5,839 | **5,527** | **-312** |
| NB | 52,654 | **52,525** | **-129** |
| AB | 3,282 | **3,180** | **-102** |
| SK | 2,759 | **2,737** | **-22** |
| NL | 1,710 | **1,690** | **-20** |
| NS | 3,662 | **3,649** | **-13** |
| NU | 2 | **0** | **-2** (100%!) |
| PE | 19,462 | **19,461** | **-1** |
| **Total** | | | **-9,303** |

Zero regressions in streetType, streetDir, or any other field.

### ODA Bulk (9.6M addresses)

All fields identical to v1.1.1 baseline except 1 updated ODA test expectation
(DU 6E-RANG hyphen now preserved). Runtime: 1m 24s.

---

## French Article Prefix Guard (2026-03-21)

**Changes:**
- Extended Rule 1 to treat French article/adjective + French type word as a proper
  name: GRANDE ALLÉE → name=GRANDE ALLÉE (no type), GRAND BOULEVARD → name=GRAND
  BOULEVARD (no type, in QC only).
- For types exclusively French (ALLÉE, CÔTE, RANG, MONTÉE): fires regardless of province.
- For types shared with English (BOULEVARD, PLACE): fires only when province is QC —
  avoids regressing English-province streets like GRAND AVE or LE BLVD.
- Per Commission de toponymie du Québec: these are complete odonyms with no generic.
- Updated ODA test expectations for GRANDE ALLEE and GRAND BOULEVARD to match
  Commission classification.

**Branch:** `missing-addressee-support`

### Impact (vs CSV fix baseline)

| Province | Field | Prior | New | Delta |
|----------|-------|-------|-----|-------|
| QC | streetName | 20,769 | **12,896** | **-7,873** |
| QC | streetType | 767 | 921 | +154 |
| ON | streetName | 27,403 | 27,347 | -56 |
| NB | streetName | 52,668 | 52,654 | -14 |
| All others | all fields | — | — | unchanged |

Zero regressions in any province.

### ODA Bulk (9.6M addresses)

All fields identical to v1.1.1 baseline. Zero regressions. Runtime: 1m 13s.

---

## CSV Fix + Parallel Processing (2026-03-21)

**Changes:**
- Fixed CSV parsing bug in NarBulkValidationTest — `split(",")` replaced with RFC 4180
  `parseCsvLine()` that handles quoted fields. Provinces with quoted CSD names
  (e.g., `"Kings, Subd. A"`) had shifted column indices, causing CSD type codes (SC,
  SNO, FD, NO, T) to be read as street names. This was a **test bug**, not a parser bug.
- Added parallel processing via ThreadPoolExecutor (22 worker threads + CallerRunsPolicy)
- Added `logback-test.xml` to silence DEBUG logging (was dumping ~170M lines to stdout)
- Applied same parallelization to OdaBulkValidationTest

**Performance:** ODA 9.6M in 1m22s, NAR 16.4M in 2m05s. Combined **3m27s** (was ~2 hours).
Heap reduced from 4GB to **1.5GB**.

**Branch:** `missing-addressee-support`

**Note:** Row counts decreased because the CSV fix correctly identifies rows with empty
MAIL_STREET_NAME (previously read from wrong column) and skips them.

### Full Field Accuracy (all provinces)

| Province | Tested | streetNo | unitNo | streetName | streetType | streetDir | city | postalCode |
|----------|--------|----------|--------|------------|------------|-----------|------|------------|
| AB | 1,672,407 | 100.000% | 99.999% | 99.804% | 99.976% | 99.998% | 100.000% | 100.000% |
| BC | 2,247,454 | 100.000% | 99.999% | 99.740% | 99.983% | 100.000% | 100.000% | 100.000% |
| MB | 468,160 | 99.987% | 99.966% | 99.569% | 99.998% | 100.000% | 100.000% | 100.000% |
| NB | 393,749 | 100.000% | 100.000% | 86.624% | 99.690% | 100.000% | 99.136% | 100.000% |
| NL | 152,302 | 99.999% | 99.997% | 98.877% | 99.987% | 100.000% | 100.000% | 100.000% |
| NS | 479,056 | 100.000% | 100.000% | 99.236% | 99.954% | 100.000% | 99.984% | 100.000% |
| NT | 10,245 | 99.815% | 99.487% | 99.571% | 100.000% | 100.000% | 100.000% | 100.000% |
| NU | 2,940 | 100.000% | 100.000% | 99.932% | 100.000% | — | 100.000% | 100.000% |
| ON | 6,009,147 | 99.999% | 99.997% | 99.544% | 99.969% | 99.993% | 99.910% | 100.000% |
| PE | 73,285 | 99.997% | 99.987% | 73.443% | 99.998% | 100.000% | 100.000% | 100.000% |
| QC | 4,524,961 | 100.000% | 99.999% | 99.541% | 99.983% | 99.920% | 100.000% | 100.000% |
| SK | 328,603 | 99.995% | 99.986% | 99.160% | 99.972% | 100.000% | 100.000% | 100.000% |
| YT | 13,513 | 99.985% | 99.962% | 99.623% | 100.000% | 100.000% | 100.000% | 100.000% |

### Key improvements from CSV fix (vs prior run with broken CSV)

| Province | Field | Before | After | Cause |
|----------|-------|--------|-------|-------|
| NS | streetName | 77.51% | 99.24% | CSD type "SC" was read as street name |
| NS | streetType | 76.71% | 99.95% | "SC" was read as street type |
| NS | streetDir | 1.14% | 100.00% | Community names were read as direction |
| NS | postalCode | 78.95% | 100.00% | Municipality was read as postal code |
| NL | streetName | 94.95% | 98.88% | CSD type "SNO" was read as street name |
| NL | streetType | 95.63% | 99.99% | "SNO" was read as street type |
| NL | streetDir | 32.42% | 100.00% | Community names were read as direction |
| ON | streetDir | 97.66% | 99.99% | Shifted columns from "Unorganized" CSD names |
| PE | streetType | 89.85% | 99.998% | CSD type "FD" was read as street type |
| PE | streetDir | 4.62% | 100.00% | Road refs were read as direction |
| MB | streetType | 99.52% | 99.998% | Shifted columns from CSD names |

### ODA Bulk (9.6M addresses)

All fields identical to v1.1.1 baseline. Zero regressions. Runtime: 1m 22s (was ~1 hour).

---

## Lettered Avenue + Type Abbreviations (2026-03-20)

**Changes:** Lettered/numbered avenue detection, French type-as-name guard, direction
guard for 2-word French type patterns, 17 new street type abbreviations (SIDERD, CONC,
TLINE, CIRCT, VILLGE, PATHWAY, PTWAY, DRIVEWAY, DRWY, CTR, POINTE, HIGHLANDS,
HGHLDS, HARBR, CROIS, RLE, SENT).

**Branch:** `missing-addressee-support`

### streetName Accuracy

| Province | Prior % | Prior Mismatches | New % | New Mismatches | Fixed |
|----------|---------|-----------------|-------|----------------|-------|
| SK | 96.052% | 12,980 | 99.110% | 2,925 | -10,055 |
| ON | 98.823% | 70,802 | 99.259% | 44,537 | -26,265 |
| QC | 99.226% | 35,015 | 99.541% | 20,769 | -14,246 |
| AB | 99.651% | 5,785 | 99.804% | 3,285 | -2,500 |
| BC | 99.714% | 6,332 | 99.740% | 5,839 | -493 |
| MB | 99.079% | 4,304 | 99.094% | 4,261 | -43 |
| NB | 86.228% | 54,227 | 86.624% | 52,668 | -1,559 |
| NL | 94.945% | 7,929 | 94.945% | 7,929 | 0 |
| NS | 77.497% | 112,225 | 77.511% | 112,174 | -51 |
| PE | 69.973% | 22,268 | 70.028% | 22,227 | -41 |
| NT | 99.551% | 46 | 99.551% | 46 | 0 |
| NU | 99.932% | 2 | 99.932% | 2 | 0 |
| YT | 98.971% | 140 | 98.971% | 140 | 0 |
| **Total** | | **331,056** | | **275,803** | **-55,253** |

### streetType Accuracy

| Province | Prior % | Prior Mismatches | New % | New Mismatches | Fixed |
|----------|---------|-----------------|-------|----------------|-------|
| SK | 99.865% | 427 | 99.920% | 253 | -174 |
| ON | 99.212% | 46,083 | 99.673% | 19,113 | -26,970 |
| QC | 99.625% | 16,863 | 99.983% | 767 | -16,096 |
| AB | 99.821% | 2,904 | 99.976% | 394 | -2,510 |
| BC | 99.961% | 864 | 99.983% | 371 | -493 |
| MB | 99.507% | 2,294 | 99.516% | 2,251 | -43 |
| NB | 99.237% | 2,626 | 99.690% | 1,067 | -1,559 |
| NL | 95.634% | 6,772 | 95.634% | 6,772 | 0 |
| NS | 76.697% | 110,161 | 76.708% | 110,110 | -51 |
| PE | 89.780% | 5,886 | 89.851% | 5,845 | -41 |
| NT | 99.990% | 1 | 99.990% | 1 | 0 |
| NU | 100.000% | 0 | 100.000% | 0 | 0 |
| YT | 99.726% | 37 | 99.726% | 37 | 0 |
| **Total** | | **194,918** | | **146,981** | **-47,937** |

### Other Fields (unchanged)

streetDirection, streetNumber, unitNumber, city, province, postalCode — no changes
in any province. Zero regressions detected.

### ODA Bulk (9.6M addresses)

All fields identical to v1.1.1 baseline. Zero regressions.

### Key Drivers

| Fix | streetName | streetType | Provinces |
|-----|-----------|-----------|-----------|
| CROIS abbreviation | ~14,200 | ~14,600 | QC, NB |
| SIDERD abbreviation | ~10,600 | ~10,600 | ON |
| Lettered avenue guard | ~9,900 | 0 | SK |
| CONC abbreviation | ~4,300 | ~4,300 | ON |
| VILLGE/CTR/HARBR/POINTE | ~2,500 | ~2,500 | AB |
| TLINE abbreviation | ~2,000 | ~2,000 | ON |
| CIRCT abbreviation | ~1,900 | ~1,900 | ON |
| PTWAY abbreviation | ~1,200 | ~1,200 | ON |
| RLE abbreviation | ~1,200 | ~1,200 | QC, NB |
| SENT abbreviation | ~850 | ~850 | QC |
| Avenue direction guard | ~730 | 0 | ON |
| Type-as-name guard (AVENUE RD) | ~500 | ~500 | ON, SK |
| DRWY abbreviation | ~600 | ~600 | ON |
| BC/MB/NS/PE (misc) | ~628 | ~628 | BC, MB, NS, PE |

---

## v1.1.1 Baseline (2026-03-19)

First full NAR validation run. Baseline numbers are the "Prior" column above.
