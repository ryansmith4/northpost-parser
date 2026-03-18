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
 * Bulk validation test that runs the parser against the Statistics Canada National Address Register (NAR).
 *
 * <p><b>Not included in normal builds.</b> Tagged with {@code nar-bulk} and excluded by default in Gradle. To run:
 *
 * <pre>
 *   # All provinces (only nar-bulk tests)
 *   ./gradlew cleanTest test -PnarBulk -PnarBulkOnly
 *
 *   # Specific province(s) — use 2-letter codes
 *   ./gradlew cleanTest test -PnarBulk -PnarBulkOnly -PnarProvinces=AB,BC
 *
 *   # Include nar-bulk alongside all other tests
 *   ./gradlew cleanTest test -PnarBulk
 * </pre>
 *
 * <p><b>Setup:</b> Download a NAR zip from <a
 * href="https://www150.statcan.gc.ca/n1/pub/46-26-0002/462600022022001-eng.htm">Statistics Canada NAR</a> and place it
 * in the {@code nar-data/} directory at the project root. The zip contains {@code Addresses/Address_XX[_part_N].csv}
 * files organized by numeric province code (e.g., 35 = ON, 48 = AB). The test maps these automatically.
 *
 * <p>For each NAR row, this test constructs a mailing address from the MAIL_* fields, parses it, and compares all
 * extracted fields against NAR ground truth: unit number, street number, street name, street type, street direction,
 * municipality, province, and postal code.
 *
 * <p>Reports are written to {@code build/reports/nar-bulk/NAR_XX_report.txt} and a CSV of all mismatches is written to
 * {@code build/reports/nar-bulk/NAR_XX_mismatches.csv} for analysis.
 */
@Tag("nar-bulk")
@DisplayName("NAR Bulk Validation")
class NarBulkValidationTest {

    private static final Path NAR_DIR = Path.of("nar-data");
    private static final Path REPORT_DIR = Path.of("build/reports/nar-bulk");

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
    private static final int MIN_COLUMNS = 19;

    /** Statistics Canada Standard Geographical Classification numeric codes → 2-letter province abbreviations. */
    private static final Map<String, String> SGC_TO_PROV = Map.ofEntries(
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

    /** Reverse mapping: 2-letter abbreviation → SGC code. */
    private static final Map<String, String> PROV_TO_SGC;

    static {
        var map = new HashMap<String, String>();
        SGC_TO_PROV.forEach((sgc, prov) -> map.put(prov, sgc));
        PROV_TO_SGC = Map.copyOf(map);
    }

    private static final Pattern CSV_PATTERN =
            Pattern.compile("Addresses/Address_(\\d{2})(?:_part_\\d+)?\\.csv", Pattern.CASE_INSENSITIVE);

    private final AddressParserService parser = new AddressParserService();

    @ParameterizedTest(name = "{0}")
    @MethodSource("narProvinces")
    void validateProvince(String province, Path zipPath, List<String> csvEntries) throws Exception {
        var stats = new BulkStats(province);

        System.out.printf("=== Processing %s addresses from %s (%d CSV file(s)) ===%n", province, zipPath.getFileName(),
                csvEntries.size());

        for (String csvEntry : csvEntries) {
            processCsvEntry(zipPath, csvEntry, province, stats);
        }

        stats.printSummary(System.out);
        writeReport(province, stats);
        writeMismatchCsv(province, stats);

        assertThat(stats.parseSuccessRate())
                .as("Parse success rate for %s (actual: %.2f%%)", province, stats.parseSuccessRate() * 100)
                .isGreaterThanOrEqualTo(0.95);
    }

    static Stream<Arguments> narProvinces() throws IOException {
        if (!Files.isDirectory(NAR_DIR)) {
            return Stream.empty();
        }

        // Find all NAR zip files
        List<Path> zips = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(NAR_DIR, "NAR_*.zip")) {
            for (Path zip : stream) {
                zips.add(zip);
            }
        }
        if (zips.isEmpty()) {
            return Stream.empty();
        }

        String filter = System.getProperty("nar.provinces", "");
        Set<String> allowedProvinces = new HashSet<>();
        if (!filter.isBlank()) {
            for (String p : filter.split(",")) {
                allowedProvinces.add(p.trim().toUpperCase());
            }
        }

        // Scan zip contents and group CSV entries by province
        // Use the first (or only) zip file found
        Path zipPath = zips.get(0);
        Map<String, List<String>> provinceCsvs = new TreeMap<>();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Matcher m = CSV_PATTERN.matcher(entry.getName());
                if (m.matches()) {
                    String sgcCode = m.group(1);
                    String prov = SGC_TO_PROV.get(sgcCode);
                    if (prov != null && (allowedProvinces.isEmpty() || allowedProvinces.contains(prov))) {
                        provinceCsvs.computeIfAbsent(prov, k -> new ArrayList<>()).add(entry.getName());
                    }
                }
            }
        }

        // Sort CSV entries within each province so parts are processed in order
        provinceCsvs.values().forEach(Collections::sort);

        return provinceCsvs.entrySet().stream()
                .map(e -> Arguments.of(e.getKey(), zipPath, e.getValue()));
    }

    private void processCsvEntry(Path zipPath, String csvEntry, String province, BulkStats stats) throws Exception {
        long startTime = System.currentTimeMillis();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(csvEntry);
            if (entry == null) {
                throw new IllegalStateException("CSV entry '" + csvEntry + "' not found in " + zipPath);
            }

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                String header = reader.readLine(); // skip header (may have BOM)

                String line;
                int lineNo = 1;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    processRow(line, province, stats);

                    if (lineNo % 100_000 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        System.out.printf("  %s: %,d rows (%,d ms, %.0f rows/sec)%n", csvEntry, lineNo, elapsed,
                                lineNo / (elapsed / 1000.0));
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("  %s done in %,d ms%n", csvEntry, elapsed);
    }

    private void processRow(String csvLine, String province, BulkStats stats) {
        String[] fields = csvLine.split(",", -1);
        if (fields.length < MIN_COLUMNS) {
            stats.skip("short_row");
            return;
        }

        String civicNo = fields[COL_CIVIC_NO].trim();
        String civicSuffix = fields[COL_CIVIC_NO_SUFFIX].trim();
        String mailStreetName = fields[COL_MAIL_STREET_NAME].trim();
        String mailStreetType = fields[COL_MAIL_STREET_TYPE].trim();
        String mailStreetDir = fields[COL_MAIL_STREET_DIR].trim();
        String mailMunName = fields[COL_MAIL_MUN_NAME].trim();
        String mailProvAbvn = fields[COL_MAIL_PROV_ABVN].trim();
        String mailPostalCode = fields[COL_MAIL_POSTAL_CODE].trim();
        String aptLabel = fields[COL_APT_NO_LABEL].trim();

        // Skip rows without civic number or mailing street name — these are non-civic (PO box only)
        if (civicNo.isEmpty() || mailStreetName.isEmpty()) {
            stats.skip("no_civic_or_mail_street");
            return;
        }

        // Build the street number (civic number + optional suffix)
        String streetNo = civicNo;
        if (!civicSuffix.isEmpty()) {
            streetNo = civicNo + civicSuffix;
        }

        // Build delivery line: [APT x] streetNo streetName [streetType] [streetDir]
        StringBuilder deliveryLine = new StringBuilder();
        if (!aptLabel.isEmpty()) {
            deliveryLine.append("APT ").append(aptLabel).append(" ");
        }
        deliveryLine.append(streetNo);
        deliveryLine.append(" ").append(mailStreetName);
        if (!mailStreetType.isEmpty()) {
            deliveryLine.append(" ").append(mailStreetType);
        }
        if (!mailStreetDir.isEmpty()) {
            deliveryLine.append(" ").append(mailStreetDir);
        }

        // Build region line: municipality province  postalCode
        StringBuilder regionLine = new StringBuilder();
        if (!mailMunName.isEmpty()) regionLine.append(mailMunName);
        if (!mailProvAbvn.isEmpty()) {
            if (!regionLine.isEmpty()) regionLine.append(" ");
            regionLine.append(mailProvAbvn);
        } else {
            // Use the province from the test parameter if MAIL_PROV_ABVN is missing
            if (!regionLine.isEmpty()) regionLine.append(" ");
            regionLine.append(province);
        }
        if (!mailPostalCode.isEmpty()) {
            if (!regionLine.isEmpty()) regionLine.append("  ");
            regionLine.append(mailPostalCode);
        }

        String delivery = deliveryLine.toString();
        String fullAddress = "TEST PERSON\n" + delivery + "\n" + regionLine.toString().trim();

        stats.total(province);

        var result = parser.parseAddress(fullAddress);

        if (!result.successful()) {
            stats.parseFail(province, fullAddress, result.errors());
            return;
        }

        stats.parseSuccess(province);
        var c = result.components();

        // Street number (civic number + suffix combined)
        if (c.streetNumber().equalsIgnoreCase(streetNo)) {
            stats.match("streetNo", province);
        } else {
            stats.mismatch("streetNo", province, streetNo, c.streetNumber(), delivery);
        }

        // Unit number (NEW — not available in ODA)
        if (!aptLabel.isEmpty()) {
            stats.hasField("unitNo", province);
            if (c.unitNumber().equalsIgnoreCase(aptLabel)) {
                stats.match("unitNo", province);
            } else {
                stats.mismatch("unitNo", province, aptLabel, c.unitNumber(), delivery);
            }
        }

        // Street name
        if (!mailStreetName.isEmpty()) {
            stats.hasField("streetName", province);
            if (c.streetName().equalsIgnoreCase(mailStreetName)) {
                stats.match("streetName", province);
            } else {
                stats.mismatch("streetName", province, mailStreetName, c.streetName(), delivery);
            }
        }

        // Street type
        if (!mailStreetType.isEmpty()) {
            stats.hasField("streetType", province);
            if (c.streetType().equalsIgnoreCase(mailStreetType)) {
                stats.match("streetType", province);
            } else if (!c.streetType().isEmpty()) {
                stats.mismatch("streetType", province, mailStreetType, c.streetType(), delivery);
            } else {
                stats.mismatch("streetType", province, mailStreetType, "(empty)", delivery);
            }
        }

        // Street direction
        if (!mailStreetDir.isEmpty()) {
            stats.hasField("streetDir", province);
            if (c.streetDirection().equalsIgnoreCase(mailStreetDir)) {
                stats.match("streetDir", province);
            } else {
                stats.mismatch("streetDir", province, mailStreetDir, c.streetDirection(), delivery);
            }
        }

        // Municipality
        if (!mailMunName.isEmpty()) {
            stats.hasField("city", province);
            if (c.municipality().equalsIgnoreCase(mailMunName)) {
                stats.match("city", province);
            } else {
                stats.mismatch("city", province, mailMunName, c.municipality(), delivery);
            }
        }

        // Province
        String expectedProv = mailProvAbvn.isEmpty() ? province : mailProvAbvn;
        stats.hasField("province", province);
        if (c.province().equalsIgnoreCase(expectedProv)) {
            stats.match("province", province);
        } else {
            stats.mismatch("province", province, expectedProv, c.province(), delivery);
        }

        // Postal code
        if (!mailPostalCode.isEmpty()) {
            stats.hasField("postalCode", province);
            String expectedPc = mailPostalCode.replaceAll("\\s", "").toUpperCase();
            String actualPc = c.postalCode().replaceAll("\\s", "").toUpperCase();
            if (expectedPc.equals(actualPc)) {
                stats.match("postalCode", province);
            } else {
                stats.mismatch("postalCode", province, mailPostalCode, c.postalCode(), delivery);
            }
        }
    }

    private void writeReport(String province, BulkStats stats) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Path reportFile = REPORT_DIR.resolve("NAR_" + province + "_report.txt");

        try (PrintStream out = new PrintStream(Files.newOutputStream(reportFile), true, StandardCharsets.UTF_8)) {
            out.printf("NAR Bulk Validation Report — %s%n", province);
            out.printf("Generated: %s%n%n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.printSummary(out);
            stats.printSampleMismatches(out);
        }

        System.out.printf("  Report written to: %s%n", reportFile);
    }

    private void writeMismatchCsv(String province, BulkStats stats) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Path csvFile = REPORT_DIR.resolve("NAR_" + province + "_mismatches.csv");

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
            out.println("BULK NAR VALIDATION SUMMARY");
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
            out.println("FIELD ACCURACY (case-insensitive match against NAR ground truth)");
            out.println("-".repeat(80));
            String[] fields = {
                "streetNo", "unitNo", "streetName", "streetType", "streetDir", "city", "province", "postalCode"
            };
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
