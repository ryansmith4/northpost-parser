package com.guidedbyte.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.guidedbyte.address.AddressFixtures.Fixture;
import com.guidedbyte.address.service.AddressParserService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks hand-written addresses that differ in layout: where the unit sits, which lines are present, and so on.
 *
 * <p>The ODA and NAR bulk tests build every address in one layout, so a bug that only shows in another layout passes
 * them. The file {@code layout_test_addresses.csv} holds those other layouts. Its expected values were written by a
 * person and say what the parser should return; contrast {@link NarSampleRegressionTest}, whose file records what the
 * parser did return.
 *
 * <p>Every column is compared exactly, so an empty expected value means the field must be empty; contrast
 * {@link OdaAddressTest}, where some empty columns mean "not checked". See {@link AddressFixtures} for the file format.
 */
@DisplayName("Address Layouts")
class LayoutAddressTest {

    static final String RESOURCE = "/layout_test_addresses.csv";

    static Stream<Fixture> layouts() throws Exception {
        return AddressFixtures.load(RESOURCE).stream();
    }

    /** {@link AddressFixtures#load} drops a line with too few columns without saying so; this catches that. */
    @Test
    void everyDataLineIsLoaded() throws Exception {
        List<String> dataLines;
        try (var in = LayoutAddressTest.class.getResourceAsStream(RESOURCE)) {
            dataLines = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
        // The first line that is not a comment is the column header.
        assertThat(AddressFixtures.load(RESOURCE)).hasSize(dataLines.size() - 1).isNotEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("layouts")
    void shouldParseLayout(Fixture expected) {
        var result = new AddressParserService().parseAddress(expected.address());

        assertThat(result.successful())
                .as("parse should succeed for: %s", expected.description())
                .isTrue();

        var c = result.components();
        assertSoftly(softly -> {
            softly.assertThat(c.streetNumber()).as("streetNumber").isEqualTo(expected.streetNumber());
            softly.assertThat(c.streetName()).as("streetName").isEqualTo(expected.streetName());
            softly.assertThat(c.streetType()).as("streetType").isEqualTo(expected.streetType());
            softly.assertThat(c.streetDirection()).as("streetDirection").isEqualTo(expected.streetDirection());
            softly.assertThat(c.unitNumber()).as("unitNumber").isEqualTo(expected.unit());
            softly.assertThat(c.municipality()).as("municipality").isEqualTo(expected.city());
            softly.assertThat(c.province()).as("province").isEqualTo(expected.province());
            softly.assertThat(c.postalCode()).as("postalCode").isEqualTo(expected.postalCode());
        });
    }
}
