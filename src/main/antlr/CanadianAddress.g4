grammar CanadianAddress;

// ============================================================
// Canadian Postal Address Grammar (v3)
// ============================================================
//
// Design philosophy:
//   The LEXER is deliberately minimal — it recognizes structural
//   tokens (words, numbers, postal codes, punctuation) without
//   trying to classify street types, provinces, unit designators,
//   directions, or keywords. All semantic interpretation belongs
//   in the VISITOR, which can use updatable lookup tables rather
//   than hardcoded grammar rules.
//
//   The PARSER splits input into lines and each line into tokens.
//   The visitor uses line position and token patterns to extract
//   address components.
//
// Canadian address format (Canada Post addressing guidelines):
//   Line 1:  Addressee name (person or organization)
//   Line 2:  Additional delivery info — care-of, attention, unit, etc. (optional)
//   Line 3:  Delivery address — one of:
//              Civic:    [unit] civicNumber streetName [streetType] [direction]
//              PO Box:   PO BOX number [STN stationName]
//              Rural:    RR number [STN stationName]
//              General:  GD [STN stationName]
//   Line 4:  Municipality  Province  PostalCode
//            (1 space between municipality and province,
//             2 spaces between province and postal code;
//             postal code may be on its own line if space is tight)
//   Line 5:  Country — CANADA (optional, for international mail only;
//            Canada Post says to AVOID "Canada" in domestic addresses)
//
//   Maximum 6 lines in an address block.
//   Uppercase preferred; lowercase accepted.
//   No punctuation unless part of a proper name (e.g., ST. JOHN'S).
//   Never use the # symbol.
//   Accents are acceptable and not considered punctuation.
//
// Province/territory codes (13 total — NT was missing in v1/v2):
//   AB BC MB NB NL NT NS NU ON PE QC SK YT
//
// The visitor should interpret lines positionally:
//   - FIRST line  → addressee
//   - LAST line   → region (municipality / province / postal code)
//   - MIDDLE lines → delivery information
//
// Visitor classification hints for middle (delivery) lines:
//   - Starts with NUMBER or ALPHANUMERIC  → likely civic address
//   - Contains SLASH (C/O, A/S)           → likely care-of
//   - First WORD matches PO|CP|BOX        → postal box
//   - First WORD matches RR               → rural route
//   - First WORD matches GD               → general delivery
//   - First WORD matches SITE|EMPL        → rural site/compartment info
//   - Otherwise                           → additional addressee/delivery info
//
// Postal code detection:
//   - POSTAL_CODE token: 6 chars, no space (e.g., M5V2Y7)
//   - Split postal code: WORD(A1A) + NUMBER(1) + WORD(A1)
//     e.g., "M5V 2Y7" → WORD('M5V'), NUMBER('2'), WORD('Y7')
//     The visitor should recognize this pattern on the region line.
//   - First letter of postal code maps to province/territory:
//       A→NL  B→NS  C→PE  E→NB  G→QC  H→QC  J→QC  K→ON  L→ON
//       M→ON  N→ON  P→ON  R→MB  S→SK  T→AB  V→BC  X→NT/NU  Y→YT
//     (D,F,I,O,Q,U,W,Z are not used)
//
// ============================================================


// ---- Parser Rules ----

// Entry point — tolerates leading/trailing whitespace and blank lines
address
    : (WS | NL)* addressBody (WS | NL)* EOF
    ;

// One or more non-empty address lines separated by newline(s)
addressBody
    : addressLine (lineSep addressLine)*
    ;

// Newline separator — one or more newlines, possibly with whitespace-only
// blank lines in between (handles double-spaced input, trailing spaces, etc.)
lineSep
    : NL (WS* NL)*
    ;

// A single address line with optional leading/trailing whitespace
// (handles indented input from text blocks, form fields, etc.)
addressLine
    : WS* lineContent WS*
    ;

// Sequence of tokens within a line.
// WS* (not WS+) between tokens allows adjacent tokens for patterns
// like C/O (WORD SLASH WORD) and 10-123 (NUMBER HYPHEN NUMBER).
lineContent
    : lineToken (WS* lineToken)*
    ;

// Any token that can appear within a line.
// The visitor inspects token type and text to determine semantics.
lineToken
    : WORD
    | NUMBER
    | POSTAL_CODE
    | ALPHANUMERIC
    | HYPHEN
    | SLASH
    | DOT
    | COMMA
    | AMPERSAND
    ;


// ---- Lexer Rules ----
// Ordered by priority: longest-match wins, then declaration order breaks ties.

// Canadian postal code without space (e.g., M5V2Y7)
// Must precede WORD so it wins on a 6-char letter-digit-letter-digit-letter-digit match.
// Intentionally broad — does not restrict which letters are valid; the visitor
// can validate against Canada Post rules (no D,F,I,O,Q,U,W,Z).
POSTAL_CODE
    : [A-Za-z] [0-9] [A-Za-z] [0-9] [A-Za-z] [0-9]
    ;

// Numeric sequences — civic numbers, PO box numbers, route numbers, etc.
NUMBER
    : [0-9]+
    ;

// Text words — includes hyphenated names (O'Brien, Saint-Jean-sur-Richelieu,
// Ste-Anne-de-Bellevue) and dotted abbreviations (P.O., R.R., St., Ave.)
// because the characters are consumed greedily after an initial letter.
// This means street types, province names, unit designators, and all other
// keywords are lexed as WORD — the visitor distinguishes them semantically.
WORD
    : LETTER (LETTER | [0-9] | '\'' | '\u2019' | '-' | '.')*
    ;

// Digit-leading alphanumeric sequences (e.g., 2Y7 for the second half of a
// spaced postal code, or 1ST/2ND/3RD for ordinal street names, or 123A for
// civic number with suffix). Matches when a digit-start token contains letters,
// beating NUMBER on length.
ALPHANUMERIC
    : (LETTER | [0-9])+
    ;

fragment LETTER
    : [A-Za-z\u00C0-\u00FF\u0100-\u017F]   // ASCII + Latin Extended (French accents)
    ;

// Punctuation — kept as distinct tokens for semantic use by visitor
SLASH  : '/' ;    // care-of (C/O, A/S)
HYPHEN : '-' ;    // unit-civic separator (10-123), standalone hyphens
DOT    : '.' ;    // standalone dots (mid-word dots consumed by WORD)
COMMA     : ',' ;    // sometimes used before province (TORONTO, ON)
AMPERSAND : '&' ;    // business names (SMITH & JONES LTD)

// Whitespace — spaces and tabs only; newlines are a separate token
NL : '\r\n' | '\r' | '\n' ;
WS : [ \t]+ ;

// Discard characters that have no addressing meaning.
// Note: slash, hyphen, dot, comma are NOT skipped — they are tokens above.
// Apostrophes/quotes within words are consumed by WORD; standalone ones are skipped.
// Catch-all: discard any character not matched by the rules above.
// This prevents lexer errors on unexpected input (stray punctuation,
// unicode symbols, etc.) while keeping the tokens we care about.
CATCHALL
    : . -> skip
    ;
