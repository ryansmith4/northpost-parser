package com.guidedbyte.address;

import com.guidedbyte.address.AddressFixtures.Fixture;
import com.guidedbyte.address.model.AddressComponents;
import com.guidedbyte.address.service.AddressParserService;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Draws and refreshes the checked-in NAR regression sample, {@code src/test/resources/nar_sample_addresses.csv}.
 *
 * <p>The sample is a few thousand addresses from the Statistics Canada National Address Register (NAR) together with
 * the exact component values the Java parser produced for them. {@link NarSampleRegressionTest} replays it on every
 * build, and it is also the conformance target for ports of the parser to other languages.
 *
 * <p>Two modes:
 *
 * <ul>
 *   <li>{@code draw} ({@code ./gradlew drawNarSample}) reads the full NAR zip from {@code nar-data/}, picks a
 *       deterministic stratified sample, parses each address and writes the file. Needs the 2 GB zip; takes a few
 *       minutes. Run this to change the sample itself (new NAR release, new strata, new quotas).
 *   <li>{@code refresh} ({@code ./gradlew refreshNarSample}) keeps the sampled addresses and re-records what the parser
 *       returns for them. Needs no NAR data. Run this after an intentional parser change, then review the diff.
 * </ul>
 *
 * <p>Selection is deterministic: every candidate row is ranked by a seeded hash of its address text and each stratum
 * keeps the lowest-ranked rows, so the same zip and seed always give the same sample regardless of thread timing.
 * Within a province, rows are stratified by shape (has unit / has type / has direction / has civic suffix) so rarer
 * shapes are not drowned out. On top of that, known-hard patterns documented in {@code docs/tier3-structural-issues.md}
 * get their own small strata across provinces.
 */
public final class NarSampleGenerator {

    static final Path OUTPUT = Path.of("src/test/resources/nar_sample_addresses.csv");

    private static final long SEED = 20251108L;

    /** Random rows per province. Weighted toward variety, not population, so ON and QC do not swamp the rest. */
    private static final Map<String, Integer> PROVINCE_QUOTAS = new LinkedHashMap<>();

    static {
        PROVINCE_QUOTAS.put("AB", 250);
        PROVINCE_QUOTAS.put("BC", 250);
        PROVINCE_QUOTAS.put("MB", 150);
        PROVINCE_QUOTAS.put("NB", 150);
        PROVINCE_QUOTAS.put("NL", 150);
        PROVINCE_QUOTAS.put("NS", 150);
        PROVINCE_QUOTAS.put("NT", 60);
        PROVINCE_QUOTAS.put("NU", 40);
        PROVINCE_QUOTAS.put("ON", 400);
        PROVINCE_QUOTAS.put("PE", 100);
        PROVINCE_QUOTAS.put("QC", 400);
        PROVINCE_QUOTAS.put("SK", 150);
        PROVINCE_QUOTAS.put("YT", 60);
    }

    /** A named group of rows with its own quota. */
    private record Stratum(String id, String title, int quota, Predicate<NarCsv.Row> matches) {}

    private static final Pattern ROUTE_N = Pattern.compile("ROUTE \\d+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SINGLE_LETTER = Pattern.compile("[A-Z]");
    private static final Pattern ARTICLE_OR_GRAND = Pattern.compile("(GRAND|GRANDE|LE|LA|LES) \\S+");
    private static final Pattern ARTICLE = Pattern.compile("(LE|LA|LES) \\S+");
    private static final Pattern COMPOUND_UNIT = Pattern.compile(".*[-/.].*");
    private static final Set<String> AVENUE_TYPES = Set.of("AV", "AVE", "AVENUE");

    private static final List<Stratum> HARD_CASES = List.of(
            new Stratum(
                    "nb-route-unsplit",
                    "NB ROUTE + number, stored unsplit by NAR; the parser splits it per Canada Post (name=number,"
                            + " type=ROUTE)",
                    15,
                    r -> r.province().equals("NB")
                            && r.streetType().isEmpty()
                            && ROUTE_N.matcher(r.streetName()).matches()),
            new Stratum(
                    "nb-rte-split",
                    "NB route number with RTE type",
                    10,
                    r -> r.province().equals("NB")
                            && r.streetType().equals("RTE")
                            && DIGITS.matcher(r.streetName()).matches()),
            new Stratum(
                    "qc-rte-split",
                    "QC route number with RTE type",
                    10,
                    r -> r.province().equals("QC")
                            && r.streetType().equals("RTE")
                            && DIGITS.matcher(r.streetName()).matches()),
            new Stratum(
                    "qc-odonym-no-type",
                    "QC GRANDE/GRAND/LE/LA/LES + type word kept whole as the street name (no type)",
                    20,
                    r -> r.province().equals("QC")
                            && r.streetType().isEmpty()
                            && ARTICLE_OR_GRAND.matcher(r.streetName()).matches()),
            new Stratum(
                    "qc-article-with-type",
                    "QC LE/LA/LES + name followed by a real type (the odonym guard must not fire)",
                    15,
                    r -> r.province().equals("QC")
                            && !r.streetType().isEmpty()
                            && ARTICLE.matcher(r.streetName()).matches()),
            new Stratum(
                    "numbered-avenue",
                    "Numbered avenues (name is a bare number, type AVE/AVENUE/AV)",
                    15,
                    r -> AVENUE_TYPES.contains(r.streetType())
                            && DIGITS.matcher(r.streetName()).matches()),
            new Stratum(
                    "lettered-street",
                    "Lettered streets (name is a single letter)",
                    15,
                    r -> SINGLE_LETTER.matcher(r.streetName()).matches()),
            new Stratum(
                    "fractional-civic",
                    "Fractional civic numbers (suffix such as 1/2)",
                    15,
                    r -> r.civicSuffix().contains("/")),
            new Stratum(
                    "compound-unit",
                    "Compound unit labels (405-2, 1/2, 1101.1)",
                    20,
                    r -> COMPOUND_UNIT.matcher(r.unit()).matches()));

    private static final int SHAPES = 16;

    /** A candidate row with its deterministic rank. Lower key = picked first. */
    private record Candidate(long key, String text, NarCsv.Row row) {}

    private static final Comparator<Candidate> ORDER =
            Comparator.comparingLong(Candidate::key).thenComparing(Candidate::text);

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "draw";
        switch (mode) {
            case "draw" -> draw();
            case "refresh" -> refresh();
            default -> {
                System.err.println("Usage: NarSampleGenerator draw|refresh");
                System.exit(2);
            }
        }
    }

    // ---- refresh: re-record parser output for the existing addresses ----

    static void refresh() throws IOException {
        var parser = new AddressParserService();
        List<String> out = new ArrayList<>();
        int rows = 0;
        int changed = 0;
        int failed = 0;
        boolean headerSeen = false;

        for (String line : Files.readAllLines(OUTPUT, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.add(line);
                continue;
            }
            if (!headerSeen) {
                headerSeen = true;
                out.add(AddressFixtures.HEADER);
                continue;
            }
            Fixture old = Fixture.parse(trimmed);
            if (old == null) {
                out.add(line);
                continue;
            }
            rows++;
            Fixture fresh = record(parser, old.address());
            if (fresh == null) {
                failed++;
                System.err.println("Parse failed, line kept as-is: " + old.description());
                out.add(line);
                continue;
            }
            if (!fresh.equals(old)) {
                changed++;
            }
            out.add(fresh.toCsvLine());
        }

        write(out);
        System.out.printf("Refreshed %s: %d rows, %d changed, %d parse failures%n", OUTPUT, rows, changed, failed);
    }

    // ---- draw: stratified sample from the NAR zip ----

    static void draw() throws Exception {
        Path zip = NarCsv.findZip()
                .orElseThrow(() -> new IllegalStateException("No NAR_*.zip found in " + NarCsv.NAR_DIR.toAbsolutePath()
                        + ". See README 'NAR Bulk Validation' for the download."));
        Map<String, List<String>> entriesByProvince = NarCsv.provinceEntries(zip, Set.of());
        System.out.printf(
                "Reading %s (%d CSV entries)...%n",
                zip, entriesByProvince.values().stream().mapToInt(List::size).sum());

        long start = System.currentTimeMillis();
        List<Collector> collectors = scan(zip, entriesByProvince);
        System.out.printf("Scanned in %,d ms%n", System.currentTimeMillis() - start);

        Map<String, List<Candidate>> buckets = merge(collectors);
        Set<String> taken = new HashSet<>();
        var parser = new AddressParserService();
        var agreement = new Agreement();
        List<String> out = new ArrayList<>(header(zip));
        out.add(AddressFixtures.HEADER);

        for (Stratum s : HARD_CASES) {
            List<Candidate> picked = take(buckets.getOrDefault("H:" + s.id(), List.of()), s.quota(), taken);
            if (picked.size() < s.quota()) {
                System.err.printf("WARNING: stratum %s has only %d of %d rows%n", s.id(), picked.size(), s.quota());
            }
            out.add("");
            out.add("# === hard case: " + s.title() + " (" + picked.size() + ") ===");
            emit(out, picked, parser, agreement);
        }

        for (var e : PROVINCE_QUOTAS.entrySet()) {
            String prov = e.getKey();
            List<List<Candidate>> byShape = new ArrayList<>();
            for (int shape = 0; shape < SHAPES; shape++) {
                byShape.add(buckets.getOrDefault("P:" + prov + ":" + shape, List.of()));
            }
            List<List<Candidate>> picked = waterFill(byShape, e.getValue(), taken);
            int n = picked.stream().mapToInt(List::size).sum();
            if (n < e.getValue()) {
                System.err.printf("WARNING: %s has only %d of %d rows%n", prov, n, e.getValue());
            }
            out.add("");
            out.add("# === " + prov + ": random rows stratified by shape (" + n + ") ===");
            for (int shape = 0; shape < SHAPES; shape++) {
                if (picked.get(shape).isEmpty()) {
                    continue;
                }
                out.add("# -- " + shapeLabel(shape) + " (" + picked.get(shape).size() + ") --");
                emit(out, picked.get(shape), parser, agreement);
            }
        }

        write(out);
        System.out.printf("Wrote %s: %d rows%n", OUTPUT, taken.size());
        agreement.print();
    }

    private static List<String> header(Path zip) {
        String release = zip.getFileName().toString().replaceFirst("\\.zip$", "");
        return List.of(
                "# NAR regression sample for NorthPost Parser.",
                "#",
                "# Adapted from Statistics Canada, National Address Register (" + release + ").",
                "# This does not constitute an endorsement by Statistics Canada of this product.",
                "# Licence: Statistics Canada Open Licence, https://www.statcan.gc.ca/en/reference/licence",
                "#",
                "# Each line is one input address (literal \\n = line break) followed by the exact values the Java",
                "# parser returned for it. These are the parser's outputs, not NAR ground truth, so the file is a",
                "# behaviour snapshot: a diff here means the parser changed. An empty column means an empty string.",
                "#",
                "# Generated by NarSampleGenerator (seed " + SEED + "). Do not hand-edit:",
                "#   ./gradlew refreshNarSample   re-records expected values for these addresses (no NAR data needed)",
                "#   ./gradlew drawNarSample      redraws the sample from nar-data/NAR_*.zip",
                "#");
    }

    /** Parses each candidate and appends its fixture line; rows the parser rejects are reported and skipped. */
    private static void emit(List<String> out, List<Candidate> picked, AddressParserService parser, Agreement agr) {
        for (Candidate c : picked) {
            Fixture f = record(parser, c.text());
            if (f == null) {
                System.err.println("Parse failed, row dropped: " + c.text().replace("\n", " | "));
                continue;
            }
            agr.add(c.row(), f);
            out.add(f.toCsvLine());
        }
    }

    /** Parses one address and returns it as a fixture, or {@code null} if parsing failed. */
    private static Fixture record(AddressParserService parser, String address) {
        var result = parser.parseAddress(address);
        if (!result.successful()) {
            return null;
        }
        AddressComponents c = result.components();
        return new Fixture(
                address,
                c.streetNumber(),
                c.streetName(),
                c.streetType(),
                c.streetDirection(),
                c.unitNumber(),
                c.municipality(),
                c.province(),
                c.postalCode());
    }

    private static void write(List<String> lines) throws IOException {
        Files.createDirectories(OUTPUT.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(OUTPUT, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                w.write(line);
                w.write('\n');
            }
        }
    }

    // ---- scanning ----

    /** Per-thread bounded top-k collectors, one map entry per stratum bucket. */
    private static final class Collector {
        final Map<String, TopK> buckets = new HashMap<>();

        void offer(String bucket, int capacity, Candidate c) {
            buckets.computeIfAbsent(bucket, k -> new TopK(capacity)).offer(c);
        }
    }

    /** Keeps the {@code capacity} lowest-ranked candidates seen so far. */
    private static final class TopK {
        private final int capacity;
        private final PriorityQueue<Candidate> heap = new PriorityQueue<>(ORDER.reversed());

        TopK(int capacity) {
            this.capacity = capacity;
        }

        void offer(Candidate c) {
            if (heap.size() < capacity) {
                heap.add(c);
            } else if (ORDER.compare(c, heap.peek()) < 0) {
                heap.poll();
                heap.add(c);
            }
        }
    }

    private static List<Collector> scan(Path zip, Map<String, List<String>> entriesByProvince) throws Exception {
        List<Callable<Collector>> tasks = new ArrayList<>();
        for (var e : entriesByProvince.entrySet()) {
            for (String entry : e.getValue()) {
                tasks.add(() -> scanEntry(zip, entry, e.getKey()));
            }
        }
        int threads = Math.min(tasks.size(), Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Collector> collectors = new ArrayList<>();
            for (Future<Collector> f : pool.invokeAll(tasks)) {
                collectors.add(f.get());
            }
            return collectors;
        } catch (ExecutionException ex) {
            throw new IllegalStateException("NAR scan failed", ex.getCause());
        } finally {
            pool.shutdown();
        }
    }

    private static Collector scanEntry(Path zip, String entryName, String fileProvince) throws IOException {
        var collector = new Collector();
        long rows = 0;
        try (ZipFile zipFile = new ZipFile(zip.toFile());
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        zipFile.getInputStream(zipFile.getEntry(entryName)), StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = NarCsv.splitLine(line);
                if (fields.length < NarCsv.MIN_COLUMNS) {
                    continue;
                }
                NarCsv.Row row = NarCsv.toRow(fields, fileProvince);
                if (row == null) {
                    continue;
                }
                Integer quota = PROVINCE_QUOTAS.get(row.province());
                if (quota == null) {
                    continue;
                }
                String text = row.addressText();
                if (text.indexOf('|') >= 0 || text.indexOf('\\') >= 0 || text.indexOf('\r') >= 0) {
                    continue; // cannot be represented in the fixture format
                }
                rows++;
                var candidate = new Candidate(rank(text), text, row);
                collector.offer("P:" + row.province() + ":" + shape(row), quota, candidate);
                for (Stratum s : HARD_CASES) {
                    if (s.matches().test(row)) {
                        collector.offer("H:" + s.id(), s.quota(), candidate);
                    }
                }
            }
        }
        System.out.printf("  %-32s %,10d civic rows%n", entryName, rows);
        return collector;
    }

    /** Seeded 64-bit hash of the address text (FNV-1a, then a splitmix64 finaliser). */
    static long rank(String text) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            h ^= text.charAt(i);
            h *= 0x100000001b3L;
        }
        h ^= SEED;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        return h ^ (h >>> 31);
    }

    /** Shape bucket 0..15: bit 0 = has unit, bit 1 = has type, bit 2 = has direction, bit 3 = has civic suffix. */
    private static int shape(NarCsv.Row r) {
        return (r.unit().isEmpty() ? 0 : 1)
                | (r.streetType().isEmpty() ? 0 : 2)
                | (r.streetDirection().isEmpty() ? 0 : 4)
                | (r.civicSuffix().isEmpty() ? 0 : 8);
    }

    private static String shapeLabel(int shape) {
        List<String> parts = new ArrayList<>();
        if ((shape & 1) != 0) parts.add("unit");
        if ((shape & 2) != 0) parts.add("type");
        if ((shape & 4) != 0) parts.add("direction");
        if ((shape & 8) != 0) parts.add("civic suffix");
        return parts.isEmpty() ? "number + name only" : String.join(" + ", parts);
    }

    // ---- selection ----

    /** Combines per-thread collectors: each bucket sorted by rank, duplicates (same text) dropped. */
    private static Map<String, List<Candidate>> merge(List<Collector> collectors) {
        Map<String, List<Candidate>> all = new TreeMap<>();
        for (Collector c : collectors) {
            for (var e : c.buckets.entrySet()) {
                all.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue().heap);
            }
        }
        for (List<Candidate> list : all.values()) {
            list.sort(ORDER);
            Set<String> seen = new HashSet<>();
            list.removeIf(c -> !seen.add(c.text()));
        }
        return all;
    }

    /** First {@code n} candidates not already taken; marks them taken. */
    private static List<Candidate> take(List<Candidate> sorted, int n, Set<String> taken) {
        List<Candidate> picked = new ArrayList<>();
        for (Candidate c : sorted) {
            if (picked.size() >= n) break;
            if (taken.add(c.text())) {
                picked.add(c);
            }
        }
        return picked;
    }

    /**
     * Round-robin across shape buckets until the quota is met or every bucket is empty, so sparse shapes contribute all
     * they have and the rest is spread evenly over the others.
     */
    private static List<List<Candidate>> waterFill(List<List<Candidate>> byShape, int quota, Set<String> taken) {
        List<Deque<Candidate>> queues = new ArrayList<>();
        List<List<Candidate>> picked = new ArrayList<>();
        for (List<Candidate> list : byShape) {
            Deque<Candidate> q = new ArrayDeque<>();
            list.stream().filter(c -> !taken.contains(c.text())).forEach(q::add);
            queues.add(q);
            picked.add(new ArrayList<>());
        }
        int total = 0;
        boolean progress = true;
        while (total < quota && progress) {
            progress = false;
            for (int shape = 0; shape < queues.size() && total < quota; shape++) {
                Candidate c = queues.get(shape).poll();
                if (c != null && taken.add(c.text())) {
                    picked.get(shape).add(c);
                    total++;
                    progress = true;
                }
            }
        }
        return picked;
    }

    // ---- reporting: how often the parser agrees with NAR on the sampled rows ----

    private static final class Agreement {
        private static final int MAX_EXAMPLES = 12;
        private final Map<String, int[]> counts = new LinkedHashMap<>();
        private final Map<String, List<String>> examples = new LinkedHashMap<>();

        void add(NarCsv.Row nar, Fixture f) {
            String where = nar.deliveryLine();
            tally("streetNumber", nar.streetNumber(), f.streetNumber(), where);
            if (!nar.unit().isEmpty()) tally("unit", nar.unit(), f.unit(), where);
            tally("streetName", nar.streetName(), f.streetName(), where);
            if (!nar.streetType().isEmpty()) tally("streetType", nar.streetType(), f.streetType(), where);
            if (!nar.streetDirection().isEmpty()) {
                tally("streetDirection", nar.streetDirection(), f.streetDirection(), where);
            }
            if (!nar.municipality().isEmpty()) tally("city", nar.municipality(), f.city(), where);
            tally("province", nar.province(), f.province(), where);
            if (!nar.postalCode().isEmpty()) {
                tally(
                        "postalCode",
                        nar.postalCode().replaceAll("\\s", ""),
                        f.postalCode().replaceAll("\\s", ""),
                        where);
            }
        }

        private void tally(String field, String expected, String actual, String where) {
            int[] c = counts.computeIfAbsent(field, k -> new int[2]);
            c[1]++;
            if (expected.equalsIgnoreCase(actual)) {
                c[0]++;
            } else {
                List<String> ex = examples.computeIfAbsent(field, k -> new ArrayList<>());
                if (ex.size() < MAX_EXAMPLES) {
                    ex.add("NAR '" + expected + "' vs parser '" + actual + "'  in: " + where);
                }
            }
        }

        void print() {
            System.out.println("Agreement with NAR ground truth on the sampled rows (informational):");
            counts.forEach((field, c) ->
                    System.out.printf("  %-16s %5d / %5d  (%.1f%%)%n", field, c[0], c[1], 100.0 * c[0] / c[1]));
            examples.forEach((field, ex) -> {
                System.out.printf("  first %s disagreements:%n", field);
                ex.forEach(e -> System.out.println("    " + e));
            });
        }
    }
}
