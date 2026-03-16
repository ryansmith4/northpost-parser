package com.guidedbyte.address.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes address components (street types and directions) to canonical forms.
 *
 * <p>Supports two normalization strategies: {@link NormalizationStrategy#FULL_FORM} expands abbreviations to full words
 * (e.g., "ST" → "STREET"), and {@link NormalizationStrategy#ABBREVIATED} reduces full words to Canada Post standard
 * abbreviations (e.g., "STREET" → "ST").
 *
 * <p>Values not found in the mapping tables are returned unchanged.
 */
final class AddressNormalizer {

    private AddressNormalizer() {}

    // ---- Street type mappings ----

    private static final Map<String, String> STREET_TYPE_TO_FULL = new HashMap<>();
    private static final Map<String, String> STREET_TYPE_TO_ABBREV = new HashMap<>();

    static {
        // English street types: abbreviation(s) → full form, full form → preferred abbreviation
        registerStreetType("STREET", "ST");
        registerStreetType("AVENUE", "AVE");
        registerStreetType("BOULEVARD", "BLVD");
        registerStreetType("DRIVE", "DR");
        registerStreetType("ROAD", "RD");
        registerStreetType("PLACE", "PL");
        registerStreetType("COURT", "CRT");
        registerStreetType("CRESCENT", "CRES");
        registerStreetType("CIRCLE", "CIR");
        registerStreetType("WAY", "WY");
        registerStreetType("LANE", "LN");
        registerStreetType("TERRACE", "TERR");
        registerStreetType("TRAIL", "TRL");
        registerStreetType("PARKWAY", "PKY");
        registerStreetType("HIGHWAY", "HWY");
        registerStreetType("CLOSE", "CL");
        registerStreetType("GATE", "GT");
        registerStreetType("GREEN", "GR");
        registerStreetType("GROVE", "GV");
        registerStreetType("HEATH", "HE");
        registerStreetType("HEIGHTS", "HTS");
        registerStreetType("HILL", "HL");
        registerStreetType("LANDING", "LD");
        registerStreetType("MEWS", "MS");
        registerStreetType("PARK", "PK");
        registerStreetType("POINT", "PT");
        registerStreetType("SQUARE", "SQ");
        registerStreetType("WALK", "WK");
        registerStreetType("BAY", "BA");
        registerStreetType("BRANCH", "BR");
        registerStreetType("CIRCUIT", "CIRC");
        registerStreetType("COMMON", "CM");
        registerStreetType("CONCESSION", "CON");
        registerStreetType("COVE", "CO");
        registerStreetType("CREEK", "CRK");
        registerStreetType("CROSSING", "XG");
        registerStreetType("ESPLANADE", "ESPL");
        registerStreetType("ESTATES", "ESTS");
        registerStreetType("EXTENSION", "EXTEN");
        registerStreetType("FREEWAY", "FWY");
        registerStreetType("GARDENS", "GDNS");
        registerStreetType("INLET", "INL");
        registerStreetType("LINE", "LI");
        registerStreetType("LOOKOUT", "LKOUT");
        registerStreetType("MANOR", "MR");
        registerStreetType("MOUNT", "MT");
        registerStreetType("MOUNTAIN", "MTN");
        registerStreetType("PASSAGE", "PASS");
        registerStreetType("PRIVATE", "PVT");
        registerStreetType("PROMENADE", "PROM");
        registerStreetType("RANGE", "RG");
        registerStreetType("RISE", "RI");
        registerStreetType("SIDEROAD", "SDRD");
        registerStreetType("TOWNLINE", "TL");
        registerStreetType("TRUNK", "TR");
        registerStreetType("VIEW", "VW");

        // Additional abbreviation variants that map to the same full form
        addAbbreviation("AV", "AVENUE");
        addAbbreviation("BV", "BOULEVARD");
        addAbbreviation("DV", "DRIVE");
        addAbbreviation("CT", "COURT");
        addAbbreviation("CR", "CRESCENT");
        addAbbreviation("CI", "CIRCLE");
        addAbbreviation("PY", "PARKWAY");
        addAbbreviation("HY", "HIGHWAY");
        addAbbreviation("CLO", "CLOSE");
        addAbbreviation("GA", "GATE");
        addAbbreviation("GRN", "GREEN");
        addAbbreviation("GRV", "GROVE");
        addAbbreviation("HT", "HEIGHTS");
        addAbbreviation("LANDNG", "LANDING");
        addAbbreviation("PA", "PARK");
        addAbbreviation("CV", "COVE");
        addAbbreviation("CDS", "CROSSING");
        addAbbreviation("GDN", "GARDENS");
        addAbbreviation("PR", "PRIVATE");
        addAbbreviation("RO", "RISE");
        addAbbreviation("SR", "SIDEROAD");
        addAbbreviation("VI", "VIEW");
        addAbbreviation("ME", "MAZE");
        addAbbreviation("TC", "THICKET");
        addAbbreviation("TE", "THICKET");
        addAbbreviation("XG", "CROSSING");

        // French street types
        registerStreetType("BOULEVARD", "BOUL");
        registerStreetType("CHEMIN", "CH");
        registerStreetType("ROUTE", "RTE");
        registerStreetType("IMPASSE", "IMP");
        registerStreetType("TERRASSE", "TSSE");
        registerStreetType("CARRÉ", "CAR");

        addAbbreviation("BL", "BOULEVARD");
        addAbbreviation("CMN", "CHEMIN");
        addAbbreviation("CARRE", "CARRÉ");
        addAbbreviation("MONTEE", "MONTÉE");
        addAbbreviation("COTE", "CÔTE");
        addAbbreviation("ALLEE", "ALLÉE");
        addAbbreviation("CRETE", "CRÊTE");
    }

    // ---- Direction mappings ----

    private static final Map<String, String> DIRECTION_TO_FULL = new HashMap<>();
    private static final Map<String, String> DIRECTION_TO_ABBREV = new HashMap<>();

    static {
        // English directions
        registerDirection("NORTH", "N");
        registerDirection("SOUTH", "S");
        registerDirection("EAST", "E");
        registerDirection("WEST", "W");
        registerDirection("NORTHEAST", "NE");
        registerDirection("NORTHWEST", "NW");
        registerDirection("SOUTHEAST", "SE");
        registerDirection("SOUTHWEST", "SW");
        registerDirection("NORTHBOUND", "NB");
        registerDirection("SOUTHBOUND", "SB");
        registerDirection("EASTBOUND", "EB");
        registerDirection("WESTBOUND", "WB");

        // Dotted abbreviations → same full form
        addDirectionAbbrev("N.", "NORTH");
        addDirectionAbbrev("S.", "SOUTH");
        addDirectionAbbrev("E.", "EAST");
        addDirectionAbbrev("W.", "WEST");
        addDirectionAbbrev("N.E.", "NORTHEAST");
        addDirectionAbbrev("N.W.", "NORTHWEST");
        addDirectionAbbrev("S.E.", "SOUTHEAST");
        addDirectionAbbrev("S.W.", "SOUTHWEST");

        // French directions — full words map to themselves, share abbreviations with English
        DIRECTION_TO_FULL.put("NORD", "NORD");
        DIRECTION_TO_FULL.put("SUD", "SUD");
        DIRECTION_TO_FULL.put("EST", "EST");
        DIRECTION_TO_FULL.put("OUEST", "OUEST");
        DIRECTION_TO_ABBREV.put("NORD", "N");
        DIRECTION_TO_ABBREV.put("SUD", "S");
        DIRECTION_TO_ABBREV.put("EST", "E");
        DIRECTION_TO_ABBREV.put("OUEST", "O");

        // French single-letter abbreviation
        DIRECTION_TO_FULL.put("O", "OUEST");
        DIRECTION_TO_ABBREV.put("O", "O");
    }

    // ---- Registration helpers ----

    private static void registerStreetType(String fullForm, String abbreviation) {
        STREET_TYPE_TO_FULL.put(abbreviation, fullForm);
        STREET_TYPE_TO_FULL.put(fullForm, fullForm);
        STREET_TYPE_TO_ABBREV.putIfAbsent(fullForm, abbreviation);
        STREET_TYPE_TO_ABBREV.put(abbreviation, abbreviation);
    }

    private static void addAbbreviation(String abbreviation, String fullForm) {
        STREET_TYPE_TO_FULL.put(abbreviation, fullForm);
        STREET_TYPE_TO_ABBREV.put(abbreviation, STREET_TYPE_TO_ABBREV.getOrDefault(fullForm, abbreviation));
    }

    private static void registerDirection(String fullForm, String abbreviation) {
        DIRECTION_TO_FULL.put(abbreviation, fullForm);
        DIRECTION_TO_FULL.put(fullForm, fullForm);
        DIRECTION_TO_ABBREV.putIfAbsent(fullForm, abbreviation);
        DIRECTION_TO_ABBREV.put(abbreviation, abbreviation);
    }

    private static void addDirectionAbbrev(String abbreviation, String fullForm) {
        DIRECTION_TO_FULL.put(abbreviation, fullForm);
        DIRECTION_TO_ABBREV.put(abbreviation, DIRECTION_TO_ABBREV.getOrDefault(fullForm, abbreviation));
    }

    // ---- Public API ----

    static String normalizeStreetType(String streetType, NormalizationStrategy strategy) {
        if (streetType == null || streetType.isEmpty()) return streetType;
        String upper = streetType.toUpperCase();
        return switch (strategy) {
            case FULL_FORM -> STREET_TYPE_TO_FULL.getOrDefault(upper, streetType);
            case ABBREVIATED -> STREET_TYPE_TO_ABBREV.getOrDefault(upper, streetType);
        };
    }

    static String normalizeDirection(String direction, NormalizationStrategy strategy) {
        if (direction == null || direction.isEmpty()) return direction;
        String upper = direction.toUpperCase();
        return switch (strategy) {
            case FULL_FORM -> DIRECTION_TO_FULL.getOrDefault(upper, direction);
            case ABBREVIATED -> DIRECTION_TO_ABBREV.getOrDefault(upper, direction);
        };
    }
}
