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
 * Bulk validation test that runs the v3 parser against Statistics Canada Open Database of Addresses (ODA) datasets.
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
 * Canada ODA</a> and place them in the {@code oda-data/} directory at the project root:
 *
 * <pre>
 *   northpost/
 *     oda-data/
 *       ODA_BC_v1.zip
 *       ODA_QC_v1.zip
 *       ODA_ON_v1.zip
 *       ...
 * </pre>
 *
 * <p>The test reads CSV data directly from the zip files — no manual unzipping required. Each zip must contain
 * {@code ODA_XX_v1.csv} where {@code XX} is the province/territory code.
 *
 * <p>For each ODA row that has a street number and street text, this test:
 *
 * <ol>
 *   <li>Constructs a 3-line Canadian address (addressee / delivery / region)
 *   <li>Parses it with {@link AddressParserService}
 *   <li>Validates: parse success, street number match, type detection, city match
 * </ol>
 *
 * <p>Reports are written to {@code build/reports/oda-bulk/ODA_XX_report.txt}.
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
    private static final int COL_STR_TYPE = 8;
    private static final int COL_CITY = 11;
    private static final int COL_POSTAL_CODE = 12;
    private static final int COL_CITY_PCS = 14;

    private final AddressParserService parser = new AddressParserService();

    @ParameterizedTest(name = "{0}")
    @MethodSource("odaZipFiles")
    void validateProvince(String province, Path zipPath) throws Exception {
        var stats = new BulkStats();

        System.out.printf("=== Processing %s addresses from %s ===%n", province, zipPath.getFileName());
        processZip(zipPath, province, stats);

        stats.printSummary(System.out);
        writeReport(province, stats);

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

        String strType = fields[COL_STR_TYPE].trim();
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

        // Street number match
        if (c.streetNumber().equalsIgnoreCase(streetNo)) {
            stats.streetNoMatch(province);
        } else {
            stats.streetNoMismatch(province, streetNo, c.streetNumber(), deliveryLine);
        }

        // Street type detection (did we find a type when ODA says there is one?)
        if (!strType.isEmpty()) {
            stats.odaHasType(province);
            if (!c.streetType().isEmpty()) {
                stats.typeDetected(province);
            } else {
                stats.typeMissed(province, strType, deliveryLine);
            }
        }

        // Municipality match
        if (!city.isEmpty() && c.municipality().equalsIgnoreCase(city)) {
            stats.cityMatch(province);
        }
    }

    private void writeReport(String province, BulkStats stats) throws IOException {
        Files.createDirectories(REPORT_DIR);
        Path reportFile = REPORT_DIR.resolve("ODA_" + province + "_report.txt");

        try (PrintStream out = new PrintStream(Files.newOutputStream(reportFile), true, StandardCharsets.UTF_8)) {
            out.printf("ODA Bulk Validation Report — %s%n", province);
            out.printf("Generated: %s%n%n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            stats.printSummary(out);
            stats.printFullDetails(out);
        }

        System.out.printf("  Report written to: %s%n", reportFile);
    }

    // ---- Statistics tracking ----

    static class BulkStats {
        final Map<String, Integer> totalByProvince = new LinkedHashMap<>();
        private final Map<String, Integer> skipped = new HashMap<>();
        private final Map<String, Integer> parseSuccesses = new LinkedHashMap<>();
        private final Map<String, Integer> parseFailures = new HashMap<>();
        private final Map<String, Integer> streetNoMatches = new HashMap<>();
        private final Map<String, Integer> streetNoMismatches = new HashMap<>();
        private final Map<String, Integer> odaHasTypes = new HashMap<>();
        private final Map<String, Integer> typesDetected = new HashMap<>();
        private final Map<String, Integer> typesMissed = new HashMap<>();
        private final Map<String, Integer> cityMatches = new HashMap<>();

        private final List<String> sampleParseFailures = new ArrayList<>();
        private final List<String> sampleStreetNoMismatches = new ArrayList<>();
        private final List<String> sampleTypeMisses = new ArrayList<>();
        private final Map<String, Integer> missedTypeFrequency = new HashMap<>();
        private static final int MAX_SAMPLES = 50;

        void total(String p) {
            inc(totalByProvince, p);
        }

        void skip(String reason) {
            inc(skipped, reason);
        }

        void parseSuccess(String p) {
            inc(parseSuccesses, p);
        }

        void streetNoMatch(String p) {
            inc(streetNoMatches, p);
        }

        void odaHasType(String p) {
            inc(odaHasTypes, p);
        }

        void typeDetected(String p) {
            inc(typesDetected, p);
        }

        void cityMatch(String p) {
            inc(cityMatches, p);
        }

        void parseFail(String p, String addr, List<String> errors) {
            inc(parseFailures, p);
            if (sampleParseFailures.size() < MAX_SAMPLES) {
                sampleParseFailures.add(addr.replace("\n", " | ") + "  errors=" + errors);
            }
        }

        void streetNoMismatch(String p, String expected, String actual, String delivery) {
            inc(streetNoMismatches, p);
            if (sampleStreetNoMismatches.size() < MAX_SAMPLES) {
                sampleStreetNoMismatches.add("expected='" + expected + "' actual='" + actual + "'  line=" + delivery);
            }
        }

        void typeMissed(String p, String odaType, String delivery) {
            inc(typesMissed, p);
            inc(missedTypeFrequency, odaType.toUpperCase());
            if (sampleTypeMisses.size() < MAX_SAMPLES) {
                sampleTypeMisses.add("odaType='" + odaType + "'  line=" + delivery);
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
            int stNoOk = sum(streetNoMatches);
            int stNoMiss = sum(streetNoMismatches);
            int hasType = sum(odaHasTypes);
            int typeOk = sum(typesDetected);
            int typeMiss = sum(typesMissed);
            int cityOk = sum(cityMatches);

            out.println();
            out.println("=".repeat(70));
            out.println("BULK ODA VALIDATION SUMMARY");
            out.println("=".repeat(70));
            out.printf("Rows skipped:            %,d%n", skip);
            skipped.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> out.printf("  %-30s %,d%n", e.getKey(), e.getValue()));
            out.printf("Rows tested:             %,d%n", total);
            out.println("-".repeat(70));
            out.printf("Parse success:           %,d / %,d  (%.2f%%)%n", success, total, pct(success, total));
            out.printf("Parse failures:          %,d%n", fail);
            out.printf("Street # exact match:    %,d / %,d  (%.2f%%)%n", stNoOk, success, pct(stNoOk, success));
            out.printf("Street # mismatch:       %,d%n", stNoMiss);
            out.printf("Type detected (of %,d):  %,d  (%.2f%%)%n", hasType, typeOk, pct(typeOk, hasType));
            out.printf("Type missed:             %,d%n", typeMiss);
            out.printf("City exact match:        %,d%n", cityOk);

            // Per-province
            out.println("-".repeat(70));
            for (var entry : totalByProvince.entrySet()) {
                String p = entry.getKey();
                int pt = entry.getValue();
                int ps = parseSuccesses.getOrDefault(p, 0);
                out.printf(
                        "%-3s  total=%,9d  success=%,9d (%.2f%%)  streetNo=%,9d  type=%,9d/%,9d%n",
                        p,
                        pt,
                        ps,
                        pct(ps, pt),
                        streetNoMatches.getOrDefault(p, 0),
                        typesDetected.getOrDefault(p, 0),
                        odaHasTypes.getOrDefault(p, 0));
            }

            // Top 20 missed types (console-friendly)
            if (!missedTypeFrequency.isEmpty()) {
                out.println("-".repeat(70));
                out.println("Unrecognized street types (top 20):");
                missedTypeFrequency.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(20)
                        .forEach(e -> out.printf("  %-25s %,d%n", e.getKey(), e.getValue()));
            }

            out.println("=".repeat(70));
        }

        void printFullDetails(PrintStream out) {
            // All missed types (not just top 20)
            if (!missedTypeFrequency.isEmpty()) {
                out.println();
                out.println("ALL UNRECOGNIZED STREET TYPES");
                out.println("-".repeat(70));
                missedTypeFrequency.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(e -> out.printf("  %-25s %,d%n", e.getKey(), e.getValue()));
            }

            if (!sampleParseFailures.isEmpty()) {
                out.println();
                out.println("SAMPLE PARSE FAILURES (up to " + MAX_SAMPLES + ")");
                out.println("-".repeat(70));
                sampleParseFailures.forEach(f -> out.println("  " + f));
            }

            if (!sampleStreetNoMismatches.isEmpty()) {
                out.println();
                out.println("SAMPLE STREET# MISMATCHES (up to " + MAX_SAMPLES + ")");
                out.println("-".repeat(70));
                sampleStreetNoMismatches.forEach(m -> out.println("  " + m));
            }

            if (!sampleTypeMisses.isEmpty()) {
                out.println();
                out.println("SAMPLE TYPE MISSES (up to " + MAX_SAMPLES + ")");
                out.println("-".repeat(70));
                sampleTypeMisses.forEach(m -> out.println("  " + m));
            }
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
