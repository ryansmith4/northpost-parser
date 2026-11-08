package com.guidedbyte.address;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.guidedbyte.address.service.AddressParserService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parameterized tests using addresses sourced from Statistics Canada's Open Database of Addresses (ODA) for British
 * Columbia and Quebec.
 *
 * <p>The CSV file {@code oda_test_addresses.csv} contains realistic multi-line Canadian addresses (with {@code \n} for
 * line breaks) alongside expected parsed component values. The test feeds each address to {@link AddressParserService}
 * and validates extracted components. Expectations here were curated against ODA ground truth, so empty expected values
 * for the name, unit and postal code mean "not checked"; contrast {@link NarSampleRegressionTest}, which checks every
 * field exactly.
 */
@DisplayName("ODA Address Parsing")
class OdaAddressTest {

    private AddressParserService parser;

    @BeforeEach
    void setUp() {
        parser = new AddressParserService();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("odaAddresses")
    void shouldParseOdaAddress(
            String description,
            String inputAddress,
            String expStreetNo,
            String expStreetName,
            String expStreetType,
            String expStreetDir,
            String expUnit,
            String expCity,
            String expProvince,
            String expPostalCode) {

        var result = parser.parseAddress(inputAddress);

        assertSoftly(softly -> {
            softly.assertThat(result.successful())
                    .as("parse should succeed for: %s", description)
                    .isTrue();

            if (!result.successful()) return;

            var c = result.components();

            softly.assertThat(c.streetNumber()).as("streetNumber").isEqualToIgnoringCase(expStreetNo);

            if (!expStreetName.isEmpty()) {
                softly.assertThat(c.streetName()).as("streetName").isEqualToIgnoringCase(expStreetName);
            }

            if (!expStreetType.isEmpty()) {
                softly.assertThat(c.streetType()).as("streetType").isEqualToIgnoringCase(expStreetType);
            } else {
                softly.assertThat(c.streetType())
                        .as("streetType (should be empty)")
                        .isEmpty();
            }

            if (!expStreetDir.isEmpty()) {
                softly.assertThat(c.streetDirection()).as("streetDirection").isEqualToIgnoringCase(expStreetDir);
            } else {
                softly.assertThat(c.streetDirection())
                        .as("streetDirection (should be empty)")
                        .isEmpty();
            }

            if (!expUnit.isEmpty()) {
                softly.assertThat(c.unitNumber()).as("unitNumber").isEqualToIgnoringCase(expUnit);
            }

            softly.assertThat(c.municipality()).as("municipality").isEqualToIgnoringCase(expCity);

            softly.assertThat(c.province()).as("province").isEqualToIgnoringCase(expProvince);

            if (!expPostalCode.isEmpty()) {
                softly.assertThat(c.postalCode().replaceAll("\\s", ""))
                        .as("postalCode")
                        .isEqualToIgnoringCase(expPostalCode.replaceAll("\\s", ""));
            }
        });
    }

    /** One argument set per fixture in {@code oda_test_addresses.csv}; see {@link AddressFixtures} for the format. */
    static Stream<Arguments> odaAddresses() throws Exception {
        return AddressFixtures.load("/oda_test_addresses.csv").stream()
                .map(f -> Arguments.of(
                        f.description(),
                        f.address(),
                        f.streetNumber(),
                        f.streetName(),
                        f.streetType(),
                        f.streetDirection(),
                        f.unit(),
                        f.city(),
                        f.province(),
                        f.postalCode()));
    }
}
