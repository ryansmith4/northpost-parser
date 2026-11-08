package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.guidedbyte.address.AddressFixtures.Fixture;
import com.guidedbyte.address.service.AddressParserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Replays the checked-in NAR regression sample ({@code nar_sample_addresses.csv}) and fails if the parser returns
 * anything other than the recorded component values.
 *
 * <p>The sample holds a few thousand Statistics Canada National Address Register addresses from all 13 provinces and
 * territories plus the known-hard patterns, each with the exact fields the parser produced when the file was generated.
 * It is a behaviour snapshot, not ground truth: a failure here means the parser's output changed. If the change is
 * intentional, run {@code ./gradlew refreshNarSample} and review the resulting diff. See {@link NarSampleGenerator} for
 * how the sample is drawn.
 *
 * <p>One test per province keeps the build fast and reports every differing row of that province in one message.
 */
@DisplayName("NAR Regression Sample")
class NarSampleRegressionTest {

    static final String RESOURCE = "/nar_sample_addresses.csv";

    static final Set<String> ALL_PROVINCES =
            Set.of("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT");

    private static final int MAX_REPORTED = 40;

    private static Map<String, List<Fixture>> byProvince;

    @BeforeAll
    static void loadSample() throws Exception {
        byProvince = new TreeMap<>();
        for (Fixture f : AddressFixtures.load(RESOURCE)) {
            byProvince.computeIfAbsent(f.province(), k -> new ArrayList<>()).add(f);
        }
    }

    static Stream<String> provinces() {
        return byProvince.keySet().stream();
    }

    @Test
    void sampleCoversAllThirteenProvincesAndTerritories() {
        assertThat(byProvince.keySet()).containsAll(ALL_PROVINCES);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provinces")
    void parserOutputMatchesRecordedSample(String province) {
        var parser = new AddressParserService();
        List<Fixture> fixtures = byProvince.get(province);
        List<String> diffs = new ArrayList<>();
        int rowsWithDiffs = 0;

        for (Fixture expected : fixtures) {
            var result = parser.parseAddress(expected.address());
            if (!result.successful()) {
                rowsWithDiffs++;
                diffs.add(expected.description() + "\n    parse failed: " + result.errors());
                continue;
            }
            var c = result.components();
            Fixture actual = new Fixture(
                    expected.address(),
                    c.streetNumber(),
                    c.streetName(),
                    c.streetType(),
                    c.streetDirection(),
                    c.unitNumber(),
                    c.municipality(),
                    c.province(),
                    c.postalCode());
            if (!actual.equals(expected)) {
                rowsWithDiffs++;
                diffs.add(describeDiff(expected, actual));
            }
        }

        if (!diffs.isEmpty()) {
            var msg = new StringBuilder();
            msg.append(String.format(
                    "%d of %d sampled %s addresses no longer parse the way the recorded sample says.\n",
                    rowsWithDiffs, fixtures.size(), province));
            msg.append("If the change is intentional, run ./gradlew refreshNarSample and review the diff.\n");
            diffs.stream()
                    .limit(MAX_REPORTED)
                    .forEach(d -> msg.append("  ").append(d).append('\n'));
            if (diffs.size() > MAX_REPORTED) {
                msg.append("  ... and ").append(diffs.size() - MAX_REPORTED).append(" more\n");
            }
            fail(msg.toString());
        }
    }

    private static String describeDiff(Fixture expected, Fixture actual) {
        var sb = new StringBuilder(expected.description());
        field(sb, "streetNumber", expected.streetNumber(), actual.streetNumber());
        field(sb, "streetName", expected.streetName(), actual.streetName());
        field(sb, "streetType", expected.streetType(), actual.streetType());
        field(sb, "streetDirection", expected.streetDirection(), actual.streetDirection());
        field(sb, "unit", expected.unit(), actual.unit());
        field(sb, "city", expected.city(), actual.city());
        field(sb, "province", expected.province(), actual.province());
        field(sb, "postalCode", expected.postalCode(), actual.postalCode());
        return sb.toString();
    }

    private static void field(StringBuilder sb, String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            sb.append("\n    ")
                    .append(name)
                    .append(": recorded '")
                    .append(expected)
                    .append("' but got '")
                    .append(actual)
                    .append('\'');
        }
    }
}
