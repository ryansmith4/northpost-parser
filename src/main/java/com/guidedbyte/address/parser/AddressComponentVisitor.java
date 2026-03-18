package com.guidedbyte.address.parser;

import com.guidedbyte.address.model.AddressComponents;
import com.guidedbyte.address.model.AddressComponents.AddressType;
import com.guidedbyte.address.model.AddressComponents.ParsingMode;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR visitor for extracting Canadian address components using the grammar.
 *
 * <p>The v3 grammar is deliberately minimal — the lexer only produces structural tokens (WORD, NUMBER, POSTAL_CODE,
 * ALPHANUMERIC, punctuation). All semantic interpretation happens here in the visitor using lookup tables and
 * anchor-based classification.
 *
 * <p>Line interpretation strategy (anchor-based classification):
 * <ul>
 *   <li>Last line → region (municipality / province / postal code) — positional, per Canada Post convention
 *   <li>Pre-region lines → classified by content anchors (delivery keywords, care-of markers, etc.)
 *   <li>Lines with no recognizable anchors → addressee (first) or delivery (subsequent)
 * </ul>
 */
public class AddressComponentVisitor extends CanadianAddressBaseVisitor<AddressComponents> {

    private static final Logger logger = LoggerFactory.getLogger(AddressComponentVisitor.class);

    // ---- Anchor-based line classification ----

    /** Role assigned to each address line based on content anchors */
    private enum LineRole {
        REGION,
        DELIVERY,
        CARE_OF,
        SITE_INFO,
        ADDRESSEE,
        COUNTRY,
        POSTAL_CODE_ONLY
    }

    /** A line paired with its classified role */
    private record ClassifiedLine(LineTokens line, LineRole role) {}

    // ---- Lookup tables (updatable, not hardcoded in grammar) ----

    /** All 13 province/territory codes */
    private static final Set<String> PROVINCE_CODES =
            Set.of("AB", "BC", "MB", "NB", "NL", "NT", "NS", "NU", "ON", "PE", "QC", "SK", "YT");

    /** FSA first-letter → province mapping (for inferring province from postal code) */
    private static final Map<Character, String> POSTAL_PREFIX_TO_PROVINCE = Map.ofEntries(
            Map.entry('A', "NL"),
            Map.entry('B', "NS"),
            Map.entry('C', "PE"),
            Map.entry('E', "NB"),
            Map.entry('G', "QC"),
            Map.entry('H', "QC"),
            Map.entry('J', "QC"),
            Map.entry('K', "ON"),
            Map.entry('L', "ON"),
            Map.entry('M', "ON"),
            Map.entry('N', "ON"),
            Map.entry('P', "ON"),
            Map.entry('R', "MB"),
            Map.entry('S', "SK"),
            Map.entry('T', "AB"),
            Map.entry('V', "BC"),
            Map.entry('X', "NT"), // X can be NT or NU
            Map.entry('Y', "YT"));

    /** Common English street type abbreviations and full names */
    private static final Set<String> STREET_TYPES_EN = Set.of(
            "STREET",
            "ST",
            "AVENUE",
            "AVE",
            "AV",
            "BOULEVARD",
            "BLVD",
            "BV",
            "DRIVE",
            "DR",
            "DV",
            "ROAD",
            "RD",
            "PLACE",
            "PL",
            "COURT",
            "CRT",
            "CT",
            "CRESCENT",
            "CRES",
            "CR",
            "CIRCLE",
            "CIR",
            "CI",
            "WAY",
            "WY",
            "LANE",
            "LN",
            "TERRACE",
            "TERR",
            "TRAIL",
            "TRL",
            "PARKWAY",
            "PKY",
            "PY",
            "HIGHWAY",
            "HWY",
            "HY",
            "CLOSE",
            "CL",
            "CLO",
            "GATE",
            "GT",
            "GA",
            "GREEN",
            "GR",
            "GRN",
            "GROVE",
            "GV",
            "GRV",
            "HEATH",
            "HE",
            "HEIGHTS",
            "HTS",
            "HT",
            "HILL",
            "HL",
            "LANDING",
            "LANDNG",
            "LD",
            "MEWS",
            "MS",
            "PARADE",
            "PARK",
            "PK",
            "PA",
            "PATH",
            "POINT",
            "PT",
            "RIDGE",
            "ROW",
            "SQUARE",
            "SQ",
            "WALK",
            "WK",
            "ABBEY",
            "ACRES",
            "ALLEY",
            "BAY",
            "BA",
            "BEACH",
            "BEND",
            "BRANCH",
            "BR",
            "BYPASS",
            "BYWAY",
            "CAPE",
            "CENTRE",
            "CHASE",
            "CIRCUIT",
            "CIRC",
            "COMMON",
            "CM",
            "CONCESSION",
            "CON",
            "CORNERS",
            "COVE",
            "CO",
            "CV",
            "CREEK",
            "CRK",
            "CROSS",
            "CROSSING",
            "XG",
            "CDS",
            "DALE",
            "DELL",
            "DIVERSION",
            "DOWNS",
            "END",
            "ESPLANADE",
            "ESPL",
            "ESTATES",
            "ESTS",
            "EXTENSION",
            "EXTEN",
            "FAIRWAY",
            "FARM",
            "FIELD",
            "FORD",
            "FOREST",
            "FREEWAY",
            "FWY",
            "FRONT",
            "GARDEN",
            "GARDENS",
            "GDNS",
            "GDN",
            "GLADE",
            "GLEN",
            "GRANGE",
            "HARBOUR",
            "HAVEN",
            "HOLLOW",
            "INLET",
            "INL",
            "ISLAND",
            "KEY",
            "KNOLL",
            "LIMITS",
            "LINE",
            "LI",
            "LINK",
            "LOOKOUT",
            "LKOUT",
            "LOOP",
            "MALL",
            "MANOR",
            "MR",
            "MAZE",
            "ME",
            "MEADOW",
            "MEADOWS",
            "MOUNT",
            "MT",
            "MOUNTAIN",
            "MTN",
            "ORCHARD",
            "PARKLAND",
            "PASSAGE",
            "PASS",
            "PINES",
            "PLATEAU",
            "PLAZA",
            "PORT",
            "PRIVATE",
            "PVT",
            "PR",
            "PROMENADE",
            "PROM",
            "QUAY",
            "RAMP",
            "RANGE",
            "RG",
            "REACH",
            "RISE",
            "RI",
            "RO",
            "RUN",
            "SHORE",
            "SHORES",
            "SHORELINE",
            "SIDEROAD",
            "SDRD",
            "SR",
            "SPRING",
            "STROLL",
            "TC",
            "TE",
            "THICKET",
            "TOWERS",
            "TOWNLINE",
            "TL",
            "TRUNK",
            "TR",
            "TUNNEL",
            "TURNABOUT",
            "VALE",
            "VIEW",
            "VW",
            "VI",
            "VILLAGE",
            "VILLAS",
            "VISTA",
            "WHARF",
            "WOOD",
            "WOODS",
            "WYND",
            // v1.1.1: Common types and Calgary/Edmonton abbreviations
            "PKWY",
            "CREST",
            "BLUFF",
            "SPUR",
            "ESTATE",
            "GATEWAY",
            "GY",
            "WALKWAY",
            "OUTLOOK",
            "GD",
            "CA",
            "CX",
            "PS",
            "PZ",
            "LP",
            "AL",
            "HI",
            "GL",
            "PW",
            "CE",
            "LK",
            "GW");

    /** Common French street type abbreviations and full names */
    private static final Set<String> STREET_TYPES_FR = Set.of(
            "RUE",
            "AVENUE",
            "AVE",
            "AV",
            "BOULEVARD",
            "BOUL",
            "BL",
            "CHEMIN",
            "CH",
            "CMN",
            "ROUTE",
            "RTE",
            "PLACE",
            "RANG",
            "MONTÉE",
            "MONTEE",
            "CÔTE",
            "COTE",
            "CROISSANT",
            "IMPASSE",
            "IMP",
            "PASSAGE",
            "PROMENADE",
            "TERRASSE",
            "TSSE",
            "ALLÉE",
            "ALLEE",
            "CARRÉ",
            "CARRE",
            "CAR",
            "CERCLE",
            "CIRCUIT",
            "COUR",
            "COURS",
            "CRÊTE",
            "CRETE",
            "PARC",
            "PONT",
            "SENTIER",
            "TUNNEL",
            "VOIE",
            "RUELLE",
            "AUTOROUTE",
            "DESSERTE",
            "JARDIN",
            "QUAI",
            "DIVERS");

    /** Combined street types for quick lookup */
    private static final Set<String> ALL_STREET_TYPES;

    static {
        Set<String> combined = new HashSet<>();
        combined.addAll(STREET_TYPES_EN);
        combined.addAll(STREET_TYPES_FR);
        ALL_STREET_TYPES = Collections.unmodifiableSet(combined);
    }

    /**
     * Checks if a word is a known street type, stripping a trailing period if needed. Returns the canonical form
     * (without period) on match, or null if not a recognized type. This handles Ontario-style abbreviations like DR.,
     * RD., CRES., AVE. without polluting the type lookup tables.
     */
    private static String normalizeStreetType(String word) {
        if (ALL_STREET_TYPES.contains(word)) return word;
        if (word.endsWith(".")) {
            String stripped = word.substring(0, word.length() - 1);
            if (ALL_STREET_TYPES.contains(stripped)) return stripped;
        }
        return null;
    }

    private static boolean isStreetType(String word) {
        return normalizeStreetType(word) != null;
    }

    private static boolean isFrenchStreetType(String word) {
        if (STREET_TYPES_FR.contains(word)) return true;
        if (word.endsWith(".")) {
            return STREET_TYPES_FR.contains(word.substring(0, word.length() - 1));
        }
        return false;
    }

    /** Checks if a word is a unit designator, stripping trailing period if needed. */
    private static boolean isUnitDesignator(String word) {
        if (UNIT_DESIGNATORS.contains(word)) return true;
        if (word.endsWith(".")) {
            return UNIT_DESIGNATORS.contains(word.substring(0, word.length() - 1));
        }
        return false;
    }

    /**
     * Plan 4: Try to extract a unit designator prefix from a concatenated token like "APT5" or "STE200". Returns a
     * UnitSplit with the designator and unit number, or null if no match. Checks longest designators first to avoid
     * "APT" matching inside "APARTMENT". Validates that the remainder starts with a digit to avoid false positives
     * ("STEW").
     */
    private record UnitSplit(String designator, String unitNumber) {}

    private static UnitSplit tryExtractUnitPrefix(String word) {
        String upper = word.toUpperCase();
        for (String designator : UNIT_DESIGNATORS_BY_LENGTH) {
            if (upper.startsWith(designator) && upper.length() > designator.length()) {
                String remainder = upper.substring(designator.length());
                // Check for period between designator and number (e.g., "APT.5")
                if (remainder.startsWith(".")) {
                    remainder = remainder.substring(1);
                }
                if (!remainder.isEmpty() && Character.isDigit(remainder.charAt(0))) {
                    return new UnitSplit(designator, remainder);
                }
            }
        }
        return null;
    }

    /**
     * Plan 2: Try to parse a fraction pattern (NUMBER SLASH NUMBER) starting at startIdx. Returns the fraction string
     * (e.g., "1/2") and number of tokens consumed, or null. Only matches valid fractions: denominator in {2,3,4,8},
     * numerator < denominator.
     */
    private record FractionResult(String fraction, int tokensConsumed) {}

    private static FractionResult tryParseFraction(List<TokenInfo> tokens, int startIdx) {
        if (startIdx + 2 >= tokens.size()) return null;
        TokenInfo t0 = tokens.get(startIdx);
        TokenInfo t1 = tokens.get(startIdx + 1);
        TokenInfo t2 = tokens.get(startIdx + 2);
        if (t0.type == CanadianAddressParser.NUMBER
                && t1.type == CanadianAddressParser.SLASH
                && t2.type == CanadianAddressParser.NUMBER) {
            try {
                int numerator = Integer.parseInt(t0.text);
                int denominator = Integer.parseInt(t2.text);
                Set<Integer> validDenominators = Set.of(2, 3, 4, 8);
                if (validDenominators.contains(denominator) && numerator < denominator) {
                    return new FractionResult(t0.text + "/" + t2.text, 3);
                }
            } catch (NumberFormatException e) {
                // Not a valid fraction
            }
        }
        return null;
    }

    /** Short directional abbreviations safe to treat as prefix directions (e.g., "N MAIN ST") */
    private static final Set<String> PREFIX_DIRECTIONS =
            Set.of("N", "S", "E", "W", "O", "NE", "NW", "SE", "SW", "N.", "S.", "E.", "W.");

    /** Directional indicators (English and French) — full set for suffix detection */
    private static final Set<String> DIRECTIONS = Set.of(
            "N",
            "S",
            "E",
            "W",
            "O",
            "NE",
            "NW",
            "SE",
            "SW",
            "NB",
            "SB",
            "EB",
            "WB",
            "NORTH",
            "SOUTH",
            "EAST",
            "WEST",
            "NORTHEAST",
            "NORTHWEST",
            "SOUTHEAST",
            "SOUTHWEST",
            "NORTHBOUND",
            "SOUTHBOUND",
            "EASTBOUND",
            "WESTBOUND",
            "N.",
            "S.",
            "E.",
            "W.",
            "N.E.",
            "N.W.",
            "S.E.",
            "S.W.",
            "EST",
            "OUEST",
            "NORD",
            "SUD");

    /** Unit designators (English and French) */
    private static final Set<String> UNIT_DESIGNATORS = Set.of(
            "APT",
            "APARTMENT",
            "SUITE",
            "STE",
            "UNIT",
            "BUREAU",
            "BUR",
            "ROOM",
            "RM",
            "FLOOR",
            "FL",
            "OFFICE",
            "DEPT",
            "DEPARTMENT",
            "BLDG",
            "BUILDING",
            "TOWER",
            "TWR",
            "PH",
            "PENTHOUSE",
            "APP",
            "APPARTEMENT",
            "PORTE",
            "ÉTAGE",
            "ETAGE");

    /** Designators sorted by length descending for longest-match-first (Plan 4) */
    private static final List<String> UNIT_DESIGNATORS_BY_LENGTH;

    static {
        List<String> sorted = new ArrayList<>(UNIT_DESIGNATORS);
        sorted.sort((a, b) -> b.length() - a.length());
        UNIT_DESIGNATORS_BY_LENGTH = Collections.unmodifiableList(sorted);
    }

    /**
     * Province/territory full names and informal abbreviations → 2-letter code. Multi-word names (e.g., "BRITISH
     * COLUMBIA") are stored with space separators; the lookup logic handles matching multiple consecutive tokens.
     * Dotted abbreviations like "B.C." are single WORD tokens (lexer's WORD rule includes '.' as a continuation char),
     * so they appear as-is.
     */
    private static final Map<String, String> PROVINCE_NAMES = new LinkedHashMap<>();

    static {
        // Longest names first for correct matching order
        PROVINCE_NAMES.put("NEWFOUNDLAND AND LABRADOR", "NL");
        PROVINCE_NAMES.put("PRINCE EDWARD ISLAND", "PE");
        PROVINCE_NAMES.put("BRITISH COLUMBIA", "BC");
        PROVINCE_NAMES.put("NEW BRUNSWICK", "NB");
        PROVINCE_NAMES.put("NORTHWEST TERRITORIES", "NT");
        PROVINCE_NAMES.put("NOVA SCOTIA", "NS");
        PROVINCE_NAMES.put("SASKATCHEWAN", "SK");
        PROVINCE_NAMES.put("NEWFOUNDLAND", "NL");
        PROVINCE_NAMES.put("MANITOBA", "MB");
        PROVINCE_NAMES.put("ONTARIO", "ON");
        PROVINCE_NAMES.put("ALBERTA", "AB");
        PROVINCE_NAMES.put("NUNAVUT", "NU");
        PROVINCE_NAMES.put("QUEBEC", "QC");
        PROVINCE_NAMES.put("QUÉBEC", "QC");
        PROVINCE_NAMES.put("YUKON", "YT");
        // Informal abbreviations (single-word, including dotted forms)
        PROVINCE_NAMES.put("NFLD.", "NL");
        PROVINCE_NAMES.put("NFLD", "NL");
        PROVINCE_NAMES.put("P.E.I.", "PE");
        PROVINCE_NAMES.put("PEI", "PE");
        PROVINCE_NAMES.put("N.S.", "NS");
        PROVINCE_NAMES.put("N.B.", "NB");
        PROVINCE_NAMES.put("QUE.", "QC");
        PROVINCE_NAMES.put("QUE", "QC");
        PROVINCE_NAMES.put("ONT.", "ON");
        PROVINCE_NAMES.put("ONT", "ON");
        PROVINCE_NAMES.put("MAN.", "MB");
        PROVINCE_NAMES.put("MAN", "MB");
        PROVINCE_NAMES.put("SASK.", "SK");
        PROVINCE_NAMES.put("SASK", "SK");
        PROVINCE_NAMES.put("ALTA.", "AB");
        PROVINCE_NAMES.put("ALTA", "AB");
        PROVINCE_NAMES.put("B.C.", "BC");
        PROVINCE_NAMES.put("N.W.T.", "NT");
        PROVINCE_NAMES.put("NWT", "NT");
        PROVINCE_NAMES.put("Y.T.", "YT");
    }

    /** Country keywords */
    private static final Set<String> COUNTRY_KEYWORDS = Set.of("CANADA", "CAN", "CA");

    /** PO Box keywords (English and French) */
    private static final Set<String> PO_BOX_KEYWORDS = Set.of("PO", "P.O.", "CP", "C.P.", "BOX");

    /** Postal code pattern: A1A 1A1 or A1A1A1 */
    private static final Pattern POSTAL_CODE_FSA = Pattern.compile("[A-Z]\\d[A-Z]");

    private static final Pattern POSTAL_CODE_LDU = Pattern.compile("\\d[A-Z]\\d");

    // ---- Instance state ----

    private final AddressComponents.Builder builder = AddressComponents.builder();

    public AddressComponentVisitor() {
        this(ParsingMode.LENIENT);
    }

    public AddressComponentVisitor(ParsingMode parsingMode) {
        builder.parsingMode(parsingMode);
    }

    // ---- Visitor entry point ----

    @Override
    public AddressComponents visitAddress(CanadianAddressParser.AddressContext ctx) {
        logger.debug("Visiting address");

        var bodyCtx = ctx.addressBody();
        if (bodyCtx == null) {
            return builder.build();
        }

        // Collect all non-empty address lines
        List<CanadianAddressParser.AddressLineContext> lines = bodyCtx.addressLine();
        if (lines == null || lines.isEmpty()) {
            return builder.build();
        }

        // Extract token lists for each line (uppercased text with token types)
        List<LineTokens> parsedLines = new ArrayList<>();
        for (var line : lines) {
            LineTokens lt = extractLineTokens(line);
            if (!lt.tokens.isEmpty()) {
                parsedLines.add(lt);
            }
        }

        if (parsedLines.isEmpty()) {
            return builder.build();
        }

        logger.debug("Found {} non-empty lines", parsedLines.size());

        // Plan 7: Split comma-delimited single-line addresses
        // When a single line is split by commas, the result has NO addressee —
        // the first part is delivery and the last part is region.
        boolean commaSplit = false;
        if (parsedLines.size() == 1) {
            List<LineTokens> splitLines = tryCommaSplit(parsedLines.get(0));
            if (splitLines != null) {
                parsedLines = splitLines;
                commaSplit = true;
                logger.debug("Comma-split produced {} lines", parsedLines.size());
            }
        }

        // Interpret lines by classification
        if (commaSplit && parsedLines.size() == 1) {
            // Comma-split resolved to a region-only line (e.g., "ST. JOHN'S, NL A1A 1A1")
            interpretRegionLine(parsedLines.get(0));
        } else if (parsedLines.size() == 1) {
            // Single line — could be just a postal code, or a full single-line address
            interpretSingleLine(parsedLines.get(0));
        } else if (commaSplit) {
            // Comma-split: no addressee, first part(s) are delivery, last part is region
            int regionLineIndex = parsedLines.size() - 1;
            interpretRegionLine(parsedLines.get(regionLineIndex));
            for (int i = 0; i < regionLineIndex; i++) {
                interpretDeliveryLine(parsedLines.get(i));
            }
        } else {
            // ---- Phase 1: Region identification (last line, per Canada Post convention) ----
            int regionLineIndex = parsedLines.size() - 1;

            // Peel country line from tail
            if (isCountryLine(parsedLines.get(regionLineIndex))) {
                builder.country(parsedLines.get(regionLineIndex).text);
                regionLineIndex--;
            }

            // Peel postal-code-only line from tail
            if (regionLineIndex >= 1 && isPostalCodeOnlyLine(parsedLines.get(regionLineIndex))) {
                String pc = findPostalCode(parsedLines.get(regionLineIndex));
                if (pc != null) {
                    builder.postalCode(pc);
                    logger.debug("Peeled postal-code-only line: '{}'", pc);
                    regionLineIndex--;
                }
            }

            // Last remaining line → region
            interpretRegionLine(parsedLines.get(regionLineIndex));

            // ---- Phase 2: Classify pre-region lines by content anchors ----
            List<ClassifiedLine> classified = new ArrayList<>();
            for (int i = 0; i < regionLineIndex; i++) {
                LineTokens line = parsedLines.get(i);
                LineRole role = classifyLine(line);
                classified.add(new ClassifiedLine(line, role));
                logger.debug("Line {} classified as {}: '{}'", i, role, line.text);
            }

            // ---- Phase 3: Dispatch by classification ----
            boolean addresseeSeen = false;
            for (ClassifiedLine cl : classified) {
                switch (cl.role()) {
                    case DELIVERY -> interpretDeliveryLine(cl.line());
                    case CARE_OF -> builder.careOf(extractAfterCareOf(cl.line().tokens));
                    case SITE_INFO -> builder.siteInfo(cl.line().text);
                    case COUNTRY -> builder.country(cl.line().text);
                    case POSTAL_CODE_ONLY -> {
                        String pc = findPostalCode(cl.line());
                        if (pc != null) builder.postalCode(pc);
                    }
                    case ADDRESSEE -> {
                        if (!addresseeSeen) {
                            interpretAddresseeLine(cl.line());
                            addresseeSeen = true;
                        } else {
                            // Subsequent unanchored lines → route through delivery interpretation
                            interpretDeliveryLine(cl.line());
                        }
                    }
                    default -> interpretDeliveryLine(cl.line());
                }
            }
        }

        // Infer province from postal code if missing
        if (!builder.build().hasProvince() && builder.build().hasPostalCode()) {
            String pc = builder.build().postalCode().replaceAll("\\s", "");
            if (!pc.isEmpty()) {
                String inferred = POSTAL_PREFIX_TO_PROVINCE.get(pc.charAt(0));
                if (inferred != null) {
                    logger.debug("Inferred province {} from postal code {}", inferred, pc);
                    builder.province(inferred);
                }
            }
        }

        return finalizeComponents();
    }

    // ---- Line interpretation methods ----

    private void interpretSingleLine(LineTokens line) {
        // Check if it looks like just a postal code
        if (line.tokens.size() <= 3 && findPostalCode(line) != null) {
            String pc = findPostalCode(line);
            builder.postalCode(pc);
            return;
        }
        // Otherwise, treat the whole line as the addressee and attempt region extraction
        // from the tail end if we can detect postal code / province patterns
        interpretAddresseeLine(line);
    }

    private void interpretAddresseeLine(LineTokens line) {
        builder.addressee(line.text);
    }

    private void interpretRegionLine(LineTokens line) {
        logger.debug("Interpreting region line: '{}'", line.text);
        List<TokenInfo> tokens = line.tokens;

        // Find postal code (either a POSTAL_CODE token, or FSA + LDU split pattern)
        // Skip if already pre-set (Plan 3: postal code peeled from its own line)
        String postalCode = null;
        int postalCodeStartIdx = -1;
        int postalCodeEndIdx = -1;

        boolean postalCodePreSet = builder.build().hasPostalCode();
        if (!postalCodePreSet) {
            for (int i = 0; i < tokens.size(); i++) {
                TokenInfo t = tokens.get(i);
                if (t.type == CanadianAddressParser.POSTAL_CODE) {
                    postalCode = formatPostalCode(t.text);
                    postalCodeStartIdx = i;
                    postalCodeEndIdx = i;
                    break;
                }
                // Split postal code: WORD(A1A) + ALPHANUMERIC(1A1) or NUMBER(1) + WORD(A1) etc.
                if (i < tokens.size() - 1 && isFSA(t.text)) {
                    TokenInfo next = tokens.get(i + 1);
                    if (isLDU(next.text)) {
                        postalCode = t.text + " " + next.text;
                        postalCodeStartIdx = i;
                        postalCodeEndIdx = i + 1;
                        break;
                    }
                    if (i < tokens.size() - 2) {
                        String combined = next.text + tokens.get(i + 2).text;
                        if (isLDU(combined)) {
                            postalCode = t.text + " " + next.text + tokens.get(i + 2).text;
                            postalCodeStartIdx = i;
                            postalCodeEndIdx = i + 2;
                            break;
                        }
                    }
                }
            }

            if (postalCode != null) {
                builder.postalCode(postalCode);
                logger.debug("Found postal code: '{}'", postalCode);
            }
        }

        // Find province code (2-letter)
        String province = null;
        int provinceStartIdx = -1;
        int provinceEndIdx = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (i >= postalCodeStartIdx && i <= postalCodeEndIdx) continue;
            if (tokens.get(i).type == CanadianAddressParser.WORD
                    && PROVINCE_CODES.contains(tokens.get(i).text.toUpperCase())) {
                province = tokens.get(i).text.toUpperCase();
                provinceStartIdx = i;
                provinceEndIdx = i;
                break;
            }
        }

        // Plan 1: If no 2-letter code found, scan right-to-left for province names
        if (province == null) {
            int scanEnd = tokens.size();
            if (postalCodeStartIdx >= 0) scanEnd = postalCodeStartIdx;

            // Right-to-left: try longest match first at each position
            for (int i = scanEnd - 1; i >= 0 && province == null; i--) {
                if (tokens.get(i).type != CanadianAddressParser.WORD) continue;

                for (Map.Entry<String, String> entry : PROVINCE_NAMES.entrySet()) {
                    String name = entry.getKey();
                    String[] nameParts = name.split("\\s+");

                    // Check if enough tokens exist starting at position (i - nameParts.length + 1)
                    int startPos = i - nameParts.length + 1;
                    if (startPos < 0) continue;

                    boolean match = true;
                    for (int j = 0; j < nameParts.length; j++) {
                        int tokenIdx = startPos + j;
                        if (tokenIdx >= postalCodeStartIdx && tokenIdx <= postalCodeEndIdx && postalCodeStartIdx >= 0) {
                            match = false;
                            break;
                        }
                        String tokenText = tokens.get(tokenIdx).text.toUpperCase();
                        // Strip trailing period for comparison
                        String compareText =
                                tokenText.endsWith(".") ? tokenText.substring(0, tokenText.length() - 1) : tokenText;
                        String namePartCompare = nameParts[j].endsWith(".")
                                ? nameParts[j].substring(0, nameParts[j].length() - 1)
                                : nameParts[j];
                        if (!compareText.equals(namePartCompare) && !tokenText.equals(nameParts[j])) {
                            match = false;
                            break;
                        }
                    }

                    if (match) {
                        province = entry.getValue();
                        provinceStartIdx = startPos;
                        provinceEndIdx = i;
                        logger.debug("Matched province name '{}' → '{}'", name, province);
                        break;
                    }
                }
            }
        }

        if (province != null) {
            builder.province(province);
            logger.debug("Found province: '{}'", province);
        }

        // Find country (check last tokens)
        int countryIdx = -1;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (i >= postalCodeStartIdx && i <= postalCodeEndIdx) continue;
            if (i >= provinceStartIdx && i <= provinceEndIdx) continue;
            if (tokens.get(i).type == CanadianAddressParser.WORD
                    && COUNTRY_KEYWORDS.contains(tokens.get(i).text.toUpperCase())) {
                builder.country(tokens.get(i).text.toUpperCase());
                countryIdx = i;
                break;
            }
        }

        // Everything before province/postalCode/country is the municipality
        int municipalityEnd = tokens.size();
        if (provinceStartIdx >= 0) municipalityEnd = Math.min(municipalityEnd, provinceStartIdx);
        if (postalCodeStartIdx >= 0) municipalityEnd = Math.min(municipalityEnd, postalCodeStartIdx);
        if (countryIdx >= 0) municipalityEnd = Math.min(municipalityEnd, countryIdx);

        StringBuilder munBuilder = new StringBuilder();
        for (int i = 0; i < municipalityEnd; i++) {
            TokenInfo t = tokens.get(i);
            // Skip commas in municipality (e.g., "TORONTO, ON")
            if (t.type == CanadianAddressParser.COMMA) continue;
            if (!munBuilder.isEmpty()) munBuilder.append(" ");
            munBuilder.append(t.text);
        }
        String municipality = munBuilder.toString().trim();
        if (!municipality.isEmpty()) {
            builder.municipality(municipality);
            logger.debug("Found municipality: '{}'", municipality);
        }

        // Check for country after postal code
        if (countryIdx < 0 && postalCodeEndIdx >= 0) {
            for (int i = postalCodeEndIdx + 1; i < tokens.size(); i++) {
                if (tokens.get(i).type == CanadianAddressParser.WORD
                        && COUNTRY_KEYWORDS.contains(tokens.get(i).text.toUpperCase())) {
                    builder.country(tokens.get(i).text.toUpperCase());
                    break;
                }
            }
        }
    }

    private void interpretDeliveryLine(LineTokens line) {
        logger.debug("Interpreting delivery line: '{}'", line.text);
        List<TokenInfo> tokens = line.tokens;

        if (tokens.isEmpty()) return;

        TokenInfo firstToken = tokens.get(0);
        String firstText = firstToken.text.toUpperCase();

        // Check for care-of: C/O or A/S pattern
        if (isCareOfLine(tokens)) {
            // Extract the name after C/O or A/S
            String careOfName = extractAfterCareOf(tokens);
            builder.careOf(careOfName);
            return;
        }

        // Check for PO Box: starts with PO, CP, BOX, P.O., C.P.
        if (PO_BOX_KEYWORDS.contains(firstText)) {
            interpretPoBoxLine(tokens);
            return;
        }

        // Check for Rural Route: starts with RR or R.R.
        if (firstText.equals("RR") || firstText.equals("R.R.")) {
            interpretRuralRouteLine(tokens);
            return;
        }

        // Check for General Delivery: GD, GENERAL DELIVERY, GEN DEL
        if (firstText.equals("GD") || firstText.equals("GENERAL") || isGenDel(firstText, tokens)) {
            interpretGeneralDeliveryLine();
            return;
        }

        // Check for site info: starts with SITE or COMP
        if (firstText.equals("SITE") || firstText.equals("COMP") || firstText.equals("EMPL")) {
            builder.siteInfo(line.text);
            return;
        }

        // Check if line starts with a unit designator (APT 5, APT. 5, etc.)
        if (isUnitDesignator(firstText)) {
            interpretCivicLineWithLeadingUnit(tokens);
            return;
        }

        // Plan 4: Check for concatenated unit+number (APT5, STE200, APT.5)
        UnitSplit unitSplit = tryExtractUnitPrefix(firstText);
        if (unitSplit != null) {
            builder.unitNumber(unitSplit.unitNumber);
            // Continue parsing remaining tokens as civic line (skip first token)
            int idx = 1;
            if (idx < tokens.size()
                    && (tokens.get(idx).type == CanadianAddressParser.NUMBER
                            || tokens.get(idx).type == CanadianAddressParser.ALPHANUMERIC)) {
                builder.streetNumber(tokens.get(idx).text);
                idx++;
                builder.addressType(AddressType.CIVIC);
                idx = handlePostCivicSlashPattern(tokens, idx);
            }
            interpretStreetTokens(tokens, idx);
            return;
        }

        // Check if line starts with NUMBER or ALPHANUMERIC → likely civic address
        if (firstToken.type == CanadianAddressParser.NUMBER || firstToken.type == CanadianAddressParser.ALPHANUMERIC) {
            interpretCivicLine(tokens);
            return;
        }

        // Check if this looks like a street name without a number (e.g., "MAIN STREET")
        if (looksLikeStreetLine(tokens)) {
            interpretStreetOnlyLine(tokens);
            return;
        }

        // Default: treat as additional addressee/delivery info
        // Could be an organization name, dept, floor, etc.
        // If careOf is not yet set, this might be additional name info
        if (!builder.build().hasCareOf()) {
            builder.careOf(line.text);
        }
    }

    // ---- Delivery line sub-interpreters ----

    private void interpretCivicLine(List<TokenInfo> tokens) {
        int idx;

        // Check for unit-hyphen-civic pattern: 10-123 (NUMBER HYPHEN NUMBER)
        if (tokens.size() >= 3
                && tokens.get(0).type == CanadianAddressParser.NUMBER
                && tokens.get(1).type == CanadianAddressParser.HYPHEN
                && tokens.get(2).type == CanadianAddressParser.NUMBER) {
            builder.unitNumber(tokens.get(0).text);
            builder.streetNumber(tokens.get(2).text);
            idx = 3;
            builder.addressType(AddressType.CIVIC);
        } else {
            // First token is civic number (possibly with suffix like 123A)
            builder.streetNumber(tokens.get(0).text);
            idx = 1;
            builder.addressType(AddressType.CIVIC);
        }

        // Plan 2+6: Check for fraction or dual civic after street number
        idx = handlePostCivicSlashPattern(tokens, idx);

        // Remaining tokens: street name, type, direction
        interpretStreetTokens(tokens, idx);
    }

    /**
     * Plans 2+6: After extracting civic number, check for fraction (1/2) or dual civic (123/125). Fractions:
     * denominator in {2,3,4,8} and numerator < denominator → append to street number. Dual civics: otherwise → append
     * "/{number}" to street number. Returns the updated index past consumed tokens.
     */
    private int handlePostCivicSlashPattern(List<TokenInfo> tokens, int idx) {
        if (idx >= tokens.size()) return idx;

        // Plan 2: fraction — NUMBER SLASH NUMBER pattern (e.g., "123 1/2 MAIN ST")
        FractionResult fraction = tryParseFraction(tokens, idx);
        if (fraction != null) {
            String currentStreetNum = builder.build().streetNumber();
            builder.streetNumber(currentStreetNum + " " + fraction.fraction);
            return idx + fraction.tokensConsumed;
        }

        // Plan 6: dual civic — SLASH NUMBER pattern directly after civic (e.g., "123/125 MAIN ST")
        // Tokens: NUMBER(123) SLASH(/) NUMBER(125) WORD(MAIN) WORD(ST)
        // At this point idx is right after civic number, so tokens[idx] is SLASH
        if (idx + 1 < tokens.size()
                && tokens.get(idx).type == CanadianAddressParser.SLASH
                && tokens.get(idx + 1).type == CanadianAddressParser.NUMBER) {
            String currentStreetNum = builder.build().streetNumber();
            builder.streetNumber(currentStreetNum + "/" + tokens.get(idx + 1).text);
            return idx + 2;
        }

        return idx;
    }

    private void interpretCivicLineWithLeadingUnit(List<TokenInfo> tokens) {
        // Pattern: UNIT_DESIGNATOR [number] civicNumber streetName [streetType] [direction]
        int idx = 1; // skip the designator

        // Next token should be the unit number
        if (idx < tokens.size()
                && (tokens.get(idx).type == CanadianAddressParser.NUMBER
                        || tokens.get(idx).type == CanadianAddressParser.ALPHANUMERIC)) {
            builder.unitNumber(tokens.get(idx).text);
            idx++;
        }

        // Next should be the civic/street number
        if (idx < tokens.size()
                && (tokens.get(idx).type == CanadianAddressParser.NUMBER
                        || tokens.get(idx).type == CanadianAddressParser.ALPHANUMERIC)) {
            builder.streetNumber(tokens.get(idx).text);
            idx++;
            builder.addressType(AddressType.CIVIC);

            // Plan 2: Check for fraction after civic number
            idx = handlePostCivicSlashPattern(tokens, idx);
        }

        // Remaining: street name, type, direction
        interpretStreetTokens(tokens, idx);
    }

    private void interpretStreetTokens(List<TokenInfo> tokens, int startIdx) {
        if (startIdx >= tokens.size()) return;

        // Collect remaining words (with original indices for Plan 5 trailing unit detection)
        List<String> words = new ArrayList<>();
        List<Integer> wordIndices = new ArrayList<>(); // index into tokens list
        for (int i = startIdx; i < tokens.size(); i++) {
            TokenInfo t = tokens.get(i);
            if (t.type == CanadianAddressParser.WORD
                    || t.type == CanadianAddressParser.ALPHANUMERIC
                    || t.type == CanadianAddressParser.NUMBER) {
                words.add(t.text);
                wordIndices.add(i);
            }
            // Skip punctuation tokens
        }

        if (words.isEmpty()) return;

        // Direction detection: suffix preferred over prefix (suffix is unambiguously a direction,
        // while prefix letters like E/N/S/W are ambiguous — could be direction, unit, or street name abbreviation)
        String direction = null;
        boolean hasPrefixDir = words.size() > 1
                && PREFIX_DIRECTIONS.contains(words.get(0).toUpperCase());

        // Check suffix first (higher confidence — unambiguously a direction)
        if (words.size() > 1) {
            String lastWord = words.get(words.size() - 1).toUpperCase();
            if (DIRECTIONS.contains(lastWord)) {
                direction = lastWord;
                words.remove(words.size() - 1);
                wordIndices.remove(wordIndices.size() - 1);
            }
        }

        // Fallback: use prefix only when no suffix was found
        if (direction == null && hasPrefixDir) {
            direction = words.get(0).toUpperCase();
            words.remove(0);
            wordIndices.remove(0);
        }

        // Plan 5: Detect trailing unit after street type (e.g., "MAIN ST APT 5")
        // Only attempt if we haven't already extracted a unit number
        if (!builder.build().hasUnitNumber() && words.size() >= 3) {
            extractTrailingUnit(words);
        }

        classifyStreetNameAndType(words);

        // Don't overwrite a direction already set by extractTrailingUnit (e.g., "DR NW APT 5")
        if (direction != null && !builder.build().hasStreetDirection()) {
            builder.streetDirection(direction);
        }
    }

    /**
     * Plan 5: Scan for a trailing unit designator + number after the street type. E.g., words = [MAIN, ST, APT, 5] →
     * extracts APT 5, returns [MAIN, ST]. Also handles concatenated form via tryExtractUnitPrefix (e.g., [MAIN, ST,
     * APT5]). Returns the number of words removed, or 0 if no trailing unit found.
     */
    private int extractTrailingUnit(List<String> words) {
        // Need at least: streetName + streetType + unitDesignator + unitNumber = 4 words
        // or: streetName + streetType + concatenatedUnit = 3 words
        if (words.size() < 3) return 0;

        // Find the rightmost street type in the words (must exist to anchor the search)
        int streetTypeIdx = -1;
        for (int i = words.size() - 1; i >= 1; i--) {
            if (isStreetType(words.get(i).toUpperCase())) {
                streetTypeIdx = i;
                break;
            }
        }
        if (streetTypeIdx < 0 || streetTypeIdx >= words.size() - 1) return 0;

        // Check tokens after the street type, possibly with a direction in between
        int afterType = streetTypeIdx + 1;
        String directionFound = null;

        // Check if the first token after street type is a direction (e.g., "DR E APT 5")
        if (afterType < words.size() && DIRECTIONS.contains(words.get(afterType).toUpperCase())) {
            directionFound = words.get(afterType).toUpperCase();
            afterType++;
        }

        // Case 1: Separate designator + number (APT 5, SUITE 200)
        if (afterType + 1 < words.size()) {
            String possibleDesignator = words.get(afterType).toUpperCase();
            if (isUnitDesignator(possibleDesignator)) {
                // Collect remaining as unit number (could be "5", "5A", etc.)
                StringBuilder unitNum = new StringBuilder();
                for (int i = afterType + 1; i < words.size(); i++) {
                    if (!unitNum.isEmpty()) unitNum.append(" ");
                    unitNum.append(words.get(i));
                }
                builder.unitNumber(unitNum.toString());
                if (directionFound != null) {
                    builder.streetDirection(directionFound);
                }
                // Remove direction + unit tokens from words
                int removeFrom = directionFound != null ? streetTypeIdx + 1 : afterType;
                int removed = words.size() - removeFrom;
                while (words.size() > removeFrom) {
                    words.remove(words.size() - 1);
                }
                return removed;
            }
        }

        // Case 2: Concatenated unit (APT5, STE200) — single token after street type
        if (afterType < words.size()) {
            UnitSplit split = tryExtractUnitPrefix(words.get(afterType));
            if (split != null) {
                builder.unitNumber(split.unitNumber);
                if (directionFound != null) {
                    builder.streetDirection(directionFound);
                }
                int removeFrom = directionFound != null ? streetTypeIdx + 1 : afterType;
                int removed = words.size() - removeFrom;
                while (words.size() > removeFrom) {
                    words.remove(words.size() - 1);
                }
                return removed;
            }
        }

        return 0;
    }

    /**
     * Classifies a list of street words into street name and street type, applying three disambiguation rules:
     *
     * <p>Rule 1 — "THE" prefix (English): If the word immediately before a trailing street type word is "THE", the
     * entire phrase is the street name, not a type. E.g., "THE PARKWAY" → name="THE PARKWAY", type=(none).
     *
     * <p>Rule 2 — Two consecutive street types (USPS Pub 28 §234.2): If there are two consecutive words that are both
     * in the street type set, the LAST one is the type and the FIRST is part of the name. E.g., "PARK DRIVE" →
     * name="PARK", type="DRIVE".
     *
     * <p>Rule 3 — French linking particles (Commission de toponymie "no double generic"): When the first word is a
     * French street type (generic), everything after it is the specific (name) — even if it contains other street type
     * words after a linking particle (DE, DU, DE LA, DES, etc.). E.g., "CHEMIN DE LA CÔTE-DES-NEIGES" → type="CHEMIN",
     * name="DE LA CÔTE-DES-NEIGES". E.g., "AVENUE DU PARC" → type="AVENUE", name="DU PARC".
     */
    private void classifyStreetNameAndType(List<String> words) {
        if (words.isEmpty()) return;

        if (words.size() == 1) {
            builder.streetName(words.get(0));
            return;
        }

        String firstWord = words.get(0).toUpperCase();

        // ---- Rule 3: French pattern — type comes BEFORE name ----
        // If the first word is a French street type, treat it as the generic.
        // Everything after it is the specific (name), even if it contains street type
        // words after linking particles. The Commission de toponymie du Québec
        // prohibits double generics, so only the first word is the type.
        if (isFrenchStreetType(firstWord) && words.size() > 1) {
            String normalized = normalizeStreetType(firstWord);
            builder.streetType(normalized != null ? normalized : firstWord);
            builder.streetName(String.join(" ", words.subList(1, words.size())));
            return;
        }

        // ---- English pattern — type comes AFTER name (rightmost street type wins) ----
        String lastStreetWord = words.get(words.size() - 1).toUpperCase();
        String normalizedLast = normalizeStreetType(lastStreetWord);
        if (normalizedLast == null) {
            // No recognizable street type at end — entire phrase is the street name
            builder.streetName(String.join(" ", words));
            return;
        }

        // We have a trailing street type word. Apply disambiguation rules.
        String precedingWord = words.get(words.size() - 2).toUpperCase();

        // ---- Rule 1: "THE" prefix ----
        // "THE" + street-type-word = proper street name (THE PARKWAY, THE BOULEVARD)
        if (precedingWord.equals("THE")) {
            builder.streetName(String.join(" ", words));
            return;
        }

        // ---- Mid-name direction (e.g., "VICTORIA E ST", "KING W ST") ----
        // If the word before the type is a short direction and there's still a name before it,
        // extract the direction and exclude it from the name.
        if (words.size() >= 3 && DIRECTIONS.contains(precedingWord)) {
            builder.streetType(normalizedLast);
            builder.streetDirection(precedingWord);
            builder.streetName(String.join(" ", words.subList(0, words.size() - 2)));
            return;
        }

        // ---- Rule 2 / Default: standard English pattern ----
        // Last word is the type, everything before is the name.
        // This also covers two consecutive street types (e.g., "PARK DRIVE"),
        // where the last one wins as the type and the first is part of the name.
        builder.streetType(normalizedLast);
        builder.streetName(String.join(" ", words.subList(0, words.size() - 1)));
    }

    private void interpretStreetOnlyLine(List<TokenInfo> tokens) {
        // Line with words that look like a street (contains a street type but no civic number)
        List<String> words = new ArrayList<>();
        for (TokenInfo t : tokens) {
            if (t.type == CanadianAddressParser.WORD
                    || t.type == CanadianAddressParser.ALPHANUMERIC
                    || t.type == CanadianAddressParser.NUMBER) {
                words.add(t.text);
            }
        }

        if (words.isEmpty()) return;

        // Direction detection: suffix preferred over prefix
        String direction = null;
        boolean hasPrefixDir = words.size() > 1
                && DIRECTIONS.contains(words.get(0).toUpperCase());

        // Suffix first (higher confidence)
        if (words.size() > 1) {
            String lastWord = words.get(words.size() - 1).toUpperCase();
            if (DIRECTIONS.contains(lastWord)) {
                direction = lastWord;
                words.remove(words.size() - 1);
            }
        }

        // Prefix fallback
        if (direction == null && hasPrefixDir) {
            direction = words.get(0).toUpperCase();
            words.remove(0);
        }

        // Use shared disambiguation logic
        classifyStreetNameAndType(words);

        // Don't overwrite a direction already set elsewhere
        if (direction != null && !builder.build().hasStreetDirection()) {
            builder.streetDirection(direction);
        }

        if (builder.build().addressType() == AddressType.INCOMPLETE) {
            builder.addressType(AddressType.CIVIC);
        }
    }

    private void interpretPoBoxLine(List<TokenInfo> tokens) {
        builder.addressType(AddressType.POSTAL_BOX);
        // Find the box number (first NUMBER token after PO/BOX/CP keywords)
        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo t = tokens.get(i);
            if (t.type == CanadianAddressParser.NUMBER) {
                builder.postalBoxNumber(t.text);
                break;
            }
        }
    }

    private void interpretRuralRouteLine(List<TokenInfo> tokens) {
        builder.addressType(AddressType.RURAL_ROUTE);
        // Find the route number (first NUMBER after RR)
        for (int i = 1; i < tokens.size(); i++) {
            TokenInfo t = tokens.get(i);
            if (t.type == CanadianAddressParser.NUMBER) {
                builder.ruralRoute(t.text);
                break;
            }
        }
    }

    private void interpretGeneralDeliveryLine() {
        builder.addressType(AddressType.GENERAL_DELIVERY);
    }

    // ---- Helper methods ----

    private boolean isCareOfLine(List<TokenInfo> tokens) {
        // Pattern: WORD('C') SLASH WORD('O') or WORD('A') SLASH WORD('S')
        // Or: WORD('C/O') or WORD('A/S') — but slash is a separate token in v3
        if (tokens.size() < 3) return false;
        String t0 = tokens.get(0).text.toUpperCase();
        boolean isSlash = tokens.get(1).type == CanadianAddressParser.SLASH;
        String t2 = tokens.get(2).text.toUpperCase();
        return isSlash && ((t0.equals("C") && t2.equals("O")) || (t0.equals("A") && t2.equals("S")));
    }

    private String extractAfterCareOf(List<TokenInfo> tokens) {
        // Skip C/O or A/S (3 tokens), collect the rest as the name
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < tokens.size(); i++) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(tokens.get(i).text);
        }
        return sb.toString().trim();
    }

    private static boolean isGenDel(String firstWord, List<TokenInfo> tokens) {
        if (!firstWord.equals("GEN")) return false;
        if (tokens.size() >= 2) {
            String second = tokens.get(1).text.toUpperCase();
            return second.equals("DEL") || second.equals("DEL.");
        }
        return false;
    }

    /**
     * Plan 7: Attempt to split a single-line comma-delimited address into synthetic delivery + region lines. Scans
     * comma positions right-to-left, looking for a province code/name or postal code after the comma. The rightmost
     * comma that satisfies this heuristic is the split point.
     *
     * <p>Returns a list of LineTokens (delivery line, region line) or null if no valid split point was found.
     */
    private List<LineTokens> tryCommaSplit(LineTokens line) {
        List<TokenInfo> tokens = line.tokens;

        // Find all comma positions
        List<Integer> commaPositions = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == CanadianAddressParser.COMMA) {
                commaPositions.add(i);
            }
        }
        if (commaPositions.isEmpty()) return null;

        // Scan comma positions to find the best split point.
        // Strategy: find the leftmost comma where everything after it forms a valid
        // region (municipality + province + postal code). This ensures municipality
        // is included in the region, not the delivery part.
        int bestCommaIdx = -1;
        int bestAfterIdx = -1;
        for (int ci = 0; ci < commaPositions.size(); ci++) {
            int commaIdx = commaPositions.get(ci);
            // Get first significant token after comma
            int afterIdx = commaIdx + 1;
            while (afterIdx < tokens.size() && tokens.get(afterIdx).type == CanadianAddressParser.COMMA) {
                afterIdx++;
            }
            if (afterIdx >= tokens.size()) continue;

            // Check if tokens from afterIdx to end contain a province code/name or postal code
            boolean hasRegionMarker = false;
            for (int j = afterIdx; j < tokens.size(); j++) {
                TokenInfo tj = tokens.get(j);
                if (tj.type == CanadianAddressParser.COMMA) continue;
                if (tj.type == CanadianAddressParser.WORD) {
                    String tjText = tj.text.toUpperCase();
                    if (PROVINCE_CODES.contains(tjText)) {
                        hasRegionMarker = true;
                        break;
                    }
                    String tjStripped = tjText.endsWith(".") ? tjText.substring(0, tjText.length() - 1) : tjText;
                    if (PROVINCE_NAMES.containsKey(tjText) || PROVINCE_NAMES.containsKey(tjStripped)) {
                        hasRegionMarker = true;
                        break;
                    }
                }
                if (tj.type == CanadianAddressParser.POSTAL_CODE || isFSA(tj.text.toUpperCase())) {
                    hasRegionMarker = true;
                    break;
                }
            }

            if (hasRegionMarker) {
                // Also verify the delivery part (before comma) has some content
                boolean hasDeliveryContent = false;
                for (int j = 0; j < commaIdx; j++) {
                    int tokenType = tokens.get(j).type;
                    if (tokenType == CanadianAddressParser.WORD
                            || tokenType == CanadianAddressParser.NUMBER
                            || tokenType == CanadianAddressParser.ALPHANUMERIC) {
                        hasDeliveryContent = true;
                        break;
                    }
                }
                if (hasDeliveryContent) {
                    bestCommaIdx = commaIdx;
                    bestAfterIdx = afterIdx;
                    break; // Use first (leftmost) valid split
                }
            }
        }

        if (bestCommaIdx >= 0) {
            List<TokenInfo> deliveryTokens = new ArrayList<>(tokens.subList(0, bestCommaIdx));
            List<TokenInfo> regionTokens = new ArrayList<>(tokens.subList(bestAfterIdx, tokens.size()));

            if (deliveryTokens.isEmpty() || regionTokens.isEmpty()) return null;

            // Check if the delivery part looks like an actual delivery line.
            // A delivery line must contain a street number (NUMBER or ALPHANUMERIC).
            // Without a number, it's likely a municipality name and the whole line
            // is really a region line (e.g., "ST. JOHN'S, NL A1A 1A1" — "ST." is
            // not a street type here, it's part of the city name).
            boolean hasNumber = false;
            for (TokenInfo dt : deliveryTokens) {
                if (dt.type == CanadianAddressParser.NUMBER || dt.type == CanadianAddressParser.ALPHANUMERIC) {
                    hasNumber = true;
                    break;
                }
            }
            if (!hasNumber) {
                // Treat as region-only: municipality is before comma, rest is province/postal
                List<TokenInfo> combinedRegion = new ArrayList<>(deliveryTokens);
                combinedRegion.addAll(regionTokens);
                String combinedText = buildText(combinedRegion);
                logger.debug("Comma split: region-only='{}'", combinedText);
                return List.of(new LineTokens(combinedRegion, combinedText));
            }

            String deliveryText = buildText(deliveryTokens);
            String regionText = buildText(regionTokens);

            logger.debug("Comma split: delivery='{}', region='{}'", deliveryText, regionText);
            return List.of(new LineTokens(deliveryTokens, deliveryText), new LineTokens(regionTokens, regionText));
        }

        return null;
    }

    /** Build a space-separated text string from a list of tokens */
    private String buildText(List<TokenInfo> tokens) {
        StringBuilder sb = new StringBuilder();
        for (TokenInfo t : tokens) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(t.text);
        }
        return sb.toString();
    }

    /**
     * Checks if a line contains ONLY a postal code (no municipality, province, etc.). Matches single POSTAL_CODE token
     * or the FSA+LDU split pattern with no other significant tokens (commas and dots are tolerated).
     */
    private boolean isPostalCodeOnlyLine(LineTokens line) {
        // Filter to significant tokens (skip COMMA, DOT)
        List<TokenInfo> significant = new ArrayList<>();
        for (TokenInfo t : line.tokens) {
            if (t.type != CanadianAddressParser.COMMA && t.type != CanadianAddressParser.DOT) {
                significant.add(t);
            }
        }
        if (significant.size() == 1 && significant.get(0).type == CanadianAddressParser.POSTAL_CODE) {
            return true;
        }
        // FSA + LDU split: 2 tokens (WORD + ALPHANUMERIC) or 3 tokens (WORD + NUMBER + WORD)
        if (significant.size() == 2 && isFSA(significant.get(0).text) && isLDU(significant.get(1).text)) {
            return true;
        }
        if (significant.size() == 3 && isFSA(significant.get(0).text)) {
            String combined = significant.get(1).text + significant.get(2).text;
            return isLDU(combined);
        }
        return false;
    }

    private boolean isCountryLine(LineTokens line) {
        return line.tokens.size() == 1
                && COUNTRY_KEYWORDS.contains(line.tokens.get(0).text.toUpperCase());
    }

    private boolean looksLikeDeliveryLine(List<TokenInfo> tokens) {
        if (tokens.isEmpty()) return false;
        TokenInfo first = tokens.get(0);
        // Starts with a number → likely a civic address (e.g., "123 MAIN ST")
        if (first.type == CanadianAddressParser.NUMBER || first.type == CanadianAddressParser.ALPHANUMERIC) {
            return true;
        }
        String firstText = first.text.toUpperCase();
        // Starts with PO Box, RR, GD, or unit designator → delivery line
        if (PO_BOX_KEYWORDS.contains(firstText) || firstText.equals("RR") || firstText.equals("GD")) {
            return true;
        }
        // "GENERAL DELIVERY" or "GEN DEL" patterns
        if (firstText.equals("GENERAL")) {
            return true;
        }
        if (isGenDel(firstText, tokens)) {
            return true;
        }
        if (isUnitDesignator(firstText)) {
            return true;
        }
        // Concatenated unit prefix (e.g., "APT5", "STE200")
        if (tryExtractUnitPrefix(firstText) != null) {
            return true;
        }
        // Starts with a short prefix direction followed by a number → likely "N 123 MAIN ST"
        if (tokens.size() >= 2
                && PREFIX_DIRECTIONS.contains(firstText)
                && (tokens.get(1).type == CanadianAddressParser.NUMBER
                        || tokens.get(1).type == CanadianAddressParser.ALPHANUMERIC)) {
            return true;
        }
        return false;
    }

    private boolean looksLikeStreetLine(List<TokenInfo> tokens) {
        // A line looks like a street if any word is a recognized street type
        for (TokenInfo t : tokens) {
            if (t.type == CanadianAddressParser.WORD && isStreetType(t.text.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Classify a pre-region line by its content anchors. Priority order prevents misclassification
     * (e.g., a country line is checked before delivery keywords). Lines with no recognizable
     * anchors default to ADDRESSEE; the dispatch logic promotes subsequent ADDRESSEE lines to
     * delivery.
     *
     * <p>Note: {@code looksLikeStreetLine()} is deliberately excluded — it would misclassify
     * organization names containing street-type words (e.g., "ABBEY NATIONAL CORP").
     */
    private LineRole classifyLine(LineTokens line) {
        List<TokenInfo> tokens = line.tokens;
        if (tokens.isEmpty()) return LineRole.ADDRESSEE;

        // 1. Country line (CANADA, CAN, CA)
        if (isCountryLine(line)) return LineRole.COUNTRY;

        // 2. Postal-code-only line
        if (isPostalCodeOnlyLine(line)) return LineRole.POSTAL_CODE_ONLY;

        // 3. Care-of (C/O, A/S)
        if (isCareOfLine(tokens)) return LineRole.CARE_OF;

        // 4. Site/compartment/employer info
        String firstText = tokens.get(0).text.toUpperCase();
        if (firstText.equals("SITE") || firstText.equals("COMP") || firstText.equals("EMPL")) {
            return LineRole.SITE_INFO;
        }

        // 5. Delivery line (civic number, PO box, RR, GD, general delivery, unit designator, etc.)
        if (looksLikeDeliveryLine(tokens)) return LineRole.DELIVERY;

        // 6. Default — no recognized anchor
        return LineRole.ADDRESSEE;
    }

    private String findPostalCode(LineTokens line) {
        for (int i = 0; i < line.tokens.size(); i++) {
            TokenInfo t = line.tokens.get(i);
            if (t.type == CanadianAddressParser.POSTAL_CODE) {
                return formatPostalCode(t.text);
            }
            if (isFSA(t.text) && i + 1 < line.tokens.size() && isLDU(line.tokens.get(i + 1).text)) {
                return t.text + " " + line.tokens.get(i + 1).text;
            }
        }
        return null;
    }

    private boolean isFSA(String text) {
        return text != null
                && text.length() == 3
                && POSTAL_CODE_FSA.matcher(text.toUpperCase()).matches();
    }

    private boolean isLDU(String text) {
        return text != null
                && text.length() == 3
                && POSTAL_CODE_LDU.matcher(text.toUpperCase()).matches();
    }

    private String formatPostalCode(String raw) {
        // Normalize to A1A 1A1 format (with space)
        String upper = raw.toUpperCase().replaceAll("\\s", "");
        if (upper.length() == 6) {
            return upper.substring(0, 3) + " " + upper.substring(3);
        }
        return upper;
    }

    /** Determine address type from collected components */
    private AddressType determineAddressType() {
        var partial = builder.build();
        boolean hasCivic = partial.hasStreetName() || partial.hasStreetNumber();
        boolean hasBox = partial.hasPostalBoxNumber();
        boolean hasRural = partial.hasRuralRoute();

        if (hasBox && hasCivic) return AddressType.MIXED;
        if (hasBox) return AddressType.POSTAL_BOX;
        if (hasRural) return AddressType.RURAL_ROUTE;
        if (hasCivic) return AddressType.CIVIC;
        return AddressType.INCOMPLETE;
    }

    private AddressComponents finalizeComponents() {
        var partial = builder.build();
        if (partial.addressType() == AddressType.INCOMPLETE) {
            builder.addressType(determineAddressType());
        }
        builder.isComplete(builder.build().isMailingValid());
        return builder.build();
    }

    // ---- Token extraction ----

    /** Represents the tokens and full text of a single address line */
    private record LineTokens(List<TokenInfo> tokens, String text) {}

    /** A single token with its type and uppercased text */
    private record TokenInfo(int type, String text) {}

    /**
     * Extract tokens from an addressLine context, returning uppercased text and preserving token type info for semantic
     * analysis.
     */
    private LineTokens extractLineTokens(CanadianAddressParser.AddressLineContext lineCtx) {
        List<TokenInfo> tokens = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        var contentCtx = lineCtx.lineContent();
        if (contentCtx == null) return new LineTokens(tokens, "");

        for (var tokenCtx : contentCtx.lineToken()) {
            int type = getTokenType(tokenCtx);
            String text = tokenCtx.getText().toUpperCase();
            tokens.add(new TokenInfo(type, text));
            if (!fullText.isEmpty()) fullText.append(" ");
            fullText.append(text);
        }

        return new LineTokens(tokens, fullText.toString());
    }

    /** Determine which terminal token type a lineToken context holds */
    private int getTokenType(CanadianAddressParser.LineTokenContext ctx) {
        if (ctx.WORD() != null) return CanadianAddressParser.WORD;
        if (ctx.NUMBER() != null) return CanadianAddressParser.NUMBER;
        if (ctx.POSTAL_CODE() != null) return CanadianAddressParser.POSTAL_CODE;
        if (ctx.ALPHANUMERIC() != null) return CanadianAddressParser.ALPHANUMERIC;
        if (ctx.HYPHEN() != null) return CanadianAddressParser.HYPHEN;
        if (ctx.SLASH() != null) return CanadianAddressParser.SLASH;
        if (ctx.DOT() != null) return CanadianAddressParser.DOT;
        if (ctx.COMMA() != null) return CanadianAddressParser.COMMA;
        if (ctx.AMPERSAND() != null) return CanadianAddressParser.AMPERSAND;
        return -1;
    }
}
