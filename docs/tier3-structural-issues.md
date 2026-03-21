# Tier 3: Structural Address Pattern Issues

Analysis of NAR mismatch patterns that require dedicated investigation beyond simple
street type table additions or guard logic.

Generated: 2026-03-20
Updated: 2026-03-21

> **RESOLVED (2026-03-21):** Issues #1, #3, #4, #5, and #6 were caused by a CSV
> parsing bug in the test, not parser limitations or bad NAR data. The test used
> `split(",")` which doesn't handle quoted fields. NAR CSD names like
> `"Kings, Subd. A"` shifted column indices, causing CSD type codes (SC, SNO, FD, NO,
> T) to be read as street names. Fixed by implementing RFC 4180 `parseCsvLine()`.
> Only issues #2 (ROUTE+number) and #7 (GRANDE/LE) remain as genuine parser questions.

## 1. ~~Nova Scotia — SC Pattern (96,481 mismatches)~~ RESOLVED — CSV parsing bug

**Impact:** streetName 77.5%, streetType 76.7%, streetDir 1.1% — NS has the worst
field-level accuracy of any province, driven almost entirely by this pattern.

**Pattern:** `NUMBER SC SC [COMMUNITY_NAME]`
```
475 SC SC LAKESIDE
62 SC SC
3929 SC SC
12280 SC SC 1
```

**What SC means:** SC appears to be a Nova Scotia-specific rural addressing prefix
(possibly "Section" or "Service Community"). It appears doubled ("SC SC") in the NAR
MAIL_STREET_NAME field, and NAR classifies type=SC, name=SC, with the community name
in the direction field.

**Why it's hard:**
- SC appears twice — as both the type and the name. The parser would need special-case
  logic to handle the duplication.
- Community names after "SC SC" are classified as directions by NAR, which is
  non-standard.
- Adding SC as a street type alone would fix type detection (~96K) but would NOT fix
  the name duplication or direction assignment.
- Needs investigation into whether these addresses have a stable pattern or vary.

**Recommended approach:** Add SC as a recognized street type (fixes type accuracy),
then investigate whether a "doubled type" detection pattern could extract the community
name. Run a targeted SC-only analysis to understand the full range of variants.

**Risk:** Low for adding SC as type. Medium for the name/direction extraction — needs
QC impact assessment since SC could theoretically appear in French addresses (unlikely).

---

## 2. New Brunswick — ROUTE + Number (49,114 mismatches) — NAR data inconsistency

**Impact:** streetName 86.6% in NB — the largest single accuracy gap for any province,
driven almost entirely by ROUTE (49,114 of 52,668 streetName mismatches).

**Pattern:** `NUMBER ROUTE NNN`
```
781 ROUTE 465
3111 ROUTE 132
11545 ROUTE 11
4384 ROUTE 640
```

**Current behavior:** French type-first rule fires: type=ROUTE, name=465. NAR expects
name=ROUTE 465 with no type.

### Research findings (2026-03-21)

**Canada Post confirms ROUTE is a street type** with official abbreviation **RTE**
(source: Canada Post street type abbreviation table). The correct Canada Post
decomposition of "781 ROUTE 465" is: civic=781, name=465, type=ROUTE.

**Raw NAR data reveals an inconsistency within NB itself:**

| Source | MAIL_STREET_NAME | MAIL_STREET_TYPE | Count |
|--------|-----------------|------------------|-------|
| NB (majority) | `ROUTE 465` | _(empty)_ | 49,113 |
| NB (minority) | `102` | `RTE` | 385 |
| QC (all) | `132` | `RTE` | consistent |

NB's data provider stores "ROUTE 465" as the entire street name with no type for 99.2%
of route addresses, but correctly splits the remaining 0.8% (name=number, type=RTE).
QC consistently splits them per Canada Post convention.

**Our parser follows Canada Post convention**, which matches:
- Canada Post's official street type table (ROUTE/RTE is a recognized type)
- QC's NAR classification (name=number, type=RTE)
- The 385 NB addresses that DO split correctly
- ODA ground truth for all provinces

**Conclusion:** This is a **NAR data quality issue**, not a parser bug. The NB data
provider chose to store the unsplit form ("ROUTE 465" as full name, no type) for the
majority of route addresses, which conflicts with Canada Post's own addressing standard
and with how the same pattern is classified in QC and in 385 other NB addresses.

**Decision: leave as-is.** Changing the parser to match NB's unsplit classification
would break correctness for QC, for the 385 correctly-split NB addresses, and for ODA
validation. The parser's decomposition (name=465, type=ROUTE) is more useful than
storing the unsplit form because it actually separates the components.

**References:**
- Canada Post street type table confirms ROUTE/RTE as recognized type
- NAR User Guide states MAIL_* fields "follow Canada Post's addressing guidelines"
- NB's own data contradicts this for 49,113 route addresses

---

## 3. ~~Newfoundland — SNO Pattern (5,895 mismatches)~~ RESOLVED — CSV parsing bug

**Impact:** streetName 94.9%, streetType 95.6%, postalCode 96.0%

**Pattern:** `NUMBER SNO SNO [COMMUNITY]`
```
33 SNO SNO
11 SNO SNO
9 SNO SNO BLUNDONS
```

**What SNO means:** SNO appears to be a Newfoundland-specific rural identifier
(possibly "Station Number Organization" or similar). Like NS's SC pattern, it appears
doubled.

**Why it's hard:**
- Same duplication issue as SC — both type and name are "SNO".
- Community names (BLUNDONS, etc.) appear after the doubled SNO.
- NL also has a severe postal code accuracy issue (96.0%) that may be related —
  many SNO addresses lack postal codes in the NAR data.
- NL direction accuracy is 32.4% — SNO addresses store community names in the
  direction field per NAR, similar to NS's SC pattern.

**Recommended approach:** Investigate jointly with the NS SC pattern, as they share
the "doubled type" structural issue. Adding SNO as a street type would be a first step.

**Risk:** Low for adding SNO as type. Moderate for full fix — same structural
complexity as SC.

---

## 4. ~~Prince Edward Island — FD Pattern (4,844 mismatches)~~ RESOLVED — CSV parsing bug

**Impact:** streetName 70.0%, streetType 89.8%, streetDir 4.6%

**Pattern:** `NUMBER FD FD [ROAD_NAME] - RTE NN`
```
3849 FD FD FORT AUGUSTUS RD - RTE 21
```

**What FD means:** FD appears to be "Fire District" — a PE-specific rural addressing
system. Like SC and SNO, it appears doubled.

**Why it's hard:**
- Same doubled-type pattern as SC/SNO.
- PE addresses frequently include "RD - RTE NN" suffixes (road name + route number
  separated by a hyphen), which the parser doesn't model.
- The hyphen-dash between road name and route creates ambiguity with the unit-hyphen
  pattern.
- PE direction accuracy is 4.6% — NAR stores route information in the direction field.
- PE also has RTE+number mismatches (same ROUTE issue as NB but with RTE abbreviation).

**Recommended approach:** Add FD as a street type. The "RD - RTE NN" suffix pattern
needs separate investigation — it's a PE convention where a street has both a name and
a route number.

**Risk:** Low for adding FD. Medium for the route suffix pattern.

---

## 5. ~~Multi-Province — NO NO Pattern (~6,762 mismatches)~~ RESOLVED — CSV parsing bug

**Impact:** ON (4,483), MB (2,076), SK (166), YT (37)

**Pattern:** `NUMBER NO NO [COMMUNITY/DESCRIPTION]`
```
73 NO NO
536 NO NO
171 NO NO FOURTH
APT E 13932 NO NO GILBRIDE
```

**What NO means:** NO appears to be "Number Organization" or similar — a placeholder
for streets with no official name. NAR classifies type=NO, name=NO.

**Why it's hard:**
- NO is 2 letters and could conflict with other uses (e.g., "NO" as a word in a street
  name, or abbreviated direction).
- The pattern is structural: both type and name are "NO" (same duplication as SC/SNO).
- Community/area names after "NO NO" are classified as directions by NAR.
- Some addresses have additional context ("NO NO FOURTH", "NO NO GILBRIDE") that
  the parser would need to classify correctly.
- Many NO NO addresses lack region information (no postal code or province) in the NAR
  data, causing cascade mismatches in postal code and city fields.

**Recommended approach:** Do NOT add NO as a street type — it would cause widespread
false positives. Instead, investigate whether "NO NO" can be detected as a specific
pattern (two consecutive "NO" tokens) and handled as a special case.

**Risk:** High — NO is too common a word to add as a street type. Special-case pattern
detection is needed.

---

## 6. ~~Yukon/NT — LOT/BLOCK Descriptors (140+ mismatches)~~ RESOLVED — CSV parsing bug

**Impact:** YT streetNo 99.3%, unitNo 98.3%; NT streetNo 99.8%, unitNo 99.4%

**Pattern:** `APT LOT#N BLOCK#N NUMBER STREET`
```
APT LOT#7 BLOCK#5 512 ROBIN RD
APT LOT#8 BLOCK#22 216 NORTHERN LIGHTS ST
```

**Also:** YT has narrative property descriptions in quoted fields:
```
APT "BOREAL LOT 38 RD"145 NO
APT "109-SIMMONS LOG HOUSEUPPER PL" NO NO
```

**Why it's hard:**
- LOT#N BLOCK#N are parsed as unit designator + value, consuming tokens that should be
  the civic number.
- The `#` character is discarded by the CATCHALL rule, leaving "LOTN" or "LOT" + "N"
  depending on whitespace.
- Narrative descriptions in YT NAR data are not real addresses — they're property
  descriptions embedded in address fields ("LOG HOUSE", "GREEN/TAN HOUSE").
- These patterns are extremely localized (140 addresses total across YT/NT).

**Recommended approach:** Low priority given the small volume. If pursued, would need
LOT and BLOCK recognized as special keywords in the unit parsing path, with the
following NUMBER treated as the property identifier rather than the civic number.

**Risk:** Low impact, medium complexity. The narrative descriptions are unfixable
without NAR data cleanup.

---

## 7. ~~Quebec — GRANDE/LE + Type Word (~500 mismatches)~~ RESOLVED — article prefix guard

**Impact:** 8,027 NAR mismatches across QC, affecting well-known streets.

**Pattern:**
```
GRANDE ALLÉE (4,691)  — famous Quebec City street
GRAND BOULEVARD (2,106) — Montreal
GRANDE CÔTE (681)     — Lanoraie
GRAND RANG (499)      — Saint-Hyacinthe
LE BOULEVARD (49)     — Pierrefonds
LA MONTÉE (1)
```

**Root cause:** The English pattern (Rule 2) consumed trailing type words (ALLÉE,
BOULEVARD, CÔTE, RANG) even when preceded by a French article/adjective (GRANDE,
GRAND, LE, LA, LES). These are complete proper names (odonyms) where the type word
is part of the name, not a separable generic.

**Confirmation:** The Commission de toponymie du Québec's official record for
"Grande Allée Ouest" lists: spécifique=`Grande Allée Ouest`, générique=_(empty)_,
type d'entité=`Avenue`. The generic (type) field is intentionally blank — "Allée" is
part of the proper name. NAR correctly reflects this classification.

**Fix (2026-03-21):** Extended Rule 1's "THE" prefix guard to include French
articles/adjectives: GRAND, GRANDE, LE, LA, LES. When one of these words precedes a
trailing type word, the entire phrase is treated as the street name with no type —
matching the Commission de toponymie's classification.

For types exclusively French (ALLÉE, CÔTE, RANG, MONTÉE): fires regardless of province.
For types shared with English (BOULEVARD, PLACE): fires only when province is QC —
avoids regressing English-province streets like GRAND AVE or LE BLVD.

Updated 2 ODA test expectations that previously matched ODA's incorrect split
(name=GRANDE, type=ALLEE; name=GRAND, type=BOULEVARD) to match the Commission's
official classification (name=GRANDE ALLEE, no type; name=GRAND BOULEVARD, no type).

---

## Summary

| Issue | Province | Mismatches | Status | Notes |
|-------|----------|------------|--------|-------|
| ~~SC doubled type~~ | NS | ~~96,481~~ | **RESOLVED** | CSV parsing bug — CSD type read as street name |
| ROUTE + number | NB | 49,114 | **NAR data issue** | NB stores unsplit; parser follows Canada Post |
| ~~SNO doubled type~~ | NL | ~~5,895~~ | **RESOLVED** | CSV parsing bug |
| ~~FD doubled type~~ | PE | ~~4,844~~ | **RESOLVED** | CSV parsing bug |
| ~~NO NO pattern~~ | ON/MB/SK/YT | ~~6,762~~ | **RESOLVED** | CSV parsing bug |
| ~~LOT/BLOCK~~ | YT/NT | ~~140~~ | **RESOLVED** | CSV parsing bug |
| ~~GRANDE/LE + type~~ | QC | ~~8,027~~ | **RESOLVED** | Article prefix guard — per Commission de toponymie |
