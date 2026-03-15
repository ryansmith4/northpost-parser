package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.guidedbyte.address.service.AddressParserService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Bulk validation test that runs the parser against Statistics Canada Open Database of Addresses (ODA) datasets.
 *
 * <p><b>Not included in normal builds.</b> Tagged with {@code oda-bulk} and excluded by default in Gradle. To run:
 *
 * <pre>
 *   # All provinces (only oda-bulk tests)
 *   ./gradlew cleanTest test -PodaBulk -PodaBulkOnly
 *
 *   # Specific province(s)
 *   ./gradlew cleanTest test -PodaBulk -PodaBulkOnly -PodaProvinces=BC,QC
 *
 *   # Include oda-bulk alongside all other tests
 *   ./gradlew cleanTest test -PodaBulk
 * </pre>
 *
 * <p><b>Setup:</b> Download ODA zip files from <a href="https://www.statcan.gc.ca/en/lode/databases/oda">Statistics
 * Canada ODA</a> and place them in the {@code oda-data/} directory at the project root.
 *
 * <p>The test reads CSV data directly from the zip files — no manual unzipping required. Each zip must contain
 * {@code ODA_XX_v1.csv} where {@code XX} is the province/territory code.
 *
 * <p>For each ODA row, this test constructs a 3-line address, parses it, and compares all extracted fields against
 * ODA's ground truth: street number, street name, street type, street direction, municipality, province, and postal
 * code.
 *
 * <p>Reports are written to {@code build/reports/oda-bulk/ODA_XX_report.txt} and a CSV of all mismatches is written to
 * {@code build/reports/oda-bulk/ODA_XX_mismatches.csv} for analysis.
 */
@Tag("oda-bulk")
@DisplayName("ODA Bulk Validation")
class OdaBulkValidationTest {

    private static final Path ODA_DIR = Path.of("oda-data");
    private static final Path REPORT_DIR = Path.of("build/reports/oda-bulk");
    private static final Pattern ZIP_PATTERN = Pattern.compile("ODA_([A-Z]{2})_v1\\.zip", Pattern.CASE_INSENSITIVE);

    // ODA CSV column indices (0-based)
    private static final int COL_STREET_NO = 5;
    private static final int COL_STREET = 6;
    private static final int COL_STR_NAME = 7;
    private static final int COL_STR_TYPE = 8;
    private static final int COL_STR_DIR = 9;
    private static final int COL_UNIT = 10;
    private static final int COL_CITY = 11;
    private static final int COL_POSTAL_CODE = 12;
    private static final int COL_CITY_PCS = 14;

    private final AddressParserService parser = new AddressParserService();

    @ParameterizedTest(name = "{0}")
    @MethodSource("odaZipFiles")
    void validateProvince(String province, Path zipPath) throws Exception {
        var stats = new BulkStats(province);

        System.out.printf("=== Processing %s addresses from %s ===%n", province, zipPath.getFileName());
        processZip(zipPath, province, stats);

        stats.printSummary(System.out);
        writeReport(province, stats);
        writeMismatchCsv(province, stats);

        assertThat(stats.parseSuccessRate())
                .as("Parse success rate for %s (actual: %.2f%%)", province, stats.parseSuccessRate() * 100)
                .isGreaterThanOrEqualTo(0.95);
    }

    static Stream<Arguments> odaZipFiles() throws IOException {
        if (!Files.isDirectory(ODA_DIR)) {
            return Stream.empty();
        }

        String filter = System.getProperty("oda.provinces", "");
        Set<String> allowedProvinces = new HashSet<>();
        if (!filter.isBlank()) {
            for (String p : filter.split(",")) {
                allowedProvinces.add(p.trim().toUpperCase());
            }
        }

        List<Arguments> args = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ODA_DIR, "ODA_*_v1.zip")) {
            for (Path zip : stream) {
                Matcher m = ZIP_PATTERN.matcher(zip.getFileName().toString());
                if (m.matches()) {
                    String province = m.group(1).toUpperCase();
                    if (allowedProvinces.isEmpty() || allowedProvinces.contains(province)) {
                        args.add(Arguments.of(province, zip));
                    }
                }
            }
        }

        args.sort(Comparator.comparing(a -> a.get()[0].toString()));
        return args.stream();
    }

    private void processZip(Path zipPath, String province, BulkStats stats) throws Exception {
        long startTime = System.currentTimeMillis();
        String csvName = "ODA_" + province + "_v1.csv";

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(csvName);
            if (entry == null) {
                throw new IllegalStateException("CSV entry '" + csvName + "' not found in " + zipPath);
            }

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                reader.readLine(); // skip header

                String line;
                int lineNo = 1;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    processRow(line, province, stats);

                    if (lineNo % 100_000 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        System.out.printf(
                                "  %,d rows (%,d ms, %.0f rows/sec)%n", lineNo, elapsed, lineNo / (elapsed / 1000.0));
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        int total = stats.totalByProvince.getOrDefault(province, 0);
        System.out.printf("  Done: %,d rows in %,d ms (%.0f rows/sec)%n", total, elapsed, total / (elapsed / 1000.0));
    }

    private void processRow(String csvLine, String province, BulkStats stats) {
        String[] fields = csvLine.split(",", -1);
        if (fields.length <= COL_CITY_PCS) {
            stats.skip("short_row");
            return;
        }

        String streetNo = fields[COL_STREET_NO].trim();
        String street = fields[COL_STREET].trim();

        if (streetNo.isEmpty() || street.isEmpty()) {
            stats.skip("no_street_no_or_street");
            return;
        }

        String strName = fields[COL_STR_NAME].trim();
        String strType = fields[COL_STR_TYPE].trim();
        String strDir = fields[COL_STR_DIR].trim();
        String unit = fields[COL_UNIT].trim();
        String city = fields[COL_CITY].trim();
        if (city.isEmpty()) {
            city = fields[COL_CITY_PCS].trim();
        }
        String postalCode = fields[COL_POSTAL_CODE].trim();

        // Construct a 3-line address
        String deliveryLine = streetNo + " " + street;

        StringBuilder regionLine = new StringBuilder();
        if (!city.isEmpty()) regionLine.append(city);
        regionLine.append(" ").append(province);
        if (!postalCode.isEmpty()) regionLine.append("  ").append(postalCode);

        String fullAddress =
                "TEST PERSON\n" + deliveryLine + "\n" + regionLine.toString().trim();

        stats.total(province);

        var result = parser.parseAddress(fullAddress);

        if (!result.successful()) {
            stats.parseFail(province, fullAddress, result.errors());
            return;
        }

        stats.parseSuccess(province);
        var c = result.components();

        // Street number
        if (c.streetNumber().equalsIgnoreCase(streetNo)) {
            stats.match("streetNo", province);
        } else {
            stats.mismatch("streetNo", province, streetNo, c.streetNumber(), deliveryLine);
        }

        // Street name
        if (!strName.isEmpty()) {
            stats.hasField("streetName", province);
            if (c.streetName().equalsIgnoreCase(strName)) {
                stats.match("streetName", province);
            } else {
                stats.mismatch("streetName", province, strName, c.streetName(), deliveryLine);
            }
        }

        // Street type
        if (!strType.isEmpty()) {
            stats.hasField("streetType", province);
            if (c.streetType().equalsIgnoreCase(strType)) {
                stats.match("streetType", province);
            } else if (!c.streetType().isEmpty()) {
                // Type was detected but doesn't match — could be abbreviation vs full form
                stats.mismatch("streetType", province, strType, c.streetType(), deliveryLine);
            } else {
                stats.mismatch("streetType", province, strType, "(empty)", deliveryLine);
            }
        }

        // Street direction
        if (!strDir.isEmpty()) {
            stats.hasField("streetDir", province);
            if (c.streetDirection().equalsIgnoreCase(strDir)) {
                stats.match("streetDir", province);
            } else {
                stats.mismatch("streetDir", province, strDir, c.streetDirection(), deliveryLine);
            }
        }

        // Municipality
        if (!city.isEmpty()) {
            stats.hasField("city", province);
            if (c.municipality().equalsIgnoreCase(city)) {
                stats.match("city", province);
            } else {
                stats.mismatch("city", province, city, c.municipality(), deliveryLine);
            }
        }

        // Province
        stats.hasField("province", province);
        if (c.province().equalsIgnoreCase(province)) {
            stats.match("province", province);
        } else {
            stats.mismatch("province", province, province, c.province(), deliveryLine);
        }

        // Postal code
        if (!postalCode.isEmpty()) {
            stats.hasField("postalCode", province);
            String expectedPc = postalCode.replaceAll("\\s", "").toUpperCase();
            String actualPc = c.postalCode().replaceAll("\\s", "").toUpperCase();
            if (expectedPc.equals(actualPc)) {
                stats.match("postalCode", province);
            } else {
                stats.mismatch("postalCode", province, postalCode, c.postalCode(), deliveryLine);
            }
        }
    }

    private void writeReport(String province, BulkStats stats) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Path reportFile = REPORT_DIR.resolve("ODA_" + province + "_report.txt");

        try (PrintStream out = new PrintStream(Files.newOutputStream(reportFile), true, StandardCharsets.UTF_8)) {
            out.printf("ODA Bulk Validation Report — %s%n", province);
            out.printf("Generated: %s%n%n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.printSummary(out);
            stats.printSampleMismatches(out);
        }

        System.out.printf("  Report written to: %s%n", reportFile);
    }

    private void writeMismatchCsv(String province, BulkStats stats) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Path csvFile = REPORT_DIR.resolve("ODA_" + province + "_mismatches.csv");

        try (PrintStream out = new PrintStream(Files.newOutputStream(csvFile), true, StandardCharsets.UTF_8)) {
            out.println("field|province|expected|actual|delivery_line");
            for (var m : stats.allMismatches) {
                out.printf(
                        "%s|%s|%s|%s|%s%n",
                        m.field, m.province, escapeCsv(m.expected), escapeCsv(m.actual), escapeCsv(m.deliveryLine));
            }
        }

        System.out.printf("  Mismatch CSV written to: %s (%,d rows)%n", csvFile, stats.allMismatches.size());
    }

    private static String escapeCsv(String value) {
        if (value.contains("|") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ---- Statistics tracking ----

    record Mismatch(String field, String province, String expected, String actual, String deliveryLine) {}

    static class BulkStats {
        final String currentProvince;
        final Map<String, Integer> totalByProvince = new LinkedHashMap<>();
        private final Map<String, Integer> skipped = new HashMap<>();
        private final Map<String, Integer> parseSuccesses = new LinkedHashMap<>();
        private final Map<String, Integer> parseFailures = new HashMap<>();

        // Per-field accuracy: field -> province -> count
        private final Map<String, Map<String, Integer>> fieldHas = new HashMap<>();
        private final Map<String, Map<String, Integer>> fieldMatches = new HashMap<>();
        private final Map<String, Map<String, Integer>> fieldMismatches = new HashMap<>();

        // Sample mismatches per field (for report)
        private final Map<String, List<String>> sampleMismatches = new LinkedHashMap<>();
        private static final int MAX_SAMPLES = 50;

        // All mismatches for CSV export
        final List<Mismatch> allMismatches = new ArrayList<>();

        private final List<String> sampleParseFailures = new ArrayList<>();
        private final Map<String, Integer> missedTypeFrequency = new HashMap<>();

        BulkStats(String province) {
            this.currentProvince = province;
        }

        void total(String p) {
            inc(totalByProvince, p);
        }

        void skip(String reason) {
            inc(skipped, reason);
        }

        void parseSuccess(String p) {
            inc(parseSuccesses, p);
        }

        void parseFail(String p, String addr, List<String> errors) {
            inc(parseFailures, p);
            if (sampleParseFailures.size() < MAX_SAMPLES) {
                sampleParseFailures.add(addr.replace("\n", " | ") + "  errors=" + errors);
            }
        }

        void hasField(String field, String p) {
            fieldHas.computeIfAbsent(field, k -> new HashMap<>());
            inc(fieldHas.get(field), p);
        }

        void match(String field, String p) {
            fieldMatches.computeIfAbsent(field, k -> new HashMap<>());
            inc(fieldMatches.get(field), p);
        }

        void mismatch(String field, String p, String expected, String actual, String deliveryLine) {
            fieldMismatches.computeIfAbsent(field, k -> new HashMap<>());
            inc(fieldMismatches.get(field), p);

            allMismatches.add(new Mismatch(field, p, expected, actual, deliveryLine));

            List<String> samples = sampleMismatches.computeIfAbsent(field, k -> new ArrayList<>());
            if (samples.size() < MAX_SAMPLES) {
                samples.add("expected='" + expected + "' actual='" + actual + "'  line=" + deliveryLine);
            }

            if ("streetType".equals(field) && "(empty)".equals(actual)) {
                inc(missedTypeFrequency, expected.toUpperCase());
            }
        }

        double parseSuccessRate() {
            int t = sum(totalByProvince);
            return t == 0 ? 0 : (double) sum(parseSuccesses) / t;
        }

        void printSummary(PrintStream out) {
            int total = sum(totalByProvince);
            int skip = sum(skipped);
            int success = sum(parseSuccesses);
            int fail = sum(parseFailures);

            out.println();
            out.println("=".repeat(80));
            out.println("BULK ODA VALIDATION SUMMARY");
            out.println("=".repeat(80));
            out.printf("Rows skipped:            %,d%n", skip);
            skipped.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> out.printf("  %-30s %,d%n", e.getKey(), e.getValue()));
            out.printf("Rows tested:             %,d%n", total);
            out.println("-".repeat(80));
            out.printf("Parse success:           %,d / %,d  (%.4f%%)%n", success, total, pct(success, total));
            out.printf("Parse failures:          %,d%n", fail);

            // Per-field accuracy
            out.println("-".repeat(80));
            out.println("FIELD ACCURACY (case-insensitive match against ODA ground truth)");
            out.println("-".repeat(80));
            String[] fields = {"streetNo", "streetName", "streetType", "streetDir", "city", "province", "postalCode"};
            for (String field : fields) {
                int has = sumField(fieldHas, field);
                int ok = sumField(fieldMatches, field);
                int miss = sumField(fieldMismatches, field);
                if (has > 0 || "streetNo".equals(field)) {
                    int denom = "streetNo".equals(field) ? success : has;
                    out.printf(
                            "  %-15s  %,9d / %,9d  (%7.3f%%)  mismatches: %,d%n",
                            field, ok, denom, pct(ok, denom), miss);
                }
            }

            // Per-province summary
            out.println("-".repeat(80));
            out.println("PER-PROVINCE PARSE RATE");
            out.println("-".repeat(80));
            for (var entry : totalByProvince.entrySet()) {
                String p = entry.getKey();
                int pt = entry.getValue();
                int ps = parseSuccesses.getOrDefault(p, 0);
                out.printf("  %-3s  total=%,9d  success=%,9d (%7.3f%%)%n", p, pt, ps, pct(ps, pt));
            }

            // Top 20 unrecognized types
            if (!missedTypeFrequency.isEmpty()) {
                out.println("-".repeat(80));
                out.println("UNRECOGNIZED STREET TYPES (top 20)");
                out.println("-".repeat(80));
                missedTypeFrequency.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(20)
                        .forEach(e -> out.printf("  %-25s %,d%n", e.getKey(), e.getValue()));
            }

            out.println("=".repeat(80));
        }

        void printSampleMismatches(PrintStream out) {
            if (!sampleParseFailures.isEmpty()) {
                out.println();
                out.println("SAMPLE PARSE FAILURES (up to " + MAX_SAMPLES + ")");
                out.println("-".repeat(80));
                sampleParseFailures.forEach(f -> out.println("  " + f));
            }

            for (var entry : sampleMismatches.entrySet()) {
                out.println();
                out.printf("SAMPLE %s MISMATCHES (up to %d)%n", entry.getKey().toUpperCase(), MAX_SAMPLES);
                out.println("-".repeat(80));
                entry.getValue().forEach(m -> out.println("  " + m));
            }

            // All unrecognized types
            if (!missedTypeFrequency.isEmpty()) {
                out.println();
                out.println("ALL UNRECOGNIZED STREET TYPES");
                out.println("-".repeat(80));
                missedTypeFrequency.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(e -> out.printf("  %-25s %,d%n", e.getKey(), e.getValue()));
            }
        }

        private static int sumField(Map<String, Map<String, Integer>> fieldMap, String field) {
            Map<String, Integer> m = fieldMap.get(field);
            return m == null ? 0 : sum(m);
        }

        private static void inc(Map<String, Integer> m, String k) {
            m.merge(k, 1, Integer::sum);
        }

        private static int sum(Map<String, Integer> m) {
            return m.values().stream().mapToInt(i -> i).sum();
        }

        private static double pct(int n, int d) {
            return d == 0 ? 0 : (double) n / d * 100;
        }
    }
}
