package com.guidedbyte.address;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads rows of the Statistics Canada National Address Register (NAR) CSVs and turns each one into the mailing address
 * text the parser is tested with.
 *
 * <p>Shared by {@link NarBulkValidationTest} (full dataset, excluded from normal builds) and {@link NarSampleGenerator}
 * (draws the checked-in regression sample), so both build the address text the same way.
 */
final class NarCsv {

    /** Directory (gitignored) where the NAR zip is expected: {@code nar-data/NAR_YYYYMM.zip}. */
    static final Path NAR_DIR = Path.of("nar-data");

    /** Rows with fewer columns than this are malformed and skipped. */
    static final int MIN_COLUMNS = 19;

    private static final Pattern CSV_ENTRY =
            Pattern.compile("Addresses/Address_(\\d{2})(?:_part_\\d+)?\\.csv", Pattern.CASE_INSENSITIVE);

    // NAR CSV column indices (0-based)
    private static final int COL_APT_NO_LABEL = 2;
    private static final int COL_CIVIC_NO = 3;
    private static final int COL_CIVIC_NO_SUFFIX = 4;
    private static final int COL_MAIL_STREET_NAME = 13;
    private static final int COL_MAIL_STREET_TYPE = 14;
    private static final int COL_MAIL_STREET_DIR = 15;
    private static final int COL_MAIL_MUN_NAME = 16;
    private static final int COL_MAIL_PROV_ABVN = 17;
    private static final int COL_MAIL_POSTAL_CODE = 18;

    /** Statistics Canada Standard Geographical Classification numeric codes → 2-letter province abbreviations. */
    static final Map<String, String> SGC_TO_PROV = Map.ofEntries(
            Map.entry("10", "NL"),
            Map.entry("11", "PE"),
            Map.entry("12", "NS"),
            Map.entry("13", "NB"),
            Map.entry("24", "QC"),
            Map.entry("35", "ON"),
            Map.entry("46", "MB"),
            Map.entry("47", "SK"),
            Map.entry("48", "AB"),
            Map.entry("59", "BC"),
            Map.entry("60", "YT"),
            Map.entry("61", "NT"),
            Map.entry("62", "NU"));

    private NarCsv() {}

    /**
     * One civic NAR row, reduced to the MAIL_* fields the parser is compared against. {@code province} is the
     * MAIL_PROV_ABVN value, or the province the CSV file belongs to when that column is empty.
     */
    record Row(
            String unit,
            String civicNumber,
            String civicSuffix,
            String streetName,
            String streetType,
            String streetDirection,
            String municipality,
            String province,
            String postalCode) {

        /** Civic number with its suffix appended ({@code 123} + {@code A} → {@code 123A}). */
        String streetNumber() {
            return civicNumber + civicSuffix;
        }

        /** {@code [APT unit] number name [type] [direction]}. */
        String deliveryLine() {
            var sb = new StringBuilder();
            if (!unit.isEmpty()) {
                sb.append("APT ").append(unit).append(' ');
            }
            sb.append(streetNumber()).append(' ').append(streetName);
            if (!streetType.isEmpty()) {
                sb.append(' ').append(streetType);
            }
            if (!streetDirection.isEmpty()) {
                sb.append(' ').append(streetDirection);
            }
            return sb.toString();
        }

        /** {@code municipality province postalCode} (two spaces before the postal code, as Canada Post formats it). */
        String regionLine() {
            var sb = new StringBuilder();
            if (!municipality.isEmpty()) {
                sb.append(municipality).append(' ');
            }
            sb.append(province);
            if (!postalCode.isEmpty()) {
                sb.append("  ").append(postalCode);
            }
            return sb.toString().trim();
        }

        /** The three-line address fed to the parser: a placeholder addressee, the delivery line, the region line. */
        String addressText() {
            return "TEST PERSON\n" + deliveryLine() + "\n" + regionLine();
        }
    }

    /**
     * Splits a CSV line respecting RFC 4180 quoted fields. A naive split on commas fails on NAR rows where census
     * subdivision names contain commas (e.g., {@code "Kings, Subd. A"}).
     */
    static String[] splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Builds a {@link Row} from split fields.
     *
     * @return the row, or {@code null} when it has no civic number or no mailing street name (PO-box-only rows)
     */
    static Row toRow(String[] fields, String fallbackProvince) {
        String civicNo = fields[COL_CIVIC_NO].trim();
        String streetName = fields[COL_MAIL_STREET_NAME].trim();
        if (civicNo.isEmpty() || streetName.isEmpty()) {
            return null;
        }
        String province = fields[COL_MAIL_PROV_ABVN].trim();
        return new Row(
                fields[COL_APT_NO_LABEL].trim(),
                civicNo,
                fields[COL_CIVIC_NO_SUFFIX].trim(),
                streetName,
                fields[COL_MAIL_STREET_TYPE].trim(),
                fields[COL_MAIL_STREET_DIR].trim(),
                fields[COL_MAIL_MUN_NAME].trim(),
                province.isEmpty() ? fallbackProvince : province,
                fields[COL_MAIL_POSTAL_CODE].trim());
    }

    /** The first {@code NAR_*.zip} in {@link #NAR_DIR}, if the directory and a zip exist. */
    static Optional<Path> findZip() throws IOException {
        if (!Files.isDirectory(NAR_DIR)) {
            return Optional.empty();
        }
        List<Path> zips = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(NAR_DIR, "NAR_*.zip")) {
            for (Path zip : stream) {
                zips.add(zip);
            }
        }
        Collections.sort(zips);
        return zips.stream().findFirst();
    }

    /**
     * Lists the address CSV entries in a NAR zip, grouped by 2-letter province code and sorted so multi-part files are
     * read in order.
     *
     * @param allowedProvinces provinces to keep, or empty for all
     */
    static Map<String, List<String>> provinceEntries(Path zipPath, Set<String> allowedProvinces) throws IOException {
        Map<String, List<String>> provinceCsvs = new TreeMap<>();
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Matcher m = CSV_ENTRY.matcher(entry.getName());
                if (!m.matches()) {
                    continue;
                }
                String prov = SGC_TO_PROV.get(m.group(1));
                if (prov != null && (allowedProvinces.isEmpty() || allowedProvinces.contains(prov))) {
                    provinceCsvs.computeIfAbsent(prov, k -> new ArrayList<>()).add(entry.getName());
                }
            }
        }
        provinceCsvs.values().forEach(Collections::sort);
        return provinceCsvs;
    }
}
