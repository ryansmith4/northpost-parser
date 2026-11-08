package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.guidedbyte.address.service.AddressParserService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
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
 * files organized by numeric province code (e.g., 35 = ON, 48 = AB). The test maps these automatically. Reading the CSV
 * rows and building the address text is shared with {@link NarSampleGenerator} through {@link NarCsv}.
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

    private static final Path REPORT_DIR = Path.of("build/reports/nar-bulk");

    private static final int WORKER_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int QUEUE_CAPACITY = 10_000;

    /** Thread-local parser — avoids creating a new service per call while remaining thread-safe. */
    private static final ThreadLocal<AddressParserService> PARSER = ThreadLocal.withInitial(AddressParserService::new);

    /** Shared progress counter — incremented by workers, read by progress reporter. */
    private final LongAdder processedCount = new LongAdder();

    private volatile long provinceStartTime;

    @ParameterizedTest(name = "{0}")
    @MethodSource("narProvinces")
    void validateProvince(String province, Path zipPath, List<String> csvEntries) throws Exception {
        var stats = new BulkStats(province);
        processedCount.reset();

        Runtime rt = Runtime.getRuntime();
        System.out.printf(
                "=== %s: %d CSV file(s), %d worker threads, heap max=%dMB ===%n",
                province, csvEntries.size(), WORKER_THREADS, rt.maxMemory() / (1024 * 1024));

        provinceStartTime = System.currentTimeMillis();

        // Bounded work queue + CallerRunsPolicy = natural backpressure.
        // When the queue fills, the reader thread does parsing work instead of blocking idle.
        var executor = new ThreadPoolExecutor(
                WORKER_THREADS,
                WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadPoolExecutor.CallerRunsPolicy());

        try {
            for (String csvEntry : csvEntries) {
                processCsvEntry(zipPath, csvEntry, province, stats, executor);
            }
        } finally {
            executor.shutdown();
            // Print progress while waiting for workers to drain
            while (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                printProgress(province, executor);
            }
        }

        long elapsed = System.currentTimeMillis() - provinceStartTime;
        long total = processedCount.sum();
        System.out.printf(
                "=== %s complete: %,d addresses in %,d ms (%.0f addr/sec) ===%n",
                province, total, elapsed, total / (elapsed / 1000.0));

        stats.printSummary(System.out);
        writeReport(province, stats);
        writeMismatchCsv(province, stats);

        assertThat(stats.parseSuccessRate())
                .as("Parse success rate for %s (actual: %.2f%%)", province, stats.parseSuccessRate() * 100)
                .isGreaterThanOrEqualTo(0.95);
    }

    private void printProgress(String province, ThreadPoolExecutor executor) {
        long count = processedCount.sum();
        long elapsed = System.currentTimeMillis() - provinceStartTime;
        double rate = elapsed > 0 ? count / (elapsed / 1000.0) : 0;
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        System.out.printf(
                "  %s: %,d processed (%.0f addr/sec, queue=%d, heap=%dMB)%n",
                province, count, rate, executor.getQueue().size(), usedMB);
    }

    static Stream<Arguments> narProvinces() throws IOException {
        Optional<Path> zip = NarCsv.findZip();
        if (zip.isEmpty()) {
            return Stream.empty();
        }

        String filter = System.getProperty("nar.provinces", "");
        Set<String> allowedProvinces = new HashSet<>();
        if (!filter.isBlank()) {
            for (String p : filter.split(",")) {
                allowedProvinces.add(p.trim().toUpperCase());
            }
        }

        Map<String, List<String>> provinceCsvs = NarCsv.provinceEntries(zip.get(), allowedProvinces);
        return provinceCsvs.entrySet().stream().map(e -> Arguments.of(e.getKey(), zip.get(), e.getValue()));
    }

    private void processCsvEntry(
            Path zipPath, String csvEntry, String province, BulkStats stats, ThreadPoolExecutor executor)
            throws Exception {
        System.out.printf("  Reading %s ...%n", csvEntry);

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(csvEntry);
            if (entry == null) {
                throw new IllegalStateException("CSV entry '" + csvEntry + "' not found in " + zipPath);
            }

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                String header = reader.readLine(); // skip header (may have BOM)

                String line;
                long lastProgressTime = System.currentTimeMillis();
                while ((line = reader.readLine()) != null) {
                    final String csvLine = line;
                    executor.execute(() -> {
                        processRow(csvLine, province, stats);
                        processedCount.increment();
                    });

                    // Print progress every 10 seconds
                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime > 10_000) {
                        printProgress(province, executor);
                        lastProgressTime = now;
                    }
                }
            }
        }
    }

    private void processRow(String csvLine, String province, BulkStats stats) {
        String[] fields = NarCsv.splitLine(csvLine);
        if (fields.length < NarCsv.MIN_COLUMNS) {
            stats.skip("short_row");
            return;
        }

        NarCsv.Row row = NarCsv.toRow(fields, province);
        if (row == null) {
            stats.skip("no_civic_or_mail_street");
            return;
        }

        String aptLabel = row.unit();
        String streetNo = row.streetNumber();
        String mailStreetName = row.streetName();
        String mailStreetType = row.streetType();
        String mailStreetDir = row.streetDirection();
        String mailMunName = row.municipality();
        String mailPostalCode = row.postalCode();
        String delivery = row.deliveryLine();
        String fullAddress = row.addressText();

        stats.total(province);

        var result = PARSER.get().parseAddress(fullAddress);

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
        String expectedProv = row.province();
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
        final ConcurrentMap<String, LongAdder> totalByProvince = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, LongAdder> skipped = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, LongAdder> parseSuccesses = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, LongAdder> parseFailures = new ConcurrentHashMap<>();

        // Per-field accuracy: field -> province -> count
        private final ConcurrentMap<String, ConcurrentMap<String, LongAdder>> fieldHas = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, ConcurrentMap<String, LongAdder>> fieldMatches = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, ConcurrentMap<String, LongAdder>> fieldMismatches =
                new ConcurrentHashMap<>();

        // Sample mismatches per field (for report) — bounded, so contention is minimal
        private final ConcurrentMap<String, List<String>> sampleMismatches = new ConcurrentHashMap<>();
        private static final int MAX_SAMPLES = 50;

        // All mismatches for CSV export
        final Queue<Mismatch> allMismatches = new ConcurrentLinkedQueue<>();

        private final Queue<String> sampleParseFailures = new ConcurrentLinkedQueue<>();
        private final ConcurrentMap<String, LongAdder> missedTypeFrequency = new ConcurrentHashMap<>();
        private final AtomicInteger parseFailureSampleCount = new AtomicInteger();

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
            if (parseFailureSampleCount.getAndIncrement() < MAX_SAMPLES) {
                sampleParseFailures.add(addr.replace("\n", " | ") + "  errors=" + errors);
            }
        }

        void hasField(String field, String p) {
            inc(fieldHas.computeIfAbsent(field, k -> new ConcurrentHashMap<>()), p);
        }

        void match(String field, String p) {
            inc(fieldMatches.computeIfAbsent(field, k -> new ConcurrentHashMap<>()), p);
        }

        void mismatch(String field, String p, String expected, String actual, String deliveryLine) {
            inc(fieldMismatches.computeIfAbsent(field, k -> new ConcurrentHashMap<>()), p);

            allMismatches.add(new Mismatch(field, p, expected, actual, deliveryLine));

            List<String> samples =
                    sampleMismatches.computeIfAbsent(field, k -> Collections.synchronizedList(new ArrayList<>()));
            if (samples.size() < MAX_SAMPLES) {
                samples.add("expected='" + expected + "' actual='" + actual + "'  line=" + deliveryLine);
            }

            if ("streetType".equals(field) && "(empty)".equals(actual)) {
                inc(missedTypeFrequency, expected.toUpperCase());
            }
        }

        double parseSuccessRate() {
            long t = sum(totalByProvince);
            return t == 0 ? 0 : (double) sum(parseSuccesses) / t;
        }

        void printSummary(PrintStream out) {
            long total = sum(totalByProvince);
            long skip = sum(skipped);
            long success = sum(parseSuccesses);
            long fail = sum(parseFailures);

            out.println();
            out.println("=".repeat(80));
            out.println("BULK NAR VALIDATION SUMMARY");
            out.println("=".repeat(80));
            out.printf("Rows skipped:            %,d%n", skip);
            skipped.entrySet().stream()
                    .sorted(Map.Entry.<String, LongAdder>comparingByValue(Comparator.comparingLong(LongAdder::sum))
                            .reversed())
                    .forEach(e ->
                            out.printf("  %-30s %,d%n", e.getKey(), e.getValue().sum()));
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
                long has = sumField(fieldHas, field);
                long ok = sumField(fieldMatches, field);
                long miss = sumField(fieldMismatches, field);
                if (has > 0 || "streetNo".equals(field)) {
                    long denom = "streetNo".equals(field) ? success : has;
                    out.printf(
                            "  %-15s  %,9d / %,9d  (%7.3f%%)  mismatches: %,d%n",
                            field, ok, denom, pct(ok, denom), miss);
                }
            }

            // Per-province summary
            out.println("-".repeat(80));
            out.println("PER-PROVINCE PARSE RATE");
            out.println("-".repeat(80));
            totalByProvince.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String p = entry.getKey();
                        long pt = entry.getValue().sum();
                        long ps = parseSuccesses.containsKey(p)
                                ? parseSuccesses.get(p).sum()
                                : 0;
                        out.printf("  %-3s  total=%,9d  success=%,9d (%7.3f%%)%n", p, pt, ps, pct(ps, pt));
                    });

            // Top 20 unrecognized types
            if (!missedTypeFrequency.isEmpty()) {
                out.println("-".repeat(80));
                out.println("UNRECOGNIZED STREET TYPES (top 20)");
                out.println("-".repeat(80));
                missedTypeFrequency.entrySet().stream()
                        .sorted(Map.Entry.<String, LongAdder>comparingByValue(Comparator.comparingLong(LongAdder::sum))
                                .reversed())
                        .limit(20)
                        .forEach(e -> out.printf(
                                "  %-25s %,d%n", e.getKey(), e.getValue().sum()));
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
                        .sorted(Map.Entry.<String, LongAdder>comparingByValue(Comparator.comparingLong(LongAdder::sum))
                                .reversed())
                        .forEach(e -> out.printf(
                                "  %-25s %,d%n", e.getKey(), e.getValue().sum()));
            }
        }

        private static long sumField(ConcurrentMap<String, ConcurrentMap<String, LongAdder>> fieldMap, String field) {
            ConcurrentMap<String, LongAdder> m = fieldMap.get(field);
            return m == null ? 0 : sum(m);
        }

        private static void inc(ConcurrentMap<String, LongAdder> m, String k) {
            m.computeIfAbsent(k, x -> new LongAdder()).increment();
        }

        private static long sum(ConcurrentMap<String, LongAdder> m) {
            return m.values().stream().mapToLong(LongAdder::sum).sum();
        }

        private static double pct(long n, long d) {
            return d == 0 ? 0 : (double) n / d * 100;
        }
    }
}
